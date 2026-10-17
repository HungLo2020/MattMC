//! Java hash codes and `java.util.HashMap` iteration order, for ports whose
//! observable behavior follows a Java map's iteration (dependency sorting,
//! first-match lookups).
//!
//! A `HashMap` (or `HashSet`) iterates its table buckets in index order.
//! Within a bucket, `put` appends a new key and `computeIfAbsent` (also
//! `compute`, `merge`) prepends it; resizes keep each bucket's order, so a
//! bucket iterates in insertion order, or reverse insertion order.
//! Buckets converted to trees (eight or more colliding keys in a table of at
//! least 64) are not modeled; identifier keys never collide that densely.

/// `String.hashCode` over UTF-16 code units.
pub(crate) fn string_hash(text: &str) -> i32 {
    text.encode_utf16().fold(0i32, |hash, unit| hash.wrapping_mul(31).wrapping_add(i32::from(unit)))
}

/// `ResourceLocation.hashCode`: `31 * namespace.hashCode() + path.hashCode()`.
pub(crate) fn location_hash(namespace: &str, path: &str) -> i32 {
    string_hash(namespace).wrapping_mul(31).wrapping_add(string_hash(path))
}

/// `HashMap.hash`: the high half folded into the low half.
fn spread(hash: i32) -> u32 {
    let hash = hash as u32;
    hash ^ (hash >> 16)
}

/// `HashMap`'s table size after `count` insertions from `initial` buckets
/// (resizing when the size exceeds three quarters of the table).
pub(crate) fn table_size(initial: usize, count: usize) -> usize {
    let mut table = initial.max(1).next_power_of_two();
    while count > table * 3 / 4 {
        table *= 2;
    }
    table
}

/// How keys entered the map.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Insertion {
    /// `put` / `add`: appended to the bucket.
    Put,
    /// `computeIfAbsent`: prepended to the bucket.
    ComputeIfAbsent,
}

/// The iteration order of distinct keys inserted in order into a `HashMap`
/// whose first table has `initial` buckets (16 for `new HashMap()`): indices
/// into `hashes`.
pub(crate) fn iteration_order(hashes: &[i32], initial: usize, insertion: Insertion) -> Vec<usize> {
    iteration_order_in(hashes, table_size(initial, hashes.len()), insertion)
}

/// The iteration order of `hashes` (in insertion order) in a table of
/// `table` buckets: for maps whose table grew larger than their final
/// size because entries were removed (`table_size` of the peak size).
pub(crate) fn iteration_order_in(hashes: &[i32], table: usize, insertion: Insertion) -> Vec<usize> {
    let mask = (table - 1) as u32;
    let mut order: Vec<usize> = (0..hashes.len()).collect();
    match insertion {
        Insertion::Put => order.sort_by_key(|&index| (spread(hashes[index]) & mask, index)),
        Insertion::ComputeIfAbsent => order.sort_by_key(|&index| (spread(hashes[index]) & mask, std::cmp::Reverse(index))),
    }
    order
}

/// The first table of `new HashMap(capacity)` (`tableSizeFor`).
pub(crate) fn table_for_capacity(capacity: usize) -> usize {
    capacity.max(1).next_power_of_two()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hashes_and_orders_match_java() {
        // "minecraft".hashCode() and new ResourceLocation("minecraft", "stone").hashCode().
        assert_eq!(695_073_197, string_hash("minecraft"));
        assert_eq!(109_770_853, string_hash("stone"));
        assert_eq!(31i32.wrapping_mul(695_073_197).wrapping_add(109_770_853), location_hash("minecraft", "stone"));
        assert_eq!(0, string_hash(""));
        // Buckets 2, 1, 1: bucket 1's keys first, in insertion order.
        assert_eq!(vec![1, 2, 0], iteration_order(&[2, 17, 1], 16, Insertion::Put));
        // Thirteen keys resize the table to 32: 16 and 0 then differ.
        let thirteen: Vec<i32> = (0..13).map(|key| if key == 0 { 16 } else { key }).collect();
        assert_eq!(0, iteration_order(&thirteen, 16, Insertion::Put)[12]);
        assert_eq!(vec![2, 1, 0], iteration_order(&[2, 17, 1], 16, Insertion::ComputeIfAbsent));
        assert_eq!(32, table_size(16, 13));
        assert_eq!(8, table_size(4, 4));
        assert_eq!(4, table_for_capacity(3));
    }
}
