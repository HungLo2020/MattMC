//! Item definitions: the identity (name, registry ID order) of every
//! registered item, its block and block aliases, description prefix,
//! crafting remainder, required features and default data components.
//! Components are stored in their save encoding (SNBT) and decoded by the
//! component codecs Java binds. Java's `Item` objects are projections; Java
//! declares only behavior factories. See docs/development/game-model/ITEM-DEFINITIONS.md.
mod catalog;
pub mod snbt;
pub mod runtime;
#[cfg(test)]
mod tests;

#[derive(Clone, Copy, Debug)]
pub struct ItemDef {
    /// Path in the `minecraft` namespace.
    pub name: &'static str,
    /// The block a `BlockItem` places.
    pub block: Option<&'static str>,
    /// Further blocks whose item form is this item (wall variants and similar).
    pub aliases: &'static [&'static str],
    /// Description ID `block.minecraft.<name>` rather than `item.minecraft.<name>`.
    pub block_prefix: bool,
    /// The item left behind after crafting.
    pub remainder: Option<&'static str>,
    pub required_features: &'static [&'static str],
    /// Component type and SNBT value, where they differ from `common()`.
    pub components: &'static [(&'static str, &'static str)],
}

/// A plain item.
pub(crate) const fn item(name: &'static str) -> ItemDef {
    ItemDef { name, block: None, aliases: &[], block_prefix: false, remainder: None, required_features: &["minecraft:vanilla"], components: &[] }
}
/// The item form of the block of the same name.
pub(crate) const fn block(name: &'static str) -> ItemDef {
    ItemDef { block: Some(name), block_prefix: true, ..item(name) }
}

impl ItemDef {
    pub fn description_id(&self) -> String {
        format!("{}.minecraft.{}", if self.block_prefix { "block" } else { "item" }, self.name)
    }
    /// Every default component as (type, SNBT): the common ones, the item's
    /// own, then the name and model every item carries.
    pub fn default_components(&self) -> Vec<(&'static str, String)> {
        let mut all: Vec<(&'static str, String)> = common().iter()
            .filter(|(t, _)| !self.components.iter().any(|(own, _)| own == t))
            .map(|(t, v)| (*t, (*v).to_owned())).collect();
        all.extend(self.components.iter().map(|(t, v)| (*t, (*v).to_owned())));
        all.push(("item_name", format!("{{translate:\"{}\"}}", self.description_id())));
        all.push(("item_model", format!("\"minecraft:{}\"", self.name)));
        all
    }
}

/// Every registered item, by registry ID.
pub fn definitions() -> &'static [ItemDef] {
    catalog::ITEMS
}

/// Components every item starts with (Java's former `COMMON_ITEM_COMPONENTS`).
pub fn common() -> &'static [(&'static str, &'static str)] {
    catalog::COMMON
}

/// Text table for Java, tab separated: "K type snbt" per common component,
/// then per item "I name block|- block_prefix(0/1) remainder|- features,..."
/// followed by its "A block" aliases and "C type snbt" components.
pub fn export() -> String {
    let mut out = String::new();
    for (t, v) in common() {
        out.push_str(&format!("K\t{t}\t{v}\n"));
    }
    for def in definitions() {
        out.push_str(&format!("I\t{}\t{}\t{}\t{}\t{}\n", def.name, def.block.unwrap_or("-"), u8::from(def.block_prefix),
            def.remainder.unwrap_or("-"), def.required_features.join(",")));
        for alias in def.aliases {
            out.push_str(&format!("A\t{alias}\n"));
        }
        for (t, v) in def.components {
            out.push_str(&format!("C\t{t}\t{v}\n"));
        }
    }
    out
}

/// Every component value of `export()`, in its order (common first, then
/// each item's), parsed from SNBT into NBT (see [`snbt::write`]).
pub fn export_tags() -> Result<Vec<u8>, String> {
    let mut out = Vec::new();
    for (t, v) in common().iter().chain(definitions().iter().flat_map(|d| d.components.iter())) {
        snbt::write(&snbt::parse(v).map_err(|e| format!("{t} {v}: {e}"))?, &mut out);
    }
    Ok(out)
}

/// Copies the parsed component values into `out` when they fit; returns
/// their full length, or -1 if a catalog value does not parse.
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_component_tags(out: *mut u8, capacity: i64) -> i64 {
    match std::panic::catch_unwind(export_tags) {
        Ok(Ok(table)) => {
            if !out.is_null() && capacity >= table.len() as i64 {
                std::ptr::copy_nonoverlapping(table.as_ptr(), out, table.len());
            }
            table.len() as i64
        }
        _ => -1,
    }
}
