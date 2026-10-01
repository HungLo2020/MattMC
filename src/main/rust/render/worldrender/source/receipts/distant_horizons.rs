//! Distant Horizons material, stream, channel and transform receipts with their dedup keys.

use super::*;

impl WorldPrimitiveFrontend {
    /// Records the actual selected-source DH material contract for one
    /// diagnostic frame. The selected pack program may legally consume the
    /// reduced DH stream. Provenance-resolved atlas ranges are executed only
    /// when that program explicitly declares an atlas-backed material input;
    /// both facts are reported so a capture cannot mistake prepared atlas data
    /// for an executed draw.
    pub(crate) fn write_selected_source_distant_horizons_material_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        opaque_program: &LoweredDistantHorizonsSourceProgram,
        late_translucent_program: Option<&LoweredDistantHorizonsSourceProgram>,
        opaque_draw_count: usize,
        opaque_index_count: u64,
        transparent_draw_count: usize,
        transparent_index_count: u64,
        water_draw_count: usize,
        water_index_count: u64,
        water_origins: &[[i32; 3]],
        late_translucent_draw_count: usize,
        late_translucent_index_count: u64,
        exact_atlas_draw_count: usize,
        exact_atlas_index_count: u64,
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                std::env::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let bindings = opaque_program
            .opaque_resource_bindings
            .bindings()
            .iter()
            .map(|binding| {
                format!(
                    "{{\"source_name\":\"{}\",\"semantic_role\":\"{}\",\"kind\":\"{:?}\"}}",
                    json_escape(binding.resource_name()),
                    json_escape(binding.role().semantic_name()),
                    binding.kind(),
                )
            })
            .collect::<Vec<_>>();
        let atlas_bound = opaque_program
            .opaque_resource_bindings
            .bindings()
            .iter()
            .any(|binding| matches!(binding.role(), TerrainSourceResourceRole::MaterialAtlas));
        let exact_atlas_segments_available = self
            .lod_textured_gpu_column_assets
            .values()
            .map(|asset| asset.segments.len() as u64)
            .sum::<u64>();
        let texture_identity_contract = if exact_atlas_draw_count > 0 && atlas_bound {
            "mixed-exact-atlas-and-pack-atlas"
        } else if exact_atlas_draw_count > 0 {
            "mixed-exact-atlas-and-reduced-color"
        } else if atlas_bound {
            "atlas-backed"
        } else {
            "reduced-color-material-category"
        };
        // Audit-only: prove a requested DH fragment probe reached the exact
        // lowered program about to be compiled, rather than a stale process.
        let fragment_probe = opaque_program
            .fragment
            .source
            .split("selected-source DH diagnostic probe: ")
            .nth(1)
            .and_then(|suffix| suffix.split_whitespace().next())
            .unwrap_or("none");
        let path = Path::new(&dir).join(format!(
            "world-lod-selected-source-material-contract-frame-{}.json",
            frame.frame_id
        ));
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                path,
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"world_generation\":{},\"program\":\"{}\",",
                        "\"pass_kind\":\"{:?}\",\"late_translucent_program\":{},",
                        "\"late_translucent_pass_kind\":{},\"vertex_stride\":{},",
                        "\"texture_identity_contract\":\"{}\",\"material_atlas_bound\":{},",
                        "\"opaque_draw_count\":{},\"opaque_index_count\":{},",
                        "\"exact_atlas_draw_count\":{},\"exact_atlas_index_count\":{},",
                        "\"transparent_draw_count\":{},\"transparent_index_count\":{},",
                        "\"water_draw_count\":{},\"water_index_count\":{},",
                        "\"water_origins\":[{}],",
                        "\"late_translucent_draw_count\":{},\"late_translucent_index_count\":{},",
                        "\"exact_atlas_segments_available\":{},",
                        "\"fragment_probe\":\"{}\",",
                        "\"resource_bindings\":[{}]}}"
                    ),
                    frame.frame_id,
                    frame.shader_environment.world_generation,
                    json_escape(opaque_program.identity.as_str()),
                    opaque_program.pass_kind,
                    late_translucent_program
                        .map(|program| format!("\"{}\"", json_escape(program.identity.as_str())))
                        .unwrap_or_else(|| "null".to_string()),
                    late_translucent_program
                        .map(|program| format!("\"{:?}\"", program.pass_kind))
                        .unwrap_or_else(|| "null".to_string()),
                    opaque_program.execution_interface.vertex_stride,
                    texture_identity_contract,
                    atlas_bound,
                    opaque_draw_count,
                    opaque_index_count,
                    exact_atlas_draw_count,
                    exact_atlas_index_count,
                    transparent_draw_count,
                    transparent_index_count,
                    water_draw_count,
                    water_index_count,
                    water_origins
                        .iter()
                        .take(16)
                        .map(|origin| format!("[{},{},{}]", origin[0], origin[1], origin[2]))
                        .collect::<Vec<_>>()
                        .join(","),
                    late_translucent_draw_count,
                    late_translucent_index_count,
                    exact_atlas_segments_available,
                    json_escape(fragment_probe),
                    bindings.join(","),
                ),
            );
        }
    }

    /// Captures a bounded decode of the *actual* compact DH stream selected
    /// for a source-program frame. This is deliberately emitted before the
    /// private source pass expands it into commands, so an incorrect color,
    /// light, material category, or normal cannot be mistaken for an atlas
    /// lookup problem later in the shader graph. It is audit-only and never
    /// participates in route selection or rendering.
    pub(crate) fn write_selected_source_distant_horizons_stream_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        program: &LoweredDistantHorizonsSourceProgram,
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                std::env::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };

        const SAMPLE_LIMIT: usize = 48;
        let mut samples = Vec::new();
        let mut sampled_categories = BTreeSet::new();
        let mut seen_segments = BTreeSet::new();
        for instance in &frame.lod_instances {
            let segment_key = (
                instance.column_key,
                instance.column_generation,
                instance.layer,
                instance.segment_index,
            );
            if !seen_segments.insert(segment_key) {
                continue;
            }
            let Some(column) = self.lod_gpu_column_assets.get(&instance.column_key) else {
                continue;
            };
            if column.column_generation != instance.column_generation {
                continue;
            }
            let Some(segment) = column.segments.get(instance.segment_index as usize) else {
                continue;
            };
            if segment.layer != instance.layer
                || segment.vertex_layout_version != lod::WORLD_LOD_GPU_VERTEX_LAYOUT_V2
                || segment.vertex_bytes.len() % lod::WORLD_LOD_GPU_VERTEX_BYTES != 0
            {
                continue;
            }
            for (vertex_index, vertex) in segment
                .vertex_bytes
                .chunks_exact(lod::WORLD_LOD_GPU_VERTEX_BYTES)
                .enumerate()
            {
                let Some(decoded) = lod::decode_world_lod_gpu_vertex(vertex) else {
                    continue;
                };
                let material = lod::material_category_id(decoded.material);
                // Keep one representative for every coarse DH category, then
                // retain a few additional vertices only while the receipt is
                // still small. This avoids a single far column hiding every
                // other semantic material in a capture.
                if samples.len() >= SAMPLE_LIMIT
                    || (!sampled_categories.insert(material) && samples.len() >= 16)
                {
                    continue;
                }
                let local = decoded.local_position;
                let micro = decoded.micro_offset;
                let color = decoded
                    .color_rgba
                    .map(|value| (value * 255.0).round() as u8);
                let sky_light = decoded.sky_light;
                let block_light = decoded.block_light;
                let normal = lod::face_normal_id(decoded.normal);
                samples.push(format!(
                    concat!(
                        "{{\"columnKey\":{},\"columnGeneration\":{},\"layer\":{},\"segment\":{},\"vertex\":{},",
                        "\"local\":[{:.3},{:.3},{:.3}],\"micro\":[{:.3},{:.3},{:.3}],",
                        "\"world\":[{:.3},{:.3},{:.3}],\"rgba\":[{},{},{},{}],",
                        "\"skyLight\":{},\"blockLight\":{},\"materialCategory\":{},\"normalIndex\":{}}}"
                    ),
                    instance.column_key,
                    instance.column_generation,
                    instance.layer,
                    instance.segment_index,
                    vertex_index,
                    local[0],
                    local[1],
                    local[2],
                    micro[0],
                    micro[1],
                    micro[2],
                    local[0] + micro[0] + column.origin[0] as f32,
                    local[1] + micro[1] + column.origin[1] as f32,
                    local[2] + micro[2] + column.origin[2] as f32,
                    color[0],
                    color[1],
                    color[2],
                    color[3],
                    sky_light,
                    block_light,
                    material,
                    normal,
                ));
                if samples.len() >= SAMPLE_LIMIT {
                    break;
                }
            }
            if samples.len() >= SAMPLE_LIMIT {
                break;
            }
        }
        let path = Path::new(&dir).join(format!(
            "world-lod-selected-source-stream-frame-{}.json",
            frame.frame_id
        ));
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                path,
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"world_generation\":{},\"program\":\"{}\",",
                        "\"vertex_stride\":{},\"contract\":\"{:?}\",\"samples\":[{}]}}"
                    ),
                    frame.frame_id,
                    frame.shader_environment.world_generation,
                    json_escape(program.identity.as_str()),
                    lod::WORLD_LOD_GPU_VERTEX_BYTES,
                    program.execution_interface.material_identity_contract,
                    samples.join(","),
                ),
            );
        }
    }

    /// Records the compact DH channels immediately before Rust expands them
    /// into draw commands. This is a bounded graphics-audit receipt only: it
    /// never runs on ordinary launches and cannot affect route admission or
    /// resource ownership. It separates source-stream colors/light values
    /// from shader or lightmap amplification when a capture shows saturated
    /// LOD planes.
    pub(crate) fn write_distant_horizons_channel_receipt(
        &mut self,
        frame: &WorldPrimitiveFrame,
        visible: &[lod::WorldLodGpuDraw],
    ) {
        if !matches!(
            std::env::var("MATTMC_GRAPHICS_AUDIT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            return;
        }
        let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let directory = Path::new(&dir);
        let mut receipt_key = self.dh_audit_frame_key(frame);
        for draw in visible {
            for value in [
                draw.column_key,
                draw.column_generation,
                u64::from(draw.layer),
                u64::from(draw.segment_index),
                u64::from(draw.order),
                u64::from(draw.index_count),
            ] {
                receipt_key = Self::fnv_update_u64(receipt_key, value);
            }
        }
        if !Self::audit_receipt_is_new(&mut self.last_dh_channel_receipt, directory, receipt_key) {
            return;
        }
        let private_color_debug = matches!(
            std::env::var("MATTMC_CAPTURE_DH_PRIVATE_COLOR_DEBUG").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let private_depth_debug = matches!(
            std::env::var("MATTMC_CAPTURE_DH_PRIVATE_DEPTH_DEBUG").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let private_isolate_vanilla = matches!(
            std::env::var("MATTMC_CAPTURE_DH_PRIVATE_ISOLATE_VANILLA").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let private_direct_route_eligible = frame.lod_render_frame.dh_fog_parameters[16] >= 0.5;
        let camera = frame.lod_render_frame.camera_world_position;
        let combined_matrix = frame.lod_render_frame.combined_matrix;
        let mut rows = Vec::new();
        for draw in visible {
            let Some(column) = self.lod_gpu_column_assets.get(&draw.column_key) else {
                continue;
            };
            if column.column_generation != draw.column_generation {
                continue;
            }
            let Some(segment) = column.segments.get(draw.segment_index as usize) else {
                continue;
            };
            if segment.layer != draw.layer
                || segment.vertex_layout_version != lod::WORLD_LOD_GPU_VERTEX_LAYOUT_V2
                || segment.vertex_bytes.len() % lod::WORLD_LOD_GPU_VERTEX_BYTES != 0
            {
                continue;
            }
            let source_semantic_hash = self
                .lod_column_assets
                .get(&draw.column_key)
                .and_then(|source_column| {
                    let source_segment = source_column.segments.get(draw.segment_index as usize)?;
                    (source_segment.layer == draw.layer)
                        .then(|| Self::semantic_source_hash(source_segment))
                })
                .map(|hash| format!("\"{:016x}\"", hash))
                .unwrap_or_else(|| "null".to_string());
            let mut vertex_count = 0u64;
            let mut white_count = 0u64;
            let mut alpha_zero_count = 0u64;
            let mut alpha_full_count = 0u64;
            let mut color_min = [u8::MAX; 3];
            let mut color_max = [0u8; 3];
            let mut light_min = [u8::MAX; 2];
            let mut light_max = [0u8; 2];
            let mut local_min = [f32::INFINITY; 3];
            let mut local_max = [f32::NEG_INFINITY; 3];
            let mut projected_vertices = 0u64;
            let mut clip_w_positive = 0u64;
            let mut ndc_xy_in_expanded_view = 0u64;
            let mut ndc_min = [f32::INFINITY; 3];
            let mut ndc_max = [f32::NEG_INFINITY; 3];
            for vertex in segment
                .vertex_bytes
                .chunks_exact(lod::WORLD_LOD_GPU_VERTEX_BYTES)
            {
                let Some(decoded) = lod::decode_world_lod_gpu_vertex(vertex) else {
                    continue;
                };
                vertex_count += 1;
                let local = decoded.local_position;
                for axis in 0..3 {
                    local_min[axis] = local_min[axis].min(local[axis]);
                    local_max[axis] = local_max[axis].max(local[axis]);
                }
                let color = decoded
                    .color_rgba
                    .map(|value| (value * 255.0).round() as u8);
                for channel in 0..3 {
                    color_min[channel] = color_min[channel].min(color[channel]);
                    color_max[channel] = color_max[channel].max(color[channel]);
                }
                if color[0] == u8::MAX && color[1] == u8::MAX && color[2] == u8::MAX {
                    white_count += 1;
                }
                alpha_zero_count += u64::from(color[3] == 0);
                alpha_full_count += u64::from(color[3] == u8::MAX);
                let light = [decoded.sky_light, decoded.block_light];
                for channel in 0..2 {
                    light_min[channel] = light_min[channel].min(light[channel]);
                    light_max[channel] = light_max[channel].max(light[channel]);
                }
                // Project the complete expanded stream in this audit-only
                // receipt. The bounded transform receipt samples one vertex
                // per segment; these bounds reveal whether a whole segment is
                // clipped before depth or composition can affect it.
                let micro = decoded.micro_offset;
                let model_offset = [
                    draw.origin[0] as f32 - camera[0],
                    draw.origin[1] as f32 - camera[1],
                    draw.origin[2] as f32 - camera[2],
                ];
                let clip = matrix4_column_major_transform_point(
                    combined_matrix,
                    [
                        local[0] + micro[0] + model_offset[0],
                        local[1] + model_offset[1],
                        local[2] + micro[2] + model_offset[2],
                        1.0,
                    ],
                );
                if clip.iter().all(|value| value.is_finite()) && clip[3].abs() > f32::EPSILON {
                    projected_vertices += 1;
                    if clip[3] > 0.0 {
                        clip_w_positive += 1;
                    }
                    let ndc = [clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]];
                    for axis in 0..3 {
                        ndc_min[axis] = ndc_min[axis].min(ndc[axis]);
                        ndc_max[axis] = ndc_max[axis].max(ndc[axis]);
                    }
                    if ndc[0] >= -1.0 && ndc[0] <= 1.0 && ndc[1] >= -1.0 && ndc[1] <= 1.0 {
                        ndc_xy_in_expanded_view += 1;
                    }
                }
            }
            let ndc_min_json = if projected_vertices > 0 {
                format!("[{:.6},{:.6},{:.6}]", ndc_min[0], ndc_min[1], ndc_min[2])
            } else {
                "null".to_string()
            };
            let ndc_max_json = if projected_vertices > 0 {
                format!("[{:.6},{:.6},{:.6}]", ndc_max[0], ndc_max[1], ndc_max[2])
            } else {
                "null".to_string()
            };
            rows.push(format!(
                concat!(
                    "{{\"columnKey\":{},\"columnGeneration\":{},\"layer\":{},",
                    "\"segment\":{},\"vertices\":{},\"quadCount\":{},\"indexCount\":{},",
                    "\"projectedVertices\":{},\"clipWPositive\":{},\"ndcXYInExpandedView\":{},",
                    "\"ndcMin\":{},\"ndcMax\":{},\"whiteRgb\":{},",
                    "\"sourceSemanticHash\":{},",
                    "\"alphaZero\":{},\"alphaFull\":{},\"rgbMin\":[{},{},{}],",
                    "\"rgbMax\":[{},{},{}],\"lightMin\":[{},{}],",
                    "\"lightMax\":[{},{}],\"localMin\":[{:.3},{:.3},{:.3}],",
                    "\"localMax\":[{:.3},{:.3},{:.3}]}}"
                ),
                draw.column_key,
                draw.column_generation,
                draw.layer,
                draw.segment_index,
                vertex_count,
                vertex_count / 4,
                (vertex_count / 4) * 6,
                projected_vertices,
                clip_w_positive,
                ndc_xy_in_expanded_view,
                ndc_min_json,
                ndc_max_json,
                white_count,
                source_semantic_hash,
                alpha_zero_count,
                alpha_full_count,
                color_min[0],
                color_min[1],
                color_min[2],
                color_max[0],
                color_max[1],
                color_max[2],
                light_min[0],
                light_min[1],
                light_max[0],
                light_max[1],
                local_min[0],
                local_min[1],
                local_min[2],
                local_max[0],
                local_max[1],
                local_max[2],
            ));
        }
        let path = Path::new(&dir).join(format!("world-lod-channel-frame-{}.json", frame.frame_id));
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                path,
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"privateColorDebugEnv\":{},",
                        "\"privateDepthDebugEnv\":{},\"privateIsolateVanillaEnv\":{},",
                        "\"privateDirectRouteEligible\":{},",
                        "\"draws\":[{}]}}"
                    ),
                    frame.frame_id,
                    private_color_debug,
                    private_depth_debug,
                    private_isolate_vanilla,
                    private_direct_route_eligible,
                    rows.join(",")
                ),
            );
        }
    }

    pub(crate) fn fnv_update_u16(hash: u64, value: u16) -> u64 {
        let mut hash = hash;
        for byte in value.to_le_bytes() {
            hash = Self::fnv_update_byte(hash, byte);
        }
        hash
    }

    pub(crate) fn fnv_update_byte(hash: u64, value: u8) -> u64 {
        (hash ^ u64::from(value)).wrapping_mul(0x100000001b3)
    }

    /// Returns a stable identity for the copied DH state that the audit
    /// receipts inspect. Frame counters and texture bytes are deliberately
    /// excluded: a static camera and unchanged immutable columns should not
    /// trigger a full scan of every LOD vertex or atlas merely because another
    /// frame was presented. Resource generations cover replacement boundaries;
    /// the receipts themselves contain no atlas-pixel sample.
    pub(crate) fn dh_audit_frame_key(&self, frame: &WorldPrimitiveFrame) -> u64 {
        let mut hash = 0xcbf29ce484222325u64;
        for value in [
            self.lod_asset_generation,
            self.mesh_asset_generation,
            self.material_asset_generation,
            frame.shader_environment.world_generation,
            frame.lod_instances.len() as u64,
        ] {
            hash = Self::fnv_update_u64(hash, value);
        }
        for matrix in [
            &frame.lod_render_frame.combined_matrix,
            &frame.lod_render_frame.projection_matrix,
            &frame.lod_render_frame.model_view_matrix,
        ] {
            for value in *matrix {
                hash = Self::fnv_update_u32(hash, value.to_bits());
            }
        }
        for value in frame
            .lod_render_frame
            .camera_world_position
            .into_iter()
            .chain([
                frame.lod_render_frame.clip_distance,
                frame.lod_render_frame.micro_offset,
                frame.lod_render_frame.noise_intensity,
                frame.lod_render_frame.earth_radius,
            ])
        {
            hash = Self::fnv_update_u32(hash, value.to_bits());
        }
        for value in frame.lod_render_frame.dh_fog_parameters {
            hash = Self::fnv_update_u32(hash, value.to_bits());
        }
        for instance in &frame.lod_instances {
            for value in [
                instance.column_key,
                instance.column_generation,
                u64::from(instance.layer),
                u64::from(instance.segment_index),
                u64::from(instance.order),
            ] {
                hash = Self::fnv_update_u64(hash, value);
            }
        }
        hash
    }

    pub(crate) fn fnv_update_u32(hash: u64, value: u32) -> u64 {
        let mut hash = hash;
        for byte in value.to_le_bytes() {
            hash = Self::fnv_update_byte(hash, byte);
        }
        hash
    }

    pub(crate) fn fnv_update_u64(hash: u64, value: u64) -> u64 {
        let mut hash = hash;
        for byte in value.to_le_bytes() {
            hash = Self::fnv_update_byte(hash, byte);
        }
        hash
    }

    pub(crate) fn audit_receipt_is_new(slot: &mut Option<(PathBuf, u64)>, directory: &Path, key: u64) -> bool {
        let identity = (directory.to_path_buf(), key);
        if slot.as_ref() == Some(&identity) {
            return false;
        }
        *slot = Some(identity);
        true
    }

    pub(crate) fn audit_transform_receipt_is_new(
        slot: &mut Option<(PathBuf, u64, String)>,
        directory: &Path,
        key: u64,
        scope: &str,
    ) -> bool {
        let identity = (directory.to_path_buf(), key, scope.to_owned());
        if slot.as_ref() == Some(&identity) {
            return false;
        }
        *slot = Some(identity);
        true
    }

    /// Captures the source-program position contract for a bounded set of
    /// *executed* coarse DH draws. The selected source shader receives a
    /// camera-relative local position plus the per-column model offset, then
    /// applies DH's copied projection and model-view matrices. Recording both
    /// multiplication forms catches a semantic camera/origin fault before a
    /// screenshot is blamed on material data or later composition.
    pub(crate) fn write_distant_horizons_transform_receipt(
        &mut self,
        frame: &WorldPrimitiveFrame,
        scope: &str,
        program_identity: &str,
        draws: &[lod::WorldLodGpuDraw],
    ) {
        if !matches!(
            std::env::var("MATTMC_GRAPHICS_AUDIT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            return;
        }
        let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let directory = Path::new(&dir);
        let mut receipt_key = self.dh_audit_frame_key(frame);
        for draw in draws {
            for value in [
                draw.column_key,
                draw.column_generation,
                u64::from(draw.layer),
                u64::from(draw.segment_index),
                u64::from(draw.order),
                u64::from(draw.index_count),
            ] {
                receipt_key = Self::fnv_update_u64(receipt_key, value);
            }
        }
        receipt_key = Self::fnv_update_u64(receipt_key, fnv64_bytes(program_identity.as_bytes()));
        if !Self::audit_transform_receipt_is_new(
            &mut self.last_dh_transform_receipt,
            directory,
            receipt_key,
            scope,
        ) {
            return;
        }

        const SAMPLE_LIMIT: usize = 24;
        let camera = frame.lod_render_frame.camera_world_position;
        let combined_from_parts = matrix4_column_major_multiply(
            frame.lod_render_frame.projection_matrix,
            frame.lod_render_frame.model_view_matrix,
        );
        let mut samples = Vec::new();
        for draw in draws {
            if samples.len() >= SAMPLE_LIMIT {
                break;
            }
            let Some(column) = self.lod_gpu_column_assets.get(&draw.column_key) else {
                continue;
            };
            if column.column_generation != draw.column_generation {
                continue;
            }
            let Some(segment) = column.segments.get(draw.segment_index as usize) else {
                continue;
            };
            if segment.layer != draw.layer
                || segment.vertex_layout_version != lod::WORLD_LOD_GPU_VERTEX_LAYOUT_V2
                || segment.vertex_bytes.len() < lod::WORLD_LOD_GPU_VERTEX_BYTES
            {
                continue;
            }
            // A water surface is the one DH stream where a first-vertex-only
            // receipt cannot establish its projected orientation. Keep this
            // bounded, but retain each vertex of its first two quads.
            let sampled_vertices = if draw.layer == WORLD_LOD_LAYER_TRANSPARENT_WATER_UP {
                (segment.vertex_bytes.len() / lod::WORLD_LOD_GPU_VERTEX_BYTES).min(8)
            } else {
                1
            };
            for vertex_index in 0..sampled_vertices {
                if samples.len() >= SAMPLE_LIMIT {
                    break;
                }
                let vertex_start = vertex_index * lod::WORLD_LOD_GPU_VERTEX_BYTES;
                let vertex = &segment.vertex_bytes
                    [vertex_start..vertex_start + lod::WORLD_LOD_GPU_VERTEX_BYTES];
                let Some(decoded) = lod::decode_world_lod_gpu_vertex(vertex) else {
                    continue;
                };
                let local = decoded.local_position;
                let micro = decoded.micro_offset;
                // Iris's DH transformer decodes all micro fields but applies only
                // X/Z to the final terrain position. Preserve that exact semantic
                // contract here rather than treating the compact stream as a
                // generic three-axis offset.
                let model_offset = [
                    draw.origin[0] as f32 - camera[0],
                    draw.origin[1] as f32 - camera[1],
                    draw.origin[2] as f32 - camera[2],
                ];
                let shader_position = [
                    local[0] + micro[0] + model_offset[0],
                    local[1] + model_offset[1],
                    local[2] + micro[2] + model_offset[2],
                    1.0,
                ];
                let model_view = matrix4_column_major_transform_point(
                    frame.lod_render_frame.model_view_matrix,
                    shader_position,
                );
                let clip_from_parts = matrix4_column_major_transform_point(
                    frame.lod_render_frame.projection_matrix,
                    model_view,
                );
                let clip_from_frame = matrix4_column_major_transform_point(
                    frame.lod_render_frame.combined_matrix,
                    shader_position,
                );
                let clip_from_derived =
                    matrix4_column_major_transform_point(combined_from_parts, shader_position);
                let ndc = matrix4_ndc(clip_from_parts);
                samples.push(format!(
                concat!(
                    "{{\"columnKey\":{},\"columnGeneration\":{},\"layer\":{},\"segment\":{},\"vertexIndex\":{},\"quadIndex\":{},",
                    "\"origin\":[{},{},{}],\"camera\":[{:.3},{:.3},{:.3}],",
                    "\"local\":[{:.3},{:.3},{:.3}],\"micro\":[{:.3},{:.3},{:.3}],",
                    "\"modelOffset\":[{:.3},{:.3},{:.3}],\"shaderPosition\":[{:.3},{:.3},{:.3},{:.3}],",
                    "\"modelView\":[{:.6},{:.6},{:.6},{:.6}],\"clip\":[{:.6},{:.6},{:.6},{:.6}],",
                    "\"frameCombinedClip\":[{:.6},{:.6},{:.6},{:.6}],",
                    "\"derivedCombinedClip\":[{:.6},{:.6},{:.6},{:.6}],",
                    "\"frameVsPartsMaxAbsError\":{:.8},\"derivedVsPartsMaxAbsError\":{:.8},",
                    "\"ndc\":{} }}"
                ),
                draw.column_key,
                draw.column_generation,
                draw.layer,
                draw.segment_index,
                vertex_index,
                vertex_index / 4,
                draw.origin[0],
                draw.origin[1],
                draw.origin[2],
                camera[0],
                camera[1],
                camera[2],
                local[0],
                local[1],
                local[2],
                micro[0],
                micro[1],
                micro[2],
                model_offset[0],
                model_offset[1],
                model_offset[2],
                shader_position[0],
                shader_position[1],
                shader_position[2],
                shader_position[3],
                model_view[0],
                model_view[1],
                model_view[2],
                model_view[3],
                clip_from_parts[0],
                clip_from_parts[1],
                clip_from_parts[2],
                clip_from_parts[3],
                clip_from_frame[0],
                clip_from_frame[1],
                clip_from_frame[2],
                clip_from_frame[3],
                clip_from_derived[0],
                clip_from_derived[1],
                clip_from_derived[2],
                clip_from_derived[3],
                vector4_max_abs_difference(clip_from_frame, clip_from_parts),
                vector4_max_abs_difference(clip_from_derived, clip_from_parts),
                matrix4_ndc_json(ndc),
            ));
            }
        }
        let path = Path::new(&dir).join(format!(
            "world-lod-{}-transform-frame-{}.json",
            scope, frame.frame_id
        ));
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                path,
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"world_generation\":{},\"scope\":\"{}\",\"program\":\"{}\",",
                        "\"drawCount\":{},\"camera\":[{:.3},{:.3},{:.3}],",
                        "\"worldYOffset\":{},\"clipDistance\":{},\"microOffset\":{},",
                        "\"flags\":[{},{},{},{}],\"fogColor\":{},\"fogRanges\":{},",
                        "\"dhFogParameters\":{},",
                        "\"projectionModelViewMaxAbsError\":{:.8},\"samples\":[{}]}}"
                    ),
                    frame.frame_id,
                    frame.shader_environment.world_generation,
                    json_escape(scope),
                    json_escape(program_identity),
                    draws.len(),
                    camera[0],
                    camera[1],
                    camera[2],
                    frame.lod_render_frame.world_y_offset,
                    frame.lod_render_frame.clip_distance,
                    frame.lod_render_frame.micro_offset,
                    frame.lod_render_frame.flags,
                    frame.lod_render_frame.noise_steps,
                    frame.lod_render_frame.noise_dropoff,
                    0,
                    float_json_values(&frame.shader_environment.fog_parameter_color),
                    float_json_values(&[
                        frame.shader_environment.fog_environmental_start,
                        frame.shader_environment.fog_environmental_end,
                        frame.shader_environment.fog_render_distance_start,
                        frame.shader_environment.fog_render_distance_end,
                    ]),
                    float_json_values(&frame.lod_render_frame.dh_fog_parameters),
                    matrix4_max_abs_difference(
                        frame.lod_render_frame.combined_matrix,
                        combined_from_parts,
                    ),
                    samples.join(","),
                ),
            );
        }
    }
}
