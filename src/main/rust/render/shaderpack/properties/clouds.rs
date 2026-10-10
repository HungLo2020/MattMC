//! Source-derived cloud directives.
//!
//! Iris reads `clouds` and `dhClouds` from the resolved `shaders.properties`
//! and cancels DH's "Clouds" generic group when `dhClouds=off`, or when
//! `dhClouds` is unset and `clouds=off` (`DHCompatInternal.avoidRenderingClouds`).
//! The properties are resolved with the source's option and environment
//! defines, so `#ifdef DISTANT_HORIZONS` blocks apply exactly as in Iris.

use super::super::source::{preprocess::preprocess_artifact_with_runtime_options, ShaderPackSource};

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
enum CloudSetting {
    #[default]
    Default,
    Off,
    On,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ShaderPackCloudPolicy {
    generation: u64,
    clouds: CloudSetting,
    dh_clouds: CloudSetting,
}

impl ShaderPackCloudPolicy {
    pub fn from_source(source: &ShaderPackSource) -> Self {
        let mut policy = Self {
            generation: source.generation(),
            clouds: CloudSetting::Default,
            dh_clouds: CloudSetting::Default,
        };
        if source.get("shaders.properties").is_none() {
            return policy;
        }
        // An unresolvable properties file leaves Iris's defaults: DH clouds draw.
        let Ok(properties) = preprocess_artifact_with_runtime_options(source, "shaders.properties", &[])
        else {
            return policy;
        };
        for raw_line in properties.expanded_source().lines() {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
                continue;
            }
            let Some((key, value)) = line.split_once('=') else {
                continue;
            };
            // java.util.Properties keeps the last value of a repeated key.
            match (key.trim(), value.trim()) {
                ("clouds", "off") => policy.clouds = CloudSetting::Off,
                ("clouds", "fast" | "fancy") => policy.clouds = CloudSetting::On,
                ("dhClouds", "off") => policy.dh_clouds = CloudSetting::Off,
                ("dhClouds", "on" | "fancy") => policy.dh_clouds = CloudSetting::On,
                _ => {}
            }
        }
        policy
    }

    pub fn generation(self) -> u64 {
        self.generation
    }

    /// Whether DH's own "Clouds" generic group must not be drawn.
    pub fn avoids_distant_horizons_clouds(self) -> bool {
        self.dh_clouds == CloudSetting::Off
            || (self.dh_clouds == CloudSetting::Default && self.clouds == CloudSetting::Off)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::shaderpack::source::{ShaderSourceFile, RUNTIME_ENVIRONMENT_PATH};

    fn policy_with(properties: &str, environment: &str) -> ShaderPackCloudPolicy {
        let source = ShaderPackSource::new(
            "clouds-test".to_string(),
            1,
            vec![
                ShaderSourceFile::new("shaders.properties", properties.to_string()),
                ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, environment.to_string()),
            ],
        )
        .unwrap();
        ShaderPackCloudPolicy::from_source(&source)
    }

    fn policy(properties: &str) -> ShaderPackCloudPolicy {
        policy_with(properties, "IS_IRIS=1\n")
    }

    #[test]
    fn follows_iris_avoid_rendering_clouds() {
        assert!(!policy("").avoids_distant_horizons_clouds());
        assert!(policy("dhClouds = off\n").avoids_distant_horizons_clouds());
        assert!(policy("clouds=off\n").avoids_distant_horizons_clouds());
        assert!(!policy("clouds=off\ndhClouds=on\n").avoids_distant_horizons_clouds());
        assert!(!policy("clouds=fancy\n").avoids_distant_horizons_clouds());
        assert!(!policy("dhClouds=off\ndhClouds=on\n").avoids_distant_horizons_clouds());
    }

    #[test]
    fn conditional_directives_need_their_define() {
        let guarded = "#ifdef DISTANT_HORIZONS\n    dhClouds = off\n#endif\n";
        assert!(!policy(guarded).avoids_distant_horizons_clouds());
        // Java's collected snapshot defines DISTANT_HORIZONS while DH renders.
        assert!(policy_with(guarded, "IS_IRIS=1\nDISTANT_HORIZONS=1\n").avoids_distant_horizons_clouds());
    }

    #[test]
    fn packs_without_properties_keep_dh_clouds() {
        let source = ShaderPackSource::new("empty".to_string(), 2, Vec::new()).unwrap();
        assert!(!ShaderPackCloudPolicy::from_source(&source).avoids_distant_horizons_clouds());
    }
}
