use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use flate2::write::{GzEncoder, ZlibEncoder};
use flate2::Compression;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::nbt::tape::{document_from_tape, document_to_tape};

use super::decompress::{decompress_region_payload, encode_lz4_java_block_stream_for_test};
use super::error::RegionErrorKind;
use super::ffi::{
    mattmc_region_close, mattmc_region_delete_chunk, mattmc_region_flush,
    mattmc_region_handle_delete_chunk, mattmc_region_handle_flush,
    mattmc_region_handle_read_chunk_nbt_tape, mattmc_region_handle_read_chunk_payload,
    mattmc_region_handle_write_chunk_nbt_tape, mattmc_region_handle_write_chunk_payload,
    mattmc_region_open, mattmc_region_read_chunk_nbt_fingerprint, mattmc_region_read_chunk_payload,
    mattmc_region_write_chunk_payload, NativeRegionNbtResult, NativeRegionOpenResult,
    NativeRegionPayloadResult, NativeRegionTapeResult, NativeRegionWriteResult,
    STATUS_DECOMPRESSION_ERROR, STATUS_INVALID_ARGUMENT, STATUS_INVALID_HANDLE, STATUS_NBT_ERROR,
    STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
};
use super::format::{
    local_chunk_index, CHUNK_HEADER_BYTES, COMPRESSION_CUSTOM, COMPRESSION_GZIP, COMPRESSION_LZ4,
    COMPRESSION_RAW, COMPRESSION_ZLIB, EXTERNAL_STREAM_FLAG, HEADER_BYTES, SECTOR_BYTES,
};
use super::writer::write_chunk_payload;
use super::{delete_chunk_payload, flush_region, read_chunk_nbt_fingerprint, read_chunk_payload};

static NEXT_DIR: AtomicU64 = AtomicU64::new(1);

#[test]
fn reads_internal_payload_with_metadata() {
    let dir = temp_dir("internal-payload");
    let region = dir.join("r.0.0.mca");
    let payload = b"compressed bytes".to_vec();
    write_region(
        &region,
        &[entry(2, 1, 12345, COMPRESSION_ZLIB, &payload, None)],
    );

    let result = read_chunk_payload(&region, 0, 0).unwrap().unwrap();

    assert_eq!(12345, result.timestamp);
    assert_eq!(COMPRESSION_ZLIB, result.compression_id);
    assert!(!result.external);
    assert_eq!(payload, result.payload);
}

#[test]
fn maps_global_chunk_coordinates_to_local_region_entries() {
    let dir = temp_dir("local-coordinates");
    let region = dir.join("r.-1.-1.mca");
    write_region(
        &region,
        &[entry(2, 1, 777, COMPRESSION_RAW, b"negative local", None)],
    );

    let result = read_chunk_payload(&region, -32, -32).unwrap().unwrap();

    assert_eq!(777, result.timestamp);
    assert_eq!(b"negative local".to_vec(), result.payload);
}

#[test]
fn absent_chunk_is_not_an_error() {
    let dir = temp_dir("absent");
    let region = dir.join("r.0.0.mca");
    write_region(&region, &[]);

    assert!(read_chunk_payload(&region, 0, 0).unwrap().is_none());
    assert!(read_chunk_payload(&dir.join("missing.mca"), 0, 0)
        .unwrap()
        .is_none());
}

#[test]
fn supports_compression_ids_as_metadata_without_decompressing() {
    let dir = temp_dir("formats");
    let ids = [
        COMPRESSION_GZIP,
        COMPRESSION_ZLIB,
        COMPRESSION_RAW,
        COMPRESSION_LZ4,
    ];
    for (i, id) in ids.into_iter().enumerate() {
        let region = dir.join(format!("r.{}.{}.mca", i, i));
        let payload = vec![id, id.wrapping_add(1), id.wrapping_add(2)];
        write_region(
            &region,
            &[entry(2, 1, 10 + i as u32, id, &payload, Some(i))],
        );
        let result = read_chunk_payload(&region, i as i32, 0).unwrap().unwrap();
        assert_eq!(id, result.compression_id);
        assert_eq!(payload, result.payload);
    }
}

#[test]
fn reads_external_payload_from_mcc_file() {
    let dir = temp_dir("external");
    let region = dir.join("r.0.0.mca");
    let external_payload = b"external compressed payload".to_vec();
    fs::write(dir.join("c.0.0.mcc"), &external_payload).unwrap();
    write_region(&region, &[external_entry(2, 1, 44, COMPRESSION_ZLIB, 0, 0)]);

    let result = read_chunk_payload(&region, 0, 0).unwrap().unwrap();

    assert!(result.external);
    assert_eq!(COMPRESSION_ZLIB, result.compression_id);
    assert_eq!(external_payload, result.payload);
}

#[test]
fn rejects_corrupt_headers_and_allocations() {
    let dir = temp_dir("corrupt");
    let truncated = dir.join("truncated.mca");
    fs::write(&truncated, vec![0u8; HEADER_BYTES - 1]).unwrap();
    assert_error(&truncated, RegionErrorKind::TruncatedHeader);

    let inside_header = dir.join("inside-header.mca");
    write_manual_region(
        &inside_header,
        packed_location(1, 1),
        0,
        &[0u8; SECTOR_BYTES],
    );
    assert!(read_chunk_payload(&inside_header, 0, 0).unwrap().is_none());
    assert_error_on_write(&inside_header, RegionErrorKind::OffsetInsideHeader);

    let zero_count = dir.join("zero-count.mca");
    write_manual_region(&zero_count, packed_location(2, 0), 0, &[0u8; SECTOR_BYTES]);
    assert!(read_chunk_payload(&zero_count, 0, 0).unwrap().is_none());
    assert_error_on_write(&zero_count, RegionErrorKind::ZeroSectorCount);

    let out_of_bounds = dir.join("out-of-bounds.mca");
    write_header_only(&out_of_bounds, packed_location(3, 1), 0);
    assert!(read_chunk_payload(&out_of_bounds, 0, 0).unwrap().is_none());
    assert_error_on_write(&out_of_bounds, RegionErrorKind::OutOfBoundsSector);

    let overlap = dir.join("overlap.mca");
    write_two_overlapping_entries(&overlap);
    assert!(read_chunk_payload(&overlap, 0, 0).unwrap().is_none());
    assert_error_on_write(&overlap, RegionErrorKind::OverlappingSectors);
}

#[test]
fn read_tolerates_java_null_chunk_payloads() {
    let dir = temp_dir("corrupt-payload");

    let zero_len = dir.join("zero-length.mca");
    write_region_with_record(&zero_len, 2, 1, 0, &[0, 0, 0, 0, COMPRESSION_RAW]);
    assert!(read_chunk_payload(&zero_len, 0, 0).unwrap().is_none());

    let negative_len = dir.join("negative-length.mca");
    write_region_with_record(&negative_len, 2, 1, 0, &[0x80, 0, 0, 0, COMPRESSION_RAW]);
    assert!(read_chunk_payload(&negative_len, 0, 0).unwrap().is_none());

    let oversize = dir.join("oversize.mca");
    write_region_with_record(
        &oversize,
        2,
        1,
        0,
        &[0, 0, 0x20, 0, COMPRESSION_RAW, 1, 2, 3],
    );
    assert!(read_chunk_payload(&oversize, 0, 0).unwrap().is_none());

    let invalid_compression = dir.join("invalid-compression.mca");
    write_region(
        &invalid_compression,
        &[entry(2, 1, 0, 99, b"payload", None)],
    );
    assert!(read_chunk_payload(&invalid_compression, 0, 0)
        .unwrap()
        .is_none());

    let custom_compression = dir.join("custom-compression.mca");
    write_region(
        &custom_compression,
        &[entry(2, 1, 0, COMPRESSION_CUSTOM, b"payload", None)],
    );
    assert!(read_chunk_payload(&custom_compression, 0, 0)
        .unwrap()
        .is_none());
}

#[test]
fn read_matches_java_partial_final_sector_from_new_world_region() {
    let dir = temp_dir("new-world-partial-final-sector");
    let region = dir.join("r.1.-1.mca");
    let first_sector = 1659;
    let sector_count = 4;
    let file_len = 6_809_170usize;
    let sector_start = first_sector as usize * SECTOR_BYTES;
    let declared_len = 13_902u32;
    let payload_len = declared_len as usize - 1;
    let mut bytes = vec![0u8; file_len];
    let chunk_index = local_chunk_index(47, -15);
    write_u32_be(
        &mut bytes,
        chunk_index * 4,
        packed_location(first_sector, sector_count),
    );
    write_u32_be(&mut bytes, HEADER_BYTES / 2 + chunk_index * 4, 123);
    write_u32_be(&mut bytes, sector_start, declared_len);
    bytes[sector_start + 4] = COMPRESSION_ZLIB;
    for i in 0..payload_len {
        bytes[sector_start + CHUNK_HEADER_BYTES + i] = (i & 0xff) as u8;
    }
    fs::write(&region, bytes).unwrap();

    let result = read_chunk_payload(&region, 47, -15).unwrap().unwrap();

    assert_eq!(123, result.timestamp);
    assert_eq!(COMPRESSION_ZLIB, result.compression_id);
    assert_eq!(payload_len, result.payload.len());
}

#[test]
fn rejects_invalid_external_payloads() {
    let dir = temp_dir("bad-external");
    let missing = dir.join("missing.mca");
    write_region(&missing, &[external_entry(2, 1, 0, COMPRESSION_ZLIB, 0, 0)]);
    assert_error(&missing, RegionErrorKind::MissingExternalFile);

    let empty = dir.join("empty.mca");
    fs::write(dir.join("c.1.0.mcc"), []).unwrap();
    write_region(&empty, &[external_entry(2, 1, 0, COMPRESSION_ZLIB, 1, 0)]);
    assert_error_for_chunk(&empty, 1, 0, RegionErrorKind::TruncatedExternalFile);

    let invalid_stub = dir.join("invalid-stub.mca");
    fs::write(dir.join("c.2.0.mcc"), b"external bytes").unwrap();
    write_region(
        &invalid_stub,
        &[external_entry(2, 1, 0, COMPRESSION_ZLIB, 2, 0).with_stub_payload_len(1)],
    );
    let result = read_chunk_payload(&invalid_stub, 2, 0).unwrap().unwrap();
    assert_eq!(b"external bytes".to_vec(), result.payload);
}

#[test]
fn ffi_reports_required_length_and_copies_payload() {
    let dir = temp_dir("ffi");
    let region = dir.join("r.0.0.mca");
    write_region(
        &region,
        &[entry(2, 1, 99, COMPRESSION_ZLIB, b"ffi payload", None)],
    );
    let path = region.to_string_lossy().as_bytes().to_vec();
    let mut result = NativeRegionPayloadResult::default();

    let status = unsafe {
        mattmc_region_read_chunk_payload(
            path.as_ptr(),
            path.len() as u64,
            0,
            0,
            std::ptr::null_mut(),
            0,
            &mut result,
        )
    };

    assert_eq!(STATUS_OUTPUT_TOO_SMALL, status);
    assert_eq!(b"ffi payload".len() as u64, result.output_len);
    assert_eq!(1, result.present);
    assert_eq!(99, result.timestamp);

    let mut output = vec![0; result.output_len as usize];
    let status = unsafe {
        mattmc_region_read_chunk_payload(
            path.as_ptr(),
            path.len() as u64,
            0,
            0,
            output.as_mut_ptr(),
            output.len() as u64,
            &mut result,
        )
    };

    assert_eq!(STATUS_OK, status);
    assert_eq!(b"ffi payload".to_vec(), output);
}

#[test]
fn ffi_rejects_malformed_inputs() {
    let mut result = NativeRegionPayloadResult::default();
    let status = unsafe {
        mattmc_region_read_chunk_payload(
            std::ptr::null(),
            1,
            0,
            0,
            std::ptr::null_mut(),
            0,
            &mut result,
        )
    };
    assert_eq!(STATUS_INVALID_ARGUMENT, status);

    let invalid_utf8 = [0xFFu8];
    let status = unsafe {
        mattmc_region_read_chunk_payload(
            invalid_utf8.as_ptr(),
            invalid_utf8.len() as u64,
            0,
            0,
            std::ptr::null_mut(),
            0,
            &mut result,
        )
    };
    assert_eq!(STATUS_INVALID_ARGUMENT, status);
    assert_eq!(RegionErrorKind::PathEncoding as i32, result.error_kind);
}

#[test]
fn writer_creates_replaces_reclaims_and_deletes_internal_chunks() {
    let dir = temp_dir("writer-internal");
    let region = dir.join("r.0.0.mca");
    let small = minimal_nbt_document();
    let other = tiny_nbt_with_name("other");
    let large = vec![0x42; SECTOR_BYTES * 2 + 100];

    let first = write_chunk_payload(&region, 0, 0, COMPRESSION_RAW, &small).unwrap();
    assert_eq!(2, first.first_sector);
    assert_eq!(1, first.sector_count);
    assert_eq!(
        small,
        read_chunk_payload(&region, 0, 0).unwrap().unwrap().payload
    );

    let second = write_chunk_payload(&region, 1, 0, COMPRESSION_RAW, &other).unwrap();
    assert_eq!(3, second.first_sector);
    assert_eq!(1, second.sector_count);

    let grown = write_chunk_payload(&region, 0, 0, COMPRESSION_RAW, &large).unwrap();
    assert_eq!(4, grown.first_sector);
    assert_eq!(3, grown.sector_count);
    assert_eq!(
        large,
        read_chunk_payload(&region, 0, 0).unwrap().unwrap().payload
    );

    let shrunk = write_chunk_payload(&region, 0, 0, COMPRESSION_RAW, &small).unwrap();
    assert_eq!(2, shrunk.first_sector);
    assert_eq!(1, shrunk.sector_count);

    let deleted = delete_chunk_payload(&region, 1, 0).unwrap();
    assert!(!deleted.present);
    assert!(read_chunk_payload(&region, 1, 0).unwrap().is_none());

    let rewritten = write_chunk_payload(&region, 1, 0, COMPRESSION_RAW, &other).unwrap();
    assert_eq!(3, rewritten.first_sector);
    flush_region(&region).unwrap();
    assert_eq!(
        other,
        read_chunk_payload(&region, 1, 0).unwrap().unwrap().payload
    );
}

#[test]
fn writer_handles_external_transition_and_semantic_reopen() {
    let dir = temp_dir("writer-external");
    let region = dir.join("r.0.0.mca");
    let large = large_raw_nbt_document(1_100_000);
    let small = minimal_nbt_document();

    let external = write_chunk_payload(&region, 0, 0, COMPRESSION_RAW, &large).unwrap();
    assert!(external.external);
    assert_eq!(1, external.sector_count);
    assert!(dir.join("c.0.0.mcc").is_file());
    assert!(read_chunk_payload(&region, 0, 0).unwrap().unwrap().external);
    assert!(read_chunk_nbt_fingerprint(
        &region,
        0,
        0,
        CompressionLimits::from_ffi(0, 0),
        NbtLimits::from_ffi(0, 0, 0, 0)
    )
    .unwrap()
    .is_some());

    let internal = write_chunk_payload(&region, 0, 0, COMPRESSION_RAW, &small).unwrap();
    assert!(!internal.external);
    assert!(!dir.join("c.0.0.mcc").exists());
    flush_region(&region).unwrap();
    let reopened = read_chunk_nbt_fingerprint(
        &region,
        0,
        0,
        CompressionLimits::from_ffi(0, 0),
        NbtLimits::from_ffi(0, 0, 0, 0),
    )
    .unwrap()
    .unwrap();
    assert_eq!(small.len() as u64, reopened.decompressed_len);
}

#[test]
fn writer_supports_all_compression_ids_for_semantic_reads() {
    let dir = temp_dir("writer-compression");
    let nbt = minimal_nbt_document();
    let cases = [
        (COMPRESSION_GZIP, gzip(&nbt)),
        (COMPRESSION_ZLIB, zlib(&nbt)),
        (COMPRESSION_RAW, nbt.clone()),
        (
            COMPRESSION_LZ4,
            encode_lz4_java_block_stream_for_test(&nbt, true),
        ),
    ];
    for (i, (compression_id, payload)) in cases.into_iter().enumerate() {
        let region = dir.join(format!("r.{}.{}.mca", i, i));
        write_chunk_payload(&region, i as i32, 0, compression_id, &payload).unwrap();
        let semantic = read_chunk_nbt_fingerprint(
            &region,
            i as i32,
            0,
            CompressionLimits::from_ffi(0, 0),
            NbtLimits::from_ffi(0, 0, 0, 0),
        )
        .unwrap()
        .unwrap();
        assert_eq!(compression_id, semantic.compression_id);
        assert_eq!(payload.len() as u64, semantic.compressed_len);
        assert_eq!(nbt.len() as u64, semantic.decompressed_len);
    }
}

#[test]
fn writer_ffi_validates_inputs_and_round_trips_payloads() {
    let dir = temp_dir("writer-ffi");
    let region = dir.join("r.0.0.mca");
    let path = region.to_string_lossy().as_bytes().to_vec();
    let payload = minimal_nbt_document();
    let mut result = NativeRegionWriteResult::default();

    let status = unsafe {
        mattmc_region_write_chunk_payload(
            path.as_ptr(),
            path.len() as u64,
            0,
            0,
            COMPRESSION_RAW as i32,
            payload.as_ptr(),
            payload.len() as u64,
            &mut result,
        )
    };
    assert_eq!(STATUS_OK, status);
    assert_eq!(1, result.present);
    assert_eq!(payload.len() as u64, result.payload_len);
    assert_eq!(
        payload,
        read_chunk_payload(&region, 0, 0).unwrap().unwrap().payload
    );

    let status = unsafe { mattmc_region_flush(path.as_ptr(), path.len() as u64, &mut result) };
    assert_eq!(STATUS_OK, status);

    let status =
        unsafe { mattmc_region_delete_chunk(path.as_ptr(), path.len() as u64, 0, 0, &mut result) };
    assert_eq!(STATUS_OK, status);
    assert_eq!(0, result.present);
    assert!(read_chunk_payload(&region, 0, 0).unwrap().is_none());

    let status = unsafe {
        mattmc_region_write_chunk_payload(
            std::ptr::null(),
            1,
            0,
            0,
            COMPRESSION_RAW as i32,
            payload.as_ptr(),
            payload.len() as u64,
            &mut result,
        )
    };
    assert_eq!(STATUS_INVALID_ARGUMENT, status);

    let missing_parent = dir.join("missing").join("r.0.0.mca");
    let missing_path = missing_parent.to_string_lossy().as_bytes().to_vec();
    let status = unsafe {
        mattmc_region_write_chunk_payload(
            missing_path.as_ptr(),
            missing_path.len() as u64,
            0,
            0,
            COMPRESSION_RAW as i32,
            payload.as_ptr(),
            payload.len() as u64,
            &mut result,
        )
    };
    assert_ne!(STATUS_OK, status);
}

#[test]
fn region_handle_ffi_round_trips_without_reopening_path() {
    let dir = temp_dir("handle-roundtrip");
    let region = dir.join("r.0.0.mca");
    let payload = minimal_nbt_document();
    let handle = open_region_handle(&region);
    let mut write_result = NativeRegionWriteResult::default();

    let status = unsafe {
        mattmc_region_handle_write_chunk_payload(
            handle,
            0,
            0,
            COMPRESSION_RAW as i32,
            payload.as_ptr(),
            payload.len() as u64,
            &mut write_result,
        )
    };
    assert_eq!(STATUS_OK, status);
    assert_eq!(1, write_result.present);

    let mut payload_result = NativeRegionPayloadResult::default();
    let status = unsafe {
        mattmc_region_handle_read_chunk_payload(
            handle,
            0,
            0,
            std::ptr::null_mut(),
            0,
            &mut payload_result,
        )
    };
    assert_eq!(STATUS_OUTPUT_TOO_SMALL, status);
    assert_eq!(payload.len() as u64, payload_result.output_len);

    let mut output = vec![0; payload_result.output_len as usize];
    let status = unsafe {
        mattmc_region_handle_read_chunk_payload(
            handle,
            0,
            0,
            output.as_mut_ptr(),
            output.len() as u64,
            &mut payload_result,
        )
    };
    assert_eq!(STATUS_OK, status);
    assert_eq!(payload, output);

    let status = unsafe { mattmc_region_handle_delete_chunk(handle, 0, 0, &mut write_result) };
    assert_eq!(STATUS_OK, status);
    assert_eq!(0, write_result.present);

    let status = unsafe { mattmc_region_handle_flush(handle, &mut write_result) };
    assert_eq!(STATUS_OK, status);
    close_region_handle(handle);
    assert!(read_chunk_payload(&region, 0, 0).unwrap().is_none());
}

#[test]
fn region_handle_ffi_rejects_stale_wrong_kind_and_double_close() {
    let dir = temp_dir("handle-stale");
    let region = dir.join("r.0.0.mca");
    let first = open_region_handle(&region);
    let mut write_result = NativeRegionWriteResult::default();
    let status = unsafe { mattmc_region_close(first, &mut write_result) };
    assert_eq!(STATUS_OK, status);

    let status = unsafe { mattmc_region_close(first, &mut write_result) };
    assert_eq!(STATUS_INVALID_HANDLE, status);

    let second = open_region_handle(&region);
    assert_ne!(first, second);
    let payload = minimal_nbt_document();
    let status = unsafe {
        mattmc_region_handle_write_chunk_payload(
            first,
            0,
            0,
            COMPRESSION_RAW as i32,
            payload.as_ptr(),
            payload.len() as u64,
            &mut write_result,
        )
    };
    assert_eq!(STATUS_INVALID_HANDLE, status);

    let wrong_kind = second ^ (1u64 << 56);
    let status = unsafe { mattmc_region_handle_flush(wrong_kind, &mut write_result) };
    assert_eq!(STATUS_INVALID_HANDLE, status);

    let status = unsafe {
        mattmc_region_handle_write_chunk_payload(
            second,
            0,
            0,
            COMPRESSION_RAW as i32,
            payload.as_ptr(),
            payload.len() as u64,
            &mut write_result,
        )
    };
    assert_eq!(STATUS_OK, status);
    close_region_handle(second);
}

#[test]
fn region_handle_nbt_tape_round_trips_all_compression_ids() {
    let dir = temp_dir("handle-nbt-tape");
    let source_document = read_document(&minimal_nbt_document(), NbtLimits::defaults()).unwrap();
    let tape = document_to_tape(&source_document, NbtLimits::defaults()).unwrap();
    let expected_fingerprint =
        crate::storage::nbt::fingerprint::fingerprint_document(&source_document);
    let ids = [
        COMPRESSION_GZIP,
        COMPRESSION_ZLIB,
        COMPRESSION_RAW,
        COMPRESSION_LZ4,
    ];

    for (i, compression_id) in ids.into_iter().enumerate() {
        let region = dir.join(format!("r.{}.0.mca", i));
        let handle = open_region_handle(&region);
        let mut write_result = NativeRegionWriteResult::default();
        let status = unsafe {
            mattmc_region_handle_write_chunk_nbt_tape(
                handle,
                i as i32,
                0,
                compression_id as i32,
                tape.as_ptr(),
                tape.len() as u64,
                0,
                0,
                0,
                0,
                0,
                0,
                &mut write_result,
            )
        };
        assert_eq!(STATUS_OK, status, "write compression id {}", compression_id);
        assert_eq!(compression_id as i32, write_result.compression_id);

        let mut tape_result = NativeRegionTapeResult::default();
        let status = unsafe {
            mattmc_region_handle_read_chunk_nbt_tape(
                handle,
                i as i32,
                0,
                std::ptr::null_mut(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                &mut tape_result,
            )
        };
        assert_eq!(
            STATUS_OUTPUT_TOO_SMALL, status,
            "query compression id {}",
            compression_id
        );
        assert_eq!(1, tape_result.present);
        assert_eq!(compression_id as i32, tape_result.compression_id);
        assert_eq!(expected_fingerprint, tape_result.fingerprint);

        let mut output = vec![0; tape_result.output_len as usize];
        let status = unsafe {
            mattmc_region_handle_read_chunk_nbt_tape(
                handle,
                i as i32,
                0,
                output.as_mut_ptr(),
                output.len() as u64,
                0,
                0,
                0,
                0,
                0,
                0,
                &mut tape_result,
            )
        };
        assert_eq!(STATUS_OK, status, "read compression id {}", compression_id);
        let decoded = document_from_tape(&output, NbtLimits::defaults()).unwrap();
        assert_eq!(expected_fingerprint, tape_result.fingerprint);
        assert_eq!(
            expected_fingerprint,
            crate::storage::nbt::fingerprint::fingerprint_document(&decoded)
        );
        assert!(tape_result.compressed_len > 0);
        assert!(tape_result.decompressed_len > 0);
        close_region_handle(handle);
    }
}

#[test]
fn region_handle_nbt_tape_rejects_stale_handles_and_bad_tape() {
    let dir = temp_dir("handle-nbt-tape-errors");
    let region = dir.join("r.0.0.mca");
    let handle = open_region_handle(&region);
    close_region_handle(handle);

    let mut tape_result = NativeRegionTapeResult::default();
    let status = unsafe {
        mattmc_region_handle_read_chunk_nbt_tape(
            handle,
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
            &mut tape_result,
        )
    };
    assert_eq!(STATUS_INVALID_HANDLE, status);

    let handle = open_region_handle(&region);
    let bad_tape = [0x44, 0x33, 0x22, 0x11];
    let mut write_result = NativeRegionWriteResult::default();
    let status = unsafe {
        mattmc_region_handle_write_chunk_nbt_tape(
            handle,
            0,
            0,
            COMPRESSION_RAW as i32,
            bad_tape.as_ptr(),
            bad_tape.len() as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut write_result,
        )
    };
    assert_eq!(STATUS_NBT_ERROR, status);
    assert!(read_chunk_payload(&region, 0, 0).unwrap().is_none());
    close_region_handle(handle);
}

#[test]
fn decompresses_region_payloads_for_all_supported_compression_ids() {
    let nbt = minimal_nbt_document();
    let cases = [
        (COMPRESSION_GZIP, gzip(&nbt)),
        (COMPRESSION_ZLIB, zlib(&nbt)),
        (COMPRESSION_RAW, nbt.clone()),
        (
            COMPRESSION_LZ4,
            encode_lz4_java_block_stream_for_test(&nbt, true),
        ),
    ];
    for (compression_id, payload) in cases {
        let decoded =
            decompress_region_payload(compression_id, &payload, CompressionLimits::from_ffi(0, 0))
                .unwrap();
        assert_eq!(nbt, decoded, "compression id {}", compression_id);
    }
}

#[test]
fn lz4_decompression_rejects_truncated_and_checksum_corrupt_streams() {
    const LZ4_JAVA_BLOCK_HEADER_LEN: usize = 21;
    let nbt = minimal_nbt_document();
    let encoded = encode_lz4_java_block_stream_for_test(&nbt, true);
    let error = decompress_region_payload(
        COMPRESSION_LZ4,
        &encoded[..LZ4_JAVA_BLOCK_HEADER_LEN - 1],
        CompressionLimits::from_ffi(0, 0),
    )
    .unwrap_err();
    assert_eq!(RegionErrorKind::Lz4InvalidHeader, error.kind);

    let mut checksum_corrupt = encode_lz4_java_block_stream_for_test(&nbt, false);
    checksum_corrupt[LZ4_JAVA_BLOCK_HEADER_LEN] ^= 0x55;
    let error = decompress_region_payload(
        COMPRESSION_LZ4,
        &checksum_corrupt,
        CompressionLimits::from_ffi(0, 0),
    )
    .unwrap_err();
    assert_eq!(RegionErrorKind::Lz4ChecksumMismatch, error.kind);
}

#[test]
fn semantic_fingerprint_reports_sizes_and_metadata() {
    let dir = temp_dir("semantic");
    let region = dir.join("r.0.0.mca");
    let nbt = minimal_nbt_document();
    let payload = zlib(&nbt);
    write_region(
        &region,
        &[entry(2, 1, 888, COMPRESSION_ZLIB, &payload, None)],
    );

    let result = read_chunk_nbt_fingerprint(
        &region,
        0,
        0,
        CompressionLimits::from_ffi(0, 0),
        NbtLimits::from_ffi(0, 0, 0, 0),
    )
    .unwrap()
    .unwrap();

    assert_eq!(888, result.timestamp);
    assert_eq!(COMPRESSION_ZLIB, result.compression_id);
    assert!(!result.external);
    assert_eq!(payload.len() as u64, result.compressed_len);
    assert_eq!(nbt.len() as u64, result.decompressed_len);
    assert_ne!(0, result.fingerprint);
}

#[test]
fn semantic_fingerprint_reads_external_lz4_payload() {
    let dir = temp_dir("semantic-external");
    let region = dir.join("r.0.0.mca");
    let nbt = minimal_nbt_document();
    let payload = encode_lz4_java_block_stream_for_test(&nbt, false);
    fs::write(dir.join("c.0.0.mcc"), &payload).unwrap();
    write_region(&region, &[external_entry(2, 1, 999, COMPRESSION_LZ4, 0, 0)]);

    let result = read_chunk_nbt_fingerprint(
        &region,
        0,
        0,
        CompressionLimits::from_ffi(0, 0),
        NbtLimits::from_ffi(0, 0, 0, 0),
    )
    .unwrap()
    .unwrap();

    assert!(result.external);
    assert_eq!(COMPRESSION_LZ4, result.compression_id);
    assert_eq!(payload.len() as u64, result.compressed_len);
    assert_eq!(nbt.len() as u64, result.decompressed_len);
}

#[test]
fn semantic_layer_distinguishes_decompression_limits_and_nbt_parse_errors() {
    let dir = temp_dir("semantic-errors");
    let region = dir.join("r.0.0.mca");
    let nbt = minimal_nbt_document();
    let payload = zlib(&nbt);
    write_region(&region, &[entry(2, 1, 0, COMPRESSION_ZLIB, &payload, None)]);

    let limit_error = read_chunk_nbt_fingerprint(
        &region,
        0,
        0,
        CompressionLimits::from_ffi(0, 3),
        NbtLimits::from_ffi(0, 0, 0, 0),
    )
    .unwrap_err();
    assert_eq!(RegionErrorKind::DecompressionSizeLimit, limit_error.kind);

    let malformed_nbt = gzip(&[10, 0]);
    let malformed_region = dir.join("malformed-nbt.mca");
    write_region(
        &malformed_region,
        &[entry(2, 1, 0, COMPRESSION_GZIP, &malformed_nbt, None)],
    );
    let nbt_error = read_chunk_nbt_fingerprint(
        &malformed_region,
        0,
        0,
        CompressionLimits::from_ffi(0, 0),
        NbtLimits::from_ffi(0, 0, 0, 0),
    )
    .unwrap_err();
    assert_eq!(RegionErrorKind::NbtParseError, nbt_error.kind);
}

#[test]
fn ffi_semantic_fingerprint_reports_domains_and_metadata() {
    let dir = temp_dir("ffi-semantic");
    let region = dir.join("r.0.0.mca");
    let nbt = minimal_nbt_document();
    let payload = gzip(&nbt);
    write_region(
        &region,
        &[entry(2, 1, 222, COMPRESSION_GZIP, &payload, None)],
    );
    let path = region.to_string_lossy().as_bytes().to_vec();
    let mut result = NativeRegionNbtResult::default();

    let status = unsafe {
        mattmc_region_read_chunk_nbt_fingerprint(
            path.as_ptr(),
            path.len() as u64,
            0,
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

    assert_eq!(STATUS_OK, status);
    assert_eq!(1, result.present);
    assert_eq!(COMPRESSION_GZIP as i32, result.compression_id);
    assert_eq!(222, result.timestamp);
    assert_eq!(payload.len() as u64, result.compressed_len);
    assert_eq!(nbt.len() as u64, result.decompressed_len);
    assert_ne!(0, result.fingerprint);

    let status = unsafe {
        mattmc_region_read_chunk_nbt_fingerprint(
            path.as_ptr(),
            path.len() as u64,
            0,
            0,
            0,
            3,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(STATUS_DECOMPRESSION_ERROR, status);

    let malformed_region = dir.join("malformed.mca");
    let malformed_payload = gzip(&[10, 0]);
    write_region(
        &malformed_region,
        &[entry(2, 1, 0, COMPRESSION_GZIP, &malformed_payload, None)],
    );
    let malformed_path = malformed_region.to_string_lossy().as_bytes().to_vec();
    let status = unsafe {
        mattmc_region_read_chunk_nbt_fingerprint(
            malformed_path.as_ptr(),
            malformed_path.len() as u64,
            0,
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
    assert_eq!(STATUS_NBT_ERROR, status);
}

fn assert_error(path: &Path, kind: RegionErrorKind) {
    assert_error_for_chunk(path, 0, 0, kind);
}

fn assert_error_for_chunk(path: &Path, chunk_x: i32, chunk_z: i32, kind: RegionErrorKind) {
    let error = read_chunk_payload(path, chunk_x, chunk_z).unwrap_err();
    assert_eq!(kind, error.kind, "{}", error);
}

fn assert_error_on_write(path: &Path, kind: RegionErrorKind) {
    let error =
        write_chunk_payload(path, 0, 0, COMPRESSION_RAW, &minimal_nbt_document()).unwrap_err();
    assert_eq!(kind, error.kind, "{}", error);
}

fn temp_dir(name: &str) -> PathBuf {
    let id = NEXT_DIR.fetch_add(1, Ordering::Relaxed);
    let path = std::env::temp_dir().join(format!(
        "mattmc-region-{}-{}-{}",
        name,
        std::process::id(),
        id
    ));
    let _ = fs::remove_dir_all(&path);
    fs::create_dir_all(&path).unwrap();
    path
}

fn open_region_handle(path: &Path) -> u64 {
    let path = path.to_string_lossy().as_bytes().to_vec();
    let mut result = NativeRegionOpenResult::default();
    let status = unsafe { mattmc_region_open(path.as_ptr(), path.len() as u64, 0, &mut result) };
    assert_eq!(STATUS_OK, status);
    assert_ne!(0, result.handle);
    result.handle
}

fn close_region_handle(handle: u64) {
    let mut result = NativeRegionWriteResult::default();
    let status = unsafe { mattmc_region_close(handle, &mut result) };
    assert_eq!(STATUS_OK, status);
}

#[derive(Clone)]
struct Entry {
    first_sector: u32,
    sector_count: u8,
    timestamp: u32,
    compression_id: u8,
    payload: Vec<u8>,
    chunk_index: usize,
    external: bool,
    stub_payload_len: usize,
}

impl Entry {
    fn with_stub_payload_len(mut self, len: usize) -> Self {
        self.stub_payload_len = len;
        self
    }
}

fn entry(
    first_sector: u32,
    sector_count: u8,
    timestamp: u32,
    compression_id: u8,
    payload: &[u8],
    chunk_index: Option<usize>,
) -> Entry {
    Entry {
        first_sector,
        sector_count,
        timestamp,
        compression_id,
        payload: payload.to_vec(),
        chunk_index: chunk_index.unwrap_or(0),
        external: false,
        stub_payload_len: 0,
    }
}

fn external_entry(
    first_sector: u32,
    sector_count: u8,
    timestamp: u32,
    compression_id: u8,
    chunk_x: i32,
    chunk_z: i32,
) -> Entry {
    Entry {
        first_sector,
        sector_count,
        timestamp,
        compression_id,
        payload: Vec::new(),
        chunk_index: local_chunk_index(chunk_x, chunk_z),
        external: true,
        stub_payload_len: 0,
    }
}

fn write_region(path: &Path, entries: &[Entry]) {
    let last_sector = entries
        .iter()
        .map(|entry| entry.first_sector + entry.sector_count as u32)
        .max()
        .unwrap_or(2);
    let mut bytes = vec![0u8; last_sector as usize * SECTOR_BYTES];
    for entry in entries {
        let packed = packed_location(entry.first_sector, entry.sector_count);
        write_u32_be(&mut bytes, entry.chunk_index * 4, packed);
        write_u32_be(
            &mut bytes,
            HEADER_BYTES / 2 + entry.chunk_index * 4,
            entry.timestamp,
        );
        let sector_start = entry.first_sector as usize * SECTOR_BYTES;
        if entry.external {
            let declared_len = entry.stub_payload_len as u32 + 1;
            write_u32_be(&mut bytes, sector_start, declared_len);
            bytes[sector_start + 4] = entry.compression_id | EXTERNAL_STREAM_FLAG;
            for i in 0..entry.stub_payload_len {
                bytes[sector_start + CHUNK_HEADER_BYTES + i] = 0xAB;
            }
        } else {
            let declared_len = entry.payload.len() as u32 + 1;
            write_u32_be(&mut bytes, sector_start, declared_len);
            bytes[sector_start + 4] = entry.compression_id;
            bytes[sector_start + CHUNK_HEADER_BYTES
                ..sector_start + CHUNK_HEADER_BYTES + entry.payload.len()]
                .copy_from_slice(&entry.payload);
        }
    }
    fs::write(path, bytes).unwrap();
}

fn write_header_only(path: &Path, packed: u32, timestamp: u32) {
    let mut bytes = vec![0u8; HEADER_BYTES];
    write_u32_be(&mut bytes, 0, packed);
    write_u32_be(&mut bytes, HEADER_BYTES / 2, timestamp);
    fs::write(path, bytes).unwrap();
}

fn write_manual_region(path: &Path, packed: u32, timestamp: u32, body: &[u8]) {
    let mut bytes = vec![0u8; HEADER_BYTES];
    write_u32_be(&mut bytes, 0, packed);
    write_u32_be(&mut bytes, HEADER_BYTES / 2, timestamp);
    bytes.extend_from_slice(body);
    fs::write(path, bytes).unwrap();
}

fn write_two_overlapping_entries(path: &Path) {
    let mut bytes = vec![0u8; 4 * SECTOR_BYTES];
    write_u32_be(&mut bytes, 0, packed_location(2, 2));
    write_u32_be(&mut bytes, 4, packed_location(3, 1));
    fs::write(path, bytes).unwrap();
}

fn write_region_with_record(
    path: &Path,
    first_sector: u32,
    sector_count: u8,
    timestamp: u32,
    record: &[u8],
) {
    let mut bytes = vec![0u8; (first_sector as usize + sector_count as usize) * SECTOR_BYTES];
    write_u32_be(&mut bytes, 0, packed_location(first_sector, sector_count));
    write_u32_be(&mut bytes, HEADER_BYTES / 2, timestamp);
    let sector_start = first_sector as usize * SECTOR_BYTES;
    bytes[sector_start..sector_start + record.len()].copy_from_slice(record);
    fs::write(path, bytes).unwrap();
}

fn packed_location(first_sector: u32, sector_count: u8) -> u32 {
    (first_sector << 8) | sector_count as u32
}

fn write_u32_be(bytes: &mut [u8], offset: usize, value: u32) {
    bytes[offset..offset + 4].copy_from_slice(&value.to_be_bytes());
}

fn minimal_nbt_document() -> Vec<u8> {
    vec![
        10, 0, 0, // root compound with empty name
        3, 0, 1, b'x', 0, 0, 0, 42, // int x = 42
        0,  // end
    ]
}

fn tiny_nbt_with_name(name: &str) -> Vec<u8> {
    let name_bytes = name.as_bytes();
    let mut bytes = vec![10, 0, 0, 8, 0, 4, b'n', b'a', b'm', b'e'];
    bytes.extend_from_slice(&(name_bytes.len() as u16).to_be_bytes());
    bytes.extend_from_slice(name_bytes);
    bytes.push(0);
    bytes
}

fn large_raw_nbt_document(byte_count: usize) -> Vec<u8> {
    let mut bytes = vec![
        10, 0, 0, // root compound with empty name
        7, 0, 5, b'b', b'y', b't', b'e', b's',
    ];
    bytes.extend_from_slice(&(byte_count as i32).to_be_bytes());
    bytes.resize(bytes.len() + byte_count, 0x5A);
    bytes.push(0);
    bytes
}

fn gzip(input: &[u8]) -> Vec<u8> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(input).unwrap();
    encoder.finish().unwrap()
}

fn zlib(input: &[u8]) -> Vec<u8> {
    let mut encoder = ZlibEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(input).unwrap();
    encoder.finish().unwrap()
}
