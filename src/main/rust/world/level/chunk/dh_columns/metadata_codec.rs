//! Saved metadata payloads read/write primary native planes, with no Java mirror.
//! The synchronous IO scope pins the metadata lease and excludes mutations.
use super::metadata::Metadata;

pub(crate) struct Decoder {
    metadata: *const Metadata,
    kind: usize,
    offset: usize,
}
pub(crate) struct Encoder {
    metadata: *const Metadata,
    kind: usize,
    offset: usize,
    uniform: bool,
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_decoder(
    metadata: *const Metadata,
    kind: i32,
) -> *mut Decoder {
    let Some(owner) = metadata.as_ref() else {
        return std::ptr::null_mut();
    };
    if !(0..3).contains(&kind) || kind == 2 && owner.plane(2).len() != 4096 {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(Decoder {
        metadata,
        kind: kind as usize,
        offset: 0,
    }))
}
/// Returns done=1, more=0 or invalid ABI=-1. No buffer pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_feed(
    decoder: *mut Decoder,
    bytes: *const u8,
    count: i32,
) -> i32 {
    let Some(decoder) = decoder.as_mut() else {
        return -1;
    };
    if count < 0 || count > 0 && bytes.is_null() {
        return -1;
    }
    let input = if count == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(bytes, count as usize)
    };
    let skip = if decoder.kind == 2 { 4096usize } else { 0 };
    let total = 4096 + skip;
    if decoder.offset >= total {
        return 1;
    }
    let count = input.len().min(total - decoder.offset);
    let discard = count.min(skip.saturating_sub(decoder.offset));
    let start = decoder.offset + discard;
    if discard < count {
        let plane = (*decoder.metadata).plane_mut(decoder.kind);
        // Frozen reads the fixed payload into retained backing capacity even
        // after list shrink. Consume all bytes, projecting only the logical
        // prefix. Subsequent list growth zeroes its new elements in both paths.
        let from = (start - skip).min(plane.len());
        let to = (decoder.offset + count - skip).min(plane.len());
        let output = &mut plane[from..to];
        if decoder.kind == 2 {
            for (out, &value) in output.iter_mut().zip(&input[discard..count]) {
                *out = i8::from(value != 0);
            }
        } else {
            output.copy_from_slice(std::slice::from_raw_parts(
                input[discard..count].as_ptr().cast(),
                output.len(),
            ));
        }
    }
    decoder.offset += count;
    i32::from(decoder.offset == total)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_decoder_release(decoder: *mut Decoder) {
    if !decoder.is_null() {
        drop(Box::from_raw(decoder));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_encoder(
    metadata: *const Metadata,
    kind: i32,
    uniform: i32,
) -> *mut Encoder {
    let Some(owner) = metadata.as_ref() else {
        return std::ptr::null_mut();
    };
    if !(0..2).contains(&kind)
        || owner.plane(kind as usize).len() > i32::MAX as usize
        || uniform != 0 && owner.plane(2).len() != 4096
    {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(Encoder {
        metadata,
        kind: kind as usize,
        offset: 0,
        uniform: uniform != 0,
    }))
}
/// Writes [done,bytes written]. Only byte spans are exported; source planes
/// remain primary. Uniformity uses the original nonzero-to-boolean projection.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_next(
    encoder: *mut Encoder,
    bytes: *mut u8,
    capacity: i32,
    out: *mut i64,
) {
    if encoder.is_null() || capacity <= 0 || bytes.is_null() || out.is_null() {
        if !out.is_null() {
            out.write(-1);
            out.add(1).write(0);
        }
        return;
    }
    let encoder = &mut *encoder;
    let plane = (*encoder.metadata).plane(encoder.kind);
    let total = plane.len() + if encoder.uniform { 4096 } else { 0 };
    let count = (capacity as usize).min(total - encoder.offset);
    let output = std::slice::from_raw_parts_mut(bytes, count);
    for (i, value) in output.iter_mut().enumerate() {
        let at = encoder.offset + i;
        *value = if at < plane.len() {
            plane[at] as u8
        } else {
            u8::from((*encoder.metadata).plane(2)[at - plane.len()] != 0)
        };
    }
    encoder.offset += count;
    out.write(i64::from(encoder.offset == total));
    out.add(1).write(count as i64);
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_encoder_release(encoder: *mut Encoder) {
    if !encoder.is_null() {
        drop(Box::from_raw(encoder));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn imports_signed_prefixes_without_changing_suffix_or_list_extent() {
        unsafe {
            let metadata = Metadata::new();
            metadata.plane_mut(0).resize(4100, -7);
            let decoder = mattmc_dh_metadata_decoder(&metadata, 0);
            assert!(!decoder.is_null());
            assert_eq!(
                mattmc_dh_metadata_feed(decoder, [255, 128, 127].as_ptr(), 3),
                0
            );
            assert_eq!(&metadata.plane(0)[..5], &[-1, -128, 127, 0, 0]);
            let rest = vec![13u8; 4093];
            assert_eq!(mattmc_dh_metadata_feed(decoder, rest.as_ptr(), 4093), 1);
            assert_eq!(metadata.plane(0).len(), 4100);
            assert_eq!(&metadata.plane(0)[4096..], &[-7; 4]);
            mattmc_dh_metadata_decoder_release(decoder);
            metadata.plane_mut(1).truncate(2);
            let decoder = mattmc_dh_metadata_decoder(&metadata, 1);
            assert_eq!(mattmc_dh_metadata_feed(decoder, [255, 128, 127].as_ptr(), 3), 0);
            assert_eq!(metadata.plane(1), [-1, -128]);
            assert_eq!(mattmc_dh_metadata_feed(decoder, rest.as_ptr(), 4093), 1);
            assert_eq!(metadata.plane(1).len(), 2);
            mattmc_dh_metadata_decoder_release(decoder);
        }
    }
    #[test]
    fn uniform_decode_skips_compression_prefix_and_normalizes_nonzero() {
        unsafe {
            let metadata = Metadata::new();
            metadata.plane_mut(2).fill(1);
            let decoder = mattmc_dh_metadata_decoder(&metadata, 2);
            let mut bytes = vec![88u8; 4096];
            bytes.extend_from_slice(&[0, 1, 2, 255]);
            assert_eq!(mattmc_dh_metadata_feed(decoder, bytes.as_ptr(), 4095), 0);
            assert_eq!(metadata.plane(2), &[1; 4096]);
            assert_eq!(
                mattmc_dh_metadata_feed(decoder, bytes[4095..].as_ptr(), 5),
                0
            );
            assert_eq!(&metadata.plane(2)[..5], &[0, 1, 1, 1, 1]);
            mattmc_dh_metadata_decoder_release(decoder);
            metadata.plane_mut(1)[0] = -128;
            metadata.plane_mut(2)[0] = -7;
            let encoder = mattmc_dh_metadata_encoder(&metadata, 1, 1);
            let mut encoded = Vec::new();
            let mut out = [0i64; 2];
            loop {
                let mut bytes = [0u8; 257];
                mattmc_dh_metadata_next(encoder, bytes.as_mut_ptr(), 257, out.as_mut_ptr());
                encoded.extend_from_slice(&bytes[..out[1] as usize]);
                if out[0] == 1 {
                    break;
                }
            }
            assert_eq!(encoded.len(), 8192);
            assert_eq!(encoded[0], 128);
            assert_eq!(&encoded[4096..4101], &[1, 1, 1, 1, 1]);
            mattmc_dh_metadata_encoder_release(encoder);
        }
    }
}
