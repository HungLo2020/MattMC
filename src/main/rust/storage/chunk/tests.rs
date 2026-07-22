use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::tape::{document_from_tape, document_to_tape};

use super::decoder::{
    decode_chunk_document, decode_unified_chunk_document, CURRENT_CHUNK_DATA_VERSION,
};
use super::error::ChunkErrorKind;
use super::model::BiomePaletteEntry;
use super::tape::{encode_chunk_tape, encode_unified_chunk_tape};
use super::ticks::{
    decode_scheduled_tick_tape, decode_scheduled_ticks_document, encode_scheduled_tick_tape,
    merge_scheduled_ticks_document, ChunkScheduledTicks, ScheduledTickRecord,
};
use super::writer::{
    document_from_typed_chunk_tape, document_from_typed_chunk_tape_for_position,
    merge_typed_chunk_document,
};

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

#[test]
fn unified_decode_returns_residual_without_section_owned_fields() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        2,
        3,
        vec![section(
            0,
            block_states(vec![block_state("minecraft:air")], None),
            biomes(vec!["minecraft:plains"], None),
            None,
            None,
        )],
        vec![heightmap("MOTION_BLOCKING", vec![7; 37])],
    );
    let mut document = document;
    let NbtTag::Compound(entries) = &mut document.root else {
        unreachable!();
    };
    entries.push(entry(
        "block_entities",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: vec![NbtTag::Compound(vec![entry(
                "id",
                NbtTag::String(JavaString::from_str("minecraft:chest")),
            )])],
        }),
    ));
    entries.push(entry(
        "mattmc:custom",
        NbtTag::String(JavaString::from_str("preserved")),
    ));

    let unified = decode_unified_chunk_document(&document, 2, 3).expect("decode");
    let tape = encode_unified_chunk_tape(&unified.sections, &unified.residual).expect("tape");
    assert!(!tape.is_empty());
    let NbtTag::Compound(residual) = &unified.residual.root else {
        panic!("expected residual compound");
    };

    assert!(find_entry(residual, "sections").is_none());
    assert!(find_entry(residual, "Heightmaps").is_none());
    assert!(find_entry(residual, "block_entities").is_some());
    assert!(find_entry(residual, "mattmc:custom").is_some());
    assert!(document_from_tape(
        &crate::storage::nbt::tape::document_to_tape(
            &unified.residual,
            crate::storage::nbt::limits::NbtLimits::defaults()
        )
        .unwrap(),
        crate::storage::nbt::limits::NbtLimits::defaults()
    )
    .is_ok());
}

#[test]
fn typed_write_merges_sections_heightmaps_and_residual_without_duplicates() {
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
            biomes(vec!["minecraft:plains"], None),
            Some(vec![1; 2048]),
            None,
        )],
        vec![heightmap("MOTION_BLOCKING", vec![7; 37])],
    );
    let unified = decode_unified_chunk_document(&document, 2, 3).expect("decode");
    let mut residual = unified.residual.clone();
    let NbtTag::Compound(entries) = &mut residual.root else {
        panic!("expected residual compound");
    };
    entries.push(entry("sections", NbtTag::Compound(Vec::new())));
    entries.push(entry("Heightmaps", NbtTag::Compound(Vec::new())));
    entries.push(entry(
        "mattmc:custom",
        NbtTag::String(JavaString::from_str("preserved")),
    ));

    let merged = merge_typed_chunk_document(
        &unified.sections,
        &residual,
        crate::storage::nbt::limits::NbtLimits::defaults(),
    )
    .expect("merge");
    let NbtTag::Compound(root) = &merged.root else {
        panic!("expected root compound");
    };

    assert_eq!(1, count_entries(root, "sections"));
    assert_eq!(1, count_entries(root, "Heightmaps"));
    assert_eq!(Some(&NbtTag::Int(2)), find_entry(root, "xPos"));
    assert_eq!(Some(&NbtTag::Int(3)), find_entry(root, "zPos"));
    assert!(find_entry(root, "mattmc:custom").is_some());
    assert!(matches!(
        find_entry(root, "sections"),
        Some(NbtTag::List(_))
    ));
    assert!(matches!(
        find_entry(root, "Heightmaps"),
        Some(NbtTag::Compound(_))
    ));
}

#[test]
fn typed_write_omits_empty_block_state_properties_like_java_serializer() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        0,
        0,
        vec![section(
            0,
            block_states(
                vec![
                    block_state_with_properties("minecraft:air", Vec::new()),
                    block_state_with_properties(
                        "minecraft:oak_log",
                        vec![entry("axis", NbtTag::String(JavaString::from_str("y")))],
                    ),
                ],
                Some(vec![0; 256]),
            ),
            biomes(vec!["minecraft:plains"], None),
            None,
            None,
        )],
        Vec::new(),
    );
    let unified = decode_unified_chunk_document(&document, 0, 0).expect("decode");
    let merged = merge_typed_chunk_document(
        &unified.sections,
        &unified.residual,
        crate::storage::nbt::limits::NbtLimits::defaults(),
    )
    .expect("merge");
    let NbtTag::Compound(root) = &merged.root else {
        panic!("expected root compound");
    };
    let Some(NbtTag::List(sections)) = find_entry(root, "sections") else {
        panic!("expected sections");
    };
    let NbtTag::Compound(section) = &sections.elements[0] else {
        panic!("expected section");
    };
    let Some(NbtTag::Compound(block_states)) = find_entry(section, "block_states") else {
        panic!("expected block_states");
    };
    let Some(NbtTag::List(palette)) = find_entry(block_states, "palette") else {
        panic!("expected palette");
    };
    let NbtTag::Compound(air) = &palette.elements[0] else {
        panic!("expected air");
    };
    let NbtTag::Compound(oak_log) = &palette.elements[1] else {
        panic!("expected oak_log");
    };

    assert!(find_entry(air, "Properties").is_none());
    assert!(matches!(
        find_entry(oak_log, "Properties"),
        Some(NbtTag::Compound(properties)) if !properties.is_empty()
    ));
}

#[test]
fn typed_write_tape_round_trips_to_current_version_document() {
    let document = chunk_document(
        CURRENT_CHUNK_DATA_VERSION,
        -5,
        9,
        vec![section(
            -4,
            block_states(vec![block_state("minecraft:air")], None),
            biomes(vec!["minecraft:plains", "minecraft:forest"], Some(vec![0])),
            None,
            Some(vec![15; 2048]),
        )],
        vec![heightmap("WORLD_SURFACE", vec![3; 37])],
    );
    let unified = decode_unified_chunk_document(&document, -5, 9).expect("decode");
    let tape = encode_unified_chunk_tape(&unified.sections, &unified.residual).expect("tape");
    let merged =
        document_from_typed_chunk_tape(&tape, crate::storage::nbt::limits::NbtLimits::defaults())
            .expect("write document");
    let encoded_tape =
        document_to_tape(&merged, crate::storage::nbt::limits::NbtLimits::defaults()).unwrap();
    assert!(document_from_tape(
        &encoded_tape,
        crate::storage::nbt::limits::NbtLimits::defaults()
    )
    .is_ok());
}

#[test]
fn typed_write_rejects_coordinate_mismatch() {
    let document = chunk_document(CURRENT_CHUNK_DATA_VERSION, 4, 5, Vec::new(), Vec::new());
    let unified = decode_unified_chunk_document(&document, 4, 5).expect("decode");
    let tape = encode_unified_chunk_tape(&unified.sections, &unified.residual).expect("tape");
    let error = document_from_typed_chunk_tape_for_position(
        &tape,
        Some((4, 6)),
        crate::storage::nbt::limits::NbtLimits::defaults(),
    )
    .expect_err("coordinate mismatch");
    assert_eq!(ChunkErrorKind::InvalidPosition, error.kind);
}

#[test]
fn typed_write_rejects_invalid_light_and_old_versions() {
    let old = chunk_document(CURRENT_CHUNK_DATA_VERSION - 1, 0, 0, Vec::new(), Vec::new());
    let unified = decode_unified_chunk_document(&old, 0, 0).expect("old decode");
    assert!(unified.sections.requires_dfu);
    let tape = encode_chunk_tape(&unified.sections).expect("old tape");
    let error =
        document_from_typed_chunk_tape(&tape, crate::storage::nbt::limits::NbtLimits::defaults())
            .expect_err("old typed write");
    assert_eq!(ChunkErrorKind::UnsupportedDataVersion, error.kind);

    let invalid = chunk_document(
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
    assert_eq!(
        ChunkErrorKind::InvalidLightArray,
        decode_unified_chunk_document(&invalid, 0, 0)
            .expect_err("invalid light")
            .kind
    );
}

#[test]
fn decodes_scheduled_ticks_filters_to_requested_chunk_and_preserves_order() {
    let mut document = chunk_document(CURRENT_CHUNK_DATA_VERSION, -1, 2, Vec::new(), Vec::new());
    let NbtTag::Compound(entries) = &mut document.root else {
        unreachable!();
    };
    entries.push(entry(
        "block_ticks",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: vec![
                tick("minecraft:stone", -16, 64, 32, 7, -3),
                tick("minecraft:dirt", -1, 65, 47, -2, 3),
                tick("minecraft:grass_block", 0, 66, 32, 9, 0),
            ],
        }),
    ));
    entries.push(entry(
        "fluid_ticks",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: vec![tick("minecraft:water", -15, -12, 40, 1, 2)],
        }),
    ));

    let ticks = decode_scheduled_ticks_document(&document, -1, 2).expect("decode ticks");

    assert!(!ticks.requires_dfu);
    assert_eq!(2, ticks.block_ticks.len());
    assert_eq!(
        "minecraft:stone",
        ticks.block_ticks[0]
            .id
            .to_string_lossless_if_valid()
            .unwrap()
    );
    assert_eq!(
        "minecraft:dirt",
        ticks.block_ticks[1]
            .id
            .to_string_lossless_if_valid()
            .unwrap()
    );
    assert_eq!(-2, ticks.block_ticks[1].delay);
    assert_eq!(3, ticks.block_ticks[1].priority);
    assert_eq!(1, ticks.fluid_ticks.len());

    let tape = encode_scheduled_tick_tape(&ticks).expect("encode tick tape");
    let round_trip = decode_scheduled_tick_tape(&tape).expect("decode tick tape");
    assert_eq!(ticks, round_trip);
}

#[test]
fn scheduled_tick_decode_reports_old_versions_as_dfu_required() {
    let document = chunk_document(CURRENT_CHUNK_DATA_VERSION - 1, 1, 1, Vec::new(), Vec::new());
    let ticks = decode_scheduled_ticks_document(&document, 1, 1).expect("old version");
    assert!(ticks.requires_dfu);
    assert_eq!(CURRENT_CHUNK_DATA_VERSION - 1, ticks.data_version);
    assert!(ticks.block_ticks.is_empty());
    assert!(ticks.fluid_ticks.is_empty());
}

#[test]
fn scheduled_tick_decode_rejects_malformed_records() {
    let mut document = chunk_document(CURRENT_CHUNK_DATA_VERSION, 0, 0, Vec::new(), Vec::new());
    let NbtTag::Compound(entries) = &mut document.root else {
        unreachable!();
    };
    entries.push(entry(
        "block_ticks",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: vec![NbtTag::Compound(vec![
                entry("i", NbtTag::String(JavaString::from_str("minecraft:stone"))),
                entry("x", NbtTag::Int(0)),
                entry("y", NbtTag::Int(64)),
                entry("z", NbtTag::Int(0)),
                entry("t", NbtTag::Int(1)),
                entry("p", NbtTag::String(JavaString::from_str("normal"))),
            ])],
        }),
    ));

    let error = decode_scheduled_ticks_document(&document, 0, 0).expect_err("bad priority");
    assert_eq!(ChunkErrorKind::WrongType, error.kind);
}

#[test]
fn scheduled_tick_write_replaces_only_tick_lists_and_preserves_residual_fields() {
    let mut residual = chunk_document(CURRENT_CHUNK_DATA_VERSION, 2, -3, Vec::new(), Vec::new());
    let NbtTag::Compound(entries) = &mut residual.root else {
        unreachable!();
    };
    entries.push(entry(
        "block_ticks",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: vec![tick("minecraft:old", 32, 64, -48, 1, 0)],
        }),
    ));
    entries.push(entry(
        "fluid_ticks",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: Vec::new(),
        }),
    ));
    entries.push(entry(
        "mattmc:custom",
        NbtTag::String(JavaString::from_str("preserved")),
    ));
    let typed = ChunkScheduledTicks {
        data_version: CURRENT_CHUNK_DATA_VERSION,
        chunk_x: 2,
        chunk_z: -3,
        requires_dfu: false,
        block_ticks: vec![ScheduledTickRecord {
            id: JavaString::from_str("minecraft:stone"),
            x: 33,
            y: 70,
            z: -47,
            delay: 5,
            priority: -1,
        }],
        fluid_ticks: vec![ScheduledTickRecord {
            id: JavaString::from_str("minecraft:water"),
            x: 34,
            y: -5,
            z: -33,
            delay: 6,
            priority: 2,
        }],
    };

    let merged = merge_scheduled_ticks_document(&residual, &typed, Some((2, -3))).expect("merge");
    let NbtTag::Compound(root) = &merged.root else {
        panic!("expected root compound");
    };

    assert_eq!(1, count_entries(root, "block_ticks"));
    assert_eq!(1, count_entries(root, "fluid_ticks"));
    assert!(find_entry(root, "mattmc:custom").is_some());
    let Some(NbtTag::List(block_ticks)) = find_entry(root, "block_ticks") else {
        panic!("expected block ticks");
    };
    assert_eq!(1, block_ticks.elements.len());
    let NbtTag::Compound(block_tick) = &block_ticks.elements[0] else {
        panic!("expected tick compound");
    };
    assert_eq!(
        Some(&NbtTag::String(JavaString::from_str("minecraft:stone"))),
        find_entry(block_tick, "i")
    );
    assert_eq!(Some(&NbtTag::Int(-1)), find_entry(block_tick, "p"));
}

#[test]
fn typed_chunk_write_tape_can_replace_scheduled_ticks() {
    let mut document = chunk_document(CURRENT_CHUNK_DATA_VERSION, 2, -3, Vec::new(), Vec::new());
    let NbtTag::Compound(entries) = &mut document.root else {
        unreachable!();
    };
    entries.push(entry(
        "block_ticks",
        NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: vec![tick("minecraft:old", 32, 64, -48, 1, 0)],
        }),
    ));
    let unified = decode_unified_chunk_document(&document, 2, -3).expect("decode");
    let residual_tape = crate::storage::nbt::tape::document_to_tape(
        &unified.residual,
        crate::storage::nbt::limits::NbtLimits::defaults(),
    )
    .expect("residual tape");
    let ticks = ChunkScheduledTicks {
        data_version: CURRENT_CHUNK_DATA_VERSION,
        chunk_x: 2,
        chunk_z: -3,
        requires_dfu: false,
        block_ticks: vec![ScheduledTickRecord {
            id: JavaString::from_str("minecraft:stone"),
            x: 33,
            y: 70,
            z: -47,
            delay: 5,
            priority: -1,
        }],
        fluid_ticks: vec![ScheduledTickRecord {
            id: JavaString::from_str("minecraft:water"),
            x: 34,
            y: -5,
            z: -33,
            delay: 6,
            priority: 2,
        }],
    };
    let tick_tape = encode_scheduled_tick_tape(&ticks).expect("tick tape");
    let tape = super::tape::encode_chunk_tape_with_residual_and_tick_bytes(
        &unified.sections,
        &residual_tape,
        &tick_tape,
    )
    .expect("chunk tape");

    let merged = document_from_typed_chunk_tape_for_position(
        &tape,
        Some((2, -3)),
        crate::storage::nbt::limits::NbtLimits::defaults(),
    )
    .expect("merge chunk and ticks");
    let NbtTag::Compound(root) = &merged.root else {
        panic!("expected root compound");
    };
    let Some(NbtTag::List(block_ticks)) = find_entry(root, "block_ticks") else {
        panic!("expected block ticks");
    };
    assert_eq!(1, block_ticks.elements.len());
    let NbtTag::Compound(block_tick) = &block_ticks.elements[0] else {
        panic!("expected tick compound");
    };
    assert_eq!(
        Some(&NbtTag::String(JavaString::from_str("minecraft:stone"))),
        find_entry(block_tick, "i")
    );
    assert!(find_entry(root, "fluid_ticks").is_some());
}

#[test]
fn scheduled_tick_write_rejects_wrong_chunk_and_priority() {
    let residual = chunk_document(CURRENT_CHUNK_DATA_VERSION, 0, 0, Vec::new(), Vec::new());
    let outside = ChunkScheduledTicks {
        data_version: CURRENT_CHUNK_DATA_VERSION,
        chunk_x: 0,
        chunk_z: 0,
        requires_dfu: false,
        block_ticks: vec![ScheduledTickRecord {
            id: JavaString::from_str("minecraft:stone"),
            x: 16,
            y: 64,
            z: 0,
            delay: 1,
            priority: 0,
        }],
        fluid_ticks: Vec::new(),
    };
    let error =
        merge_scheduled_ticks_document(&residual, &outside, Some((0, 0))).expect_err("outside");
    assert_eq!(ChunkErrorKind::InvalidPosition, error.kind);

    let bad_priority = ChunkScheduledTicks {
        block_ticks: vec![ScheduledTickRecord {
            x: 0,
            priority: 99,
            ..outside.block_ticks[0].clone()
        }],
        ..outside
    };
    let error = encode_scheduled_tick_tape(&bad_priority).expect_err("bad priority");
    assert_eq!(ChunkErrorKind::InvalidTick, error.kind);
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

fn block_state_with_properties(name: &str, properties: Vec<CompoundEntry>) -> NbtTag {
    NbtTag::Compound(vec![
        entry("Name", NbtTag::String(JavaString::from_str(name))),
        entry("Properties", NbtTag::Compound(properties)),
    ])
}

fn heightmap(name: &str, data: Vec<i64>) -> CompoundEntry {
    entry(name, NbtTag::LongArray(data))
}

fn tick(id: &str, x: i32, y: i32, z: i32, delay: i32, priority: i32) -> NbtTag {
    NbtTag::Compound(vec![
        entry("i", NbtTag::String(JavaString::from_str(id))),
        entry("x", NbtTag::Int(x)),
        entry("y", NbtTag::Int(y)),
        entry("z", NbtTag::Int(z)),
        entry("t", NbtTag::Int(delay)),
        entry("p", NbtTag::Int(priority)),
    ])
}

fn entry(name: &str, value: NbtTag) -> CompoundEntry {
    CompoundEntry {
        name: JavaString::from_str(name),
        value,
    }
}

fn find_entry<'a>(entries: &'a [CompoundEntry], name: &str) -> Option<&'a NbtTag> {
    entries
        .iter()
        .find(|entry| entry.name.units() == JavaString::from_str(name).units())
        .map(|entry| &entry.value)
}

fn count_entries(entries: &[CompoundEntry], name: &str) -> usize {
    entries
        .iter()
        .filter(|entry| entry.name.units() == JavaString::from_str(name).units())
        .count()
}
