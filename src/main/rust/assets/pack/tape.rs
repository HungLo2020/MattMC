use super::errors::{PackError, PackResult};

const MAGIC: u32 = 0x4b435052;
const VERSION: u16 = 1;

pub fn string_list(strings: &[String]) -> PackResult<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(&MAGIC.to_le_bytes());
    out.extend_from_slice(&VERSION.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    out.extend_from_slice(&(strings.len() as u32).to_le_bytes());
    for value in strings {
        let bytes = value.as_bytes();
        if bytes.len() > u32::MAX as usize {
            return Err(PackError::invalid_argument(
                "string too large for pack tape",
            ));
        }
        out.extend_from_slice(&(bytes.len() as u32).to_le_bytes());
        out.extend_from_slice(bytes);
    }
    Ok(out)
}
