use super::*;

fn expand_rig(
    instance: &FfiWorldMeshInstanceRecord,
    poses: &[FfiModelRigPose],
    out: &mut Vec<FfiWorldMeshInstanceRecord>,
) -> GalResult<()> {
    expand_model_rig(&lock_model_rigs().unwrap(), instance, poses, &mut Vec::new(), out)
}

fn node(parent: i32, mesh_key: u64) -> FfiModelRigNode {
    FfiModelRigNode {
        parent,
        flags: if mesh_key == 0 { 0 } else { MODEL_RIG_NODE_HAS_MESH },
        mesh_key,
        mesh_generation: if mesh_key == 0 { 0 } else { mesh_key + 1000 },
    }
}

fn pose(offset: [f32; 3], rotation: [f32; 3], flags: u32) -> FfiModelRigPose {
    FfiModelRigPose { offset, rotation, scale: [1.0; 3], flags }
}

fn instance(rig: u64, first_pose: u64, transform: Matrix) -> FfiWorldMeshInstanceRecord {
    // SAFETY: the FFI record is plain old data; zero is a valid bit pattern.
    let mut record: FfiWorldMeshInstanceRecord = unsafe { std::mem::zeroed() };
    record.mesh_key = rig;
    record.mesh_generation = first_pose + 1;
    record.transform = transform;
    record.flags = WORLD_MESH_INSTANCE_FLAG_MODEL_RIG | 4;
    record.packed_light = 0x00f0_00f0;
    record
}

fn translation(x: f32, y: f32, z: f32) -> Matrix {
    let mut matrix = IDENTITY;
    matrix[12] = x;
    matrix[13] = y;
    matrix[14] = z;
    matrix
}

fn close(a: &Matrix, b: &Matrix) -> bool {
    a.iter().zip(b).all(|(x, y)| (x - y).abs() < 1e-6)
}

const VISIBLE: u32 = MODEL_RIG_POSE_VISIBLE;

#[test]
fn parts_compose_parent_poses_in_visit_order() {
    // root (no cubes) -> body -> head; root -> tail.
    register(9001, &[node(-1, 0), node(0, 10), node(1, 11), node(0, 12)]).unwrap();
    let entity = translation(5.0, 6.0, 7.0);
    let poses = [
        pose([0.0; 3], [0.0; 3], VISIBLE),
        pose([0.0, 16.0, 0.0], [0.0; 3], VISIBLE),
        pose([0.0, 8.0, 0.0], [0.0, std::f32::consts::FRAC_PI_2, 0.0], VISIBLE),
        pose([16.0, 0.0, 0.0], [0.0; 3], VISIBLE),
    ];
    let mut out = Vec::new();
    expand_rig(&instance(9001, 0, entity), &poses, &mut out).unwrap();
    assert_eq!(vec![10, 11, 12], out.iter().map(|part| part.mesh_key).collect::<Vec<_>>());
    assert_eq!(vec![1010, 1011, 1012], out.iter().map(|part| part.mesh_generation).collect::<Vec<_>>());
    assert!(out.iter().all(|part| part.flags == 4 && part.packed_light == 0x00f0_00f0));
    assert!(close(&out[0].transform, &translation(5.0, 7.0, 7.0)));
    // The head turns a quarter about Y on top of the body's offset.
    let expected_head = mul(&translation(5.0, 7.5, 7.0), &[0.0, 0.0, -1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0]);
    assert!(close(&out[1].transform, &expected_head), "{:?}", out[1].transform);
    assert!(close(&out[2].transform, &translation(6.0, 6.0, 7.0)));
    release(9001);
}

#[test]
fn hidden_parts_hide_their_subtree_and_skip_draw_keeps_children() {
    register(9002, &[node(-1, 0), node(0, 10), node(1, 11), node(0, 12)]).unwrap();
    let mut out = Vec::new();
    let hidden_body = [
        pose([0.0; 3], [0.0; 3], VISIBLE),
        pose([0.0; 3], [0.0; 3], 0),
        pose([0.0; 3], [0.0; 3], VISIBLE),
        pose([0.0; 3], [0.0; 3], VISIBLE),
    ];
    expand_rig(&instance(9002, 0, IDENTITY), &hidden_body, &mut out).unwrap();
    assert_eq!(vec![12], out.iter().map(|part| part.mesh_key).collect::<Vec<_>>());

    out.clear();
    let skipped_body = [
        pose([0.0; 3], [0.0; 3], VISIBLE),
        pose([0.0; 3], [0.0; 3], VISIBLE | MODEL_RIG_POSE_SKIP_DRAW),
        pose([0.0; 3], [0.0; 3], VISIBLE),
        pose([0.0; 3], [0.0; 3], VISIBLE),
    ];
    expand_rig(&instance(9002, 0, IDENTITY), &skipped_body, &mut out).unwrap();
    assert_eq!(vec![11, 12], out.iter().map(|part| part.mesh_key).collect::<Vec<_>>());
    release(9002);
}

#[test]
fn poses_are_read_from_the_instance_offset_and_bounds_are_enforced() {
    register(9003, &[node(-1, 20)]).unwrap();
    let poses = [pose([0.0; 3], [0.0; 3], 0), pose([16.0, 0.0, 0.0], [0.0; 3], VISIBLE)];
    let mut out = Vec::new();
    expand_rig(&instance(9003, 1, IDENTITY), &poses, &mut out).unwrap();
    assert!(close(&out[0].transform, &translation(1.0, 0.0, 0.0)));
    assert!(expand_rig(&instance(9003, 2, IDENTITY), &poses, &mut out).is_err());
    assert!(expand_rig(&instance(9999, 0, IDENTITY), &poses, &mut out).is_err());
    assert!(release(9003));
    assert!(!release(9003));
}

#[test]
fn invalid_rigs_are_rejected() {
    assert!(register(0, &[node(-1, 1)]).is_err());
    assert!(register(9004, &[]).is_err());
    assert!(register(9004, &[node(1, 1), node(-1, 2)]).is_err());
    let mut bad = node(-1, 1);
    bad.mesh_generation = 0;
    assert!(register(9004, &[bad]).is_err());
}

#[test]
fn rotation_matches_joml_rotate_zyx_order() {
    let angles = [0.3f32, -1.1, 2.4];
    let part = pose([0.0; 3], angles, VISIBLE);
    let rotated = translate_and_rotate(IDENTITY, &part);
    // Rz * Ry * Rx applied to the X axis.
    let (sx, sy, sz) = (angles[0].sin(), angles[1].sin(), angles[2].sin());
    let (cx, cy, cz) = (angles[0].cos(), angles[1].cos(), angles[2].cos());
    let _ = (sx, cx);
    let x_axis = [cz * cy, sz * cy, -sy];
    assert!((rotated[0] - x_axis[0]).abs() < 1e-6 && (rotated[1] - x_axis[1]).abs() < 1e-6 && (rotated[2] - x_axis[2]).abs() < 1e-6);
    assert!((cos_from_sin(sin(2.4), 2.4) - 2.4f32.cos()).abs() < 1e-6);
    assert!((cos_from_sin(sin(-2.4), -2.4) - (-2.4f32).cos()).abs() < 1e-6);
}
