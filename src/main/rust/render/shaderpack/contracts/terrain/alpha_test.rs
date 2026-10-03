//! Frozen Sodium source alpha tests, resolved in the selected program options.
use super::*;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct NormalTerrainAlphaTestPolicy {
    opaque_cutoff_bits: Option<u32>,
    cutout_cutoff_bits: Option<u32>,
}

impl Default for NormalTerrainAlphaTestPolicy {
    fn default() -> Self {
        Self {
            opaque_cutoff_bits: None,
            cutout_cutoff_bits: Some(0.1f32.to_bits()),
        }
    }
}

impl NormalTerrainAlphaTestPolicy {
    pub(super) fn from_source(
        source: &ShaderPackSource,
        defines: &[(String, String)],
    ) -> GalResult<Self> {
        let properties = crate::render::shaderpack::properties::directives::resolved_properties(
            source, defines,
        )?;
        let Some(value) = selected_property_value(
            properties.as_ref().map_or("", |p| p.expanded_source()),
            "alphaTest.gbuffers_terrain",
        )?
        else {
            return Ok(Self::default());
        };
        let cutoff = if matches!(value, "off" | "false") {
            None
        } else {
            Some(
                parse_translucent_alpha_test(value)?
                    .greater_than()
                    .to_bits(),
            )
        };
        Ok(Self {
            opaque_cutoff_bits: cutoff,
            cutout_cutoff_bits: cutoff,
        })
    }

    pub fn cutoff(&self, material: TerrainMaterialClass) -> Option<f32> {
        match material {
            TerrainMaterialClass::Opaque => self.opaque_cutoff_bits,
            TerrainMaterialClass::Cutout => self.cutout_cutoff_bits,
            TerrainMaterialClass::Translucent => None,
        }
        .map(f32::from_bits)
    }
}
