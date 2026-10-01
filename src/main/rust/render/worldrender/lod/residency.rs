//! GPU residency of LOD columns (plain and textured) with prefetch uploads.

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

        let mut created = BTreeMap::new();
        let mut staged_ops = Vec::new();
        let result =
            (|| -> GalResult<()> {
                for column_key in requested {
                    let asset = assets
                        .get(&column_key)
                        .expect("requested columns are validated against the asset map");
                    if self.active.get(&column_key).is_some_and(|resources| {
                        resources.column_generation == asset.column_generation
                    }) {
                        continue;
                    }
                    let resources = create_column_resources(gal, asset)?;
                    staged_ops.extend(upload_ops(asset, &resources));
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
        for (column_key, mut resources) in created {
            // The successful combined submission recorded the staging copies.
            // GAL defers these destroys until that submission completes.
            if let Some(buffer) = resources.shared_vertex_upload_buffer.take() {
                let _ = gal.destroy(buffer);
            }
            if let Some(buffer) = resources.index_upload_buffer.take() {
                let _ = gal.destroy(buffer);
            }
            for segment in &mut resources.segments {
                segment.retire_uploads(gal);
            }
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
        // Pending resources may be referenced by a resolved draw list. A
        // failed submission must retire both together.
        self.visible_draw_cache = None;
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
        for column_key in stale {
            if let Some(resources) = self.active.remove(&column_key) {
                resources.destroy(gal);
            }
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.discard_submission(gal);
        self.visible_draw_cache = None;
        for (_, resources) in std::mem::take(&mut self.active) {
            resources.destroy(gal);
        }
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
                let _ = gal.destroy(index_buffer);
            }
            let _ = gal.destroy(segment.index_buffer);
            let _ = gal.destroy(segment.vertex_buffer);
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

pub(super) fn create_column_resources(
    gal: &mut VulkanicGal,
    asset: &WorldLodGpuColumnAsset,
) -> GalResult<WorldLodGpuColumnResources> {
    let mut segments = Vec::with_capacity(asset.segments.len());
    let mut index_bytes = 0u64;
    let vertex_bytes = asset.segments.iter().try_fold(0u64, |total, segment| {
        total
            .checked_add(segment.vertex_bytes.len() as u64)
            .ok_or_else(|| GalError::invalid_argument("world LOD vertex stream overflow"))
    })?;
    // A single private storage stream removes per-segment GPU allocations and
    // transfer copies. Oversized columns retain the original segment path;
    // no admitted asset can exceed the backend's buffer limit merely because
    // its independent segments are combined here.
    let pack_vertices = vertex_bytes > 0
        && vertex_bytes <= gal.capabilities().limits.max_buffer_size.min(MAX_SHARED_LOD_VERTEX_BYTES);
    let mut shared_vertex_upload_buffer = None;
    let mut shared_vertex_buffer = None;
    let result = (|| -> GalResult<(Handle, Handle)> {
        if pack_vertices {
            let label = format!(
                "world-lod-column{}-gen{}.vertices",
                asset.column_key, asset.column_generation
            );
            let upload = gal.create_buffer(BufferDesc {
                label: format!("{label}-upload"),
                size: vertex_bytes,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
            })?;
            shared_vertex_upload_buffer = Some(upload);
            shared_vertex_buffer = Some(gal.create_buffer(BufferDesc {
                label,
                size: vertex_bytes,
                memory: MemoryDomain::DeviceLocal,
                usages: vec![BufferUsage::Vertex, BufferUsage::Storage, BufferUsage::TransferDst],
            })?);
        }
        let mut vertex_base = 0u32;
        for (segment_index, segment) in asset.segments.iter().enumerate() {
            if !segment.upload_payload_is_present() {
                return Err(GalError::invalid_argument(format!(
                    "world LOD column {} generation {} segment {segment_index} upload payload was released before GPU residency",
                    asset.column_key, asset.column_generation
                )));
            }
            let label = format!(
                "world-lod-column{}-gen{}-segment{segment_index}",
                asset.column_key, asset.column_generation
            );
            // All segments in a column share one immutable index stream. A
            // four-byte boundary keeps both U16 and U32 index offsets valid.
            let index_offset = index_bytes
                .checked_add(3)
                .map(|value| value & !3)
                .ok_or_else(|| GalError::invalid_argument("world LOD index offset overflow"))?;
            index_bytes = index_offset
                .checked_add(segment.index_bytes.len() as u64)
                .ok_or_else(|| GalError::invalid_argument("world LOD index stream overflow"))?;
            let segment_vertex_base = if pack_vertices { vertex_base } else { 0 };
            if pack_vertices {
                vertex_base = vertex_base
                    .checked_add(segment.vertex_count)
                    .ok_or_else(|| GalError::invalid_argument("world LOD vertex base overflow"))?;
            }
            if let Some(vertex_buffer) = shared_vertex_buffer {
                segments.push(WorldLodGpuSegmentResources {
                    vertex_buffer,
                    vertex_upload_buffer: None,
                    vertex_base: segment_vertex_base,
                    index_offset,
                });
                continue;
            }
            // Oversized columns keep the original device-local segment path.
            let created_segment = (|| -> GalResult<WorldLodGpuSegmentResources> {
                let vertex_upload_buffer = gal.create_buffer(BufferDesc {
                    label: format!("{label}.vertices-upload"),
                    size: segment.vertex_bytes.len() as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
                })?;
                let vertex_buffer = match gal.create_buffer(BufferDesc {
                    label: format!("{label}.vertices"),
                    size: segment.vertex_bytes.len() as u64,
                    memory: MemoryDomain::DeviceLocal,
                    usages: vec![
                        BufferUsage::Vertex,
                        BufferUsage::Storage,
                        BufferUsage::TransferDst,
                    ],
                }) {
                    Ok(buffer) => buffer,
                    Err(error) => {
                        let _ = gal.destroy(vertex_upload_buffer);
                        return Err(error);
                    }
                };
                Ok(WorldLodGpuSegmentResources {
                    vertex_buffer,
                    vertex_upload_buffer: Some(vertex_upload_buffer),
                    vertex_base: 0,
                    index_offset,
                })
            })();
            match created_segment {
                Ok(resources) => segments.push(resources),
                Err(error) => return Err(error),
            }
        }
        if index_bytes == 0 {
            return Err(GalError::invalid_argument(
                "world LOD column has no index payload",
            ));
        }
        let label = format!(
            "world-lod-column{}-gen{}.indices",
            asset.column_key, asset.column_generation
        );
        let index_upload_buffer = gal.create_buffer(BufferDesc {
            label: format!("{label}-upload"),
            size: index_bytes,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
        })?;
        let index_buffer = match gal.create_buffer(BufferDesc {
            label,
            size: index_bytes,
            memory: MemoryDomain::DeviceLocal,
            usages: vec![BufferUsage::Index, BufferUsage::TransferDst],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.destroy(index_upload_buffer);
                return Err(error);
            }
        };
        Ok((index_upload_buffer, index_buffer))
    })();
    let (index_upload_buffer, index_buffer) = match result {
        Ok(handles) => handles,
        Err(error) => {
            for segment in segments.into_iter().rev() {
                if shared_vertex_buffer.is_none() {
                    segment.destroy(gal);
                }
            }
            if let Some(buffer) = shared_vertex_buffer {
                let _ = gal.destroy(buffer);
            }
            if let Some(buffer) = shared_vertex_upload_buffer {
                let _ = gal.destroy(buffer);
            }
            return Err(error);
        }
    };
    Ok(WorldLodGpuColumnResources {
        column_generation: asset.column_generation,
        segments,
        shared_vertex_buffer,
        shared_vertex_upload_buffer,
        index_buffer,
        index_upload_buffer: Some(index_upload_buffer),
    })
}

pub(super) fn upload_ops(
    asset: &WorldLodGpuColumnAsset,
    resources: &WorldLodGpuColumnResources,
) -> Vec<CommandOp> {
    debug_assert_eq!(asset.segments.len(), resources.segments.len());
    let mut ops = Vec::with_capacity(asset.segments.len() * 5 + 5);
    let mut index_payload = Vec::new();
    if let (Some(vertex_buffer), Some(vertex_upload_buffer)) = (
        resources.shared_vertex_buffer,
        resources.shared_vertex_upload_buffer,
    ) {
        let mut vertex_payload = Vec::with_capacity(
            usize::try_from(asset.segments.iter().map(|segment| segment.vertex_bytes.len() as u64).sum::<u64>())
                .expect("validated LOD vertex stream fits host address space"),
        );
        for segment in &asset.segments {
            vertex_payload.extend_from_slice(&segment.vertex_bytes);
        }
        let size = vertex_payload.len() as u64;
        ops.push(CommandOp::HostWriteBuffer {
            buffer: vertex_upload_buffer,
            offset: 0,
            data: vertex_payload,
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            vertex_upload_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::Barrier(buffer_barrier(
            vertex_buffer,
            TextureUsageState::Undefined,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::CopyBuffer {
            src: vertex_upload_buffer,
            dst: vertex_buffer,
            size,
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            vertex_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
    }
    for (segment, resources) in asset.segments.iter().zip(&resources.segments) {
        if let Some(vertex_upload_buffer) = resources.vertex_upload_buffer {
            ops.push(CommandOp::HostWriteBuffer {
                buffer: vertex_upload_buffer,
                offset: 0,
                data: segment.vertex_bytes.clone(),
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                vertex_upload_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )));
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.vertex_buffer,
                TextureUsageState::Undefined,
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::CopyBuffer {
                src: vertex_upload_buffer,
                dst: resources.vertex_buffer,
                size: segment.vertex_bytes.len() as u64,
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.vertex_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        let index_offset = usize::try_from(resources.index_offset)
            .expect("validated LOD index stream fits host address space");
        debug_assert!(index_payload.len() <= index_offset);
        index_payload.resize(index_offset, 0);
        index_payload.extend_from_slice(&segment.index_bytes);
    }
    let index_upload_buffer = resources
        .index_upload_buffer
        .expect("new LOD column retains index staging until submission");
    let index_payload_bytes = index_payload.len() as u64;
    ops.push(CommandOp::HostWriteBuffer {
        buffer: index_upload_buffer,
        offset: 0,
        data: index_payload,
    });
    ops.push(CommandOp::Barrier(buffer_barrier(
        index_upload_buffer,
        TextureUsageState::TransferDst,
        TextureUsageState::TransferSrc,
    )));
    ops.push(CommandOp::Barrier(buffer_barrier(
        resources.index_buffer,
        TextureUsageState::Undefined,
        TextureUsageState::TransferDst,
    )));
    ops.push(CommandOp::CopyBuffer {
        src: index_upload_buffer,
        dst: resources.index_buffer,
        size: index_payload_bytes,
    });
    ops.push(CommandOp::Barrier(buffer_barrier(
        resources.index_buffer,
        TextureUsageState::TransferDst,
        TextureUsageState::IndexRead,
    )));
    ops
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
                    let _ = gal.destroy(vertex_buffer);
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
                        let _ = gal.destroy(index_buffer);
                        let _ = gal.destroy(vertex_buffer);
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
                let _ = gal.destroy(index_buffer);
            }
            let _ = gal.destroy(segment.index_buffer);
            let _ = gal.destroy(segment.vertex_buffer);
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
