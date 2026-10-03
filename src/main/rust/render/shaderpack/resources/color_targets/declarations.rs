//! Pack color-target declarations (`lib/pipelineSettings.glsl`) and their formats.

use super::*;

pub const PIPELINE_SETTINGS_PATH: &str = "lib/pipelineSettings.glsl";

pub(super) const MAX_SOURCE_COLOR_TARGETS: u32 = 8;

/// Pack-level color format before a backend capability decision. This keeps
/// a source declaration such as `R11F_G11F_B10F` explicit even while the
/// current GAL has not yet exposed that color format.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum ShaderPackColorFormat {
    R11fG11fB10f,
    R32f,
    R16f,
    Rgb16f,
    Rgba8,
    R8,
    Rgba8Snorm,
    Rgba16f,
}

impl ShaderPackColorFormat {
    pub(super) fn parse(value: &str) -> GalResult<Self> {
        match value {
            "R11F_G11F_B10F" => Ok(Self::R11fG11fB10f),
            "R32F" => Ok(Self::R32f),
            "R16F" => Ok(Self::R16f),
            "RGB16F" => Ok(Self::Rgb16f),
            "RGBA8" => Ok(Self::Rgba8),
            "R8" => Ok(Self::R8),
            "RGBA8_SNORM" => Ok(Self::Rgba8Snorm),
            "RGBA16F" => Ok(Self::Rgba16f),
            _ => Err(GalError::unsupported_feature(format!(
                "unsupported shader-pack color target format '{value}'"
            ))),
        }
    }

    /// The exact generic GAL format declared by this source target. Native
    /// device/driver image-format support is checked when the Rust-owned
    /// target cache stages resources; there is no optional schema fallback.
    pub const fn gal_schema_color_format(self) -> TextureFormat {
        match self {
            Self::R11fG11fB10f => TextureFormat::R11fG11fB10f,
            Self::R32f => TextureFormat::R32Float,
            Self::R16f => TextureFormat::R16Float,
            Self::Rgb16f => TextureFormat::Rgb16Float,
            Self::Rgba8 => TextureFormat::Rgba8Unorm,
            Self::R8 => TextureFormat::R8Unorm,
            Self::Rgba8Snorm => TextureFormat::Rgba8Snorm,
            Self::Rgba16f => TextureFormat::Rgba16Float,
        }
    }

    /// Physical Rust-owned storage formats that retain this source-level
    /// color contract. RGB16F has no Vulkan color-attachment guarantee on
    /// common devices, while RGBA16F retains its sampled RGB precision. The
    /// source contract continues to identify the target as RGB16F; callers
    /// only observe the selected backend-neutral storage format.
    pub(super) const fn compatible_storage_formats(self) -> &'static [TextureFormat] {
        match self {
            Self::Rgb16f => &[TextureFormat::Rgb16Float, TextureFormat::Rgba16Float],
            Self::R11fG11fB10f => &[TextureFormat::R11fG11fB10f],
            Self::R32f => &[TextureFormat::R32Float],
            Self::R16f => &[TextureFormat::R16Float],
            Self::Rgba8 => &[TextureFormat::Rgba8Unorm],
            Self::R8 => &[TextureFormat::R8Unorm],
            Self::Rgba8Snorm => &[TextureFormat::Rgba8Snorm],
            Self::Rgba16f => &[TextureFormat::Rgba16Float],
        }
    }

    pub(super) fn accepts_storage_format(self, format: TextureFormat) -> bool {
        match self {
            Self::Rgb16f => matches!(
                format,
                TextureFormat::Rgb16Float | TextureFormat::Rgba16Float
            ),
            _ => self.gal_schema_color_format() == format,
        }
    }
}

/// One named color target as declared by a source generation.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackColorTargetDecl {
    /// Source-declared output slot. This stays source-level metadata: runtime
    /// scheduling resolves it to a Rust-owned attachment, never an Iris or
    /// OpenGL draw-buffer identity.
    pub source_slot: u32,
    pub role: TerrainSourceResourceRole,
    pub format: ShaderPackColorFormat,
    pub clear_each_frame: bool,
    /// Optional literal `colortexNClearColor` supplied by the selected source
    /// generation. Bits preserve exact source values while keeping manifest
    /// equality independent of floating-point comparison rules.
    pub clear_color_bits: Option<[u32; 4]>,
}

impl ShaderPackColorTargetDecl {
    pub fn name(&self) -> &str {
        self.role
            .shader_pack_color_name()
            .expect("source target declarations always use ShaderPackColor roles")
    }

    pub const fn source_slot(&self) -> u32 {
        self.source_slot
    }

    pub const fn gal_schema_color_format(&self) -> TextureFormat {
        self.format.gal_schema_color_format()
    }

    pub(super) fn accepts_storage_format(&self, format: TextureFormat) -> bool {
        self.format.accepts_storage_format(format)
    }
}

/// Immutable source-generation target metadata. It is a preparation artifact
/// only: target allocation, feedback ping-pong, pass execution, and route
/// selection remain separate Rust runtime responsibilities.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackColorTargetManifest {
    pub(super) pack_name: String,
    pub(super) generation: u64,
    pub(super) targets: BTreeMap<String, ShaderPackColorTargetDecl>,
}

impl ShaderPackColorTargetManifest {
    pub fn from_source(
        source: &ShaderPackSource,
        bindings: &TerrainSourceResourceBindings,
    ) -> GalResult<Self> {
        let Some(settings) = source.get(PIPELINE_SETTINGS_PATH) else {
            return Self::from_source_for_scope(source, bindings,
                crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Default);
        };
        let declarations = parse_pipeline_settings(settings)?;
        Self::from_declarations(source, bindings, declarations)
    }

    pub(super) fn from_declarations(
        source: &ShaderPackSource, bindings: &TerrainSourceResourceBindings,
        declarations: BTreeMap<u32, SourceColorSlotDecl>,
    ) -> GalResult<Self> {
        let mut targets = BTreeMap::new();
        for slot in 0..MAX_SOURCE_COLOR_TARGETS {
            let role = bindings.shader_pack_color_output_for_slot(slot)?;
            let name = role
                .shader_pack_color_name()
                .expect("shader_pack_color_output_for_slot returns a color role")
                .to_string();
            // `colortex` directives use the portable OptiFine/Iris protocol:
            // absent properties have a defined RGBA8/clear/no-mipmap default.
            // This is source-language semantics, not an Iris render-target
            // query or a backend substitution.
            let target = declarations
                .get(&slot)
                .copied()
                .unwrap_or_else(SourceColorSlotDecl::protocol_default);
            if targets
                .insert(
                    name.clone(),
                    ShaderPackColorTargetDecl {
                        source_slot: slot,
                        role,
                        format: target.format,
                        clear_each_frame: target.clear_each_frame,
                        clear_color_bits: target.clear_color_bits,
                    },
                )
                .is_some()
            {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack source maps more than one color slot to semantic target '{name}'"
                )));
            }
        }
        Ok(Self {
            pack_name: source.name().to_string(),
            generation: source.generation(),
            targets,
        })
    }

    pub fn pack_name(&self) -> &str {
        &self.pack_name
    }

    pub const fn generation(&self) -> u64 {
        self.generation
    }

    pub fn target(&self, name: &str) -> Option<&ShaderPackColorTargetDecl> {
        self.targets.get(name)
    }

    /// Resolves a source output slot through the pack's semantic target
    /// declaration. A source executor uses this before creating a Rust-owned
    /// render target, so sparse or mismatched `DRAWBUFFERS` locations fail at
    /// source scheduling rather than being guessed by either backend.
    pub fn target_for_source_slot(&self, source_slot: u32) -> Option<&ShaderPackColorTargetDecl> {
        self.targets
            .values()
            .find(|target| target.source_slot == source_slot)
    }

    pub fn targets(&self) -> impl Iterator<Item = &ShaderPackColorTargetDecl> {
        self.targets.values()
    }

    /// Rejects a candidate source plan until every color target it needs has
    /// an exact generic GAL format. This deliberately names the semantic
    /// target, never an underlying image, attachment, or API handle. Native
    /// support remains a separate cache-staging check, before route admission.
    pub fn require_gal_schema_formats(&self) -> GalResult<()> {
        // Every parsed source format has an exact schema mapping. Native
        // image support is checked during Rust-owned target staging.
        Ok(())
    }
}

#[derive(Clone, Copy, Debug)]
pub(super) struct SourceColorSlotDecl {
    pub(super) format: ShaderPackColorFormat,
    pub(super) clear_each_frame: bool,
    pub(super) clear_color_bits: Option<[u32; 4]>,
}

impl SourceColorSlotDecl {
    pub(super) const fn protocol_default() -> Self {
        Self {
            format: ShaderPackColorFormat::Rgba8,
            clear_each_frame: true,
            clear_color_bits: None,
        }
    }
}

#[derive(Default)]
pub(super) struct PartialSourceColorSlotDecl {
    pub(super) format: Option<ShaderPackColorFormat>,
    pub(super) clear_each_frame: Option<bool>,
    pub(super) clear_color_bits: Option<[u32; 4]>,
}

pub(super) fn parse_pipeline_settings(contents: &str) -> GalResult<BTreeMap<u32, SourceColorSlotDecl>> {
    let mut slots = BTreeMap::<u32, PartialSourceColorSlotDecl>::new();
    for (line_number, raw_line) in contents.lines().enumerate() {
        // Shader-pack `const` directives are intentionally discovered from
        // the raw source, including `/* ... */` blocks. Complementary uses
        // that documented directive channel for its colortex formats.
        let line = directive_fragment(raw_line);
        if line.is_empty() {
            continue;
        }
        if let Some((slot, format)) = parse_const_assignment(line, "int", "Format")? {
            let format = ShaderPackColorFormat::parse(&format)?;
            let declaration = slots.entry(slot).or_default();
            if declaration.format.replace(format).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack color target colortex{slot} declares its format more than once at line {}",
                    line_number + 1
                )));
            }
            continue;
        }
        if let Some((slot, value)) = parse_const_assignment(line, "bool", "Clear")? {
            let declaration = slots.entry(slot).or_default();
            if declaration
                .clear_each_frame
                .replace(parse_bool(&value)?)
                .is_some()
            {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack color target colortex{slot} declares its clear policy more than once at line {}",
                    line_number + 1
                )));
            }
            continue;
        }
        if let Some((slot, value)) = parse_const_assignment(line, "vec4", "ClearColor")? {
            let declaration = slots.entry(slot).or_default();
            if declaration
                .clear_color_bits
                .replace(parse_clear_color_bits(&value)?)
                .is_some()
            {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack color target colortex{slot} declares its clear color more than once at line {}",
                    line_number + 1
                )));
            }
            continue;
        }
    }
    let mut complete = BTreeMap::new();
    for (slot, declaration) in slots {
        if slot >= MAX_SOURCE_COLOR_TARGETS {
            return Err(GalError::unsupported_feature(format!(
                "shader-pack color target colortex{slot} exceeds the bounded source target range"
            )));
        }
        let defaults = SourceColorSlotDecl::protocol_default();
        let format = declaration.format.unwrap_or(defaults.format);
        let clear_each_frame = declaration
            .clear_each_frame
            .unwrap_or(defaults.clear_each_frame);
        let clear_color_bits = declaration.clear_color_bits.or(defaults.clear_color_bits);
        complete.insert(
            slot,
            SourceColorSlotDecl {
                format,
                clear_each_frame,
                clear_color_bits,
            },
        );
    }
    Ok(complete)
}

pub(super) fn parse_const_assignment(
    line: &str,
    value_type: &str,
    suffix: &str,
) -> GalResult<Option<(u32, String)>> {
    let Some(rest) = line.strip_prefix(&format!("const {value_type} ")) else {
        return Ok(None);
    };
    let Some((name, value)) = rest.split_once('=') else {
        return Err(GalError::invalid_argument(
            "shader-pack color target declaration is missing '='",
        ));
    };
    let name = name.trim();
    let Some(slot) = name
        .strip_prefix("colortex")
        .and_then(|name| name.strip_suffix(suffix))
    else {
        return Ok(None);
    };
    let slot = slot.parse::<u32>().map_err(|_| {
        GalError::invalid_argument(format!(
            "shader-pack color target declaration '{name}' has an invalid slot"
        ))
    })?;
    let value = value
        .split_once(';')
        .map(|(value, _)| value)
        .unwrap_or(value)
        .trim();
    if value.is_empty() {
        return Err(GalError::invalid_argument(format!(
            "shader-pack color target declaration '{name}' has an empty value"
        )));
    }
    Ok(Some((slot, value.to_string())))
}

pub(super) fn parse_bool(value: &str) -> GalResult<bool> {
    match value {
        "true" => Ok(true),
        "false" => Ok(false),
        _ => Err(GalError::invalid_argument(format!(
            "shader-pack color target boolean must be true or false, found '{value}'"
        ))),
    }
}

/// Parses the bounded literal subset of the shader-pack clear-color
/// directive. Source expressions, macros, and constructors with non-literal
/// values are intentionally rejected until the reusable constant evaluator
/// can represent them; silently substituting a protocol default would make a
/// selected source generation semantically dishonest.
pub(super) fn parse_clear_color_bits(value: &str) -> GalResult<[u32; 4]> {
    let value = value.trim();
    let Some(arguments) = value
        .strip_prefix("vec4(")
        .and_then(|arguments| arguments.strip_suffix(')'))
    else {
        return Err(GalError::unsupported_feature(format!(
            "shader-pack color clear value '{value}' must be a literal vec4(...) constructor"
        )));
    };
    let components = arguments.split(',').map(str::trim).collect::<Vec<_>>();
    let components = match components.as_slice() {
        [component] => [*component, *component, *component, *component],
        [r, g, b, a] => [*r, *g, *b, *a],
        _ => {
            return Err(GalError::invalid_argument(format!(
                "shader-pack color clear value '{value}' must contain one or four literal components"
            )));
        }
    };
    let mut bits = [0; 4];
    for (index, component) in components.into_iter().enumerate() {
        let component = component
            .strip_suffix(['f', 'F'])
            .unwrap_or(component)
            .trim();
        let parsed = component.parse::<f32>().map_err(|_| {
            GalError::invalid_argument(format!(
                "shader-pack color clear component '{component}' is not a finite literal"
            ))
        })?;
        if !parsed.is_finite() {
            return Err(GalError::invalid_argument(format!(
                "shader-pack color clear component '{component}' must be finite"
            )));
        }
        bits[index] = parsed.to_bits();
    }
    Ok(bits)
}

pub(super) fn directive_fragment(line: &str) -> &str {
    line.find("const ")
        .map(|index| line[index..].trim())
        .unwrap_or("")
}
