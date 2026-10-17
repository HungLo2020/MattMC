//! Model semantic facts become the existing backend-neutral instance format
//! once, at production. Deriving a variant never mutates the original.
use crate::render::bridge::abi::FfiWorldMeshInstanceRecord;

const RIG: u32 = 0x4000_0000;
const ENTITY: u32 = 67;
const SHADOW: u32 = 69;

#[repr(C)]
pub struct Input {
    pub rig: u64,
    pub first_pose: u32,
    pub flags: u32,
    pub depth: u32,
    pub cull: u32,
    pub color: u32,
    pub entity_color: u32,
    pub outline: u32,
    pub packed_light: i32,
    pub width: i32,
    pub height: i32,
    pub block_id: i32,
    pub order: i32,
    pub order_mode: u32,
    pub foil_mode: u32,
    pub foil_clock: u64,
    pub foil_speed: f64,
    pub foil_strength: f32,
    pub reserved: u32,
    pub transform: [f32; 16],
    pub culling_mode: u32,
    pub culling_flags: u32,
    pub bounds: [f64; 6],
    pub leash_bounds: [f64; 6],
    pub camera: [f64; 3],
}

fn valid(record: &FfiWorldMeshInstanceRecord) -> bool {
    let flags = record.flags & !RIG;
    let layering = flags & 12;
    record.mesh_key != 0
        && record.mesh_generation != 0
        && record.block_entity_id >= -1
        && record.depth_policy <= 3
        && (record.depth_policy != 3 || (record.stratum == ENTITY && record.item_foil_mode == 0))
        && record.packed_light <= 0x00ff00ff
        && flags & !15 == 0
        && flags & 2 == 0
        && (flags & 1 == 0 || (record.stratum == ENTITY && record.outline_color_argb != 0))
        && (layering == 0
            || (layering != 12
                && flags & !12 == 0
                && record.stratum == ENTITY
                && record.item_foil_mode == 0
                && record.block_entity_id == -1))
        && (record.item_foil_mode == 0
            || (record.stratum == ENTITY && flags == 0 && record.block_entity_id == -1))
        && (record.item_foil_mode == 0
            || (record.item_foil_mode <= 4
                && record.item_foil_clock_millis <= i64::MAX as u64
                && record.item_foil_speed.is_finite()
                && (0.0..=1.0).contains(&record.item_foil_speed)
                && record.item_foil_strength.is_finite()
                && (0.0..=1.0).contains(&record.item_foil_strength)))
        && (record.model_submission_order_mode == 0 || record.stratum == ENTITY)
        && (record.entity_culling_mode == 0 || record.block_entity_id == -1)
}

pub(super) fn produce(input: &Input) -> Option<FfiWorldMeshInstanceRecord> {
    if input.reserved != 0 || input.order_mode > 1 || input.culling_mode > 1 || input.foil_mode > 4
    {
        return None;
    }
    // Every field is a scalar or scalar array. Absent optional lanes are
    // canonical zeroes; no pointer or Rust ownership is embedded in this POD.
    let mut result: FfiWorldMeshInstanceRecord = unsafe { std::mem::zeroed() };
    result.byte_size = std::mem::size_of::<FfiWorldMeshInstanceRecord>() as u32;
    result.stratum = ENTITY;
    result.mesh_section_index = u32::MAX;
    result.winding = 1;
    result.mesh_key = input.rig;
    result.mesh_generation = u64::from(input.first_pose) + 1;
    result.flags = input.flags | RIG;
    result.depth_policy = input.depth;
    result.cull_policy = input.cull;
    result.color_argb = input.color;
    result.entity_color_argb = input.entity_color;
    result.outline_color_argb = input.outline;
    result.packed_light = input.packed_light as u32;
    result.viewport_width = input.width;
    result.viewport_height = input.height;
    result.block_entity_id = input.block_id;
    result.model_submission_order_mode = input.order_mode;
    result.model_submission_order = if input.order_mode == 0 {
        0
    } else {
        input.order
    };
    result.item_foil_mode = input.foil_mode;
    if input.foil_mode != 0 {
        result.item_foil_clock_millis = input.foil_clock;
        result.item_foil_speed = input.foil_speed;
        result.item_foil_strength = input.foil_strength;
    }
    result.transform = input.transform;
    result.entity_culling_mode = input.culling_mode;
    if input.culling_mode != 0 {
        result.entity_culling_flags = input.culling_flags;
        result.entity_culling_bounds = input.bounds;
        result.entity_culling_leash_bounds = input.leash_bounds;
        result.entity_culling_camera = input.camera;
    }
    valid(&result).then_some(result)
}

pub(super) fn variant(
    mut record: FfiWorldMeshInstanceRecord,
    kind: u32,
    a: i64,
    b: i64,
    speed: f64,
    strength: f32,
) -> Option<FfiWorldMeshInstanceRecord> {
    match kind {
        1 => {
            record.viewport_width = a as i32;
            record.viewport_height = b as i32;
        }
        2 => record.packed_light = a as u32,
        3 => {
            record.model_submission_order_mode = u32::from(b != 0);
            record.model_submission_order = if b == 0 { 0 } else { a as i32 };
        }
        4 => {
            if record.stratum != ENTITY
                || record.item_foil_mode != 0
                || record.block_entity_id != -1
                || record.flags & 1 != 0
            {
                return None;
            }
            record.stratum = SHADOW;
            if record.depth_policy == 3 {
                record.depth_policy = 1;
            }
            record.outline_color_argb = 0;
            record.flags &= RIG;
            record.model_submission_order_mode = 0;
            record.model_submission_order = 0;
        }
        5 => {
            if !(1..=4).contains(&a) {
                return None;
            }
            record.item_foil_mode = a as u32;
            record.item_foil_clock_millis = b as u64;
            record.item_foil_speed = speed;
            record.item_foil_strength = strength;
        }
        _ => return None,
    }
    valid(&record).then_some(record)
}
