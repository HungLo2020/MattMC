//! Rust-owned semantic reconstruction of Minecraft's dynamic 16x16 lightmap.
//!
//! The source of truth is Minecraft's `core/lightmap.fsh`. Java supplies its
//! gameplay-derived scalar inputs in a later transport slice; neither a Java
//! GPU texture nor a renderer-owned texture view is part of this contract.

use crate::render::vulkanic::commands::{
    BufferImageCopyRegion, CommandOp, ResourceBarrier, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BufferDesc, BufferUsage, CombinedTextureSamplerDesc, Extent3d, MemoryDomain,
    QueueClass, ResourceBinding, ResourceBindingKind, ResourceSetDesc, SamplerAddressMode,
    SamplerDesc, SamplerFilter, TextureDesc, TextureDimension, TextureFormat, TextureUsage,
    TextureViewDesc,
};
use std::collections::BTreeMap;

use super::terrain_source_resources::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailability,
    TerrainSourceResourceAvailabilitySet, TerrainSourceResourceRole,
    TerrainSourceSampledResourceShape,
};

pub const VANILLA_LIGHTMAP_WIDTH: usize = 16;
pub const VANILLA_LIGHTMAP_HEIGHT: usize = 16;
pub const VANILLA_LIGHTMAP_RGBA_BYTES: usize = VANILLA_LIGHTMAP_WIDTH * VANILLA_LIGHTMAP_HEIGHT * 4;

/// Exact semantic fields in Minecraft's `LightmapInfo` uniform block. The
/// values are scalar gameplay/environment inputs, not GL uniforms or texture
/// state. Color values may legitimately exceed one during an end flash, so
/// validation only requires finite non-negative components.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VanillaLightmapInputs {
    pub ambient_light_factor: f32,
    pub sky_factor: f32,
    pub block_factor: f32,
    pub night_vision_factor: f32,
    pub darkness_scale: f32,
    pub darken_world_factor: f32,
    pub brightness_factor: f32,
    pub sky_light_color: [f32; 3],
    pub ambient_color: [f32; 3],
}

/// Generation-bound frame transport for one dynamic vanilla lightmap update.
/// A generation of zero is never valid when the frame is present.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct VanillaLightmapFrame {
    pub generation: u64,
    pub inputs: VanillaLightmapInputs,
}

impl VanillaLightmapFrame {
    pub fn validate(self) -> GalResult<()> {
        if self.generation == 0 {
            return Err(GalError::invalid_argument(
                "vanilla lightmap frame generation must be non-zero",
            ));
        }
        self.inputs.validate()
    }
}

impl VanillaLightmapInputs {
    pub fn validate(self) -> GalResult<()> {
        let scalars = [
            self.ambient_light_factor,
            self.sky_factor,
            self.block_factor,
            self.night_vision_factor,
            self.darkness_scale,
            self.darken_world_factor,
            self.brightness_factor,
        ];
        if scalars
            .iter()
            .any(|value| !value.is_finite() || *value < 0.0)
            || self
                .sky_light_color
                .into_iter()
                .chain(self.ambient_color)
                .any(|value| !value.is_finite() || value < 0.0)
        {
            return Err(GalError::invalid_argument(
                "vanilla lightmap inputs must be finite and non-negative",
            ));
        }
        Ok(())
    }

    /// Generates the complete RGBA8 source image. Coordinates follow the
    /// vanilla lightmap convention: X is block light and Y is sky light.
    pub fn rgba8(self) -> GalResult<[u8; VANILLA_LIGHTMAP_RGBA_BYTES]> {
        self.validate()?;
        let mut pixels = [0u8; VANILLA_LIGHTMAP_RGBA_BYTES];
        for sky_light in 0..VANILLA_LIGHTMAP_HEIGHT {
            for block_light in 0..VANILLA_LIGHTMAP_WIDTH {
                let color = self.texel(block_light as u8, sky_light as u8)?;
                let offset = (sky_light * VANILLA_LIGHTMAP_WIDTH + block_light) * 4;
                pixels[offset] = unorm8(color[0]);
                pixels[offset + 1] = unorm8(color[1]);
                pixels[offset + 2] = unorm8(color[2]);
                pixels[offset + 3] = u8::MAX;
            }
        }
        Ok(pixels)
    }

    /// Evaluates one exact lightmap texel before UNORM8 conversion.
    pub fn texel(self, block_light: u8, sky_light: u8) -> GalResult<[f32; 3]> {
        self.validate()?;
        if block_light >= VANILLA_LIGHTMAP_WIDTH as u8 || sky_light >= VANILLA_LIGHTMAP_HEIGHT as u8
        {
            return Err(GalError::invalid_argument(format!(
                "vanilla lightmap coordinate ({block_light}, {sky_light}) is outside 16x16"
            )));
        }
        let block_brightness = brightness(block_light as f32 / 15.0) * self.block_factor;
        let sky_brightness = brightness(sky_light as f32 / 15.0) * self.sky_factor;
        let mut color = [
            block_brightness,
            block_brightness * ((block_brightness * 0.6 + 0.4) * 0.6 + 0.4),
            block_brightness * (block_brightness * block_brightness * 0.6 + 0.4),
        ];
        color = mix(color, self.ambient_color, self.ambient_light_factor);
        color = add(color, scale(self.sky_light_color, sky_brightness));
        color = mix(color, [0.75; 3], 0.04);

        if self.ambient_light_factor == 0.0 {
            color = mix(
                color,
                multiply(color, [0.7, 0.6, 0.6]),
                self.darken_world_factor,
            );
        }
        if self.night_vision_factor > 0.0 {
            let max_component = color.into_iter().fold(0.0f32, f32::max);
            if max_component > 0.0 && max_component < 1.0 {
                color = mix(
                    color,
                    scale(color, 1.0 / max_component),
                    self.night_vision_factor,
                );
            }
        }
        if self.ambient_light_factor == 0.0 {
            color = add(color, [-self.darkness_scale; 3]);
        }
        color = color.map(|component| component.clamp(0.0, 1.0));
        color = mix(color, not_gamma(color), self.brightness_factor);
        Ok(mix(color, [0.75; 3], 0.04))
    }
}

/// Rust-owned copied representation of the dynamic vanilla lightmap for one
/// world generation. This is deliberately a byte cache rather than a backend
/// image: the later shader-runtime resource owner can upload only a coherent,
/// immutable generation without retaining Java-owned lightmap state.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct VanillaLightmapCache {
    world_generation: u64,
    lightmap_generation: u64,
    rgba8: Vec<u8>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum VanillaLightmapCacheUpdate {
    Unchanged,
    Replaced,
}

impl VanillaLightmapCache {
    pub fn update(
        &mut self,
        world_generation: u64,
        frame: VanillaLightmapFrame,
    ) -> GalResult<VanillaLightmapCacheUpdate> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "vanilla lightmap cache requires a non-zero world generation",
            ));
        }
        frame.validate()?;
        let rgba8 = frame.inputs.rgba8()?.to_vec();
        if self.world_generation == world_generation && self.lightmap_generation == frame.generation
        {
            if self.rgba8 != rgba8 {
                return Err(GalError::invalid_argument(
                    "vanilla lightmap generation changed its semantic contents",
                ));
            }
            return Ok(VanillaLightmapCacheUpdate::Unchanged);
        }
        if self.world_generation == world_generation
            && self.lightmap_generation != 0
            && frame.generation < self.lightmap_generation
        {
            return Err(GalError::invalid_argument(format!(
                "stale vanilla lightmap generation {} for world {}; current is {}",
                frame.generation, world_generation, self.lightmap_generation
            )));
        }
        self.world_generation = world_generation;
        self.lightmap_generation = frame.generation;
        self.rgba8 = rgba8;
        Ok(VanillaLightmapCacheUpdate::Replaced)
    }

    pub fn clear(&mut self) {
        self.world_generation = 0;
        self.lightmap_generation = 0;
        self.rgba8.clear();
    }

    pub fn world_generation(&self) -> u64 {
        self.world_generation
    }

    pub fn lightmap_generation(&self) -> u64 {
        self.lightmap_generation
    }

    pub fn rgba8(&self) -> &[u8] {
        &self.rgba8
    }
}

/// Backend-neutral GAL residency for a coherent vanilla lightmap generation.
/// This resource is entirely Rust-owned: callers stage its upload into their
/// combined submission, then bind the resulting semantic `Lightmap` role to a
/// source-derived terrain program. No Java texture identity or backend object
/// is part of this contract.
#[derive(Debug)]
pub(crate) struct VanillaLightmapResidency {
    world_generation: u64,
    lightmap_generation: u64,
    upload_buffer: Handle,
    texture: Handle,
    sampler: Handle,
    view: Handle,
    combined_sampler: Handle,
    /// Consumer descriptor sets are owned by this exact residency generation.
    /// This guarantees they retire before the view they reference, even when
    /// a later frame replaces the lightmap while other consumers still exist.
    consumer_resource_sets: BTreeMap<Handle, Handle>,
}

/// Rust-private semantic binding for consumers which require the separate
/// texture and sampler form of the vanilla lightmap. The identity is based on
/// immutable world/lightmap generations; the contained GAL handles remain
/// entirely inside Rust and never represent a Java or backend-owned texture.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct VanillaLightmapBinding {
    pub world_generation: u64,
    pub lightmap_generation: u64,
    pub texture_view: Handle,
    pub sampler: Handle,
}

impl VanillaLightmapResidency {
    pub(crate) fn create(gal: &mut VulkanicGal, cache: &VanillaLightmapCache) -> GalResult<Self> {
        validate_cache(cache)?;
        let label = format!(
            "shader-pack.vanilla-lightmap.world{}.gen{}",
            cache.world_generation, cache.lightmap_generation
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.upload"),
                size: VANILLA_LIGHTMAP_RGBA_BYTES as u64,
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
                    width: VANILLA_LIGHTMAP_WIDTH as u32,
                    height: VANILLA_LIGHTMAP_HEIGHT as u32,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(texture);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.sampler"),
                // Frozen Java OpenGL's `LightTexture` installs a linear
                // sampler. Its active Sodium terrain vertex shader supplies
                // packed `level / 16` coordinates, so filtering is part of
                // that renderer's visible lighting contract; other consumers
                // may intentionally use texel-centre coordinates instead.
                min_filter: SamplerFilter::Linear,
                mag_filter: SamplerFilter::Linear,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            let view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label}.combined"),
                    texture_view: view,
                    sampler,
                })?;
            created.push(combined_sampler);
            Ok(Self {
                world_generation: cache.world_generation,
                lightmap_generation: cache.lightmap_generation,
                upload_buffer,
                texture,
                sampler,
                view,
                combined_sampler,
                consumer_resource_sets: BTreeMap::new(),
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(crate) fn is_compatible_with(&self, cache: &VanillaLightmapCache) -> bool {
        self.world_generation == cache.world_generation
            && self.lightmap_generation == cache.lightmap_generation
    }

    pub(crate) fn binding(&self) -> VanillaLightmapBinding {
        VanillaLightmapBinding {
            world_generation: self.world_generation,
            lightmap_generation: self.lightmap_generation,
            texture_view: self.view,
            sampler: self.sampler,
        }
    }

    /// Creates one explicit consumer set for a caller-owned layout. The
    /// layout is only an ABI declaration; texture/view/sampler ownership and
    /// retirement remain with this Rust lightmap generation.
    pub(crate) fn resource_set_for_layout(
        &mut self,
        gal: &mut VulkanicGal,
        layout: Handle,
    ) -> GalResult<Handle> {
        if let Some(set) = self.consumer_resource_sets.get(&layout) {
            return Ok(*set);
        }
        let set = gal.create_resource_set(ResourceSetDesc {
            label: format!(
                "shader-pack.vanilla-lightmap.world{}.gen{}.consumer-set",
                self.world_generation, self.lightmap_generation
            ),
            layout,
            bindings: vec![
                ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: self.view,
                    kind: ResourceBindingKind::SampledTexture,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 1,
                    array_index: 0,
                    resource: self.sampler,
                    kind: ResourceBindingKind::Sampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
            ],
        })?;
        self.consumer_resource_sets.insert(layout, set);
        Ok(set)
    }

    /// Appends, but never submits, the complete upload and sampled transition.
    /// The world frame remains solely responsible for the final submission and
    /// confirmation ordering.
    pub(crate) fn append_upload(
        &self,
        cache: &VanillaLightmapCache,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if !self.is_compatible_with(cache) {
            return Err(GalError::invalid_argument(
                "vanilla lightmap upload does not match the owned resource generation",
            ));
        }
        validate_cache(cache)?;
        ops.extend([
            CommandOp::HostWriteBuffer {
                buffer: self.upload_buffer,
                offset: 0,
                data: cache.rgba8.clone(),
            },
            CommandOp::Barrier(resource_barrier(
                self.upload_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )),
            CommandOp::Barrier(resource_barrier(
                self.texture,
                TextureUsageState::Undefined,
                TextureUsageState::TransferDst,
            )),
            CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                buffer: self.upload_buffer,
                buffer_offset: 0,
                bytes_per_row: (VANILLA_LIGHTMAP_WIDTH * 4) as u32,
                rows_per_image: VANILLA_LIGHTMAP_HEIGHT as u32,
                texture: self.texture,
                texture_mip: 0,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: Extent3d {
                    width: VANILLA_LIGHTMAP_WIDTH as u32,
                    height: VANILLA_LIGHTMAP_HEIGHT as u32,
                    depth: 1,
                },
            }),
            CommandOp::Barrier(resource_barrier(
                self.texture,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )),
        ]);
        Ok(())
    }

    pub(crate) fn semantic_resource_set(
        &self,
        shader_pack_generation: u64,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if shader_pack_generation == 0 {
            return Err(GalError::invalid_argument(
                "vanilla lightmap resource set requires a non-zero shader-pack generation",
            ));
        }
        let availability = TerrainSourceResourceAvailabilitySet::new(
            shader_pack_generation,
            self.world_generation,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::Lightmap,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: self.lightmap_generation,
            }],
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::Lightmap,
                combined_sampler: self.combined_sampler,
            }],
        )
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        for (_, set) in self.consumer_resource_sets {
            gal.destroy(set)?;
        }
        for handle in [
            self.combined_sampler,
            self.view,
            self.sampler,
            self.texture,
            self.upload_buffer,
        ] {
            gal.destroy(handle)?;
        }
        Ok(())
    }
}

fn validate_cache(cache: &VanillaLightmapCache) -> GalResult<()> {
    if cache.world_generation == 0 || cache.lightmap_generation == 0 {
        return Err(GalError::invalid_argument(
            "vanilla lightmap residency requires a complete world and lightmap generation",
        ));
    }
    if cache.rgba8.len() != VANILLA_LIGHTMAP_RGBA_BYTES {
        return Err(GalError::invalid_argument(format!(
            "vanilla lightmap cache contains {} bytes, expected {}",
            cache.rgba8.len(),
            VANILLA_LIGHTMAP_RGBA_BYTES
        )));
    }
    Ok(())
}

fn resource_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn brightness(level: f32) -> f32 {
    level / (4.0 - 3.0 * level)
}

fn not_gamma(color: [f32; 3]) -> [f32; 3] {
    let max_component = color.into_iter().fold(0.0f32, f32::max);
    if max_component <= 0.0 {
        return [0.0; 3];
    }
    let max_inverted = 1.0 - max_component;
    let max_scaled = 1.0 - max_inverted.powi(4);
    scale(color, max_scaled / max_component)
}

fn add(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] + right[0], left[1] + right[1], left[2] + right[2]]
}

fn multiply(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] * right[0], left[1] * right[1], left[2] * right[2]]
}

fn scale(color: [f32; 3], factor: f32) -> [f32; 3] {
    [color[0] * factor, color[1] * factor, color[2] * factor]
}

fn mix(left: [f32; 3], right: [f32; 3], factor: f32) -> [f32; 3] {
    [
        left[0] + (right[0] - left[0]) * factor,
        left[1] + (right[1] - left[1]) * factor,
        left[2] + (right[2] - left[2]) * factor,
    ]
}

fn unorm8(value: f32) -> u8 {
    // Frozen's RGBA8 lightmap render target converts the shader's normalized
    // fragment output with nearest-UNORM quantization. Truncation makes the
    // owned Rust image systematically one code point darker across most of the
    // table, which is then amplified by terrain texture modulation.
    (value.clamp(0.0, 1.0) * 255.0).round() as u8
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::{
        mock::MockBackend, presentation_capabilities, vulkan_capabilities,
    };
    use crate::render::vulkanic::commands::{CommandList, CommandListDesc, SubmissionBatch};

    fn inputs() -> VanillaLightmapInputs {
        VanillaLightmapInputs {
            ambient_light_factor: 0.0,
            sky_factor: 1.0,
            block_factor: 1.5,
            night_vision_factor: 0.0,
            darkness_scale: 0.0,
            darken_world_factor: 0.0,
            brightness_factor: 0.0,
            sky_light_color: [1.0, 1.0, 1.0],
            ambient_color: [1.0, 1.0, 1.0],
        }
    }

    #[test]
    fn reconstructs_vanilla_lightmap_with_block_on_x_and_sky_on_y() {
        let pixels = inputs().rgba8().unwrap();
        let dark = &pixels[0..4];
        let block_lit = &pixels[(VANILLA_LIGHTMAP_WIDTH - 1) * 4..VANILLA_LIGHTMAP_WIDTH * 4];
        let sky_lit_offset = ((VANILLA_LIGHTMAP_HEIGHT - 1) * VANILLA_LIGHTMAP_WIDTH) * 4;
        let sky_lit = &pixels[sky_lit_offset..sky_lit_offset + 4];
        assert_eq!(u8::MAX, dark[3]);
        assert!(block_lit[0] > dark[0]);
        assert!(sky_lit[2] > dark[2]);
        assert_eq!(u8::MAX, sky_lit[3]);
    }

    #[test]
    fn rgba8_quantization_matches_the_frozen_unorm_framebuffer_contract() {
        // The Java OpenGL reference stores the lightmap in an RGBA8 render
        // target. Keep the Rust-owned semantic reconstruction on the same
        // nearest-UNORM conversion rather than systematically darkening the
        // CPU image by truncating every fractional code point.
        assert_eq!(85, unorm8(84.99 / 255.0));
        assert_eq!(85, unorm8(85.0 / 255.0));
        assert_eq!(255, unorm8(1.0));
    }

    #[test]
    fn default_daylight_texels_match_the_frozen_opengl_capture_contract() {
        // These values are sampled from Frozen Java OpenGL's captured 16×16
        // RGBA8 lightmap for the same explicit default inputs below. This is
        // a semantic CPU-image regression test: it neither reads a Java GPU
        // texture nor permits a Java renderer fallback.
        let pixels = VanillaLightmapInputs {
            brightness_factor: 0.5,
            ..inputs()
        }
        .rgba8()
        .unwrap();
        let texel = |block: usize, sky: usize| {
            let offset = (sky * VANILLA_LIGHTMAP_WIDTH + block) * 4;
            [pixels[offset], pixels[offset + 1], pixels[offset + 2], pixels[offset + 3]]
        };
        assert_eq!([25, 25, 25, 255], texel(0, 0));
        assert_eq!([39, 34, 31, 255], texel(1, 0));
        assert_eq!([252, 252, 252, 255], texel(15, 0));
        assert_eq!([204, 204, 204, 255], texel(0, 13));
        assert_eq!([252, 252, 252, 255], texel(15, 15));
    }

    #[test]
    fn full_light_preserves_the_source_final_mix_and_invalid_inputs_reject() {
        let full = VanillaLightmapInputs {
            ambient_light_factor: 1.0,
            sky_factor: 1.0,
            block_factor: 1.5,
            night_vision_factor: 1.0,
            darkness_scale: 0.0,
            darken_world_factor: 0.0,
            brightness_factor: 1.0,
            sky_light_color: [1.0, 1.0, 1.0],
            ambient_color: [1.0, 1.0, 1.0],
        };
        assert_eq!([0.99; 3], full.texel(15, 15).unwrap());
        assert!(full.texel(16, 0).is_err());
        assert!(VanillaLightmapInputs {
            sky_factor: f32::NAN,
            ..full
        }
        .rgba8()
        .is_err());
    }

    #[test]
    fn darkness_and_gamma_follow_the_source_order() {
        let baseline = inputs().texel(4, 8).unwrap();
        let darkened = VanillaLightmapInputs {
            darkness_scale: 0.25,
            darken_world_factor: 0.5,
            brightness_factor: 0.75,
            ..inputs()
        }
        .texel(4, 8)
        .unwrap();
        assert!(darkened[0] < baseline[0]);
        assert!(darkened[1] < baseline[1]);
    }

    #[test]
    fn cache_is_world_and_generation_coherent_without_retaining_caller_state() {
        let frame = VanillaLightmapFrame {
            generation: 7,
            inputs: inputs(),
        };
        let mut cache = VanillaLightmapCache::default();
        assert_eq!(
            VanillaLightmapCacheUpdate::Replaced,
            cache.update(3, frame).unwrap()
        );
        let expected = cache.rgba8().to_vec();
        assert_eq!(
            VanillaLightmapCacheUpdate::Unchanged,
            cache.update(3, frame).unwrap()
        );
        assert_eq!(expected, cache.rgba8());

        let changed_same_generation = VanillaLightmapFrame {
            inputs: VanillaLightmapInputs {
                sky_factor: 0.25,
                ..inputs()
            },
            ..frame
        };
        assert!(cache.update(3, changed_same_generation).is_err());
        assert_eq!(expected, cache.rgba8());

        let stale = VanillaLightmapFrame {
            generation: 6,
            inputs: inputs(),
        };
        assert!(cache.update(3, stale).is_err());
        assert_eq!(expected, cache.rgba8());

        assert_eq!(
            VanillaLightmapCacheUpdate::Replaced,
            cache.update(4, stale).unwrap(),
            "a new world generation owns an independent dynamic-lightmap timeline"
        );
        cache.clear();
        assert_eq!(0, cache.world_generation());
        assert_eq!(0, cache.lightmap_generation());
        assert!(cache.rgba8().is_empty());
    }

    #[test]
    fn residency_stages_a_complete_owned_upload_and_semantic_lightmap_binding() {
        let mut cache = VanillaLightmapCache::default();
        cache
            .update(
                3,
                VanillaLightmapFrame {
                    generation: 7,
                    inputs: inputs(),
                },
            )
            .unwrap();
        let mut gal = VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(presentation_capabilities(
                vulkan_capabilities(),
            ))),
            false,
        );
        let residency = VanillaLightmapResidency::create(&mut gal, &cache).unwrap();
        assert!(residency.is_compatible_with(&cache));
        let set = residency.semantic_resource_set(11).unwrap();
        assert_eq!(1, set.len());
        assert!(set
            .availability()
            .resource_for(TerrainSourceResourceRole::Lightmap)
            .is_some());

        let mut ops = Vec::new();
        residency.append_upload(&cache, &mut ops).unwrap();
        assert_eq!(5, ops.len());
        gal.submit(SubmissionBatch {
            label: "test.vanilla-lightmap.upload".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "test.vanilla-lightmap.upload.commands".to_string(),
                operations: ops,
            })],
        })
        .unwrap();
        residency.destroy(&mut gal).unwrap();
    }
}
