//! Atlas sprite animation: observation, ticks, owned-image uploads and GUI atlas views.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) fn atlas_animation_registry_residency_fits(
    atlases: usize,
    source_bytes: usize,
    sprites: usize,
    frames: usize,
    mips: usize,
) -> bool {
    atlases <= 64
        && source_bytes <= 96 * 1024 * 1024
        && sprites <= 16_384
        && frames <= 65_536
        && mips <= 65_536
}

impl WorldPrimitiveFrontend {
    /// A frame boundary must not overtake this event. Reclaim at most the three
    /// owned leases required for capacity, using explicit submission completion.
    pub(crate) fn advance_atlas_animation_before_frame(
        &mut self,
        gal: &mut VulkanicGal,
        event: crate::render::shared::sprite_interpolation::AtlasAnimationTickEvent,
    ) -> GalResult<bool> {
        for attempt in 0..=3 {
            if self.advance_atlas_animation(gal, event.clone())? {
                return Ok(true);
            }
            if attempt < 3 {
                self.atlas_animation_uploads.wait_for_oldest(gal)?;
            }
        }
        Err(GalError::backend(
            "animation capacity did not recover after owned completions",
        ))
    }

    /// Returns false only for completion backpressure. A retry must carry the
    /// identical semantic event; visibility cannot be replaced by a later frame.
    pub(crate) fn advance_atlas_animation(
        &mut self,
        gal: &mut VulkanicGal,
        event: crate::render::shared::sprite_interpolation::AtlasAnimationTickEvent,
    ) -> GalResult<bool> {
        if event.visible.len() > 16384 {
            return Err(GalError::invalid_argument(
                "animation visibility bound exceeded",
            ));
        }
        let event_generation = event.generation;
        let event_tick = event.tick;
        let texture_id = event.texture_id;
        let animation = self
            .staged_atlas_animations
            .get(&texture_id)
            .ok_or_else(|| GalError::invalid_argument("atlas animation is not staged"))?;
        if event.texture_id != animation.texture_id || event.generation != animation.generation {
            return Err(GalError::invalid_argument(
                "animation tick names a stale atlas incarnation",
            ));
        }
        if let Some(pending) = &self.pending_atlas_animation_event {
            if pending != &event {
                return Err(GalError::invalid_argument(
                    "animation retry must preserve the pending semantic event",
                ));
            }
        } else {
            self.prepare_atlas_animation_tick(
                texture_id,
                event.tick,
                &event.visible,
                event.animate_only_visible,
            )?;
            self.pending_atlas_animation_event = Some(event);
        }
        let pending_shape = self
            .pending_atlas_animation
            .as_ref()
            .map(|prepared| {
                let bytes = prepared
                    .patches()
                    .iter()
                    .flat_map(|patch| &patch.mip_pixels)
                    .map(|pixels| pixels.len() as u64)
                    .sum::<u64>();
                (prepared.patches().len() as u64, bytes)
            })
            .unwrap_or((0, 0));
        match self.submit_atlas_animation_tick(gal)? {
            assets::animation_upload::UploadAttempt::PendingCompletion => Ok(false),
            assets::animation_upload::UploadAttempt::Accepted(submission) => {
                let (patch_count, patch_bytes) = pending_shape;
                self.atlas_animation_patch_uploads = self
                    .atlas_animation_patch_uploads
                    .saturating_add(u64::from(patch_bytes != 0));
                self.atlas_animation_patch_bytes =
                    self.atlas_animation_patch_bytes.saturating_add(patch_bytes);
                self.atlas_animation_empty_ticks = self
                    .atlas_animation_empty_ticks
                    .saturating_add(u64::from(patch_bytes == 0));
                self.trace_atlas_animation_upload(
                    texture_id,
                    event_generation,
                    event_tick,
                    submission,
                    patch_count,
                    patch_bytes,
                );
                self.trace_accepted_atlas_animation(gal, texture_id);
                self.pending_atlas_animation_event = None;
                Ok(true)
            }
        }
    }

    /// Emits only in an explicit graphics audit. This receipt is deliberately
    /// after the owned upload submission has been accepted, so it cannot imply
    /// that a queued or rejected patch reached the Vulkan texture.
    pub(in crate::render::worldrender) fn trace_atlas_animation_upload(
        &self,
        texture_id: u32,
        generation: u64,
        tick: u64,
        submission: Option<SubmissionId>,
        patch_count: u64,
        patch_bytes: u64,
    ) {
        use std::sync::{
            atomic::{AtomicUsize, Ordering},
            OnceLock,
        };
        static ENABLED: OnceLock<bool> = OnceLock::new();
        static LINES: AtomicUsize = AtomicUsize::new(0);
        if !*ENABLED.get_or_init(|| {
            matches!(
                std::env::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true")
            )
        }) {
            return;
        }
        if LINES
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |count| {
                (count < 2048).then_some(count + 1)
            })
            .is_err()
        {
            return;
        }
        let submission = submission.map_or_else(|| "none".to_string(), |id| id.0.to_string());
        eprintln!(
            "rust_gal_atlas_animation_upload texture={} generation={} tick={} patches={} bytes={} submission={} empty={} cumulative_uploads={} cumulative_bytes={} cumulative_empty_ticks={}",
            texture_id,
            generation,
            tick,
            patch_count,
            patch_bytes,
            submission,
            patch_bytes == 0,
            self.atlas_animation_patch_uploads,
            self.atlas_animation_patch_bytes,
            self.atlas_animation_empty_ticks,
        );
    }

    /// Bounded, opt-in observation of accepted owned state; never drives uploads.
    pub(in crate::render::worldrender) fn trace_accepted_atlas_animation(&mut self, gal: &VulkanicGal, texture_id: u32) {
        use std::sync::{
            atomic::{AtomicUsize, Ordering},
            OnceLock,
        };
        static SELECTED: OnceLock<Option<(u32, Vec<u32>)>> = OnceLock::new();
        static LINES: AtomicUsize = AtomicUsize::new(0);
        let selected = SELECTED.get_or_init(|| {
            if !matches!(
                std::env::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true")
            ) {
                return None;
            }
            // Preserve the existing single-sprite spelling; comma-separated IDs
            // opt into a bounded snapshot of several sprites in the same atlas.
            let sprites =
                Self::parse_atlas_trace_sprites(&std::env::var("MATTMC_ATLAS_TRACE_SPRITE").ok()?)?;
            let texture = match std::env::var("MATTMC_ATLAS_TRACE_TEXTURE") {
                Ok(value) => value.parse::<u32>().ok()?,
                Err(_) => WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
            };
            Some((texture, sprites))
        });
        let Some((selected_texture, ids)) = selected else {
            return;
        };
        if *selected_texture != texture_id {
            return;
        }
        self.retain_atlas_animation_observations(gal, texture_id, ids);
        // Retention continues after the log budget is exhausted. Capture paths
        // echo the latest bounded snapshot alongside the presentation evidence.
        for observation in &self.latest_atlas_animation_observations {
            if LINES
                .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |count| {
                    (count < 1024).then_some(count + 1)
                })
                .is_err()
            {
                break;
            }
            eprintln!("{observation}");
        }
    }

    pub(in crate::render::worldrender) fn parse_atlas_trace_sprites(value: &str) -> Option<Vec<u32>> {
        let mut ids = Vec::new();
        for part in value.split(',') {
            if ids.len() == 16 {
                return None;
            }
            let id = part.trim().parse::<u32>().ok()?;
            if id == 0 || ids.contains(&id) {
                return None;
            }
            ids.push(id);
        }
        Some(ids)
    }

    pub(in crate::render::worldrender) fn retain_atlas_animation_observations(
        &mut self,
        gal: &VulkanicGal,
        texture_id: u32,
        ids: &[u32],
    ) {
        // Replace the entire snapshot so a missing or replaced sprite can never
        // inherit a receipt from the previous accepted state.
        self.latest_atlas_animation_observations = ids
            .iter()
            .take(16)
            .filter_map(|id| self.accepted_atlas_animation_observation(gal, texture_id, *id))
            .collect();
        self.latest_atlas_animation_texture =
            (!self.latest_atlas_animation_observations.is_empty()).then_some(texture_id);
    }

    /// Accepted same-context texture metadata, never a backend/native image handle.
    /// Atlas interpolation changes pixels without changing this resource incarnation.
    pub(crate) fn accepted_gui_atlas_incarnation(
        &self,
        texture_id: u32,
    ) -> Option<crate::render::vulkanic::gui_atlas_reference::AcceptedAtlasIncarnation> {
        let asset = self.mesh_texture_assets.get(&texture_id)?;
        // Legacy independently animated image sheets are not stitched atlases.
        if asset.frame_count != 1
            || asset.animation_flags != 0
            || asset.frame_width != asset.width
            || asset.frame_height != asset.height
        {
            return None;
        }
        Some(crate::render::vulkanic::gui_atlas_reference::AcceptedAtlasIncarnation {
            texture_id,
            generation: asset.animation_generation,
            width: asset.width,
            height: asset.height,
        })
    }

    /// Private GUI sampling cannot bypass the world upload transaction. A future
    /// combined-frame consumer must explicitly order queued uploads before use.
    pub(crate) fn require_gui_atlas_upload_boundary(&self) -> GalResult<()> {
        if self.defer_world_uploads || !self.pending_world_upload_ops.is_empty() {
            return Err(GalError::invalid_argument(
                "GUI atlas sampling requires completed world upload recording",
            ));
        }
        Ok(())
    }

    /// Private GAL view of the owner's image. The caller owns only this view and
    /// must retire dependent GUI sets and this view before replacing the atlas.
    /// No pixel copy or second image is created when the owner is already resident.
    pub(crate) fn create_gui_atlas_view(
        &mut self,
        gal: &mut VulkanicGal,
        reference: crate::render::vulkanic::gui_atlas_reference::GuiAtlasReference,
    ) -> GalResult<Handle> {
        reference.validate()?;
        if self.accepted_gui_atlas_incarnation(reference.atlas.texture_id) != Some(reference.atlas)
        {
            return Err(GalError::ffi(
                StatusCode::StaleHandle,
                "GUI atlas reference does not name this accepted incarnation",
            ));
        }
        self.ensure_mesh_texture_resources(gal, reference.atlas.texture_id, "gui-owned-atlas")?;
        let resource = self
            .mesh_texture_resources
            .get(&reference.atlas.texture_id)
            .ok_or_else(|| GalError::invalid_argument("GUI atlas image was not prepared"))?;
        if resource.width != reference.atlas.width || resource.height != reference.atlas.height {
            return Err(GalError::invalid_argument(
                "GUI atlas image storage extent differs from its declaration",
            ));
        }
        gal.create_texture_view(TextureViewDesc {
            label: format!(
                "gui-atlas-{}-generation-{}",
                reference.asset_id, reference.atlas.generation
            ),
            texture: resource.texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: resource.mip_levels,
            base_layer: 0,
            layer_count: 1,
        })
    }

    pub(in crate::render::worldrender) fn accepted_atlas_animation_observation(
        &self,
        gal: &VulkanicGal,
        texture_id: u32,
        id: u32,
    ) -> Option<String> {
        let animation = self.staged_atlas_animations.get(&texture_id)?;
        let sprite = animation
            .sprites
            .iter()
            .find(|sprite| sprite.sprite_id == id)?;
        let atlas = self.mesh_texture_assets.get(&animation.texture_id)?;
        let mip_levels = self
            .mesh_texture_resources
            .get(&animation.texture_id)?
            .mip_levels;
        let (frame, sheet_frame, subframe, tick) = sprite.clock.diagnostic_state();
        let (retained_frame, retained_sheet_frame, retained_subframe, retained_tick) =
            sprite.clock.diagnostic_retained_pixel_state();
        let mut hashes = Vec::with_capacity(mip_levels as usize);
        for (mip, pixels) in std::iter::once(&atlas.rgba)
            .chain(&atlas.mip_rgba)
            .enumerate()
        {
            let mut hash = 0xcbf29ce484222325u64;
            let width = sprite.region.width >> mip;
            let height = sprite.region.height >> mip;
            if width == 0 || height == 0 {
                return None;
            }
            for row in (sprite.region.y >> mip)..(sprite.region.y >> mip) + height {
                let start = ((row as usize * (atlas.width >> mip) as usize)
                    + (sprite.region.x >> mip) as usize)
                    * 4;
                let row_pixels = pixels.get(start..start + width as usize * 4)?;
                for byte in row_pixels {
                    hash = (hash ^ u64::from(*byte)).wrapping_mul(0x100000001b3);
                }
            }
            hashes.push(format!("{hash:016x}"));
        }
        if hashes.len() != mip_levels as usize {
            return None;
        }
        let visible = self
            .pending_atlas_animation_event
            .as_ref()
            .is_some_and(|event| event.texture_id == texture_id && event.visible.contains(&id));
        Some(format!("atlas-animation-observation texture={} generation={} sprite={} tick={} frame={} sheet_frame={} subframe={} visible={} retained_rgba_fnv64={} accepted_submission={} mip_levels={} retained_mip_rgba_fnv64={} retained_frame={} retained_sheet_frame={} retained_subframe={} retained_tick={} cpu_retained_not_gpu_readback=true",
            animation.texture_id, animation.generation, id, tick, frame, sheet_frame, subframe,
            visible, hashes.first()?, gal.latest_submission_id().0, mip_levels, hashes.join(","),
            retained_frame, retained_sheet_frame, retained_subframe, retained_tick))
    }

    /// One uncommitted semantic texture tick at a time. Presentation frames
    /// must not synthesize ticks, and backpressure must not overwrite one.
    pub(in crate::render::worldrender) fn prepare_atlas_animation_tick(
        &mut self,
        texture_id: u32,
        tick: u64,
        visible: &std::collections::BTreeSet<u32>,
        animate_only_visible: bool,
    ) -> GalResult<()> {
        if self.pending_atlas_animation.is_some() {
            return Err(GalError::invalid_argument(
                "previous atlas animation tick is still pending",
            ));
        }
        let animation = self
            .staged_atlas_animations
            .get(&texture_id)
            .ok_or_else(|| GalError::invalid_argument("atlas animation is not staged"))?;
        let atlas = self
            .mesh_texture_assets
            .get(&animation.texture_id)
            .ok_or_else(|| GalError::invalid_argument("animation atlas incarnation is absent"))?;
        let mip_count = atlas.mip_rgba.len() + 1;
        let prepared = animation
            .prepare_tick(
                atlas.width,
                atlas.height,
                mip_count,
                tick,
                visible,
                animate_only_visible,
            )
            .map_err(|error| {
                let clock = animation
                    .sprites
                    .first()
                    .map(|sprite| sprite.clock.diagnostic_state());
                eprintln!(
                    "atlas-animation.prepare-rejected texture={} generation={} tick={} atlas={}x{} mips={} sprites={} first_clock={clock:?} error={error}",
                    animation.texture_id,
                    animation.generation,
                    tick,
                    atlas.width,
                    atlas.height,
                    mip_count,
                    animation.sprites.len(),
                );
                error
            })?;
        self.pending_atlas_animation = Some(prepared);
        Ok(())
    }

    /// Must execute outside a deferred world-upload recording transaction.
    /// Success is an actual GAL submission receipt, not a queued command list.
    pub(in crate::render::worldrender) fn submit_atlas_animation_tick(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<assets::animation_upload::UploadAttempt> {
        if self.defer_world_uploads || !self.pending_world_upload_ops.is_empty() {
            return Err(GalError::invalid_argument(
                "atlas tick cannot submit inside deferred world uploads",
            ));
        }
        if self.pending_atlas_animation.is_none() {
            return Err(GalError::invalid_argument(
                "no prepared atlas animation tick",
            ));
        }
        let texture_id = self
            .pending_atlas_animation
            .as_ref()
            .expect("validated pending tick")
            .texture_id();
        self.ensure_mesh_texture_resources(gal, texture_id, "atlas-animation")?;
        self.atlas_animation_uploads.submit(
            gal,
            self.mesh_texture_resources
                .get(&texture_id)
                .expect("ensured atlas"),
            self.staged_atlas_animations
                .get_mut(&texture_id)
                .expect("validated animation"),
            self.mesh_texture_assets
                .get_mut(&texture_id)
                .expect("staged atlas"),
            &mut self.pending_atlas_animation,
        )
    }

    pub(crate) fn stage_atlas_animation_assets(
        &mut self,
        update: crate::render::shared::sprite_interpolation::OwnedAtlasAnimationUpdate,
    ) -> GalResult<()> {
        let atlas = self
            .mesh_texture_assets
            .get(&update.texture_id)
            .ok_or_else(|| {
                GalError::invalid_argument("animation atlas has no owned texture incarnation")
            })?;
        let mip_levels = atlas.mip_rgba.len() + 1;
        if atlas.coordinate_origin != WorldMeshTextureCoordinateOrigin::Vulkanic {
            return Err(GalError::unsupported_feature(
                "animated atlas requires canonical resource rows",
            ));
        }
        if atlas.requested_mip_levels as usize != mip_levels
            || self
                .mesh_texture_resources
                .get(&update.texture_id)
                .is_some_and(|image| {
                    image.mip_levels as usize != mip_levels
                        || image.width != atlas.width
                        || image.height != atlas.height
                })
        {
            return Err(GalError::invalid_argument(
                "animated atlas requires its exact explicit mip allocation",
            ));
        }
        if update.generation != atlas.animation_generation
            || self
                .staged_atlas_animations
                .contains_key(&update.texture_id)
        {
            return Err(GalError::invalid_argument(
                "stale or duplicate atlas animation generation",
            ));
        }
        update.validate_for_atlas(atlas.width, atlas.height, atlas.mip_rgba.len() + 1)?;
        // The aggregate source budget does not grow with the number of atlases.
        // Validate before mutating any staged incarnation or pending event.
        let mut bytes = update.source_payload_bytes()?;
        let mut sprites = update.sprites.len();
        let (mut frames, mut mips) = update.source_metadata_counts()?;
        for animation in self.staged_atlas_animations.values() {
            bytes = bytes
                .checked_add(animation.source_payload_bytes()?)
                .ok_or_else(|| {
                    GalError::invalid_argument("animation registry byte count overflow")
                })?;
            let invalid =
                || GalError::invalid_argument("animation registry metadata count overflow");
            let (animation_frames, animation_mips) = animation.source_metadata_counts()?;
            sprites = sprites
                .checked_add(animation.sprites.len())
                .ok_or_else(invalid)?;
            frames = frames.checked_add(animation_frames).ok_or_else(invalid)?;
            mips = mips.checked_add(animation_mips).ok_or_else(invalid)?;
        }
        if !atlas_animation_registry_residency_fits(
            self.staged_atlas_animations.len() + 1,
            bytes,
            sprites,
            frames,
            mips,
        ) {
            return Err(GalError::invalid_argument(
                "animation registry residency bound exceeded",
            ));
        }
        self.staged_atlas_animations
            .insert(update.texture_id, update);
        Ok(())
    }
}
