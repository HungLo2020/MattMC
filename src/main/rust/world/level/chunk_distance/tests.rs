//! Converged-state checks against a brute-force distance reference. Exact
//! call-order parity with the original Java graph is covered by the Java
//! oracle tests (`NativePlayerChunkDistancesTest`).
use super::ffi::*;
use super::graph::{as_long, INVALID_CHUNK_POS};
use super::position_map::PositionMap;
use super::PlayerDistances;

const SPAWN: usize = 0;
const TICKETS: usize = 1;

fn chebyshev(a: i64, b: i64) -> i64 {
    let dx = ((a as i32).wrapping_sub(b as i32)) as i64;
    let dz = (((a >> 32) as i32).wrapping_sub((b >> 32) as i32)) as i64;
    dx.abs().max(dz.abs())
}

/// Settled level of `position` for players at `players` with `max_distance`.
fn expected(players: &[i64], position: i64, max_distance: i32) -> i32 {
    if position == INVALID_CHUNK_POS {
        // The source sentinel never receives a level of its own.
        return max_distance + 2;
    }
    let nearest = players
        .iter()
        .map(|player| chebyshev(*player, position))
        .min()
        .unwrap_or(i64::MAX);
    if nearest <= max_distance as i64 {
        nearest as i32
    } else {
        max_distance + 2
    }
}

fn settle(distances: &mut PlayerDistances) {
    for field in [SPAWN, TICKETS] {
        distances.run_updates(field, i32::MAX).unwrap();
        assert!(!distances.field(field).has_work());
    }
}

fn assert_settled(distances: &PlayerDistances, players: &[i64], centres: &[i64], radius: i32) {
    for (field, max_distance) in [(SPAWN, 3), (TICKETS, 6)] {
        for centre in centres {
            let (x, z) = (*centre as i32, (*centre >> 32) as i32);
            for dx in -radius..=radius {
                for dz in -radius..=radius {
                    let position = as_long(x.wrapping_add(dx), z.wrapping_add(dz));
                    assert_eq!(
                        expected(players, position, max_distance),
                        distances.field(field).level(position),
                        "field {field} position {position:#x} players {players:x?}",
                    );
                }
            }
        }
    }
}

#[test]
fn single_player_settles_to_chebyshev_distance_and_vacating_restores_defaults() {
    let mut distances = PlayerDistances::new(3, 6).unwrap();
    let player = as_long(5, -7);
    distances.player_entered(player).unwrap();
    assert!(distances.field(SPAWN).has_work() && distances.field(TICKETS).has_work());
    settle(&mut distances);
    assert_settled(&distances, &[player], &[player], 9);
    assert_eq!(49, distances.field(SPAWN).changes().len());
    distances.chunk_vacated(player).unwrap();
    settle(&mut distances);
    assert_settled(&distances, &[], &[player], 9);
}

#[test]
fn random_player_sets_match_reference_including_wrap_and_source_sentinel() {
    let mut seed = 0x5eed_u64;
    let mut next = |bound: u64| {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed % bound
    };
    let anchors = [
        as_long(0, 0),
        as_long(i32::MAX, i32::MIN),
        as_long(-1, -1),
        // Neighbours of the sentinel must treat it as the source, never a node.
        as_long(1_875_065, 1_875_067),
    ];
    for anchor in anchors {
        let mut distances = PlayerDistances::new(3, 6).unwrap();
        let mut players: Vec<i64> = Vec::new();
        for step in 0..120 {
            let (x, z) = (anchor as i32, (anchor >> 32) as i32);
            let position = as_long(
                x.wrapping_add(next(9) as i32 - 4),
                z.wrapping_add(next(9) as i32 - 4),
            );
            if position == INVALID_CHUNK_POS {
                continue;
            }
            if players.contains(&position) && next(2) == 0 {
                players.retain(|player| *player != position);
                distances.chunk_vacated(position).unwrap();
            } else {
                if !players.contains(&position) {
                    players.push(position);
                }
                distances.player_entered(position).unwrap();
            }
            if step % 3 == 0 {
                settle(&mut distances);
                assert_settled(&distances, &players, &[anchor], 12);
            }
        }
    }
}

#[test]
fn budget_limits_processed_nodes_and_preserves_remaining_work() {
    let mut distances = PlayerDistances::new(3, 6).unwrap();
    distances.player_entered(as_long(0, 0)).unwrap();
    assert_eq!(0, distances.run_updates(SPAWN, 1).unwrap());
    assert_eq!(1, distances.field(SPAWN).changes().len());
    assert!(distances.field(SPAWN).has_work());
    assert!(distances.run_updates(SPAWN, i32::MAX).unwrap() < i32::MAX);
    assert!(!distances.field(SPAWN).has_work());
    // An empty queue returns the budget unchanged, as the original does.
    assert_eq!(17, distances.run_updates(SPAWN, 17).unwrap());
}

#[test]
fn ffi_reports_work_counts_and_drains_ordered_changes() {
    unsafe {
        assert_eq!(0, mattmc_player_distance_create(252, 8));
        let id = mattmc_player_distance_create(2, 4);
        assert_ne!(0, id);
        let status = mattmc_player_distance_entered(id, as_long(1, 2));
        assert_eq!(0x300, status, "both fields queue work");
        let status = mattmc_player_distance_run(id, 0, i32::MAX);
        assert_eq!(0x200, status & 0xffff_ffff, "field 0 settled; field 1 pending");
        let count = (status >> 32) as usize;
        assert_eq!(25, count);
        let mut buffer = vec![0i64; count * 2];
        assert_eq!(-1, mattmc_player_distance_drain(id, 0, buffer.as_mut_ptr(), count as i32 - 1));
        assert_eq!(count as i32, mattmc_player_distance_drain(id, 0, buffer.as_mut_ptr(), count as i32));
        assert_eq!([as_long(1, 2), 0], buffer[..2]);
        assert_eq!(0, mattmc_player_distance_drain(id, 0, buffer.as_mut_ptr(), 0));
        assert_eq!(5, mattmc_player_distance_run(id, 2, 1));
        mattmc_player_distance_release(id);
    }
}

#[test]
fn position_map_backward_shift_keeps_colliding_chains_reachable() {
    let mut map = PositionMap::with_expected(2).unwrap();
    let keys: Vec<i64> = (0..400).map(|index| index * 0x1_0000_0001 - 77).collect();
    for (index, key) in keys.iter().enumerate() {
        map.insert(*key, index as u8).unwrap();
    }
    for (index, key) in keys.iter().enumerate().step_by(3) {
        assert_eq!(Some(index as u8), map.remove(*key));
    }
    for (index, key) in keys.iter().enumerate() {
        assert_eq!(index % 3 != 0, map.contains(*key), "key {key}");
        if index % 3 != 0 {
            assert_eq!(Some(index as u8), map.get(*key));
        }
    }
    assert_eq!(keys.len() - keys.len().div_ceil(3), map.len());
}

#[test]
fn simulation_settles_to_lowest_ticket_level_plus_distance() {
    use super::graph::DistanceField;
    use super::ticket::TicketDistance;
    const ABSENT_TICKET: i32 = 45;
    let mut seed = 0x51_u64;
    let mut next = |bound: u64| {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed % bound
    };
    let mut distance = TicketDistance::new(ABSENT_TICKET, DistanceField::simulation().unwrap()).unwrap();
    // Per chunk: every simulating ticket level, as TicketStorage holds them.
    let mut tickets: Vec<(i64, Vec<i32>)> = Vec::new();
    let lowest = |levels: &Vec<i32>| levels.iter().copied().min().unwrap_or(ABSENT_TICKET);
    for step in 0..300 {
        let position = as_long(next(15) as i32 - 7, next(15) as i32 - 7);
        let index = match tickets.iter().position(|(chunk, _)| *chunk == position) {
            Some(index) => index,
            None => {
                tickets.push((position, Vec::new()));
                tickets.len() - 1
            }
        };
        let before = lowest(&tickets[index].1);
        if tickets[index].1.is_empty() || next(3) != 0 {
            let level = next(40) as i32 - 3;
            tickets[index].1.push(level);
            // addTicket notifies only when the new ticket lowers the level.
            if level < before {
                distance.update(position, level, level, true).unwrap();
            }
        } else {
            let removed = next(tickets[index].1.len() as u64) as usize;
            tickets[index].1.remove(removed);
            let after = lowest(&tickets[index].1);
            distance.update(position, after, after, false).unwrap();
        }
        if step % 7 == 0 {
            distance.run_updates(i32::MAX).unwrap();
            for dx in -12..=12 {
                for dz in -12..=12 {
                    let probe = as_long(dx, dz);
                    let best = tickets
                        .iter()
                        .map(|(chunk, levels)| lowest(levels).max(0).saturating_add(chebyshev(*chunk, probe) as i32))
                        .min()
                        .unwrap_or(i32::MAX)
                        .clamp(0, 33);
                    let expected = if best > 32 { 33 } else { best };
                    assert_eq!(expected, distance.field().level(probe), "step {step} probe {dx},{dz}");
                }
            }
        }
    }
}

#[test]
fn poi_sections_settle_to_three_dimensional_distance_from_village_centres() {
    use super::graph::section_long;
    use super::poi::PoiDistance;
    let mut seed = 0x9e1_u64;
    let mut next = |bound: u64| {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed % bound
    };
    let unpack = |position: i64| ((position >> 42) as i32, ((position << 44) >> 44) as i32, ((position << 22) >> 42) as i32);
    // Ordinary, packed-coordinate wrap (x/z 22 bits, y 20 bits) and zero anchors.
    for (ax, ay, az) in [(0, 0, 0), ((1 << 21) - 1, (1 << 19) - 1, -(1 << 21)), (-5, 3, 9)] {
        let mut distance = PoiDistance::new().unwrap();
        let mut centres: Vec<i64> = Vec::new();
        for step in 0..160 {
            let position = section_long(ax + next(9) as i32 - 4, ay + next(9) as i32 - 4, az + next(9) as i32 - 4);
            let centre = !centres.contains(&position) && next(3) != 0;
            centres.retain(|existing| *existing != position);
            if centre {
                centres.push(position);
            }
            distance.section_changed(position, centre).unwrap();
            if step % 5 == 0 {
                distance.run_updates(i32::MAX).unwrap();
                assert!(!distance.field().has_work());
                for dx in -6..=6 {
                    for dy in -6..=6 {
                        for dz in -6..=6 {
                            let probe = section_long(ax + dx, ay + dy, az + dz);
                            let (px, py, pz) = unpack(probe);
                            let nearest = centres
                                .iter()
                                .map(|centre| {
                                    let (cx, cy, cz) = unpack(*centre);
                                    // Distances wrap with the packed field widths.
                                    let wrap = |a: i32, b: i32, bits: u32| {
                                        let d = (a.wrapping_sub(b) as i64).rem_euclid(1 << bits);
                                        d.min((1 << bits) - d)
                                    };
                                    wrap(px, cx, 22).max(wrap(py, cy, 20)).max(wrap(pz, cz, 22))
                                })
                                .min()
                                .unwrap_or(i64::MAX);
                            // Propagation stops at levelCount - 2 = 5. The original's
                            // increase path stores its top level 6, so farther sections
                            // read 6 or the default 7 depending on history.
                            let level = distance.field().level(probe);
                            if nearest <= 5 {
                                assert_eq!(nearest as i32, level, "step {step} probe {dx},{dy},{dz}");
                            } else {
                                assert!(level == 6 || level == 7, "step {step} probe {dx},{dy},{dz}: {level}");
                            }
                        }
                    }
                }
            }
        }
    }
}

#[test]
fn loading_settles_to_lowest_load_ticket_plus_distance() {
    use super::graph::DistanceField;
    use super::ticket::TicketDistance;
    const MAX: i32 = 44; // ChunkLevel.MAX_LEVEL
    let mut distance = TicketDistance::new(MAX + 1, DistanceField::loading(MAX, 2_096_000).unwrap()).unwrap();
    let mut tickets: Vec<(i64, i32)> = Vec::new();
    let mut seed = 0x10ad_u64;
    let mut next = |bound: u64| {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        seed % bound
    };
    for step in 0..200 {
        let position = as_long(next(11) as i32 - 5, next(11) as i32 - 5);
        let previous = tickets.iter().find(|(chunk, _)| *chunk == position).map(|(_, level)| *level);
        tickets.retain(|(chunk, _)| *chunk != position);
        if previous.is_some() && next(2) == 0 {
            distance.update(position, MAX + 1, MAX + 1, false).unwrap();
        } else {
            let level = 20 + next(28) as i32;
            tickets.push((position, level));
            let lowered = previous.map_or(true, |old| level < old);
            distance.update(position, level, level, lowered).unwrap();
        }
        if step % 4 == 0 {
            let remaining = distance.run_updates(i32::MAX).unwrap();
            assert!(remaining <= i32::MAX);
            for dx in -30..=30 {
                for dz in -30..=30 {
                    let probe = as_long(dx, dz);
                    let best = tickets
                        .iter()
                        .map(|(chunk, level)| level.max(&0) + chebyshev(*chunk, probe) as i32)
                        .min()
                        .unwrap_or(i32::MAX)
                        .clamp(0, MAX + 1);
                    let expected = if best > MAX { MAX + 1 } else { best };
                    assert_eq!(expected, distance.field().level(probe), "step {step} probe {dx},{dz}");
                }
            }
        }
    }
}
