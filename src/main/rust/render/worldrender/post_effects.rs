//! Vanilla post effects: source resolution, validation and external targets.

use super::*;

impl WorldPrimitiveFrontend {
    /// Dedicated vanilla executors are only interchangeable with the generic
    /// post-effect identity when the active copied definition *and* shader
    /// stages are byte-for-byte equivalent to the bundled Rust graph.  A
    /// resource pack may override either half; silently selecting the bundled
    /// transparency/outline chain in that case would violate parity.
    pub(super) fn dedicated_post_effect_is_bundled(&self, identity: &str) -> GalResult<bool> {
        // Focused semantic frame fixtures may exercise the bundled route
        // without installing a shader-pack snapshot. Keep that convenience
        // test-only; production requires the active copied snapshot so a
        // missing or stale resource-pack override cannot be mistaken for the
        // bundled graph.
        #[cfg(test)]
        let source = match self.shader_pack_sources.active() {
            Some(source) => source,
            None => return Ok(true),
        };
        #[cfg(not(test))]
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::unsupported_feature(
                "dedicated post-effect requires a Rust-owned shader source snapshot",
            )
        })?;
        let assets = self.active_shader_pack_assets()?;
        let (contract, sources) = assets.resolve_post_effect_contract(identity, source)?;
        let (bundled_plan, bundled_sources) = match identity {
            "transparency" => (
                crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_executor()?
                    .plan()
                    .clone(),
                crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_shader_sources()?,
            ),
            "entity_outline" => (
                crate::render::shaderpack::vanilla::post_effect::executor::bundled_entity_outline_executor()?
                    .plan()
                    .clone(),
                crate::render::shaderpack::vanilla::post_effect::executor::bundled_entity_outline_shader_sources()?,
            ),
            _ => return Ok(false),
        };
        let active_plan = contract.execution_plan();
        let normalize_effect = |effect: &str| -> String {
            effect
                .strip_prefix("minecraft:")
                .or_else(|| effect.split_once(':').map(|(_, path)| path))
                .unwrap_or(effect)
                .to_string()
        };
        let matching_graph = normalize_effect(&active_plan.effect_name)
            == normalize_effect(&bundled_plan.effect_name)
            && active_plan.intermediate_targets == bundled_plan.intermediate_targets
            && active_plan.ordered_passes == bundled_plan.ordered_passes;
        if !matching_graph {
            return Ok(false);
        }
        // The active graph alone is insufficient: a vanilla resource pack
        // can retain the same targets/passes while replacing either shader
        // stage. The dedicated executor compiles Rust's bundled stages, so
        // admitting such a snapshot would silently ignore visible pack
        // behavior. Compare only the graph-resolved stages (not the source
        // snapshot's unrelated inventory) and keep every mismatch private
        // until that resource-pack stage has its own Rust implementation.
        Ok(sources == bundled_sources)
    }

    #[cfg(test)]
    pub(super) fn custom_post_effect_sources(
        &self,
        identity: &str,
        conventions: ShaderConventions,
    ) -> GalResult<Vec<CustomPostEffectSource>> {
        self.custom_post_effect_sources_with_globals(identity, None, conventions)
    }

    /// Lowers the pack's post-effect passes to explicit-binding GLSL adapted
    /// to `conventions`; execution admits them only on such backends.
    pub(super) fn custom_post_effect_sources_with_globals(
        &self,
        identity: &str,
        globals: Option<crate::render::shaderpack::vanilla::engine_globals::EngineGlobals>,
        conventions: ShaderConventions,
    ) -> GalResult<Vec<CustomPostEffectSource>> {
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::unsupported_feature(
                "custom post-effect requires a Rust-owned shader source snapshot",
            )
        })?;
        let assets = self.active_shader_pack_assets()?;
        let (contract, _) = assets.resolve_post_effect_contract(identity, source)?;
        let sources = contract.expanded_shader_sources_from_source(source)?;
        let plan = contract.execution_plan();
        let external_targets = plan.required_external_targets();
        let private_targets = plan
            .ordered_passes
            .iter()
            .flat_map(|pass| {
                std::iter::once(pass.output.as_str())
                    .chain(pass.inputs.iter().map(|input| input.target.as_str()))
            })
            .filter(|target| {
                !target.is_empty()
                    && *target != "minecraft:main"
                    && !external_targets.contains(*target)
            })
            .collect::<BTreeSet<_>>();
        let final_output_is_main = plan
            .ordered_passes
            .last()
            .map(|pass| pass.output == "minecraft:main")
            .unwrap_or(false);
        let mut produced = BTreeSet::new();
        let private_graph_is_sequential = plan.ordered_passes.iter().all(|pass| {
            let valid = pass.inputs.iter().all(|input| {
                input.texture_path.is_some()
                    || !private_targets.contains(input.target.as_str())
                    || (produced.contains(&input.target) && input.target != pass.output)
            });
            produced.insert(pass.output.clone());
            valid
        });
        if plan.ordered_passes.is_empty()
            || plan.ordered_passes.len() > crate::render::guirender::frontend::MAX_CUSTOM_POST_EFFECT_PASSES
            || private_targets.len() > 4
            || !final_output_is_main
            || !private_graph_is_sequential
            || plan.ordered_passes.iter().any(|pass| {
                (pass.output != "minecraft:main" && !private_targets.contains(pass.output.as_str()))
                    || pass.inputs.is_empty()
                    || pass.inputs.len() > 4
                    || pass.uniform_values.len() > 4
            })
            || sources.len() != plan.ordered_passes.len()
        {
            return Err(GalError::unsupported_feature(format!(
                "custom post-effect '{identity}' requires a bounded sequential main-target graph with target-only inputs"
            )));
        }
        plan.ordered_passes
            .iter()
            .zip(sources)
            .map(|(pass, shader)| {
                let mut pass = pass.clone();
                let (vertex_source, fragment_source) = crate::render::shaderpack::lowering::bind_simple_paired_varyings(
                    std::str::from_utf8(&shader.vertex_shader).map_err(|_| GalError::invalid_argument("post-effect vertex is not UTF-8"))?,
                    std::str::from_utf8(&shader.fragment_shader).map_err(|_| GalError::invalid_argument("post-effect fragment is not UTF-8"))?,
                )?;
                let vertex_globals = crate::render::shaderpack::vanilla::engine_globals::uses_globals(&vertex_source)?;
                let fragment_globals = crate::render::shaderpack::vanilla::engine_globals::uses_globals(&fragment_source)?;
                if vertex_globals || fragment_globals {
                    if pass.uniform_values.contains_key("Globals") {
                        return Err(GalError::unsupported_feature("post-effect definition cannot override engine Globals"));
                    }
                    let values = globals.ok_or_else(|| GalError::unsupported_feature(
                        "copied post-effect requires explicit per-frame engine Globals"))?;
                    pass.uniform_values.insert("Globals".into(), values.uniforms()?);
                }
                let has_sampler_info = std::str::from_utf8(&shader.fragment_shader)
                    .map_err(|_| GalError::invalid_argument("post-effect shader is not UTF-8"))?
                    .contains("uniform SamplerInfo")
                    || std::str::from_utf8(&shader.vertex_shader)
                        .map_err(|_| GalError::invalid_argument("post-effect vertex shader is not UTF-8"))?
                        .contains("uniform SamplerInfo");
                if has_sampler_info {
                    use crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectUniform;
                    let names = std::iter::once("OutSize".to_owned())
                        .chain(pass.inputs.iter().map(|input| format!("{}Size", input.sampler_name)));
                    pass.uniform_values.insert("SamplerInfo".into(), names.map(|name|
                        VanillaPostEffectUniform { name, value_type: "vec2".into(), values: vec![0.0, 0.0] }
                    ).collect());
                }
                let packed = pack_uniform_blocks(&pass)?;
                let sampler_info_uniform = if has_sampler_info {
                    packed.keys().position(|name| name == "SamplerInfo")
                } else { None };
                let uniform_blocks = packed.into_values().collect();
                let input_images = pass
                    .inputs
                    .iter()
                    .map(|input| {
                        let Some(path) = input.texture_path.as_deref() else {
                            return Ok(None);
                        };
                        let path = path
                            .split_once(':')
                            .map(|(_, path)| path)
                            .unwrap_or(path)
                            .trim_start_matches('/');
                        let path = if path.starts_with("textures/") {
                            path.to_owned()
                        } else {
                            format!("textures/{path}")
                        };
                        let image = assets.decode_rgba8(&path)?;
                        if Some(image.width) != input.texture_width
                            || Some(image.height) != input.texture_height
                        {
                            return Err(GalError::invalid_argument(format!(
                                "custom post-effect texture input '{}' dimensions do not match its declared {}x{} size",
                                path,
                                input.texture_width.unwrap_or_default(),
                                input.texture_height.unwrap_or_default()
                            )));
                        }
                        Ok(Some(CustomPostEffectImage {
                            path,
                            width: image.width,
                            height: image.height,
                            pixels_rgba8: image.pixels_rgba8,
                            bilinear: input.bilinear,
                        }))
                    })
                    .collect::<GalResult<Vec<_>>>()?;
                Ok(CustomPostEffectSource {
                    input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Reverse,
                    input_bilinear: pass.inputs.iter().map(|input| input.bilinear).collect(),
                    sampler_info_uniform,
                    vertex_shader: lower_post_effect_vertex_source_for_pass(
                        conventions,
                        vertex_source.as_bytes(),
                        &pass,
                        crate::render::vulkanic::commands::TextureRowOrder::Reverse,
                    )?,
                    fragment_shader: lower_post_effect_fragment_source(
                        conventions,
                        fragment_source.as_bytes(),
                        &pass,
                    )?,
                    input_count: pass.inputs.len(),
                    input_targets: pass
                        .inputs
                        .iter()
                        .map(|input| input.target.clone())
                        .collect(),
                    input_images,
                    input_use_depth: pass
                        .inputs
                        .iter()
                        .map(|input| input.use_depth_buffer)
                        .collect(),
                    output_target: pass.output.clone(),
                    uniform_blocks,
                })
            })
            .collect()
    }

    /// Admits a frame's copied post-effect identity through Rust-owned pack
    /// snapshots. Generic graphs allow ten ordered passes and four private
    /// targets, including ordered target reuse but not same-pass feedback.
    /// Static effect uniforms and explicit per-frame engine data are packed
    /// into Rust-owned buffers. Fabulous and outline external-target routes
    /// retain their separate composition contracts.
    pub(crate) fn validate_post_effect_request(
        &self,
        identity: &[u8],
        conventions: ShaderConventions,
    ) -> GalResult<()> {
        self.validate_post_effect_request_with_globals(identity, None, conventions)
    }

    pub(crate) fn validate_post_effect_request_with_globals(
        &self,
        identity: &[u8],
        globals: Option<crate::render::shaderpack::vanilla::engine_globals::EngineGlobals>,
        conventions: ShaderConventions,
    ) -> GalResult<()> {
        if identity.is_empty() {
            return Ok(());
        }
        if identity.len() > 256 {
            return Err(GalError::invalid_argument(
                "post-effect identity exceeds 256 UTF-8 bytes",
            ));
        }
        let identity = std::str::from_utf8(identity)
            .map_err(|_| GalError::invalid_argument("post-effect identity must be UTF-8"))?;
        if identity.trim().is_empty() || identity.chars().any(char::is_control) {
            return Err(GalError::invalid_argument(
                "post-effect identity is empty or contains control characters",
            ));
        }
        let normalized = identity
            .strip_prefix("minecraft:")
            .or_else(|| identity.split_once(':').map(|(_, path)| path))
            .unwrap_or(identity);
        // Fabulous transparency and entity outlines are not generic
        // single-target post effects: their complete Rust executors consume
        // explicit external attachments prepared from the semantic frame.
        // Validate the copied bundled contract here, before frame routing,
        // without attempting to lower that contract through the generic
        // target-only path.  A resource-pack override remains unadmitted and
        // therefore continues into the generic validator, which rejects its
        // external graph rather than silently selecting bundled shaders.
        if matches!(normalized, "transparency" | "entity_outline")
            && self.dedicated_post_effect_is_bundled(normalized)?
        {
            return Ok(());
        }
        self.custom_post_effect_sources_with_globals(identity, globals, conventions)
            .map(|_| ())
    }

    /// Prepares external post-effect roles only for a frame that is already
    /// guaranteed to use the complete Fabulous material route. That route
    /// clears/populates every role before `gui_ops` execute; admitting these
    /// bindings on the ordinary frame graph would sample uninitialized
    /// attachments or require a Java/Iris fallback.
    pub(super) fn prepare_custom_external_post_effect_targets(
        &mut self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
        frame: &WorldPrimitiveFrame,
        identity: &str,
    ) -> GalResult<Option<crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectExternalTargetBindings>>{
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::unsupported_feature(
                "custom post-effect requires a Rust-owned shader source snapshot",
            )
        })?;
        let assets = self.active_shader_pack_assets()?;
        let (contract, _) = assets.resolve_post_effect_contract(identity, source)?;
        let plan = contract.execution_plan();
        let required = plan.required_external_targets();
        if required.iter().all(|target| target == "minecraft:main") {
            return Ok(None);
        }
        if !self.frame_has_fabulous_transparency_work(frame)
            || !self.custom_external_fabulous_frame_is_admissible(frame)
            || self.pending_terrain_fabulous_handoff
            || self.runtime_source_execution_is_armed()
            || self.candidate_lowered_source_execution_requested()
        {
            return Err(GalError::unsupported_feature(format!(
                "custom post-effect '{identity}' requires populated Fabulous external attachments on this frame"
            )));
        }
        let color_format = gal.pass_target_color_format(frame_target)?;
        self.ensure_fabulous_attachment_set(gal, frame_target, frame, color_format, color_format)?;
        let set = self.fabulous_attachment_set.as_ref().ok_or_else(|| {
            GalError::backend("Fabulous attachment set missing for external post effect")
        })?;
        let populated = BTreeSet::from([
            "minecraft:main".to_owned(),
            "minecraft:translucent".to_owned(),
            "minecraft:item_entity".to_owned(),
            "minecraft:particles".to_owned(),
            "minecraft:clouds".to_owned(),
            "minecraft:weather".to_owned(),
        ]);
        set.external_inventory()
            .validate_populated_for_plan(&plan, &populated)
            .map(Some)
    }
}

impl WorldPrimitiveFrontend {
    pub(super) fn custom_external_fabulous_frame_is_admissible(&self, frame: &WorldPrimitiveFrame) -> bool {
        // Dedicated Fabulous lowers outlines itself. Arbitrary custom external
        // effects have not established that ownership contract and stay closed.
        Self::fabulous_material_frame_content_is_supported(frame)
            && !frame.mesh_instances.iter().any(|instance| {
                instance.outline_color_argb != 0
                    || instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0
            })
    }
}
