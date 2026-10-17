//! Shared GUI graphics pipelines keyed by blend, depth and target formats.

use super::*;

/// Immutable GUI program state shared by all texture resources with the same
/// explicit framebuffer and raster contract.  Texture resources still own
/// their descriptor sets because each set contains a distinct texture and
/// uniform buffer; only the identical shader/layout/pipeline objects are
/// reused.  This prevents an atlas or raw image count from multiplying native
/// pipeline compiler residency.
#[derive(Clone, Copy)]
pub(super) struct GuiSharedPipeline {
    pub(super) vertex_shader: Handle,
    pub(super) fragment_shader: Handle,
    pub(super) resource_layout: Handle,
    pub(super) pipeline_layout: Handle,
    pub(super) pipeline: Handle,
}

impl GuiSharedPipeline {
    pub(super) fn handles_in_destroy_order(self) -> [Handle; 5] {
        [
            self.pipeline,
            self.pipeline_layout,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
        ]
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct GuiSharedPipelineKey {
    pub(super) color_format: ColorFormat,
    pub(super) depth_format: Option<TextureFormat>,
    pub(super) blend: u32,
    pub(super) depth_compare: u32,
    pub(super) item_raster: bool,
    pub(super) item_cutout: bool,
}

impl GuiSharedPipelineKey {
    pub(super) fn new(
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
    ) -> Self {
        Self {
            color_format,
            depth_format,
            blend: group.blend() as u32,
            item_raster: matches!(group, TextureGroup::DynamicItemRaster(_)),
            item_cutout: matches!(group, TextureGroup::DynamicItemCutout(_)),
            depth_compare: if matches!(group, TextureGroup::DynamicLequalDepth(_)) {
                CompareOp::LessOrEqual as u32
            } else {
                0
            },
        }
    }
}

pub(super) fn gui_fragment_material_code(source: &[u8], item_raster: bool, item_cutout: bool) -> Vec<u8> {
    if !item_raster && !item_cutout {
        return source.to_vec();
    }
    // GLSL requires #version first. This specialization belongs to the explicit
    // item pipeline and cannot affect normal GUI images sharing its atlas.
    let version_end = source.iter().position(|byte| *byte == b'\n').unwrap() + 1;
    let mut code = source[..version_end].to_vec();
    code.extend_from_slice(if item_cutout {
        b"#define GUI_ITEM_CUTOUT\n"
    } else {
        b"#define GUI_ITEM_RASTER\n"
    });
    code.extend_from_slice(&source[version_end..]);
    code
}

impl GuiFrontend {
    pub(super) fn ensure_shared_pipeline(
        &mut self,
        gal: &mut VulkanicGal,
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
    ) -> GalResult<GuiSharedPipeline> {
        let key = GuiSharedPipelineKey::new(group, color_format, depth_format);
        if let Some(pipeline) = self.shared_pipelines.get(&key) {
            return Ok(*pipeline);
        }
        if self.shared_pipelines.len() >= GUI_MAX_SHARED_PIPELINES {
            return Err(GalError::unsupported_feature(format!(
                "GUI shared pipeline cache exceeds bounded limit {GUI_MAX_SHARED_PIPELINES}"
            )));
        }
        let label = format!(
            "minecraft.gui.shared-pipeline.format{}-depth{}-blend{}-compare{}-item{}-cutout{}",
            color_format as u32,
            depth_format.map(|format| format as u32).unwrap_or(0),
            key.blend,
            key.depth_compare,
            key.item_raster,
            key.item_cutout,
        );
        let (vertex_code, fragment_code) = if gal.capabilities().shader_conventions.glsl_dialect
            == GlslDialect::ExplicitBindings
        {
            (VERTEX_SHADER_VULKAN, FRAGMENT_SHADER_VULKAN)
        } else {
            (VERTEX_SHADER_OPENGL, FRAGMENT_SHADER_OPENGL)
        };
        let fragment_code =
            gui_fragment_material_code(fragment_code, key.item_raster, key.item_cutout);
        let mut created = Vec::new();
        let result = (|| -> GalResult<GuiSharedPipeline> {
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: vertex_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: fragment_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 2,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ],
            })?;
            created.push(resource_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend: group.blend(),
                depth_compare: if key.depth_compare == 0 {
                    None
                } else {
                    Some(CompareOp::LessOrEqual)
                },
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format,
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(GuiSharedPipeline {
                vertex_shader,
                fragment_shader,
                resource_layout,
                pipeline_layout,
                pipeline,
            })
        })();
        match result {
            Ok(pipeline) => {
                self.shared_pipelines.insert(key, pipeline);
                Ok(pipeline)
            }
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.retire(handle);
                }
                Err(error)
            }
        }
    }
}
