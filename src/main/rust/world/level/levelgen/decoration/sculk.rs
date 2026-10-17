//! `SculkPatchFeature` with the world-generation `SculkSpreader`: charge
//! cursors, the sculk, sculk vein and default `SculkBehaviour`s, and the vein's
//! `MultifaceSpreader` configs. Sounds, level events and entity pushes are
//! no-ops in a `WorldGenRegion` and are left out.
use super::placement::Placement;
use super::program::{IntProvider, Predicate, Reader};
use super::region::Result;
use super::vegetation::DIRECTIONS;
use super::write::EVENT_POST_PROCESS;
use crate::content::block::{StateFlags, StateId};

/// `SculkSpreader.createWorldGenSpreader()`.
const GROWTH_SPAWN_COST: i32 = 50;
const NO_GROWTH_RADIUS: i32 = 1;
const CHARGE_DECAY_RATE: i32 = 5;
const ADDITIONAL_DECAY_RATE: i32 = 10;
const UP: usize = 1;

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Sculk {
    spread_rounds: i32,
    growth_rounds: i32,
    charge_count: i32,
    amount_per_charge: i32,
    spread_attempts: i32,
    extra_rare_growths: IntProvider,
    catalyst_chance: f32,
    /// Sculk vein states (contiguous): face bits, with/without each face, waterlogged.
    vein_first: u32,
    vein_faces: Vec<u8>,
    vein_with: Vec<[u32; 6]>,
    vein_without: Vec<[u32; 6]>,
    vein_waterlogged: Vec<u32>,
    vein_default: u32,
    vein_default_waterlogged: u32,
    sculk: u32,
    catalyst: u32,
    shrieker: u32,
    shrieker_waterlogged: u32,
    sensor: u32,
    sensor_waterlogged: u32,
    air: u32,
    water: u32,
    sets: Sets,
    /// `isFaceSturdy(direction)` at the position, by direction.
    sturdy: Vec<Predicate>,
    /// `MultifaceBlock.canAttachTo(level, direction, pos, state)` at the position.
    attach: Vec<Predicate>,
    /// `isCollisionShapeFullBlock` at the position.
    collision_full: Predicate,
}

#[derive(Clone, Debug, PartialEq)]
struct Sets {
    sculk_block: usize,
    catalyst_block: usize,
    moving_piston: usize,
    sensor_block: usize,
    shrieker_block: usize,
    water_block: usize,
    fire: usize,
    /// `sculk_replaceable_world_gen`.
    replaceable_world_gen: usize,
    /// `sculk_replaceable`.
    replaceable: usize,
    fluid_empty: usize,
    /// `getFluidState().is(Fluids.WATER)`.
    fluid_water: usize,
    fluid_source: usize,
    /// `isSourceOfType(Fluids.WATER)`.
    water_source: usize,
    can_be_replaced: usize,
}

#[derive(Clone, Copy, PartialEq)]
enum Behaviour {
    Sculk,
    Vein,
    Default,
}

struct Cursor {
    pos: [i32; 3],
    charge: i32,
    update_delay: i32,
    decay_delay: i32,
    /// `facings`: None until set, then a face mask (empty for non-multiface blocks).
    facings: Option<u8>,
}

/// `MultifaceSpreader.SpreadType`s in `DEFAULT_SPREAD_ORDER`.
#[derive(Clone, Copy)]
enum SpreadType {
    SamePosition,
    SamePlane,
    WrapAround,
}

const VEIN_SPREAD: [SpreadType; 3] = [SpreadType::SamePosition, SpreadType::SamePlane, SpreadType::WrapAround];
const SAME_SPACE_SPREAD: [SpreadType; 1] = [SpreadType::SamePosition];

fn add(a: [i32; 3], b: [i32; 3]) -> [i32; 3] {
    [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
}

fn dist_sqr(a: [i32; 3], b: [i32; 3]) -> f64 {
    let (dx, dy, dz) = ((a[0] - b[0]) as f64, (a[1] - b[1]) as f64, (a[2] - b[2]) as f64);
    dx * dx + dy * dy + dz * dz
}

fn dist_manhattan(a: [i32; 3], b: [i32; 3]) -> i32 {
    ((b[0] - a[0]).abs() as f32 + (b[1] - a[1]).abs() as f32 + (b[2] - a[2]).abs() as f32) as i32
}

fn shuffle<T>(placement: &mut Placement, items: &mut [T]) {
    for j in (2..=items.len()).rev() {
        let k = placement.random.next_int(j as i32) as usize;
        items.swap(j - 1, k);
    }
}

impl Sculk {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let spread_rounds = reader.next()?;
        let growth_rounds = reader.next()?;
        let charge_count = reader.next()?;
        let amount_per_charge = reader.next()?;
        let spread_attempts = reader.next()?;
        let extra_rare_growths = IntProvider::read(reader)?;
        let catalyst_chance = reader.float()?;
        let vein_first = reader.state()?;
        let count = reader.count(1 << 12).filter(|&n| n > 0)?;
        let (mut vein_faces, mut vein_with, mut vein_without, mut vein_waterlogged) = (vec![], vec![], vec![], vec![]);
        for _ in 0..count {
            vein_faces.push(u8::try_from(reader.next()?).ok().filter(|&f| f < 64)?);
            let mut with = [0; 6];
            let mut without = [0; 6];
            for d in 0..6 {
                with[d] = reader.write_state()?;
                without[d] = reader.write_state()?;
            }
            vein_with.push(with);
            vein_without.push(without);
            vein_waterlogged.push(reader.write_state()?);
        }
        let in_vein = |state: u32| state.checked_sub(vein_first).is_some_and(|i| (i as usize) < count);
        let vein_default = reader.write_state().filter(|&s| in_vein(s))?;
        let vein_default_waterlogged = reader.write_state().filter(|&s| in_vein(s))?;
        let mut states = [0u32; 8];
        for state in states.iter_mut() {
            *state = reader.write_state()?;
        }
        let [sculk, catalyst, shrieker, shrieker_waterlogged, sensor, sensor_waterlogged, air, water] = states;
        let sets = Sets {
            sculk_block: reader.set()?,
            catalyst_block: reader.set()?,
            moving_piston: reader.set()?,
            sensor_block: reader.set()?,
            shrieker_block: reader.set()?,
            water_block: reader.set()?,
            fire: reader.set()?,
            replaceable_world_gen: reader.set()?,
            replaceable: reader.set()?,
            fluid_empty: reader.set()?,
            fluid_water: reader.set()?,
            fluid_source: reader.set()?,
            water_source: reader.set()?,
            can_be_replaced: reader.set()?,
        };
        let sturdy = (0..6).map(|_| Predicate::read(reader, 0)).collect::<Option<Vec<_>>>()?;
        let attach = (0..6).map(|_| Predicate::read(reader, 0)).collect::<Option<Vec<_>>>()?;
        let collision_full = Predicate::read(reader, 0)?;
        Some(Self {
            spread_rounds, growth_rounds, charge_count, amount_per_charge, spread_attempts, extra_rare_growths, catalyst_chance,
            vein_first, vein_faces, vein_with, vein_without, vein_waterlogged, vein_default, vein_default_waterlogged,
            sculk, catalyst, shrieker, shrieker_waterlogged, sensor, sensor_waterlogged, air, water, sets, sturdy, attach, collision_full,
        })
    }

    fn state(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
        placement.region.level_block_state(pos[0], pos[1], pos[2])
    }

    fn is(placement: &Placement, set: usize, state: u32) -> bool {
        placement.program.in_set(set, state)
    }

    fn air(placement: &Placement, state: u32) -> bool {
        placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR)
    }

    fn vein_index(&self, state: u32) -> Option<usize> {
        state.checked_sub(self.vein_first).map(|i| i as usize).filter(|&i| i < self.vein_faces.len())
    }

    fn has_face(&self, state: u32, direction: usize) -> bool {
        self.vein_index(state).is_some_and(|i| self.vein_faces[i] & (1 << direction) != 0)
    }

    fn behaviour(&self, placement: &Placement, state: u32) -> Behaviour {
        if self.vein_index(state).is_some() {
            Behaviour::Vein
        } else if Self::is(placement, self.sets.sculk_block, state) {
            Behaviour::Sculk
        } else {
            Behaviour::Default
        }
    }

    fn set_block(placement: &mut Placement, pos: [i32; 3], state: u32, flags: i32) -> Result<bool> {
        placement.region.set_block(pos[0], pos[1], pos[2], state, flags)
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !self.can_spread_from(placement, origin)? {
            return Ok(false);
        }
        let mut cursors: Vec<Cursor> = Vec::new();
        for round in 0..self.spread_rounds + self.growth_rounds {
            for _ in 0..self.charge_count {
                let mut amount = self.amount_per_charge;
                while amount > 0 {
                    let charge = amount.min(1000);
                    if cursors.len() < 32 {
                        cursors.push(Cursor { pos: origin, charge, update_delay: 0, decay_delay: 1, facings: None });
                    }
                    amount -= charge;
                }
            }
            let spreading = round < self.spread_rounds;
            for _ in 0..self.spread_attempts {
                if cursors.is_empty() {
                    continue;
                }
                let mut kept = Vec::with_capacity(cursors.len());
                for mut cursor in std::mem::take(&mut cursors) {
                    // `isPosUnreasonable`: chessboard distance over 1024 drops the cursor.
                    let chessboard = (cursor.pos[0] - origin[0]).abs().max((cursor.pos[1] - origin[1]).abs()).max((cursor.pos[2] - origin[2]).abs());
                    if chessboard > 1024 {
                        continue;
                    }
                    self.update(placement, &mut cursor, origin, spreading)?;
                    // World generation never merges cursors sharing a position.
                    if cursor.charge > 0 {
                        kept.push(cursor);
                    }
                }
                cursors = kept;
            }
            cursors.clear();
        }
        let below = [origin[0], origin[1] - 1, origin[2]];
        if placement.random.next_float() <= self.catalyst_chance && placement.test(&self.collision_full, below)? {
            Self::set_block(placement, origin, self.catalyst, 3)?;
        }
        let extra = self.extra_rare_growths.sample(placement);
        for _ in 0..extra {
            let dx = placement.random.next_int(5) - 2;
            let dz = placement.random.next_int(5) - 2;
            let at = [origin[0] + dx, origin[1], origin[2] + dz];
            if Self::air(placement, Self::state(placement, at)?) && placement.test(&self.sturdy[UP], [at[0], at[1] - 1, at[2]])? {
                Self::set_block(placement, at, self.shrieker, 3)?;
            }
        }
        Ok(true)
    }

    fn can_spread_from(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<bool> {
        let state = Self::state(placement, pos)?;
        if self.behaviour(placement, state) != Behaviour::Default {
            return Ok(true);
        }
        if !Self::air(placement, state) && !(Self::is(placement, self.sets.water_block, state) && Self::is(placement, self.sets.fluid_source, state)) {
            return Ok(false);
        }
        for by in DIRECTIONS {
            if placement.test(&self.collision_full, add(pos, by))? {
                return Ok(true);
            }
        }
        Ok(false)
    }

    /// `ChargeCursor.update` (world generation always updates).
    fn update(&self, placement: &mut Placement, cursor: &mut Cursor, origin: [i32; 3], spreading: bool) -> Result<()> {
        if cursor.charge <= 0 {
            return Ok(());
        }
        if cursor.update_delay > 0 {
            cursor.update_delay -= 1;
            return Ok(());
        }
        let mut state = Self::state(placement, cursor.pos)?;
        let mut behaviour = self.behaviour(placement, state);
        if spreading && self.attempt_spread_vein(placement, behaviour, cursor.pos, state, cursor.facings)? && behaviour != Behaviour::Sculk {
            state = Self::state(placement, cursor.pos)?;
            behaviour = self.behaviour(placement, state);
        }
        cursor.charge = self.attempt_use_charge(placement, behaviour, cursor, origin, spreading)?;
        if cursor.charge <= 0 {
            self.on_discharged(placement, behaviour, state, cursor.pos)?;
            return Ok(());
        }
        if let Some(next) = self.valid_movement_pos(placement, cursor.pos)? {
            self.on_discharged(placement, behaviour, state, cursor.pos)?;
            cursor.pos = next;
            // `closerThan(new Vec3i(origin.x, pos.y, origin.z), 15.0)`.
            if dist_sqr(next, [origin[0], next[1], origin[2]]) >= 225.0 {
                cursor.charge = 0;
                return Ok(());
            }
            state = Self::state(placement, next)?;
        }
        let at = self.behaviour(placement, state);
        if at != Behaviour::Default {
            cursor.facings = Some(self.vein_index(state).map_or(0, |i| self.vein_faces[i]));
        }
        cursor.decay_delay = match behaviour {
            Behaviour::Default => (cursor.decay_delay - 1).max(0),
            _ => 1,
        };
        cursor.update_delay = 1;
        Ok(())
    }

    fn attempt_spread_vein(&self, placement: &mut Placement, behaviour: Behaviour, pos: [i32; 3], state: u32, facings: Option<u8>) -> Result<bool> {
        if behaviour == Behaviour::Default {
            match facings {
                None => {
                    let here = Self::state(placement, pos)?;
                    return Ok(self.spread_all(placement, &SAME_SPACE_SPREAD, here, pos)? > 0);
                }
                Some(faces) if faces != 0 => {
                    if !Self::air(placement, state) && !Self::is(placement, self.sets.fluid_water, state) {
                        return Ok(false);
                    }
                    return self.regrow(placement, pos, state, faces);
                }
                Some(_) => {}
            }
        }
        Ok(self.spread_all(placement, &VEIN_SPREAD, state, pos)? > 0)
    }

    /// `SculkVeinBlock.regrow`.
    fn regrow(&self, placement: &mut Placement, pos: [i32; 3], state: u32, faces: u8) -> Result<bool> {
        let mut vein = self.vein_default;
        let mut any = false;
        for direction in 0..6 {
            if faces & (1 << direction) == 0 {
                continue;
            }
            if placement.test(&self.attach[direction], add(pos, DIRECTIONS[direction]))? {
                vein = self.vein_with[self.vein_index(vein).expect("vein state")][direction];
                any = true;
            }
        }
        if !any {
            return Ok(false);
        }
        if !Self::is(placement, self.sets.fluid_empty, state) {
            vein = self.vein_waterlogged[self.vein_index(vein).expect("vein state")];
        }
        Self::set_block(placement, pos, vein, 3)?;
        Ok(true)
    }

    fn attempt_use_charge(&self, placement: &mut Placement, behaviour: Behaviour, cursor: &Cursor, origin: [i32; 3], spreading: bool) -> Result<i32> {
        let charge = cursor.charge;
        match behaviour {
            Behaviour::Default => Ok(if cursor.decay_delay > 0 { charge } else { 0 }),
            Behaviour::Vein => {
                if spreading && self.attempt_place_sculk(placement, cursor.pos)? {
                    return Ok(charge - 1);
                }
                Ok(if placement.random.next_int(CHARGE_DECAY_RATE) == 0 { (charge as f32 * 0.5f32).floor() as i32 } else { charge })
            }
            Behaviour::Sculk => {
                if charge == 0 || placement.random.next_int(CHARGE_DECAY_RATE) != 0 {
                    return Ok(charge);
                }
                let pos = cursor.pos;
                let near = dist_sqr(pos, origin) < (NO_GROWTH_RADIUS as f64) * (NO_GROWTH_RADIUS as f64);
                if !near && self.can_place_growth(placement, pos)? {
                    if placement.random.next_int(GROWTH_SPAWN_COST) < charge {
                        let above = [pos[0], pos[1] + 1, pos[2]];
                        let shrieker = placement.random.next_int(11) == 0;
                        let wet = !Self::is(placement, self.sets.fluid_empty, Self::state(placement, above)?);
                        let growth = match (shrieker, wet) {
                            (true, false) => self.shrieker,
                            (true, true) => self.shrieker_waterlogged,
                            (false, false) => self.sensor,
                            (false, true) => self.sensor_waterlogged,
                        };
                        Self::set_block(placement, above, growth, 3)?;
                    }
                    return Ok((charge - GROWTH_SPAWN_COST).max(0));
                }
                if placement.random.next_int(ADDITIONAL_DECAY_RATE) != 0 {
                    return Ok(charge);
                }
                Ok(charge - if near { 1 } else { decay_penalty(pos, origin, charge) })
            }
        }
    }

    fn can_place_growth(&self, placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let above = Self::state(placement, [pos[0], pos[1] + 1, pos[2]])?;
        if !(Self::air(placement, above) || Self::is(placement, self.sets.water_block, above) && Self::is(placement, self.sets.fluid_water, above)) {
            return Ok(false);
        }
        let mut count = 0;
        for z in pos[2] - 4..=pos[2] + 4 {
            for y in pos[1]..=pos[1] + 2 {
                for x in pos[0] - 4..=pos[0] + 4 {
                    let state = Self::state(placement, [x, y, z])?;
                    if Self::is(placement, self.sets.sensor_block, state) || Self::is(placement, self.sets.shrieker_block, state) {
                        count += 1;
                    }
                    if count > 2 {
                        return Ok(false);
                    }
                }
            }
        }
        Ok(true)
    }

    /// `SculkVeinBlock.attemptPlaceSculk`.
    fn attempt_place_sculk(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<bool> {
        let state = Self::state(placement, pos)?;
        let mut directions = [0usize, 1, 2, 3, 4, 5];
        shuffle(placement, &mut directions);
        for direction in directions {
            if !self.has_face(state, direction) {
                continue;
            }
            let target = add(pos, DIRECTIONS[direction]);
            if !Self::is(placement, self.sets.replaceable_world_gen, Self::state(placement, target)?) {
                continue;
            }
            Self::set_block(placement, target, self.sculk, 3)?;
            self.spread_all(placement, &VEIN_SPREAD, self.sculk, target)?;
            let back = direction ^ 1;
            for (d, by) in DIRECTIONS.iter().enumerate() {
                if d == back {
                    continue;
                }
                let next = add(target, *by);
                let there = Self::state(placement, next)?;
                if self.vein_index(there).is_some() {
                    self.on_discharged(placement, Behaviour::Vein, there, next)?;
                }
            }
            return Ok(true);
        }
        Ok(false)
    }

    /// `SculkVeinBlock.onDischarged`; the other behaviours do nothing.
    fn on_discharged(&self, placement: &mut Placement, behaviour: Behaviour, state: u32, pos: [i32; 3]) -> Result<()> {
        if behaviour != Behaviour::Vein || self.vein_index(state).is_none() {
            return Ok(());
        }
        let mut state = state;
        for direction in 0..6 {
            if self.has_face(state, direction) && Self::is(placement, self.sets.sculk_block, Self::state(placement, add(pos, DIRECTIONS[direction]))?) {
                state = self.vein_without[self.vein_index(state).expect("vein state")][direction];
            }
        }
        if self.vein_index(state).is_some_and(|i| self.vein_faces[i] == 0) {
            let here = Self::state(placement, pos)?;
            state = if Self::is(placement, self.sets.fluid_empty, here) { self.air } else { self.water };
        }
        Self::set_block(placement, pos, state, 3)?;
        Ok(())
    }

    fn valid_movement_pos(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<Option<[i32; 3]>> {
        // `NON_CORNER_NEIGHBOURS`: betweenClosed(-1..1) order without corners or the centre.
        let mut offsets = Vec::with_capacity(18);
        for z in -1..=1 {
            for y in -1..=1 {
                for x in -1..=1 {
                    if (x == 0 || y == 0 || z == 0) && [x, y, z] != [0, 0, 0] {
                        offsets.push([x, y, z]);
                    }
                }
            }
        }
        shuffle(placement, &mut offsets);
        let mut chosen = pos;
        for by in offsets {
            let next = add(pos, by);
            let state = Self::state(placement, next)?;
            if self.behaviour(placement, state) != Behaviour::Default && self.movement_unobstructed(placement, pos, next)? {
                chosen = next;
                if self.has_substrate_access(placement, state, next)? {
                    break;
                }
            }
        }
        Ok((chosen != pos).then_some(chosen))
    }

    fn movement_unobstructed(&self, placement: &mut Placement, from: [i32; 3], to: [i32; 3]) -> Result<bool> {
        if dist_manhattan(from, to) == 1 {
            return Ok(true);
        }
        let d = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
        // `Direction.fromAxisAndDirection`: negative when below zero, else positive.
        let x = if d[0] < 0 { 4 } else { 5 };
        let y = if d[1] < 0 { 0 } else { 1 };
        let z = if d[2] < 0 { 2 } else { 3 };
        let (a, b) = if d[0] == 0 { (y, z) } else if d[1] == 0 { (x, z) } else { (x, y) };
        Ok(self.unobstructed(placement, from, a)? || self.unobstructed(placement, from, b)?)
    }

    fn unobstructed(&self, placement: &mut Placement, from: [i32; 3], direction: usize) -> Result<bool> {
        Ok(!placement.test(&self.sturdy[direction ^ 1], add(from, DIRECTIONS[direction]))?)
    }

    fn has_substrate_access(&self, placement: &Placement, state: u32, pos: [i32; 3]) -> Result<bool> {
        if self.vein_index(state).is_none() {
            return Ok(false);
        }
        for direction in 0..6 {
            if self.has_face(state, direction) && Self::is(placement, self.sets.replaceable, Self::state(placement, add(pos, DIRECTIONS[direction]))?) {
                return Ok(true);
            }
        }
        Ok(false)
    }

    /// `MultifaceSpreader.spreadAll` with a `SculkVeinSpreaderConfig`.
    fn spread_all(&self, placement: &mut Placement, types: &[SpreadType], state: u32, pos: [i32; 3]) -> Result<i64> {
        let other_source = self.vein_index(state).is_none();
        let mut total = 0;
        for face in 0..6 {
            if !(other_source || self.has_face(state, face)) {
                continue;
            }
            for toward in 0..6 {
                if self.spread(placement, types, state, pos, face, toward, other_source)? {
                    total += 1;
                }
            }
        }
        Ok(total)
    }

    #[allow(clippy::too_many_arguments)]
    fn spread(&self, placement: &mut Placement, types: &[SpreadType], state: u32, pos: [i32; 3], face: usize, toward: usize, other_source: bool) -> Result<bool> {
        if toward / 2 == face / 2 {
            return Ok(false);
        }
        if !(other_source || self.has_face(state, face) && !self.has_face(state, toward)) {
            return Ok(false);
        }
        for &kind in types {
            let (at, side) = match kind {
                SpreadType::SamePosition => (pos, toward),
                SpreadType::SamePlane => (add(pos, DIRECTIONS[toward]), face),
                SpreadType::WrapAround => (add(add(pos, DIRECTIONS[toward]), DIRECTIONS[face]), toward ^ 1),
            };
            if self.can_spread_into(placement, pos, at, side)? {
                // `spreadToFace` with `markPostProcessing` (world generation).
                let there = Self::state(placement, at)?;
                let Some(placed) = self.state_for_placement(placement, there, at, side)? else {
                    return Ok(false);
                };
                placement.region.event(EVENT_POST_PROCESS, at[0], at[1], at[2], 0);
                return Self::set_block(placement, at, placed, 2);
            }
        }
        Ok(false)
    }

    fn can_spread_into(&self, placement: &mut Placement, from: [i32; 3], at: [i32; 3], side: usize) -> Result<bool> {
        let state = Self::state(placement, at)?;
        Ok(self.state_can_be_replaced(placement, from, at, side, state)? && self.valid_for_placement(placement, state, at, side)?)
    }

    /// `SculkVeinSpreaderConfig.stateCanBeReplaced`.
    fn state_can_be_replaced(&self, placement: &mut Placement, from: [i32; 3], at: [i32; 3], side: usize, state: u32) -> Result<bool> {
        let behind = Self::state(placement, add(at, DIRECTIONS[side]))?;
        let sets = &self.sets;
        if Self::is(placement, sets.sculk_block, behind) || Self::is(placement, sets.catalyst_block, behind) || Self::is(placement, sets.moving_piston, behind) {
            return Ok(false);
        }
        if dist_manhattan(from, at) == 2 && placement.test(&self.sturdy[side], add(from, DIRECTIONS[side ^ 1]))? {
            return Ok(false);
        }
        if !Self::is(placement, sets.fluid_empty, state) && !Self::is(placement, sets.fluid_water, state) {
            return Ok(false);
        }
        if Self::is(placement, sets.fire, state) {
            return Ok(false);
        }
        Ok(Self::is(placement, sets.can_be_replaced, state)
            || Self::air(placement, state)
            || self.vein_index(state).is_some()
            || Self::is(placement, sets.water_block, state) && Self::is(placement, sets.fluid_source, state))
    }

    /// `isValidStateForPlacement` (every face supported).
    fn valid_for_placement(&self, placement: &mut Placement, state: u32, at: [i32; 3], side: usize) -> Result<bool> {
        if self.has_face(state, side) {
            return Ok(false);
        }
        placement.test(&self.attach[side], add(at, DIRECTIONS[side]))
    }

    fn state_for_placement(&self, placement: &mut Placement, state: u32, at: [i32; 3], side: usize) -> Result<Option<u32>> {
        if !self.valid_for_placement(placement, state, at, side)? {
            return Ok(None);
        }
        let base = if self.vein_index(state).is_some() {
            state
        } else if Self::is(placement, self.sets.water_source, state) {
            self.vein_default_waterlogged
        } else {
            self.vein_default
        };
        Ok(Some(self.vein_with[self.vein_index(base).expect("vein state")][side]))
    }
}

/// `SculkBlock.getDecayPenalty` in Java's float arithmetic.
fn decay_penalty(pos: [i32; 3], origin: [i32; 3], charge: i32) -> i32 {
    let j = NO_GROWTH_RADIUS;
    let root = dist_sqr(pos, origin).sqrt() as f32 - j as f32;
    let f = root * root;
    let k = (24 - j) * (24 - j);
    let g = 1.0f32.min(f / k as f32);
    1.max((charge as f32 * g * 0.5f32) as i32)
}
