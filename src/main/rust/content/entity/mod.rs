//! Entity type definitions: the identity (name, registry ID order) and every
//! intrinsic setting of each registered entity type. Java's `EntityType`
//! objects are projections of these definitions; they declare only behavior
//! factories. See docs/development/game-model/ENTITY-TYPES.md.
mod catalog;
mod serializers;
mod synched;
#[cfg(test)]
mod tests;

use std::panic::{catch_unwind, AssertUnwindSafe};

/// Java's `MobCategory`, by serialized name.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MobCategory {
    Monster,
    Creature,
    Ambient,
    Axolotls,
    UndergroundWaterCreature,
    WaterCreature,
    WaterAmbient,
    Misc,
}
impl MobCategory {
    pub fn serialized_name(self) -> &'static str {
        match self {
            Self::Monster => "monster",
            Self::Creature => "creature",
            Self::Ambient => "ambient",
            Self::Axolotls => "axolotls",
            Self::UndergroundWaterCreature => "underground_water_creature",
            Self::WaterCreature => "water_creature",
            Self::WaterAmbient => "water_ambient",
            Self::Misc => "misc",
        }
    }
}

/// Java's `EntityAttachment`, in its declaration order.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Attachment {
    Passenger,
    Vehicle,
    NameTag,
    WardenChest,
}
pub const ATTACHMENTS: [Attachment; 4] = [Attachment::Passenger, Attachment::Vehicle, Attachment::NameTag, Attachment::WardenChest];

#[derive(Clone, Copy, Debug)]
pub struct EntityTypeDef {
    /// Path in the `minecraft` namespace.
    pub name: &'static str,
    pub category: MobCategory,
    pub serialize: bool,
    pub summon: bool,
    pub fire_immune: bool,
    pub spawn_far: bool,
    /// Allowed to spawn on peaceful difficulty.
    pub peaceful: bool,
    /// Block names the entity takes no damage from.
    pub immune_to: &'static [&'static str],
    pub width: f32,
    pub height: f32,
    /// None: 85% of the height.
    pub eye_height: Option<f32>,
    /// Explicit points; kinds without any use the height-based fallback.
    pub attachments: &'static [(Attachment, [f64; 3])],
    pub spawn_scale: f32,
    pub tracking_range: i32,
    pub update_interval: i32,
    /// Whether the type has the default `entities/<name>` loot table.
    pub loot_table: bool,
    pub required_features: &'static [&'static str],
}

/// The defaults every entry starts from.
pub(crate) const fn base(name: &'static str, category: MobCategory) -> EntityTypeDef {
    EntityTypeDef {
        name,
        category,
        serialize: true,
        summon: true,
        fire_immune: false,
        spawn_far: matches!(category, MobCategory::Creature | MobCategory::Misc),
        peaceful: true,
        immune_to: &[],
        width: 0.6,
        height: 1.8,
        eye_height: None,
        attachments: &[],
        spawn_scale: 1.0,
        tracking_range: 5,
        update_interval: 3,
        loot_table: true,
        required_features: &["minecraft:vanilla"],
    }
}

impl EntityTypeDef {
    pub fn eye_height(&self) -> f32 {
        self.eye_height.unwrap_or(self.height * 0.85)
    }
    /// The type's points of one kind: the explicit ones, or Java's fallback.
    pub fn attachment_points(&self, kind: Attachment) -> Vec<[f64; 3]> {
        let explicit: Vec<_> = self.attachments.iter().filter(|(k, _)| *k == kind).map(|(_, p)| *p).collect();
        if !explicit.is_empty() {
            return explicit;
        }
        let height = f64::from(self.height);
        vec![match kind {
            Attachment::Passenger | Attachment::NameTag => [0.0, height, 0.0],
            Attachment::Vehicle => [0.0, 0.0, 0.0],
            Attachment::WardenChest => [0.0, height / 2.0, 0.0],
        }]
    }
}

/// Every registered entity type, by registry ID.
pub fn definitions() -> &'static [EntityTypeDef] {
    catalog::ENTITY_TYPES
}

const EXPORT_MAGIC: u32 = 0x4d45_5459; // "METY"
const EXPORT_VERSION: u32 = 1;

fn put_str(out: &mut Vec<u8>, text: &str) {
    out.extend_from_slice(&(text.len() as u16).to_le_bytes());
    out.extend_from_slice(text.as_bytes());
}

/// The table Java projects `EntityType` objects from, little endian:
/// magic, version, count, then per type: name, category, flags (serialize 1,
/// summon 2, fire immune 4, spawn far 8, peaceful 16, loot table 32), tracking
/// range, update interval, width, height, eye height, spawn scale, features,
/// immune blocks and the four resolved attachment lists.
pub fn export() -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(&EXPORT_MAGIC.to_le_bytes());
    out.extend_from_slice(&EXPORT_VERSION.to_le_bytes());
    out.extend_from_slice(&(definitions().len() as u32).to_le_bytes());
    for def in definitions() {
        put_str(&mut out, def.name);
        put_str(&mut out, def.category.serialized_name());
        let flags = u8::from(def.serialize)
            | u8::from(def.summon) << 1
            | u8::from(def.fire_immune) << 2
            | u8::from(def.spawn_far) << 3
            | u8::from(def.peaceful) << 4
            | u8::from(def.loot_table) << 5;
        out.push(flags);
        out.extend_from_slice(&def.tracking_range.to_le_bytes());
        out.extend_from_slice(&def.update_interval.to_le_bytes());
        for value in [def.width, def.height, def.eye_height(), def.spawn_scale] {
            out.extend_from_slice(&value.to_le_bytes());
        }
        out.push(def.required_features.len() as u8);
        for feature in def.required_features {
            put_str(&mut out, feature);
        }
        out.push(def.immune_to.len() as u8);
        for block in def.immune_to {
            put_str(&mut out, block);
        }
        for kind in ATTACHMENTS {
            let points = def.attachment_points(kind);
            out.extend_from_slice(&(points.len() as u16).to_le_bytes());
            for point in points {
                for value in point {
                    out.extend_from_slice(&value.to_le_bytes());
                }
            }
        }
    }
    out
}

/// Entity data serializers, by network ID.
pub fn serializers() -> &'static [&'static str] {
    serializers::SERIALIZERS
}

/// An entity class that defines synchronized data fields.
#[derive(Clone, Copy, Debug)]
pub struct SynchedClass {
    /// Java binary class name.
    pub class: &'static str,
    /// The nearest superclass that also defines fields; IDs continue after its last.
    pub base: Option<&'static str>,
    /// Java field name and serializer name, in definition order.
    pub fields: &'static [(&'static str, &'static str)],
}

pub fn synched_classes() -> &'static [SynchedClass] {
    synched::SYNCHED_CLASSES
}

/// The ID of each class's first field: one after its base's last, else 0.
pub fn synched_first_ids() -> Vec<u32> {
    let classes = synched_classes();
    let index: std::collections::HashMap<_, _> = classes.iter().enumerate().map(|(i, c)| (c.class, i)).collect();
    let mut first = vec![u32::MAX; classes.len()];
    fn resolve(i: usize, classes: &[SynchedClass], index: &std::collections::HashMap<&str, usize>, first: &mut [u32], depth: usize) -> u32 {
        assert!(depth < classes.len(), "synched data base cycle at {}", classes[i].class);
        if first[i] == u32::MAX {
            first[i] = match classes[i].base {
                None => 0,
                Some(base) => {
                    let b = *index.get(base).unwrap_or_else(|| panic!("unknown synched base {base}"));
                    resolve(b, classes, index, first, depth + 1) + classes[b].fields.len() as u32
                }
            };
        }
        first[i]
    }
    for i in 0..classes.len() {
        resolve(i, classes, &index, &mut first, 0);
    }
    first
}

/// Text table for Java: one line per class, "class base first_id serializer...",
/// with base "-" for none.
pub fn export_synched() -> String {
    let first = synched_first_ids();
    synched_classes().iter().zip(first).map(|(c, id)| {
        let fields: Vec<_> = c.fields.iter().map(|(_, s)| *s).collect();
        format!("{} {} {} {}\n", c.class, c.base.unwrap_or("-"), id, fields.join(" "))
    }).collect()
}

/// Content identity tables as text: 0 component types, 1 serializers, 2 synched data, 3 items.
/// Copies into `out` when it fits; returns the full length, or -1 for an unknown table.
#[no_mangle]
pub unsafe extern "C" fn mattmc_content_identity_export(table: i32, out: *mut u8, capacity: i64) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let text = match table {
            0 => crate::content::component::export(),
            1 => serializers().iter().map(|s| format!("{s}\n")).collect(),
            2 => export_synched(),
            3 => crate::content::item::export(),
            _ => return -1,
        };
        if !out.is_null() && capacity >= text.len() as i64 {
            std::ptr::copy_nonoverlapping(text.as_ptr(), out, text.len());
        }
        text.len() as i64
    }))
    .unwrap_or(-1)
}

/// Copies the export table into `out` when it fits; returns its full length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_types_export(out: *mut u8, capacity: i64) -> i64 {
    catch_unwind(AssertUnwindSafe(|| {
        let table = export();
        if !out.is_null() && capacity >= table.len() as i64 {
            std::ptr::copy_nonoverlapping(table.as_ptr(), out, table.len());
        }
        table.len() as i64
    }))
    .unwrap_or(-1)
}
