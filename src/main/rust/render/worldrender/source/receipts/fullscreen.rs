//! Fullscreen chain, probe, sky and fullscreen/sky program and uniform receipts.

use super::*;

impl WorldPrimitiveFrontend {
    /// Records the exact lowered fullscreen chain attached to one selected
    /// source frame. This is a bounded source-contract receipt: it exposes
    /// semantic stage/resource identities only, never attachments, native
    /// descriptors, or backend state. It lets a real gameplay capture isolate
    /// the first consumer that can change an already-valid DH primary target.
    pub(crate) fn write_selected_source_fullscreen_chain_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        programs: &[Arc<LoweredFullscreenSourceProgram>],
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let stages = programs
            .iter()
            .map(|program| {
                let outputs = program
                    .outputs
                    .iter()
                    .map(|output| {
                        format!(
                            "{{\"location\":{},\"semantic_role\":\"{}\"}}",
                            output.source_location(),
                            json_escape(&output.role().diagnostic_name()),
                        )
                    })
                    .collect::<Vec<_>>();
                let inputs = program
                    .opaque_resource_bindings
                    .bindings()
                    .iter()
                    .map(|binding| {
                        format!(
                            "{{\"source_name\":\"{}\",\"semantic_role\":\"{}\",\"kind\":\"{:?}\"}}",
                            json_escape(binding.resource_name()),
                            json_escape(&binding.role().diagnostic_name()),
                            binding.kind(),
                        )
                    })
                    .collect::<Vec<_>>();
                let feedback = program
                    .feedback_requirements
                    .iter()
                    .map(|requirement| {
                        format!(
                            "{{\"semantic_role\":\"{}\",\"sampled_binding\":{},\"output_location\":{}}}",
                            json_escape(&requirement.role.diagnostic_name()),
                            requirement.sampled_binding,
                            requirement.output_location,
                        )
                    })
                    .collect::<Vec<_>>();
                let mipmaps = program
                    .mipmap_requirements
                    .iter()
                    .map(|requirement| {
                        format!(
                            "{{\"semantic_role\":\"{}\",\"sampled_binding\":{}}}",
                            json_escape(&requirement.role.diagnostic_name()),
                            requirement.sampled_binding,
                        )
                    })
                    .collect::<Vec<_>>();
                format!(
                    concat!(
                        "{{\"identity\":\"{}\",\"source_stage\":\"{}\",",
                        "\"outputs\":[{}],\"inputs\":[{}],\"feedback\":[{}],\"mipmap_samples\":[{}]}}"
                    ),
                    json_escape(program.identity.as_str()),
                    json_escape(&program.source_stage_path),
                    outputs.join(","),
                    inputs.join(","),
                    feedback.join(","),
                    mipmaps.join(","),
                )
            })
            .collect::<Vec<_>>();
        let path = Path::new(&dir).join(format!(
            "selected-source-fullscreen-chain-frame-{}.json",
            frame.frame_id
        ));
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                path,
                format!(
                    "{{\"frame_id\":{},\"world_generation\":{},\"stages\":[{}]}}",
                    frame.frame_id,
                    frame.shader_environment.world_generation,
                    stages.join(","),
                ),
            );
        }
    }

    /// Makes an explicitly requested source probe observable at the lowered
    /// program boundary. A stage image alone cannot distinguish an inactive
    /// source branch from a probe that was never compiled into the program.
    pub(crate) fn write_selected_source_fullscreen_probe_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        programs: &[Arc<LoweredFullscreenSourceProgram>],
    ) {
        let Ok(mode) = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE") else {
            return;
        };
        let mode = mode.trim();
        if mode.is_empty()
            || !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let Some(program) = programs.iter().find(|program| {
            program
                .source_stage_path
                .replace('\\', "/")
                .ends_with("world0/deferred1.fsh")
        }) else {
            return;
        };
        let source = &program.fragment.source;
        let hash = source
            .as_bytes()
            .iter()
            .fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
                (hash ^ u64::from(*byte)).wrapping_mul(0x0000_0100_0000_01b3)
            });
        let marker = format!("selected-source fullscreen diagnostic probe: {mode}");
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_err() {
            return;
        }
        let _ = std::fs::write(
            dir.join("selected-source-fullscreen-probe-receipt.json"),
            format!(
                concat!(
                    "{{\"frame_id\":{},\"mode\":\"{}\",\"program\":\"{}\",",
                    "\"source_stage_path\":\"{}\",\"fragment_bytes\":{},",
                    "\"fragment_hash\":\"{:016x}\",\"marker_present\":{},",
                    "\"dh_depth_binding_present\":{}}}"
                ),
                frame.frame_id,
                json_escape(mode),
                json_escape(program.identity.as_str()),
                json_escape(&program.source_stage_path),
                source.len(),
                hash,
                source
                    .as_bytes()
                    .windows(marker.len())
                    .any(|window| window == marker.as_bytes()),
                source
                    .as_bytes()
                    .windows(b"dhDepthTex".len())
                    .any(|window| window == b"dhDepthTex"),
            ),
        );
    }

    /// Retains the pre-terrain sky decision separately from the post-terrain
    /// fullscreen chain. The latter intentionally never included sky, which
    /// made a real selected-source capture unable to distinguish a missing sky
    /// program from a successfully staged pre-terrain writer.
    pub(crate) fn write_selected_source_sky_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        sky: &[PreparedNamedSourceFullscreenConsumer],
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let stages = sky
            .iter()
            .map(|sky| {
                format!(
                    "{{\"identity\":\"{}\",\"source_stage\":\"{}\"}}",
                    json_escape(sky.program.identity.as_str()),
                    json_escape(&sky.program.source_stage_path),
                )
            })
            .collect::<Vec<_>>()
            .join(",");
        let path = Path::new(&dir).join(format!(
            "selected-source-pre-terrain-sky-frame-{}.json",
            frame.frame_id
        ));
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                path,
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"world_generation\":{},",
                        "\"requested\":{},\"sky_visible\":{},\"sky_type\":{},",
                        "\"staged\":[{}]}}"
                    ),
                    frame.frame_id,
                    frame.shader_environment.world_generation,
                    source_sky_initializer_requested(frame),
                    frame.background.sky.visible,
                    frame.background.sky_type,
                    stages,
                ),
            );
        }
    }

    /// Records the complete, source-derived sky scalar block for one opt-in
    /// deterministic parity capture. This is CPU-side transport evidence only:
    /// it neither changes the program nor exposes a native resource identity.
    pub(crate) fn write_selected_source_sky_uniform_receipt(
        &self,
        frame_id: u64,
        program_identity: &str,
        fields: &[TerrainSourceUniformField],
        bytes: &[u8],
    ) {
        if !matches!(
            crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_SKY_UNIFORM_RECEIPT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) || !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            || !program_identity.contains("gbuffers_skybasic")
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        const NAMES: &[&str] = &[
            "gbufferModelView",
            "gbufferProjectionInverse",
            "sunAngle",
            "worldTime",
            "viewWidth",
            "viewHeight",
            "skyColor",
            "fogColor",
            "rainFactor",
            "maxBlindnessDarkness",
        ];
        let fields = fields
            .iter()
            .filter(|field| NAMES.contains(&field.name()))
            .map(|field| {
                let offset = field.offset() as usize;
                let end = offset
                    .saturating_add(field.size() as usize)
                    .min(bytes.len());
                let words = bytes[offset..end]
                    .chunks_exact(4)
                    .map(|word| {
                        u32::from_le_bytes(word.try_into().expect("four-byte uniform word"))
                    })
                    .collect::<Vec<_>>();
                let words = words
                    .iter()
                    .map(|word| word.to_string())
                    .collect::<Vec<_>>()
                    .join(",");
                format!(
                    "{{\"name\":\"{}\",\"offset\":{},\"size\":{},\"words\":[{}]}}",
                    field.name(),
                    field.offset(),
                    field.size(),
                    words,
                )
            })
            .collect::<Vec<_>>();
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                Path::new(&dir).join("selected-source-sky-uniform-receipt.json"),
                format!(
                    "{{\"frame_id\":{},\"program\":\"{}\",\"byte_len\":{},\"fields\":[{}]}}",
                    frame_id,
                    json_escape(program_identity),
                    bytes.len(),
                    fields.join(","),
                ),
            );
        }
    }

    /// Retains the semantic scalar block for the one post-terrain source pass
    /// selected by an opt-in audit. This is transport evidence only: it does
    /// not read backend state or alter source program execution.
    pub(crate) fn write_selected_source_fullscreen_uniform_receipt(
        &self,
        frame_id: u64,
        program: &LoweredFullscreenSourceProgram,
        bytes: &[u8],
    ) {
        if !self.fullscreen_uniform_receipts_enabled() {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let (stage_name, document) = Self::fullscreen_uniform_receipt_data(frame_id, program, bytes);
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                Path::new(&dir).join(format!("selected-source-{stage_name}-uniform-receipt.json")),
                &document,
            );
            // Keep the established deferred1 filename for existing harness consumers.
            if program.source_stage_path.ends_with("world0/deferred1.fsh") {
                let _ = std::fs::write(
                    Path::new(&dir).join("selected-source-deferred1-uniform-receipt.json"),
                    document,
                );
            }
        }
    }

    /// Keep the immutable CPU blocks belonging to this selected capture until
    /// its submission completes. Later frames may overwrite the latest receipts.
    pub(crate) fn retain_captured_fullscreen_uniform_receipts<'a>(
        &self,
        frame: &WorldPrimitiveFrame,
        capture: &mut GameplayAttachmentCapture,
        inputs: impl IntoIterator<Item = (&'a LoweredFullscreenSourceProgram, &'a [u8])>,
    ) {
        if !Self::fullscreen_uniform_receipt_audit_enabled()
            || !capture.source_selected
            || capture.frame_id != frame.frame_id
            || capture.correlation_id != frame.correlation_id
        {
            return;
        }
        capture.fullscreen_uniform_receipts = inputs
            .into_iter()
            .map(|(program, bytes)| Self::fullscreen_uniform_receipt_data(frame.frame_id, program, bytes))
            .collect();
    }

    fn fullscreen_uniform_receipts_enabled(&self) -> bool {
        self.source_execution_enabled() && Self::fullscreen_uniform_receipt_audit_enabled()
    }

    fn fullscreen_uniform_receipt_audit_enabled() -> bool {
        matches!(
            crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_UNIFORM_RECEIPT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
    }

    fn fullscreen_uniform_receipt_data(
        frame_id: u64,
        program: &LoweredFullscreenSourceProgram,
        bytes: &[u8],
    ) -> (String, String) {
        let fields = program
            .execution_interface
            .scalar_uniform_fields
            .iter()
            .map(|field| {
                let offset = field.offset() as usize;
                let end = offset
                    .saturating_add(field.size() as usize)
                    .min(bytes.len());
                let words = bytes[offset..end]
                    .chunks_exact(4)
                    .map(|word| {
                        u32::from_le_bytes(word.try_into().expect("four-byte uniform word"))
                    })
                    .map(|word| word.to_string())
                    .collect::<Vec<_>>()
                    .join(",");
                format!(
                    "{{\"name\":\"{}\",\"offset\":{},\"size\":{},\"words\":[{}]}}",
                    field.name(),
                    field.offset(),
                    field.size(),
                    words,
                )
            })
            .collect::<Vec<_>>();
        let document = format!(
            "{{\"frame_id\":{},\"program\":\"{}\",\"source_stage_path\":\"{}\",\"byte_len\":{},\"fields\":[{}]}}",
            frame_id,
            json_escape(program.identity.as_str()),
            json_escape(&program.source_stage_path),
            bytes.len(),
            fields.join(","),
        );
        let stage_name = program
            .source_stage_path
            .trim_end_matches(".fsh")
            .chars()
            .map(|character| {
                if character.is_ascii_alphanumeric() {
                    character
                } else {
                    '-'
                }
            })
            .collect::<String>();
        (stage_name, document)
    }

    /// Retains the lowered deferred source only for an explicit audit run.
    /// The receipt permits source-branch and UBO-contract inspection without
    /// exposing backend program objects or changing the submitted pass.
    pub(crate) fn write_selected_source_fullscreen_program_receipt(
        &self,
        frame_id: u64,
        program: &LoweredFullscreenSourceProgram,
    ) {
        if !matches!(
            crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROGRAM_RECEIPT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) || !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            || !program.source_stage_path.ends_with("world0/deferred1.fsh")
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_err() {
            return;
        }
        let vertex = program.vertex.source.as_bytes();
        let fragment = program.fragment.source.as_bytes();
        let source_hash = |bytes: &[u8]| {
            bytes.iter().fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
                (hash ^ u64::from(*byte)).wrapping_mul(0x0000_0100_0000_01b3)
            })
        };
        let _ = std::fs::write(dir.join("selected-source-deferred1-lowered.vert"), vertex);
        let _ = std::fs::write(dir.join("selected-source-deferred1-lowered.frag"), fragment);
        let _ = std::fs::write(
            dir.join("selected-source-deferred1-program-receipt.json"),
            format!(
                concat!(
                    "{{\"frame_id\":{},\"program\":\"{}\",\"source_stage_path\":\"{}\",",
                    "\"vertex\":{{\"bytes\":{},\"hash\":\"{:016x}\"}},",
                    "\"fragment\":{{\"bytes\":{},\"hash\":\"{:016x}\",",
                    "\"contains_atmospheric_fog\":{},\"contains_eye_brightness_m\":{},",
                    "\"contains_distant_horizons\":{}}}}}"
                ),
                frame_id,
                json_escape(program.identity.as_str()),
                json_escape(&program.source_stage_path),
                vertex.len(),
                source_hash(vertex),
                fragment.len(),
                source_hash(fragment),
                program.fragment.source.contains("DoAtmosphericFog("),
                program.fragment.source.contains("eyeBrightnessM"),
                program.fragment.source.contains("DISTANT_HORIZONS"),
            ),
        );
    }

    /// Retains the one generated source pair used by an explicitly requested
    /// selected-source sky capture. This is deliberately opt-in diagnostic
    /// evidence: it proves the compiled Rust-owned source and never changes
    /// source selection, program creation, or native execution state.
    pub(crate) fn write_selected_source_sky_program_receipt(
        &self,
        frame_id: u64,
        program: &LoweredFullscreenSourceProgram,
    ) {
        if !matches!(
            crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_SKY_PROGRAM_RECEIPT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) || !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            || !program.identity.as_str().contains("gbuffers_skybasic")
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_err() {
            return;
        }
        let vertex_name = "selected-source-sky-lowered.vert";
        let fragment_name = "selected-source-sky-lowered.frag";
        let vertex = program.vertex.source.as_bytes();
        let fragment = program.fragment.source.as_bytes();
        let source_hash = |bytes: &[u8]| {
            bytes.iter().fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
                (hash ^ u64::from(*byte)).wrapping_mul(0x0000_0100_0000_01b3)
            })
        };
        let receipt = format!(
            concat!(
                "{{\"frame_id\":{},\"program\":\"{}\",\"source_stage_path\":\"{}\",",
                "\"vertex\":{{\"file\":\"{}\",\"bytes\":{},\"hash\":\"{:016x}\",",
                "\"contains_far_world_depth\":{}}},",
                "\"fragment\":{{\"file\":\"{}\",\"bytes\":{},\"hash\":\"{:016x}\",",
                "\"contains_get_sky\":{},\"contains_overworld_define\":{}}}}}"
            ),
            frame_id,
            json_escape(program.identity.as_str()),
            json_escape(&program.source_stage_path),
            vertex_name,
            vertex.len(),
            source_hash(vertex),
            program
                .vertex
                .source
                .contains("vec4(vulkanic_source_fullscreen_position(), 1.0, 1.0)"),
            fragment_name,
            fragment.len(),
            source_hash(fragment),
            program.fragment.source.contains("GetSky("),
            program.fragment.source.contains("#define OVERWORLD"),
        );
        let _ = std::fs::write(dir.join(vertex_name), &program.vertex.source);
        let _ = std::fs::write(dir.join(fragment_name), &program.fragment.source);
        let _ = std::fs::write(
            dir.join("selected-source-sky-program-receipt.json"),
            receipt,
        );
    }
}
