//! Programs shared by GUI mesh passes, keyed by target formats and material.

use super::*;

/// Immutable GUI-mesh GPU program for one explicit raster contract. Texture
/// bindings and stream buffers remain per asset, so sharing this object never
/// aliases mutable draw state between semantic callsites.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshSharedProgram {
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub resource_layout: Handle,
    pub pipeline_layout: Handle,
    pub pipeline: Handle,
}

impl GuiMeshSharedProgram {
    pub fn create(
        gal: &mut VulkanicGal,
        label: &str,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
        material_mode: GuiMeshMaterialMode,
        front_face: crate::render::vulkanic::resources::FrontFace,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let (vertex_code, fragment_code) =
                gui_mesh_shader_sources(gal.capabilities().shader_conventions.glsl_dialect, material_mode);
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
                label: format!("{label}.layout"),
                bindings: vec![
                    resource_binding_desc(0, ResourceBindingKind::StorageBuffer),
                    resource_binding_desc(1, ResourceBindingKind::UniformBuffer),
                    resource_binding_desc(2, ResourceBindingKind::SampledTexture),
                    resource_binding_desc(3, ResourceBindingKind::Sampler),
                ],
            })?;
            created.push(resource_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let (cull_mode, blend, depth_compare, depth_write) =
                gui_mesh_raster_state(material_mode);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode,
                front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend,
                depth_compare,
                depth_write,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format,
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(Self {
                vertex_shader,
                fragment_shader,
                resource_layout,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
        ] {
            let _ = gal.retire(handle);
        }
    }
}
