//! Immutable shader-pack binary asset ownership.
//!
//! Source text and binary pack assets travel independently today. This store
//! provides the Rust-owned, generation-safe binary half without exposing file
//! handles, Java resource objects, or backend texture state. A later runtime
//! slice will decode supported assets and create private GAL resources only
//! after it has matched this snapshot to its source generation.

use std::collections::BTreeMap;
use std::io::BufReader;

use serde_json::Value;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;

pub const SHADER_PROPERTIES_PATH: &str = "shaders.properties";

/// One copied shader-pack binary asset. Paths are pack-relative semantic
/// identities, never host file-system locations.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackAssetFile {
    pub path: String,
    pub bytes: Vec<u8>,
}

impl ShaderPackAssetFile {
    pub fn new(path: impl Into<String>, bytes: Vec<u8>) -> Self {
        Self {
            path: path.into(),
            bytes,
        }
    }
}

/// One bulk, immutable asset generation paired with a shader-pack source
/// generation. This intentionally has no source text, native handle, or
/// resource-cache entry.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackAssetUpdate {
    pub pack_name: String,
    pub generation: u64,
    pub files: Vec<ShaderPackAssetFile>,
}

/// Owned asset snapshot available to a future shader runtime.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackAssets {
    pack_name: String,
    generation: u64,
    files: BTreeMap<String, Vec<u8>>,
}

/// A decoded, Rust-owned RGBA texture candidate. This is a semantic image
/// payload only: a later runtime owns texture/view/sampler creation and no
/// native object is retained here.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackRgbaAsset {
    pub path: String,
    pub width: u32,
    pub height: u32,
    pub pixels_rgba8: Vec<u8>,
}

/// Pack-declared texture sampling semantics. This is intentionally expressed
/// without texture units or backend sampler objects; the Rust shader runtime
/// lowers it into a private GAL sampler only after source execution is fully
/// admitted.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct ShaderPackAssetSamplerPolicy {
    pub blur: bool,
    pub clamp: bool,
}

impl Default for ShaderPackAssetSamplerPolicy {
    fn default() -> Self {
        Self {
            // This is the documented default used by the Java resource path
            // when a PNG has no texture metadata sidecar.
            blur: false,
            clamp: false,
        }
    }
}

/// A decoded image paired with the exact supported pack sampling policy.
/// This remains a copied semantic payload and owns no GAL or backend state.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackResolvedRgbaAsset {
    pub image: ShaderPackRgbaAsset,
    pub sampler_policy: ShaderPackAssetSamplerPolicy,
}

/// Source-derived custom PNG bindings that apply to the terrain G-buffer
/// stage. The mapping is semantic pack data, not an Iris sampler binding or a
/// backend texture-unit assignment. Raw texture declarations and custom
/// images intentionally remain unsupported until their complete resource
/// contracts exist in Rust.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct TerrainShaderPackAssetBindings {
    sampler_paths: BTreeMap<String, String>,
}

impl TerrainShaderPackAssetBindings {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let Some(properties) = source.get(SHADER_PROPERTIES_PATH) else {
            return Ok(Self::default());
        };
        let mut sampler_paths = BTreeMap::new();
        for (line_number, raw_line) in properties.lines().enumerate() {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let Some((raw_key, raw_value)) = line.split_once('=') else {
                continue;
            };
            let key = raw_key.trim();
            let value = raw_value.trim();
            let sampler = if key == "texture.noise" {
                Some("noisetex")
            } else if let Some(suffix) = key.strip_prefix("texture.gbuffers.") {
                suffix.split('.').next().filter(|name| !name.is_empty())
            } else if let Some(suffix) = key.strip_prefix("customTexture.gbuffers_terrain.") {
                Some(suffix)
            } else {
                None
            };
            let Some(sampler) = sampler else {
                continue;
            };
            if !valid_sampler_name(sampler) {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack terrain texture line {} has invalid sampler '{sampler}'",
                    line_number + 1
                )));
            }
            if value.split_ascii_whitespace().count() != 1 {
                return Err(GalError::unsupported_feature(format!(
                    "shader-pack terrain texture '{}' on line {} uses an unsupported raw texture declaration",
                    sampler,
                    line_number + 1
                )));
            }
            let path = normalize_declared_asset_path(value)?;
            if !path.to_ascii_lowercase().ends_with(".png") {
                return Err(GalError::unsupported_feature(format!(
                    "shader-pack terrain texture '{}' on line {} is not a PNG asset",
                    sampler,
                    line_number + 1
                )));
            }
            if sampler_paths.insert(sampler.to_string(), path).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack terrain texture sampler '{sampler}' is declared more than once"
                )));
            }
        }
        Ok(Self { sampler_paths })
    }

    pub fn sampler_path(&self, sampler: &str) -> Option<&str> {
        self.sampler_paths.get(sampler).map(String::as_str)
    }

    pub fn samplers(&self) -> impl Iterator<Item = (&str, &str)> {
        self.sampler_paths
            .iter()
            .map(|(sampler, path)| (sampler.as_str(), path.as_str()))
    }

    /// Resolves one declared sampler to a decoded Rust-owned image. Resource
    /// creation remains downstream, so an unavailable or malformed asset
    /// cannot be mistaken for a renderer-owned texture.
    pub fn resolve_rgba8(
        &self,
        assets: &ShaderPackAssets,
        sampler: &str,
    ) -> GalResult<ShaderPackRgbaAsset> {
        let path = self.sampler_path(sampler).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack terrain texture sampler '{sampler}' is not declared"
            ))
        })?;
        assets.decode_rgba8(path)
    }

    /// Resolves both copied pixels and the source pack's PNG sampling policy.
    /// A malformed texture sidecar is an explicit source-resource failure,
    /// never an invitation to silently choose a backend default.
    pub fn resolve_rgba8_with_sampler_policy(
        &self,
        assets: &ShaderPackAssets,
        sampler: &str,
    ) -> GalResult<ShaderPackResolvedRgbaAsset> {
        let path = self.sampler_path(sampler).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack terrain texture sampler '{sampler}' is not declared"
            ))
        })?;
        Ok(ShaderPackResolvedRgbaAsset {
            image: assets.decode_rgba8(path)?,
            sampler_policy: assets.sampler_policy(path)?,
        })
    }
}

impl ShaderPackAssets {
    pub const MAX_FILES: usize = 4096;
    pub const MAX_FILE_BYTES: usize = 32 * 1024 * 1024;
    pub const MAX_TOTAL_BYTES: usize = 256 * 1024 * 1024;
    pub const MAX_RGBA_PIXELS: usize = 64 * 1024 * 1024;

    pub fn new(update: ShaderPackAssetUpdate) -> GalResult<Self> {
        if update.pack_name.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack asset name is empty",
            ));
        }
        if update.generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack asset generation must be non-zero",
            ));
        }
        if update.files.len() > Self::MAX_FILES {
            return Err(GalError::invalid_argument(format!(
                "shader-pack assets contain {} files, exceeding {}",
                update.files.len(),
                Self::MAX_FILES
            )));
        }

        let mut total_bytes = 0usize;
        let mut files = BTreeMap::new();
        for file in update.files {
            let path = normalize_asset_path(&file.path)?;
            if file.bytes.len() > Self::MAX_FILE_BYTES {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack asset {path} exceeds {} bytes",
                    Self::MAX_FILE_BYTES
                )));
            }
            total_bytes = total_bytes.checked_add(file.bytes.len()).ok_or_else(|| {
                GalError::invalid_argument("shader-pack asset aggregate byte count overflow")
            })?;
            if total_bytes > Self::MAX_TOTAL_BYTES {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack assets exceed {} aggregate bytes",
                    Self::MAX_TOTAL_BYTES
                )));
            }
            if files.insert(path.clone(), file.bytes).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "duplicate shader-pack asset path {path}"
                )));
            }
        }
        Ok(Self {
            pack_name: update.pack_name,
            generation: update.generation,
            files,
        })
    }

    pub fn pack_name(&self) -> &str {
        &self.pack_name
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    /// Returns an owned copy so callers cannot mutate the cache after
    /// validation. Backend uploads are intentionally downstream of this API.
    pub fn copy(&self, path: &str) -> Option<Vec<u8>> {
        normalize_asset_path(path)
            .ok()
            .and_then(|path| self.files.get(&path).cloned())
    }

    pub fn paths(&self) -> impl Iterator<Item = &str> {
        self.files.keys().map(String::as_str)
    }

    /// Decodes a copied PNG into a caller-owned RGBA8 image. It intentionally
    /// rejects every other format until a source-resource contract explicitly
    /// admits and implements it; silent format substitution would make a
    /// selected shader program appear resource-complete when it is not.
    pub fn decode_rgba8(&self, path: &str) -> GalResult<ShaderPackRgbaAsset> {
        let normalized = normalize_asset_path(path)?;
        if !normalized.to_ascii_lowercase().ends_with(".png") {
            return Err(GalError::unsupported_feature(format!(
                "shader-pack asset '{normalized}' is not a supported PNG texture"
            )));
        }
        let bytes = self.files.get(&normalized).ok_or_else(|| {
            GalError::invalid_argument(format!("shader-pack asset '{normalized}' is missing"))
        })?;
        let mut decoder = png::Decoder::new(BufReader::new(bytes.as_slice()));
        decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
        let mut reader = decoder.read_info().map_err(|error| {
            GalError::invalid_argument(format!("shader-pack PNG '{normalized}' header: {error}"))
        })?;
        let info = reader.info();
        let pixels = (info.width as usize)
            .checked_mul(info.height as usize)
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "shader-pack PNG '{normalized}' dimensions overflow"
                ))
            })?;
        if pixels == 0 || pixels > Self::MAX_RGBA_PIXELS {
            return Err(GalError::invalid_argument(format!(
                "shader-pack PNG '{normalized}' has {pixels} pixels; maximum is {}",
                Self::MAX_RGBA_PIXELS
            )));
        }
        let mut decoded = vec![0u8; reader.output_buffer_size()];
        let output = reader.next_frame(&mut decoded).map_err(|error| {
            GalError::invalid_argument(format!("shader-pack PNG '{normalized}' decode: {error}"))
        })?;
        let bytes = &decoded[..output.buffer_size()];
        let pixels_rgba8 = match output.color_type {
            png::ColorType::Rgba => bytes.to_vec(),
            png::ColorType::Rgb => bytes
                .chunks_exact(3)
                .flat_map(|rgb| [rgb[0], rgb[1], rgb[2], 255])
                .collect(),
            png::ColorType::GrayscaleAlpha => bytes
                .chunks_exact(2)
                .flat_map(|gray_alpha| [gray_alpha[0], gray_alpha[0], gray_alpha[0], gray_alpha[1]])
                .collect(),
            png::ColorType::Grayscale => bytes
                .iter()
                .flat_map(|gray| [*gray, *gray, *gray, 255])
                .collect(),
            png::ColorType::Indexed => {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack PNG '{normalized}' stayed indexed after expansion"
                )));
            }
        };
        let expected_bytes = pixels.checked_mul(4).ok_or_else(|| {
            GalError::invalid_argument(format!("shader-pack PNG '{normalized}' RGBA size overflow"))
        })?;
        if pixels_rgba8.len() != expected_bytes {
            return Err(GalError::invalid_argument(format!(
                "shader-pack PNG '{normalized}' decoded {} RGBA bytes; expected {expected_bytes}",
                pixels_rgba8.len()
            )));
        }
        Ok(ShaderPackRgbaAsset {
            path: normalized,
            width: output.width,
            height: output.height,
            pixels_rgba8,
        })
    }

    /// Reads only the supported Minecraft texture metadata fields. The
    /// sidecar is copied together with its PNG, so parsing cannot observe a
    /// mutable pack file after validation. Unknown metadata remains inert,
    /// matching the resource loader's texture-specific behavior.
    pub fn sampler_policy(&self, path: &str) -> GalResult<ShaderPackAssetSamplerPolicy> {
        let normalized = normalize_asset_path(path)?;
        let sidecar = format!("{normalized}.mcmeta");
        let Some(bytes) = self.files.get(&sidecar) else {
            return Ok(ShaderPackAssetSamplerPolicy::default());
        };
        let root: Value = serde_json::from_slice(bytes).map_err(|error| {
            GalError::invalid_argument(format!(
                "shader-pack texture metadata '{sidecar}' is malformed: {error}"
            ))
        })?;
        let Some(texture) = root.get("texture") else {
            return Ok(ShaderPackAssetSamplerPolicy::default());
        };
        let texture = texture.as_object().ok_or_else(|| {
            GalError::invalid_argument(format!(
                "shader-pack texture metadata '{sidecar}' field 'texture' must be an object"
            ))
        })?;
        let blur = metadata_bool(texture.get("blur"), "blur", &sidecar)?
            .unwrap_or(ShaderPackAssetSamplerPolicy::default().blur);
        let clamp = metadata_bool(texture.get("clamp"), "clamp", &sidecar)?
            .unwrap_or(ShaderPackAssetSamplerPolicy::default().clamp);
        Ok(ShaderPackAssetSamplerPolicy { blur, clamp })
    }
}

/// Generation-safe asset replacement. A failed update keeps the previous
/// valid snapshot intact, so shader resource reload can remain atomic.
#[derive(Default)]
pub struct ShaderPackAssetStore {
    active: Option<ShaderPackAssets>,
    failed_generations: Vec<u64>,
}

impl ShaderPackAssetStore {
    pub const MAX_FAILED_GENERATIONS: usize = 32;

    pub fn apply_update(&mut self, update: ShaderPackAssetUpdate) -> GalResult<()> {
        if self
            .active
            .as_ref()
            .is_some_and(|active| update.generation <= active.generation())
        {
            self.record_failed_generation(update.generation);
            return Err(GalError::invalid_argument(
                "shader-pack asset generation is stale",
            ));
        }
        let generation = update.generation;
        match ShaderPackAssets::new(update) {
            Ok(candidate) => {
                self.active = Some(candidate);
                Ok(())
            }
            Err(error) => {
                self.record_failed_generation(generation);
                Err(error)
            }
        }
    }

    /// Returns an asset snapshot only if it belongs to the exact active source
    /// pack identity and generation. This blocks mixed source/resource reloads
    /// before a shader runtime allocates anything.
    pub fn active_for_source(
        &self,
        source_name: &str,
        source_generation: u64,
    ) -> GalResult<&ShaderPackAssets> {
        let active = self.active.as_ref().ok_or_else(|| {
            GalError::invalid_argument("shader-pack assets have not been provided")
        })?;
        if active.pack_name() != source_name || active.generation() != source_generation {
            return Err(GalError::invalid_argument(format!(
                "shader-pack asset snapshot '{}'/{} does not match source '{}'/{}",
                active.pack_name(),
                active.generation(),
                source_name,
                source_generation
            )));
        }
        Ok(active)
    }

    pub fn active(&self) -> Option<&ShaderPackAssets> {
        self.active.as_ref()
    }

    pub fn failed_generations(&self) -> &[u64] {
        &self.failed_generations
    }

    fn record_failed_generation(&mut self, generation: u64) {
        if self.failed_generations.len() == Self::MAX_FAILED_GENERATIONS {
            self.failed_generations.remove(0);
        }
        self.failed_generations.push(generation);
    }
}

fn normalize_asset_path(path: &str) -> GalResult<String> {
    let path = path.replace('\\', "/");
    if path.is_empty()
        || path.starts_with('/')
        || path.split('/').any(|segment| {
            segment.is_empty() || segment == "." || segment == ".." || segment.contains('\0')
        })
    {
        return Err(GalError::invalid_argument(
            "shader-pack asset path is not a normalized relative path",
        ));
    }
    Ok(path)
}

/// `ShaderPack.readTexture` accepts one leading slash in shader-pack texture
/// declarations, then resolves the remainder relative to the pack root. Keep
/// that source syntax compatibility localized here; transported asset paths
/// themselves remain strictly normalized relative paths.
fn normalize_declared_asset_path(path: &str) -> GalResult<String> {
    let path = path.trim();
    if path.contains(':') {
        return Err(GalError::unsupported_feature(
            "resource-location shader-pack textures are not implemented by the Rust-owned source resource path",
        ));
    }
    normalize_asset_path(path.strip_prefix('/').unwrap_or(path))
}

fn metadata_bool(value: Option<&Value>, field: &str, sidecar: &str) -> GalResult<Option<bool>> {
    match value {
        None => Ok(None),
        Some(Value::Bool(value)) => Ok(Some(*value)),
        Some(_) => Err(GalError::invalid_argument(format!(
            "shader-pack texture metadata '{sidecar}' field '{field}' must be boolean"
        ))),
    }
}

fn valid_sampler_name(name: &str) -> bool {
    let mut bytes = name.bytes();
    matches!(bytes.next(), Some(byte) if byte == b'_' || byte.is_ascii_alphabetic())
        && bytes.all(|byte| byte == b'_' || byte.is_ascii_alphanumeric())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn png(width: u32, height: u32, rgba: &[u8]) -> Vec<u8> {
        let mut bytes = Vec::new();
        let mut encoder = png::Encoder::new(&mut bytes, width, height);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        encoder
            .write_header()
            .unwrap()
            .write_image_data(rgba)
            .unwrap();
        bytes
    }

    fn update(generation: u64, bytes: &[u8]) -> ShaderPackAssetUpdate {
        ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation,
            files: vec![ShaderPackAssetFile::new(
                "textures/noise.png",
                bytes.to_vec(),
            )],
        }
    }

    #[test]
    fn copies_binary_assets_and_normalizes_pack_relative_paths() {
        let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 3,
            files: vec![ShaderPackAssetFile::new(
                "textures\\noise.png",
                vec![1, 2, 3],
            )],
        })
        .unwrap();
        assert_eq!(Some(vec![1, 2, 3]), assets.copy("textures/noise.png"));
        let mut caller_copy = assets.copy("textures/noise.png").unwrap();
        caller_copy[0] = 99;
        assert_eq!(Some(vec![1, 2, 3]), assets.copy("textures/noise.png"));
        assert_eq!(
            vec!["textures/noise.png"],
            assets.paths().collect::<Vec<_>>()
        );
        assert!(ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 3,
            files: vec![ShaderPackAssetFile::new("../escape.png", Vec::new())],
        })
        .is_err());
    }

    #[test]
    fn store_keeps_prior_generation_on_stale_or_malformed_updates() {
        let mut store = ShaderPackAssetStore::default();
        store.apply_update(update(1, &[4, 5])).unwrap();
        assert!(store.apply_update(update(1, &[6])).is_err());
        assert!(store
            .apply_update(ShaderPackAssetUpdate {
                pack_name: "selected-pack".to_string(),
                generation: 2,
                files: vec![ShaderPackAssetFile::new("textures/../bad.png", Vec::new())],
            })
            .is_err());
        let active = store.active_for_source("selected-pack", 1).unwrap();
        assert_eq!(Some(vec![4, 5]), active.copy("textures/noise.png"));
        assert_eq!(vec![1, 2], store.failed_generations());
        assert!(store.active_for_source("other-pack", 1).is_err());
        assert!(store.active_for_source("selected-pack", 2).is_err());
    }

    #[test]
    fn decodes_owned_png_without_exposing_backend_resource_state() {
        let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 3,
            files: vec![ShaderPackAssetFile::new(
                "textures/noise.png",
                png(2, 1, &[1, 2, 3, 4, 5, 6, 7, 8]),
            )],
        })
        .unwrap();
        let decoded = assets.decode_rgba8("textures/noise.png").unwrap();
        assert_eq!("textures/noise.png", decoded.path);
        assert_eq!((2, 1), (decoded.width, decoded.height));
        assert_eq!(vec![1, 2, 3, 4, 5, 6, 7, 8], decoded.pixels_rgba8);
        assert!(assets.decode_rgba8("textures/missing.png").is_err());
        assert!(assets.decode_rgba8("textures/noise.raw").is_err());
    }

    #[test]
    fn resolves_png_sampler_metadata_without_backend_state() {
        let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 3,
            files: vec![
                ShaderPackAssetFile::new("textures/noise.png", png(1, 1, &[1, 2, 3, 4])),
                ShaderPackAssetFile::new(
                    "textures/noise.png.mcmeta",
                    br#"{"texture": {"blur": true, "clamp": false}}"#.to_vec(),
                ),
            ],
        })
        .unwrap();
        assert_eq!(
            ShaderPackAssetSamplerPolicy {
                blur: true,
                clamp: false,
            },
            assets.sampler_policy("textures/noise.png").unwrap()
        );
        assert_eq!(
            ShaderPackAssetSamplerPolicy::default(),
            assets.sampler_policy("textures/missing.png").unwrap()
        );
        assert!(ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 4,
            files: vec![
                ShaderPackAssetFile::new("textures/bad.png", png(1, 1, &[1, 2, 3, 4])),
                ShaderPackAssetFile::new("textures/bad.png.mcmeta", b"{bad".to_vec()),
            ],
        })
        .unwrap()
        .sampler_policy("textures/bad.png")
        .is_err());
    }

    #[test]
    fn rejects_malformed_png_before_resource_creation() {
        let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 3,
            files: vec![ShaderPackAssetFile::new("textures/bad.png", vec![1, 2, 3])],
        })
        .unwrap();
        assert!(assets
            .decode_rgba8("textures/bad.png")
            .unwrap_err()
            .to_string()
            .contains("header"));
    }

    #[test]
    fn parses_iris_property_texture_declarations_as_pack_semantics() {
        let source = ShaderPackSource::new(
            "selected-pack",
            3,
            vec![super::super::source::ShaderSourceFile::new(
                SHADER_PROPERTIES_PATH,
                concat!(
                    "texture.noise=lib/textures/noise.png\n",
                    "texture.gbuffers.gaux4=lib/textures/cloud-water.png\n",
                    "customTexture.gbuffers_terrain.detail=lib/textures/detail.png\n",
                ),
            )],
        )
        .unwrap();
        let bindings = TerrainShaderPackAssetBindings::from_source(&source).unwrap();
        assert_eq!(
            Some("lib/textures/noise.png"),
            bindings.sampler_path("noisetex")
        );
        assert_eq!(
            Some("lib/textures/cloud-water.png"),
            bindings.sampler_path("gaux4")
        );
        assert_eq!(
            Some("lib/textures/detail.png"),
            bindings.sampler_path("detail")
        );

        let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 3,
            files: vec![ShaderPackAssetFile::new(
                "lib/textures/noise.png",
                png(1, 1, &[9, 8, 7, 6]),
            )],
        })
        .unwrap();
        assert_eq!(
            vec![9, 8, 7, 6],
            bindings
                .resolve_rgba8(&assets, "noisetex")
                .unwrap()
                .pixels_rgba8
        );
        assert!(bindings.resolve_rgba8(&assets, "gaux4").is_err());
    }

    #[test]
    fn rejects_raw_or_duplicated_terrain_texture_declarations() {
        let raw = ShaderPackSource::new(
            "selected-pack",
            3,
            vec![super::super::source::ShaderSourceFile::new(
                SHADER_PROPERTIES_PATH,
                "texture.gbuffers.noisetex=volume.raw TEXTURE_3D rgba16f 16 16 16 rgba half_float\n",
            )],
        )
        .unwrap();
        assert!(TerrainShaderPackAssetBindings::from_source(&raw)
            .unwrap_err()
            .to_string()
            .contains("raw texture"));
        let duplicate = ShaderPackSource::new(
            "selected-pack",
            3,
            vec![super::super::source::ShaderSourceFile::new(
                SHADER_PROPERTIES_PATH,
                "texture.noise=lib/one.png\ntexture.gbuffers.noisetex=lib/two.png\n",
            )],
        )
        .unwrap();
        assert!(TerrainShaderPackAssetBindings::from_source(&duplicate).is_err());

        let leading_slash = ShaderPackSource::new(
            "selected-pack",
            3,
            vec![super::super::source::ShaderSourceFile::new(
                SHADER_PROPERTIES_PATH,
                "texture.noise=/lib/textures/noise.png\n",
            )],
        )
        .unwrap();
        assert_eq!(
            Some("lib/textures/noise.png"),
            TerrainShaderPackAssetBindings::from_source(&leading_slash)
                .unwrap()
                .sampler_path("noisetex")
        );

        let resource_location = ShaderPackSource::new(
            "selected-pack",
            3,
            vec![super::super::source::ShaderSourceFile::new(
                SHADER_PROPERTIES_PATH,
                "texture.noise=minecraft:textures/atlas/blocks.png\n",
            )],
        )
        .unwrap();
        assert!(
            TerrainShaderPackAssetBindings::from_source(&resource_location)
                .unwrap_err()
                .to_string()
                .contains("resource-location")
        );
    }
}
