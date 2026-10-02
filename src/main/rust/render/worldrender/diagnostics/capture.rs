//! Diagnostic capture of selected-source outputs and gameplay attachments.

use crate::render::worldrender::*;

/// Capture at most once for one exact Java-selected submission.  A selected
/// source sequence deliberately uses a new frame/correlation pair per pose,
/// so a process-wide one-shot would force later poses onto an unrelated
/// external-window capture path.
pub(in crate::render::worldrender) static GAMEPLAY_ATTACHMENT_CAPTURE_REQUEST: Mutex<Option<(u64, u64)>> = Mutex::new(None);

/// The uncorrelated attachment probe is strictly one real submission per
/// process. Unlike the normal selector it has no Java screenshot identity, so
/// allowing it to repeat would turn a bounded diagnostic into unbounded I/O.
pub(in crate::render::worldrender) static GAMEPLAY_ATTACHMENT_DIAGNOSTIC_ONCE_CAPTURED: AtomicBool = AtomicBool::new(false);

pub(in crate::render::worldrender) static SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN: AtomicBool = AtomicBool::new(false);

/// One bounded readback of the exact Rust-owned selected-source result. It is
/// deliberately separate from the ordinary G-buffer capture: that capture
/// may occur on the normal confirmation frame immediately before source
/// execution is armed and therefore cannot prove the selected-source output.
pub(in crate::render::worldrender) struct SelectedSourceOutputCapture {
    pub(in crate::render::worldrender) dir: PathBuf,
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) correlation_id: u64,
    pub(in crate::render::worldrender) source_role: String,
    pub(in crate::render::worldrender) artifact_name: String,
    pub(in crate::render::worldrender) format: TextureFormat,
    pub(in crate::render::worldrender) extent: Extent3d,
    pub(in crate::render::worldrender) readback_rows_bottom_up: bool,
    pub(in crate::render::worldrender) texture: Handle,
    pub(in crate::render::worldrender) readback: Handle,
}

/// One opt-in readback request immediately after a named source fullscreen
/// stage. The program identity and color name are pack semantics, never
/// attachment indices or backend handles.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct SelectedSourceFullscreenStageCapture {
    pub(in crate::render::worldrender) program_identity: String,
    pub(in crate::render::worldrender) color_name: String,
}

/// A bounded, opt-in trace of the complete pack-declared fullscreen chain for
/// one selected gameplay frame. It is diagnostic-only: the same consumers are
/// still recorded in the same order, and every readback restores the target's
/// shader-read state before the following source stage runs.
pub(in crate::render::worldrender) struct SelectedSourceFullscreenTraceCapture {
    pub(in crate::render::worldrender) program_identity: String,
    pub(in crate::render::worldrender) capture: SelectedSourceOutputCapture,
}

impl SelectedSourceFullscreenTraceCapture {
    pub(in crate::render::worldrender) fn select_all(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        consumers: &[PreparedNamedSourceFullscreenConsumer],
        extent: Extent3d,
    ) -> GalResult<Vec<Self>> {
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(Vec::new());
        };
        if !selected_source_output_capture_requested(frame)? {
            return Ok(Vec::new());
        }
        // The currently selected Complementary chain exposes thirteen named
        // outputs across its seven active stages. Leave a small bounded headroom
        // for another legitimate feedback target without allowing a pack to
        // turn this one-frame diagnostic into an unbounded readback.
        const MAX_OUTPUTS: usize = 16;
        let mut trace = Vec::new();
        for consumer in consumers {
            for output in consumer.plan.outputs() {
                if trace.len() == MAX_OUTPUTS {
                    return Err(GalError::invalid_argument(
                        "selected-source fullscreen trace exceeds its bounded output limit",
                    ));
                }
                let name = output.role.shader_pack_color_name().ok_or_else(|| {
                    GalError::backend(
                        "selected-source fullscreen trace encountered a non-color output",
                    )
                })?;
                let capture = SelectedSourceOutputCapture::select_texture(
                    gal,
                    frame,
                    PathBuf::from(&dir),
                    format!(
                        "shader-pack-trace-{}-color-{}",
                        consumer
                            .program
                            .identity
                            .as_str()
                            .replace(|character: char| !character.is_ascii_alphanumeric(), "-"),
                        name.replace(|character: char| !character.is_ascii_alphanumeric(), "-"),
                    ),
                    format!(
                        "ShaderPackStage(\"{}\")::ShaderPackColor(\"{}\")",
                        consumer.program.identity.as_str(),
                        name
                    ),
                    output.format,
                    extent,
                    output.texture,
                )?
                .ok_or_else(|| {
                    GalError::backend(
                        "selected-source fullscreen trace unexpectedly skipped an armed capture",
                    )
                })?;
                trace.push(Self {
                    program_identity: consumer.program.identity.as_str().to_string(),
                    capture,
                });
            }
        }
        Ok(trace)
    }

    pub(in crate::render::worldrender) fn matches_fullscreen_consumer(
        &self,
        consumer: &PreparedNamedSourceFullscreenConsumer,
    ) -> bool {
        self.program_identity == consumer.program.identity.as_str()
    }
}

impl SelectedSourceOutputCapture {
    pub(in crate::render::worldrender) fn select(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        output: &SourceFinalOutputPlan,
    ) -> GalResult<Option<Self>> {
        if selected_source_capture_stage() == "terrain-primary"
            || selected_source_capture_stage() == "distant-horizons-primary"
            || selected_source_capture_stage() == "distant-horizons-depth"
            || selected_source_shader_pack_color_capture_name().is_some()
        {
            return Ok(None);
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(None);
        };
        if !selected_source_output_capture_requested(frame)?
            || SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
        {
            return Ok(None);
        }
        Self::select_texture(
            gal,
            frame,
            PathBuf::from(dir),
            "overlay",
            format!("{:?}", output.source_role()),
            output.overlay_color_format(),
            output.overlay_extent(),
            output.overlay_color_texture(),
        )
    }

    pub(in crate::render::worldrender) fn select_terrain_primary(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        texture: Handle,
        format: TextureFormat,
        extent: Extent3d,
    ) -> GalResult<Option<Self>> {
        if selected_source_capture_stage() != "terrain-primary"
            || selected_source_shader_pack_color_capture_name().is_some()
            || selected_source_fullscreen_stage_capture().is_some()
        {
            return Ok(None);
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(None);
        };
        if !selected_source_output_capture_requested(frame)?
            || SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
        {
            return Ok(None);
        }
        Self::select_texture(
            gal,
            frame,
            PathBuf::from(dir),
            "terrain-primary",
            "ShaderPackColor(\"primary\")".to_string(),
            format,
            extent,
            texture,
        )
    }

    pub(in crate::render::worldrender) fn select_distant_horizons_primary(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        texture: Handle,
        format: TextureFormat,
        extent: Extent3d,
    ) -> GalResult<Option<Self>> {
        if selected_source_capture_stage() != "distant-horizons-primary"
            || selected_source_shader_pack_color_capture_name().is_some()
            || selected_source_fullscreen_stage_capture().is_some()
        {
            return Ok(None);
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(None);
        };
        if !selected_source_output_capture_requested(frame)?
            || SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
        {
            return Ok(None);
        }
        Self::select_texture(
            gal,
            frame,
            PathBuf::from(dir),
            "distant-horizons-primary",
            "DistantHorizons::ShaderPackColor(\"primary\")".to_string(),
            format,
            extent,
            texture,
        )
    }

    pub(in crate::render::worldrender) fn select_distant_horizons_depth(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        texture: Handle,
        extent: Extent3d,
    ) -> GalResult<Option<Self>> {
        if selected_source_capture_stage() != "distant-horizons-depth"
            || selected_source_shader_pack_color_capture_name().is_some()
            || selected_source_fullscreen_stage_capture().is_some()
        {
            return Ok(None);
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(None);
        };
        if !selected_source_output_capture_requested(frame)?
            || SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
        {
            return Ok(None);
        }
        Self::select_texture(
            gal,
            frame,
            PathBuf::from(dir),
            "distant-horizons-depth",
            "DistantHorizons::OpaqueDepth".to_string(),
            TextureFormat::Depth32Float,
            extent,
            texture,
        )
    }

    pub(in crate::render::worldrender) fn select_shader_pack_color(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        name: &str,
        texture: Handle,
        format: TextureFormat,
        extent: Extent3d,
    ) -> GalResult<Option<Self>> {
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(None);
        };
        if selected_source_fullscreen_stage_capture().is_some() {
            return Ok(None);
        }
        if !selected_source_output_capture_requested(frame)?
            || SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
        {
            return Ok(None);
        }
        Self::select_texture(
            gal,
            frame,
            PathBuf::from(dir),
            format!(
                "shader-pack-color-{}",
                name.replace(|character: char| !character.is_ascii_alphanumeric(), "-")
            ),
            format!("ShaderPackColor(\"{name}\")"),
            format,
            extent,
            texture,
        )
    }

    pub(in crate::render::worldrender) fn select_fullscreen_stage(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        program_identity: &str,
        color_name: &str,
        texture: Handle,
        format: TextureFormat,
        extent: Extent3d,
    ) -> GalResult<Option<Self>> {
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return Ok(None);
        };
        if !selected_source_output_capture_requested(frame)?
            || SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
        {
            return Ok(None);
        }
        Self::select_texture(
            gal,
            frame,
            PathBuf::from(dir),
            format!(
                "shader-pack-stage-{}-color-{}",
                program_identity.replace(|character: char| !character.is_ascii_alphanumeric(), "-"),
                color_name.replace(|character: char| !character.is_ascii_alphanumeric(), "-")
            ),
            format!(
                "ShaderPackStage(\"{}\")::ShaderPackColor(\"{}\")",
                program_identity, color_name
            ),
            format,
            extent,
            texture,
        )
    }

    pub(in crate::render::worldrender) fn matches_fullscreen_consumer(
        &self,
        consumer: &PreparedNamedSourceFullscreenConsumer,
    ) -> bool {
        let Some(request) = selected_source_fullscreen_stage_capture() else {
            return false;
        };
        consumer.writes_named_color(&request.program_identity, &request.color_name)
    }

    #[allow(clippy::too_many_arguments)]
    pub(in crate::render::worldrender) fn select_texture(
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        dir: PathBuf,
        artifact_name: impl Into<String>,
        source_role: String,
        format: TextureFormat,
        extent: Extent3d,
        texture: Handle,
    ) -> GalResult<Option<Self>> {
        let artifact_name = artifact_name.into();
        let Some(bytes_per_texel) = format.copy_bytes_per_texel() else {
            SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN.store(false, Ordering::SeqCst);
            return Err(GalError::unsupported_feature(
                "selected-source diagnostic output has no host-copy format",
            ));
        };
        let bytes_per_row = extent.width.checked_mul(bytes_per_texel).ok_or_else(|| {
            GalError::invalid_argument("selected-source diagnostic row pitch overflows u32")
        })?;
        let byte_count = u64::from(bytes_per_row)
            .checked_mul(u64::from(extent.height))
            .ok_or_else(|| {
                GalError::invalid_argument("selected-source diagnostic size overflows u64")
            })?;
        let readback = match gal.create_buffer(BufferDesc {
            label: format!(
                "selected-source-frame-{}.{}.readback",
                frame.frame_id, artifact_name
            ),
            size: byte_count,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        }) {
            Ok(readback) => readback,
            Err(error) => {
                SELECTED_SOURCE_OUTPUT_CAPTURE_WRITTEN.store(false, Ordering::SeqCst);
                return Err(error);
            }
        };
        Ok(Some(Self {
            dir,
            frame_id: frame.frame_id,
            correlation_id: frame.correlation_id,
            source_role,
            artifact_name,
            format,
            extent,
            readback_rows_bottom_up: diagnostic_readback_rows_bottom_up(gal.capabilities().shader_conventions),
            texture,
            readback,
        }))
    }

    pub(in crate::render::worldrender) fn append_ops(
        &self,
        before: TextureUsageState,
        restore: TextureUsageState,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        let bytes_per_texel = self.format.copy_bytes_per_texel().ok_or_else(|| {
            GalError::unsupported_feature(
                "selected-source diagnostic output has no host-copy format",
            )
        })?;
        let bytes_per_row = self
            .extent
            .width
            .checked_mul(bytes_per_texel)
            .ok_or_else(|| {
                GalError::invalid_argument("selected-source diagnostic row pitch overflows u32")
            })?;
        let byte_count = u64::from(bytes_per_row) * u64::from(self.extent.height);
        operations.push(CommandOp::Barrier(texture_barrier(
            self.texture,
            before,
            TextureUsageState::TransferSrc,
        )));
        operations.push(CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: self.readback,
            buffer_offset: 0,
            bytes_per_row,
            rows_per_image: self.extent.height,
            texture: self.texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: self.extent,
        }));
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::HostReadBuffer {
            buffer: self.readback,
            offset: 0,
            size: byte_count,
        });
        // The persistent private overlay is the next source frame's color
        // attachment. Restore that semantic usage explicitly after the
        // diagnostic copy instead of leaving a readback-only state behind.
        operations.push(CommandOp::Barrier(texture_barrier(
            self.texture,
            TextureUsageState::TransferSrc,
            restore,
        )));
        Ok(())
    }

    pub(in crate::render::worldrender) fn write_artifacts(self, reads: &[CompletedHostRead], submission_id: u64) -> GalResult<()> {
        let read = reads
            .iter()
            .rev()
            .find(|read| read.buffer == self.readback)
            .ok_or_else(|| {
                GalError::backend("selected-source diagnostic output readback produced no bytes")
            })?;
        std::fs::create_dir_all(&self.dir).map_err(|error| {
            GalError::backend(format!(
                "failed to create selected-source diagnostic dir {}: {error}",
                self.dir.display()
            ))
        })?;
        let stem = format!(
            "selected-source-{}-frame-{}",
            self.artifact_name, self.frame_id
        );
        std::fs::write(self.dir.join(format!("{stem}.raw")), &read.bytes).map_err(|error| {
            GalError::backend(format!(
                "failed to write selected-source raw output: {error}"
            ))
        })?;
        let mut preview = selected_source_preview_rgba(self.format, &read.bytes);
        if self.readback_rows_bottom_up {
            flip_rgba_rows_in_place(&mut preview, self.extent.width, self.extent.height)?;
        }
        write_rgba_png(
            &self.dir.join(format!("{stem}.png")),
            self.extent.width,
            self.extent.height,
            &preview,
        )?;
        std::fs::write(
            self.dir.join(format!("{stem}.json")),
            format!(
                "{{\"artifact_class\":\"rust_selected_source_overlay\",\"frame_id\":{},\"correlation_id\":{},\"submission_id\":{},\"source_role\":\"{}\",\"format\":\"{:?}\",\"extent\":{{\"width\":{},\"height\":{}}},\"bytes\":{},\"hash\":\"{:08x}\",\"png_row_origin\":\"top-left\",\"readback_row_origin\":\"{}\"}}\n",
                self.frame_id,
                self.correlation_id,
                submission_id,
                json_escape(&self.source_role),
                self.format,
                self.extent.width,
                self.extent.height,
                read.bytes.len(),
                xxh32(&read.bytes, 0x53_4f_55_52),
                if self.readback_rows_bottom_up { "bottom-left" } else { "top-left" },
            ),
        )
        .map_err(|error| GalError::backend(format!("failed to write selected-source manifest: {error}")))
    }
}

pub(in crate::render::worldrender) fn selected_source_capture_stage() -> &'static str {
    match crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE")
        .ok()
        .as_deref()
        .map(str::trim)
    {
        Some("terrain-primary") => "terrain-primary",
        Some("distant-horizons-primary") => "distant-horizons-primary",
        Some("distant-horizons-depth") => "distant-horizons-depth",
        _ => "overlay",
    }
}

/// The source terrain transaction leaves its named color outputs readable;
/// optional readback therefore starts from `ShaderRead`, just like every
/// later source-stage capture. Keeping this fact centralized prevents a
/// diagnostic-only barrier from invalidating an otherwise valid submission.
pub(in crate::render::worldrender) fn selected_source_terrain_capture_before_state() -> TextureUsageState {
    TextureUsageState::ShaderRead
}

/// A selected-source diagnostic may read one named source color after the
/// complete Rust-owned fullscreen chain. The name remains a pack semantic
/// identity, never a source attachment number or backend resource handle.
pub(in crate::render::worldrender) fn selected_source_shader_pack_color_capture_name() -> Option<String> {
    crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE")
        .ok()
        .and_then(|value| {
            value
                .trim()
                .strip_prefix("shader-pack-color:")
                .map(str::to_owned)
        })
        .filter(|name| !name.is_empty())
}

/// Parses an opt-in source-stage diagnostic in the form
/// `shader-pack-stage:<program identity>:<named color>`. An explicit `*`
/// requests the first actual lowered output solely to discover a stage's
/// source-derived contract. This remains a single bounded readback and never
/// guesses a stage or attachment.
pub(in crate::render::worldrender) fn selected_source_fullscreen_stage_capture() -> Option<SelectedSourceFullscreenStageCapture> {
    parse_selected_source_fullscreen_stage_capture(
        crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE")
            .ok()?
            .as_str(),
    )
}

pub(in crate::render::worldrender) fn selected_source_fullscreen_stage_trace_enabled() -> bool {
    matches!(
        crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE")
            .as_deref()
            .map(str::trim),
        Ok("shader-pack-trace")
    )
}

pub(in crate::render::worldrender) fn parse_selected_source_fullscreen_stage_capture(
    value: &str,
) -> Option<SelectedSourceFullscreenStageCapture> {
    let value = value.trim().strip_prefix("shader-pack-stage:")?;
    let (program_identity, color_name) = value.rsplit_once(':')?;
    (!program_identity.is_empty() && !color_name.is_empty()).then(|| {
        SelectedSourceFullscreenStageCapture {
            program_identity: program_identity.to_string(),
            color_name: color_name.to_string(),
        }
    })
}

/// Selected-source output readback is a diagnostic for one explicitly
/// correlated gameplay frame. Mesh updates may occur while the copied world is
/// still settling; they must not turn every source frame into a synchronous
/// full-frame readback before the deterministic screenshot request exists.
pub(in crate::render::worldrender) fn selected_source_output_capture_requested(frame: &WorldPrimitiveFrame) -> GalResult<bool> {
    if !matches!(
        crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_EXECUTION")
            .as_deref()
            .map(str::trim),
        Ok("1") | Ok("true") | Ok("TRUE")
    ) {
        return Ok(false);
    }
    let Some(request_path) = crate::core::environment::var_os("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST") else {
        return Ok(false);
    };
    let request_path = PathBuf::from(request_path);
    if !request_path.is_file() {
        return Ok(false);
    }
    let request = GameplayAttachmentCaptureRequest::read(&request_path)?;
    Ok(selected_source_capture_request_matches(
        frame.frame_id,
        frame.correlation_id,
        request,
    ))
}

pub(in crate::render::worldrender) fn selected_source_capture_request_matches(
    frame_id: u64,
    correlation_id: u64,
    request: GameplayAttachmentCaptureRequest,
) -> bool {
    request.frame_id == frame_id && request.correlation_id == correlation_id
}

pub(in crate::render::worldrender) struct GameplayAttachmentCapture {
    pub(in crate::render::worldrender) dir: PathBuf,
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) correlation_id: u64,
    pub(in crate::render::worldrender) deterministic_rendered_frame_index: u64,
    pub(in crate::render::worldrender) lod_instance_count: usize,
    pub(in crate::render::worldrender) lod_route_selected: bool,
    pub(in crate::render::worldrender) generation: u64,
    pub(in crate::render::worldrender) resource_generation: u64,
    pub(in crate::render::worldrender) extent: Extent3d,
    pub(in crate::render::worldrender) readback_rows_bottom_up: bool,
    pub(in crate::render::worldrender) final_output_only: bool,
    pub(in crate::render::worldrender) workload_fingerprint: String,
    /// Immutable sky/fog inputs consumed by this exact selected submission.
    pub(in crate::render::worldrender) sky_fog_receipt: String,
    /// SSAO inputs and pass admission for this exact Rust-owned DH frame.
    /// This is a bounded diagnostic receipt; it contains no backend handles.
    pub(in crate::render::worldrender) ssao_parameters: [f32; 8],
    pub(in crate::render::worldrender) ssao_pass_appended: bool,
    /// Copied transition policy plus the exact Rust pass count are emitted as
    /// a bounded execution receipt after the selected submission completes.
    pub(in crate::render::worldrender) lod_fade_flags: u32,
    pub(in crate::render::worldrender) lod_direct_composite_pass_count: u32,
    /// Observed command inputs, published only after this submission completes.
    pub(in crate::render::worldrender) decal_inputs: serde_json::Value,
    pub(in crate::render::worldrender) equipment_inputs: serde_json::Value,
    pub(in crate::render::worldrender) wolf_inputs: serde_json::Value,
    pub(in crate::render::worldrender) readbacks: BTreeMap<String, Handle>,
    pub(in crate::render::worldrender) readback_formats: BTreeMap<String, TextureFormat>,
    pub(in crate::render::worldrender) source_presented_capture: Option<SourceFinalPresentationCapture>,
    /// Frame-local copies used exclusively by the normal-route attachment
    /// diagnostic. They are retained through completion, then destroyed with
    /// their readback buffers so a full capture cannot leak a transient image.
    pub(in crate::render::worldrender) normal_presented_textures: Vec<Handle>,
    pub(in crate::render::worldrender) transient_gui_passes: Vec<Handle>,
    /// Whether this capture observed the normal route's deferred G-buffer.
    /// Forward routes still capture their actual Rust-owned world/final images,
    /// but must never fabricate unavailable intermediate attachments.
    pub(in crate::render::worldrender) g_buffer_attachments_available: bool,
}

impl GameplayAttachmentCapture {
    pub(in crate::render::worldrender) fn select(
        frame: &WorldPrimitiveFrame,
        generation: u64,
        resource_generation: u64,
        conventions: ShaderConventions,
    ) -> GalResult<Option<Self>> {
        Self::select_for_frame(frame, generation, resource_generation, conventions, false)
    }

    /// The selected-source graph is admitted a frame after the normal graph
    /// prepares its exact semantic snapshot. A pending request is deliberately
    /// invisible to the normal graph and may only be promoted by this path.
    pub(in crate::render::worldrender) fn select_source(
        frame: &WorldPrimitiveFrame,
        generation: u64,
        resource_generation: u64,
        conventions: ShaderConventions,
    ) -> GalResult<Option<Self>> {
        Self::select_for_frame(frame, generation, resource_generation, conventions, true)
    }

    pub(in crate::render::worldrender) fn select_for_frame(
        frame: &WorldPrimitiveFrame,
        generation: u64,
        resource_generation: u64,
        conventions: ShaderConventions,
        source_selected: bool,
    ) -> GalResult<Option<Self>> {
        let Some(dir) = crate::core::environment::var_os("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR") else {
            return Ok(None);
        };
        let request_path =
            crate::core::environment::var_os("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST").map(PathBuf::from);
        // Normal capture uses the Java deterministic selector.  The explicit
        // diagnostic-once mode is deliberately Rust-owned: it reads back one
        // eligible submitted frame to isolate a pass-local defect when that
        // external selector is itself under investigation. It never supplies
        // screenshot-parity evidence and is unavailable to selected-source
        // execution, where correlation is part of source admission.
        let diagnostic_once = !source_selected
            && matches!(
                crate::core::environment::var("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIAGNOSTIC_ONCE").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let request =
            if let Some(request_path) = request_path.as_ref().filter(|path| path.is_file()) {
                GameplayAttachmentCaptureRequest::read(request_path)?
            } else if diagnostic_once {
                GameplayAttachmentCaptureRequest {
                    frame_id: frame.frame_id,
                    correlation_id: frame.correlation_id,
                    deterministic_rendered_frame_index: frame.frame_id,
                    source_selected_capture: false,
                    source_selected_pending: false,
                    required_entity_mesh: false,
                }
            } else {
                return Ok(None);
            };
        let request = if request.source_selected_pending {
            if !source_selected {
                return Ok(None);
            }
            if request.required_entity_mesh
                && !frame
                    .mesh_instances
                    .iter()
                    .any(|instance| instance.stratum == WORLD_STRATUM_ENTITY_MESH)
            {
                return Ok(None);
            }
            request.promote_to_source_frame(
                request_path.as_deref().ok_or_else(|| {
                    GalError::invalid_argument(
                        "selected-source attachment capture requires a request path",
                    )
                })?,
                frame,
            )?
        } else {
            request
        };
        let min_mesh_instances =
            crate::core::environment::var("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_MESH_INSTANCES")
                .ok()
                .and_then(|value| value.parse::<usize>().ok())
                .unwrap_or(1);
        if request.frame_id != frame.frame_id || request.correlation_id != frame.correlation_id {
            return Ok(None);
        }
        let has_selected_lod_work =
            frame.lod_render_frame.rust_route_selected() && !frame.lod_instances.is_empty();
        if frame.mesh_instances.len() < min_mesh_instances && !has_selected_lod_work {
            return Ok(None);
        }
        if diagnostic_once
            && GAMEPLAY_ATTACHMENT_DIAGNOSTIC_ONCE_CAPTURED.swap(true, Ordering::AcqRel)
        {
            return Ok(None);
        }
        let mut captured_request = GAMEPLAY_ATTACHMENT_CAPTURE_REQUEST
            .lock()
            .expect("gameplay attachment capture request mutex must not be poisoned");
        if *captured_request == Some((request.frame_id, request.correlation_id)) {
            return Ok(None);
        }
        *captured_request = Some((request.frame_id, request.correlation_id));
        Ok(Some(Self {
            dir: PathBuf::from(dir),
            frame_id: frame.frame_id,
            correlation_id: frame.correlation_id,
            deterministic_rendered_frame_index: request.deterministic_rendered_frame_index,
            lod_instance_count: frame.lod_instances.len(),
            lod_route_selected: frame.lod_render_frame.rust_route_selected(),
            generation,
            resource_generation,
            extent: Extent3d {
                width: frame.viewport_width.max(1),
                height: frame.viewport_height.max(1),
                depth: 1,
            },
            readback_rows_bottom_up: diagnostic_readback_rows_bottom_up(conventions),
            // A full attachment dump is intentionally expensive. Deterministic
            // multi-pose captures retain it for their selected diagnostic pose;
            // later poses need only the exact renderer-owned final image.
            final_output_only: crate::core::environment::var("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY")
                .is_ok_and(|value| matches!(value.as_str(), "1" | "true" | "TRUE")),
            workload_fingerprint: format!(
                "segments={} crack_quads={} border_quads={} material_quads={} mesh_instances={} lod_instances={} lod_route_selected={} background_enabled={}",
                frame.segments.len(),
                frame.crack_quads.len(),
                frame.border_quads.len(),
                frame.material_quads.len(),
                frame.mesh_instances.len(),
                frame.lod_instances.len(),
                frame.lod_render_frame.rust_route_selected(),
                frame.background.enabled
            ),
            sky_fog_receipt: sky_fog_receipt_json(frame)?,
            ssao_parameters: frame.lod_render_frame.ssao_parameters,
            ssao_pass_appended: false,
            lod_fade_flags: frame.lod_render_frame.flags
                & (WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS
                    | WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS
                    | WORLD_LOD_FLAG_DH_FAR_CLIP_FADE),
            lod_direct_composite_pass_count: 0,
            decal_inputs: serde_json::json!({"schema":"world-decal-submission-inputs-v1",
                "complete":false,"gpu_readback":false,"capability_admitted":false,
                "reason":"command stream observation unavailable for this route"}),
            wolf_inputs: serde_json::json!({"schema":"wolf-submission-inputs-v1", "complete":false,
                "gpu_readback":false,"capability_admitted":false,"reason":"command observation unavailable"}),
            equipment_inputs: serde_json::json!({"schema":"equipment-submission-inputs-v1", "complete":false,
                "gpu_readback":false,"capability_admitted":false,"reason":"command observation unavailable"}),
            readbacks: BTreeMap::new(),
            readback_formats: BTreeMap::new(),
            source_presented_capture: None,
            normal_presented_textures: Vec::new(),
            transient_gui_passes: Vec::new(),
            g_buffer_attachments_available: false,
        }))
    }

    /// A selected-source diagnostic capture must mirror the actual final
    /// present copy rather than reading the pre-present overlay. The mirror is
    /// frame-local and remains entirely Rust-owned.
    pub(in crate::render::worldrender) fn stage_source_presented_capture(
        &mut self,
        gal: &mut VulkanicGal,
        final_output: &SourceFinalOutputPlan,
    ) -> GalResult<()> {
        if self.source_presented_capture.is_some() {
            return Err(GalError::invalid_argument(
                "gameplay attachment capture already staged a source presentation mirror",
            ));
        }
        self.source_presented_capture = Some(final_output.stage_presented_capture(
            gal,
            &format!("gameplay-frame-{}.presented-final", self.frame_id),
        )?);
        Ok(())
    }

    pub(in crate::render::worldrender) fn append_ops(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        g_buffer: Option<&GBufferResources>,
        final_output: Option<&SourceFinalOutputPlan>,
    ) -> GalResult<()> {
        self.append_ops_with_final_state(
            gal,
            ops,
            g_buffer,
            final_output,
            TextureUsageState::ShaderRead,
        )
    }

    pub(in crate::render::worldrender) fn append_ops_with_final_state(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        g_buffer: Option<&GBufferResources>,
        final_output: Option<&SourceFinalOutputPlan>,
        final_output_before: TextureUsageState,
    ) -> GalResult<()> {
        let Some(g_buffer) = g_buffer else {
            // Fancy's forward graph has no deferred intermediates to read.
            // Leave those attachments explicitly unavailable, then let the
            // existing normal-route capture copy the real pre-GUI and final
            // Rust presentation images. A capture request is observational;
            // it must not turn a supported forward frame into a fatal submit.
            return Ok(());
        };
        self.g_buffer_attachments_available = true;
        let final_attachment = if let Some(presented) = self.source_presented_capture.as_ref() {
            (
                "final_output",
                presented.color_texture(),
                presented.format(),
            )
        } else if let Some(final_output) = final_output {
            (
                "final_output",
                final_output.overlay_color_texture(),
                final_output.overlay_color_format(),
            )
        } else {
            (
                "final_output",
                g_buffer.composite1_texture,
                SHADER_G_BUFFER_COLOR_FORMAT,
            )
        };
        // The ordinary and selected-source routes both read the acquired
        // presentation target after GUI composition. Deferred attachments
        // are captured here; the final image is captured at that later point.
        let defer_normal_final_output =
            final_output.is_none() && self.source_presented_capture.is_none();
        let attachments = if self.final_output_only {
            if defer_normal_final_output {
                Vec::new()
            } else {
                vec![final_attachment]
            }
        } else {
            let mut attachments = vec![
                (
                    "shadow_depth",
                    g_buffer.shadow_depth_texture,
                    TextureFormat::Depth32Float,
                ),
                (
                    "albedo",
                    g_buffer.albedo_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                (
                    "normal",
                    g_buffer.normal_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                (
                    "material_light",
                    g_buffer.material_light_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                (
                    "world_position",
                    g_buffer.world_position_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                (
                    "main_depth",
                    g_buffer.depth_texture,
                    TextureFormat::Depth32Float,
                ),
                (
                    "deferred_lit",
                    g_buffer.deferred_lit_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                // This is the exact Rust-owned source consumed by the
                // Fabulous `minecraft:translucent` handoff.  Capturing it
                // alongside the lit/composite images makes an overlapping
                // pane mismatch attributable to either the terrain capture
                // or the later post-effect composition without changing the
                // submitted render graph.
                (
                    "translucent_capture",
                    g_buffer.translucent_capture_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                (
                    "translucent_capture_depth",
                    g_buffer.translucent_capture_depth_texture,
                    TextureFormat::Depth32Float,
                ),
                (
                    "composite_0",
                    g_buffer.composite0_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
                (
                    "composite_1",
                    g_buffer.composite1_texture,
                    SHADER_G_BUFFER_COLOR_FORMAT,
                ),
            ];
            if !defer_normal_final_output {
                attachments.push(final_attachment);
            }
            attachments
        };
        for (name, texture, format) in attachments {
            let bytes_per_texel = format.copy_bytes_per_texel().ok_or_else(|| {
                GalError::unsupported_feature(format!(
                    "gameplay attachment {name} uses {format:?}, which has no host-copy contract"
                ))
            })?;
            let byte_count = u64::from(self.extent.width)
                .checked_mul(u64::from(self.extent.height))
                .and_then(|texels| texels.checked_mul(u64::from(bytes_per_texel)))
                .ok_or_else(|| {
                    GalError::invalid_argument("gameplay attachment readback size overflows")
                })?;
            let readback = gal.create_buffer(BufferDesc {
                label: format!(
                    "gameplay-frame-{}.attachment-{name}.readback",
                    self.frame_id
                ),
                size: byte_count,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })?;
            self.readbacks.insert(name.to_string(), readback);
            self.readback_formats.insert(name.to_string(), format);
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                if name == "final_output" {
                    final_output_before
                } else {
                    TextureUsageState::ShaderRead
                },
                TextureUsageState::TransferSrc,
            )));
            ops.push(CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
                buffer: readback,
                buffer_offset: 0,
                bytes_per_row: self.extent.width * bytes_per_texel,
                rows_per_image: self.extent.height,
                texture,
                texture_mip: 0,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: self.extent,
            }));
            ops.push(CommandOp::Barrier(buffer_barrier(
                readback,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::HostReadBuffer {
                buffer: readback,
                offset: 0,
                size: byte_count,
            });
            // A readback is the last operation in this frame transaction.
            // Persistent source-final overlays are reused by acquired-image
            // identity and the next frame explicitly expects ShaderRead. The
            // frame-local presented mirror is transfer-only (not sampled), so
            // it must remain ColorAttachment instead.
            // The presentation mirror is only the final-output attachment.
            // The other readbacks (including depth) remain shader-readable
            // after capture; restoring all of them to ColorAttachment would
            // issue illegal Vulkan layout transitions for depth images and
            // leave sampled G-buffer descriptors in the wrong layout.
            let restore =
                gameplay_attachment_restore_state(name, self.source_presented_capture.is_some());
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                TextureUsageState::TransferSrc,
                restore,
            )));
        }
        Ok(())
    }

    /// Read back one additional Rust-owned shader-readable attachment for the
    /// selected diagnostic frame. The texture is restored immediately, so
    /// this observes the production graph without becoming a presentation or
    /// resource-sharing path.
    pub(in crate::render::worldrender) fn append_shader_read_attachment(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        name: &str,
        texture: Handle,
        format: TextureFormat,
    ) -> GalResult<()> {
        if self.readbacks.contains_key(name) {
            return Err(GalError::invalid_argument(format!(
                "gameplay attachment capture already has a {name} readback"
            )));
        }
        let bytes_per_texel = format.copy_bytes_per_texel().ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "gameplay attachment {name} uses {format:?}, which has no host-copy contract"
            ))
        })?;
        let byte_count = u64::from(self.extent.width)
            .checked_mul(u64::from(self.extent.height))
            .and_then(|texels| texels.checked_mul(u64::from(bytes_per_texel)))
            .ok_or_else(|| {
                GalError::invalid_argument("gameplay attachment readback size overflows")
            })?;
        let readback = gal.create_buffer(BufferDesc {
            label: format!(
                "gameplay-frame-{}.attachment-{name}.readback",
                self.frame_id
            ),
            size: byte_count,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })?;
        self.readbacks.insert(name.to_string(), readback);
        self.readback_formats.insert(name.to_string(), format);
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: self.extent.width * bytes_per_texel,
            rows_per_image: self.extent.height,
            texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: self.extent,
        }));
        ops.push(CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: byte_count,
        });
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::TransferSrc,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    /// Appends the normal route's exact final output after all semantic GUI
    /// commands. This is a capture-only readback of Rust's acquired target;
    /// it neither presents nor creates another renderer.
    pub(in crate::render::worldrender) fn append_normal_presented_output(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
    ) -> GalResult<()> {
        self.append_normal_frame_output(gal, ops, frame_target, "final_output")
    }

    /// Captures a forward route's real depth attachment for the opt-in whole
    /// frame audit. Deferred routes use `append_ops_with_final_state` above,
    /// while forward routes have no G-buffer and therefore need this explicit
    /// attachment copy. The depth image is restored to its prior attachment
    /// state before the normal frame submission continues.
    pub(in crate::render::worldrender) fn append_forward_depth_output(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        depth_texture: Handle,
        depth_before: TextureUsageState,
    ) -> GalResult<()> {
        if self.final_output_only {
            return Ok(());
        }
        if self.readbacks.contains_key("main_depth") {
            return Err(GalError::invalid_argument(
                "gameplay attachment capture already has a main_depth readback",
            ));
        }
        let bytes_per_texel = TextureFormat::Depth32Float
            .copy_bytes_per_texel()
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "forward gameplay depth attachment has no host-copy contract",
                )
            })?;
        let byte_count = u64::from(self.extent.width)
            .checked_mul(u64::from(self.extent.height))
            .and_then(|texels| texels.checked_mul(u64::from(bytes_per_texel)))
            .ok_or_else(|| {
                GalError::invalid_argument("forward gameplay depth readback size overflows")
            })?;
        let readback = gal.create_buffer(BufferDesc {
            label: format!(
                "gameplay-frame-{}.attachment-main_depth.readback",
                self.frame_id
            ),
            size: byte_count,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })?;
        self.readbacks.insert("main_depth".to_string(), readback);
        self.readback_formats
            .insert("main_depth".to_string(), TextureFormat::Depth32Float);
        ops.push(CommandOp::Barrier(texture_barrier(
            depth_texture,
            depth_before,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: self.extent.width * bytes_per_texel,
            rows_per_image: self.extent.height,
            texture: depth_texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: self.extent,
        }));
        ops.push(CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: byte_count,
        });
        ops.push(CommandOp::Barrier(texture_barrier(
            depth_texture,
            TextureUsageState::TransferSrc,
            depth_before,
        )));
        Ok(())
    }

    /// Captures the normal graph's world-final acquired image before semantic
    /// GUI work. This is enabled only for the opt-in attachment diagnostic.
    pub(in crate::render::worldrender) fn append_normal_world_output(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
    ) -> GalResult<()> {
        if self.final_output_only {
            return Ok(());
        }
        self.append_normal_frame_output(gal, ops, frame_target, "world_final_pre_gui")
    }

    /// Copies the actual acquired target after the requested semantic stage.
    /// Source captures use this after GUI so atlas-backed item meshes appear
    /// exactly as submitted, without a second private GUI rendering pass.
    pub(in crate::render::worldrender) fn append_normal_frame_output(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        frame_target: Handle,
        name: &str,
    ) -> GalResult<()> {
        if self.readbacks.contains_key(name) {
            return Err(GalError::invalid_argument(format!(
                "normal gameplay capture already has a {name} readback"
            )));
        }
        let format = gal.pass_target_color_format(frame_target)?;
        let texture = gal.create_texture(TextureDesc {
            label: format!("gameplay-frame-{}.{}.copy", self.frame_id, name),
            dimension: TextureDimension::D2,
            format,
            extent: self.extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
        })?;
        self.normal_presented_textures.push(texture);
        let bytes_per_texel = format.copy_bytes_per_texel().ok_or_else(|| {
            GalError::unsupported_feature(
                "normal gameplay presentation target has no host-copy contract",
            )
        })?;
        let byte_count = u64::from(self.extent.width)
            .checked_mul(u64::from(self.extent.height))
            .and_then(|texels| texels.checked_mul(u64::from(bytes_per_texel)))
            .ok_or_else(|| {
                GalError::invalid_argument("normal gameplay final readback size overflows")
            })?;
        let readback = gal.create_buffer(BufferDesc {
            label: format!("gameplay-frame-{}.{}.readback", self.frame_id, name),
            size: byte_count,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })?;
        self.readbacks.insert(name.to_string(), readback);
        self.readback_formats.insert(name.to_string(), format);
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::Undefined,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::CopyFrameTargetToTexture {
            src: frame_target,
            dst: texture,
            extent: self.extent,
        });
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: self.extent.width * bytes_per_texel,
            rows_per_image: self.extent.height,
            texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: self.extent,
        }));
        ops.push(CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: byte_count,
        });
        Ok(())
    }

    pub(in crate::render::worldrender) fn append_source_presented_ops(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
        g_buffer: Option<&GBufferResources>,
        final_output: &SourceFinalOutputPlan,
    ) -> GalResult<()> {
        // The diagnostic mirror receives the exact GUI replay immediately
        // before this readback, so it is currently a color attachment rather
        // than shader-readable. Keep that explicit state in the copy contract;
        // a generic ShaderRead assumption produces a Vulkan layout mismatch.
        self.append_ops_with_final_state(
            gal,
            ops,
            g_buffer,
            Some(final_output),
            TextureUsageState::ColorAttachment,
        )
    }

    pub(in crate::render::worldrender) fn append_source_presented_copy(&self, ops: &mut Vec<CommandOp>) -> GalResult<()> {
        let presented = self.source_presented_capture.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected-source attachment capture was not staged against the final presentation copy",
            )
        })?;
        presented.append_draw(ops);
        Ok(())
    }

    pub(in crate::render::worldrender) fn source_presented_target(&self) -> GalResult<Handle> {
        self.source_presented_capture
            .as_ref()
            .map(SourceFinalPresentationCapture::target)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source attachment capture has no presentation mirror target",
                )
            })
    }

    pub(in crate::render::worldrender) fn source_presented_color_attachment(&self) -> GalResult<Handle> {
        self.source_presented_capture
            .as_ref()
            .map(SourceFinalPresentationCapture::color_attachment)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source attachment capture has no presentation mirror color attachment",
                )
            })
    }

    pub(in crate::render::worldrender) fn retain_transient_gui_passes(&mut self, passes: Vec<Handle>) {
        self.transient_gui_passes.extend(passes);
    }

    pub(in crate::render::worldrender) fn write_artifacts(
        mut self,
        gal: &mut VulkanicGal,
        reads: Vec<CompletedHostRead>,
        submission_id: u64,
        stats: &WorldPrimitiveSubmitStats,
    ) -> GalResult<()> {
        self.ssao_pass_appended |= stats.lod_ssao_pass_appended;
        self.lod_direct_composite_pass_count = stats.lod_direct_composite_pass_count;
        let write_result = (|| -> GalResult<()> {
            std::fs::create_dir_all(&self.dir).map_err(|error| {
                GalError::backend(format!(
                    "failed to create gameplay attachment dump dir {}: {error}",
                    self.dir.display()
                ))
            })?;
            let mut hashes = Vec::new();
            let mut evidence = Vec::new();
            for (name, &buffer) in &self.readbacks {
                let Some(read) = reads.iter().rev().find(|read| read.buffer == buffer) else {
                    return Err(GalError::backend(format!(
                        "gameplay attachment dump missing readback bytes for {name}"
                    )));
                };
                let bytes = &read.bytes;
                let format = self.readback_formats.get(name).copied().ok_or_else(|| {
                    GalError::backend(format!(
                        "gameplay attachment dump lost format metadata for {name}"
                    ))
                })?;
                let hash = xxh32(bytes, 0x47_41_4d_45);
                hashes.push(format!("\"{name}\":\"{hash:08x}\""));
                evidence.push(format!(
                    "\"{name}\":{}",
                    attachment_evidence_json_for_format(
                        self.extent.width,
                        self.extent.height,
                        &name,
                        format,
                        bytes,
                    )
                ));
                if format == TextureFormat::Depth32Float {
                    std::fs::write(self.dir.join(format!("attachment-{name}.raw")), bytes)
                        .map_err(|error| {
                            GalError::backend(format!(
                                "failed to write gameplay depth attachment {name}: {error}"
                            ))
                        })?;
                    let mut rgba = depth_attachment_to_grayscale_rgba(bytes);
                    if self.readback_rows_bottom_up {
                        flip_rgba_rows_in_place(&mut rgba, self.extent.width, self.extent.height)?;
                    }
                    write_rgba_png(
                        &self.dir.join(format!("attachment-{name}.png")),
                        self.extent.width,
                        self.extent.height,
                        &rgba,
                    )?;
                } else {
                    if format != TextureFormat::Rgba8Unorm {
                        std::fs::write(self.dir.join(format!("attachment-{name}.raw")), bytes)
                            .map_err(|error| {
                                GalError::backend(format!(
                                    "failed to write gameplay attachment raw bytes {name}: {error}"
                                ))
                            })?;
                    }
                    let mut rgba = selected_source_preview_rgba(format, bytes);
                    if self.readback_rows_bottom_up {
                        flip_rgba_rows_in_place(&mut rgba, self.extent.width, self.extent.height)?;
                    }
                    write_rgba_png(
                        &self.dir.join(format!("attachment-{name}.png")),
                        self.extent.width,
                        self.extent.height,
                        &rgba,
                    )?;
                }
            }
            let attachment_files = gameplay_attachment_file_names_json(&self.readback_formats);
            self.equipment_inputs["gameplay_frame_id"] = self.frame_id.into();
            self.equipment_inputs["correlation_id"] = self.correlation_id.into();
            self.equipment_inputs["gal_submission_id"] = submission_id.into();
            self.equipment_inputs["deterministic_rendered_frame_index"] =
                self.deterministic_rendered_frame_index.into();
            std::fs::write(
                self.dir.join("attachment-equipment-inputs.json"),
                self.equipment_inputs.to_string(),
            )
            .map_err(|e| {
                GalError::backend(format!("failed to write equipment input receipt: {e}"))
            })?;
            self.wolf_inputs["gameplay_frame_id"] = self.frame_id.into();
            self.wolf_inputs["correlation_id"] = self.correlation_id.into();
            self.wolf_inputs["gal_submission_id"] = submission_id.into();
            self.wolf_inputs["deterministic_rendered_frame_index"] =
                self.deterministic_rendered_frame_index.into();
            std::fs::write(
                self.dir.join("attachment-wolf-inputs.json"),
                self.wolf_inputs.to_string(),
            )
            .map_err(|e| GalError::backend(format!("failed to write wolf input receipt: {e}")))?;
            self.decal_inputs["gameplay_frame_id"] = self.frame_id.into();
            self.decal_inputs["correlation_id"] = self.correlation_id.into();
            self.decal_inputs["gal_submission_id"] = submission_id.into();
            self.decal_inputs["deterministic_rendered_frame_index"] =
                self.deterministic_rendered_frame_index.into();
            std::fs::write(
                self.dir.join("attachment-decal-inputs.json"),
                self.decal_inputs.to_string(),
            )
            .map_err(|error| {
                GalError::backend(format!("failed to write decal input receipt: {error}"))
            })?;
            // Java copies this immutable receipt beside the final image at the
            // same selected-source handoff. Keep the producer-side name
            // constant so repeated readiness submissions cannot accumulate
            // unbounded diagnostics.
            let sky_fog_receipt_name = "attachment-sky-fog.json";
            std::fs::write(self.dir.join(&sky_fog_receipt_name), &self.sky_fog_receipt).map_err(
                |error| {
                    GalError::backend(format!(
                        "failed to write gameplay sky/fog receipt {}: {error}",
                        self.dir.display()
                    ))
                },
            )?;
            let ssao = self.ssao_parameters;
            let ssao_receipt = format!(
                concat!(
                    "{{\"schema\":\"rust-vulkan-dh-ssao-receipt-v1\",",
                    "\"gameplay_frame_id\":{},\"correlation_id\":{},",
                    "\"gal_submission_id\":{},\"enabled\":{},",
                    "\"pass_appended\":{},\"sample_count\":{},",
                    "\"radius\":{:.6},\"strength\":{:.6},",
                    "\"min_light\":{:.6},\"bias\":{:.6},",
                    "\"fade_distance\":{:.6},\"blur_radius\":{:.6},",
                    "\"owner\":\"rust-vulkan-dh-private-depth\"}}\n"
                ),
                self.frame_id,
                self.correlation_id,
                submission_id,
                ssao[0] >= 0.5,
                self.ssao_pass_appended,
                ssao[1].round() as u32,
                ssao[2],
                ssao[3],
                ssao[4],
                ssao[5],
                ssao[6],
                ssao[7],
            );
            std::fs::write(self.dir.join("attachment-ssao.json"), ssao_receipt).map_err(
                |error| {
                    GalError::backend(format!(
                        "failed to write gameplay SSAO receipt {}: {error}",
                        self.dir.display()
                    ))
                },
            )?;
            let lod_only = self.lod_fade_flags
                & (WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS
                    | WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS)
                == (WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS
                    | WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS);
            let vanilla_fade_mode = if lod_only {
                "LOD_ONLY"
            } else if self.lod_fade_flags & WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS != 0 {
                "DOUBLE_PASS"
            } else if self.lod_fade_flags & WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS != 0 {
                "SINGLE_PASS"
            } else {
                "NONE"
            };
            let fade_receipt = format!(
                concat!(
                    "{{\"schema\":\"rust-vulkan-dh-fade-receipt-v1\",",
                    "\"gameplay_frame_id\":{},\"correlation_id\":{},",
                    "\"gal_submission_id\":{},\"vanilla_fade_mode\":\"{}\",",
                    "\"far_clip_fade\":{},\"composite_pass_count\":{},",
                    "\"owner\":\"rust-vulkan-dh-private-compositor\"}}\n"
                ),
                self.frame_id,
                self.correlation_id,
                submission_id,
                vanilla_fade_mode,
                self.lod_fade_flags & WORLD_LOD_FLAG_DH_FAR_CLIP_FADE != 0,
                self.lod_direct_composite_pass_count,
            );
            std::fs::write(self.dir.join("attachment-dh-fade.json"), fade_receipt).map_err(
                |error| {
                    GalError::backend(format!(
                        "failed to write gameplay DH fade receipt {}: {error}",
                        self.dir.display()
                    ))
                },
            )?;
            let manifest = format!(
                "{{\n  \"artifact_class\":\"rust_vulkan_whole_frame_gameplay_attachments\",\n  \"source\":\"real-gameplay-whole-frame-submit\",\n  \"capture_scope\":\"{}\",\n  \"png_row_origin\":\"top-left\",\n  \"readback_row_origin\":\"{}\",\n  \"synthetic_shader_scene\":false,\n  \"java_iris_participation\":false,\n  \"gameplay_frame_id\":{},\n  \"correlation_id\":{},\n  \"deterministic_rendered_frame_index\":{},\n  \"gal_submission_id\":{},\n  \"vulkan_submission_timeline_value\":{},\n  \"pass_graph_generation\":3,\n  \"shader_resource_generation\":{},\n  \"frame_generation\":{},\n  \"extent\":{{\"width\":{},\"height\":{}}},\n  \"producer_workload_fingerprint\":\"{}\",\n  \"world_mesh_instances\":{},\n  \"world_mesh_batches\":{},\n  \"world_mesh_draws\":{},\n  \"world_lod_instances\":{},\n  \"world_lod_route_selected\":{},\n  \"world_material_quads\":{},\n  \"world_crack_quads\":{},\n  \"world_border_quads\":{},\n  \"final_output_source\":\"selected-source frames mirror the exact Rust-owned final present copy into a frame-local diagnostic target after the acquired-target copy has been recorded; other frames read composite_1\",\n  \"sky_fog_receipt\":\"{}\",\n  \"attachment_hashes\":{{{}}},\n  \"attachment_evidence\":{{{}}},\n  \"attachment_files\":[{}]\n}}\n",
                gameplay_attachment_capture_scope(
                    self.final_output_only,
                    self.g_buffer_attachments_available,
                ),
                if self.readback_rows_bottom_up {
                    "bottom-left"
                } else {
                    "top-left"
                },
                self.frame_id,
                self.correlation_id,
                self.deterministic_rendered_frame_index,
                submission_id,
                submission_id,
                self.resource_generation,
                self.generation,
                self.extent.width,
                self.extent.height,
                json_escape(&self.workload_fingerprint),
                stats.mesh_instance_count,
                stats.mesh_batch_count,
                stats.mesh_draw_count,
                self.lod_instance_count,
                self.lod_route_selected,
                stats.material_quad_count,
                stats.crack_quad_count,
                stats.border_quad_count,
                sky_fog_receipt_name,
                hashes.join(","),
                evidence.join(","),
                attachment_files
            );
            std::fs::write(
                self.dir
                    .join(format!("gameplay-attachments-frame-{}.json", self.frame_id)),
                manifest,
            )
            .map_err(|error| {
                GalError::backend(format!(
                    "failed to write gameplay attachment manifest {}: {error}",
                    self.dir.display()
                ))
            })
        })();
        for (_, readback) in std::mem::take(&mut self.readbacks) {
            let _ = gal.destroy(readback);
        }
        for pass in std::mem::take(&mut self.transient_gui_passes) {
            let _ = gal.destroy(pass);
        }
        if let Some(presented) = self.source_presented_capture.take() {
            presented.destroy(gal);
        }
        for texture in std::mem::take(&mut self.normal_presented_textures) {
            let _ = gal.destroy(texture);
        }
        write_result
    }
}

pub(in crate::render::worldrender) fn gameplay_attachment_capture_scope(
    final_output_only: bool,
    g_buffer_attachments_available: bool,
) -> &'static str {
    if final_output_only {
        "final-output-only"
    } else if g_buffer_attachments_available {
        "full-attachments"
    } else {
        "forward-final-attachments"
    }
}

pub(in crate::render::worldrender) fn gameplay_attachment_file_names_json(
    readback_formats: &std::collections::BTreeMap<String, TextureFormat>,
) -> String {
    readback_formats
        .iter()
        .flat_map(|(name, format)| {
            let mut files = vec![format!("\"attachment-{name}.png\"")];
            if *format != TextureFormat::Rgba8Unorm {
                files.push(format!("\"attachment-{name}.raw\""));
            }
            files
        })
        .collect::<Vec<_>>()
        .join(",")
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct GameplayAttachmentCaptureRequest {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) correlation_id: u64,
    pub(in crate::render::worldrender) deterministic_rendered_frame_index: u64,
    pub(in crate::render::worldrender) source_selected_capture: bool,
    pub(in crate::render::worldrender) source_selected_pending: bool,
    pub(in crate::render::worldrender) required_entity_mesh: bool,
}

impl GameplayAttachmentCaptureRequest {
    pub(in crate::render::worldrender) fn read(path: &Path) -> GalResult<Self> {
        let contents = std::fs::read_to_string(path).map_err(|error| {
            GalError::invalid_argument(format!(
                "whole-frame attachment capture request {} is unreadable: {error}",
                path.display()
            ))
        })?;
        let mut frame_id = None;
        let mut correlation_id = None;
        let mut deterministic_rendered_frame_index = None;
        let mut source_selected_capture = false;
        let mut source_selected_pending = false;
        let mut required_entity_mesh = false;
        for line in contents.lines() {
            let Some((key, value)) = line.split_once('=') else {
                continue;
            };
            match key.trim() {
                "source_selected_capture" => {
                    source_selected_capture = parse_capture_request_bool(path, key, value)?;
                    continue;
                }
                "source_selected_pending" => {
                    source_selected_pending = parse_capture_request_bool(path, key, value)?;
                    continue;
                }
                "required_entity_mesh" => {
                    required_entity_mesh = parse_capture_request_bool(path, key, value)?;
                    continue;
                }
                _ => {}
            }
            let value = value.trim().parse::<u64>().map_err(|error| {
                GalError::invalid_argument(format!(
                    "whole-frame attachment capture request {} has invalid {key}: {error}",
                    path.display()
                ))
            })?;
            match key.trim() {
                "gameplay_frame_id" => frame_id = Some(value),
                "correlation_id" => correlation_id = Some(value),
                "deterministic_rendered_frame_index" => {
                    deterministic_rendered_frame_index = Some(value)
                }
                _ => {}
            }
        }
        let request = Self {
            frame_id: frame_id.ok_or_else(|| {
                GalError::invalid_argument("whole-frame attachment capture request is missing gameplay_frame_id")
            })?,
            correlation_id: correlation_id.ok_or_else(|| {
                GalError::invalid_argument("whole-frame attachment capture request is missing correlation_id")
            })?,
            deterministic_rendered_frame_index: deterministic_rendered_frame_index.ok_or_else(|| {
                GalError::invalid_argument("whole-frame attachment capture request is missing deterministic_rendered_frame_index")
            })?,
            source_selected_capture,
            source_selected_pending,
            required_entity_mesh,
        };
        if request.frame_id == 0
            || request.correlation_id == 0
            || request.deterministic_rendered_frame_index == 0
        {
            return Err(GalError::invalid_argument(
                "whole-frame attachment capture request identities must be non-zero",
            ));
        }
        if request.source_selected_pending && !request.source_selected_capture {
            return Err(GalError::invalid_argument(
                "whole-frame attachment capture request cannot be source-selected pending without source-selected capture",
            ));
        }
        Ok(request)
    }

    pub(in crate::render::worldrender) fn promote_to_source_frame(self, path: &Path, frame: &WorldPrimitiveFrame) -> GalResult<Self> {
        let frame_delta = frame.frame_id.checked_sub(self.frame_id).ok_or_else(|| {
            GalError::invalid_argument(
                "selected-source capture request belongs to a future gameplay frame",
            )
        })?;
        if frame_delta > 8 {
            return Err(GalError::invalid_argument(format!(
                "selected-source capture request waited too long for source admission: requested frame {}, source frame {}",
                self.frame_id, frame.frame_id
            )));
        }
        let deterministic_rendered_frame_index = self
            .deterministic_rendered_frame_index
            .checked_add(frame_delta)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected-source capture deterministic render index overflows",
                )
            })?;
        let promoted = Self {
            frame_id: frame.frame_id,
            correlation_id: frame.correlation_id,
            deterministic_rendered_frame_index,
            source_selected_capture: true,
            source_selected_pending: false,
            required_entity_mesh: self.required_entity_mesh,
        };
        promoted.write_atomic(path)?;
        Ok(promoted)
    }

    pub(in crate::render::worldrender) fn write_atomic(self, path: &Path) -> GalResult<()> {
        let parent = path.parent().ok_or_else(|| {
            GalError::invalid_argument(
                "whole-frame attachment capture request has no parent directory",
            )
        })?;
        std::fs::create_dir_all(parent).map_err(|error| {
            GalError::backend(format!(
                "failed to create whole-frame attachment request directory {}: {error}",
                parent.display()
            ))
        })?;
        let temporary = path.with_extension("properties.tmp");
        std::fs::write(
            &temporary,
            format!(
                "gameplay_frame_id={}\ncorrelation_id={}\ndeterministic_rendered_frame_index={}\nsource_selected_capture={}\nsource_selected_pending={}\nrequired_entity_mesh={}\n",
                self.frame_id,
                self.correlation_id,
                self.deterministic_rendered_frame_index,
                u8::from(self.source_selected_capture),
                u8::from(self.source_selected_pending),
                u8::from(self.required_entity_mesh),
            ),
        )
        .map_err(|error| {
            GalError::backend(format!(
                "failed to write pending selected-source attachment request {}: {error}",
                temporary.display()
            ))
        })?;
        std::fs::rename(&temporary, path).map_err(|error| {
            GalError::backend(format!(
                "failed to promote selected-source attachment request {}: {error}",
                path.display()
            ))
        })
    }
}

pub(in crate::render::worldrender) fn gameplay_attachment_restore_state(
    name: &str,
    has_source_presented_capture: bool,
) -> TextureUsageState {
    if name == "final_output" && has_source_presented_capture {
        TextureUsageState::ColorAttachment
    } else {
        TextureUsageState::ShaderRead
    }
}

pub(in crate::render::worldrender) fn parse_capture_request_bool(path: &Path, key: &str, value: &str) -> GalResult<bool> {
    match value.trim() {
        "1" | "true" | "TRUE" => Ok(true),
        "0" | "false" | "FALSE" => Ok(false),
        value => Err(GalError::invalid_argument(format!(
            "whole-frame attachment capture request {} has invalid {key}={value}",
            path.display()
        ))),
    }
}

pub(in crate::render::worldrender) fn attachment_evidence_json_for_format(
    width: u32,
    height: u32,
    name: &str,
    format: TextureFormat,
    bytes: &[u8],
) -> String {
    if name == "shadow_depth" || name == "main_depth" {
        let mut finite = 0usize;
        let mut less_than_clear = 0usize;
        let mut min_value = 1.0f32;
        let mut max_value = 0.0f32;
        for chunk in bytes.chunks_exact(4) {
            let value = f32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
            if value.is_finite() {
                finite += 1;
                min_value = min_value.min(value);
                max_value = max_value.max(value);
                if value < 0.999 {
                    less_than_clear += 1;
                }
            }
        }
        return format!(
            "{{\"kind\":\"depth\",\"width\":{},\"height\":{},\"finite_samples\":{},\"less_than_clear\":{},\"min\":{:.6},\"max\":{:.6}}}",
            width, height, finite, less_than_clear, min_value, max_value
        );
    }
    let rgba = selected_source_preview_rgba(format, bytes);
    let mut nonzero_alpha = 0usize;
    let mut nonblack_rgb = 0usize;
    let mut unique = std::collections::BTreeSet::new();
    for px in rgba.chunks_exact(4) {
        if px[3] != 0 {
            nonzero_alpha += 1;
        }
        if px[0] != 0 || px[1] != 0 || px[2] != 0 {
            nonblack_rgb += 1;
        }
        if unique.len() < 512 {
            unique.insert([px[0], px[1], px[2], px[3]]);
        }
    }
    format!(
        "{{\"kind\":\"color\",\"format\":\"{:?}\",\"width\":{},\"height\":{},\"nonzero_alpha\":{},\"nonblack_rgb\":{},\"sampled_unique_rgba\":{}}}",
        format,
        width,
        height,
        nonzero_alpha,
        nonblack_rgb,
        unique.len()
    )
}

pub(in crate::render::worldrender) fn depth_attachment_to_grayscale_rgba(bytes: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(bytes.len());
    for chunk in bytes.chunks_exact(4) {
        let value = f32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
        let normalized = if value.is_finite() {
            (1.0 - value.clamp(0.0, 1.0)) * 255.0
        } else {
            0.0
        };
        let gray = normalized.round().clamp(0.0, 255.0) as u8;
        out.extend_from_slice(&[gray, gray, gray, 255]);
    }
    out
}

pub(in crate::render::worldrender) fn write_rgba_png(path: &Path, width: u32, height: u32, pixels: &[u8]) -> GalResult<()> {
    let file = std::fs::File::create(path).map_err(|error| {
        GalError::backend(format!("failed to create PNG {}: {error}", path.display()))
    })?;
    let mut encoder = png::Encoder::new(file, width, height);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    let mut writer = encoder
        .write_header()
        .map_err(|error| GalError::backend(format!("failed to write PNG header: {error}")))?;
    writer
        .write_image_data(pixels)
        .map_err(|error| GalError::backend(format!("failed to write PNG pixels: {error}")))
}

/// Some backends read framebuffers back bottom row first; others already
/// return the top row used by Java GUI semantics and final-output
/// diagnostics. PNG artifacts are top-origin; raw readback files
/// intentionally remain untouched.
pub(in crate::render::worldrender) fn diagnostic_readback_rows_bottom_up(conventions: ShaderConventions) -> bool {
    conventions.readback_rows_bottom_up
}

pub(in crate::render::worldrender) fn flip_rgba_rows_in_place(pixels: &mut [u8], width: u32, height: u32) -> GalResult<()> {
    let row_bytes = usize::try_from(width)
        .ok()
        .and_then(|width| width.checked_mul(4))
        .ok_or_else(|| GalError::invalid_argument("RGBA diagnostic row size overflows usize"))?;
    let rows = usize::try_from(height)
        .map_err(|_| GalError::invalid_argument("RGBA diagnostic height exceeds usize"))?;
    let expected = row_bytes
        .checked_mul(rows)
        .ok_or_else(|| GalError::invalid_argument("RGBA diagnostic size overflows usize"))?;
    if pixels.len() != expected {
        return Err(GalError::invalid_argument(format!(
            "RGBA diagnostic has {} bytes but {}x{} requires {expected}",
            pixels.len(),
            width,
            height
        )));
    }
    for top in 0..rows / 2 {
        let bottom = rows - 1 - top;
        for offset in 0..row_bytes {
            pixels.swap(top * row_bytes + offset, bottom * row_bytes + offset);
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn sampled_texture_bytes(asset: &WorldMaterialTextureAsset) -> GalResult<Vec<u8>> {
    let expected = usize::try_from(asset.width)
        .ok()
        .and_then(|width| usize::try_from(asset.height).ok()?.checked_mul(width))
        .and_then(|pixels| pixels.checked_mul(4))
        .ok_or_else(|| GalError::invalid_argument("sampled texture dimensions overflow"))?;
    if asset.rgba.len() != expected {
        return Err(GalError::invalid_argument(format!(
            "sampled texture payload has {} bytes but {}x{} requires {expected}",
            asset.rgba.len(),
            asset.width,
            asset.height
        )));
    }
    let mut rgba = asset.rgba.clone();
    if asset.coordinate_origin == WorldMeshTextureCoordinateOrigin::MinecraftTopLeft {
        flip_rgba_rows_in_place(&mut rgba, asset.width, asset.height)?;
    }
    Ok(rgba)
}

pub(in crate::render::worldrender) fn selected_source_preview_rgba(format: TextureFormat, bytes: &[u8]) -> Vec<u8> {
    match format {
        TextureFormat::Rgba8Unorm => bytes.to_vec(),
        TextureFormat::Bgra8Unorm => bytes
            .chunks_exact(4)
            .flat_map(|pixel| [pixel[2], pixel[1], pixel[0], pixel[3]])
            .collect(),
        // Diagnostic previews normalize signed channels around zero. The
        // underlying readback remains byte-exact; this only prevents valid
        // normal attachments from being rendered as a misleading black PNG.
        TextureFormat::Rgba8Snorm => bytes
            .iter()
            .map(|&value| (i8::from_ne_bytes([value]) as i16 + 128) as u8)
            .collect(),
        TextureFormat::Rgba16Float => bytes
            .chunks_exact(8)
            .flat_map(|pixel| {
                [0, 2, 4, 6].map(|offset| {
                    let value = half_to_f32(u16::from_le_bytes([pixel[offset], pixel[offset + 1]]));
                    (value.clamp(0.0, 1.0) * 255.0).round() as u8
                })
            })
            .collect(),
        TextureFormat::R11fG11fB10f => bytes
            .chunks_exact(4)
            .flat_map(|pixel| {
                let packed = u32::from_le_bytes([pixel[0], pixel[1], pixel[2], pixel[3]]);
                [
                    unsigned_packed_float_to_u8(packed & 0x07ff, 6),
                    unsigned_packed_float_to_u8((packed >> 11) & 0x07ff, 6),
                    unsigned_packed_float_to_u8((packed >> 22) & 0x03ff, 5),
                    255,
                ]
            })
            .collect(),
        TextureFormat::Depth32Float => bytes
            .chunks_exact(4)
            .flat_map(|pixel| {
                let depth =
                    f32::from_le_bytes([pixel[0], pixel[1], pixel[2], pixel[3]]).clamp(0.0, 1.0);
                let value = (depth * 255.0).round() as u8;
                [value, value, value, 255]
            })
            .collect(),
        _ => vec![0; bytes.len() / format.copy_bytes_per_texel().unwrap_or(1) as usize * 4],
    }
}

/// `R11F_G11F_B10F` stores unsigned floating-point channels with a shared
/// five-bit exponent shape and six- or five-bit fraction. This decoder is
/// deliberately diagnostic-only: source execution and backend lowering keep
/// the packed GPU representation untouched.
pub(in crate::render::worldrender) fn unsigned_packed_float_to_u8(bits: u32, fraction_bits: u32) -> u8 {
    let exponent = (bits >> fraction_bits) & 0x1f;
    let fraction = bits & ((1 << fraction_bits) - 1);
    let value = match exponent {
        0 => (fraction as f32) * 2.0_f32.powi(-14 - fraction_bits as i32),
        31 => {
            if fraction == 0 {
                f32::INFINITY
            } else {
                f32::NAN
            }
        }
        _ => {
            (1.0 + (fraction as f32) / (1_u32 << fraction_bits) as f32)
                * 2.0_f32.powi(exponent as i32 - 15)
        }
    };
    (if value.is_finite() { value } else { 0.0 }.clamp(0.0, 1.0) * 255.0).round() as u8
}

pub(in crate::render::worldrender) fn half_to_f32(bits: u16) -> f32 {
    let sign = u32::from(bits >> 15) << 31;
    let exponent = u32::from((bits >> 10) & 0x1f);
    let fraction = u32::from(bits & 0x03ff);
    let value = match exponent {
        0 if fraction == 0 => sign,
        0 => {
            let mut fraction = fraction;
            let mut exponent = 113_u32;
            while fraction & 0x0400 == 0 {
                fraction <<= 1;
                exponent -= 1;
            }
            sign | (exponent << 23) | ((fraction & 0x03ff) << 13)
        }
        31 => sign | 0x7f80_0000 | (fraction << 13),
        _ => sign | ((exponent + 112) << 23) | (fraction << 13),
    };
    f32::from_bits(value)
}
