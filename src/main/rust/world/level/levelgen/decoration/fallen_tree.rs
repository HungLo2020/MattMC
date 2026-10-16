//! Complete FallenTreeFeature control, independent of the genuine effect backend.
//! Both actual region placement and VM world effects consume this same cursor.
use super::java_set::JavaHashSet;
use super::random::WorldgenRandom;

pub(crate) const STUMP: usize = 0;
pub(crate) const LOG: usize = 1;
pub(crate) const POST: usize = 2;
const HORIZONTAL: [[i32; 3]; 4] = [[0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Effect {
    BeginStump,
    Provider(usize),
    OrientLog,
    SetLog(usize),
    BeginPost(usize),
    ReadPostAir,
    MarkPost,
    Decorate(bool), // true: original stump list; false: original fallen-log list
    SampleLength,
    BeginLog(i32), // distance from the actual still-live origin
    ValidLog,
    Supported,
    Done,
}

#[derive(Clone, Copy)]
pub(crate) struct Reply {
    pub(crate) value: i32,
    /// Actual VM cursor mutations, if an effect exposes those cursor objects.
    pub(crate) mask: u8,
    pub(crate) positions: [[i32; 3]; 3],
}
impl Reply {
    pub(crate) fn scalar(value: i32) -> Self {
        Self {
            value,
            mask: 0,
            positions: [[0; 3]; 3],
        }
    }
}

#[derive(Clone, Copy)]
enum Phase {
    Begin,
    StumpProvider,
    StumpSet,
    PostBegin(usize),
    PostRead(usize),
    PostAnswer(usize),
    PostMarked(usize),
    DecorateStump,
    Direction,
    Length,
    StartLog,
    GroundValid,
    GroundValidAnswer,
    GroundSupport,
    GroundSupportAnswer,
    WholeValid,
    WholeValidAnswer,
    WholeSupport,
    WholeSupportAnswer,
    PlaceProvider,
    PlaceOrient,
    PlaceSet,
    DecorateLogs,
    Done,
}

pub(crate) struct Frame {
    phase: Phase,
    pub(crate) positions: [[i32; 3]; 3],
    pub(crate) project: u8,
    pub(crate) direction: usize,
    length: i32,
    index: i32,
    gap: i32,
    probes: i32,
    post: i32,
    stump: [i32; 3],
    logs: JavaHashSet,
}
impl Frame {
    pub(crate) fn new(origin: [i32; 3]) -> Self {
        Self {
            phase: Phase::Begin,
            positions: [origin, [0; 3], [0; 3]],
            project: 0,
            direction: 0,
            length: 0,
            index: 0,
            gap: 0,
            probes: 0,
            post: 0,
            stump: origin,
            logs: JavaHashSet::new(),
        }
    }
    fn move_by(&mut self, which: usize, vector: [i32; 3], amount: i32) {
        for (coordinate, delta) in self.positions[which].iter_mut().zip(vector) {
            *coordinate = coordinate.wrapping_add(delta.wrapping_mul(amount));
        }
        self.project |= 1 << which;
    }
    fn move_log(&mut self, amount: i32) {
        self.move_by(LOG, HORIZONTAL[self.direction], amount);
    }
    fn finished_log(&mut self, which: usize) {
        if which == STUMP {
            self.stump = self.positions[STUMP];
            self.phase = Phase::DecorateStump;
        } else {
            self.logs.insert(self.positions[LOG]);
            self.move_log(1);
            self.index = self.index.wrapping_add(1);
            self.phase = Phase::PlaceProvider;
        }
    }
    pub(crate) fn decorator_positions(&self, stump: bool) -> Vec<[i32; 3]> {
        if stump {
            vec![self.stump]
        } else {
            self.logs.to_vec()
        }
    }
    pub(crate) fn direction_ordinal(&self) -> i32 {
        [2, 5, 3, 4][self.direction]
    }
    /// Called after each effect, with no native borrow retained by the caller
    /// during the effect itself. SetBlock's boolean is deliberately ignored.
    pub(crate) fn next(&mut self, random: &mut WorldgenRandom, reply: Reply) -> Effect {
        for which in 0..3 {
            if reply.mask & (1 << which) != 0 {
                self.positions[which] = reply.positions[which];
            }
        }
        self.project = 0;
        loop {
            match self.phase {
                Phase::Begin => {
                    self.phase = Phase::StumpProvider;
                    return Effect::BeginStump;
                }
                Phase::StumpProvider => {
                    self.phase = Phase::StumpSet;
                    return Effect::Provider(STUMP);
                }
                Phase::StumpSet => {
                    self.phase = Phase::PostBegin(STUMP);
                    return Effect::SetLog(STUMP);
                }
                Phase::PostBegin(which) => {
                    self.positions[POST] = self.positions[which];
                    self.post = 0;
                    self.phase = Phase::PostRead(which);
                    return Effect::BeginPost(which);
                }
                Phase::PostRead(which) => {
                    self.move_by(POST, [0, 1, 0], 1);
                    self.phase = Phase::PostAnswer(which);
                    return Effect::ReadPostAir;
                }
                Phase::PostAnswer(which) => {
                    if reply.value != 0 {
                        self.finished_log(which);
                    } else {
                        self.phase = Phase::PostMarked(which);
                        return Effect::MarkPost;
                    }
                }
                Phase::PostMarked(which) => {
                    self.post += 1;
                    if self.post == 2 {
                        self.finished_log(which);
                    } else {
                        self.phase = Phase::PostRead(which);
                    }
                }
                Phase::DecorateStump => {
                    self.phase = Phase::Direction;
                    return Effect::Decorate(true);
                }
                Phase::Direction => {
                    self.direction = random.next_int(4) as usize;
                    self.phase = Phase::Length;
                    return Effect::SampleLength;
                }
                Phase::Length => {
                    self.length = reply.value.wrapping_sub(2);
                    let distance = 2 + random.next_int(2);
                    self.phase = Phase::StartLog;
                    return Effect::BeginLog(distance);
                }
                Phase::StartLog => {
                    self.move_by(LOG, [0, 1, 0], 1);
                    self.probes = 0;
                    self.phase = Phase::GroundValid;
                }
                Phase::GroundValid => {
                    self.phase = Phase::GroundValidAnswer;
                    return Effect::ValidLog;
                }
                Phase::GroundValidAnswer => {
                    if reply.value != 0 {
                        self.phase = Phase::GroundSupport;
                    } else {
                        self.failed_ground_probe();
                    }
                }
                Phase::GroundSupport => {
                    self.phase = Phase::GroundSupportAnswer;
                    return Effect::Supported;
                }
                Phase::GroundSupportAnswer => {
                    if reply.value != 0 {
                        self.begin_whole_log();
                    } else {
                        self.failed_ground_probe();
                    }
                }
                Phase::WholeValid => {
                    if self.index >= self.length {
                        self.move_log(self.length.wrapping_neg());
                        self.index = 0;
                        self.phase = Phase::PlaceProvider;
                    } else {
                        self.phase = Phase::WholeValidAnswer;
                        return Effect::ValidLog;
                    }
                }
                Phase::WholeValidAnswer => {
                    if reply.value == 0 {
                        self.phase = Phase::Done;
                    } else {
                        self.phase = Phase::WholeSupport;
                    }
                }
                Phase::WholeSupport => {
                    self.phase = Phase::WholeSupportAnswer;
                    return Effect::Supported;
                }
                Phase::WholeSupportAnswer => {
                    if reply.value != 0 {
                        self.gap = 0;
                    } else {
                        self.gap += 1;
                    }
                    if self.gap > 2 {
                        self.phase = Phase::Done;
                    } else {
                        self.move_log(1);
                        self.index = self.index.wrapping_add(1);
                        self.phase = Phase::WholeValid;
                    }
                }
                Phase::PlaceProvider => {
                    if self.index >= self.length {
                        self.phase = Phase::DecorateLogs;
                    } else {
                        self.phase = Phase::PlaceOrient;
                        return Effect::Provider(LOG);
                    }
                }
                Phase::PlaceOrient => {
                    self.phase = Phase::PlaceSet;
                    return Effect::OrientLog;
                }
                Phase::PlaceSet => {
                    self.phase = Phase::PostBegin(LOG);
                    return Effect::SetLog(LOG);
                }
                Phase::DecorateLogs => {
                    self.phase = Phase::Done;
                    return Effect::Decorate(false);
                }
                Phase::Done => return Effect::Done,
            }
        }
    }
    fn begin_whole_log(&mut self) {
        self.index = 0;
        self.gap = 0;
        self.phase = Phase::WholeValid;
    }
    fn failed_ground_probe(&mut self) {
        self.move_by(LOG, [0, -1, 0], 1);
        self.probes += 1;
        if self.probes == 6 {
            self.begin_whole_log();
        } else {
            self.phase = Phase::GroundValid;
        }
    }
}

/// Complete attached-log shuffle and per-entry decision cursor.
pub(crate) mod attached {
    //! AttachedToLogsDecorator progression. The copied VM references remain real
    //! resources: native shuffles their indices, never snapshots mutable positions.
    use super::super::random::WorldgenRandom;

    #[derive(Clone, Copy, Debug, PartialEq)]
    pub(crate) enum Effect {
        SnapshotLogs,
        SelectLog(usize),
        DirectionSize,
        InvalidBound(i32),
        DirectionAt(i32),
        Relative(usize),
        IsAir,
        Provider,
        Set,
        Done,
    }
    #[derive(Clone, Copy)]
    enum Phase {
        Begin,
        Snapshot,
        Next,
        Selected,
        Size,
        Direction,
        Relative,
        Air,
        Provider,
        Set,
        Advance,
        Done,
    }
    pub(crate) struct Frame {
        phase: Phase,
        probability: f32,
        order: Vec<usize>,
        index: usize,
    }
    impl Frame {
        pub(crate) fn new(probability: f32) -> Self {
            Self {
                phase: Phase::Begin,
                probability,
                order: Vec::new(),
                index: 0,
            }
        }
        pub(crate) fn next(&mut self, random: &mut WorldgenRandom, reply: i32) -> Effect {
            loop {
                match self.phase {
                    Phase::Begin => {
                        self.phase = Phase::Snapshot;
                        return Effect::SnapshotLogs;
                    }
                    Phase::Snapshot => {
                        self.order = (0..reply.max(0) as usize).collect();
                        for j in (2..=self.order.len()).rev() {
                            let k = random.next_int(j as i32) as usize;
                            self.order.swap(j - 1, k);
                        }
                        self.phase = Phase::Next;
                    }
                    Phase::Next => {
                        if self.index == self.order.len() {
                            self.phase = Phase::Done;
                        } else {
                            self.phase = Phase::Selected;
                            return Effect::SelectLog(self.order[self.index]);
                        }
                    }
                    Phase::Selected => {
                        self.phase = Phase::Size;
                        return Effect::DirectionSize;
                    }
                    Phase::Size => {
                        // Invalid direct-constructor values use the actual VM random
                        // method's original error, with no invented codec admission.
                        if reply <= 0 {
                            return Effect::InvalidBound(reply);
                        }
                        let selected = random.next_int(reply);
                        self.phase = Phase::Direction;
                        return Effect::DirectionAt(selected);
                    }
                    Phase::Direction => {
                        self.phase = Phase::Relative;
                        return Effect::Relative(self.order[self.index]);
                    }
                    Phase::Relative => {
                        if random.next_float() <= self.probability {
                            self.phase = Phase::Air;
                            return Effect::IsAir;
                        }
                        self.phase = Phase::Advance;
                    }
                    Phase::Air => {
                        if reply != 0 {
                            self.phase = Phase::Provider;
                            return Effect::Provider;
                        }
                        self.phase = Phase::Advance;
                    }
                    Phase::Provider => {
                        self.phase = Phase::Set;
                        return Effect::Set;
                    }
                    Phase::Set | Phase::Advance => {
                        self.index += 1;
                        self.phase = Phase::Next;
                    }
                    Phase::Done => return Effect::Done,
                }
            }
        }
    }
}

/// Shared TrunkVineDecorator decision kernel. The context's original forEach
/// remains a genuine live VM operation when that context is externally visible.
pub(crate) mod vines {
    use super::super::random::WorldgenRandom;
    #[derive(Clone, Copy, Debug, PartialEq)]
    pub(crate) enum Effect {
        Air(usize),
        Place(usize),
        Done,
    }
    pub(crate) struct Frame {
        side: usize,
        phase: u8,
    }
    impl Frame {
        pub(crate) fn new() -> Self {
            Self { side: 0, phase: 0 }
        }
        pub(crate) fn next(&mut self, random: &mut WorldgenRandom, reply: bool) -> Effect {
            loop {
                if self.side == 4 {
                    return Effect::Done;
                }
                match self.phase {
                    0 => {
                        if random.next_int(3) > 0 {
                            self.phase = 1;
                            return Effect::Air(self.side);
                        }
                        self.side += 1;
                    }
                    1 => {
                        if reply {
                            self.phase = 2;
                            return Effect::Place(self.side);
                        }
                        self.side += 1;
                        self.phase = 0;
                    }
                    _ => {
                        self.side += 1;
                        self.phase = 0;
                    }
                }
            }
        }
    }
}

/// Immutable native provider/length program. The root index retains the actual
/// configuration only for its live decorator lists and genuine VM effects.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Config {
    provider: super::tree::Provider,
    length: super::program::IntProvider,
    valid: usize,
    sturdy: usize,
    root: usize,
    axes: Vec<(u32, [u32; 2])>,
}
impl Config {
    pub(crate) fn read(reader: &mut super::program::Reader) -> Option<Self> {
        let provider = super::tree::Provider::read(reader)?;
        let length = super::program::IntProvider::read(reader)?;
        let (valid, sturdy, root) = (reader.index()?, reader.index()?, reader.count(1 << 16)?);
        let count = reader.count(256)?;
        let axes = (0..count)
            .map(|_| Some((reader.state()?, [reader.state()?, reader.state()?])))
            .collect::<Option<_>>()?;
        let axes: Vec<(u32, [u32; 2])> = axes;
        let mut states = Vec::new();
        provider.states(&mut states);
        if states
            .iter()
            .any(|state| !axes.iter().any(|(base, _)| base == state))
            || axes
                .iter()
                .enumerate()
                .any(|(index, (base, _))| axes[..index].iter().any(|(other, _)| base == other))
        {
            return None;
        }
        Some(Self {
            provider,
            length,
            valid,
            sturdy,
            root,
            axes,
        })
    }
    pub(crate) fn states(&self, out: &mut Vec<u32>) {
        self.provider.states(out);
        for (_, states) in &self.axes {
            out.extend(states);
        }
    }
    pub(crate) fn sets(&self, out: &mut Vec<usize>) {
        out.extend([self.valid, self.sturdy]);
    }
}

/// Consume the same full feature cursor directly against the existing native
/// region. A dynamic world effect switches this invocation's remaining effects
/// to genuine VM resources; its control and random progression remain native.
pub(crate) fn place(
    placement: &mut super::placement::Placement,
    config: &Config,
    origin: [i32; 3],
) -> super::region::Result<bool> {
    let mut frame = Frame::new(origin);
    let mut reply = Reply::scalar(0);
    let mut state = 0;
    let mut vm_world = false;
    let mut resources = false;
    loop {
        let effect = frame.next(&mut placement.random, reply);
        reply = Reply::scalar(0);
        if effect == Effect::Done {
            if resources {
                generation_effect(placement, config, &frame, Effect::Done, state)?;
            }
            return Ok(true);
        }
        // The native write binding is 3x3; WorldGenRegion may serve a wider
        // read cache. Preserve actual VM cursors and all subsequent effects
        // once the frame reaches outside that binding, just as for dynamic
        // support. A refused far write still has reads/postprocessing after it.
        let exposed = match effect {
            Effect::ValidLog | Effect::Supported => Some(LOG),
            Effect::ReadPostAir | Effect::MarkPost => Some(POST),
            Effect::SetLog(which) => Some(which),
            _ => None,
        };
        if let Some(which) = exposed {
            let pos = frame.positions[which];
            if !placement.region.binds_chunk(pos[0] >> 4, pos[2] >> 4) {
                vm_world = true;
            }
        }
        if effect == Effect::Supported && !vm_world {
            let pos = frame.positions[LOG];
            let below =
                placement
                    .region
                    .level_block_state(pos[0], pos[1].wrapping_sub(1), pos[2])?;
            if super::facts::installed().is_none_or(|facts| facts.dynamic_shape(below)) {
                vm_world = true;
            } else {
                reply.value = i32::from(placement.program.in_set(config.sturdy, below));
                continue;
            }
        }
        if vm_world || matches!(effect, Effect::Decorate(_)) {
            reply = generation_effect(placement, config, &frame, effect, state)?;
            resources = true;
            continue;
        }
        match effect {
            Effect::BeginStump => {}
            Effect::Provider(_) => state = config.provider.state(placement),
            Effect::OrientLog => {
                if let Some((_, axes)) = config.axes.iter().find(|(base, _)| *base == state) {
                    state = axes[usize::from(frame.direction % 2 == 0)];
                }
            }
            Effect::SetLog(which) => {
                let pos = frame.positions[which];
                placement
                    .region
                    .set_block(pos[0], pos[1], pos[2], state, 3)?;
            }
            Effect::BeginPost(_) => {}
            Effect::ReadPostAir => {
                let pos = frame.positions[POST];
                reply.value = i32::from(placement.region.is_air(pos[0], pos[1], pos[2])?);
            }
            Effect::MarkPost => {
                let pos = frame.positions[POST];
                // Original getChunk must happen even for an out-of-height mark.
                placement.region.chunk_index(pos[0] >> 4, pos[2] >> 4)?;
                placement
                    .region
                    .event(super::write::EVENT_POST_PROCESS, pos[0], pos[1], pos[2], 0);
            }
            Effect::SampleLength => reply.value = config.length.sample(placement),
            Effect::BeginLog(distance) => {
                for axis in 0..3 {
                    frame.positions[LOG][axis] = origin[axis]
                        .wrapping_add(HORIZONTAL[frame.direction][axis].wrapping_mul(distance));
                }
            }
            Effect::ValidLog => {
                let pos = frame.positions[LOG];
                let at = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
                reply.value = i32::from(placement.program.in_set(config.valid, at));
            }
            Effect::Supported | Effect::Done | Effect::Decorate(_) => unreachable!(),
        }
    }
}

fn effect_operation(effect: Effect) -> (i32, i64) {
    match effect {
        Effect::BeginStump => (1, 0),
        Effect::Provider(which) => (2, which as i64),
        Effect::OrientLog => (3, 0),
        Effect::SetLog(which) => (4, which as i64),
        Effect::BeginPost(which) => (5, which as i64),
        Effect::ReadPostAir => (6, 0),
        Effect::MarkPost => (7, 0),
        Effect::Decorate(stump) => (8, i64::from(stump)),
        Effect::SampleLength => (9, 0),
        Effect::BeginLog(distance) => (10, i64::from(distance)),
        Effect::ValidLog => (11, 0),
        Effect::Supported => (12, 0),
        Effect::Done => (0, 0),
    }
}
fn generation_effect(
    placement: &mut super::placement::Placement,
    config: &Config,
    frame: &Frame,
    effect: Effect,
    state: u32,
) -> super::region::Result<Reply> {
    let (operation, parameter) = effect_operation(effect);
    let mut words = vec![
        i64::from(operation),
        config.root as i64,
        placement.region.slot,
        i64::from(state),
        7,
    ];
    for pos in frame.positions {
        words.extend(pos.map(i64::from));
    }
    words.extend([i64::from(frame.direction_ordinal()), parameter]);
    if operation == 8 {
        let positions = frame.decorator_positions(parameter != 0);
        words.push(positions.len() as i64);
        for pos in positions {
            words.extend(pos.map(i64::from));
        }
    }
    placement.region.sync_call(24, &mut words)?;
    Ok(Reply {
        value: words[15] as i32,
        mask: words[4] as u8,
        positions: std::array::from_fn(|slot| {
            std::array::from_fn(|axis| words[5 + slot * 3 + axis] as i32)
        }),
    })
}
