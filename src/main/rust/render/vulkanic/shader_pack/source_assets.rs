//! Rust-owned GAL residency for supported copied shader-pack PNG assets.
//!
//! This is intentionally private to the source-runtime preparation path. It
//! turns copied pack bytes into explicit GAL resources without exposing a GL
//! texture unit, Vulkan descriptor, or Java resource object. Source-selected
//! terrain execution remains separately gated on the complete contract.

use std::collections::BTreeMap;

use crate::render::vulkanic::commands::{
    BufferImageCopyRegion, CommandList, CommandListDesc, CommandOp, ResourceBarrier,
    SubmissionBatch, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    BufferDesc, BufferUsage, CombinedTextureSamplerDesc, Extent3d, MemoryDomain, QueueClass,
    SamplerAddressMode, SamplerDesc, SamplerFilter, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc,
};

use super::assets::{
    ShaderPackAssetSamplerPolicy, ShaderPackAssets, ShaderPackRgbaAsset,
    TerrainShaderPackAssetBindings,
};
use super::lowering::TerrainSourceOpaqueResourceBindingPlan;
use super::terrain_source_resources::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailability,
    TerrainSourceResourceAvailabilitySet, TerrainSourceResourceRole,
    TerrainSourceSampledResourceShape,
};

#[derive(Clone, Copy, Debug)]
struct TextureHandles {
    texture: Handle,
    view: Handle,
}

/// Generation-bound sampled resources for supported PNG declarations across
/// one shader pack's separately lowered source pairs. The mapping is by
/// semantic sampler name, not a backend binding slot; each program lowering
/// retains its own resource-set layout.
#[derive(Debug)]
pub(crate) struct TerrainSourceAssetResources {
    pack_name: String,
    generation: u64,
    combined_samplers: BTreeMap<String, Handle>,
    combined_handles: Vec<Handle>,
    sampler_handles: Vec<Handle>,
    view_handles: Vec<Handle>,
    texture_handles: Vec<Handle>,
}

impl TerrainSourceAssetResources {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        assets: &ShaderPackAssets,
        bindings: &TerrainShaderPackAssetBindings,
        active_binding_plans: &[&TerrainSourceOpaqueResourceBindingPlan],
    ) -> GalResult<Self> {
        if assets.generation() == 0 || assets.pack_name().trim().is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack asset resources require a non-empty pack identity and generation",
            ));
        }

        let mut textures_by_path = BTreeMap::new();
        let mut samplers_by_policy = BTreeMap::new();
        let mut combined_samplers = BTreeMap::new();
        let mut combined_handles = Vec::new();
        let mut sampler_handles = Vec::new();
        let mut view_handles = Vec::new();
        let mut texture_handles = Vec::new();
        let mut upload_buffers = Vec::new();
        let mut upload_ops = Vec::new();

        let result = (|| -> GalResult<()> {
            for (sampler_name, path) in bindings.samplers() {
                // Properties may declare images that are unused by the
                // lowered terrain program. They are not runtime resources
                // until the source plan gives them a semantic role.
                let Some(role) = role_for_sampler(active_binding_plans, sampler_name)? else {
                    continue;
                };
                if let Some(expected_path) = role.pack_texture_path() {
                    if path != expected_path {
                        return Err(GalError::invalid_argument(format!(
                            "shader-pack source sampler '{sampler_name}' resolves to '{path}', but its semantic role requires '{expected_path}'"
                        )));
                    }
                }
                let resolved = bindings.resolve_rgba8_with_sampler_policy(assets, sampler_name)?;
                let texture = if let Some(handles) = textures_by_path.get(path).copied() {
                    handles
                } else {
                    let handles = create_texture(gal, &resolved.image, &mut upload_buffers)?;
                    append_upload(
                        &mut upload_ops,
                        handles.texture,
                        upload_buffers.last().copied().ok_or_else(|| {
                            GalError::backend("shader-pack texture upload buffer was not retained")
                        })?,
                        &resolved.image,
                    )?;
                    textures_by_path.insert(path.to_string(), handles);
                    texture_handles.push(handles.texture);
                    view_handles.push(handles.view);
                    handles
                };

                let sampler = if let Some(handle) =
                    samplers_by_policy.get(&resolved.sampler_policy).copied()
                {
                    handle
                } else {
                    let handle = gal.create_sampler(sampler_desc(
                        assets.pack_name(),
                        assets.generation(),
                        resolved.sampler_policy,
                    ))?;
                    samplers_by_policy.insert(resolved.sampler_policy, handle);
                    sampler_handles.push(handle);
                    handle
                };
                let combined = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!(
                        "shader-pack.{}.gen{}.{}.combined",
                        assets.pack_name(),
                        assets.generation(),
                        sampler_name
                    ),
                    texture_view: texture.view,
                    sampler,
                })?;
                combined_samplers.insert(sampler_name.to_string(), combined);
                combined_handles.push(combined);
            }

            if !upload_ops.is_empty() {
                gal.submit(SubmissionBatch {
                    label: format!(
                        "shader-pack.{}.gen{}.png-upload",
                        assets.pack_name(),
                        assets.generation()
                    ),
                    command_lists: vec![CommandList::from(CommandListDesc {
                        label: "shader-pack.png-upload.commands".to_string(),
                        operations: upload_ops,
                    })],
                })?;
                for upload in upload_buffers.drain(..) {
                    gal.destroy(upload)?;
                }
            }
            Ok(())
        })();

        if let Err(error) = result {
            for handle in upload_buffers.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
            destroy_handles(
                gal,
                combined_handles,
                sampler_handles,
                view_handles,
                texture_handles,
            );
            return Err(error);
        }
        Ok(Self {
            pack_name: assets.pack_name().to_string(),
            generation: assets.generation(),
            combined_samplers,
            combined_handles,
            sampler_handles,
            view_handles,
            texture_handles,
        })
    }

    pub(crate) fn pack_name(&self) -> &str {
        &self.pack_name
    }

    pub(crate) fn generation(&self) -> u64 {
        self.generation
    }

    pub(crate) fn combined_sampler_for(&self, sampler_name: &str) -> Option<Handle> {
        self.combined_samplers.get(sampler_name).copied()
    }

    pub(crate) fn len(&self) -> usize {
        self.combined_samplers.len()
    }

    /// Converts active lowered source resources backed by copied pack PNGs
    /// into the shared semantic resource family. Other roles (the Minecraft
    /// atlas, shadow attachments, and voxel fields) remain independently
    /// owned and must be merged by the complete source runtime before any
    /// program can be selected.
    pub(crate) fn declared_semantic_resources(
        &self,
        active_binding_plans: &[&TerrainSourceOpaqueResourceBindingPlan],
        world_generation: u64,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack PNG semantic resources require a non-zero world generation",
            ));
        }
        let mut availability = Vec::new();
        let mut resources = Vec::new();
        for (sampler_name, combined_sampler) in &self.combined_samplers {
            let Some(role) = role_for_sampler(active_binding_plans, sampler_name)? else {
                continue;
            };
            if role.expected_sampled_resource_shape()
                != TerrainSourceSampledResourceShape::Texture2d
            {
                return Err(GalError::unsupported_feature(format!(
                    "shader-pack PNG sampler '{sampler_name}' cannot satisfy non-2D semantic role '{}'",
                    role.semantic_name()
                )));
            }
            availability.push(TerrainSourceResourceAvailability {
                role: role.clone(),
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: self.generation,
            });
            resources.push(TerrainSourceOwnedResource {
                role,
                combined_sampler: *combined_sampler,
            });
        }
        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.generation,
            world_generation,
            availability,
        )?;
        TerrainSourceOwnedResourceSet::new(availability, resources)
    }

    /// Destruction is dependency ordered and remains retirement-safe in GAL.
    pub(crate) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        for handle in self.combined_handles {
            gal.destroy(handle)?;
        }
        for handle in self.sampler_handles {
            gal.destroy(handle)?;
        }
        for handle in self.view_handles {
            gal.destroy(handle)?;
        }
        for handle in self.texture_handles {
            gal.destroy(handle)?;
        }
        Ok(())
    }
}

/// A pack asset may be referenced by separately lowered terrain and shadow
/// source pairs. They must agree on its semantic role; accepting whichever
/// plan happens to be queried first would make a pack-wide asset depend on
/// pass preparation order.
fn role_for_sampler(
    active_binding_plans: &[&TerrainSourceOpaqueResourceBindingPlan],
    sampler_name: &str,
) -> GalResult<Option<TerrainSourceResourceRole>> {
    let mut role = None;
    for plan in active_binding_plans {
        let Some(candidate) = plan.role_for(sampler_name) else {
            continue;
        };
        match &role {
            Some(existing) if existing != &candidate => {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack sampler '{sampler_name}' has conflicting semantic roles across lowered source pairs"
                )));
            }
            Some(_) => {}
            None => role = Some(candidate),
        }
    }
    Ok(role)
}

fn create_texture(
    gal: &mut VulkanicGal,
    image: &ShaderPackRgbaAsset,
    upload_buffers: &mut Vec<Handle>,
) -> GalResult<TextureHandles> {
    let label = format!("shader-pack.asset.{}", image.path);
    let texture = gal.create_texture(TextureDesc {
        label: format!("{label}.texture"),
        dimension: TextureDimension::D2,
        format: TextureFormat::Rgba8Unorm,
        extent: Extent3d {
            width: image.width,
            height: image.height,
            depth: 1,
        },
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
    })?;
    let view = match gal.create_texture_view(TextureViewDesc {
        label: format!("{label}.view"),
        texture,
        format: TextureFormat::Rgba8Unorm,
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    }) {
        Ok(view) => view,
        Err(error) => {
            let _ = gal.destroy(texture);
            return Err(error);
        }
    };
    let size = u64::try_from(image.pixels_rgba8.len()).map_err(|_| {
        GalError::invalid_argument("shader-pack decoded image byte count does not fit u64")
    })?;
    let upload = match gal.create_buffer(BufferDesc {
        label: format!("{label}.upload"),
        size,
        memory: MemoryDomain::Upload,
        usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
    }) {
        Ok(upload) => upload,
        Err(error) => {
            let _ = gal.destroy(view);
            let _ = gal.destroy(texture);
            return Err(error);
        }
    };
    upload_buffers.push(upload);
    Ok(TextureHandles { texture, view })
}

fn append_upload(
    ops: &mut Vec<CommandOp>,
    texture: Handle,
    upload: Handle,
    image: &ShaderPackRgbaAsset,
) -> GalResult<()> {
    let bytes_per_row = image
        .width
        .checked_mul(4)
        .ok_or_else(|| GalError::invalid_argument("shader-pack image row pitch overflows u32"))?;
    ops.push(CommandOp::HostWriteBuffer {
        buffer: upload,
        offset: 0,
        data: image.pixels_rgba8.clone(),
    });
    ops.push(CommandOp::Barrier(buffer_barrier(
        upload,
        TextureUsageState::TransferDst,
        TextureUsageState::TransferSrc,
    )));
    ops.push(CommandOp::Barrier(texture_barrier(
        texture,
        TextureUsageState::Undefined,
        TextureUsageState::TransferDst,
    )));
    ops.push(CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row,
        rows_per_image: image.height,
        texture,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: image.width,
            height: image.height,
            depth: 1,
        },
    }));
    ops.push(CommandOp::Barrier(texture_barrier(
        texture,
        TextureUsageState::TransferDst,
        TextureUsageState::ShaderRead,
    )));
    Ok(())
}

fn sampler_desc(
    pack_name: &str,
    generation: u64,
    policy: ShaderPackAssetSamplerPolicy,
) -> SamplerDesc {
    let filter = if policy.blur {
        SamplerFilter::Linear
    } else {
        SamplerFilter::Nearest
    };
    let address = if policy.clamp {
        SamplerAddressMode::ClampToEdge
    } else {
        SamplerAddressMode::Repeat
    };
    SamplerDesc {
        label: format!("shader-pack.{pack_name}.gen{generation}.sampler.{policy:?}"),
        min_filter: filter,
        mag_filter: filter,
        mip_filter: filter,
        address_u: address,
        address_v: address,
        address_w: address,
        comparison: None,
    }
}

fn buffer_barrier(
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

fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    buffer_barrier(resource, before, after)
}

fn destroy_handles(
    gal: &mut VulkanicGal,
    combined: Vec<Handle>,
    samplers: Vec<Handle>,
    views: Vec<Handle>,
    textures: Vec<Handle>,
) {
    for handle in combined.into_iter().rev() {
        let _ = gal.destroy(handle);
    }
    for handle in samplers.into_iter().rev() {
        let _ = gal.destroy(handle);
    }
    for handle in views.into_iter().rev() {
        let _ = gal.destroy(handle);
    }
    for handle in textures.into_iter().rev() {
        let _ = gal.destroy(handle);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::mock::MockBackend;
    use crate::render::vulkanic::shader_pack::assets::{
        ShaderPackAssetFile, ShaderPackAssetUpdate,
    };
    use crate::render::vulkanic::shader_pack::lowering::lower_terrain_source_pair;
    use crate::render::vulkanic::shader_pack::preprocess::preprocess_terrain_sources;
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};
    use crate::render::vulkanic::shader_pack::terrain_contract::derive_complementary_terrain_contract;
    use crate::render::vulkanic::shader_pack::terrain_source_resources::{
        TerrainSourceResourceBindings, TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
    };

    fn png() -> Vec<u8> {
        let mut bytes = Vec::new();
        let mut encoder = png::Encoder::new(&mut bytes, 1, 1);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        encoder
            .write_header()
            .unwrap()
            .write_image_data(&[1, 2, 3, 4])
            .unwrap();
        bytes
    }

    fn assets() -> ShaderPackAssets {
        ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "test-pack".to_string(),
            generation: 7,
            files: vec![
                ShaderPackAssetFile::new("lib/noise.png", png()),
                ShaderPackAssetFile::new(
                    "lib/noise.png.mcmeta",
                    br#"{"texture":{"blur":true,"clamp":false}}"#.to_vec(),
                ),
            ],
        })
        .unwrap()
    }

    fn bindings() -> TerrainShaderPackAssetBindings {
        TerrainShaderPackAssetBindings::from_source(
            &ShaderPackSource::new(
                "test-pack",
                7,
                vec![ShaderSourceFile::new(
                    super::super::assets::SHADER_PROPERTIES_PATH,
                    "texture.noise=/lib/noise.png\ncustomTexture.gbuffers_terrain.detail=lib/noise.png\n",
                )],
            )
            .unwrap(),
        )
        .unwrap()
    }

    fn active_resource_bindings() -> TerrainSourceOpaqueResourceBindingPlan {
        let source = ShaderPackSource::new(
            "test-pack",
            7,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.vsh",
                    "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nuniform sampler2D noisetex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb * texture2D(noisetex, texCoord).rrr; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(
                    TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\nnoisetex=noise\n",
                ),
            ],
        )
        .unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let artifacts =
            preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
        let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
        lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap()
    }

    #[test]
    fn owns_one_decoded_texture_per_path_and_one_binding_per_sampler() {
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let active_bindings = active_resource_bindings();
        let resources = TerrainSourceAssetResources::create(
            &mut gal,
            &assets(),
            &bindings(),
            &[&active_bindings],
        )
        .unwrap();
        assert_eq!("test-pack", resources.pack_name());
        assert_eq!(7, resources.generation());
        assert_eq!(1, resources.len());
        assert!(resources.combined_sampler_for("detail").is_none());
        // texture, view, upload, sampler, and one active combined sampler.
        assert_eq!(5, gal.metrics().resource_creates);
        resources.destroy(&mut gal).unwrap();
    }

    #[test]
    fn failed_asset_resolution_does_not_leave_valid_source_resources() {
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let source = ShaderPackSource::new(
            "test-pack",
            7,
            vec![ShaderSourceFile::new(
                super::super::assets::SHADER_PROPERTIES_PATH,
                "texture.noise=lib/missing.png\n",
            )],
        )
        .unwrap();
        let bindings = TerrainShaderPackAssetBindings::from_source(&source).unwrap();
        let active_bindings = active_resource_bindings();
        assert!(TerrainSourceAssetResources::create(
            &mut gal,
            &assets(),
            &bindings,
            &[&active_bindings],
        )
        .is_err());
        assert_eq!(0, gal.metrics().resource_creates);
    }

    #[test]
    fn maps_declared_pack_pngs_into_shared_semantic_resource_roles() {
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let active_bindings = active_resource_bindings();
        let resources = TerrainSourceAssetResources::create(
            &mut gal,
            &assets(),
            &bindings(),
            &[&active_bindings],
        )
        .unwrap();
        let semantic = resources
            .declared_semantic_resources(&[&active_bindings], 11)
            .unwrap();
        assert_eq!(7, semantic.availability().shader_pack_generation());
        assert_eq!(11, semantic.availability().world_generation());
        assert_eq!(
            resources.combined_sampler_for("noisetex"),
            semantic.combined_sampler_for(TerrainSourceResourceRole::Noise)
        );
        assert_eq!(
            None,
            semantic.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
        );
        resources.destroy(&mut gal).unwrap();
    }
}
