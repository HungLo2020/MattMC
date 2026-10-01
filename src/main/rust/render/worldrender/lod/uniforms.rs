//! Per-draw LOD uniforms and the packed uniform slots shared across draws.

use crate::render::worldrender::lod::*;

pub(super) fn packed_lod_uniforms_enabled() -> bool {
    !matches!(
        std::env::var("MATTMC_RUST_DH_PACKED_UNIFORMS").as_deref(),
        Ok("0") | Ok("false") | Ok("FALSE") | Ok("off") | Ok("OFF")
    )
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct WorldLodDrawUniform {
    pub combined_matrix: [f32; 16],
    pub column_origin_and_world_y: [f32; 4],
    /// Distant Horizons keeps its per-column geometry local and supplies the
    /// camera-relative column origin independently of its raw model-view
    /// matrix. `world_y_offset` remains retained in the trailing lane for
    /// source-pack semantics and diagnostics; it is not applied to the
    /// already dimension-local column origin a second time.
    pub model_offset_and_reserved: [f32; 4],
    pub clip_micro_noise_earth: [f32; 4],
    pub flags_and_noise: [u32; 4],
    /// Copied vanilla fog color/alpha and range semantics. These are filled
    /// at the whole-frame material boundary, never sourced from Java GL or
    /// shader-pack state.
    pub fog_color_and_alpha: [f32; 4],
    pub fog_ranges: [f32; 4],
    pub dh_fog_parameters: [f32; 20],
}

impl WorldLodDrawUniform {
    pub(crate) fn from_semantics(
        frame: &WorldLodRenderFrame,
        draw: WorldLodGpuDraw,
    ) -> GalResult<Self> {
        Self::from_semantics_with_camera(frame, draw, [0.0; 3])
    }

    pub(crate) fn from_semantics_with_camera(
        frame: &WorldLodRenderFrame,
        draw: WorldLodGpuDraw,
        camera_world_position: [f32; 3],
    ) -> GalResult<Self> {
        if !frame.enabled {
            return Err(GalError::invalid_argument(
                "world LOD draw uniform requires an enabled semantic render frame",
            ));
        }
        if frame.flags & !0xff != 0
            || frame.micro_offset <= 0.0
            || frame.clip_distance < 0.0
            || frame
                .combined_matrix
                .iter()
                .chain([
                    &frame.clip_distance,
                    &frame.micro_offset,
                    &frame.noise_intensity,
                    &frame.earth_radius,
                ])
                .any(|value| !value.is_finite())
            || camera_world_position.iter().any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "world LOD draw uniform received invalid frame semantics",
            ));
        }
        Ok(Self {
            combined_matrix: frame.combined_matrix,
            column_origin_and_world_y: [
                draw.origin[0] as f32,
                draw.origin[1] as f32,
                draw.origin[2] as f32,
                frame.world_y_offset as f32,
            ],
            model_offset_and_reserved: [
                draw.origin[0] as f32 - camera_world_position[0],
                draw.origin[1] as f32 - camera_world_position[1],
                draw.origin[2] as f32 - camera_world_position[2],
                draw.vertex_base as f32,
            ],
            clip_micro_noise_earth: [
                frame.clip_distance,
                frame.micro_offset,
                frame.noise_intensity,
                frame.earth_radius,
            ],
            flags_and_noise: [
                frame.flags,
                frame.noise_steps,
                frame.noise_dropoff as u32,
                0,
            ],
            fog_color_and_alpha: [0.0; 4],
            fog_ranges: [0.0; 4],
            dh_fog_parameters: frame.dh_fog_parameters,
        })
    }

    pub(crate) fn with_fog(mut self, fog_color_and_alpha: [f32; 4], fog_ranges: [f32; 4]) -> Self {
        self.fog_color_and_alpha = fog_color_and_alpha;
        self.fog_ranges = fog_ranges;
        self
    }

    pub(crate) fn without_dh_fog(mut self) -> Self {
        self.dh_fog_parameters[16] = 0.0;
        self
    }

    /// Capture-only probe for the legacy DH Vulkan Y clip convention.  The
    /// private direct compositor keeps this bit in the owned frame block so
    /// the production route and all shader-pack/source routes remain
    /// unchanged when the probe is disabled.
    pub(crate) fn with_private_audit_flip_y(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] = 1;
        }
        self
    }

    /// Capture-only probe for the OpenGL-to-Vulkan clip-depth remap.  Bit 1
    /// shares the existing private audit lane; normal frames leave it clear
    /// and retain the backend's explicit zero-to-one conversion.
    pub(crate) fn with_private_audit_no_depth_remap(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 2;
        }
        self
    }

    /// Capture-only probe that paints each submitted DH column with a stable
    /// origin-derived color. It is restricted to the private direct route by
    /// the caller and leaves the production material contract unchanged.
    pub(crate) fn with_private_audit_column_ids(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 4;
        }
        self
    }

    /// Capture-only probe that bypasses the copied DH distance fade.  This is
    /// restricted to the private direct route and exists only to distinguish
    /// fragment discard coverage from missing source geometry.
    pub(crate) fn with_private_audit_no_fade(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 8;
        }
        self
    }

    /// Capture-only probe for the reduced-color DH transparent material. The
    /// fragment shader uses this bit to bypass lightmap/noise/fog color while
    /// retaining the normal raster coverage and blend state. It is never set
    /// on a normal frame.
    pub(crate) fn with_private_audit_raw_transparent_color(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 16;
        }
        self
    }

    /// Capture-only probe that paints reduced-color DH water fragments a
    /// stable diagnostic green. This distinguishes water coverage/blending
    /// from its copied source color without changing pass ownership or depth
    /// policy. It is never set on a normal frame.
    pub(crate) fn with_private_audit_water_debug_color(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 32;
        }
        self
    }

    /// Capture-only probe that outputs the sampled lightmap color from the
    /// generic transparent DH material before vertex-color modulation. It
    /// preserves coverage, depth, and blend state and is never set normally.
    pub(crate) fn with_private_audit_raw_lightmap_color(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 64;
        }
        self
    }

    /// Capture-only probe for provenance-resolved DH atlas sampling. It
    /// outputs the sampled sprite color before semantic lighting and fog.
    pub(crate) fn with_private_audit_raw_exact_atlas_color(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 128;
        }
        self
    }

    /// Capture-only probe that forces exact-atlas samples to the copied base
    /// mip.  Semantic atlas snapshots retain per-sprite mip rows, but a
    /// distant merged face can select an atlas mip whose sprite footprint is
    /// smaller than one texel.  This bit isolates that sampling boundary
    /// without changing the normal sampler contract.
    pub(crate) fn with_private_audit_exact_atlas_base_mip(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 256;
        }
        self
    }

    /// Capture-only probe for the source OpenGL Bayer phase.  Vulkan's
    /// fragment origin and the shared negative viewport can make the Y phase
    /// ambiguous even when geometry is aligned.  Bit 512 selects the direct
    /// `gl_FragCoord.y` phase; normal frames leave it clear and retain the
    /// production Vulkan-to-OpenGL mapping.
    pub(crate) fn with_private_audit_dither_y(mut self, enabled: bool) -> Self {
        if enabled {
            self.flags_and_noise[3] |= 512;
        }
        self
    }

    /// Fixed backend-neutral buffer layout for the first Rust-owned LOD
    /// material pass. The layout consists of one matrix, three vec4-aligned
    /// float blocks, and one uvec4 block, so both Vulkan std140 and the OpenGL
    /// compatibility backend can bind the same owned bytes without decoding
    /// any DH or Java renderer state.
    pub(crate) fn pack_std140(self) -> [u8; 240] {
        let mut bytes = [0u8; 240];
        let mut offset = 0usize;
        for value in self
            .combined_matrix
            .iter()
            .chain(self.column_origin_and_world_y.iter())
            .chain(self.model_offset_and_reserved.iter())
            .chain(self.clip_micro_noise_earth.iter())
        {
            bytes[offset..offset + std::mem::size_of::<f32>()]
                .copy_from_slice(&value.to_ne_bytes());
            offset += std::mem::size_of::<f32>();
        }
        for value in self.flags_and_noise {
            bytes[offset..offset + std::mem::size_of::<u32>()]
                .copy_from_slice(&value.to_ne_bytes());
            offset += std::mem::size_of::<u32>();
        }
        for value in self
            .fog_color_and_alpha
            .iter()
            .chain(self.fog_ranges.iter())
            .chain(self.dh_fog_parameters.iter())
        {
            bytes[offset..offset + std::mem::size_of::<f32>()]
                .copy_from_slice(&value.to_ne_bytes());
            offset += std::mem::size_of::<f32>();
        }
        debug_assert_eq!(offset, bytes.len());
        bytes
    }

    /// Packs the reduced source-derived DH ABI. Lowered shader-pack source
    /// programs consume only the first five std140 blocks (matrix, origin,
    /// camera-relative offset, fade controls, and flags); fog and DH fog
    /// fields belong to the ordinary Rust-owned material pass and must not be
    /// written past the source descriptor's 128-byte contract.
    pub(crate) fn pack_source_std140(self) -> [u8; 128] {
        let mut bytes = [0u8; 128];
        let mut offset = 0usize;
        for value in self
            .combined_matrix
            .iter()
            .chain(self.column_origin_and_world_y.iter())
            .chain(self.model_offset_and_reserved.iter())
            .chain(self.clip_micro_noise_earth.iter())
        {
            bytes[offset..offset + std::mem::size_of::<f32>()]
                .copy_from_slice(&value.to_ne_bytes());
            offset += std::mem::size_of::<f32>();
        }
        for value in self.flags_and_noise {
            bytes[offset..offset + std::mem::size_of::<u32>()]
                .copy_from_slice(&value.to_ne_bytes());
            offset += std::mem::size_of::<u32>();
        }
        debug_assert_eq!(offset, bytes.len());
        bytes
    }
}

/// One Rust-owned upload target for a material pass. Draw offsets are assigned
/// afresh each frame, while the GPU buffer and geometry descriptor sets remain
/// stable across frames. Updates are recorded before all LOD draws on the same
/// queue, so reuse cannot race a prior submitted frame.
pub(super) struct WorldLodPackedUniforms {
    pub(in crate::render::worldrender::lod) buffer: Handle,
    pub(in crate::render::worldrender::lod) stride: usize,
    pub(in crate::render::worldrender::lod) bytes: Vec<u8>,
    pub(in crate::render::worldrender::lod) last_uploaded_bytes: Vec<u8>,
}

impl WorldLodPackedUniforms {
    pub(super) fn new(gal: &mut VulkanicGal, label: &str) -> GalResult<Self> {
        let alignment = usize::try_from(
            gal.capabilities()
                .limits
                .uniform_buffer_offset_alignment
                .max(1),
        )
        .map_err(|_| GalError::invalid_argument("LOD uniform alignment exceeds host size"))?;
        let stride = PACKED_LOD_UNIFORM_BYTES
            .checked_add(alignment - 1)
            .map(|value| value / alignment * alignment)
            .ok_or_else(|| GalError::invalid_argument("LOD uniform stride overflow"))?;
        let size = stride
            .checked_mul(MAX_PACKED_LOD_UNIFORM_SLOTS)
            .ok_or_else(|| GalError::invalid_argument("LOD uniform arena size overflow"))?;
        if size as u64 > gal.capabilities().limits.max_buffer_size {
            return Err(GalError::invalid_argument(
                "LOD uniform arena exceeds backend buffer limit",
            ));
        }
        let buffer = gal.create_buffer(BufferDesc {
            label: format!("{label}.packed-frame-uniforms"),
            size: size as u64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
        })?;
        Ok(Self {
            buffer,
            stride,
            bytes: Vec::with_capacity(stride * 1024),
            last_uploaded_bytes: Vec::new(),
        })
    }

    pub(super) fn begin_frame(&mut self) {
        self.bytes.clear();
    }

    pub(super) fn push(&mut self, packed: [u8; PACKED_LOD_UNIFORM_BYTES]) -> GalResult<u64> {
        let slot = self.bytes.len() / self.stride;
        if slot >= MAX_PACKED_LOD_UNIFORM_SLOTS {
            return Err(GalError::invalid_argument(
                "LOD packed uniform slots exceed visible segment limit",
            ));
        }
        let offset = self.bytes.len() as u64;
        self.bytes.extend_from_slice(&packed);
        self.bytes.resize((slot + 1) * self.stride, 0);
        Ok(offset)
    }

    pub(super) fn flush(&mut self, ops: &mut Vec<CommandOp>) {
        if self.bytes.is_empty() {
            return;
        }
        let changed_chunks = self
            .bytes
            .chunks(MAX_INLINE_BUFFER_UPDATE_BYTES)
            .enumerate()
            .filter_map(|(index, chunk)| {
                let start = index * MAX_INLINE_BUFFER_UPDATE_BYTES;
                (self.last_uploaded_bytes.get(start..start + chunk.len()) != Some(chunk))
                    .then_some(index)
            })
            .collect::<Vec<_>>();
        if changed_chunks.is_empty() {
            std::mem::swap(&mut self.bytes, &mut self.last_uploaded_bytes);
            return;
        }
        ops.push(CommandOp::Barrier(buffer_barrier(
            self.buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        for index in changed_chunks {
            let start = index * MAX_INLINE_BUFFER_UPDATE_BYTES;
            let end = (start + MAX_INLINE_BUFFER_UPDATE_BYTES).min(self.bytes.len());
            ops.push(CommandOp::HostWriteBuffer {
                buffer: self.buffer,
                offset: start as u64,
                data: self.bytes[start..end].to_vec(),
            });
        }
        ops.push(CommandOp::Barrier(buffer_barrier(
            self.buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        std::mem::swap(&mut self.bytes, &mut self.last_uploaded_bytes);
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        let _ = gal.destroy(self.buffer);
    }
}
