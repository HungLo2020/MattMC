//! `pack.mcmeta` format declarations: `PackFormat` decoding and validation,
//! overlay entries, the overlays that apply to a game version, and
//! `PackCompatibility`. Java keeps the section codecs as adapters over these
//! functions (the description is a Java text component).
//!
//! Values follow DataFixerUpper's `JsonOps`: only JSON numbers read as
//! integers (by truncation), and the validation messages are Java's.

use std::cmp::Ordering;

use serde_json::{Map, Value};

/// `PackFormat(major, minor)`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub(crate) struct Format {
    pub(crate) major: i32,
    pub(crate) minor: i32,
}

impl Format {
    pub(crate) fn of(major: i32) -> Format {
        Format { major, minor: 0 }
    }
}

impl PartialOrd for Format {
    fn partial_cmp(&self, other: &Format) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for Format {
    fn cmp(&self, other: &Format) -> Ordering {
        self.major.cmp(&other.major).then(self.minor.cmp(&other.minor))
    }
}

impl std::fmt::Display for Format {
    /// `PackFormat.toString`.
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        if self.minor == i32::MAX {
            write!(formatter, "{}.*", self.major)
        } else {
            write!(formatter, "{}.{}", self.major, self.minor)
        }
    }
}

/// `InclusiveRange<PackFormat>`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Range {
    pub(crate) min: Format,
    pub(crate) max: Format,
}

/// `PackCompatibility` ordinals.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Compatibility {
    TooOld = 0,
    TooNew = 1,
    Unknown = 2,
    Compatible = 3,
}

/// `PackCompatibility.forVersion`.
pub(crate) fn compatibility(range: Range, current: Format) -> Compatibility {
    if range.min.major == i32::MAX {
        Compatibility::Unknown
    } else if range.max < current {
        Compatibility::TooOld
    } else if current < range.min {
        Compatibility::TooNew
    } else {
        Compatibility::Compatible
    }
}

/// `PackFormat.lastPreMinorVersion`.
pub(crate) fn last_pre_minor_version(pack_type: usize) -> i32 {
    if pack_type == 0 {
        64
    } else {
        81
    }
}

fn json(value: &Value) -> String {
    value.to_string()
}

/// `Codec.INT` over `JsonOps`: `Number.intValue()` of Gson's lazily parsed
/// number (other JSON types are errors).
fn int(value: &Value) -> Result<i32, String> {
    match value {
        Value::Number(number) => Ok(if let Some(integer) = number.as_i64() {
            integer as i32
        } else if let Some(unsigned) = number.as_u64() {
            unsigned as i64 as i32
        } else {
            // BigDecimal.intValue(): truncate, then keep the low 32 bits.
            let float = number.as_f64().unwrap_or(0.0);
            (float.trunc() as i128) as i32
        }),
        other => Err(format!("Not a number: {}", json(other))),
    }
}

/// `ExtraCodecs.NON_NEGATIVE_INT`.
fn non_negative(value: &Value) -> Result<i32, String> {
    let integer = int(value)?;
    if integer >= 0 {
        Ok(integer)
    } else {
        Err(format!("Value must be non-negative: {integer}"))
    }
}

/// `PackFormat.fullCodec(defaultMinor)`: one non-negative integer, or a list
/// of 1 to 256 of them (major, then minor).
fn format(value: &Value, default_minor: i32) -> Result<Format, String> {
    let list: Result<Vec<i32>, String> = match value {
        Value::Array(items) => {
            let parsed: Result<Vec<i32>, String> = items.iter().map(non_negative).collect();
            parsed.and_then(|parsed| match parsed.len() {
                0 => Err("List is too short: 0, expected range [1-256]".to_string()),
                length if length > 256 => Err(format!("List is too long: {length}, expected range [1-256]")),
                _ => Ok(parsed),
            })
        }
        other => Err(format!("Not a json array: {}", json(other))),
    };
    let list = match list {
        Ok(list) => list,
        Err(first) => match non_negative(value) {
            Ok(single) => vec![single],
            Err(second) => return Err(format!("Failed to parse either. First: {first}; Second: {second}")),
        },
    };
    Ok(Format { major: list[0], minor: if list.len() > 1 { list[1] } else { default_minor } })
}

/// `InclusiveRange.codec(Codec.INT)`: a point, a `[min, max]` list or a
/// `{min_inclusive, max_inclusive}` object.
fn int_range(value: &Value) -> Result<(i32, i32), String> {
    let (min, max) = match value {
        Value::Array(items) => {
            if items.len() != 2 {
                return Err(format!("Input is not a list of 2 elements: {}", json(value)));
            }
            (int(&items[0])?, int(&items[1])?)
        }
        Value::Object(object) => {
            let min = object.get("min_inclusive").ok_or_else(|| "No key min_inclusive in MapLike".to_string())?;
            let max = object.get("max_inclusive").ok_or_else(|| "No key max_inclusive in MapLike".to_string())?;
            (int(min)?, int(max)?)
        }
        point => {
            let point = int(point)?;
            (point, point)
        }
    };
    if min <= max {
        Ok((min, max))
    } else {
        Err("min_inclusive must be less than or equal to max_inclusive".into())
    }
}

/// `PackFormat.IntermediaryFormat`.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
struct Intermediary {
    min: Option<Format>,
    max: Option<Format>,
    format: Option<i32>,
    supported: Option<(i32, i32)>,
}

fn optional<T>(object: &Map<String, Value>, key: &str, decode: impl Fn(&Value) -> Result<T, String>) -> Result<Option<T>, String> {
    object.get(key).map(decode).transpose()
}

impl Intermediary {
    /// `IntermediaryFormat.PACK_CODEC`.
    fn pack(object: &Map<String, Value>) -> Result<Intermediary, String> {
        Ok(Intermediary {
            min: optional(object, "min_format", |value| format(value, 0))?,
            max: optional(object, "max_format", |value| format(value, i32::MAX))?,
            format: optional(object, "pack_format", int)?,
            supported: optional(object, "supported_formats", int_range)?,
        })
    }

    /// `IntermediaryFormat.OVERLAY_CODEC`: `format` is the minimum's major.
    fn overlay(object: &Map<String, Value>) -> Result<Intermediary, String> {
        let min = optional(object, "min_format", |value| format(value, 0))?;
        Ok(Intermediary {
            min,
            max: optional(object, "max_format", |value| format(value, i32::MAX))?,
            format: min.map(|min| min.major),
            supported: optional(object, "formats", int_range)?,
        })
    }

    fn effective_min_major(&self) -> i32 {
        match (self.min, self.supported) {
            (Some(min), Some(supported)) => min.major.min(supported.0),
            (Some(min), None) => min.major,
            (None, Some(supported)) => supported.0,
            (None, None) => i32::MAX,
        }
    }

    /// `IntermediaryFormat.validate(i, pack, requireOld, name, key)`.
    fn validate(&self, last: i32, pack: bool, require_old: bool, name: &str, key: &str) -> Result<Range, String> {
        if self.min.is_some() != self.max.is_some() {
            return Err(format!("{name} missing field, must declare both min_format and max_format"));
        }
        if require_old && self.supported.is_none() {
            return Err(format!(
                "{name} missing required field {key}, must be present in all overlays for any overlays to work across game versions"
            ));
        }
        if self.min.is_some() {
            return self.validate_new(last, pack, require_old, name, key);
        }
        if self.supported.is_some() {
            return self.validate_old(last, pack, name);
        }
        if pack {
            if let Some(format) = self.format {
                return if format > last {
                    Err(format!(
                        "{name} declares support for version newer than {last}, but is missing mandatory fields min_format and max_format"
                    ))
                } else {
                    Ok(Range { min: Format::of(format), max: Format::of(format) })
                };
            }
        }
        Err(format!("{name} could not be parsed, missing format version information"))
    }

    fn validate_new(&self, last: i32, pack: bool, require_old: bool, name: &str, key: &str) -> Result<Range, String> {
        let (min, max) = (self.min.unwrap(), self.max.unwrap());
        let (j, k) = (min.major, max.major);
        if min > max {
            return Err(format!("{name} min_format ({min}) is greater than max_format ({max})"));
        }
        if j > last && !require_old {
            if self.supported.is_some() {
                return Err(format!(
                    "{name} key {key} is deprecated starting from pack format {}. Remove {key} from your pack.mcmeta.",
                    last + 1
                ));
            }
            if pack && self.format.is_some() {
                if let Some(error) = self.validate_pack_format(j, k) {
                    return Err(error);
                }
            }
        } else {
            let Some(supported) = self.supported else {
                return Err(format!(
                    "{name} declares support for format {j}, but game versions supporting formats 17 to {last} require a {key} field. \
                     Add \"{key}\": [{j}, {last}] or require a version greater or equal to {}.0.",
                    last + 1
                ));
            };
            if supported.0 != j {
                return Err(format!(
                    "{name} version declaration mismatch between {key} (from {}) and min_format ({min})",
                    supported.0
                ));
            }
            if supported.1 != k && supported.1 != last {
                return Err(format!(
                    "{name} version declaration mismatch between {key} (up to {}) and max_format ({max})",
                    supported.1
                ));
            }
            if pack {
                if self.format.is_none() {
                    return Err(format!(
                        "{name} declares support for formats up to {last}, but game versions supporting formats 17 to {last} require a \
                         pack_format field. Add \"pack_format\": {j} or require a version greater or equal to {}.0.",
                        last + 1
                    ));
                }
                if let Some(error) = self.validate_pack_format(j, k) {
                    return Err(error);
                }
            }
        }
        Ok(Range { min, max })
    }

    fn validate_old(&self, last: i32, pack: bool, name: &str) -> Result<Range, String> {
        let (j, k) = self.supported.unwrap();
        if k > last {
            return Err(format!(
                "{name} declares support for version newer than {last}, but is missing mandatory fields min_format and max_format"
            ));
        }
        if pack {
            if self.format.is_none() {
                return Err(format!(
                    "{name} declares support for formats up to {last}, but game versions supporting formats 17 to {last} require a \
                     pack_format field. Add \"pack_format\": {j} or require a version greater or equal to {}.0.",
                    last + 1
                ));
            }
            if let Some(error) = self.validate_pack_format(j, k) {
                return Err(error);
            }
        }
        Ok(Range { min: Format::of(j), max: Format::of(k) })
    }

    fn validate_pack_format(&self, i: i32, j: i32) -> Option<String> {
        let k = self.format?;
        if k < i || k > j {
            Some(format!("Pack declared support for versions {i} to {j} but declared main format is {k}"))
        } else if k < 15 {
            Some("Multi-version packs cannot support minimum version of less than 15, since this will leave versions in range unable to load pack.".into())
        } else {
            None
        }
    }

    /// `IntermediaryFormat.fromRange(range, last)`.
    fn from_range(range: Range, last: i32) -> Intermediary {
        let in_range = range.min.major <= last && last <= range.max.major;
        Intermediary {
            min: Some(range.min),
            max: Some(range.max),
            format: in_range.then_some(range.min.major),
            supported: in_range.then_some((range.min.major, range.max.major)),
        }
    }
}

fn object(value: &Value) -> Result<&Map<String, Value>, String> {
    value.as_object().ok_or_else(|| format!("Not a JSON object: {}", json(value)))
}

/// `PackFormat.packCodec(type)` decoding of a `pack` section's format fields.
pub(crate) fn decode_pack_formats(section: &Value, pack_type: usize) -> Result<Range, String> {
    let intermediary = Intermediary::pack(object(section)?)?;
    intermediary.validate(last_pre_minor_version(pack_type), true, false, "Pack", "supported_formats")
}

/// `PackFormat.packCodec(type)` encoding: the format fields of a range.
pub(crate) fn encode_pack_formats(range: Range, pack_type: usize) -> Map<String, Value> {
    let intermediary = Intermediary::from_range(range, last_pre_minor_version(pack_type));
    encode_intermediary(&intermediary, "pack_format", "supported_formats", true)
}

fn encode_format(format: Format, default_minor: i32) -> Value {
    if format.minor != default_minor {
        Value::from(vec![format.major, format.minor])
    } else {
        Value::from(format.major)
    }
}

fn encode_intermediary(intermediary: &Intermediary, format_key: &str, supported_key: &str, with_format: bool) -> Map<String, Value> {
    let mut map = Map::new();
    if let Some(min) = intermediary.min {
        map.insert("min_format".into(), encode_format(min, 0));
    }
    if let Some(max) = intermediary.max {
        map.insert("max_format".into(), encode_format(max, i32::MAX));
    }
    if with_format {
        if let Some(format) = intermediary.format {
            map.insert(format_key.into(), Value::from(format));
        }
    }
    if let Some((min, max)) = intermediary.supported {
        map.insert(
            supported_key.into(),
            if min == max { Value::from(min) } else { Value::from(vec![min, max]) },
        );
    }
    map
}

/// One overlay entry (`OverlayMetadataSection.OverlayEntry`).
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Overlay {
    pub(crate) range: Range,
    pub(crate) directory: String,
}

/// Overlay entries skipped with a warning while decoding.
pub(crate) struct OverlayDecode {
    pub(crate) overlays: Vec<Overlay>,
    pub(crate) warnings: Vec<String>,
}

/// `[-_a-zA-Z0-9.]+`.
fn valid_overlay_directory(directory: &str) -> bool {
    !directory.is_empty() && directory.bytes().all(|byte| matches!(byte, b'-' | b'_' | b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'.'))
}

/// `OverlayMetadataSection.codecForPackType(type)` decoding.
pub(crate) fn decode_overlays(section: &Value, pack_type: usize) -> Result<OverlayDecode, String> {
    let entries = object(section)?.get("entries").ok_or_else(|| "No key entries in MapLike".to_string())?;
    let Value::Array(entries) = entries else {
        return Err(format!("Not a json array: {}", json(entries)));
    };
    let mut decoded = Vec::with_capacity(entries.len());
    for entry in entries {
        let fields = object(entry)?;
        let intermediary = Intermediary::overlay(fields)?;
        let directory = match fields.get("directory") {
            Some(Value::String(directory)) => directory.clone(),
            Some(other) => return Err(format!("Not a string: {}", json(other))),
            None => return Err("No key directory in MapLike".into()),
        };
        if !valid_overlay_directory(&directory) {
            return Err(format!("{directory} is not accepted directory name"));
        }
        decoded.push((intermediary, directory));
    }
    let last = last_pre_minor_version(pack_type);
    let minimum = decoded.iter().map(|(format, _)| format.effective_min_major()).min().unwrap_or(i32::MAX);
    let mut overlays = Vec::with_capacity(decoded.len());
    let mut warnings = Vec::new();
    for (intermediary, directory) in decoded {
        if intermediary.min.is_none() && intermediary.max.is_none() && intermediary.supported.is_none() {
            warnings.push(format!("Unknown or broken overlay entry {directory}"));
            continue;
        }
        let range = intermediary.validate(last, false, minimum <= last, &format!("Overlay \"{directory}\""), "formats")?;
        overlays.push(Overlay { range, directory });
    }
    Ok(OverlayDecode { overlays, warnings })
}

/// `OverlayMetadataSection.codecForPackType(type)` encoding.
pub(crate) fn encode_overlays(overlays: &[Overlay], pack_type: usize) -> Value {
    let last = last_pre_minor_version(pack_type);
    let entries: Vec<Value> = overlays
        .iter()
        .map(|overlay| {
            let mut map = encode_intermediary(&Intermediary::from_range(overlay.range, last), "", "formats", false);
            map.insert("directory".into(), Value::from(overlay.directory.clone()));
            Value::Object(map)
        })
        .collect();
    let mut section = Map::new();
    section.insert("entries".into(), Value::from(entries));
    Value::Object(section)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    const CLIENT: usize = 0;
    const SERVER: usize = 1;

    fn range(min: (i32, i32), max: (i32, i32)) -> Range {
        Range { min: Format { major: min.0, minor: min.1 }, max: Format { major: max.0, minor: max.1 } }
    }

    #[test]
    fn pack_formats_follow_the_old_and_new_rules() {
        assert_eq!(Ok(range((15, 0), (15, 0))), decode_pack_formats(&json!({"pack_format": 15}), CLIENT));
        assert_eq!(Ok(range((15, 0), (40, 0))), decode_pack_formats(&json!({"pack_format": 15, "supported_formats": [15, 40]}), CLIENT));
        assert_eq!(Ok(range((69, 0), (74, i32::MAX))), decode_pack_formats(&json!({"min_format": [69, 0], "max_format": 74}), CLIENT));
        assert_eq!(
            Err("Pack declares support for version newer than 64, but is missing mandatory fields min_format and max_format".into()),
            decode_pack_formats(&json!({"pack_format": 70}), CLIENT)
        );
        assert_eq!(
            Err("Pack missing field, must declare both min_format and max_format".into()),
            decode_pack_formats(&json!({"min_format": 70}), CLIENT)
        );
        assert_eq!(
            Err("Pack could not be parsed, missing format version information".into()),
            decode_pack_formats(&json!({}), SERVER)
        );
        assert!(decode_pack_formats(&json!({"min_format": 60, "max_format": 70}), CLIENT).unwrap_err().contains("require a supported_formats"));
        assert_eq!(Ok(range((60, 0), (70, i32::MAX))), decode_pack_formats(
            &json!({"min_format": 60, "max_format": 70, "supported_formats": [60, 64], "pack_format": 60}), CLIENT));
        assert!(decode_pack_formats(&json!({"pack_format": 10, "supported_formats": [5, 20]}), CLIENT).unwrap_err().contains("less than 15"));
        assert_eq!(Ok(range((20, 0), (20, 0))), decode_pack_formats(&json!({"pack_format": 20.9}), CLIENT));
        assert_eq!(Err("Value must be non-negative: -1".into()), decode_pack_formats(&json!({"min_format": -1, "max_format": 70}), CLIENT)
            .map_err(|error| error.split("Second: ").nth(1).unwrap_or(&error).to_string()));
    }

    #[test]
    fn overlays_decode_and_reject_bad_directories() {
        let decoded = decode_overlays(&json!({"entries": [
            {"min_format": 69, "max_format": 70, "directory": "now"},
            {"min_format": 80, "max_format": 82, "directory": "future"},
            {"directory": "broken"}
        ]}), CLIENT).unwrap();
        assert_eq!(vec!["Unknown or broken overlay entry broken".to_string()], decoded.warnings);
        assert_eq!(vec!["now", "future"], decoded.overlays.iter().map(|overlay| overlay.directory.as_str()).collect::<Vec<_>>());
        assert!(decode_overlays(&json!({"entries": [{"formats": [1, 5], "directory": "bad dir"}]}), CLIENT).is_err());
        let round_trip = decode_overlays(&encode_overlays(&decoded.overlays, CLIENT), CLIENT).unwrap();
        assert_eq!(decoded.overlays, round_trip.overlays);
    }

    #[test]
    fn compatibility_and_encoding_match_java() {
        let current = Format { major: 69, minor: 0 };
        assert_eq!(Compatibility::Compatible, compatibility(range((69, 0), (69, i32::MAX)), current));
        assert_eq!(Compatibility::TooOld, compatibility(range((10, 0), (20, 0)), current));
        assert_eq!(Compatibility::TooNew, compatibility(range((70, 0), (80, 0)), current));
        assert_eq!(Compatibility::Unknown, compatibility(range((i32::MAX, 0), (i32::MAX, 0)), current));
        // The legacy fields appear only when the range spans the last pre-minor version (64).
        let encoded = encode_pack_formats(range((15, 0), (70, 0)), CLIENT);
        assert_eq!(json!({"min_format": 15, "max_format": [70, 0], "pack_format": 15, "supported_formats": [15, 70]}), Value::Object(encoded.clone()));
        assert_eq!(Ok(range((15, 0), (70, 0))), decode_pack_formats(&Value::Object(encoded), CLIENT));
        assert_eq!(json!({"min_format": 88, "max_format": 88}), Value::Object(encode_pack_formats(range((88, 0), (88, i32::MAX)), SERVER)));
    }
}
