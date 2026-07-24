use std::ffi::c_void;
use std::ptr;

use libloading::os::unix::{Library, RTLD_NOW};

const RENDERDOC_API_VERSION_1_0_0: i32 = 10000;

type RenderDocGetApi = unsafe extern "C" fn(i32, *mut *mut c_void) -> i32;
type RenderDocStartFrameCapture = unsafe extern "C" fn(*mut c_void, *mut c_void);
type RenderDocEndFrameCapture = unsafe extern "C" fn(*mut c_void, *mut c_void) -> u32;

#[repr(C)]
struct RenderDocApiV1_0_0 {
    get_api_version: *const c_void,
    set_capture_option_u32: *const c_void,
    set_capture_option_f32: *const c_void,
    get_capture_option_u32: *const c_void,
    get_capture_option_f32: *const c_void,
    set_focus_toggle_keys: *const c_void,
    set_capture_keys: *const c_void,
    get_overlay_bits: *const c_void,
    mask_overlay_bits: *const c_void,
    shutdown: *const c_void,
    unload_crash_handler: *const c_void,
    set_capture_file_path_template: *const c_void,
    get_capture_file_path_template: *const c_void,
    get_num_captures: *const c_void,
    get_capture: *const c_void,
    trigger_capture: *const c_void,
    is_remote_access_connected: *const c_void,
    launch_replay_ui: *const c_void,
    set_active_window: *const c_void,
    start_frame_capture: RenderDocStartFrameCapture,
    is_frame_capturing: *const c_void,
    end_frame_capture: RenderDocEndFrameCapture,
}

pub(super) struct RenderDocFrame {
    api: *mut RenderDocApiV1_0_0,
    _library: Library,
    active: bool,
}

impl RenderDocFrame {
    pub(super) fn start_if_requested() -> Option<Self> {
        if !std::env::var("MATTMC_RENDERDOC_CAPTURE")
            .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
            .unwrap_or(false)
        {
            return None;
        }
        unsafe {
            let library =
                Library::open(Some("librenderdoc.so"), RTLD_NOW | libc::RTLD_NOLOAD).ok()?;
            let get_api = library
                .get::<RenderDocGetApi>(b"RENDERDOC_GetAPI\0")
                .ok()
                .map(|symbol| *symbol)?;
            let mut api = ptr::null_mut();
            if get_api(RENDERDOC_API_VERSION_1_0_0, &mut api) == 0 || api.is_null() {
                return None;
            }
            let api = api.cast::<RenderDocApiV1_0_0>();
            ((*api).start_frame_capture)(ptr::null_mut(), ptr::null_mut());
            Some(Self {
                api,
                _library: library,
                active: true,
            })
        }
    }
}

impl Drop for RenderDocFrame {
    fn drop(&mut self) {
        if self.active {
            unsafe {
                ((*self.api).end_frame_capture)(ptr::null_mut(), ptr::null_mut());
            }
            self.active = false;
        }
    }
}
