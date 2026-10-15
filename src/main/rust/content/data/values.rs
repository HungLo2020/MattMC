//! Value types other definitions embed, field for field as Java's codecs:
//! `IntProvider`, `FloatProvider` and enchantments' `LevelBasedValue`. Each is
//! a plain number or a `type` dispatch (`Codec.either`); a typed constant
//! normalizes to the plain form, as the Java codecs encode it.
//!
//! Decoded forms: `Variant(0, [number])` for a constant, `Variant(1,
//! [Variant(case, fields)])` for a dispatched provider.

use super::schema::{case, checked, f, D, P, R, S};
use super::value::java_float;

use P::Required;

/// A value kind Java embeds through a native codec (`NativeValues.Kind` ordinal).
pub struct ValueKind {
    pub name: &'static str,
    pub schema: &'static S,
    /// The range `codec(min, max)` variants check, as `(min, max)`.
    pub bounds: Option<fn(&D) -> (f64, f64)>,
}

pub static VALUES: &[ValueKind] = &[
    ValueKind { name: "int_provider", schema: &INT_PROVIDER, bounds: Some(|d| int_bounds(d).map_or((0.0, 0.0), |(a, b)| (f64::from(a), f64::from(b)))) },
    ValueKind { name: "float_provider", schema: &FLOAT_PROVIDER, bounds: Some(|d| float_bounds(d).map_or((0.0, 0.0), |(a, b)| (f64::from(a), f64::from(b)))) },
    ValueKind { name: "level_based_value", schema: &LEVEL_BASED_VALUE, bounds: None },
    ValueKind { name: "uniform_int", schema: &UNIFORM_INT, bounds: Some(|d| (f64::from(int(d, 0)), f64::from(int(d, 1)))) },
    ValueKind { name: "particle", schema: &PARTICLE, bounds: None },
    ValueKind { name: "position_source", schema: &POSITION_SOURCE, bounds: None },
    ValueKind { name: "component", schema: &super::text::COMPONENT, bounds: None },
    ValueKind { name: "style", schema: &super::text::COMPACT_STYLE, bounds: None },
    ValueKind { name: "click_event", schema: &super::text::CLICK_EVENT, bounds: None },
    ValueKind { name: "hover_event", schema: &super::text::HOVER_EVENT, bounds: None },
    ValueKind { name: "item_stack", schema: &super::items::ITEM_STACK, bounds: None },
    ValueKind { name: "single_item", schema: &super::items::SINGLE_ITEM, bounds: None },
    ValueKind { name: "component_patch", schema: &super::items::PATCH, bounds: None },
    ValueKind { name: "item_stack_with_slot", schema: &super::items::ITEM_STACK_WITH_SLOT, bounds: None },
    ValueKind { name: "enchantment_effects", schema: &super::enchant::EFFECTS, bounds: None },
    ValueKind { name: "enchantment_value_effect", schema: &super::enchant::VALUE_EFFECT, bounds: None },
    ValueKind { name: "enchantment_entity_effect", schema: &super::enchant::ENTITY_EFFECT, bounds: None },
    ValueKind { name: "enchantment_location_effect", schema: &super::enchant::LOCATION_EFFECT, bounds: None },
];

/// `UniformInt.CODEC.codec()`: the uniform case's fields without a type.
pub static UNIFORM_INT: S = S::Validated(&S::Record(&[f("min_inclusive", &S::Int, Required), f("max_inclusive", &S::Int, Required)]), int_min_max);

fn fields(d: &D) -> &[D] {
    match d {
        D::Variant(_, fields) | D::Record(fields) => fields,
        _ => &[],
    }
}
fn int(d: &D, i: usize) -> i32 {
    match fields(d).get(i) {
        Some(D::Int(v)) => *v,
        _ => 0,
    }
}
fn float(d: &D, i: usize) -> f32 {
    match fields(d).get(i) {
        Some(D::Float(v)) => *v,
        _ => 0.0,
    }
}

// IntProvider (`IntProvider.CODEC`).

fn int_min_max(d: &D) -> R<()> {
    let (min, max) = (int(d, 0), int(d, 1));
    if max < min {
        return Err(format!("Max must be at least min, min_inclusive: {min}, max_inclusive: {max}"));
    }
    Ok(())
}
fn clamped_int(d: &D) -> R<()> {
    let (min, max) = (int(d, 1), int(d, 2));
    if max < min {
        return Err(format!("Max must be at least min, min_inclusive: {min}, max_inclusive: {max}"));
    }
    Ok(())
}
fn clamped_normal_int(d: &D) -> R<()> {
    let (min, max) = (int(d, 2), int(d, 3));
    if max < min {
        return Err(format!("Max must be larger than min: [{min}, {max}]"));
    }
    Ok(())
}
fn non_empty(d: &D) -> R<()> {
    match d {
        D::List(items) if items.is_empty() => Err("List must have contents".into()),
        _ => Ok(()),
    }
}

static WEIGHTED_INT: S = S::Validated(
    &S::Validated(&S::List(&S::Record(&[f("data", &INT_PROVIDER, Required), f("weight", &S::IntRange(0, i32::MAX), Required)])), non_empty),
    total_weight,
);

/// `WeightedList`'s constructor (`WeightedRandom.getTotalWeight`) throws
/// when the weights overflow; DFU builds it from partial lists too.
fn total_weight(d: &D) -> R<()> {
    let D::List(entries) = d else { return Ok(()) };
    let total: i64 = entries.iter().map(|e| i64::from(int(e, 1))).sum();
    if total > i64::from(i32::MAX) {
        return Err(super::schema::throw("Sum of weights must be <= 2147483647"));
    }
    Ok(())
}

pub static INT_PROVIDER: S = S::Normalize(
    &S::Either(
        &S::Int,
        &S::Dispatch("int_provider_type", &[
            case("constant", &[f("value", &S::Int, Required)]),
            checked("uniform", &[f("min_inclusive", &S::Int, Required), f("max_inclusive", &S::Int, Required)], int_min_max),
            checked("biased_to_bottom", &[f("min_inclusive", &S::Int, Required), f("max_inclusive", &S::Int, Required)], int_min_max),
            checked(
                "clamped",
                &[f("source", &INT_PROVIDER, Required), f("min_inclusive", &S::Int, Required), f("max_inclusive", &S::Int, Required)],
                clamped_int,
            ),
            case("weighted_list", &[f("distribution", &WEIGHTED_INT, Required)]),
            checked(
                "clamped_normal",
                &[
                    f("mean", &S::Float, Required),
                    f("deviation", &S::Float, Required),
                    f("min_inclusive", &S::Int, Required),
                    f("max_inclusive", &S::Int, Required),
                ],
                clamped_normal_int,
            ),
        ]),
    ),
    normalize_constant,
);

/// A typed constant (`{"type": "constant", "value": v}`) is the plain number.
fn normalize_constant(d: D) -> D {
    match d {
        D::Variant(1, mut outer) => match outer.pop() {
            Some(D::Variant(0, value)) => D::Variant(0, value),
            Some(inner) => D::Variant(1, vec![inner]),
            None => D::Variant(1, outer),
        },
        other => other,
    }
}

/// `getMinValue` / `getMaxValue`.
pub fn int_bounds(d: &D) -> Option<(i32, i32)> {
    Some(match d {
        D::Variant(0, v) => match v.first()? {
            D::Int(v) => (*v, *v),
            _ => return None,
        },
        D::Variant(1, inner) => {
            let p = inner.first()?;
            match p {
                D::Variant(0, _) => (int(p, 0), int(p, 0)),
                D::Variant(1 | 2, _) => (int(p, 0), int(p, 1)),
                D::Variant(3, f) => {
                    let (lo, hi) = int_bounds(f.first()?)?;
                    (int(p, 1).max(lo), int(p, 2).min(hi))
                }
                D::Variant(4, f) => {
                    let D::List(entries) = f.first()? else { return None };
                    let mut bounds = (i32::MAX, i32::MIN);
                    for entry in entries {
                        let (lo, hi) = int_bounds(fields(entry).first()?)?;
                        bounds = (bounds.0.min(lo), bounds.1.max(hi));
                    }
                    bounds
                }
                D::Variant(5, _) => (int(p, 2), int(p, 3)),
                _ => return None,
            }
        }
        _ => return None,
    })
}

// FloatProvider (`FloatProvider.CODEC`).

fn uniform_float(d: &D) -> R<()> {
    let (min, max) = (float(d, 0), float(d, 1));
    if max <= min {
        return Err(format!("Max must be larger than min, min_inclusive: {}, max_exclusive: {}", java_float(min), java_float(max)));
    }
    Ok(())
}
fn clamped_normal_float(d: &D) -> R<()> {
    let (min, max) = (float(d, 2), float(d, 3));
    if max < min {
        return Err(format!("Max must be larger than min: [{}, {}]", java_float(min), java_float(max)));
    }
    Ok(())
}
fn trapezoid_float(d: &D) -> R<()> {
    let (min, max, plateau) = (float(d, 0), float(d, 1), float(d, 2));
    if max < min {
        return Err(format!("Max must be larger than min: [{}, {}]", java_float(min), java_float(max)));
    }
    if plateau > max - min {
        return Err(format!("Plateau can at most be the full span: [{}, {}]", java_float(min), java_float(max)));
    }
    Ok(())
}

pub static FLOAT_PROVIDER: S = S::Normalize(
    &S::Either(
        &S::Float,
        &S::Dispatch("float_provider_type", &[
            case("constant", &[f("value", &S::Float, Required)]),
            checked("uniform", &[f("min_inclusive", &S::Float, Required), f("max_exclusive", &S::Float, Required)], uniform_float),
            checked(
                "clamped_normal",
                &[f("mean", &S::Float, Required), f("deviation", &S::Float, Required), f("min", &S::Float, Required), f("max", &S::Float, Required)],
                clamped_normal_float,
            ),
            checked("trapezoid", &[f("min", &S::Float, Required), f("max", &S::Float, Required), f("plateau", &S::Float, Required)], trapezoid_float),
        ]),
    ),
    normalize_constant,
);

pub fn float_bounds(d: &D) -> Option<(f32, f32)> {
    Some(match d {
        // `ConstantFloat.of`: zero (either sign) is `ZERO`, +0.0.
        D::Variant(0, v) => match v.first()? {
            D::Float(v) => {
                let v = if *v == 0.0 { 0.0 } else { *v };
                (v, v)
            }
            _ => return None,
        },
        D::Variant(1, inner) => {
            let p = inner.first()?;
            match p {
                D::Variant(0, _) => (float(p, 0), float(p, 0)),
                D::Variant(1, _) => (float(p, 0), float(p, 1)),
                D::Variant(2, _) => (float(p, 2), float(p, 3)),
                D::Variant(3, _) => (float(p, 0), float(p, 1)),
                _ => return None,
            }
        }
        _ => return None,
    })
}

// LevelBasedValue (`LevelBasedValue.CODEC`): no typed constant is registered.

fn clamped_level(d: &D) -> R<()> {
    let (min, max) = (float(d, 1), float(d, 2));
    if max <= min {
        return Err(format!("Max must be larger than min, min: {}, max: {}", java_float(min), java_float(max)));
    }
    Ok(())
}

pub static LEVEL_BASED_VALUE: S = S::Either(
    &S::Float,
    &S::Dispatch("enchantment_level_based_value_type", &[
        checked(
            "clamped",
            &[f("value", &LEVEL_BASED_VALUE, Required), f("min", &S::Float, Required), f("max", &S::Float, Required)],
            clamped_level,
        ),
        case("fraction", &[f("numerator", &LEVEL_BASED_VALUE, Required), f("denominator", &LEVEL_BASED_VALUE, Required)]),
        case("levels_squared", &[f("added", &S::Float, Required)]),
        case("linear", &[f("base", &S::Float, Required), f("per_level_above_first", &S::Float, Required)]),
        case("lookup", &[f("values", &S::List(&S::Float), Required), f("fallback", &LEVEL_BASED_VALUE, Required)]),
    ]),
);

/// `codec(min, max)`'s check of a decoded or encoded provider.
pub fn check_bounds(kind: &ValueKind, d: &D, min: f64, max: f64, float: bool) -> R<()> {
    let Some(bounds) = kind.bounds else { return Ok(()) };
    let (lo, hi) = bounds(d);
    let show = |v: f64| if float { java_float(v as f32) } else { (v as i64).to_string() };
    if lo < min {
        return Err(format!("Value provider too low: {} [{}-{}]", show(min), show(lo), show(hi)));
    }
    if hi > max {
        return Err(format!("Value provider too high: {} [{}-{}]", show(max), show(lo), show(hi)));
    }
    Ok(())
}

fn kind(index: usize) -> R<&'static ValueKind> {
    VALUES.get(index).ok_or_else(|| format!("unknown value kind {index}"))
}

/// Range checks of a bounded codec (`IntProvider.codec(min, max)`).
#[derive(Clone, Copy)]
pub struct Bounds {
    pub min: f64,
    pub max: f64,
}

/// Decodes a value of kind `index`, then checks `bounds`; the context holds
/// delegated slots and logs.
pub fn decode(index: usize, node: &super::value::Node, mode: super::value::Mode, registry_ops: bool, bounds: Option<Bounds>) -> (super::schema::DR, super::schema::Cx) {
    let mut cx = super::schema::Cx::new(mode, registry_ops);
    let kind = match kind(index) {
        Ok(kind) => kind,
        Err(error) => return (Err(error.into()), cx),
    };
    let result = super::schema::decode(kind.schema, node, &mut cx);
    let result = match bounds {
        Some(b) => super::schema::check_result(result, |d| check_bounds(kind, d, b.min, b.max, index == 1)),
        None => result,
    };
    (result, cx)
}

/// The `type` dispatch inside a kind's schema (through `Codec.either`'s
/// second side and normalizing maps).
fn dispatch_of(s: &'static S) -> Option<&'static [super::schema::Case]> {
    match s {
        S::Normalize(inner, _) | S::Validated(inner, _) => dispatch_of(inner),
        S::Either(_, second) => dispatch_of(second),
        S::Dispatch(_, cases) | S::KeyDispatch(_, _, cases) => Some(cases),
        _ => None,
    }
}

/// A kind's dispatch case names, in case order (the type registry's IDs).
pub fn case_names(index: usize) -> R<Vec<&'static str>> {
    let cases = dispatch_of(kind(index)?.schema).ok_or_else(|| format!("value kind {index} is not dispatched"))?;
    Ok(cases.iter().map(|c| c.name).collect())
}

/// One dispatch case's fields from a map without `type` (the case's
/// `MapCodec`, as a type registry holds it), wrapped as the full kind decodes.
pub fn decode_case(index: usize, case_name: &str, node: &super::value::Node, mode: super::value::Mode, registry_ops: bool) -> (super::schema::DR, super::schema::Cx) {
    use super::schema::{check_result, decode_fields, map_result};
    fn within(s: &'static S, name: &str, node: &super::value::Node, cx: &mut super::schema::Cx) -> Option<super::schema::DR> {
        Some(match s {
            S::Normalize(inner, f) => map_result(within(inner, name, node, cx)?, *f),
            S::Validated(inner, c) => check_result(within(inner, name, node, cx)?, *c),
            S::Either(_, second) => map_result(within(second, name, node, cx)?, |d| D::Variant(1, vec![d])),
            S::Dispatch(_, cases) | S::KeyDispatch(_, _, cases) => {
                let index = cases.iter().position(|c| c.name == name)?;
                let case = &cases[index];
                let result = map_result(decode_fields(case.fields, node, cx), move |r| match r {
                    D::Record(fields) => D::Variant(index as u16, fields),
                    other => other,
                });
                match case.check {
                    Some(c) => check_result(result, c),
                    None => result,
                }
            }
            _ => return None,
        })
    }
    let mut cx = super::schema::Cx::new(mode, registry_ops);
    let name = case_name.strip_prefix("minecraft:").unwrap_or(case_name);
    let result = match kind(index) {
        Ok(kind) => within(kind.schema, name, node, &mut cx).unwrap_or_else(|| Err(format!("No case {case_name} in value kind {index}").into())),
        Err(error) => Err(error.into()),
    };
    (result, cx)
}

/// Encodes a value Java built, with the checks its codec runs on encode
/// (each `validate` applies both ways).
pub fn encode(index: usize, d: &D, bounds: Option<Bounds>) -> R<super::schema::O<'_>> {
    let kind = kind(index)?;
    // The bounded codec's check runs first, then the inner codecs' checks.
    if let Some(b) = bounds {
        check_bounds(kind, d, b.min, b.max, index == 1)?;
    }
    super::schema::check(kind.schema, d)?;
    super::schema::encode(kind.schema, kind.schema, d)
}

// Shared primitives.

macro_rules! fixed_size {
    ($name:ident, $n:expr, $what:expr) => {
        /// `Util.fixedSize`: exactly the size; a longer input keeps its
        /// first elements as the partial.
        fn $name(d: D) -> super::schema::DR {
            let D::List(items) = d else { return Ok(d) };
            if items.len() == $n {
                return Ok(D::List(items));
            }
            let msg = format!("Input is not a list of {} {}", $n, $what);
            let partial = (items.len() >= $n).then(|| D::List(items[..$n].to_vec()));
            Err(super::schema::Fail { msg, partial })
        }
    };
}
fixed_size!(three_elements, 3, "elements");
fixed_size!(three_ints, 3, "ints");
fixed_size!(four_ints, 4, "ints");

/// `Vec3.CODEC`.
pub static VEC3: S = S::Then(&S::List(&S::Double), three_elements);
/// `ExtraCodecs.VECTOR3F`.
pub static VECTOR3F: S = S::Then(&S::List(&S::Float), three_elements);
/// `BlockPos.CODEC` / `Vec3i.CODEC`.
pub static BLOCK_POS: S = S::Then(&S::IntStream, three_ints);
/// `UUIDUtil.CODEC`: four ints.
pub static UUID: S = S::Then(&S::IntStream, four_ints);

/// `Mth.floor(f * 255.0F)`.
fn channel(f: f32) -> i32 {
    let v = f * 255.0;
    let i = v as i32;
    if v < i as f32 { i - 1 } else { i }
}

/// `ExtraCodecs.RGB_COLOR_CODEC`: an int, or `[r, g, b]` floats as opaque RGB.
pub static RGB_COLOR: S = S::Normalize(&S::Either(&S::Int, &VECTOR3F), |d| match d {
    D::Variant(1, v) => {
        let c = |i: usize| match &v[0] {
            D::List(items) => match items.get(i) {
                Some(D::Float(f)) => *f,
                _ => 0.0,
            },
            _ => 0.0,
        };
        let color = (255 << 24) | (channel(c(0)) & 0xFF) << 16 | (channel(c(1)) & 0xFF) << 8 | (channel(c(2)) & 0xFF);
        D::Variant(0, vec![D::Int(color)])
    }
    other => other,
});

/// `ExtraCodecs.VECTOR4F`.
pub static VECTOR4F: S = S::Then(&S::List(&S::Float), four_elements);
fixed_size!(four_elements, 4, "elements");

/// `ExtraCodecs.ARGB_COLOR_CODEC`: an int, or `[r, g, b, a]` floats.
pub static ARGB_COLOR: S = S::Normalize(&S::Either(&S::Int, &VECTOR4F), |d| match d {
    D::Variant(1, v) => {
        let c = |i: usize| match &v[0] {
            D::List(items) => match items.get(i) {
                Some(D::Float(f)) => *f,
                _ => 0.0,
            },
            _ => 0.0,
        };
        let color = (channel(c(3)) & 0xFF) << 24 | (channel(c(0)) & 0xFF) << 16 | (channel(c(1)) & 0xFF) << 8 | (channel(c(2)) & 0xFF);
        D::Variant(0, vec![D::Int(color)])
    }
    other => other,
});

/// `Direction.CODEC`.
pub static DIRECTION: S = S::Enum(&["down", "up", "north", "south", "west", "east"]);

// Particle options (`ParticleTypes.CODEC`).

fn not_entity(d: &D) -> R<()> {
    match d {
        D::Variant(1, _) => Err("Entity position sources are not allowed".into()),
        _ => Ok(()),
    }
}

/// `PositionSource.CODEC`.
pub static POSITION_SOURCE: S = S::Dispatch("position_source_type", &[
    case("block", &[f("pos", &BLOCK_POS, Required)]),
    case("entity", &[f("source_entity", &UUID, Required), f("y_offset", &S::Float, P::OrElse(super::schema::Dv::Float(0.0)))]),
]);

fn scale(d: &D) -> R<()> {
    match d {
        D::Float(v) if *v >= 0.01 && *v <= 4.0 => Ok(()),
        D::Float(v) => Err(format!("Value must be within range [0.01;4.0]: {}", java_float(*v))),
        _ => Ok(()),
    }
}
static SCALE: S = S::Validated(&S::Float, scale);
static BLOCK_STATE_OR_NAME: S = S::Normalize(&S::Either(&S::BlockState, &S::BlockDefaultState), |d| match d {
    D::Variant(_, v) => D::Variant(0, v),
    other => other,
});

const BLOCK: &[super::schema::F] = &[f("block_state", &BLOCK_STATE_OR_NAME, Required)];
const COLOR: &[super::schema::F] = &[f("color", &ARGB_COLOR, Required)];
const POWER: &[super::schema::F] = &[f("power", &S::Float, P::DefaultAlways(super::schema::Dv::Float(1.0)))];
const SPELL: &[super::schema::F] = &[
    f("color", &RGB_COLOR, P::Default(super::schema::Dv::Variant0Int(-1))),
    f("power", &S::Float, P::Default(super::schema::Dv::Float(1.0))),
];

/// `ItemParticleOption.ITEM_CODEC`: a single item, or just its ID.
static PARTICLE_ITEM: S = S::Normalize(&S::Either(&super::items::SINGLE_ITEM, &super::items::ITEM), |d| match d {
    D::Variant(1, mut v) => D::Variant(0, vec![D::Record(vec![v.pop().unwrap_or(D::Absent), D::Map(Vec::new())])]),
    other => other,
});

/// Particle types in `ParticleTypes` registration order (the registry's IDs).
pub static PARTICLE: S = S::Dispatch("particle_type", &[
    case("angry_villager", &[]),
    case("block", BLOCK),
    case("block_marker", BLOCK),
    case("bullet_hole", &[
        f("direction", &DIRECTION, Required),
        f("pos", &BLOCK_POS, Required),
        f("ammo_id", &S::Str, P::Default(super::schema::Dv::Name(""))),
        f("gun_id", &S::Str, P::Default(super::schema::Dv::Name(""))),
        f("gun_display_id", &S::Str, P::Default(super::schema::Dv::Name(""))),
    ]),
    case("bubble", &[]),
    case("cloud", &[]),
    case("copper_fire_flame", &[]),
    case("crit", &[]),
    case("damage_indicator", &[]),
    case("dragon_breath", POWER),
    case("dripping_lava", &[]),
    case("falling_lava", &[]),
    case("landing_lava", &[]),
    case("dripping_water", &[]),
    case("falling_water", &[]),
    case("dust", &[f("color", &RGB_COLOR, Required), f("scale", &SCALE, Required)]),
    case("dust_color_transition", &[f("from_color", &RGB_COLOR, Required), f("to_color", &RGB_COLOR, Required), f("scale", &SCALE, Required)]),
    case("effect", SPELL),
    case("elder_guardian", &[]),
    case("enchanted_hit", &[]),
    case("enchant", &[]),
    case("end_rod", &[]),
    case("entity_effect", COLOR),
    case("explosion_emitter", &[]),
    case("explosion", &[]),
    case("gust", &[]),
    case("small_gust", &[]),
    case("gust_emitter_large", &[]),
    case("gust_emitter_small", &[]),
    case("sonic_boom", &[]),
    case("falling_dust", BLOCK),
    case("firework", &[]),
    case("fishing", &[]),
    case("flame", &[]),
    case("infested", &[]),
    case("cherry_leaves", &[]),
    case("pale_oak_leaves", &[]),
    case("tinted_leaves", COLOR),
    case("sculk_soul", &[]),
    case("sculk_charge", &[f("roll", &S::Float, Required)]),
    case("sculk_charge_pop", &[]),
    case("soul_fire_flame", &[]),
    case("soul", &[]),
    case("flash", COLOR),
    case("happy_villager", &[]),
    case("composter", &[]),
    case("heart", &[]),
    case("instant_effect", SPELL),
    case("item", &[f("item", &PARTICLE_ITEM, Required)]),
    case("vibration", &[f("destination", &S::Validated(&POSITION_SOURCE, not_entity), Required), f("arrival_in_ticks", &S::Int, Required)]),
    case("trail", &[f("target", &VEC3, Required), f("color", &RGB_COLOR, Required), f("duration", &S::IntRange(1, i32::MAX), Required)]),
    case("item_slime", &[]),
    case("item_cobweb", &[]),
    case("item_snowball", &[]),
    case("large_smoke", &[]),
    case("lava", &[]),
    case("mycelium", &[]),
    case("note", &[]),
    case("poof", &[]),
    case("portal", &[]),
    case("rain", &[]),
    case("smoke", &[]),
    case("white_smoke", &[]),
    case("sneeze", &[]),
    case("spit", &[]),
    case("squid_ink", &[]),
    case("sweep_attack", &[]),
    case("totem_of_undying", &[]),
    case("underwater", &[]),
    case("splash", &[]),
    case("witch", &[]),
    case("bubble_pop", &[]),
    case("current_down", &[]),
    case("bubble_column_up", &[]),
    case("nautilus", &[]),
    case("dolphin", &[]),
    case("campfire_cosy_smoke", &[]),
    case("campfire_signal_smoke", &[]),
    case("dripping_honey", &[]),
    case("falling_honey", &[]),
    case("landing_honey", &[]),
    case("falling_nectar", &[]),
    case("falling_spore_blossom", &[]),
    case("ash", &[]),
    case("crimson_spore", &[]),
    case("warped_spore", &[]),
    case("spore_blossom_air", &[]),
    case("dripping_obsidian_tear", &[]),
    case("falling_obsidian_tear", &[]),
    case("landing_obsidian_tear", &[]),
    case("reverse_portal", &[]),
    case("white_ash", &[]),
    case("small_flame", &[]),
    case("snowflake", &[]),
    case("dripping_dripstone_lava", &[]),
    case("falling_dripstone_lava", &[]),
    case("dripping_dripstone_water", &[]),
    case("falling_dripstone_water", &[]),
    case("glow_squid_ink", &[]),
    case("glow", &[]),
    case("wax_on", &[]),
    case("wax_off", &[]),
    case("electric_spark", &[]),
    case("scrape", &[]),
    case("shriek", &[f("delay", &S::Int, Required)]),
    case("egg_crack", &[]),
    case("dust_plume", &[]),
    case("trial_spawner_detection", &[]),
    case("trial_spawner_detection_ominous", &[]),
    case("vault_connection", &[]),
    case("dust_pillar", BLOCK),
    case("ominous_spawning", &[]),
    case("raid_omen", &[]),
    case("trial_omen", &[]),
    case("block_crumble", BLOCK),
    case("firefly", &[]),
    case("fly", &[]),
    case("water_tremor", &[]),
    case("sunbird_feather", &[]),
]);
