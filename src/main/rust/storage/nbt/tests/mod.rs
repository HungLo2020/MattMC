use super::error::NbtErrorKind;
use super::fingerprint::fingerprint_document;
use super::limits::NbtLimits;
use super::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use super::modified_utf8::{decode_modified_utf8, encode_modified_utf8};
use super::reader::read_document;
use super::tape::{document_from_tape, document_to_tape};
use super::writer::write_document;
use super::{
    mattmc_nbt_decode_fingerprint, mattmc_nbt_decode_to_tape, mattmc_nbt_encode_from_tape,
    mattmc_nbt_parse_fingerprint, mattmc_nbt_recompress, mattmc_nbt_reencode, NativeNbtResult,
    STATUS_COMPRESSION_ERROR, STATUS_INVALID_ARGUMENT, STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
    STATUS_PARSE_ERROR,
};

fn sample_document() -> NbtDocument {
    NbtDocument {
        name: JavaString::from_str("root"),
        root: NbtTag::Compound(vec![
            CompoundEntry {
                name: JavaString::from_str("byte"),
                value: NbtTag::Byte(-7),
            },
            CompoundEntry {
                name: JavaString::from_str("short"),
                value: NbtTag::Short(-1234),
            },
            CompoundEntry {
                name: JavaString::from_str("int"),
                value: NbtTag::Int(1234567),
            },
            CompoundEntry {
                name: JavaString::from_str("long"),
                value: NbtTag::Long(-9876543210),
            },
            CompoundEntry {
                name: JavaString::from_str("float"),
                value: NbtTag::Float(f32::NAN.to_bits()),
            },
            CompoundEntry {
                name: JavaString::from_str("double"),
                value: NbtTag::Double(f64::INFINITY.to_bits()),
            },
            CompoundEntry {
                name: JavaString::from_str("bytes"),
                value: NbtTag::ByteArray(vec![-1, 0, 1]),
            },
            CompoundEntry {
                name: JavaString::from_str("string"),
                value: NbtTag::String(JavaString::from_units(vec![0, 0xD83D, 0xDE00, 0xD800])),
            },
            CompoundEntry {
                name: JavaString::from_str("list"),
                value: NbtTag::List(ListTag {
                    element_type: TagId::Int,
                    elements: vec![NbtTag::Int(1), NbtTag::Int(2)],
                }),
            },
            CompoundEntry {
                name: JavaString::from_str("empty_list"),
                value: NbtTag::List(ListTag {
                    element_type: TagId::End,
                    elements: Vec::new(),
                }),
            },
            CompoundEntry {
                name: JavaString::from_str("ints"),
                value: NbtTag::IntArray(vec![i32::MIN, 0, i32::MAX]),
            },
            CompoundEntry {
                name: JavaString::from_str("longs"),
                value: NbtTag::LongArray(vec![i64::MIN, 0, i64::MAX]),
            },
        ]),
    }
}

#[test]
fn round_trips_document_with_every_tag_type() {
    let document = sample_document();
    let encoded = write_document(&document, NbtLimits::defaults()).expect("write");
    let decoded = read_document(&encoded, NbtLimits::defaults()).expect("read");

    assert_eq!(decoded, document);
    assert_eq!(
        fingerprint_document(&decoded),
        fingerprint_document(&document)
    );
}

#[test]
fn preserves_nan_and_infinity_bits() {
    let nan_bits = 0x7FC0_1234;
    let inf_bits = f64::NEG_INFINITY.to_bits();
    let document = NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![
            CompoundEntry {
                name: JavaString::from_str("f"),
                value: NbtTag::Float(nan_bits),
            },
            CompoundEntry {
                name: JavaString::from_str("d"),
                value: NbtTag::Double(inf_bits),
            },
        ]),
    };
    let encoded = write_document(&document, NbtLimits::defaults()).expect("write");
    let decoded = read_document(&encoded, NbtLimits::defaults()).expect("read");
    assert_eq!(decoded, document);
}

#[test]
fn modified_utf8_matches_java_code_unit_rules() {
    let value = JavaString::from_units(vec![0, b'a' as u16, 0x07FF, 0x0800, 0xD800, 0xDC00]);
    let encoded = encode_modified_utf8(&value, 0).expect("encode");

    assert_eq!(encoded[0..2], [0xC0, 0x80]);
    assert_eq!(decode_modified_utf8(&encoded, 0).expect("decode"), value);
    assert_eq!(
        JavaString::from_units(vec![0xD800]).to_string_lossless_if_valid(),
        None
    );
}

#[test]
fn modified_utf8_rejects_four_byte_utf8() {
    let error = decode_modified_utf8(&[0xF0, 0x9F, 0x98, 0x80], 10).expect_err("reject");
    assert_eq!(error.kind, NbtErrorKind::InvalidModifiedUtf8);
    assert_eq!(error.offset, 10);
}

#[test]
fn modified_utf8_rejects_too_long_encoding() {
    let value = JavaString::from_units(vec![0x0800; 21_846]);
    let error = encode_modified_utf8(&value, 5).expect_err("too long");
    assert_eq!(error.kind, NbtErrorKind::ModifiedUtf8TooLong);
}

#[test]
fn duplicate_compound_keys_keep_last_value_in_first_slot() {
    let mut input = Vec::new();
    input.extend([10, 0, 0]);
    input.extend([3, 0, 1, b'a', 0, 0, 0, 1]);
    input.extend([3, 0, 1, b'a', 0, 0, 0, 2]);
    input.push(0);

    let decoded = read_document(&input, NbtLimits::defaults()).expect("read");
    let NbtTag::Compound(entries) = decoded.root else {
        panic!("root compound");
    };
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].value, NbtTag::Int(2));
}

#[test]
fn rejects_missing_list_element_type_for_non_empty_list() {
    let mut input = Vec::new();
    input.extend([10, 0, 0]);
    input.extend([9, 0, 1, b'l']);
    input.push(0);
    input.extend(1i32.to_be_bytes());

    let error = read_document(&input, NbtLimits::defaults()).expect_err("reject");
    assert_eq!(error.kind, NbtErrorKind::MissingListElementType);
}

#[test]
fn rejects_negative_array_length() {
    let mut input = Vec::new();
    input.extend([10, 0, 0]);
    input.extend([7, 0, 1, b'b']);
    input.extend((-1i32).to_be_bytes());

    let error = read_document(&input, NbtLimits::defaults()).expect_err("reject");
    assert_eq!(error.kind, NbtErrorKind::NegativeLength);
}

#[test]
fn rejects_trailing_data_after_root() {
    let mut encoded = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    encoded.push(1);

    let error = read_document(&encoded, NbtLimits::defaults()).expect_err("reject");
    assert_eq!(error.kind, NbtErrorKind::TrailingData);
}

#[test]
fn rejects_non_compound_root() {
    let input = [1, 0, 0, 5];
    let error = read_document(&input, NbtLimits::defaults()).expect_err("reject");
    assert_eq!(error.kind, NbtErrorKind::InvalidRootType);
}

#[test]
fn enforces_depth_limit() {
    let document = NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![CompoundEntry {
            name: JavaString::from_str("nested"),
            value: NbtTag::Compound(Vec::new()),
        }]),
    };
    let encoded = write_document(&document, NbtLimits::defaults()).expect("write");
    let limits = NbtLimits::from_ffi(1, 0, 0, 0);

    let error = read_document(&encoded, limits).expect_err("depth");
    assert_eq!(error.kind, NbtErrorKind::DepthLimit);
}

#[test]
fn enforces_collection_and_allocation_limits() {
    let document = NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![CompoundEntry {
            name: JavaString::from_str("bytes"),
            value: NbtTag::ByteArray(vec![1, 2, 3, 4]),
        }]),
    };
    let encoded = write_document(&document, NbtLimits::defaults()).expect("write");

    let collection =
        read_document(&encoded, NbtLimits::from_ffi(0, 3, 0, 0)).expect_err("collection");
    assert_eq!(collection.kind, NbtErrorKind::ExcessiveLength);
    let allocation =
        read_document(&encoded, NbtLimits::from_ffi(0, 0, 8, 0)).expect_err("allocation");
    assert_eq!(allocation.kind, NbtErrorKind::AllocationLimit);
}

#[test]
fn ffi_parse_reports_fingerprint() {
    let encoded = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_parse_fingerprint(
            encoded.as_ptr(),
            encoded.len() as u64,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };

    assert_eq!(status, STATUS_OK);
    assert_eq!(result.status, STATUS_OK);
    assert_ne!(result.fingerprint, 0);
}

#[test]
fn ffi_reencode_supports_size_query_and_copy() {
    let encoded = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_reencode(
            encoded.as_ptr(),
            encoded.len() as u64,
            std::ptr::null_mut(),
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OUTPUT_TOO_SMALL);
    assert_eq!(result.output_len, encoded.len() as u64);

    let mut output = vec![0; result.output_len as usize];
    let status = unsafe {
        mattmc_nbt_reencode(
            encoded.as_ptr(),
            encoded.len() as u64,
            output.as_mut_ptr(),
            output.len() as u64,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OK);
    assert_eq!(output, encoded);
}

#[test]
fn tape_round_trips_document_with_every_tag_type() {
    let document = sample_document();
    let tape = document_to_tape(&document, NbtLimits::defaults()).expect("tape");
    let decoded = document_from_tape(&tape, NbtLimits::defaults()).expect("from tape");

    assert_eq!(decoded, document);
    assert_eq!(
        fingerprint_document(&decoded),
        fingerprint_document(&document)
    );
}

#[test]
fn tape_rejects_bad_magic_and_trailing_bytes() {
    let document = sample_document();
    let mut tape = document_to_tape(&document, NbtLimits::defaults()).expect("tape");
    tape[0] ^= 1;
    let error = document_from_tape(&tape, NbtLimits::defaults()).expect_err("bad magic");
    assert_eq!(error.kind, NbtErrorKind::InvalidArgument);

    let mut tape = document_to_tape(&document, NbtLimits::defaults()).expect("tape");
    tape.push(1);
    let error = document_from_tape(&tape, NbtLimits::defaults()).expect_err("trailing");
    assert_eq!(error.kind, NbtErrorKind::TrailingData);
}

#[test]
fn ffi_decode_to_tape_and_encode_from_tape_round_trip_compressed_inputs() {
    let raw = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let gzip = recompress_for_test(&raw, 1);
    let tape = decode_to_tape_for_test(&gzip, -1);
    let encoded = encode_from_tape_for_test(&tape, 0);

    assert_eq!(encoded, raw);
    assert_eq!(
        decode_fingerprint_for_test(&raw, -1).fingerprint,
        decode_fingerprint_for_test(&encoded, -1).fingerprint
    );
}

#[test]
fn ffi_tape_calls_reject_malformed_inputs() {
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_decode_to_tape(
            std::ptr::null(),
            1,
            std::ptr::null_mut(),
            0,
            -1,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_INVALID_ARGUMENT);

    let status = unsafe {
        mattmc_nbt_encode_from_tape(
            [0u8; 8].as_ptr(),
            8,
            std::ptr::null_mut(),
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
    assert_eq!(status, STATUS_PARSE_ERROR);
}

#[test]
fn ffi_rejects_malformed_inputs() {
    let mut result = NativeNbtResult::default();
    let status =
        unsafe { mattmc_nbt_parse_fingerprint(std::ptr::null(), 1, 0, 0, 0, 0, &mut result) };
    assert_eq!(status, STATUS_INVALID_ARGUMENT);

    let malformed = [10, 0];
    let status = unsafe {
        mattmc_nbt_parse_fingerprint(
            malformed.as_ptr(),
            malformed.len() as u64,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_PARSE_ERROR);
    assert_eq!(result.error_kind, NbtErrorKind::UnexpectedEof as i32);
}

#[test]
fn ffi_auto_detects_raw_gzip_and_zlib_inputs() {
    let raw = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let gzip = recompress_for_test(&raw, 1);
    let zlib = recompress_for_test(&raw, 2);
    let raw_fp = decode_fingerprint_for_test(&raw, -1);

    assert_eq!(raw_fp.status, STATUS_OK);
    assert_eq!(raw_fp.error_kind, 0);
    let gzip_fp = decode_fingerprint_for_test(&gzip, -1);
    assert_eq!(gzip_fp.status, STATUS_OK);
    assert_eq!(gzip_fp.error_kind, 1);
    assert_eq!(gzip_fp.fingerprint, raw_fp.fingerprint);
    let zlib_fp = decode_fingerprint_for_test(&zlib, -1);
    assert_eq!(zlib_fp.status, STATUS_OK);
    assert_eq!(zlib_fp.error_kind, 2);
    assert_eq!(zlib_fp.fingerprint, raw_fp.fingerprint);
}

#[test]
fn ffi_recompresses_between_formats_without_changing_semantics() {
    let raw = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let gzip = recompress_for_test(&raw, 1);
    let zlib = recompress_for_test(&gzip, 2);
    let back_to_raw = recompress_for_test(&zlib, 0);

    assert_eq!(
        decode_fingerprint_for_test(&raw, -1).fingerprint,
        decode_fingerprint_for_test(&gzip, -1).fingerprint
    );
    assert_eq!(
        decode_fingerprint_for_test(&raw, -1).fingerprint,
        decode_fingerprint_for_test(&zlib, -1).fingerprint
    );
    assert_eq!(back_to_raw, raw);
}

#[test]
fn ffi_compression_rejects_truncated_streams_trailing_garbage_and_limits() {
    let raw = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let mut gzip = recompress_for_test(&raw, 1);
    gzip.truncate(gzip.len() - 2);
    let truncated = decode_fingerprint_for_test(&gzip, -1);
    assert_eq!(truncated.status, STATUS_COMPRESSION_ERROR);

    let mut zlib = recompress_for_test(&raw, 2);
    zlib.push(1);
    let trailing = decode_fingerprint_for_test(&zlib, -1);
    assert_eq!(trailing.status, STATUS_COMPRESSION_ERROR);

    let mut result = NativeNbtResult::default();
    let gzip = recompress_for_test(&raw, 1);
    let status = unsafe {
        mattmc_nbt_decode_fingerprint(
            gzip.as_ptr(),
            gzip.len() as u64,
            -1,
            0,
            8,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_COMPRESSION_ERROR);
    assert_eq!(
        result.error_kind,
        NbtErrorKind::DecompressedSizeLimit as i32
    );
}

#[test]
fn ffi_compression_rejects_unsupported_formats() {
    let raw = write_document(&sample_document(), NbtLimits::defaults()).expect("write");
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_decode_fingerprint(
            raw.as_ptr(),
            raw.len() as u64,
            99,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_COMPRESSION_ERROR);
    assert_eq!(
        result.error_kind,
        NbtErrorKind::UnsupportedCompression as i32
    );
}

fn decode_fingerprint_for_test(input: &[u8], input_compression: i32) -> NativeNbtResult {
    let mut result = NativeNbtResult::default();
    unsafe {
        mattmc_nbt_decode_fingerprint(
            input.as_ptr(),
            input.len() as u64,
            input_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        );
    }
    result
}

fn recompress_for_test(input: &[u8], output_compression: i32) -> Vec<u8> {
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_recompress(
            input.as_ptr(),
            input.len() as u64,
            std::ptr::null_mut(),
            0,
            -1,
            output_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OUTPUT_TOO_SMALL);
    let mut output = vec![0; result.output_len as usize];
    let status = unsafe {
        mattmc_nbt_recompress(
            input.as_ptr(),
            input.len() as u64,
            output.as_mut_ptr(),
            output.len() as u64,
            -1,
            output_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OK);
    output
}

fn decode_to_tape_for_test(input: &[u8], input_compression: i32) -> Vec<u8> {
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_decode_to_tape(
            input.as_ptr(),
            input.len() as u64,
            std::ptr::null_mut(),
            0,
            input_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OUTPUT_TOO_SMALL);
    let mut output = vec![0; result.output_len as usize];
    let status = unsafe {
        mattmc_nbt_decode_to_tape(
            input.as_ptr(),
            input.len() as u64,
            output.as_mut_ptr(),
            output.len() as u64,
            input_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OK);
    output
}

fn encode_from_tape_for_test(tape: &[u8], output_compression: i32) -> Vec<u8> {
    let mut result = NativeNbtResult::default();
    let status = unsafe {
        mattmc_nbt_encode_from_tape(
            tape.as_ptr(),
            tape.len() as u64,
            std::ptr::null_mut(),
            0,
            output_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OUTPUT_TOO_SMALL);
    let mut output = vec![0; result.output_len as usize];
    let status = unsafe {
        mattmc_nbt_encode_from_tape(
            tape.as_ptr(),
            tape.len() as u64,
            output.as_mut_ptr(),
            output.len() as u64,
            output_compression,
            0,
            0,
            0,
            0,
            0,
            0,
            &mut result,
        )
    };
    assert_eq!(status, STATUS_OK);
    output
}
