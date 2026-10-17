//! Native update/edge/descending-scan control. VM owns only actual effect resources.
//! No pointers, callbacks, block/shape objects or mutable position objects are retained here.
use super::{Error, Source};
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Pos {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}
impl Pos {
    fn down(self) -> Self {
        Self {
            y: self.y.wrapping_sub(1),
            ..self
        }
    }
}
#[derive(Clone, Copy, Debug, Default)]
pub struct Reply {
    pub value: i32,
    pub first: Pos,
    pub second: Pos,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum EdgeEffect {
    LowerLightBlock,
    UpperCanOcclude,
    UpperUseShape,
    UpperFace,
    UpperEmpty,
    LowerCanOcclude,
    LowerUseShape,
    LowerFace,
    LowerEmpty,
    UpperIsBlock,
    LowerIsBlock,
    UpperIsEmpty,
    LowerIsEmpty,
    JoinOr,
    JoinBlockMinus,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Effect {
    /// Set one actual MutableBlockPos if requested, invoke the real reader,
    /// store its actual BlockState in a VM slot. No source decision occurs in VM.
    Get {
        position: u8,
        set: Option<Pos>,
        destination: u8,
        copy_lower_to_upper: bool,
    },
    /// Update actual live alias objects and copy the actual state resource.
    Positions {
        first: Pos,
        second: Pos,
        copy_lower_to_upper: bool,
        set_upper_air: bool,
    },
    Highest,
    SectionY {
        index: i32,
        set_air: bool,
    },
    Section {
        index: i32,
    },
    SectionAir,
    SectionState {
        x: i32,
        y: i32,
        z: i32,
    },
    Edge(EdgeEffect),
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Request {
    Effect(Effect),
    Done(bool),
}
#[derive(Clone, Copy, Debug)]
pub(super) enum EdgeNext {
    Effect(EdgeEffect),
    Done(bool),
}
pub(super) struct Edge {
    current: EdgeEffect,
}
impl Edge {
    pub(super) fn current_effect(&self) -> EdgeEffect {
        self.current
    }

    pub(super) fn new() -> Self {
        Self {
            current: EdgeEffect::LowerLightBlock,
        }
    }
    pub(super) fn advance(&mut self, value: i32) -> EdgeNext {
        use EdgeEffect::*;
        let next = match self.current {
            LowerLightBlock if value != 0 => return EdgeNext::Done(true),
            LowerLightBlock => UpperCanOcclude,
            UpperCanOcclude if value != 0 => UpperUseShape,
            UpperCanOcclude => UpperEmpty,
            UpperUseShape if value != 0 => UpperFace,
            UpperUseShape => UpperEmpty,
            UpperFace | UpperEmpty => LowerCanOcclude,
            LowerCanOcclude if value != 0 => LowerUseShape,
            LowerCanOcclude => LowerEmpty,
            LowerUseShape if value != 0 => LowerFace,
            LowerUseShape => LowerEmpty,
            LowerFace | LowerEmpty => UpperIsBlock,
            UpperIsBlock | LowerIsBlock if value != 0 => return EdgeNext::Done(true),
            UpperIsBlock => LowerIsBlock,
            LowerIsBlock => UpperIsEmpty,
            UpperIsEmpty if value != 0 => LowerIsEmpty,
            UpperIsEmpty => JoinOr,
            LowerIsEmpty if value != 0 => return EdgeNext::Done(false),
            LowerIsEmpty => JoinOr,
            JoinOr => JoinBlockMinus,
            JoinBlockMinus => return EdgeNext::Done(value == 0),
        };
        self.current = next;
        EdgeNext::Effect(next)
    }
}
#[derive(Clone, Copy, Debug)]
enum Phase {
    First,
    Second,
    Third,
    FirstEdge,
    SecondEdge,
    BelowPositions,
    BelowGet,
    BelowEdge,
    Done(bool),
}
/// A pending producer holds the actual native source state through every VM effect.
/// No source mutex is held while an effect is requested or performed.
pub struct Update {
    source: Source,
    index: i32,
    original: i32,
    min: i32,
    x: i32,
    y: i32,
    z: i32,
    phase: Phase,
    captured_y: i32,
    edge: Edge,
}
impl Update {
    pub fn new(source: Source, x: i32, y: i32, z: i32) -> Result<(Self, Request), Error> {
        let index = x.wrapping_add(z.wrapping_mul(16));
        let (min, original) = {
            let store = source.0.lock().map_err(|_| Error::Poisoned)?;
            (store.min, store.get(index)?.wrapping_add(store.min))
        };
        let skip = y.wrapping_add(1) < original;
        let update = Self {
            source,
            index,
            original,
            min,
            x,
            y,
            z,
            phase: if skip {
                Phase::Done(false)
            } else {
                Phase::First
            },
            captured_y: 0,
            edge: Edge::new(),
        };
        let request = if skip {
            Request::Done(false)
        } else {
            Request::Effect(Effect::Get {
                position: 0,
                set: Some(Pos {
                    x,
                    y: y.wrapping_add(1),
                    z,
                }),
                destination: 0,
                copy_lower_to_upper: false,
            })
        };
        Ok((update, request))
    }
    fn done(&mut self, value: bool) -> Result<Request, Error> {
        self.phase = Phase::Done(value);
        Ok(Request::Done(value))
    }
    fn commit(&mut self, y: i32) -> Result<Request, Error> {
        self.source.set(self.index, y)?;
        self.done(true)
    }
    fn start_edge(&mut self, phase: Phase) -> Result<Request, Error> {
        self.phase = phase;
        self.edge = Edge::new();
        Ok(Request::Effect(Effect::Edge(self.edge.current)))
    }
    fn start_below(&mut self, position: Pos) -> Result<Request, Error> {
        self.phase = Phase::BelowPositions;
        Ok(Request::Effect(Effect::Positions {
            first: position,
            second: position.down(),
            copy_lower_to_upper: true,
            set_upper_air: false,
        }))
    }
    fn fallback(&mut self) -> Result<Request, Error> {
        self.phase = Phase::Third;
        Ok(Request::Effect(Effect::Get {
            position: 0,
            set: Some(Pos {
                x: self.x,
                y: self.y.wrapping_sub(1),
                z: self.z,
            }),
            destination: 1,
            copy_lower_to_upper: true,
        }))
    }
    pub fn resume(&mut self, reply: Reply) -> Result<Request, Error> {
        match self.phase {
            Phase::Done(value) => Ok(Request::Done(value)),
            Phase::First => {
                self.phase = Phase::Second;
                Ok(Request::Effect(Effect::Get {
                    position: 1,
                    set: Some(Pos {
                        x: self.x,
                        y: self.y,
                        z: self.z,
                    }),
                    destination: 1,
                    copy_lower_to_upper: false,
                }))
            }
            // updateEdge captures its upper Y BEFORE any getLightBlock/shape effects.
            Phase::Second => {
                self.captured_y = reply.first.y;
                self.start_edge(Phase::FirstEdge)
            }
            Phase::Third => {
                self.captured_y = reply.second.y;
                self.start_edge(Phase::SecondEdge)
            }
            Phase::FirstEdge | Phase::SecondEdge | Phase::BelowEdge => {
                match self.edge.advance(reply.value) {
                    EdgeNext::Effect(effect) => Ok(Request::Effect(Effect::Edge(effect))),
                    EdgeNext::Done(occluded) => {
                        if matches!(self.phase, Phase::BelowEdge) {
                            // findLowestSourceBelow reads the LIVE upper Y AFTER edge effects.
                            if occluded {
                                self.commit(reply.first.y)
                            } else {
                                self.start_below(reply.second)
                            }
                        } else if occluded && self.captured_y > self.original {
                            self.commit(self.captured_y)
                        } else if !occluded && self.captured_y == self.original {
                            let lower = if matches!(self.phase, Phase::FirstEdge) {
                                reply.second
                            } else {
                                reply.first
                            };
                            self.start_below(lower)
                        } else if matches!(self.phase, Phase::FirstEdge) {
                            self.fallback()
                        } else {
                            self.done(false)
                        }
                    }
                }
            }
            Phase::BelowPositions => {
                if reply.second.y < self.min {
                    return self.commit(self.min);
                }
                self.phase = Phase::BelowGet;
                Ok(Request::Effect(Effect::Get {
                    position: 1,
                    set: None,
                    destination: 1,
                    copy_lower_to_upper: false,
                }))
            }
            Phase::BelowGet => self.start_edge(Phase::BelowEdge),
        }
    }
}
