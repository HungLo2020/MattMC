//! Pack-owned held-item light composition policy.
//!
//! `oldHandLight` is a shader-pack scalar rule, not an Iris object or a
//! renderer callback. Java copies each hand's gameplay light emission once
//! per frame; Rust applies the selected source generation's composition rule.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ShaderPackHeldLightPolicy {
    generation: u64,
    /// When enabled, the main hand receives the stronger of the two vanilla
    /// hand emissions, matching the documented legacy shader-pack rule.
    main_hand_uses_stronger_off_hand: bool,
}

impl ShaderPackHeldLightPolicy {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let mut old_hand_light = None;
        if let Some(properties) = source.get("shaders.properties") {
            for (line_number, raw_line) in properties.lines().enumerate() {
                let line = raw_line.trim();
                if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
                    continue;
                }
                let Some((key, value)) = line.split_once('=') else {
                    continue;
                };
                if key.trim() != "oldHandLight" {
                    continue;
                }
                if old_hand_light.is_some() {
                    return Err(GalError::invalid_argument(
                        "shaders.properties declares oldHandLight more than once",
                    ));
                }
                let value = match value.trim() {
                    "true" => true,
                    "false" => false,
                    other => {
                        return Err(GalError::invalid_argument(format!(
                            "shaders.properties oldHandLight line {} must be true or false, got {other}",
                            line_number + 1
                        )));
                    }
                };
                old_hand_light = Some(value);
            }
        }
        Ok(Self {
            generation: source.generation(),
            // Iris defaults absent `oldHandLight` to true.
            main_hand_uses_stronger_off_hand: old_hand_light.unwrap_or(true),
        })
    }

    pub fn generation(self) -> u64 {
        self.generation
    }

    pub fn main_hand_uses_stronger_off_hand(self) -> bool {
        self.main_hand_uses_stronger_off_hand
    }

    pub fn compose(self, main_hand_emission: i32, off_hand_emission: i32) -> GalResult<[i32; 2]> {
        for (label, value) in [
            ("main-hand held light emission", main_hand_emission),
            ("off-hand held light emission", off_hand_emission),
        ] {
            if !(0..=15).contains(&value) {
                return Err(GalError::invalid_argument(format!(
                    "{label} must be within [0, 15]"
                )));
            }
        }
        Ok([
            if self.main_hand_uses_stronger_off_hand {
                main_hand_emission.max(off_hand_emission)
            } else {
                main_hand_emission
            },
            off_hand_emission,
        ])
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    fn source(contents: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "held-light-policy",
            7,
            vec![ShaderSourceFile::new("shaders.properties", contents)],
        )
        .unwrap()
    }

    #[test]
    fn defaults_to_the_documented_legacy_hand_light_rule() {
        let policy =
            ShaderPackHeldLightPolicy::from_source(&source("profile.TEST=VALUE=1\n")).unwrap();
        assert_eq!(7, policy.generation());
        assert!(policy.main_hand_uses_stronger_off_hand());
        assert_eq!([12, 12], policy.compose(3, 12).unwrap());
    }

    #[test]
    fn uses_the_explicit_pack_policy_and_rejects_malformed_values() {
        let disabled =
            ShaderPackHeldLightPolicy::from_source(&source("oldHandLight=false\n")).unwrap();
        assert!(!disabled.main_hand_uses_stronger_off_hand());
        assert_eq!([3, 12], disabled.compose(3, 12).unwrap());
        assert!(ShaderPackHeldLightPolicy::from_source(&source("oldHandLight=maybe\n")).is_err());
        assert!(ShaderPackHeldLightPolicy::from_source(&source(
            "oldHandLight=true\noldHandLight=false\n"
        ))
        .is_err());
        assert!(disabled.compose(16, 0).is_err());
    }
}
