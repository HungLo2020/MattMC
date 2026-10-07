//! Rust-owned model rigs: the `ModelPart` tree of an entity model and each
//! part's immutable local-space mesh asset, registered once by Java.
//!
//! Each frame Java sends one entity mesh instance per model (flagged with
//! [`WORLD_MESH_INSTANCE_FLAG_MODEL_RIG`]; `mesh_key` is the rig id and
//! `mesh_generation - 1` the index of its first pose) plus the raw pose
//! fields of every rig node after `setupAnim`. Rust composes the part
//! hierarchy exactly as `ModelPart.visitRenderable` does (pre-order, hidden
//! subtrees skipped, translate / ZYX rotate / scale) and expands one ordinary
//! instance per drawn part with `entity * part` as its transform.

use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

use crate::render::bridge::abi::{FfiModelRigNode, FfiModelRigPose, FfiWorldMeshInstanceRecord};
use crate::render::vulkanic::{GalError, GalResult};

/// Instance flag marking a model-rig instance (never reaches the frontend).
pub const WORLD_MESH_INSTANCE_FLAG_MODEL_RIG: u32 = 0x4000_0000;
/// `FfiModelRigNode.flags`: the node owns a mesh (it has cubes).
pub const MODEL_RIG_NODE_HAS_MESH: u32 = 1;
/// `FfiModelRigPose.flags`: `ModelPart.visible`.
pub const MODEL_RIG_POSE_VISIBLE: u32 = 1;
/// `FfiModelRigPose.flags`: `ModelPart.skipDraw`.
pub const MODEL_RIG_POSE_SKIP_DRAW: u32 = 2;
/// Bound on nodes per rig (vanilla models have well under a hundred).
pub const MAX_MODEL_RIG_NODES: usize = 1024;
/// Bound on registered rigs (Java caches at most 512 model topologies).
const MAX_MODEL_RIGS: usize = 4096;

#[derive(Clone, Copy, Debug, PartialEq)]
struct RigNode {
    parent: Option<usize>,
    mesh: Option<(u64, u64)>,
}

fn rigs() -> &'static Mutex<HashMap<u64, Arc<[RigNode]>>> {
    static RIGS: OnceLock<Mutex<HashMap<u64, Arc<[RigNode]>>>> = OnceLock::new();
    RIGS.get_or_init(Default::default)
}

fn register(id: u64, nodes: &[FfiModelRigNode]) -> GalResult<()> {
    if id == 0 || nodes.is_empty() || nodes.len() > MAX_MODEL_RIG_NODES {
        return Err(GalError::invalid_argument("model rig needs a nonzero id and 1..=1024 nodes"));
    }
    let mut decoded = Vec::with_capacity(nodes.len());
    for (index, node) in nodes.iter().enumerate() {
        let parent = match node.parent {
            -1 => None,
            parent if parent >= 0 && (parent as usize) < index => Some(parent as usize),
            _ => return Err(GalError::invalid_argument("model rig nodes must follow their parent")),
        };
        if node.flags & !MODEL_RIG_NODE_HAS_MESH != 0 {
            return Err(GalError::invalid_argument("unknown model rig node flags"));
        }
        let mesh = if node.flags & MODEL_RIG_NODE_HAS_MESH != 0 {
            if node.mesh_key == 0 || node.mesh_generation == 0 {
                return Err(GalError::invalid_argument("model rig mesh identity must be non-zero"));
            }
            Some((node.mesh_key, node.mesh_generation))
        } else {
            None
        };
        decoded.push(RigNode { parent, mesh });
    }
    let mut rigs = rigs().lock().map_err(|_| GalError::invalid_argument("model rig registry poisoned"))?;
    if !rigs.contains_key(&id) && rigs.len() >= MAX_MODEL_RIGS {
        return Err(GalError::invalid_argument("model rig registry is full"));
    }
    rigs.insert(id, decoded.into());
    Ok(())
}

fn release(id: u64) -> bool {
    rigs().lock().map(|mut rigs| rigs.remove(&id).is_some()).unwrap_or(false)
}

fn rig(id: u64) -> GalResult<Arc<[RigNode]>> {
    rigs()
        .lock()
        .map_err(|_| GalError::invalid_argument("model rig registry poisoned"))?
        .get(&id)
        .cloned()
        .ok_or_else(|| GalError::invalid_argument(format!("unknown model rig {id}")))
}

/// Registers (or replaces) a rig. Returns 0, or -2 for an invalid rig.
///
/// # Safety
/// `nodes` addresses `count` records.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_world_model_rig_register(
    id: u64,
    nodes: *const FfiModelRigNode,
    count: u32,
) -> i32 {
    if nodes.is_null() || count == 0 {
        return -2;
    }
    match register(id, std::slice::from_raw_parts(nodes, count as usize)) {
        Ok(()) => 0,
        Err(_) => -2,
    }
}

/// Releases a rig; 1 when it was registered. Frames decode synchronously at
/// submit, so a released rig is never referenced by a pending frame.
#[no_mangle]
pub extern "C" fn mattmc_vulkanic_world_model_rig_release(id: u64) -> i32 {
    i32::from(release(id))
}

type Matrix = [f32; 16];

const IDENTITY: Matrix = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0];

/// Column-major `a * b`.
fn mul(a: &Matrix, b: &Matrix) -> Matrix {
    let mut out = [0.0; 16];
    for column in 0..4 {
        for row in 0..4 {
            out[column * 4 + row] = a[row] * b[column * 4]
                + a[4 + row] * b[column * 4 + 1]
                + a[8 + row] * b[column * 4 + 2]
                + a[12 + row] * b[column * 4 + 3];
        }
    }
    out
}

/// JOML `Math.sin` (double precision, rounded to float).
fn sin(angle: f32) -> f32 {
    f64::from(angle).sin() as f32
}

/// JOML `Math.cosFromSin`.
fn cos_from_sin(sin: f32, angle: f32) -> f32 {
    const PI: f32 = std::f32::consts::PI;
    const PI2: f32 = PI * 2.0;
    let cos = (1.0 - sin * sin).sqrt();
    let a = angle + std::f32::consts::FRAC_PI_2;
    let mut b = a - (a / PI2) as i32 as f32 * PI2;
    if b < 0.0 {
        b += PI2;
    }
    if b >= PI {
        -cos
    } else {
        cos
    }
}

/// `ModelPart.translateAndRotate` applied to `pose`.
fn translate_and_rotate(mut pose: Matrix, part: &FfiModelRigPose) -> Matrix {
    let [x, y, z] = part.offset;
    if x != 0.0 || y != 0.0 || z != 0.0 {
        let (x, y, z) = (x * (1.0 / 16.0), y * (1.0 / 16.0), z * (1.0 / 16.0));
        for row in 0..4 {
            pose[12 + row] = pose[row] * x + pose[4 + row] * y + pose[8 + row] * z + pose[12 + row];
        }
    }
    let [x_rot, y_rot, z_rot] = part.rotation;
    if x_rot != 0.0 || y_rot != 0.0 || z_rot != 0.0 {
        let (sin_x, sin_y, sin_z) = (sin(x_rot), sin(y_rot), sin(z_rot));
        let (cos_x, cos_y, cos_z) = (cos_from_sin(sin_x, x_rot), cos_from_sin(sin_y, y_rot), cos_from_sin(sin_z, z_rot));
        let rotate_z: Matrix = [cos_z, sin_z, 0.0, 0.0, -sin_z, cos_z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0];
        let rotate_y: Matrix = [cos_y, 0.0, -sin_y, 0.0, 0.0, 1.0, 0.0, 0.0, sin_y, 0.0, cos_y, 0.0, 0.0, 0.0, 0.0, 1.0];
        let rotate_x: Matrix = [1.0, 0.0, 0.0, 0.0, 0.0, cos_x, sin_x, 0.0, 0.0, -sin_x, cos_x, 0.0, 0.0, 0.0, 0.0, 1.0];
        pose = mul(&mul(&mul(&pose, &rotate_z), &rotate_y), &rotate_x);
    }
    let [x_scale, y_scale, z_scale] = part.scale;
    if x_scale != 1.0 || y_scale != 1.0 || z_scale != 1.0 {
        for row in 0..4 {
            pose[row] *= x_scale;
            pose[4 + row] *= y_scale;
            pose[8 + row] *= z_scale;
        }
    }
    pose
}

/// Expands a model-rig instance into one ordinary instance per drawn part,
/// in `visitRenderable` order, appending to `out`.
pub(super) fn expand_model_rig(
    instance: &FfiWorldMeshInstanceRecord,
    poses: &[FfiModelRigPose],
    out: &mut Vec<FfiWorldMeshInstanceRecord>,
) -> GalResult<()> {
    let nodes = rig(instance.mesh_key)?;
    let first = usize::try_from(instance.mesh_generation.wrapping_sub(1))
        .map_err(|_| GalError::invalid_argument("model rig pose index out of range"))?;
    let poses = first
        .checked_add(nodes.len())
        .and_then(|end| poses.get(first..end))
        .ok_or_else(|| GalError::invalid_argument("model rig poses out of range"))?;
    let mut models = Vec::with_capacity(nodes.len());
    for (node, pose) in nodes.iter().zip(poses) {
        if pose.flags & !(MODEL_RIG_POSE_VISIBLE | MODEL_RIG_POSE_SKIP_DRAW) != 0 {
            return Err(GalError::invalid_argument("unknown model rig pose flags"));
        }
        let parent = match node.parent {
            Some(parent) => models[parent],
            None => Some(IDENTITY),
        };
        // `visitRenderable` returns before a hidden part's cubes and children.
        let model = parent.filter(|_| pose.flags & MODEL_RIG_POSE_VISIBLE != 0).map(|parent| translate_and_rotate(parent, pose));
        models.push(model);
        let (Some(model), Some((mesh_key, mesh_generation))) = (model, node.mesh) else {
            continue;
        };
        if pose.flags & MODEL_RIG_POSE_SKIP_DRAW != 0 {
            continue;
        }
        let transform = mul(&instance.transform, &model);
        if transform.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument("model rig produced a non-finite part transform"));
        }
        out.push(FfiWorldMeshInstanceRecord {
            mesh_key,
            mesh_generation,
            transform,
            flags: instance.flags & !WORLD_MESH_INSTANCE_FLAG_MODEL_RIG,
            ..*instance
        });
    }
    Ok(())
}

/// Verification export: the column-major part transforms `entity * part`
/// that a rig instance with these poses expands to, in draw order. Returns
/// the part count (written up to `capacity`), or -2 for invalid input.
///
/// # Safety
/// `poses` addresses `pose_count` records, `entity` 16 floats and `out`
/// `capacity * 16` floats.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_world_model_rig_expand_transforms(
    rig_id: u64,
    poses: *const FfiModelRigPose,
    pose_count: u32,
    entity: *const f32,
    out: *mut f32,
    capacity: u32,
) -> i32 {
    if poses.is_null() || entity.is_null() || (capacity != 0 && out.is_null()) {
        return -2;
    }
    // SAFETY: the FFI record is plain old data; zero is a valid bit pattern.
    let mut instance: FfiWorldMeshInstanceRecord = std::mem::zeroed();
    instance.mesh_key = rig_id;
    instance.mesh_generation = 1;
    instance.transform = std::ptr::read_unaligned(entity.cast());
    let mut parts = Vec::new();
    if expand_model_rig(&instance, std::slice::from_raw_parts(poses, pose_count as usize), &mut parts).is_err() {
        return -2;
    }
    for (index, part) in parts.iter().take(capacity as usize).enumerate() {
        std::ptr::copy_nonoverlapping(part.transform.as_ptr(), out.add(index * 16), 16);
    }
    parts.len() as i32
}

#[cfg(test)]
mod tests;
