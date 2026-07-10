use std::slice;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_COMPILE_FAILED: i32 = -3;

fn shader_kind(stage: i32) -> Result<shaderc::ShaderKind, String> {
    match stage {
        0 => Ok(shaderc::ShaderKind::Vertex),
        1 => Ok(shaderc::ShaderKind::Fragment),
        2 => Ok(shaderc::ShaderKind::Geometry),
        3 => Ok(shaderc::ShaderKind::Compute),
        4 => Ok(shaderc::ShaderKind::TessControl),
        5 => Ok(shaderc::ShaderKind::TessEvaluation),
        _ => Err(format!("unsupported Vulkanic shader stage ordinal: {stage}")),
    }
}

unsafe fn read_utf8<'a>(ptr: *const u8, len: u64, label: &str) -> Result<&'a str, String> {
    if ptr.is_null() {
        return Err(format!("{label} pointer is null"));
    }

    let len = usize::try_from(len).map_err(|_| format!("{label} length does not fit usize"))?;
    let bytes = slice::from_raw_parts(ptr, len);
    std::str::from_utf8(bytes).map_err(|error| format!("{label} is not valid UTF-8: {error}"))
}

unsafe fn write_boxed_bytes(bytes: Vec<u8>, out_ptr: *mut u64, out_len: *mut u64) {
    let boxed = bytes.into_boxed_slice();
    let len = boxed.len();
    let ptr = Box::into_raw(boxed) as *mut u8;
    *out_ptr = ptr as u64;
    *out_len = len as u64;
}

unsafe fn write_error(message: String, out_error_ptr: *mut u64, out_error_len: *mut u64) {
    write_boxed_bytes(message.into_bytes(), out_error_ptr, out_error_len);
}

unsafe fn compile_inner(
    stage: i32,
    source_ptr: *const u8,
    source_len: u64,
    source_name_ptr: *const u8,
    source_name_len: u64,
    entry_point_ptr: *const u8,
    entry_point_len: u64,
) -> Result<Vec<u8>, String> {
    let source = read_utf8(source_ptr, source_len, "shader source")?;
    let source_name = read_utf8(source_name_ptr, source_name_len, "shader source name")?;
    let entry_point = read_utf8(entry_point_ptr, entry_point_len, "shader entry point")?;
    if source.trim().is_empty() {
        return Err(format!("cannot compile blank shader source: {source_name}"));
    }
    if entry_point.trim().is_empty() {
        return Err(format!("shader entry point is blank for {source_name}"));
    }

    let compiler = shaderc::Compiler::new()
        .map_err(|error| format!("failed to create Shaderc compiler: {error}"))?;
    let mut options = shaderc::CompileOptions::new()
        .map_err(|error| format!("failed to create Shaderc compile options: {error}"))?;
    options.set_source_language(shaderc::SourceLanguage::GLSL);
    options.set_target_env(shaderc::TargetEnv::Vulkan, shaderc::EnvVersion::Vulkan1_0 as u32);
    options.set_auto_bind_uniforms(true);
    options.set_auto_map_locations(true);

    let artifact = compiler
        .compile_into_spirv(
            source,
            shader_kind(stage)?,
            source_name,
            entry_point,
            Some(&options),
        )
        .map_err(|error| {
            format!(
                "SPIR-V compilation failed for '{source_name}' (stage={stage}, entryPoint={entry_point}) using Shaderc: {error}"
            )
        })?;

    let spirv = artifact.as_binary_u8().to_vec();
    if spirv.is_empty() {
        return Err(format!(
            "Shaderc produced empty SPIR-V for '{source_name}' (stage={stage}, entryPoint={entry_point})"
        ));
    }

    Ok(spirv)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkan_shaderc_compile_glsl_to_spirv(
    stage: i32,
    source_ptr: *const u8,
    source_len: u64,
    source_name_ptr: *const u8,
    source_name_len: u64,
    entry_point_ptr: *const u8,
    entry_point_len: u64,
    out_spirv_ptr: *mut u64,
    out_spirv_len: *mut u64,
    out_error_ptr: *mut u64,
    out_error_len: *mut u64,
) -> i32 {
    if out_spirv_ptr.is_null()
        || out_spirv_len.is_null()
        || out_error_ptr.is_null()
        || out_error_len.is_null()
    {
        return ERR_NULL_POINTER;
    }

    *out_spirv_ptr = 0;
    *out_spirv_len = 0;
    *out_error_ptr = 0;
    *out_error_len = 0;

    match compile_inner(
        stage,
        source_ptr,
        source_len,
        source_name_ptr,
        source_name_len,
        entry_point_ptr,
        entry_point_len,
    ) {
        Ok(spirv) => {
            write_boxed_bytes(spirv, out_spirv_ptr, out_spirv_len);
            OK
        }
        Err(message) => {
            let status = if message.contains("unsupported Vulkanic shader stage")
                || message.contains("pointer is null")
                || message.contains("length does not fit")
                || message.contains("not valid UTF-8")
                || message.contains("blank")
            {
                ERR_INVALID_ARGUMENT
            } else {
                ERR_COMPILE_FAILED
            };
            write_error(message, out_error_ptr, out_error_len);
            status
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkan_shaderc_free_buffer(ptr: u64, len: u64) {
    if ptr == 0 {
        return;
    }

    let Ok(len) = usize::try_from(len) else {
        return;
    };
    let slice = slice::from_raw_parts_mut(ptr as *mut u8, len);
    drop(Box::from_raw(slice));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn shaderc_compiles_minimal_vertex_shader() {
        let source = "#version 450\nvoid main(){gl_Position=vec4(0.0);}";
        let name = "test:minimal_vertex";
        let entry = "main";

        let spirv = unsafe {
            compile_inner(
                0,
                source.as_ptr(),
                source.len() as u64,
                name.as_ptr(),
                name.len() as u64,
                entry.as_ptr(),
                entry.len() as u64,
            )
        }
        .expect("Shaderc should compile a minimal vertex shader");

        assert!(spirv.len() >= 4);
        assert_eq!([0x03, 0x02, 0x23, 0x07], spirv[0..4]);
    }
}
