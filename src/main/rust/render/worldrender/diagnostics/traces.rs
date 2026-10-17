//! Environment-gated traces and receipts of the built-in route.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) static STATIC_TERRAIN_APPEARANCE_TRACE_WRITTEN: AtomicBool = AtomicBool::new(false);

pub(in crate::render::worldrender) static STATIC_TERRAIN_APPEARANCE_TRACE_RECEIPTS: AtomicUsize = AtomicUsize::new(0);

pub(in crate::render::worldrender) static STATIC_TERRAIN_APPEARANCE_TRACE_DETAILS: AtomicUsize = AtomicUsize::new(0);

pub(in crate::render::worldrender) static STATIC_TERRAIN_ATLAS_TRACE_WRITTEN: AtomicBool = AtomicBool::new(false);

pub(in crate::render::worldrender) fn graphics_audit_enabled() -> bool {
    matches!(
        crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref().map(str::trim),
        Ok("1") | Ok("true") | Ok("TRUE")
    )
}

pub(in crate::render::worldrender) fn whole_frame_phase_trace(phase: &str, frame_id: u64, started: Option<std::time::Instant>) {
    if crate::core::environment::var_os("MATTMC_TRACE_WHOLE_FRAME").is_none() {
        return;
    }
    match started {
        Some(started) => crate::core::console::stderr(format_args!(
            "whole-frame.phase={} frame={} elapsed_nanos={}",
            phase,
            frame_id,
            crate::render::vulkanic::metrics::elapsed_nanos_u64(started)
        )),
        None => crate::core::console::stderr(format_args!("whole-frame.phase={} frame={} begin", phase, frame_id)),
    }
}

// Test-only, opt-in trace of one semantic mesh asset after FFI decode and before GPU upload.
// It is intentionally keyed by a semantic mesh key rather than backend resources or handles.
pub(in crate::render::worldrender) fn trace_static_terrain_appearance(
    mesh: &WorldMeshAsset,
    atlas: Option<&WorldMaterialTextureAsset>,
) {
    let Some(root) = crate::core::environment::var_os("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_DIR") else {
        return;
    };
    // A test-only receipt distinguishes a missing native environment from a
    // semantic-key mismatch without touching rendering or backend resources.
    let root = PathBuf::from(root);
    if std::fs::create_dir_all(&root).is_ok() {
        if let Some(atlas) = atlas {
            trace_static_terrain_atlas_receipt(&root, atlas);
        }
        if STATIC_TERRAIN_APPEARANCE_TRACE_RECEIPTS.fetch_add(1, Ordering::Relaxed) < 512 {
            use std::io::Write;
            if let Ok(mut file) = std::fs::OpenOptions::new()
                .create(true)
                .append(true)
                .open(root.join("static_terrain_appearance_rust_frontend_receipts.jsonl"))
            {
                let expected_key = crate::core::environment::var("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_MESH_KEY")
                    .ok()
                    .and_then(|value| u64::from_str_radix(value.trim_start_matches("0x"), 16).ok());
                let _ = writeln!(
                    file,
                    "{{\"meshKey\":\"{:016x}\",\"meshGeneration\":{},\"matchesRequestedKey\":{}}}",
                    mesh.mesh_key,
                    mesh.mesh_generation,
                    expected_key.is_some_and(|expected| expected == mesh.mesh_key),
                );
            }
        }
    }
    let expected_key = crate::core::environment::var("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_MESH_KEY")
        .ok()
        .and_then(|value| u64::from_str_radix(value.trim_start_matches("0x"), 16).ok());
    // A selected mesh remains a one-record trace.  With no selector, retain a
    // bounded JSONL inventory so one diagnostic run can discover the exact
    // mesh key instead of requiring a discovery capture and a second capture.
    let trace_all_meshes = expected_key.is_none();
    if trace_all_meshes
        && STATIC_TERRAIN_APPEARANCE_TRACE_DETAILS.fetch_add(1, Ordering::Relaxed) >= 512
    {
        return;
    }
    if (!trace_all_meshes && mesh.mesh_key != expected_key.expect("selected mesh key is present"))
        || (!trace_all_meshes
            && STATIC_TERRAIN_APPEARANCE_TRACE_WRITTEN
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err())
    {
        return;
    }
    if !trace_all_meshes {
        let _ = std::fs::write(
            root.join("static_terrain_appearance_rust_frontend_receipt.json"),
            format!(
                "{{\"schema\":\"mattmc-static-terrain-appearance-rust-receipt-v1\",\"observedMeshKey\":\"{:016x}\",\"observedMeshGeneration\":{},\"requestedMeshKey\":\"{:016x}\",\"matchesRequestedKey\":true}}\n",
                mesh.mesh_key,
                mesh.mesh_generation,
                expected_key.expect("selected mesh key is present"),
            ),
        );
    }
    let mut samples = String::new();
    for (index, vertex) in mesh.vertices.iter().take(8).enumerate() {
        if index != 0 {
            samples.push(',');
        }
        let color = argb_to_rgba(vertex.color_argb);
        let normal = unpack_normal_i8(vertex.normal_packed);
        let [block_light, sky_light] = packed_light_channels(vertex.light);
        let atlas_sample = atlas
            .map(|atlas| trace_atlas_sample(atlas, vertex.shader_atlas_uv))
            .unwrap_or_else(|| "{\"status\":\"atlas-unavailable\"}".to_string());
        samples.push_str(&format!(
            concat!(
                "{{\"vertexIndex\":{index},\"primitiveIndex\":{primitive},",
                "\"position\":[{px:.6},{py:.6},{pz:.6}],",
                "\"uv\":[{u:.6},{v:.6}],\"atlasUv\":[{au:.6},{av:.6}],",
                "\"colorRgba\":[{cr:.6},{cg:.6},{cb:.6},{ca:.6}],",
                "\"normal\":[{nx:.6},{ny:.6},{nz:.6}],",
                "\"packedLight\":\"{light:08x}\",\"blockLight\":{block:.6},\"skyLight\":{sky:.6},",
                "\"bakedLightFactor\":{baked:.6},\"shaderBlockId\":{block_id},",
                "\"shaderMaterialType\":{material_type},\"terrainMaterialBits\":{material_bits},",
                "\"atlasSampling\":{atlas_sample}}}"
            ),
            index = index,
            primitive = index / 4,
            px = vertex.position[0],
            py = vertex.position[1],
            pz = vertex.position[2],
            u = vertex.uv[0],
            v = vertex.uv[1],
            au = vertex.shader_atlas_uv[0],
            av = vertex.shader_atlas_uv[1],
            cr = color[0],
            cg = color[1],
            cb = color[2],
            ca = color[3],
            nx = normal[0],
            ny = normal[1],
            nz = normal[2],
            light = vertex.light,
            block = block_light,
            sky = sky_light,
            baked = baked_light_factor(vertex.light),
            block_id = vertex.shader_block_id,
            material_type = vertex.shader_material_type,
            material_bits = vertex.terrain_material_bits,
            atlas_sample = atlas_sample,
        ));
    }
    let sections = mesh
        .sections
        .iter()
        .map(|section| {
            format!(
                "{{\"materialId\":{},\"textureId\":{},\"materialMode\":{},\"cullPolicy\":{},\"winding\":{},\"indexOffset\":{},\"indexCount\":{}}}",
                section.material_id,
                section.texture_id,
                section.material_mode,
                section.cull_policy,
                section.winding,
                section.index_offset,
                section.index_count
            )
        })
        .collect::<Vec<_>>()
        .join(",");
    let payload = format!(
        concat!(
            "{{\"schema\":\"mattmc-static-terrain-appearance-rust-frontend-v1\",",
            "\"stage\":\"ffi-decoded-to-shader-packed\",\"meshKey\":\"{:016x}\",",
            "\"meshGeneration\":{},\"vertexLayoutVersion\":{},\"indexType\":\"{:?}\",",
            "\"vertexCount\":{},\"shaderUniformContract\":{{",
            "\"perVertex\":\"color_rgba; normal_i8; block_sky_light_for_explicit_vanilla_lightmap; atlas_uv; shader_ids\",",
            "\"perPass\":\"view;projection;light_view_projection;shadow_params;fog;color_grade\"}},",
            "\"atlasUploadConvention\":\"minecraft-atlas-uv-preserved\",",
            "\"sections\":[{}],\"samples\":[{}]}}\n"
        ),
        mesh.mesh_key,
        mesh.mesh_generation,
        mesh.vertex_layout_version,
        mesh.index_type,
        mesh.vertices.len(),
        sections,
        samples
    );
    if std::fs::create_dir_all(&root).is_ok() {
        if trace_all_meshes {
            use std::io::Write;
            if let Ok(mut file) = std::fs::OpenOptions::new()
                .create(true)
                .append(true)
                .open(root.join("static_terrain_appearance_rust_frontend.jsonl"))
            {
                let _ = file.write_all(payload.as_bytes());
            }
        } else {
            let _ = std::fs::write(
                root.join("static_terrain_appearance_rust_frontend.json"),
                payload,
            );
        }
    }
}

pub(in crate::render::worldrender) fn trace_static_terrain_atlas_receipt(root: &Path, atlas: &WorldMaterialTextureAsset) {
    if STATIC_TERRAIN_ATLAS_TRACE_WRITTEN
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return;
    }
    let hash = fnv64_bytes(&atlas.rgba);
    let _ = std::fs::write(
        root.join("static_terrain_atlas_rust_frontend_receipt.json"),
        format!(
            concat!(
                "{{\"schema\":\"mattmc-static-terrain-atlas-rust-frontend-receipt-v1\",",
                "\"stage\":\"ffi-decoded-before-gpu-upload\",",
                "\"extent\":[{},{}],\"rgbaHash\":\"{:016x}\",",
                "\"bytes\":{}}}\n"
            ),
            atlas.width,
            atlas.height,
            hash,
            atlas.rgba.len(),
        ),
    );
}

pub(in crate::render::worldrender) fn fnv64_bytes(bytes: &[u8]) -> u64 {
    let mut hash = 0xcbf29ce484222325u64;
    for byte in bytes {
        hash ^= u64::from(*byte);
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

/// Captures only immutable semantic lightmap evidence for an explicitly
/// requested terrain-appearance audit.  This is intentionally written before
/// the bytes enter a GAL upload: it proves the exact Rust-owned image content
/// without exposing a Java texture, a native handle, or backend state.
pub(in crate::render::worldrender) fn trace_builtin_terrain_lightmap_receipt(
    frame_id: u64,
    world_generation: u64,
    frame: crate::render::shaderpack::vanilla::lightmap::VanillaLightmapFrame,
    rgba: &[u8],
) {
    let Ok(root) = crate::core::environment::var("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_DIR") else {
        return;
    };
    let root = Path::new(&root);
    if std::fs::create_dir_all(root).is_err() {
        return;
    }
    let inputs = frame.inputs;
    let rgba_hex = rgba
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    let _ = std::fs::write(
        root.join("static_terrain_lightmap_rust_frontend_receipt.json"),
        format!(
            concat!(
                "{{\"schema\":\"mattmc-static-terrain-lightmap-rust-frontend-receipt-v1\",",
                "\"stage\":\"semantic-lightmap-before-gpu-upload\",",
                "\"frameId\":{},\"worldGeneration\":{},\"lightmapGeneration\":{},",
                "\"rgbaHash\":\"{:016x}\",\"bytes\":{},\"rgbaHex\":\"{}\",",
                "\"inputs\":{{\"ambientLightFactor\":{:.8},\"skyFactor\":{:.8},",
                "\"blockFactor\":{:.8},\"nightVisionFactor\":{:.8},",
                "\"darknessScale\":{:.8},\"darkenWorldFactor\":{:.8},",
                "\"brightnessFactor\":{:.8},\"skyLightColor\":[{:.8},{:.8},{:.8}],",
                "\"ambientColor\":[{:.8},{:.8},{:.8}]}}}}\n"
            ),
            frame_id,
            world_generation,
            frame.generation,
            fnv64_bytes(rgba),
            rgba.len(),
            rgba_hex,
            inputs.ambient_light_factor,
            inputs.sky_factor,
            inputs.block_factor,
            inputs.night_vision_factor,
            inputs.darkness_scale,
            inputs.darken_world_factor,
            inputs.brightness_factor,
            inputs.sky_light_color[0],
            inputs.sky_light_color[1],
            inputs.sky_light_color[2],
            inputs.ambient_color[0],
            inputs.ambient_color[1],
            inputs.ambient_color[2],
        ),
    );
}

pub(in crate::render::worldrender) fn trace_atlas_sample(atlas: &WorldMaterialTextureAsset, uv: [f32; 2]) -> String {
    let Some([x, y]) = atlas_texel_coordinates(uv, atlas.width, atlas.height) else {
        return "{\"status\":\"invalid-atlas-coordinate\"}".to_string();
    };
    let offset = ((y as usize)
        .saturating_mul(atlas.width as usize)
        .saturating_add(x as usize))
    .saturating_mul(4);
    let rgba = atlas
        .rgba
        .get(offset..offset.saturating_add(4))
        .unwrap_or(&[]);
    let [red, green, blue, alpha] = match rgba {
        [red, green, blue, alpha] => [*red, *green, *blue, *alpha],
        _ => [0, 0, 0, 0],
    };
    format!(
        "{{\"status\":\"ok\",\"extent\":[{},{}],\"shaderUvPixel\":[{},{}],\"uploadedRgba\":[{},{},{},{}]}}",
        atlas.width, atlas.height, x, y, red, green, blue, alpha
    )
}

/// Minecraft terrain UVs name the copied atlas directly. Keep the decoded
/// payload and UV rows in that same semantic coordinate system; a global V
/// inversion would point a valid block sprite at an unrelated atlas region.
pub(in crate::render::worldrender) fn atlas_texel_coordinates(uv: [f32; 2], width: u32, height: u32) -> Option<[u32; 2]> {
    if width == 0 || height == 0 || !uv.into_iter().all(f32::is_finite) {
        return None;
    }
    Some([
        (uv[0] * width as f32)
            .floor()
            .clamp(0.0, width.saturating_sub(1) as f32) as u32,
        (uv[1] * height as f32)
            .floor()
            .clamp(0.0, height.saturating_sub(1) as f32) as u32,
    ])
}

// Test-only, opt-in trace for static translucent terrain batches after frame
// instance expansion and before any GAL command or backend resource binding.
// A caller can pin a semantic mesh key; otherwise the first matching batch is
// sampled in detail while bounded projected-bounds summaries retain enough
// coverage to identify a differently transformed translucent section.
pub(in crate::render::worldrender) fn trace_static_terrain_mesh_batch(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    batches: &[MeshBatch],
    sorted_indices: &[u8],
) {
    let Some(root) = crate::core::environment::var_os("MATTMC_STATIC_TERRAIN_BATCH_TRACE_DIR") else {
        return;
    };
    let requested_mesh_key = crate::core::environment::var("MATTMC_STATIC_TERRAIN_BATCH_TRACE_MESH_KEY")
        .ok()
        .and_then(|value| u64::from_str_radix(value.trim_start_matches("0x"), 16).ok());
    let trace_all_materials = matches!(
        crate::core::environment::var("MATTMC_STATIC_TERRAIN_BATCH_TRACE_ALL_MATERIALS").as_deref(),
        Ok("1") | Ok("true") | Ok("TRUE")
    );
    let translucent_batches = batches
        .iter()
        .filter(|batch| batch.key.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT)
        .collect::<Vec<_>>();
    // An explicit semantic key is a bounded diagnostic request and may name
    // opaque, cutout, or translucent terrain. The unselected inventory stays
    // translucent-only so ordinary captures retain their narrow scope.
    let candidates = if requested_mesh_key.is_some() || trace_all_materials {
        batches.iter().collect::<Vec<_>>()
    } else {
        translucent_batches.clone()
    };
    let Some(batch) = candidates.iter().copied().find(|batch| {
        if let Some(mesh_key) = requested_mesh_key {
            return batch.key.mesh_key == mesh_key;
        }
        // The frame also contains first-person/entity mesh instances. Their
        // copied local textures are valid but are not static-terrain evidence;
        // static terrain owns no entity identity in the semantic asset table.
        frontend
            .mesh_assets
            .get(&batch.key.mesh_key)
            .is_some_and(|asset| asset.entity_identity.is_empty())
    }) else {
        return;
    };
    let summary_candidates = if trace_all_materials {
        candidates.as_slice()
    } else {
        translucent_batches.as_slice()
    };
    let batch_summaries = summary_candidates
        .iter()
        .take(128)
        .filter_map(|candidate| {
            static_terrain_batch_projected_bounds(frame, frontend, candidate, sorted_indices).map(
                |bounds| {
                    format!(
                        concat!(
                            "{{\"meshKey\":\"{:016x}\",\"meshGeneration\":{},",
                            "\"textureId\":{},\"materialId\":{},",
                            "\"indexOffset\":{},\"indexCount\":{},\"instances\":{},",
                            "\"ndcBounds\":[{:.6},{:.6},{:.6},{:.6}]}}"
                        ),
                        candidate.key.mesh_key,
                        frontend
                            .mesh_assets
                            .get(&candidate.key.mesh_key)
                            .map(|asset| asset.mesh_generation)
                            .unwrap_or_default(),
                        candidate.key.texture_id,
                        candidate.key.material_id,
                        candidate.index_offset,
                        candidate.index_count,
                        candidate.indices.len(),
                        bounds[0],
                        bounds[1],
                        bounds[2],
                        bounds[3],
                    )
                },
            )
        })
        .collect::<Vec<_>>();
    let mesh_key = batch.key.mesh_key;
    let Some(asset) = frontend.mesh_assets.get(&mesh_key) else {
        return;
    };
    let Some(instance_index) = batch.indices.first().copied() else {
        return;
    };
    let Some(instance) = frame.mesh_instances.get(instance_index) else {
        return;
    };
    let index_stride = index_stride(asset.index_type) as usize;
    let (index_bytes, start) = if let Some(offset) = batch.sorted_index_offset {
        (sorted_indices, offset as usize)
    } else {
        (asset.index_bytes.as_slice(), batch.index_offset as usize)
    };
    let sample_count = usize::try_from(batch.index_count).unwrap_or(0).min(12);
    let mut samples = Vec::new();
    for ordinal in 0..sample_count {
        let Some(byte_index) = ordinal
            .checked_mul(index_stride)
            .and_then(|offset| start.checked_add(offset))
        else {
            break;
        };
        let index = match mesh_index_value(index_bytes, asset.index_type, byte_index / index_stride)
        {
            Ok(index) => index,
            Err(_) => break,
        };
        let Some(vertex_offset) = usize::try_from(index)
            .ok()
            .and_then(|index| index.checked_mul(WORLD_MESH_GPU_VERTEX_BYTES))
        else {
            break;
        };
        let Some(vertex_bytes) = asset
            .vertex_bytes
            .get(vertex_offset..vertex_offset + WORLD_MESH_GPU_VERTEX_BYTES)
        else {
            break;
        };
        let position: [f32; 3] = std::array::from_fn(|component| {
            let offset = component * 4;
            f32::from_ne_bytes(
                vertex_bytes[offset..offset + 4]
                    .try_into()
                    .expect("fixed packed world mesh position lane"),
            )
        });
        let f32_lane = |offset: usize| {
            f32::from_ne_bytes(
                vertex_bytes[offset..offset + 4]
                    .try_into()
                    .expect("fixed packed world mesh f32 lane"),
            )
        };
        // These lanes are the direct-terrain shader inputs for a submitted
        // draw: raw UV, modulation colour, light, material policy, and atlas
        // UV. The bounded receipt diagnoses semantic data without exposing
        // any backend-private state.
        let raw_uv = [f32_lane(12), f32_lane(28)];
        let color = [f32_lane(16), f32_lane(20), f32_lane(24), f32_lane(44)];
        let packed_light = [f32_lane(48), f32_lane(52)];
        // `packed_mesh_vertices` stores the semantic material byte in the
        // fixed shader ABI as an f32. Decode that lane as the shader does;
        // interpreting its IEEE-754 bytes as a u32 made the receipt report
        // `1065353216` for the perfectly valid material value `1`.
        let material_bits = f32_lane(60).clamp(0.0, 255.0) as u32;
        let shader_atlas_uv = [f32_lane(64), f32_lane(68)];
        let model = transform_column_major_vec4(
            instance.transform,
            [position[0], position[1], position[2], 1.0],
        );
        let view = transform_column_major_vec4(frame.view_matrix, model);
        let clip = transform_column_major_vec4(frame.projection_matrix, view);
        let ndc = if clip[3].abs() > f32::EPSILON {
            [clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]]
        } else {
            [f32::NAN; 3]
        };
        samples.push(format!(
            concat!(
                "{{\"ordinal\":{ordinal},\"vertexIndex\":{index},",
                "\"local\":[{lx:.6},{ly:.6},{lz:.6}],",
                "\"model\":[{mx:.6},{my:.6},{mz:.6},{mw:.6}],",
                "\"view\":[{vx:.6},{vy:.6},{vz:.6},{vw:.6}],",
                "\"clip\":[{cx:.6},{cy:.6},{cz:.6},{cw:.6}],",
                "\"ndc\":[{nx:.6},{ny:.6},{nz:.6}],",
                "\"rawUv\":[{raw_u:.6},{raw_v:.6}],",
                "\"shaderAtlasUv\":[{atlas_u:.6},{atlas_v:.6}],",
                "\"color\":[{color_r:.6},{color_g:.6},{color_b:.6},{color_a:.6}],",
                "\"packedLight\":[{block_light:.6},{sky_light:.6}],",
                "\"terrainMaterialBits\":{material_bits}}}"
            ),
            ordinal = ordinal,
            index = index,
            lx = position[0],
            ly = position[1],
            lz = position[2],
            mx = model[0],
            my = model[1],
            mz = model[2],
            mw = model[3],
            vx = view[0],
            vy = view[1],
            vz = view[2],
            vw = view[3],
            cx = clip[0],
            cy = clip[1],
            cz = clip[2],
            cw = clip[3],
            nx = ndc[0],
            ny = ndc[1],
            nz = ndc[2],
            raw_u = raw_uv[0],
            raw_v = raw_uv[1],
            atlas_u = shader_atlas_uv[0],
            atlas_v = shader_atlas_uv[1],
            color_r = color[0],
            color_g = color[1],
            color_b = color[2],
            color_a = color[3],
            block_light = packed_light[0],
            sky_light = packed_light[1],
            material_bits = material_bits,
        ));
    }
    let selection = if requested_mesh_key.is_some() {
        "requested-mesh-key"
    } else if trace_all_materials {
        "first-non-entity-static-terrain-any-material"
    } else {
        "first-non-entity-static-translucent"
    };
    let payload = format!(
        concat!(
            "{{\"schema\":\"mattmc-static-terrain-batch-trace-v1\",",
            "\"selection\":\"{}\",\"frameId\":{},\"meshKey\":\"{:016x}\",\"meshGeneration\":{},",
            "\"indexType\":\"{:?}\",\"batchIndexOffset\":{},\"batchIndexCount\":{},",
            "\"batchInstances\":{},\"instanceIndex\":{},\"instanceColorArgb\":{},",
            "\"instanceColorRgba\":[{:.6},{:.6},{:.6},{:.6}],\"instanceTransform\":{},",
            "\"viewMatrix\":{},\"projectionMatrix\":{},\"samples\":[{}],",
            "\"cameraSortedQuads\":{},\"meshIndexCount\":{},",
            "\"orderedMeshRangesComplete\":{},\"orderedMeshRanges\":[{}],\"textureMipLevels\":{}}}\n"
        ),
        selection,
        frame.frame_id,
        mesh_key,
        asset.mesh_generation,
        asset.index_type,
        batch.index_offset,
        batch.index_count,
        batch.indices.len(),
        instance_index,
        instance.color_argb,
        argb_to_rgba(instance.color_argb)[0],
        argb_to_rgba(instance.color_argb)[1],
        argb_to_rgba(instance.color_argb)[2],
        argb_to_rgba(instance.color_argb)[3],
        matrix4_json_array(instance.transform),
        matrix4_json_array(frame.view_matrix),
        matrix4_json_array(frame.projection_matrix),
        samples.join(","),
        instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0,
        asset.index_bytes.len() / index_stride,
        batches.iter().filter(|candidate| candidate.key.mesh_key == mesh_key).count() <= 4096,
        batches.iter().filter(|candidate| candidate.key.mesh_key == mesh_key)
            .take(4096).map(|candidate| format!("[{},{}]", candidate.index_offset, candidate.index_count))
            .collect::<Vec<_>>().join(","),
        frontend.mesh_texture_resources.get(&batch.key.texture_id).map_or(0, |resource| resource.mip_levels),
    );
    let payload = payload.replacen(
        "\"samples\":[",
        &format!(
            "\"batchSummaries\":[{}],\"samples\":[",
            batch_summaries.join(",")
        ),
        1,
    );
    let root = PathBuf::from(root);
    if std::fs::create_dir_all(&root).is_ok() {
        let _ = std::fs::write(root.join("static_terrain_batch_trace.json"), payload);
    }
}

pub(in crate::render::worldrender) fn static_terrain_batch_projected_bounds(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    batch: &MeshBatch,
    sorted_indices: &[u8],
) -> Option<[f32; 4]> {
    let asset = frontend.mesh_assets.get(&batch.key.mesh_key)?;
    let instance_index = *batch.indices.first()?;
    let instance = frame.mesh_instances.get(instance_index)?;
    let index_stride = index_stride(asset.index_type) as usize;
    let (index_bytes, start) = if let Some(offset) = batch.sorted_index_offset {
        (sorted_indices, usize::try_from(offset).ok()?)
    } else {
        (
            asset.index_bytes.as_slice(),
            usize::try_from(batch.index_offset).ok()?,
        )
    };
    let count = usize::try_from(batch.index_count).ok()?.min(4_096);
    let mut bounds = [
        f32::INFINITY,
        f32::INFINITY,
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
    ];
    let mut found = false;
    for ordinal in 0..count {
        let byte_index = ordinal.checked_mul(index_stride)?.checked_add(start)?;
        let index =
            mesh_index_value(index_bytes, asset.index_type, byte_index / index_stride).ok()?;
        let vertex_offset = usize::try_from(index)
            .ok()?
            .checked_mul(WORLD_MESH_GPU_VERTEX_BYTES)?;
        let position_bytes = asset.vertex_bytes.get(vertex_offset..vertex_offset + 12)?;
        let position: [f32; 3] = std::array::from_fn(|component| {
            let offset = component * 4;
            f32::from_ne_bytes(
                position_bytes[offset..offset + 4]
                    .try_into()
                    .expect("fixed packed world mesh position lane"),
            )
        });
        let model = transform_column_major_vec4(
            instance.transform,
            [position[0], position[1], position[2], 1.0],
        );
        let view = transform_column_major_vec4(frame.view_matrix, model);
        let clip = transform_column_major_vec4(frame.projection_matrix, view);
        if !clip[3].is_finite() || clip[3].abs() <= f32::EPSILON {
            continue;
        }
        let x = clip[0] / clip[3];
        let y = clip[1] / clip[3];
        if !x.is_finite() || !y.is_finite() {
            continue;
        }
        bounds[0] = bounds[0].min(x);
        bounds[1] = bounds[1].min(y);
        bounds[2] = bounds[2].max(x);
        bounds[3] = bounds[3].max(y);
        found = true;
    }
    found.then_some(bounds)
}

/// Writes a rolling normal-route receipt for non-selected diagnostic runs.
/// Selected final-output captures instead retain `sky_fog_receipt_json` next
/// to their exact final image, so later frames cannot overwrite its evidence.
pub(in crate::render::worldrender) fn write_normal_route_fog_diagnostic(frame: &WorldPrimitiveFrame) {
    if !matches!(
        crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
            .as_deref()
            .map(str::trim),
        Ok("1") | Ok("true") | Ok("TRUE")
    ) {
        return;
    }
    let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
        return;
    };
    if std::fs::create_dir_all(&dir).is_ok() {
        if let Ok(receipt) = sky_fog_receipt_json(frame) {
            let _ = std::fs::write(Path::new(&dir).join("normal-route-fog-last.json"), receipt);
        }
    }
}
