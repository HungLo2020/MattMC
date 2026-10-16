//! Whole native invocation control; Java answers only the selected genuine effect.
use super::{
    fallen_tree::{self, Effect, Reply},
    random::{ActiveRandom, InvocationScope, WorldgenRandom},
    region::Callback,
};

fn header(frame: &fallen_tree::Frame, parameter: i64) -> Vec<i64> {
    let mut words = Vec::with_capacity(13);
    words.push(i64::from(frame.project));
    for position in frame.positions {
        words.extend(position.map(i64::from));
    }
    words.push(i64::from(frame.direction_ordinal()));
    words.push(parameter);
    words
}

unsafe fn invoke(
    state: *mut i64,
    callback: Option<Callback>,
    work: impl FnOnce(&mut WorldgenRandom, Callback) -> i32,
) -> i32 {
    if state.is_null() || callback.is_none() {
        return -2;
    }
    let initial = std::ptr::read(state as *const [i64; 5]);
    let scope = InvocationScope::enter(callback, initial);
    let mut random = WorldgenRandom::from_invocation(initial);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let active = ActiveRandom::enter(&mut random);
        let result = work(&mut random, scope.callback().unwrap());
        drop(active);
        result
    }))
    .unwrap_or(-2);
    std::ptr::write(state as *mut [i64; 5], scope.state());
    result
}

/// # Safety
/// `state` contains five live aligned words; callback consumes only this call's
/// bounded effect arguments and never retains their address.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fallen_tree_place(
    x: i32,
    y: i32,
    z: i32,
    state: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    invoke(state, callback, |random, callback| {
        let mut frame = fallen_tree::Frame::new([x, y, z]);
        let mut reply = Reply::scalar(0);
        loop {
            let effect = frame.next(random, reply);
            let (op, parameter) = match effect {
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
                Effect::Done => return 1,
            };
            let mut words = header(&frame, parameter);
            if let Effect::Decorate(stump) = effect {
                let positions = frame.decorator_positions(stump);
                words.push(positions.len() as i64);
                for position in positions {
                    words.extend(position.map(i64::from));
                }
            }
            let status = callback(128 + op, words.as_mut_ptr(), words.len() as i32);
            if status < 0 {
                return -1;
            }
            reply = Reply {
                value: words[11] as i32,
                mask: words[0] as u8,
                positions: std::array::from_fn(|slot| {
                    std::array::from_fn(|axis| words[1 + slot * 3 + axis] as i32)
                }),
            };
        }
    })
}

/// # Safety
/// As fallen_tree_place; provider/context/list roots remain fenced in Java.
#[no_mangle]
pub unsafe extern "C" fn mattmc_attached_logs_place(
    probability: f32,
    state: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    invoke(state, callback, |random, callback| {
        let mut frame = fallen_tree::attached::Frame::new(probability);
        let mut reply = 0;
        loop {
            use fallen_tree::attached::Effect as E;
            let (op, parameter) = match frame.next(random, reply) {
                E::SnapshotLogs => (1, 0),
                E::SelectLog(index) => (9, index as i64),
                E::DirectionSize => (2, 0),
                E::InvalidBound(bound) => (3, i64::from(bound)),
                E::DirectionAt(index) => (4, i64::from(index)),
                E::Relative(index) => (5, index as i64),
                E::IsAir => (6, 0),
                E::Provider => (7, 0),
                E::Set => (8, 0),
                E::Done => return 1,
            };
            let mut words = [parameter, 0];
            if callback(256 + op, words.as_mut_ptr(), 2) < 0 {
                return -1;
            }
            reply = words[1] as i32;
        }
    })
}

/// One selected log's shared native TrunkVine decisions; its real context owns
/// the original live forEach and selected BlockPos identity.
/// # Safety
/// As fallen_tree_place.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fallen_vines_place(
    state: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    invoke(state, callback, |random, callback| {
        let mut frame = fallen_tree::vines::Frame::new();
        let mut reply = false;
        loop {
            use fallen_tree::vines::Effect as E;
            let (op, side) = match frame.next(random, reply) {
                E::Air(side) => (1, side),
                E::Place(side) => (2, side),
                E::Done => return 1,
            };
            let mut words = [side as i64, 0];
            if callback(384 + op, words.as_mut_ptr(), 2) < 0 {
                return -1;
            }
            reply = words[1] != 0;
        }
    })
}

/// Decorator Context.isAir reads the WorldGenRegion, including its wider
/// read cache. Region.is_air is BulkSectionAccess's bounded read instead.
pub(crate) fn level_air(region: &super::region::Region<'_>, pos: [i32; 3]) -> super::region::Result<bool> {
    let state = region.level_block_state(pos[0], pos[1], pos[2])?;
    let registry = region.registry();
    if state as usize >= registry.state_count() {
        return Err(super::region::Failure::Unbound);
    }
    Ok(registry
        .flags(crate::content::block::StateId(state as u16))
        .contains(crate::content::block::StateFlags::AIR))
}

/// Real region consumer for immutable stock context resources. All Java roots
/// stay fenced by the owning invocation; unsupported resources decline before
/// any draw, write or callback. Both variants consume the shared native kernels.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fallen_decorator_generation(
    kind: i32,
    words: *mut i64,
    word_count: i32,
    out: *mut i64,
    out_count: i32,
    descriptor: *const i32,
    descriptor_count: i32,
    state: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    use super::{
        program::Reader,
        region::{Region, Shared},
        tree::Provider,
    };
    if words.is_null()
        || out.is_null()
        || descriptor.is_null()
        || state.is_null()
        || word_count < 0
        || out_count < 0
        || descriptor_count < 0
        || callback.is_none()
    {
        return -2;
    }
    let data = std::slice::from_raw_parts(descriptor, descriptor_count as usize);
    let mut reader = Reader::new(data);
    let Some(probability) = reader.float() else {
        return 0;
    };
    let provider = if kind == 0 {
        let Some(provider) = Provider::read(&mut reader) else {
            return 0;
        };
        if !provider.is_plain() {
            return 0;
        }
        Some(provider)
    } else if kind == 1 {
        None
    } else {
        return 0;
    };
    let vines = if kind == 1 {
        let Some(states) = reader.take(4) else {
            return 0;
        };
        if states.iter().any(|&state| state < 0) {
            return 0;
        }
        Some([
            states[0] as u32,
            states[1] as u32,
            states[2] as u32,
            states[3] as u32,
        ])
    } else {
        None
    };
    let Some(direction_count) = reader.count(6) else {
        return 0;
    };
    let Some(direction_words) = reader.take(direction_count) else {
        return 0;
    };
    let directions: Vec<usize> = direction_words
        .iter()
        .map(|&direction| direction as usize)
        .collect();
    if directions.iter().any(|&direction| direction >= 6) || kind == 0 && directions.is_empty() {
        return 0;
    }
    let Some(log_count) = reader.count(1 << 20) else {
        return 0;
    };
    let mut positions = Vec::with_capacity(log_count);
    for _ in 0..log_count {
        let Some(position) = reader.take(3) else {
            return 0;
        };
        positions.push([position[0], position[1], position[2]]);
    }
    if reader.next().is_some() {
        return 0;
    }
    let Some(mut region) = Region::bind(
        Shared::new(words, word_count as usize),
        Shared::new(out, out_count as usize),
    ) else {
        return 0;
    };
    if !(region.write_path_ready()
        && super::facts::installed().is_some_and(|facts| !facts.any_unknown()))
    {
        return 0;
    }
    let Some(policies) = crate::world::level::chunk::counters::installed_policies() else {
        return 0;
    };
    let mut states = Vec::new();
    if let Some(provider) = &provider {
        provider.states(&mut states);
    }
    if let Some(vines) = vines {
        states.extend(vines);
    }
    if states
        .iter()
        .any(|&state| policies.get(state as usize).is_none_or(Option::is_none))
    {
        return 0;
    }
    let initial = std::ptr::read(state as *const [i64; 5]);
    let scope = InvocationScope::enter(callback, initial);
    region.callback = scope.callback();
    let mut random = WorldgenRandom::from_invocation(initial);
    let answer = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let active = ActiveRandom::enter(&mut random);
        let result = (|| -> super::region::Result<()> {
            const SIDES: [[i32; 3]; 6] = [
                [0, -1, 0],
                [0, 1, 0],
                [0, 0, -1],
                [0, 0, 1],
                [-1, 0, 0],
                [1, 0, 0],
            ];
            if kind == 0 {
                let mut frame = fallen_tree::attached::Frame::new(probability);
                let (mut selected, mut direction, mut target, mut block) = (0, 0, [0; 3], 0);
                let mut reply = 0;
                loop {
                    use fallen_tree::attached::Effect as E;
                    let effect = frame.next(&mut random, reply);
                    reply = 0;
                    match effect {
                        E::SnapshotLogs => reply = positions.len() as i32,
                        E::SelectLog(index) => selected = index,
                        E::DirectionSize => reply = directions.len() as i32,
                        E::DirectionAt(index) => direction = directions[index as usize],
                        E::Relative(_) => {
                            target = std::array::from_fn(|axis| {
                                positions[selected][axis].wrapping_add(SIDES[direction][axis])
                            })
                        }
                        E::IsAir => reply = i32::from(level_air(&region, target)?),
                        E::Provider => {
                            block = provider.as_ref().unwrap().plain_state(&mut random).unwrap()
                        }
                        E::Set => {
                            region.set_block(target[0], target[1], target[2], block, 19)?;
                        }
                        E::InvalidBound(_) => unreachable!(),
                        E::Done => break,
                    }
                }
            } else {
                let vines = vines.unwrap();
                const HORIZONTAL: [[i32; 3]; 4] = [[-1, 0, 0], [1, 0, 0], [0, 0, -1], [0, 0, 1]];
                for log in positions {
                    let mut frame = fallen_tree::vines::Frame::new();
                    let mut reply = false;
                    loop {
                        use fallen_tree::vines::Effect as E;
                        match frame.next(&mut random, reply) {
                            E::Air(side) => {
                                let target = std::array::from_fn::<_, 3, _>(|axis| {
                                    log[axis].wrapping_add(HORIZONTAL[side][axis])
                                });
                                reply = level_air(&region, target)?;
                            }
                            E::Place(side) => {
                                let target = std::array::from_fn::<_, 3, _>(|axis| {
                                    log[axis].wrapping_add(HORIZONTAL[side][axis])
                                });
                                region.set_block(
                                    target[0],
                                    target[1],
                                    target[2],
                                    vines[side],
                                    19,
                                )?;
                            }
                            E::Done => break,
                        }
                    }
                }
            }
            Ok(())
        })();
        drop(active);
        result
    }));
    std::ptr::write(state as *mut [i64; 5], scope.state());
    super::ffi::publish_events(std::mem::take(&mut region.events));
    match answer {
        Ok(Ok(())) => 1,
        Ok(Err(failure)) => {
            region.fail_detail(&failure);
            match failure {
                super::region::Failure::UnavailableChunk { .. } => super::ffi::UNAVAILABLE_CHUNK,
                super::region::Failure::Callback => super::ffi::CALLBACK_FAILED,
                _ => super::ffi::BROKEN_BINDING,
            }
        }
        Err(_) => super::ffi::PANIC,
    }
}
