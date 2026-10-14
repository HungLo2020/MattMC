//! Declarative record schemas with DFU's decode and encode rules.
//!
//! A schema decodes an input [`Node`] into a canonical [`D`] value, and
//! encodes a [`D`] back into an output tree in the field order and with the
//! default omission Java's `RecordCodecBuilder` codecs use. Values that
//! belong to registries Java still owns are [`S::Delegate`] slots: decoding
//! keeps their input node for Java to resolve, encoding leaves a slot for
//! Java to fill.

use std::cmp::Ordering;

use super::value::{Mode, Node, Num, Value};
use crate::content::tags::Location;

pub type R<T> = Result<T, String>;

pub enum S {
    Bool,
    Int,
    /// `ExtraCodecs.intRange` and `NON_NEGATIVE_INT` / `POSITIVE_INT`, inclusive.
    IntRange(i32, i32),
    Float,
    /// `Codec.floatRange`: inclusive, by `Float.compareTo`.
    FloatRange(f32, f32),
    /// `ExtraCodecs.POSITIVE_FLOAT`: above 0, at most `Float.MAX_VALUE`.
    PositiveFloat,
    Double,
    Str,
    /// `ResourceLocation.CODEC` (and `ResourceKey.codec`); canonical "namespace:path".
    Location,
    /// `ExtraCodecs.RESOURCE_PATH_CODEC`.
    ResourcePath,
    /// `StringRepresentable.fromEnum`: serialized names.
    Enum(&'static [&'static str]),
    Record(&'static [F]),
    List(&'static S),
    /// A list whose failing entries are dropped and logged with this prefix (`promotePartial`).
    PartialList(&'static S, &'static str),
    /// `Codec.simpleMap` / `unboundedMap` from a string-keyed map.
    Map(&'static S, &'static S),
    /// A `"type"`-keyed dispatch over a built-in registry, by path in `minecraft`.
    Dispatch(&'static [(&'static str, &'static [F])]),
    /// `SoundEvent.CODEC`: a registered sound's ID, or an inline `{sound_id, range}`.
    Sound,
    /// `BuiltInRegistries.ENTITY_TYPE.byNameCodec()`, against the Rust entity catalog.
    EntityType,
    /// `MinMaxBounds.Doubles.CODEC`: a number, or `{min, max}` with no swapped bounds.
    DoubleBounds,
    /// Decoded, then checked.
    Validated(&'static S, fn(&D) -> R<()>),
    /// Resolved and encoded by a Java codec of another registry.
    Delegate,
    /// A delegated `HolderSet` (`HolderSetCodec`): a `#tag`, or IDs (a list,
    /// or one ID unless `force_list`). Simple forms are parsed for Java to
    /// look up directly; anything else Java decodes with the codec.
    HolderSet { force_list: bool },
    /// A delegated list of `HolderSet`s that are always lists (placed feature steps).
    HolderSetList,
}

/// A holder set's references as parsed: a tag, or element IDs.
#[derive(Clone, Debug, PartialEq)]
pub enum Refs {
    Tag(String),
    Ids(Vec<String>),
}

pub struct F {
    pub name: &'static str,
    pub schema: &'static S,
    pub presence: P,
}

pub const fn f(name: &'static str, schema: &'static S, presence: P) -> F {
    F { name, schema, presence }
}

#[derive(Clone, Copy)]
pub enum P {
    /// `fieldOf`.
    Required,
    /// `optionalFieldOf`: absent, or present and valid.
    Optional,
    /// `optionalFieldOf(name, default)`: omitted on encode when equal to the default.
    Default(Dv),
    /// `fieldOf(...).orElse(default)`: missing or invalid is the default; always encoded.
    OrElse(Dv),
    /// `lenientOptionalFieldOf`: invalid is absent.
    Lenient,
    /// A `MapCodec` record merged into its parent; the name only labels it.
    Inline,
    /// A delegated `optionalFieldOf(name, default)` whose default Java holds.
    JavaDefault,
}

/// Default values, in canonical form.
#[derive(Clone, Copy)]
pub enum Dv {
    Bool(bool),
    Float(f32),
    /// An enum's serialized name.
    Name(&'static str),
    EmptyMap,
}

impl Dv {
    fn to_d(self) -> D {
        match self {
            Dv::Bool(v) => D::Bool(v),
            Dv::Float(v) => D::Float(v),
            Dv::Name(v) => D::Str(v.into()),
            Dv::EmptyMap => D::Map(Vec::new()),
        }
    }
}

/// A decoded value.
#[derive(Clone, Debug)]
pub enum D {
    Absent,
    Bool(bool),
    Int(i32),
    Float(f32),
    Double(f64),
    Str(String),
    List(Vec<D>),
    Map(Vec<(String, D)>),
    /// Fields in schema order.
    Record(Vec<D>),
    /// A dispatch case and its fields.
    Variant(u16, Vec<D>),
    /// A delegated value: index into the decoded element's slots.
    Slot(u32),
    /// A delegated holder set (or list of them) and its parsed references, if simple.
    Holders(u32, Option<Vec<Refs>>),
    SoundRef(String),
    SoundDirect(String, Option<f32>),
}

impl PartialEq for D {
    /// `Objects.equals` on the Java values: floats compare by bits.
    fn eq(&self, other: &D) -> bool {
        match (self, other) {
            (D::Float(a), D::Float(b)) => a.to_bits() == b.to_bits(),
            (D::Double(a), D::Double(b)) => a.to_bits() == b.to_bits(),
            (D::Absent, D::Absent) => true,
            (D::Bool(a), D::Bool(b)) => a == b,
            (D::Int(a), D::Int(b)) => a == b,
            (D::Str(a), D::Str(b)) | (D::SoundRef(a), D::SoundRef(b)) => a == b,
            (D::List(a), D::List(b)) | (D::Record(a), D::Record(b)) => a == b,
            (D::Map(a), D::Map(b)) => a == b,
            (D::Variant(t, a), D::Variant(u, b)) => t == u && a == b,
            (D::Slot(a), D::Slot(b)) => a == b,
            // Parsed references derive from the slot's own input.
            (D::Holders(a, _), D::Holders(b, _)) => a == b,
            (D::SoundDirect(a, r), D::SoundDirect(b, s)) => a == b && r.map(f32::to_bits) == s.map(f32::to_bits),
            _ => false,
        }
    }
}

/// Decoding state: the source's rules, delegated input nodes and logged partial errors.
pub struct Cx {
    pub mode: Mode,
    /// Whether the source is a `RegistryOps` (registry references resolve; otherwise sounds are inline).
    pub registry_ops: bool,
    pub slots: Vec<Option<Node>>,
    pub logs: Vec<String>,
}

impl Cx {
    pub fn new(mode: Mode, registry_ops: bool) -> Cx {
        Cx { mode, registry_ops, slots: Vec::new(), logs: Vec::new() }
    }
    fn slot(&mut self, node: Option<Node>) -> D {
        self.slots.push(node);
        D::Slot(self.slots.len() as u32 - 1)
    }
}

fn not<T>(kind: &str, node: &Node) -> R<T> {
    Err(format!("Not a {kind}: {node}"))
}

fn number(node: &Node) -> R<&Num> {
    match &node.value {
        Value::Number(n) => Ok(n),
        _ => not("number", node),
    }
}

fn string(node: &Node) -> R<&str> {
    match &node.value {
        Value::String(s) => Ok(s),
        _ => not("string", node),
    }
}

fn map_members(node: &Node) -> R<&[(String, Node)]> {
    match &node.value {
        Value::Map(m) => Ok(m),
        _ => not("map", node),
    }
}

fn list_items(node: &Node) -> R<&[Node]> {
    match &node.value {
        Value::List(items) => Ok(items),
        _ => not("list", node),
    }
}

/// `Float.compareTo` / `Double.compareTo` ordering.
fn in_range<T: Copy>(value: T, min: T, max: T, cmp: fn(&T, &T) -> Ordering) -> bool {
    cmp(&value, &min) != Ordering::Less && cmp(&value, &max) != Ordering::Greater
}

fn location(node: &Node) -> R<String> {
    Ok(Location::parse(string(node)?)?.to_string())
}

pub fn decode(s: &S, node: &Node, cx: &mut Cx) -> R<D> {
    Ok(match s {
        S::Bool => match (&node.value, cx.mode) {
            (Value::Bool(v), _) => D::Bool(*v),
            (Value::Number(n), Mode::Nbt) => D::Bool(n.byte_value() != 0),
            _ => return not("boolean", node),
        },
        S::Int => D::Int(number(node)?.int_value()),
        S::IntRange(min, max) => {
            let v = number(node)?.int_value();
            if v < *min || v > *max {
                return Err(format!("Value must be within range [{min};{max}]: {v}"));
            }
            D::Int(v)
        }
        S::Float => D::Float(number(node)?.float_value()),
        S::FloatRange(min, max) => {
            let v = number(node)?.float_value();
            if !in_range(v, *min, *max, f32::total_cmp) {
                return Err(format!("Value {v} outside of range [{min}:{max}]"));
            }
            D::Float(v)
        }
        S::PositiveFloat => {
            let v = number(node)?.float_value();
            if v.total_cmp(&0.0) != Ordering::Greater || v.total_cmp(&f32::MAX) == Ordering::Greater {
                return Err(format!("Value must be positive: {v}"));
            }
            D::Float(v)
        }
        S::Double => D::Double(number(node)?.double_value()),
        S::Str => D::Str(string(node)?.to_owned()),
        S::Location => D::Str(location(node)?),
        S::ResourcePath => {
            let path = string(node)?;
            if !crate::core::id::is_valid_path(path) {
                return Err(format!("Invalid string to use as a resource path element: {path}"));
            }
            D::Str(path.to_owned())
        }
        S::Enum(names) => {
            let name = string(node)?;
            if !names.contains(&name) {
                return Err(format!("Unknown element name:{name}"));
            }
            D::Str(name.to_owned())
        }
        S::Record(fields) => {
            map_members(node)?;
            D::Record(record(fields, node, cx)?)
        }
        S::List(item) => D::List(list_items(node)?.iter().map(|n| decode(item, n, cx)).collect::<R<_>>()?),
        S::PartialList(item, prefix) => {
            let mut out = Vec::new();
            for n in list_items(node)? {
                match decode(item, n, cx) {
                    Ok(d) => out.push(d),
                    Err(error) => cx.logs.push(format!("{prefix}{error}")),
                }
            }
            D::List(out)
        }
        S::Map(key, value) => {
            let mut out = Vec::new();
            for (k, v) in map_members(node)? {
                let D::Str(k) = decode(key, &Node::new(Value::String(k.clone())), cx)? else { unreachable!() };
                out.push((k, decode(value, v, cx)?));
            }
            D::Map(out)
        }
        S::Dispatch(cases) => {
            map_members(node)?;
            let kind = node.get("type").ok_or_else(|| format!("Input does not contain a key [type]: MapLike[{node}]"))?;
            let location = Location::parse(string(kind)?)?;
            let index = cases.iter().position(|(name, _)| &*location.namespace == "minecraft" && *name == &*location.path)
                .ok_or_else(|| format!("Unknown registry key: {location}"))?;
            D::Variant(index as u16, record(cases[index].1, node, cx)?)
        }
        S::Sound => {
            let reference = if cx.registry_ops { string(node).ok().and_then(|s| Location::parse(s).ok()) } else { None };
            match reference {
                Some(location) => D::SoundRef(location.to_string()),
                None => {
                    map_members(node)?;
                    let id = location(node.get("sound_id").ok_or_else(|| format!("No key sound_id in MapLike[{node}]"))?)?;
                    let range = node.get("range").and_then(|n| number(n).ok()).map(Num::float_value);
                    D::SoundDirect(id, range)
                }
            }
        }
        S::EntityType => {
            let location = Location::parse(string(node)?)?;
            if &*location.namespace != "minecraft" || !entity_names().contains(&*location.path) {
                return Err(format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:entity_type]: {location}"));
            }
            D::Str(location.to_string())
        }
        S::DoubleBounds => {
            let (min, max) = match &node.value {
                Value::Map(_) => {
                    let opt = |key| node.get(key).map(|n| number(n).map(Num::double_value)).transpose();
                    match (opt("min"), opt("max")) {
                        (Ok(min), Ok(max)) => (min, max),
                        (Err(first), _) | (_, Err(first)) => return Err(format!("Failed to parse either. First: {first}; Second: Not a number: {node}")),
                    }
                }
                _ => {
                    let v = number(node).map_err(|second| format!("Failed to parse either. First: Not a map: {node}; Second: {second}"))?.double_value();
                    (Some(v), Some(v))
                }
            };
            if let (Some(a), Some(b)) = (min, max) {
                if a.total_cmp(&b) == Ordering::Greater {
                    return Err(format!("Swapped bounds in range: {a} is higher than {b}"));
                }
            }
            D::Record(vec![min.map_or(D::Absent, D::Double), max.map_or(D::Absent, D::Double)])
        }
        S::Validated(inner, check) => {
            let d = decode(inner, node, cx)?;
            check(&d)?;
            d
        }
        S::Delegate => cx.slot(Some(node.clone())),
        S::HolderSet { force_list } => {
            let refs = refs(node, *force_list).map(|r| vec![r]);
            let D::Slot(i) = cx.slot(Some(node.clone())) else { unreachable!() };
            D::Holders(i, refs)
        }
        S::HolderSetList => {
            let refs = match &node.value {
                Value::List(items) => items.iter().map(|n| refs(n, true)).collect::<Option<Vec<_>>>(),
                _ => None,
            };
            let D::Slot(i) = cx.slot(Some(node.clone())) else { unreachable!() };
            D::Holders(i, refs)
        }
    })
}

/// `HolderSetCodec`'s simple forms: `#tag`, or element IDs (a list, or one
/// ID unless `force_list`). None leaves the codec to decide.
fn refs(node: &Node, force_list: bool) -> Option<Refs> {
    let id = |n: &Node| match &n.value {
        Value::String(s) if !s.starts_with('#') => Location::parse(s).ok().map(|l| l.to_string()),
        _ => None,
    };
    match &node.value {
        Value::String(s) => match s.strip_prefix('#') {
            Some(tag) => Location::parse(tag).ok().map(|l| Refs::Tag(l.to_string())),
            None if !force_list => id(node).map(|i| Refs::Ids(vec![i])),
            None => None,
        },
        Value::List(items) => items.iter().map(id).collect::<Option<Vec<_>>>().map(Refs::Ids),
        _ => None,
    }
}

fn record(fields: &[F], map: &Node, cx: &mut Cx) -> R<Vec<D>> {
    fields.iter().map(|field| {
        if let P::Inline = field.presence {
            let S::Record(inner) = field.schema else { panic!("inline field {} is not a record", field.name) };
            return Ok(D::Record(record(inner, map, cx)?));
        }
        let value = map.get(field.name);
        Ok(match (field.presence, value) {
            (P::Required, None) => return Err(format!("No key {} in MapLike[{map}]", field.name)),
            (P::Optional | P::Lenient, None) => D::Absent,
            (P::Default(d) | P::OrElse(d), None) => d.to_d(),
            (P::JavaDefault, None) => match (cx.slot(None), field.schema) {
                (D::Slot(i), S::HolderSet { .. } | S::HolderSetList) => D::Holders(i, None),
                (slot, _) => slot,
            },
            (P::Lenient, Some(n)) => decode(field.schema, n, cx).unwrap_or(D::Absent),
            (P::OrElse(d), Some(n)) => decode(field.schema, n, cx).unwrap_or(d.to_d()),
            (_, Some(n)) => decode(field.schema, n, cx)?,
            (P::Inline, _) => unreachable!(),
        })
    }).collect()
}

fn entity_names() -> &'static std::collections::HashSet<&'static str> {
    static NAMES: std::sync::OnceLock<std::collections::HashSet<&'static str>> = std::sync::OnceLock::new();
    NAMES.get_or_init(|| crate::content::entity::definitions().iter().map(|d| d.name).collect())
}

/// An encoded value: Java turns it into its `DynamicOps` output.
#[derive(Clone, Debug, PartialEq)]
pub enum O {
    Bool(bool),
    Int(i32),
    Float(f32),
    Double(f64),
    Str(String),
    List(Vec<O>),
    Map(Vec<(String, O)>),
    /// A delegated value Java encodes.
    Slot(u32),
    /// A delegated `JavaDefault` field: Java omits the entry when it equals the default.
    SlotUnlessDefault(u32),
    /// A registered sound: its ID for `RegistryOps`, else its inline form (`RegistryFileCodec`).
    SoundRef(String),
}

/// Encodes `d`, decoded with `stored`, through `target`. Records match
/// fields by name, so a direct definition encodes in its network form.
pub fn encode(target: &S, stored: &S, d: &D) -> R<O> {
    Ok(match (target, d) {
        (S::Record(t), D::Record(values)) => {
            let S::Record(s) = stored else { return Err("record schema mismatch".into()) };
            let mut entries = Vec::new();
            record_entries(t, s, values, &mut entries)?;
            O::Map(entries)
        }
        (S::Validated(inner, _), _) => encode(inner, unvalidated(stored), d)?,
        (_, D::Bool(v)) => O::Bool(*v),
        (_, D::Int(v)) => O::Int(*v),
        (_, D::Float(v)) => O::Float(*v),
        (S::DoubleBounds, D::Record(bounds)) => match (&bounds[0], &bounds[1]) {
            (D::Double(a), D::Double(b)) if a.to_bits() == b.to_bits() => O::Double(*a),
            (min, max) => {
                let mut entries = Vec::new();
                if let D::Double(v) = min {
                    entries.push(("min".to_owned(), O::Double(*v)));
                }
                if let D::Double(v) = max {
                    entries.push(("max".to_owned(), O::Double(*v)));
                }
                O::Map(entries)
            }
        },
        (_, D::Double(v)) => O::Double(*v),
        (_, D::Str(v)) => O::Str(v.clone()),
        (_, D::SoundRef(v)) => O::SoundRef(v.clone()),
        (_, D::SoundDirect(id, range)) => {
            let mut entries = vec![("sound_id".to_owned(), O::Str(id.clone()))];
            if let Some(r) = range {
                entries.push(("range".to_owned(), O::Float(*r)));
            }
            O::Map(entries)
        }
        (S::List(item) | S::PartialList(item, _), D::List(items)) => {
            let stored_item = match stored { S::List(i) | S::PartialList(i, _) => *i, _ => *item };
            O::List(items.iter().map(|d| encode(item, stored_item, d)).collect::<R<_>>()?)
        }
        (S::Map(_, value), D::Map(entries)) => {
            let stored_value = match stored { S::Map(_, v) => *v, _ => *value };
            O::Map(entries.iter().map(|(k, d)| Ok((k.clone(), encode(value, stored_value, d)?))).collect::<R<_>>()?)
        }
        (S::Dispatch(cases), D::Variant(tag, values)) => {
            let (name, fields) = cases[*tag as usize];
            let mut entries = Vec::new();
            record_entries(fields, fields, values, &mut entries)?;
            // DFU's dispatch writes the case's fields, then the type.
            entries.push(("type".to_owned(), O::Str(format!("minecraft:{name}"))));
            O::Map(entries)
        }
        (_, D::Slot(i)) | (_, D::Holders(i, _)) => O::Slot(*i),
        (_, other) => return Err(format!("cannot encode {other:?}")),
    })
}

fn unvalidated(s: &S) -> &S {
    match s {
        S::Validated(inner, _) => inner,
        other => other,
    }
}

/// The order `RecordCodecBuilder` writes a group of N members: its
/// applicative nesting regroups larger records (measured from DFU 8.0.16).
const GROUP_ORDER: [&[usize]; 17] = [
    &[],
    &[0],
    &[0, 1],
    &[0, 1, 2],
    &[0, 1, 2, 3],
    &[2, 3, 4, 0, 1],
    &[3, 4, 5, 0, 1, 2],
    &[3, 4, 5, 6, 0, 1, 2],
    &[4, 5, 6, 7, 0, 1, 2, 3],
    &[6, 7, 8, 4, 5, 0, 1, 2, 3],
    &[7, 8, 9, 5, 6, 2, 3, 4, 0, 1],
    &[8, 9, 10, 5, 6, 7, 2, 3, 4, 0, 1],
    &[9, 10, 11, 6, 7, 8, 3, 4, 5, 0, 1, 2],
    &[9, 10, 11, 12, 6, 7, 8, 3, 4, 5, 0, 1, 2],
    &[10, 11, 12, 13, 7, 8, 9, 3, 4, 5, 6, 0, 1, 2],
    &[11, 12, 13, 14, 7, 8, 9, 10, 3, 4, 5, 6, 0, 1, 2],
    &[12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3],
];

fn record_entries(target: &[F], stored: &[F], values: &[D], out: &mut Vec<(String, O)>) -> R<()> {
    let order = GROUP_ORDER.get(target.len()).ok_or_else(|| format!("record of {} members", target.len()))?;
    for field in order.iter().map(|&i| &target[i]) {
        let index = stored.iter().position(|s| s.name == field.name).ok_or_else(|| format!("field {} is not stored", field.name))?;
        let (stored_field, value) = (&stored[index], &values[index]);
        match field.presence {
            P::Inline => {
                let (S::Record(t), S::Record(s), D::Record(v)) = (field.schema, stored_field.schema, value) else {
                    return Err(format!("inline field {} mismatch", field.name));
                };
                record_entries(t, s, v, out)?;
            }
            P::Optional | P::Lenient if *value == D::Absent => {}
            P::Default(d) if *value == d.to_d() => {}
            P::JavaDefault => {
                let (D::Slot(i) | D::Holders(i, _)) = value else { return Err(format!("{} is not a slot", field.name)) };
                out.push((field.name.to_owned(), O::SlotUnlessDefault(*i)));
            }
            _ => out.push((field.name.to_owned(), encode(field.schema, stored_field.schema, value)?)),
        }
    }
    Ok(())
}

/// An encoded value as an input node; delegated slots become placeholder
/// strings, which delegates accept unseen.
pub fn to_node(o: &O) -> Node {
    Node::new(match o {
        O::Bool(v) => Value::Bool(*v),
        O::Int(v) => Value::Number(Num::Int(*v)),
        O::Float(v) => Value::Number(Num::Float(*v)),
        O::Double(v) => Value::Number(Num::Double(*v)),
        O::Str(s) | O::SoundRef(s) => Value::String(s.clone()),
        O::List(items) => Value::List(items.iter().map(to_node).collect::<Vec<_>>().into()),
        O::Map(entries) => Value::Map(entries.iter().filter(|(_, v)| !matches!(v, O::SlotUnlessDefault(_))).map(|(k, v)| (k.clone(), to_node(v))).collect::<Vec<_>>().into()),
        O::Slot(_) | O::SlotUnlessDefault(_) => Value::String("<delegated>".into()),
    })
}
