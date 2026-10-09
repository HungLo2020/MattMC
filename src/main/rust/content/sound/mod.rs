//! Immutable sound content. Playback resources, channels and mixing belong to
//! audio; this owner contains only semantic identities and definitions.
mod events;
mod types;
mod instruments;
mod ffi;
#[cfg(test)]
mod tests;
pub use types::SoundType;
pub use instruments::Instrument;
pub(crate) use instruments::NAMES as INSTRUMENT_NAMES;
use std::{collections::HashMap, sync::OnceLock};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
#[repr(transparent)]
pub struct EventId(pub u16);

pub struct EventDefinition {
    pub id: EventId,
    pub key: &'static str,
    pub location: &'static str,
    pub fixed_range: Option<f32>,
}
impl EventDefinition {
    pub fn range(&self, volume: f32) -> f32 {
        self.fixed_range.unwrap_or_else(|| if volume > 1.0 { 16.0 * volume } else { 16.0 })
    }
}

pub struct SoundTypeDefinition {
    pub id: SoundType,
    pub name: &'static str,
    pub volume: f32,
    pub pitch: f32,
    /// Break, step, place, hit, fall, in that order.
    pub events: [EventId; 5],
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum InstrumentKind { Base = 0, Head = 1, Custom = 2 }
pub struct InstrumentDefinition {
    pub id: Instrument,
    pub name: &'static str,
    pub event: EventId,
    pub kind: InstrumentKind,
}
impl InstrumentDefinition {
    pub fn is_tunable(&self) -> bool { self.kind == InstrumentKind::Base }
    pub fn has_custom_sound(&self) -> bool { self.kind == InstrumentKind::Custom }
    pub fn works_above_note_block(&self) -> bool { self.kind != InstrumentKind::Base }
}

pub struct Registry {
    events: Vec<EventDefinition>,
    types: Vec<SoundTypeDefinition>,
    instruments: Vec<InstrumentDefinition>,
    by_key: HashMap<&'static str, EventId>,
    header: [i32; 5],
    event_rows: Vec<i32>,
    type_rows: Vec<i32>,
    instrument_rows: Vec<i32>,
    strings: Vec<u8>,
}
impl Registry {
    fn build() -> Self {
        let mut by_key = HashMap::new();
        let events: Vec<_> = events::EVENTS.iter().enumerate().map(|(id, &key)| {
            let id = EventId(u16::try_from(id).expect("bounded sound event IDs"));
            assert!(key.starts_with("minecraft:") && key.len() > 10, "qualified sound event");
            assert!(by_key.insert(key, id).is_none(), "duplicate sound event key");
            EventDefinition { id, key, location: key, fixed_range: None }
        }).collect();
        let find = |key: &str| *by_key.get(key).expect("declared sound event");
        let types = types::build(find);
        let instruments = instruments::build(find);
        let mut r = Self { events, types, instruments, by_key, header: [0;5],
            event_rows: Vec::new(), type_rows: Vec::new(), instrument_rows: Vec::new(), strings: Vec::new() };
        let mut offsets = HashMap::new();
        let mut text = |value: &'static str| -> [i32; 2] {
            *offsets.entry(value).or_insert_with(|| {
                let pair = [r.strings.len() as i32, value.len() as i32];
                r.strings.extend(value.as_bytes()); pair
            })
        };
        for e in &r.events {
            r.event_rows.extend(text(e.key)); r.event_rows.extend(text(e.location));
            r.event_rows.extend([i32::from(e.fixed_range.is_some()), e.fixed_range.unwrap_or(0.0).to_bits() as i32]);
        }
        for t in &r.types {
            r.type_rows.extend(text(t.name));
            r.type_rows.extend([t.volume.to_bits() as i32, t.pitch.to_bits() as i32]);
            r.type_rows.extend(t.events.map(|id| id.0 as i32));
        }
        for i in &r.instruments {
            r.instrument_rows.extend(text(i.name));
            r.instrument_rows.extend([i.event.0 as i32, i.kind as i32]);
        }
        r.header = [1, r.events.len() as i32, r.types.len() as i32, r.instruments.len() as i32, r.strings.len() as i32];
        r
    }
    pub fn events(&self) -> &[EventDefinition] { &self.events }
    pub fn event(&self, id: EventId) -> Option<&EventDefinition> { self.events.get(id.0 as usize) }
    pub fn find_event(&self, key: &str) -> Option<&EventDefinition> { self.by_key.get(key).and_then(|&id| self.event(id)) }
    pub fn types(&self) -> &[SoundTypeDefinition] { &self.types }
    pub fn instruments(&self) -> &[InstrumentDefinition] { &self.instruments }
}
pub fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(Registry::build)
}
