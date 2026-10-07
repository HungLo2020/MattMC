//! Decodes `NativeBlockRegistry`'s one-time export of Java's frozen block
//! registries and checks that its state layout is the arithmetic one.
//!
//! `ints`: [FORMAT, property count, block count, state count, face count];
//! per property: name length, value count, each value name's length; per
//! block: name length, default state offset, property count, property IDs
//! in name order; per state: its value index for each of its block's
//! properties; per state: six face IDs.
//! `chars`: the names, UTF-16, in the order their lengths appear.
//! `bytes`: per state: flags, light block, emission; then the face table.
use super::{BlockRegistry, Builder, Error, FaceId, PropertyId, StateFacts, StateFlags, DIRECTIONS};

pub(crate) const FORMAT: i32 = 1;

struct Cursor<'a, T> {
    values: &'a [T],
    at: usize,
}

impl<'a, T: Copy> Cursor<'a, T> {
    fn new(values: &'a [T]) -> Self {
        Self { values, at: 0 }
    }

    fn take(&mut self, n: usize) -> Result<&'a [T], Error> {
        let end = self.at.checked_add(n).ok_or(Error::Invalid("export length"))?;
        let values = self.values.get(self.at..end).ok_or(Error::Invalid("export truncated"))?;
        self.at = end;
        Ok(values)
    }

    fn done(&self) -> Result<(), Error> {
        if self.at == self.values.len() { Ok(()) } else { Err(Error::Invalid("export trailing data")) }
    }
}

impl Cursor<'_, i32> {
    fn next(&mut self) -> Result<i32, Error> {
        Ok(self.take(1)?[0])
    }

    fn count(&mut self, max: usize) -> Result<usize, Error> {
        let value = self.next()?;
        usize::try_from(value).ok().filter(|&v| v <= max).ok_or(Error::Invalid("export count"))
    }
}

fn string(chars: &mut Cursor<u16>, len: usize) -> Result<String, Error> {
    String::from_utf16(chars.take(len)?).map_err(|_| Error::Invalid("export name"))
}

pub(crate) fn decode(ints: &[i32], chars: &[u16], bytes: &[u8]) -> Result<BlockRegistry, Error> {
    let (mut ints, mut chars, mut bytes) = (Cursor::new(ints), Cursor::new(chars), Cursor::new(bytes));
    if ints.next()? != FORMAT {
        return Err(Error::Invalid("export format"));
    }
    let property_count = ints.count(u16::MAX as usize)?;
    let block_count = ints.count(u16::MAX as usize)?;
    let state_count = ints.count(super::MAX_STATES)?;
    let face_count = ints.count(u16::MAX as usize)?;
    let mut builder = Builder::new();
    for _ in 0..property_count {
        let name_len = ints.count(chars.values.len())?;
        let value_count = ints.count(u16::MAX as usize)?;
        let lengths = ints.take(value_count)?;
        let name = string(&mut chars, name_len)?;
        let mut values = Vec::with_capacity(value_count);
        for &len in lengths {
            let len = usize::try_from(len).map_err(|_| Error::Invalid("export count"))?;
            values.push(string(&mut chars, len)?);
        }
        builder.property_owned(name, values)?;
    }
    struct Pending {
        name: String,
        default: u16,
        properties: Vec<PropertyId>,
    }
    let mut blocks = Vec::with_capacity(block_count);
    for _ in 0..block_count {
        let name_len = ints.count(chars.values.len())?;
        let default = ints.count(u16::MAX as usize)? as u16;
        let count = ints.count(property_count)?;
        let mut properties = Vec::with_capacity(count);
        for _ in 0..count {
            properties.push(PropertyId(ints.count(property_count.saturating_sub(1))? as u16));
        }
        blocks.push(Pending { name: string(&mut chars, name_len)?, default, properties });
    }
    // Value indices per state, checked against the arithmetic layout below.
    let mut values = Vec::with_capacity(blocks.len());
    let mut states = 0usize;
    for block in &blocks {
        let size: usize = block.properties.iter().map(|p| builder.properties[p.0 as usize].values.len()).product();
        states = states.checked_add(size).filter(|&s| s <= state_count).ok_or(Error::Invalid("export state count"))?;
        values.push(ints.take(size * block.properties.len())?);
    }
    if states != state_count {
        return Err(Error::Invalid("export state count"));
    }
    let faces = ints.take(state_count * DIRECTIONS)?;
    let facts = bytes.take(state_count * 3)?;
    let mut state = 0usize;
    for block in &blocks {
        let size: usize = block.properties.iter().map(|p| builder.properties[p.0 as usize].values.len()).product();
        let mut list = Vec::with_capacity(size);
        for s in state..state + size {
            let f = &faces[s * DIRECTIONS..s * DIRECTIONS + DIRECTIONS];
            let mut light_faces = [FaceId(0); DIRECTIONS];
            for (face, &id) in light_faces.iter_mut().zip(f) {
                *face = FaceId(u16::try_from(id).map_err(|_| Error::Invalid("export face"))?);
            }
            list.push(StateFacts { flags: StateFlags(facts[s * 3]), light_block: facts[s * 3 + 1], emission: facts[s * 3 + 2], light_faces });
        }
        builder.block(&block.name, &block.properties, block.default, list)?;
        state += size;
    }
    let occludes = bytes.take(face_count * face_count)?.to_vec();
    ints.done()?;
    chars.done()?;
    bytes.done()?;
    let registry = builder.finish(face_count, occludes)?;
    // Java's expansion order must be the arithmetic one for every state.
    for (b, block) in registry.blocks().iter().enumerate() {
        let props: Vec<PropertyId> = block.properties().collect();
        for (i, state) in block.states().enumerate() {
            let row = &values[b][i * props.len()..(i + 1) * props.len()];
            for (&property, &value) in props.iter().zip(row) {
                if registry.value(state, property).map(i32::from) != Some(value) {
                    return Err(Error::Invalid("export state layout"));
                }
            }
            debug_assert_eq!(registry.state(super::BlockId(b as u16), &row.iter().map(|&v| v as u16).collect::<Vec<_>>()), Some(state));
        }
    }
    Ok(registry)
}
