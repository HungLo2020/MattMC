//! Persistent item component structures. Registry holders, restricted book
//! text and typed entity NBT remain original Java codec leaves; the same
//! schemas serve item patches, stacks, nested containers and hover items.
//! Standalone codecs and arbitrary DynamicOps retain the original Java codecs.

use std::borrow::Cow;
use super::schema::{self, f, Custom, D, Dv, O, P, S};
use super::value::Value;
use crate::content::tags::Location;

pub fn is_bulk_component(path: &str) -> bool {
    matches!(path, "break_sound" | "repairable" | "enchantable" | "damage_resistant" | "trim"
        | "instrument" | "jukebox_playable" | "provides_trim_material" | "banner_patterns"
        | "food" | "attribute_modifiers" | "potion_contents" | "suspicious_stew_effects"
        | "written_book_content" | "bees")
}

// NativeValues.JavaCodec ordinals; append new leaves without moving old ones.
pub const TRIM_MATERIAL: u16 = 25;
pub const TRIM_PATTERN: u16 = 26;
pub const INSTRUMENT_HOLDER: u16 = 27;
pub const JUKEBOX_HOLDER: u16 = 28;
pub const PROVIDED_TRIM_MATERIAL: u16 = 29;
pub const BANNER_PATTERN: u16 = 30;
pub const MOB_EFFECT: u16 = 31;
pub const POTION_HOLDER: u16 = 32;
pub const MOB_EFFECT_INSTANCE: u16 = 33;
pub const BOOK_PAGE: u16 = 34;
pub const BEE_ENTITY_DATA: u16 = 35;
pub const ITEM_ATTRIBUTE_DISPLAY: u16 = 36;

pub static BREAK_SOUND: S = S::Java(super::enchant::SOUND_EVENT);
pub static REPAIRABLE: S = S::Record(&[f("items", &S::Java(super::registries::ITEMS), P::Required)]);
pub static ENCHANTABLE: S = S::Record(&[f("value", &S::IntRange(1, i32::MAX), P::Required)]);

static DAMAGE_TAG: S = S::Custom(&Custom {
    decode: |node, cx| {
        let Value::String(text) = &node.value else { return Err(schema::not_msg("string", node, cx.mode).into()) };
        let id = text.strip_prefix('#').ok_or("Not a tag id")?;
        Ok(D::Str(Location::parse(id)?.to_string()))
    },
    encode: |value| match value {
        D::Str(id) => Ok(O::Str(Cow::Owned(format!("#{id}")))),
        other => Err(format!("not a damage tag: {other:?}")),
    },
    check: |_| Ok(()),
});
pub static DAMAGE_RESISTANT: S = S::Record(&[f("types", &DAMAGE_TAG, P::Required)]);
pub static TRIM: S = S::Record(&[
    f("material", &S::Java(TRIM_MATERIAL), P::Required),
    f("pattern", &S::Java(TRIM_PATTERN), P::Required),
]);
// These three components wrap an EitherHolder; its original codec retains
// holder lookup and bootstrap-only unresolved-key semantics.
pub static INSTRUMENT: S = S::Java(INSTRUMENT_HOLDER);
pub static JUKEBOX_PLAYABLE: S = S::Java(JUKEBOX_HOLDER);
pub static PROVIDES_TRIM_MATERIAL: S = S::Java(PROVIDED_TRIM_MATERIAL);
pub static BANNER_PATTERNS: S = S::List(&S::Record(&[
    f("pattern", &S::Java(BANNER_PATTERN), P::Required),
    f("color", &super::items::DYE_COLOR, P::Required),
]));
pub static FOOD: S = S::Record(&[
    f("nutrition", &S::IntRange(0, i32::MAX), P::Required),
    f("saturation", &S::Float, P::Required),
    f("can_always_eat", &S::Bool, P::Default(Dv::Bool(false))),
]);
static ATTRIBUTE_MODIFIER: S = S::Record(&[
    f("id", &S::Location, P::Required),
    f("amount", &S::Double, P::Required),
    f("operation", &S::Enum(&["add_value", "add_multiplied_base", "add_multiplied_total"]), P::Required),
]);
pub static ATTRIBUTE_MODIFIERS: S = S::List(&S::Record(&[
    f("type", &S::Java(super::enchant::ATTRIBUTE), P::Required),
    f("modifier", &ATTRIBUTE_MODIFIER, P::Inline),
    f("slot", &S::Enum(super::registries::SLOT_GROUPS), P::Default(Dv::Name("any"))),
    f("display", &S::Java(ITEM_ATTRIBUTE_DISPLAY), P::JavaDefault),
]));
static POTION_FULL: S = S::Record(&[
    f("potion", &S::Java(POTION_HOLDER), P::Optional),
    f("custom_color", &S::Int, P::Optional),
    f("custom_effects", &S::List(&S::Java(MOB_EFFECT_INSTANCE)), P::Default(Dv::EmptyList)),
    f("custom_name", &S::Str, P::Optional),
]);
pub static POTION_CONTENTS: S = S::Normalize(&S::Either(&POTION_FULL, &S::Java(POTION_HOLDER)), |value| match value {
    D::Variant(1, mut fields) => D::Variant(0, vec![D::Record(vec![
        fields.pop().unwrap_or(D::Absent), D::Absent, D::List(Vec::new()), D::Absent,
    ])]),
    other => other,
});
pub static SUSPICIOUS_STEW_EFFECTS: S = S::List(&S::Record(&[
    f("id", &S::Java(MOB_EFFECT), P::Required),
    f("duration", &S::Int, P::LenientDefault(Dv::Int(160))),
]));

// Filterable accepts {raw, filtered}, or the raw value, and always encodes
// the record form. The restricted component codec stays an original leaf:
// its size check encodes the decoded component through the caller's ops.
fn filterable(value: D) -> D {
    match value {
        D::Variant(1, mut fields) => D::Variant(0, vec![D::Record(vec![fields.pop().unwrap_or(D::Absent), D::Absent])]),
        other => other,
    }
}
static TITLE: S = S::Normalize(&S::Either(&S::Record(&[
    f("raw", &S::SizedStr(0, 32), P::Required),
    f("filtered", &S::SizedStr(0, 32), P::Optional),
]), &S::SizedStr(0, 32)), filterable);
static PAGE: S = S::Normalize(&S::Either(&S::Record(&[
    f("raw", &S::Java(BOOK_PAGE), P::Required),
    f("filtered", &S::Java(BOOK_PAGE), P::Optional),
]), &S::Java(BOOK_PAGE)), filterable);
pub static WRITTEN_BOOK_CONTENT: S = S::Record(&[
    f("title", &TITLE, P::Required),
    f("author", &S::Str, P::Required),
    f("generation", &S::IntRange(0, 3), P::Default(Dv::Int(0))),
    f("pages", &S::List(&PAGE), P::Default(Dv::EmptyList)),
    f("resolved", &S::Bool, P::Default(Dv::Bool(false))),
]);
pub static BEES: S = S::List(&S::Record(&[
    f("entity_data", &S::Java(BEE_ENTITY_DATA), P::Required),
    f("ticks_in_hive", &S::Int, P::Required),
    f("min_ticks_in_hive", &S::Int, P::Required),
]));
