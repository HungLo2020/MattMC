//! Input values as DFU sees them: a parsed data pack file, or a tree Java
//! walked from any `DynamicOps` input. Numbers keep Java's representation so
//! each target type converts them as Java does.

use std::sync::Arc;

use crate::content::json::{number, Build, Json};

/// A number as Java holds it: source text (`LazilyParsedNumber`) or a boxed primitive.
#[derive(Clone, Debug, PartialEq)]
pub enum Num {
    Text(String),
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
}

impl Num {
    /// `Number.intValue`.
    pub fn int_value(&self) -> i32 {
        match self {
            Num::Text(text) => number::int_value(text),
            Num::Byte(v) => i32::from(*v),
            Num::Short(v) => i32::from(*v),
            Num::Int(v) => *v,
            Num::Long(v) => *v as i32,
            // Java's narrowing saturates and maps NaN to 0, as `as` does.
            Num::Float(v) => *v as i32,
            Num::Double(v) => *v as i32,
        }
    }
    /// `Number.longValue`.
    pub fn long_value(&self) -> i64 {
        match self {
            Num::Text(text) => number::long_value(text),
            Num::Byte(v) => i64::from(*v),
            Num::Short(v) => i64::from(*v),
            Num::Int(v) => i64::from(*v),
            Num::Long(v) => *v,
            // Java's narrowing saturates and maps NaN to 0, as `as` does.
            Num::Float(v) => *v as i64,
            Num::Double(v) => *v as i64,
        }
    }
    /// `Number.floatValue`.
    pub fn float_value(&self) -> f32 {
        match self {
            Num::Text(text) => number::float_value(text),
            Num::Byte(v) => f32::from(*v),
            Num::Short(v) => f32::from(*v),
            Num::Int(v) => *v as f32,
            Num::Long(v) => *v as f32,
            Num::Float(v) => *v,
            Num::Double(v) => *v as f32,
        }
    }
    /// `Number.doubleValue`.
    pub fn double_value(&self) -> f64 {
        match self {
            Num::Text(text) => number::double_value(text),
            Num::Byte(v) => f64::from(*v),
            Num::Short(v) => f64::from(*v),
            Num::Int(v) => f64::from(*v),
            Num::Long(v) => *v as f64,
            Num::Float(v) => f64::from(*v),
            Num::Double(v) => *v,
        }
    }
    /// `Number.byteValue` (`NbtOps` reads booleans as `byteValue() != 0`).
    pub fn byte_value(&self) -> i8 {
        match self {
            Num::Byte(v) => *v,
            Num::Short(v) => *v as i8,
            Num::Long(v) => *v as i8,
            Num::Float(v) => (*v as i32) as i8,
            Num::Double(v) => (*v as i32) as i8,
            other => other.int_value() as i8,
        }
    }
}

/// `BigDecimal(text).doubleValue()`: the correctly rounded double.
pub fn number_text_double(text: &str) -> f64 {
    text.parse::<f64>().unwrap_or_else(|_| number::double_value(text))
}

impl std::fmt::Display for Num {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Num::Text(text) => f.write_str(text),
            Num::Byte(v) => write!(f, "{v}b"),
            Num::Short(v) => write!(f, "{v}s"),
            Num::Int(v) => write!(f, "{v}"),
            Num::Long(v) => write!(f, "{v}L"),
            Num::Float(v) => write!(f, "{v}f"),
            Num::Double(v) => write!(f, "{v}d"),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    Null,
    Bool(bool),
    Number(Num),
    String(String),
    /// Shared, so keeping a delegated subtree does not copy it.
    List(Arc<[Node]>),
    Map(Arc<[(String, Node)]>),
}

/// An input value and, for trees from Java, its preorder index there.
#[derive(Clone, Debug, PartialEq)]
pub struct Node {
    pub index: Option<u32>,
    pub value: Value,
}

impl Node {
    pub fn new(value: Value) -> Node {
        Node { index: None, value }
    }

    pub fn from_json(json: Json) -> Node {
        Node::new(match json {
            Json::Null => Value::Null,
            Json::Bool(v) => Value::Bool(v),
            Json::Number(text) => Value::Number(Num::Text(text)),
            Json::String(text) => Value::String(text),
            Json::Array(items) => Value::List(items.into_iter().map(Node::from_json).collect()),
            Json::Object(members) => Value::Map(members.into_iter().map(|(k, v)| (k, Node::from_json(v))).collect()),
        })
    }

    /// A map member; null counts as absent, as `JsonOps` and `NbtOps` maps do.
    pub fn get(&self, key: &str) -> Option<&Node> {
        match &self.value {
            Value::Map(members) => members.iter().find(|(k, _)| k == key).map(|(_, v)| v).filter(|v| v.value != Value::Null),
            _ => None,
        }
    }
}

impl Node {
    /// The value as its `DynamicOps` prints it: Gson's JSON for `JsonOps`,
    /// SNBT (`StringTagVisitor`, keys sorted) for `NbtOps`.
    pub fn show(&self, mode: Mode) -> String {
        let mut out = String::new();
        self.write_shown(mode, &mut out);
        out
    }

    fn write_shown(&self, mode: Mode, out: &mut String) {
        use std::fmt::Write;
        match (&self.value, mode) {
            (Value::Null, Mode::Json) => out.push_str("null"),
            (Value::Null, Mode::Nbt) => out.push_str("END"),
            (Value::Bool(v), Mode::Json) => write!(out, "{v}").unwrap(),
            (Value::Bool(v), Mode::Nbt) => out.push_str(if *v { "1b" } else { "0b" }),
            (Value::Number(n), Mode::Json) => match n {
                Num::Text(t) => out.push_str(t),
                Num::Byte(v) => write!(out, "{v}").unwrap(),
                Num::Short(v) => write!(out, "{v}").unwrap(),
                Num::Int(v) => write!(out, "{v}").unwrap(),
                Num::Long(v) => write!(out, "{v}").unwrap(),
                Num::Float(v) => out.push_str(&java_float(*v)),
                Num::Double(v) => out.push_str(&java_double(*v)),
            },
            (Value::Number(n), Mode::Nbt) => match n {
                Num::Text(t) => out.push_str(t),
                Num::Byte(v) => write!(out, "{v}b").unwrap(),
                Num::Short(v) => write!(out, "{v}s").unwrap(),
                Num::Int(v) => write!(out, "{v}").unwrap(),
                Num::Long(v) => write!(out, "{v}L").unwrap(),
                Num::Float(v) => write!(out, "{}f", java_float(*v)).unwrap(),
                Num::Double(v) => write!(out, "{}d", java_double(*v)).unwrap(),
            },
            (Value::String(s), Mode::Json) => write!(out, "{}", Json::String(s.clone())).unwrap(),
            (Value::String(s), Mode::Nbt) => snbt_quote(s, out),
            (Value::List(items), _) => {
                out.push('[');
                for (i, item) in items.iter().enumerate() {
                    if i > 0 {
                        out.push(',');
                    }
                    item.write_shown(mode, out);
                }
                out.push(']');
            }
            (Value::Map(members), _) => {
                out.push('{');
                let mut order: Vec<&(String, Node)> = members.iter().collect();
                if mode == Mode::Nbt {
                    order.sort_by(|a, b| java_compare(&a.0, &b.0));
                }
                for (i, (k, v)) in order.into_iter().enumerate() {
                    if i > 0 {
                        out.push(',');
                    }
                    match mode {
                        Mode::Json => write!(out, "{}", Json::String(k.clone())).unwrap(),
                        Mode::Nbt if snbt_bare_key(k) => out.push_str(k),
                        Mode::Nbt => snbt_quote(k, out),
                    }
                    out.push(':');
                    v.write_shown(mode, out);
                }
                out.push('}');
            }
        }
    }
}

/// `String.compareTo`: UTF-16 code unit order.
fn java_compare(a: &str, b: &str) -> std::cmp::Ordering {
    a.encode_utf16().cmp(b.encode_utf16())
}

/// `StringTagVisitor`'s unquoted keys: `[A-Za-z._]+[A-Za-z0-9._+-]*`, not true/false.
fn snbt_bare_key(key: &str) -> bool {
    let bytes = key.as_bytes();
    let start = |c: u8| c.is_ascii_alphabetic() || c == b'.' || c == b'_';
    !key.eq_ignore_ascii_case("true")
        && !key.eq_ignore_ascii_case("false")
        && !bytes.is_empty()
        && start(bytes[0])
        && bytes.iter().all(|&c| c.is_ascii_alphanumeric() || matches!(c, b'.' | b'_' | b'+' | b'-'))
}

/// `StringTag.quoteAndEscape`: the quote not in the text (double first).
fn snbt_quote(s: &str, out: &mut String) {
    let quote = s.chars().find(|c| *c == '"' || *c == '\'').map_or('"', |c| if c == '"' { '\'' } else { '"' });
    out.push(quote);
    for c in s.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '"' | '\'' if c == quote => {
                out.push('\\');
                out.push(c);
            }
            '\u{8}' => out.push_str("\\b"),
            '\t' => out.push_str("\\t"),
            '\n' => out.push_str("\\n"),
            '\u{c}' => out.push_str("\\f"),
            '\r' => out.push_str("\\r"),
            c if (c as u32) < 0x20 => {
                use std::fmt::Write;
                write!(out, "\\x{:02X}", c as u32).unwrap();
            }
            c => out.push(c),
        }
    }
    out.push(quote);
}

impl std::fmt::Display for Node {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match &self.value {
            Value::Null => f.write_str("null"),
            Value::Bool(v) => write!(f, "{v}"),
            Value::Number(n) => write!(f, "{n}"),
            Value::String(s) => write!(f, "{}", Json::String(s.clone())),
            Value::List(items) => {
                f.write_str("[")?;
                for (i, item) in items.iter().enumerate() {
                    if i > 0 {
                        f.write_str(",")?;
                    }
                    write!(f, "{item}")?;
                }
                f.write_str("]")
            }
            Value::Map(members) => {
                f.write_str("{")?;
                for (i, (k, v)) in members.iter().enumerate() {
                    if i > 0 {
                        f.write_str(",")?;
                    }
                    write!(f, "{}:{v}", Json::String(k.clone()))?;
                }
                f.write_str("}")
            }
        }
    }
}

/// The parser builds nodes directly, without an intermediate `Json` tree.
impl Build for Node {
    fn null() -> Self {
        Node::new(Value::Null)
    }
    fn boolean(value: bool) -> Self {
        Node::new(Value::Bool(value))
    }
    fn number(text: String) -> Self {
        Node::new(Value::Number(Num::Text(text)))
    }
    fn string(text: String) -> Self {
        Node::new(Value::String(text))
    }
    fn array(items: Vec<Self>) -> Self {
        Node::new(Value::List(items.into()))
    }
    fn object(members: Vec<(String, Self)>) -> Self {
        Node::new(Value::Map(members.into()))
    }
    fn is_null(&self) -> bool {
        self.value == Value::Null
    }
}

/// How the source `DynamicOps` reads primitives.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    /// `JsonOps`: booleans are only JSON booleans.
    Json,
    /// `NbtOps`: booleans are numbers (`byteValue() != 0`).
    Nbt,
}

/// `Float.toString`: shortest round-trip digits, plain between 10^-3 and
/// 10^7, else computerized scientific notation; at least two digits.
pub fn java_float(v: f32) -> String {
    if v.is_nan() {
        return "NaN".into();
    }
    if v.is_infinite() {
        return if v > 0.0 { "Infinity" } else { "-Infinity" }.into();
    }
    let shortest = format!("{v:e}");
    let mantissa = shortest.split('e').next().unwrap();
    // One significant digit: Java prints the closest two-digit value.
    let sci = if mantissa.trim_start_matches('-').len() == 1 { format!("{v:.1e}") } else { shortest };
    java_decimal(&sci, f64::from(v.abs()))
}

/// `Double.toString`, by the same rules.
pub fn java_double(v: f64) -> String {
    if v.is_nan() {
        return "NaN".into();
    }
    if v.is_infinite() {
        return if v > 0.0 { "Infinity" } else { "-Infinity" }.into();
    }
    let shortest = format!("{v:e}");
    let mantissa = shortest.split('e').next().unwrap();
    let sci = if mantissa.trim_start_matches('-').len() == 1 { format!("{v:.1e}") } else { shortest };
    java_decimal(&sci, v.abs())
}

fn java_decimal(sci: &str, magnitude: f64) -> String {
    let (mantissa, exp) = sci.split_once('e').unwrap();
    let exp: i32 = exp.parse().unwrap();
    let negative = mantissa.starts_with('-');
    let digits: String = mantissa.chars().filter(char::is_ascii_digit).collect();
    let digits = digits.trim_end_matches('0');
    let digits = if digits.is_empty() { "0" } else { digits };
    let sign = if negative { "-" } else { "" };
    if magnitude == 0.0 {
        return format!("{sign}0.0");
    }
    if (1e-3..1e7).contains(&magnitude) {
        if exp >= 0 {
            let point = exp as usize + 1;
            if digits.len() <= point {
                format!("{sign}{digits}{}.0", "0".repeat(point - digits.len()))
            } else {
                format!("{sign}{}.{}", &digits[..point], &digits[point..])
            }
        } else {
            format!("{sign}0.{}{digits}", "0".repeat((-exp - 1) as usize))
        }
    } else {
        let frac = if digits.len() > 1 { &digits[1..] } else { "0" };
        format!("{sign}{}.{frac}E{exp}", &digits[..1])
    }
}
