//! Alpha/depth policy insertion must select the definition rather than trivia.
use super::*;

#[test]
fn source_main_function_parser_preserves_trivia_prototypes_and_comment_braces() {
    let source="// void main() { }\nvoid main();\nvoid main_extra() {}\nvoid /* π */ main /* signature */ ( void ) {\n if (true) { /* } */ return; } // }\n}\n";
    let renamed = rename_glsl_main(source, "owned_main").unwrap();
    assert!(renamed.contains("void /* π */ owned_main /* signature */ ( void )"));
    assert!(renamed.contains("// void main() { }\nvoid main();\nvoid main_extra() {}"));
    assert_eq!(
        source.rfind('}'),
        source_main_function_closing_brace(source)
    );
    assert_eq!(
        source.find("void /* π */"),
        main_function_declaration_start(source)
    );
}

#[test]
fn source_main_function_parser_rejects_missing_or_unbalanced_definitions() {
    for source in [
        "// void main() {}",
        "void main();",
        "void main_extra() {}",
        "void main() { /* } */",
        "void main() /* unterminated",
    ] {
        assert!(rename_glsl_main(source, "owned_main").is_err(), "{source}");
    }
}
