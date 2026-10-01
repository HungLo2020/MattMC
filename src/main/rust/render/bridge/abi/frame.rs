//! Frame surface, acquire, resize, present and readback records.

use super::*;

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameSurfaceConfigRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub extent: FfiExtent3d,
    pub color_format: u32,
    pub present_mode: u32,
    pub max_frames_in_flight: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameAcquireRequest {
    pub header: FfiHeader,
    pub correlation_id: u64,
    pub expected_extent: FfiExtent3d,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameAcquireResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub acquire_status: u32,
    pub frame_target: FfiHandle,
    pub frame_target_identity: u64,
    pub extent: FfiExtent3d,
    pub color_format: u32,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiFrameAcquireResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            frame_id: 0,
            correlation_id: 0,
            acquire_status: 0,
            frame_target: FfiHandle::default(),
            frame_target_identity: 0,
            extent: FfiExtent3d::default(),
            color_format: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameResizeRequest {
    pub header: FfiHeader,
    pub correlation_id: u64,
    pub extent: FfiExtent3d,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameResizeResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub resize_status: u32,
    pub extent: FfiExtent3d,
}

impl Default for FfiFrameResizeResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            resize_status: 0,
            extent: FfiExtent3d::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFramePresentRequest {
    pub header: FfiHeader,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub wait_submission_id: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFramePresentResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub frame_id: u64,
    pub correlation_id: u64,
    pub present_status: u32,
    pub completed_submission_id: u64,
    pub frame_target_identity: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiFrameCancelRequest {
    pub header: FfiHeader,
    pub frame_id: u64,
    pub correlation_id: u64,
}

impl Default for FfiFramePresentResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            frame_id: 0,
            correlation_id: 0,
            present_status: 0,
            completed_submission_id: 0,
            frame_target_identity: 0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiReadbackRequest {
    pub header: FfiHeader,
    pub submission_id: u64,
    pub buffer: FfiHandle,
    pub offset: u64,
    pub size: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiReadbackResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub submission_id: u64,
    pub required_bytes: u64,
    pub written_bytes: u64,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiReadbackResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            submission_id: 0,
            required_bytes: 0,
            written_bytes: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}
