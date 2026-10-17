//! Native custom/public source fill control and committed-prefix publication.
use super::{
    source_update::{Edge, EdgeNext, Effect, Pos, Reply, Request},
    Error, Source,
};
#[derive(Clone, Copy)]
enum Phase {
    Highest,
    InitialY,
    InitialPositions,
    Section,
    Air,
    EmptyY,
    EmptyPositions,
    State,
    Edge,
    AdvancePositions,
    Done,
}
pub struct Fill {
    source: Source,
    min: i32,
    highest: i32,
    section: i32,
    block_y: i32,
    column: i32,
    phase: Phase,
    edge: Edge,
}
impl Fill {
    pub fn new(source: Source) -> Result<(Self, Request), Error> {
        let min = source.0.lock().map_err(|_| Error::Poisoned)?.min;
        Ok((
            Self {
                source,
                min,
                highest: 0,
                section: 0,
                block_y: 15,
                column: 0,
                phase: Phase::Highest,
                edge: Edge::new(),
            },
            Request::Effect(Effect::Highest),
        ))
    }
    fn effect(&mut self, phase: Phase, effect: Effect) -> Result<Request, Error> {
        self.phase = phase;
        Ok(Request::Effect(effect))
    }
    fn begin_column(&mut self) -> Result<Request, Error> {
        self.section = self.highest;
        self.effect(
            Phase::InitialY,
            Effect::SectionY {
                index: self.highest,
                set_air: false,
            },
        )
    }
    fn commit_column(&mut self, y: i32) -> Result<Request, Error> {
        self.source.set(self.column, y.max(self.min))?;
        self.column += 1;
        if self.column == 256 {
            self.phase = Phase::Done;
            Ok(Request::Done(true))
        } else {
            self.begin_column()
        }
    }
    fn read_section(&mut self) -> Result<Request, Error> {
        if self.section < 0 {
            self.commit_column(self.min)
        } else {
            self.effect(
                Phase::Section,
                Effect::Section {
                    index: self.section,
                },
            )
        }
    }
    fn read_state(&mut self) -> Result<Request, Error> {
        if self.block_y < 0 {
            self.section = self.section.wrapping_sub(1);
            self.read_section()
        } else {
            self.effect(
                Phase::State,
                Effect::SectionState {
                    x: self.column & 15,
                    y: self.block_y,
                    z: self.column >> 4,
                },
            )
        }
    }
    pub fn resume(&mut self, reply: Reply) -> Result<Request, Error> {
        match self.phase {
            Phase::Done => Ok(Request::Done(true)),
            Phase::Highest => {
                self.highest = reply.value;
                if self.highest == -1 {
                    self.source.clear()?;
                    self.phase = Phase::Done;
                    Ok(Request::Done(true))
                } else {
                    self.begin_column()
                }
            }
            Phase::InitialY => {
                let y = reply.value.wrapping_add(1).wrapping_shl(4);
                let first = Pos {
                    x: self.column & 15,
                    y,
                    z: self.column >> 4,
                };
                let second = Pos {
                    y: y.wrapping_sub(1),
                    ..first
                };
                self.effect(
                    Phase::InitialPositions,
                    Effect::Positions {
                        first,
                        second,
                        copy_lower_to_upper: false,
                        set_upper_air: true,
                    },
                )
            }
            Phase::InitialPositions => self.read_section(),
            Phase::Section => self.effect(Phase::Air, Effect::SectionAir),
            Phase::Air => {
                if reply.value != 0 {
                    self.effect(
                        Phase::EmptyY,
                        Effect::SectionY {
                            index: self.section,
                            set_air: true,
                        },
                    )
                } else {
                    self.block_y = 15;
                    self.read_state()
                }
            }
            Phase::EmptyY => {
                let y = reply.value.wrapping_shl(4);
                let first = Pos { y, ..reply.first };
                let second = Pos {
                    y: y.wrapping_sub(1),
                    ..reply.second
                };
                self.effect(
                    Phase::EmptyPositions,
                    Effect::Positions {
                        first,
                        second,
                        copy_lower_to_upper: false,
                        set_upper_air: false,
                    },
                )
            }
            Phase::EmptyPositions => {
                self.section = self.section.wrapping_sub(1);
                self.read_section()
            }
            Phase::State => {
                self.edge = Edge::new();
                self.effect(Phase::Edge, Effect::Edge(self.edge.current_effect()))
            }
            Phase::Edge => match self.edge.advance(reply.value) {
                EdgeNext::Effect(effect) => self.effect(Phase::Edge, Effect::Edge(effect)),
                EdgeNext::Done(true) => self.commit_column(reply.first.y),
                EdgeNext::Done(false) => {
                    let first = reply.second;
                    let second = Pos {
                        y: first.y.wrapping_sub(1),
                        ..first
                    };
                    self.effect(
                        Phase::AdvancePositions,
                        Effect::Positions {
                            first,
                            second,
                            copy_lower_to_upper: true,
                            set_upper_air: false,
                        },
                    )
                }
            },
            Phase::AdvancePositions => {
                self.block_y = self.block_y.wrapping_sub(1);
                self.read_state()
            }
        }
    }
}
