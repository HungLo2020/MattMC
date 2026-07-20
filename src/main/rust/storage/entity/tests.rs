use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::writer::write_document;
use crate::storage::region::ffi::{
    mattmc_region_close, mattmc_region_open, NativeRegionOpenResult, NativeRegionWriteResult,
};
use crate::storage::region::format::COMPRESSION_RAW;
use crate::storage::region::write_chunk_payload;

use super::decoder::{decode_entity_document, CURRENT_ENTITY_DATA_VERSION};
use super::encoder::encode_entity_document_from_tape;
use super::error::EntityErrorKind;
use super::ffi::{
    mattmc_entity_decode_chunk_envelope_from_region, mattmc_entity_write_chunk_envelope_to_region,
    NativeEntityDecodeResult, NativeEntityWriteResult,
};
use super::tape::{decode_entity_tape, encode_entity_tape};

#[test]
fn decodes_current_version_entity_envelope() {
    let decoded =
        decode_entity_document(&sample_entity_document(CURRENT_ENTITY_DATA_VERSION)).unwrap();

    assert!(!decoded.requires_dfu);
    assert_eq!(CURRENT_ENTITY_DATA_VERSION, decoded.data_version);
    assert_eq!((2, 3), (decoded.chunk_x, decoded.chunk_z));
    assert_eq!(2, decoded.entities.len());
    assert_eq!("minecraft:pig", decoded.entities[0].id.as_deref().unwrap());
    assert_eq!(1, decoded.entities[0].passenger_count);
    assert_eq!(1, decoded.entities[0].passenger_depth);
    assert!(decoded.entities[0].uuid.is_some());
    assert!(decoded.entities[0].position.is_some());
    assert!(!decoded.entities[1].id_malformed);
}

#[test]
fn old_entity_chunks_report_requires_dfu_without_decoding_entities() {
    let decoded =
        decode_entity_document(&sample_entity_document(CURRENT_ENTITY_DATA_VERSION - 1)).unwrap();

    assert!(decoded.requires_dfu);
    assert_eq!(CURRENT_ENTITY_DATA_VERSION - 1, decoded.data_version);
    assert_eq!((2, 3), (decoded.chunk_x, decoded.chunk_z));
    assert!(decoded.entities.is_empty());
}

#[test]
fn unknown_and_malformed_ids_do_not_fail_the_chunk() {
    let mut document = sample_entity_document(CURRENT_ENTITY_DATA_VERSION);
    let NbtTag::Compound(root) = &mut document.root else {
        unreachable!();
    };
    let NbtTag::List(entities) = find_mut(root, "Entities").unwrap() else {
        unreachable!();
    };
    let NbtTag::Compound(first) = &mut entities.elements[0] else {
        unreachable!();
    };
    *find_mut(first, "id").unwrap() = NbtTag::Int(7);
    let NbtTag::Compound(second) = &mut entities.elements[1] else {
        unreachable!();
    };
    *find_mut(second, "id").unwrap() =
        NbtTag::String(JavaString::from_str("mattmc:not_registered"));

    let decoded = decode_entity_document(&document).unwrap();

    assert!(decoded.entities[0].id.is_none());
    assert!(decoded.entities[0].id_malformed);
    assert_eq!(
        "mattmc:not_registered",
        decoded.entities[1].id.as_deref().unwrap()
    );
}

#[test]
fn malformed_envelopes_fail_safely() {
    let mut document = sample_entity_document(CURRENT_ENTITY_DATA_VERSION);
    let NbtTag::Compound(root) = &mut document.root else {
        unreachable!();
    };
    *find_mut(root, "Position").unwrap() = NbtTag::String(JavaString::from_str("nope"));

    let error = decode_entity_document(&document).unwrap_err();

    assert_eq!(EntityErrorKind::InvalidPosition, error.kind);
}

#[test]
fn entity_tape_roundtrips_opaque_blobs_and_metadata() {
    let decoded =
        decode_entity_document(&sample_entity_document(CURRENT_ENTITY_DATA_VERSION)).unwrap();
    let tape = super::tape::encode_entity_tape(&decoded).unwrap();
    let roundtrip = decode_entity_tape(&tape).unwrap();

    assert_eq!(decoded.data_version, roundtrip.data_version);
    assert_eq!(decoded.chunk_x, roundtrip.chunk_x);
    assert_eq!(decoded.chunk_z, roundtrip.chunk_z);
    assert_eq!(decoded.entities.len(), roundtrip.entities.len());
    assert_eq!(decoded.entities[0].id, roundtrip.entities[0].id);
    assert_eq!(decoded.entities[0].nbt_tape, roundtrip.entities[0].nbt_tape);
}

#[test]
fn entity_tape_builds_current_version_chunk_document_for_write() {
    let decoded =
        decode_entity_document(&sample_entity_document(CURRENT_ENTITY_DATA_VERSION)).unwrap();
    let document = encode_entity_document_from_tape(&decoded, NbtLimits::defaults()).unwrap();
    let roundtrip = decode_entity_document(&document).unwrap();

    assert_eq!(CURRENT_ENTITY_DATA_VERSION, roundtrip.data_version);
    assert_eq!((2, 3), (roundtrip.chunk_x, roundtrip.chunk_z));
    assert_eq!(2, roundtrip.entities.len());
    assert_eq!(decoded.entities[0].nbt_tape, roundtrip.entities[0].nbt_tape);
}

#[test]
fn ffi_decodes_from_persistent_region_handle_and_sizes_output() {
    let dir = temp_dir("entity-ffi");
    std::fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");
    let raw_nbt = write_document(
        &sample_entity_document(CURRENT_ENTITY_DATA_VERSION),
        NbtLimits::defaults(),
    )
    .unwrap();
    write_chunk_payload(&region_path, 2, 3, COMPRESSION_RAW, &raw_nbt).unwrap();

    let path = region_path.to_string_lossy().into_owned();
    let handle = open_region_handle(path.as_bytes());
    let mut result = NativeEntityDecodeResult::default();
    let status = unsafe {
        mattmc_entity_decode_chunk_envelope_from_region(
            handle,
            2,
            3,
            std::ptr::null_mut(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(-4, status);
    assert!(result.output_len > 0);

    let mut output = vec![0; result.output_len as usize];
    let status = unsafe {
        mattmc_entity_decode_chunk_envelope_from_region(
            handle,
            2,
            3,
            output.as_mut_ptr(),
            output.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(0, status);
    assert_eq!(1, result.present);
    assert_eq!(0, result.requires_dfu);
    assert_eq!(2, result.entity_count);
    assert_eq!((2, 3), (result.chunk_x, result.chunk_z));
    let decoded = decode_entity_tape(&output).unwrap();
    assert_eq!(2, decoded.entities.len());

    close_region_handle(handle);
}

#[test]
fn ffi_writes_entity_envelope_tape_through_persistent_region_handle() {
    let dir = temp_dir("entity-write-ffi");
    std::fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");
    let decoded =
        decode_entity_document(&sample_entity_document(CURRENT_ENTITY_DATA_VERSION)).unwrap();
    let envelope = encode_entity_tape(&decoded).unwrap();

    let path = region_path.to_string_lossy().into_owned();
    let handle = open_region_handle(path.as_bytes());
    let mut write_result = NativeEntityWriteResult::default();
    let status = unsafe {
        mattmc_entity_write_chunk_envelope_to_region(
            handle,
            2,
            3,
            COMPRESSION_RAW as i32,
            envelope.as_ptr(),
            envelope.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut write_result,
        )
    };
    assert_eq!(0, status);
    assert_eq!(0, write_result.status);
    assert_eq!(2, write_result.entity_count);

    let mut decode_result = NativeEntityDecodeResult::default();
    let mut output = vec![0; 4096];
    let status = unsafe {
        mattmc_entity_decode_chunk_envelope_from_region(
            handle,
            2,
            3,
            output.as_mut_ptr(),
            output.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut decode_result,
        )
    };
    assert_eq!(0, status);
    assert_eq!(2, decode_result.entity_count);
    output.truncate(decode_result.output_len as usize);
    let roundtrip = decode_entity_tape(&output).unwrap();
    assert_eq!(decoded.entities[0].nbt_tape, roundtrip.entities[0].nbt_tape);

    close_region_handle(handle);
}

#[test]
fn ffi_entity_writer_rejects_malformed_and_wrong_position_tapes() {
    let dir = temp_dir("entity-write-bad-ffi");
    std::fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");
    let path = region_path.to_string_lossy().into_owned();
    let handle = open_region_handle(path.as_bytes());
    let mut result = NativeEntityWriteResult::default();
    let malformed = [1u8, 2, 3, 4];
    let status = unsafe {
        mattmc_entity_write_chunk_envelope_to_region(
            handle,
            2,
            3,
            COMPRESSION_RAW as i32,
            malformed.as_ptr(),
            malformed.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(-8, status);

    let decoded =
        decode_entity_document(&sample_entity_document(CURRENT_ENTITY_DATA_VERSION)).unwrap();
    let envelope = encode_entity_tape(&decoded).unwrap();
    let status = unsafe {
        mattmc_entity_write_chunk_envelope_to_region(
            handle,
            9,
            9,
            COMPRESSION_RAW as i32,
            envelope.as_ptr(),
            envelope.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(-8, status);
    assert_eq!(EntityErrorKind::InvalidPosition as i32, result.error_kind);

    close_region_handle(handle);
}

fn sample_entity_document(data_version: i32) -> NbtDocument {
    NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![
            entry("DataVersion", NbtTag::Int(data_version)),
            entry("Position", NbtTag::IntArray(vec![2, 3])),
            entry(
                "Entities",
                NbtTag::List(ListTag {
                    element_type: TagId::Compound,
                    elements: vec![
                        entity(
                            "minecraft:pig",
                            vec![
                                entry(
                                    "Passengers",
                                    NbtTag::List(ListTag {
                                        element_type: TagId::Compound,
                                        elements: vec![entity("minecraft:chicken", vec![])],
                                    }),
                                ),
                                entry(
                                    "UUID",
                                    NbtTag::IntArray(vec![0x1234_5678, -1, 0x0102_0304, 5]),
                                ),
                                entry(
                                    "Pos",
                                    NbtTag::List(ListTag {
                                        element_type: TagId::Double,
                                        elements: vec![
                                            NbtTag::Double(1.25f64.to_bits()),
                                            NbtTag::Double(64.0f64.to_bits()),
                                            NbtTag::Double((-2.5f64).to_bits()),
                                        ],
                                    }),
                                ),
                            ],
                        ),
                        entity("minecraft:item", vec![]),
                    ],
                }),
            ),
        ]),
    }
}

fn entity(id: &str, mut extra: Vec<CompoundEntry>) -> NbtTag {
    let mut entries = vec![entry("id", NbtTag::String(JavaString::from_str(id)))];
    entries.append(&mut extra);
    NbtTag::Compound(entries)
}

fn entry(name: &str, value: NbtTag) -> CompoundEntry {
    CompoundEntry {
        name: JavaString::from_str(name),
        value,
    }
}

fn find_mut<'a>(entries: &'a mut [CompoundEntry], name: &str) -> Option<&'a mut NbtTag> {
    entries
        .iter_mut()
        .find(|entry| entry.name.units() == JavaString::from_str(name).units())
        .map(|entry| &mut entry.value)
}

fn open_region_handle(path: &[u8]) -> u64 {
    let mut result = NativeRegionOpenResult::default();
    let status = unsafe { mattmc_region_open(path.as_ptr(), path.len() as u64, 0, &mut result) };
    assert_eq!(0, status);
    assert_eq!(0, result.status);
    result.handle
}

fn close_region_handle(handle: u64) {
    let mut result = NativeRegionWriteResult::default();
    let status = unsafe { mattmc_region_close(handle, &mut result) };
    assert_eq!(0, status);
    assert_eq!(0, result.status);
}

fn temp_dir(name: &str) -> std::path::PathBuf {
    let mut path = std::env::temp_dir();
    path.push(format!("mattmc-entity-{}-{}", name, std::process::id()));
    let _ = std::fs::remove_dir_all(&path);
    path
}
