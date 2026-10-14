use super::*;
use std::io::Read;

/// Frozen's dump (DevUtils/tests/content/EntityTypeReference.java): every
/// value as "decimal/hex bits", so the comparison is exact.
fn frozen() -> Vec<String> {
    let mut text = String::new();
    flate2::read::GzDecoder::new(include_bytes!("../../../../test/resources/content/frozen-entity-types.txt.gz").as_slice())
        .read_to_string(&mut text)
        .unwrap();
    text.lines().map(str::to_owned).collect()
}
fn field<'a>(row: &'a str, key: &str) -> &'a str {
    let start = row.find(&format!(" {key}=")).unwrap_or_else(|| panic!("{key} missing in {row}")) + key.len() + 2;
    let rest = &row[start..];
    if rest.starts_with('[') { &rest[..rest.find(']').unwrap() + 1] } else { rest.split(' ').next().unwrap() }
}
fn bits(token: &str) -> &str {
    token.split('/').nth(1).unwrap()
}

#[test]
fn every_definition_matches_frozen_exactly() {
    let rows = frozen();
    assert_eq!(rows.len(), definitions().len());
    for (id, (row, def)) in rows.iter().zip(definitions()).enumerate() {
        let head: Vec<_> = row.splitn(3, ' ').collect();
        assert_eq!(head[0], id.to_string());
        assert_eq!(head[1], format!("minecraft:{}", def.name), "row {id}");
        assert_eq!(field(row, "category"), def.category.serialized_name(), "{}", def.name);
        for (key, value) in [("serialize", def.serialize), ("summon", def.summon), ("fireImmune", def.fire_immune),
            ("spawnFar", def.spawn_far), ("peaceful", def.peaceful)] {
            assert_eq!(field(row, key), value.to_string(), "{} {key}", def.name);
        }
        let immune: Vec<_> = def.immune_to.to_vec();
        assert_eq!(field(row, "immuneTo"), format!("[{}]", immune.join(", ")), "{}", def.name);
        for (key, value) in [("width", def.width), ("height", def.height), ("eye", def.eye_height()), ("spawnScale", def.spawn_scale)] {
            assert_eq!(bits(field(row, key)), format!("{:x}", value.to_bits()), "{} {key}", def.name);
        }
        assert_eq!(field(row, "fixed"), "false");
        for (key, kind) in [("passenger", Attachment::Passenger), ("vehicle", Attachment::Vehicle),
            ("name_tag", Attachment::NameTag), ("warden_chest", Attachment::WardenChest)] {
            let expected: Vec<String> = field(row, key).trim_matches(['[', ']']).split(", ")
                .filter(|p| !p.is_empty()).map(|p| p.split(',').map(|v| bits(v).to_owned()).collect::<Vec<_>>().join(",")).collect();
            let actual: Vec<String> = def.attachment_points(kind).iter()
                .map(|p| p.iter().map(|v| format!("{:x}", v.to_bits())).collect::<Vec<_>>().join(",")).collect();
            assert_eq!(actual, expected, "{} {key}", def.name);
        }
        assert_eq!(field(row, "tracking"), def.tracking_range.to_string(), "{}", def.name);
        assert_eq!(field(row, "interval"), def.update_interval.to_string(), "{}", def.name);
        assert_eq!(field(row, "description"), format!("entity.minecraft.{}", def.name));
        assert_eq!(field(row, "loot"), if def.loot_table { format!("minecraft:entities/{}", def.name) } else { "none".into() });
        assert_eq!(field(row, "features"), format!("[{}]", def.required_features.join(", ")), "{}", def.name);
    }
}

#[test]
fn names_are_unique_and_the_export_table_parses() {
    let mut names: Vec<_> = definitions().iter().map(|d| d.name).collect();
    names.sort_unstable();
    names.dedup();
    assert_eq!(names.len(), definitions().len());
    let table = export();
    assert_eq!(&table[..4], &EXPORT_MAGIC.to_le_bytes());
    let needed = unsafe { mattmc_entity_types_export(std::ptr::null_mut(), 0) };
    assert_eq!(needed as usize, table.len());
}
