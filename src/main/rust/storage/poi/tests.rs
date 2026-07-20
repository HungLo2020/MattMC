use std::env;
use std::fs;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::writer::write_document;
use crate::storage::region::ffi::{
    mattmc_region_close, mattmc_region_open, NativeRegionOpenResult, NativeRegionWriteResult,
    STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
};
use crate::storage::region::format::COMPRESSION_RAW;
use crate::storage::region::format::COMPRESSION_ZLIB;
use crate::storage::region::write_chunk_payload;

use super::decoder::{decode_poi_document, CURRENT_POI_DATA_VERSION};
use super::encoder::encode_poi_document;
use super::error::PoiErrorKind;
use super::ffi::{
    mattmc_poi_decode_chunk_from_region, mattmc_poi_write_chunk_to_region, NativePoiDecodeResult,
    NativePoiWriteResult,
};
use super::tape::{decode_poi_tape, encode_poi_tape};

#[test]
fn decodes_current_version_poi_sections() {
    let decoded = decode_poi_document(&sample_poi_document(CURRENT_POI_DATA_VERSION)).unwrap();

    assert_eq!(2, decoded.sections.len());
    assert_eq!(-1, decoded.sections[0].section_y);
    assert!(!decoded.sections[0].valid);
    assert_eq!(0, decoded.sections[0].records.len());
    assert_eq!(4, decoded.sections[1].section_y);
    assert!(decoded.sections[1].valid);
    assert_eq!(2, decoded.sections[1].records.len());
    assert_eq!("minecraft:armorer", decoded.sections[1].records[0].poi_type);
    assert_eq!(2, decoded.sections[1].records[0].free_tickets);
    assert_eq!("minecraft:meeting", decoded.sections[1].records[1].poi_type);
    assert_eq!(0, decoded.sections[1].records[1].free_tickets);
}

#[test]
fn rejects_old_schema_without_running_dfu() {
    let error =
        decode_poi_document(&sample_poi_document(CURRENT_POI_DATA_VERSION - 1)).unwrap_err();

    assert_eq!(PoiErrorKind::UnsupportedDataVersion, error.kind);
}

#[test]
fn rejects_malformed_records_and_invalid_type_ids() {
    let mut document = sample_poi_document(CURRENT_POI_DATA_VERSION);
    let root = root_entries_mut(&mut document);
    let sections = as_compound_mut(compound_entry_mut(root, "Sections"));
    let section = as_compound_mut(compound_entry_mut(sections, "4"));
    let records = compound_entry_mut(section, "Records");
    let NbtTag::List(list) = records else {
        panic!("records must be a list");
    };
    let NbtTag::Compound(first) = &mut list.elements[0] else {
        panic!("record must be a compound");
    };
    compound_entry_mut(first, "type")
        .clone_from(&NbtTag::String(JavaString::from_str("Minecraft:Invalid")));

    let error = decode_poi_document(&document).unwrap_err();
    assert_eq!(PoiErrorKind::InvalidPoiType, error.kind);

    let mut missing_pos = sample_poi_document(CURRENT_POI_DATA_VERSION);
    let root = root_entries_mut(&mut missing_pos);
    let sections = as_compound_mut(compound_entry_mut(root, "Sections"));
    let section = as_compound_mut(compound_entry_mut(sections, "4"));
    let records = compound_entry_mut(section, "Records");
    let NbtTag::List(list) = records else {
        panic!("records must be a list");
    };
    let NbtTag::Compound(first) = &mut list.elements[0] else {
        panic!("record must be a compound");
    };
    first.retain(|entry| entry.name.units() != JavaString::from_str("pos").units());

    let error = decode_poi_document(&missing_pos).unwrap_err();
    assert_eq!(PoiErrorKind::MissingField, error.kind);
}

#[test]
fn typed_buffer_contains_bulk_sections_and_records() {
    let decoded = decode_poi_document(&sample_poi_document(CURRENT_POI_DATA_VERSION)).unwrap();
    let tape = encode_poi_tape(&decoded).unwrap();
    let roundtrip = decode_poi_tape(&tape).unwrap();

    assert_eq!(b"MPOI", &tape[0..4]);
    assert_eq!(1, u16::from_le_bytes([tape[4], tape[5]]));
    assert_eq!(
        2,
        u32::from_le_bytes([tape[8], tape[9], tape[10], tape[11]])
    );
    assert!(tape
        .windows("minecraft:armorer".len())
        .any(|window| window == b"minecraft:armorer"));
    assert!(tape
        .windows("minecraft:meeting".len())
        .any(|window| window == b"minecraft:meeting"));
    assert_eq!(decoded, roundtrip);
}

#[test]
fn encodes_current_version_poi_document_from_typed_records() {
    let decoded = decode_poi_document(&sample_poi_document(CURRENT_POI_DATA_VERSION)).unwrap();
    let encoded = encode_poi_document(&decoded).unwrap();
    let roundtrip = decode_poi_document(&encoded).unwrap();

    assert_eq!(decoded, roundtrip);
}

#[test]
fn ffi_decodes_from_persistent_region_handle_and_sizes_output() {
    let dir = temp_dir("poi-ffi");
    fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");
    let raw_nbt = write_document(
        &sample_poi_document(CURRENT_POI_DATA_VERSION),
        NbtLimits::defaults(),
    )
    .unwrap();
    write_chunk_payload(&region_path, 0, 0, COMPRESSION_RAW, &raw_nbt).unwrap();

    let path = region_path.to_string_lossy().into_owned();
    let mut open_result = NativeRegionOpenResult::default();
    let open_status = unsafe {
        mattmc_region_open(
            path.as_ptr(),
            path.len() as u64,
            0,
            &mut open_result as *mut NativeRegionOpenResult,
        )
    };
    assert_eq!(STATUS_OK, open_status);
    assert_ne!(0, open_result.handle);

    let mut query = NativePoiDecodeResult::default();
    let query_status = unsafe {
        mattmc_poi_decode_chunk_from_region(
            open_result.handle,
            0,
            0,
            std::ptr::null_mut(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut query as *mut NativePoiDecodeResult,
        )
    };
    assert_eq!(STATUS_OUTPUT_TOO_SMALL, query_status);
    assert_eq!(1, query.present);
    assert!(query.output_len > 0);
    assert_eq!(2, query.section_count);
    assert_eq!(2, query.record_count);

    let mut output = vec![0u8; query.output_len as usize];
    let mut result = NativePoiDecodeResult::default();
    let status = unsafe {
        mattmc_poi_decode_chunk_from_region(
            open_result.handle,
            0,
            0,
            output.as_mut_ptr(),
            output.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result as *mut NativePoiDecodeResult,
        )
    };
    assert_eq!(STATUS_OK, status);
    assert_eq!(query.output_len, result.output_len);
    assert_eq!(b"MPOI", &output[0..4]);

    let mut close_result = NativeRegionWriteResult::default();
    let close_status = unsafe {
        mattmc_region_close(
            open_result.handle,
            &mut close_result as *mut NativeRegionWriteResult,
        )
    };
    assert_eq!(STATUS_OK, close_status);
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn ffi_reports_absent_chunk_without_buffer() {
    let dir = temp_dir("poi-absent");
    fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");
    write_chunk_payload(&region_path, 0, 0, COMPRESSION_RAW, &minimal_poi_nbt()).unwrap();

    let path = region_path.to_string_lossy().into_owned();
    let mut open_result = NativeRegionOpenResult::default();
    unsafe {
        mattmc_region_open(
            path.as_ptr(),
            path.len() as u64,
            0,
            &mut open_result as *mut NativeRegionOpenResult,
        );
    }

    let mut result = NativePoiDecodeResult::default();
    let status = unsafe {
        mattmc_poi_decode_chunk_from_region(
            open_result.handle,
            1,
            0,
            std::ptr::null_mut(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result as *mut NativePoiDecodeResult,
        )
    };
    assert_eq!(STATUS_OK, status);
    assert_eq!(0, result.present);
    assert_eq!(0, result.output_len);

    let mut close_result = NativeRegionWriteResult::default();
    unsafe {
        mattmc_region_close(
            open_result.handle,
            &mut close_result as *mut NativeRegionWriteResult,
        );
    }
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn ffi_writes_typed_poi_tape_through_persistent_region_handle() {
    let dir = temp_dir("poi-write-ffi");
    fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");
    let chunk = decode_poi_document(&sample_poi_document(CURRENT_POI_DATA_VERSION)).unwrap();
    let tape = encode_poi_tape(&chunk).unwrap();

    let path = region_path.to_string_lossy().into_owned();
    let mut open_result = NativeRegionOpenResult::default();
    let open_status = unsafe {
        mattmc_region_open(
            path.as_ptr(),
            path.len() as u64,
            0,
            &mut open_result as *mut NativeRegionOpenResult,
        )
    };
    assert_eq!(STATUS_OK, open_status);
    assert_ne!(0, open_result.handle);

    let mut write_result = NativePoiWriteResult::default();
    let write_status = unsafe {
        mattmc_poi_write_chunk_to_region(
            open_result.handle,
            0,
            0,
            COMPRESSION_ZLIB as i32,
            tape.as_ptr(),
            tape.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut write_result as *mut NativePoiWriteResult,
        )
    };
    assert_eq!(STATUS_OK, write_status);
    assert_eq!(1, write_result.present);
    assert_eq!(COMPRESSION_ZLIB as i32, write_result.compression_id);
    assert_eq!(2, write_result.section_count);
    assert_eq!(2, write_result.record_count);
    assert!(write_result.compressed_len > 0);
    assert!(write_result.decompressed_len > 0);
    assert_ne!(0, write_result.fingerprint);

    let mut query = NativePoiDecodeResult::default();
    let query_status = unsafe {
        mattmc_poi_decode_chunk_from_region(
            open_result.handle,
            0,
            0,
            std::ptr::null_mut(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut query as *mut NativePoiDecodeResult,
        )
    };
    assert_eq!(STATUS_OUTPUT_TOO_SMALL, query_status);
    let mut output = vec![0u8; query.output_len as usize];
    let mut decode_result = NativePoiDecodeResult::default();
    let decode_status = unsafe {
        mattmc_poi_decode_chunk_from_region(
            open_result.handle,
            0,
            0,
            output.as_mut_ptr(),
            output.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut decode_result as *mut NativePoiDecodeResult,
        )
    };
    assert_eq!(STATUS_OK, decode_status);
    assert_eq!(chunk, decode_poi_tape(&output).unwrap());

    let mut close_result = NativeRegionWriteResult::default();
    let close_status = unsafe {
        mattmc_region_close(
            open_result.handle,
            &mut close_result as *mut NativeRegionWriteResult,
        )
    };
    assert_eq!(STATUS_OK, close_status);
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn ffi_rejects_malformed_write_tape_without_region_mutation() {
    let dir = temp_dir("poi-write-bad-tape");
    fs::create_dir_all(&dir).unwrap();
    let region_path = dir.join("r.0.0.mca");

    let path = region_path.to_string_lossy().into_owned();
    let mut open_result = NativeRegionOpenResult::default();
    unsafe {
        mattmc_region_open(
            path.as_ptr(),
            path.len() as u64,
            0,
            &mut open_result as *mut NativeRegionOpenResult,
        );
    }
    let mut result = NativePoiWriteResult::default();
    let status = unsafe {
        mattmc_poi_write_chunk_to_region(
            open_result.handle,
            0,
            0,
            COMPRESSION_ZLIB as i32,
            b"bad".as_ptr(),
            3,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result as *mut NativePoiWriteResult,
        )
    };
    assert_ne!(STATUS_OK, status);
    assert_eq!(0, result.present);
    assert_eq!(PoiErrorKind::Overflow as i32, result.error_kind);

    let mut absent = NativePoiDecodeResult::default();
    let read_status = unsafe {
        mattmc_poi_decode_chunk_from_region(
            open_result.handle,
            0,
            0,
            std::ptr::null_mut(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut absent as *mut NativePoiDecodeResult,
        )
    };
    assert_eq!(STATUS_OK, read_status);
    assert_eq!(0, absent.present);

    let mut close_result = NativeRegionWriteResult::default();
    unsafe {
        mattmc_region_close(
            open_result.handle,
            &mut close_result as *mut NativeRegionWriteResult,
        );
    }
    fs::remove_dir_all(dir).unwrap();
}

fn sample_poi_document(data_version: i32) -> NbtDocument {
    NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![
            entry("DataVersion", NbtTag::Int(data_version)),
            entry(
                "Sections",
                NbtTag::Compound(vec![
                    entry(
                        "4",
                        NbtTag::Compound(vec![
                            entry("Valid", NbtTag::Byte(1)),
                            entry(
                                "Records",
                                NbtTag::List(ListTag {
                                    element_type: TagId::Compound,
                                    elements: vec![
                                        record(10, 64, 11, "minecraft:armorer", Some(2)),
                                        record(-2, 65, 17, "minecraft:meeting", None),
                                    ],
                                }),
                            ),
                        ]),
                    ),
                    entry(
                        "-1",
                        NbtTag::Compound(vec![entry(
                            "Records",
                            NbtTag::List(ListTag {
                                element_type: TagId::Compound,
                                elements: Vec::new(),
                            }),
                        )]),
                    ),
                ]),
            ),
        ]),
    }
}

fn minimal_poi_nbt() -> Vec<u8> {
    write_document(
        &sample_poi_document(CURRENT_POI_DATA_VERSION),
        NbtLimits::defaults(),
    )
    .unwrap()
}

fn record(x: i32, y: i32, z: i32, poi_type: &str, free_tickets: Option<i32>) -> NbtTag {
    let mut entries = vec![
        entry("pos", NbtTag::IntArray(vec![x, y, z])),
        entry("type", NbtTag::String(JavaString::from_str(poi_type))),
    ];
    if let Some(free_tickets) = free_tickets {
        entries.push(entry("free_tickets", NbtTag::Int(free_tickets)));
    }
    NbtTag::Compound(entries)
}

fn entry(name: &str, value: NbtTag) -> CompoundEntry {
    CompoundEntry {
        name: JavaString::from_str(name),
        value,
    }
}

fn root_entries_mut(document: &mut NbtDocument) -> &mut Vec<CompoundEntry> {
    let NbtTag::Compound(entries) = &mut document.root else {
        panic!("root must be a compound");
    };
    entries
}

fn compound_entry_mut<'a>(entries: &'a mut [CompoundEntry], name: &str) -> &'a mut NbtTag {
    entries
        .iter_mut()
        .find(|entry| entry.name.units() == JavaString::from_str(name).units())
        .map(|entry| &mut entry.value)
        .unwrap()
}

fn as_compound_mut(tag: &mut NbtTag) -> &mut Vec<CompoundEntry> {
    let NbtTag::Compound(entries) = tag else {
        panic!("tag must be a compound");
    };
    entries
}

fn temp_dir(prefix: &str) -> PathBuf {
    let unique = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    env::temp_dir().join(format!("{prefix}-{unique}"))
}
