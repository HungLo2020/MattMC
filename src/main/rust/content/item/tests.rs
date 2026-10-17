use super::*;
use std::collections::{BTreeSet, HashMap};
use std::io::Read;

/// Frozen's item rows (DevUtils/tests/content/ContentReference.java, section "items").
fn frozen() -> Vec<String> {
    let mut text = String::new();
    flate2::read::GzDecoder::new(include_bytes!("../../../../test/resources/content/frozen-items.txt.gz").as_slice())
        .read_to_string(&mut text)
        .unwrap();
    text.lines().map(str::to_owned).collect()
}

/// The rows the observer prints for the catalog, except each item's Java class.
fn rows() -> (Vec<String>, BTreeSet<String>) {
    let component_ids: HashMap<_, _> = crate::content::component::definitions().iter().enumerate().map(|(i, d)| (d.name, i)).collect();
    let mut items = Vec::new();
    let mut aliases = BTreeSet::new();
    for (id, def) in definitions().iter().enumerate() {
        items.push(format!("item {id} minecraft:{} desc={} remainder={} features=[{}] block={}", def.name, def.description_id(),
            def.remainder.map_or("none".into(), |r| format!("minecraft:{r}x1")), def.required_features.join(", "),
            def.block.map_or("none".into(), |b| format!("minecraft:{b}"))));
        let mut components = def.default_components();
        components.sort_by_key(|(t, _)| component_ids[t]);
        for (t, v) in components {
            items.push(format!("item_component {id} minecraft:{t} {v}"));
        }
        for b in def.block.iter().chain(def.aliases) {
            aliases.insert(format!("block_item minecraft:{b} minecraft:{}", def.name));
        }
    }
    (items, aliases)
}

#[test]
fn every_item_matches_frozen_exactly() {
    let (items, aliases) = rows();
    let frozen = frozen();
    let expected: Vec<String> = frozen.iter().filter(|r| !r.starts_with("block_item "))
        .map(|r| match r.find(" class=") {
            Some(start) => format!("{}{}", &r[..start], &r[start + r[start + 1..].find(' ').unwrap() + 1..]),
            None => r.clone(),
        })
        // The observer marks bare keys its frozen-registry context cannot decode.
        .map(|r| r.replacen(" UNDECODABLE ", " ", 1))
        // Stored as the equivalent empty list; see ITEM-DEFINITIONS.md.
        .map(|r| r.replace(" minecraft:pot_decorations [\"minecraft:brick\",\"minecraft:brick\",\"minecraft:brick\",\"minecraft:brick\"]", " minecraft:pot_decorations []"))
        .collect();
    for (i, (a, b)) in items.iter().zip(&expected).enumerate() {
        assert_eq!(a, b, "row {i}");
    }
    assert_eq!(items.len(), expected.len());
    let frozen_aliases: BTreeSet<String> = frozen.into_iter().filter(|r| r.starts_with("block_item ")).collect();
    assert_eq!(aliases, frozen_aliases);
}

#[test]
fn names_blocks_and_component_types_are_valid() {
    let mut names: Vec<_> = definitions().iter().map(|d| d.name).collect();
    names.sort_unstable();
    names.dedup();
    assert_eq!(names.len(), definitions().len());
    let blocks: BTreeSet<_> = definitions().iter().flat_map(|d| d.block.iter().chain(d.aliases)).collect();
    assert_eq!(blocks.len(), definitions().iter().map(|d| d.block.iter().count() + d.aliases.len()).sum::<usize>(), "a block maps to two items");
    let types: HashMap<_, _> = crate::content::component::definitions().iter().map(|d| (d.name, d.persistent)).collect();
    for def in definitions() {
        for (t, _) in def.components.iter().chain(common()) {
            assert_eq!(types.get(t), Some(&true), "{} uses unknown or unsaved component {t}", def.name);
        }
        if let Some(r) = def.remainder {
            let position = |n| definitions().iter().position(|d| d.name == n).unwrap();
            assert!(position(r) < position(def.name), "{} remainder {r} registers later", def.name);
        }
    }
    assert!(export().lines().all(|l| l.split('\t').count() >= 2));
}
