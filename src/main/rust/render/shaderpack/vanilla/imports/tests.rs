use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::vanilla::imports::*;

fn snapshot(files: &[(&str, &str)]) -> ShaderPackSource {
    ShaderPackSource::new(
        "copied-import-fixture",
        1,
        files
            .iter()
            .map(|(path, text)| ShaderSourceFile::new(*path, *text))
            .collect(),
    )
    .unwrap()
}

#[test]
fn multiline_comments_join_only_mojang_directive_token_gaps() {
    let source = snapshot(&[("include/a.glsl", "float imported;\n")]);
    let result = expand(&source, "minecraft", "post/test.fsh",
        "#version 330\n#/* five\n six */moj_import/* seven\n eight */<a.glsl>\nfloat ordinary; /* keep\n line */\n").unwrap();
    assert!(result.starts_with("#version 330\n"));
    assert_eq!(1, result.matches("float imported;").count());
    assert!(!result.contains("moj_import"));
    assert_eq!(
        "float a;     \n       float b;\n",
        without_comments("float a; /* x\n y */  float b;\n").unwrap()
    );
    assert_eq!(
        "#\nmoj_import <a.glsl>\n",
        without_comments("#\nmoj_import <a.glsl>\n").unwrap()
    );
    assert_eq!(
        "#       \n      define VALUE 1\n",
        without_comments("#/* keep\nme */ define VALUE 1\n").unwrap()
    );
    assert!(!directive_after_comments(
        "/* joined\n*/\nmoj_import <a.glsl>",
        "moj_import"
    ));
    assert!(!directive_after_comments(
        "/* joined\n*/moj_import_invalid <a.glsl>",
        "moj_import"
    ));
    for version in ["#/* gap\n*/version 330\n", "#version/* gap\n*/330\n"] {
        assert!(expand(&source, "minecraft", "post/test.fsh", version)
            .unwrap_err()
            .message
            .contains("multiline GLSL version"));
    }
    assert!(expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#moj_import\n<a.glsl>\n"
    )
    .is_err());
    assert!(expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#moj_import /* unterminated\n"
    )
    .is_err());
}

#[test]
fn nested_quoted_imports_use_root_directory_and_accumulated_prefix() {
    let source = snapshot(&[
        (
            "post/lib/a.glsl",
            "#moj_import \"nested/b.glsl\"\nfloat a;\n",
        ),
        ("post/lib/nested/b.glsl", "float b;\n"),
    ]);
    let result = expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#version 330\n#moj_import \"lib/a.glsl\"\nvoid main(){}\n",
    )
    .unwrap();
    assert!(result.find("float b;").unwrap() < result.find("float a;").unwrap());
    assert!(!result.contains("#moj_import"));
}

#[test]
fn angle_import_resets_quoted_prefix_to_original_shader_directory() {
    let source = snapshot(&[
        ("include/a.glsl", "#moj_import \"local.glsl\"\n"),
        ("post/local.glsl", "float originalDirectory;\n"),
        ("include/local.glsl", "float wrongDirectory;\n"),
    ]);
    let result = expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#version 330\n#moj_import <minecraft:a.glsl>\n",
    )
    .unwrap();
    assert!(result.contains("float originalDirectory;"));
    assert!(!result.contains("wrongDirectory"));
}

#[test]
fn versions_comments_and_import_once_match_stage_local_contract() {
    let source = snapshot(&[
        (
            "include/a.glsl",
            "#version 450\n#moj_import <b.glsl>\nfloat a;\n",
        ),
        (
            "include/b.glsl",
            "#version 400\n#moj_import <a.glsl>\nfloat b;\n",
        ),
    ]);
    let root = "#version 330\n/* #moj_import <missing.glsl> */\n// #moj_import <missing.glsl>\n# /* note */ moj_import <a.glsl> // trailing\n#moj_import <minecraft:a.glsl>\n";
    let result = expand(&source, "minecraft", "post/test.fsh", root).unwrap();
    assert!(result.starts_with("#version 450\n"));
    assert_eq!(1, result.matches("float a;").count());
    assert_eq!(1, result.matches("float b;").count());
    assert_eq!(1, result.matches("#version").count());
    assert_eq!(
        result,
        expand(&source, "minecraft", "post/test.fsh", root).unwrap()
    );
}

#[test]
fn missing_invalid_and_unrepresented_imports_fail_without_fallback() {
    let source = snapshot(&[("include/a.glsl", "float a;\n")]);
    for import in [
        "<missing.glsl>",
        "<other:a.glsl>",
        "\"../../escape.glsl\"",
        "\"\"",
        "\"",
        "<>",
        "\"/a.glsl\"",
        "<a.glsl> trailing",
    ] {
        assert!(
            expand(
                &source,
                "minecraft",
                "post/test.fsh",
                &format!("#version 330\n#moj_import {import}\n")
            )
            .is_err(),
            "{import}"
        );
    }
}

#[test]
fn block_comment_ending_before_import_does_not_comment_out_expansion() {
    let source = snapshot(&[("include/a.glsl", "float imported;\n")]);
    let result = expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#version 330\n/* comment\n*/ #moj_import <a.glsl>\nvoid main(){}\n",
    )
    .unwrap();
    assert!(!result.contains("/*"));
    assert!(result.contains("float imported;"));
    assert!(result.contains("void main(){}"));
    assert!(expand(&source, "minecraft", "post/test.fsh", "/* unterminated").is_err());
}

#[test]
fn depth_and_expanded_bytes_are_bounded() {
    let files = (0..35)
        .map(|i| {
            ShaderSourceFile::new(
                format!("include/{i}.glsl"),
                format!("#moj_import <{}.glsl>\n", i + 1),
            )
        })
        .collect();
    let source = ShaderPackSource::new("depth", 1, files).unwrap();
    assert!(expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#moj_import <0.glsl>\n"
    )
    .unwrap_err()
    .message
    .contains("depth"));
    let source = ShaderPackSource::new(
        "bytes",
        1,
        vec![ShaderSourceFile::new(
            "include/a.glsl",
            " ".repeat(MAX_EXPANDED_BYTES),
        )],
    )
    .unwrap();
    assert!(expand(
        &source,
        "minecraft",
        "post/test.fsh",
        "#version 330\n#moj_import <a.glsl>\n"
    )
    .unwrap_err()
    .message
    .contains("byte bound"));
}
