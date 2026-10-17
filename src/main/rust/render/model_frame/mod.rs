//! Frame-local model production. The producer writes bounded immutable CPU
//! pose facts; Rust owns instances, variants and storage through submission.
//! No renderer resource, command buffer or presenter is exposed here.
use crate::render::bridge::abi::{
    FfiModelRigPose, FfiWorldMeshInstanceRecord, FFI_MAX_BATCH_ITEMS,
};

mod ffi;
mod instance;
#[cfg(test)]
mod tests;

const MAX_NODES: usize = 1024;
const MAX_POSES: usize = FFI_MAX_BATCH_ITEMS * MAX_NODES;
// Base instances, shadow variants and viewport projections share this owner.
const MAX_VERSIONS: usize = FFI_MAX_BATCH_ITEMS * 4;

/// Private CPU view. Capacity bounds borrowed views; counts bound live values.
/// Java refreshes both views after any call that may grow their allocation.
#[repr(C)]
pub struct Header {
    instances: *const FfiWorldMeshInstanceRecord,
    poses: *mut FfiModelRigPose,
    instance_capacity: u32,
    pose_capacity: u32,
    instance_count: u32,
    pose_count: u32,
    versions: *const u64,
    version_capacity: u32,
    reserved: u32,
}

#[repr(C)]
pub struct Frame {
    header: Header,
    instances: Vec<FfiWorldMeshInstanceRecord>,
    poses: Vec<FfiModelRigPose>,
    versions: Vec<u64>,
    next_version: u64,
    reserved_poses: usize,
    sealed: bool,
}

impl Default for Frame {
    fn default() -> Self {
        Self {
            header: Header {
                instances: std::ptr::null(),
                poses: std::ptr::null_mut(),
                instance_capacity: 0,
                pose_capacity: 0,
                instance_count: 0,
                pose_count: 0,
                versions: std::ptr::null(),
                version_capacity: 0,
                reserved: 0,
            },
            instances: Vec::new(),
            poses: Vec::new(),
            versions: Vec::new(),
            next_version: 0,
            reserved_poses: 0,
            sealed: false,
        }
    }
}

impl Frame {
    fn refresh(&mut self) {
        self.header.instances = self.instances.as_ptr();
        self.header.poses = self.poses.as_mut_ptr();
        self.header.instance_capacity = self.instances.capacity() as u32;
        self.header.pose_capacity = self.poses.capacity() as u32;
        self.header.instance_count = self.instances.len() as u32;
        self.header.versions = self.versions.as_ptr();
        self.header.version_capacity = self.versions.capacity() as u32;
    }

    fn reserve_poses(&mut self, count: usize) -> Option<usize> {
        let first = self.header.pose_count as usize;
        if self.sealed || count == 0 || count > MAX_NODES || count > MAX_POSES - first {
            return None;
        }
        // An interrupted producer can leave a reservation, but no live poses.
        self.poses.truncate(first);
        self.reserved_poses = 0;
        self.poses.try_reserve(count).ok()?;
        self.poses.resize(
            first + count,
            FfiModelRigPose {
                offset: [0.0; 3],
                rotation: [0.0; 3],
                scale: [1.0; 3],
                flags: 0,
            },
        );
        self.reserved_poses = count;
        self.refresh();
        Some(first)
    }

    fn commit_poses(&mut self, first: usize, count: usize) -> bool {
        if self.sealed
            || first != self.header.pose_count as usize
            || count == 0
            || count != self.reserved_poses
        {
            return false;
        }
        self.header.pose_count += count as u32;
        self.reserved_poses = 0;
        true
    }

    fn append(&mut self, input: &instance::Input) -> Option<usize> {
        if self.sealed || input.first_pose >= self.header.pose_count {
            return None;
        }
        self.push(instance::produce(input)?)
    }

    fn push(&mut self, instance: FfiWorldMeshInstanceRecord) -> Option<usize> {
        if self.instances.len() == MAX_VERSIONS || self.next_version == u64::MAX {
            return None;
        }
        let reserved =
            self.instances.try_reserve(1).is_ok() && self.versions.try_reserve(1).is_ok();
        // The first reserve may have moved storage even if the second failed.
        self.refresh();
        if !reserved {
            return None;
        }
        let index = self.instances.len();
        self.next_version += 1;
        self.instances.push(instance);
        self.versions.push(self.next_version);
        self.refresh();
        Some(index)
    }

    fn variant(
        &mut self,
        index: usize,
        kind: u32,
        a: i64,
        b: i64,
        speed: f64,
        strength: f32,
    ) -> Option<usize> {
        let original = *self.instances.get(index)?;
        self.push(instance::variant(original, kind, a, b, speed, strength)?)
    }

    fn rollback(&mut self, instances: usize, poses: usize) -> bool {
        if self.sealed
            || instances > self.instances.len()
            || poses > self.header.pose_count as usize
        {
            return false;
        }
        self.instances.truncate(instances);
        self.versions.truncate(instances);
        self.poses.truncate(poses);
        self.header.pose_count = poses as u32;
        self.reserved_poses = 0;
        self.refresh();
        true
    }

    fn seal(&mut self) {
        self.poses.truncate(self.header.pose_count as usize);
        self.reserved_poses = 0;
        self.sealed = true;
        // Immutable instance variants may still be appended by viewport/shadow
        // projection. Existing indices and all published poses stay unchanged.
        self.refresh();
    }
}
