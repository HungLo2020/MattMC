use std::slice;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::OnceLock;
use std::time::Instant;

use super::*;

static FLUID_DIAG_COUNT: AtomicUsize = AtomicUsize::new(0);
static FLUID_FLUSH_DIAG_COUNT: AtomicUsize = AtomicUsize::new(0);
static FLUID_DIAG_ENABLED: OnceLock<bool> = OnceLock::new();
static FLUID_FORCE_UNASSIGNED: OnceLock<bool> = OnceLock::new();
const FLUID_DIAG_LIMIT: usize = 10_000;

pub(super) fn native_fluid_diag_enabled() -> bool {
    *FLUID_DIAG_ENABLED.get_or_init(|| std::env::var_os("MATTMC_NATIVE_FLUID_DIAG").is_some())
}

fn native_fluid_force_unassigned() -> bool {
    *FLUID_FORCE_UNASSIGNED
        .get_or_init(|| std::env::var_os("MATTMC_NATIVE_FLUID_FORCE_UNASSIGNED").is_some())
}

fn native_fluid_diag_log(message: impl std::fmt::Display) {
    if native_fluid_diag_enabled() {
        eprintln!("MATTMC_NATIVE_FLUID_DIAG {message}");
    }
}

#[inline(always)]
fn fluid_sprite_mask(fluid_type: i32, still: bool, overlay: bool) -> i32 {
    match (fluid_type, still, overlay) {
        (FLUID_WATER, true, _) => FLUID_SPRITE_WATER_STILL,
        (FLUID_WATER, false, true) => FLUID_SPRITE_WATER_OVERLAY,
        (FLUID_WATER, false, false) => FLUID_SPRITE_WATER_FLOW,
        (FLUID_LAVA, true, _) => FLUID_SPRITE_LAVA_STILL,
        (FLUID_LAVA, false, _) => FLUID_SPRITE_LAVA_FLOW,
        _ => 0,
    }
}

#[derive(Clone, Copy)]
pub(super) struct NativeFluidFace {
    pub quad: NativeQuad,
    pub packed_normal: i32,
    pub facing: usize,
}

trait NativeFluidFaceSink {
    fn profile(&mut self) -> &mut NativeMeshingProfile;

    fn mark_fluid_sprite(&mut self, mask: i32);

    fn emit(&mut self, face: NativeFluidFace) -> Result<(), i32>;
}

struct BuilderFluidFaceSink<'a, 'b> {
    builder: &'a mut NativeSectionMeshBuilder,
    pending_counts: &'b mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    profile_scan_substages: bool,
    profile_staging_substages: bool,
    total_committed: &'b mut i32,
}

impl NativeFluidFaceSink for BuilderFluidFaceSink<'_, '_> {
    #[inline(always)]
    fn profile(&mut self) -> &mut NativeMeshingProfile {
        &mut self.builder.profile
    }

    #[inline(always)]
    fn mark_fluid_sprite(&mut self, mask: i32) {
        self.builder.fluid_sprite_mask |= mask;
    }

    #[inline(always)]
    fn emit(&mut self, face: NativeFluidFace) -> Result<(), i32> {
        let append_started = profile_start(self.profile_scan_substages);
        unsafe {
            push_native_section_quad(
                self.builder,
                face.quad,
                face.packed_normal,
                face.facing,
                self.pending_counts,
                self.analyzer,
                self.format,
                self.store_raw_quads,
                self.profile_staging_substages,
                self.total_committed,
            )?;
        }
        self.builder
            .profile
            .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
        Ok(())
    }
}

fn native_fluid_flush_diag(
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

pub(super) unsafe fn section_builder_append_fluid_face_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    analyzer: Option<(u64, i32)>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<(i32, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok((0, 0));
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<FluidFaceRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const FluidFaceRecord, record_count);
    let mut processed = 0usize;
    let mut total_valid = 0i32;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let geometry_started = Instant::now();
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                let record = records[processed + index];
                pending.quads[index] = fluid_face_record_to_quad(record)?;
                pending.packed_normals[index] = record.packed_normal;
            }
            builder
                .profile
                .add_stage(PROFILE_FLUID_GEOM_UV, geometry_started);
        }

        let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
        let mut chunk_valid = chunk_count as i32;
        let validity = if let Some((analyzer_handle, translucent_facing)) = analyzer {
            let analyzer_started = Instant::now();
            let status = translucent::append_native_quad_batch_to_analyzer(
                analyzer_handle,
                builder.pending[facing].quads.as_ptr() as u64,
                chunk_count as i32,
                translucent_facing,
                builder.pending[facing].packed_normals.as_ptr(),
                validity_address,
                &mut chunk_valid,
            );
            if status != OK {
                return Err(status);
            }
            builder
                .profile
                .add_stage(PROFILE_TRANSLUCENT_INGEST, analyzer_started);
            builder
                .profile
                .add_count(PROFILE_COUNT_TRANSLUCENT_QUADS, chunk_valid.max(0) as usize);
            Some(slice::from_raw_parts(
                validity_address as *const u8,
                chunk_count,
            ))
        } else {
            None
        };

        let staging_started = Instant::now();
        let chunk_committed = section_builder_append_batch_encoded(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            validity,
            format,
            store_raw_quads,
        )?;
        builder
            .profile
            .add_stage(PROFILE_QUAD_STAGING, staging_started);

        native_fluid_flush_diag(
            facing,
            analyzer.is_some(),
            chunk_count,
            chunk_valid,
            chunk_committed,
            &records[processed..processed + chunk_count],
        );

        total_valid = total_valid.checked_add(chunk_valid).ok_or(ERR_CAPACITY)?;
        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok((total_valid, total_committed))
}

pub(super) unsafe fn emit_native_section_fluid_faces(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    builder: &mut NativeSectionMeshBuilder,
    pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    profile_scan_substages: bool,
    profile_staging_substages: bool,
    total_committed: &mut i32,
) -> Result<usize, i32> {
    let mut sink = BuilderFluidFaceSink {
        builder,
        pending_counts,
        analyzer,
        format,
        store_raw_quads,
        profile_scan_substages,
        profile_staging_substages,
        total_committed,
    };
    native_section_fluid_faces_to_sink(block, state, states, &mut sink)
}

fn native_section_fluid_faces_to_sink<S: NativeFluidFaceSink>(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    sink: &mut S,
) -> Result<usize, i32> {
    let mut emitted = 0usize;
    let profile_fluid_substages = fluid_substage_profile_enabled();
    let visibility_started = Instant::now();
    native_fluid_diag_log(format_args!(
        "begin pos={},{},{} local={},{},{} state={} fluid_type={} pass={} material={} block_id={}",
        block.absolute_x,
        block.absolute_y,
        block.absolute_z,
        block.local_x,
        block.local_y,
        block.local_z,
        block.state_id,
        state.fluid_type,
        state.fluid_pass_id,
        state.fluid_material_bits,
        state.fluid_block_id,
    ));
    if state.fluid_type != FLUID_WATER && state.fluid_type != FLUID_LAVA {
        sink.profile()
            .add_stage(PROFILE_FLUID_VIS_HEIGHT, visibility_started);
        native_fluid_diag_log(format_args!(
            "skip-unsupported pos={},{},{} fluid_type={}",
            block.absolute_x, block.absolute_y, block.absolute_z, state.fluid_type
        ));
        return Ok(0);
    }
    let h = fluid_height(block, state, states, 0, 0, 0, 1);
    native_fluid_diag_log(format_args!(
        "own-height pos={},{},{} height={:.4}",
        block.absolute_x, block.absolute_y, block.absolute_z, h
    ));
    let heights = if h >= 1.0 {
        [1.0; 4]
    } else {
        let corner_started = profile_start(profile_fluid_substages);
        let hn = fluid_height(block, state, states, 0, 0, -1, 2);
        let hs = fluid_height(block, state, states, 0, 0, 1, 3);
        let hw = fluid_height(block, state, states, -1, 0, 0, 4);
        let he = fluid_height(block, state, states, 1, 0, 0, 5);
        let heights = [
            fluid_corner_height(block, state, states, h, hn, hw, -1, 0, -1),
            fluid_corner_height(block, state, states, h, hs, hw, -1, 0, 1),
            fluid_corner_height(block, state, states, h, hs, he, 1, 0, 1),
            fluid_corner_height(block, state, states, h, hn, he, 1, 0, -1),
        ];
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_CORNER_HEIGHT_USE, corner_started);
        heights
    };
    let cull_up = fluid_side_occluded(block, state, states, 1);
    let cull_down = fluid_side_occluded(block, state, states, 0)
        || !fluid_side_exposed(block, states, 0, 0, -1, 0.8888889);
    let lighting_tint_started = profile_start(profile_fluid_substages);
    let color = argb_to_abgr(native_tint_color(block, state, true));
    let light = get_emissive_lightmap(block.light_words[13]);
    sink.profile()
        .add_optional_stage(PROFILE_FLUID_LIGHTING_TINT, lighting_tint_started);
    let y_offset = if cull_down { 0.0 } else { 0.001 };
    let top_exposed = fluid_side_exposed(
        block,
        states,
        0,
        1,
        0,
        heights.iter().copied().fold(1.0, f32::min),
    );
    let mut render_heights = heights;
    let emit_top = !cull_up && top_exposed;
    if emit_top {
        for height in &mut render_heights {
            *height -= 0.001;
        }
    }
    let sides = [
        (
            2,
            MODEL_QUAD_FACING_NEG_Z,
            MODEL_QUAD_FACING_POS_Z,
            0.8,
            render_heights[0],
            render_heights[3],
            0.0,
            0.001,
            1.0,
            0.001,
        ),
        (
            3,
            MODEL_QUAD_FACING_POS_Z,
            MODEL_QUAD_FACING_NEG_Z,
            0.8,
            render_heights[2],
            render_heights[1],
            1.0,
            0.999,
            0.0,
            0.999,
        ),
        (
            4,
            MODEL_QUAD_FACING_NEG_X,
            MODEL_QUAD_FACING_POS_X,
            0.6,
            render_heights[1],
            render_heights[0],
            0.001,
            1.0,
            0.001,
            0.0,
        ),
        (
            5,
            MODEL_QUAD_FACING_POS_X,
            MODEL_QUAD_FACING_NEG_X,
            0.6,
            render_heights[3],
            render_heights[2],
            0.999,
            0.0,
            0.999,
            1.0,
        ),
    ];
    let mut visible_sides = [false; 4];
    let mut overlay_sides = [false; 4];
    for (index, (dir, _, _, _, h1, h2, _, _, _, _)) in sides.iter().copied().enumerate() {
        let step = dir_step(dir);
        visible_sides[index] = !fluid_side_occluded(block, state, states, dir)
            && fluid_side_exposed(block, states, step.0, step.1, step.2, h1.max(h2));
        if visible_sides[index] {
            let overlay_started = profile_start(profile_fluid_substages);
            overlay_sides[index] = fluid_side_uses_overlay(block, state, states, dir);
            sink.profile()
                .add_optional_stage(PROFILE_FLUID_OVERLAY_SELECTION, overlay_started);
        }
    }
    sink.profile()
        .add_stage(PROFILE_FLUID_VIS_HEIGHT, visibility_started);
    fluid_diag(
        block,
        state,
        "top-check",
        cull_up,
        top_exposed,
        heights,
        color,
        light,
        None,
    );

    let geometry_started = Instant::now();
    if emit_top {
        let uv_started = profile_start(profile_fluid_substages);
        native_fluid_diag_log(format_args!(
            "top-uv-start pos={},{},{} flow={:.4},{:.4} falling={}",
            block.absolute_x,
            block.absolute_y,
            block.absolute_z,
            block.fluid_flow_x,
            block.fluid_flow_z,
            state.fluid_falling
        ));
        let top_uses_still =
            block.fluid_flow_x == 0.0 && block.fluid_flow_z == 0.0 && state.fluid_falling == 0;
        let top =
            if top_uses_still {
                native_fluid_diag_log(format_args!(
                    "top-uv-still pos={},{},{}",
                    block.absolute_x, block.absolute_y, block.absolute_z
                ));
                still_fluid_top_uvs(state.fluid_still)
            } else {
                let sprite = state.fluid_flow;
                native_fluid_diag_log(format_args!(
                    "top-uv-flowing-before-atan pos={},{},{}",
                    block.absolute_x, block.absolute_y, block.absolute_z
                ));
                let dir = (mth_atan2(block.fluid_flow_z as f64, block.fluid_flow_x as f64)
                    as f32)
                    - 1.5707964_f32;
                native_fluid_diag_log(format_args!(
                    "top-uv-flowing-after-atan pos={},{},{} dir={:.6}",
                    block.absolute_x, block.absolute_y, block.absolute_z, dir
                ));
                let sin = mth_sin(dir) * 0.25;
                let cos = mth_cos(dir) * 0.25;
                native_fluid_diag_log(format_args!(
                    "top-uv-flowing-after-trig pos={},{},{} sin={:.6} cos={:.6}",
                    block.absolute_x, block.absolute_y, block.absolute_z, sin, cos
                ));
                shrink_fluid_uvs(
                    [
                        (
                            sprite_u(sprite, 0.5 + (-cos - sin)),
                            sprite_v(sprite, 0.5 + -cos + sin),
                        ),
                        (
                            sprite_u(sprite, 0.5 + -cos + sin),
                            sprite_v(sprite, 0.5 + cos + sin),
                        ),
                        (
                            sprite_u(sprite, 0.5 + cos + sin),
                            sprite_v(sprite, 0.5 + (cos - sin)),
                        ),
                        (
                            sprite_u(sprite, 0.5 + (cos - sin)),
                            sprite_v(sprite, 0.5 + (-cos - sin)),
                        ),
                    ],
                    state.fluid_still.shrink,
                )
            };
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_STILL_FLOWING_UV, uv_started);
        native_fluid_diag_log(format_args!(
            "top-uv-end pos={},{},{} uv0={:.5},{:.5}",
            block.absolute_x, block.absolute_y, block.absolute_z, top[0].0, top[0].1
        ));
        let top_started = profile_start(profile_fluid_substages);
        let top_aligned = fluid_top_aligned(render_heights);
        let top_facing = if top_aligned {
            MODEL_QUAD_FACING_POS_Y
        } else {
            MODEL_QUAD_FACING_UNASSIGNED
        };
        let top_face_kind = if fluid_top_crease_ne_sw(render_heights, top_aligned) {
            FLUID_FACE_TOP_NE_SW
        } else {
            FLUID_FACE_TOP_NW_SE
        };
        let top_record_uvs = if top_face_kind == FLUID_FACE_TOP_NE_SW {
            [top[3], top[0], top[1], top[2]]
        } else {
            top
        };
        native_fluid_diag_log(format_args!(
            "top-face-before-quad pos={},{},{} facing={} kind={}",
            block.absolute_x, block.absolute_y, block.absolute_z, top_facing, top_face_kind
        ));
        let top_face = fluid_semantic_native_face(
            state,
            block,
            top_facing,
            false,
            MODEL_QUAD_FACING_POS_Y as i32,
            0,
            top_face_kind,
            0.0,
            render_heights,
            [0.0; 4],
            top_record_uvs,
            color,
            1.0,
            light,
        );
        native_fluid_diag_log(format_args!(
            "top-face-after-quad pos={},{},{} normal=0x{:08x}",
            block.absolute_x,
            block.absolute_y,
            block.absolute_z,
            top_face.packed_normal
        ));
        fluid_semantic_record_diag(
            block,
            "top-record",
            state,
            top_face.facing,
            false,
            top_face_kind,
            0.0,
            render_heights,
            [0.0; 4],
            top_record_uvs,
            color,
            1.0,
            light,
            top_face.packed_normal,
        );
        let append_started = profile_start(profile_fluid_substages);
        native_fluid_diag_log(format_args!(
            "emit-top pos={},{},{} facing={} kind={} heights={:.4},{:.4},{:.4},{:.4}",
            block.absolute_x,
            block.absolute_y,
            block.absolute_z,
            top_face.facing,
            top_face_kind,
            render_heights[0],
            render_heights[1],
            render_heights[2],
            render_heights[3]
        ));
        sink.emit(top_face)?;
        sink.mark_fluid_sprite(fluid_sprite_mask(state.fluid_type, top_uses_still, false));
        native_fluid_diag_log(format_args!(
            "emit-top-done pos={},{},{}",
            block.absolute_x, block.absolute_y, block.absolute_z
        ));
        emitted += 1;
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_NATIVE_QUAD_APPEND, append_started);
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_TOP_FACE_CONSTRUCTION, top_started);
        fluid_diag(
            block,
            state,
            "top-emitted",
            cull_up,
            top_exposed,
            render_heights,
            color,
            light,
            Some(top_facing),
        );
        let normal_backface_started = profile_start(profile_fluid_substages);
        if fluid_backward_up_face(block, state, states) {
            let backward_facing = if top_facing == MODEL_QUAD_FACING_POS_Y {
                MODEL_QUAD_FACING_NEG_Y
            } else {
                MODEL_QUAD_FACING_UNASSIGNED
            };
            let backward_face = fluid_semantic_native_face(
                state,
                block,
                backward_facing,
                true,
                MODEL_QUAD_FACING_POS_Y as i32,
                0,
                top_face_kind,
                0.0,
                render_heights,
                [0.0; 4],
                top_record_uvs,
                color,
                1.0,
                light,
            );
            fluid_semantic_record_diag(
                block,
                "top-back-record",
                state,
                backward_face.facing,
                true,
                top_face_kind,
                0.0,
                render_heights,
                [0.0; 4],
                top_record_uvs,
                color,
                1.0,
                light,
                backward_face.packed_normal,
            );
            let append_started = profile_start(profile_fluid_substages);
            native_fluid_diag_log(format_args!(
                "emit-top-back pos={},{},{} facing={} kind={}",
                block.absolute_x, block.absolute_y, block.absolute_z, backward_face.facing, top_face_kind
            ));
            sink.emit(backward_face)?;
            sink.mark_fluid_sprite(fluid_sprite_mask(state.fluid_type, top_uses_still, false));
            emitted += 1;
            sink.profile()
                .add_optional_stage(PROFILE_FLUID_NATIVE_QUAD_APPEND, append_started);
        }
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_NORMAL_BACKFACE, normal_backface_started);
    }

    if !cull_down {
        let bottom_started = profile_start(profile_fluid_substages);
        let sprite = state.fluid_still;
        let material_started = profile_start(profile_fluid_substages);
        let uvs = [
            (sprite.u0, sprite.v1),
            (sprite.u0, sprite.v0),
            (sprite.u1, sprite.v0),
            (sprite.u1, sprite.v1),
        ];
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_MATERIAL_SPRITE_ROUTING, material_started);
        let bottom_face = fluid_semantic_native_face(
            state,
            block,
            MODEL_QUAD_FACING_NEG_Y,
            false,
            0,
            0,
            FLUID_FACE_BOTTOM,
            y_offset,
            [0.0; 4],
            [0.0; 4],
            uvs,
            color,
            1.0,
            light,
        );
        fluid_semantic_record_diag(
            block,
            "bottom-record",
            state,
            MODEL_QUAD_FACING_NEG_Y,
            false,
            FLUID_FACE_BOTTOM,
            y_offset,
            [0.0; 4],
            [0.0; 4],
            uvs,
            color,
            1.0,
            light,
            bottom_face.packed_normal,
        );
        let append_started = profile_start(profile_fluid_substages);
        native_fluid_diag_log(format_args!(
            "emit-bottom pos={},{},{} facing={}",
            block.absolute_x, block.absolute_y, block.absolute_z, bottom_face.facing
        ));
        sink.emit(bottom_face)?;
        sink.mark_fluid_sprite(fluid_sprite_mask(state.fluid_type, true, false));
        emitted += 1;
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_NATIVE_QUAD_APPEND, append_started);
        sink.profile()
            .add_optional_stage(PROFILE_FLUID_BOTTOM_FACE_CONSTRUCTION, bottom_started);
    }

    let flow_sprite = state.fluid_flow;
    let flow_u1 = flow_sprite.u0;
    let flow_u2 = sprite_mid_u(flow_sprite);
    let flow_v3 = sprite_mid_v(flow_sprite);
    let overlay_sprite = state.fluid_overlay;
    let overlay_u1 = overlay_sprite.u0;
    let overlay_u2 = sprite_mid_u(overlay_sprite);
    let overlay_v3 = sprite_mid_v(overlay_sprite);

    for (index, (dir, facing, opposite_facing, shade, h1, h2, x1, z1, x2, z2)) in
        sides.iter().copied().enumerate()
    {
        if visible_sides[index] {
            let side_started = profile_start(profile_fluid_substages);
            let material_started = profile_start(profile_fluid_substages);
            let is_overlay = overlay_sides[index];
            let (sprite, u1, u2, v3) = if is_overlay {
                (overlay_sprite, overlay_u1, overlay_u2, overlay_v3)
            } else {
                (flow_sprite, flow_u1, flow_u2, flow_v3)
            };
            sink.profile()
                .add_optional_stage(PROFILE_FLUID_MATERIAL_SPRITE_ROUTING, material_started);
            let uv_started = profile_start(profile_fluid_substages);
            let v1 = sprite_v(sprite, (1.0 - h1) * 0.5);
            let v2 = sprite_v(sprite, (1.0 - h2) * 0.5);
            let uvs = [(u2, v2), (u2, v3), (u1, v3), (u1, v1)];
            sink.profile()
                .add_optional_stage(PROFILE_FLUID_STILL_FLOWING_UV, uv_started);
            let side_face = fluid_semantic_native_face(
                state,
                block,
                facing,
                false,
                dir,
                MODEL_QUAD_FLAG_PARALLEL | MODEL_QUAD_FLAG_ALIGNED,
                FLUID_FACE_SIDE,
                y_offset,
                [h1, h2, 0.0, 0.0],
                [x1, z1, x2, z2],
                uvs,
                color,
                shade,
                light,
            );
            fluid_semantic_record_diag(
                block,
                "side-record",
                state,
                facing,
                false,
                FLUID_FACE_SIDE,
                y_offset,
                [h1, h2, 0.0, 0.0],
                [x1, z1, x2, z2],
                uvs,
                color,
                shade,
                light,
                side_face.packed_normal,
            );
            let append_started = profile_start(profile_fluid_substages);
            native_fluid_diag_log(format_args!(
                "emit-side pos={},{},{} dir={} facing={} overlay={} h={:.4},{:.4}",
                block.absolute_x,
                block.absolute_y,
                block.absolute_z,
                dir,
                side_face.facing,
                is_overlay,
                h1,
                h2
            ));
            sink.emit(side_face)?;
            sink.mark_fluid_sprite(fluid_sprite_mask(state.fluid_type, false, is_overlay));
            emitted += 1;
            sink.profile()
                .add_optional_stage(PROFILE_FLUID_NATIVE_QUAD_APPEND, append_started);
            if !is_overlay {
                let normal_backface_started = profile_start(profile_fluid_substages);
                let back_face = fluid_semantic_native_face(
                    state,
                    block,
                    opposite_facing,
                    true,
                    dir,
                    MODEL_QUAD_FLAG_PARALLEL | MODEL_QUAD_FLAG_ALIGNED,
                    FLUID_FACE_SIDE,
                    y_offset,
                    [h1, h2, 0.0, 0.0],
                    [x1, z1, x2, z2],
                    uvs,
                    color,
                    shade,
                    light,
                );
                fluid_semantic_record_diag(
                    block,
                    "side-back-record",
                    state,
                    opposite_facing,
                    true,
                    FLUID_FACE_SIDE,
                    y_offset,
                    [h1, h2, 0.0, 0.0],
                    [x1, z1, x2, z2],
                    uvs,
                    color,
                    shade,
                    light,
                    back_face.packed_normal,
                );
                let append_started = profile_start(profile_fluid_substages);
                native_fluid_diag_log(format_args!(
                    "emit-side-back pos={},{},{} dir={} facing={} h={:.4},{:.4}",
                    block.absolute_x,
                    block.absolute_y,
                    block.absolute_z,
                    dir,
                    back_face.facing,
                    h1,
                    h2
                ));
                sink.emit(back_face)?;
                sink.mark_fluid_sprite(fluid_sprite_mask(state.fluid_type, false, false));
                emitted += 1;
                sink.profile()
                    .add_optional_stage(PROFILE_FLUID_NATIVE_QUAD_APPEND, append_started);
                sink.profile()
                    .add_optional_stage(PROFILE_FLUID_NORMAL_BACKFACE, normal_backface_started);
            }
            sink.profile()
                .add_optional_stage(PROFILE_FLUID_SIDE_FACE_CONSTRUCTION, side_started);
        }
    }
    sink.profile()
        .add_stage(PROFILE_FLUID_GEOM_UV, geometry_started);
    native_fluid_diag_log(format_args!(
        "end pos={},{},{} emitted={}",
        block.absolute_x, block.absolute_y, block.absolute_z, emitted
    ));
    Ok(emitted)
}

fn fluid_diag(
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

fn fluid_record_diag(
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

fn encoded_fluid_record_textures(record: &FluidFaceRecord) -> [u32; 4] {
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

fn fluid_diag_target_matches(record: &FluidFaceRecord) -> bool {
    let Some(target) = std::env::var_os("MATTMC_FLUID_DIAG_TARGET") else {
        return true;
    };
    let Some(target) = target.to_str() else {
        return true;
    };
    let mut parts = target.split(',');
    let (Some(x), Some(y), Some(z), None) = (parts.next(), parts.next(), parts.next(), parts.next())
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

fn should_log_fluid_diag(block: &NativeSectionBlockRecord) -> bool {
    if std::env::var_os("MATTMC_FLUID_DIAG_REPLAY").is_some() {
        return (0..=15).contains(&block.absolute_x)
            && (64..=79).contains(&block.absolute_y)
            && (0..=15).contains(&block.absolute_z);
    }

    (0..=160).contains(&block.absolute_x)
        && (60..=72).contains(&block.absolute_y)
        && (360..=660).contains(&block.absolute_z)
}

fn fluid_top_aligned(heights: [f32; 4]) -> bool {
    fluid_aligned_equals(heights[3], heights[0])
        && fluid_aligned_equals(heights[0], heights[2])
        && fluid_aligned_equals(heights[2], heights[1])
        && fluid_aligned_equals(heights[1], heights[3])
}

fn fluid_aligned_equals(a: f32, b: f32) -> bool {
    (a - b).abs() <= FLUID_ALIGNED_EQUALS_EPSILON
}

fn fluid_top_crease_ne_sw(heights: [f32; 4], aligned: bool) -> bool {
    aligned
        || heights[3] > heights[0] && heights[3] > heights[2]
        || heights[3] < heights[0] && heights[3] < heights[2]
        || heights[1] > heights[0] && heights[1] > heights[2]
        || heights[1] < heights[0] && heights[1] < heights[2]
}

#[cfg(test)]
pub(super) fn fluid_semantic_face(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> (FluidFaceRecord, usize) {
    let mut facing = facing;
    if native_fluid_force_unassigned() {
        facing = MODEL_QUAD_FACING_UNASSIGNED;
    }
    let mut record = fluid_semantic_record(
        state,
        block,
        facing,
        flip,
        face_kind,
        y_offset,
        heights,
        side_coords,
        uvs,
        color,
        ao,
        light,
    );
    let quad = fluid_face_record_to_quad(record)
        .expect("native fluid semantic face generated an invalid fluid face record");
    record.packed_normal = norm_i8_pack_from_quad(&quad);
    (record, facing)
}

fn fluid_semantic_native_face(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    mut facing: usize,
    flip: bool,
    light_face: i32,
    light_flags: i32,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> NativeFluidFace {
    if native_fluid_force_unassigned() {
        facing = MODEL_QUAD_FACING_UNASSIGNED;
    }

    let mut quad = fluid_semantic_quad(
        state,
        block,
        false,
        face_kind,
        y_offset,
        heights,
        side_coords,
        uvs,
        color,
        ao,
        light,
    );
    apply_fluid_lighting(&mut quad, block, state, light_face, light_flags, ao);
    let mut packed_normal = packed_fluid_normal(facing, &quad);
    if flip {
        quad.vertices = [
            quad.vertices[0],
            quad.vertices[3],
            quad.vertices[2],
            quad.vertices[1],
        ];
        packed_normal = flip_packed_normal(packed_normal);
    }
    NativeFluidFace {
        quad,
        packed_normal,
        facing,
    }
}

fn apply_fluid_lighting(
    quad: &mut NativeQuad,
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    light_face: i32,
    flags: i32,
    face_brightness: f32,
) {
    let mut light_quad = StaticModelQuadRecord {
        flags,
        light_face,
        cull_face: -1,
        normal_face: -1,
        has_ao: if state.fluid_type == FLUID_WATER { 1 } else { 0 },
        shade: 0,
        ..StaticModelQuadRecord::default()
    };

    for (index, vertex) in quad.vertices.iter().enumerate() {
        light_quad.vertices[index] = StaticModelVertexRecord {
            x: vertex.x - block.local_x as f32,
            y: vertex.y - block.local_y as f32,
            z: vertex.z - block.local_z as f32,
            color: vertex.color,
            u: vertex.u,
            v: vertex.v,
            light: vertex.light,
        };
    }

    let light = native_quad_lighting(block, &light_quad, state);
    for (index, vertex) in quad.vertices.iter_mut().enumerate() {
        vertex.ao = light.ao[index] * face_brightness;
        vertex.light = light.lm[index];
    }
}

fn fluid_semantic_record_diag(
    block: &NativeSectionBlockRecord,
    phase: &str,
    state: NativeMeshingState,
    facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
    packed_normal: i32,
) {
    if !native_fluid_diag_enabled() {
        return;
    }
    let mut record = fluid_semantic_record(
        state,
        block,
        facing,
        flip,
        face_kind,
        y_offset,
        heights,
        side_coords,
        uvs,
        color,
        ao,
        light,
    );
    record.packed_normal = packed_normal;
    fluid_record_diag(block, phase, &record, facing);
}

fn fluid_semantic_record(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    facing: usize,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> FluidFaceRecord {
    let _ = facing;
    FluidFaceRecord {
        packed_normal: 0,
        material_bits: state.fluid_material_bits,
        block_emission: state.block_emission,
        render_type: 1,
        ignore_mid_block: 0,
        block_id: choose_block_id(block.fluid_block_id, state.fluid_block_id),
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        face_kind,
        flip: if flip { 1 } else { 0 },
        origin_x: block.local_x as f32,
        origin_y: block.local_y as f32,
        origin_z: block.local_z as f32,
        y_offset,
        heights,
        side_coords,
        uvs: [
            uvs[0].0, uvs[0].1, uvs[1].0, uvs[1].1, uvs[2].0, uvs[2].1, uvs[3].0, uvs[3].1,
        ],
        colors: [color; 4],
        aos: [ao; 4],
        lights: [light; 4],
    }
}

fn fluid_semantic_quad(
    state: NativeMeshingState,
    block: &NativeSectionBlockRecord,
    flip: bool,
    face_kind: i32,
    y_offset: f32,
    heights: [f32; 4],
    side_coords: [f32; 4],
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> NativeQuad {
    let origin_x = block.local_x as f32;
    let origin_y = block.local_y as f32;
    let origin_z = block.local_z as f32;
    let mut vertices = match face_kind {
        FLUID_FACE_TOP_NE_SW => [
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[3],
                origin_z,
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + heights[0],
                origin_z,
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + heights[1],
                origin_z + 1.0,
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[2],
                origin_z + 1.0,
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        FLUID_FACE_TOP_NW_SE => [
            fluid_native_vertex(
                origin_x,
                origin_y + heights[0],
                origin_z,
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + heights[1],
                origin_z + 1.0,
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[2],
                origin_z + 1.0,
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + heights[3],
                origin_z,
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        FLUID_FACE_BOTTOM => [
            fluid_native_vertex(
                origin_x,
                origin_y + y_offset,
                origin_z + 1.0,
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x,
                origin_y + y_offset,
                origin_z,
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + y_offset,
                origin_z,
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + 1.0,
                origin_y + y_offset,
                origin_z + 1.0,
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        FLUID_FACE_SIDE => [
            fluid_native_vertex(
                origin_x + side_coords[2],
                origin_y + heights[1],
                origin_z + side_coords[3],
                0,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + side_coords[2],
                origin_y + y_offset,
                origin_z + side_coords[3],
                1,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + side_coords[0],
                origin_y + y_offset,
                origin_z + side_coords[1],
                2,
                uvs,
                color,
                ao,
                light,
            ),
            fluid_native_vertex(
                origin_x + side_coords[0],
                origin_y + heights[0],
                origin_z + side_coords[1],
                3,
                uvs,
                color,
                ao,
                light,
            ),
        ],
        _ => [QuadVertex::default(); 4],
    };

    if flip {
        vertices = [vertices[0], vertices[3], vertices[2], vertices[1]];
    }

    NativeQuad {
        vertices,
        block_emission: state.block_emission.clamp(0, 255) as u8,
        render_type: 1,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: choose_block_id(block.fluid_block_id, state.fluid_block_id),
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        material_bits: state.fluid_material_bits,
    }
}

fn packed_fluid_normal(facing: usize, quad: &NativeQuad) -> i32 {
    match facing {
        MODEL_QUAD_FACING_POS_X => 0x0000007f,
        MODEL_QUAD_FACING_POS_Y => 0x00007f00,
        MODEL_QUAD_FACING_POS_Z => 0x007f0000,
        MODEL_QUAD_FACING_NEG_X => 0x00000081,
        MODEL_QUAD_FACING_NEG_Y => 0x00008100,
        MODEL_QUAD_FACING_NEG_Z => 0x00810000,
        _ => norm_i8_pack_from_quad(quad),
    }
}

pub(super) fn flip_packed_normal(normal: i32) -> i32 {
    let x = normal as u32 & 0xff;
    let y = (normal as u32 >> 8) & 0xff;
    let z = (normal as u32 >> 16) & 0xff;
    let flipped = ((!x).wrapping_add(1) & 0xff)
        | (((!y).wrapping_add(1) & 0xff) << 8)
        | (((!z).wrapping_add(1) & 0xff) << 16);
    flipped as i32
}

fn fluid_native_vertex(
    x: f32,
    y: f32,
    z: f32,
    vertex: usize,
    uvs: [(f32, f32); 4],
    color: i32,
    ao: f32,
    light: i32,
) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color,
        ao,
        u: uvs[vertex].0,
        v: uvs[vertex].1,
        light,
    }
}

fn fluid_side_uses_overlay(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    direction: i32,
) -> bool {
    if state.fluid_type != FLUID_WATER || state.fluid_overlay_valid == 0 {
        return false;
    }
    let (dx, dy, dz) = dir_step(direction);
    let Some(neighbor_id) = neighborhood_state_id(block, dx, dy, dz) else {
        return false;
    };
    let Some(neighbor) = state_by_id(states, neighbor_id) else {
        return false;
    };
    (neighbor.flags & STATE_FLAG_AIR) == 0 && (neighbor.flags & STATE_FLAG_CAN_OCCLUDE) == 0
}

fn sprite_u(sprite: FluidSprite, value: f32) -> f32 {
    sprite.u0 + (sprite.u1 - sprite.u0) * value
}

fn sprite_v(sprite: FluidSprite, value: f32) -> f32 {
    sprite.v0 + (sprite.v1 - sprite.v0) * value
}

fn mth_sin(value: f32) -> f32 {
    mth_sin_table()[((value * 10430.378_f32) as i32 & 65_535) as usize]
}

fn mth_cos(value: f32) -> f32 {
    mth_sin_table()[((value * 10430.378_f32 + 16_384.0_f32) as i32 & 65_535) as usize]
}

fn mth_sin_table() -> &'static [f32; 65_536] {
    static TABLE: OnceLock<Box<[f32; 65_536]>> = OnceLock::new();
    TABLE
        .get_or_init(|| {
            let mut table = vec![0.0_f32; 65_536].into_boxed_slice();
            for (index, value) in table.iter_mut().enumerate() {
                *value =
                    ((index as f64) * std::f64::consts::PI * 2.0 / 65_536.0).sin() as f32;
            }
            match table.try_into() {
                Ok(table) => table,
                Err(_) => unreachable!("native fluid sine table has a fixed length"),
            }
        })
        .as_ref()
}

#[cfg(test)]
pub(super) fn flowing_top_trig_for_test(flow_x: f32, flow_z: f32) -> (f32, f32, f32) {
    let dir = (mth_atan2(flow_z as f64, flow_x as f64) as f32) - 1.5707964_f32;
    (dir, mth_sin(dir), mth_cos(dir))
}

fn mth_atan2(mut y: f64, mut x: f64) -> f64 {
    let square = x * x + y * y;
    if square.is_nan() {
        return f64::NAN;
    }

    let y_negative = y < 0.0;
    if y_negative {
        y = -y;
    }

    let x_negative = x < 0.0;
    if x_negative {
        x = -x;
    }

    let swapped = y > x;
    if swapped {
        std::mem::swap(&mut x, &mut y);
    }

    let inv = mth_fast_inv_sqrt(square);
    x *= inv;
    y *= inv;

    let biased = f64::from_bits(4_805_340_802_404_319_232_u64) + y;
    let index = (biased.to_bits() as u32 as usize).min(256);
    let asin = (index as f64 / 256.0).asin();
    let cos = asin.cos();
    let lookup = biased - f64::from_bits(4_805_340_802_404_319_232_u64);
    let delta = y * cos - x * lookup;
    let correction = (6.0 + delta * delta) * delta * (1.0 / 6.0);
    let mut angle = asin + correction;

    if swapped {
        angle = std::f64::consts::FRAC_PI_2 - angle;
    }
    if x_negative {
        angle = std::f64::consts::PI - angle;
    }
    if y_negative {
        angle = -angle;
    }

    angle
}

fn mth_fast_inv_sqrt(value: f64) -> f64 {
    let half = 0.5 * value;
    let bits = 6_910_469_410_427_058_090_u64.wrapping_sub(value.to_bits() >> 1);
    let estimate = f64::from_bits(bits);
    estimate * (1.5 - half * estimate * estimate)
}

#[inline(always)]
fn sprite_mid_u(sprite: FluidSprite) -> f32 {
    (sprite.u0 + sprite.u1) * 0.5
}

#[inline(always)]
fn sprite_mid_v(sprite: FluidSprite) -> f32 {
    (sprite.v0 + sprite.v1) * 0.5
}

fn still_fluid_top_uvs(sprite: FluidSprite) -> [(f32, f32); 4] {
    if sprite.shrink == 0.0 {
        return [
            (sprite.u0, sprite.v0),
            (sprite.u0, sprite.v1),
            (sprite.u1, sprite.v1),
            (sprite.u1, sprite.v0),
        ];
    }
    let mid_u = sprite_mid_u(sprite);
    let mid_v = sprite_mid_v(sprite);
    let u0 = sprite.u0 + (mid_u - sprite.u0) * sprite.shrink;
    let u1 = sprite.u1 + (mid_u - sprite.u1) * sprite.shrink;
    let v0 = sprite.v0 + (mid_v - sprite.v0) * sprite.shrink;
    let v1 = sprite.v1 + (mid_v - sprite.v1) * sprite.shrink;
    [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
}

fn shrink_fluid_uvs(mut uvs: [(f32, f32); 4], shrink: f32) -> [(f32, f32); 4] {
    if shrink == 0.0 {
        return uvs;
    }
    let avg_u = (uvs[0].0 + uvs[1].0 + uvs[2].0 + uvs[3].0) * 0.25;
    let avg_v = (uvs[0].1 + uvs[1].1 + uvs[2].1 + uvs[3].1) * 0.25;
    for uv in &mut uvs {
        uv.0 += (avg_u - uv.0) * shrink;
        uv.1 += (avg_v - uv.1) * shrink;
    }
    uvs
}

fn fluid_height(
    block: &NativeSectionBlockRecord,
    center: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    dx: i32,
    dy: i32,
    dz: i32,
    fallback_neighbor_index: usize,
) -> f32 {
    let state_id = neighborhood_state_id(block, dx, dy, dz)
        .unwrap_or_else(|| block.neighbor_state_ids[fallback_neighbor_index]);
    let Some(sample) = state_by_id(states, state_id) else {
        return 0.0;
    };
    if sample.fluid_type == center.fluid_type && sample.fluid_type != 0 {
        if neighborhood_state_id(block, dx, dy + 1, dz)
            .and_then(|above_id| state_by_id(states, above_id))
            .map(|above| above.fluid_type == center.fluid_type)
            .unwrap_or(false)
        {
            1.0
        } else {
            sample.fluid_own_height
        }
    } else if (sample.flags & STATE_FLAG_BLOCKS_MOTION) == 0 {
        0.0
    } else {
        -1.0
    }
}

fn fluid_corner_height(
    block: &NativeSectionBlockRecord,
    center_state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    center: f32,
    hx: f32,
    hz: f32,
    dx: i32,
    dy: i32,
    dz: i32,
) -> f32 {
    if hx >= 1.0 || hz >= 1.0 {
        return 1.0;
    }
    let mut total = 0.0;
    let mut samples = 0.0;
    if hx > 0.0 || hz > 0.0 {
        let diagonal = fluid_height(block, center_state, states, dx, dy, dz, 0);
        if diagonal >= 1.0 {
            return 1.0;
        }
        modify_fluid_height(&mut total, &mut samples, diagonal);
    }
    modify_fluid_height(&mut total, &mut samples, center);
    modify_fluid_height(&mut total, &mut samples, hx);
    modify_fluid_height(&mut total, &mut samples, hz);
    if samples == 0.0 {
        0.0
    } else {
        total / samples
    }
}

fn modify_fluid_height(total: &mut f32, samples: &mut f32, height: f32) {
    if height >= 0.8 {
        *total += height * 10.0;
        *samples += 10.0;
    } else if height >= 0.0 {
        *total += height;
        *samples += 1.0;
    }
}

fn fluid_side_occluded(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
    dir: i32,
) -> bool {
    let neighbor_id = block.neighbor_state_ids[dir as usize];
    let Some(neighbor) = state_by_id(states, neighbor_id) else {
        return false;
    };
    neighbor.fluid_type == state.fluid_type
        || ((neighbor.flags & STATE_FLAG_FULL_OCCLUSION) != 0 && dir != 1)
}

fn fluid_side_exposed(
    block: &NativeSectionBlockRecord,
    states: &[Option<NativeMeshingState>],
    dx: i32,
    dy: i32,
    dz: i32,
    _height: f32,
) -> bool {
    neighborhood_state_id(block, dx, dy, dz)
        .and_then(|id| state_by_id(states, id))
        .map(|state| {
            (state.flags & STATE_FLAG_CAN_OCCLUDE) == 0
                || (state.flags & STATE_FLAG_FULL_OCCLUSION) == 0
        })
        .unwrap_or(true)
}

fn fluid_backward_up_face(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    states: &[Option<NativeMeshingState>],
) -> bool {
    for dz in -1..=1 {
        for dx in -1..=1 {
            let Some(id) = neighborhood_state_id(block, dx, 1, dz) else {
                return true;
            };
            let Some(sample) = state_by_id(states, id) else {
                return true;
            };
            if sample.fluid_type != state.fluid_type
                && (sample.flags & STATE_FLAG_SOLID_RENDER) == 0
            {
                return true;
            }
        }
    }
    false
}

pub(super) fn fluid_face_record_to_quad(record: FluidFaceRecord) -> Result<NativeQuad, i32> {
    let mut vertices = match record.face_kind {
        // Top face, diagonal from north-east to south-west.
        0 => [
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[3],
                record.origin_z,
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[0],
                record.origin_z,
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[1],
                record.origin_z + 1.0,
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[2],
                record.origin_z + 1.0,
                3,
                record,
            ),
        ],
        // Top face, diagonal from north-west to south-east.
        1 => [
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[0],
                record.origin_z,
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.heights[1],
                record.origin_z + 1.0,
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[2],
                record.origin_z + 1.0,
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.heights[3],
                record.origin_z,
                3,
                record,
            ),
        ],
        // Bottom face.
        2 => [
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.y_offset,
                record.origin_z + 1.0,
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x,
                record.origin_y + record.y_offset,
                record.origin_z,
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.y_offset,
                record.origin_z,
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + 1.0,
                record.origin_y + record.y_offset,
                record.origin_z + 1.0,
                3,
                record,
            ),
        ],
        // Horizontal side face. side_coords = x1,z1,x2,z2 and heights = c1,c2,...
        3 => [
            fluid_vertex(
                record.origin_x + record.side_coords[2],
                record.origin_y + record.heights[1],
                record.origin_z + record.side_coords[3],
                0,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[2],
                record.origin_y + record.y_offset,
                record.origin_z + record.side_coords[3],
                1,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[0],
                record.origin_y + record.y_offset,
                record.origin_z + record.side_coords[1],
                2,
                record,
            ),
            fluid_vertex(
                record.origin_x + record.side_coords[0],
                record.origin_y + record.heights[0],
                record.origin_z + record.side_coords[1],
                3,
                record,
            ),
        ],
        _ => return Err(ERR_INVALID_ARGUMENT),
    };

    if record.flip != 0 {
        vertices = [vertices[0], vertices[3], vertices[2], vertices[1]];
    }

    Ok(NativeQuad {
        vertices,
        block_emission: record.block_emission.clamp(0, 255) as u8,
        render_type: record.render_type.clamp(0, 255) as u8,
        ignore_mid_block: if record.ignore_mid_block != 0 { 1 } else { 0 },
        _padding: 0,
        block_id: record.block_id,
        local_x: record.local_x,
        local_y: record.local_y,
        local_z: record.local_z,
        material_bits: record.material_bits,
    })
}

fn fluid_vertex(x: f32, y: f32, z: f32, vertex: usize, record: FluidFaceRecord) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color: record.colors[vertex],
        ao: record.aos[vertex],
        u: record.uvs[vertex * 2],
        v: record.uvs[vertex * 2 + 1],
        light: record.lights[vertex],
    }
}
