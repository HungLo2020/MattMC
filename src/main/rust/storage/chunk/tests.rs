use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};

use super::decoder::{decode_chunk_document, CURRENT_CHUNK_DATA_VERSION};
use super::error::ChunkErrorKind;
use super::model::BiomePaletteEntry;
use super::tape::encode_chunk_tape;

#[test]
fn decodes_current_version_section_records() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        2,
        3,
        vec![section(
            0,
            block_states(
                vec![block_state("minecraft:air"), block_state("minecraft:stone")],
                Some(vec![0; 256]),
            ),
            biomes(vec!["minecraft:plains", "minecraft:forest"], Some(vec![0])),
            Some(vec![1; 2048]),
            Some(vec![2; 2048]),
        )],
        vec![heightmap("MOTION_BLOCKING", vec![7; 37])],
    );

    let chunk = decode_chunk_document(&document, 2, 3).expect("decode chunk");

    assert!(!chunk.requires_dfu);
    assert_eq!(CURRENT_CHUNK_DATA_VERSION, chunk.data_version);
    assert_eq!(2, chunk.chunk_x);
    assert_eq!(3, chunk.chunk_z);
    assert_eq!(
        "minecraft:full",
        chunk.status.to_string_lossless_if_valid().unwrap()
    );
    assert!(chunk.is_light_on);
    assert_eq!(1, chunk.sections.len());
    let section = &chunk.sections[0];
    assert_eq!(0, section.section_y);
    assert_eq!(2, section.block_palette.len());
    assert_eq!(256, section.block_data.len());
    assert_eq!(2, section.biome_palette.len());
    assert_eq!(1, section.biome_data.len());
    assert_eq!(2048, section.block_light.as_ref().unwrap().len());
    assert_eq!(2048, section.sky_light.as_ref().unwrap().len());
    assert_eq!(1, chunk.heightmaps.len());
    assert!(!encode_chunk_tape(&chunk).expect("typed tape").is_empty());
}

#[test]
fn missing_or_old_data_version_requires_dfu() {
    let missing = NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![]),
    };
    let decoded = decode_chunk_document(&missing, 0, 0).expect("missing version");
    assert!(decoded.requires_dfu);

    let old = chunk_document(CURRENT_CHUNK_DATA_VERSION - 1, 0, 0, Vec::new(), Vec::new());
    let decoded = decode_chunk_document(&old, 0, 0).expect("old version");
    assert!(decoded.requires_dfu);
    assert_eq!(CURRENT_CHUNK_DATA_VERSION - 1, decoded.data_version);
}

#[test]
fn rejects_coordinate_mismatch() {
    let document = chunk_document(CURRENT_CHUNK_DATA_VERSION, 5, 6, Vec::new(), Vec::new());
    let error = decode_chunk_document(&document, 5, 7).expect_err("coordinate mismatch");
    assert_eq!(ChunkErrorKind::InvalidPosition, error.kind);
}

#[test]
fn rejects_sections_outside_current_build_height() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        0,
        0,
        vec![section(
            20,
            block_states(vec![block_state("minecraft:air")], None),
            biomes(vec!["minecraft:plains"], None),
            None,
            None,
        )],
        Vec::new(),
    );
    let error = decode_chunk_document(&document, 0, 0).expect_err("invalid section y");
    assert_eq!(ChunkErrorKind::InvalidPosition, error.kind);
}

#[test]
fn rejects_invalid_light_length() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        0,
        0,
        vec![section(
            0,
            block_states(vec![block_state("minecraft:air")], None),
            biomes(vec!["minecraft:plains"], None),
            Some(vec![0; 7]),
            None,
        )],
        Vec::new(),
    );
    let error = decode_chunk_document(&document, 0, 0).expect_err("invalid light");
    assert_eq!(ChunkErrorKind::InvalidLightArray, error.kind);
}

#[test]
fn rejects_malformed_packed_arrays() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        0,
        0,
        vec![section(
            0,
            block_states(
                vec![block_state("minecraft:air"), block_state("minecraft:stone")],
                Some(vec![0; 2]),
            ),
            biomes(vec!["minecraft:plains"], None),
            None,
            None,
        )],
        Vec::new(),
    );
    let error = decode_chunk_document(&document, 0, 0).expect_err("invalid packed data");
    assert_eq!(ChunkErrorKind::InvalidPackedData, error.kind);
}

#[test]
fn preserves_biome_resource_identifier_units() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        0,
        0,
        vec![section(
            -4,
            block_states(vec![block_state("minecraft:air")], None),
            biomes(vec!["minecraft:plains"], None),
            None,
            None,
        )],
        Vec::new(),
    );

    let chunk = decode_chunk_document(&document, 0, 0).expect("decode");
    let BiomePaletteEntry::ResourceId(value) = &chunk.sections[0].biome_palette[0] else {
        panic!("expected biome resource id");
    };
    assert_eq!(
        "minecraft:plains",
        value.to_string_lossless_if_valid().unwrap()
    );
}

fn chunk_document(
    data_version: i32,
    x: i32,
    z: i32,
    sections: Vec<NbtTag>,
    heightmaps: Vec<CompoundEntry>,
) -> NbtDocument {
    NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![
            entry("DataVersion", NbtTag::Int(data_version)),
            entry("xPos", NbtTag::Int(x)),
            entry("zPos", NbtTag::Int(z)),
            entry("yPos", NbtTag::Int(-4)),
            entry(
                "Status",
                NbtTag::String(JavaString::from_str("minecraft:full")),
            ),
            entry("isLightOn", NbtTag::Byte(1)),
            entry("LastUpdate", NbtTag::Long(123)),
            entry("InhabitedTime", NbtTag::Long(456)),
            entry(
                "sections",
                NbtTag::List(ListTag {
                    element_type: TagId::Compound,
                    elements: sections,
                }),
            ),
            entry("Heightmaps", NbtTag::Compound(heightmaps)),
        ]),
    }
}

fn section(
    y: i8,
    block_states: NbtTag,
    biomes: NbtTag,
    block_light: Option<Vec<u8>>,
    sky_light: Option<Vec<u8>>,
) -> NbtTag {
    let mut entries = vec![
        entry("Y", NbtTag::Byte(y)),
        entry("block_states", block_states),
        entry("biomes", biomes),
    ];
    if let Some(bytes) = block_light {
        entries.push(entry(
            "BlockLight",
            NbtTag::ByteArray(bytes.into_iter().map(|value| value as i8).collect()),
        ));
    }
    if let Some(bytes) = sky_light {
        entries.push(entry(
            "SkyLight",
            NbtTag::ByteArray(bytes.into_iter().map(|value| value as i8).collect()),
        ));
    }
    NbtTag::Compound(entries)
}

fn block_states(palette: Vec<NbtTag>, data: Option<Vec<i64>>) -> NbtTag {
    let mut entries = vec![entry(
        "palette",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: palette,
        }),
    )];
    if let Some(data) = data {
        entries.push(entry("data", NbtTag::LongArray(data)));
    }
    NbtTag::Compound(entries)
}

fn biomes(palette: Vec<&str>, data: Option<Vec<i64>>) -> NbtTag {
    let mut entries = vec![entry(
        "palette",
        NbtTag::List(ListTag {
            element_type: TagId::String,
            elements: palette
                .into_iter()
                .map(|value| NbtTag::String(JavaString::from_str(value)))
                .collect(),
        }),
    )];
    if let Some(data) = data {
        entries.push(entry("data", NbtTag::LongArray(data)));
    }
    NbtTag::Compound(entries)
}

fn block_state(name: &str) -> NbtTag {
    NbtTag::Compound(vec![entry(
        "Name",
        NbtTag::String(JavaString::from_str(name)),
    )])
}

fn heightmap(name: &str, data: Vec<i64>) -> CompoundEntry {
    entry(name, NbtTag::LongArray(data))
}

fn entry(name: &str, value: NbtTag) -> CompoundEntry {
    CompoundEntry {
        name: JavaString::from_str(name),
        value,
    }
}
