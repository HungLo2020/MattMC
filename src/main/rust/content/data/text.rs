//! Text components (`ComponentSerialization.CODEC`), field for field as the
//! Java codecs: a string, a non-empty list, or a record of contents (typed
//! or matched by fields), siblings and style.
//!
//! Canonical form: `Record([contents, extra, style])`. A string decodes to
//! plain text; a list to its first component with the rest appended. A
//! component with plain text, no siblings and an empty style encodes as its
//! string (`tryCollapseToString`).
//!
//! Selector patterns and untrusted URIs are checked by their Java parsers
//! (the command system's and the JDK's), called back during decoding.

use super::schema::{self, case, f, Custom, Cx, Dv, Leaf, D, DR, O, P, R, S};
use super::value::{Node, Num, Value};
use super::values::{ARGB_COLOR, UUID};

use P::{Default, Inline, Lenient, LenientDefault, Optional, Required};

/// `ComponentSerialization.CODEC`.
pub static COMPONENT: S = S::Custom(&Custom { decode: decode_component, encode: encode_component, check: check_component });

static RAW: S = S::Either(&S::Either(&S::Str, &LIST), &RECORD);
static LIST: S = S::Validated(&S::List(&COMPONENT), non_empty);
static RECORD: S = S::Record(&[f("contents", &CONTENTS, Inline), f("extra", &LIST, Default(Dv::EmptyList)), f("style", &COMPACT_STYLE, Inline)]);

/// `STYLE`, with an empty style as an empty record (`Style.EMPTY`, no fields).
pub static COMPACT_STYLE: S = S::Custom(&Custom {
    decode: |map, cx| {
        if !matches!(map.value, Value::Map(_)) {
            return Err(schema::not_msg("map", map, cx.mode).into());
        }
        const STYLE_KEYS: [&str; 11] = ["color", "shadow_color", "bold", "italic", "underlined", "strikethrough", "obfuscated", "click_event", "hover_event", "insertion", "font"];
        if STYLE_KEYS.iter().all(|k| map.get(k).is_none()) {
            return Ok(D::Record(Vec::new()));
        }
        schema::decode_fields(STYLE_FIELDS_SCHEMA, map, cx)
    },
    encode: |d| match d {
        D::Record(v) if v.is_empty() => Ok(O::Map(Vec::new())),
        d => schema::encode(&STYLE, &STYLE, d),
    },
    check: |d| match d {
        D::Record(v) if v.is_empty() => Ok(()),
        d => schema::check(&STYLE, d),
    },
});

const TEXT: u16 = 0;

fn non_empty(d: &D) -> R<()> {
    match d {
        D::List(items) if items.is_empty() => Err("List must have contents".into()),
        _ => Ok(()),
    }
}

/// `Component.literal(text)`.
pub fn literal(text: String) -> D {
    D::Record(vec![D::Variant(TEXT, vec![D::Str(text)]), D::List(Vec::new()), D::Record(Vec::new())])
}

fn decode_component(node: &Node, cx: &mut Cx) -> DR {
    // The input's shape picks the one side that can succeed; outcomes other
    // than a plain failure are the same as `RAW`'s. A plain failure takes the
    // full path, which spells out every side's message.
    let (slots, logs) = (cx.slots.len(), cx.logs.len());
    match &node.value {
        Value::String(text) => return Ok(literal(text.clone())),
        Value::Map(_) => match schema::decode(&RECORD, node, cx) {
            Err(schema::Fail { partial: None, .. }) => {}
            result => return result,
        },
        Value::List(_) => match schema::decode(&LIST, node, cx) {
            Ok(list) => return Ok(from_list(list)),
            Err(schema::Fail { msg, partial: Some(p) }) => return Err(schema::Fail { msg, partial: Some(from_list(p)) }),
            Err(_) => {}
        },
        _ => {}
    }
    cx.slots.truncate(slots);
    cx.logs.truncate(logs);
    decode_raw(node, cx)
}

/// `createFromList`: the first component's copy with the rest appended.
fn from_list(list: D) -> D {
    let D::List(items) = list else { return D::Absent };
    let mut items = items.into_iter();
    match items.next() {
        Some(D::Record(mut first)) => {
            if let D::List(extra) = &mut first[1] {
                extra.extend(items);
            }
            D::Record(first)
        }
        _ => D::Absent,
    }
}

fn decode_raw(node: &Node, cx: &mut Cx) -> DR {
    schema::map_result(schema::decode(&RAW, node, cx), |d| match d {
        D::Variant(0, mut v) => match v.pop() {
            Some(D::Variant(0, mut s)) => match s.pop() {
                Some(D::Str(text)) => literal(text),
                _ => D::Absent,
            },
            // `createFromList`: the first component's copy with the rest appended.
            Some(D::Variant(1, mut l)) => match l.pop() {
                Some(D::List(items)) => {
                    let mut items = items.into_iter();
                    match items.next() {
                        Some(D::Record(mut first)) => {
                            if let D::List(extra) = &mut first[1] {
                                extra.extend(items);
                            }
                            D::Record(first)
                        }
                        _ => D::Absent,
                    }
                }
                _ => D::Absent,
            },
            _ => D::Absent,
        },
        D::Variant(1, mut v) => v.pop().unwrap_or(D::Absent),
        other => other,
    })
}

/// `tryCollapseToString`: plain text with no siblings and an empty style.
pub fn collapse(d: &D) -> Option<&str> {
    let D::Record(parts) = d else { return None };
    match (&parts[0], &parts[1], &parts[2]) {
        (D::Variant(TEXT, text), D::List(extra), D::Record(style)) if extra.is_empty() && style.iter().all(|s| *s == D::Absent) => match text.first() {
            Some(D::Str(s)) => Some(s),
            _ => None,
        },
        _ => None,
    }
}

fn encode_component(d: &D) -> R<O<'_>> {
    match collapse(d) {
        Some(text) => Ok(O::Str(std::borrow::Cow::Borrowed(text))),
        None => schema::encode(&RECORD, &RECORD, d),
    }
}

fn check_component(d: &D) -> R<()> {
    match collapse(d) {
        Some(_) => Ok(()),
        None => schema::check(&RECORD, d),
    }
}

// Contents.

static CONTENTS: S = S::Matcher("type", &[
    case("text", &[f("text", &S::Str, Required)]),
    case("translatable", &[f("translate", &S::Str, Required), f("fallback", &S::Str, Lenient), f("with", &ARGS, Default(Dv::EmptyList))]),
    case("keybind", &[f("keybind", &S::Str, Required)]),
    case("score", &[f("score", &SCORE, Required)]),
    case("selector", &[f("selector", &S::LeafStr(Leaf::Selector), Required), f("separator", &COMPONENT, Optional)]),
    case("nbt", &[
        f("nbt", &S::Str, Required),
        f("interpret", &S::Bool, LenientDefault(Dv::Bool(false))),
        f("separator", &COMPONENT, Lenient),
        f("source", &DATA_SOURCE, Inline),
    ]),
    case("object", &[f("contents", &OBJECT_INFO, Inline)]),
]);

/// `ScoreContents`: the name is a selector when it parses, else the plain
/// string (`Codec.either`).
static SCORE: S = S::Record(&[f("name", &SCORE_NAME, Required), f("objective", &S::Str, Required)]);
static SCORE_NAME: S = S::Either(&S::LeafStr(Leaf::Selector), &S::Str);

static DATA_SOURCE: S = S::Matcher("source", &[
    case("entity", &[f("entity", &S::Str, Required)]),
    case("block", &[f("block", &S::Str, Required)]),
    case("storage", &[f("storage", &S::Location, Required)]),
]);

/// `ObjectInfos.CODEC`; profiles are Java's codec until they are native.
static OBJECT_INFO: S = S::Matcher("object", &[
    case("atlas", &[f("atlas", &S::Location, Default(Dv::Name("minecraft:blocks"))), f("sprite", &S::Location, Required)]),
    case("player", &[f("player", &S::Java(schema::JAVA_PROFILE), Required), f("hat", &S::Bool, Default(Dv::Bool(true)))]),
]);

/// `TranslatableContents.ARG_CODEC`: a primitive, else a component (collapsed
/// to its string when it has one).
static ARGS: S = S::List(&ARG);
static ARG: S = S::Normalize(&S::Either(&PRIMITIVE, &COMPONENT), |d| match d {
    D::Variant(1, mut v) => match v.pop() {
        Some(component) => match collapse(&component) {
            Some(text) => D::Variant(0, vec![D::Str(text.to_owned())]),
            None => D::Variant(1, vec![component]),
        },
        None => D::Variant(1, v),
    },
    other => other,
});

/// `JsonOps.convertTo` of a number: exact integers as the narrowest of
/// byte, short, int and long; others as float when exact, else double.
fn narrow(text: &str) -> Num {
    let exact = || -> Option<i64> {
        let t = text.trim_start_matches('+');
        let (negative, t) = match t.strip_prefix('-') {
            Some(rest) => (true, rest),
            None => (false, t),
        };
        let (mantissa, exponent) = match t.find(['e', 'E']) {
            Some(i) => (&t[..i], t[i + 1..].parse::<i64>().ok()?),
            None => (t, 0),
        };
        let (int, frac) = mantissa.split_once('.').unwrap_or((mantissa, ""));
        let mut digits: String = format!("{int}{frac}");
        let mut scale = frac.len() as i64 - exponent;
        while scale > 0 && digits.ends_with('0') {
            digits.pop();
            scale -= 1;
        }
        let digits = digits.trim_start_matches('0');
        if digits.is_empty() {
            return Some(0);
        }
        if scale > 0 || -scale > 19 {
            return None;
        }
        let mut value: i128 = digits.parse().ok()?;
        for _ in 0..-scale {
            value *= 10;
        }
        let value = if negative { -value } else { value };
        i64::try_from(value).ok()
    };
    match exact() {
        Some(l) if l as i8 as i64 == l => Num::Byte(l as i8),
        Some(l) if l as i16 as i64 == l => Num::Short(l as i16),
        Some(l) if l as i32 as i64 == l => Num::Int(l as i32),
        Some(l) => Num::Long(l),
        None => {
            let d = super::value::number_text_double(text);
            if (d as f32) as f64 == d || d.is_nan() { Num::Float(d as f32) } else { Num::Double(d) }
        }
    }
}

/// The JVM's message for the `NullPointerException` `JsonOps` throws there.
const JSON_NULL_MESSAGE: &str = "Cannot invoke \"com.google.gson.JsonElement.getAsJsonPrimitive()\" because \"input\" is null";

fn has_null(node: &Node) -> bool {
    match &node.value {
        Value::Null => true,
        Value::Map(members) => members.iter().any(|(_, v)| has_null(v)),
        Value::List(items) => items.iter().any(has_null),
        _ => false,
    }
}

/// `ExtraCodecs.JAVA` limited to numbers, booleans and strings.
static PRIMITIVE: S = S::Custom(&Custom {
    decode: |node, _| match &node.value {
        Value::Number(Num::Text(text)) => Ok(D::Num(narrow(text))),
        Value::Number(n) => Ok(D::Num(n.clone())),
        Value::Bool(b) => Ok(D::Bool(*b)),
        Value::String(s) => Ok(D::Str(s.clone())),
        // Converting a nested JSON null throws in `JsonOps` (only JSON has nulls).
        Value::Map(_) | Value::List(_) if has_null(node) => Err(schema::throw_as(schema::NULL_POINTER, JSON_NULL_MESSAGE).into()),
        _ => Err("This value needs to be parsed as component".into()),
    },
    encode: |d| match d {
        D::Num(n) => Ok(O::Num(n.clone())),
        D::Bool(b) => Ok(O::Bool(*b)),
        D::Str(s) => Ok(O::Str(std::borrow::Cow::Borrowed(s))),
        D::Int(v) => Ok(O::Int(*v)),
        other => Err(format!("not a primitive argument: {other:?}")),
    },
    check: |_| Ok(()),
});

// Style (`Style.Serializer.MAP_CODEC`).

const STYLE_FIELDS_SCHEMA: &[schema::F] = match &STYLE {
    S::Record(fields) => fields,
    _ => &[],
};

pub static STYLE: S = S::Record(&[
    f("color", &TEXT_COLOR, Optional),
    f("shadow_color", &ARGB_COLOR, Optional),
    f("bold", &S::Bool, Optional),
    f("italic", &S::Bool, Optional),
    f("underlined", &S::Bool, Optional),
    f("strikethrough", &S::Bool, Optional),
    f("obfuscated", &S::Bool, Optional),
    f("click_event", &CLICK_EVENT, Optional),
    f("hover_event", &HOVER_EVENT, Optional),
    f("insertion", &S::Str, Optional),
    f("font", &S::Location, Optional),
]);

const NAMED_COLORS: &[&str] = &[
    "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
    "light_purple", "yellow", "white",
];

/// `Integer.parseInt(text, 16)` for ASCII digits: an optional sign, then digits.
fn parse_hex_int(text: &str) -> Option<i32> {
    let (negative, digits) = match text.as_bytes().first() {
        Some(b'-') => (true, &text[1..]),
        Some(b'+') => (false, &text[1..]),
        _ => (false, text),
    };
    if digits.is_empty() {
        return None;
    }
    let mut value: i64 = 0;
    for c in digits.chars() {
        value = value * 16 + i64::from(c.to_digit(16)?);
        if value > i64::from(i32::MAX) + 1 {
            return None;
        }
    }
    let value = if negative { -value } else { value };
    i32::try_from(value).ok()
}

/// `TextColor.CODEC`: a named color, or `#RRGGBB`; decoded as its serialized form.
static TEXT_COLOR: S = S::Custom(&Custom {
    decode: |node, cx| {
        let Value::String(text) = &node.value else { return Err(schema::not_msg("string", node, cx.mode).into()) };
        if let Some(hex) = text.strip_prefix('#') {
            return match parse_hex_int(hex) {
                Some(v) if (0..=0xFF_FFFF).contains(&v) => Ok(D::Str(format!("#{v:06X}"))),
                Some(_) => Err(format!("Color value out of range: {text}").into()),
                None => Err(format!("Invalid color value: {text}").into()),
            };
        }
        if NAMED_COLORS.contains(&text.as_str()) { Ok(D::Str(text.clone())) } else { Err(format!("Invalid color name: {text}").into()) }
    },
    encode: |d| match d {
        D::Str(s) => Ok(O::Str(std::borrow::Cow::Borrowed(s))),
        other => Err(format!("not a text color: {other:?}")),
    },
    check: |_| Ok(()),
});

/// `ExtraCodecs.CHAT_STRING`.
static CHAT_STRING: S = S::Validated(&S::Str, |d| {
    if let D::Str(s) = d {
        for c in s.chars() {
            let i = c as u32;
            if i == 167 || i < 32 || i == 127 {
                return Err(format!("Disallowed chat character: '{c}'"));
            }
        }
    }
    Ok(())
});

const CLICK_ACTIONS: &[&str] = &["open_url", "open_file", "run_command", "suggest_command", "show_dialog", "change_page", "copy_to_clipboard", "custom"];
static CLICK_ACTION: S = S::Validated(&S::Enum(CLICK_ACTIONS), |d| match d {
    D::Str(name) if name == "open_file" => Err("Click event type not allowed: OPEN_FILE".into()),
    _ => Ok(()),
});

/// `ClickEvent.CODEC`, in `ClickEvent.Action` order.
pub static CLICK_EVENT: S = S::KeyDispatch("action", &CLICK_ACTION, &[
    case("open_url", &[f("url", &S::LeafStr(Leaf::UntrustedUri), Required)]),
    case("open_file", &[f("path", &S::Str, Required)]),
    case("run_command", &[f("command", &CHAT_STRING, Required)]),
    case("suggest_command", &[f("command", &CHAT_STRING, Required)]),
    case("show_dialog", &[f("dialog", &S::Java(schema::JAVA_DIALOG), Required)]),
    case("change_page", &[f("page", &S::IntRange(1, i32::MAX), Required)]),
    case("copy_to_clipboard", &[f("value", &S::Str, Required)]),
    case("custom", &[f("id", &S::Location, Required), f("payload", &S::Java(schema::JAVA_NBT), Optional)]),
]);

/// `HoverEvent.CODEC`, in `HoverEvent.Action` order.
pub static HOVER_EVENT: S = S::KeyDispatch("action", &S::Enum(&["show_text", "show_item", "show_entity"]), &[
    case("show_text", &[f("value", &COMPONENT, Required)]),
    case("show_item", &[f("item", &super::items::ITEM_STACK, Inline)]),
    case("show_entity", &[f("id", &S::EntityType, Required), f("uuid", &LENIENT_UUID, Required), f("name", &COMPONENT, Optional)]),
]);

/// `UUIDUtil.LENIENT_CODEC`: four ints, or a UUID string; encodes as ints.
static LENIENT_UUID: S = S::Normalize(&S::Either(&UUID, &STRING_UUID), |d| match d {
    D::Variant(_, v) => D::Variant(0, v),
    other => other,
});

/// `UUIDUtil.STRING_CODEC`: `UUID.fromString`, as four ints.
static STRING_UUID: S = S::Custom(&Custom {
    decode: |node, cx| {
        let Value::String(text) = &node.value else { return Err(schema::not_msg("string", node, cx.mode).into()) };
        let (msb, lsb) = uuid_from_string(text).map_err(|e| format!("Invalid UUID {text}: {e}"))?;
        Ok(D::List(vec![D::Int((msb >> 32) as i32), D::Int(msb as i32), D::Int((lsb >> 32) as i32), D::Int(lsb as i32)]))
    },
    encode: |_| Err("string UUIDs encode as ints".into()),
    check: |_| Ok(()),
});

/// `Long.parseLong(text[begin..end], 16)`.
fn parse_hex_long(text: &str, begin: usize, end: usize) -> Result<i64, String> {
    let part = &text[begin..end];
    if part.is_empty() {
        return Err("For input string: \"\" under radix 16".into());
    }
    let (negative, digits) = match part.as_bytes()[0] {
        b'-' => (true, &part[1..]),
        b'+' => (false, &part[1..]),
        _ => (false, part),
    };
    let error = || format!("Error at index {} in: \"{part}\"", 0);
    if digits.is_empty() {
        return Err(error());
    }
    let mut value: i128 = 0;
    for c in digits.chars() {
        value = value * 16 + i128::from(c.to_digit(16).ok_or_else(error)?);
        if value > i128::from(i64::MAX) + 1 {
            return Err(error());
        }
    }
    let value = if negative { -value } else { value };
    i64::try_from(value).map_err(|_| error())
}

/// `UUID.fromString`.
fn uuid_from_string(name: &str) -> Result<(i64, i64), String> {
    if name.chars().count() > 36 {
        return Err("UUID string too large".into());
    }
    let dashes: Vec<usize> = name.match_indices('-').map(|(i, _)| i).collect();
    if dashes.len() != 4 {
        return Err(format!("Invalid UUID string: {name}"));
    }
    let (d1, d2, d3, d4) = (dashes[0], dashes[1], dashes[2], dashes[3]);
    let mut msb = parse_hex_long(name, 0, d1)? & 0xffff_ffff;
    msb = (msb << 16) | (parse_hex_long(name, d1 + 1, d2)? & 0xffff);
    msb = (msb << 16) | (parse_hex_long(name, d2 + 1, d3)? & 0xffff);
    let mut lsb = parse_hex_long(name, d3 + 1, d4)? & 0xffff;
    lsb = (lsb << 48) | (parse_hex_long(name, d4 + 1, name.len())? & 0xffff_ffff_ffff);
    Ok((msb, lsb))
}

