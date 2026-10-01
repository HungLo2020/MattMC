//! Explicit command generation for validated vanilla fullscreen graphs.
//!
//! Resource creation and shader compilation remain owned by the GAL runtime;
//! this module only lowers an already validated semantic plan into command
//! operations using caller-supplied private handles.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::shaderpack::vanilla::post_effect::contract::{
    VanillaPostEffectExecutionPlan, VanillaPostEffectInput, VanillaPostEffectPass,
    VanillaPostEffectUniform,
};
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandOp, PassAttachment, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::{GlslDialect, ShaderConventions};

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
    /// Rust-owned pass compatible with the target's attachment layout.  A
    /// target/view pair without its pass is not executable: requiring the
    /// pass here prevents callers from reconstructing implicit framebuffer
    /// state at the last lowering step.
    pub render_pass: Handle,
    pub render_target: Handle,
    pub color_attachment: Handle,
    pub depth_attachment: Option<Handle>,
    pub sampler: Handle,
    /// Current semantic usage at the point this graph is lowered.  The
    /// coordinator supplies this state; the executor never guesses from a
    /// backend framebuffer.
    pub color_usage: TextureUsageState,
    pub depth_usage: Option<TextureUsageState>,
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

    /// Projects the typed Fabulous inventory down to exactly the roles a
    /// copied shader-pack graph declares.  The full inventory is appropriate
    /// for the bundled transparency graph, but passing all six roles to a
    /// smaller graph would (correctly) look like undeclared external state.
    /// This projection keeps validation graph-driven and prevents accidental
    /// attachment aliasing or Java target substitution.
    pub fn validate_for_plan(
        self,
        plan: &VanillaPostEffectExecutionPlan,
    ) -> GalResult<VanillaPostEffectExternalTargetBindings> {
        let all = self.bindings();
        let required = plan.required_external_targets();
        let selected = required
            .iter()
            .map(|name| {
                all.get(name)
                    .copied()
                    .map(|binding| (name.clone(), binding))
                    .ok_or_else(|| {
                        GalError::unsupported_feature(format!(
                            "vanilla post effect {} has no Rust-owned Fabulous role for {name}",
                            plan.effect_name
                        ))
                    })
            })
            .collect::<GalResult<BTreeMap<_, _>>>()?;
        VanillaPostEffectExternalTargetBindings::new(plan, selected)
    }

    /// Validates both the Rust-owned resource inventory and the frame-local
    /// write receipt. Allocation alone is not enough: sampling an attachment
    /// that was not populated by this frame would create a silent visual
    /// fallback. The coordinator supplies semantic role names, never backend
    /// or Java target handles.
    pub fn validate_populated_for_plan(
        self,
        plan: &VanillaPostEffectExecutionPlan,
        populated_roles: &BTreeSet<String>,
    ) -> GalResult<VanillaPostEffectExternalTargetBindings> {
        let required = plan.required_external_targets();
        let missing = required
            .difference(populated_roles)
            .cloned()
            .collect::<Vec<_>>();
        if !missing.is_empty() {
            return Err(GalError::unsupported_feature(format!(
                "vanilla post effect {} has unpopulated Rust external targets: {}",
                plan.effect_name,
                missing.join(", ")
            )));
        }
        self.validate_for_plan(plan)
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
            binding.render_pass.require_kind(HandleKind::RenderPass).map_err(|error| {
                GalError::invalid_argument(format!(
                    "vanilla post effect {} external target {name} has invalid render pass: {error}",
                    plan.effect_name
                ))
            })?;
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
            binding
                .sampler
                .require_kind(HandleKind::Sampler)
                .map_err(|error| {
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
            if binding.depth_attachment.is_some() != binding.depth_usage.is_some() {
                return Err(GalError::invalid_argument(format!(
                    "vanilla post effect {} external target {name} depth usage does not match its depth attachment",
                    plan.effect_name
                )));
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

    pub fn names(&self) -> impl Iterator<Item = &str> {
        self.bindings.keys().map(String::as_str)
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
    let contract = crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract::parse(
        "minecraft:entity_outline",
        include_bytes!("../../../../../resources/assets/minecraft/post_effect/entity_outline.json"),
    )?;
    VanillaPostEffectExecutor::new(contract.execution_plan())
}

/// Constructs the bundled vanilla transparency executor from Rust-owned
/// resource bytes.  This remains a private capability until the frame
/// coordinator supplies every declared external attachment (main,
/// translucent, item-entity, particles, clouds, and weather); callers must not
/// substitute Java PostChain targets for those bindings.
pub fn bundled_transparency_executor() -> GalResult<VanillaPostEffectExecutor> {
    let contract = crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract::parse(
        "minecraft:transparency",
        include_bytes!("../../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )?;
    VanillaPostEffectExecutor::new(contract.execution_plan())
}

/// Returns copied Rust-owned shader bytes for every bundled outline pass.
/// Missing sources are an explicit preparation failure, never a Java/Iris
/// lookup fallback.
pub fn bundled_entity_outline_shader_sources(
) -> GalResult<Vec<crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectShaderSource>> {
    let contract = crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract::parse(
        "minecraft:entity_outline",
        include_bytes!("../../../../../resources/assets/minecraft/post_effect/entity_outline.json"),
    )?;
    contract.shader_sources()
}

/// Returns the copied shader sources for the bundled transparency graph.  The
/// graph can only become executable after its external attachment inventory is
/// validated by the frame coordinator; shader lookup itself never consults a
/// Java `ShaderManager` or Iris runtime object.
pub fn bundled_transparency_shader_sources(
) -> GalResult<Vec<crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectShaderSource>> {
    let contract = crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract::parse(
        "minecraft:transparency",
        include_bytes!("../../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )?;
    contract.shader_sources()
}

/// Lowers copied vanilla fullscreen GLSL into the explicit Vulkan descriptor
/// ABI used by the Rust post-effect resource writer. The source graph remains
/// semantic input: sampler and uniform binding numbers are derived from the
/// validated pass order, never from Java's runtime shader objects.
pub fn bundled_transparency_lowered_shader_sources(
    conventions: ShaderConventions,
) -> GalResult<Vec<(Vec<u8>, Vec<u8>)>> {
    let contract = crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract::parse(
        "minecraft:transparency",
        include_bytes!("../../../../../resources/assets/minecraft/post_effect/transparency.json"),
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
                lower_post_effect_vertex_source(conventions, &source.vertex_shader)?,
                lower_post_effect_fragment_source(
                    conventions,
                    &source.fragment_shader,
                    pass,
                )?,
            ))
        })
        .collect()
}

/// `#version 450` plus the convention macros the backend needs; the UV flip
/// applies only where attachment rows keep their original order.
fn explicit_binding_version_header(conventions: ShaderConventions, flip_uv: bool) -> String {
    let mut header = String::from("#version 450");
    if conventions.zero_to_one_clip_depth {
        header.push_str("\n#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1");
    }
    if flip_uv && conventions.flip_fullscreen_uv_y {
        header.push_str("\n#define VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y 1");
    }
    header
}

pub(crate) fn lower_post_effect_vertex_source(conventions: ShaderConventions, source: &[u8]) -> GalResult<Vec<u8>> {
    lower_post_effect_vertex_source_for_input_rows(
        conventions,
        source,
        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
    )
}

/// Preserve copied shader arithmetic when attachment inputs have already
/// been converted into the shader's original row convention explicitly.
pub(crate) fn lower_post_effect_vertex_source_for_input_rows(
    conventions: ShaderConventions,
    source: &[u8],
    rows: crate::render::vulkanic::commands::TextureRowOrder,
) -> GalResult<Vec<u8>> {
    let source = std::str::from_utf8(source).map_err(|_| {
        GalError::invalid_argument("vanilla post-effect vertex shader is not UTF-8")
    })?;
    if conventions.glsl_dialect != GlslDialect::ExplicitBindings {
        return Ok(source.as_bytes().to_vec());
    }
    let preserve_rows = rows == crate::render::vulkanic::commands::TextureRowOrder::Preserve;
    Ok(source
        .replacen(
            "#version 330",
            &explicit_binding_version_header(conventions, preserve_rows),
            1,
        )
        .replacen("out vec2 texCoord;", "layout(location = 0) out vec2 texCoord;",
            usize::from(source.lines().any(|line| line.trim() == "out vec2 texCoord;")))
        .replace("gl_VertexID", "gl_VertexIndex")
        // Vanilla's screenquad source assigns texture coordinates directly
        // from the fullscreen triangle. Vulkan attachment sampling has the
        // opposite row origin, so make the declared normalization real
        // instead of only defining its feature macro. Without this, the
        // Rust-owned Fabulous composition is a vertically mirrored game
        // frame even though all named attachments and ordering are correct.
        .replacen(
            "texCoord = uv;",
            if rows == crate::render::vulkanic::commands::TextureRowOrder::Preserve {
                "texCoord = uv;\n#ifdef VULKANIC_GAL_FLIP_FULLSCREEN_UV_Y\n    texCoord.y = 1.0 - texCoord.y;\n#endif"
            } else { "texCoord = uv;" },
            1,
        )
        .into_bytes())
}

pub(crate) fn lower_post_effect_vertex_source_for_pass(
    conventions: ShaderConventions,
    source: &[u8],
    pass: &VanillaPostEffectPass,
    rows: crate::render::vulkanic::commands::TextureRowOrder,
) -> GalResult<Vec<u8>> {
    let normalized = lower_post_effect_vertex_source_for_input_rows(conventions, source, rows)?;
    if conventions.glsl_dialect != GlslDialect::ExplicitBindings {
        return Ok(normalized);
    }
    let source = std::str::from_utf8(&normalized)
        .map_err(|_| GalError::invalid_argument("post-effect vertex shader is not UTF-8"))?;
    Ok(bind_explicit_uniform_blocks(source.to_owned(), pass).into_bytes())
}

fn bind_explicit_uniform_blocks(mut source: String, pass: &VanillaPostEffectPass) -> String {
    // The sorted semantic block inventory assigns identical descriptor slots
    // in both stages. A block absent from one stage needs no declaration there.
    for (block_index, block_name) in pass.uniform_values.keys().enumerate() {
        let binding = pass.inputs.len() + block_index;
        source = source.replacen(
            &format!("layout(std140) uniform {block_name}"),
            &format!("layout(set = 0, binding = {binding}, std140) uniform {block_name}"),
            1,
        );
    }
    source
}

pub(crate) fn lower_post_effect_fragment_source(
    conventions: ShaderConventions,
    source: &[u8],
    pass: &VanillaPostEffectPass,
) -> GalResult<Vec<u8>> {
    let source = std::str::from_utf8(source)
        .map_err(|_| GalError::invalid_argument("vanilla post-effect shader is not UTF-8"))?;
    if conventions.glsl_dialect != GlslDialect::ExplicitBindings {
        return Ok(source.as_bytes().to_vec());
    }
    let mut lowered = source
        .replacen("#version 330", &explicit_binding_version_header(conventions, true), 1)
        .replacen("in vec2 texCoord;", "layout(location = 0) in vec2 texCoord;",
            usize::from(source.lines().any(|line| line.trim() == "in vec2 texCoord;")))
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
    Ok(bind_explicit_uniform_blocks(lowered, pass).into_bytes())
}

/// Packs one validated uniform block according to the scalar/vector subset used
/// by bundled vanilla post effects. The returned bytes are backend-neutral and
/// can be uploaded to a Rust-owned uniform buffer without Java reflection.
pub fn pack_uniform_block(pass: &VanillaPostEffectPass, block_name: &str) -> GalResult<Vec<u8>> {
    let uniforms = pass.uniform_values.get(block_name).ok_or_else(|| {
        GalError::invalid_argument(format!(
            "vanilla post effect pass has no uniform block {block_name}"
        ))
    })?;
    let mut bytes = Vec::new();
    for uniform in uniforms {
        let (alignment, padded_size, expected_values) = match uniform.value_type.as_str() {
            "float" | "int" => (4usize, 4usize, 1usize),
            "vec2" | "ivec2" => (8usize, 8usize, 2usize),
            "vec3" | "ivec3" => (16usize, 16usize, 3usize),
            "vec4" | "ivec4" => (16usize, 16usize, 4usize),
            // A mat4 is four std140 vec4 columns, with a 16-byte base
            // alignment and a 64-byte occupied range.
            "matrix4x4" => (16usize, 64usize, 16usize),
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
        let integer = matches!(
            uniform.value_type.as_str(),
            "int" | "ivec2" | "ivec3" | "ivec4"
        );
        if integer
            && uniform.values.iter().any(|value| {
                value.fract() != 0.0 || *value < i32::MIN as f32 || *value > i32::MAX as f32
            })
        {
            return Err(GalError::invalid_argument(format!(
                "vanilla post effect integer uniform {} must contain finite 32-bit integral values",
                uniform.name
            )));
        }
        let aligned = align_uniform_offset(bytes.len(), alignment);
        bytes.resize(aligned, 0);
        for value in &uniform.values {
            if integer {
                bytes.extend_from_slice(&(*value as i32).to_le_bytes());
            } else {
                bytes.extend_from_slice(&value.to_le_bytes());
            }
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
pub fn pack_uniform_blocks(pass: &VanillaPostEffectPass) -> GalResult<BTreeMap<String, Vec<u8>>> {
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
        validate_binding(
            &plan.effect_name,
            index,
            pass,
            binding,
            index + 1 == plan.ordered_passes.len(),
        )?;
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
        (
            "pipeline layout",
            binding.pipeline_layout,
            HandleKind::PipelineLayout,
        ),
        (
            "resource set",
            binding.resource_set,
            HandleKind::ResourceSet,
        ),
    ] {
        handle.require_kind(expected).map_err(|error| {
            GalError::invalid_argument(format!(
                "vanilla post effect {effect_name} pass {index} has invalid {label} binding: {error}"
            ))
        })?;
    }
    let color_valid = binding.color_attachment.kind() == Some(HandleKind::TextureView)
        || (allow_frame_target && binding.color_attachment.kind() == Some(HandleKind::FrameTarget));
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
    binding
        .sampler
        .require_kind(HandleKind::Sampler)
        .map_err(|error| {
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
mod tests;
