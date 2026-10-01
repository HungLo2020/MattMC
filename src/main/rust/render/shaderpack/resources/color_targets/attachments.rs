//! Resolving source color attachments by slot and their sampling plans.

use super::*;

/// One resolved, Rust-owned color attachment for a source fullscreen pass.
/// `source_slot` is the semantic destination recovered from `DRAWBUFFERS`.
/// The caller orders attachments by the lowered GLSL output location, while
/// this record keeps semantic target identity separate from that ordinal. The
/// texture and view are opaque GAL handles; native attachment state stays in
/// the backend.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct FullscreenSourceColorAttachment {
    pub source_slot: u32,
    pub role: TerrainSourceResourceRole,
    pub texture: Handle,
    pub view: Handle,
    pub format: TextureFormat,
    pub clear_each_frame: bool,
    /// Source-declared clear value, if any. A primary target without one uses
    /// the dynamic semantic fog color supplied for the source frame.
    pub clear_color_bits: Option<[u32; 4]>,
}

/// One Rust-owned target selected for a named terrain fragment output. The
/// source slot retains pack-level meaning while `output` keeps the lowered
/// terrain shader's compact output ordering explicit. Neither is a native
/// attachment index or a backend object.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct TerrainSourceColorAttachment {
    pub output: TerrainPassOutput,
    pub source_slot: u32,
    pub role: TerrainSourceResourceRole,
    pub texture: Handle,
    pub view: Handle,
    pub format: TextureFormat,
    pub clear_each_frame: bool,
    pub clear_color_bits: Option<[u32; 4]>,
}

/// Per-binding source-color sampling requirements. The plan is intentionally
/// independent of any source stage type: terrain, Distant Horizons, and
/// fullscreen programs all sample the same named color resources through this
/// semantic policy. A missing entry means current-image, base-mip sampling.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub(crate) struct ShaderPackColorSamplingPlan {
    pub(super) bindings: BTreeMap<(TerrainSourceResourceRole, u32), SourceColorBinding>,
}

impl ShaderPackColorSamplingPlan {
    pub(crate) fn from_fullscreen(program: &LoweredFullscreenSourceProgram) -> Self {
        let mut bindings = BTreeMap::new();
        for binding in program.opaque_resource_bindings.bindings() {
            if !matches!(
                binding.kind(),
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler
            ) || !matches!(
                binding.role(),
                TerrainSourceResourceRole::ShaderPackColor(_)
            ) {
                continue;
            }
            bindings.insert(
                (binding.role(), binding.binding()),
                SourceColorBinding {
                    feedback: program.feedback_requirements.iter().any(|requirement| {
                        requirement.role == binding.role()
                            && requirement.sampled_binding == binding.binding()
                    }),
                    mipmapped: program.mipmap_requirements.iter().any(|requirement| {
                        requirement.role == binding.role()
                            && requirement.sampled_binding == binding.binding()
                    }),
                },
            );
        }
        Self { bindings }
    }

    pub(super) fn binding_for(&self, role: TerrainSourceResourceRole, binding: u32) -> SourceColorBinding {
        self.bindings
            .get(&(role, binding))
            .copied()
            .unwrap_or_default()
    }

    pub(super) fn validate_for(
        &self,
        opaque_bindings: &TerrainSourceOpaqueResourceBindingPlan,
    ) -> GalResult<()> {
        for ((role, binding), _) in &self.bindings {
            let matches_source_binding = opaque_bindings.bindings().iter().any(|candidate| {
                candidate.binding() == *binding
                    && candidate.role() == *role
                    && candidate.kind() == TerrainSourceOpaqueResourceKind::CombinedTextureSampler
            });
            if !matches_source_binding {
                return Err(GalError::invalid_argument(format!(
                    "source-color sampling policy references absent combined sampler '{}' at binding {}",
                    role.semantic_name(),
                    binding
                )));
            }
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) struct SourceColorBinding {
    pub(super) feedback: bool,
    pub(super) mipmapped: bool,
}

impl Default for SourceColorBinding {
    fn default() -> Self {
        Self {
            feedback: false,
            mipmapped: false,
        }
    }
}

/// Resolves the output side of a lowered fullscreen stage through the pack
/// manifest and the Rust-owned color target generation. This is the missing
/// semantic bridge between source `DRAWBUFFERS` locations and a future GAL
/// render target: locations are checked against the manifest once, while the
/// executor receives only named roles and opaque GAL views.
///
/// The helper intentionally does not create a pass, pipeline, framebuffer, or
/// command. In particular, it cannot select a shader-pack route by itself.
pub(crate) fn resolve_fullscreen_source_color_attachments(
    program: &LoweredFullscreenSourceProgram,
    manifest: &ShaderPackColorTargetManifest,
    targets: &ShaderPackColorTargets,
) -> GalResult<Vec<FullscreenSourceColorAttachment>> {
    if program.shader_pack_generation != manifest.generation()
        || program.shader_pack_generation != targets.identity.shader_pack_generation
    {
        return Err(GalError::invalid_argument(
            "fullscreen source program, color manifest, and staged color targets must share one shader-pack generation",
        ));
    }

    let mut attachments = Vec::with_capacity(program.outputs.len());
    let mut locations = std::collections::BTreeSet::new();
    let mut roles = std::collections::BTreeSet::new();
    for output in &program.outputs {
        let location = output.source_location();
        let role = output.role();
        if !locations.insert(location) {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source program '{}' writes source location {location} more than once",
                program.identity.as_str()
            )));
        }
        if !roles.insert(role.clone()) {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source program '{}' writes semantic color '{}' more than once",
                program.identity.as_str(),
                role.semantic_name()
            )));
        }
        let name = role.shader_pack_color_name().ok_or_else(|| {
            GalError::invalid_argument(format!(
                "fullscreen source output '{}' is not a shader-pack color target",
                output.semantic_name()
            ))
        })?;
        let source_slot = output.source_slot();
        let declaration = manifest.target_for_source_slot(source_slot).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "fullscreen source output location {location} maps to missing shader-pack color slot {source_slot}"
            ))
        })?;
        if declaration.role != role {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source output location {location} maps to semantic color '{}' but program writes '{}'",
                declaration.name(),
                role.semantic_name()
            )));
        }
        let target = targets.target(name).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "fullscreen source output semantic color '{name}' has no staged Rust-owned target"
            ))
        })?;
        let expected_format = declaration.gal_schema_color_format();
        if !declaration.accepts_storage_format(target.format) {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source output semantic color '{name}' staged storage format {:?} is incompatible with manifest {:?}",
                target.format, expected_format
            )));
        }
        attachments.push(FullscreenSourceColorAttachment {
            source_slot,
            role,
            texture: target.current_texture,
            view: target.current_attachment_view,
            format: target.format,
            clear_each_frame: target.clear_each_frame,
            clear_color_bits: target.clear_color_bits,
        });
    }
    attachments.sort_by_key(|attachment| {
        program
            .outputs
            .iter()
            .find(|output| output.role() == attachment.role)
            .map(FullscreenSourceFragmentOutput::source_location)
            .expect("resolved fullscreen attachment must originate from program output")
    });
    if attachments.is_empty() {
        return Err(GalError::invalid_argument(
            "fullscreen source program has no resolved color attachments",
        ));
    }
    Ok(attachments)
}

/// Resolves the source-derived normal-terrain output schema against one
/// staged Rust-owned shader-pack color generation. The caller supplies the
/// mapping retained by the lowered program rather than raw `DRAWBUFFERS`
/// text, which makes missing, duplicate, or stale outputs fail before a GAL
/// pass can be constructed.
pub(crate) fn resolve_terrain_source_color_attachments(
    output_color_slots: &[(TerrainPassOutput, u32)],
    manifest: &ShaderPackColorTargetManifest,
    targets: &ShaderPackColorTargets,
) -> GalResult<Vec<TerrainSourceColorAttachment>> {
    if manifest.generation() != targets.identity.shader_pack_generation {
        return Err(GalError::invalid_argument(
            "terrain source color manifest and staged targets must share one shader-pack generation",
        ));
    }
    if output_color_slots.is_empty() {
        return Err(GalError::invalid_argument(
            "terrain source program has no named color outputs to resolve",
        ));
    }
    let mut outputs = std::collections::BTreeSet::new();
    let mut source_slots = std::collections::BTreeSet::new();
    let mut roles = std::collections::BTreeSet::new();
    let mut attachments = Vec::with_capacity(output_color_slots.len());
    for &(output, source_slot) in output_color_slots {
        if !outputs.insert(output) {
            return Err(GalError::invalid_argument(format!(
                "terrain source program maps '{}' more than once",
                output.semantic_name()
            )));
        }
        if !source_slots.insert(source_slot) {
            return Err(GalError::invalid_argument(format!(
                "terrain source program maps more than one semantic output to shader-pack color slot {source_slot}"
            )));
        }
        let declaration = manifest
            .target_for_source_slot(source_slot)
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                "terrain source output '{}' maps to missing shader-pack color slot {source_slot}",
                output.semantic_name()
            ))
            })?;
        let name = declaration.name();
        if !roles.insert(declaration.role.clone()) {
            return Err(GalError::invalid_argument(format!(
                "terrain source output '{}' aliases semantic shader-pack color '{name}'",
                output.semantic_name()
            )));
        }
        let target = targets.target(name).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "terrain source output '{}' has no staged Rust-owned target for semantic color '{name}'",
                output.semantic_name()
            ))
        })?;
        let format = declaration.gal_schema_color_format();
        if !declaration.accepts_storage_format(target.format) {
            return Err(GalError::invalid_argument(format!(
                "terrain source output '{}' staged storage format {:?} is incompatible with source manifest {:?}",
                output.semantic_name(),
                target.format,
                format
            )));
        }
        attachments.push(TerrainSourceColorAttachment {
            output,
            source_slot,
            role: declaration.role.clone(),
            texture: target.current_texture,
            view: target.current_attachment_view,
            format: target.format,
            clear_each_frame: target.clear_each_frame,
            clear_color_bits: target.clear_color_bits,
        });
    }
    attachments.sort_by_key(|attachment| attachment.output);
    Ok(attachments)
}

/// Returns all staged semantic colors in exact source-slot order. A future
/// fullscreen pass uses this to construct its complete backend-neutral target
/// and pipeline color-format vector, including source slots the particular
/// shader leaves unwritten. No slot is synthesized from a backend default.
pub(crate) fn source_color_attachments_by_slot(
    manifest: &ShaderPackColorTargetManifest,
    targets: &ShaderPackColorTargets,
) -> GalResult<Vec<FullscreenSourceColorAttachment>> {
    if manifest.generation() != targets.identity.shader_pack_generation {
        return Err(GalError::invalid_argument(
            "shader-pack color manifest and staged color targets must share one generation",
        ));
    }
    let mut attachments = Vec::with_capacity(MAX_SOURCE_COLOR_TARGETS as usize);
    for slot in 0..MAX_SOURCE_COLOR_TARGETS {
        let declaration = manifest.target_for_source_slot(slot).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack color manifest lacks source slot {slot}"
            ))
        })?;
        let name = declaration.name();
        let target = targets.target(name).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack color source slot {slot} ('{name}') has no staged Rust-owned target"
            ))
        })?;
        let format = declaration.gal_schema_color_format();
        if !declaration.accepts_storage_format(target.format) {
            return Err(GalError::invalid_argument(format!(
                "shader-pack color source slot {slot} ('{name}') staged storage format {:?} is incompatible with manifest {:?}",
                target.format, format
            )));
        }
        attachments.push(FullscreenSourceColorAttachment {
            source_slot: slot,
            role: declaration.role.clone(),
            texture: target.current_texture,
            view: target.current_attachment_view,
            format: target.format,
            clear_each_frame: target.clear_each_frame,
            clear_color_bits: target.clear_color_bits,
        });
    }
    Ok(attachments)
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) struct ShaderPackColorSamplingPolicy {
    pub(super) mipmapped: bool,
}

impl ShaderPackColorSamplingPolicy {
    pub(super) fn descriptor(self, label: &str) -> SamplerDesc {
        SamplerDesc {
            label: label.to_string(),
            min_filter: SamplerFilter::Linear,
            mag_filter: SamplerFilter::Linear,
            mip_filter: if self.mipmapped {
                SamplerFilter::Linear
            } else {
                SamplerFilter::Nearest
            },
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        }
    }
}
