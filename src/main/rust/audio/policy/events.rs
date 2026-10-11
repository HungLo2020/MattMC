//! `sounds.json` event registry and weighted resolution.
//!
//! Mirrors `SoundEventRegistrationSerializer`, `SoundManager.Preparations`,
//! `WeighedSoundEvents` and nested event entries. Java reads pack files (the
//! resource layer owns pack IO) and passes each `sounds.json` text in resource
//! stack order; Rust parses, applies replace/append, validates file entries
//! against the listed `.ogg` resources and resolves events.
//!
//! Resolution draws come from the caller's random source, so Java instances keep
//! their exact random sequence: [`SoundRegistry::resolve`] consumes the draws it
//! is given and reports the next bound it needs.

use std::collections::{HashMap, HashSet};
use std::sync::Arc;

use serde_json::{Map, Value};

/// Default attenuation distance (`Sound`'s `attenuation_distance` default).
const DEFAULT_ATTENUATION: i32 = 16;
/// Nested event references deeper than this resolve as empty (Java would overflow).
const MAX_NESTING: usize = 32;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum EntryKind {
    File,
    Event,
}

/// One `sounds` array element after deserialization.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct SoundSpec {
    /// `namespace:path` (sound file or referenced event).
    pub(crate) location: Arc<str>,
    pub(crate) volume: f32,
    pub(crate) pitch: f32,
    pub(crate) weight: i32,
    pub(crate) kind: EntryKind,
    pub(crate) stream: bool,
    pub(crate) preload: bool,
    pub(crate) attenuation_distance: i32,
}

impl SoundSpec {
    /// The resource path of a file entry: `ns:sounds/<path>.ogg`.
    pub(crate) fn file_path(location: &str) -> String {
        let (namespace, path) = location.split_once(':').unwrap_or(("minecraft", location));
        format!("{namespace}:sounds/{path}.ogg")
    }
}

#[derive(Clone, Debug, Default)]
pub(crate) struct EventDef {
    pub(crate) subtitle: Option<Arc<str>>,
    /// Indices into [`SoundRegistry::specs`].
    pub(crate) entries: Vec<u32>,
}

/// A resolved playable sound: Java's `Sound` value, possibly a nested combination.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct ResolvedSound {
    /// The innermost file entry (its spec index), or `None` for the
    /// `minecraft:empty` placeholder a missing nested event produces.
    pub(crate) file_spec: Option<u32>,
    pub(crate) location: Arc<str>,
    pub(crate) volume: f32,
    pub(crate) pitch: f32,
    pub(crate) weight: i32,
    pub(crate) stream: bool,
    pub(crate) preload: bool,
    pub(crate) attenuation_distance: i32,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Resolution {
    /// No registered event of that name (`getSoundEvent` returns null).
    UnknownEvent,
    /// The next draw must be `random.nextInt(bound)`.
    NeedDraw(i32),
    /// `SoundManager.EMPTY_SOUND`.
    Empty,
    Sound(ResolvedSound),
}

/// The immutable registry of one resource reload.
#[derive(Debug, Default)]
pub(crate) struct SoundRegistry {
    pub(crate) generation: u64,
    pub(crate) specs: Vec<SoundSpec>,
    pub(crate) events: Vec<EventDef>,
    pub(crate) names: Vec<Arc<str>>,
    index: HashMap<Arc<str>, u32>,
    pub(crate) warnings: Vec<String>,
}

impl SoundRegistry {
    pub(crate) fn event_index(&self, name: &str) -> Option<u32> {
        self.index.get(name).copied()
    }

    /// `WeighedSoundEvents.getWeight()`.
    pub(crate) fn event_weight(&self, event: u32) -> i32 {
        self.event_weight_at(event, 0)
    }

    fn event_weight_at(&self, event: u32, depth: usize) -> i32 {
        if depth > MAX_NESTING {
            return 0;
        }
        self.events[event as usize]
            .entries
            .iter()
            .fold(0i32, |total, &spec| total.wrapping_add(self.entry_weight(spec, depth)))
    }

    fn entry_weight(&self, spec: u32, depth: usize) -> i32 {
        let spec = &self.specs[spec as usize];
        match spec.kind {
            EntryKind::File => spec.weight,
            EntryKind::Event => self
                .event_index(&spec.location)
                .map_or(0, |target| self.event_weight_at(target, depth + 1)),
        }
    }

    /// Resolves `name` with the caller's draws, in order. Returns
    /// [`Resolution::NeedDraw`] when `draws` is exhausted before a choice.
    pub(crate) fn resolve(&self, name: &str, draws: &[i32]) -> Resolution {
        let Some(event) = self.event_index(name) else {
            return Resolution::UnknownEvent;
        };
        let mut draws = draws.iter().copied();
        match self.event_sound(event, &mut |_| draws.next(), 0) {
            Ok(Some(sound)) => Resolution::Sound(sound),
            Ok(None) => Resolution::Empty,
            Err(bound) => Resolution::NeedDraw(bound),
        }
    }

    /// Resolves `event` drawing `next(bound)` from a Rust-owned random source
    /// (`None` is `EMPTY_SOUND`).
    pub(crate) fn resolve_with(&self, event: u32, next: &mut impl FnMut(i32) -> i32) -> Option<ResolvedSound> {
        self.event_sound(event, &mut |bound| Some(next(bound)), 0).unwrap_or(None)
    }

    /// `WeighedSoundEvents.getSound(random)`: `None` is `EMPTY_SOUND`. A
    /// draw callback returning `None` aborts with the bound it needed.
    fn event_sound(
        &self,
        event: u32,
        draw: &mut impl FnMut(i32) -> Option<i32>,
        depth: usize,
    ) -> Result<Option<ResolvedSound>, i32> {
        let entries = &self.events[event as usize].entries;
        let total = self.event_weight_at(event, depth);
        if entries.is_empty() || total == 0 {
            return Ok(None);
        }
        if total < 0 {
            // An overflowed weight sum: a step caller's `nextInt` throws as
            // Java's does; `resolve_with` plays nothing.
            return Err(total);
        }
        let mut choice = draw(total).ok_or(total)?;
        for &spec in entries {
            choice -= self.entry_weight(spec, depth);
            if choice < 0 {
                return self.entry_sound(spec, draw, depth).map(Some);
            }
        }
        Ok(None)
    }

    fn entry_sound(
        &self,
        spec_index: u32,
        draw: &mut impl FnMut(i32) -> Option<i32>,
        depth: usize,
    ) -> Result<ResolvedSound, i32> {
        let spec = &self.specs[spec_index as usize];
        if spec.kind == EntryKind::File {
            return Ok(ResolvedSound {
                file_spec: Some(spec_index),
                location: spec.location.clone(),
                volume: spec.volume,
                pitch: spec.pitch,
                weight: spec.weight,
                stream: spec.stream,
                preload: spec.preload,
                attenuation_distance: spec.attenuation_distance,
            });
        }
        // A nested event: Java wraps the inner sound (EMPTY_SOUND when the
        // target is missing or draws nothing) into a new FILE sound that
        // multiplies volume/pitch, keeps the outer weight and ORs `stream`.
        let inner = match self.event_index(&spec.location) {
            Some(target) if depth < MAX_NESTING => self.event_sound(target, draw, depth + 1)?,
            _ => None,
        };
        let inner = inner.unwrap_or_else(empty_sound);
        Ok(ResolvedSound {
            file_spec: inner.file_spec,
            location: inner.location,
            volume: 1.0 * inner.volume * spec.volume,
            pitch: 1.0 * inner.pitch * spec.pitch,
            weight: spec.weight,
            stream: inner.stream || spec.stream,
            preload: inner.preload,
            attenuation_distance: inner.attenuation_distance,
        })
    }

    /// Every file entry flagged `preload` (`Weighted.preloadIfRequired` over all events).
    pub(crate) fn preload_paths(&self) -> Vec<String> {
        let mut seen = HashSet::new();
        let mut paths = Vec::new();
        for event in &self.events {
            for &spec in &event.entries {
                let spec = &self.specs[spec as usize];
                if spec.kind == EntryKind::File && spec.preload {
                    let path = SoundSpec::file_path(&spec.location);
                    if seen.insert(path.clone()) {
                        paths.push(path);
                    }
                }
            }
        }
        paths
    }
}

fn empty_sound() -> ResolvedSound {
    ResolvedSound {
        file_spec: None,
        location: Arc::from("minecraft:empty"),
        volume: 1.0,
        pitch: 1.0,
        weight: 1,
        stream: false,
        preload: false,
        attenuation_distance: DEFAULT_ATTENUATION,
    }
}

/// Builds one reload's registry from `sounds.json` resources in Java's order:
/// namespaces, then each namespace's resource stack (lowest pack first).
pub(crate) struct RegistryBuilder {
    registry: SoundRegistry,
    available_files: HashSet<String>,
}

impl RegistryBuilder {
    /// `available_files` are the listed `ns:sounds/....ogg` resources.
    pub(crate) fn new(generation: u64, available_files: HashSet<String>) -> Self {
        Self {
            registry: SoundRegistry { generation, ..SoundRegistry::default() },
            available_files,
        }
    }

    /// Adds one `sounds.json`; a malformed resource is skipped with a warning,
    /// as `SoundManager.prepare` does.
    pub(crate) fn add_resource(&mut self, namespace: &str, pack: &str, json: &str) {
        let parsed = serde_json::from_str::<Value>(json)
            .map_err(|error| error.to_string())
            .and_then(|value| parse_registrations(&value));
        match parsed {
            Ok(registrations) => {
                for (key, registration) in registrations {
                    if !valid_path(&key) {
                        // `ResourceLocation.fromNamespaceAndPath` throws mid-loop:
                        // earlier entries of this resource stay registered.
                        self.warn_invalid(pack, &format!("Non [a-z0-9/._-] character in path of location: {namespace}:{key}"));
                        break;
                    }
                    self.register(format!("{namespace}:{key}"), registration);
                }
            }
            Err(error) => self.warn_invalid(pack, &error),
        }
    }

    fn warn_invalid(&mut self, pack: &str, error: &str) {
        self.registry
            .warnings
            .push(format!("Invalid sounds.json in resourcepack: '{pack}': {error}"));
    }

    /// `Preparations.handleRegistration`.
    fn register(&mut self, name: String, registration: Registration) {
        let registry = &mut self.registry;
        let event = match registry.index.get(name.as_str()).copied() {
            Some(existing) if !registration.replace => existing,
            existing => {
                let def = EventDef {
                    subtitle: registration.subtitle.map(Arc::from),
                    entries: Vec::new(),
                };
                match existing {
                    Some(index) => {
                        registry.events[index as usize] = def;
                        index
                    }
                    None => {
                        let key: Arc<str> = Arc::from(name.as_str());
                        let index = registry.events.len() as u32;
                        registry.events.push(def);
                        registry.names.push(key.clone());
                        registry.index.insert(key, index);
                        index
                    }
                }
            }
        };
        for spec in registration.sounds {
            if spec.kind == EntryKind::File {
                let path = SoundSpec::file_path(&spec.location);
                if !self.available_files.contains(&path) {
                    registry
                        .warnings
                        .push(format!("File {path} does not exist, cannot add it to event {name}"));
                    continue;
                }
            }
            let index = registry.specs.len() as u32;
            registry.specs.push(spec);
            registry.events[event as usize].entries.push(index);
        }
    }

    pub(crate) fn build(self) -> SoundRegistry {
        self.registry
    }
}

struct Registration {
    sounds: Vec<SoundSpec>,
    replace: bool,
    subtitle: Option<String>,
}

fn parse_registrations(value: &Value) -> Result<Vec<(String, Registration)>, String> {
    let Value::Object(map) = value else {
        return Err("Expected sounds.json to be an object".into());
    };
    map.iter()
        .map(|(key, entry)| Ok((key.clone(), parse_registration(entry)?)))
        .collect()
}

/// `SoundEventRegistrationSerializer.deserialize`.
fn parse_registration(value: &Value) -> Result<Registration, String> {
    let Value::Object(object) = value else {
        return Err(format!("Expected entry to be a JsonObject, was {}", type_name(value)));
    };
    let replace = get_bool(object, "replace", false)?;
    let subtitle = get_string(object, "subtitle")?;
    let mut sounds = Vec::new();
    if let Some(array) = object.get("sounds") {
        let Value::Array(array) = array else {
            return Err(format!("Expected sounds to be a JsonArray, was {}", type_name(array)));
        };
        for element in array {
            sounds.push(match element {
                Value::String(name) => SoundSpec {
                    location: Arc::from(parse_location(name)?),
                    volume: 1.0,
                    pitch: 1.0,
                    weight: 1,
                    kind: EntryKind::File,
                    stream: false,
                    preload: false,
                    attenuation_distance: DEFAULT_ATTENUATION,
                },
                Value::Object(sound) => parse_sound(sound)?,
                other => return Err(format!("Expected sound to be a JsonObject, was {}", type_name(other))),
            });
        }
    }
    Ok(Registration { sounds, replace, subtitle })
}

fn parse_sound(object: &Map<String, Value>) -> Result<SoundSpec, String> {
    let Some(name) = get_string(object, "name")? else {
        return Err("Missing name, expected to find a string".into());
    };
    let location = parse_location(&name)?;
    let kind = match get_string(object, "type")? {
        None => EntryKind::File,
        Some(kind) if kind == "file" => EntryKind::File,
        Some(kind) if kind == "event" => EntryKind::Event,
        Some(_) => return Err("Invalid type".into()),
    };
    let volume = get_float(object, "volume", 1.0)?;
    if volume.is_nan() || volume <= 0.0 {
        return Err("Invalid volume".into());
    }
    let pitch = get_float(object, "pitch", 1.0)?;
    if pitch.is_nan() || pitch <= 0.0 {
        return Err("Invalid pitch".into());
    }
    let weight = get_int(object, "weight", 1)?;
    if weight <= 0 {
        return Err("Invalid weight".into());
    }
    let preload = get_bool(object, "preload", false)?;
    let stream = get_bool(object, "stream", false)?;
    let attenuation_distance = get_int(object, "attenuation_distance", DEFAULT_ATTENUATION)?;
    Ok(SoundSpec {
        location: Arc::from(location),
        volume,
        pitch,
        weight,
        kind,
        stream,
        preload,
        attenuation_distance,
    })
}

fn type_name(value: &Value) -> &'static str {
    match value {
        Value::Null => "null",
        Value::Bool(_) | Value::Number(_) | Value::String(_) => "a primitive",
        Value::Array(_) => "an array",
        Value::Object(_) => "an object",
    }
}

/// `GsonHelper.getAsBoolean`: a primitive member, read by `JsonPrimitive.getAsBoolean`.
fn get_bool(object: &Map<String, Value>, key: &str, fallback: bool) -> Result<bool, String> {
    match object.get(key) {
        None => Ok(fallback),
        Some(Value::Bool(value)) => Ok(*value),
        Some(Value::String(value)) => Ok(value.eq_ignore_ascii_case("true")),
        Some(Value::Number(_)) => Ok(false),
        Some(other) => Err(format!("Expected {key} to be a Boolean, was {}", type_name(other))),
    }
}

/// `GsonHelper.getAsString(object, key, null)`: any primitive's string form.
fn get_string(object: &Map<String, Value>, key: &str) -> Result<Option<String>, String> {
    match object.get(key) {
        None => Ok(None),
        Some(Value::String(value)) => Ok(Some(value.clone())),
        Some(Value::Bool(value)) => Ok(Some(value.to_string())),
        Some(Value::Number(value)) => Ok(Some(value.to_string())),
        Some(other) => Err(format!("Expected {key} to be a string, was {}", type_name(other))),
    }
}

/// `GsonHelper.getAsFloat`: numeric primitives only.
fn get_float(object: &Map<String, Value>, key: &str, fallback: f32) -> Result<f32, String> {
    match object.get(key) {
        None => Ok(fallback),
        Some(Value::Number(value)) => {
            // Java parses the number text as a float (`Float.parseFloat`).
            value.to_string().parse::<f32>().map_err(|error| error.to_string())
        }
        Some(other) => Err(format!("Expected {key} to be a Float, was {}", type_name(other))),
    }
}

/// `GsonHelper.getAsInt`: numeric primitives, truncated like `Number.intValue()`.
fn get_int(object: &Map<String, Value>, key: &str, fallback: i32) -> Result<i32, String> {
    match object.get(key) {
        None => Ok(fallback),
        Some(Value::Number(value)) => Ok(match value.as_i64() {
            Some(integer) => integer as i32,
            None => value.as_f64().map_or(0, |float| float as i64 as i32),
        }),
        Some(other) => Err(format!("Expected {key} to be a Int, was {}", type_name(other))),
    }
}

/// `ResourceLocation.parse`: `namespace:path`, defaulting to `minecraft`.
fn parse_location(text: &str) -> Result<String, String> {
    let (namespace, path) = match text.split_once(':') {
        Some((namespace, path)) => (if namespace.is_empty() { "minecraft" } else { namespace }, path),
        None => ("minecraft", text),
    };
    if !namespace.bytes().all(|byte| matches!(byte, b'a'..=b'z' | b'0'..=b'9' | b'_' | b'-' | b'.')) {
        return Err(format!("Non [a-z0-9_.-] character in namespace of location: {namespace}:{path}"));
    }
    if !valid_path(path) {
        return Err(format!("Non [a-z0-9/._-] character in path of location: {namespace}:{path}"));
    }
    Ok(format!("{namespace}:{path}"))
}

fn valid_path(path: &str) -> bool {
    path.bytes().all(|byte| matches!(byte, b'a'..=b'z' | b'0'..=b'9' | b'_' | b'-' | b'.' | b'/'))
}
