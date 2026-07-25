use std::ffi::{c_char, c_int, c_ulong, c_void, CString};
use std::num::NonZeroU32;
use std::ptr;
use std::rc::Rc;
use std::thread::ThreadId;

use glow::HasContext;
use libloading::Library;

use crate::render::vulkanic::error::{GalError, GalResult};

const EGL_FALSE: c_int = 0;
const EGL_NONE: c_int = 0x3038;
const EGL_NO_CONTEXT: EglContextHandle = ptr::null_mut();
const EGL_NO_DISPLAY: EglDisplay = ptr::null_mut();
const EGL_NO_SURFACE: EglSurface = ptr::null_mut();
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
const EGL_EXTENSIONS: c_int = 0x3055;
const EGL_PLATFORM_SURFACELESS_MESA: c_int = 0x31DD;
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
type EglGetError = unsafe extern "C" fn() -> EglInt;
type EglQueryString = unsafe extern "C" fn(EglDisplay, EglInt) -> *const c_char;
type EglGetPlatformDisplayExt =
    unsafe extern "C" fn(EglInt, *mut c_void, *const EglInt) -> EglDisplay;

const GLX_RGBA_BIT: c_int = 0x0000_0001;
const GLX_PBUFFER_BIT: c_int = 0x0000_0004;
const GLX_DOUBLEBUFFER: c_int = 5;
const GLX_RED_SIZE: c_int = 8;
const GLX_GREEN_SIZE: c_int = 9;
const GLX_BLUE_SIZE: c_int = 10;
const GLX_ALPHA_SIZE: c_int = 11;
const GLX_DEPTH_SIZE: c_int = 12;
const GLX_DRAWABLE_TYPE: c_int = 0x8010;
const GLX_RENDER_TYPE: c_int = 0x8011;
const GLX_X_RENDERABLE: c_int = 0x8012;
const GLX_RGBA_TYPE: c_int = 0x8014;
const GLX_PBUFFER_HEIGHT: c_int = 0x8040;
const GLX_PBUFFER_WIDTH: c_int = 0x8041;

type XDisplay = *mut c_void;
type GlxFbConfig = *mut c_void;
type GlxContextHandle = *mut c_void;
type GlxDrawable = c_ulong;
type GlxPbuffer = c_ulong;

type XOpenDisplay = unsafe extern "C" fn(*const c_char) -> XDisplay;
type XDefaultScreen = unsafe extern "C" fn(XDisplay) -> c_int;
type XCloseDisplay = unsafe extern "C" fn(XDisplay) -> c_int;
type XFree = unsafe extern "C" fn(*mut c_void) -> c_int;
type GlxChooseFbConfig =
    unsafe extern "C" fn(XDisplay, c_int, *const c_int, *mut c_int) -> *mut GlxFbConfig;
type GlxCreateNewContext =
    unsafe extern "C" fn(XDisplay, GlxFbConfig, c_int, GlxContextHandle, c_int) -> GlxContextHandle;
type GlxCreatePbuffer = unsafe extern "C" fn(XDisplay, GlxFbConfig, *const c_int) -> GlxPbuffer;
type GlxMakeContextCurrent =
    unsafe extern "C" fn(XDisplay, GlxDrawable, GlxDrawable, GlxContextHandle) -> c_int;
type GlxDestroyPbuffer = unsafe extern "C" fn(XDisplay, GlxPbuffer);
type GlxDestroyContext = unsafe extern "C" fn(XDisplay, GlxContextHandle);
type GlxGetProcAddress = unsafe extern "C" fn(*const u8) -> *const c_void;

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
    get_error: EglGetError,
    query_string: EglQueryString,
    get_platform_display_ext: Option<EglGetPlatformDisplayExt>,
}

pub(super) struct OpenGlContext {
    native: NativeOpenGlContext,
    gl: Rc<glow::Context>,
    _gl_library: Option<Library>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::vulkanic::backends) struct ExistingOpenGlContextDesc {
    pub(super) label: String,
    pub(super) stable_window_id: u64,
    pub(super) render_thread: ThreadId,
}

enum NativeOpenGlContext {
    Egl {
        egl: EglFns,
        display: EglDisplay,
        surface: EglSurface,
        context: EglContextHandle,
    },
    Glx(GlxContextState),
    Existing {
        _stable_window_id: u64,
        render_thread: ThreadId,
    },
}

impl OpenGlContext {
    pub(super) fn new(label: &str) -> GalResult<Self> {
        let gl_library = unsafe { Library::new("libGL.so.1") }.ok();
        let native = create_native_context()?;
        let gl = unsafe {
            glow::Context::from_loader_function(|name| {
                let cname = CString::new(name).expect("GL symbol names do not contain NUL");
                let ptr = native.get_proc_address(cname.as_ptr());
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
            native,
            gl,
            _gl_library: gl_library,
        })
    }

    pub(super) fn from_existing_context(desc: ExistingOpenGlContextDesc) -> GalResult<Self> {
        if std::thread::current().id() != desc.render_thread {
            return Err(GalError::backend(
                "existing OpenGL context must be registered on the render thread",
            ));
        }
        let gl_library = unsafe { Library::new("libGL.so.1") }
            .map_err(|error| GalError::backend(format!("failed to load libGL.so.1: {error}")))?;
        let get_proc_address = unsafe {
            gl_library
                .get::<GlxGetProcAddress>(b"glXGetProcAddress\0")
                .ok()
                .map(|symbol| *symbol)
        };
        let gl = unsafe {
            glow::Context::from_loader_function(|name| {
                let cname = CString::new(name).expect("GL symbol names do not contain NUL");
                if let Some(get_proc_address) = get_proc_address {
                    let ptr = get_proc_address(cname.as_ptr().cast::<u8>());
                    if !ptr.is_null() {
                        return ptr.cast();
                    }
                }
                gl_library
                    .get::<*const c_void>(cname.as_bytes_with_nul())
                    .ok()
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
                    2,
                    glow::DEBUG_SEVERITY_NOTIFICATION,
                    &format!(
                        "MattMC OpenGL VulkanicGAL borrowed context: {} window={}",
                        desc.label, desc.stable_window_id
                    ),
                );
            }
        }
        Ok(Self {
            native: NativeOpenGlContext::Existing {
                _stable_window_id: desc.stable_window_id,
                render_thread: desc.render_thread,
            },
            gl,
            _gl_library: Some(gl_library),
        })
    }

    pub(super) fn gl(&self) -> &Rc<glow::Context> {
        &self.gl
    }

    pub(super) fn borrowed_state_guard(&self) -> Option<BorrowedOpenGlStateGuard> {
        self.native
            .is_existing()
            .then(|| BorrowedOpenGlStateGuard::capture(self.gl.clone()))
    }

    pub(super) fn make_current(&self) -> GalResult<()> {
        self.native.make_current()
    }

    pub(super) fn current_draw_framebuffer(&self) -> Option<glow::Framebuffer> {
        unsafe { framebuffer_name(self.gl.get_parameter_i32(glow::DRAW_FRAMEBUFFER_BINDING)) }
    }
}

impl Drop for OpenGlContext {
    fn drop(&mut self) {
        self.native.destroy();
    }
}

impl NativeOpenGlContext {
    fn make_current(&self) -> GalResult<()> {
        match self {
            NativeOpenGlContext::Egl {
                egl,
                display,
                surface,
                context,
            } => {
                if unsafe { (egl.make_current)(*display, *surface, *surface, *context) }
                    == EGL_FALSE
                {
                    return Err(GalError::backend(egl_error(egl, "EGL make-current failed")));
                }
                Ok(())
            }
            NativeOpenGlContext::Glx(state) => state.make_current(),
            NativeOpenGlContext::Existing { render_thread, .. } => {
                if std::thread::current().id() != *render_thread {
                    return Err(GalError::backend(
                        "borrowed OpenGL context used from a non-render thread",
                    ));
                }
                Ok(())
            }
        }
    }

    fn is_existing(&self) -> bool {
        matches!(self, NativeOpenGlContext::Existing { .. })
    }

    fn get_proc_address(&self, name: *const c_char) -> *const c_void {
        match self {
            NativeOpenGlContext::Egl { egl, .. } => unsafe { (egl.get_proc_address)(name) },
            NativeOpenGlContext::Glx(state) => unsafe {
                (state.fns.get_proc_address)(name.cast::<u8>())
            },
            NativeOpenGlContext::Existing { .. } => ptr::null(),
        }
    }

    fn destroy(&mut self) {
        match self {
            NativeOpenGlContext::Egl {
                egl,
                display,
                surface,
                context,
            } => unsafe {
                (egl.make_current)(*display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                (egl.destroy_context)(*display, *context);
                if !surface.is_null() {
                    (egl.destroy_surface)(*display, *surface);
                }
                (egl.terminate)(*display);
            },
            NativeOpenGlContext::Glx(state) => state.destroy(),
            NativeOpenGlContext::Existing { .. } => {}
        }
    }
}

pub(super) struct BorrowedOpenGlStateGuard {
    gl: Rc<glow::Context>,
    program: Option<glow::NativeProgram>,
    vertex_array: Option<glow::NativeVertexArray>,
    array_buffer: Option<glow::NativeBuffer>,
    copy_read_buffer: Option<glow::NativeBuffer>,
    copy_write_buffer: Option<glow::NativeBuffer>,
    pixel_unpack_buffer: Option<glow::NativeBuffer>,
    pixel_pack_buffer: Option<glow::NativeBuffer>,
    uniform_buffer: Option<glow::NativeBuffer>,
    storage_buffer: Option<glow::NativeBuffer>,
    draw_framebuffer: Option<glow::NativeFramebuffer>,
    read_framebuffer: Option<glow::NativeFramebuffer>,
    active_texture: i32,
    texture_units: Vec<TextureUnitState>,
    viewport: [i32; 4],
    scissor_box: [i32; 4],
    scissor_enabled: bool,
    cull_enabled: bool,
    cull_face: i32,
    blend_enabled: bool,
    blend_src_rgb: i32,
    blend_dst_rgb: i32,
    blend_src_alpha: i32,
    blend_dst_alpha: i32,
    blend_equation_rgb: i32,
    blend_equation_alpha: i32,
    color_writemask: [bool; 4],
    front_face: i32,
    stencil_enabled: bool,
    unpack_alignment: i32,
    unpack_row_length: i32,
    unpack_skip_rows: i32,
    unpack_skip_pixels: i32,
    unpack_image_height: i32,
    pack_alignment: i32,
    pack_row_length: i32,
    pack_skip_rows: i32,
    pack_skip_pixels: i32,
    pack_image_height: i32,
    depth_enabled: bool,
    depth_func: i32,
    depth_writemask: bool,
}

struct TextureUnitState {
    unit: u32,
    texture_2d: Option<glow::NativeTexture>,
    sampler: Option<glow::NativeSampler>,
}

impl BorrowedOpenGlStateGuard {
    fn capture(gl: Rc<glow::Context>) -> Self {
        unsafe {
            let active_texture = gl.get_parameter_i32(glow::ACTIVE_TEXTURE);
            let mut texture_units = Vec::new();
            let texture_unit_count = gl
                .get_parameter_i32(glow::MAX_COMBINED_TEXTURE_IMAGE_UNITS)
                .max(1);
            for unit in 0..u32::try_from(texture_unit_count).unwrap_or(1) {
                gl.active_texture(glow::TEXTURE0 + unit);
                texture_units.push(TextureUnitState {
                    unit,
                    texture_2d: texture_name(gl.get_parameter_i32(glow::TEXTURE_BINDING_2D)),
                    sampler: sampler_name(gl.get_parameter_i32(glow::SAMPLER_BINDING)),
                });
            }
            gl.active_texture(active_texture as u32);

            Self {
                program: program_name(gl.get_parameter_i32(glow::CURRENT_PROGRAM)),
                vertex_array: vertex_array_name(gl.get_parameter_i32(glow::VERTEX_ARRAY_BINDING)),
                array_buffer: buffer_name(gl.get_parameter_i32(glow::ARRAY_BUFFER_BINDING)),
                copy_read_buffer: buffer_name(gl.get_parameter_i32(glow::COPY_READ_BUFFER_BINDING)),
                copy_write_buffer: buffer_name(
                    gl.get_parameter_i32(glow::COPY_WRITE_BUFFER_BINDING),
                ),
                pixel_unpack_buffer: buffer_name(
                    gl.get_parameter_i32(glow::PIXEL_UNPACK_BUFFER_BINDING),
                ),
                pixel_pack_buffer: buffer_name(
                    gl.get_parameter_i32(glow::PIXEL_PACK_BUFFER_BINDING),
                ),
                uniform_buffer: buffer_name(gl.get_parameter_i32(glow::UNIFORM_BUFFER_BINDING)),
                storage_buffer: buffer_name(
                    gl.get_parameter_i32(glow::SHADER_STORAGE_BUFFER_BINDING),
                ),
                draw_framebuffer: framebuffer_name(
                    gl.get_parameter_i32(glow::DRAW_FRAMEBUFFER_BINDING),
                ),
                read_framebuffer: framebuffer_name(
                    gl.get_parameter_i32(glow::READ_FRAMEBUFFER_BINDING),
                ),
                active_texture,
                texture_units,
                viewport: parameter_i32x4(&gl, glow::VIEWPORT),
                scissor_box: parameter_i32x4(&gl, glow::SCISSOR_BOX),
                scissor_enabled: gl.is_enabled(glow::SCISSOR_TEST),
                cull_enabled: gl.is_enabled(glow::CULL_FACE),
                cull_face: gl.get_parameter_i32(glow::CULL_FACE_MODE),
                blend_enabled: gl.is_enabled(glow::BLEND),
                blend_src_rgb: gl.get_parameter_i32(glow::BLEND_SRC_RGB),
                blend_dst_rgb: gl.get_parameter_i32(glow::BLEND_DST_RGB),
                blend_src_alpha: gl.get_parameter_i32(glow::BLEND_SRC_ALPHA),
                blend_dst_alpha: gl.get_parameter_i32(glow::BLEND_DST_ALPHA),
                blend_equation_rgb: gl.get_parameter_i32(glow::BLEND_EQUATION_RGB),
                blend_equation_alpha: gl.get_parameter_i32(glow::BLEND_EQUATION_ALPHA),
                color_writemask: parameter_boolx4(&gl, glow::COLOR_WRITEMASK),
                front_face: gl.get_parameter_i32(glow::FRONT_FACE),
                stencil_enabled: gl.is_enabled(glow::STENCIL_TEST),
                unpack_alignment: gl.get_parameter_i32(glow::UNPACK_ALIGNMENT),
                unpack_row_length: gl.get_parameter_i32(glow::UNPACK_ROW_LENGTH),
                unpack_skip_rows: gl.get_parameter_i32(glow::UNPACK_SKIP_ROWS),
                unpack_skip_pixels: gl.get_parameter_i32(glow::UNPACK_SKIP_PIXELS),
                unpack_image_height: gl.get_parameter_i32(glow::UNPACK_IMAGE_HEIGHT),
                pack_alignment: gl.get_parameter_i32(glow::PACK_ALIGNMENT),
                pack_row_length: gl.get_parameter_i32(glow::PACK_ROW_LENGTH),
                pack_skip_rows: gl.get_parameter_i32(glow::PACK_SKIP_ROWS),
                pack_skip_pixels: gl.get_parameter_i32(glow::PACK_SKIP_PIXELS),
                pack_image_height: gl.get_parameter_i32(glow::PACK_IMAGE_HEIGHT),
                depth_enabled: gl.is_enabled(glow::DEPTH_TEST),
                depth_func: gl.get_parameter_i32(glow::DEPTH_FUNC),
                depth_writemask: gl.get_parameter_i32(glow::DEPTH_WRITEMASK) != 0,
                gl,
            }
        }
    }
}

impl Drop for BorrowedOpenGlStateGuard {
    fn drop(&mut self) {
        unsafe {
            self.gl.use_program(self.program);
            self.gl.bind_vertex_array(self.vertex_array);
            self.gl.bind_buffer(glow::ARRAY_BUFFER, self.array_buffer);
            self.gl
                .bind_buffer(glow::COPY_READ_BUFFER, self.copy_read_buffer);
            self.gl
                .bind_buffer(glow::COPY_WRITE_BUFFER, self.copy_write_buffer);
            self.gl
                .bind_buffer(glow::PIXEL_UNPACK_BUFFER, self.pixel_unpack_buffer);
            self.gl
                .bind_buffer(glow::PIXEL_PACK_BUFFER, self.pixel_pack_buffer);
            self.gl
                .bind_buffer(glow::UNIFORM_BUFFER, self.uniform_buffer);
            self.gl
                .bind_buffer(glow::SHADER_STORAGE_BUFFER, self.storage_buffer);
            self.gl
                .bind_framebuffer(glow::DRAW_FRAMEBUFFER, self.draw_framebuffer);
            self.gl
                .bind_framebuffer(glow::READ_FRAMEBUFFER, self.read_framebuffer);
            for state in &self.texture_units {
                self.gl.active_texture(glow::TEXTURE0 + state.unit);
                self.gl.bind_texture(glow::TEXTURE_2D, state.texture_2d);
                self.gl.bind_sampler(state.unit, state.sampler);
            }
            self.gl.active_texture(self.active_texture as u32);
            self.gl.viewport(
                self.viewport[0],
                self.viewport[1],
                self.viewport[2],
                self.viewport[3],
            );
            self.gl.scissor(
                self.scissor_box[0],
                self.scissor_box[1],
                self.scissor_box[2],
                self.scissor_box[3],
            );
            set_enabled(&self.gl, glow::SCISSOR_TEST, self.scissor_enabled);
            set_enabled(&self.gl, glow::CULL_FACE, self.cull_enabled);
            self.gl.cull_face(self.cull_face as u32);
            set_enabled(&self.gl, glow::BLEND, self.blend_enabled);
            self.gl.blend_func_separate(
                self.blend_src_rgb as u32,
                self.blend_dst_rgb as u32,
                self.blend_src_alpha as u32,
                self.blend_dst_alpha as u32,
            );
            self.gl.blend_equation_separate(
                self.blend_equation_rgb as u32,
                self.blend_equation_alpha as u32,
            );
            self.gl.color_mask(
                self.color_writemask[0],
                self.color_writemask[1],
                self.color_writemask[2],
                self.color_writemask[3],
            );
            self.gl.front_face(self.front_face as u32);
            set_enabled(&self.gl, glow::STENCIL_TEST, self.stencil_enabled);
            self.gl
                .pixel_store_i32(glow::UNPACK_ALIGNMENT, self.unpack_alignment);
            self.gl
                .pixel_store_i32(glow::UNPACK_ROW_LENGTH, self.unpack_row_length);
            self.gl
                .pixel_store_i32(glow::UNPACK_SKIP_ROWS, self.unpack_skip_rows);
            self.gl
                .pixel_store_i32(glow::UNPACK_SKIP_PIXELS, self.unpack_skip_pixels);
            self.gl
                .pixel_store_i32(glow::UNPACK_IMAGE_HEIGHT, self.unpack_image_height);
            self.gl
                .pixel_store_i32(glow::PACK_ALIGNMENT, self.pack_alignment);
            self.gl
                .pixel_store_i32(glow::PACK_ROW_LENGTH, self.pack_row_length);
            self.gl
                .pixel_store_i32(glow::PACK_SKIP_ROWS, self.pack_skip_rows);
            self.gl
                .pixel_store_i32(glow::PACK_SKIP_PIXELS, self.pack_skip_pixels);
            self.gl
                .pixel_store_i32(glow::PACK_IMAGE_HEIGHT, self.pack_image_height);
            set_enabled(&self.gl, glow::DEPTH_TEST, self.depth_enabled);
            self.gl.depth_func(self.depth_func as u32);
            self.gl.depth_mask(self.depth_writemask);
        }
    }
}

fn parameter_i32x4(gl: &glow::Context, parameter: u32) -> [i32; 4] {
    let mut values = [0; 4];
    unsafe {
        gl.get_parameter_i32_slice(parameter, &mut values);
    }
    values
}

fn parameter_boolx4(gl: &glow::Context, parameter: u32) -> [bool; 4] {
    let values = parameter_i32x4(gl, parameter);
    [
        values[0] != 0,
        values[1] != 0,
        values[2] != 0,
        values[3] != 0,
    ]
}

fn set_enabled(gl: &glow::Context, flag: u32, enabled: bool) {
    unsafe {
        if enabled {
            gl.enable(flag);
        } else {
            gl.disable(flag);
        }
    }
}

fn non_zero_name(value: i32) -> Option<NonZeroU32> {
    u32::try_from(value).ok().and_then(NonZeroU32::new)
}

fn program_name(value: i32) -> Option<glow::NativeProgram> {
    non_zero_name(value).map(glow::NativeProgram)
}

fn buffer_name(value: i32) -> Option<glow::NativeBuffer> {
    non_zero_name(value).map(glow::NativeBuffer)
}

fn vertex_array_name(value: i32) -> Option<glow::NativeVertexArray> {
    non_zero_name(value).map(glow::NativeVertexArray)
}

fn texture_name(value: i32) -> Option<glow::NativeTexture> {
    non_zero_name(value).map(glow::NativeTexture)
}

fn sampler_name(value: i32) -> Option<glow::NativeSampler> {
    non_zero_name(value).map(glow::NativeSampler)
}

fn framebuffer_name(value: i32) -> Option<glow::NativeFramebuffer> {
    non_zero_name(value).map(glow::NativeFramebuffer)
}

fn create_native_context() -> GalResult<NativeOpenGlContext> {
    let mut errors = Vec::new();
    match load_egl().and_then(|egl| {
        let (display, surface, context) = create_isolated_context(&egl)?;
        Ok(NativeOpenGlContext::Egl {
            egl,
            display,
            surface,
            context,
        })
    }) {
        Ok(context) => return Ok(context),
        Err(error) => errors.push(format!("EGL: {error}")),
    }
    match GlxContextState::new() {
        Ok(context) => Ok(NativeOpenGlContext::Glx(context)),
        Err(error) => {
            errors.push(format!("GLX: {error}"));
            Err(GalError::backend(format!(
                "failed to create isolated Rust OpenGL context: {}",
                errors.join("; ")
            )))
        }
    }
}

fn create_isolated_context(egl: &EglFns) -> GalResult<(EglDisplay, EglSurface, EglContextHandle)> {
    let mut errors = Vec::new();
    if client_supports(egl, "EGL_MESA_platform_surfaceless") {
        match create_context_attempt(egl, true) {
            Ok(objects) => return Ok(objects),
            Err(error) => errors.push(format!("surfaceless EGL: {error}")),
        }
    } else {
        errors.push("surfaceless EGL: EGL_MESA_platform_surfaceless unavailable".to_string());
    }
    match create_context_attempt(egl, false) {
        Ok(objects) => Ok(objects),
        Err(error) => {
            errors.push(format!("pbuffer EGL: {error}"));
            Err(GalError::backend(errors.join("; ")))
        }
    }
}

fn create_context_attempt(
    egl: &EglFns,
    surfaceless: bool,
) -> GalResult<(EglDisplay, EglSurface, EglContextHandle)> {
    let display = choose_display(egl, surfaceless)?;
    if display.is_null() {
        return Err(GalError::backend("EGL returned no display"));
    }
    let mut major = 0;
    let mut minor = 0;
    if unsafe { (egl.initialize)(display, &mut major, &mut minor) } == EGL_FALSE {
        return Err(GalError::backend(egl_error(
            egl,
            "EGL initialization failed",
        )));
    }
    if unsafe { (egl.bind_api)(EGL_OPENGL_API) } == EGL_FALSE {
        unsafe { (egl.terminate)(display) };
        return Err(GalError::backend(egl_error(
            egl,
            "EGL OpenGL API binding failed",
        )));
    }
    if surfaceless && !display_supports(egl, display, "EGL_KHR_surfaceless_context") {
        unsafe { (egl.terminate)(display) };
        return Err(GalError::backend(
            "EGL_KHR_surfaceless_context unavailable on display",
        ));
    }

    let config = choose_config(egl, display, surfaceless)?;
    let surface = if surfaceless {
        EGL_NO_SURFACE
    } else {
        let surface_attribs = [EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE];
        let surface =
            unsafe { (egl.create_pbuffer_surface)(display, config, surface_attribs.as_ptr()) };
        if surface.is_null() {
            unsafe { (egl.terminate)(display) };
            return Err(GalError::backend(egl_error(
                egl,
                "EGL pbuffer surface creation failed",
            )));
        }
        surface
    };

    let context = create_context(egl, display, config).map_err(|error| {
        if !surface.is_null() {
            unsafe { (egl.destroy_surface)(display, surface) };
        }
        unsafe { (egl.terminate)(display) };
        error
    })?;
    if unsafe { (egl.make_current)(display, surface, surface, context) } == EGL_FALSE {
        let error = egl_error(egl, "EGL make-current failed");
        unsafe {
            (egl.destroy_context)(display, context);
            if !surface.is_null() {
                (egl.destroy_surface)(display, surface);
            }
            (egl.terminate)(display);
        }
        return Err(GalError::backend(error));
    }
    Ok((display, surface, context))
}

fn choose_display(egl: &EglFns, surfaceless: bool) -> GalResult<EglDisplay> {
    if surfaceless {
        let Some(get_platform_display) = egl.get_platform_display_ext else {
            return Err(GalError::backend(
                "eglGetPlatformDisplayEXT unavailable for surfaceless context",
            ));
        };
        return Ok(unsafe {
            get_platform_display(EGL_PLATFORM_SURFACELESS_MESA, ptr::null_mut(), ptr::null())
        });
    }
    Ok(unsafe { (egl.get_display)(ptr::null_mut()) })
}

fn choose_config(egl: &EglFns, display: EglDisplay, surfaceless: bool) -> GalResult<EglConfig> {
    let pbuffer_config = [
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
    let surfaceless_config = [
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
    let attribs = if surfaceless {
        &surfaceless_config[..]
    } else {
        &pbuffer_config[..]
    };
    let mut config = ptr::null_mut();
    let mut config_count = 0;
    if unsafe { (egl.choose_config)(display, attribs.as_ptr(), &mut config, 1, &mut config_count) }
        == EGL_FALSE
        || config.is_null()
        || config_count == 0
    {
        unsafe { (egl.terminate)(display) };
        return Err(GalError::backend(egl_error(
            egl,
            "EGL could not choose an isolated OpenGL config",
        )));
    }
    Ok(config)
}

fn create_context(
    egl: &EglFns,
    display: EglDisplay,
    config: EglConfig,
) -> GalResult<EglContextHandle> {
    let context_attribs = [
        EGL_CONTEXT_MAJOR_VERSION,
        3,
        EGL_CONTEXT_MINOR_VERSION,
        3,
        EGL_CONTEXT_OPENGL_PROFILE_MASK,
        EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
        EGL_NONE,
    ];
    let mut context =
        unsafe { (egl.create_context)(display, config, EGL_NO_CONTEXT, context_attribs.as_ptr()) };
    if context.is_null() {
        let fallback = [
            EGL_CONTEXT_MAJOR_VERSION,
            3,
            EGL_CONTEXT_MINOR_VERSION,
            3,
            EGL_NONE,
        ];
        context =
            unsafe { (egl.create_context)(display, config, EGL_NO_CONTEXT, fallback.as_ptr()) };
    }
    if context.is_null() {
        return Err(GalError::backend(egl_error(
            egl,
            "EGL OpenGL context creation failed",
        )));
    }
    Ok(context)
}

fn client_supports(egl: &EglFns, extension: &str) -> bool {
    query_extensions(egl, EGL_NO_DISPLAY).contains(extension)
}

fn display_supports(egl: &EglFns, display: EglDisplay, extension: &str) -> bool {
    query_extensions(egl, display).contains(extension)
}

fn query_extensions(egl: &EglFns, display: EglDisplay) -> String {
    let ptr = unsafe { (egl.query_string)(display, EGL_EXTENSIONS) };
    if ptr.is_null() {
        String::new()
    } else {
        unsafe { std::ffi::CStr::from_ptr(ptr) }
            .to_string_lossy()
            .into_owned()
    }
}

fn egl_error(egl: &EglFns, message: &str) -> String {
    let code = unsafe { (egl.get_error)() };
    format!("{message}; eglGetError=0x{code:04x}")
}

struct GlxFns {
    _gl_library: Library,
    _x11_library: Library,
    open_display: XOpenDisplay,
    default_screen: XDefaultScreen,
    close_display: XCloseDisplay,
    x_free: XFree,
    choose_fb_config: GlxChooseFbConfig,
    create_new_context: GlxCreateNewContext,
    create_pbuffer: GlxCreatePbuffer,
    make_context_current: GlxMakeContextCurrent,
    destroy_pbuffer: GlxDestroyPbuffer,
    destroy_context: GlxDestroyContext,
    get_proc_address: GlxGetProcAddress,
}

struct GlxContextState {
    fns: GlxFns,
    display: XDisplay,
    pbuffer: GlxPbuffer,
    context: GlxContextHandle,
}

impl GlxContextState {
    fn new() -> GalResult<Self> {
        let fns = load_glx()?;
        let display = unsafe { (fns.open_display)(ptr::null()) };
        if display.is_null() {
            return Err(GalError::backend(
                "XOpenDisplay returned null for isolated GLX context",
            ));
        }
        let screen = unsafe { (fns.default_screen)(display) };
        let attribs = [
            GLX_X_RENDERABLE,
            1,
            GLX_DRAWABLE_TYPE,
            GLX_PBUFFER_BIT,
            GLX_RENDER_TYPE,
            GLX_RGBA_BIT,
            GLX_RED_SIZE,
            8,
            GLX_GREEN_SIZE,
            8,
            GLX_BLUE_SIZE,
            8,
            GLX_ALPHA_SIZE,
            8,
            GLX_DEPTH_SIZE,
            24,
            GLX_DOUBLEBUFFER,
            0,
            0,
        ];
        let mut config_count = 0;
        let configs =
            unsafe { (fns.choose_fb_config)(display, screen, attribs.as_ptr(), &mut config_count) };
        if configs.is_null() || config_count <= 0 {
            unsafe { (fns.close_display)(display) };
            return Err(GalError::backend(
                "glXChooseFBConfig returned no pbuffer-capable framebuffer config",
            ));
        }
        let config = unsafe { *configs };
        unsafe { (fns.x_free)(configs.cast()) };

        let context =
            unsafe { (fns.create_new_context)(display, config, GLX_RGBA_TYPE, ptr::null_mut(), 1) };
        if context.is_null() {
            unsafe { (fns.close_display)(display) };
            return Err(GalError::backend(
                "glXCreateNewContext failed for isolated Rust OpenGL context",
            ));
        }
        let pbuffer_attribs = [GLX_PBUFFER_WIDTH, 16, GLX_PBUFFER_HEIGHT, 16, 0];
        let pbuffer = unsafe { (fns.create_pbuffer)(display, config, pbuffer_attribs.as_ptr()) };
        if pbuffer == 0 {
            unsafe {
                (fns.destroy_context)(display, context);
                (fns.close_display)(display);
            }
            return Err(GalError::backend(
                "glXCreatePbuffer failed for isolated Rust OpenGL context",
            ));
        }
        let state = Self {
            fns,
            display,
            pbuffer,
            context,
        };
        state.make_current()?;
        Ok(state)
    }

    fn make_current(&self) -> GalResult<()> {
        let ok = unsafe {
            (self.fns.make_context_current)(self.display, self.pbuffer, self.pbuffer, self.context)
        };
        if ok == 0 {
            return Err(GalError::backend(
                "glXMakeContextCurrent failed for isolated Rust OpenGL context",
            ));
        }
        Ok(())
    }

    fn destroy(&mut self) {
        unsafe {
            (self.fns.make_context_current)(self.display, 0, 0, ptr::null_mut());
            if self.pbuffer != 0 {
                (self.fns.destroy_pbuffer)(self.display, self.pbuffer);
            }
            if !self.context.is_null() {
                (self.fns.destroy_context)(self.display, self.context);
            }
            if !self.display.is_null() {
                (self.fns.close_display)(self.display);
            }
        }
    }
}

fn load_glx() -> GalResult<GlxFns> {
    let gl_library = unsafe { Library::new("libGL.so.1") }
        .map_err(|error| GalError::backend(format!("failed to load libGL.so.1: {error}")))?;
    let x11_library = unsafe { Library::new("libX11.so.6") }
        .map_err(|error| GalError::backend(format!("failed to load libX11.so.6: {error}")))?;
    unsafe {
        macro_rules! gl_sym {
            ($name:literal, $ty:ty) => {
                *gl_library
                    .get::<$ty>(concat!($name, "\0").as_bytes())
                    .map_err(|error| {
                        GalError::backend(format!("failed to load GLX symbol {}: {error}", $name))
                    })?
            };
        }
        macro_rules! x_sym {
            ($name:literal, $ty:ty) => {
                *x11_library
                    .get::<$ty>(concat!($name, "\0").as_bytes())
                    .map_err(|error| {
                        GalError::backend(format!("failed to load X11 symbol {}: {error}", $name))
                    })?
            };
        }
        Ok(GlxFns {
            open_display: x_sym!("XOpenDisplay", XOpenDisplay),
            default_screen: x_sym!("XDefaultScreen", XDefaultScreen),
            close_display: x_sym!("XCloseDisplay", XCloseDisplay),
            x_free: x_sym!("XFree", XFree),
            choose_fb_config: gl_sym!("glXChooseFBConfig", GlxChooseFbConfig),
            create_new_context: gl_sym!("glXCreateNewContext", GlxCreateNewContext),
            create_pbuffer: gl_sym!("glXCreatePbuffer", GlxCreatePbuffer),
            make_context_current: gl_sym!("glXMakeContextCurrent", GlxMakeContextCurrent),
            destroy_pbuffer: gl_sym!("glXDestroyPbuffer", GlxDestroyPbuffer),
            destroy_context: gl_sym!("glXDestroyContext", GlxDestroyContext),
            get_proc_address: gl_sym!("glXGetProcAddressARB", GlxGetProcAddress),
            _gl_library: gl_library,
            _x11_library: x11_library,
        })
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
        let get_proc_address = sym!("eglGetProcAddress", EglGetProcAddress);
        let get_platform_display_ext = library
            .get::<EglGetPlatformDisplayExt>(b"eglGetPlatformDisplayEXT\0")
            .ok()
            .map(|symbol| *symbol)
            .or_else(|| {
                let name = CString::new("eglGetPlatformDisplayEXT").ok()?;
                let ptr = get_proc_address(name.as_ptr());
                if ptr.is_null() {
                    None
                } else {
                    Some(std::mem::transmute::<*const c_void, EglGetPlatformDisplayExt>(ptr))
                }
            });
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
            get_proc_address,
            get_error: sym!("eglGetError", EglGetError),
            query_string: sym!("eglQueryString", EglQueryString),
            get_platform_display_ext,
            _library: library,
        })
    }
}
