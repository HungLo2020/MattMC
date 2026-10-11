//! Validate packed palette indices once per word, ignoring final-word padding.

/// Callers have already checked the supported bit width and exact word count.
pub(crate) fn valid_indices(words: &[u64], bits: usize, cells: usize, bound: usize) -> bool {
    if bits == 0 {
        return bound != 0;
    }
    let per_word = 64 / bits;
    let mask = (1u64 << bits) - 1;
    for (index, &word) in words.iter().enumerate() {
        let fields = per_word.min(cells - index * per_word);
        let mut value = word;
        for _ in 0..fields {
            if (value & mask) as usize >= bound {
                return false;
            }
            value >>= bits;
        }
    }
    true
}
