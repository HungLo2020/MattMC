//! Explicit command generation for validated vanilla fullscreen graphs.
//!
//! Resource creation and shader compilation remain owned by the GAL runtime;
//! this module only lowers an already validated semantic plan into command
//! operations using caller-supplied private handles.

use std::collections::{BTreeMap, BTreeSet};

use super::vanilla_post_effect_contract::{
    VanillaPostEffectExecutionPlan, VanillaPostEffectInput, VanillaPostEffectPass,
    VanillaPostEffectUniform,
};
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandOp, PassAttachment,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::resources::BackendApi;
use crate::render::vulkanic::handles::{Handle, HandleKind};

const MAX_PACKED_UNIFORM_BLOCK_BYTES: usize = 64 * 1024;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VanillaPostEffectInputBinding {
    pub texture_view: Handle,
    pub sampler: Handle,
    pub bilinear: bool,
    pub use_depth_buffer: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub struct VanillaPostEffectPassBinding {
    pub render_pass: Handle,
    pub render_target: Handle,
    pub color_attachment: Handle,
    pub depth_attachment: Option<Handle>,
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    pub inputs: Vec<VanillaPostEffectInputBinding>,
    pub uniform_values: BTreeMap<String, Vec<VanillaPostEffectUniform>>,
}

/// One Rust-owned external attachment role supplied by the frame
/// coordinator.  The role is semantic; these handles are opaque GAL values
/// and never Java/Iris/native backend identities.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct VanillaPostEffectExternalTargetBinding {
    pub render_target: Handle,
    pub color_attachment: Handle,
    pub depth_attachment: Option<Handle>,
    pub sampler: Handle,
}

/// Validated external attachment set for one effect plan.  Intermediate
/// targets remain private to the executor; this object only accepts the
/// externally supplied roles declared by the copied semantic graph.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct VanillaPostEffectExternalTargetBindings {
    bindings: BTreeMap<String, VanillaPostEffectExternalTargetBinding>,
}

/// The complete Rust-owned attachment inventory required by the bundled
/// Fabulous transparency graph.  Keeping the six roles typed prevents a
/// frame coordinator from accidentally admitting a partial graph or silently
/// aliasing one semantic source to another.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct FabulousExternalTargetInventory {
    pub main: VanillaPostEffectExternalTargetBinding,
    pub translucent: VanillaPostEffectExternalTargetBinding,
    pub item_entity: VanillaPostEffectExternalTargetBinding,
    pub particles: VanillaPostEffectExternalTargetBinding,
    pub clouds: VanillaPostEffectExternalTargetBinding,
    pub weather: VanillaPostEffectExternalTargetBinding,
}

impl FabulousExternalTargetInventory {
    pub fn bindings(self) -> BTreeMap<String, VanillaPostEffectExternalTargetBinding> {
        BTreeMap::from([
            ("minecraft:main".to_string(), self.main),
            ("minecraft:translucent".to_string(), self.translucent),
            ("minecraft:item_entity".to_string(), self.item_entity),
            ("minecraft:particles".to_string(), self.particles),
            ("minecraft:clouds".to_string(), self.clouds),
            ("minecraft:weather".to_string(), self.weather),
        ])
    }

    pub fn validate_against(
        self,
        plan: &VanillaPostEffectExecutionPlan,
    ) -> GalResult<VanillaPostEffectExternalTargetBindings> {
        let values = [
            self.main,
            self.translucent,
            self.item_entity,
            self.particles,
            self.clouds,
            self.weather,
        ];
        let render_targets = values
            .iter()
            .map(|binding| binding.render_target)
            .collect::<BTreeSet<_>>();
        let color_attachments = values
            .iter()
            .map(|binding| binding.color_attachment)
            .collect::<BTreeSet<_>>();
        if render_targets.len() != values.len() || color_attachments.len() != values.len() {
            return Err(GalError::invalid_argument(
                "bundled Fabulous transparency external targets must not alias",
            ));
        }
        VanillaPostEffectExternalTargetBindings::new(plan, self.bindings())
    }
}

impl VanillaPostEffectExternalTargetBindings {
    pub fn new(
        plan: &VanillaPostEffectExecutionPlan,
        bindings: BTreeMap<String, VanillaPostEffectExternalTargetBinding>,
    ) -> GalResult<Self> {
        let provided = bindings.keys().cloned().collect();
        plan.validate_external_targets(&provided)?;
        for (name, binding) in &bindings {
            let target_kind = binding.render_target.kind();
            if target_kind != Some(HandleKind::RenderTarget)
                && target_kind != Some(HandleKind::FrameTarget)
            {
                return Err(GalError::invalid_argument(format!(
                    "vanilla post effect {} external target {name} has invalid render target kind {:?}",
                    plan.effect_name, target_kind
                )));
            }
            binding.color_attachment.require_kind(HandleKind::TextureView).map_err(|error| {
                GalError::invalid_argument(format!(
                    "vanilla post effect {} external target {name} has invalid color attachment: {error}",
                    plan.effect_name
                ))
            })?;
            binding.sampler.require_kind(HandleKind::Sampler).map_err(|error| {
                GalError::invalid_argument(format!(
                    "vanilla post effect {} external target {name} has invalid sampler: {error}",
                    plan.effect_name
                ))
            })?;
            if let Some(depth) = binding.depth_attachment {
                depth.require_kind(HandleKind::TextureView).map_err(|error| {
                    GalError::invalid_argument(format!(
                        "vanilla post effect {} external target {name} has invalid depth attachment: {error}",
                        plan.effect_name
                    ))
                })?;
            }
        }
        for pass in &plan.ordered_passes {
            for input in &pass.inputs {
                if input.use_depth_buffer
                    && bindings
                        .get(&input.target)
                        .and_then(|binding| binding.depth_attachment)
                        .is_none()
                {
                    return Err(GalError::unsupported_feature(format!(
                        "vanilla post effect {} requires Rust-owned depth attachment for external target {}",
                        plan.effect_name, input.target
                    )));
                }
            }
        }
        Ok(Self { bindings })
    }

    pub fn get(&self, name: &str) -> Option<VanillaPostEffectExternalTargetBinding> {
        self.bindings.get(name).copied()
    }
}

/// Immutable Rust-owned submission object for one validated vanilla effect.
///
/// Callers construct this at resource/pack load time and retain it for the
/// effect's lifetime.  Submission only supplies private GAL handles, so the
/// frame path cannot replace the graph or silently fall back to Java state.
#[derive(Clone, Debug, PartialEq)]
pub struct VanillaPostEffectExecutor {
    plan: VanillaPostEffectExecutionPlan,
}

impl VanillaPostEffectExecutor {
    pub fn new(plan: VanillaPostEffectExecutionPlan) -> GalResult<Self> {
        if plan.effect_name.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "vanilla post effect executor requires a non-empty effect name",
            ));
        }
        if plan.ordered_passes.is_empty() || plan.ordered_passes.len() > 64 {
            return Err(GalError::invalid_argument(
                "vanilla post effect executor pass count is outside the bounded range",
            ));
        }
        for pass in &plan.ordered_passes {
            if pass.inputs.len() > 32 || pass.uniform_values.len() > 16 {
                return Err(GalError::invalid_argument(
                    "vanilla post effect executor pass exceeds bounded input or uniform-block count",
                ));
            }
            for input in &pass.inputs {
                if input.sampler_name.trim().is_empty() || input.target.trim().is_empty() {
                    return Err(GalError::invalid_argument(
                        "vanilla post effect executor input requires sampler and target names",
                    ));
                }
            }
            // Validate and size every upload while the immutable executor is
            // created, rather than discovering malformed pack data halfway
            // through a frame submission.
            pack_uniform_blocks(pass)?;
        }
        Ok(Self { plan })
    }

    pub fn plan(&self) -> &VanillaPostEffectExecutionPlan {
        &self.plan
    }

    pub fn lower(&self, bindings: &[VanillaPostEffectPassBinding]) -> GalResult<Vec<CommandOp>> {
        lower_validated_plan(&self.plan, bindings)
    }
}

/// Constructs the bundled vanilla entity-outline executor from Rust-owned
/// resource bytes. The returned executor contains only the validated semantic
/// graph; all handles remain caller-owned and private to the eventual Vulkan
/// resource writer.
pub fn bundled_entity_outline_executor() -> GalResult<VanillaPostEffectExecutor> {
    let contract = super::vanilla_post_effect_contract::VanillaPostEffectContract::parse(
        "minecraft:entity_outline",
        include_bytes!("../../../../resources/assets/minecraft/post_effect/entity_outline.json"),
    )?;
    VanillaPostEffectExecutor::new(contract.execution_plan())
}

/// Constructs the bundled vanilla transparency executor from Rust-owned
/// resource bytes.  This remains a private capability until the frame
/// coordinator supplies every declared external attachment (main,
/// translucent, item-entity, particles, clouds, and weather); callers must not
/// substitute Java PostChain targets for those bindings.
pub fn bundled_transparency_executor() -> GalResult<VanillaPostEffectExecutor> {
    let contract = super::vanilla_post_effect_contract::VanillaPostEffectContract::parse(
        "minecraft:transparency",
        include_bytes!("../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )?;
    VanillaPostEffectExecutor::new(contract.execution_plan())
}

/// Returns copied Rust-owned shader bytes for every bundled outline pass.
/// Missing sources are an explicit preparation failure, never a Java/Iris
/// lookup fallback.
pub fn bundled_entity_outline_shader_sources(
) -> GalResult<Vec<super::vanilla_post_effect_contract::VanillaPostEffectShaderSource>> {
    let contract = super::vanilla_post_effect_contract::VanillaPostEffectContract::parse(
        "minecraft:entity_outline",
        include_bytes!("../../../../resources/assets/minecraft/post_effect/entity_outline.json"),
    )?;
    contract.shader_sources()
}

/// Returns the copied shader sources for the bundled transparency graph.  The
/// graph can only become executable after its external attachment inventory is
/// validated by the frame coordinator; shader lookup itself never consults a
/// Java `ShaderManager` or Iris runtime object.
pub fn bundled_transparency_shader_sources(
) -> GalResult<Vec<super::vanilla_post_effect_contract::VanillaPostEffectShaderSource>> {
    let contract = super::vanilla_post_effect_contract::VanillaPostEffectContract::parse(
        "minecraft:transparency",
        include_bytes!("../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )?;
    contract.shader_sources()
}

/// Lowers copied vanilla fullscreen GLSL into the explicit Vulkan descriptor
/// ABI used by the future Rust post-effect resource writer.  The source graph
/// remains semantic input: sampler and uniform binding numbers are derived
/// from the validated pass order, never from Java's runtime shader objects.
pub fn bundled_transparency_vulkan_shader_sources() -> GalResult<Vec<(Vec<u8>, Vec<u8>)>> {
    let contract = super::vanilla_post_effect_contract::VanillaPostEffectContract::parse(
        "minecraft:transparency",
        include_bytes!("../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )?;
    let sources = contract.shader_sources()?;
    if sources.len() != contract.passes.len() {
        return Err(GalError::backend(
            "transparency shader source count does not match its validated pass graph",
        ));
    }
    contract
        .passes
        .iter()
        .zip(sources)
        .map(|(pass, source)| {
            Ok((
                normalize_vulkan_vertex_source(BackendApi::Vulkan, &source.vertex_shader)?,
                normalize_vulkan_fullscreen_source(
                    BackendApi::Vulkan,
                    &source.fragment_shader,
                    pass,
                )?,
            ))
        })
        .collect()
}

pub(crate) fn normalize_vulkan_vertex_source(api: BackendApi, source: &[u8]) -> GalResult<Vec<u8>> {
    let source = std::str::from_utf8(source)
        .map_err(|_| GalError::invalid_argument("vanilla post-effect vertex shader is not UTF-8"))?;
    if api != BackendApi::Vulkan {
        return Ok(source.as_bytes().to_vec());
    }
    Ok(source
        .replacen(
            "#version 330",
            "#version 450\n#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1\n#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1",
            1,
        )
        .replacen("out vec2 texCoord;", "layout(location = 0) out vec2 texCoord;", 1)
		.replace("gl_VertexID", "gl_VertexIndex")
        .into_bytes())
}

pub(crate) fn normalize_vulkan_fullscreen_source(
    api: BackendApi,
    source: &[u8],
    pass: &VanillaPostEffectPass,
) -> GalResult<Vec<u8>> {
    let source = std::str::from_utf8(source)
        .map_err(|_| GalError::invalid_argument("vanilla post-effect shader is not UTF-8"))?;
    if api != BackendApi::Vulkan {
        return Ok(source.as_bytes().to_vec());
    }
    let mut lowered = source
        .replacen(
            "#version 330",
            "#version 450\n#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1\n#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1",
            1,
        )
        .replacen("in vec2 texCoord;", "layout(location = 0) in vec2 texCoord;", 1)
        .replacen(
            "out vec4 fragColor;",
            "layout(location = 0) out vec4 fragColor;",
            1,
    );
    for (binding, input) in pass.inputs.iter().enumerate() {
        let shader_sampler_name = format!("{}Sampler", input.sampler_name);
        let declaration = format!("uniform sampler2D {shader_sampler_name};");
        let explicit = format!(
            "layout(set = 0, binding = {binding}) uniform sampler2D {shader_sampler_name};",
        );
        if !lowered.contains(&declaration) {
            return Err(GalError::invalid_argument(format!(
                "vanilla post-effect pass shader is missing sampler declaration {}",
                shader_sampler_name
            )));
        }
        lowered = lowered.replacen(&declaration, &explicit, 1);
    }
    for (block_index, block_name) in pass.uniform_values.keys().enumerate() {
        let uniform_binding = pass.inputs.len() + block_index;
        let declaration = format!("layout(std140) uniform {block_name}");
        let explicit = format!(
            "layout(set = 0, binding = {uniform_binding}, std140) uniform {block_name}"
        );
        if lowered.contains(&declaration) {
            lowered = lowered.replacen(&declaration, &explicit, 1);
        }
    }
    Ok(lowered.into_bytes())
}

/// Packs one validated uniform block according to the scalar/vector subset used
/// by bundled vanilla post effects. The returned bytes are backend-neutral and
/// can be uploaded to a Rust-owned uniform buffer without Java reflection.
pub fn pack_uniform_block(
    pass: &VanillaPostEffectPass,
    block_name: &str,
) -> GalResult<Vec<u8>> {
    let uniforms = pass.uniform_values.get(block_name).ok_or_else(|| {
        GalError::invalid_argument(format!(
            "vanilla post effect pass has no uniform block {block_name}"
        ))
    })?;
    let mut bytes = Vec::new();
    for uniform in uniforms {
        let (alignment, padded_size, expected_values) = match uniform.value_type.as_str() {
            "float" => (4usize, 4usize, 1usize),
            "vec2" => (8usize, 8usize, 2usize),
            "vec3" => (16usize, 16usize, 3usize),
            "vec4" => (16usize, 16usize, 4usize),
            value_type => {
                return Err(GalError::unsupported_feature(format!(
                    "vanilla post effect uniform block {block_name} uses unsupported type {value_type}"
                )))
            }
        };
        if uniform.values.len() != expected_values {
            return Err(GalError::invalid_argument(format!(
                "vanilla post effect uniform {} declares {} but supplies {} values",
                uniform.name,
                uniform.value_type,
                uniform.values.len()
            )));
        }
        if uniform.values.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(format!(
                "vanilla post effect uniform {} contains a non-finite value",
                uniform.name
            )));
        }
        let aligned = align_uniform_offset(bytes.len(), alignment);
        bytes.resize(aligned, 0);
        for value in &uniform.values {
            bytes.extend_from_slice(&value.to_le_bytes());
        }
        bytes.resize(aligned + padded_size, 0);
    }
    bytes.resize(align_uniform_offset(bytes.len(), 16), 0);
    if bytes.len() > MAX_PACKED_UNIFORM_BLOCK_BYTES {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect uniform block {block_name} exceeds {} packed bytes",
            MAX_PACKED_UNIFORM_BLOCK_BYTES
        )));
    }
    Ok(bytes)
}

/// Packs every declared block in stable lexical order and enforces a bounded
/// aggregate upload budget for one pass.
pub fn pack_uniform_blocks(
    pass: &VanillaPostEffectPass,
) -> GalResult<BTreeMap<String, Vec<u8>>> {
    let mut packed = BTreeMap::new();
    let mut total = 0usize;
    for block_name in pass.uniform_values.keys() {
        let bytes = pack_uniform_block(pass, block_name)?;
        total = total.checked_add(bytes.len()).ok_or_else(|| {
            GalError::invalid_argument("vanilla post effect uniform upload size overflow")
        })?;
        if total > MAX_PACKED_UNIFORM_BLOCK_BYTES {
            return Err(GalError::invalid_argument(format!(
                "vanilla post effect pass exceeds {} packed uniform bytes",
                MAX_PACKED_UNIFORM_BLOCK_BYTES
            )));
        }
        packed.insert(block_name.clone(), bytes);
    }
    Ok(packed)
}

fn align_uniform_offset(offset: usize, alignment: usize) -> usize {
    (offset + alignment - 1) / alignment * alignment
}

pub fn lower_execution_plan(
    plan: &VanillaPostEffectExecutionPlan,
    bindings: &[VanillaPostEffectPassBinding],
) -> GalResult<Vec<CommandOp>> {
    VanillaPostEffectExecutor::new(plan.clone())?.lower(bindings)
}

fn lower_validated_plan(
    plan: &VanillaPostEffectExecutionPlan,
    bindings: &[VanillaPostEffectPassBinding],
) -> GalResult<Vec<CommandOp>> {
    if plan.ordered_passes.len() != bindings.len() {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect {} has {} passes but {} private bindings",
            plan.effect_name,
            plan.ordered_passes.len(),
            bindings.len()
        )));
    }
    let mut operations = Vec::with_capacity(bindings.len() * 6);
    for (index, (pass, binding)) in plan.ordered_passes.iter().zip(bindings).enumerate() {
        validate_binding(&plan.effect_name, index, pass, binding, index + 1 == plan.ordered_passes.len())?;
        operations.push(CommandOp::BeginPass {
            pass: binding.render_pass,
            target: binding.render_target,
            colors: vec![PassAttachment {
                view: binding.color_attachment,
                load_op: AttachmentLoadOp::DontCare,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: binding.depth_attachment.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::DontCare,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(binding.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: binding.pipeline_layout,
            set_index: 0,
            set: binding.resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
    }
    Ok(operations)
}

fn validate_binding(
    effect_name: &str,
    index: usize,
    pass: &VanillaPostEffectPass,
    binding: &VanillaPostEffectPassBinding,
    allow_frame_target: bool,
) -> GalResult<()> {
    for (label, handle, expected) in [
        ("render pass", binding.render_pass, HandleKind::RenderPass),
        ("pipeline", binding.pipeline, HandleKind::GraphicsPipeline),
        ("pipeline layout", binding.pipeline_layout, HandleKind::PipelineLayout),
        ("resource set", binding.resource_set, HandleKind::ResourceSet),
    ] {
        handle.require_kind(expected).map_err(|error| {
            GalError::invalid_argument(format!(
                "vanilla post effect {effect_name} pass {index} has invalid {label} binding: {error}"
            ))
        })?;
    }
    let color_valid = binding.color_attachment.kind() == Some(HandleKind::TextureView)
        || (allow_frame_target
            && binding.color_attachment.kind() == Some(HandleKind::FrameTarget));
    if !color_valid {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} has invalid color attachment binding: expected TextureView{} got {:?}",
            if allow_frame_target { " or FrameTarget" } else { "" },
            binding.color_attachment.kind(),
        )));
    }
    if let Some(depth) = binding.depth_attachment {
        depth.require_kind(HandleKind::TextureView).map_err(|error| {
            GalError::invalid_argument(format!(
                "vanilla post effect {effect_name} pass {index} has invalid depth attachment: {error}"
            ))
        })?;
    }
    let target_valid = binding.render_target.kind() == Some(HandleKind::RenderTarget)
        || (allow_frame_target && binding.render_target.kind() == Some(HandleKind::FrameTarget));
    if !target_valid {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} has invalid render target binding: expected RenderTarget{} got {:?}",
            if allow_frame_target { " or FrameTarget" } else { "" },
            binding.render_target.kind(),
        )));
    }
    if binding.inputs.len() != pass.inputs.len() {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} has {} graph inputs but {} private input bindings",
            pass.inputs.len(),
            binding.inputs.len()
        )));
    }
    for (input, private) in pass.inputs.iter().zip(&binding.inputs) {
        validate_input_binding(effect_name, index, input, private)?;
    }
    if binding.uniform_values != pass.uniform_values {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} private uniform values do not match the validated graph"
        )));
    }
    // Validate the exact backend-neutral upload representation before command
    // generation; a later resource writer may consume these bytes directly.
    pack_uniform_blocks(pass)?;
    Ok(())
}

fn validate_input_binding(
    effect_name: &str,
    index: usize,
    input: &VanillaPostEffectInput,
    binding: &VanillaPostEffectInputBinding,
) -> GalResult<()> {
    binding.texture_view.require_kind(HandleKind::TextureView).map_err(|error| {
        GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} input {} has invalid texture view: {error}",
            input.sampler_name
        ))
    })?;
    binding.sampler.require_kind(HandleKind::Sampler).map_err(|error| {
        GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} input {} has invalid sampler: {error}",
            input.sampler_name
        ))
    })?;
    if binding.bilinear != input.bilinear || binding.use_depth_buffer != input.use_depth_buffer {
        return Err(GalError::invalid_argument(format!(
            "vanilla post effect {effect_name} pass {index} input {} sampling flags do not match the graph",
            input.sampler_name
        )));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::handles::HandleKind;
    use super::super::vanilla_post_effect_contract::VanillaPostEffectContract;

    fn handle(kind: HandleKind, index: u32) -> Handle {
        Handle::new(kind, index, 1).unwrap()
    }

    #[test]
    fn lowers_each_validated_pass_to_one_explicit_triangle_pass() {
        let contract = VanillaPostEffectContract::parse(
            "invert",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/invert.json"),
        )
        .unwrap();
        let plan = contract.execution_plan();
        let bindings = (0..plan.ordered_passes.len())
            .map(|index| VanillaPostEffectPassBinding {
                render_pass: handle(HandleKind::RenderPass, index as u32 + 1),
                render_target: handle(HandleKind::RenderTarget, index as u32 + 1),
                color_attachment: handle(HandleKind::TextureView, index as u32 + 1),
                depth_attachment: None,
                pipeline: handle(HandleKind::GraphicsPipeline, index as u32 + 1),
                pipeline_layout: handle(HandleKind::PipelineLayout, index as u32 + 1),
                resource_set: handle(HandleKind::ResourceSet, index as u32 + 1),
                inputs: vec![VanillaPostEffectInputBinding {
                    texture_view: handle(HandleKind::TextureView, index as u32 + 1),
                    sampler: handle(HandleKind::Sampler, index as u32 + 1),
                    bilinear: false,
                    use_depth_buffer: false,
                }],
                uniform_values: plan.ordered_passes[index].uniform_values.clone(),
            })
            .collect::<Vec<_>>();
        let operations = lower_execution_plan(&plan, &bindings).unwrap();
        assert_eq!(plan.ordered_passes.len() * 5, operations.len());
        assert!(operations.iter().any(|operation| matches!(operation, CommandOp::Draw { vertices: 3, instances: 1 })));
    }

    #[test]
    fn null_private_binding_is_rejected_before_command_generation() {
        let contract = VanillaPostEffectContract::parse(
            "invert",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/invert.json"),
        )
        .unwrap();
        let plan = contract.execution_plan();
        let bindings = vec![VanillaPostEffectPassBinding {
            render_pass: Handle::NULL,
            render_target: handle(HandleKind::RenderTarget, 1),
            color_attachment: handle(HandleKind::TextureView, 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, 1),
            resource_set: handle(HandleKind::ResourceSet, 1),
            inputs: vec![VanillaPostEffectInputBinding {
                texture_view: handle(HandleKind::TextureView, 1),
                sampler: handle(HandleKind::Sampler, 1),
                bilinear: false,
                use_depth_buffer: false,
            }],
            uniform_values: plan.ordered_passes[0].uniform_values.clone(),
        }; plan.ordered_passes.len()];
        let error = lower_execution_plan(&plan, &bindings).unwrap_err();
        assert!(error.to_string().contains("invalid render pass binding"));
        assert!(error.to_string().contains("null handle"));
    }

    #[test]
    fn wrong_private_binding_kind_is_rejected_before_command_generation() {
        let contract = VanillaPostEffectContract::parse(
            "invert",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/invert.json"),
        )
        .unwrap();
        let plan = contract.execution_plan();
        let bindings = vec![VanillaPostEffectPassBinding {
            render_pass: handle(HandleKind::Buffer, 1),
            render_target: handle(HandleKind::RenderTarget, 1),
            color_attachment: handle(HandleKind::TextureView, 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, 1),
            resource_set: handle(HandleKind::ResourceSet, 1),
            inputs: vec![VanillaPostEffectInputBinding {
                texture_view: handle(HandleKind::TextureView, 1),
                sampler: handle(HandleKind::Sampler, 1),
                bilinear: false,
                use_depth_buffer: false,
            }],
            uniform_values: plan.ordered_passes[0].uniform_values.clone(),
        }; plan.ordered_passes.len()];
        let error = lower_execution_plan(&plan, &bindings).unwrap_err();
        assert!(error.to_string().contains("invalid render pass binding"));
        assert!(error.to_string().contains("expected RenderPass"));
    }

    #[test]
    fn graph_sampling_flags_must_match_private_input_bindings() {
        let contract = VanillaPostEffectContract::parse(
            "blur",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/blur.json"),
        )
        .unwrap();
        let plan = contract.execution_plan();
        let mut bindings = plan
            .ordered_passes
            .iter()
            .enumerate()
            .map(|(index, pass)| VanillaPostEffectPassBinding {
                render_pass: handle(HandleKind::RenderPass, index as u32 + 1),
                render_target: handle(HandleKind::RenderTarget, index as u32 + 1),
                color_attachment: handle(HandleKind::TextureView, index as u32 + 1),
                depth_attachment: None,
                pipeline: handle(HandleKind::GraphicsPipeline, index as u32 + 1),
                pipeline_layout: handle(HandleKind::PipelineLayout, index as u32 + 1),
                resource_set: handle(HandleKind::ResourceSet, index as u32 + 1),
                inputs: pass
                    .inputs
                    .iter()
                    .map(|input| VanillaPostEffectInputBinding {
                        texture_view: handle(HandleKind::TextureView, index as u32 + 1),
                        sampler: handle(HandleKind::Sampler, index as u32 + 1),
                        bilinear: input.bilinear,
                        use_depth_buffer: input.use_depth_buffer,
                    })
                    .collect(),
                uniform_values: pass.uniform_values.clone(),
            })
            .collect::<Vec<_>>();
        bindings[0].inputs[0].bilinear = false;
        let error = lower_execution_plan(&plan, &bindings).unwrap_err();
        assert!(error.to_string().contains("sampling flags do not match"));
    }

    #[test]
    fn packs_bundled_uniform_blocks_with_std140_alignment() {
        let contract = VanillaPostEffectContract::parse(
            "blur",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/blur.json"),
        )
        .unwrap();
        let block = pack_uniform_block(&contract.passes[0], "BlurConfig").unwrap();
        // vec2 at offset 0, scalar at offset 8, block rounded to 16 bytes.
        assert_eq!(16, block.len());
        assert_eq!(&block[0..4], &1.0f32.to_le_bytes());
        assert_eq!(&block[4..8], &0.0f32.to_le_bytes());
        assert_eq!(&block[8..12], &0.0f32.to_le_bytes());
        assert!(block[12..].iter().all(|byte| *byte == 0));

        let invert = VanillaPostEffectContract::parse(
            "invert",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/invert.json"),
        )
        .unwrap();
        let inverse = pack_uniform_block(&invert.passes[0], "InvertConfig").unwrap();
        assert_eq!(16, inverse.len());
        assert_eq!(&inverse[0..4], &0.8f32.to_le_bytes());
        let packed = pack_uniform_blocks(&invert.passes[0]).unwrap();
        assert_eq!(packed.len(), 1);
        assert_eq!(packed.get("InvertConfig"), Some(&inverse));
    }

    #[test]
    fn normalizes_multiple_static_uniform_blocks_to_distinct_vulkan_bindings() {
        let mut pass = VanillaPostEffectPass {
            vertex_shader: "fullscreen.vsh".to_owned(),
            fragment_shader: "fullscreen.fsh".to_owned(),
            inputs: vec![VanillaPostEffectInput {
                sampler_name: "In".to_owned(),
                target: "minecraft:main".to_owned(),
                bilinear: true,
                use_depth_buffer: false,
            }],
            output: "minecraft:main".to_owned(),
            uniform_blocks: BTreeSet::from(["First".to_owned(), "Second".to_owned()]),
            uniform_values: BTreeMap::from([
                ("First".to_owned(), Vec::new()),
                ("Second".to_owned(), Vec::new()),
            ]),
        };
        let source = "#version 330\nin vec2 texCoord;\nuniform sampler2D InSampler;\nlayout(std140) uniform First { vec4 value; };\nlayout(std140) uniform Second { vec4 value; };\nout vec4 fragColor;";
        pass.fragment_shader = source.to_owned();
        let lowered = normalize_vulkan_fullscreen_source(BackendApi::Vulkan, source.as_bytes(), &pass).unwrap();
        let lowered = String::from_utf8(lowered).unwrap();
        assert!(lowered.contains("layout(set = 0, binding = 1, std140) uniform First"));
        assert!(lowered.contains("layout(set = 0, binding = 2, std140) uniform Second"));
    }

    #[test]
    fn uniform_packer_rejects_declared_type_and_value_count_mismatch() {
        let contract = VanillaPostEffectContract::parse(
            "invert",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/invert.json"),
        )
        .unwrap();
        let mut invalid = contract.passes[0].clone();
        invalid
            .uniform_values
            .get_mut("InvertConfig")
            .unwrap()[0]
            .values
            .push(0.25);
        let error = pack_uniform_block(&invalid, "InvertConfig").unwrap_err();
        assert!(error.to_string().contains("declares float but supplies 2 values"));
    }

    #[test]
    fn executor_owns_validated_plan_before_submission() {
        let contract = VanillaPostEffectContract::parse(
            "minecraft:invert",
            include_bytes!("../../../../resources/assets/minecraft/post_effect/invert.json"),
        )
        .unwrap();
        let executor = VanillaPostEffectExecutor::new(contract.execution_plan().clone()).unwrap();
        assert_eq!(executor.plan().effect_name, "minecraft:invert");
        assert_eq!(executor.plan().ordered_passes.len(), 2);
    }

    #[test]
    fn bundled_entity_outline_executor_owns_the_complete_four_pass_graph() {
        let executor = bundled_entity_outline_executor().unwrap();
        assert_eq!("minecraft:entity_outline", executor.plan().effect_name);
        assert_eq!(4, executor.plan().ordered_passes.len());
        assert_eq!(
            "minecraft:entity_outline",
            executor.plan().ordered_passes[0].inputs[0].target
        );
        assert_eq!(
            "minecraft:entity_outline",
            executor.plan().ordered_passes[3].output
        );
    }

    #[test]
    fn bundled_transparency_executor_owns_all_external_attachment_inputs() {
        let executor = bundled_transparency_executor().unwrap();
        assert_eq!("minecraft:transparency", executor.plan().effect_name);
        assert_eq!(2, executor.plan().ordered_passes.len());
        let first = &executor.plan().ordered_passes[0];
        assert_eq!("final", first.output);
        assert_eq!(12, first.inputs.len());
        assert!(first.inputs.iter().any(|input| {
            input.target == "minecraft:weather" && input.use_depth_buffer
        }));
        assert_eq!("minecraft:main", executor.plan().ordered_passes[1].output);
        let sources = bundled_transparency_shader_sources().unwrap();
        assert_eq!(2, sources.len());
        assert!(sources.iter().all(|source| {
            !source.vertex_shader.is_empty() && !source.fragment_shader.is_empty()
        }));
        let vulkan_sources = bundled_transparency_vulkan_shader_sources().unwrap();
        assert_eq!(2, vulkan_sources.len());
        let first_vertex = String::from_utf8(vulkan_sources[0].0.clone()).unwrap();
        assert!(first_vertex.contains("#version 450"));
        assert!(first_vertex.contains("layout(location = 0) out vec2 texCoord;"));
        let first_fragment = String::from_utf8(vulkan_sources[0].1.clone()).unwrap();
        assert!(first_fragment.contains("#version 450"));
        assert!(first_fragment.contains("layout(set = 0, binding = 0) uniform sampler2D MainSampler;"));
        assert!(first_fragment.contains("layout(set = 0, binding = 11) uniform sampler2D"));
        let second_fragment = String::from_utf8(vulkan_sources[1].1.clone()).unwrap();
        assert!(second_fragment.contains("layout(set = 0, binding = 0) uniform sampler2D InSampler;"));
        assert!(second_fragment.contains("layout(set = 0, binding = 1, std140) uniform BlitConfig"));

        let inventory = FabulousExternalTargetInventory {
            main: VanillaPostEffectExternalTargetBinding {
                render_target: handle(HandleKind::RenderTarget, 1),
                color_attachment: handle(HandleKind::TextureView, 1),
                depth_attachment: Some(handle(HandleKind::TextureView, 1)),
                sampler: handle(HandleKind::Sampler, 1),
            },
            translucent: VanillaPostEffectExternalTargetBinding {
                render_target: handle(HandleKind::RenderTarget, 2),
                color_attachment: handle(HandleKind::TextureView, 2),
                depth_attachment: Some(handle(HandleKind::TextureView, 2)),
                sampler: handle(HandleKind::Sampler, 2),
            },
            item_entity: VanillaPostEffectExternalTargetBinding {
                render_target: handle(HandleKind::RenderTarget, 3),
                color_attachment: handle(HandleKind::TextureView, 3),
                depth_attachment: Some(handle(HandleKind::TextureView, 3)),
                sampler: handle(HandleKind::Sampler, 3),
            },
            particles: VanillaPostEffectExternalTargetBinding {
                render_target: handle(HandleKind::RenderTarget, 4),
                color_attachment: handle(HandleKind::TextureView, 4),
                depth_attachment: Some(handle(HandleKind::TextureView, 4)),
                sampler: handle(HandleKind::Sampler, 4),
            },
            clouds: VanillaPostEffectExternalTargetBinding {
                render_target: handle(HandleKind::RenderTarget, 5),
                color_attachment: handle(HandleKind::TextureView, 5),
                depth_attachment: Some(handle(HandleKind::TextureView, 5)),
                sampler: handle(HandleKind::Sampler, 5),
            },
            weather: VanillaPostEffectExternalTargetBinding {
                render_target: handle(HandleKind::RenderTarget, 6),
                color_attachment: handle(HandleKind::TextureView, 6),
                depth_attachment: Some(handle(HandleKind::TextureView, 6)),
                sampler: handle(HandleKind::Sampler, 6),
            },
        };
        let external = inventory.validate_against(executor.plan()).unwrap();
        assert!(external.get("minecraft:particles").is_some());

        let error = FabulousExternalTargetInventory {
            main: inventory.main,
            translucent: VanillaPostEffectExternalTargetBinding { depth_attachment: None, ..inventory.translucent },
            item_entity: inventory.item_entity,
            particles: inventory.particles,
            clouds: inventory.clouds,
            weather: inventory.weather,
        }
        .validate_against(executor.plan())
        .unwrap_err();
        assert!(error.to_string().contains("depth attachment"));

        let error = FabulousExternalTargetInventory {
            particles: inventory.main,
            ..inventory
        }
        .validate_against(executor.plan())
        .unwrap_err();
        assert!(error.to_string().contains("must not alias"));
    }
}
