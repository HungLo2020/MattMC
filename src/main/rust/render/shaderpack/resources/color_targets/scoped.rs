//! Color directives from a selected dimension's fragment programs.

use super::*;
use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
use crate::render::shaderpack::properties::directives::{
    constant_alias, visit_fragment_directives,
};

const LEGACY_NAMES: [&str; 8] = [
    "gcolor",
    "gdepth",
    "gnormal",
    "composite",
    "gaux1",
    "gaux2",
    "gaux3",
    "gaux4",
];

impl ShaderPackColorTargetManifest {
    pub fn from_source_for_scope(
        source: &ShaderPackSource,
        bindings: &TerrainSourceResourceBindings,
        scope: TerrainProgramScope,
    ) -> GalResult<Self> {
        let mut slots = BTreeMap::<u32, PartialSourceColorSlotDecl>::new();
        let scan = visit_fragment_directives(source, scope, false, |artifact| {
            read_color_directives(artifact.expanded_source(), artifact.defines(), &mut slots)
        });
        let scan = match scan {
            Ok(scan) => scan,
            // Compact transported snapshots may intentionally omit libraries.
            // Their explicit target metadata remains usable for discovery;
            // missing executable stages still reject source admission.
            Err(error)
                if source.get(PIPELINE_SETTINGS_PATH).is_some()
                    && error.to_string().contains("missing shader source") =>
            {
                return Self::from_source(source, bindings)
            }
            Err(error) => return Err(error),
        };
        if scan.fragment_count == 0 && !source.paths().any(|path| path.ends_with(".fsh")) {
            if source.get(PIPELINE_SETTINGS_PATH).is_some() {
                return Self::from_source(source, bindings);
            }
            return Err(GalError::unsupported_feature("shader-pack color targets require selected fragment directives or an explicit compact declaration"));
        }
        let declarations = slots
            .into_iter()
            .map(|(slot, partial)| {
                let defaults = SourceColorSlotDecl::protocol_default();
                (
                    slot,
                    SourceColorSlotDecl {
                        format: partial.format.unwrap_or(defaults.format),
                        clear_each_frame: partial
                            .clear_each_frame
                            .unwrap_or(defaults.clear_each_frame),
                        clear_color_bits: partial.clear_color_bits.or(defaults.clear_color_bits),
                    },
                )
            })
            .collect();
        Self::from_declarations(source, bindings, declarations)
    }
}

fn color_directive_name(name: &str) -> GalResult<Option<(u32, &'static str)>> {
    for suffix in ["ClearColor", "Clear", "Format"] {
        let Some(base) = name.strip_suffix(suffix) else {
            continue;
        };
        let slot = if let Some(slot) = base.strip_prefix("colortex") {
            slot.parse::<u32>()
                .map_err(|_| GalError::invalid_argument("invalid source color directive slot"))?
        } else if let Some(slot) = LEGACY_NAMES.iter().position(|legacy| *legacy == base) {
            slot as u32
        } else {
            continue;
        };
        if slot >= MAX_SOURCE_COLOR_TARGETS {
            return Err(GalError::unsupported_feature(
                "source color directive exceeds bounded target range",
            ));
        }
        return Ok(Some((slot, suffix)));
    }
    Ok(None)
}

fn read_color_directives(
    text: &str,
    defines: &[(String, String)],
    slots: &mut BTreeMap<u32, PartialSourceColorSlotDecl>,
) -> GalResult<()> {
    for raw in text.lines() {
        let line = directive_fragment(raw);
        let Some((declaration, value)) = line.split_once('=') else {
            continue;
        };
        let words = declaration.split_whitespace().collect::<Vec<_>>();
        let ["const", value_type, name] = words.as_slice() else {
            continue;
        };
        let Some((slot, suffix)) = color_directive_name(name)? else {
            continue;
        };
        let value = value
            .split_once(';')
            .ok_or_else(|| GalError::invalid_argument("source color directive lacks semicolon"))?
            .0
            .trim();
        let partial = slots.entry(slot).or_default();
        match (*value_type, suffix) {
            ("int", "Format") => {
                // Format vocabulary is source semantics, not a GLSL macro's
                // backend numeric value. Resolve aliases only when necessary.
                partial.format =
                    Some(ShaderPackColorFormat::parse(value).or_else(|_| {
                        ShaderPackColorFormat::parse(constant_alias(value, defines)?)
                    })?);
            }
            ("bool", "Clear") => {
                partial.clear_each_frame = Some(parse_bool(constant_alias(value, defines)?)?)
            }
            ("vec4", "ClearColor") => {
                partial.clear_color_bits =
                    Some(parse_clear_color_bits(constant_alias(value, defines)?)?)
            }
            _ => {
                return Err(GalError::invalid_argument(
                    "source color directive has incompatible constant type",
                ))
            }
        }
        // Frozen registers callbacks in ProgramSet order: the last accepted
        // declaration wins, including aliases and repeated included headers.
    }
    Ok(())
}

#[cfg(test)]
mod tests;
