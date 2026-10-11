//! `Stitcher`: packs sprites into a power-of-two atlas. Sprites sort by
//! padded height, then padded width (both descending), then name in
//! `ResourceLocation` order; each goes into the first region that fits, or
//! the atlas grows along its shorter axis. Placements are gathered by a
//! depth-first walk of the regions, the order Java fills its sprite map in.

use crate::core::id;

/// A sprite to place: its index in the caller's list, size and name.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Entry<'a> {
    pub(crate) index: usize,
    pub(crate) width: u32,
    pub(crate) height: u32,
    pub(crate) namespace: &'a str,
    pub(crate) path: &'a str,
}

#[derive(Clone, Copy, Debug)]
struct Holder<'a> {
    entry: Entry<'a>,
    width: u32,
    height: u32,
}

/// `smallestFittingMinTexel`: rounded up to a multiple of `2^mip`.
fn padded(size: u32, mip: u32) -> u32 {
    ((size >> mip) + u32::from(size & ((1 << mip) - 1) != 0)) << mip
}

/// `Mth.smallestEncompassingPowerOfTwo` (Java's bit trick gives 0 for 0).
fn power_of_two(value: u32) -> u32 {
    let mut value = value.wrapping_sub(1);
    value |= value >> 1;
    value |= value >> 2;
    value |= value >> 4;
    value |= value >> 8;
    value |= value >> 16;
    value.wrapping_add(1)
}

struct Region {
    x: u32,
    y: u32,
    width: u32,
    height: u32,
    sub_slots: Option<Vec<Region>>,
    holder: Option<usize>,
}

impl Region {
    fn new(x: u32, y: u32, width: u32, height: u32) -> Region {
        Region { x, y, width, height, sub_slots: None, holder: None }
    }

    fn add(&mut self, holder: usize, width: u32, height: u32) -> bool {
        if self.holder.is_some() || width > self.width || height > self.height {
            return false;
        }
        if width == self.width && height == self.height {
            self.holder = Some(holder);
            return true;
        }
        if self.sub_slots.is_none() {
            let mut slots = vec![Region::new(self.x, self.y, width, height)];
            let right = self.width - width;
            let below = self.height - height;
            if below > 0 && right > 0 {
                let a = self.height.max(right);
                let b = self.width.max(below);
                if a >= b {
                    slots.push(Region::new(self.x, self.y + height, width, below));
                    slots.push(Region::new(self.x + width, self.y, right, self.height));
                } else {
                    slots.push(Region::new(self.x + width, self.y, right, height));
                    slots.push(Region::new(self.x, self.y + height, self.width, below));
                }
            } else if right == 0 {
                slots.push(Region::new(self.x, self.y + height, width, below));
            } else if below == 0 {
                slots.push(Region::new(self.x + width, self.y, right, height));
            }
            self.sub_slots = Some(slots);
        }
        self.sub_slots.as_mut().unwrap().iter_mut().any(|slot| slot.add(holder, width, height))
    }

    fn walk(&self, output: &mut Vec<(usize, u32, u32)>) {
        if let Some(holder) = self.holder {
            output.push((holder, self.x, self.y));
        } else if let Some(slots) = &self.sub_slots {
            for slot in slots {
                slot.walk(output);
            }
        }
    }
}

/// A finished layout: atlas size and each placement (entry index, x, y) in
/// gather order.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Layout {
    pub(crate) width: u32,
    pub(crate) height: u32,
    pub(crate) placements: Vec<(usize, u32, u32)>,
}

/// `StitcherException`: the sprite that did not fit.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct DoesNotFit {
    pub(crate) index: usize,
}

/// `new Stitcher(max, max, mip)`, every entry registered, then `stitch()`
/// and `gatherSprites`.
pub(crate) fn stitch(entries: &[Entry<'_>], max_width: u32, max_height: u32, mip: u32) -> Result<Layout, DoesNotFit> {
    let mut holders: Vec<Holder<'_>> = entries.iter().map(|&entry| Holder { entry, width: padded(entry.width, mip), height: padded(entry.height, mip) }).collect();
    // List.sort is stable, as is sort_by.
    holders.sort_by(|a, b| {
        b.height
            .cmp(&a.height)
            .then(b.width.cmp(&a.width))
            .then_with(|| id::compare_locations((a.entry.namespace, a.entry.path), (b.entry.namespace, b.entry.path)))
    });
    let mut storage: Vec<Region> = Vec::new();
    let (mut storage_x, mut storage_y) = (0u32, 0u32);
    for (position, holder) in holders.iter().enumerate() {
        if storage.iter_mut().any(|region| region.add(position, holder.width, holder.height)) {
            continue;
        }
        // expand
        let current_x = power_of_two(storage_x);
        let current_y = power_of_two(storage_y);
        let grown_x = power_of_two(storage_x + holder.width);
        let grown_y = power_of_two(storage_y + holder.height);
        let fits_x = grown_x <= max_width;
        let fits_y = grown_y <= max_height;
        if !fits_x && !fits_y {
            return Err(DoesNotFit { index: holder.entry.index });
        }
        let changes_x = fits_x && current_x != grown_x;
        let changes_y = fits_y && current_y != grown_y;
        let grow_x = if changes_x ^ changes_y { changes_x } else { fits_x && current_x <= current_y };
        let mut region = if grow_x {
            if storage_y == 0 {
                storage_y = grown_y;
            }
            let region = Region::new(storage_x, 0, grown_x - storage_x, storage_y);
            storage_x = grown_x;
            region
        } else {
            let region = Region::new(0, storage_y, storage_x, grown_y - storage_y);
            storage_y = grown_y;
            region
        };
        region.add(position, holder.width, holder.height);
        storage.push(region);
    }
    let mut gathered = Vec::with_capacity(holders.len());
    for region in &storage {
        region.walk(&mut gathered);
    }
    let placements = gathered.into_iter().map(|(position, x, y)| (holders[position].entry.index, x, y)).collect();
    Ok(Layout { width: storage_x, height: storage_y, placements })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn entry(index: usize, width: u32, height: u32, path: &'static str) -> Entry<'static> {
        Entry { index, width, height, namespace: "minecraft", path }
    }

    #[test]
    fn packs_like_java() {
        assert_eq!(16, padded(15, 4));
        assert_eq!(16, padded(16, 4));
        assert_eq!(0, power_of_two(0));
        assert_eq!(32, power_of_two(17));
        let entries = [entry(0, 16, 16, "b"), entry(1, 16, 16, "a"), entry(2, 32, 16, "c"), entry(3, 16, 32, "d")];
        let layout = stitch(&entries, 1024, 1024, 0).unwrap();
        // Tallest first (d), then widest (c), then by name (a before b).
        // d fills the first 16x32 region; the atlas grows to 64 wide for c,
        // whose region splits into (48,0,16,16) for a and (16,16,48,16) for b.
        assert_eq!(Layout { width: 64, height: 32, placements: vec![(3, 0, 0), (2, 16, 0), (1, 48, 0), (0, 16, 16)] }, layout);
        assert_eq!(Err(DoesNotFit { index: 0 }), stitch(&[entry(0, 64, 64, "big")], 32, 32, 0));
    }
}
