//! Data component types: the identity (name, registry ID order) of every
//! component an item stack can carry, and whether it is saved (`persistent`)
//! and has its encoding cached. Java binds each type's codecs by name.
//! See docs/development/game-model/CONTENT-IDENTITIES.md.
mod catalog;

#[derive(Clone, Copy, Debug)]
pub struct ComponentTypeDef {
    /// Path in the `minecraft` namespace.
    pub name: &'static str,
    pub persistent: bool,
    pub cached: bool,
}

/// Every data component type, by registry ID.
pub fn definitions() -> &'static [ComponentTypeDef] {
    catalog::COMPONENT_TYPES
}

/// One line per type: "name persistent cached" (0/1).
pub fn export() -> String {
    definitions().iter().map(|d| format!("{} {} {}\n", d.name, u8::from(d.persistent), u8::from(d.cached))).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn names_are_unique_and_cached_types_are_persistent() {
        let mut names: Vec<_> = definitions().iter().map(|d| d.name).collect();
        names.sort_unstable();
        names.dedup();
        assert_eq!(names.len(), definitions().len());
        assert!(definitions().iter().all(|d| d.persistent || !d.cached));
    }
}
