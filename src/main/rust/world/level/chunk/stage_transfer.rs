//! Native stage/live-section handoff. Stages keep isolated packed inputs;
//! completed dense stage results become independently owned live storage.
use super::live::Owner;
use crate::world::level::levelgen::noise_fill::section::Section;

/// CPU output header: one newly owned section pointer and the original counters.
/// The caller consumes the pointer only after a successful result.
#[repr(C)]
pub(crate) struct ResultHeader {
    owner: *mut Owner,
    counts: [i32; 3],
}

/// Returns 1 with a new owner, or 2 when the stage's original compatibility
/// representation must be installed. An invalid output span returns -1.
/// # Safety
/// `output` is aligned and writable for a 24-byte header. It never overlaps the
/// stage. On success the caller must adopt/release the returned owner once.
pub(crate) unsafe fn write_result(
    section: &Section,
    limit: i32,
    global_bits: i32,
    output: *mut ResultHeader,
) -> i32 {
    if output.is_null() || output as usize % 8 != 0 {
        return -1;
    }
    let packed = section.packed();
    // i64/u64 share size/alignment and every bit pattern is valid. This borrowed
    // reinterpretation avoids another packed-word allocation before adoption.
    let words = unsafe { std::slice::from_raw_parts(packed.as_ptr().cast::<u64>(), packed.len()) };
    let Some(owner) = Owner::load(
        section.storage_bits() as usize,
        section.requested_bits() as usize,
        section.palette(),
        words,
        limit as u32,
        global_bits as usize,
    ) else {
        return 2;
    };
    unsafe {
        (*output).counts = [section.non_empty, section.ticking, section.fluid];
        (*output).owner = Box::into_raw(Box::new(owner));
    }
    1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn generated_results_keep_palette_history_and_survive_stage_release() {
        for size in [1, 16, 17, 32, 33, 64, 65, 128, 129, 256, 257, 4096] {
            let mut section = Section::new(0, 15);
            for i in 0..4096 {
                section.set(i, (i % size) as i32);
            }
            section.non_empty = 4095;
            section.ticking = 73;
            section.fluid = 155;
            let expected_words = section.packed();
            let expected_palette = section.palette().to_vec();
            let mut result = ResultHeader {
                owner: std::ptr::null_mut(),
                counts: [0; 3],
            };
            assert_eq!(unsafe { write_result(&section, 31809, 15, &mut result) }, 1);
            assert_eq!(result.counts, [4095, 73, 155]);
            let owner = unsafe { Box::from_raw(result.owner) };
            let (_, bits, palette, words) = owner.stage_snapshot(31809, 15).unwrap();
            assert_eq!(bits, section.storage_bits());
            assert_eq!(palette, expected_palette);
            assert_eq!(words, expected_words);
            drop(section);
            assert_eq!(owner.stage_snapshot(31809, 15).unwrap().3, expected_words);
        }
        assert_eq!(std::mem::size_of::<ResultHeader>(), 24);
    }
    #[test]
    fn aliases_and_invalid_spans_keep_compatibility_without_transferring_an_owner() {
        let alias = Section::load(1, 4, vec![2, 2], vec![0; 4096], 15, [0; 3]).unwrap();
        let mut result = ResultHeader {
            owner: std::ptr::null_mut(),
            counts: [17; 3],
        };
        assert_eq!(unsafe { write_result(&alias, 31809, 15, &mut result) }, 2);
        assert!(result.owner.is_null());
        assert_eq!(result.counts, [17; 3]);
        assert_eq!(
            unsafe { write_result(&alias, 31809, 15, std::ptr::null_mut()) },
            -1
        );
    }
}
