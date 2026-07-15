//! Diagnostic logging for native fluid replay and targeted fluid records.
//!
//! Diagnostics are opt-in through environment variables and must never affect
//! emitted geometry, profile counters, or fallback behavior.

use super::*;

pub(in crate::render::chunk::meshing) static FLUID_DIAG_COUNT: AtomicUsize = AtomicUsize::new(0);
pub(in crate::render::chunk::meshing) static FLUID_FLUSH_DIAG_COUNT: AtomicUsize =
    AtomicUsize::new(0);
pub(in crate::render::chunk::meshing) static FLUID_DIAG_ENABLED: OnceLock<bool> = OnceLock::new();
pub(in crate::render::chunk::meshing) const FLUID_DIAG_LIMIT: usize = 10_000;

pub(in crate::render::chunk::meshing) fn native_fluid_diag_enabled() -> bool {
    *FLUID_DIAG_ENABLED.get_or_init(|| std::env::var_os("MATTMC_NATIVE_FLUID_DIAG").is_some())
}

pub(in crate::render::chunk::meshing) fn native_fluid_diag_log(message: impl std::fmt::Display) {
    if native_fluid_diag_enabled() {
        eprintln!("MATTMC_NATIVE_FLUID_DIAG {message}");
    }
}

pub(in crate::render::chunk::meshing) fn native_fluid_flush_diag(
    facing: usize,
    has_analyzer: bool,
    chunk_count: usize,
    valid_count: i32,
    committed_count: i32,
    records: &[FluidFaceRecord],
) {
    if !native_fluid_diag_enabled() {
        return;
    }
    let Some(record) = records
        .iter()
        .find(|record| record.render_type == 1 && record.face_kind == FLUID_FACE_TOP_NE_SW)
    else {
        return;
    };
    let index = FLUID_FLUSH_DIAG_COUNT.fetch_add(1, Ordering::Relaxed);
    if index >= 80 {
        return;
    }
    eprintln!(
        "MATTMC_NATIVE_FLUID_DIAG #{index} flush facing={} analyzer={} quads={} valid={} committed={} first_pos={},{},{} first_face={} first_flip={} first_light=0x{:08x} first_color=0x{:08x}",
        facing,
        has_analyzer,
        chunk_count,
        valid_count,
        committed_count,
        record.local_x,
        record.local_y,
        record.local_z,
        record.face_kind,
        record.flip,
        record.lights[0],
        record.colors[0] as u32,
    );
}

pub(in crate::render::chunk::meshing) fn fluid_diag(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    phase: &str,
    cull_up: bool,
    top_exposed: bool,
    heights: [f32; 4],
    color: i32,
    light: i32,
    facing: Option<usize>,
) {
    if !native_fluid_diag_enabled() {
        return;
    }
    if state.fluid_type != FLUID_WATER {
        return;
    }
    if !should_log_fluid_diag(block) {
        return;
    }
    if phase == "top-check" && cull_up {
        return;
    }
    let index = FLUID_DIAG_COUNT.fetch_add(1, Ordering::Relaxed);
    if index >= FLUID_DIAG_LIMIT {
        return;
    }
    let color_u = color as u32;
    eprintln!(
        "MATTMC_NATIVE_FLUID_DIAG #{index} {phase} pos={},{},{} state={} fluid_block_id={} state_fluid_block_id={} pass={} material={} cull_up={} top_exposed={} heights={:.4},{:.4},{:.4},{:.4} color=0x{color_u:08x} alpha={} light=0x{:08x} facing={:?} sprite_still=({:.5},{:.5},{:.5},{:.5}) flow=({:.4},{:.4})",
        block.absolute_x,
        block.absolute_y,
        block.absolute_z,
        block.state_id,
        block.fluid_block_id,
        state.fluid_block_id,
        state.fluid_pass_id,
        state.fluid_material_bits,
        cull_up,
        top_exposed,
        heights[0],
        heights[1],
        heights[2],
        heights[3],
        (color_u >> 24) & 0xff,
        light,
        facing,
        state.fluid_still.u0,
        state.fluid_still.u1,
        state.fluid_still.v0,
        state.fluid_still.v1,
        block.fluid_flow_x,
        block.fluid_flow_z,
    );
}

pub(in crate::render::chunk::meshing) fn fluid_record_diag(
    block: &NativeSectionBlockRecord,
    phase: &str,
    record: &FluidFaceRecord,
    facing: usize,
) {
    if !native_fluid_diag_enabled() {
        return;
    }
    if !should_log_fluid_diag(block) {
        return;
    }
    if !fluid_diag_target_matches(record) {
        return;
    }
    let index = FLUID_DIAG_COUNT.fetch_add(1, Ordering::Relaxed);
    if index >= FLUID_DIAG_LIMIT {
        return;
    }
    let color = record.colors[0] as u32;
    let encoded_textures = encoded_fluid_record_textures(record);
    eprintln!(
        "MATTMC_NATIVE_FLUID_DIAG #{index} {phase} pos={},{},{} facing={} flip={} face={} origin={:.1},{:.1},{:.1} heights={:.4},{:.4},{:.4},{:.4} uv0={:.5},{:.5} uv1={:.5},{:.5} uv2={:.5},{:.5} uv3={:.5},{:.5} tex=0x{:08x},0x{:08x},0x{:08x},0x{:08x} color0=0x{color:08x} ao0={:.4} light=0x{:08x},0x{:08x},0x{:08x},0x{:08x} normal=0x{:08x} material={} pass={}",
        block.absolute_x,
        block.absolute_y,
        block.absolute_z,
        facing,
        record.flip,
        record.face_kind,
        record.origin_x,
        record.origin_y,
        record.origin_z,
        record.heights[0],
        record.heights[1],
        record.heights[2],
        record.heights[3],
        record.uvs[0],
        record.uvs[1],
        record.uvs[2],
        record.uvs[3],
        record.uvs[4],
        record.uvs[5],
        record.uvs[6],
        record.uvs[7],
        encoded_textures[0],
        encoded_textures[1],
        encoded_textures[2],
        encoded_textures[3],
        record.aos[0],
        record.lights[0],
        record.lights[1],
        record.lights[2],
        record.lights[3],
        record.packed_normal,
        record.material_bits,
        record.render_type,
    );
}

pub(in crate::render::chunk::meshing) fn encoded_fluid_record_textures(
    record: &FluidFaceRecord,
) -> [u32; 4] {
    let u_center = (record.uvs[0] + record.uvs[2] + record.uvs[4] + record.uvs[6]) * 0.25;
    let v_center = (record.uvs[1] + record.uvs[3] + record.uvs[5] + record.uvs[7]) * 0.25;
    let mut output = [0u32; 4];
    for vertex in 0..4 {
        let u = encode_texture(u_center, record.uvs[vertex * 2]);
        let v = encode_texture(v_center, record.uvs[vertex * 2 + 1]);
        output[vertex] = pack_texture(u, v) as u32;
    }
    if record.flip != 0 {
        [output[0], output[3], output[2], output[1]]
    } else {
        output
    }
}

pub(in crate::render::chunk::meshing) fn fluid_diag_target_matches(
    record: &FluidFaceRecord,
) -> bool {
    let Some(target) = std::env::var_os("MATTMC_FLUID_DIAG_TARGET") else {
        return true;
    };
    let Some(target) = target.to_str() else {
        return true;
    };
    let mut parts = target.split(',');
    let (Some(x), Some(y), Some(z), None) =
        (parts.next(), parts.next(), parts.next(), parts.next())
    else {
        return true;
    };
    let Ok(x) = x.trim().parse::<i32>() else {
        return true;
    };
    let Ok(y) = y.trim().parse::<i32>() else {
        return true;
    };
    let Ok(z) = z.trim().parse::<i32>() else {
        return true;
    };

    record.origin_x as i32 == x && record.origin_y as i32 == y && record.origin_z as i32 == z
}

pub(in crate::render::chunk::meshing) fn should_log_fluid_diag(
    block: &NativeSectionBlockRecord,
) -> bool {
    if std::env::var_os("MATTMC_FLUID_DIAG_REPLAY").is_some() {
        return (0..=15).contains(&block.absolute_x)
            && (64..=79).contains(&block.absolute_y)
            && (0..=15).contains(&block.absolute_z);
    }

    (0..=160).contains(&block.absolute_x)
        && (60..=72).contains(&block.absolute_y)
        && (360..=660).contains(&block.absolute_z)
}
