use super::error::{EntityError, EntityErrorKind, EntityResult};
use super::model::{EntityChunkEnvelope, EntityEnvelope};

const MAGIC: u32 = 0x544e_454d; // MENT
const VERSION: u16 = 1;

pub const FLAG_ID_PRESENT: u32 = 1 << 0;
pub const FLAG_UUID_PRESENT: u32 = 1 << 1;
pub const FLAG_POSITION_PRESENT: u32 = 1 << 2;
pub const FLAG_ID_MALFORMED: u32 = 1 << 3;

pub fn encode_entity_tape(chunk: &EntityChunkEnvelope) -> EntityResult<Vec<u8>> {
    let entity_count = u32::try_from(chunk.entities.len()).map_err(|_| {
        EntityError::new(
            EntityErrorKind::Overflow,
            "entity count does not fit in the typed buffer",
        )
    })?;
    let mut output = Vec::new();
    write_u32(&mut output, MAGIC);
    write_u16(&mut output, VERSION);
    write_u16(&mut output, 0);
    write_i32(&mut output, chunk.data_version);
    write_i32(&mut output, chunk.chunk_x);
    write_i32(&mut output, chunk.chunk_z);
    write_u32(&mut output, entity_count);
    for entity in &chunk.entities {
        encode_entity(&mut output, entity)?;
    }
    Ok(output)
}

pub fn decode_entity_tape(input: &[u8]) -> EntityResult<EntityChunkEnvelope> {
    let mut reader = TapeReader::new(input);
    if reader.read_u32()? != MAGIC {
        return Err(EntityError::new(
            EntityErrorKind::WrongType,
            "invalid entity envelope tape magic",
        ));
    }
    if reader.read_u16()? != VERSION {
        return Err(EntityError::new(
            EntityErrorKind::WrongType,
            "unsupported entity envelope tape version",
        ));
    }
    reader.read_u16()?;
    let data_version = reader.read_i32()?;
    let chunk_x = reader.read_i32()?;
    let chunk_z = reader.read_i32()?;
    let entity_count = reader.read_u32()? as usize;
    let mut entities = Vec::with_capacity(entity_count);
    for _ in 0..entity_count {
        entities.push(decode_entity(&mut reader)?);
    }
    if !reader.is_done() {
        return Err(EntityError::new(
            EntityErrorKind::Overflow,
            "trailing bytes after entity envelope tape",
        ));
    }
    Ok(EntityChunkEnvelope {
        data_version,
        chunk_x,
        chunk_z,
        requires_dfu: false,
        entities,
    })
}

fn encode_entity(output: &mut Vec<u8>, entity: &EntityEnvelope) -> EntityResult<()> {
    let mut flags = 0u32;
    if entity.id.is_some() {
        flags |= FLAG_ID_PRESENT;
    }
    if entity.uuid.is_some() {
        flags |= FLAG_UUID_PRESENT;
    }
    if entity.position.is_some() {
        flags |= FLAG_POSITION_PRESENT;
    }
    if entity.id_malformed {
        flags |= FLAG_ID_MALFORMED;
    }
    write_u32(output, flags);
    write_u32(output, entity.passenger_count);
    write_u32(output, entity.passenger_depth);
    write_u64(output, 0);
    let (uuid_most, uuid_least) = entity.uuid.unwrap_or((0, 0));
    write_i64(output, uuid_most);
    write_i64(output, uuid_least);
    let (x, y, z) = entity.position.unwrap_or((0, 0, 0));
    write_u64(output, x);
    write_u64(output, y);
    write_u64(output, z);
    let id_bytes = entity.id.as_deref().unwrap_or("").as_bytes();
    let id_len = u32::try_from(id_bytes.len()).map_err(|_| {
        EntityError::new(
            EntityErrorKind::Overflow,
            "entity id is too large for the typed buffer",
        )
    })?;
    let blob_len = u32::try_from(entity.nbt_tape.len()).map_err(|_| {
        EntityError::new(
            EntityErrorKind::Overflow,
            "entity NBT tape is too large for the typed buffer",
        )
    })?;
    write_u32(output, id_len);
    write_u32(output, blob_len);
    output.extend_from_slice(id_bytes);
    output.extend_from_slice(&entity.nbt_tape);
    Ok(())
}

fn decode_entity(reader: &mut TapeReader<'_>) -> EntityResult<EntityEnvelope> {
    let flags = reader.read_u32()?;
    let passenger_count = reader.read_u32()?;
    let passenger_depth = reader.read_u32()?;
    reader.read_u64()?;
    let uuid_most = reader.read_i64()?;
    let uuid_least = reader.read_i64()?;
    let x = reader.read_u64()?;
    let y = reader.read_u64()?;
    let z = reader.read_u64()?;
    let id_len = reader.read_u32()? as usize;
    let blob_len = reader.read_u32()? as usize;
    let id = if flags & FLAG_ID_PRESENT != 0 {
        Some(reader.read_utf8(id_len)?)
    } else {
        reader.skip(id_len)?;
        None
    };
    let nbt_tape = reader.read_bytes(blob_len)?.to_vec();
    Ok(EntityEnvelope {
        id,
        id_malformed: flags & FLAG_ID_MALFORMED != 0,
        uuid: (flags & FLAG_UUID_PRESENT != 0).then_some((uuid_most, uuid_least)),
        position: (flags & FLAG_POSITION_PRESENT != 0).then_some((x, y, z)),
        passenger_count,
        passenger_depth,
        nbt_tape,
    })
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

fn write_i64(output: &mut Vec<u8>, value: i64) {
    output.extend_from_slice(&value.to_le_bytes());
}

fn write_u64(output: &mut Vec<u8>, value: u64) {
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

    fn read_u16(&mut self) -> EntityResult<u16> {
        Ok(u16::from_le_bytes(self.read_array()?))
    }

    fn read_u32(&mut self) -> EntityResult<u32> {
        Ok(u32::from_le_bytes(self.read_array()?))
    }

    fn read_i32(&mut self) -> EntityResult<i32> {
        Ok(i32::from_le_bytes(self.read_array()?))
    }

    fn read_i64(&mut self) -> EntityResult<i64> {
        Ok(i64::from_le_bytes(self.read_array()?))
    }

    fn read_u64(&mut self) -> EntityResult<u64> {
        Ok(u64::from_le_bytes(self.read_array()?))
    }

    fn read_utf8(&mut self, len: usize) -> EntityResult<String> {
        let bytes = self.read_bytes(len)?;
        std::str::from_utf8(bytes)
            .map(|value| value.to_owned())
            .map_err(|_| EntityError::new(EntityErrorKind::WrongType, "invalid UTF-8 entity id"))
    }

    fn read_bytes(&mut self, len: usize) -> EntityResult<&'a [u8]> {
        self.require(len)?;
        let start = self.cursor;
        self.cursor += len;
        Ok(&self.input[start..start + len])
    }

    fn skip(&mut self, len: usize) -> EntityResult<()> {
        self.require(len)?;
        self.cursor += len;
        Ok(())
    }

    fn is_done(&self) -> bool {
        self.cursor == self.input.len()
    }

    fn read_array<const N: usize>(&mut self) -> EntityResult<[u8; N]> {
        self.require(N)?;
        let mut bytes = [0u8; N];
        bytes.copy_from_slice(&self.input[self.cursor..self.cursor + N]);
        self.cursor += N;
        Ok(bytes)
    }

    fn require(&self, len: usize) -> EntityResult<()> {
        if self.input.len().saturating_sub(self.cursor) < len {
            Err(EntityError::new(
                EntityErrorKind::Overflow,
                "truncated entity envelope tape",
            ))
        } else {
            Ok(())
        }
    }
}
