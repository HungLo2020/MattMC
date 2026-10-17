//! Block-light sources flow from retained world sections straight to pending work.
use super::{block_pos, increase_from_emission, work::Queues};
use crate::content::block::{BlockRegistry, StateFlags, StateId};
use crate::world::level::chunk::inputs::{Chunk, Sections};
use std::ffi::c_void;

fn seed(
    sections: Sections<'_>,
    registry: &BlockRegistry,
    queues: &Queues,
    min_y: i32,
    min_x: i32,
    min_z: i32,
) -> Result<usize, ()> {
    // Reject every unsupported palette before changing pending work. Unused
    // palette entries matter too: Java's maybeHas visits them before scanning.
    for section in 0..sections.len() {
        if !sections.block(section).with_state_reader(|reader| {
            reader.all_states(|id| {
                (id as usize) < registry.state_count()
                    && !registry
                        .flags(StateId(id as u16))
                        .contains(StateFlags::CUSTOM)
            })
        }) {
            return Err(());
        }
    }
    let mut pending = queues.data.lock().unwrap();
    if pending[1].len() > i32::MAX as usize - sections.len() * 4096 * 2 {
        return Err(());
    }
    let mut emitted = 0;
    for section in 0..sections.len() {
        sections.block(section).with_state_reader(|reader| {
            if reader.all_states(|id| registry.emission(StateId(id as u16)) == 0) {
                return;
            }
            reader.for_each_state(|index, id| {
                let id = StateId(id as u16);
                let emission = registry.emission(id);
                if emission == 0 {
                    return;
                }
                let position = block_pos(
                    min_x.wrapping_add((index & 15) as i32),
                    min_y.wrapping_add((section * 16 + (index >> 8)) as i32),
                    min_z.wrapping_add(((index >> 4) & 15) as i32),
                );
                let entry = increase_from_emission(
                    i32::from(emission),
                    registry.flags(id).contains(StateFlags::LIGHT_EMPTY_SHAPE),
                );
                pending[1].extend([position, entry as i64]);
                emitted += 1;
            });
        });
    }
    queues.publish(&pending);
    Ok(emitted)
}

/// Returns the number of sources queued, -2 to use contextual Java enumeration,
/// or -1 for an invalid ABI. No Java callback runs while native locks are held.
/// # Safety
/// Chunk and queue leases remain live; the original chunk mutation exclusion
/// applies. Coordinates have Java wrapping semantics. No pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_block_sources(
    chunk: *const Chunk,
    owner: *const c_void,
    min_x: i32,
    min_z: i32,
) -> i32 {
    let Some(chunk) = chunk.as_ref() else {
        return -1;
    };
    if owner.is_null() {
        return -1;
    }
    let Some(registry) = crate::content::block::installed() else {
        return -2;
    };
    let queues = super::work::retain(owner.cast());
    seed(
        Sections::Retained(chunk),
        registry,
        &queues,
        chunk.min_y,
        min_x,
        min_z,
    )
    .map_or(-2, |count| count as i32)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::content::block::{Builder, StateFacts};
    use crate::world::level::chunk::live::Owner;
    fn registry() -> BlockRegistry {
        let mut builder = Builder::new();
        builder
            .block("air", &[], 0, vec![StateFacts::default()])
            .unwrap();
        builder
            .block(
                "emitter",
                &[],
                0,
                vec![StateFacts {
                    emission: 13,
                    flags: StateFlags(StateFlags::LIGHT_EMPTY_SHAPE.0),
                    ..StateFacts::default()
                }],
            )
            .unwrap();
        builder
            .block(
                "custom",
                &[],
                0,
                vec![StateFacts {
                    flags: StateFlags(StateFlags::CUSTOM.0),
                    ..StateFacts::default()
                }],
            )
            .unwrap();
        builder.finish(1, vec![0]).unwrap()
    }
    #[test]
    fn sources_append_in_section_y_z_x_order_with_exact_entry_bits() {
        let mut words = vec![0u64; 256];
        for index in [0usize, 15, 16, 256, 4095] {
            words[index / 16] |= 1 << ((index % 16) * 4);
        }
        let first = Owner::load(4, 4, &[0, 1], &words, 3, 15).unwrap();
        let second = Owner::load(0, 0, &[1], &[], 3, 15).unwrap();
        let queues = Queues::default();
        queues.data.lock().unwrap()[1].extend([99, 100]);
        assert_eq!(
            seed(
                Sections::Blocks(&[&first, &second], None),
                &registry(),
                &queues,
                -64,
                -16,
                32
            ),
            Ok(4101)
        );
        let pending = queues.take();
        assert_eq!(
            &pending[1].iter().copied().take(2).collect::<Vec<_>>(),
            &[99, 100]
        );
        let entry = increase_from_emission(13, true) as i64;
        let pairs: Vec<_> = pending[1].iter().copied().skip(2).collect();
        for (at, index) in [0usize, 15, 16, 256, 4095].into_iter().enumerate() {
            assert_eq!(
                &pairs[at * 2..at * 2 + 2],
                &[
                    block_pos(
                        -16 + (index & 15) as i32,
                        -64 + (index >> 8) as i32,
                        32 + ((index >> 4) & 15) as i32
                    ),
                    entry
                ]
            );
        }
        assert_eq!(&pairs[10..12], &[block_pos(-16, -48, 32), entry]);
        assert_eq!(&pairs[pairs.len() - 2..], &[block_pos(-1, -33, 47), entry]);
    }
    #[test]
    fn unused_custom_palette_declines_without_a_partial_seed_prefix() {
        let first = Owner::load(0, 0, &[1], &[], 3, 15).unwrap();
        let second = Owner::load(4, 4, &[0, 2], &[0; 256], 3, 15).unwrap();
        let queues = Queues::default();
        queues.data.lock().unwrap()[1].extend([99, 100]);
        assert_eq!(
            seed(
                Sections::Blocks(&[&first, &second], None),
                &registry(),
                &queues,
                0,
                0,
                0
            ),
            Err(())
        );
        assert_eq!(
            queues.take()[1],
            std::collections::VecDeque::from([99, 100])
        );
    }
}
