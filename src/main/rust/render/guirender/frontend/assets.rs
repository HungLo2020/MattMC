//! Asset updates: bundled sprite overrides and raw GUI images.

use super::*;

#[derive(Clone, Eq, PartialEq)]
pub(super) struct RawGuiImage {
    pub(super) format: GuiRawImageFormat,
    pub(super) width: u32,
    pub(super) height: u32,
    pub(super) pixels: Vec<u8>,
    pub(super) sampling: Option<(SamplerFilter, SamplerAddressMode)>,
}

impl GuiFrontend {
    pub fn apply_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<GuiAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI asset generation must be non-zero",
            ));
        }
        if generation <= self.asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale GUI asset generation {generation}; current generation is {}",
                    self.asset_generation
                ),
            ));
        }
        let mut overrides = BTreeMap::new();
        for payload in payloads {
            let def = sprite_def(payload.sprite_id)?;
            if payload.png_bytes.is_empty() {
                continue;
            }
            if overrides.contains_key(&payload.sprite_id) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate GUI asset payload for sprite id {}",
                        payload.sprite_id
                    ),
                ));
            }
            let _ = decode_sprite_bytes(def, &payload.png_bytes)?;
            overrides.insert(payload.sprite_id, payload.png_bytes);
        }
        for def in SPRITES {
            // Rust-owned procedural sprites have no vanilla pack payload. They
            // are synthesized by `load_sprite` and must not participate in
            // bundled-asset validation or override generation checks.
            if def.id == GUI_POST_EFFECT_INVERT_ID
                || def.id == GUI_POST_EFFECT_CREEPER_ID
                || def.id == GUI_POST_EFFECT_SPIDER_ID
            {
                continue;
            }
            let bytes = overrides
                .get(&def.id)
                .map(Vec::as_slice)
                .or_else(|| bundled_sprite_bytes(def.path))
                .ok_or_else(|| {
                    GalError::backend(format!("missing bundled GUI sprite '{}'", def.path))
                })?;
            let _ = decode_sprite_bytes(def, bytes)?;
        }
        self.asset_generation = generation;
        self.asset_overrides = overrides;
        self.destroy_render_resources(gal);
        Ok(())
    }

    /// Replaces the complete dynamic GUI image generation atomically. The caller
    /// retains no ownership after this returns; malformed images leave the last
    /// valid generation intact.
    pub fn apply_raw_image_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<GuiRawImageAssetPayload>,
    ) -> GalResult<()> {
        self.apply_raw_image_patch(gal, generation, payloads, Vec::new())
    }

    /// A nonempty manifest retains resident identities without copying their pixels.
    /// All admission checks precede eviction or resource retirement, so failures are retryable.
    pub fn apply_raw_image_patch(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<GuiRawImageAssetPayload>,
        retained_asset_ids: Vec<u64>,
    ) -> GalResult<()> {
        if generation == 0 || generation <= self.raw_image_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale raw GUI image generation {generation}; current generation is {}",
                    self.raw_image_generation
                ),
            ));
        }
        if payloads.len() > GUI_MAX_RAW_IMAGES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "raw GUI image payload count {} exceeds bounded limit {GUI_MAX_RAW_IMAGES}",
                    payloads.len()
                ),
            ));
        }
        let mut images = BTreeMap::new();
        let mut total_bytes = 0usize;
        for payload in payloads {
            if payload.asset_id == 0
                || payload.width == 0
                || payload.height == 0
                || payload.width > 8192
                || payload.height > 8192
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "invalid raw GUI image {} dimensions {}x{}",
                        payload.asset_id, payload.width, payload.height
                    ),
                ));
            }
            let pixel_count = (payload.width as usize)
                .checked_mul(payload.height as usize)
                .ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "raw GUI image pixel count overflows",
                    )
                })?;
            if pixel_count > GUI_MAX_RAW_IMAGE_PIXELS {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "raw GUI image {} has {pixel_count} pixels; maximum is {GUI_MAX_RAW_IMAGE_PIXELS}",
                        payload.asset_id
                    ),
                ));
            }
            let expected = (payload.width as usize)
                .checked_mul(payload.height as usize)
                .and_then(|pixels| pixels.checked_mul(payload.format.bytes_per_pixel()))
                .ok_or_else(|| {
                    GalError::ffi(StatusCode::InvalidArgument, "raw GUI image size overflows")
                })?;
            if payload.pixels.len() != expected {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "raw GUI image {} has {} bytes; expected {expected}",
                        payload.asset_id,
                        payload.pixels.len()
                    ),
                ));
            }
            // Admission bounds the expanded resident bytes before any map allocation.
            let resident_format = payload.format.resident_format();
            let resident_bytes = pixel_count.checked_mul(resident_format.bytes_per_pixel()).ok_or_else(|| {
                GalError::ffi(StatusCode::InvalidArgument, "raw GUI resident image size overflows")
            })?;
            total_bytes = total_bytes.checked_add(resident_bytes).ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "raw GUI image aggregate byte count overflows",
                )
            })?;
            if total_bytes > GUI_MAX_RAW_IMAGE_BYTES_TOTAL {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "raw GUI image aggregate bytes {total_bytes} exceed bounded limit {GUI_MAX_RAW_IMAGE_BYTES_TOTAL}"
                    ),
                ));
            }
            let pixels = match payload.format {
                GuiRawImageSourceFormat::MapColor8 => crate::content::map_color::expand_rgba(&payload.pixels),
                GuiRawImageSourceFormat::Alpha8 | GuiRawImageSourceFormat::Rgba8 => payload.pixels,
            };
            if images
                .insert(
                    payload.asset_id,
                    RawGuiImage {
                        sampling: payload.sampling,
                        format: resident_format,
                        width: payload.width,
                        height: payload.height,
                        pixels,
                    },
                )
                .is_some()
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "duplicate raw GUI image asset id",
                ));
            }
        }
        if images.keys().any(|id| self.atlas_references.contains(*id)) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "raw GUI image collides with explicit atlas reference",
            ));
        }
        if retained_asset_ids.is_empty() {
            let changed_assets = self.changed_raw_image_assets(&images);
            self.destroy_dynamic_resources_for_assets(gal, &changed_assets);
            self.raw_images = images;
        } else {
            if retained_asset_ids.len() > GUI_MAX_RAW_IMAGES {
                return Err(GalError::invalid_argument("raw GUI retained identity bound exceeded"));
            }
            let retained: BTreeSet<_> = retained_asset_ids.iter().copied().collect();
            if retained.contains(&0) || retained.len() != retained_asset_ids.len()
                || images.keys().any(|id| !retained.contains(id)) {
                return Err(GalError::invalid_argument("invalid raw GUI retained identity manifest"));
            }
            let mut resident_bytes = 0usize;
            for id in &retained {
                let image = images.get(id).or_else(|| self.raw_images.get(id))
                    .ok_or_else(|| GalError::invalid_argument("retained raw GUI image has no resident or incoming pixels"))?;
                resident_bytes = resident_bytes.checked_add(image.pixels.len())
                    .ok_or_else(|| GalError::invalid_argument("raw GUI resident byte count overflow"))?;
            }
            if resident_bytes > GUI_MAX_RAW_IMAGE_BYTES_TOTAL {
                return Err(GalError::invalid_argument("raw GUI resident byte bound exceeded"));
            }
            let changed_assets: BTreeSet<_> = self.raw_images.keys().filter(|id| !retained.contains(id)).copied()
                .chain(images.iter().filter(|(id, image)| self.raw_images.get(id) != Some(*image)).map(|(id, _)| *id))
                .collect();
            self.destroy_dynamic_resources_for_assets(gal, &changed_assets);
            self.raw_images.retain(|id, _| retained.contains(id));
            self.raw_images.append(&mut images);
        }
        self.raw_image_generation = generation;
        Ok(())
    }

    pub(super) fn changed_raw_image_assets(&self, next: &BTreeMap<u64, RawGuiImage>) -> BTreeSet<u64> {
        self.raw_images
            .keys()
            .chain(next.keys())
            .copied()
            .collect::<BTreeSet<_>>()
            .into_iter()
            .filter(|asset_id| self.raw_images.get(asset_id) != next.get(asset_id))
            .collect()
    }

    pub(super) fn destroy_dynamic_resources_for_assets(
        &mut self,
        gal: &mut VulkanicGal,
        asset_ids: &BTreeSet<u64>,
    ) {
        let keys: Vec<_> = self
            .resources
            .keys()
            .copied()
            .filter(|key| {
                key.group
                    .dynamic_asset_id()
                    .is_some_and(|asset_id| asset_ids.contains(&asset_id))
            })
            .collect();
        let mesh_rasters = std::mem::take(&mut self.mesh_rasters);
        for (key, resources) in mesh_rasters {
            if asset_ids.contains(&key.asset_id) {
                resources.destroy_asset_resources(gal);
            } else {
                self.mesh_rasters.insert(key, resources);
            }
        }
        let mut texture_keys = BTreeSet::new();
        for key in keys {
            if let Some(resource) = self.resources.remove(&key) {
                if let GuiImageOwnership::SharedRaw { key } = resource.image_ownership {
                    texture_keys.insert(key);
                }
                for handle in resource.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
        // Partial image replacement has the same dependency order as full
        // teardown: mesh descriptor sets must release their sampled views
        // and samplers before the shared texture ownership records are removed.
        for texture_key in texture_keys {
            if let Some(texture) = self.dynamic_textures.remove(&texture_key) {
                for handle in [
                    texture.texture_view,
                    texture.linear_sampler,
                    texture.nearest_sampler,
                    texture.texture,
                    texture.upload_buffer,
                ] {
                    let _ = gal.retire(handle);
                }
            }
        }
        // Keep submitted leases until the shared allocator observes completion.
        // Their transaction keys prevent reuse as a newly replaced asset.

    }
}
