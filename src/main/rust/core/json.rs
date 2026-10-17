//! JSON as Gson 2.11 reads resource files in strict mode, for Rust ports
//! of Java loaders.
//!
//! Unlike `serde_json` this keeps what Java observes: object keys in file
//! order (a repeated key replaces the value in place, as Gson's
//! `JsonObject` does), the text of numbers (`Float.parseFloat`,
//! `Double.parseDouble` and `LazilyParsedNumber.intValue` read it), and
//! Gson's strict grammar: no comments, unquoted text or trailing commas,
//! no unescaped control characters in strings, lowercase literals and a
//! nesting limit of 255.

use std::fmt;

/// Gson's default nesting limit.
const NESTING_LIMIT: usize = 255;

/// A JSON value (`JsonElement`).
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Json {
    Null,
    Bool(bool),
    /// The number's text, as written.
    Number(Box<str>),
    String(String),
    Array(Vec<Json>),
    Object(Object),
}

/// A JSON object: entries in file order.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct Object {
    entries: Vec<(String, Json)>,
}

impl Object {
    pub(crate) fn get(&self, key: &str) -> Option<&Json> {
        self.entries.iter().find(|(name, _)| name == key).map(|(_, value)| value)
    }

    /// `JsonObject.has`: true for a member holding `null`.
    pub(crate) fn has(&self, key: &str) -> bool {
        self.get(key).is_some()
    }

    pub(crate) fn iter(&self) -> impl Iterator<Item = (&str, &Json)> {
        self.entries.iter().map(|(name, value)| (name.as_str(), value))
    }

    pub(crate) fn len(&self) -> usize {
        self.entries.len()
    }

    fn put(&mut self, key: String, value: Json) {
        match self.entries.iter_mut().find(|(name, _)| *name == key) {
            Some((_, slot)) => *slot = value,
            None => self.entries.push((key, value)),
        }
    }
}

impl fmt::Display for Json {
    /// Gson's compact `toString`.
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Json::Null => formatter.write_str("null"),
            Json::Bool(value) => write!(formatter, "{value}"),
            Json::Number(text) => formatter.write_str(text),
            Json::String(text) => write_string(formatter, text),
            Json::Array(values) => {
                formatter.write_str("[")?;
                for (index, value) in values.iter().enumerate() {
                    if index > 0 {
                        formatter.write_str(",")?;
                    }
                    write!(formatter, "{value}")?;
                }
                formatter.write_str("]")
            }
            Json::Object(object) => {
                formatter.write_str("{")?;
                for (index, (key, value)) in object.iter().enumerate() {
                    if index > 0 {
                        formatter.write_str(",")?;
                    }
                    write_string(formatter, key)?;
                    write!(formatter, ":{value}")?;
                }
                formatter.write_str("}")
            }
        }
    }
}

fn write_string(formatter: &mut fmt::Formatter<'_>, text: &str) -> fmt::Result {
    formatter.write_str("\"")?;
    for character in text.chars() {
        match character {
            '"' => formatter.write_str("\\\"")?,
            '\\' => formatter.write_str("\\\\")?,
            '\n' => formatter.write_str("\\n")?,
            '\r' => formatter.write_str("\\r")?,
            '\t' => formatter.write_str("\\t")?,
            '<' | '>' | '&' | '=' | '\'' | '\u{2028}' | '\u{2029}' => write!(formatter, "\\u{:04x}", character as u32)?,
            character if (character as u32) < 0x20 => write!(formatter, "\\u{:04x}", character as u32)?,
            character => write!(formatter, "{character}")?,
        }
    }
    formatter.write_str("\"")
}

impl Json {
    pub(crate) fn as_object(&self) -> Option<&Object> {
        match self {
            Json::Object(object) => Some(object),
            _ => None,
        }
    }

    pub(crate) fn as_array(&self) -> Option<&[Json]> {
        match self {
            Json::Array(values) => Some(values),
            _ => None,
        }
    }

    pub(crate) fn is_null(&self) -> bool {
        matches!(self, Json::Null)
    }

    /// `JsonElement.getAsString`: a primitive's text, or a one-element
    /// array's element; `None` where Java throws.
    pub(crate) fn as_text(&self) -> Option<String> {
        match self {
            Json::Bool(value) => Some(value.to_string()),
            Json::Number(text) => Some(text.to_string()),
            Json::String(text) => Some(text.clone()),
            Json::Array(values) if values.len() == 1 => values[0].as_text(),
            _ => None,
        }
    }

    /// `JsonPrimitive.getAsBoolean`: a boolean, or `Boolean.parseBoolean`
    /// of the text.
    pub(crate) fn as_java_boolean(&self) -> Option<bool> {
        match self {
            Json::Bool(value) => Some(*value),
            Json::Number(text) => Some(text.eq_ignore_ascii_case("true")),
            Json::String(text) => Some(text.eq_ignore_ascii_case("true")),
            Json::Array(values) if values.len() == 1 => values[0].as_java_boolean(),
            _ => None,
        }
    }
}

/// `Float.parseFloat` of a JSON number's text (correctly rounded).
pub(crate) fn parse_f32(text: &str) -> f32 {
    text.parse::<f32>().unwrap_or(f32::NAN)
}

/// `Double.parseDouble` of a JSON number's text.
pub(crate) fn parse_f64(text: &str) -> f64 {
    text.parse::<f64>().unwrap_or(f64::NAN)
}

/// `LazilyParsedNumber.intValue()`: `Integer.parseInt`, else
/// `(int) Long.parseLong`, else `new BigDecimal(text).intValue()` (the
/// integer part's low 32 bits).
pub(crate) fn int_value(text: &str) -> i32 {
    if let Ok(value) = text.parse::<i32>() {
        return value;
    }
    if !text.contains(['.', 'e', 'E']) {
        if let Ok(value) = text.parse::<i64>() {
            return value as i32;
        }
    }
    big_decimal_int(text)
}

/// `new BigDecimal(text).intValue()`: truncate toward zero, then keep the
/// low 32 bits of the two's-complement integer.
fn big_decimal_int(text: &str) -> i32 {
    let (negative, rest) = match text.strip_prefix('-') {
        Some(rest) => (true, rest),
        None => (false, text.strip_prefix('+').unwrap_or(text)),
    };
    let (mantissa, exponent) = match rest.find(['e', 'E']) {
        Some(at) => (&rest[..at], rest[at + 1..].parse::<i64>().unwrap_or(0)),
        None => (rest, 0),
    };
    let (whole, fraction) = mantissa.split_once('.').unwrap_or((mantissa, ""));
    let digits: Vec<u8> = whole.bytes().chain(fraction.bytes()).map(|digit| digit.wrapping_sub(b'0')).collect();
    // The integer part keeps the first `whole.len() + exponent` digits.
    let integer_digits = whole.len() as i64 + exponent;
    let mut value: u32 = 0;
    if integer_digits > 0 {
        for index in 0..integer_digits {
            let digit = digits.get(index as usize).copied().unwrap_or(0);
            value = value.wrapping_mul(10).wrapping_add(u32::from(digit));
            // Beyond the digits only zeros remain: once the low 32 bits are 0 they stay 0.
            if index as usize >= digits.len() && value == 0 {
                break;
            }
        }
    }
    let value = value as i32;
    if negative {
        value.wrapping_neg()
    } else {
        value
    }
}

/// A parse failure (Gson's `MalformedJsonException`/`EOFException` text).
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Error(pub(crate) String);

impl fmt::Display for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

struct Parser<'a> {
    text: &'a [u8],
    at: usize,
    depth: usize,
}

impl<'a> Parser<'a> {
    fn error<T>(&self, message: &str) -> Result<T, Error> {
        Err(Error(format!("{message} at offset {}", self.at)))
    }

    fn skip_whitespace(&mut self) {
        while let Some(&byte) = self.text.get(self.at) {
            if matches!(byte, b' ' | b'\t' | b'\n' | b'\r') {
                self.at += 1;
            } else {
                break;
            }
        }
    }

    fn peek(&self) -> Option<u8> {
        self.text.get(self.at).copied()
    }

    fn value(&mut self) -> Result<Json, Error> {
        self.skip_whitespace();
        match self.peek() {
            None => Err(Error("End of input".into())),
            Some(b'{') => self.nested(|parser| parser.object()),
            Some(b'[') => self.nested(|parser| parser.array()),
            Some(b'"') => self.string().map(Json::String),
            Some(b't') => self.keyword("true", Json::Bool(true)),
            Some(b'f') => self.keyword("false", Json::Bool(false)),
            Some(b'n') => self.keyword("null", Json::Null),
            Some(b'-' | b'0'..=b'9') => self.number(),
            Some(_) => self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON"),
        }
    }

    fn nested(&mut self, read: impl FnOnce(&mut Parser<'a>) -> Result<Json, Error>) -> Result<Json, Error> {
        self.depth += 1;
        if self.depth > NESTING_LIMIT {
            return self.error(&format!("Nesting limit {NESTING_LIMIT} reached"));
        }
        let result = read(self);
        self.depth -= 1;
        result
    }

    fn keyword(&mut self, word: &str, value: Json) -> Result<Json, Error> {
        if self.text[self.at..].starts_with(word.as_bytes()) {
            let after = self.text.get(self.at + word.len()).copied();
            if after.is_none_or(|byte| !is_literal(byte)) {
                self.at += word.len();
                return Ok(value);
            }
        }
        self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON")
    }

    fn number(&mut self) -> Result<Json, Error> {
        let start = self.at;
        let bytes = self.text;
        let mut at = self.at;
        if bytes.get(at) == Some(&b'-') {
            at += 1;
        }
        match bytes.get(at) {
            Some(b'0') => {
                at += 1;
                if bytes.get(at).is_some_and(u8::is_ascii_digit) {
                    return self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON");
                }
            }
            Some(b'1'..=b'9') => {
                while bytes.get(at).is_some_and(u8::is_ascii_digit) {
                    at += 1;
                }
            }
            _ => return self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON"),
        }
        if bytes.get(at) == Some(&b'.') {
            at += 1;
            if !bytes.get(at).is_some_and(u8::is_ascii_digit) {
                return self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON");
            }
            while bytes.get(at).is_some_and(u8::is_ascii_digit) {
                at += 1;
            }
        }
        if matches!(bytes.get(at), Some(b'e' | b'E')) {
            at += 1;
            if matches!(bytes.get(at), Some(b'+' | b'-')) {
                at += 1;
            }
            if !bytes.get(at).is_some_and(u8::is_ascii_digit) {
                return self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON");
            }
            while bytes.get(at).is_some_and(u8::is_ascii_digit) {
                at += 1;
            }
        }
        if bytes.get(at).is_some_and(|&byte| is_literal(byte)) {
            return self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON");
        }
        self.at = at;
        Ok(Json::Number(std::str::from_utf8(&bytes[start..at]).unwrap_or("0").into()))
    }

    fn string(&mut self) -> Result<String, Error> {
        self.at += 1;
        let mut units: Vec<u16> = Vec::new();
        let mut text = String::new();
        loop {
            let Some(byte) = self.peek() else { return Err(Error("Unterminated string".into())) };
            match byte {
                b'"' => {
                    self.at += 1;
                    flush(&mut units, &mut text);
                    return Ok(text);
                }
                b'\\' => {
                    self.at += 1;
                    let Some(escape) = self.peek() else { return Err(Error("Unterminated escape sequence".into())) };
                    self.at += 1;
                    let unit = match escape {
                        b'"' => u16::from(b'"'),
                        b'\\' => u16::from(b'\\'),
                        b'/' => u16::from(b'/'),
                        b'b' => 0x08,
                        b'f' => 0x0c,
                        b'n' => u16::from(b'\n'),
                        b'r' => u16::from(b'\r'),
                        b't' => u16::from(b'\t'),
                        b'u' => {
                            let hex = self.text.get(self.at..self.at + 4).and_then(|hex| std::str::from_utf8(hex).ok());
                            match hex.and_then(|hex| u16::from_str_radix(hex, 16).ok()) {
                                Some(unit) if hex.is_some_and(|hex| hex.bytes().all(|byte| byte.is_ascii_hexdigit())) => {
                                    self.at += 4;
                                    unit
                                }
                                _ => return self.error("Malformed Unicode escape"),
                            }
                        }
                        _ => return self.error("Invalid escape sequence"),
                    };
                    units.push(unit);
                }
                0x00..=0x1f => return self.error("Unescaped control characters (\\u0000-\\u001F) are not allowed in strict mode"),
                _ => {
                    flush(&mut units, &mut text);
                    let rest = &self.text[self.at..];
                    let length = utf8_length(byte).min(rest.len());
                    text.push_str(&String::from_utf8_lossy(&rest[..length]));
                    self.at += length;
                }
            }
        }
    }

    fn array(&mut self) -> Result<Json, Error> {
        self.at += 1;
        let mut values = Vec::new();
        self.skip_whitespace();
        if self.peek() == Some(b']') {
            self.at += 1;
            return Ok(Json::Array(values));
        }
        loop {
            values.push(self.value()?);
            self.skip_whitespace();
            match self.peek() {
                Some(b',') => self.at += 1,
                Some(b']') => {
                    self.at += 1;
                    return Ok(Json::Array(values));
                }
                None => return Err(Error("End of input".into())),
                _ => return self.error("Unterminated array"),
            }
        }
    }

    fn object(&mut self) -> Result<Json, Error> {
        self.at += 1;
        let mut object = Object::default();
        self.skip_whitespace();
        if self.peek() == Some(b'}') {
            self.at += 1;
            return Ok(Json::Object(object));
        }
        loop {
            self.skip_whitespace();
            if self.peek() != Some(b'"') {
                return self.error("Expected name");
            }
            let key = self.string()?;
            self.skip_whitespace();
            if self.peek() != Some(b':') {
                return self.error("Expected ':'");
            }
            self.at += 1;
            let value = self.value()?;
            object.put(key, value);
            self.skip_whitespace();
            match self.peek() {
                Some(b',') => self.at += 1,
                Some(b'}') => {
                    self.at += 1;
                    return Ok(Json::Object(object));
                }
                None => return Err(Error("End of input".into())),
                _ => return self.error("Unterminated object"),
            }
        }
    }
}

/// Characters Gson reads as part of an unquoted literal.
fn is_literal(byte: u8) -> bool {
    !matches!(byte, b'/' | b'\\' | b';' | b'#' | b'=' | b'{' | b'}' | b'[' | b']' | b':' | b',' | b' ' | b'\t' | b'\x0c' | b'\r' | b'\n')
}

fn utf8_length(first: u8) -> usize {
    match first {
        0xF0..=0xF7 => 4,
        0xE0..=0xEF => 3,
        0xC0..=0xDF => 2,
        _ => 1,
    }
}

/// Appends escaped UTF-16 units (lone surrogates become U+FFFD).
fn flush(units: &mut Vec<u16>, text: &mut String) {
    if !units.is_empty() {
        text.push_str(&String::from_utf16_lossy(units));
        units.clear();
    }
}

/// The input as Java's UTF-8 reader decodes it.
fn decode(bytes: &[u8]) -> std::borrow::Cow<'_, str> {
    String::from_utf8_lossy(bytes)
}

/// The first value of a resource file, as `GsonHelper.parse`/`fromJson`
/// read it: anything after it is ignored. An empty file is `Null`.
pub(crate) fn parse_value(bytes: &[u8]) -> Result<Json, Error> {
    let text = decode(bytes);
    let mut parser = Parser { text: text.as_bytes(), at: 0, depth: 0 };
    parser.skip_whitespace();
    if parser.peek().is_none() {
        return Ok(Json::Null);
    }
    parser.value()
}

/// A whole document, as `StrictJsonParser.parse` reads it: nothing may
/// follow the value (unless the value is `null`). An empty file is `Null`.
pub(crate) fn parse_document(bytes: &[u8]) -> Result<Json, Error> {
    let text = decode(bytes);
    let mut parser = Parser { text: text.as_bytes(), at: 0, depth: 0 };
    parser.skip_whitespace();
    if parser.peek().is_none() {
        return Ok(Json::Null);
    }
    let value = parser.value()?;
    parser.skip_whitespace();
    if !value.is_null() && parser.peek().is_some() {
        return Err(Error("Did not consume the entire document.".into()));
    }
    Ok(value)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_like_gson_strict() {
        let value = parse_document(br#"{"b": 1.50, "a": [true, null, "xA"], "b": 2}"#).unwrap();
        let object = value.as_object().unwrap();
        let keys: Vec<&str> = object.iter().map(|(key, _)| key).collect();
        assert_eq!(vec!["b", "a"], keys, "file order; a repeated key replaces in place");
        assert_eq!(Some(&Json::Number("2".into())), object.get("b"));
        assert_eq!(Some("xA".into()), object.get("a").unwrap().as_array().unwrap()[2].as_text());
        assert!(parse_document(b"{\"a\":1} trailing").is_err());
        assert!(parse_value(b"{\"a\":1} trailing").is_ok());
        assert!(parse_document(b"[1,]").is_err());
        assert!(parse_document(b"01").is_err());
        assert!(parse_document(b"TRUE").is_err());
        assert!(parse_document(b"{'a':1}").is_err());
        assert!(parse_document(b"\"tab\there\"").is_err());
        assert_eq!(Json::Null, parse_document(b"  ").unwrap());
        let deep = "[".repeat(256) + &"]".repeat(256);
        assert!(parse_document(deep.as_bytes()).is_err());
        assert_eq!(Some("1.50".into()), Json::Number("1.50".into()).as_text());
        assert_eq!(Some(true), Json::String("TRUE".into()).as_java_boolean());
        assert_eq!(Some(false), Json::Number("1".into()).as_java_boolean());
    }

    #[test]
    fn numbers_follow_java() {
        assert_eq!(1_410_065_408, int_value("1e10"));
        assert_eq!(3, int_value("3.9"));
        assert_eq!(-3, int_value("-3.9"));
        assert_eq!(-1_294_967_296, int_value("3000000000"));
        assert_eq!(0, int_value("1e400"));
        assert_eq!(1, int_value("1.0"));
        assert_eq!(0.1f32, parse_f32("0.1"));
        // A value whose double rounds differently than its direct float parse.
        assert_eq!("1.0000001".parse::<f32>().unwrap(), parse_f32("1.00000005960464477539062500001"));
    }
}
