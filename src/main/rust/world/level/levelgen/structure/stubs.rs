//! `findGenerationPoint` and `isValidBiome` for structures whose start
//! position needs at most one rotation draw: ocean monuments
//! (`getBiomesWithin` over the surrounding quarts), single-piece temples (the
//! lowest corner against sea level), chunk-centre structures on a heightmap,
//! fixed positions and the 5x5 lowest-corner search. Java then builds the
//! pieces with the generation context's random (redrawing that rotation).

/// The structure families answered here. Monuments with param 1 plan their
/// building (`monument_plan.rs`) for valid stubs.
pub(crate) const MONUMENT: i32 = 0;
/// `SinglePieceStructure`: width and depth.
pub(crate) const SINGLE_PIECE: i32 = 1;
/// `onTopOfChunkCenter` on a heightmap (0 `WORLD_SURFACE_WG`, 1 `OCEAN_FLOOR_WG`).
pub(crate) const ON_TOP: i32 = 2;
/// A fixed position: the chunk's minimum corner at a Y.
pub(crate) const FIXED: i32 = 3;
/// A random rotation, then `getLowestYIn5by5BoxOffset7Blocks` (below Y 60
/// places nothing). Params [planner (1 End city, 2 mansion), 0, the planner's
/// params]: valid stubs plan their pieces as words.
pub(crate) const LOWEST_5X5: i32 = 4;
/// `RuinedPortalStructure`: params [0, 0, setups, per setup (placement
/// ordinal, air pocket probability bits, weight bits), then the 10 portal and
/// 3 giant portal templates' sizes (x, y, z)].
pub(crate) const RUINED_PORTAL: i32 = 5;

/// `RuinedPortalPiece.VerticalPlacement` ordinals.
const PARTLY_BURIED: i32 = 1;
const ON_OCEAN_FLOOR: i32 = 2;
const IN_MOUNTAIN: i32 = 3;
const UNDERGROUND: i32 = 4;
const IN_NETHER: i32 = 5;
/// `NetherFossilStructure`: params [0, 0, uniform min Y, max Y, air states, then
/// floor states (soul sand or a sturdy top), each list after its length].
pub(crate) const NETHER_FOSSIL: i32 = 6;
/// `MineshaftStructure`: params [0, 0, mesa, the generator's min Y]; the
/// pieces come back as words (see `Stub::words`).
pub(crate) const MINESHAFT: i32 = 7;
/// `NetherFortressStructure`: the fixed position at a Y, then (valid stubs)
/// `fortress_plan.rs`'s pieces as words.
pub(crate) const FORTRESS: i32 = 8;
/// `StrongholdStructure`: params [0, 0, the generator's min Y]; Y 0, then
/// (valid stubs) `stronghold_plan.rs`'s pieces as words.
pub(crate) const STRONGHOLD: i32 = 9;

/// Heights by heightmap and climate leaves at quart positions.
pub(crate) trait Sampling {
    /// `getBaseHeight(x, z, WORLD_SURFACE_WG)`.
    fn surface(&mut self, x: i32, z: i32) -> Result<i32, ()>;
    /// `getBaseHeight(x, z, OCEAN_FLOOR_WG)`.
    fn ocean_floor(&mut self, x: i32, z: i32) -> Result<i32, ()>;
    /// `getNoiseBiome(qx, qy, qz)`'s leaf, from the thread's previous leaf.
    fn leaf(&mut self, qx: i32, qy: i32, qz: i32) -> Result<usize, ()>;
    /// `getBaseColumn(x, z)`'s states from `column_min_y()` up.
    fn column(&mut self, x: i32, z: i32) -> Result<Vec<i32>, ()>;
    /// Whether `state` passes `WORLD_SURFACE_WG`'s (or `OCEAN_FLOOR_WG`'s) predicate.
    fn heightmap(&self, state: i32, ocean: bool) -> bool;
    /// The base columns' first Y.
    fn column_min_y(&self) -> i32;
}

pub(crate) struct Stub {
    pub pos: [i32; 3],
    pub valid: bool,
    /// The rotation drawn (`LOWEST_5X5`, `RUINED_PORTAL`), else 0.
    pub rotation: i32,
    /// `RUINED_PORTAL`: [setup, template (giant portals from 10), air pocket | mirror front-back << 1].
    pub extra: [i32; 3],
    /// `MINESHAFT`'s pieces: per piece [kind, depth, box×6, direction, fields×3,
    /// entrances, entrance boxes×6].
    pub words: Vec<i32>,
}

/// `findValidGenerationPoint`: None for no stub; `accept` per climate leaf is
/// the structure's biome set, `surround` the monument's required tag.
pub(crate) fn find(kind: i32, chunk: [i32; 2], sea_level: i32, min_y: i32, seed: i64, params: &[i32], accept: &dyn Fn(usize) -> bool,
    surround: &dyn Fn(usize) -> bool, s: &mut dyn Sampling) -> Result<Option<Stub>, ()> {
    let mut rotation = 0;
    let mut extra = [0; 3];
    let mut words = Vec::new();
    let (min_x, min_z) = (chunk[0] * 16, chunk[1] * 16);
    let (mid_x, mid_z) = (min_x + 8, min_z + 8);
    let on_top = |s: &mut dyn Sampling, ocean: bool| -> Result<[i32; 3], ()> {
        // onTopOfChunkCenter: getFirstOccupiedHeight, one below the base height.
        let h = if ocean { s.ocean_floor(mid_x, mid_z)? } else { s.surface(mid_x, mid_z)? };
        Ok([mid_x, h - 1, mid_z])
    };
    let pos = match kind {
        MONUMENT => {
            // getBiomesWithin(blockX(9), seaLevel, blockZ(9), 29): every quart, z outer, x, then y.
            let (i, j, k, l) = (min_x + 9, sea_level, min_z + 9, 29);
            let (m, n, o) = ((i - l) >> 2, (j - l) >> 2, (k - l) >> 2);
            let (p, q, r) = ((i + l) >> 2, (j + l) >> 2, (k + l) >> 2);
            let mut all = true;
            for v in 0..=(r - o) {
                for w in 0..=(p - m) {
                    for x in 0..=(q - n) {
                        let leaf = s.leaf(m + w, n + x, o + v)?;
                        all &= surround(leaf);
                    }
                }
            }
            if !all {
                return Ok(None);
            }
            on_top(s, true)?
        }
        SINGLE_PIECE => {
            // getLowestY(width, depth) over the four corners' first occupied heights.
            let (width, depth) = (params[0], params[1]);
            let corners = [s.surface(min_x, min_z)?, s.surface(min_x, min_z + depth)?, s.surface(min_x + width, min_z)?, s.surface(min_x + width, min_z + depth)?];
            if corners.iter().map(|h| h - 1).min().unwrap_or(i32::MIN) < sea_level {
                return Ok(None);
            }
            on_top(s, false)?
        }
        ON_TOP => on_top(s, params[0] != 0)?,
        FIXED | FORTRESS => [min_x, params[0], min_z],
        STRONGHOLD => [min_x, 0, min_z],
        LOWEST_5X5 => {
            // The generation context's random: Rotation.getRandom.
            let mut random = crate::world::level::levelgen::random::Legacy::new(0);
            random.set_large_feature_seed(seed, chunk[0], chunk[1]);
            rotation = random.next_int_bound(4);
            let (i, j) = match rotation {
                1 => (-5, 5),
                2 => (-5, -5),
                3 => (5, -5),
                _ => (5, 5),
            };
            let (k, l) = (min_x + 7, min_z + 7);
            let corners = [s.surface(k, l)?, s.surface(k, l + j)?, s.surface(k + i, l)?, s.surface(k + i, l + j)?];
            let y = corners.iter().map(|h| h - 1).min().unwrap_or(i32::MIN);
            if y < 60 {
                return Ok(None);
            }
            [k, y, l]
        }
        NETHER_FOSSIL => match nether_fossil(chunk, sea_level, seed, params.get(2..).ok_or(())?, s)? {
            Some(pos) => pos,
            None => return Ok(None),
        },
        MINESHAFT => {
            let (mesa, generator_min_y) = (*params.get(2).ok_or(())? != 0, *params.get(3).ok_or(())?);
            let (pieces, offset) = super::mineshaft_plan::plan(chunk, seed, mesa, sea_level, generator_min_y, &mut |x, z| s.surface(x, z))?;
            for p in pieces {
                words.extend([p.kind, p.depth]);
                words.extend(p.bb);
                words.push(p.direction);
                words.extend(p.fields);
                words.push(p.entrances.len() as i32);
                for e in p.entrances {
                    words.extend(e);
                }
            }
            [min_x + 8, 50 + offset, min_z]
        }
        RUINED_PORTAL => {
            let (pos, r, e) = ruined_portal(chunk, min_y, seed, params.get(2..).ok_or(())?, s)?;
            (rotation, extra) = (r, e);
            pos
        }
        _ => return Err(()),
    };
    let leaf = s.leaf(pos[0] >> 2, pos[1] >> 2, pos[2] >> 2)?;
    let valid = accept(leaf);
    if valid && (kind == SINGLE_PIECE || kind == ON_TOP) && params.len() > 2 {
        // small_plan.rs: params [a, b, family, family config].
        let mut random = crate::world::level::levelgen::random::Legacy::new(0);
        random.set_large_feature_seed(seed, chunk[0], chunk[1]);
        words = super::small_plan::plan(&mut random, chunk, pos, &params[2..])?;
    }
    if valid && kind == MONUMENT && params.first() == Some(&1) {
        let mut random = crate::world::level::levelgen::random::Legacy::new(0);
        random.set_large_feature_seed(seed, chunk[0], chunk[1]);
        words = super::monument_plan::plan(&mut random, chunk, pos, &[])?;
    }
    if valid && kind == LOWEST_5X5 && params.first().is_some_and(|&p| p != 0) {
        let mut random = crate::world::level::levelgen::random::Legacy::new(0);
        random.set_large_feature_seed(seed, chunk[0], chunk[1]);
        let planned = params.get(2..).ok_or(())?;
        words = match params[0] {
            1 => super::end_city_plan::plan(&mut random, chunk, pos, planned)?,
            2 => super::mansion_plan::plan(&mut random, chunk, pos, planned)?,
            _ => return Err(()),
        };
    }
    if valid && (kind == FORTRESS || kind == STRONGHOLD) {
        // The pieces with a fresh generation context random.
        let mut random = crate::world::level::levelgen::random::Legacy::new(0);
        random.set_large_feature_seed(seed, chunk[0], chunk[1]);
        words = if kind == FORTRESS {
            super::fortress_plan::plan(&mut random, chunk)?
        } else {
            super::stronghold_plan::plan(&mut random, seed, chunk, sea_level, *params.get(2).ok_or(())?)?
        };
    }
    Ok(Some(Stub { pos, valid, rotation, extra, words }))
}

/// `RuinedPortalStructure.findGenerationPoint`'s draws, bounding box and `findSuitableY`.
fn ruined_portal(chunk: [i32; 2], level_min_y: i32, seed: i64, params: &[i32], s: &mut dyn Sampling) -> Result<([i32; 3], i32, [i32; 3]), ()> {
    let setups = usize::try_from(*params.first().ok_or(())?).map_err(|_| ())?;
    let setup_words = params.get(1..1 + setups * 3).ok_or(())?;
    let sizes = params.get(1 + setups * 3..1 + setups * 3 + 39).ok_or(())?;
    if setups == 0 {
        return Err(());
    }
    let mut random = crate::world::level::levelgen::random::Legacy::new(0);
    random.set_large_feature_seed(seed, chunk[0], chunk[1]);
    let float = |w: i32| f32::from_bits(w as u32);
    let mut setup = 0;
    if setups > 1 {
        let total = setup_words.chunks(3).fold(0.0f32, |f, w| f + float(w[2]));
        let mut g = random.next_float();
        let mut chosen = None;
        for (index, w) in setup_words.chunks(3).enumerate() {
            g -= float(w[2]) / total;
            if g < 0.0 {
                chosen = Some(index);
                break;
            }
        }
        // Java throws: leave the structure to it.
        setup = chosen.ok_or(())?;
    }
    let (placement, air_probability) = (setup_words[setup * 3], float(setup_words[setup * 3 + 1]));
    let air_pocket = if air_probability == 0.0 {
        false
    } else {
        air_probability == 1.0 || random.next_float() < air_probability
    };
    let template = if random.next_float() < 0.05 { 10 + random.next_int_bound(3) } else { random.next_int_bound(10) };
    let rotation = random.next_int_bound(4);
    let mirror = if random.next_float() < 0.5 { 0 } else { 2 };
    let size = &sizes[template as usize * 3..template as usize * 3 + 3];
    let pivot = [size[0] / 2, 0, size[2] / 2];
    let transform = crate::world::level::levelgen::decoration::template::transform;
    let (a, b) = (transform([0, 0, 0], mirror, rotation as usize, pivot), transform([size[0] - 1, size[1] - 1, size[2] - 1], mirror, rotation as usize, pivot));
    let (min_x, min_z) = (chunk[0] * 16, chunk[1] * 16);
    let lo = [a[0].min(b[0]) + min_x, a[1].min(b[1]), a[2].min(b[2]) + min_z];
    let hi = [a[0].max(b[0]) + min_x, a[1].max(b[1]), a[2].max(b[2]) + min_z];
    let ocean = placement == ON_OCEAN_FLOOR;
    let y_span = hi[1] - lo[1] + 1;
    let k = level_min_y + 15;
    // getBaseHeight at the box's centre, one below (unused in the Nether).
    let mut i = || -> Result<i32, ()> {
        let (cx, cz) = (lo[0] + (hi[0] - lo[0] + 1) / 2, lo[2] + (hi[2] - lo[2] + 1) / 2);
        Ok(if ocean { s.ocean_floor(cx, cz)? } else { s.surface(cx, cz)? } - 1)
    };
    let between = |random: &mut crate::world::level::levelgen::random::Legacy, a: i32, b: i32| random.next_int_bound(b - a + 1) + a;
    let within = |random: &mut crate::world::level::levelgen::random::Legacy, a: i32, b: i32| if a < b { random.next_int_bound(b - a + 1) + a } else { b };
    let l = match placement {
        IN_NETHER => {
            if air_pocket {
                between(&mut random, 32, 100)
            } else if random.next_float() < 0.5 {
                between(&mut random, 27, 29)
            } else {
                between(&mut random, 29, 100)
            }
        }
        IN_MOUNTAIN => {
            let m = i()? - y_span;
            within(&mut random, 70, m)
        }
        UNDERGROUND => {
            let m = i()? - y_span;
            within(&mut random, k, m)
        }
        PARTLY_BURIED => {
            let m = i()? - y_span;
            m + between(&mut random, 2, 8)
        }
        _ => i()?,
    };
    let corners = [[lo[0], lo[2]], [hi[0], lo[2]], [lo[0], hi[2]], [hi[0], hi[2]]];
    let mut columns = Vec::with_capacity(4);
    for [x, z] in corners {
        columns.push(s.column(x, z)?);
    }
    let column_min_y = s.column_min_y();
    let mut n = l;
    'scan: while n > k {
        let mut count = 0;
        for column in &columns {
            let index = i64::from(n) - i64::from(column_min_y);
            if index >= 0 && column.get(index as usize).is_some_and(|&state| s.heightmap(state, ocean)) {
                count += 1;
                if count == 3 {
                    break 'scan;
                }
            }
        }
        n -= 1;
    }
    let flags = i32::from(air_pocket) | (i32::from(mirror == 2) << 1);
    Ok(([min_x, n, min_z], rotation, [setup as i32, template, flags]))
}

/// `NetherFossilStructure.findGenerationPoint`: a random column and height,
/// then down to sea level for air over soul sand or a sturdy top.
fn nether_fossil(chunk: [i32; 2], sea_level: i32, seed: i64, params: &[i32], s: &mut dyn Sampling) -> Result<Option<[i32; 3]>, ()> {
    let (min, max) = (*params.first().ok_or(())?, *params.get(1).ok_or(())?);
    let air_n = usize::try_from(*params.get(2).ok_or(())?).map_err(|_| ())?;
    let air = params.get(3..3 + air_n).ok_or(())?;
    let floor_n = usize::try_from(*params.get(3 + air_n).ok_or(())?).map_err(|_| ())?;
    let floor = params.get(4 + air_n..4 + air_n + floor_n).ok_or(())?;
    if min > max {
        return Err(());
    }
    let mut random = crate::world::level::levelgen::random::Legacy::new(0);
    random.set_large_feature_seed(seed, chunk[0], chunk[1]);
    let x = chunk[0] * 16 + random.next_int_bound(16);
    let z = chunk[1] * 16 + random.next_int_bound(16);
    let mut l = random.next_int_bound(max - min + 1) + min;
    let column = s.column(x, z)?;
    let column_min_y = s.column_min_y();
    // NoiseColumn.getBlock: air outside the column.
    let at = |y: i32| usize::try_from(i64::from(y) - i64::from(column_min_y)).ok().and_then(|i| column.get(i).copied());
    while l > sea_level {
        let above = at(l);
        l -= 1;
        let below = at(l);
        if above.is_none_or(|state| air.contains(&state)) && below.is_some_and(|state| floor.contains(&state)) {
            break;
        }
    }
    Ok((l > sea_level).then_some([x, l, z]))
}
