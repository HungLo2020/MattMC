//! Validation of resource-set bindings against layouts, stages and buffer ranges.

use super::*;

impl VulkanicGal {
    pub(super) fn validate_binding_resource(&mut self, binding: &ResourceBinding) -> GalResult<()> {
        match binding.kind {
            ResourceBindingKind::UniformBuffer => {
                let record = self.buffers.get(binding.resource)?;
                if !record.desc.usages.contains(&BufferUsage::Uniform) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "uniform buffer binding requires uniform buffer usage",
                    ));
                }
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "uniform buffer binding cannot declare write access",
                    ));
                }
            }
            ResourceBindingKind::StorageBuffer => {
                let record = self.buffers.get(binding.resource)?;
                if !record.desc.usages.contains(&BufferUsage::Storage) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "storage buffer binding requires storage buffer usage",
                    ));
                }
            }
            ResourceBindingKind::SampledTexture => {
                let info = self.texture_view_info(binding.resource)?;
                if !info.usages.contains(&TextureUsage::Sampled) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "sampled texture binding requires sampled texture usage",
                    ));
                }
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "sampled texture binding cannot declare write access",
                    ));
                }
            }
            ResourceBindingKind::CombinedTextureSampler => {
                let pair = self.combined_texture_samplers.get(binding.resource)?;
                let view = self.texture_view_info(pair.desc.texture_view)?;
                if !view.usages.contains(&TextureUsage::Sampled) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "combined texture sampler requires sampled texture usage",
                    ));
                }
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "combined texture sampler binding cannot declare write access",
                    ));
                }
            }
            ResourceBindingKind::StorageTexture => {
                let info = self.texture_view_info(binding.resource)?;
                if !info.usages.contains(&TextureUsage::Storage) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "storage texture binding requires storage texture usage",
                    ));
                }
            }
            ResourceBindingKind::Sampler => {
                self.samplers.get(binding.resource)?;
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "sampler binding cannot declare write access",
                    ));
                }
            }
        }
        Ok(())
    }

    pub(super) fn validate_integer_sampled_texture_sampler_pairing(
        &mut self,
        bindings: &[ResourceBinding],
    ) -> GalResult<()> {
        // The current semantic resource-set model exposes samplers separately
        // from sampled textures. Both native backends bind those sampler
        // objects to the sampled set, so an integer texture cannot safely
        // coexist with linear sampler state in that set. Keep the restriction
        // here until the ABI gains an explicit texture-to-sampler association.
        let has_integer_sampled_texture = bindings.iter().any(|binding| {
            binding.kind == ResourceBindingKind::SampledTexture
                && self
                    .texture_view_info(binding.resource)
                    .map(|info| info.format == TextureFormat::R8Uint)
                    .unwrap_or(false)
        });
        if !has_integer_sampled_texture {
            return Ok(());
        }
        for binding in bindings {
            if binding.kind != ResourceBindingKind::Sampler {
                continue;
            }
            let sampler = self.samplers.get(binding.resource)?;
            if sampler.desc.min_filter != SamplerFilter::Nearest
                || sampler.desc.mag_filter != SamplerFilter::Nearest
                || sampler.desc.mip_filter != SamplerFilter::Nearest
            {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "integer sampled textures require nearest min, mag, and mip sampler filters in the same resource set",
                ));
            }
        }
        Ok(())
    }

    pub(super) fn validate_resource_binding_buffer_range(
        &mut self,
        binding: &ResourceBinding,
        offsets: &[u64],
    ) -> GalResult<()> {
        match self.check_resource_binding_buffer_range(binding, offsets) {
            Err(error) if error.code == StatusCode::InvalidArgument => self.validation_error(error),
            other => other,
        }
    }

    /// Pure range check shared by resource-set creation and bind validation;
    /// callers route a failure through `validation_error`.
    pub(super) fn check_resource_binding_buffer_range(
        &self,
        binding: &ResourceBinding,
        offsets: &[u64],
    ) -> GalResult<()> {
        if binding.buffer_range.is_some()
            && !matches!(
                binding.kind,
                ResourceBindingKind::UniformBuffer | ResourceBindingKind::StorageBuffer
            )
        {
            return Err(GalError::resource(
                StatusCode::InvalidArgument,
                format!(
                    "binding {} buffer range is only valid for buffer bindings",
                    binding.binding
                ),
            ));
        }
        if !matches!(
            binding.kind,
            ResourceBindingKind::UniformBuffer | ResourceBindingKind::StorageBuffer
        ) {
            return Ok(());
        }
        let record = self.buffers.get(binding.resource)?;
        let range = binding.buffer_range.unwrap_or_else(|| {
            let max_default_offset = binding.dynamic_offsets.iter().copied().max().unwrap_or(0);
            record.desc.size.saturating_sub(max_default_offset)
        });
        if range == 0 {
            return Err(GalError::resource(
                StatusCode::InvalidArgument,
                format!("binding {} buffer range must be non-zero", binding.binding),
            ));
        }
        let uniform_alignment = self.capabilities().limits.uniform_buffer_offset_alignment.max(1);
        for offset in offsets {
            if binding.kind == ResourceBindingKind::UniformBuffer {
                let alignment = uniform_alignment;
                if offset % alignment != 0 {
                    return Err(GalError::resource(
                        StatusCode::InvalidArgument,
                        format!(
                            "binding {} uniform buffer offset {} is not aligned to {} bytes",
                            binding.binding, offset, alignment
                        ),
                    ));
                }
            }
            let Some(end) = offset.checked_add(range) else {
                return Err(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("binding {} dynamic buffer range overflows", binding.binding),
                ));
            };
            if end > record.desc.size {
                return Err(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!(
                        "binding {} dynamic buffer range is outside the buffer",
                        binding.binding
                    ),
                ));
            }
        }
        Ok(())
    }

    pub(super) fn require_shader_stage(&self, handle: Handle, stage: ShaderStage) -> GalResult<()> {
        let shader = self.shaders.get(handle)?;
        if shader.desc.stage != stage {
            return Err(GalError::resource(
                StatusCode::InvalidArgument,
                format!(
                    "shader stage mismatch: expected {stage:?}, got {:?}",
                    shader.desc.stage
                ),
            ));
        }
        Ok(())
    }
}
