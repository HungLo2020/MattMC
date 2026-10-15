//! Schemas of the data-driven entity, item and biome registries, field for
//! field as Java's codecs declare them (order, presence and defaults).

use super::schema::{case, f, D, Dv, P, R, S};
use super::values::INT_PROVIDER;

use P::{Default, Inline, JavaDefault, Optional, OrElse, Required};

/// A registry Rust decodes: its data pack directory, direct schema and
/// network schema (`RegistryDataLoader.SYNCHRONIZED_REGISTRIES`).
pub struct Registry {
    pub name: &'static str,
    pub direct: &'static S,
    pub network: &'static S,
}

/// Registries by Java-side ID (`NativeData.Kind` ordinal).
pub static REGISTRIES: &[Registry] = &[
    Registry { name: "worldgen/biome", direct: &BIOME, network: &BIOME_NETWORK },
    Registry { name: "painting_variant", direct: &PAINTING, network: &PAINTING },
    Registry { name: "damage_type", direct: &DAMAGE_TYPE, network: &DAMAGE_TYPE },
    Registry { name: "banner_pattern", direct: &BANNER_PATTERN, network: &BANNER_PATTERN },
    Registry { name: "jukebox_song", direct: &JUKEBOX_SONG, network: &JUKEBOX_SONG },
    Registry { name: "trim_pattern", direct: &TRIM_PATTERN, network: &TRIM_PATTERN },
    Registry { name: "trim_material", direct: &TRIM_MATERIAL, network: &TRIM_MATERIAL },
    Registry { name: "instrument", direct: &INSTRUMENT, network: &INSTRUMENT },
    Registry { name: "enchantment", direct: &ENCHANTMENT, network: &ENCHANTMENT },
    Registry { name: "enchantment_provider", direct: &ENCHANTMENT_PROVIDER, network: &ENCHANTMENT_PROVIDER },
    Registry { name: "cat_variant", direct: &ASSET_VARIANT, network: &ASSET_VARIANT_NETWORK },
    Registry { name: "frog_variant", direct: &ASSET_VARIANT, network: &ASSET_VARIANT_NETWORK },
    Registry { name: "wolf_variant", direct: &WOLF_VARIANT, network: &WOLF_VARIANT_NETWORK },
    Registry { name: "wolf_sound_variant", direct: &WOLF_SOUND_VARIANT, network: &WOLF_SOUND_VARIANT },
    Registry { name: "pig_variant", direct: &PIG_VARIANT, network: &PIG_VARIANT_NETWORK },
    Registry { name: "cow_variant", direct: &COW_VARIANT, network: &COW_VARIANT_NETWORK },
    Registry { name: "chicken_variant", direct: &CHICKEN_VARIANT, network: &CHICKEN_VARIANT_NETWORK },
    Registry { name: "zombie_nautilus_variant", direct: &ZOMBIE_NAUTILUS_VARIANT, network: &ZOMBIE_NAUTILUS_VARIANT_NETWORK },
];

const NON_NEGATIVE: S = S::IntRange(0, i32::MAX);
/// `RegistryCodecs.homogeneousList`: a tag, one ID or a list.
const HOLDERS: S = S::HolderSet { force_list: false };
const POSITIVE: S = S::IntRange(1, i32::MAX);

// Biome (`Biome.DIRECT_CODEC` / `NETWORK_CODEC`).

pub const TEMPERATURE_MODIFIERS: &[&str] = &["none", "frozen"];
pub const GRASS_COLOR_MODIFIERS: &[&str] = &["none", "dark_forest", "swamp"];
pub const MOB_CATEGORIES: &[&str] =
    &["monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc"];

static CLIMATE: S = S::Record(&[
    f("has_precipitation", &S::Bool, Required),
    f("temperature", &S::Float, Required),
    f("temperature_modifier", &S::Enum(TEMPERATURE_MODIFIERS), Default(Dv::Name("none"))),
    f("downfall", &S::Float, Required),
]);
static PARTICLE: S = S::Record(&[f("options", &super::values::PARTICLE, Required), f("probability", &S::Float, Required)]);
static MOOD: S = S::Record(&[
    f("sound", &S::Sound, Required),
    f("tick_delay", &S::Int, Required),
    f("block_search_extent", &S::Int, Required),
    f("offset", &S::Double, Required),
]);
static ADDITIONS: S = S::Record(&[f("sound", &S::Sound, Required), f("tick_chance", &S::Double, Required)]);
static MUSIC_DATA: S = S::Record(&[
    f("sound", &S::Sound, Required),
    f("min_delay", &S::Int, Required),
    f("max_delay", &S::Int, Required),
    f("replace_current_music", &S::Bool, Required),
]);
static MUSIC: S = S::Record(&[f("data", &MUSIC_DATA, Required), f("weight", &NON_NEGATIVE, Required)]);
static EFFECTS: S = S::Record(&[
    f("fog_color", &S::Int, Required),
    f("water_color", &S::Int, Required),
    f("water_fog_color", &S::Int, Required),
    f("sky_color", &S::Int, Required),
    f("foliage_color", &S::Int, Optional),
    f("dry_foliage_color", &S::Int, Optional),
    f("grass_color", &S::Int, Optional),
    f("grass_color_modifier", &S::Enum(GRASS_COLOR_MODIFIERS), Default(Dv::Name("none"))),
    f("particle", &PARTICLE, Optional),
    f("ambient_sound", &S::Sound, Optional),
    f("mood_sound", &MOOD, Optional),
    f("additions_sound", &ADDITIONS, Optional),
    f("music", &S::List(&MUSIC), Optional),
    f("music_volume", &S::Float, OrElse(Dv::Float(1.0))),
]);
fn spawner_counts(d: &D) -> R<()> {
    match d {
        D::Record(v) if matches!((&v[1], &v[2]), (D::Int(min), D::Int(max)) if min > max) => {
            Err("minCount needs to be smaller or equal to maxCount".into())
        }
        _ => Ok(()),
    }
}
static SPAWNER: S = S::Validated(
    &S::Record(&[
        f("type", &S::EntityType, Required),
        f("minCount", &POSITIVE, Required),
        f("maxCount", &POSITIVE, Required),
        f("weight", &NON_NEGATIVE, Required),
    ]),
    spawner_counts,
);
static SPAWN_COST: S = S::Record(&[f("energy_budget", &S::Double, Required), f("charge", &S::Double, Required)]);
static BIOME: S = S::Record(&[
    f("climate", &CLIMATE, Inline),
    f("effects", &EFFECTS, Required),
    f("generation", &S::Record(&[
        f("carvers", &S::HolderSet { force_list: false }, Required),
        f("features", &S::HolderSetList, Required),
    ]), Inline),
    f("mobs", &S::Record(&[
        f("creature_spawn_probability", &S::FloatRange(0.0, 0.999_999_9), Default(Dv::Float(0.1))),
        f("spawners", &S::Map(&S::Enum(MOB_CATEGORIES), &S::PartialList(&SPAWNER, "Spawn data: ")), Required),
        f("spawn_costs", &S::Map(&S::EntityType, &SPAWN_COST), Required),
    ]), Inline),
]);
static BIOME_NETWORK: S = S::Record(&[f("climate", &CLIMATE, Inline), f("effects", &EFFECTS, Required)]);

// Items.

static PAINTING: S = S::Record(&[
    f("width", &S::IntRange(1, 16), Required),
    f("height", &S::IntRange(1, 16), Required),
    f("asset_id", &S::Location, Required),
    f("title", &S::Delegate, Optional),
    f("author", &S::Delegate, Optional),
]);
static DAMAGE_TYPE: S = S::Record(&[
    f("message_id", &S::Str, Required),
    f("scaling", &S::Enum(&["never", "when_caused_by_living_non_player", "always"]), Required),
    f("exhaustion", &S::Float, Required),
    f("effects", &S::Enum(&["hurt", "thorns", "drowning", "burning", "poking", "freezing"]), Default(Dv::Name("hurt"))),
    f("death_message_type", &S::Enum(&["default", "fall_variants", "intentional_game_design"]), Default(Dv::Name("default"))),
]);
static BANNER_PATTERN: S = S::Record(&[f("asset_id", &S::Location, Required), f("translation_key", &S::Str, Required)]);
static JUKEBOX_SONG: S = S::Record(&[
    f("sound_event", &S::Sound, Required),
    f("description", &S::Delegate, Required),
    f("length_in_seconds", &S::PositiveFloat, Required),
    f("comparator_output", &S::IntRange(0, 15), Required),
]);
static TRIM_PATTERN: S = S::Record(&[
    f("asset_id", &S::Location, Required),
    f("description", &S::Delegate, Required),
    f("decal", &S::Bool, OrElse(Dv::Bool(false))),
]);
static TRIM_MATERIAL: S = S::Record(&[
    f("assets", &S::Record(&[
        f("asset_name", &S::ResourcePath, Required),
        f("override_armor_assets", &S::Map(&S::Location, &S::ResourcePath), Default(Dv::EmptyMap)),
    ]), Inline),
    f("description", &S::Delegate, Required),
]);
static INSTRUMENT: S = S::Record(&[
    f("sound_event", &S::Sound, Required),
    f("use_duration", &S::PositiveFloat, Required),
    f("range", &S::PositiveFloat, Required),
    f("description", &S::Delegate, Required),
]);
pub const SLOT_GROUPS: &[&str] = &["any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body", "saddle"];
static COST: S = S::Record(&[f("base", &S::Int, Required), f("per_level_above_first", &S::Int, Required)]);
static ENCHANTMENT: S = S::Record(&[
    f("description", &S::Delegate, Required),
    f("definition", &S::Record(&[
        f("supported_items", &HOLDERS, Required),
        f("primary_items", &HOLDERS, Optional),
        f("weight", &S::IntRange(1, 1024), Required),
        f("max_level", &S::IntRange(1, 255), Required),
        f("min_cost", &COST, Required),
        f("max_cost", &COST, Required),
        f("anvil_cost", &NON_NEGATIVE, Required),
        f("slots", &S::List(&S::Enum(SLOT_GROUPS)), Required),
    ]), Inline),
    f("exclusive_set", &HOLDERS, JavaDefault),
    f("effects", &S::Delegate, JavaDefault),
]);
static ENCHANTMENT_PROVIDER: S = S::Dispatch("enchantment_provider_type", &[
    case("by_cost", &[f("enchantments", &HOLDERS, Required), f("cost", &INT_PROVIDER, Required)]),
    case("by_cost_with_difficulty", &[
        f("enchantments", &HOLDERS, Required),
        f("min_cost", &S::IntRange(1, 10000), Required),
        f("max_cost_span", &S::IntRange(0, 10000), Required),
    ]),
    case("single", &[f("enchantment", &S::Delegate, Required), f("level", &INT_PROVIDER, Required)]),
]);

// Entity variants.

static SPAWN_CONDITION: S = S::Dispatch("spawn_condition_type", &[
    case("structure", &[f("structures", &HOLDERS, Required)]),
    case("moon_brightness", &[f("range", &S::DoubleBounds, Required)]),
    case("biome", &[f("biomes", &HOLDERS, Required)]),
]);
static SPAWN_CONDITIONS: S = S::List(&S::Record(&[f("condition", &SPAWN_CONDITION, Optional), f("priority", &S::Int, Required)]));
static ASSET_VARIANT: S = S::Record(&[f("asset_id", &S::Location, Required), f("spawn_conditions", &SPAWN_CONDITIONS, Required)]);
static ASSET_VARIANT_NETWORK: S = S::Record(&[f("asset_id", &S::Location, Required)]);
static WOLF_ASSETS: S = S::Record(&[f("wild", &S::Location, Required), f("tame", &S::Location, Required), f("angry", &S::Location, Required)]);
static WOLF_VARIANT: S = S::Record(&[f("assets", &WOLF_ASSETS, Required), f("spawn_conditions", &SPAWN_CONDITIONS, Required)]);
static WOLF_VARIANT_NETWORK: S = S::Record(&[f("assets", &WOLF_ASSETS, Required)]);
static WOLF_SOUND_VARIANT: S = S::Record(&[
    f("ambient_sound", &S::Sound, Required),
    f("death_sound", &S::Sound, Required),
    f("growl_sound", &S::Sound, Required),
    f("hurt_sound", &S::Sound, Required),
    f("pant_sound", &S::Sound, Required),
    f("whine_sound", &S::Sound, Required),
]);

macro_rules! model_variant {
    ($direct:ident, $network:ident, $models:expr) => {
        static $direct: S = S::Record(&[
            f("model", &S::Enum($models), Default(Dv::Name("normal"))),
            f("asset_id", &S::Location, Required),
            f("spawn_conditions", &SPAWN_CONDITIONS, Required),
        ]);
        static $network: S = S::Record(&[f("model", &S::Enum($models), Default(Dv::Name("normal"))), f("asset_id", &S::Location, Required)]);
    };
}
model_variant!(PIG_VARIANT, PIG_VARIANT_NETWORK, &["normal", "cold"]);
model_variant!(COW_VARIANT, COW_VARIANT_NETWORK, &["normal", "cold", "warm"]);
model_variant!(CHICKEN_VARIANT, CHICKEN_VARIANT_NETWORK, &["normal", "cold"]);
model_variant!(ZOMBIE_NAUTILUS_VARIANT, ZOMBIE_NAUTILUS_VARIANT_NETWORK, &["normal", "warm"]);

