use super::*;
use crate::render::shaderpack::source::ShaderSourceFile;

fn manifest(
    files: Vec<ShaderSourceFile>,
    scope: TerrainProgramScope,
) -> GalResult<ShaderPackColorTargetManifest> {
    let source = ShaderPackSource::new("scoped-targets", 9, files)?;
    let bindings = TerrainSourceResourceBindings::from_source(&source)?;
    ShaderPackColorTargetManifest::from_source_for_scope(&source, &bindings, scope)
}

fn files() -> Vec<ShaderSourceFile> {
    vec![
        ShaderSourceFile::new("gbuffers_terrain.vsh", "void main() {}"),
        ShaderSourceFile::new("gbuffers_terrain.fsh", "const int colortex0Format = RGBA16F;\n"),
        ShaderSourceFile::new("world0/gbuffers_terrain.vsh", "void main() {}"),
        ShaderSourceFile::new("world0/gbuffers_terrain.fsh", concat!(
            "#define SELECTED_FORMAT RGBA8_SNORM\n#include \"/lib/policy.glsl\"\n",
            "const bool gaux4Clear = false;\n")),
        ShaderSourceFile::new("lib/policy.glsl", "const int gaux4Format = SELECTED_FORMAT;\nconst vec4 gaux4ClearColor = vec4(0.5, 0.25, 0.0, 1.0);\n"),
        ShaderSourceFile::new("world0/final.fsh", "const int gaux4Format = RGB16F;\n"),
        ShaderSourceFile::new("world0/deferred.fsh", "const int colortex7Format = RGBA16F;\n"),
        ShaderSourceFile::new("world0/composite2.fsh", "const int gaux4Format = R8;\n"),
        ShaderSourceFile::new("world1/gbuffers_terrain.vsh", "void main() {}"),
        ShaderSourceFile::new("world1/gbuffers_terrain.fsh", "const int colortex3Format = RGB16F;\n"),
        ShaderSourceFile::new("world0/unselected.glsl", "const int colortex7Format = INVALID;\n"),
    ]
}

#[test]
fn scoped_directive_color_targets_select_dimension_aliases_and_frozen_last_winner() {
    let overworld = manifest(files(), TerrainProgramScope::Overworld).unwrap();
    assert_eq!(
        ShaderPackColorFormat::R8,
        overworld.target_for_source_slot(7).unwrap().format
    );
    let target = overworld.target_for_source_slot(7).unwrap();
    assert!(!target.clear_each_frame);
    assert_eq!(
        Some([
            0.5f32.to_bits(),
            0.25f32.to_bits(),
            0.0f32.to_bits(),
            1.0f32.to_bits()
        ]),
        target.clear_color_bits
    );
    // Existing override directory selects a whole set, without root merge.
    assert_eq!(
        ShaderPackColorFormat::Rgba8,
        overworld.target_for_source_slot(0).unwrap().format
    );
    let end = manifest(files(), TerrainProgramScope::End).unwrap();
    assert_eq!(
        ShaderPackColorFormat::Rgb16f,
        end.target_for_source_slot(3).unwrap().format
    );
    assert_eq!(
        ShaderPackColorFormat::Rgba8,
        end.target_for_source_slot(7).unwrap().format
    );
    let nether = manifest(files(), TerrainProgramScope::Nether).unwrap();
    assert_eq!(
        ShaderPackColorFormat::Rgba16f,
        nether.target_for_source_slot(0).unwrap().format
    );
}

#[test]
fn scoped_directive_color_targets_reject_invalid_active_declarations() {
    for text in [
        "const int colortex8Format = RGBA8;",
        "const int colortex0Format = UNKNOWN;",
        "const float colortex0Clear = 1.0;",
        "const vec4 colortex0ClearColor = vec4(NaN);",
    ] {
        let mut files = files();
        files.retain(|file| file.path != "world0/composite2.fsh");
        files.push(ShaderSourceFile::new("world0/composite2.fsh", text));
        assert!(
            manifest(files, TerrainProgramScope::Overworld).is_err(),
            "{text}"
        );
    }
}
