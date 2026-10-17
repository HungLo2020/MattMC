//! Fullscreen consumers of named-source plans: deferred, composite, sky and celestial stages.

use super::*;

/// One source-derived fullscreen stage retained with the exact combined
/// terrain/DH resource generation it reads. Source and execution plans are
/// paired here so later confirmation can retire Rust-owned pipeline/resources
/// only after the same one combined source submission succeeds.
pub(crate) struct PreparedNamedSourceFullscreenConsumer {
    pub(in crate::render::worldrender) program: Arc<LoweredFullscreenSourceProgram>,
    pub(in crate::render::worldrender) plan: FullscreenSourceExecutionPlan,
    pub(in crate::render::worldrender) frame: FullscreenSourcePassFrame,
}

impl PreparedNamedSourceFullscreenConsumer {
    pub(crate) fn append(
        &self,
        color_transaction: &mut ShaderPackSourceColorFrameTransaction,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        color_transaction.append_fullscreen_consumer(
            &self.plan,
            &self.program,
            FullscreenSourcePassFrame {
                texture_transforms: self.frame.texture_transforms.clone(),
                scalar_uniforms: self.frame.scalar_uniforms.clone(),
                texture_transform_before: self.frame.texture_transform_before,
                scalar_uniform_before: self.frame.scalar_uniform_before,
                clear_values: self.frame.clear_values,
                color_attachment_before: Vec::new(),
                clear_targets_this_pass: None,
            },
            operations,
        )
    }

    pub(crate) fn writes_named_color(&self, program_identity: &str, color_name: &str) -> bool {
        (self.program.identity.as_str() == program_identity
            || self.program.source_stage_path == program_identity)
            && (color_name == "*"
                || self
                    .plan
                    .outputs()
                    .iter()
                    .any(|output| output.role.shader_pack_color_name() == Some(color_name)))
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        self.plan.destroy(gal);
    }
}

pub(crate) fn is_deferred_source_stage(stage_path: &str) -> bool {
    stage_path
        .rsplit('/')
        .next()
        .is_some_and(|name| name.starts_with("deferred") && name.ends_with(".fsh"))
}

pub(in crate::render::worldrender) fn append_named_source_fullscreen_consumer(
    consumer: &PreparedNamedSourceFullscreenConsumer,
    color_transaction: &mut ShaderPackSourceColorFrameTransaction,
    fullscreen_stage_capture: Option<&SelectedSourceOutputCapture>,
    fullscreen_stage_trace_captures: &[SelectedSourceFullscreenTraceCapture],
    operations: &mut Vec<CommandOp>,
) -> GalResult<()> {
    let operation_start = operations.len();
    consumer.append(color_transaction, operations)?;
    require_source_fullscreen_writer_coverage(
        consumer.program.identity.as_str(),
        &operations[operation_start..],
    )?;
    if let Some(capture) =
        fullscreen_stage_capture.filter(|capture| capture.matches_fullscreen_consumer(consumer))
    {
        capture.append_ops(
            TextureUsageState::ShaderRead,
            TextureUsageState::ShaderRead,
            operations,
        )?;
    }
    for trace in fullscreen_stage_trace_captures {
        if trace.matches_fullscreen_consumer(consumer) {
            trace.capture.append_ops(
                TextureUsageState::ShaderRead,
                TextureUsageState::ShaderRead,
                operations,
            )?;
        }
    }
    Ok(())
}

pub(crate) fn destroy_named_source_fullscreen_consumers(
    gal: &mut VulkanicGal,
    consumers: impl IntoIterator<Item = PreparedNamedSourceFullscreenConsumer>,
) {
    for consumer in consumers {
        consumer.destroy(gal);
    }
}

impl WorldPrimitiveFrontend {
    /// Stages the complete pack-declared fullscreen chain once every terrain
    /// writer has contributed its generation-coherent semantic inputs. Normal
    /// terrain owns this scheduling boundary; Distant Horizons may add depth
    /// resources but never gains a private composite or presentation path.
    pub(crate) fn prepare_complete_named_source_fullscreen_consumers(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        color_targets: &ShaderPackColorTargets,
        main_depth_resources: TerrainSourceOwnedResourceSet,
        deferred_main_depth_resources: Option<TerrainSourceOwnedResourceSet>,
        distant_horizons: Option<&PreparedNamedSourceDistantHorizonsFramePlan>,
    ) -> GalResult<Vec<PreparedNamedSourceFullscreenConsumer>> {
        let shader_pack_generation = color_targets.identity.shader_pack_generation;
        let world_generation = color_targets.identity.world_generation;
        let source_snapshot = self
            .candidate_source_resource_snapshot_for_frame(
                shader_pack_generation,
                world_generation,
                frame.frame_id,
            )?
            .resources
            .excluding_roles([TerrainSourceResourceRole::MainDepth])?;
        let deferred_external_inputs = match deferred_main_depth_resources {
            Some(resources) => {
                let unique = resources.excluding_roles_already_owned_by(&source_snapshot)?;
                let mut inputs = vec![source_snapshot.clone()];
                if unique.len() != 0 {
                    inputs.push(unique);
                }
                Some(inputs)
            }
            None => None,
        };
        let mut external_inputs = vec![source_snapshot.clone()];
        let mut accumulated = source_snapshot;
        let exact_main_depth_availability = main_depth_resources
            .availability()
            .resource_for(TerrainSourceResourceRole::MainDepth);
        let mut append_unique = |resources: TerrainSourceOwnedResourceSet| -> GalResult<()> {
            let unique = resources.excluding_roles_already_owned_by(&accumulated)?;
            if unique.len() != 0 {
                accumulated = TerrainSourceOwnedResourceSet::merge([&accumulated, &unique])?;
                external_inputs.push(unique);
            }
            Ok(())
        };
        append_unique(main_depth_resources)?;
        if let Some(distant_horizons) = distant_horizons {
            append_unique(distant_horizons.depth_targets.semantic_resources()?)?;
        }
        if matches!(
            crate::core::environment::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            crate::core::console::stderr(format_args!(
                "[MattMC source-depth-trace] fullscreen main-depth sampler={:?} available={:?} exact_resources={:?}",
                accumulated
                    .combined_sampler_for(TerrainSourceResourceRole::MainDepth)
                    .map(Handle::raw),
                accumulated
                    .availability()
                    .resource_for(TerrainSourceResourceRole::MainDepth),
                exact_main_depth_availability,
            ));
        }

        let source_uses_distant_horizons = distant_horizons.is_some();
        let programs = {
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::backend("shader runtime vanished before complete fullscreen staging")
            })?;
            runtime.shared_post_terrain_fullscreen_programs(source_uses_distant_horizons)?
        };
        self.write_selected_source_fullscreen_chain_receipt(frame, &programs);
        self.write_selected_source_fullscreen_probe_receipt(frame, &programs);
        let mut source_uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
        if source_uses_distant_horizons {
            // The shared post-terrain source stages reconstruct ordinary
            // pixels through the normal gbuffer matrices, but reconstruct DH
            // pixels from the separate copied DH depth image. Keep those
            // coordinate systems independent and supply the exact projection
            // pair that created that depth image.
            apply_distant_horizons_fullscreen_projection(
                &mut source_uniforms,
                &frame.lod_render_frame,
            )?;
        }
        let frames = programs
            .iter()
            .map(|program| {
                let scalar_uniforms = program.pack_scalar_uniforms(&source_uniforms)?;
                self.write_selected_source_fullscreen_uniform_receipt(
                    frame.frame_id,
                    program,
                    &scalar_uniforms,
                );
                self.write_selected_source_fullscreen_program_receipt(frame.frame_id, program);
                Ok(FullscreenSourcePassFrame {
                    texture_transforms: program.pack_texture_transforms(
                        &TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                    )?,
                    scalar_uniforms,
                    texture_transform_before: TextureUsageState::Undefined,
                    scalar_uniform_before: program
                        .execution_interface
                        .scalar_uniforms
                        .map(|_| TextureUsageState::Undefined),
                    clear_values: ShaderPackColorClearValues {
                        fog_color: background_clear_color(&frame.background),
                    },
                    color_attachment_before: Vec::new(),
                    clear_targets_this_pass: None,
                })
            })
            .collect::<GalResult<Vec<_>>>()?;
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::backend("shader runtime vanished while staging complete fullscreen chain")
        })?;
        let plans = if source_uses_distant_horizons {
            runtime.stage_distant_horizons_complete_post_terrain_execution_plans(
                gal,
                color_targets,
                |stage_path| match deferred_external_inputs.as_deref() {
                    Some(inputs) if is_deferred_source_stage(stage_path) => inputs,
                    _ => external_inputs.as_slice(),
                },
                color_targets.identity.extent,
            )?
        } else {
            runtime.stage_complete_post_terrain_execution_plans(
                gal,
                color_targets,
                |stage_path| match deferred_external_inputs.as_deref() {
                    Some(inputs) if is_deferred_source_stage(stage_path) => inputs,
                    _ => external_inputs.as_slice(),
                },
                color_targets.identity.extent,
            )?
        };
        if programs.len() != plans.len() || programs.len() != frames.len() {
            destroy_named_source_fullscreen_consumers(
                gal,
                programs
                    .into_iter()
                    .zip(plans)
                    .zip(frames)
                    .map(
                        |((program, plan), frame)| PreparedNamedSourceFullscreenConsumer {
                            program,
                            plan,
                            frame,
                        },
                    ),
            );
            return Err(GalError::backend(
                "complete source fullscreen program, frame-data, and execution-plan counts diverged",
            ));
        }
        Ok(programs
            .into_iter()
            .zip(plans)
            .zip(frames)
            .map(
                |((program, plan), frame)| PreparedNamedSourceFullscreenConsumer {
                    program,
                    plan,
                    frame,
                },
            )
            .collect())
    }

    /// Begin runs before shadows; prepare runs after shadows and before sky.
    /// Both use the ordinary named-color transaction and sole frame owner.
    pub(crate) fn prepare_pre_terrain_fullscreen_consumers(
        &mut self, gal: &mut VulkanicGal, frame: &WorldPrimitiveFrame,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<Vec<PreparedNamedSourceFullscreenConsumer>> {
        let inputs = self.candidate_source_resource_snapshot_for_frame(
            targets.identity.shader_pack_generation, targets.identity.world_generation, frame.frame_id,
        )?.resources.clone();
        let runtime = self.shader_runtime.as_ref().ok_or_else(||
            GalError::backend("shader runtime vanished before pre-terrain fullscreen staging"))?;
        let programs = runtime
            .prepared_lowered_pre_terrain_fullscreen_programs(source_frame_includes_distant_horizons(frame))?
            .into_iter().map(|program| runtime.shared_fullscreen_program(program)).collect::<Vec<_>>();
        let mut uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
        if source_frame_includes_distant_horizons(frame) {
            apply_distant_horizons_fullscreen_projection(&mut uniforms, &frame.lod_render_frame)?;
        }
        // Pack every payload before allocating, so a missing semantic value
        // cannot leave a partially staged chain alive.
        let frames = programs.iter().map(|program| Ok(FullscreenSourcePassFrame {
            texture_transforms: program.pack_texture_transforms(&TerrainSourceTextureTransforms::canonical_minecraft_terrain())?,
            scalar_uniforms: program.pack_scalar_uniforms(&uniforms)?,
            texture_transform_before: TextureUsageState::Undefined,
            scalar_uniform_before: program.execution_interface.scalar_uniforms.map(|_| TextureUsageState::Undefined),
            clear_values: ShaderPackColorClearValues { fog_color: background_clear_color(&frame.background) },
            color_attachment_before: Vec::new(), clear_targets_this_pass: None,
        })).collect::<GalResult<Vec<_>>>()?;
        let runtime = self.shader_runtime.as_ref().expect("retained shader runtime");
        let mut consumers = Vec::with_capacity(programs.len());
        for (program, pass_frame) in programs.into_iter().zip(frames) {
            match runtime.stage_pre_terrain_fullscreen_execution_plan(gal, &program, targets, &inputs) {
                Ok(plan) => consumers.push(PreparedNamedSourceFullscreenConsumer { program, plan, frame: pass_frame }),
                Err(error) => {
                    destroy_named_source_fullscreen_consumers(gal, consumers);
                    return Err(error);
                }
            }
        }
        Ok(consumers)
    }

    /// Stages the selected sky source first on Iris's horizon and then on
    /// Minecraft's disc. Separate consumers retain draw-local feedback and
    /// fog/sky colors inside the same Rust-owned color transaction.
    pub(crate) fn prepare_pre_terrain_source_sky_consumers(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        color_targets: &ShaderPackColorTargets,
        main_depth_resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<Vec<PreparedNamedSourceFullscreenConsumer>> {
        if !source_sky_initializer_requested(frame) && !source_horizon_initializer_requested(frame) {
            return Ok(Vec::new());
        }
        let programs = {
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::backend("shader runtime vanished before source sky staging")
            })?;
            let mut programs = Vec::with_capacity(2);
            // Frozen draws the horizon for the Overworld (or a custom
            // skylit dimension). Custom dimensions require separate semantic
            // admission; End and Nether must not acquire this geometry.
            if source_horizon_initializer_requested(frame) {
                programs.extend(runtime.prepared_lowered_pre_terrain_horizon_program()?.map(|program| runtime.shared_fullscreen_program(program)));
            }
            if source_sky_initializer_requested(frame) {
                programs.extend(runtime.prepared_lowered_pre_terrain_sky_program()?.map(|program| runtime.shared_fullscreen_program(program)));
            }
            programs
        };
        if programs.is_empty() { return Ok(Vec::new()); }
        let source_snapshot = self.candidate_source_resource_snapshot_for_frame(
            color_targets.identity.shader_pack_generation,
            color_targets.identity.world_generation,
            frame.frame_id,
        )?.resources.clone();
        let unique_main_depth = main_depth_resources.excluding_roles_already_owned_by(&source_snapshot)?;
        let inputs = TerrainSourceOwnedResourceSet::merge([&source_snapshot, &unique_main_depth])?;
        let source_uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
        // Pack both frames before staging resources, so a missing semantic
        // cannot leak a first draw's plan or partially admit the sky route.
        let frames = programs.iter().map(|program| -> GalResult<_> {
            let scalar_uniforms = program.pack_scalar_uniforms(&source_uniforms)?;
            self.write_selected_source_sky_uniform_receipt(
                frame.frame_id, program.identity.as_str(),
                &program.execution_interface.scalar_uniform_fields, &scalar_uniforms,
            );
            self.write_selected_source_sky_program_receipt(frame.frame_id, program);
            Ok(FullscreenSourcePassFrame {
                texture_transforms: program.pack_texture_transforms(
                    &TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                )?,
                scalar_uniforms,
                texture_transform_before: TextureUsageState::Undefined,
                scalar_uniform_before: program.execution_interface.scalar_uniforms.map(|_| TextureUsageState::Undefined),
                clear_values: ShaderPackColorClearValues { fog_color: background_clear_color(&frame.background) },
                color_attachment_before: Vec::new(), clear_targets_this_pass: None,
            })
        }).collect::<GalResult<Vec<_>>>()?;
        let runtime = self.shader_runtime.as_ref().expect("retained shader runtime");
        let mut consumers = Vec::with_capacity(programs.len());
        for (program, pass_frame) in programs.into_iter().zip(frames) {
            match runtime.stage_pre_terrain_fullscreen_execution_plan(gal, &program, color_targets, &inputs) {
                Ok(plan) => consumers.push(PreparedNamedSourceFullscreenConsumer { program, plan, frame: pass_frame }),
                Err(error) => {
                    destroy_named_source_fullscreen_consumers(gal, consumers);
                    return Err(error);
                }
            }
        }
        Ok(consumers)
    }

    /// Stages the source pack's textured vanilla celestial writer twice: once
    /// for the sun and once for the moon. Both draws consume copied vanilla
    /// sky semantics and Rust-owned PNG resources; no Iris object, texture,
    /// or phase is borrowed.
    pub(crate) fn prepare_pre_terrain_source_celestial_consumers(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        color_targets: &ShaderPackColorTargets,
        main_depth_resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<Vec<PreparedNamedSourceFullscreenConsumer>> {
        if !source_sky_initializer_requested(frame) {
            return Ok(Vec::new());
        }
        // The End has no sun or moon: SkyRenderer draws its textured sky box
        // with the same `gbuffers_skytextured` writer instead.
        let end_sky = frame_has_end_sky_quads(frame);
        // Candidate preparation is also used by non-executing source tests.
        // A selected source route separately treats these assets as required;
        // here their absence simply keeps the optional writer inert.
        if !self.source_celestial_assets_available(end_sky) {
            return Ok(Vec::new());
        }
        let source_snapshot = self
            .candidate_source_resource_snapshot_for_frame(
                color_targets.identity.shader_pack_generation,
                color_targets.identity.world_generation,
                frame.frame_id,
            )?
            .resources
            .clone();
        let (program, has_program) = {
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::backend("shader runtime vanished before source celestial staging")
            })?;
            let program = runtime.prepared_lowered_pre_terrain_celestial_program()?;
            (program.map(|program| runtime.shared_fullscreen_program(program)), program.is_some())
        };
        if !has_program {
            return Ok(Vec::new());
        }
        let program = program.expect("checked source celestial program presence");
        let mut consumers = Vec::with_capacity(2);
        let draws: &[(i32, u32)] = if end_sky {
            &[(SOURCE_CELESTIAL_END_SKY, WORLD_MATERIAL_TEXTURE_END_SKY)]
        } else {
            &[
                (SOURCE_CELESTIAL_SUN, WORLD_MATERIAL_TEXTURE_SKY_SUN),
                (SOURCE_CELESTIAL_MOON, WORLD_MATERIAL_TEXTURE_SKY_MOON_PHASES),
            ]
        };
        for &(celestial, texture_id) in draws {
            let (resources, _, texture_extent) = self
                .source_resources_with_local_material_texture(
                    gal,
                    program.shader_pack_generation,
                    &source_snapshot,
                    texture_id,
                    frame.frame_id,
                )?;
            let unique_main_depth =
                main_depth_resources.excluding_roles_already_owned_by(&resources)?;
            let merged_resources = if unique_main_depth.len() == 0 {
                resources
            } else {
                TerrainSourceOwnedResourceSet::merge([&resources, &unique_main_depth])?
            };
            let plan = self
                .shader_runtime
                .as_ref()
                .ok_or_else(|| {
                    GalError::backend("shader runtime vanished while staging source celestial")
                })?
                .stage_pre_terrain_celestial_execution_plan(
                    gal,
                    color_targets,
                    std::slice::from_ref(&merged_resources),
                    color_targets.identity.extent,
                )?
                .ok_or_else(|| {
                    GalError::backend(
                        "source celestial program vanished after successful discovery",
                    )
                })?;
            let mut source_uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
            source_uniforms.render_stage = Some(self.source_celestial_render_stage(celestial)?);
            source_uniforms.celestial_is_moon = Some(celestial);
            source_uniforms.celestial_alpha = Some(if celestial == SOURCE_CELESTIAL_END_SKY {
                1.0
            } else {
                frame.background.sky.rain_brightness
            });
            source_uniforms.moon_phase = Some(frame.background.sky.moon_phase);
            source_uniforms.material_atlas_size = Some(texture_extent);
            let scalar_uniforms = program.pack_scalar_uniforms(&source_uniforms)?;
            self.write_selected_source_sky_uniform_receipt(
                frame.frame_id,
                program.identity.as_str(),
                &program.execution_interface.scalar_uniform_fields,
                &scalar_uniforms,
            );
            self.write_selected_source_sky_program_receipt(frame.frame_id, &program);
            consumers.push(PreparedNamedSourceFullscreenConsumer {
                frame: FullscreenSourcePassFrame {
                    texture_transforms: program.pack_texture_transforms(
                        &TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                    )?,
                    scalar_uniforms,
                    texture_transform_before: TextureUsageState::Undefined,
                    scalar_uniform_before: program
                        .execution_interface
                        .scalar_uniforms
                        .map(|_| TextureUsageState::Undefined),
                    clear_values: ShaderPackColorClearValues {
                        fog_color: background_clear_color(&frame.background),
                    },
                    color_attachment_before: Vec::new(),
                    clear_targets_this_pass: None,
                },
                program: Arc::clone(&program),
                plan,
            });
        }
        Ok(consumers)
    }
}
