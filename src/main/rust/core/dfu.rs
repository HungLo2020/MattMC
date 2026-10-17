//! DataFixerUpper `JsonOps` decoding rules over Gson-read JSON
//! ([`crate::core::json`]), shared by Rust ports of Java codecs.
//!
//! `Codec.INT` reads only JSON numbers (`LazilyParsedNumber.intValue`),
//! `Codec.BOOL` only booleans and `Codec.STRING` only strings; a field
//! holding JSON `null` counts as absent. Messages follow DFU's where a
//! caller logs them.

use super::json::{self, Json, Object};
use crate::content::tags::Location;

/// `Codec.INT`.
pub(crate) fn int(value: &Json) -> Result<i32, String> {
    match value {
        Json::Number(text) => Ok(json::int_value(text)),
        other => Err(format!("Not a number: {other}")),
    }
}

/// `ExtraCodecs.NON_NEGATIVE_INT`.
pub(crate) fn non_negative(value: &Json) -> Result<i32, String> {
    let integer = int(value)?;
    if integer >= 0 {
        Ok(integer)
    } else {
        Err(format!("Value must be non-negative: {integer}"))
    }
}

/// `ExtraCodecs.POSITIVE_INT`.
pub(crate) fn positive(value: &Json) -> Result<i32, String> {
    let integer = int(value)?;
    if integer > 0 {
        Ok(integer)
    } else {
        Err(format!("Value must be positive: {integer}"))
    }
}

/// `Codec.DOUBLE`.
pub(crate) fn double(value: &Json) -> Result<f64, String> {
    match value {
        Json::Number(text) => Ok(json::parse_f64(text)),
        other => Err(format!("Not a number: {other}")),
    }
}

/// `Codec.BOOL`.
pub(crate) fn boolean(value: &Json) -> Result<bool, String> {
    match value {
        Json::Bool(value) => Ok(*value),
        other => Err(format!("Not a boolean: {other}")),
    }
}

/// `Codec.STRING`.
pub(crate) fn string(value: &Json) -> Result<&str, String> {
    match value {
        Json::String(text) => Ok(text),
        other => Err(format!("Not a string: {other}")),
    }
}

/// A map codec's input.
pub(crate) fn object(value: &Json) -> Result<&Object, String> {
    value.as_object().ok_or_else(|| format!("Not a JSON object: {value}"))
}

/// A list codec's input.
pub(crate) fn array(value: &Json) -> Result<&[Json], String> {
    value.as_array().ok_or_else(|| format!("Not a json array: {value}"))
}

/// `ResourceLocation.CODEC`.
pub(crate) fn location(value: &Json) -> Result<Location, String> {
    Location::parse(string(value)?)
}

/// A field as `MapLike.get` sees it (`null` is absent).
pub(crate) fn field<'a>(object: &'a Object, key: &str) -> Option<&'a Json> {
    object.get(key).filter(|value| !value.is_null())
}

/// `fieldOf(key)`.
pub(crate) fn required<'a, T>(object: &'a Object, key: &str, decode: impl Fn(&'a Json) -> Result<T, String>) -> Result<T, String> {
    match field(object, key) {
        Some(value) => decode(value),
        None => Err(format!("No key {key} in MapLike")),
    }
}

/// `optionalFieldOf(key)`: absent is `None`; a present value must decode.
pub(crate) fn optional<'a, T>(object: &'a Object, key: &str, decode: impl Fn(&'a Json) -> Result<T, String>) -> Result<Option<T>, String> {
    field(object, key).map(decode).transpose()
}

/// `listOf()` of `decode`.
pub(crate) fn list<'a, T>(value: &'a Json, decode: impl Fn(&'a Json) -> Result<T, String>) -> Result<Vec<T>, String> {
    array(value)?.iter().map(decode).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(text: &str) -> Json {
        json::parse_document(text.as_bytes()).unwrap()
    }

    #[test]
    fn primitives_follow_json_ops() {
        assert_eq!(Ok(1_410_065_408), int(&parse("1e10")));
        assert_eq!(Ok(3), int(&parse("3.9")));
        assert!(int(&parse("true")).is_err());
        assert!(boolean(&parse("1")).is_err());
        assert!(string(&parse("1")).is_err());
        assert!(positive(&parse("0")).is_err());
        assert_eq!(Ok(0), non_negative(&parse("0")));
        let object = parse(r#"{"a": null, "b": 2}"#);
        let object = object.as_object().unwrap();
        assert_eq!(Ok(None), optional(object, "a", int));
        assert_eq!(Ok(Some(2)), optional(object, "b", int));
        assert!(required(object, "a", int).is_err());
    }
}
