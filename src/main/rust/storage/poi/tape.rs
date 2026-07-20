use super::decoder::validate_resource_id;
use super::error::{PoiError, PoiErrorKind, PoiResult};
use super::model::{PoiChunk, PoiRecord, PoiSection};

const MAGIC: u32 = 0x494f_504d; // MPOI
const VERSION: u16 = 1;
const HEADER_LEN: usize = 12;

pub fn encode_poi_tape(chunk: &PoiChunk) -> PoiResult<Vec<u8>> {
    let section_count = u32::try_from(chunk.sections.len()).map_err(|_| {
        PoiError::new(
            PoiErrorKind::Overflow,
            "POI section count does not fit in the typed buffer",
        )
    })?;
    let record_count = chunk.sections.iter().try_fold(0u32, |count, section| {
        let records = u32::try_from(section.records.len()).map_err(|_| {
            PoiError::new(
                PoiErrorKind::Overflow,
                "POI record count does not fit in the typed buffer",
            )
        })?;
        count.checked_add(records).ok_or_else(|| {
            PoiError::new(
                PoiErrorKind::Overflow,
                "total POI record count does not fit in the typed buffer",
            )
        })
    })?;

    let mut output = Vec::new();
    write_u32(&mut output, MAGIC);
    write_u16(&mut output, VERSION);
    write_u16(&mut output, 0);
    write_u32(&mut output, section_count);

    for section in &chunk.sections {
        write_i32(&mut output, section.section_y);
        output.push(u8::from(section.valid));
        output.extend_from_slice(&[0, 0, 0]);
        write_u32(&mut output, u32::try_from(section.records.len()).unwrap());
        for record in &section.records {
            write_i32(&mut output, record.x);
            write_i32(&mut output, record.y);
            write_i32(&mut output, record.z);
            write_i32(&mut output, record.free_tickets);
            let type_bytes = record.poi_type.as_bytes();
            let type_len = u32::try_from(type_bytes.len()).map_err(|_| {
                PoiError::new(
                    PoiErrorKind::Overflow,
                    "POI type identifier is too large for the typed buffer",
                )
            })?;
            write_u32(&mut output, type_len);
            output.extend_from_slice(type_bytes);
        }
    }

    debug_assert!(output.len() >= HEADER_LEN);
    let _ = record_count;
    Ok(output)
}

pub fn count_records(chunk: &PoiChunk) -> u32 {
    chunk
        .sections
        .iter()
        .map(|section| section.records.len() as u32)
        .sum()
}

pub fn decode_poi_tape(input: &[u8]) -> PoiResult<PoiChunk> {
    let mut reader = TapeReader::new(input);
    if reader.read_u32()? != MAGIC {
        return Err(PoiError::new(
            PoiErrorKind::WrongType,
            "invalid POI tape magic",
        ));
    }
    if reader.read_u16()? != VERSION {
        return Err(PoiError::new(
            PoiErrorKind::WrongType,
            "unsupported POI tape version",
        ));
    }
    reader.read_u16()?;
    let section_count = reader.read_u32()? as usize;
    let mut sections = Vec::with_capacity(section_count);
    for _ in 0..section_count {
        let section_y = reader.read_i32()?;
        let valid = reader.read_u8()? != 0;
        reader.skip(3)?;
        let record_count = reader.read_u32()? as usize;
        let mut records = Vec::with_capacity(record_count);
        for _ in 0..record_count {
            let x = reader.read_i32()?;
            let y = reader.read_i32()?;
            let z = reader.read_i32()?;
            let free_tickets = reader.read_i32()?;
            let type_len = reader.read_u32()? as usize;
            let poi_type = reader.read_utf8(type_len)?;
            validate_resource_id(&poi_type)?;
            records.push(PoiRecord {
                x,
                y,
                z,
                poi_type,
                free_tickets,
            });
        }
        sections.push(PoiSection {
            section_y,
            valid,
            records,
        });
    }
    if !reader.is_done() {
        return Err(PoiError::new(
            PoiErrorKind::Overflow,
            "trailing bytes after POI tape",
        ));
    }
    Ok(PoiChunk { sections })
}

fn write_u16(output: &mut Vec<u8>, value: u16) {
    output.extend_from_slice(&value.to_le_bytes());
}

fn write_u32(output: &mut Vec<u8>, value: u32) {
    output.extend_from_slice(&value.to_le_bytes());
}

fn write_i32(output: &mut Vec<u8>, value: i32) {
    output.extend_from_slice(&value.to_le_bytes());
}

struct TapeReader<'a> {
    input: &'a [u8],
    cursor: usize,
}

impl<'a> TapeReader<'a> {
    fn new(input: &'a [u8]) -> Self {
        Self { input, cursor: 0 }
    }

    fn read_u8(&mut self) -> PoiResult<u8> {
        self.require(1)?;
        let value = self.input[self.cursor];
        self.cursor += 1;
        Ok(value)
    }

    fn read_u16(&mut self) -> PoiResult<u16> {
        Ok(u16::from_le_bytes(self.read_array()?))
    }

    fn read_u32(&mut self) -> PoiResult<u32> {
        Ok(u32::from_le_bytes(self.read_array()?))
    }

    fn read_i32(&mut self) -> PoiResult<i32> {
        Ok(i32::from_le_bytes(self.read_array()?))
    }

    fn read_utf8(&mut self, len: usize) -> PoiResult<String> {
        self.require(len)?;
        let start = self.cursor;
        self.cursor += len;
        std::str::from_utf8(&self.input[start..start + len])
            .map(|value| value.to_owned())
            .map_err(|_| {
                PoiError::new(PoiErrorKind::InvalidPoiType, "invalid UTF-8 in POI type id")
            })
    }

    fn skip(&mut self, len: usize) -> PoiResult<()> {
        self.require(len)?;
        self.cursor += len;
        Ok(())
    }

    fn is_done(&self) -> bool {
        self.cursor == self.input.len()
    }

    fn read_array<const N: usize>(&mut self) -> PoiResult<[u8; N]> {
        self.require(N)?;
        let mut bytes = [0u8; N];
        bytes.copy_from_slice(&self.input[self.cursor..self.cursor + N]);
        self.cursor += N;
        Ok(bytes)
    }

    fn require(&self, len: usize) -> PoiResult<()> {
        if self.input.len().saturating_sub(self.cursor) < len {
            Err(PoiError::new(PoiErrorKind::Overflow, "truncated POI tape"))
        } else {
            Ok(())
        }
    }
}
