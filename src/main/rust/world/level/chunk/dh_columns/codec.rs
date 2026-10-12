//! Saved DH point codecs stream bounded bytes directly against primary columns.
//! Compression and contextual material resolution remain at the Java IO edge.
use super::{mutable::Column, source::Source};

const MORE: i64 = 0;
const DONE: i64 = 1;
const VARINT: i64 = 2;
const NEGATIVE_V1: i64 = 4;
const POINT: i64 = 5;
const ENCODED_ID: i64 = 6;
const NEGATIVE_WRITE: i64 = 7;
const OOM: i64 = 8;
const ABI: i64 = 9;
#[derive(Clone, Copy)]
struct Range {
    x: usize,
    z: usize,
    width: usize,
    count: usize,
}
impl Range {
    fn new(x: i32, end_x: i32, z: i32, end_z: i32) -> Option<Self> {
        if !(0..=64).contains(&x)
            || !(x..=64).contains(&end_x)
            || !(0..=64).contains(&z)
            || !(z..=64).contains(&end_z)
        {
            return None;
        }
        Some(Self {
            x: x as usize,
            z: z as usize,
            width: (end_z - z) as usize,
            count: ((end_x - x) * (end_z - z)) as usize,
        })
    }
    fn at(self, rank: usize) -> usize {
        (self.x + rank / self.width) * 64 + self.z + rank % self.width
    }
}
unsafe fn admitted(source: &mut Source, range: Range, write: bool) -> bool {
    for rank in 0..range.count {
        let index = range.at(rank);
        if source.column(index).is_none() || (write && !source.present(index)) {
            return false;
        }
    }
    !write || !source.identities().windows(2).any(|p| p[0] == p[1])
}
#[derive(Default)]
struct Token {
    value: u64,
    have: u32,
}
impl Token {
    /// Width zero denotes Java's wrapping 32-bit varint, including its fifth
    /// byte truncation and rejection before requesting a sixth byte.
    fn read(&mut self, width: u32, input: &mut &[u8]) -> Result<Option<u64>, ()> {
        while let Some((&byte, rest)) = input.split_first() {
            *input = rest;
            if width == 0 {
                self.value |= u64::from(((byte & 127) as u32).wrapping_shl(self.have));
                self.have += 7;
                if byte & 128 == 0 {
                    let value = self.value;
                    *self = Self::default();
                    return Ok(Some(value));
                }
                if self.have >= 32 {
                    return Err(());
                }
            } else {
                self.value = (self.value << 8) | u64::from(byte);
                self.have += 1;
                if self.have == width {
                    let value = self.value;
                    *self = Self::default();
                    return Ok(Some(value));
                }
            }
        }
        Ok(None)
    }
}
fn height(p: u64) -> i32 {
    ((p >> 32) & 4095) as i32
}
fn bottom(p: u64) -> i32 {
    ((p >> 44) & 4095) as i32
}
fn invalid(p: u64) -> bool {
    height(p) == 0 || bottom(p) + height(p) > 6095
}
fn zigzag(n: i32) -> i32 {
    (n << 1) ^ (n >> 31)
}
fn unzigzag(n: i32) -> i32 {
    ((n as u32 >> 1) as i32) ^ -(n & 1)
}
struct Decoder {
    source: *mut Source,
    range: Range,
    v1: bool,
    validate: bool,
    phase: u8,
    rank: usize,
    point: usize,
    previous: i32,
    token: Token,
    status: [i64; 4],
}
impl Decoder {
    fn fail(&mut self, status: i64, index: usize, value: i64) {
        self.status = [status, index as i64, value, 0];
    }
    unsafe fn feed(&mut self, mut input: &[u8]) {
        if self.status[0] != MORE {
            return;
        }
        loop {
            if self.rank == self.range.count {
                if self.v1 || self.phase == 6 {
                    self.status[0] = DONE;
                    return;
                }
                self.phase += 1;
                self.rank = 0;
                self.point = 0;
                self.previous = 0;
                if self.phase == 5 {
                    if !self.validate {
                        self.status[0] = DONE;
                        return;
                    }
                    self.phase = 6;
                }
                continue;
            }
            let index = self.range.at(self.rank);
            let source = &mut *self.source;
            if self.phase != 0 && self.point == source.codec_len(index).max(0) as usize {
                self.rank += 1;
                self.point = 0;
                if self.v1 {
                    self.phase = 0;
                }
                continue;
            }
            let data = if self.phase == 0 {
                0
            } else {
                source.column(index).unwrap()[self.point]
            };
            if self.phase == 6 {
                if invalid(data) {
                    self.fail(POINT, index, data as i64);
                    return;
                }
                self.point += 1;
                continue;
            }
            let needs = match self.phase {
                0 => true,
                1 => true,
                2 => true,
                3 => bottom(data) != 0,
                4 => data >> 60 != 0,
                _ => false,
            };
            // The V2 phases are counts, IDs, heights, bottoms, light, then done.
            let width = if self.v1 {
                if self.phase == 0 {
                    2
                } else {
                    8
                }
            } else if self.phase == 4 {
                1
            } else {
                0
            };
            let value = if !needs {
                0
            } else {
                match self.token.read(width, &mut input) {
                    Ok(Some(value)) => value,
                    Ok(None) => return,
                    Err(()) => {
                        self.fail(VARINT, index, 0);
                        return;
                    }
                }
            };
            if self.phase == 0 {
                let count = if self.v1 {
                    value as i16 as i32
                } else {
                    value as u32 as i32
                };
                if self.v1 && count < 0 {
                    self.fail(NEGATIVE_V1, index, count as i64);
                    return;
                }
                if !source.codec_reset(index, count.max(0) as usize) {
                    self.fail(OOM, index, 0);
                    return;
                }
                if count < 0 {
                    source.codec_negative(index, count);
                }
                if self.v1 {
                    self.phase = 1;
                } else {
                    self.rank += 1;
                }
                continue;
            }
            let result = if self.v1 {
                if self.validate && invalid(value) {
                    self.fail(POINT, index, value as i64);
                    return;
                }
                value
            } else {
                let signed = value as u32 as i32;
                match self.phase {
                    1 => {
                        if self.validate && signed >> 2 < 0 {
                            self.fail(ENCODED_ID, index, signed as i64);
                            return;
                        }
                        ((signed >> 2) as u64 & 0x7fffffff)
                            | (1u64 << 32)
                            | ((signed as u64 & 1) << 44)
                            | ((signed as u64 & 2) << 60)
                    }
                    2 => (data & !(4095u64 << 32)) | ((signed as i64 as u64) << 32),
                    3 => {
                        let y = self
                            .previous
                            .wrapping_sub(height(data))
                            .wrapping_add(unzigzag(signed));
                        self.previous = y;
                        (data & !(4095u64 << 44)) | ((y as i64 as u64) << 44)
                    }
                    4 => {
                        let block = (value as u8 as i8) >> 4;
                        (data & !(255u64 << 56))
                            | ((block as i64 as u64) << 60)
                            | ((value & 15) << 56)
                    }
                    _ => unreachable!(),
                }
            };
            source.codec_set(index, self.point, result);
            self.point += 1;
        }
    }
}
struct Encoder {
    source: *mut Source,
    range: Range,
    phase: u8,
    rank: usize,
    point: usize,
    previous: i32,
    pending: [u8; 5],
    pending_at: usize,
    pending_count: usize,
    status: [i64; 4],
}
impl Encoder {
    fn varint(&mut self, value: i32, index: usize) {
        if value < 0 {
            self.status = [NEGATIVE_WRITE, index as i64, value as i64, 0];
            return;
        }
        let mut value = value as u32;
        while value >= 128 {
            self.pending[self.pending_count] = (value as u8) | 128;
            self.pending_count += 1;
            value >>= 7;
        }
        self.pending[self.pending_count] = value as u8;
        self.pending_count += 1;
    }
    unsafe fn next(&mut self, output: &mut [u8]) -> usize {
        let mut written = 0;
        while written < output.len() && self.status[0] == MORE {
            if self.pending_at < self.pending_count {
                output[written] = self.pending[self.pending_at];
                written += 1;
                self.pending_at += 1;
                continue;
            }
            self.pending_at = 0;
            self.pending_count = 0;
            if self.rank == self.range.count {
                if self.phase == 4 {
                    self.status[0] = DONE;
                    break;
                }
                self.phase += 1;
                self.rank = 0;
                self.point = 0;
                self.previous = 0;
                continue;
            }
            let index = self.range.at(self.rank);
            let points = (&*self.source).column(index).unwrap();
            if self.phase == 0 {
                self.varint(points.len() as i32, index);
                self.rank += 1;
                continue;
            }
            if self.point == points.len() {
                self.rank += 1;
                self.point = 0;
                continue;
            }
            let data = points[self.point];
            self.point += 1;
            match self.phase {
                1 => {
                    let discontinuous = bottom(data) != self.previous.wrapping_sub(height(data));
                    self.previous = bottom(data);
                    let lit = (data >> 56) & 255 != 0;
                    self.varint(
                        ((data as i32 & 0x7fffffff) << 2)
                            | if lit { 2 } else { 0 }
                            | i32::from(discontinuous),
                        index,
                    );
                }
                2 => self.varint(height(data), index),
                3 => {
                    let expected = self.previous.wrapping_sub(height(data));
                    self.previous = bottom(data);
                    if bottom(data) != expected {
                        self.varint(zigzag(bottom(data).wrapping_sub(expected)), index);
                    }
                }
                4 => {
                    let light = ((data >> 60) << 4) | ((data >> 56) & 15);
                    if light != 0 {
                        self.pending[0] = light as u8;
                        self.pending_count = 1;
                    }
                }
                _ => unreachable!(),
            }
        }
        written
    }
}

/// Clear all source columns and install the original EMPTY_DATA_POINT. The
/// synchronous Java caller pins/excludes this CPU table and projects readers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_initialize(source: *mut Source) -> i32 {
    let Some(source) = source.as_mut() else {
        return -1;
    };
    let range = Range::new(0, 64, 0, 64).unwrap();
    if !admitted(source, range, true) {
        return 0;
    }
    for i in 0..4096 {
        if !source.codec_reset(i, 1) {
            return -2;
        }
    }
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_decoder(
    source: *mut Source,
    x: i32,
    end_x: i32,
    z: i32,
    end_z: i32,
    v1: i32,
    validate: i32,
) -> *mut Decoder {
    let Some(range) = Range::new(x, end_x, z, end_z) else {
        return std::ptr::null_mut();
    };
    if source.is_null() || !admitted(&mut *source, range, true) {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(Decoder {
        source,
        range,
        v1: v1 != 0,
        validate: validate != 0,
        phase: 0,
        rank: 0,
        point: 0,
        previous: 0,
        token: Token::default(),
        status: [0; 4],
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_encoder(
    source: *mut Source,
    x: i32,
    end_x: i32,
    z: i32,
    end_z: i32,
) -> *mut Encoder {
    let Some(range) = Range::new(x, end_x, z, end_z) else {
        return std::ptr::null_mut();
    };
    if source.is_null() || !admitted(&mut *source, range, false) {
        return std::ptr::null_mut();
    }
    Box::into_raw(Box::new(Encoder {
        source,
        range,
        phase: 0,
        rank: 0,
        point: 0,
        previous: 0,
        pending: [0; 5],
        pending_at: 0,
        pending_count: 0,
        status: [0; 4],
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_feed(
    owner: *mut Decoder,
    input: *const u8,
    count: i32,
    out: *mut i64,
) {
    if owner.is_null() || count < 0 || (count > 0 && input.is_null()) || out.is_null() {
        if !out.is_null() {
            std::ptr::copy_nonoverlapping([ABI, 0, 0, 0].as_ptr(), out, 4);
        }
        return;
    }
    let input = if count == 0 {
        &[]
    } else {
        std::slice::from_raw_parts(input, count as usize)
    };
    (*owner).feed(input);
    std::ptr::copy_nonoverlapping((*owner).status.as_ptr(), out, 4);
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_next(
    owner: *mut Encoder,
    output: *mut u8,
    capacity: i32,
    out: *mut i64,
) {
    if owner.is_null() || capacity <= 0 || output.is_null() || out.is_null() {
        if !out.is_null() {
            std::ptr::copy_nonoverlapping([ABI, 0, 0, 0].as_ptr(), out, 4);
        }
        return;
    }
    let count = (*owner).next(std::slice::from_raw_parts_mut(output, capacity as usize));
    (*owner).status[3] = count as i64;
    std::ptr::copy_nonoverlapping((*owner).status.as_ptr(), out, 4);
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_decoder_release(owner: *mut Decoder) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_encoder_release(owner: *mut Encoder) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
/// Sparse CPU extent projection; no point array export or newly escaped reader.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_codec_project(source: *const Source, out: *mut u64) -> i32 {
    let Some(source) = source.as_ref() else {
        return -1;
    };
    if out.is_null() {
        return -1;
    }
    let mut count = 0;
    for i in 0..4096 {
        if !source.has_reader(i) {
            continue;
        }
        let owner = source.owner(i);
        if owner.is_null() {
            continue;
        }
        let points = (*owner).points();
        let row = std::slice::from_raw_parts_mut(out.add(count * 5), 5);
        row.copy_from_slice(&[
            i as u64,
            owner as u64,
            points.as_ptr() as u64,
            (*owner).logical_len() as u64,
            u64::from((*owner).is_owned()),
        ]);
        count += 1;
    }
    count as i32
}

#[cfg(test)]
mod tests {
    use super::*;
    unsafe fn decode(
        source: *mut Source,
        bytes: &[u8],
        v1: bool,
        validate: bool,
        pieces: usize,
    ) -> [i64; 4] {
        let owner = mattmc_dh_codec_decoder(source, 0, 1, 0, 1, i32::from(v1), i32::from(validate));
        assert!(!owner.is_null());
        let mut out = [0; 4];
        for piece in bytes.chunks(pieces) {
            mattmc_dh_codec_feed(owner, piece.as_ptr(), piece.len() as i32, out.as_mut_ptr());
        }
        mattmc_dh_codec_feed(owner, std::ptr::null(), 0, out.as_mut_ptr());
        mattmc_dh_codec_decoder_release(owner);
        out
    }
    #[test]
    fn corrupt_negative_count_retains_frozen_logical_size_without_a_negative_slice() {
        unsafe {
            let source = super::super::source::mattmc_dh_source_create();
            assert_eq!(
                decode(source, &[255, 255, 255, 255, 15], false, false, 1)[0],
                DONE
            );
            assert_eq!((&*source).codec_len(0), -1);
            assert!(
                (&*source).column(0).is_none(),
                "native consumers must decline invalid logical extents"
            );
            assert!((&mut *source).codec_reset(0, 2));
            assert_eq!((&*source).column(0).unwrap(), &[0, 0]);
            super::super::source::mattmc_dh_source_release(source);
        }
    }
    #[test]
    fn zero_sentinel_obeys_the_callers_validation_mode() {
        unsafe {
            let source = super::super::source::mattmc_dh_source_create();
            assert_eq!(decode(source, &[1, 0, 0], false, false, 1)[0], DONE);
            assert_eq!((&*source).column(0).unwrap(), &[0]);
            assert_eq!(decode(source, &[1, 0, 0], false, true, 1)[0], POINT);
            super::super::source::mattmc_dh_source_release(source);
        }
    }
    #[test]
    fn explicit_v2_bytes_and_every_byte_boundary_match_and_roundtrip() {
        unsafe {
            let source = super::super::source::mattmc_dh_source_create();
            // count=1, ID7+light+discontinuity, height3, zigzag(bottom2-(-3))=10, block11/sky5.
            let bytes = [1, 31, 3, 10, 0xb5];
            for width in 1..=bytes.len() {
                assert_eq!(decode(source, &bytes, false, true, width)[0], DONE);
                assert_eq!(
                    (&*source).column(0).unwrap(),
                    &[7 | (3 << 32) | (2 << 44) | (11 << 60) | (5 << 56)]
                );
            }
            let encoder = mattmc_dh_codec_encoder(source, 0, 1, 0, 1);
            let mut result = Vec::new();
            let mut out = [0; 4];
            loop {
                let mut one = [0u8];
                mattmc_dh_codec_next(encoder, one.as_mut_ptr(), 1, out.as_mut_ptr());
                result.extend_from_slice(&one[..out[3] as usize]);
                if out[0] != MORE {
                    break;
                }
            }
            assert_eq!(out[0], DONE);
            assert_eq!(result, bytes);
            mattmc_dh_codec_encoder_release(encoder);
            super::super::source::mattmc_dh_source_release(source);
        }
    }
    #[test]
    fn legacy_big_endian_and_corruption_preserve_successful_mutation_prefixes() {
        unsafe {
            let source = super::super::source::mattmc_dh_source_create();
            let a = 7u64 | (3 << 32) | (2 << 44);
            let b = 9u64 | (1 << 32);
            let mut bytes = vec![0, 2];
            bytes.extend_from_slice(&a.to_be_bytes());
            bytes.extend_from_slice(&b.to_be_bytes());
            assert_eq!(decode(source, &bytes, true, true, 1)[0], DONE);
            assert_eq!((&*source).column(0).unwrap(), &[a, b]);
            let out = decode(source, &[1, 0x80, 0x80, 0x80, 0x80, 0x80], false, false, 2);
            assert_eq!(out[0], VARINT);
            assert_eq!((&*source).column(0).unwrap(), &[0]);
            let out = decode(source, &[0xff, 0xff], true, false, 1);
            assert_eq!(out[0], NEGATIVE_V1);
            assert_eq!((&*source).column(0).unwrap(), &[0]);
            super::super::source::mattmc_dh_source_release(source);
        }
    }
}
