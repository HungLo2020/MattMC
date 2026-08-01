use std::ffi::{c_void, CString};
use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};

use libloading::os::unix::{Library, RTLD_NOW};

const RENDERDOC_API_VERSION_1_0_0: i32 = 10000;
static CAPTURE_STARTED: AtomicBool = AtomicBool::new(false);

type RenderDocGetApi = unsafe extern "C" fn(i32, *mut *mut c_void) -> i32;
type RenderDocSetCaptureFilePathTemplate = unsafe extern "C" fn(*const libc::c_char);
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
    set_capture_file_path_template: RenderDocSetCaptureFilePathTemplate,
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

pub(in crate::render::vulkanic) struct RenderDocFrame {
    api: *mut RenderDocApiV1_0_0,
    _library: Library,
    active: bool,
}

impl RenderDocFrame {
    pub(in crate::render::vulkanic) fn start_if_requested() -> Option<Self> {
        if !std::env::var("MATTMC_RENDERDOC_CAPTURE")
            .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
            .unwrap_or(false)
        {
            return None;
        }
        if !std::env::var("MATTMC_RENDERDOC_BACKEND")
            .map(|value| value.is_empty() || value.eq_ignore_ascii_case("vulkan"))
            .unwrap_or(true)
        {
            return None;
        }
        if CAPTURE_STARTED.swap(true, Ordering::SeqCst) {
            return None;
        }
        eprintln!("RenderDoc capture requested for Rust Vulkan test");
        unsafe {
            let library_name = std::env::var("MATTMC_RENDERDOC_LIBRARY_PATH")
                .ok()
                .filter(|value| !value.trim().is_empty())
                .unwrap_or_else(|| "librenderdoc.so".to_string());
            let library = Library::open(Some(library_name.as_str()), RTLD_NOW | libc::RTLD_NOLOAD)
                .or_else(|_| Library::open(Some(library_name.as_str()), RTLD_NOW))
                .map_err(|error| {
                    eprintln!("RenderDoc library load failed for {library_name}: {error}");
                    error
                })
                .ok()?;
            let get_api = library
                .get::<RenderDocGetApi>(b"RENDERDOC_GetAPI\0")
                .map_err(|error| {
                    eprintln!("RenderDoc API symbol lookup failed: {error}");
                    error
                })
                .ok()
                .map(|symbol| *symbol)?;
            let mut api = ptr::null_mut();
            if get_api(RENDERDOC_API_VERSION_1_0_0, &mut api) == 0 || api.is_null() {
                eprintln!(
                    "RenderDoc API negotiation failed for version {RENDERDOC_API_VERSION_1_0_0}"
                );
                return None;
            }
            let api = api.cast::<RenderDocApiV1_0_0>();
            eprintln!("RenderDoc API initialized for Rust Vulkan test capture");
            if let Ok(template) = std::env::var("MATTMC_RENDERDOC_CAPTURE_TEMPLATE") {
                if let Ok(c_template) = CString::new(template) {
                    ((*api).set_capture_file_path_template)(c_template.as_ptr());
                }
            }
            ((*api).start_frame_capture)(ptr::null_mut(), ptr::null_mut());
            eprintln!("Started RenderDoc frame capture (rust-vulkan-gbuffer-test)");
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
                let result = ((*self.api).end_frame_capture)(ptr::null_mut(), ptr::null_mut());
                eprintln!(
                    "Ended RenderDoc frame capture (rust-vulkan-gbuffer-test) result={result}"
                );
            }
            self.active = false;
        }
    }
}
