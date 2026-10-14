//! `ChunkGenerator.applyBiomeDecoration`'s step loop: for every generation
//! step, the step's structures (a Java upcall), then the union of the present
//! biomes' placed features in index order, each after
//! `setFeatureSeed(decoration, index, step)`. Native programs run here over one
//! binding; every other feature is placed by Java through a sync upcall.
use super::ffi::{run_program, PlaceOutcome};
use super::program::Program;
use super::random::{gaussian_from, gaussian_words};
use super::region::{Failure, Region, Result};

/// Runs a step's structures: `[step]`.
pub(crate) const OP_STRUCTURES: i32 = 6;
/// Places feature `[step, index]` on Java's path; returns 2 when the binding no
/// longer holds and Java must finish the loop from the next feature.
pub(crate) const OP_PLACE_FEATURE: i32 = 7;
const BINDING_LOST: i32 = 2;

/// A generator's decoration plan: per step, each biome's feature indices, and
/// each feature's native program when it has one.
pub(crate) struct Plan {
    steps: usize,
    features: Vec<Step>,
}

struct Step {
    count: usize,
    /// By region biome ID: sorted feature indices.
    biomes: Vec<Box<[u16]>>,
    /// By feature index: program addresses (0 for Java), owned by Java.
    programs: Box<[usize]>,
    /// The plan slot of index 0: slots number programs across steps.
    first_slot: usize,
}

impl Plan {
    /// `[steps, feature steps, per feature step: [count, biome count, (n, index*)*]]`
    /// plus one program address per (step, index), in step order.
    pub(crate) fn decode(words: &[i32], programs: &[u64]) -> Option<Self> {
        let mut at = 0;
        let mut next = || {
            let value = *words.get(at)?;
            at += 1;
            Some(value)
        };
        let steps = usize::try_from(next()?).ok().filter(|&n| n <= 64)?;
        let feature_steps = usize::try_from(next()?).ok().filter(|&n| n <= steps)?;
        let mut features = Vec::with_capacity(feature_steps);
        let mut program_at = 0;
        for _ in 0..feature_steps {
            let count = usize::try_from(next()?).ok().filter(|&n| n <= u16::MAX as usize)?;
            let biome_count = usize::try_from(next()?).ok().filter(|&n| n <= 1 << 16)?;
            let mut biomes = Vec::with_capacity(biome_count);
            for _ in 0..biome_count {
                let n = usize::try_from(next()?).ok().filter(|&n| n <= count)?;
                let mut indices = Vec::with_capacity(n);
                for _ in 0..n {
                    indices.push(u16::try_from(next()?).ok().filter(|&i| (i as usize) < count)?);
                }
                if indices.windows(2).any(|w| w[0] >= w[1]) {
                    return None;
                }
                biomes.push(indices.into_boxed_slice());
            }
            let step_programs = programs.get(program_at..program_at + count)?.iter().map(|&p| p as usize).collect();
            program_at += count;
            features.push(Step { count, biomes, programs: step_programs, first_slot: program_at - count });
        }
        (at == words.len() && program_at == programs.len()).then_some(Self { steps, features })
    }
}

/// Where the loop stopped short: Java continues from `step`'s feature after `index`.
pub(crate) struct Resume {
    pub(crate) step: i32,
    pub(crate) index: i32,
}

/// The loop over one bound region. `biomes` are the present biomes' region IDs
/// (`possibleBiomes` only); `structures` has bit k set when step k places structures.
pub(crate) fn decorate(
    plan: &Plan,
    region: &mut Region,
    decoration_seed: i64,
    biomes: &[u32],
    structures: u64,
    origin: [i32; 3],
    counts: &mut [i64; 3],
    current: &mut [i32; 2],
    kinds: &mut [i64],
    gaussian: &mut Option<f64>,
) -> Result<Option<Resume>> {
    let mut present = Vec::new();
    for step in 0..plan.steps {
        *current = [step as i32, -1];
        if structures >> step & 1 != 0 {
            let [have, bits] = gaussian_words(*gaussian);
            let mut args = [step as i64, have, bits];
            region.sync_call(OP_STRUCTURES, &mut args)?;
            *gaussian = gaussian_from(args[1], args[2]);
        }
        let Some(features) = plan.features.get(step) else {
            continue;
        };
        // The union of the present biomes' indices, ascending (Java's sorted IntSet).
        present.clear();
        present.resize(features.count, false);
        for &biome in biomes {
            for &index in features.biomes.get(biome as usize).ok_or(Failure::Unbound)?.iter() {
                present[index as usize] = true;
            }
        }
        for index in 0..features.count {
            if !present[index] {
                continue;
            }
            *current = [step as i32, index as i32];
            let program = features.programs[index] as *const Program;
            region.slot = (features.first_slot + index) as i64;
            // SAFETY: Java keeps every planned program alive while the plan lives.
            let outcome = match unsafe { program.as_ref() } {
                Some(program) => {
                    let outcome = run_program(program, region, decoration_seed, index as i32, step as i32, origin, gaussian)?;
                    if matches!(outcome, PlaceOutcome::Placed(_)) {
                        if let Some(count) = kinds.get_mut(program.placed.feature.kind() as usize) {
                            *count += 1;
                        }
                    }
                    outcome
                }
                None => PlaceOutcome::JavaRoute,
            };
            match outcome {
                PlaceOutcome::Placed(_) => counts[0] += 1,
                PlaceOutcome::JavaRoute => {
                    counts[1] += 1;
                    let [have, bits] = gaussian_words(*gaussian);
                    let mut args = [step as i64, index as i64, have, bits];
                    let status = region.sync_call(OP_PLACE_FEATURE, &mut args)?;
                    *gaussian = gaussian_from(args[2], args[3]);
                    if status == BINDING_LOST {
                        return Ok(Some(Resume { step: step as i32, index: index as i32 }));
                    }
                }
            }
        }
    }
    Ok(None)
}
