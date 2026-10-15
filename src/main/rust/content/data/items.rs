//! Item stacks (`ItemStack.MAP_CODEC`, `SINGLE_ITEM_CODEC`) and their data
//! component patches (`DataComponentPatch.CODEC`), field for field as the
//! Java codecs. Item IDs and component types come from the Rust catalogs.
//!
//! A component's value is decoded here when its codec is native
//! ([`component_schema`]); otherwise Java's codec runs while Rust decodes
//! (`schema::java_delegate`), so errors and partial results compose as DFU's.
//!
//! Patch form: `Map([(type path, value)])` in input order, a removal's value
//! `Absent`. Entries for one type merge as Java's patch map does: the first
//! position, the last value.

use std::collections::HashMap;
use std::sync::OnceLock;

use super::schema::{self, f, Custom, Cx, Dv, Fail, D, DR, O, P, R, S};
use super::text::COMPONENT;
use super::value::{Node, Value};
use crate::content::tags::Location;

use P::{Default, OrElse, Required};

/// `ItemStack.MAP_CODEC` / `CODEC`.
pub static ITEM_STACK: S = S::Record(&[
    f("id", &ITEM, Required),
    f("count", &S::IntRange(1, 99), OrElse(Dv::Int(1))),
    f("components", &PATCH, Default(Dv::EmptyMap)),
]);

/// `ItemStackWithSlot.CODEC`: `Slot` (an unsigned byte, else 0) and the stack's fields.
pub static ITEM_STACK_WITH_SLOT: S = S::Record(&[f("Slot", &S::UnsignedByte, OrElse(Dv::Int(0))), f("stack", &ITEM_STACK, P::Inline)]);

/// `ItemStack.SINGLE_ITEM_CODEC`: count 1.
pub static SINGLE_ITEM: S = S::Record(&[f("id", &ITEM, Required), f("components", &PATCH, Default(Dv::EmptyMap))]);

/// Item paths to registry IDs (the catalog's order), and full IDs by ID.
fn items() -> &'static (HashMap<&'static str, i32>, Vec<String>) {
    static ITEMS: OnceLock<(HashMap<&'static str, i32>, Vec<String>)> = OnceLock::new();
    ITEMS.get_or_init(|| {
        let defs = crate::content::item::definitions();
        (defs.iter().enumerate().map(|(i, d)| (d.name, i as i32)).collect(), defs.iter().map(|d| format!("minecraft:{}", d.name)).collect())
    })
}

/// `Item.CODEC`: a registered item other than air, as its registry ID.
pub static ITEM: S = S::Custom(&Custom {
    decode: |node, cx| {
        let Value::String(text) = &node.value else { return Err(schema::not_msg("string", node, cx.mode).into()) };
        let (ids, _) = items();
        // The common spelling skips parsing; anything else parses as `ResourceLocation`.
        let id = match text.strip_prefix("minecraft:").and_then(|path| ids.get(path)) {
            Some(id) => *id,
            None => {
                let location = Location::parse(text)?;
                match (&*location.namespace == "minecraft").then(|| ids.get(&*location.path)).flatten() {
                    Some(id) => *id,
                    None => return Err(format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: {location}").into()),
                }
            }
        };
        if id == AIR {
            return Err("Item must not be minecraft:air".into());
        }
        Ok(D::Int(id))
    },
    encode: |d| match d {
        D::Int(id) => items().1.get(*id as usize).map(|n| O::Str(std::borrow::Cow::Borrowed(n.as_str()))).ok_or_else(|| format!("Unknown item {id}")),
        other => Err(format!("not an item: {other:?}")),
    },
    // `validate` runs on encode too.
    check: |d| match d {
        D::Int(AIR) => Err("Item must not be minecraft:air".into()),
        _ => Ok(()),
    },
});

/// `minecraft:air`'s registry ID.
const AIR: i32 = 0;

fn component_type(path: &str) -> Option<&'static crate::content::component::ComponentTypeDef> {
    crate::content::component::definitions().iter().find(|d| d.name == path)
}

/// `Unit.CODEC`: any map, encoded empty.
static UNIT: S = S::Record(&[]);

/// `BuiltInRegistries.ITEM.byNameCodec()`: any registered item (air too).
static ITEM_ANY: S = S::Custom(&Custom {
    decode: |node, cx| {
        let Value::String(text) = &node.value else { return Err(schema::not_msg("string", node, cx.mode).into()) };
        let location = Location::parse(text)?;
        match (&*location.namespace == "minecraft").then(|| items().0.get(&*location.path)).flatten() {
            Some(id) => Ok(D::Int(*id)),
            None => Err(format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: {location}").into()),
        }
    },
    encode: |d| match d {
        D::Int(id) => items().1.get(*id as usize).map(|n| O::Str(std::borrow::Cow::Borrowed(n.as_str()))).ok_or_else(|| format!("Unknown item {id}")),
        other => Err(format!("not an item: {other:?}")),
    },
    check: |_| Ok(()),
});

/// `DataComponentType.CODEC`: any registered component type, by path.
static COMPONENT_TYPE: S = S::Custom(&Custom {
    decode: |node, cx| {
        let Value::String(text) = &node.value else { return Err(schema::not_msg("string", node, cx.mode).into()) };
        let location = Location::parse(text)?;
        match (&*location.namespace == "minecraft").then(|| component_type(&location.path)).flatten() {
            Some(def) => Ok(D::Str(def.name.to_owned())),
            None => Err(format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:data_component_type]: {location}").into()),
        }
    },
    encode: |d| match d {
        D::Str(path) => patch_keys(path).map(|k| O::Str(std::borrow::Cow::Borrowed(k.0.as_str()))).ok_or_else(|| format!("Unknown component {path}")),
        other => Err(format!("not a component type: {other:?}")),
    },
    check: |_| Ok(()),
});

/// `DyeColor.CODEC`.
pub static DYE_COLOR: S = S::Enum(&[
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
]);

/// `GlobalPos.CODEC`.
static GLOBAL_POS: S = S::Record(&[f("dimension", &S::Location, Required), f("pos", &super::values::BLOCK_POS, Required)]);

/// `Filterable.codec(Codec.string(0, 1024))`: `{raw, filtered}`, or just the raw string.
static PAGE_1024: S = S::Normalize(
    &S::Either(&S::Record(&[f("raw", &S::SizedStr(0, 1024), Required), f("filtered", &S::SizedStr(0, 1024), P::Optional)]), &S::SizedStr(0, 1024)),
    |d| match d {
        D::Variant(1, mut v) => D::Variant(0, vec![D::Record(vec![v.pop().unwrap_or(D::Absent), D::Absent])]),
        other => other,
    },
);

static FIREWORK_EXPLOSION: S = S::Record(&[
    f("shape", &S::Enum(&["small_ball", "large_ball", "star", "creeper", "burst"]), Required),
    f("colors", &S::List(&S::Int), Default(Dv::EmptyList)),
    f("fade_colors", &S::List(&S::Int), Default(Dv::EmptyList)),
    f("has_trail", &S::Bool, Default(Dv::Bool(false))),
    f("has_twinkle", &S::Bool, Default(Dv::Bool(false))),
]);

/// `ItemContainerContents.CODEC`: slots in index order, one per index (the
/// last given wins), as `fromSlots` then `asSlots` leave them.
static CONTAINER: S = S::Normalize(
    &S::SizedList(&S::Record(&[f("slot", &S::DfuIntRange(0, 255), Required), f("item", &ITEM_STACK, Required)]), 0, 256),
    |d| match d {
        D::List(slots) => {
            let mut by_index: Vec<(i32, D)> = Vec::new();
            for slot in slots {
                let D::Record(mut parts) = slot else { continue };
                let item = parts.pop().unwrap_or(D::Absent);
                let D::Int(index) = parts.pop().unwrap_or(D::Absent) else { continue };
                match by_index.iter_mut().find(|(i, _)| *i == index) {
                    Some(entry) => entry.1 = item,
                    None => by_index.push((index, item)),
                }
            }
            by_index.sort_by_key(|(i, _)| *i);
            D::List(by_index.into_iter().map(|(i, item)| D::Record(vec![D::Int(i), item])).collect())
        }
        other => other,
    },
);

/// `PotDecorations.CODEC`: up to four items, always encoded as four (brick for none).
static POT_DECORATIONS: S = S::Normalize(&S::SizedList(&ITEM_ANY, 0, 4), |d| match d {
    D::List(mut items) => {
        let brick = items_brick();
        items.resize(4, D::Int(brick));
        D::List(items)
    }
    other => other,
});

fn items_brick() -> i32 {
    items().0.get("brick").copied().unwrap_or(0)
}

/// Components whose value codecs are native, by type path.
pub fn component_schema(path: &str) -> Option<&'static S> {
    static LORE: S = S::SizedList(&COMPONENT, 0, 256);
    static RARITY: S = S::Enum(&["common", "uncommon", "rare", "epic"]);
    static CUSTOM_MODEL_DATA: S = S::Record(&[
        f("floats", &S::List(&S::Float), Default(Dv::EmptyList)),
        f("flags", &S::List(&S::Bool), Default(Dv::EmptyList)),
        f("strings", &S::List(&S::Str), Default(Dv::EmptyList)),
        f("colors", &S::List(&super::values::RGB_COLOR), Default(Dv::EmptyList)),
    ]);
    static USE_COOLDOWN: S = S::Record(&[f("seconds", &S::PositiveFloat, Required), f("cooldown_group", &S::Location, P::Optional)]);
    static WEAPON: S = S::Record(&[
        f("item_damage_per_attack", &S::IntRange(0, i32::MAX), Default(Dv::Int(1))),
        f("disable_blocking_for_seconds", &S::NonNegativeFloat, Default(Dv::Float(0.0))),
    ]);
    /// The hidden types are a linked set: the first of each type is kept.
    static HIDDEN: S = S::Normalize(&S::List(&COMPONENT_TYPE), |d| match d {
        D::List(types) => {
            let mut unique: Vec<D> = Vec::with_capacity(types.len());
            for t in types {
                if !unique.contains(&t) {
                    unique.push(t);
                }
            }
            D::List(unique)
        }
        other => other,
    });
    static TOOLTIP_DISPLAY: S = S::Record(&[f("hide_tooltip", &S::Bool, Default(Dv::Bool(false))), f("hidden_components", &HIDDEN, Default(Dv::EmptyList))]);
    static LODESTONE: S = S::Record(&[f("target", &GLOBAL_POS, P::Optional), f("tracked", &S::Bool, Default(Dv::Bool(true)))]);
    static CONTAINER_LOOT: S = S::Record(&[f("loot_table", &S::Location, Required), f("seed", &S::Long, Default(Dv::Long(0)))]);
    static FIREWORKS: S = S::Record(&[
        f("flight_duration", &S::UnsignedByte, Default(Dv::Int(0))),
        f("explosions", &S::SizedList(&FIREWORK_EXPLOSION, 0, 256), Default(Dv::EmptyList)),
    ]);
    static WRITABLE_BOOK: S = S::Record(&[f("pages", &S::SizedList(&PAGE_1024, 0, 100), Default(Dv::EmptyList))]);
    static BLOCK_STATE: S = S::Map(&S::Str, &S::Str);
    static ITEM_LIST: S = S::List(&ITEM_STACK);
    Some(match path {
        "max_stack_size" => &S::IntRange(1, 99),
        "max_damage" => &S::IntRange(1, i32::MAX),
        "damage" | "repair_cost" => &S::IntRange(0, i32::MAX),
        "unbreakable" | "intangible_projectile" | "glider" => &UNIT,
        "custom_name" | "item_name" => &COMPONENT,
        "item_model" | "tooltip_style" | "note_block_sound" => &S::Location,
        "lore" => &LORE,
        "rarity" => &RARITY,
        "enchantment_glint_override" => &S::Bool,
        "dyed_color" => &super::values::RGB_COLOR,
        "map_color" | "map_id" => &S::Int,
        "ominous_bottle_amplifier" => &S::IntRange(0, 4),
        "potion_duration_scale" => &S::NonNegativeFloat,
        "base_color" => &DYE_COLOR,
        "custom_model_data" => &CUSTOM_MODEL_DATA,
        "use_cooldown" => &USE_COOLDOWN,
        "weapon" => &WEAPON,
        "tooltip_display" => &TOOLTIP_DISPLAY,
        "lodestone_tracker" => &LODESTONE,
        "container_loot" => &CONTAINER_LOOT,
        "firework_explosion" => &FIREWORK_EXPLOSION,
        "fireworks" => &FIREWORKS,
        "writable_book_content" => &WRITABLE_BOOK,
        "block_state" => &BLOCK_STATE,
        "container" => &CONTAINER,
        "pot_decorations" => &POT_DECORATIONS,
        "charged_projectiles" => &ITEM_LIST,
        "use_remainder" => &ITEM_STACK,
        "break_sound" => &super::item_components::BREAK_SOUND,
        "repairable" => &super::item_components::REPAIRABLE,
        "enchantable" => &super::item_components::ENCHANTABLE,
        "damage_resistant" => &super::item_components::DAMAGE_RESISTANT,
        "trim" => &super::item_components::TRIM,
        "instrument" => &super::item_components::INSTRUMENT,
        "jukebox_playable" => &super::item_components::JUKEBOX_PLAYABLE,
        "provides_trim_material" => &super::item_components::PROVIDES_TRIM_MATERIAL,
        "banner_patterns" => &super::item_components::BANNER_PATTERNS,
        "food" => &super::item_components::FOOD,
        "attribute_modifiers" => &super::item_components::ATTRIBUTE_MODIFIERS,
        "potion_contents" => &super::item_components::POTION_CONTENTS,
        "suspicious_stew_effects" => &super::item_components::SUSPICIOUS_STEW_EFFECTS,
        "written_book_content" => &super::item_components::WRITTEN_BOOK_CONTENT,
        "bees" => &super::item_components::BEES,
        _ => return None,
    })
}

/// Native component paths, for Java to check its projections against.
pub fn native_components() -> Vec<&'static str> {
    crate::content::component::definitions().iter().map(|d| d.name).filter(|n| component_schema(n).is_some()).collect()
}

/// `DataComponentPatch.CODEC` (`Codec.dispatchedMap` of `PatchKey`).
pub static PATCH: S = S::Custom(&Custom { decode: decode_patch, encode: encode_patch, check: check_patch });

/// `PatchKey.CODEC`: `[!]type`, a registered persistent component.
fn patch_key(key: &str) -> R<(&'static str, bool)> {
    let (removed, name) = match key.strip_prefix('!') {
        Some(rest) => (true, rest),
        None => (false, key),
    };
    let location = Location::parse(name).ok();
    let shown = location.as_ref().map_or_else(|| "null".to_owned(), |l| l.to_string());
    let def = location.as_ref().filter(|l| &*l.namespace == "minecraft").and_then(|l| component_type(&l.path));
    match def {
        None => Err(format!("No component with type: '{shown}'")),
        Some(def) if !def.persistent => Err(format!("'{shown}' is not a persistent component")),
        Some(def) => Ok((def.name, removed)),
    }
}

fn decode_value(path: &str, removed: bool, node: &Node, cx: &mut Cx) -> DR {
    if removed {
        // `Codec.EMPTY.codec()`: any map.
        return match &node.value {
            Value::Map(_) => Ok(D::Absent),
            _ => Err(schema::not_msg("map", node, cx.mode).into()),
        };
    }
    if cx.generic_component_ops && super::item_components::is_bulk_component(path) {
        // Reserved component-only tag: distinguishes a complete component from a native
        // schema whose root happens to be a delegated holder slot.
        return schema::map_result(
            schema::java_delegate(schema::JAVA_COMPONENT, 0, &format!("minecraft:{path}"), node, cx),
            |slot| D::Variant(u16::MAX, vec![slot]),
        );
    }
    match component_schema(path) {
        Some(s) => schema::decode(s, node, cx),
        None => schema::java_delegate(schema::JAVA_COMPONENT, 0, &format!("minecraft:{path}"), node, cx),
    }
}

fn decode_patch(node: &Node, cx: &mut Cx) -> DR {
    let mode = cx.mode;
    let Value::Map(members) = &node.value else { return Err(schema::not_msg("map", node, mode).into()) };
    // Entries by `PatchKey` (type and removal), as DFU's map holds them.
    let mut entries: Vec<(&'static str, bool, D)> = Vec::new();
    let mut errors: Vec<String> = Vec::new();
    let mut failed: Vec<(String, Node)> = Vec::new();
    for (key, value) in members.iter() {
        match patch_key(key) {
            // `apply2stable` of the key's error with the value's (the same error).
            Err(msg) => {
                errors.push(format!("{msg}; {msg}"));
                failed.push((key.clone(), value.clone()));
            }
            Ok((path, removed)) => match decode_value(path, removed, value, cx) {
                Ok(d) => entries.push((path, removed, d)),
                Err(Fail { msg, partial }) => {
                    errors.push(msg);
                    failed.push((key.clone(), value.clone()));
                    if let Some(p) = partial {
                        entries.push((path, removed, p));
                    }
                }
            },
        }
    }
    // The patch map: one entry per type, first position, last value.
    let mut patch: Vec<(String, D)> = Vec::new();
    for (path, removed, d) in entries {
        let value = if removed { D::Absent } else { d };
        match patch.iter_mut().find(|(p, _)| p == path) {
            Some(entry) => entry.1 = value,
            None => patch.push((path.to_owned(), value)),
        }
    }
    let patch = D::Map(patch);
    if errors.is_empty() {
        return Ok(patch);
    }
    let msg = errors.iter().rev().map(String::as_str).collect::<Vec<_>>().join("; ");
    let missed = Node::new(Value::Map(failed.into()));
    Err(Fail { msg: format!("{msg} missed input: {}", missed.show(mode)), partial: Some(patch) })
}

/// Patch keys by component path: set and removed spellings.
fn patch_keys(path: &str) -> Option<&'static (String, String)> {
    static KEYS: OnceLock<HashMap<&'static str, (String, String)>> = OnceLock::new();
    KEYS.get_or_init(|| {
        crate::content::component::definitions().iter().map(|d| (d.name, (format!("minecraft:{}", d.name), format!("!minecraft:{}", d.name)))).collect()
    })
    .get(path)
}

fn encode_patch(d: &D) -> R<O<'_>> {
    let D::Map(entries) = d else { return Err("not a component patch".into()) };
    let mut out = Vec::new();
    for (path, value) in entries {
        let value = complete_component_slot(path, value);
        match value {
            D::Absent => out.push((std::borrow::Cow::Borrowed(patch_keys(path).ok_or("unknown component")?.1.as_str()), O::Map(Vec::new()))),
            value => {
                let o = match (component_schema(path), value) {
                    // Java's (cached) codec encodes this one.
                    (_, D::Slot(i)) => O::Slot(*i),
                    (Some(s), value) => schema::encode(s, s, value)?,
                    (None, other) => return Err(format!("component {path} is not native: {other:?}")),
                };
                out.push((std::borrow::Cow::Borrowed(patch_keys(path).ok_or("unknown component")?.0.as_str()), o));
            }
        }
    }
    Ok(O::Map(out))
}

fn check_patch(d: &D) -> R<()> {
    let D::Map(entries) = d else { return Ok(()) };
    for (path, value) in entries {
        let value = complete_component_slot(path, value);
        if let (Some(s), false) = (component_schema(path), matches!(value, D::Absent | D::Slot(_))) {
            schema::check(s, value)?;
        }
    }
    Ok(())
}

/// Stored definitions retain the generic-ops callback marker too; both
/// checking and encoding must use its complete component slot.
fn complete_component_slot<'a>(path: &str, value: &'a D) -> &'a D {
    match value {
        D::Variant(u16::MAX, fields) if super::item_components::is_bulk_component(path) => &fields[0],
        other => other,
    }
}

