use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

const OPENAL_SOFT_REPOSITORY: &str = "https://github.com/kcat/openal-soft.git";
const OPENAL_SOFT_VERSION: &str = "1.24.3";
const OPENAL_SOFT_REVISION: &str = "dc7d7054a5b4f3bec1dc23a42fd616a0847af948";

fn main() {
    println!("cargo:rerun-if-env-changed=MATTMC_OPENAL_SOFT_SOURCE_DIR");
    println!("cargo:rerun-if-env-changed=TARGET");

    ensure_tool_exists(
        "git",
        "Git is required to fetch the pinned OpenAL Soft source",
    );
    ensure_tool_exists("cmake", "CMake is required to build OpenAL Soft");

    let source_dir = resolve_openal_soft_source();
    verify_openal_soft_source(&source_dir);
    let dst = build_openal_soft(&source_dir);
    link_openal_soft(&dst);
}

fn resolve_openal_soft_source() -> PathBuf {
    if let Ok(source_dir) = env::var("MATTMC_OPENAL_SOFT_SOURCE_DIR") {
        return PathBuf::from(source_dir);
    }

    let out_dir = PathBuf::from(env::var("OUT_DIR").expect("OUT_DIR is not set"));
    let source_dir = out_dir.join(format!("openal-soft-{OPENAL_SOFT_VERSION}"));
    if source_dir.exists() {
        return source_dir;
    }

    run(
        Command::new("git")
            .arg("clone")
            .arg("--depth")
            .arg("1")
            .arg("--branch")
            .arg(OPENAL_SOFT_VERSION)
            .arg(OPENAL_SOFT_REPOSITORY)
            .arg(&source_dir),
        "Failed to clone pinned OpenAL Soft source. Set MATTMC_OPENAL_SOFT_SOURCE_DIR to a local checkout if network access is unavailable.",
    );
    source_dir
}

fn verify_openal_soft_source(source_dir: &Path) {
    if !source_dir.join("CMakeLists.txt").is_file() {
        panic!(
            "OpenAL Soft source directory is missing CMakeLists.txt: {}",
            source_dir.display()
        );
    }

    let revision = command_output(
        Command::new("git")
            .current_dir(source_dir)
            .arg("rev-parse")
            .arg("HEAD"),
        "Failed to verify OpenAL Soft git revision",
    );
    let revision = revision.trim();
    if revision != OPENAL_SOFT_REVISION {
        panic!(
            "OpenAL Soft source revision mismatch: expected {OPENAL_SOFT_REVISION}, found {revision} in {}",
            source_dir.display()
        );
    }
}

fn build_openal_soft(source_dir: &Path) -> PathBuf {
    let mut config = cmake::Config::new(source_dir);
    config
        .define("LIBTYPE", "STATIC")
        .define("BUILD_SHARED_LIBS", "OFF")
        .define("CMAKE_POSITION_INDEPENDENT_CODE", "ON")
        .define("ALSOFT_BUILD_ROUTER", "OFF")
        .define("ALSOFT_UTILS", "OFF")
        .define("ALSOFT_NO_CONFIG_UTIL", "ON")
        .define("ALSOFT_EXAMPLES", "OFF")
        .define("ALSOFT_TESTS", "OFF")
        .define("ALSOFT_INSTALL", "ON")
        .define("ALSOFT_INSTALL_CONFIG", "OFF")
        .define("ALSOFT_INSTALL_HRTF_DATA", "OFF")
        .define("ALSOFT_INSTALL_AMBDEC_PRESETS", "OFF")
        .define("ALSOFT_INSTALL_EXAMPLES", "OFF")
        .define("ALSOFT_INSTALL_UTILS", "OFF")
        .define("ALSOFT_UPDATE_BUILD_VERSION", "OFF")
        .define("ALSOFT_SEARCH_INSTALL_DATADIR", "OFF")
        .define("ALSOFT_BACKEND_WAVE", "OFF")
        .define("ALSOFT_BACKEND_JACK", "OFF")
        .define("ALSOFT_BACKEND_PORTAUDIO", "OFF")
        .define("ALSOFT_BACKEND_SDL2", "OFF")
        .define("ALSOFT_BACKEND_SDL3", "OFF")
        .define("ALSOFT_BACKEND_OBOE", "OFF")
        .define("ALSOFT_BACKEND_OPENSL", "OFF");

    let target = env::var("TARGET").expect("TARGET is not set");
    if target.contains("windows") {
        config
            .define("ALSOFT_BACKEND_WASAPI", "ON")
            .define("ALSOFT_BACKEND_WINMM", "ON")
            .define("ALSOFT_BACKEND_DSOUND", "ON");
    } else if target.contains("apple-darwin") {
        config
            .define("ALSOFT_BACKEND_COREAUDIO", "ON")
            .define("ALSOFT_BACKEND_PIPEWIRE", "OFF")
            .define("ALSOFT_BACKEND_PULSEAUDIO", "OFF")
            .define("ALSOFT_BACKEND_ALSA", "OFF")
            .define("ALSOFT_BACKEND_OSS", "OFF")
            .define("ALSOFT_BACKEND_SOLARIS", "OFF")
            .define("ALSOFT_BACKEND_SNDIO", "OFF");
    } else if target.contains("linux") {
        configure_linux_desktop_audio_backend(&mut config);
        config
            .define("ALSOFT_DLOPEN", "ON")
            .define("ALSOFT_BACKEND_PIPEWIRE", "ON")
            .define("ALSOFT_BACKEND_PULSEAUDIO", "ON")
            .define("ALSOFT_BACKEND_ALSA", "ON")
            .define("ALSOFT_BACKEND_OSS", "OFF")
            .define("ALSOFT_BACKEND_SOLARIS", "OFF")
            .define("ALSOFT_BACKEND_SNDIO", "OFF");
    } else {
        panic!("Unsupported target for MattMC static OpenAL Soft build: {target}");
    }

    let dst = config.build();
    validate_openal_soft_build(&dst, &target);
    dst
}

fn configure_linux_desktop_audio_backend(config: &mut cmake::Config) {
    if let Some(pulseaudio_include_dir) = resolve_pulseaudio_include_dir() {
        config.define("PULSEAUDIO_INCLUDE_DIR", &pulseaudio_include_dir);
    }

    if let Some(pulseaudio_library) = find_first_linux_library(&["libpulse.so.0", "libpulse.so"]) {
        config.define("PULSEAUDIO_LIBRARY", pulseaudio_library);
    }
}

fn resolve_pulseaudio_include_dir() -> Option<PathBuf> {
    if let Ok(include_dir) = env::var("MATTMC_PULSEAUDIO_INCLUDE_DIR") {
        let include_dir = PathBuf::from(include_dir);
        if include_dir.join("pulse/pulseaudio.h").is_file() {
            return Some(include_dir);
        }
        panic!(
            "MATTMC_PULSEAUDIO_INCLUDE_DIR does not contain pulse/pulseaudio.h: {}",
            include_dir.display()
        );
    }

    let system_include = PathBuf::from("/usr/include");
    if system_include.join("pulse/pulseaudio.h").is_file() {
        return Some(system_include);
    }

    fetch_pulseaudio_dev_headers()
}

fn fetch_pulseaudio_dev_headers() -> Option<PathBuf> {
    let out_dir = PathBuf::from(env::var("OUT_DIR").expect("OUT_DIR is not set"));
    let root = out_dir.join("pulseaudio-dev");
    let include_dir = root.join("usr/include");
    if include_dir.join("pulse/pulseaudio.h").is_file() {
        return Some(include_dir);
    }

    if !tool_available("apt-get") || !tool_available("dpkg-deb") {
        return None;
    }

    fs::create_dir_all(&root).unwrap_or_else(|error| {
        panic!(
            "Failed to create PulseAudio header staging directory {}: {error}",
            root.display()
        )
    });

    let download_dir = root.join("download");
    fs::create_dir_all(&download_dir).unwrap_or_else(|error| {
        panic!(
            "Failed to create PulseAudio package download directory {}: {error}",
            download_dir.display()
        )
    });

    run(
        Command::new("apt-get")
            .current_dir(&download_dir)
            .arg("download")
            .arg("libpulse-dev"),
        "Failed to download PulseAudio development headers. Install libpulse-dev, set MATTMC_PULSEAUDIO_INCLUDE_DIR, or build on a system with PulseAudio headers available.",
    );

    let package = fs::read_dir(&download_dir)
        .unwrap_or_else(|error| {
            panic!(
                "Failed to inspect PulseAudio package download directory {}: {error}",
                download_dir.display()
            )
        })
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .find(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.starts_with("libpulse-dev_") && name.ends_with(".deb"))
        })
        .unwrap_or_else(|| {
            panic!(
                "apt-get download libpulse-dev did not produce a libpulse-dev .deb in {}",
                download_dir.display()
            )
        });

    run(
        Command::new("dpkg-deb")
            .arg("-x")
            .arg(&package)
            .arg(&root),
        "Failed to extract PulseAudio development headers",
    );
    let _ = fs::remove_dir_all(root.join("usr/share"));
    let _ = fs::remove_dir_all(root.join("usr/lib"));

    if include_dir.join("pulse/pulseaudio.h").is_file() {
        Some(include_dir)
    } else {
        panic!(
            "Extracted PulseAudio development package did not contain pulse/pulseaudio.h under {}",
            include_dir.display()
        );
    }
}

fn validate_openal_soft_build(dst: &Path, target: &str) {
    if target.contains("linux") {
        let config = fs::read_to_string(dst.join("build/config_backends.h")).unwrap_or_else(|error| {
            panic!(
                "Failed to inspect OpenAL Soft backend configuration under {}: {error}",
                dst.display()
            )
        });
        let has_pipewire = config_define_enabled(&config, "HAVE_PIPEWIRE");
        let has_pulseaudio = config_define_enabled(&config, "HAVE_PULSEAUDIO");
        if !has_pipewire && !has_pulseaudio {
            panic!(
                "MattMC's Linux OpenAL Soft build enabled neither PipeWire nor PulseAudio. \
                 Refusing to produce an ALSA/null-only audio backend because it can route to \
                 the wrong device or play no audible sound on PipeWire/Pulse desktops. \
                 Install PipeWire or PulseAudio development files for the native build \
                 (for example libpipewire-0.3-dev or libpulse-dev on Debian/Ubuntu), \
                 then rebuild. The final distribution still embeds OpenAL Soft in mattmc_rust \
                 and does not ship a separate OpenAL library."
            );
        }
    }
}

fn config_define_enabled(config: &str, name: &str) -> bool {
    let expected = format!("#define {name} 1");
    config.lines().any(|line| line.trim() == expected)
}

fn find_first_linux_library(file_names: &[&str]) -> Option<PathBuf> {
    linux_library_search_dirs()
        .into_iter()
        .flat_map(|dir| file_names.iter().map(move |file_name| dir.join(file_name)))
        .find(|path| path.is_file())
}

fn linux_library_search_dirs() -> Vec<PathBuf> {
    let mut dirs = Vec::new();
    if let Ok(output) = Command::new("cc").arg("-print-multiarch").output() {
        if output.status.success() {
            let multiarch = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !multiarch.is_empty() {
                dirs.push(PathBuf::from("/usr/lib").join(&multiarch));
                dirs.push(PathBuf::from("/lib").join(&multiarch));
            }
        }
    }

    dirs.extend([
        PathBuf::from("/usr/lib64"),
        PathBuf::from("/usr/lib"),
        PathBuf::from("/lib64"),
        PathBuf::from("/lib"),
    ]);
    dirs
}

fn link_openal_soft(dst: &Path) {
    let library = find_static_openal_library(dst).unwrap_or_else(|| {
        panic!(
            "OpenAL Soft static library was not produced under {}",
            dst.display()
        )
    });
    let library_dir = library
        .parent()
        .expect("static OpenAL library has no parent");
    let library_name = static_library_name(&library);

    println!("cargo:rustc-link-search=native={}", library_dir.display());
    println!("cargo:rustc-link-lib=static={library_name}");
    link_platform_dependencies();
}

fn find_static_openal_library(dst: &Path) -> Option<PathBuf> {
    let mut stack = vec![dst.to_path_buf()];
    while let Some(dir) = stack.pop() {
        for entry in fs::read_dir(&dir).ok()? {
            let entry = entry.ok()?;
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
                continue;
            }
            let name = path.file_name()?.to_string_lossy().to_ascii_lowercase();
            if (name == "openal.lib"
                || name == "openal32.lib"
                || name == "libopenal.a"
                || name == "libopenal32.a")
                && path.is_file()
            {
                return Some(path);
            }
        }
    }
    None
}

fn static_library_name(path: &Path) -> String {
    let file_name = path
        .file_name()
        .expect("static library path has no file name")
        .to_string_lossy();
    if file_name.ends_with(".lib") {
        file_name.trim_end_matches(".lib").to_string()
    } else {
        file_name
            .trim_start_matches("lib")
            .trim_end_matches(".a")
            .to_string()
    }
}

fn link_platform_dependencies() {
    let target = env::var("TARGET").expect("TARGET is not set");
    if target.contains("windows") {
        println!("cargo:rustc-link-lib=dylib=ole32");
        println!("cargo:rustc-link-lib=dylib=user32");
        println!("cargo:rustc-link-lib=dylib=winmm");
        println!("cargo:rustc-link-lib=dylib=dsound");
        println!("cargo:rustc-link-lib=dylib=dxguid");
        println!("cargo:rustc-link-lib=dylib=uuid");
        println!("cargo:rustc-link-lib=dylib=avrt");
        println!("cargo:rustc-link-lib=dylib=mmdevapi");
        println!("cargo:rustc-link-lib=dylib=shell32");
    } else if target.contains("apple-darwin") {
        println!("cargo:rustc-link-lib=dylib=c++");
        println!("cargo:rustc-link-lib=framework=AudioToolbox");
        println!("cargo:rustc-link-lib=framework=CoreAudio");
        println!("cargo:rustc-link-lib=framework=CoreFoundation");
    } else if target.contains("linux") {
        println!("cargo:rustc-link-lib=dylib=stdc++");
        println!("cargo:rustc-link-lib=dylib=dl");
        println!("cargo:rustc-link-lib=dylib=pthread");
        println!("cargo:rustc-link-lib=dylib=m");
    }
}

fn ensure_tool_exists(tool: &str, message: &str) {
    let status = Command::new(tool)
        .arg("--version")
        .status()
        .unwrap_or_else(|error| panic!("{message}: could not run `{tool} --version`: {error}"));
    if !status.success() {
        panic!("{message}: `{tool} --version` exited with {status}");
    }
}

fn tool_available(tool: &str) -> bool {
    Command::new(tool)
        .arg("--version")
        .status()
        .is_ok_and(|status| status.success())
}

fn run(command: &mut Command, message: &str) {
    let status = command
        .status()
        .unwrap_or_else(|error| panic!("{message}: {error}"));
    if !status.success() {
        panic!("{message}: command exited with {status}");
    }
}

fn command_output(command: &mut Command, message: &str) -> String {
    let output = command
        .output()
        .unwrap_or_else(|error| panic!("{message}: {error}"));
    if !output.status.success() {
        panic!("{message}: command exited with {}", output.status);
    }
    String::from_utf8(output.stdout).expect("command output was not UTF-8")
}
