//! Mesh/material/crack/border asset stores and updates, texture decoding, mip chains and texture uploads.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) struct MeshAssetStore {
    pub(in crate::render::worldrender) translucent_order: std::cell::RefCell<Option<geometry::translucent_order::CachedOrder>>,
    /// Coalesced section ranges are immutable for one copied mesh generation.
    /// Keep a small policy-keyed cache on the asset so streamed terrain does
    /// not rebuild the same section walk for every frame or every visible
    /// instance.  The cache contains semantic ranges only; it owns no GAL
    /// handles and is discarded with the asset generation.
    pub(in crate::render::worldrender) section_ranges_cache:
        std::cell::RefCell<Vec<(MeshSectionRangeCacheKey, Arc<Vec<MeshSectionRange>>)>>,
    /// `texture_animation_signature` for the frontend's texture-animation
    /// generation it was computed under (it walks every section).
    pub(in crate::render::worldrender) texture_animation_signature_cache: std::cell::Cell<Option<(u64, u64)>>,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) index_generation: u64,
    pub(in crate::render::worldrender) vertex_layout_version: u32,
    pub(in crate::render::worldrender) vertex_bytes: Vec<u8>,
    /// Original packed normals for native decal emission; glint assets only.
    pub(in crate::render::worldrender) decal_normals: Option<Vec<u32>>,
    /// Immutable copied source input retained until an admitted source frame
    /// actually selects this exact mesh generation. World streaming can add
    /// hundreds of off-screen sections in one update; eagerly expanding each
    /// into the shader-pack ABI turns ordinary asset registration into source
    /// rendering work on the render thread.
    pub(in crate::render::worldrender) source_input: Option<SourceMeshSemanticInput>,
    /// Canonical entity identity stored once alongside the ordinary mesh
    /// metadata. Source entity lowering borrows it through `source_view`;
    /// retaining it inside a second cloned asset used to duplicate both this
    /// string and the complete index/section payload.
    pub(in crate::render::worldrender) entity_identity: String,
    pub(in crate::render::worldrender) terrain_voxel_vertices: Option<Arc<Vec<TerrainVoxelSourceVertex>>>,
    pub(in crate::render::worldrender) terrain_voxel_indices: Option<Arc<Vec<u32>>>,
    pub(in crate::render::worldrender) terrain_voxel_translucent_indices: Option<Arc<Vec<u32>>>,
    /// Model-space bounds of the voxel block centres (vertex + mid-block).
    pub(in crate::render::worldrender) terrain_voxel_model_bounds: Option<Option<[[f32; 3]; 2]>>,
    pub(in crate::render::worldrender) index_bytes: Vec<u8>,
    pub(in crate::render::worldrender) index_type: IndexType,
    pub(in crate::render::worldrender) sections: Vec<WorldMeshSection>,
}

impl Default for MeshAssetStore {
    fn default() -> Self {
        Self {
            translucent_order: Default::default(),
            section_ranges_cache: Default::default(),
            texture_animation_signature_cache: Default::default(),
            mesh_generation: 0,
            index_generation: 0,
            vertex_layout_version: 0,
            vertex_bytes: Vec::new(),
            decal_normals: None,
            source_input: None,
            entity_identity: String::new(),
            terrain_voxel_vertices: None,
            terrain_voxel_indices: None,
            terrain_voxel_translucent_indices: None,
            terrain_voxel_model_bounds: None,
            index_bytes: Vec::new(),
            index_type: IndexType::U16,
            sections: Vec::new(),
        }
    }
}

impl MeshAssetStore {
    pub(in crate::render::worldrender) fn compatible_section_ranges(
        &self,
        instance: &WorldMeshInstanceRequest,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
        g_buffer: bool,
        texture_animated: bool,
    ) -> GalResult<Arc<Vec<MeshSectionRange>>> {
        self.compatible_section_ranges_per_texture(
            instance,
            color_format,
            raster_y_direction,
            g_buffer,
            None,
            |_| texture_animated,
        )
    }

    /// Expands a whole mesh while preserving the immutable animation class of
    /// each section. A mesh can contain a static stone range next to an
    /// animated water range; classifying the complete mesh from one `any()`
    /// result needlessly routes both through the animated fragment program.
    /// The cache key includes the complete section texture/class signature so
    /// a texture-only animation update cannot reuse stale classification.
    /// `animation_generation` identifies the frontend's texture-animation
    /// state (bumped whenever texture assets change); when given, the section
    /// signature is memoized for it instead of re-walked per instance.
    pub(in crate::render::worldrender) fn compatible_section_ranges_per_texture(
        &self,
        instance: &WorldMeshInstanceRequest,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
        g_buffer: bool,
        animation_generation: Option<u64>,
        texture_is_animated: impl Fn(u32) -> bool,
    ) -> GalResult<Arc<Vec<MeshSectionRange>>> {
        let texture_animation_signature = match animation_generation {
            Some(generation) => match self.texture_animation_signature_cache.get() {
                Some((cached_generation, signature)) if cached_generation == generation => {
                    signature
                }
                _ => {
                    let signature = texture_animation_signature(self, &texture_is_animated);
                    self.texture_animation_signature_cache
                        .set(Some((generation, signature)));
                    signature
                }
            },
            None => texture_animation_signature(self, &texture_is_animated),
        };
        let key = MeshSectionRangeCacheKey {
            stratum: instance.stratum,
            depth_policy: instance.depth_policy,
            terrain_visible_facing_mask: instance.terrain_visible_facing_mask,
            standard_item_foil: instance.item_foil.is_some(),
            color_format,
            raster_y_direction,
            g_buffer,
            view_layering: mesh_view_layering(instance),
            decal_vertex_count: if instance.decal_foil.is_some() {
                self.vertex_bytes.len() / WORLD_MESH_GPU_VERTEX_BYTES
            } else {
                0
            },
            texture_animation_signature,
        };
        if let Some((_, ranges)) = self
            .section_ranges_cache
            .borrow()
            .iter()
            .find(|(cached_key, _)| *cached_key == key)
        {
            return Ok(Arc::clone(ranges));
        }
        let ranges = Arc::new(compatible_mesh_section_ranges_per_texture(
            instance,
            self,
            color_format,
            raster_y_direction,
            g_buffer,
            texture_is_animated,
        )?);
        let mut cache = self.section_ranges_cache.borrow_mut();
        // A mesh normally uses one direct and one G-buffer policy. Keep the
        // bound defensive for unusual model/foil combinations instead of
        // allowing an unbounded policy stream to grow with a long session.
        const MAX_SECTION_RANGE_POLICIES: usize = 8;
        if cache.len() >= MAX_SECTION_RANGE_POLICIES {
            cache.remove(0);
        }
        cache.push((key, Arc::clone(&ranges)));
        Ok(ranges)
    }

    pub(in crate::render::worldrender) fn source_terrain_view(
        &self,
        mesh_key: u64,
    ) -> Option<SourceMeshAssetView<'_, WorldMeshVertex>> {
        let SourceMeshSemanticInput::Terrain(vertices) = self.source_input.as_ref()? else {
            return None;
        };
        Some(SourceMeshAssetView {
            mesh_key,
            mesh_generation: self.mesh_generation,
            vertices,
            index_bytes: &self.index_bytes,
            index_type: self.index_type,
            sections: &self.sections,
            entity_identity: &self.entity_identity,
        })
    }

    pub(in crate::render::worldrender) fn source_entity_view(
        &self,
        mesh_key: u64,
    ) -> Option<SourceMeshAssetView<'_, SourceEntitySemanticVertex>> {
        let SourceMeshSemanticInput::Entity(vertices) = self.source_input.as_ref()? else {
            return None;
        };
        Some(SourceMeshAssetView {
            mesh_key,
            mesh_generation: self.mesh_generation,
            vertices,
            index_bytes: &self.index_bytes,
            index_type: self.index_type,
            sections: &self.sections,
            entity_identity: &self.entity_identity,
        })
    }
}

impl WorldPrimitiveFrontend {
    /// Installs copied font-atlas data independently from frame traffic. This
    /// retains no caller memory and cannot make world text renderable until a
    /// later Rust-owned pass explicitly consumes it.
    pub(crate) fn apply_world_text_image_update(
        &mut self,
        generation: u64,
        assets: Vec<features::world_text::WorldTextImageAsset>,
    ) -> GalResult<()> {
        self.world_text.apply_image_update(generation, assets)
    }

    /// Copies one binary shader-pack asset generation only after its matching
    /// semantic source generation is active. No texture decode, backend
    /// upload, native handle, or route selection occurs here.
    pub(crate) fn apply_shader_pack_asset_update(
        &mut self,
        update: ShaderPackAssetUpdate,
    ) -> GalResult<()> {
        self.post_effect_source_cache.get_mut().clear();
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument(
                "shader-pack assets require an active matching source generation",
            )
        })?;
        if source.name() != update.pack_name || source.generation() != update.generation {
            return Err(GalError::invalid_argument(format!(
                "shader-pack asset update '{}'/{} does not match active source '{}'/{}",
                update.pack_name,
                update.generation,
                source.name(),
                source.generation()
            )));
        }
        // Admit the copied post-effect graph only when every definition can
        // resolve against this exact source generation. This keeps resource
        // reloads Rust-owned and prevents a malformed/custom chain from
        // silently reopening Java's post-processing route.
        let candidate_assets = ShaderPackAssets::new(update.clone())?;
        candidate_assets.validate_post_effect_contracts(source)?;
        self.shader_pack_assets.apply_update(update)?;
        // Pack assets participate in the semantic source set even though
        // their Rust-owned GPU wrappers are refreshed lazily. Do not let a
        // later executor observe the previous frame's wrapper after a new
        // copied asset snapshot has become authoritative.
        self.clear_candidate_source_resource_snapshot();
        self.reset_candidate_source_occupancy_stability();
        self.source_execution_armed = false;
        self.source_execution_activation_reported = false;
        self.source_execution_distant_horizons_reported = false;
        Ok(())
    }

    /// Exposes the immutable asset snapshot only when it belongs to the
    /// active source. A stale snapshot is deliberately indistinguishable from
    /// absent assets to downstream source-resource preparation.
    pub(crate) fn active_shader_pack_assets(&self) -> GalResult<&ShaderPackAssets> {
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument("shader-pack source has not been provided")
        })?;
        self.shader_pack_assets
            .active_for_source(source.name(), source.generation())
    }
}

#[derive(Clone, Debug)]
pub(in crate::render::worldrender) struct WorldBorderTextureAsset {
    pub(in crate::render::worldrender) rgba: Vec<u8>,
    pub(in crate::render::worldrender) width: u32,
    pub(in crate::render::worldrender) height: u32,
}

#[derive(Clone, Debug)]
pub(in crate::render::worldrender) struct WorldCrackTextureAsset {
    pub(in crate::render::worldrender) rgba: Vec<u8>,
}

#[derive(Clone, Debug)]
pub(in crate::render::worldrender) struct WorldMaterialTextureAsset {
    /// Bounded diagnostic encoding; dropped with this owned CPU incarnation.
    pub(in crate::render::worldrender) equipment_capture_png: std::sync::OnceLock<String>,
    pub(in crate::render::worldrender) rgba: Vec<u8>,
    /// Exact copied levels excluding mip zero. An empty chain requests the
    /// ordinary GAL-generated chain for assets without sprite-isolated mips.
    pub(in crate::render::worldrender) mip_rgba: Vec<Vec<u8>>,
    pub(in crate::render::worldrender) width: u32,
    pub(in crate::render::worldrender) height: u32,
    pub(in crate::render::worldrender) frame_width: u32,
    pub(in crate::render::worldrender) frame_height: u32,
    pub(in crate::render::worldrender) frame_count: u32,
    pub(in crate::render::worldrender) animation_flags: u32,
    pub(in crate::render::worldrender) interpolation_policy: u32,
    pub(in crate::render::worldrender) animation_frames: Vec<MeshTextureAnimationFrame>,
    pub(in crate::render::worldrender) animation_total_ticks: u32,
    pub(in crate::render::worldrender) animation_generation: u64,
    pub(in crate::render::worldrender) coordinate_origin: WorldMeshTextureCoordinateOrigin,
    pub(in crate::render::worldrender) sampling: Option<crate::render::shared::texture_sampling::TextureSampling>,
    pub(in crate::render::worldrender) requested_mip_levels: u32,
}

/// Semantic source-image convention for copied texture assets. Minecraft atlas
/// PNG rows use a top-left origin while the stable Rust mesh UV contract uses
/// VulkanicGAL's canonical sampled-image row order. The frontend performs this
/// one semantic conversion before either backend owns a texture resource.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) enum WorldMeshTextureCoordinateOrigin {
    Vulkanic = 0,
    MinecraftTopLeft = 1,
}

impl TryFrom<u32> for WorldMeshTextureCoordinateOrigin {
    type Error = GalError;

    fn try_from(value: u32) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(Self::Vulkanic),
            1 => Ok(Self::MinecraftTopLeft),
            value => Err(GalError::invalid_argument(format!(
                "unknown world mesh texture coordinate origin {value}"
            ))),
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub(in crate::render::worldrender) struct MeshTextureAnimationFrame {
    pub(in crate::render::worldrender) duration_ticks: u32,
    pub(in crate::render::worldrender) region: [f32; 4],
}

#[derive(Clone, Copy, Debug)]
pub(in crate::render::worldrender) struct MeshTextureAnimationSample {
    pub(in crate::render::worldrender) animated: bool,
    pub(in crate::render::worldrender) current_region: [f32; 4],
    pub(in crate::render::worldrender) next_region: [f32; 4],
    pub(in crate::render::worldrender) interpolation: f32,
}

impl Default for MeshTextureAnimationSample {
    fn default() -> Self {
        Self {
            animated: false,
            current_region: [0.0, 0.0, 1.0, 1.0],
            next_region: [0.0, 0.0, 1.0, 1.0],
            interpolation: 0.0,
        }
    }
}

pub(in crate::render::worldrender) fn sample_mesh_texture_animation(
    frames: &[MeshTextureAnimationFrame],
    total_ticks: u32,
    interpolation_policy: u32,
    frame_id: u64,
) -> MeshTextureAnimationSample {
    if frames.len() <= 1 {
        return MeshTextureAnimationSample {
            ..MeshTextureAnimationSample::default()
        };
    }
    let tick = (frame_id % u64::from(total_ticks.max(1))) as u32;
    let mut cursor = 0u32;
    let mut selected = 0usize;
    for (index, frame) in frames.iter().enumerate() {
        let end = cursor.saturating_add(frame.duration_ticks.max(1));
        if tick < end {
            selected = index;
            break;
        }
        cursor = end;
    }
    let current = frames[selected];
    let next = frames[(selected + 1) % frames.len()];
    let elapsed = tick.saturating_sub(cursor);
    let interpolation = if interpolation_policy == WORLD_MESH_ANIMATION_INTERPOLATE_LINEAR {
        elapsed as f32 / current.duration_ticks.max(1) as f32
    } else {
        0.0
    };
    MeshTextureAnimationSample {
        animated: true,
        current_region: current.region,
        next_region: next.region,
        interpolation,
    }
}

impl WorldPrimitiveFrontend {
    pub fn apply_world_border_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payload: WorldBorderAssetPayload,
    ) -> GalResult<()> {
        let result = self.apply_world_border_asset_update_inner(gal, generation, payload);
        if result.is_err() {
            self.border_asset_update_failures = self.border_asset_update_failures.saturating_add(1);
        }
        result
    }

    pub(in crate::render::worldrender) fn apply_world_border_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payload: WorldBorderAssetPayload,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world-border asset generation must be non-zero",
            ));
        }
        if generation <= self.border_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world-border asset generation {generation}; current generation is {}",
                    self.border_asset_generation
                ),
            ));
        }
        if payload.texture_id != WORLD_BORDER_TEXTURE_FORCEFIELD {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world-border texture id {}", payload.texture_id),
            ));
        }
        let replacement = if payload.png_bytes.is_empty() {
            let _ = forcefield_texture_bytes()?;
            None
        } else {
            let (rgba, width, height) =
                decode_png_rgba(&payload.png_bytes, "world border forcefield override")?;
            Some(WorldBorderTextureAsset {
                rgba,
                width,
                height,
            })
        };
        self.border_asset_generation = generation;
        self.border_asset_payload_bytes = payload.png_bytes.len() as u64;
        self.border_asset_override = replacement;
        self.destroy_border_resources(gal);
        Ok(())
    }

    pub fn apply_world_crack_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldCrackAssetPayload>,
    ) -> GalResult<()> {
        let result = self.apply_world_crack_asset_update_inner(gal, generation, payloads);
        if result.is_err() {
            self.crack_asset_update_failures = self.crack_asset_update_failures.saturating_add(1);
        }
        result
    }

    pub(in crate::render::worldrender) fn apply_world_crack_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldCrackAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world crack asset generation must be non-zero",
            ));
        }
        if generation <= self.crack_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world crack asset generation {generation}; current generation is {}",
                    self.crack_asset_generation
                ),
            ));
        }
        let mut overrides = BTreeMap::new();
        let mut payload_bytes = 0u64;
        for payload in payloads {
            if payload.stage >= CRACK_STAGE_COUNT {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world crack stage {}", payload.stage),
                ));
            }
            if overrides.contains_key(&payload.stage) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate world crack asset payload for stage {}",
                        payload.stage
                    ),
                ));
            }
            if payload.png_bytes.is_empty() {
                continue;
            }
            payload_bytes = payload_bytes.saturating_add(payload.png_bytes.len() as u64);
            let (rgba, width, height) = decode_png_rgba(
                &payload.png_bytes,
                &format!("world crack destroy_stage_{} override", payload.stage),
            )?;
            if width != CRACK_STAGE_SIZE || height != CRACK_STAGE_SIZE {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "world crack destroy_stage_{} must be {}x{}, got {}x{}",
                        payload.stage, CRACK_STAGE_SIZE, CRACK_STAGE_SIZE, width, height
                    ),
                ));
            }
            overrides.insert(payload.stage, WorldCrackTextureAsset { rgba });
        }
        self.crack_asset_generation = generation;
        self.crack_asset_payload_bytes = payload_bytes;
        self.crack_asset_overrides = overrides;
        self.destroy_crack_resources(gal);
        Ok(())
    }

    pub fn apply_world_material_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldMaterialAssetPayload>,
    ) -> GalResult<()> {
        let result = self.apply_world_material_asset_update_inner(gal, generation, payloads);
        if result.is_err() {
            self.material_asset_update_failures =
                self.material_asset_update_failures.saturating_add(1);
        }
        result
    }

    pub(in crate::render::worldrender) fn apply_world_material_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldMaterialAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world material asset generation must be non-zero",
            ));
        }
        if generation <= self.material_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world material asset generation {generation}; current generation is {}",
                    self.material_asset_generation
                ),
            ));
        }
        let mut overrides = BTreeMap::new();
        let mut payload_bytes = 0u64;
        for payload in payloads {
            let texture_id =
                frame::material_quads::canonical_texture_id(payload.texture_id).ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::UnknownEnum,
                        format!("unknown world material texture id {}", payload.texture_id),
                    )
                })?;
            if overrides.contains_key(&texture_id) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate world material asset payload for texture {}",
                        texture_id
                    ),
                ));
            }
            if payload.png_bytes.is_empty() {
                continue;
            }
            payload_bytes = payload_bytes.saturating_add(payload.png_bytes.len() as u64);
            let (rgba, width, height) = decode_png_rgba(
                &payload.png_bytes,
                &format!("world material texture {} override", texture_id),
            )?;
            overrides.insert(
                texture_id,
                WorldMaterialTextureAsset {
                    equipment_capture_png: std::sync::OnceLock::new(),
                    rgba,
                    mip_rgba: Vec::new(),
                    width,
                    height,
                    frame_width: width,
                    frame_height: height,
                    frame_count: 1,
                    animation_flags: 0,
                    interpolation_policy: WORLD_MESH_ANIMATION_INTERPOLATE_NONE,
                    animation_frames: vec![MeshTextureAnimationFrame {
                        duration_ticks: 1,
                        region: [0.0, 0.0, 1.0, 1.0],
                    }],
                    animation_total_ticks: 1,
                    animation_generation: generation,
                    coordinate_origin: WorldMeshTextureCoordinateOrigin::Vulkanic,
                    sampling: None,
                    requested_mip_levels: 0,
                },
            );
        }
        self.material_asset_generation = generation;
        self.material_asset_payload_bytes = payload_bytes;
        self.material_asset_overrides = overrides;
        // A source frame may have staged a first-use local texture upload but
        // not yet reached its single combined submission. Its old-generation
        // handles must never be appended after this atomic asset replacement.
        self.discard_all_unsubmitted_source_terrain_frames(gal);
        self.destroy_lowered_entity_source_pack_resources(gal);
        self.destroy_lowered_textured_material_source_local_texture_resources(gal);
        self.destroy_material_resources(gal);
        Ok(())
    }

    pub fn apply_world_mesh_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        meshes: Vec<WorldMeshAsset>,
        textures: Vec<WorldMeshTextureAssetPayload>,
    ) -> GalResult<()> {
        self.apply_world_mesh_asset_update_with_sorted(
            gal,
            generation,
            meshes,
            textures,
            Vec::new(),
        )
    }

    pub fn apply_world_mesh_asset_update_with_sorted(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        meshes: Vec<WorldMeshAsset>,
        textures: Vec<WorldMeshTextureAssetPayload>,
        sorted_indices: Vec<WorldMeshSortedIndexUpdate>,
    ) -> GalResult<()> {
        self.apply_world_mesh_asset_update_with_sorted_and_retirements(
            gal,
            generation,
            meshes,
            textures,
            sorted_indices,
            Vec::new(),
        )
    }

    /// Applies additions/replacements and explicit producer-owned retirements
    /// in one generation. Retirements are generation-guarded so a delayed CPU
    /// window eviction can never destroy a newer mesh replacement.
    pub fn apply_world_mesh_asset_update_with_sorted_and_retirements(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        meshes: Vec<WorldMeshAsset>,
        textures: Vec<WorldMeshTextureAssetPayload>,
        sorted_indices: Vec<WorldMeshSortedIndexUpdate>,
        retirements: Vec<(u64, u64)>,
    ) -> GalResult<()> {
        let result = self.apply_world_mesh_asset_update_inner(
            gal,
            generation,
            meshes,
            textures,
            sorted_indices,
            retirements,
        );
        if result.is_err() {
            self.mesh_asset_update_failures = self.mesh_asset_update_failures.saturating_add(1);
        }
        result
    }

    pub(in crate::render::worldrender) fn apply_world_mesh_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        meshes: Vec<WorldMeshAsset>,
        textures: Vec<WorldMeshTextureAssetPayload>,
        sorted_indices: Vec<WorldMeshSortedIndexUpdate>,
        retirements: Vec<(u64, u64)>,
    ) -> GalResult<()> {
        // Complete the previous replacement boundary before staging another
        // generation.  GAL itself defers destruction for resources already
        // marked by an in-flight submission; this drain only makes the old
        // handles eligible after the prior frame's validation boundary.
        self.flush_deferred_mesh_resource_destroys(gal);
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world mesh asset generation must be non-zero",
            ));
        }
        if generation <= self.mesh_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world mesh asset generation {generation}; current generation is {}",
                    self.mesh_asset_generation
                ),
            ));
        }
        // Source vertices are required only while a copied Rust-owned source
        // pack is actually eligible for selection.  Keeping them while the
        // ordinary route is active duplicates every streamed terrain vertex
        // for a route that cannot execute.  A later pack/resource reload
        // publishes a fresh semantic generation; until then source lowering
        // reports unavailable and never falls back to Java rendering.
        #[cfg(not(test))]
        let retain_source_semantics =
            Self::should_retain_source_semantics(self.source_execution_enabled());
        // Unit fixtures exercise source lowering independently from runtime
        // source selection, so retain their explicit test payloads.
        #[cfg(test)]
        let retain_source_semantics = true;
        let retirement_keys = retirements
            .into_iter()
            .filter_map(|(mesh_key, mesh_generation)| {
                self.mesh_assets
                    .get(&mesh_key)
                    .filter(|asset| asset.mesh_generation == mesh_generation)
                    .map(|_| mesh_key)
            })
            .collect::<BTreeSet<_>>();
        // Remove generation-matched assets before checking projected residency.
        // Their dependent explicit buffers are completion-retired below, while
        // textures remain independently owned and may still serve other meshes.
        let retired_resource_keys = self
            .mesh_resources
            .keys()
            .copied()
            .filter(|key| retirement_keys.contains(&key.mesh_key))
            .collect::<Vec<_>>();
        let retired_lowered_source_keys = self
            .lowered_source_terrain_geometry_resources
            .keys()
            .filter(|key| retirement_keys.contains(&key.mesh_key))
            .cloned()
            .collect::<Vec<_>>();
        let mut incoming_texture_ids = BTreeSet::new();
        if textures.len() > WORLD_MAX_MESH_TEXTURE_ASSETS {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh texture asset count {} exceeds {}",
                    textures.len(),
                    WORLD_MAX_MESH_TEXTURE_ASSETS
                ),
            ));
        }
        // Empty texture descriptors carry the current semantic IDs alongside an
        // incremental mesh update; they do not replace the already-uploaded
        // texture payload. Keep replacement tracking distinct so that a normal
        // terrain mesh stream cannot invalidate every cached resource set on
        // every frame.
        let mut replaced_texture_ids = BTreeSet::new();
        let mut decoded_textures = Vec::with_capacity(textures.len());
        let mut payload_bytes = 0u64;
        let mut decoded_texture_bytes = 0usize;
        for payload in textures {
            if payload.texture_id == 0 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world mesh texture id must be non-zero",
                ));
            }
            if !incoming_texture_ids.insert(payload.texture_id) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!("duplicate world mesh texture {}", payload.texture_id),
                ));
            }
            if payload.png_bytes.is_empty() {
                continue;
            }
            replaced_texture_ids.insert(payload.texture_id);
            payload_bytes = payload_bytes.saturating_add(payload.png_bytes.len() as u64);
            let coordinate_origin =
                WorldMeshTextureCoordinateOrigin::try_from(payload.coordinate_origin)?;
            let (rgba, width, height) = decode_png_rgba(
                &payload.png_bytes,
                &format!("world mesh texture {}", payload.texture_id),
            )?;
            let mut mip_rgba = Vec::with_capacity(payload.mip_png_bytes.len());
            if payload.mip_png_bytes.len() as u32 + 1 > texture_mip_level_count(width, height) {
                return Err(GalError::invalid_argument(format!(
                    "world mesh texture {} supplies {} mip levels for a {}x{} image with at most {}",
                    payload.texture_id,
                    payload.mip_png_bytes.len() + 1,
                    width,
                    height,
                    texture_mip_level_count(width, height),
                )));
            }
            for (mip_index, png_bytes) in payload.mip_png_bytes.iter().enumerate() {
                let mip = mip_index as u32 + 1;
                let (mip_pixels, mip_width, mip_height) = decode_png_rgba(
                    png_bytes,
                    &format!("world mesh texture {} mip {}", payload.texture_id, mip),
                )?;
                let expected_width = (width >> mip).max(1);
                let expected_height = (height >> mip).max(1);
                if mip_width != expected_width || mip_height != expected_height {
                    return Err(GalError::invalid_argument(format!(
                        "world mesh texture {} mip {} is {}x{}; expected {}x{}",
                        payload.texture_id,
                        mip,
                        mip_width,
                        mip_height,
                        expected_width,
                        expected_height
                    )));
                }
                decoded_texture_bytes = decoded_texture_bytes
                    .checked_add(mip_pixels.len())
                    .ok_or_else(|| {
                        GalError::invalid_argument("world mesh decoded texture mip bytes overflow")
                    })?;
                mip_rgba.push(mip_pixels);
            }
            decoded_texture_bytes =
                decoded_texture_bytes
                    .checked_add(rgba.len())
                    .ok_or_else(|| {
                        GalError::invalid_argument("world mesh decoded texture bytes overflow")
                    })?;
            if decoded_texture_bytes > WORLD_MAX_MESH_TEXTURE_DECODED_BYTES {
                return Err(GalError::invalid_argument(format!(
                    "world mesh decoded textures use {} bytes; maximum is {}",
                    decoded_texture_bytes, WORLD_MAX_MESH_TEXTURE_DECODED_BYTES
                )));
            }
            let frame_width = normalized_frame_extent(payload.frame_width, width)?;
            let frame_height = normalized_frame_extent(payload.frame_height, height)?;
            if payload.requested_mip_levels > texture_mip_level_count(frame_width, frame_height) {
                return Err(GalError::invalid_argument(
                    "explicit texture mip count exceeds a sprite frame extent",
                ));
            }
            requested_mesh_texture_mip_levels(
                payload.texture_id,
                mip_rgba.len() + 1,
                width,
                height,
                payload.requested_mip_levels,
            )?;
            let frame_ticks = normalized_frame_ticks(payload.frame_ticks)?;
            if payload.animation_flags != 0 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "world mesh texture {} uses unsupported animation flags {}",
                        payload.texture_id, payload.animation_flags
                    ),
                ));
            }
            let frame_row_size =
                normalized_frame_row_size(width, frame_width, payload.frame_row_size)?;
            let (animation_frames, animation_total_ticks) = decoded_animation_frames(
                width,
                height,
                frame_width,
                frame_height,
                frame_row_size,
                payload.frame_count,
                frame_ticks,
                &payload.animation_frames,
                payload.interpolation_policy,
                payload.texture_id,
            )?;
            let frame_count = animation_frames.len() as u32;
            decoded_textures.push((
                payload.texture_id,
                WorldMaterialTextureAsset {
                    equipment_capture_png: std::sync::OnceLock::new(),
                    rgba,
                    mip_rgba,
                    width,
                    height,
                    frame_width,
                    frame_height,
                    frame_count,
                    animation_flags: payload.animation_flags,
                    interpolation_policy: payload.interpolation_policy,
                    animation_frames,
                    animation_total_ticks,
                    animation_generation: generation,
                    coordinate_origin,
                    sampling: payload.sampling,
                    requested_mip_levels: payload.requested_mip_levels,
                },
            ));
        }
        let trace_atlas = decoded_textures
            .iter()
            .find(|(texture_id, _)| *texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
            .map(|(_, texture)| texture)
            .or_else(|| {
                self.mesh_texture_assets
                    .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
            });
        let mut incoming_mesh_keys = BTreeSet::new();
        let mut decoded_meshes = Vec::with_capacity(meshes.len());
        for mesh in meshes {
            validate_mesh_asset(&mesh)?;
            trace_static_terrain_appearance(&mesh, trace_atlas);
            if !incoming_mesh_keys.insert(mesh.mesh_key) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!("duplicate world mesh asset key {}", mesh.mesh_key),
                ));
            }
            payload_bytes = payload_bytes
                .saturating_add(
                    mesh.vertices
                        .len()
                        .saturating_mul(WORLD_MESH_GPU_VERTEX_BYTES) as u64,
                )
                .saturating_add(mesh.index_bytes.len() as u64);
            // Retain the copied semantic source input, but defer expansion to
            // the fixed source shader ABI until a selected frame references
            // this mesh. The ordinary world route must not pay source-pack
            // conversion for every streamed off-screen terrain asset.
            let source_input = (retain_source_semantics
                && source_mesh_layout_has_shader_semantics(mesh.vertex_layout_version))
            .then(|| {
                if mesh.entity_identity.is_empty() {
                    SourceMeshSemanticInput::Terrain(mesh.vertices.clone())
                } else {
                    SourceMeshSemanticInput::Entity(
                        mesh.vertices
                            .iter()
                            .map(|vertex| SourceEntitySemanticVertex {
                                position: vertex.position,
                                uv: vertex.uv,
                                color_argb: vertex.color_argb,
                                normal_packed: vertex.normal_packed,
                                light: vertex.light,
                            })
                            .collect(),
                    )
                }
            });
            decoded_meshes.push((
                mesh.mesh_key,
                MeshAssetStore {
                    translucent_order: Default::default(),
                    section_ranges_cache: Default::default(),
                    texture_animation_signature_cache: Default::default(),
                    mesh_generation: mesh.mesh_generation,
                    index_generation: 0,
                    vertex_layout_version: mesh.vertex_layout_version,
                    vertex_bytes: packed_mesh_vertices(&mesh.vertices),
                    decal_normals: mesh
                        .sections
                        .iter()
                        .any(|section| section.material_mode == WORLD_MATERIAL_MODE_GLINT)
                        .then(|| {
                            mesh.vertices
                                .iter()
                                .map(|vertex| vertex.normal_packed)
                                .collect()
                        }),
                    source_input,
                    entity_identity: mesh.entity_identity,
                    // Voxel-source buffers are relevant only to an admitted
                    // source-terrain frame. Do not duplicate every streamed
                    // mesh's vertices and indices while ordinary Rust terrain
                    // rendering is active; they are derived lazily below.
                    terrain_voxel_vertices: None,
                    terrain_voxel_indices: None,
                    terrain_voxel_translucent_indices: None,
                    terrain_voxel_model_bounds: None,
                    index_bytes: mesh.index_bytes,
                    index_type: mesh.index_type,
                    sections: mesh.sections,
                },
            ));
        }
        let decoded_sorted_indices = sorted_indices
            .into_iter()
            .map(|update| validate_mesh_sorted_index_update(&update).map(|_| update))
            .collect::<GalResult<Vec<_>>>()?;
        let projected_mesh_assets = self.mesh_assets.len().saturating_sub(retirement_keys.len())
            + incoming_mesh_keys
                .iter()
                .filter(|key| !self.mesh_assets.contains_key(key) || retirement_keys.contains(key))
                .count();
        if projected_mesh_assets > WORLD_MESH_ASSET_RESIDENCY {
            return Err(GalError::invalid_argument(format!(
                "world mesh asset residency {projected_mesh_assets} exceeds bounded limit {WORLD_MESH_ASSET_RESIDENCY}"
            )));
        }
        let projected_texture_assets = self.mesh_texture_assets.len()
            + decoded_textures
                .iter()
                .filter(|(texture_id, _)| !self.mesh_texture_assets.contains_key(texture_id))
                .count();
        if projected_texture_assets > WORLD_MESH_TEXTURE_RESIDENCY {
            return Err(GalError::invalid_argument(format!(
                "world mesh texture residency {projected_texture_assets} exceeds bounded limit {WORLD_MESH_TEXTURE_RESIDENCY}"
            )));
        }
        let stale_resource_keys: Vec<MeshResourceKey> = self
            .mesh_resources
            .keys()
            .copied()
            .filter(|key| {
                replaced_texture_ids.contains(&key.texture_id)
                    || (incoming_mesh_keys.contains(&key.mesh_key)
                        && decoded_meshes
                            .iter()
                            .find(|(mesh_key, _)| *mesh_key == key.mesh_key)
                            .map(|(_, asset)| asset.mesh_generation != key.mesh_generation)
                            .unwrap_or_else(|| {
                                self.mesh_assets.get(&key.mesh_key).is_none_or(|asset| {
                                    asset.mesh_generation != key.mesh_generation
                                })
                            }))
            })
            .collect();
        let stale_lowered_source_keys = self
            .lowered_source_terrain_geometry_resources
            .keys()
            .filter(|key| {
                incoming_mesh_keys.contains(&key.mesh_key)
                    && decoded_meshes
                        .iter()
                        .find(|(mesh_key, _)| *mesh_key == key.mesh_key)
                        .is_some_and(|(_, asset)| asset.mesh_generation != key.mesh_generation)
            })
            .cloned()
            .collect::<Vec<_>>();
        // Validate the complete future asset view before retiring or replacing anything.
        let mut sorted_keys = BTreeSet::new();
        let mut sorted_uploads = Vec::new();
        for update in &decoded_sorted_indices {
            if !sorted_keys.insert(update.mesh_key) {
                return Err(GalError::invalid_argument("duplicate sorted-index mesh update"));
            }
            let asset = decoded_meshes.iter().find(|(key, _)| *key == update.mesh_key).map(|(_, asset)| asset)
                .or_else(|| if retirement_keys.contains(&update.mesh_key) { None } else { self.mesh_assets.get(&update.mesh_key) })
                .ok_or_else(|| GalError::invalid_argument("sorted-index update references absent mesh"))?;
            if asset.mesh_generation != update.mesh_generation || update.index_generation <= asset.index_generation {
                return Err(GalError::invalid_argument("sorted-index update has stale mesh/index generation"));
            }
            validate_mesh_sorted_index_payload(asset, update)?;
            // Existing resident ranges can be updated atomically in one submission.
            if !incoming_mesh_keys.contains(&update.mesh_key) && asset.index_bytes.len() == update.index_bytes.len() {
                let keys: BTreeSet<_> = self.mesh_resources.iter().filter(|(key, _)| key.mesh_key == update.mesh_key)
                    .map(|(_, resources)| resources.geometry_key)
                    .chain(self.source_mesh_resources.iter().filter(|(key, _)| key.mesh.mesh_key == update.mesh_key)
                        .map(|(_, resources)| resources.geometry_key)).collect();
                for key in keys {
                    if let Some(resources) = self.mesh_geometry_resources.get(&key) {
                        sorted_uploads.push(CommandOp::HostWriteBuffer { buffer: resources.index_buffer,
                            offset: resources.index_offset, data: update.index_bytes.clone() });
                        sorted_uploads.push(CommandOp::Barrier(buffer_barrier(resources.index_buffer,
                            TextureUsageState::TransferDst, TextureUsageState::IndexRead)));
                    }
                }
            }
        }
        if !sorted_uploads.is_empty() {
            gal.submit(SubmissionBatch { label: "world-mesh.sorted-index-transaction".into(),
                command_lists: vec![CommandList::from(CommandListDesc { label: "sorted-indices".into(), operations: sorted_uploads })] })?;
        }
        self.destroy_mesh_resources_for_keys(gal, retired_resource_keys);
        self.destroy_lowered_source_terrain_resources_for_keys(gal, retired_lowered_source_keys);
        for mesh_key in &retirement_keys {
            self.mesh_assets.remove(mesh_key);
        }
        self.mesh_asset_generation = generation;
        self.mesh_asset_payload_bytes = payload_bytes;
        // A selected source frame owns local mesh textures separately from
        // the ordinary indexed-mesh resources. Replacing one copied texture
        // must retire only bindings that name that semantic texture, never
        // let a later source frame retain its old payload.
        if !replaced_texture_ids.is_empty() {
            self.discard_all_unsubmitted_source_terrain_frames(gal);
            self.discard_unsubmitted_source_material_textures(gal, &replaced_texture_ids);
        }
        if !stale_resource_keys.is_empty() || !replaced_texture_ids.is_empty() {
            self.destroy_mesh_page_resource_sets(gal);
        }
        self.destroy_mesh_resources_for_keys(gal, stale_resource_keys);
        self.destroy_lowered_source_terrain_resources_for_keys(gal, stale_lowered_source_keys);
        self.destroy_material_resources_for_texture_ids(gal, &replaced_texture_ids);
        self.destroy_mesh_texture_resources_for_ids(gal, &replaced_texture_ids);
        for (texture_id, texture) in decoded_textures {
            self.staged_atlas_animations.remove(&texture_id);
            if self.latest_atlas_animation_texture == Some(texture_id) {
                self.latest_atlas_animation_observations.clear();
                self.latest_atlas_animation_texture = None;
            }
            if self
                .pending_atlas_animation
                .as_ref()
                .is_some_and(|tick| tick.texture_id() == texture_id)
            {
                self.pending_atlas_animation = None;
                self.pending_atlas_animation_event = None;
                self.pending_atlas_animation_failed = false;
                self.retry_atlas_animation_events.clear();
            }
            self.retry_atlas_animation_events.retain(|event| event.texture_id != texture_id);
            self.mesh_texture_assets.insert(texture_id, texture);
            self.mesh_texture_animation_generation =
                self.mesh_texture_animation_generation.wrapping_add(1);
        }
        for (mesh_key, asset) in decoded_meshes {
            self.mesh_assets.insert(mesh_key, asset);
        }
        // Batch plans contain frame indices into the previous mesh-instance
        // stream. A payload update can change the topology/resource identity
        // represented by any cached plan, so rebuild those plans wholesale.
        // Retirement-only updates are different: they commonly drain old,
        // off-screen animated-model keys while the visible topology is
        // unchanged. Invalidate only plans that name one of the retired
        // keys, preserving the bounded cache for the still-live scene.
        // A plan depends only on the meshes it names (plans key every
        // instance's mesh key and generation), so a mesh or sorted-index
        // payload invalidates just the plans naming that key; animated models
        // re-upload every frame and must not flush the static terrain plans.
        // Texture replacement can reclassify any section, so it flushes all.
        if !replaced_texture_ids.is_empty() {
            self.mesh_batch_plan_cache.clear();
            self.source_terrain_validated_identities.clear();
        } else if !incoming_mesh_keys.is_empty()
            || !decoded_sorted_indices.is_empty()
            || !retirement_keys.is_empty()
        {
            let sorted_keys: BTreeSet<u64> = decoded_sorted_indices
                .iter()
                .map(|update| update.mesh_key)
                .collect();
            self.mesh_batch_plan_cache.retain(|entry| {
                !entry.key.instances.iter().any(|instance| {
                    incoming_mesh_keys.contains(&instance.mesh_key)
                        || sorted_keys.contains(&instance.mesh_key)
                        || retirement_keys.contains(&instance.mesh_key)
                })
            });
        }
        // Mesh generations are content identities. An update can replace a
        // visible section between two otherwise coherent source-preparation
        // frames, so discard that prior exact-frame resource assembly. Keep
        // an already-confirmed source-route arm, however: the selected
        // executor rebuilds this assembly from the current frame before it
        // validates or records a draw. Clearing the arm here starves a live
        // world that is still ingesting sections and can never select its
        // current, fully validated source transaction.
        let captured_source_frame_was_invalidated = self.source_execution_activation_reported;
        let preserve_source_execution_arm = Self::preserve_source_execution_arm_after_mesh_update(
            self.source_execution_armed,
            self.source_execution_enabled(),
        );
        self.clear_candidate_source_resource_snapshot();
        // Do not restart the bounded source-occupancy preparation window for
        // every asset upload. Streaming terrain may continue registering
        // off-screen meshes while the actual visible semantic input remains
        // unchanged. `candidate_source_occupancy_input_is_stable` compares the
        // exact visible mesh/LOD generations and camera cell before admitting
        // work, so it will still restart immediately when a relevant input
        // changes.
        self.source_execution_armed = preserve_source_execution_arm;
        self.source_execution_activation_reported = false;
        self.source_execution_distant_horizons_reported = false;
        // Capture-only output evidence must describe the first selected
        // source frame after the newly authoritative mesh generation, not a
        // pre-edit source frame. This has no production route effect and is
        // deliberately gated behind the existing native audit opt-in.
        if captured_source_frame_was_invalidated && self.source_execution_enabled() {
            SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN.store(false, Ordering::SeqCst);
        }
        for update in decoded_sorted_indices {
            self.apply_mesh_sorted_index_update(gal, update)?;
        }
        Ok(())
    }

    pub(in crate::render::worldrender) fn flush_deferred_mesh_resource_destroys(&mut self, gal: &mut VulkanicGal) {
        let deferred = std::mem::take(&mut self.deferred_mesh_resource_destroys);
        for handle in deferred {
            let _ = gal.destroy(handle);
        }
        let deferred_streams = std::mem::take(&mut self.deferred_mesh_stream_buffer_destroys);
        for handle in deferred_streams {
            let _ = gal.destroy(handle);
        }
        let submission = gal.latest_submission_id();
        let deferred_ranges = std::mem::take(&mut self.deferred_mesh_geometry_range_releases);
        if !deferred_ranges.is_empty() {
            self.destroy_mesh_page_resource_sets(gal);
        }
        for resources in deferred_ranges {
            gal.unwatch_buffer_upload_for_capture(
                resources.vertex_buffer,
                resources.vertex_offset,
                resources.vertex_range as usize,
            );
            gal.unwatch_buffer_upload_for_capture(
                resources.index_buffer,
                resources.index_offset,
                resources.index_range as usize,
            );
            self.mesh_geometry_arena
                .defer_release(submission, resources);
        }
        self.mesh_geometry_arena.reclaim_completed(gal);
    }

    pub(in crate::render::worldrender) fn world_mesh_texture_mip_bytes(&self, texture_id: u32) -> GalResult<(Vec<Vec<u8>>, u32, u32)> {
        if let Some(asset) = self.mesh_texture_assets.get(&texture_id) {
            let mut levels = vec![sampled_texture_bytes(asset)?];
            for (index, rgba) in asset.mip_rgba.iter().enumerate() {
                let mip = index as u32 + 1;
                let width = (asset.width >> mip).max(1);
                let height = (asset.height >> mip).max(1);
                let expected = usize::try_from(width)
                    .ok()
                    .and_then(|width| usize::try_from(height).ok()?.checked_mul(width))
                    .and_then(|pixels| pixels.checked_mul(4))
                    .ok_or_else(|| {
                        GalError::invalid_argument("sampled texture mip dimensions overflow")
                    })?;
                if rgba.len() != expected {
                    return Err(GalError::invalid_argument(format!(
                        "sampled texture mip {} has {} bytes but {}x{} requires {expected}",
                        mip,
                        rgba.len(),
                        width,
                        height
                    )));
                }
                let mut sampled = rgba.clone();
                if asset.coordinate_origin == WorldMeshTextureCoordinateOrigin::MinecraftTopLeft {
                    flip_rgba_rows_in_place(&mut sampled, width, height)?;
                }
                levels.push(sampled);
            }
            return Ok((levels, asset.width, asset.height));
        }
        let (base, width, height) = self.world_material_texture_bytes(texture_id)?;
        Ok((vec![base], width, height))
    }

    /// ModelPart UVs retain Minecraft's top-left PNG convention. The local
    /// model sampler addresses uploaded rows in that same semantic order, so
    /// this path must preserve those rows rather than applying the ordinary
    /// terrain-atlas conversion a second time.
    pub(in crate::render::worldrender) fn source_local_material_texture_bytes(
        &self,
        texture_id: u32,
    ) -> GalResult<(Vec<u8>, u32, u32)> {
        if let Some(asset) = self.mesh_texture_assets.get(&texture_id) {
            let expected = usize::try_from(asset.width)
                .ok()
                .and_then(|width| usize::try_from(asset.height).ok()?.checked_mul(width))
                .and_then(|pixels| pixels.checked_mul(4))
                .ok_or_else(|| {
                    GalError::invalid_argument("source-local texture dimensions overflow")
                })?;
            if asset.rgba.len() != expected {
                return Err(GalError::invalid_argument(
                    "source-local texture payload has invalid dimensions",
                ));
            }
            return Ok((asset.rgba.clone(), asset.width, asset.height));
        }
        self.world_material_texture_bytes(texture_id)
    }

    pub(in crate::render::worldrender) fn world_mesh_texture_animation_sample(
        &self,
        texture_id: u32,
        frame_id: u64,
    ) -> MeshTextureAnimationSample {
        self.mesh_texture_assets
            .get(&texture_id)
            .map(|asset| {
                sample_mesh_texture_animation(
                    &asset.animation_frames,
                    asset.animation_total_ticks,
                    asset.interpolation_policy,
                    frame_id,
                )
            })
            .unwrap_or_default()
    }

    /// Returns the validated immutable animation class for a copied texture.
    /// A one-frame table is the same full-image sampling contract as the
    /// legacy/default path, so it may use the compact static fragment variant;
    /// every multi-frame asset remains on the animated shader identity.
    pub(in crate::render::worldrender) fn world_mesh_texture_is_animated(&self, texture_id: u32) -> bool {
        self.mesh_texture_assets
            .get(&texture_id)
            .is_some_and(|asset| asset.animation_frames.len() > 1)
    }

    pub(in crate::render::worldrender) fn ensure_mesh_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
        texture_id: u32,
        label_prefix: &str,
    ) -> GalResult<()> {
        if self.mesh_texture_resources.contains_key(&texture_id) {
            return Ok(());
        }
        let (texture_levels, texture_width, texture_height) =
            self.world_mesh_texture_mip_bytes(texture_id)?;
        let mip_levels = requested_mesh_texture_mip_levels(
            texture_id,
            texture_levels.len(),
            texture_width,
            texture_height,
            self.mesh_texture_assets
                .get(&texture_id)
                .map_or(0, |asset| asset.requested_mip_levels),
        )?;
        let upload_bytes = texture_levels.iter().try_fold(0_u64, |total, level| {
            total.checked_add(level.len() as u64).ok_or_else(|| {
                GalError::invalid_argument("world mesh texture upload size overflows")
            })
        })?;
        let label = format!("{label_prefix}.texture{texture_id}");
        let mut created = Vec::new();
        let result = (|| -> GalResult<MeshTextureResources> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.upload"),
                size: upload_bytes,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(upload_buffer);
            let texture = gal.create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: texture_width,
                    height: texture_height,
                    depth: 1,
                },
                mip_levels,
                array_layers: 1,
                usages: vec![
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })?;
            created.push(texture);
            let sampling = self
                .mesh_texture_assets
                .get(&texture_id)
                .and_then(|asset| asset.sampling)
                .unwrap_or(crate::render::shared::texture_sampling::TextureSampling {
                    filter: SamplerFilter::Nearest,
                    address: SamplerAddressMode::ClampToEdge,
                });
            let sampler_descriptor = sampling.descriptor(
                format!("{label}.sampler"),
                if self
                    .mesh_texture_assets
                    .get(&texture_id)
                    .is_some_and(|asset| asset.requested_mip_levels > 0)
                    && matches!(
                        texture_id,
                        WORLD_MATERIAL_TEXTURE_WATER_STILL
                            | WORLD_MATERIAL_TEXTURE_WATER_FLOW
                            | WORLD_MATERIAL_TEXTURE_WATER_OVERLAY
                    )
                {
                    SamplerFilter::Linear
                } else {
                    mesh_texture_mip_filter(texture_id)
                },
            );
            // Capture-only observation of the exact descriptor submitted to GAL.
            // Do not infer effective filtering from the options menu or CPU pixels.
            if texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS
                && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true")
                )
                && crate::core::environment::var_os("MATTMC_ATLAS_TRACE_SPRITE").is_some()
            {
                static OBSERVATIONS: AtomicUsize = AtomicUsize::new(0);
                if OBSERVATIONS
                    .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |count| {
                        (count < 16).then_some(count + 1)
                    })
                    .is_ok()
                {
                    crate::core::console::stderr(format_args!("atlas-sampling-observation texture={} width={} height={} mip_levels={} min={:?} mag={:?} mip={:?} address_u={:?} address_v={:?} explicit_gal_descriptor_not_gpu_readback=true",
                        texture_id, texture_width, texture_height, mip_levels,
                        sampler_descriptor.min_filter, sampler_descriptor.mag_filter,
                        sampler_descriptor.mip_filter, sampler_descriptor.address_u,
                        sampler_descriptor.address_v));
                }
            }
            let sampler = gal.create_sampler(sampler_descriptor)?;
            created.push(sampler);
            let view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: mip_levels,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let resources = MeshTextureResources {
                upload_buffer,
                texture,
                sampler,
                view,
                width: texture_width,
                height: texture_height,
                mip_levels,
            };
            self.upload_mesh_texture_levels(
                gal,
                &resources,
                texture_levels,
                texture_width,
                texture_height,
            )?;
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.mesh_texture_resources.insert(texture_id, result?);
        Ok(())
    }

    /// Return the generation of the copied texture payload currently bound by
    /// Rust.  Mesh-stream generations advance for ordinary geometry updates,
    /// while an atlas payload may remain unchanged; conflating the two lets an
    /// exact-atlas DH draw appear to use a newer atlas than the texture it
    /// actually samples.
    pub(in crate::render::worldrender) fn mesh_texture_generation(&self, texture_id: u32) -> u64 {
        self.mesh_texture_assets
            .get(&texture_id)
            .map(|asset| asset.animation_generation)
            .unwrap_or(self.mesh_asset_generation)
    }

    pub(in crate::render::worldrender) fn upload_mesh_texture_levels(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &MeshTextureResources,
        texture_levels: Vec<Vec<u8>>,
        texture_width: u32,
        texture_height: u32,
    ) -> GalResult<()> {
        let operations = if texture_levels.len() > 1 {
            Self::mesh_texture_exact_mip_upload_ops(
                resources,
                texture_levels,
                texture_width,
                texture_height,
            )?
        } else {
            Self::mesh_texture_upload_ops(
                resources,
                texture_levels.into_iter().next().unwrap_or_default(),
                texture_width,
                texture_height,
            )?
        };
        self.submit_or_queue_world_upload(gal, "world-mesh.texture-upload", operations)
    }

    pub(in crate::render::worldrender) fn mesh_texture_exact_mip_upload_ops(
        resources: &MeshTextureResources,
        texture_levels: Vec<Vec<u8>>,
        texture_width: u32,
        texture_height: u32,
    ) -> GalResult<Vec<CommandOp>> {
        if texture_levels.len() as u32 != resources.mip_levels {
            return Err(GalError::invalid_argument(format!(
                "exact texture mip chain has {} levels but resource requires {}",
                texture_levels.len(),
                resources.mip_levels
            )));
        }
        let mut packed = Vec::new();
        let mut copies = Vec::with_capacity(texture_levels.len());
        for (index, level) in texture_levels.into_iter().enumerate() {
            let mip = index as u32;
            let width = (texture_width >> mip).max(1);
            let height = (texture_height >> mip).max(1);
            let expected = usize::try_from(width)
                .ok()
                .and_then(|width| usize::try_from(height).ok()?.checked_mul(width))
                .and_then(|pixels| pixels.checked_mul(4))
                .ok_or_else(|| GalError::invalid_argument("exact texture mip extent overflows"))?;
            if level.len() != expected {
                return Err(GalError::invalid_argument(format!(
                    "exact texture mip {} has {} bytes but {}x{} requires {expected}",
                    mip,
                    level.len(),
                    width,
                    height
                )));
            }
            let buffer_offset = packed.len() as u64;
            packed.extend_from_slice(&level);
            copies.push(BufferImageCopyRegion {
                buffer: resources.upload_buffer,
                buffer_offset,
                bytes_per_row: width * 4,
                rows_per_image: height,
                texture: resources.texture,
                texture_mip: mip,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
            });
        }
        let all_mips = TextureSubresourceRange {
            base_mip: 0,
            mip_count: resources.mip_levels,
            base_layer: 0,
            layer_count: 1,
        };
        let mut operations = vec![
            CommandOp::HostWriteBuffer {
                buffer: resources.upload_buffer,
                offset: 0,
                data: packed,
            },
            CommandOp::Barrier(buffer_barrier(
                resources.upload_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )),
            CommandOp::Barrier(texture_subresource_barrier(
                resources.texture,
                all_mips,
                TextureUsageState::Undefined,
                TextureUsageState::TransferDst,
            )),
        ];
        operations.extend(copies.into_iter().map(CommandOp::CopyBufferToTexture));
        operations.push(CommandOp::Barrier(texture_subresource_barrier(
            resources.texture,
            all_mips,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        Ok(operations)
    }

    pub(in crate::render::worldrender) fn mesh_texture_upload_ops(
        resources: &MeshTextureResources,
        texture_bytes: Vec<u8>,
        texture_width: u32,
        texture_height: u32,
    ) -> GalResult<Vec<CommandOp>> {
        Self::mesh_texture_upload_ops_from_state(
            resources,
            texture_bytes,
            texture_width,
            texture_height,
            TextureUsageState::Undefined,
        )
    }

    pub(in crate::render::worldrender) fn mesh_texture_upload_ops_from_state(
        resources: &MeshTextureResources,
        texture_bytes: Vec<u8>,
        texture_width: u32,
        texture_height: u32,
        before: TextureUsageState,
    ) -> GalResult<Vec<CommandOp>> {
        let bytes_per_row = texture_width
            .checked_mul(4)
            .ok_or_else(|| GalError::invalid_argument("material texture row pitch overflows"))?;
        let base_mip = TextureSubresourceRange {
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        };
        let mut operations = vec![
            CommandOp::HostWriteBuffer {
                buffer: resources.upload_buffer,
                offset: 0,
                data: texture_bytes,
            },
            CommandOp::Barrier(buffer_barrier(
                resources.upload_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )),
            CommandOp::Barrier(texture_subresource_barrier(
                resources.texture,
                base_mip,
                before,
                TextureUsageState::TransferDst,
            )),
            CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                buffer: resources.upload_buffer,
                buffer_offset: 0,
                bytes_per_row,
                rows_per_image: texture_height,
                texture: resources.texture,
                texture_mip: 0,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: Extent3d {
                    width: texture_width,
                    height: texture_height,
                    depth: 1,
                },
            }),
        ];
        if resources.mip_levels > 1 {
            let descendants = TextureSubresourceRange {
                base_mip: 1,
                mip_count: resources.mip_levels - 1,
                base_layer: 0,
                layer_count: 1,
            };
            let full_mips = TextureSubresourceRange {
                base_mip: 0,
                mip_count: resources.mip_levels,
                base_layer: 0,
                layer_count: 1,
            };
            operations.push(CommandOp::Barrier(texture_subresource_barrier(
                resources.texture,
                base_mip,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )));
            operations.push(CommandOp::Barrier(texture_subresource_barrier(
                resources.texture,
                descendants,
                before,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::GenerateMipmaps {
                texture: resources.texture,
                subresources: full_mips,
            });
            operations.push(CommandOp::Barrier(texture_subresource_barrier(
                resources.texture,
                full_mips,
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            )));
        } else {
            operations.push(CommandOp::Barrier(texture_subresource_barrier(
                resources.texture,
                base_mip,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        Ok(operations)
    }

    pub(in crate::render::worldrender) fn destroy_material_resources_for_texture_ids(
        &mut self,
        gal: &mut VulkanicGal,
        texture_ids: &BTreeSet<u32>,
    ) {
        let stale_keys = self
            .material_resources
            .keys()
            .filter(|key| texture_ids.contains(&key.texture_id))
            .copied()
            .collect::<Vec<_>>();
        for key in stale_keys {
            if let Some(resources) = self.material_resources.remove(&key) {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
            }
        }
    }

    pub(in crate::render::worldrender) fn destroy_mesh_resources(&mut self, gal: &mut VulkanicGal) {
        self.destroy_mesh_page_resource_sets(gal);
        self.destroy_lowered_textured_material_source_resources(gal);
        self.destroy_lowered_entity_source_resources(gal);
        self.destroy_lowered_source_terrain_resources(gal);
        self.destroy_source_mesh_resources(gal);
        let resources = std::mem::take(&mut self.mesh_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        let geometry = std::mem::take(&mut self.mesh_geometry_resources);
        for (_, resources) in geometry {
            self.mesh_geometry_arena.release_vertex(
                resources.vertex_buffer,
                resources.vertex_offset,
                resources.vertex_range,
            );
            self.mesh_geometry_arena.release_index(
                resources.index_buffer,
                resources.index_offset,
                resources.index_range,
            );
        }
        self.mesh_geometry_arena.destroy(gal);
        self.deferred_mesh_geometry_range_releases.clear();
    }

    pub(in crate::render::worldrender) fn destroy_mesh_texture_resources(&mut self, gal: &mut VulkanicGal) {
        self.clear_candidate_source_material_texture_resources(gal);
        let resources = std::mem::take(&mut self.mesh_texture_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
    }

    pub(in crate::render::worldrender) fn destroy_mesh_texture_resources_for_ids(
        &mut self,
        gal: &mut VulkanicGal,
        ids: &BTreeSet<u32>,
    ) {
        if ids.contains(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS) {
            self.clear_candidate_source_material_texture_resources(gal);
            self.lod_exact_atlas_opaque_pass_resources
                .clear_bindings(gal);
            if let Some(resources) = self.lod_exact_atlas_forward_opaque_pass_resources.as_mut() {
                resources.clear_bindings(gal);
            }
            for resources in [
                self.lod_exact_atlas_forward_transparent_side_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_forward_transparent_up_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_forward_water_pass_resources.as_mut(),
            ]
            .into_iter()
            .flatten()
            {
                resources.clear_bindings(gal);
            }
            for resources in [
                self.lod_exact_atlas_deferred_transparent_side_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_deferred_transparent_up_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_deferred_water_pass_resources.as_mut(),
            ]
            .into_iter()
            .flatten()
            {
                resources.clear_bindings(gal);
            }
            self.lod_exact_atlas_source_pass_resources
                .clear_bindings(gal);
        }
        let source_keys = self
            .source_mesh_resources
            .keys()
            .filter(|source_key| ids.contains(&source_key.mesh.texture_id))
            .cloned()
            .collect::<Vec<_>>();
        let mut source_geometry_keys = BTreeSet::new();
        for source_key in source_keys {
            if let Some(resources) = self.source_mesh_resources.remove(&source_key) {
                source_geometry_keys.insert(resources.geometry_key);
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
            }
        }
        for geometry_key in source_geometry_keys {
            let still_referenced = self
                .mesh_resources
                .values()
                .any(|resources| resources.geometry_key == geometry_key)
                || self
                    .source_mesh_resources
                    .values()
                    .any(|resources| resources.geometry_key == geometry_key);
            if !still_referenced {
                if let Some(resources) = self.mesh_geometry_resources.remove(&geometry_key) {
                    self.deferred_mesh_geometry_range_releases.push(resources);
                }
            }
        }
        for texture_id in ids {
            if let Some(resources) = self.mesh_texture_resources.remove(texture_id) {
                for handle in resources.handles_in_destroy_order() {
                    self.deferred_mesh_resource_destroys.push(handle);
                }
            }
        }
    }

    pub(in crate::render::worldrender) fn destroy_mesh_resources_for_keys(
        &mut self,
        _gal: &mut VulkanicGal,
        keys: Vec<MeshResourceKey>,
    ) {
        let mut geometry_keys = BTreeSet::new();
        for key in keys {
            let source_keys = self
                .source_mesh_resources
                .keys()
                .filter(|source_key| source_key.mesh == key)
                .cloned()
                .collect::<Vec<_>>();
            for source_key in source_keys {
                if let Some(resources) = self.source_mesh_resources.remove(&source_key) {
                    geometry_keys.insert(resources.geometry_key);
                    for handle in resources.handles_in_destroy_order() {
                        self.deferred_mesh_resource_destroys.push(handle);
                    }
                }
            }
            if let Some(resources) = self.mesh_resources.remove(&key) {
                geometry_keys.insert(resources.geometry_key);
                for handle in resources.handles_in_destroy_order() {
                    self.deferred_mesh_resource_destroys.push(handle);
                }
            }
        }
        for geometry_key in geometry_keys {
            let still_referenced = self
                .mesh_resources
                .values()
                .any(|resources| resources.geometry_key == geometry_key)
                || self
                    .source_mesh_resources
                    .values()
                    .any(|resources| resources.geometry_key == geometry_key);
            if !still_referenced {
                if let Some(resources) = self.mesh_geometry_resources.remove(&geometry_key) {
                    self.deferred_mesh_geometry_range_releases.push(resources);
                }
            }
        }
    }
}

/// Complete mip chain for an owned 2D atlas.  This is a resource description,
/// not a backend choice: GAL records the generation and each backend performs
/// its own explicit mip operation.
pub(in crate::render::worldrender) fn texture_mip_level_count(width: u32, height: u32) -> u32 {
    width.max(height).max(1).ilog2() + 1
}

pub(in crate::render::worldrender) fn mesh_texture_resource_mip_levels(
    texture_id: u32,
    supplied_levels: usize,
    width: u32,
    height: u32,
) -> GalResult<u32> {
    let supplied = u32::try_from(supplied_levels)
        .map_err(|_| GalError::invalid_argument("world mesh texture mip count overflows"))?;
    let complete = texture_mip_level_count(width, height);
    if supplied == 0 || supplied > complete {
        return Err(GalError::invalid_argument(
            "invalid copied mesh texture mip count",
        ));
    }
    // SkyRenderer's semantic sky assets explicitly disable mipmaps, including
    // resource-pack replacements. Describe a single-level Rust-owned image so
    // every material view and upload inherits that contract; do not generate
    // hidden levels that change minification of high-resolution sky textures.
    if matches!(
        texture_id,
        WORLD_MATERIAL_TEXTURE_SKY_SUN
            | WORLD_MATERIAL_TEXTURE_SKY_MOON_PHASES
            | WORLD_MATERIAL_TEXTURE_END_SKY
            | WORLD_MATERIAL_TEXTURE_END_FLASH
    ) {
        if supplied != 1 {
            return Err(GalError::invalid_argument(
                "semantic sky textures require exactly one supplied mip level",
            ));
        }
        return Ok(1);
    }
    // The terrain producer always supplies its exact CPU atlas chain. One
    // supplied level means mipmaps are disabled, not permission to synthesize
    // levels that the resource settings excluded.
    Ok(
        if texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS || supplied > 1 {
            supplied
        } else {
            complete
        },
    )
}

pub(in crate::render::worldrender) fn requested_mesh_texture_mip_levels(
    texture_id: u32,
    supplied: usize,
    width: u32,
    height: u32,
    requested: u32,
) -> GalResult<u32> {
    let inherited = mesh_texture_resource_mip_levels(texture_id, supplied, width, height)?;
    if requested == 0 {
        return Ok(inherited);
    }
    if requested > texture_mip_level_count(width, height)
        || (matches!(
            texture_id,
            WORLD_MATERIAL_TEXTURE_SKY_SUN
                | WORLD_MATERIAL_TEXTURE_SKY_MOON_PHASES
                | WORLD_MATERIAL_TEXTURE_END_SKY
                | WORLD_MATERIAL_TEXTURE_END_FLASH
        ) && requested != 1)
        || requested < supplied as u32
        || (supplied > 1 && requested != supplied as u32)
        || (texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS && requested != supplied as u32)
    {
        return Err(GalError::invalid_argument(
            "explicit mesh texture mip count conflicts with its extent or supplied chain",
        ));
    }
    Ok(requested)
}

/// Mirrors Minecraft's block-atlas sampler: nearest texels inside each mip,
/// linear interpolation between adjacent levels. Other copied material images
/// retain their explicitly declared nearest mip policy.
pub(in crate::render::worldrender) fn mesh_texture_mip_filter(texture_id: u32) -> SamplerFilter {
    if texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS {
        SamplerFilter::Linear
    } else {
        SamplerFilter::Nearest
    }
}

/// Exact allocation footprint of the complete 2D mip chain.  The base image
/// is uploaded separately, so callers add it when their staging buffer stays
/// resident alongside the sampled image.
pub(in crate::render::worldrender) fn texture_mip_chain_byte_size(width: u32, height: u32, bytes_per_texel: u64) -> GalResult<u64> {
    let mut total = 0_u64;
    let mut mip_width = width.max(1);
    let mut mip_height = height.max(1);
    for _ in 0..texture_mip_level_count(width, height) {
        let level_bytes = u64::from(mip_width)
            .checked_mul(u64::from(mip_height))
            .and_then(|pixels| pixels.checked_mul(bytes_per_texel))
            .ok_or_else(|| GalError::invalid_argument("texture mip chain size overflows u64"))?;
        total = total
            .checked_add(level_bytes)
            .ok_or_else(|| GalError::invalid_argument("texture mip chain size overflows u64"))?;
        mip_width = (mip_width / 2).max(1);
        mip_height = (mip_height / 2).max(1);
    }
    Ok(total)
}

pub(in crate::render::worldrender) fn normalized_frame_extent(value: u32, fallback: u32) -> GalResult<u32> {
    let extent = if value == 0 { fallback } else { value };
    if extent == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh texture animation frame extent must be non-zero",
        ));
    }
    Ok(extent)
}

pub(in crate::render::worldrender) fn normalized_frame_count(value: u32) -> GalResult<u32> {
    let count = if value == 0 { 1 } else { value };
    if count > 512 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture animation frame count {count} exceeds 512"),
        ));
    }
    Ok(count)
}

pub(in crate::render::worldrender) fn normalized_frame_ticks(value: u32) -> GalResult<u32> {
    let ticks = if value == 0 { 1 } else { value };
    if ticks > 60_000 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture animation frame duration {ticks} exceeds 60000"),
        ));
    }
    Ok(ticks)
}

pub(in crate::render::worldrender) fn normalized_frame_row_size(width: u32, frame_width: u32, value: u32) -> GalResult<u32> {
    // Frame dimensions are semantic asset data. Reject zero before doing any
    // arithmetic so malformed resource-pack metadata cannot panic the Rust
    // renderer (and consequently cannot accidentally admit a partial route).
    if width == 0 || frame_width == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh texture animation frame width must be non-zero",
        ));
    }
    let frames_per_row = width / frame_width;
    if frames_per_row == 0 || width % frame_width != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world mesh texture animation frame width must tile the payload width",
        ));
    }
    let row_size = if value == 0 { frames_per_row } else { value };
    if row_size == 0 || row_size > frames_per_row {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture animation frame row size {row_size} is out of bounds"),
        ));
    }
    Ok(row_size)
}

pub(in crate::render::worldrender) fn decoded_animation_frames(
    width: u32,
    height: u32,
    frame_width: u32,
    frame_height: u32,
    frame_row_size: u32,
    frame_count_hint: u32,
    frame_ticks: u32,
    explicit_frames: &[WorldMeshAnimationFrame],
    interpolation_policy: u32,
    texture_id: u32,
) -> GalResult<(Vec<MeshTextureAnimationFrame>, u32)> {
    if width == 0 || height == 0 || frame_width == 0 || frame_height == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture {texture_id} animation frame dimensions must be non-zero"),
        ));
    }
    if frame_width > width || frame_height > height {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture {texture_id} animation frame exceeds sheet extent"),
        ));
    }
    if height % frame_height != 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture {texture_id} animation frame height must tile sheet"),
        ));
    }
    let rows = height / frame_height;
    let capacity = frame_row_size.saturating_mul(rows);
    if rows == 0 || capacity == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture {texture_id} animation sheet has no frames"),
        ));
    }
    if interpolation_policy != WORLD_MESH_ANIMATION_INTERPOLATE_NONE
        && interpolation_policy != WORLD_MESH_ANIMATION_INTERPOLATE_LINEAR
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world mesh texture {texture_id} uses unsupported interpolation policy {interpolation_policy}"
            ),
        ));
    }
    let raw_frames: Vec<WorldMeshAnimationFrame> = if explicit_frames.is_empty() {
        let count = normalized_frame_count(frame_count_hint)?;
        (0..count)
            .map(|frame_index| WorldMeshAnimationFrame {
                frame_index,
                duration_ticks: frame_ticks,
            })
            .collect()
    } else {
        if frame_count_hint != 0 && frame_count_hint as usize != explicit_frames.len() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh texture {texture_id} animation frame count does not match frame table"
                ),
            ));
        }
        explicit_frames.to_vec()
    };
    if raw_frames.is_empty() || raw_frames.len() > WORLD_MAX_MESH_ANIMATION_FRAMES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("world mesh texture {texture_id} animation frame table size is out of bounds"),
        ));
    }
    let mut decoded = Vec::with_capacity(raw_frames.len());
    let mut total_ticks = 0u32;
    for frame in raw_frames {
        let duration = normalized_frame_ticks(frame.duration_ticks)?;
        if frame.frame_index >= capacity {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh texture {texture_id} animation frame {} is out of bounds",
                    frame.frame_index
                ),
            ));
        }
        let col = frame.frame_index % frame_row_size;
        let row = frame.frame_index / frame_row_size;
        let x0 = col.saturating_mul(frame_width);
        let y0 = row.saturating_mul(frame_height);
        if x0.saturating_add(frame_width) > width || y0.saturating_add(frame_height) > height {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "world mesh texture {texture_id} animation frame {} exceeds sheet bounds",
                    frame.frame_index
                ),
            ));
        }
        total_ticks = total_ticks.checked_add(duration).ok_or_else(|| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!("world mesh texture {texture_id} animation duration overflow"),
            )
        })?;
        decoded.push(MeshTextureAnimationFrame {
            duration_ticks: duration,
            region: [
                x0 as f32 / width as f32,
                y0 as f32 / height as f32,
                frame_width as f32 / width as f32,
                frame_height as f32 / height as f32,
            ],
        });
    }
    Ok((decoded, total_ticks.max(1)))
}

pub(in crate::render::worldrender) fn crack_atlas_bytes(overrides: &BTreeMap<u32, WorldCrackTextureAsset>) -> GalResult<Vec<u8>> {
    let atlas_width = CRACK_STAGE_COUNT * CRACK_STAGE_SIZE;
    let mut stages: Vec<Vec<u8>> = Vec::with_capacity(CRACK_STAGE_COUNT as usize);
    for stage in 0..CRACK_STAGE_COUNT {
        if let Some(asset) = overrides.get(&stage) {
            stages.push(asset.rgba.clone());
        } else {
            let bytes = bundled_crack_stage_png(stage);
            let decoded = decode_crack_stage(bytes).expect("bundled crack stage texture is valid");
            stages.push(decoded);
        }
    }
    let mut out = Vec::with_capacity((atlas_width * CRACK_STAGE_SIZE * 4) as usize);
    let stage_row_bytes = (CRACK_STAGE_SIZE * 4) as usize;
    for row in 0..CRACK_STAGE_SIZE as usize {
        let row_start = row * stage_row_bytes;
        let row_end = row_start + stage_row_bytes;
        for stage_bytes in &stages {
            out.extend_from_slice(&stage_bytes[row_start..row_end]);
        }
    }
    Ok(out)
}

pub(in crate::render::worldrender) fn bundled_crack_stage_png(stage: u32) -> &'static [u8] {
    match stage {
        0 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_0.png")
                .as_slice()
        }
        1 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_1.png")
                .as_slice()
        }
        2 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_2.png")
                .as_slice()
        }
        3 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_3.png")
                .as_slice()
        }
        4 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_4.png")
                .as_slice()
        }
        5 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_5.png")
                .as_slice()
        }
        6 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_6.png")
                .as_slice()
        }
        7 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_7.png")
                .as_slice()
        }
        8 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_8.png")
                .as_slice()
        }
        9 => {
            include_bytes!("../../../../resources/assets/minecraft/textures/block/destroy_stage_9.png")
                .as_slice()
        }
        _ => unreachable!(),
    }
}

pub(in crate::render::worldrender) fn forcefield_texture_bytes() -> GalResult<(Vec<u8>, u32, u32)> {
    decode_png_rgba(
        include_bytes!("../../../../resources/assets/minecraft/textures/misc/forcefield.png")
            .as_slice(),
        "world border forcefield",
    )
}

pub(in crate::render::worldrender) fn bundled_world_material_texture_bytes(texture_id: u32) -> GalResult<(Vec<u8>, u32, u32)> {
    if texture_id == WORLD_MATERIAL_TEXTURE_GENERATED_WHITE {
        return Ok((vec![255, 255, 255, 255], 1, 1));
    }
    let Some(texture) = assets::material_registry::texture(texture_id) else {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material texture id {texture_id}"),
        ));
    };
    decode_png_rgba(texture.default_png, texture.resource_location)
}

pub(in crate::render::worldrender) fn decode_crack_stage(bytes: &[u8]) -> GalResult<Vec<u8>> {
    let (rgba, width, height) = decode_png_rgba(bytes, "crack stage")?;
    if width != CRACK_STAGE_SIZE || height != CRACK_STAGE_SIZE {
        return Err(GalError::backend(format!(
            "unexpected crack stage dimensions {}x{}",
            width, height
        )));
    }
    Ok(rgba)
}

/// Keep compressed texture payload bounds from becoming a decompression
/// allocation escape hatch. Java admission uses the same 16M-pixel ceiling
/// for copied dynamic and atlas assets.
pub(in crate::render::worldrender) const MAX_DECODED_TEXTURE_PIXELS: u64 = 16 * 1024 * 1024;

pub(in crate::render::worldrender) fn decode_png_rgba(bytes: &[u8], label: &str) -> GalResult<(Vec<u8>, u32, u32)> {
    let mut decoder = png::Decoder::new(std::io::BufReader::new(bytes));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder
        .read_info()
        .map_err(|error| GalError::backend(format!("failed to decode {label}: {error}")))?;
    let header = reader.info();
    let pixel_count = (header.width as u64)
        .checked_mul(header.height as u64)
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                format!("{label} dimensions overflow"),
            )
        })?;
    if pixel_count == 0 || pixel_count > MAX_DECODED_TEXTURE_PIXELS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "{label} decoded pixel count {pixel_count} exceeds {MAX_DECODED_TEXTURE_PIXELS}"
            ),
        ));
    }
    let mut buf = vec![0; reader.output_buffer_size()];
    let info = reader
        .next_frame(&mut buf)
        .map_err(|error| GalError::backend(format!("failed to read {label}: {error}")))?;
    let data = &buf[..info.buffer_size()];
    let rgba = match info.color_type {
        png::ColorType::Rgba => data.to_vec(),
        png::ColorType::Rgb => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(3) {
                rgba.extend_from_slice(&[pixel[0], pixel[1], pixel[2], 255]);
            }
            rgba
        }
        png::ColorType::GrayscaleAlpha => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(2) {
                rgba.extend_from_slice(&[pixel[0], pixel[0], pixel[0], pixel[1]]);
            }
            rgba
        }
        png::ColorType::Grayscale => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for value in data {
                rgba.extend_from_slice(&[*value, *value, *value, 255]);
            }
            rgba
        }
        png::ColorType::Indexed => Err(GalError::backend(
            "indexed texture was not expanded by the PNG decoder",
        ))?,
    };
    Ok((rgba, info.width, info.height))
}
