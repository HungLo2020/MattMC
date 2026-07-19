use super::error::{NbtError, NbtErrorKind, NbtResult};
use super::model::JavaString;

pub fn decode_modified_utf8(bytes: &[u8], offset: usize) -> NbtResult<JavaString> {
    let mut units = Vec::with_capacity(bytes.len());
    let mut cursor = 0;
    while cursor < bytes.len() {
        let byte = bytes[cursor];
        if byte <= 0x7F {
            units.push(byte as u16);
            cursor += 1;
        } else if (byte & 0xE0) == 0xC0 {
            if cursor + 1 >= bytes.len() {
                return Err(NbtError::new(
                    NbtErrorKind::InvalidModifiedUtf8,
                    offset + cursor,
                ));
            }
            let b2 = bytes[cursor + 1];
            if (b2 & 0xC0) != 0x80 {
                return Err(NbtError::new(
                    NbtErrorKind::InvalidModifiedUtf8,
                    offset + cursor + 1,
                ));
            }
            let unit = (((byte & 0x1F) as u16) << 6) | ((b2 & 0x3F) as u16);
            units.push(unit);
            cursor += 2;
        } else if (byte & 0xF0) == 0xE0 {
            if cursor + 2 >= bytes.len() {
                return Err(NbtError::new(
                    NbtErrorKind::InvalidModifiedUtf8,
                    offset + cursor,
                ));
            }
            let b2 = bytes[cursor + 1];
            let b3 = bytes[cursor + 2];
            if (b2 & 0xC0) != 0x80 {
                return Err(NbtError::new(
                    NbtErrorKind::InvalidModifiedUtf8,
                    offset + cursor + 1,
                ));
            }
            if (b3 & 0xC0) != 0x80 {
                return Err(NbtError::new(
                    NbtErrorKind::InvalidModifiedUtf8,
                    offset + cursor + 2,
                ));
            }
            let unit =
                (((byte & 0x0F) as u16) << 12) | (((b2 & 0x3F) as u16) << 6) | ((b3 & 0x3F) as u16);
            units.push(unit);
            cursor += 3;
        } else {
            return Err(NbtError::new(
                NbtErrorKind::InvalidModifiedUtf8,
                offset + cursor,
            ));
        }
    }
    Ok(JavaString::from_units(units))
}

pub fn encode_modified_utf8(value: &JavaString, offset: usize) -> NbtResult<Vec<u8>> {
    let mut output = Vec::with_capacity(value.units().len());
    for unit in value.units() {
        let unit = *unit;
        if (0x0001..=0x007F).contains(&unit) {
            output.push(unit as u8);
        } else if unit <= 0x07FF {
            output.push((0xC0 | ((unit >> 6) & 0x1F)) as u8);
            output.push((0x80 | (unit & 0x3F)) as u8);
        } else {
            output.push((0xE0 | ((unit >> 12) & 0x0F)) as u8);
            output.push((0x80 | ((unit >> 6) & 0x3F)) as u8);
            output.push((0x80 | (unit & 0x3F)) as u8);
        }
        if output.len() > u16::MAX as usize {
            return Err(NbtError::new(NbtErrorKind::ModifiedUtf8TooLong, offset));
        }
    }
    Ok(output)
}
