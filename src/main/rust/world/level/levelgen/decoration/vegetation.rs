//! Small `setBlock` features: block columns, seagrass, sea pickles, kelp,
//! disks, vines, block blobs and underwater magma. `canSurvive` checks arrive
//! as predicates Java compiled for each placed state, evaluated at the position.
use super::placement::Placement;
use super::program::{IntProvider, Predicate, Reader};
use super::region::Result;
use super::tree::Provider;
use super::write::EVENT_POST_PROCESS;
use crate::content::block::{StateFlags, StateId};

/// `Direction.values()` ordinals: down, up, north, south, west, east.
pub(crate) const DIRECTIONS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];
/// `Direction.Plane.HORIZONTAL`: north, east, south, west.
const HORIZONTAL: [[i32; 3]; 4] = [[0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];
/// `Heightmap.Types.OCEAN_FLOOR`.
const OCEAN_FLOOR: u32 = 3;

/// `BlockStateProvider`s features place.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum StateProvider {
    Simple(Provider),
    /// `RuleBasedBlockStateProvider`: the first rule whose predicate holds, else the fallback.
    RuleBased { rules: Vec<(Predicate, Provider)>, fallback: Provider },
}

impl StateProvider {
    fn read(reader: &mut Reader) -> Option<Self> {
        let provider = |reader: &mut Reader| {
            let provider = Provider::read(reader)?;
            let mut states = Vec::new();
            provider.states(&mut states);
            reader.record_writes(states);
            Some(provider)
        };
        Some(match reader.next()? {
            0 => StateProvider::Simple(provider(reader)?),
            1 => {
                let count = reader.count(64)?;
                let mut rules = Vec::with_capacity(count);
                for _ in 0..count {
                    rules.push((Predicate::read_checked(reader)?, provider(reader)?));
                }
                StateProvider::RuleBased { rules, fallback: provider(reader)? }
            }
            _ => return None,
        })
    }

    /// `getState(level, random, pos)`.
    fn state(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<u32> {
        match self {
            StateProvider::Simple(provider) => Ok(provider.state(placement)),
            StateProvider::RuleBased { rules, fallback } => {
                for (predicate, then) in rules {
                    if placement.test(predicate, pos)? {
                        return Ok(then.state(placement));
                    }
                }
                Ok(fallback.state(placement))
            }
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Vegetation {
    /// `BlockColumnFeature`.
    BlockColumn { layers: Vec<(IntProvider, StateProvider)>, direction: [i32; 3], allowed: Predicate, prioritize_tip: bool },
    /// `SeagrassFeature`: tall seagrass with this probability.
    Seagrass { probability: f32, water: usize, seagrass: u32, tall_lower: u32, tall_upper: u32, seagrass_survives: Predicate, tall_survives: Predicate },
    /// `SeaPickleFeature`: the states for one to four pickles.
    SeaPickle { count: IntProvider, water: usize, pickles: [u32; 4], survives: Predicate },
    /// `KelpFeature`: kelp heads with ages 20 to 23 and the plant.
    Kelp { water: usize, kelp: usize, heads: [u32; 4], plant: u32, plant_survives: Predicate, head_survives: Predicate },
    /// `DiskFeature`.
    Disk { provider: StateProvider, target: Predicate, radius: IntProvider, half_height: i32 },
    /// `VinesFeature`: each direction's vine state and `isAcceptableNeighbour` test.
    Vines { faces: [u32; 6], acceptable: Vec<Predicate> },
    /// `BlockBlobFeature`: the blob state and the dirt or base-stone states it rests on.
    BlockBlob { state: u32, rests_on: usize },
    /// `UnderwaterMagmaFeature`.
    UnderwaterMagma { floor_search_range: i32, radius: i32, probability: f32, water: usize, magma: u32 },
    /// `MultifaceGrowthFeature` with the default `MultifaceSpreader` config.
    Multiface(Multiface),
    /// `GeodeFeature` (`geode.rs`).
    Geode(Box<super::geode::Geode>),
    /// `MonsterRoomFeature` (`rooms.rs`).
    MonsterRoom(Box<super::rooms::MonsterRoom>),
    /// `DeltaFeature` (`nether.rs`).
    Delta(Box<super::nether::Delta>),
    /// `BasaltColumnsFeature` (`nether.rs`).
    BasaltColumns(Box<super::nether::BasaltColumns>),
    /// `CoralClawFeature`, `CoralTreeFeature`, `CoralMushroomFeature` (`coral.rs`).
    Coral(Box<super::coral::Coral>),
    /// `LakeFeature` (`lakes.rs`).
    Lake(Box<super::lakes::Lake>),
    /// `SculkPatchFeature` (`sculk.rs`).
    Sculk(Box<super::sculk::Sculk>),
    /// `PointedDripstoneFeature` (`dripstone.rs`).
    PointedDripstone(Box<super::dripstone::Pointed>),
    /// `DripstoneClusterFeature` (`dripstone.rs`).
    DripstoneCluster(Box<super::dripstone::Cluster>),
    /// `LargeDripstoneFeature` (`dripstone.rs`).
    LargeDripstone(Box<super::dripstone::Large>),
    /// `BlueIceFeature` (`ice.rs`).
    BlueIce(Box<super::ice::BlueIce>),
    /// `IceSpikeFeature` (`ice.rs`).
    IceSpike(Box<super::ice::IceSpike>),
    /// `BambooFeature` (`plants.rs`).
    Bamboo(Box<super::plants::Bamboo>),
    /// `HugeRedMushroomFeature`, `HugeBrownMushroomFeature` (`plants.rs`).
    HugeMushroom(Box<super::plants::HugeMushroom>),
    /// `HugeFungusFeature` (`plants.rs`).
    HugeFungus(Box<super::plants::HugeFungus>),
    /// `ChorusPlantFeature` (`plants.rs`).
    Chorus(Box<super::plants::Chorus>),
    /// `IcebergFeature` (`ice.rs`).
    Iceberg(Box<super::ice::Iceberg>),
    /// `SnowAndFreezeFeature` (`freeze.rs`).
    Freeze(Box<super::freeze::Freeze>),
    /// `ScatteredOreFeature` (`ore.rs`).
    ScatteredOre(Box<super::ore::Scattered>),
    /// `WeepingVinesFeature`, `TwistingVinesFeature` (`plants.rs`).
    NetherVines(Box<super::plants::NetherVines>),
    /// `BasaltPillarFeature` (`nether.rs`).
    BasaltPillar(Box<super::nether::BasaltPillar>),
    /// `DesertWellFeature` (`rooms.rs`).
    DesertWell(Box<super::rooms::DesertWell>),
    /// `EndIslandFeature` (`end.rs`).
    EndIsland(Box<super::end::EndIsland>),
}

/// A multiface block's states (contiguous IDs) and the growth configuration.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Multiface {
    first: u32,
    /// Per state: its face bits by `Direction` ordinal.
    faces: Vec<u8>,
    /// Per state: the state with each face set.
    with_face: Vec<[u32; 6]>,
    default: u32,
    default_waterlogged: u32,
    /// `isFaceSupported` by direction ordinal.
    supported: u8,
    water_source: usize,
    /// `DefaultSpreaderConfig.stateCanBeReplaced`.
    replaceable: usize,
    air_or_water: usize,
    placed_on: usize,
    /// `MultifaceBlock.canAttachTo(level, d, pos, state)` at `pos`, by direction.
    attach: Vec<Predicate>,
    search_range: i32,
    chance: f32,
    /// `validDirections` in configuration order.
    valid: Vec<usize>,
    /// `SculkVeinSpreaderConfig.stateCanBeReplaced`'s extra rules, for sculk veins.
    sculk: Option<Box<SculkReplace>>,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct SculkReplace {
    /// Sculk, sculk catalyst or moving piston behind the face.
    blocks_behind: usize,
    fluid_empty: usize,
    /// `getFluidState().is(Fluids.WATER)`.
    fluid_water: usize,
    fire: usize,
    can_be_replaced: usize,
    /// `isFaceSturdy(direction)` at the position, by direction.
    sturdy: Vec<Predicate>,
}

impl Predicate {
    pub(crate) fn read_checked(reader: &mut Reader) -> Option<Self> {
        Predicate::read(reader, 0)
    }
}

impl Vegetation {
    pub(crate) fn read(kind: i32, reader: &mut Reader, _depth: usize) -> Option<Self> {
        Some(match kind {
            12 => {
                let count = reader.count(16)?;
                let mut layers = Vec::with_capacity(count);
                for _ in 0..count {
                    layers.push((IntProvider::read(reader)?, StateProvider::read(reader)?));
                }
                Vegetation::BlockColumn {
                    layers,
                    direction: *DIRECTIONS.get(reader.count(5)?)?,
                    allowed: Predicate::read_checked(reader)?,
                    prioritize_tip: reader.flag()?,
                }
            }
            13 => Vegetation::Seagrass {
                probability: reader.float()?,
                water: reader.set()?,
                seagrass: reader.write_state()?,
                tall_lower: reader.write_state()?,
                tall_upper: reader.write_state()?,
                seagrass_survives: Predicate::read_checked(reader)?,
                tall_survives: Predicate::read_checked(reader)?,
            },
            14 => Vegetation::SeaPickle {
                count: IntProvider::read(reader)?,
                water: reader.set()?,
                pickles: [reader.write_state()?, reader.write_state()?, reader.write_state()?, reader.write_state()?],
                survives: Predicate::read_checked(reader)?,
            },
            15 => Vegetation::Kelp {
                water: reader.set()?,
                kelp: reader.set()?,
                heads: [reader.write_state()?, reader.write_state()?, reader.write_state()?, reader.write_state()?],
                plant: reader.write_state()?,
                plant_survives: Predicate::read_checked(reader)?,
                head_survives: Predicate::read_checked(reader)?,
            },
            16 => Vegetation::Disk {
                provider: StateProvider::read(reader)?,
                target: Predicate::read_checked(reader)?,
                radius: IntProvider::read(reader)?,
                half_height: reader.next()?,
            },
            17 => {
                let mut faces = [0; 6];
                let mut acceptable = Vec::with_capacity(6);
                for face in faces.iter_mut() {
                    *face = reader.write_state()?;
                    acceptable.push(Predicate::read_checked(reader)?);
                }
                Vegetation::Vines { faces, acceptable }
            }
            18 => Vegetation::BlockBlob { state: reader.write_state()?, rests_on: reader.set()? },
            20 => {
                let first = reader.state()?;
                let count = reader.count(1 << 12).filter(|&n| n > 0)?;
                let mut faces = Vec::with_capacity(count);
                let mut with_face = Vec::with_capacity(count);
                for _ in 0..count {
                    faces.push(u8::try_from(reader.next()?).ok().filter(|&f| f < 64)?);
                    let mut states = [0; 6];
                    for state in states.iter_mut() {
                        *state = reader.write_state()?;
                    }
                    with_face.push(states);
                }
                let default = reader.write_state()?;
                let default_waterlogged = reader.write_state()?;
                let supported = u8::try_from(reader.next()?).ok().filter(|&f| f < 64)?;
                let (water_source, replaceable, air_or_water, placed_on) = (reader.set()?, reader.set()?, reader.set()?, reader.set()?);
                let attach = (0..6).map(|_| Predicate::read_checked(reader)).collect::<Option<Vec<_>>>()?;
                let search_range = reader.next()?;
                let chance = reader.float()?;
                let valid_count = reader.count(6)?;
                let valid = (0..valid_count).map(|_| reader.count(5)).collect::<Option<Vec<_>>>()?;
                let index = |state: u32| state.checked_sub(first).map(|i| i as usize).filter(|&i| i < count);
                if index(default).is_none() || index(default_waterlogged).is_none() {
                    return None;
                }
                let sculk = if reader.flag()? {
                    Some(Box::new(SculkReplace {
                        blocks_behind: reader.set()?,
                        fluid_empty: reader.set()?,
                        fluid_water: reader.set()?,
                        fire: reader.set()?,
                        can_be_replaced: reader.set()?,
                        sturdy: (0..6).map(|_| Predicate::read_checked(reader)).collect::<Option<Vec<_>>>()?,
                    }))
                } else {
                    None
                };
                Vegetation::Multiface(Multiface {
                    first, faces, with_face, default, default_waterlogged, supported, water_source, replaceable,
                    air_or_water, placed_on, attach, search_range, chance, valid, sculk,
                })
            }
            21 => Vegetation::Geode(Box::new(super::geode::Geode::read(reader)?)),
            22 => Vegetation::MonsterRoom(Box::new(super::rooms::MonsterRoom::read(reader)?)),
            23 => Vegetation::Delta(Box::new(super::nether::Delta::read(reader)?)),
            24 => Vegetation::BasaltColumns(Box::new(super::nether::BasaltColumns::read(reader)?)),
            25 => Vegetation::Coral(Box::new(super::coral::Coral::read(super::coral::Shape::Claw, reader)?)),
            26 => Vegetation::Coral(Box::new(super::coral::Coral::read(super::coral::Shape::Tree, reader)?)),
            27 => Vegetation::Coral(Box::new(super::coral::Coral::read(super::coral::Shape::Mushroom, reader)?)),
            28 => Vegetation::Lake(Box::new(super::lakes::Lake::read(reader)?)),
            29 => Vegetation::Sculk(Box::new(super::sculk::Sculk::read(reader)?)),
            30 => Vegetation::PointedDripstone(Box::new(super::dripstone::Pointed::read(reader)?)),
            31 => Vegetation::DripstoneCluster(Box::new(super::dripstone::Cluster::read(reader)?)),
            32 => Vegetation::LargeDripstone(Box::new(super::dripstone::Large::read(reader)?)),
            33 => Vegetation::BlueIce(Box::new(super::ice::BlueIce::read(reader)?)),
            34 => Vegetation::IceSpike(Box::new(super::ice::IceSpike::read(reader)?)),
            35 => Vegetation::Bamboo(Box::new(super::plants::Bamboo::read(reader)?)),
            36 | 37 => Vegetation::HugeMushroom(Box::new(super::plants::HugeMushroom::read(kind == 36, reader)?)),
            38 => Vegetation::HugeFungus(Box::new(super::plants::HugeFungus::read(reader)?)),
            39 => Vegetation::Chorus(Box::new(super::plants::Chorus::read(reader)?)),
            41 => Vegetation::Iceberg(Box::new(super::ice::Iceberg::read(reader)?)),
            42 => Vegetation::Freeze(Box::new(super::freeze::Freeze::read(reader)?)),
            43 => Vegetation::ScatteredOre(Box::new(super::ore::Scattered::read(reader)?)),
            44 | 45 => Vegetation::NetherVines(Box::new(super::plants::NetherVines::read(kind == 45, reader)?)),
            46 => Vegetation::BasaltPillar(Box::new(super::nether::BasaltPillar::read(reader)?)),
            49 => Vegetation::DesertWell(Box::new(super::rooms::DesertWell::read(reader)?)),
            40 => Vegetation::EndIsland(Box::new(super::end::EndIsland::read(reader)?)),
            19 => Vegetation::UnderwaterMagma {
                floor_search_range: reader.next()?,
                radius: reader.next()?,
                probability: reader.float()?,
                water: reader.set()?,
                magma: reader.write_state()?,
            },
            _ => return None,
        })
    }

    pub(crate) fn kind(&self) -> i32 {
        match self {
            Vegetation::BlockColumn { .. } => 12,
            Vegetation::Seagrass { .. } => 13,
            Vegetation::SeaPickle { .. } => 14,
            Vegetation::Kelp { .. } => 15,
            Vegetation::Disk { .. } => 16,
            Vegetation::Vines { .. } => 17,
            Vegetation::BlockBlob { .. } => 18,
            Vegetation::UnderwaterMagma { .. } => 19,
            Vegetation::Multiface(_) => 20,
            Vegetation::Geode(_) => 21,
            Vegetation::MonsterRoom(_) => 22,
            Vegetation::Delta(_) => 23,
            Vegetation::BasaltColumns(_) => 24,
            Vegetation::Coral(coral) => coral.kind(),
            Vegetation::Lake(_) => 28,
            Vegetation::Sculk(_) => 29,
            Vegetation::PointedDripstone(_) => 30,
            Vegetation::DripstoneCluster(_) => 31,
            Vegetation::LargeDripstone(_) => 32,
            Vegetation::BlueIce(_) => 33,
            Vegetation::IceSpike(_) => 34,
            Vegetation::Bamboo(_) => 35,
            Vegetation::HugeMushroom(mushroom) => mushroom.kind(),
            Vegetation::HugeFungus(_) => 38,
            Vegetation::Chorus(_) => 39,
            Vegetation::EndIsland(_) => 40,
            Vegetation::Iceberg(_) => 41,
            Vegetation::Freeze(_) => 42,
            Vegetation::ScatteredOre(_) => 43,
            Vegetation::NetherVines(vines) => vines.kind(),
            Vegetation::BasaltPillar(_) => 46,
            Vegetation::DesertWell(_) => 49,
        }
    }
}

pub(crate) fn offset(pos: [i32; 3], by: [i32; 3]) -> [i32; 3] {
    [pos[0].wrapping_add(by[0]), pos[1].wrapping_add(by[1]), pos[2].wrapping_add(by[2])]
}

fn state_at(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
    placement.region.level_block_state(pos[0], pos[1], pos[2])
}

pub(crate) fn in_set(placement: &Placement, set: usize, pos: [i32; 3]) -> Result<bool> {
    Ok(placement.program.in_set(set, state_at(placement, pos)?))
}

pub(crate) fn is_air(placement: &Placement, pos: [i32; 3]) -> Result<bool> {
    let state = state_at(placement, pos)?;
    Ok(placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR))
}

pub(crate) fn set_block(placement: &mut Placement, pos: [i32; 3], state: u32, flags: i32) -> Result<()> {
    placement.region.set_block(pos[0], pos[1], pos[2], state, flags).map(|_| ())
}

/// `BlockPos.betweenClosed(a, b)`: X fastest, then Y, then Z.
fn between_closed(a: [i32; 3], b: [i32; 3]) -> impl Iterator<Item = [i32; 3]> {
    let min = [a[0].min(b[0]), a[1].min(b[1]), a[2].min(b[2])];
    let max = [a[0].max(b[0]), a[1].max(b[1]), a[2].max(b[2])];
    (min[2]..=max[2]).flat_map(move |z| (min[1]..=max[1]).flat_map(move |y| (min[0]..=max[0]).map(move |x| [x, y, z])))
}

pub(crate) fn place(placement: &mut Placement, feature: &Vegetation, origin: [i32; 3]) -> Result<bool> {
    match feature {
        Vegetation::BlockColumn { layers, direction, allowed, prioritize_tip } => {
            block_column(placement, layers, *direction, allowed, *prioritize_tip, origin)
        }
        Vegetation::Seagrass { probability, water, seagrass, tall_lower, tall_upper, seagrass_survives, tall_survives } => {
            let random = &mut placement.random;
            let dx = random.next_int(8) - random.next_int(8);
            let dz = random.next_int(8) - random.next_int(8);
            let (x, z) = (origin[0] + dx, origin[2] + dz);
            let pos = [x, placement.region.height_by_type(OCEAN_FLOOR, x, z)?, z];
            if !in_set(placement, *water, pos)? {
                return Ok(false);
            }
            let tall = placement.random.next_double() < *probability as f64;
            if !placement.test(if tall { tall_survives } else { seagrass_survives }, pos)? {
                return Ok(false);
            }
            if tall {
                let above = offset(pos, [0, 1, 0]);
                if in_set(placement, *water, above)? {
                    set_block(placement, pos, *tall_lower, 2)?;
                    set_block(placement, above, *tall_upper, 2)?;
                }
            } else {
                set_block(placement, pos, *seagrass, 2)?;
            }
            Ok(true)
        }
        Vegetation::SeaPickle { count, water, pickles, survives } => {
            let count = count.sample(placement);
            let mut placed = 0;
            for _ in 0..count {
                let random = &mut placement.random;
                let dx = random.next_int(8) - random.next_int(8);
                let dz = random.next_int(8) - random.next_int(8);
                let (x, z) = (origin[0] + dx, origin[2] + dz);
                let pos = [x, placement.region.height_by_type(OCEAN_FLOOR, x, z)?, z];
                let state = pickles[placement.random.next_int(4) as usize];
                if in_set(placement, *water, pos)? && placement.test(survives, pos)? {
                    set_block(placement, pos, state, 2)?;
                    placed += 1;
                }
            }
            Ok(placed > 0)
        }
        Vegetation::Kelp { water, kelp, heads, plant, plant_survives, head_survives } => {
            let mut pos = [origin[0], placement.region.height_by_type(OCEAN_FLOOR, origin[0], origin[2])?, origin[2]];
            let mut placed = 0;
            if in_set(placement, *water, pos)? {
                let top = 1 + placement.random.next_int(10);
                for l in 0..=top {
                    if in_set(placement, *water, pos)?
                        && in_set(placement, *water, offset(pos, [0, 1, 0]))?
                        && placement.test(plant_survives, pos)?
                    {
                        if l == top {
                            let head = heads[placement.random.next_int(4) as usize];
                            set_block(placement, pos, head, 2)?;
                            placed += 1;
                        } else {
                            set_block(placement, pos, *plant, 2)?;
                        }
                    } else if l > 0 {
                        let below = offset(pos, [0, -1, 0]);
                        if placement.test(head_survives, below)? && !in_set(placement, *kelp, offset(below, [0, -1, 0]))? {
                            let head = heads[placement.random.next_int(4) as usize];
                            set_block(placement, below, head, 2)?;
                            placed += 1;
                        }
                        break;
                    }
                    pos = offset(pos, [0, 1, 0]);
                }
            }
            Ok(placed > 0)
        }
        Vegetation::Disk { provider, target, radius, half_height } => {
            let top = origin[1] + half_height;
            let bottom = origin[1] - half_height - 1;
            let l = radius.sample(placement);
            let mut placed = false;
            for column in between_closed(offset(origin, [-l, 0, -l]), offset(origin, [l, 0, l])) {
                let (m, n) = (column[0] - origin[0], column[2] - origin[2]);
                if m * m + n * n <= l * l {
                    placed |= disk_column(placement, provider, target, top, bottom, column)?;
                }
            }
            Ok(placed)
        }
        Vegetation::Vines { faces, acceptable } => {
            if !is_air(placement, origin)? {
                return Ok(false);
            }
            for (direction, by) in DIRECTIONS.iter().enumerate().skip(1) {
                if placement.test(&acceptable[direction], offset(origin, *by))? {
                    set_block(placement, origin, faces[direction], 2)?;
                    return Ok(true);
                }
            }
            Ok(false)
        }
        Vegetation::BlockBlob { state, rests_on } => {
            let floor = placement.region.min_y + 3;
            let mut pos = origin;
            while pos[1] > floor {
                let below = offset(pos, [0, -1, 0]);
                if !is_air(placement, below)? && in_set(placement, *rests_on, below)? {
                    break;
                }
                pos = below;
            }
            if pos[1] <= floor {
                return Ok(false);
            }
            for _ in 0..3 {
                let random = &mut placement.random;
                let (j, k, l) = (random.next_int(2), random.next_int(2), random.next_int(2));
                let f = (j + k + l) as f32 * 0.333f32 + 0.5f32;
                let limit = (f * f) as f64;
                for p in between_closed(offset(pos, [-j, -k, -l]), offset(pos, [j, k, l])) {
                    let (dx, dy, dz) = ((p[0] - pos[0]) as f64, (p[1] - pos[1]) as f64, (p[2] - pos[2]) as f64);
                    if dx * dx + dy * dy + dz * dz <= limit {
                        set_block(placement, p, *state, 3)?;
                    }
                }
                let random = &mut placement.random;
                let dx = -1 + random.next_int(2);
                let dy = -random.next_int(2);
                let dz = -1 + random.next_int(2);
                pos = offset(pos, [dx, dy, dz]);
            }
            Ok(true)
        }
        Vegetation::Multiface(multiface) => multiface.place(placement, origin),
        Vegetation::Geode(geode) => geode.place(placement, origin),
        Vegetation::MonsterRoom(room) => room.place(placement, origin),
        Vegetation::Delta(delta) => delta.place(placement, origin),
        Vegetation::BasaltColumns(columns) => columns.place(placement, origin),
        Vegetation::Coral(coral) => coral.place(placement, origin),
        Vegetation::Lake(lake) => lake.place(placement, origin),
        Vegetation::Sculk(sculk) => sculk.place(placement, origin),
        Vegetation::PointedDripstone(dripstone) => dripstone.place(placement, origin),
        Vegetation::DripstoneCluster(cluster) => cluster.place(placement, origin),
        Vegetation::LargeDripstone(large) => large.place(placement, origin),
        Vegetation::BlueIce(ice) => ice.place(placement, origin),
        Vegetation::IceSpike(spike) => spike.place(placement, origin),
        Vegetation::Bamboo(bamboo) => bamboo.place(placement, origin),
        Vegetation::HugeMushroom(mushroom) => mushroom.place(placement, origin),
        Vegetation::HugeFungus(fungus) => fungus.place(placement, origin),
        Vegetation::Chorus(chorus) => chorus.place(placement, origin),
        Vegetation::EndIsland(island) => island.place(placement, origin),
        Vegetation::Iceberg(iceberg) => iceberg.place(placement, origin),
        Vegetation::Freeze(freeze) => freeze.place(placement, origin),
        Vegetation::ScatteredOre(ore) => ore.place(placement, origin),
        Vegetation::NetherVines(vines) => vines.place(placement, origin),
        Vegetation::BasaltPillar(pillar) => pillar.place(placement, origin),
        Vegetation::DesertWell(well) => well.place(placement, origin),
        Vegetation::UnderwaterMagma { floor_search_range, radius, probability, water, magma } => {
            let Some(floor) = magma_floor(placement, origin, *floor_search_range, *water)? else {
                return Ok(false);
            };
            let center = [origin[0], floor, origin[2]];
            let r = *radius;
            let water_or_air = |placement: &Placement, pos: [i32; 3]| -> Result<bool> {
                Ok(in_set(placement, *water, pos)? || is_air(placement, pos)?)
            };
            let mut placed = 0;
            for pos in between_closed(offset(center, [-r, -r, -r]), offset(center, [r, r, r])) {
                if placement.random.next_float() >= *probability {
                    continue;
                }
                let mut valid = !water_or_air(placement, pos)? && !water_or_air(placement, offset(pos, [0, -1, 0]))?;
                if valid {
                    for by in HORIZONTAL {
                        if water_or_air(placement, offset(pos, by))? {
                            valid = false;
                            break;
                        }
                    }
                }
                if valid {
                    set_block(placement, pos, *magma, 2)?;
                    placed += 1;
                }
            }
            Ok(placed > 0)
        }
    }
}

/// `Util.shuffle`: Fisher–Yates from the top, `nextInt(j)` for j = n down to 2.
fn shuffle(placement: &mut Placement, items: &mut [usize]) {
    for j in (2..=items.len()).rev() {
        let k = placement.random.next_int(j as i32) as usize;
        items.swap(j - 1, k);
    }
}

impl Multiface {
    fn index(&self, state: u32) -> Option<usize> {
        state.checked_sub(self.first).map(|i| i as usize).filter(|&i| i < self.faces.len())
    }

    /// `MultifaceBlock.hasFace`: false for other blocks.
    fn has_face(&self, state: u32, direction: usize) -> bool {
        self.index(state).is_some_and(|i| self.faces[i] & (1 << direction) != 0)
    }

    /// `isValidStateForPlacement` then `getStateForPlacement(state, level, pos, direction)`.
    fn state_for_placement(&self, placement: &mut Placement, state: u32, pos: [i32; 3], direction: usize) -> Result<Option<u32>> {
        if self.supported & (1 << direction) == 0 || self.has_face(state, direction) {
            return Ok(None);
        }
        if !placement.test(&self.attach[direction], offset(pos, DIRECTIONS[direction]))? {
            return Ok(None);
        }
        let base = if self.index(state).is_some() {
            state
        } else if placement.program.in_set(self.water_source, state) {
            self.default_waterlogged
        } else {
            self.default
        };
        Ok(Some(self.with_face[self.index(base).expect("base is this block")][direction]))
    }

    /// The spreader config's `stateCanBeReplaced(level, pos, at, side, state)`.
    fn can_replace(&self, placement: &mut Placement, pos: [i32; 3], at: [i32; 3], side: usize, there: u32) -> Result<bool> {
        let Some(sculk) = &self.sculk else {
            return Ok(placement.program.in_set(self.replaceable, there));
        };
        let program = placement.program;
        if program.in_set(sculk.blocks_behind, state_at(placement, offset(at, DIRECTIONS[side]))?) {
            return Ok(false);
        }
        let manhattan = ((at[0] - pos[0]).abs() as f32 + (at[1] - pos[1]).abs() as f32 + (at[2] - pos[2]).abs() as f32) as i32;
        if manhattan == 2 && placement.test(&sculk.sturdy[side], offset(pos, DIRECTIONS[side ^ 1]))? {
            return Ok(false);
        }
        if !program.in_set(sculk.fluid_empty, there) && !program.in_set(sculk.fluid_water, there) {
            return Ok(false);
        }
        if program.in_set(sculk.fire, there) {
            return Ok(false);
        }
        Ok(program.in_set(sculk.can_be_replaced, there) || program.in_set(self.replaceable, there))
    }

    /// `MultifaceGrowthFeature.placeGrowthIfPossible`.
    fn place_growth(&self, placement: &mut Placement, pos: [i32; 3], state: u32, directions: &[usize]) -> Result<bool> {
        for &direction in directions {
            if !in_set(placement, self.placed_on, offset(pos, DIRECTIONS[direction]))? {
                continue;
            }
            let Some(placed) = self.state_for_placement(placement, state, pos, direction)? else {
                return Ok(false);
            };
            set_block(placement, pos, placed, 3)?;
            mark_post_processing(placement, pos)?;
            if placement.random.next_float() < self.chance {
                // `spreadFromFaceTowardRandomDirection(placed, level, pos, direction, random, true)`.
                let mut toward = [0, 1, 2, 3, 4, 5];
                shuffle(placement, &mut toward);
                for to in toward {
                    if self.spread(placement, placed, pos, direction, to)? {
                        break;
                    }
                }
            }
            return Ok(true);
        }
        Ok(false)
    }

    /// `spreadFromFaceTowardDirection` with the default config, marking post-processing.
    fn spread(&self, placement: &mut Placement, state: u32, pos: [i32; 3], face: usize, toward: usize) -> Result<bool> {
        if toward / 2 == face / 2 || !self.has_face(state, face) || self.has_face(state, toward) {
            return Ok(false);
        }
        // SAME_POSITION, SAME_PLANE, WRAP_AROUND.
        let candidates = [
            (pos, toward),
            (offset(pos, DIRECTIONS[toward]), face),
            (offset(offset(pos, DIRECTIONS[toward]), DIRECTIONS[face]), toward ^ 1),
        ];
        for (at, side) in candidates {
            let there = state_at(placement, at)?;
            if self.can_replace(placement, pos, at, side, there)? && self.state_for_placement(placement, there, at, side)?.is_some() {
                // `spreadToFace`: re-read and place.
                let there = state_at(placement, at)?;
                let Some(placed) = self.state_for_placement(placement, there, at, side)? else {
                    return Ok(false);
                };
                mark_post_processing(placement, at)?;
                return placement.region.set_block(at[0], at[1], at[2], placed, 2);
            }
        }
        Ok(false)
    }

    /// `MultifaceGrowthFeature.place`.
    fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let here = state_at(placement, origin)?;
        if !placement.program.in_set(self.air_or_water, here) {
            return Ok(false);
        }
        let mut directions = self.valid.clone();
        shuffle(placement, &mut directions);
        if self.place_growth(placement, origin, here, &directions)? {
            return Ok(true);
        }
        for &direction in &directions {
            let at = offset(origin, DIRECTIONS[direction]);
            let mut others: Vec<usize> = self.valid.iter().copied().filter(|&d| d != direction ^ 1).collect();
            shuffle(placement, &mut others);
            // `mutableBlockPos.setWithOffset(blockPos, direction)` revisits the same neighbour each step.
            for _ in 0..self.search_range {
                let state = state_at(placement, at)?;
                if !placement.program.in_set(self.air_or_water, state) && self.index(state).is_none() {
                    break;
                }
                if self.place_growth(placement, at, state, &others)? {
                    return Ok(true);
                }
            }
        }
        Ok(false)
    }
}

fn mark_post_processing(placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
    placement.region.event(EVENT_POST_PROCESS, pos[0], pos[1], pos[2], 0);
    Ok(())
}

/// `UnderwaterMagmaFeature.getFloorY`: `Column.scan` with water inside and
/// anything else as the edge; the floor is the downward scan's edge.
fn magma_floor(placement: &Placement, origin: [i32; 3], range: i32, water: usize) -> Result<Option<i32>> {
    if !in_set(placement, water, origin)? {
        return Ok(None);
    }
    let scan = |placement: &Placement, step: i32| -> Result<Option<i32>> {
        let mut pos = origin;
        let mut k = 1;
        while k < range && in_set(placement, water, pos)? {
            pos[1] += step;
            k += 1;
        }
        Ok((!in_set(placement, water, pos)?).then_some(pos[1]))
    };
    scan(placement, 1)?;
    scan(placement, -1)
}

/// `DiskFeature.placeColumn` with `Feature.markAboveForPostProcessing`.
fn disk_column(placement: &mut Placement, provider: &StateProvider, target: &Predicate, top: i32, bottom: i32, column: [i32; 3]) -> Result<bool> {
    let mut placed = false;
    let mut previous = false;
    let mut y = top;
    while y > bottom {
        let pos = [column[0], y, column[2]];
        if placement.test(target, pos)? {
            let state = provider.state(placement, pos)?;
            set_block(placement, pos, state, 2)?;
            if !previous {
                mark_above(placement, pos)?;
            }
            placed = true;
            previous = true;
        } else {
            previous = false;
        }
        y -= 1;
    }
    Ok(placed)
}

fn mark_above(placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
    let mut at = pos;
    for _ in 0..2 {
        at[1] += 1;
        if is_air(placement, at)? {
            return Ok(());
        }
        placement.region.event(EVENT_POST_PROCESS, at[0], at[1], at[2], 0);
    }
    Ok(())
}

/// `BlockColumnFeature.place` and its `truncate`.
fn block_column(
    placement: &mut Placement,
    layers: &[(IntProvider, StateProvider)],
    direction: [i32; 3],
    allowed: &Predicate,
    prioritize_tip: bool,
    origin: [i32; 3],
) -> Result<bool> {
    let mut heights = Vec::with_capacity(layers.len());
    let mut total = 0i32;
    for (height, _) in layers {
        let h = height.sample(placement);
        heights.push(h);
        total = total.wrapping_add(h);
    }
    if total == 0 {
        return Ok(false);
    }
    let mut probe = offset(origin, direction);
    for l in 0..total {
        if !placement.test(allowed, probe)? {
            let mut k = total - l;
            let order: Box<dyn Iterator<Item = usize>> =
                if prioritize_tip { Box::new(0..heights.len()) } else { Box::new((0..heights.len()).rev()) };
            for o in order {
                if k <= 0 {
                    break;
                }
                let q = heights[o].min(k);
                k -= q;
                heights[o] -= q;
            }
            break;
        }
        probe = offset(probe, direction);
    }
    let mut pos = origin;
    for (index, (_, provider)) in layers.iter().enumerate() {
        for _ in 0..heights[index].max(0) {
            let state = provider.state(placement, pos)?;
            set_block(placement, pos, state, 2)?;
            pos = offset(pos, direction);
        }
    }
    Ok(true)
}
