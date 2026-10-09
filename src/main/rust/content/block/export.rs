//! Installs remaining Java-owned state facts against native block definitions.
//! Format: [version, block count, state count, face count], then
//! per state six face IDs and remaining flags.
//! Bytes: blocked light per state, then face matrix.
//! Java no longer exports names, defaults, properties, state value indices,
//! emitted light, fluid associations or offsets; intrinsic data is native-owned.
use super::{definitions, BlockRegistry, Builder, Error, FaceId, PropertyId, StateFacts, StateFlags, DIRECTIONS};
use crate::content::property;
use definitions::physics::PhysicalFlags;

pub(crate) const FORMAT: i32 = 8;
const STATE_INTS: usize = DIRECTIONS + 1;

struct Cursor<'a, T> { values: &'a [T], at: usize }
impl<'a, T: Copy> Cursor<'a, T> {
    fn new(values: &'a [T]) -> Self { Self { values, at: 0 } }
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
    fn next(&mut self) -> Result<i32, Error> { Ok(self.take(1)?[0]) }
    fn count(&mut self, max: usize) -> Result<usize, Error> {
        usize::try_from(self.next()?).ok().filter(|&n| n <= max).ok_or(Error::Invalid("export count"))
    }
}

pub(crate) fn decode(ints: &[i32], bytes: &[u8]) -> Result<BlockRegistry, Error> {
    let (mut ints, mut bytes) = (Cursor::new(ints), Cursor::new(bytes));
    if ints.next()? != FORMAT { return Err(Error::Invalid("export format")); }
    let native = definitions::registry();
    let blocks = ints.count(u16::MAX as usize)?;
    let states = ints.count(super::MAX_STATES)?;
    let faces = ints.count(u16::MAX as usize)?;
    let expected_states: usize = native.definitions().iter().map(|d| native.state_count(d)).sum();
    if blocks != native.definitions().len() || states != expected_states { return Err(Error::Invalid("native definition count")); }
    let rows = ints.take(states * STATE_INTS)?;
    let facts = bytes.take(states)?;
    let matrix = bytes.take(faces * faces)?.to_vec();
    ints.done()?;
    bytes.done()?;
    let mut builder = Builder::new();
    let mut ids: Vec<Option<PropertyId>> = vec![None; property::registry().definitions().len()];
    for definition in native.definitions() {
        let template = native.template(definition);
        let mut properties = Vec::with_capacity(template.properties.len());
        for &property in &template.properties {
            let entry = &mut ids[property as usize];
            let id = match *entry {
                Some(id) => id,
                None => {
                    let id = builder.property_shared(property.definition().schema.clone())?;
                    *entry = Some(id);
                    id
                }
            };
            properties.push(id);
        }
        let first = definition.first_state.0 as usize;
        let mut state_facts = Vec::with_capacity(native.state_count(definition));
        for state in first..first + native.state_count(definition) {
            let row = &rows[state * STATE_INTS..(state + 1) * STATE_INTS];
            let light = facts[state];
            let mut light_faces = [FaceId(0); DIRECTIONS];
            for (face, &id) in light_faces.iter_mut().zip(&row[..DIRECTIONS]) {
                *face = FaceId(u16::try_from(id).map_err(|_| Error::Invalid("export face"))?);
            }
            let mut flags = u16::try_from(row[DIRECTIONS]).map_err(|_| Error::Invalid("export flags"))?;
            if flags & (StateFlags::AIR.0 | StateFlags::CAN_OCCLUDE.0) != 0 {
                return Err(Error::Invalid("native physical flags supplied by Java"));
            }
            if definition.physics.flags.contains(PhysicalFlags::AIR) { flags |= StateFlags::AIR.0; }
            if definition.physics.flags.contains(PhysicalFlags::CAN_OCCLUDE) { flags |= StateFlags::CAN_OCCLUDE.0; }
            let intrinsic = native.state_traits(super::StateId(state as u16)).expect("native intrinsic state");
            state_facts.push(StateFacts {
                flags: StateFlags(flags),
                light_block: light, emission: intrinsic.emission, light_faces,
                fluid_state: intrinsic.fluid,
                offset: definition.material.offset.config().kind,
            });
        }
        let id = builder.block(definition.name, &properties, template.default_local, state_facts)?;
        let offset = definition.material.offset.config();
        builder.max_offsets(id, offset.horizontal, offset.vertical)?;
    }
    builder.finish(faces, matrix)
}
