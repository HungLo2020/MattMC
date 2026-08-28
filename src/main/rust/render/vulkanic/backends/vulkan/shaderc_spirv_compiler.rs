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
        _ => Err(format!(
            "unsupported Vulkanic shader stage ordinal: {stage}"
        )),
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

/// Keeps shader compiler failures actionable without retaining or writing a
/// full generated pack source. Shaderc uses one-based source lines in its
/// diagnostics; include a small numbered window when that line is present.
fn shader_error_context(source: &str, diagnostic: &str) -> Option<String> {
    let line = diagnostic
        .split(':')
        .find_map(|part| part.trim().parse::<usize>().ok())?;
    let first = line.saturating_sub(3).max(1);
    let last = line.saturating_add(2);
    let context = source
        .lines()
        .enumerate()
        .filter_map(|(index, text)| {
            let number = index + 1;
            (first..=last)
                .contains(&number)
                .then(|| format!("{number}: {text}"))
        })
        .collect::<Vec<_>>()
        .join("\n");
    (!context.is_empty()).then_some(context)
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
    options.set_target_env(
        shaderc::TargetEnv::Vulkan,
        // The native Vulkan context is created at 1.3. Emit modules against
        // that same contract so shader-pack features are not silently
        // lowered through a stale Vulkan 1.0 capability model.
        shaderc::EnvVersion::Vulkan1_3 as u32,
    );
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
            let diagnostic = error.to_string();
            let context = shader_error_context(source, &diagnostic)
                .map(|context| format!("\nsource context:\n{context}"))
                .unwrap_or_default();
            format!(
                "SPIR-V compilation failed for '{source_name}' (stage={stage}, entryPoint={entry_point}) using Shaderc: {diagnostic}{context}"
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

pub(super) fn compile_glsl_for_backend(
    stage: shaderc::ShaderKind,
    source: &str,
    source_name: &str,
    entry: &str,
) -> Result<Vec<u8>, String> {
    let stage = match stage {
        shaderc::ShaderKind::Vertex => 0,
        shaderc::ShaderKind::Fragment => 1,
        shaderc::ShaderKind::Geometry => 2,
        shaderc::ShaderKind::Compute => 3,
        shaderc::ShaderKind::TessControl => 4,
        shaderc::ShaderKind::TessEvaluation => 5,
        _ => return Err(format!("unsupported Shaderc kind for {source_name}")),
    };
    unsafe {
        compile_inner(
            stage,
            source.as_ptr(),
            source.len() as u64,
            source_name.as_ptr(),
            source_name.len() as u64,
            entry.as_ptr(),
            entry.len() as u64,
        )
    }
}

#[cfg(test)]
pub(crate) fn compile_glsl_for_backend_test(
    stage: shaderc::ShaderKind,
    source: &str,
    source_name: &str,
) -> Result<Vec<u8>, String> {
    compile_glsl_for_backend(stage, source, source_name, "main")
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
    fn shader_error_context_extracts_a_bounded_one_based_window() {
        let source = "one\ntwo\nthree\nfour\nfive\nsix\nseven\n";
        let context = shader_error_context(source, "source:5: error: malformed")
            .expect("a Shaderc-style source line must produce context");
        assert_eq!(
            "2: two\n3: three\n4: four\n5: five\n6: six\n7: seven",
            context
        );
    }

    #[test]
    fn shader_error_context_ignores_diagnostics_without_a_source_line() {
        assert!(shader_error_context("one", "error: malformed").is_none());
    }

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
