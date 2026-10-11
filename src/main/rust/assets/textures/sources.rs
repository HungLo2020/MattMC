//! Atlas definitions (`atlases/<id>.json`) and their sprite sources
//! (`SpriteSourceList`, `SpriteSources`, `sources/*`).
//!
//! Every pack's definition is read, lowest pack first, and their source
//! lists are concatenated; a definition that fails to decode is logged and
//! skipped. Sources then run in order against one map of sprite suppliers:
//! adding replaces an existing id, and a filter removes matching ids.

use std::collections::HashMap;
use std::sync::atomic::{AtomicPtr, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use super::image::{decode_png, Image};
use crate::assets::resources::manager::{Filters, Found, Located, Manager};
use crate::content::tags::Location;
use crate::core::json::{self, Json};
use crate::core::java_hash::{iteration_order_in, location_hash, table_size, Insertion};
use crate::core::{dfu, id, java_log};

/// `Unstitcher.Region`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Region {
    pub(crate) sprite: Location,
    pub(crate) x: f64,
    pub(crate) y: f64,
    pub(crate) width: f64,
    pub(crate) height: f64,
}

/// `ResourceLocationPattern`: Java regexes, matched with `find()`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Pattern {
    pub(crate) namespace: Option<String>,
    pub(crate) path: Option<String>,
}

/// One entry of an atlas definition's `sources`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Source {
    Single { resource: Location, sprite: Option<Location> },
    Directory { source: String, prefix: String },
    Filter(Pattern),
    Unstitch { resource: Location, regions: Vec<Region>, divisor_x: f64, divisor_y: f64 },
    Paletted { textures: Vec<Location>, palette_key: Location, permutations: Vec<(String, Location)>, separator: String },
}

// -- Java regexes --------------------------------------------------------

/// `matches(pattern, text)`: 1 match, 0 no match, -1 the pattern does not
/// compile (with `compile` set, only compiles).
type PatternUpcall = unsafe extern "C" fn(pattern: *const u8, pattern_len: u64, text: *const u8, text_len: u64, compile: i32) -> i32;

static PATTERN: AtomicPtr<()> = AtomicPtr::new(std::ptr::null_mut());

/// Registers Java's regex upcall.
///
/// # Safety
/// `upcall` must stay valid for the process.
#[no_mangle]
pub unsafe extern "C" fn mattmc_textures_register_pattern(upcall: Option<PatternUpcall>) {
    PATTERN.store(upcall.map_or(std::ptr::null_mut(), |upcall| upcall as *mut ()), Ordering::Release);
}

fn ask_pattern(pattern: &str, text: &str, compile: bool) -> i32 {
    let upcall = PATTERN.load(Ordering::Acquire);
    if upcall.is_null() {
        return -1;
    }
    let upcall: PatternUpcall = unsafe { std::mem::transmute::<*mut (), PatternUpcall>(upcall) };
    unsafe { upcall(pattern.as_ptr(), pattern.len() as u64, text.as_ptr(), text.len() as u64, i32::from(compile)) }
}

/// `ExtraCodecs.PATTERN`.
fn regex(value: &Json) -> Result<String, String> {
    let pattern = dfu::string(value)?;
    if ask_pattern(pattern, "", true) < 0 {
        return Err(format!("Invalid regex pattern '{pattern}'"));
    }
    Ok(pattern.to_owned())
}

impl Pattern {
    fn matches(&self, location: &Location) -> bool {
        let test = |pattern: &Option<String>, text: &str| pattern.as_ref().is_none_or(|pattern| ask_pattern(pattern, text, false) > 0);
        test(&self.namespace, &location.namespace) && test(&self.path, &location.path)
    }
}

// -- definitions ---------------------------------------------------------

fn region(value: &Json) -> Result<Region, String> {
    let object = dfu::object(value)?;
    Ok(Region {
        sprite: dfu::required(object, "sprite", dfu::location)?,
        x: dfu::required(object, "x", dfu::double)?,
        y: dfu::required(object, "y", dfu::double)?,
        width: dfu::required(object, "width", dfu::double)?,
        height: dfu::required(object, "height", dfu::double)?,
    })
}

/// `SpriteSources.CODEC`: dispatched on `type`.
fn source(value: &Json) -> Result<Source, String> {
    let object = dfu::object(value)?;
    let kind = dfu::required(object, "type", dfu::location)?;
    if &*kind.namespace != "minecraft" {
        return Err(format!("Unknown registry key: {kind}"));
    }
    Ok(match &*kind.path {
        "single" => Source::Single {
            resource: dfu::required(object, "resource", dfu::location)?,
            sprite: dfu::optional(object, "sprite", dfu::location)?,
        },
        "directory" => Source::Directory {
            source: dfu::required(object, "source", dfu::string)?.to_owned(),
            prefix: dfu::required(object, "prefix", dfu::string)?.to_owned(),
        },
        "filter" => {
            let pattern = dfu::required(object, "pattern", dfu::object)?;
            Source::Filter(Pattern { namespace: dfu::optional(pattern, "namespace", regex)?, path: dfu::optional(pattern, "path", regex)? })
        }
        "unstitch" => {
            let regions = dfu::required(object, "regions", |regions| dfu::list(regions, region))?;
            if regions.is_empty() {
                return Err("List must have contents".into());
            }
            Source::Unstitch {
                resource: dfu::required(object, "resource", dfu::location)?,
                regions,
                divisor_x: dfu::optional(object, "divisor_x", dfu::double)?.unwrap_or(1.0),
                divisor_y: dfu::optional(object, "divisor_y", dfu::double)?.unwrap_or(1.0),
            }
        }
        "paletted_permutations" => Source::Paletted {
            textures: dfu::required(object, "textures", |textures| dfu::list(textures, dfu::location))?,
            palette_key: dfu::required(object, "palette_key", dfu::location)?,
            permutations: dfu::required(object, "permutations", |permutations| {
                dfu::object(permutations)?.iter().map(|(key, value)| Ok((key.to_owned(), dfu::location(value)?))).collect::<Result<Vec<_>, String>>()
            })?,
            separator: dfu::optional(object, "separator", dfu::string)?.unwrap_or("_").to_owned(),
        },
        _ => return Err(format!("Unknown registry key: {kind}")),
    })
}

/// `SpriteSources.FILE_CODEC` over a definition file read by
/// `StrictJsonParser` (nothing may follow the value).
pub(crate) fn decode_definition(bytes: &[u8]) -> Result<Vec<Source>, String> {
    let document = json::parse_document(bytes).map_err(|error| error.to_string())?;
    let object = dfu::object(&document)?;
    dfu::required(object, "sources", |sources| dfu::list(sources, source))
}

/// `SpriteSourceList.load`: every pack's definition, lowest first.
pub(crate) fn load_definitions(manager: &Manager, filters: &dyn Filters, atlas: &Location) -> Vec<Source> {
    let path = format!("atlases/{}.json", atlas.path);
    let mut sources = Vec::new();
    for found in manager.resource_stack(&atlas.namespace, &path, filters) {
        let decoded = manager.read(found.resource, &atlas.namespace, &path).map_err(|error| error.to_string()).and_then(|bytes| decode_definition(&bytes));
        match decoded {
            Ok(mut list) => sources.append(&mut list),
            Err(error) => java_log::error(&format!(
                "Failed to parse atlas definition {}:{path} in pack {}\n{error}",
                atlas.namespace,
                manager.pack_id(found.resource.pack)
            )),
        }
    }
    sources
}

// -- suppliers -----------------------------------------------------------

/// A texture file shared by several suppliers, decoded once
/// (`LazyLoadedImage`).
#[derive(Debug)]
pub(crate) struct SharedImage {
    pub(crate) file: Location,
    pub(crate) located: Located,
    image: OnceLock<Result<Arc<Image>, String>>,
}

impl SharedImage {
    fn new(file: Location, located: Located) -> Arc<SharedImage> {
        Arc::new(SharedImage { file, located, image: OnceLock::new() })
    }

    pub(crate) fn get(&self, manager: &Manager) -> Result<Arc<Image>, String> {
        self.image
            .get_or_init(|| {
                manager
                    .read(self.located, &self.file.namespace, &self.file.path)
                    .map_err(|error| error.to_string())
                    .and_then(|bytes| decode_png(&bytes))
                    .map(Arc::new)
                    .map_err(|error| format!("Failed to load image {}: {error}", self.file))
            })
            .clone()
    }
}

/// A palette image's colors in ARGB (`NativeImage.getPixels`). Like
/// Guava's `Suppliers.memoize`, only a success is kept: a failure is
/// retried, and logged, on every use.
#[derive(Debug)]
pub(crate) struct Palette {
    pub(crate) id: Location,
    pub(crate) located: Option<Located>,
    colors: Mutex<Option<Arc<Vec<u32>>>>,
}

impl Palette {
    fn new(manager: &Manager, filters: &dyn Filters, id: Location) -> Arc<Palette> {
        let path = texture_path(&id);
        let located = manager.resource(&id.namespace, &path, filters).map(|(located, _)| located);
        Arc::new(Palette { id, located, colors: Mutex::new(None) })
    }

    /// `loadPaletteEntryFromImage`, logging its errors.
    pub(crate) fn colors(&self, manager: &Manager) -> Result<Arc<Vec<u32>>, ()> {
        let mut colors = self.colors.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        if let Some(colors) = colors.as_ref() {
            return Ok(colors.clone());
        }
        let Some(located) = self.located else {
            java_log::error(&format!("Failed to load palette image {}", self.id));
            return Err(());
        };
        let path = texture_path(&self.id);
        match manager.read(located, &self.id.namespace, &path).map_err(|error| error.to_string()).and_then(|bytes| decode_png(&bytes)) {
            Ok(image) => {
                let loaded: Arc<Vec<u32>> = Arc::new(
                    (0..image.height).flat_map(|y| (0..image.width).map(move |x| (x, y))).map(|(x, y)| abgr_to_argb(image.abgr(x, y))).collect(),
                );
                *colors = Some(loaded.clone());
                Ok(loaded)
            }
            Err(error) => {
                java_log::error(&format!("Couldn't load texture {}\n{error}", self.id));
                Err(())
            }
        }
    }
}

/// A paletted permutation: the key palette and one permutation's palette,
/// mapped once on success (`Suppliers.memoize`).
#[derive(Debug)]
pub(crate) struct Permutation {
    pub(crate) key: Arc<Palette>,
    pub(crate) target: Arc<Palette>,
    mapping: Mutex<Option<Arc<HashMap<u32, u32>>>>,
}

impl Permutation {
    /// `createPaletteMapping`: key color (RGB) to permuted color (ARGB).
    pub(crate) fn mapping(&self, manager: &Manager) -> Result<Arc<HashMap<u32, u32>>, ()> {
        let mut mapping = self.mapping.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        if let Some(mapping) = mapping.as_ref() {
            return Ok(mapping.clone());
        }
        let key = self.key.colors(manager)?;
        let target = self.target.colors(manager)?;
        if key.len() != target.len() {
            java_log::warn(&format!("Palette mapping has different sizes: {} and {}", key.len(), target.len()));
            return Err(());
        }
        let mut built = HashMap::with_capacity(key.len());
        for (&from, &to) in key.iter().zip(target.iter()) {
            if from >> 24 != 0 {
                built.insert(from & 0x00FF_FFFF, to);
            }
        }
        let built = Arc::new(built);
        *mapping = Some(built.clone());
        Ok(built)
    }
}

/// `SpriteSource.SpriteSupplier`.
#[derive(Clone, Debug)]
pub(crate) enum Supplier {
    /// A texture file, its sidecar metadata, read by the sprite loader.
    File { file: Location, found: Found },
    Unstitch { image: Arc<SharedImage>, region: Region, divisor_x: f64, divisor_y: f64 },
    Paletted { image: Arc<SharedImage>, permutation: Arc<Permutation> },
}

pub(crate) fn abgr_to_argb(abgr: u32) -> u32 {
    (abgr & 0xFF00_FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF)
}

pub(crate) fn argb_to_abgr(argb: u32) -> u32 {
    abgr_to_argb(argb)
}

/// `SpriteSource.TEXTURE_ID_CONVERTER.idToFile`.
fn texture_path(id: &Location) -> String {
    format!("textures/{}.png", id.path)
}

/// `ResourceLocation.withPath`, which rejects invalid paths.
fn with_path(location: &Location, path: String) -> Result<Location, String> {
    if !id::is_valid_path(&path) {
        return Err(format!("Non [a-z0-9/._-] character in path of location: {}:{path}", location.namespace));
    }
    Ok(Location::new(&location.namespace, &path))
}

/// The supplier map (`SpriteSourceList.list`'s `HashMap`): entries in
/// insertion order, a replaced id keeping its place (`put`), and the peak
/// size the table grew to.
#[derive(Default)]
struct Output {
    entries: Vec<Option<(Location, Supplier)>>,
    by_id: HashMap<Location, usize>,
    live: usize,
    peak: usize,
}

impl Output {
    fn add(&mut self, id: Location, supplier: Supplier) {
        match self.by_id.get(&id) {
            Some(&slot) => self.entries[slot] = Some((id, supplier)),
            None => {
                self.by_id.insert(id.clone(), self.entries.len());
                self.entries.push(Some((id, supplier)));
                self.live += 1;
                self.peak = self.peak.max(self.live);
            }
        }
    }

    fn remove_all(&mut self, pattern: &Pattern) {
        for entry in self.entries.iter_mut() {
            if entry.as_ref().is_some_and(|(id, _)| pattern.matches(id)) {
                let (id, _) = entry.take().unwrap();
                self.by_id.remove(&id);
                self.live -= 1;
            }
        }
    }

    /// `map.values()` in Java's iteration order.
    fn into_values(self) -> Vec<(Location, Supplier)> {
        let live: Vec<(Location, Supplier)> = self.entries.into_iter().flatten().collect();
        let hashes: Vec<i32> = live.iter().map(|(id, _)| location_hash(&id.namespace, &id.path)).collect();
        let table = table_size(16, self.peak);
        let mut slots: Vec<Option<(Location, Supplier)>> = live.into_iter().map(Some).collect();
        iteration_order_in(&hashes, table, Insertion::Put).into_iter().map(|index| slots[index].take().unwrap()).collect()
    }
}

/// Runs `sources` in order: the atlas's sprite suppliers (the missing
/// sprite excluded). Fails only where Java would throw out of the reload.
pub(crate) fn evaluate(manager: &Manager, filters: &dyn Filters, sources: &[Source]) -> Result<Vec<(Location, Supplier)>, String> {
    let mut output = Output::default();
    for source in sources {
        match source {
            Source::Single { resource, sprite } => {
                let path = texture_path(resource);
                match manager.resource(&resource.namespace, &path, filters) {
                    Some((located, position)) => {
                        let metadata = manager.stack_metadata(&resource.namespace, &path, position, filters);
                        let file = Location::new(&resource.namespace, &path);
                        output.add(sprite.clone().unwrap_or_else(|| resource.clone()), Supplier::File { file, found: Found { resource: located, metadata } });
                    }
                    None => java_log::warn(&format!("Missing sprite: {}:{path}", resource.namespace)),
                }
            }
            Source::Directory { source, prefix } => {
                let directory = format!("textures/{source}");
                for ((namespace, path), found) in manager.list(&directory, filters) {
                    if !path.ends_with(".png") {
                        continue;
                    }
                    // MattMC: PBR companions (`_n`/`_s`) of a present base texture are not sprites.
                    let base = path.strip_suffix("_n.png").or_else(|| path.strip_suffix("_s.png"));
                    if let Some(base) = base {
                        if manager.resource(&namespace, &format!("{base}.png"), filters).is_some() {
                            continue;
                        }
                    }
                    let file = Location::new(&namespace, &path);
                    let relative = &path[directory.len() + 1..path.len() - ".png".len()];
                    let id = with_path(&file, format!("{prefix}{relative}"))?;
                    output.add(id, Supplier::File { file, found });
                }
            }
            Source::Filter(pattern) => output.remove_all(pattern),
            Source::Unstitch { resource, regions, divisor_x, divisor_y } => {
                let path = texture_path(resource);
                match manager.resource(&resource.namespace, &path, filters) {
                    Some((located, _)) => {
                        let image = SharedImage::new(Location::new(&resource.namespace, &path), located);
                        for region in regions {
                            output.add(
                                region.sprite.clone(),
                                Supplier::Unstitch { image: image.clone(), region: region.clone(), divisor_x: *divisor_x, divisor_y: *divisor_y },
                            );
                        }
                    }
                    None => java_log::warn(&format!("Missing sprite: {}:{path}", resource.namespace)),
                }
            }
            Source::Paletted { textures, palette_key, permutations, separator } => {
                let key = Palette::new(manager, filters, palette_key.clone());
                let permutations: Vec<(String, Arc<Permutation>)> = permutations
                    .iter()
                    .map(|(name, target)| {
                        let target = Palette::new(manager, filters, target.clone());
                        (name.clone(), Arc::new(Permutation { key: key.clone(), target, mapping: Mutex::new(None) }))
                    })
                    .collect();
                for texture in textures {
                    let path = texture_path(texture);
                    match manager.resource(&texture.namespace, &path, filters) {
                        Some((located, _)) => {
                            let image = SharedImage::new(Location::new(&texture.namespace, &path), located);
                            for (name, permutation) in &permutations {
                                let id = with_path(texture, format!("{}{separator}{name}", texture.path))?;
                                output.add(id, Supplier::Paletted { image: image.clone(), permutation: permutation.clone() });
                            }
                        }
                        None => java_log::warn(&format!("Unable to find texture {}:{path}", texture.namespace)),
                    }
                }
            }
        }
    }
    Ok(output.into_values())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_definitions() {
        let sources = decode_definition(
            br#"{"sources":[
                {"type":"directory","source":"block","prefix":"block/"},
                {"type":"minecraft:single","resource":"item/apple","sprite":"x:y"},
                {"type":"unstitch","resource":"a","divisor_x":2,"regions":[{"sprite":"s","x":0,"y":0,"width":1,"height":1}]},
                {"type":"paletted_permutations","textures":["t"],"palette_key":"k","permutations":{"red":"p/red"}}
            ]}"#,
        )
        .unwrap();
        assert_eq!(Source::Directory { source: "block".into(), prefix: "block/".into() }, sources[0]);
        assert_eq!(Source::Single { resource: Location::new("minecraft", "item/apple"), sprite: Some(Location::new("x", "y")) }, sources[1]);
        assert!(matches!(&sources[2], Source::Unstitch { divisor_x, divisor_y, .. } if *divisor_x == 2.0 && *divisor_y == 1.0));
        assert!(matches!(&sources[3], Source::Paletted { separator, .. } if separator == "_"));
        assert!(decode_definition(br#"{"sources":[{"type":"bogus"}]}"#).is_err());
        assert!(decode_definition(br#"{"sources":[{"type":"unstitch","resource":"a","regions":[]}]}"#).is_err());
        assert!(decode_definition(br#"{"nope":[]}"#).is_err());
        assert!(decode_definition(br#"{"sources":[]} trailing"#).is_err(), "StrictJsonParser reads the whole document");
        assert_eq!(0x11223344, abgr_to_argb(argb_to_abgr(0x11223344)));
        assert_eq!(0xFF332211, abgr_to_argb(0xFF112233));
    }

    #[test]
    fn later_sources_replace_and_filters_remove() {
        let mut output = Output::default();
        let file = |n: u64| Supplier::File {
            file: Location::new("minecraft", "f"),
            found: Found { resource: Located { pack: 0, entry: crate::assets::packs::pack::EntryRef::decode(n) }, metadata: None },
        };
        output.add(Location::new("minecraft", "a"), file(1));
        output.add(Location::new("minecraft", "b"), file(2));
        output.add(Location::new("minecraft", "a"), file(3));
        assert_eq!(2, output.by_id.len());
        assert!(matches!(&output.entries[0], Some((id, Supplier::File { found, .. })) if &*id.path == "a" && found.resource.entry == crate::assets::packs::pack::EntryRef::decode(3)),
            "a replaced id keeps its place");
        let ids: Vec<_> = output.into_values().into_iter().map(|(id, _)| id.path.to_string()).collect();
        // minecraft:a and minecraft:b fall in buckets 5 and 4 of 16.
        assert_eq!(vec!["b", "a"], ids);
    }
}
