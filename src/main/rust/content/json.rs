//! Strict JSON as Java reads data pack files (`StrictJsonParser`: Gson in
//! strict mode, then `JsonOps`). Numbers keep their source text, because DFU
//! converts them per target type (`LazilyParsedNumber.intValue` and friends),
//! and objects keep Gson's member order: a repeated key keeps its first
//! position and takes its last value.

use std::fmt;

#[derive(Clone, Debug, PartialEq)]
pub enum Json {
    Null,
    Bool(bool),
    /// The number's source text, a valid JSON number.
    Number(String),
    String(String),
    Array(Vec<Json>),
    Object(Vec<(String, Json)>),
}

/// Gson's default `JsonReader` nesting limit.
const NESTING_LIMIT: usize = 255;

impl Json {
    /// A member of an object; JSON `null` counts as absent, as in `JsonOps`.
    pub fn get(&self, key: &str) -> Option<&Json> {
        match self {
            Json::Object(members) => members.iter().find(|(k, _)| k == key).map(|(_, v)| v).filter(|v| !matches!(v, Json::Null)),
            _ => None,
        }
    }
}

impl fmt::Display for Json {
    /// Gson's compact `toString`, used in error messages.
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Json::Null => f.write_str("null"),
            Json::Bool(value) => write!(f, "{value}"),
            Json::Number(text) => f.write_str(text),
            Json::String(text) => write_string(f, text),
            Json::Array(items) => {
                f.write_str("[")?;
                for (i, item) in items.iter().enumerate() {
                    if i > 0 {
                        f.write_str(",")?;
                    }
                    write!(f, "{item}")?;
                }
                f.write_str("]")
            }
            Json::Object(members) => {
                f.write_str("{")?;
                for (i, (key, value)) in members.iter().enumerate() {
                    if i > 0 {
                        f.write_str(",")?;
                    }
                    write_string(f, key)?;
                    write!(f, ":{value}")?;
                }
                f.write_str("}")
            }
        }
    }
}

fn write_string(f: &mut fmt::Formatter<'_>, text: &str) -> fmt::Result {
    f.write_str("\"")?;
    for c in text.chars() {
        match c {
            '"' => f.write_str("\\\"")?,
            '\\' => f.write_str("\\\\")?,
            '\n' => f.write_str("\\n")?,
            '\r' => f.write_str("\\r")?,
            '\t' => f.write_str("\\t")?,
            '\u{8}' => f.write_str("\\b")?,
            '\u{c}' => f.write_str("\\f")?,
            c if (c as u32) < 0x20 => write!(f, "\\u{:04x}", c as u32)?,
            c => write!(f, "{c}")?,
        }
    }
    f.write_str("\"")
}

/// A tree the parser builds directly (`Json`, or a consumer's own nodes).
pub trait Build: Sized {
    fn null() -> Self;
    fn boolean(value: bool) -> Self;
    fn number(text: String) -> Self;
    fn string(text: String) -> Self;
    fn array(items: Vec<Self>) -> Self;
    fn object(members: Vec<(String, Self)>) -> Self;
    fn is_null(&self) -> bool;
}

impl Build for Json {
    fn null() -> Self {
        Json::Null
    }
    fn boolean(value: bool) -> Self {
        Json::Bool(value)
    }
    fn number(text: String) -> Self {
        Json::Number(text)
    }
    fn string(text: String) -> Self {
        Json::String(text)
    }
    fn array(items: Vec<Self>) -> Self {
        Json::Array(items)
    }
    fn object(members: Vec<(String, Self)>) -> Self {
        Json::Object(members)
    }
    fn is_null(&self) -> bool {
        matches!(self, Json::Null)
    }
}

/// Parses a data pack file: malformed UTF-8 decodes to replacement
/// characters (`InputStreamReader`), a leading byte order mark is skipped,
/// an empty document is `null`, and anything after a non-null value fails.
pub fn parse(bytes: &[u8]) -> Result<Json, String> {
    parse_into(bytes)
}

/// [`parse`] into any [`Build`] tree.
pub fn parse_into<T: Build>(bytes: &[u8]) -> Result<T, String> {
    let text = String::from_utf8_lossy(bytes);
    let mut parser = Parser { chars: text.as_bytes(), pos: 0, depth: 0 };
    if parser.chars.starts_with("\u{feff}".as_bytes()) {
        parser.pos = 3;
    }
    parser.skip_whitespace();
    if parser.pos == parser.chars.len() {
        return Ok(T::null());
    }
    let value: T = parser.value()?;
    if !value.is_null() {
        parser.skip_whitespace();
        if parser.pos != parser.chars.len() {
            return Err(parser.error("Did not consume the entire document."));
        }
    }
    Ok(value)
}

/// Parses UTF-8 bytes; every structural character is ASCII, and string
/// contents are copied as UTF-8 runs.
struct Parser<'a> {
    chars: &'a [u8],
    pos: usize,
    depth: usize,
}

impl Parser<'_> {
    fn error(&self, message: &str) -> String {
        format!("{message} at character {}", self.pos)
    }

    fn peek(&self) -> Option<char> {
        self.chars.get(self.pos).map(|&b| b as char)
    }

    fn skip_whitespace(&mut self) {
        let bytes = self.chars;
        let mut pos = self.pos;
        while pos < bytes.len() && matches!(bytes[pos], b' ' | b'\t' | b'\n' | b'\r') {
            pos += 1;
        }
        self.pos = pos;
    }

    fn expect(&mut self, c: char) -> Result<(), String> {
        self.skip_whitespace();
        if self.peek() == Some(c) {
            self.pos += 1;
            Ok(())
        } else {
            Err(self.error(&format!("Expected '{c}'")))
        }
    }

    fn value<T: Build>(&mut self) -> Result<T, String> {
        self.skip_whitespace();
        match self.peek() {
            Some('{') => self.object(),
            Some('[') => self.array(),
            Some('"') => self.string().map(T::string),
            Some('-' | '0'..='9') => self.number(),
            Some(_) => self.literal(),
            None => Err(self.error("End of input")),
        }
    }

    fn enter(&mut self) -> Result<(), String> {
        if self.depth >= NESTING_LIMIT {
            return Err(self.error(&format!("Nesting limit {NESTING_LIMIT} reached")));
        }
        self.depth += 1;
        self.pos += 1;
        Ok(())
    }

    fn object<T: Build>(&mut self) -> Result<T, String> {
        self.enter()?;
        let mut members: Vec<(String, T)> = Vec::new();
        self.skip_whitespace();
        if self.peek() == Some('}') {
            self.pos += 1;
            self.depth -= 1;
            return Ok(T::object(members));
        }
        loop {
            self.skip_whitespace();
            if self.peek() != Some('"') {
                return Err(self.error("Expected name"));
            }
            let key = self.string()?;
            self.expect(':')?;
            let value = self.value()?;
            match members.iter_mut().find(|(k, _)| *k == key) {
                Some(existing) => existing.1 = value,
                None => members.push((key, value)),
            }
            self.skip_whitespace();
            match self.peek() {
                Some(',') => self.pos += 1,
                Some('}') => {
                    self.pos += 1;
                    self.depth -= 1;
                    return Ok(T::object(members));
                }
                _ => return Err(self.error("Unterminated object")),
            }
        }
    }

    fn array<T: Build>(&mut self) -> Result<T, String> {
        self.enter()?;
        let mut items = Vec::new();
        self.skip_whitespace();
        if self.peek() == Some(']') {
            self.pos += 1;
            self.depth -= 1;
            return Ok(T::array(items));
        }
        loop {
            items.push(self.value()?);
            self.skip_whitespace();
            match self.peek() {
                Some(',') => self.pos += 1,
                Some(']') => {
                    self.pos += 1;
                    self.depth -= 1;
                    return Ok(T::array(items));
                }
                _ => return Err(self.error("Unterminated array")),
            }
        }
    }

    fn string(&mut self) -> Result<String, String> {
        self.pos += 1;
        let mut out = String::new();
        loop {
            // A run of ordinary characters copies at once (valid UTF-8, from the lossy decode).
            let start = self.pos;
            let bytes = self.chars;
            let mut pos = start;
            while pos < bytes.len() && bytes[pos] != b'"' && bytes[pos] != b'\\' && bytes[pos] >= 0x20 {
                pos += 1;
            }
            self.pos = pos;
            if pos > start {
                // The run stops only at ASCII bytes, so it is whole UTF-8 from the lossy decode.
                out.push_str(std::str::from_utf8(&bytes[start..pos]).map_err(|e| e.to_string())?);
            }
            let Some(c) = self.peek() else { return Err(self.error("Unterminated string")) };
            self.pos += 1;
            match c {
                '"' => return Ok(out),
                '\\' => {
                    let Some(e) = self.peek() else { return Err(self.error("Unterminated escape sequence")) };
                    self.pos += 1;
                    match e {
                        '"' => out.push('"'),
                        '\\' => out.push('\\'),
                        '/' => out.push('/'),
                        'b' => out.push('\u{8}'),
                        'f' => out.push('\u{c}'),
                        'n' => out.push('\n'),
                        'r' => out.push('\r'),
                        't' => out.push('\t'),
                        'u' => {
                            let unit = self.hex4()?;
                            // Gson keeps UTF-16 units; a valid surrogate pair is one character.
                            if (0xD800..0xDC00).contains(&unit) && self.chars.get(self.pos) == Some(&b'\\') && self.chars.get(self.pos + 1) == Some(&b'u') {
                                let save = self.pos;
                                self.pos += 2;
                                let low = self.hex4()?;
                                if (0xDC00..0xE000).contains(&low) {
                                    let code = 0x10000 + ((unit - 0xD800) << 10) + (low - 0xDC00);
                                    out.push(char::from_u32(code).unwrap_or('\u{fffd}'));
                                    continue;
                                }
                                self.pos = save;
                            }
                            out.push(char::from_u32(unit).unwrap_or('\u{fffd}'));
                        }
                        _ => return Err(self.error("Invalid escape sequence")),
                    }
                }
                c if (c as u32) < 0x20 => return Err(self.error("Unescaped control characters (\\u0000-\\u001F) are not allowed in strict mode")),
                c => out.push(c),
            }
        }
    }

    fn hex4(&mut self) -> Result<u32, String> {
        let mut value = 0;
        for _ in 0..4 {
            let Some(digit) = self.peek().and_then(|c| c.to_digit(16)) else {
                return Err(self.error("Malformed Unicode escape"));
            };
            value = value * 16 + digit;
            self.pos += 1;
        }
        Ok(value)
    }

    /// The JSON number grammar; anything else starting like a number is a
    /// malformed literal in strict mode.
    fn number<T: Build>(&mut self) -> Result<T, String> {
        let start = self.pos;
        let digits = |p: &mut Self| {
            let from = p.pos;
            while matches!(p.peek(), Some('0'..='9')) {
                p.pos += 1;
            }
            p.pos - from
        };
        if self.peek() == Some('-') {
            self.pos += 1;
        }
        match self.peek() {
            Some('0') => self.pos += 1,
            Some('1'..='9') => {
                digits(self);
            }
            _ => return Err(self.error("Malformed number")),
        }
        if self.peek() == Some('.') {
            self.pos += 1;
            if digits(self) == 0 {
                return Err(self.error("Malformed number"));
            }
        }
        if matches!(self.peek(), Some('e' | 'E')) {
            self.pos += 1;
            if matches!(self.peek(), Some('+' | '-')) {
                self.pos += 1;
            }
            if digits(self) == 0 {
                return Err(self.error("Malformed number"));
            }
        }
        // A number must end at a delimiter, or the token is a malformed literal.
        if let Some(c) = self.peek() {
            if !matches!(c, ' ' | '\t' | '\n' | '\r' | ',' | ']' | '}' | ':') {
                return Err(self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON"));
            }
        }
        Ok(T::number(String::from_utf8_lossy(&self.chars[start..self.pos]).into_owned()))
    }

    fn literal<T: Build>(&mut self) -> Result<T, String> {
        for (word, value) in [("true", 1u8), ("false", 0), ("null", 2)] {
            let end = self.pos + word.len();
            if end <= self.chars.len() && &self.chars[self.pos..end] == word.as_bytes() {
                let next = self.chars.get(end).map(|&b| b as char);
                if next.is_none_or(|c| matches!(c, ' ' | '\t' | '\n' | '\r' | ',' | ']' | '}' | ':')) {
                    self.pos = end;
                    return Ok(match value {
                        2 => T::null(),
                        v => T::boolean(v == 1),
                    });
                }
            }
        }
        Err(self.error("Use JsonReader.setStrictness(Strictness.LENIENT) to accept malformed JSON"))
    }
}

/// Java's `Number` conversions of a JSON number's text (`LazilyParsedNumber`).
pub mod number {
    /// `intValue`: `Integer.parseInt`, else `(int) Long.parseLong`, else
    /// `new BigDecimal(text).intValue()` (truncated, low 32 bits).
    pub fn int_value(text: &str) -> i32 {
        if let Ok(value) = text.parse::<i32>() {
            return value;
        }
        if let Ok(value) = text.parse::<i64>() {
            return value as i32;
        }
        big_decimal_low_bits(text) as i32
    }

    /// `Float.parseFloat`.
    pub fn float_value(text: &str) -> f32 {
        text.parse::<f32>().unwrap_or(f32::NAN)
    }

    /// `Double.parseDouble`.
    pub fn double_value(text: &str) -> f64 {
        text.parse::<f64>().unwrap_or(f64::NAN)
    }

    /// The low 32 bits of a decimal number's integer part (toward zero).
    fn big_decimal_low_bits(text: &str) -> u32 {
        let (negative, rest) = match text.strip_prefix('-') {
            Some(rest) => (true, rest),
            None => (false, text),
        };
        let (mantissa, exponent) = match rest.find(['e', 'E']) {
            Some(at) => (&rest[..at], rest[at + 1..].parse::<i64>().unwrap_or(0)),
            None => (rest, 0),
        };
        let (whole, fraction) = mantissa.split_once('.').unwrap_or((mantissa, ""));
        let digits: Vec<u8> = whole.bytes().chain(fraction.bytes()).map(|b| b - b'0').collect();
        // The integer part has `whole.len() + exponent` digits of `digits`.
        let integer_digits = whole.len() as i64 + exponent;
        let mut value: u32 = 0;
        if integer_digits > 0 {
            let take = integer_digits.min(digits.len() as i64) as usize;
            for &d in &digits[..take] {
                value = value.wrapping_mul(10).wrapping_add(u32::from(d));
            }
            // Trailing zeros beyond the mantissa; 10^32 is 0 modulo 2^32.
            for _ in 0..(integer_digits - take as i64).min(32) {
                value = value.wrapping_mul(10);
            }
        }
        if negative {
            value.wrapping_neg()
        } else {
            value
        }
    }
}

#[cfg(test)]
mod tests {
    use super::number::*;
    use super::*;

    #[test]
    fn strictness_matches_gson() {
        for ok in ["{}", " \t\n{}\n", "\u{feff}{}", "[1,2]", "\"\\u00e9\"", "-0", "1E2", "1e+5", "null x", "", "  "] {
            assert!(parse(ok.as_bytes()).is_ok(), "{ok:?}");
        }
        for bad in ["01", "[1,]", "{\"a\":1}x", "'a'", "[1 2]", "1.", "-", "[NaN]", "{a:1}", "\u{c}{}", "{\"a\":\"\u{1}\"}", "\"\\'\"", "tru", "[true1]", ".5", "+1"] {
            assert!(parse(bad.as_bytes()).is_err(), "{bad:?}");
        }
        assert_eq!(parse(b"").unwrap(), Json::Null);
        let nested = "[".repeat(255) + &"]".repeat(255);
        assert!(parse(nested.as_bytes()).is_ok());
        let deeper = "[".repeat(256) + &"]".repeat(256);
        assert!(parse(deeper.as_bytes()).is_err());
    }

    #[test]
    fn repeated_keys_keep_first_position_and_last_value() {
        let value = parse(br#"{"a":1,"b":2,"a":3}"#).unwrap();
        assert_eq!(value.to_string(), r#"{"a":3,"b":2}"#);
    }

    #[test]
    fn numbers_convert_as_java_does() {
        for (text, int) in [("1.5", 1), ("1e10", 1410065408), ("1e400", 0), ("-2147483649", 2147483647), ("4294967297", 1), ("-0.9", 0),
            ("1E2", 100), ("2147483648.7", -2147483648), ("123456789012345678901234567890", 1312754386)] {
            assert_eq!(int_value(text), int, "{text}");
        }
        assert_eq!(float_value("1e400"), f32::INFINITY);
        assert!(float_value("-0").is_sign_negative());
    }
}
