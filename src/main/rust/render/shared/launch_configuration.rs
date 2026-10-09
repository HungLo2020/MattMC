//! Process launch flags are immutable policy input. Java keeps a bounded scalar
//! projection; Rust parses and owns the process configuration once.
use std::sync::OnceLock;
const SOURCE_EXECUTION: u32 = 1;
const GRAPHICS_AUDIT: u32 = 2;
const SOURCE_OVERRIDE_PRESENT: u32 = 4;
static FLAGS: OnceLock<u32> = OnceLock::new();
fn yes(value: &str) -> bool {
    let mut c = value.chars();
    matches!(c.next(), Some('y' | 'Y'))
        && matches!(c.next(), Some('e' | 'E'))
        && matches!(c.next(), Some('s' | 'S' | 'ſ'))
        && c.next().is_none()
}
fn decode(source: Option<&str>, audit: Option<&str>) -> u32 {
    let true_value = |value: &str| value == "1" || value.eq_ignore_ascii_case("true");
    let mut flags = if source.is_some() {
        SOURCE_OVERRIDE_PRESENT
    } else {
        0
    };
    if source.is_some_and(|s| true_value(s) || yes(s)) {
        flags |= SOURCE_EXECUTION;
    }
    if audit.is_some_and(true_value) {
        flags |= GRAPHICS_AUDIT;
    }
    flags
}
#[unsafe(no_mangle)]
pub extern "C" fn mattmc_render_launch_configuration() -> u32 {
    *FLAGS.get_or_init(|| {
        let source = crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_EXECUTION").ok();
        let audit = crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").ok();
        decode(source.as_deref(), audit.as_deref())
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn flags_preserve_untrimmed_java_predicates_and_distinct_yes_admission() {
        for v in ["1", "true", "tRuE"] {
            assert_eq!(decode(Some(v), Some(v)), 7);
        }
        for v in ["yes", "YeS", "yeſ"] {
            assert_eq!(decode(Some(v), Some(v)), 5);
        }
        for v in [
            "", "0", "false", " true", "true ", " yes", "true\0", "on", "truе", "yеs",
        ] {
            assert_eq!(decode(Some(v), Some(v)), 4);
        }
        assert_eq!(decode(None, None), 0);
    }
}
