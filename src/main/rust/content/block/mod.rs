//! The block registry: every block, its properties and every block state.
//! A state is a dense [`StateId`]; per-state facts are columns indexed by it,
//! and a property value is arithmetic on the ID (`first + Σ index × stride`).
//!
//! The process-wide registry is installed once (today from Java's frozen
//! registries, see `NativeBlockRegistry`) and never changes. Subsystems derive
//! their own lookup tables from it instead of receiving copies from Java.
pub(crate) mod export;
pub(crate) mod collision;
pub mod definitions;
pub mod family;
pub mod offset;
pub(crate) mod ffi;
#[cfg(test)]
mod tests;

use std::collections::HashMap;
use std::ops::Range;
use std::sync::{Arc, OnceLock};
pub use super::property::Schema as Property;
use super::state::{StateLayout, StateSlot};
use super::fluid::{self, FluidStateId};

/// A block, by its `BuiltInRegistries.BLOCK` ID.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
#[repr(transparent)]
pub struct BlockId(pub u16);

/// A block state, by its `Block.BLOCK_STATE_REGISTRY` ID.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
#[repr(transparent)]
pub struct StateId(pub u16);

/// A block-state property (one per distinct Java `Property` object).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
#[repr(transparent)]
pub struct PropertyId(pub u16);

/// An interned face shape: equal box lists share an ID. Face 0 is empty.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, Default)]
#[repr(transparent)]
pub struct FaceId(pub u16);

impl StateId {
    pub const fn index(self) -> usize {
        self.0 as usize
    }
}

/// The most states a registry may hold. `u16::MAX` stays free so tables can
/// use it as "no state".
pub const MAX_STATES: usize = super::state::MAX_STATES;

/// `Direction.values()` order: down, up, north, south, west, east.
pub const DIRECTIONS: usize = 6;

/// Boolean facts about a state.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
#[repr(transparent)]
pub struct StateFlags(pub u16);

impl StateFlags {
    /// `isAir()`.
    pub const AIR: StateFlags = StateFlags(1);
    /// `blocksMotion()`.
    pub const BLOCKS_MOTION: StateFlags = StateFlags(2);
    /// `!getFluidState().isEmpty()`.
    pub const HAS_FLUID: StateFlags = StateFlags(4);
    /// `isRandomlyTicking()`.
    pub const RANDOM_TICKS: StateFlags = StateFlags(8);
    /// `LightEngine.isEmptyShape`: `!canOcclude() || !useShapeForLightOcclusion()`.
    pub const LIGHT_EMPTY_SHAPE: StateFlags = StateFlags(16);
    /// The block is a `LeavesBlock`.
    pub const LEAVES: StateFlags = StateFlags(32);
    /// A `BlockState` subclass. Its answers are not plain data, so consumers
    /// leave these states to Java.
    pub const CUSTOM: StateFlags = StateFlags(64);
    /// `isSolidRender()`.
    pub const SOLID_RENDER: StateFlags = StateFlags(128);
    /// `canOcclude()`.
    pub const CAN_OCCLUDE: StateFlags = StateFlags(256);
    /// `hasBlockEntity()`.
    pub const BLOCK_ENTITY: StateFlags = StateFlags(512);
    /// The fluid state's `FALLING` property is true.
    pub const FLUID_FALLING: StateFlags = StateFlags(1024);
    pub(crate) const ALL: u16 = 2047;

    pub const fn contains(self, flag: StateFlags) -> bool {
        self.0 & flag.0 != 0
    }
}

/// A state's fluid: `getFluidState()`'s type.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum FluidKind {
    #[default]
    None = 0,
    /// `Fluids.WATER` or `Fluids.FLOWING_WATER`.
    Water = 1,
    /// `Fluids.LAVA` or `Fluids.FLOWING_LAVA`.
    Lava = 2,
    Other = 3,
}

/// `BlockBehaviour.OffsetType`: how a state's model is offset by position.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum OffsetType {
    #[default]
    None = 0,
    Xz = 1,
    Xyz = 2,
}

impl OffsetType {
    pub(crate) fn from_u8(value: u8) -> Option<Self> {
        Some(match value {
            0 => Self::None,
            1 => Self::Xz,
            2 => Self::Xyz,
            _ => return None,
        })
    }
}

/// The facts recorded for one state.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub struct StateFacts {
    /// Block facts only. HAS_FLUID and FLUID_FALLING are derived at freeze.
    pub flags: StateFlags,
    /// `getLightBlock()`, 0..=15.
    pub light_block: u8,
    /// `getLightEmission()`, 0..=15.
    pub emission: u8,
    /// `LightEngine.getOcclusionShape(state, direction)` per direction.
    pub light_faces: [FaceId; DIRECTIONS],
    /// Association only; all fluid facts come from the native fluid registry.
    pub fluid_state: FluidStateId,
    pub offset: OffsetType,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct Slot {
    property: PropertyId,
    state: StateSlot,
}

#[derive(Debug, PartialEq, Eq)]
pub struct Block {
    name: String,
    first: u16,
    count: u16,
    default: StateId,
    /// Properties in name order; the last varies fastest, as in
    /// `StateDefinition`'s state expansion.
    slots: Vec<Slot>,
    /// `getMaxHorizontalOffset()` and `getMaxVerticalOffset()` as `f32` bits.
    max_offset_bits: [u32; 2],
}

impl Block {
    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn default_state(&self) -> StateId {
        self.default
    }

    /// `getMaxHorizontalOffset()`.
    pub fn max_horizontal_offset(&self) -> f32 {
        f32::from_bits(self.max_offset_bits[0])
    }

    /// `getMaxVerticalOffset()`.
    pub fn max_vertical_offset(&self) -> f32 {
        f32::from_bits(self.max_offset_bits[1])
    }

    pub fn states(&self) -> impl ExactSizeIterator<Item = StateId> {
        (self.first..self.first + self.count).map(StateId)
    }

    pub fn state_range(&self) -> Range<usize> {
        self.first as usize..self.first as usize + self.count as usize
    }

    /// Property IDs in name order.
    pub fn properties(&self) -> impl DoubleEndedIterator<Item = PropertyId> + ExactSizeIterator + '_ {
        self.slots.iter().map(|s| s.property)
    }

    fn slot(&self, property: PropertyId) -> Option<&Slot> {
        self.slots.iter().find(|s| s.property == property)
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum Error {
    Invalid(&'static str),
    /// A different registry is already installed.
    Conflict,
}

/// Blocks, properties and per-state columns. Immutable once built.
#[derive(Debug, PartialEq, Eq)]
pub struct BlockRegistry {
    blocks: Vec<Block>,
    by_name: HashMap<String, BlockId>,
    properties: Vec<Arc<Property>>,
    block: Vec<BlockId>,
    flags: Vec<StateFlags>,
    light_block: Vec<u8>,
    emission: Vec<u8>,
    light_faces: Vec<[FaceId; DIRECTIONS]>,
    fluid_state: Vec<FluidStateId>,
    offset: Vec<OffsetType>,
    face_count: usize,
    occludes: Vec<u8>,
    air: Option<BlockId>,
}

impl BlockRegistry {
    pub fn state_count(&self) -> usize {
        self.block.len()
    }

    pub fn blocks(&self) -> &[Block] {
        &self.blocks
    }

    pub fn block(&self, id: BlockId) -> &Block {
        &self.blocks[id.0 as usize]
    }

    pub fn by_name(&self, name: &str) -> Option<BlockId> {
        self.by_name.get(name).copied()
    }

    pub fn properties(&self) -> &[Arc<Property>] {
        &self.properties
    }

    pub fn property(&self, id: PropertyId) -> &Property {
        &self.properties[id.0 as usize]
    }

    /// `minecraft:air`, when registered.
    pub fn air(&self) -> Option<BlockId> {
        self.air
    }

    pub fn block_of(&self, state: StateId) -> BlockId {
        self.block[state.index()]
    }

    pub fn flags(&self, state: StateId) -> StateFlags {
        self.flags[state.index()]
    }

    pub fn light_block(&self, state: StateId) -> u8 {
        self.light_block[state.index()]
    }

    pub fn emission(&self, state: StateId) -> u8 {
        self.emission[state.index()]
    }

    pub fn light_face(&self, state: StateId, direction: usize) -> FaceId {
        self.light_faces[state.index()][direction]
    }

    pub fn fluid_state(&self, state: StateId) -> FluidStateId {
        self.fluid_state[state.index()]
    }

    pub fn fluid(&self, state: StateId) -> FluidKind {
        let fluids = fluid::registry();
        let traits = fluids.state(self.fluid_state(state)).expect("validated fluid state");
        match fluids.definition(traits.fluid).expect("built-in fluid owner").family {
            fluid::Family::Empty => FluidKind::None,
            fluid::Family::Water => FluidKind::Water,
            fluid::Family::Lava => FluidKind::Lava,
        }
    }

    /// `getFluidState().getOwnHeight()`, from its native owner.
    pub fn fluid_height(&self, state: StateId) -> f32 {
        fluid::registry().state(self.fluid_state(state)).expect("validated fluid state").own_height
    }

    pub fn offset(&self, state: StateId) -> OffsetType {
        self.offset[state.index()]
    }

    /// Columns for consumers that scan every state.
    pub fn block_column(&self) -> &[BlockId] {
        &self.block
    }

    pub fn flag_column(&self) -> &[StateFlags] {
        &self.flags
    }

    pub fn light_block_column(&self) -> &[u8] {
        &self.light_block
    }

    pub fn emission_column(&self) -> &[u8] {
        &self.emission
    }

    pub fn light_face_column(&self) -> &[[FaceId; DIRECTIONS]] {
        &self.light_faces
    }

    pub fn face_count(&self) -> usize {
        self.face_count
    }

    /// `Shapes.faceShapeOccludes(from, to)`.
    pub fn face_occludes(&self, from: FaceId, to: FaceId) -> bool {
        self.occludes[from.0 as usize * self.face_count + to.0 as usize] != 0
    }

    /// The `face_count`² truth table of [`Self::face_occludes`], row-major by `from`.
    pub fn face_matrix(&self) -> &[u8] {
        &self.occludes
    }

    /// The value index of `property` in `state`, if its block has it.
    pub fn value(&self, state: StateId, property: PropertyId) -> Option<u16> {
        let block = self.block(self.block_of(state));
        let slot = block.slot(property)?;
        Some(slot.state.value(state.0 - block.first))
    }

    /// `state.setValue(property, values[index])`.
    pub fn with_value(&self, state: StateId, property: PropertyId, index: u16) -> Option<StateId> {
        let block = self.block(self.block_of(state));
        let slot = block.slot(property)?;
        slot.state.with_value(state.0 - block.first, index).map(|local| StateId(block.first + local))
    }

    /// The state of `block` with each property (in name order) at `values`.
    pub fn state(&self, block: BlockId, values: &[u16]) -> Option<StateId> {
        let block = self.blocks.get(block.0 as usize)?;
        if values.len() != block.slots.len() {
            return None;
        }
        let mut offset = 0;
        for (slot, &value) in block.slots.iter().zip(values) {
            if value >= slot.state.count {
                return None;
            }
            offset += value * slot.state.stride;
        }
        Some(StateId(block.first + offset))
    }
}

/// Assembles a registry in ID order and validates it.
#[derive(Default)]
pub struct Builder {
    blocks: Vec<Block>,
    properties: Vec<Arc<Property>>,
    states: Vec<(BlockId, StateFacts)>,
}

impl Builder {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn property(&mut self, name: &str, values: &[&str]) -> Result<PropertyId, Error> {
        self.property_owned(name.to_owned(), values.iter().map(|v| (*v).to_owned()).collect())
    }

    pub(crate) fn property_owned(&mut self, name: String, values: Vec<String>) -> Result<PropertyId, Error> {
        let schema = Property::new(name, values).ok_or(Error::Invalid("property value count"))?;
        self.property_shared(Arc::new(schema))
    }

    pub fn property_shared(&mut self, schema: Arc<Property>) -> Result<PropertyId, Error> {
        let id = u16::try_from(self.properties.len()).map_err(|_| Error::Invalid("property count"))?;
        self.properties.push(schema);
        Ok(PropertyId(id))
    }

    /// Adds the next block. `properties` are in name order; `states` holds
    /// each state's facts with the last property varying fastest;
    /// `default` is the default state's offset within the block.
    pub fn block(&mut self, name: &str, properties: &[PropertyId], default: u16, states: Vec<StateFacts>) -> Result<BlockId, Error> {
        let id = u16::try_from(self.blocks.len()).map_err(|_| Error::Invalid("block count"))?;
        let mut counts = Vec::with_capacity(properties.len());
        for (i, &property) in properties.iter().enumerate() {
            let values = self.properties.get(property.0 as usize).ok_or(Error::Invalid("property id"))?.values().len();
            if properties[..i].contains(&property) {
                return Err(Error::Invalid("repeated property"));
            }
            counts.push(values as u16);
        }
        let layout = StateLayout::new(&counts).map_err(|_| Error::Invalid("state count"))?;
        let count = layout.state_count();
        let slots = properties.iter().zip(layout.slots()).map(|(&property, &state)| Slot { property, state }).collect();
        if states.len() != count || default as usize >= count {
            return Err(Error::Invalid("block states"));
        }
        let first = self.states.len();
        if first + count > MAX_STATES {
            return Err(Error::Invalid("state count"));
        }
        self.blocks.push(Block {
            name: name.to_owned(),
            first: first as u16,
            count: count as u16,
            default: StateId((first + default as usize) as u16),
            slots,
            max_offset_bits: [0.25f32.to_bits(), 0.2f32.to_bits()],
        });
        self.states.extend(states.into_iter().map(|facts| (BlockId(id), facts)));
        Ok(BlockId(id))
    }

    /// Sets a block's `getMaxHorizontalOffset()` and `getMaxVerticalOffset()`
    /// (by default Java's 0.25 and 0.2).
    pub fn max_offsets(&mut self, block: BlockId, horizontal: f32, vertical: f32) -> Result<(), Error> {
        let block = self.blocks.get_mut(block.0 as usize).ok_or(Error::Invalid("block id"))?;
        block.max_offset_bits = [horizontal.to_bits(), vertical.to_bits()];
        Ok(())
    }

    /// Validates and freezes the registry with the face truth table
    /// (`face_count`² bytes, row-major by occluding face).
    pub fn finish(self, face_count: usize, occludes: Vec<u8>) -> Result<BlockRegistry, Error> {
        if face_count == 0 || face_count > u16::MAX as usize || occludes.len() != face_count * face_count {
            return Err(Error::Invalid("face table"));
        }
        if occludes.iter().any(|&o| o > 1) {
            return Err(Error::Invalid("face table values"));
        }
        let mut by_name = HashMap::with_capacity(self.blocks.len());
        for (i, block) in self.blocks.iter().enumerate() {
            if by_name.insert(block.name.clone(), BlockId(i as u16)).is_some() {
                return Err(Error::Invalid("duplicate block name"));
            }
        }
        let n = self.states.len();
        let mut registry = BlockRegistry {
            air: by_name.get("minecraft:air").copied(),
            blocks: self.blocks,
            by_name,
            properties: self.properties,
            block: Vec::with_capacity(n),
            flags: Vec::with_capacity(n),
            light_block: Vec::with_capacity(n),
            emission: Vec::with_capacity(n),
            light_faces: Vec::with_capacity(n),
            fluid_state: Vec::with_capacity(n),
            offset: Vec::with_capacity(n),
            face_count,
            occludes,
        };
        let fluids = fluid::registry();
        let derived_flags = StateFlags::HAS_FLUID.0 | StateFlags::FLUID_FALLING.0;
        for (block, facts) in self.states {
            if facts.flags.0 & !(StateFlags::ALL & !derived_flags) != 0 || facts.light_block > 15 || facts.emission > 15
                || facts.light_faces.iter().any(|f| f.0 as usize >= face_count)
            {
                return Err(Error::Invalid("state facts"));
            }
            let traits = fluids.state(facts.fluid_state).ok_or(Error::Invalid("fluid state id"))?;
            let definition = fluids.definition(traits.fluid).expect("built-in fluid owner");
            let mut flags = facts.flags;
            if definition.family != fluid::Family::Empty { flags.0 |= StateFlags::HAS_FLUID.0; }
            if traits.falling { flags.0 |= StateFlags::FLUID_FALLING.0; }
            registry.block.push(block);
            registry.flags.push(flags);
            registry.light_block.push(facts.light_block);
            registry.emission.push(facts.emission);
            registry.light_faces.push(facts.light_faces);
            registry.fluid_state.push(facts.fluid_state);
            registry.offset.push(facts.offset);
        }
        Ok(registry)
    }
}

static INSTALLED: OnceLock<BlockRegistry> = OnceLock::new();

/// The process-wide registry, once installed.
pub fn installed() -> Option<&'static BlockRegistry> {
    INSTALLED.get()
}

/// Installs the process-wide registry. Installing an equal registry again
/// succeeds; a different one is a [`Error::Conflict`].
pub fn install(registry: BlockRegistry) -> Result<&'static BlockRegistry, Error> {
    match INSTALLED.set(registry) {
        Ok(()) => Ok(INSTALLED.get().expect("just installed")),
        Err(registry) => {
            let current = INSTALLED.get().expect("already installed");
            if *current == registry { Ok(current) } else { Err(Error::Conflict) }
        }
    }
}
