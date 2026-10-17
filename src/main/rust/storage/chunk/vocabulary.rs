//! The block-state vocabulary built from the block registry: each state's
//! `BlockState.CODEC` compound as an NBT list-element tape, and the block
//! palette's `bitsInStorage` per size.
use super::{hash_map_order, units, Tape, Vocabulary, TAG_COMPOUND, TAG_STRING};
use crate::content::block::{BlockRegistry, StateFlags, StateId};
use std::sync::OnceLock;

/// Most keys [`hash_map_order`] models (a default-capacity `HashMap`).
const MAX_KEYS: usize = 12;

/// `Strategy.createForBlockStates(...).getConfigurationForPaletteSize(size).bitsInStorage()`.
pub(crate) fn block_storage_bits(size: usize) -> u8 {
    // Mth.ceillog2.
    let bits = if size <= 1 { 0 } else { usize::BITS - (size - 1).leading_zeros() } as u8;
    match bits {
        0 => 0,
        1..=4 => 4,
        _ => bits,
    }
}

/// Appends the tape of `writeTag("", BlockState.CODEC.encodeStart(NbtOps, state))`:
/// {Name, Properties?} with Properties omitted for the default state, keys
/// in `HashMap` order of their insertion order (the dispatch codec adds
/// Properties before Name; the properties codec adds the last property
/// first). `None` when a compound has more keys than modelled.
fn fragment(registry: &BlockRegistry, state: StateId, tape: &mut Tape) -> Option<()> {
    let block = registry.block(registry.block_of(state));
    let name = units(block.name());
    let name_key = units("Name");
    let properties_key = units("Properties");
    let with_properties = block.properties().len() > 0 && state != block.default_state();
    let outer: Vec<&[u16]> = if with_properties { vec![&properties_key, &name_key] } else { vec![&name_key] };
    tape.record(TAG_COMPOUND, 0, &[], outer.len(), 0);
    for index in hash_map_order(&outer) {
        if outer[index] == name_key.as_slice() {
            tape.record(TAG_STRING, 0, &name_key, name.len(), 0);
            push_units(tape, &name);
            continue;
        }
        let properties: Vec<_> = block.properties().rev().collect();
        if properties.len() > MAX_KEYS {
            return None;
        }
        let keys: Vec<Vec<u16>> = properties.iter().map(|&p| units(registry.property(p).name())).collect();
        let key_refs: Vec<&[u16]> = keys.iter().map(Vec::as_slice).collect();
        tape.record(TAG_COMPOUND, 0, &properties_key, keys.len(), 0);
        for i in hash_map_order(&key_refs) {
            let property = registry.property(properties[i]);
            let value = units(&property.values()[registry.value(state, properties[i])? as usize]);
            tape.record(TAG_STRING, 0, &keys[i], value.len(), 0);
            push_units(tape, &value);
        }
    }
    Some(())
}

fn push_units(tape: &mut Tape, units: &[u16]) {
    for unit in units {
        tape.out.extend_from_slice(&unit.to_le_bytes());
    }
}

/// The vocabulary of `registry`: labels are state ids. `None` when a state
/// is a custom `BlockState` subclass (Java encodes those chunks) or a
/// compound is not modelled.
pub(crate) fn vocabulary(registry: &BlockRegistry) -> Option<Vocabulary> {
    if registry.flag_column().iter().any(|f| f.contains(StateFlags::CUSTOM)) {
        return None;
    }
    let mut tape = Tape { out: Vec::new() };
    let mut offsets = Vec::with_capacity(registry.state_count() + 1);
    offsets.push(0);
    for s in 0..registry.state_count() {
        fragment(registry, StateId(s as u16), &mut tape)?;
        offsets.push(u32::try_from(tape.out.len()).ok()?);
    }
    Some(Vocabulary {
        labels: (0..registry.state_count() as u32).collect(),
        fragments: tape.out,
        offsets,
        bits: (0..=4096).map(block_storage_bits).collect(),
        lookup: OnceLock::new(),
    })
}

/// [`vocabulary`] of the installed registry; `None` until it is installed
/// or when Java must encode its states.
pub(crate) fn installed() -> Option<&'static Vocabulary> {
    static VOCABULARY: OnceLock<Option<Vocabulary>> = OnceLock::new();
    let registry = crate::content::block::installed()?;
    VOCABULARY.get_or_init(|| vocabulary(registry)).as_ref()
}
