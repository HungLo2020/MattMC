use std::ffi::{c_char, c_int, c_void, CString};
use std::ptr;
use std::rc::Rc;

use glow::HasContext;
use libloading::Library;

use crate::render::vulkanic::error::{GalError, GalResult};

const EGL_FALSE: c_int = 0;
const EGL_NONE: c_int = 0x3038;
const EGL_WIDTH: c_int = 0x3057;
const EGL_HEIGHT: c_int = 0x3056;
const EGL_SURFACE_TYPE: c_int = 0x3033;
const EGL_PBUFFER_BIT: c_int = 0x0001;
const EGL_RED_SIZE: c_int = 0x3024;
const EGL_GREEN_SIZE: c_int = 0x3023;
const EGL_BLUE_SIZE: c_int = 0x3022;
const EGL_ALPHA_SIZE: c_int = 0x3021;
const EGL_DEPTH_SIZE: c_int = 0x3025;
const EGL_RENDERABLE_TYPE: c_int = 0x3040;
const EGL_OPENGL_BIT: c_int = 0x0008;
const EGL_OPENGL_API: c_int = 0x30A2;
const EGL_CONTEXT_MAJOR_VERSION: c_int = 0x3098;
const EGL_CONTEXT_MINOR_VERSION: c_int = 0x30FB;
const EGL_CONTEXT_OPENGL_PROFILE_MASK: c_int = 0x30FD;
const EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT: c_int = 0x0001;

type EglDisplay = *mut c_void;
type EglConfig = *mut c_void;
type EglSurface = *mut c_void;
type EglContextHandle = *mut c_void;
type EglBoolean = c_int;
type EglInt = c_int;

type EglGetDisplay = unsafe extern "C" fn(*mut c_void) -> EglDisplay;
type EglInitialize = unsafe extern "C" fn(EglDisplay, *mut EglInt, *mut EglInt) -> EglBoolean;
type EglChooseConfig = unsafe extern "C" fn(
    EglDisplay,
    *const EglInt,
    *mut EglConfig,
    EglInt,
    *mut EglInt,
) -> EglBoolean;
type EglBindApi = unsafe extern "C" fn(EglInt) -> EglBoolean;
type EglCreatePbufferSurface =
    unsafe extern "C" fn(EglDisplay, EglConfig, *const EglInt) -> EglSurface;
type EglCreateContext = unsafe extern "C" fn(
    EglDisplay,
    EglConfig,
    EglContextHandle,
    *const EglInt,
) -> EglContextHandle;
type EglMakeCurrent =
    unsafe extern "C" fn(EglDisplay, EglSurface, EglSurface, EglContextHandle) -> EglBoolean;
type EglDestroySurface = unsafe extern "C" fn(EglDisplay, EglSurface) -> EglBoolean;
type EglDestroyContext = unsafe extern "C" fn(EglDisplay, EglContextHandle) -> EglBoolean;
type EglTerminate = unsafe extern "C" fn(EglDisplay) -> EglBoolean;
type EglGetProcAddress = unsafe extern "C" fn(*const c_char) -> *const c_void;

struct EglFns {
    _library: Library,
    get_display: EglGetDisplay,
    initialize: EglInitialize,
    choose_config: EglChooseConfig,
    bind_api: EglBindApi,
    create_pbuffer_surface: EglCreatePbufferSurface,
    create_context: EglCreateContext,
    make_current: EglMakeCurrent,
    destroy_surface: EglDestroySurface,
    destroy_context: EglDestroyContext,
    terminate: EglTerminate,
    get_proc_address: EglGetProcAddress,
}

pub(super) struct OpenGlContext {
    egl: EglFns,
    display: EglDisplay,
    surface: EglSurface,
    context: EglContextHandle,
    gl: Rc<glow::Context>,
    _gl_library: Option<Library>,
}

impl OpenGlContext {
    pub(super) fn new(label: &str) -> GalResult<Self> {
        let egl = load_egl()?;
        let display = unsafe { (egl.get_display)(ptr::null_mut()) };
        if display.is_null() {
            return Err(GalError::backend("EGL returned no default display"));
        }
        let mut major = 0;
        let mut minor = 0;
        if unsafe { (egl.initialize)(display, &mut major, &mut minor) } == EGL_FALSE {
            return Err(GalError::backend("EGL initialization failed"));
        }
        if unsafe { (egl.bind_api)(EGL_OPENGL_API) } == EGL_FALSE {
            unsafe { (egl.terminate)(display) };
            return Err(GalError::backend("EGL OpenGL API binding failed"));
        }

        let config_attribs = [
            EGL_SURFACE_TYPE,
            EGL_PBUFFER_BIT,
            EGL_RENDERABLE_TYPE,
            EGL_OPENGL_BIT,
            EGL_RED_SIZE,
            8,
            EGL_GREEN_SIZE,
            8,
            EGL_BLUE_SIZE,
            8,
            EGL_ALPHA_SIZE,
            8,
            EGL_DEPTH_SIZE,
            24,
            EGL_NONE,
        ];
        let mut config = ptr::null_mut();
        let mut config_count = 0;
        if unsafe {
            (egl.choose_config)(
                display,
                config_attribs.as_ptr(),
                &mut config,
                1,
                &mut config_count,
            )
        } == EGL_FALSE
            || config.is_null()
            || config_count == 0
        {
            unsafe { (egl.terminate)(display) };
            return Err(GalError::backend(
                "EGL could not choose an OpenGL pbuffer config",
            ));
        }

        let surface_attribs = [EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE];
        let surface =
            unsafe { (egl.create_pbuffer_surface)(display, config, surface_attribs.as_ptr()) };
        if surface.is_null() {
            unsafe { (egl.terminate)(display) };
            return Err(GalError::backend("EGL pbuffer surface creation failed"));
        }

        let context_attribs = [
            EGL_CONTEXT_MAJOR_VERSION,
            3,
            EGL_CONTEXT_MINOR_VERSION,
            3,
            EGL_CONTEXT_OPENGL_PROFILE_MASK,
            EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
            EGL_NONE,
        ];
        let mut context = unsafe {
            (egl.create_context)(display, config, ptr::null_mut(), context_attribs.as_ptr())
        };
        if context.is_null() {
            let fallback = [
                EGL_CONTEXT_MAJOR_VERSION,
                3,
                EGL_CONTEXT_MINOR_VERSION,
                3,
                EGL_NONE,
            ];
            context = unsafe {
                (egl.create_context)(display, config, ptr::null_mut(), fallback.as_ptr())
            };
        }
        if context.is_null() {
            unsafe {
                (egl.destroy_surface)(display, surface);
                (egl.terminate)(display);
            }
            return Err(GalError::backend("EGL OpenGL context creation failed"));
        }
        if unsafe { (egl.make_current)(display, surface, surface, context) } == EGL_FALSE {
            unsafe {
                (egl.destroy_context)(display, context);
                (egl.destroy_surface)(display, surface);
                (egl.terminate)(display);
            }
            return Err(GalError::backend("EGL make-current failed"));
        }

        let gl_library = unsafe { Library::new("libGL.so.1") }.ok();
        let get_proc_address = egl.get_proc_address;
        let gl = unsafe {
            glow::Context::from_loader_function(|name| {
                let cname = CString::new(name).expect("GL symbol names do not contain NUL");
                let ptr = get_proc_address(cname.as_ptr());
                if !ptr.is_null() {
                    return ptr.cast();
                }
                gl_library
                    .as_ref()
                    .and_then(|library| {
                        library.get::<*const c_void>(cname.as_bytes_with_nul()).ok()
                    })
                    .map(|symbol| *symbol)
                    .unwrap_or(ptr::null())
                    .cast()
            })
        };
        let gl = Rc::new(gl);
        unsafe {
            if gl.supports_debug() {
                gl.debug_message_insert(
                    glow::DEBUG_SOURCE_APPLICATION,
                    glow::DEBUG_TYPE_MARKER,
                    1,
                    glow::DEBUG_SEVERITY_NOTIFICATION,
                    &format!("MattMC OpenGL VulkanicGAL context: {label}"),
                );
            }
        }

        Ok(Self {
            egl,
            display,
            surface,
            context,
            gl,
            _gl_library: gl_library,
        })
    }

    pub(super) fn gl(&self) -> &Rc<glow::Context> {
        &self.gl
    }

    pub(super) fn make_current(&self) -> GalResult<()> {
        if unsafe {
            (self.egl.make_current)(self.display, self.surface, self.surface, self.context)
        } == EGL_FALSE
        {
            return Err(GalError::backend("EGL make-current failed"));
        }
        Ok(())
    }
}

impl Drop for OpenGlContext {
    fn drop(&mut self) {
        unsafe {
            (self.egl.make_current)(
                self.display,
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
            );
            (self.egl.destroy_context)(self.display, self.context);
            (self.egl.destroy_surface)(self.display, self.surface);
            (self.egl.terminate)(self.display);
        }
    }
}

fn load_egl() -> GalResult<EglFns> {
    let library = unsafe { Library::new("libEGL.so.1") }
        .map_err(|error| GalError::backend(format!("failed to load libEGL.so.1: {error}")))?;
    unsafe {
        macro_rules! sym {
            ($name:literal, $ty:ty) => {
                *library
                    .get::<$ty>(concat!($name, "\0").as_bytes())
                    .map_err(|error| {
                        GalError::backend(format!("failed to load EGL symbol {}: {error}", $name))
                    })?
            };
        }
        Ok(EglFns {
            get_display: sym!("eglGetDisplay", EglGetDisplay),
            initialize: sym!("eglInitialize", EglInitialize),
            choose_config: sym!("eglChooseConfig", EglChooseConfig),
            bind_api: sym!("eglBindAPI", EglBindApi),
            create_pbuffer_surface: sym!("eglCreatePbufferSurface", EglCreatePbufferSurface),
            create_context: sym!("eglCreateContext", EglCreateContext),
            make_current: sym!("eglMakeCurrent", EglMakeCurrent),
            destroy_surface: sym!("eglDestroySurface", EglDestroySurface),
            destroy_context: sym!("eglDestroyContext", EglDestroyContext),
            terminate: sym!("eglTerminate", EglTerminate),
            get_proc_address: sym!("eglGetProcAddress", EglGetProcAddress),
            _library: library,
        })
    }
}
