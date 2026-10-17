//! GPU residency of LOD columns (plain and textured) with prefetch uploads.

use crate::render::vulkanic::SubmissionId;
use crate::render::worldrender::geometry::arenas::SourceTerrainGeometryPages;
use crate::render::worldrender::lod::*;

/// Private residency for immutable LOD geometry. It deliberately exposes no
/// pipeline, material, or draw operation: a completed material/pass contract
/// must select those separately. Uploads are staged into the caller's combined
/// frame submission and commit only once that submission is accepted.
/// Host bytes of off-screen DH columns staged for upload per frame.
pub(super) const WORLD_LOD_PREFETCH_UPLOAD_BYTES_PER_FRAME: usize = 8 * 1024 * 1024;

#[derive(Default)]
pub(crate) struct WorldLodGpuResidency {
    pub(in crate::render::worldrender::lod) active: BTreeMap<u64, WorldLodGpuColumnResources>,
    pub(in crate::render::worldrender::lod) pending: Option<BTreeMap<u64, WorldLodGpuColumnResources>>,
    /// Validated visible ranges for the current immutable asset generations.
    /// The instance list is retained as the cache key so camera/order changes
    /// cannot reuse a stale draw list. This cache owns only copied Rust
    /// metadata and private GAL handles; it never retains Java or DH objects.
    pub(in crate::render::worldrender::lod) visible_draw_cache: Option<(Vec<WorldLodColumnInstanceRequest>, Vec<WorldLodGpuDraw>)>,
    /// Shared device pages holding every resident column's vertices and
    /// indices (vertex pages bind as storage, index pages as index buffers).
    pub(in crate::render::worldrender::lod) pages: SourceTerrainGeometryPages,
    /// Pages written by a confirmed submission: later uploads transition
    /// them from their read state instead of from `Undefined`.
    pub(in crate::render::worldrender::lod) initialized_pages: BTreeSet<Handle>,
    /// The pending transaction's staging buffer and the pages it writes.
    pub(in crate::render::worldrender::lod) pending_staging: Option<(Handle, Vec<Handle>)>,
}

impl WorldLodGpuResidency {
    pub(crate) fn stage_visible_uploads(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
        instances: &[WorldLodColumnInstanceRequest],
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.pending.is_some() {
            return Err(GalError::backend(
                "world LOD GPU upload transaction is already awaiting submission confirmation",
            ));
        }
        let mut requested = BTreeSet::new();
        for instance in instances {
            let asset = assets.get(&instance.column_key).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "world LOD GPU upload references unknown column {}",
                    instance.column_key
                ))
            })?;
            if asset.column_generation != instance.column_generation {
                return Err(GalError::invalid_argument(
                    "world LOD GPU upload instance generation differs from cached payload",
                ));
            }
            let segment = asset
                .segments
                .get(instance.segment_index as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "world LOD GPU upload references missing segment {}",
                        instance.segment_index
                    ))
                })?;
            if segment.layer != instance.layer {
                return Err(GalError::invalid_argument(
                    "world LOD GPU upload instance layer differs from cached payload",
                ));
            }
            requested.insert(instance.column_key);
        }
        // DH uploads every loaded column, not only the visible ones. Uploading
        // off-screen columns too (bounded per frame) lets their CPU copies be
        // released once confirmed instead of being kept until they come into
        // view.
        let mut prefetch_budget = WORLD_LOD_PREFETCH_UPLOAD_BYTES_PER_FRAME;
        for (column_key, asset) in assets {
            if prefetch_budget == 0 {
                break;
            }
            if requested.contains(column_key)
                || self.active.get(column_key).is_some_and(|resources| {
                    resources.column_generation == asset.column_generation
                })
                || !asset.segments.iter().all(WorldLodGpuSegment::upload_payload_is_present)
            {
                continue;
            }
            let bytes = asset
                .segments
                .iter()
                .map(|segment| segment.vertex_bytes.len() + segment.index_bytes.len())
                .sum::<usize>();
            prefetch_budget = prefetch_budget.saturating_sub(bytes);
            requested.insert(*column_key);
        }

        // Pages emptied by completed releases go back to the GAL (destroyed
        // once the pass sets that bind them are gone).
        for page in self.pages.reclaim(gal) {
            self.initialized_pages.remove(&page);
            let _ = gal.retire(page);
        }
        let mut created = BTreeMap::new();
        let mut upload = WorldLodUploadTransaction::default();
        let result = (|| -> GalResult<()> {
            for column_key in requested {
                let asset = assets
                    .get(&column_key)
                    .expect("requested columns are validated against the asset map");
                if self.active.get(&column_key).is_some_and(|resources| {
                    resources.column_generation == asset.column_generation
                }) {
                    continue;
                }
                let resources = create_column_resources(gal, &mut self.pages, asset, &mut upload)?;
                created.insert(column_key, resources);
            }
            Ok(())
        })();
        let staged = result.and_then(|()| upload.finish(gal, &self.initialized_pages));
        let (staging, mut staged_ops) = match staged {
            Ok(staged) => staged,
            Err(error) => {
                for (_, resources) in created {
                    self.pages.release_now(resources.vertex_range);
                    self.pages.release_now(resources.index_range);
                }
                return Err(error);
            }
        };
        if !created.is_empty() {
            ops.append(&mut staged_ops);
            self.pending_staging = staging.map(|buffer| (buffer, upload.written_pages()));
            self.pending = Some(created);
            // A replacement generation may use the same column key. Do not
            // let a cached draw list retain the previous generation's handles
            // while the upload is awaiting submission confirmation.
            self.visible_draw_cache = None;
        }
        Ok(())
    }

    /// Resolves the exact visible ranges for the current combined submission.
    /// Newly created resources are intentionally visible here before
    /// `confirm_submission`: their host writes and transfer barriers precede
    /// the eventual draw in that same submission. If the submission fails,
    /// `discard_submission` destroys those resources instead of letting them
    /// escape into the active cache.
    pub(crate) fn resolve_visible_draws(
        &self,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
        instances: &[WorldLodColumnInstanceRequest],
    ) -> GalResult<Vec<WorldLodGpuDraw>> {
        let mut draws = Vec::with_capacity(instances.len());
        self.resolve_visible_draws_into(assets, instances, &mut draws)?;
        Ok(draws)
    }

    /// Resolve a visible list once per immutable asset/instance identity.
    /// Callers receive an owned vector because the surrounding frontend may
    /// need to mutably borrow other Rust-owned pass state while it plans and
    /// stages materials. The expensive generation, payload, and resource
    /// validation still happens only when the semantic instance list changes.
    pub(crate) fn resolve_visible_draws_cached(
        &mut self,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
        instances: &[WorldLodColumnInstanceRequest],
    ) -> GalResult<Vec<WorldLodGpuDraw>> {
        if let Some((cached_instances, cached_draws)) = &self.visible_draw_cache {
            if cached_instances.as_slice() == instances {
                return Ok(cached_draws.clone());
            }
        }
        let draws = self.resolve_visible_draws(assets, instances)?;
        self.visible_draw_cache = Some((instances.to_vec(), draws.clone()));
        Ok(draws)
    }

    /// Resolves into caller-owned bounded scratch so the ordinary frame path
    /// does not allocate a second visible-draw vector on every cache hit. A
    /// cache miss is built transactionally into `draws`; only a complete
    /// result replaces the retained identity and metadata, and both retained
    /// vectors reuse their previous capacities during active DH generation.
    pub(crate) fn resolve_visible_draws_cached_into(
        &mut self,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
        instances: &[WorldLodColumnInstanceRequest],
        draws: &mut Vec<WorldLodGpuDraw>,
    ) -> GalResult<()> {
        draws.clear();
        if let Some((cached_instances, cached_draws)) = &self.visible_draw_cache {
            if cached_instances.as_slice() == instances {
                draws.extend_from_slice(cached_draws);
                return Ok(());
            }
        }
        self.resolve_visible_draws_into(assets, instances, draws)?;
        let (mut cached_instances, mut cached_draws) = self
            .visible_draw_cache
            .take()
            .unwrap_or_else(|| (Vec::new(), Vec::new()));
        cached_instances.clear();
        cached_instances.extend_from_slice(instances);
        cached_draws.clear();
        cached_draws.extend_from_slice(draws);
        self.visible_draw_cache = Some((cached_instances, cached_draws));
        Ok(())
    }

    pub(super) fn resolve_visible_draws_into(
        &self,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
        instances: &[WorldLodColumnInstanceRequest],
        draws: &mut Vec<WorldLodGpuDraw>,
    ) -> GalResult<()> {
        draws.reserve(instances.len());
        for instance in instances {
            let asset = assets.get(&instance.column_key).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "world LOD draw references unknown column {}",
                    instance.column_key
                ))
            })?;
            if asset.column_generation != instance.column_generation {
                return Err(GalError::invalid_argument(
                    "world LOD draw instance generation differs from cached payload",
                ));
            }
            let segment = asset
                .segments
                .get(instance.segment_index as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "world LOD draw references missing segment {}",
                        instance.segment_index
                    ))
                })?;
            if segment.layer != instance.layer {
                return Err(GalError::invalid_argument(
                    "world LOD draw instance layer differs from cached payload",
                ));
            }
            if segment.vertex_layout_version != WORLD_LOD_GPU_VERTEX_LAYOUT_V2
                || segment.vertex_count == 0
            {
                return Err(GalError::invalid_argument(
                    "world LOD draw references an unsupported GPU vertex layout",
                ));
            }
            if segment.index_count == 0 || segment.index_count % 3 != 0 {
                return Err(GalError::invalid_argument(
                    "world LOD draw requires a non-empty triangle-aligned index range",
                ));
            }
            let column_resources =
                self.resources_for_submission(instance.column_key, instance.column_generation)?;
            let resources = column_resources
                .segments
                .get(instance.segment_index as usize)
                .ok_or_else(|| {
                    GalError::backend(format!(
                        "world LOD GPU resources are missing segment {}",
                        instance.segment_index
                    ))
                })?;
            draws.push(WorldLodGpuDraw {
                column_key: instance.column_key,
                column_generation: instance.column_generation,
                origin: asset.origin,
                layer: instance.layer,
                segment_index: instance.segment_index,
                order: instance.order,
                vertex_buffer: resources.vertex_buffer,
                vertex_base: resources.vertex_base,
                index_buffer: column_resources.index_buffer,
                index_offset: resources.index_offset,
                index_type: segment.index_type,
                index_count: segment.index_count,
            });
        }
        Ok(())
    }

    pub(crate) fn confirm_submission(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        let Some(created) = self.pending.take() else {
            return Ok(());
        };
        // The accepted submission recorded the staging copies; the GAL defers
        // the staging buffer's destruction until it completes.
        if let Some((staging, written)) = self.pending_staging.take() {
            let _ = gal.retire(staging);
            self.initialized_pages.extend(written);
        }
        let after = gal.next_submission_id();
        for (column_key, resources) in created {
            if let Some(previous) = self.active.insert(column_key, resources) {
                self.release_after(after, previous);
            }
        }
        Ok(())
    }

    pub(crate) fn discard_submission(&mut self, gal: &mut VulkanicGal) {
        if let Some((staging, _)) = self.pending_staging.take() {
            let _ = gal.retire(staging);
        }
        if let Some(created) = self.pending.take() {
            // No submitted work references ranges of a rejected transaction.
            for (_, resources) in created {
                self.pages.release_now(resources.vertex_range);
                self.pages.release_now(resources.index_range);
            }
        }
        // Pending resources may be referenced by a resolved draw list. A
        // failed submission must retire both together.
        self.visible_draw_cache = None;
    }

    /// Returns a column's ranges once submissions up to `after` complete.
    fn release_after(&mut self, after: SubmissionId, resources: WorldLodGpuColumnResources) {
        self.pages.release_after(after, resources.vertex_range);
        self.pages.release_after(after, resources.index_range);
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        self.discard_submission(gal);
        self.visible_draw_cache = None;
        let stale = self
            .active
            .iter()
            .filter_map(|(&column_key, resources)| {
                assets
                    .get(&column_key)
                    .is_none_or(|asset| asset.column_generation != resources.column_generation)
                    .then_some(column_key)
            })
            .collect::<Vec<_>>();
        let after = gal.next_submission_id();
        for column_key in stale {
            if let Some(resources) = self.active.remove(&column_key) {
                self.release_after(after, resources);
            }
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.discard_submission(gal);
        self.visible_draw_cache = None;
        self.active.clear();
        // Retired pages are destroyed once their in-flight submissions and
        // the pass sets that bind them are gone.
        self.pages.destroy_all(gal);
        self.initialized_pages.clear();
    }

    pub(crate) fn active_generation(&self, column_key: u64) -> Option<u64> {
        self.active
            .get(&column_key)
            .map(|resources| resources.column_generation)
    }

    pub(super) fn resources_for_submission(
        &self,
        column_key: u64,
        column_generation: u64,
    ) -> GalResult<&WorldLodGpuColumnResources> {
        let resources = self
            .pending
            .as_ref()
            .and_then(|pending| pending.get(&column_key))
            .or_else(|| self.active.get(&column_key))
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "world LOD draw column {column_key} has not been staged for GPU submission",
                ))
            })?;
        if resources.column_generation != column_generation {
            return Err(GalError::invalid_argument(format!(
                "world LOD draw column {column_key} GPU generation {} does not match instance generation {column_generation}",
                resources.column_generation
            )));
        }
        Ok(resources)
    }
}

#[derive(Clone, Copy, Debug)]
pub(super) struct WorldLodTexturedGpuSegmentResources {
    pub(in crate::render::worldrender::lod) vertex_buffer: Handle,
    pub(in crate::render::worldrender::lod) index_buffer: Handle,
    /// Indexes into the matching reduced-color source vertex stream for
    /// source quads which have no exact atlas provenance. Keeping this beside
    /// the exact-atlas asset lets the caller partition a partial segment
    /// without duplicating the resolved quads in the coarse pass.
    pub(in crate::render::worldrender::lod) unresolved_index_buffer: Option<Handle>,
}

#[derive(Clone, Debug)]
pub(super) struct WorldLodTexturedGpuColumnResources {
    pub(in crate::render::worldrender::lod) column_generation: u64,
    pub(in crate::render::worldrender::lod) segments: BTreeMap<u32, WorldLodTexturedGpuSegmentResources>,
}

impl WorldLodTexturedGpuColumnResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for (_, segment) in self.segments.into_iter().rev() {
            if let Some(index_buffer) = segment.unresolved_index_buffer {
                let _ = gal.retire(index_buffer);
            }
            let _ = gal.retire(segment.index_buffer);
            let _ = gal.retire(segment.vertex_buffer);
        }
    }
}

/// An exact-atlas draw replaces one source segment, never a whole column.
/// The source segment ordinal is retained until final command construction so
/// incomplete semantic provenance cannot accidentally duplicate or suppress
/// a neighboring legacy DH segment.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodTexturedGpuDraw {
    pub column_key: u64,
    pub column_generation: u64,
    pub origin: [i32; 3],
    pub layer: u32,
    pub source_segment_index: u32,
    pub order: u32,
    pub vertex_buffer: Handle,
    pub index_buffer: Handle,
    pub index_type: IndexType,
    pub index_count: u32,
}

/// Private residency for the complete exact-atlas subset of a DH column. It
/// intentionally shares no buffers with the legacy stream: the two private
/// vertex contracts differ, and route selection happens per source segment.
#[derive(Default)]
pub(crate) struct WorldLodTexturedGpuResidency {
    pub(in crate::render::worldrender::lod) active: BTreeMap<u64, WorldLodTexturedGpuColumnResources>,
    pub(in crate::render::worldrender::lod) pending: Option<BTreeMap<u64, WorldLodTexturedGpuColumnResources>>,
}

impl WorldLodTexturedGpuResidency {
    pub(crate) fn stage_visible_uploads(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodTexturedGpuColumnAsset>,
        instances: &[WorldLodColumnInstanceRequest],
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.pending.is_some() {
            return Err(GalError::backend(
                "world LOD exact-atlas GPU upload transaction is already awaiting submission confirmation",
            ));
        }
        let requested = instances
            .iter()
            .filter_map(|instance| {
                let asset = assets.get(&instance.column_key)?;
                (asset.column_generation == instance.column_generation
                    && asset.segments.iter().any(|segment| {
                        segment.source_segment_index == instance.segment_index
                            && segment.layer == instance.layer
                    }))
                .then_some(instance.column_key)
            })
            .collect::<BTreeSet<_>>();
        let mut created = BTreeMap::new();
        let mut staged_ops = Vec::new();
        let result =
            (|| -> GalResult<()> {
                for column_key in requested {
                    let asset = assets
                        .get(&column_key)
                        .expect("requested exact-atlas columns come from the asset map");
                    if self.active.get(&column_key).is_some_and(|resources| {
                        resources.column_generation == asset.column_generation
                    }) {
                        continue;
                    }
                    let resources = create_textured_column_resources(gal, asset)?;
                    staged_ops.extend(textured_upload_ops(asset, &resources));
                    created.insert(column_key, resources);
                }
                Ok(())
            })();
        if let Err(error) = result {
            for (_, resources) in created {
                resources.destroy(gal);
            }
            return Err(error);
        }
        if !created.is_empty() {
            ops.append(&mut staged_ops);
            self.pending = Some(created);
        }
        Ok(())
    }

    /// Returns `Ok(None)` for an incomplete source segment. That is a route
    /// decision, not a resource error: the caller must keep exactly one
    /// legacy-color draw for that segment.
    pub(crate) fn resolve_visible_draw(
        &self,
        assets: &BTreeMap<u64, WorldLodTexturedGpuColumnAsset>,
        instance: &WorldLodColumnInstanceRequest,
        origin: [i32; 3],
    ) -> GalResult<Option<WorldLodTexturedGpuDraw>> {
        let Some(asset) = assets.get(&instance.column_key) else {
            return Ok(None);
        };
        if asset.column_generation != instance.column_generation {
            return Ok(None);
        }
        let Some(segment) = asset.segments.iter().find(|segment| {
            segment.source_segment_index == instance.segment_index
                && segment.layer == instance.layer
        }) else {
            return Ok(None);
        };
        if segment.vertex_layout_version != WORLD_LOD_TEXTURED_GPU_VERTEX_LAYOUT_V2
            || segment.vertex_bytes.is_empty()
            || segment.vertex_bytes.len() % WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES != 0
        {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas draw references an unsupported vertex payload",
            ));
        }
        let index_stride = match segment.index_type {
            IndexType::U16 => std::mem::size_of::<u16>(),
            IndexType::U32 => std::mem::size_of::<u32>(),
        };
        if segment.index_bytes.len() % index_stride != 0 {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas index payload is not aligned to its explicit index type",
            ));
        }
        let index_count =
            u32::try_from(segment.index_bytes.len() / index_stride).map_err(|_| {
                GalError::invalid_argument("world LOD exact-atlas index count exceeds u32")
            })?;
        if index_count == 0 || index_count % 3 != 0 {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas draw requires a non-empty triangle-aligned index range",
            ));
        }
        let resources =
            self.resources_for_submission(instance.column_key, instance.column_generation)?;
        let resources = resources
            .segments
            .get(&instance.segment_index)
            .ok_or_else(|| {
                GalError::backend(format!(
                    "world LOD exact-atlas GPU resources are missing source segment {}",
                    instance.segment_index
                ))
            })?;
        Ok(Some(WorldLodTexturedGpuDraw {
            column_key: instance.column_key,
            column_generation: instance.column_generation,
            origin,
            layer: instance.layer,
            source_segment_index: instance.segment_index,
            order: instance.order,
            vertex_buffer: resources.vertex_buffer,
            index_buffer: resources.index_buffer,
            index_type: segment.index_type,
            index_count,
        }))
    }

    /// Resolves the complementary reduced-color range for a source segment
    /// which also has one or more exact-atlas quads. The caller supplies the
    /// normal LOD draw so the vertex stream and per-draw semantics remain
    /// identical; this helper replaces only its explicit index range.
    pub(crate) fn resolve_visible_unresolved_draw(
        &self,
        assets: &BTreeMap<u64, WorldLodTexturedGpuColumnAsset>,
        instance: &WorldLodColumnInstanceRequest,
        source_draw: WorldLodGpuDraw,
    ) -> GalResult<Option<WorldLodGpuDraw>> {
        if source_draw.column_key != instance.column_key
            || source_draw.column_generation != instance.column_generation
            || source_draw.layer != instance.layer
            || source_draw.segment_index != instance.segment_index
        {
            return Err(GalError::invalid_argument(
                "world LOD unresolved range does not match its visible source draw",
            ));
        }
        let Some(asset) = assets.get(&instance.column_key) else {
            return Ok(None);
        };
        if asset.column_generation != instance.column_generation {
            return Ok(None);
        }
        let Some(segment) = asset.segments.iter().find(|segment| {
            segment.source_segment_index == instance.segment_index
                && segment.layer == instance.layer
        }) else {
            return Ok(None);
        };
        let Some(unresolved_index_bytes) = segment.unresolved_index_bytes.as_ref() else {
            return Ok(None);
        };
        if unresolved_index_bytes.is_empty()
            || unresolved_index_bytes.len() % std::mem::size_of::<u32>() != 0
        {
            return Err(GalError::invalid_argument(
                "world LOD unresolved range has an invalid explicit u32 index payload",
            ));
        }
        let index_count = u32::try_from(unresolved_index_bytes.len() / std::mem::size_of::<u32>())
            .map_err(|_| {
                GalError::invalid_argument("world LOD unresolved index count exceeds u32")
            })?;
        if index_count % 3 != 0 {
            return Err(GalError::invalid_argument(
                "world LOD unresolved range is not triangle aligned",
            ));
        }
        let resources =
            self.resources_for_submission(instance.column_key, instance.column_generation)?;
        let resources = resources
            .segments
            .get(&instance.segment_index)
            .ok_or_else(|| {
                GalError::backend(format!(
                    "world LOD exact-atlas GPU resources are missing source segment {}",
                    instance.segment_index
                ))
            })?;
        let index_buffer = resources.unresolved_index_buffer.ok_or_else(|| {
            GalError::backend(
                "world LOD unresolved index buffer was not created for a partial exact-atlas segment",
            )
        })?;
        Ok(Some(WorldLodGpuDraw {
            index_buffer,
            index_offset: 0,
            index_type: IndexType::U32,
            index_count,
            ..source_draw
        }))
    }

    pub(crate) fn confirm_submission(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        let Some(created) = self.pending.take() else {
            return Ok(());
        };
        for (column_key, resources) in created {
            if let Some(previous) = self.active.insert(column_key, resources) {
                previous.destroy(gal);
            }
        }
        Ok(())
    }

    pub(crate) fn discard_submission(&mut self, gal: &mut VulkanicGal) {
        if let Some(created) = self.pending.take() {
            for (_, resources) in created {
                resources.destroy(gal);
            }
        }
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodTexturedGpuColumnAsset>,
    ) {
        self.discard_submission(gal);
        let stale = self
            .active
            .iter()
            .filter_map(|(&key, resources)| {
                assets
                    .get(&key)
                    .is_none_or(|asset| asset.column_generation != resources.column_generation)
                    .then_some(key)
            })
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(resources) = self.active.remove(&key) {
                resources.destroy(gal);
            }
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.discard_submission(gal);
        for (_, resources) in std::mem::take(&mut self.active) {
            resources.destroy(gal);
        }
    }

    pub(super) fn resources_for_submission(
        &self,
        column_key: u64,
        column_generation: u64,
    ) -> GalResult<&WorldLodTexturedGpuColumnResources> {
        let resources = self
            .pending
            .as_ref()
            .and_then(|pending| pending.get(&column_key))
            .or_else(|| self.active.get(&column_key))
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                "world LOD exact-atlas column {column_key} has not been staged for GPU submission"
            ))
            })?;
        if resources.column_generation != column_generation {
            return Err(GalError::invalid_argument(format!(
                "world LOD exact-atlas GPU generation {} does not match instance generation {column_generation}",
                resources.column_generation
            )));
        }
        Ok(resources)
    }
}

/// One residency upload transaction: every new column's bytes go into a
/// single staging buffer and are copied into the shared pages with one
/// barrier per written page on each side.
#[derive(Default)]
pub(super) struct WorldLodUploadTransaction {
    payload: Vec<u8>,
    /// (staging offset, page, page offset, bytes, index page?)
    copies: Vec<(u64, Handle, u64, u64, bool)>,
}

impl WorldLodUploadTransaction {
    fn push(&mut self, page: Handle, page_offset: u64, bytes: &[u8], index: bool) {
        self.push_parts(page, page_offset, [bytes], index);
    }

    /// Stages `parts` back to back as one block copied to `page_offset`.
    fn push_parts<'a>(
        &mut self,
        page: Handle,
        page_offset: u64,
        parts: impl IntoIterator<Item = &'a [u8]>,
        index: bool,
    ) {
        let staging_offset = self.payload.len();
        for part in parts {
            self.payload.extend_from_slice(part);
        }
        let bytes = (self.payload.len() - staging_offset) as u64;
        // Keep each staged block four-byte aligned for the copy offsets.
        self.payload.resize(self.payload.len().next_multiple_of(4), 0);
        self.copies.push((staging_offset as u64, page, page_offset, bytes, index));
    }

    fn written_pages(&self) -> Vec<Handle> {
        let mut pages: Vec<Handle> = self.copies.iter().map(|copy| copy.1).collect();
        pages.sort_unstable();
        pages.dedup();
        pages
    }

    /// Creates the staging buffer and the copy ops. A page already written by
    /// a confirmed submission transitions from its read state; a new page
    /// from `Undefined`. Ranges being written are never read by pending work.
    fn finish(
        &mut self,
        gal: &mut VulkanicGal,
        initialized: &BTreeSet<Handle>,
    ) -> GalResult<(Option<Handle>, Vec<CommandOp>)> {
        if self.copies.is_empty() {
            return Ok((None, Vec::new()));
        }
        let staging = gal.create_buffer(BufferDesc {
            label: "world-lod-upload-staging".to_owned(),
            size: self.payload.len() as u64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
        })?;
        let mut ops = Vec::with_capacity(self.copies.len() + 8);
        // Move the payload: it can hold megabytes of prefetched columns.
        ops.push(CommandOp::HostWriteBuffer { buffer: staging, offset: 0, data: std::mem::take(&mut self.payload) });
        ops.push(CommandOp::Barrier(buffer_barrier(
            staging,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )));
        let mut pages: Vec<(Handle, bool)> = self.copies.iter().map(|copy| (copy.1, copy.4)).collect();
        pages.sort_unstable();
        pages.dedup();
        let read_state = |index: bool| if index { TextureUsageState::IndexRead } else { TextureUsageState::ShaderRead };
        for &(page, index) in &pages {
            let before = if initialized.contains(&page) { read_state(index) } else { TextureUsageState::Undefined };
            ops.push(CommandOp::Barrier(buffer_barrier(page, before, TextureUsageState::TransferDst)));
        }
        for &(src_offset, dst, dst_offset, size, _) in &self.copies {
            ops.push(CommandOp::CopyBufferRegion { src: staging, src_offset, dst, dst_offset, size });
        }
        for &(page, index) in &pages {
            ops.push(CommandOp::Barrier(buffer_barrier(page, TextureUsageState::TransferDst, read_state(index))));
        }
        Ok((Some(staging), ops))
    }
}

/// Allocates a column's vertex and index ranges in the shared pages and
/// stages its bytes into `upload`.
pub(super) fn create_column_resources(
    gal: &mut VulkanicGal,
    pages: &mut SourceTerrainGeometryPages,
    asset: &WorldLodGpuColumnAsset,
    upload: &mut WorldLodUploadTransaction,
) -> GalResult<WorldLodGpuColumnResources> {
    let vertex_bytes = asset.segments.iter().try_fold(0u64, |total, segment| {
        total
            .checked_add(segment.vertex_bytes.len() as u64)
            .ok_or_else(|| GalError::invalid_argument("world LOD vertex stream overflow"))
    })?;
    if vertex_bytes == 0 || vertex_bytes > gal.capabilities().limits.max_buffer_size.min(MAX_SHARED_LOD_VERTEX_BYTES) {
        return Err(GalError::unsupported_feature(format!(
            "world LOD column {} vertex stream of {vertex_bytes} bytes does not fit a geometry page",
            asset.column_key
        )));
    }
    // All segments share one index range; a four-byte boundary keeps both
    // U16 and U32 index offsets valid.
    let mut index_bytes = 0u64;
    let mut local_index_offsets = Vec::with_capacity(asset.segments.len());
    for (segment_index, segment) in asset.segments.iter().enumerate() {
        if !segment.upload_payload_is_present() {
            return Err(GalError::invalid_argument(format!(
                "world LOD column {} generation {} segment {segment_index} upload payload was released before GPU residency",
                asset.column_key, asset.column_generation
            )));
        }
        let offset = index_bytes
            .checked_add(3)
            .map(|value| value & !3)
            .ok_or_else(|| GalError::invalid_argument("world LOD index offset overflow"))?;
        index_bytes = offset
            .checked_add(segment.index_bytes.len() as u64)
            .ok_or_else(|| GalError::invalid_argument("world LOD index stream overflow"))?;
        local_index_offsets.push(offset);
    }
    if index_bytes == 0 {
        return Err(GalError::invalid_argument("world LOD column has no index payload"));
    }
    let vertex_range = SourceTerrainGeometryPages::allocate(
        gal,
        &mut pages.vertex_pages,
        vertex_bytes,
        &[BufferUsage::Vertex, BufferUsage::Storage],
        "world-lod.vertices",
        true,
    )?;
    let index_range = match SourceTerrainGeometryPages::allocate(
        gal,
        &mut pages.index_pages,
        index_bytes,
        &[BufferUsage::Index],
        "world-lod.indices",
        true,
    ) {
        Ok(range) => range,
        Err(error) => {
            pages.release_now(vertex_range);
            return Err(error);
        }
    };
    let page_vertex_base = vertex_range.offset / WORLD_LOD_GPU_VERTEX_BYTES as u64;
    let mut index_payload = Vec::with_capacity(index_bytes as usize);
    let mut segments = Vec::with_capacity(asset.segments.len());
    let mut vertex_base = page_vertex_base;
    for (segment, local_index_offset) in asset.segments.iter().zip(local_index_offsets) {
        // The vertex base reaches shaders as an f32 lane: keep it exact.
        debug_assert!(vertex_base < 1 << 24);
        segments.push(WorldLodGpuSegmentResources {
            vertex_buffer: vertex_range.buffer,
            vertex_base: vertex_base as u32,
            index_offset: index_range.offset + local_index_offset,
        });
        vertex_base += segment.vertex_count as u64;
        index_payload.resize(local_index_offset as usize, 0);
        index_payload.extend_from_slice(&segment.index_bytes);
    }
    upload.push_parts(
        vertex_range.buffer,
        vertex_range.offset,
        asset.segments.iter().map(|segment| segment.vertex_bytes.as_slice()),
        false,
    );
    upload.push(index_range.buffer, index_range.offset, &index_payload, true);
    Ok(WorldLodGpuColumnResources {
        column_generation: asset.column_generation,
        segments,
        index_buffer: index_range.buffer,
        vertex_range,
        index_range,
    })
}

pub(super) fn create_textured_column_resources(
    gal: &mut VulkanicGal,
    asset: &WorldLodTexturedGpuColumnAsset,
) -> GalResult<WorldLodTexturedGpuColumnResources> {
    let mut segments = BTreeMap::new();
    let result = (|| -> GalResult<()> {
        for segment in &asset.segments {
            let label = format!(
                "world-lod-exact-atlas-column{}-gen{}-source-segment{}",
                asset.column_key, asset.column_generation, segment.source_segment_index
            );
            let vertex_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.vertices"),
                size: segment.vertex_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Vertex,
                    BufferUsage::Storage,
                    BufferUsage::HostWrite,
                ],
            })?;
            let index_buffer = match gal.create_buffer(BufferDesc {
                label: format!("{label}.indices"),
                size: segment.index_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Index, BufferUsage::HostWrite],
            }) {
                Ok(buffer) => buffer,
                Err(error) => {
                    let _ = gal.retire(vertex_buffer);
                    return Err(error);
                }
            };
            let unresolved_index_buffer = match segment.unresolved_index_bytes.as_ref() {
                Some(bytes) => match gal.create_buffer(BufferDesc {
                    label: format!("{label}.unresolved-indices"),
                    size: bytes.len() as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::Index, BufferUsage::HostWrite],
                }) {
                    Ok(buffer) => Some(buffer),
                    Err(error) => {
                        let _ = gal.retire(index_buffer);
                        let _ = gal.retire(vertex_buffer);
                        return Err(error);
                    }
                },
                None => None,
            };
            segments.insert(
                segment.source_segment_index,
                WorldLodTexturedGpuSegmentResources {
                    vertex_buffer,
                    index_buffer,
                    unresolved_index_buffer,
                },
            );
        }
        Ok(())
    })();
    if let Err(error) = result {
        for (_, segment) in segments.into_iter().rev() {
            if let Some(index_buffer) = segment.unresolved_index_buffer {
                let _ = gal.retire(index_buffer);
            }
            let _ = gal.retire(segment.index_buffer);
            let _ = gal.retire(segment.vertex_buffer);
        }
        return Err(error);
    }
    Ok(WorldLodTexturedGpuColumnResources {
        column_generation: asset.column_generation,
        segments,
    })
}

pub(super) fn textured_upload_ops(
    asset: &WorldLodTexturedGpuColumnAsset,
    resources: &WorldLodTexturedGpuColumnResources,
) -> Vec<CommandOp> {
    let mut ops = Vec::with_capacity(asset.segments.len() * 4);
    for segment in &asset.segments {
        let resources = resources
            .segments
            .get(&segment.source_segment_index)
            .expect("exact-atlas resources are created for every packed source segment");
        ops.push(CommandOp::HostWriteBuffer {
            buffer: resources.vertex_buffer,
            offset: 0,
            data: segment.vertex_bytes.clone(),
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            resources.vertex_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::HostWriteBuffer {
            buffer: resources.index_buffer,
            offset: 0,
            data: segment.index_bytes.clone(),
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            resources.index_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        )));
        if let (Some(bytes), Some(index_buffer)) = (
            segment.unresolved_index_bytes.as_ref(),
            resources.unresolved_index_buffer,
        ) {
            ops.push(CommandOp::HostWriteBuffer {
                buffer: index_buffer,
                offset: 0,
                data: bytes.clone(),
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                index_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::IndexRead,
            )));
        }
    }
    ops
}

pub(super) fn buffer_barrier(
    buffer: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource: buffer,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn texture_barrier(
    texture: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource: texture,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}
