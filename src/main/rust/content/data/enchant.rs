//! Enchantment effect definitions (`EnchantmentEffectComponents`): the
//! `effects` map of an enchantment and every effect it holds. Rust owns the
//! schemas, validation and encoding; values Java still owns (loot conditions
//! with their context validation, registry holders Rust doesn't resolve,
//! block state providers and predicates, explosion particles, holder sets)
//! run their Java codecs synchronously, so errors and partials compose as
//! before.

use std::sync::OnceLock;

use super::schema::{self, case, f, Custom, Cx, Dv, Fail, D, DR, O, P::*, R, S};
use super::value::{java_float, Node, Value};
use crate::content::tags::Location;
use super::values::{BLOCK_POS, FLOAT_PROVIDER, LEVEL_BASED_VALUE, PARTICLE, VEC3};

/// `NativeValues.JavaCodec` ordinals for codecs Java owns here.
pub const CONDITION_ENCHANTED_DAMAGE: u16 = 3;
pub const CONDITION_ENCHANTED_ITEM: u16 = 4;
pub const CONDITION_ENCHANTED_ENTITY: u16 = 5;
pub const CONDITION_ENCHANTED_LOCATION: u16 = 6;
pub const CONDITION_HIT_BLOCK: u16 = 7;
pub const DAMAGE_TYPE: u16 = 8;
pub const ATTRIBUTE: u16 = 9;
pub const GAME_EVENT: u16 = 10;
pub const BLOCK_STATE_PROVIDER: u16 = 11;
pub const BLOCK_PREDICATE: u16 = 12;
pub const EXPLOSION_PARTICLES: u16 = 13;
pub const MOB_EFFECTS: u16 = 14;
pub const BLOCKS: u16 = 15;
pub const ENTITY_TYPES: u16 = 16;
pub const SOUND_EVENT: u16 = 17;

const LBV: &S = &LEVEL_BASED_VALUE;
/// `SoundEvent.CODEC` (a registry file codec): Java resolves references while decoding.
static SOUND: S = S::Java(SOUND_EVENT);

// `FloatProvider.codec(min, max)`: the provider, then its range.
static VOLUME: S = S::Validated(&FLOAT_PROVIDER, |d| float_range(d, 1.0E-5, 10.0));
static PITCH: S = S::Validated(&FLOAT_PROVIDER, |d| float_range(d, 1.0E-5, 2.0));

fn float_range(d: &D, min: f32, max: f32) -> R<()> {
    let Some((lo, hi)) = super::values::float_bounds(d) else { return Ok(()) };
    let show = java_float;
    if lo < min {
        return Err(format!("Value provider too low: {} [{}-{}]", show(min), show(lo), show(hi)));
    }
    if hi > max {
        return Err(format!("Value provider too high: {} [{}-{}]", show(max), show(lo), show(hi)));
    }
    Ok(())
}

/// `BlockItemStateProperties.CODEC`: property names to values.
static BLOCK_ITEM_STATE_PROPERTIES: S = S::Map(&S::Str, &S::Str);
static EXPLOSION_INTERACTION: S = S::Enum(&["none", "block", "mob", "tnt", "trigger"]);
static OPERATION: S = S::Enum(&["add_value", "add_multiplied_base", "add_multiplied_total"]);
static TARGET: S = S::Enum(&["attacker", "damaging_entity", "victim"]);
static POSITION_SOURCE: S = S::Record(&[
    f("type", &S::Enum(&["entity_position", "in_bounding_box"]), Required),
    f("offset", &S::Float, Default(Dv::Float(0.0))),
    f("scale", &S::PositiveFloat, Default(Dv::Float(1.0))),
]);
static VELOCITY_SOURCE: S = S::Record(&[
    f("movement_scale", &S::Float, Default(Dv::Float(0.0))),
    f("base", &FLOAT_PROVIDER, Default(Dv::Variant0Float(0.0))),
]);

/// Fields shared by the entity and location effects, in registry order
/// without `attribute` (location effects add it third).
macro_rules! shared_effects {
    ($all_of:expr) => {
        [
            case("all_of", &[f("effects", $all_of, Required)]),
            case("apply_mob_effect", &[
                f("to_apply", &S::Java(MOB_EFFECTS), Required),
                f("min_duration", LBV, Required),
                f("max_duration", LBV, Required),
                f("min_amplifier", LBV, Required),
                f("max_amplifier", LBV, Required),
            ]),
            case("change_item_damage", &[f("amount", LBV, Required)]),
            case("damage_entity", &[
                f("min_damage", LBV, Required),
                f("max_damage", LBV, Required),
                f("damage_type", &S::Java(DAMAGE_TYPE), Required),
            ]),
            case("explode", &[
                f("attribute_to_user", &S::Bool, Default(Dv::Bool(false))),
                f("damage_type", &S::Java(DAMAGE_TYPE), Optional),
                f("knockback_multiplier", LBV, Optional),
                f("immune_blocks", &S::Java(BLOCKS), Optional),
                f("offset", &VEC3, Default(Dv::ZeroDoubles3)),
                f("radius", LBV, Required),
                f("create_fire", &S::Bool, Default(Dv::Bool(false))),
                f("block_interaction", &EXPLOSION_INTERACTION, Required),
                f("small_particle", &PARTICLE, Required),
                f("large_particle", &PARTICLE, Required),
                f("block_particles", &S::Java(EXPLOSION_PARTICLES), JavaDefault),
                f("sound", &SOUND, Required),
            ]),
            case("ignite", &[f("duration", LBV, Required)]),
            case("play_sound", &[f("sound", &SOUND, Required), f("volume", &VOLUME, Required), f("pitch", &PITCH, Required)]),
            case("replace_block", &[
                f("offset", &BLOCK_POS, Default(Dv::ZeroInts3)),
                f("predicate", &S::Java(BLOCK_PREDICATE), Optional),
                f("block_state", &S::Java(BLOCK_STATE_PROVIDER), Required),
                f("trigger_game_event", &S::Java(GAME_EVENT), Optional),
            ]),
            case("replace_disk", &[
                f("radius", LBV, Required),
                f("height", LBV, Required),
                f("offset", &BLOCK_POS, Default(Dv::ZeroInts3)),
                f("predicate", &S::Java(BLOCK_PREDICATE), Optional),
                f("block_state", &S::Java(BLOCK_STATE_PROVIDER), Required),
                f("trigger_game_event", &S::Java(GAME_EVENT), Optional),
            ]),
            case("run_function", &[f("function", &S::Location, Required)]),
            case("set_block_properties", &[
                f("properties", &BLOCK_ITEM_STATE_PROPERTIES, Required),
                f("offset", &BLOCK_POS, Default(Dv::ZeroInts3)),
                f("trigger_game_event", &S::Java(GAME_EVENT), Optional),
            ]),
            case("spawn_particles", &[
                f("particle", &PARTICLE, Required),
                f("horizontal_position", &POSITION_SOURCE, Required),
                f("vertical_position", &POSITION_SOURCE, Required),
                f("horizontal_velocity", &VELOCITY_SOURCE, Required),
                f("vertical_velocity", &VELOCITY_SOURCE, Required),
                f("speed", &FLOAT_PROVIDER, Default(Dv::Variant0Float(0.0))),
            ]),
            case("summon_entity", &[f("entity", &S::Java(ENTITY_TYPES), Required), f("join_team", &S::Bool, Default(Dv::Bool(false)))]),
        ]
    };
}

/// `EnchantmentValueEffect.CODEC`, in `enchantment_value_effect_type` registry order.
pub static VALUE_EFFECT: S = S::Dispatch("enchantment_value_effect_type", &[
    case("add", &[f("value", LBV, Required)]),
    case("all_of", &[f("effects", &S::List(&VALUE_EFFECT), Required)]),
    case("multiply", &[f("factor", LBV, Required)]),
    case("remove_binomial", &[f("chance", LBV, Required)]),
    case("set", &[f("value", LBV, Required)]),
]);

static ENTITY_CASES: [schema::Case; 13] = shared_effects!(&S::List(&ENTITY_EFFECT));
/// `EnchantmentEntityEffect.CODEC`, in registry order.
pub static ENTITY_EFFECT: S = S::Dispatch("enchantment_entity_effect_type", &ENTITY_CASES);

static ATTRIBUTE_FIELDS: [schema::F; 4] = [
    f("id", &S::Location, Required),
    f("attribute", &S::Java(ATTRIBUTE), Required),
    f("amount", LBV, Required),
    f("operation", &OPERATION, Required),
];
/// `EnchantmentAttributeEffect.CODEC` as a record (the `attributes` component).
pub static ATTRIBUTE_EFFECT: S = S::Record(&ATTRIBUTE_FIELDS);

static LOCATION_CASES: [schema::Case; 14] = {
    let shared: [schema::Case; 13] = shared_effects!(&S::List(&LOCATION_EFFECT));
    let [all_of, apply, change, damage, explode, ignite, sound, replace, disk, function, properties, particles, summon] = shared;
    [all_of, apply, case("attribute", &ATTRIBUTE_FIELDS), change, damage, explode, ignite, sound, replace, disk, function, properties, particles, summon]
};
/// `EnchantmentLocationBasedEffect.CODEC`, in registry order.
pub static LOCATION_EFFECT: S = S::Dispatch("enchantment_location_based_effect_type", &LOCATION_CASES);

/// `ConditionalEffect.codec(effect, params)`.
macro_rules! conditional {
    ($effect:expr, $condition:expr) => {
        S::Record(&[f("effect", $effect, Required), f("requirements", &S::Java($condition), Optional)])
    };
}

/// `TargetedConditionalEffect.codec(effect, params)`.
macro_rules! targeted {
    ($effect:expr, $condition:expr) => {
        S::Record(&[
            f("enchanted", &TARGET, Required),
            f("affected", &TARGET, Required),
            f("effect", $effect, Required),
            f("requirements", &S::Java($condition), Optional),
        ])
    };
}

static DAMAGE_VALUE: S = S::List(&conditional!(&VALUE_EFFECT, CONDITION_ENCHANTED_DAMAGE));
static ITEM_VALUE: S = S::List(&conditional!(&VALUE_EFFECT, CONDITION_ENCHANTED_ITEM));
static ENTITY_VALUE: S = S::List(&conditional!(&VALUE_EFFECT, CONDITION_ENCHANTED_ENTITY));
static DAMAGE_IMMUNITY: S = S::List(&conditional!(&UNIT, CONDITION_ENCHANTED_DAMAGE));
static POST_ATTACK: S = S::List(&targeted!(&ENTITY_EFFECT, CONDITION_ENCHANTED_DAMAGE));
static HIT_BLOCK: S = S::List(&conditional!(&ENTITY_EFFECT, CONDITION_HIT_BLOCK));
static ENTITY_ENTITY: S = S::List(&conditional!(&ENTITY_EFFECT, CONDITION_ENCHANTED_ENTITY));
static LOCATION_CHANGED: S = S::List(&conditional!(&LOCATION_EFFECT, CONDITION_ENCHANTED_LOCATION));
static ATTRIBUTES: S = S::List(&ATTRIBUTE_EFFECT);
/// `TargetedConditionalEffect.equipmentDropsCodec`: `enchanted` is attacker
/// or victim; `affected` is always the victim (not read or written).
static EQUIPMENT_DROPS: S = S::List(&S::Record(&[
    f("enchanted", &S::Validated(&TARGET, |d| match d {
        D::Str(s) if s == "damaging_entity" => Err("enchanted must be attacker or victim".into()),
        _ => Ok(()),
    }), Required),
    f("effect", &VALUE_EFFECT, Required),
    f("requirements", &S::Java(CONDITION_ENCHANTED_DAMAGE), Optional),
]));
static CHARGING_SOUNDS: S = S::List(&S::Record(&[f("start", &SOUND, Optional), f("mid", &SOUND, Optional), f("end", &SOUND, Optional)]));
static TRIDENT_SOUND: S = S::List(&SOUND);
/// `Unit.CODEC` and `DamageImmunity.CODEC` (`Codec.unit`): any input, `{}` out.
static UNIT: S = S::Record(&[]);

/// Effect component schemas by path, in `EnchantmentEffectComponents` order.
pub static COMPONENTS: &[(&str, &S)] = &[
    ("damage_protection", &DAMAGE_VALUE),
    ("damage_immunity", &DAMAGE_IMMUNITY),
    ("damage", &DAMAGE_VALUE),
    ("smash_damage_per_fallen_block", &DAMAGE_VALUE),
    ("knockback", &DAMAGE_VALUE),
    ("armor_effectiveness", &DAMAGE_VALUE),
    ("post_attack", &POST_ATTACK),
    ("hit_block", &HIT_BLOCK),
    ("item_damage", &ITEM_VALUE),
    ("attributes", &ATTRIBUTES),
    ("equipment_drops", &EQUIPMENT_DROPS),
    ("location_changed", &LOCATION_CHANGED),
    ("tick", &ENTITY_ENTITY),
    ("ammo_use", &ITEM_VALUE),
    ("projectile_piercing", &ITEM_VALUE),
    ("projectile_spawned", &ENTITY_ENTITY),
    ("projectile_spread", &ENTITY_VALUE),
    ("projectile_count", &ENTITY_VALUE),
    ("trident_return_acceleration", &ENTITY_VALUE),
    ("fishing_time_reduction", &ENTITY_VALUE),
    ("fishing_luck_bonus", &ENTITY_VALUE),
    ("block_experience", &ITEM_VALUE),
    ("mob_experience", &ENTITY_VALUE),
    ("repair_with_xp", &ITEM_VALUE),
    ("crossbow_charge_time", &VALUE_EFFECT),
    ("crossbow_charging_sounds", &CHARGING_SOUNDS),
    ("trident_sound", &TRIDENT_SOUND),
    ("prevent_equipment_drop", &UNIT),
    ("prevent_armor_change", &UNIT),
    ("trident_spin_attack_strength", &VALUE_EFFECT),
];

fn component(path: &str) -> Option<&'static S> {
    COMPONENTS.iter().find(|(p, _)| *p == path).map(|(_, s)| *s)
}

/// The effects map: `DataComponentMap.makeCodec(COMPONENT_CODEC)`, a
/// `Codec.dispatchedMap` keyed by `enchantment_effect_component_type`.
pub static EFFECTS: S = S::Custom(&Custom { decode: decode_effects, encode: encode_effects, check: check_effects });

/// `Registry.byNameCodec()` of the component type registry.
fn effect_key(key: &str) -> R<&'static str> {
    let location = Location::parse(key)?;
    let found = (&*location.namespace == "minecraft").then(|| COMPONENTS.iter().find(|(p, _)| *p == &*location.path)).flatten();
    found.map(|(p, _)| *p).ok_or_else(|| format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_effect_component_type]: {location}"))
}

fn decode_effects(node: &Node, cx: &mut Cx) -> DR {
    let mode = cx.mode;
    let Value::Map(members) = &node.value else { return Err(schema::not_msg("map", node, mode).into()) };
    let mut entries: Vec<(String, D)> = Vec::new();
    let mut errors: Vec<String> = Vec::new();
    let mut failed: Vec<(String, Node)> = Vec::new();
    for (key, value) in members.iter() {
        match effect_key(key) {
            Err(msg) => {
                errors.push(format!("{msg}; {msg}"));
                failed.push((key.clone(), value.clone()));
            }
            Ok(path) => {
                let s = component(path).expect("listed component");
                match schema::decode(s, value, cx) {
                    Ok(d) => entries.push((path.to_owned(), d)),
                    Err(Fail { msg, partial }) => {
                        errors.push(msg);
                        failed.push((key.clone(), value.clone()));
                        if let Some(p) = partial {
                            entries.push((path.to_owned(), p));
                        }
                    }
                }
            }
        }
    }
    // One entry per type: the first position, the last value.
    let mut map: Vec<(String, D)> = Vec::new();
    for (path, d) in entries {
        match map.iter_mut().find(|(p, _)| *p == path) {
            Some(entry) => entry.1 = d,
            None => map.push((path, d)),
        }
    }
    let map = D::Map(map);
    if errors.is_empty() {
        return Ok(map);
    }
    let msg = errors.iter().rev().map(String::as_str).collect::<Vec<_>>().join("; ");
    let missed = Node::new(Value::Map(failed.into()));
    Err(Fail { msg: format!("{msg} missed input: {}", missed.show(mode)), partial: Some(map) })
}

/// Component type keys by path (`minecraft:<path>`).
fn effect_keys(path: &str) -> Option<&'static str> {
    static KEYS: OnceLock<Vec<(&'static str, String)>> = OnceLock::new();
    KEYS.get_or_init(|| COMPONENTS.iter().map(|(p, _)| (*p, format!("minecraft:{p}"))).collect())
        .iter()
        .find(|(p, _)| *p == path)
        .map(|(_, k)| k.as_str())
}

fn encode_effects(d: &D) -> R<O<'_>> {
    let D::Map(entries) = d else { return Err("not an effect map".into()) };
    let mut out = Vec::new();
    for (path, value) in entries {
        let s = component(path).ok_or_else(|| format!("unknown effect component {path}"))?;
        out.push((std::borrow::Cow::Borrowed(effect_keys(path).ok_or("unknown effect component")?), schema::encode(s, s, value)?));
    }
    Ok(O::Map(out))
}

fn check_effects(d: &D) -> R<()> {
    let D::Map(entries) = d else { return Ok(()) };
    for (path, value) in entries {
        if let Some(s) = component(path) {
            schema::check(s, value)?;
        }
    }
    Ok(())
}
