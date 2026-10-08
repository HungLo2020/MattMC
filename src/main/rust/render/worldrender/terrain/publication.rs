//! Static-terrain publication: which mesh (key and generation) each section's
//! solid, cutout and translucent layer currently publishes, and which rows
//! changed since the camera section graph last read them.
//!
//! A row switches to a new generation when its layer is registered, not when
//! the upload is acknowledged (Frozen parity records that timing). Every call
//! runs on the render thread. `bridge/world/terrain_publication.rs` applies the
//! changed rows to the section graph.

use std::collections::HashMap;
use std::sync::{LazyLock, Mutex, MutexGuard};

/// Row slots: solid, cutout (`CUTOUT_MIPPED`), translucent.
pub const SLOTS: usize = 3;
pub const TRANSLUCENT_SLOT: usize = 2;

/// One section's published layers.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Row {
    pub keys: [u64; SLOTS],
    pub generations: [u64; SLOTS],
    /// The translucent layer asks Rust to camera-sort its quads.
    pub translucent_camera_sorted: bool,
}

impl Row {
    fn is_empty(&self) -> bool {
        self.keys.iter().all(|key| *key == 0)
    }
}

/// One published layer, for [`Publication::replace`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Layer {
    pub section: i64,
    pub slot: usize,
    pub key: u64,
    pub generation: u64,
    pub camera_sorted: bool,
}

/// A mesh key already published by another section layer.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Collision {
    pub key: u64,
    /// The layer that holds it.
    pub section: i64,
    pub slot: usize,
}

/// The rows the section graph has not seen yet.
#[derive(Debug, PartialEq, Eq)]
pub enum Changes {
    /// The graph must drop every row and take all of these.
    Reset(Vec<(i64, Row)>),
    /// These sections changed; `None` clears one.
    Rows(Vec<(i64, Option<Row>)>),
}

#[derive(Debug)]
pub struct Publication {
    rows: HashMap<i64, Row>,
    /// Mesh key to the layer publishing it.
    by_key: HashMap<u64, (i64, usize)>,
    /// Sections changed since the last [`Self::take_changes`] (may repeat).
    dirty: Vec<i64>,
    /// The graph must be rebuilt from every row.
    reset: bool,
}

impl Default for Publication {
    fn default() -> Self {
        Self { rows: HashMap::new(), by_key: HashMap::new(), dirty: Vec::new(), reset: true }
    }
}

impl Publication {
    /// Publishes a layer's mesh. A key published by another section layer is
    /// a collision; nothing changes then.
    pub fn publish(&mut self, layer: Layer) -> Result<(), Collision> {
        if let Some(&(section, slot)) = self.by_key.get(&layer.key) {
            if (section, slot) != (layer.section, layer.slot) {
                return Err(Collision { key: layer.key, section, slot });
            }
        }
        let row = self.rows.entry(layer.section).or_default();
        let replaced = row.keys[layer.slot];
        row.keys[layer.slot] = layer.key;
        row.generations[layer.slot] = layer.generation;
        if layer.slot == TRANSLUCENT_SLOT {
            row.translucent_camera_sorted = layer.camera_sorted;
        }
        if replaced != 0 && replaced != layer.key && self.by_key.get(&replaced) == Some(&(layer.section, layer.slot)) {
            self.by_key.remove(&replaced);
        }
        self.by_key.insert(layer.key, (layer.section, layer.slot));
        self.mark(layer.section);
        Ok(())
    }

    /// Withdraws a layer's mesh, if any.
    pub fn remove(&mut self, section: i64, slot: usize) {
        if let Some(row) = self.rows.get_mut(&section) {
            let key = std::mem::take(&mut row.keys[slot]);
            row.generations[slot] = 0;
            if slot == TRANSLUCENT_SLOT {
                row.translucent_camera_sorted = false;
            }
            if key != 0 && self.by_key.get(&key) == Some(&(section, slot)) {
                self.by_key.remove(&key);
            }
            if row.is_empty() {
                self.rows.remove(&section);
            }
        }
        self.mark(section);
    }

    /// Replaces every row at once (the resource-reload commit). A duplicate
    /// key leaves everything unchanged.
    pub fn replace(&mut self, layers: &[Layer]) -> Result<(), Collision> {
        let mut by_key = HashMap::with_capacity(layers.len());
        for layer in layers {
            if let Some(&(section, slot)) = by_key.get(&layer.key) {
                if (section, slot) != (layer.section, layer.slot) {
                    return Err(Collision { key: layer.key, section, slot });
                }
            }
            by_key.insert(layer.key, (layer.section, layer.slot));
        }
        self.clear();
        for layer in layers {
            let row = self.rows.entry(layer.section).or_default();
            row.keys[layer.slot] = layer.key;
            row.generations[layer.slot] = layer.generation;
            if layer.slot == TRANSLUCENT_SLOT {
                row.translucent_camera_sorted = layer.camera_sorted;
            }
        }
        self.by_key = by_key;
        Ok(())
    }

    /// Withdraws every row; the graph is rebuilt on its next read.
    pub fn clear(&mut self) {
        self.rows.clear();
        self.by_key.clear();
        self.dirty.clear();
        self.reset = true;
    }

    /// A section's row (all zero when it publishes nothing).
    pub fn row(&self, section: i64) -> Row {
        self.rows.get(&section).copied().unwrap_or_default()
    }

    /// The changes since the last call; with `republish` (a new graph), every row.
    pub fn take_changes(&mut self, republish: bool) -> Changes {
        let reset = std::mem::replace(&mut self.reset, false) || republish;
        let dirty = std::mem::take(&mut self.dirty);
        if reset {
            return Changes::Reset(self.rows.iter().map(|(&section, &row)| (section, row)).collect());
        }
        Changes::Rows(dirty.into_iter().map(|section| (section, self.rows.get(&section).copied())).collect())
    }

    pub fn len(&self) -> usize {
        self.rows.len()
    }

    fn mark(&mut self, section: i64) {
        if !self.reset {
            self.dirty.push(section);
        }
    }
}

static PUBLICATION: LazyLock<Mutex<Publication>> = LazyLock::new(|| Mutex::new(Publication::default()));

/// The process's terrain publication.
pub fn publication() -> MutexGuard<'static, Publication> {
    PUBLICATION.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// `SectionPos.x/y/z` of a packed Minecraft section position.
pub fn section_coordinates(section: i64) -> [i32; 3] {
    [(section >> 42) as i32, (section << 44 >> 44) as i32, (section << 22 >> 42) as i32]
}

#[cfg(test)]
mod tests {
    use super::*;

    fn layer(section: i64, slot: usize, key: u64, generation: u64) -> Layer {
        Layer { section, slot, key, generation, camera_sorted: false }
    }

    fn rows(changes: Changes) -> Vec<(i64, Option<Row>)> {
        match changes {
            Changes::Rows(rows) => rows,
            Changes::Reset(_) => panic!("expected row changes"),
        }
    }

    #[test]
    fn the_first_read_is_a_reset_and_later_reads_list_changed_rows() {
        let mut p = Publication::default();
        p.publish(layer(5, 0, 50, 1)).unwrap();
        let Changes::Reset(all) = p.take_changes(false) else { panic!("expected a reset") };
        assert_eq!(all, vec![(5, Row { keys: [50, 0, 0], generations: [1, 0, 0], translucent_camera_sorted: false })]);
        p.publish(Layer { camera_sorted: true, ..layer(5, 2, 52, 3) }).unwrap();
        p.remove(9, 1);
        let changes = rows(p.take_changes(false));
        let expected = Row { keys: [50, 0, 52], generations: [1, 0, 3], translucent_camera_sorted: true };
        assert_eq!(changes, vec![(5, Some(expected)), (9, None)]);
        assert_eq!(rows(p.take_changes(false)), vec![]);
        assert!(matches!(p.take_changes(true), Changes::Reset(all) if all.len() == 1), "a new graph takes every row");
    }

    #[test]
    fn a_mesh_key_belongs_to_one_section_layer() {
        let mut p = Publication::default();
        p.publish(layer(1, 0, 10, 1)).unwrap();
        assert_eq!(p.publish(layer(2, 0, 10, 1)), Err(Collision { key: 10, section: 1, slot: 0 }));
        assert_eq!(p.publish(layer(1, 1, 10, 1)), Err(Collision { key: 10, section: 1, slot: 0 }));
        assert_eq!(p.row(2), Row::default(), "a collision changes nothing");
        // Republishing the same layer with a new generation or key is fine.
        p.publish(layer(1, 0, 10, 2)).unwrap();
        p.publish(layer(1, 0, 11, 3)).unwrap();
        p.publish(layer(2, 0, 10, 4)).unwrap();
        // Removing a layer frees only its own key.
        p.remove(1, 0);
        assert_eq!(p.publish(layer(3, 0, 10, 5)), Err(Collision { key: 10, section: 2, slot: 0 }));
        p.publish(layer(3, 0, 11, 5)).unwrap();
    }

    #[test]
    fn removing_the_last_layer_drops_the_row() {
        let mut p = Publication::default();
        p.publish(Layer { camera_sorted: true, ..layer(4, 2, 40, 1) }).unwrap();
        p.publish(layer(4, 1, 41, 1)).unwrap();
        p.remove(4, 2);
        assert_eq!(p.row(4), Row { keys: [0, 41, 0], generations: [0, 1, 0], translucent_camera_sorted: false });
        p.remove(4, 1);
        assert_eq!(p.len(), 0);
    }

    #[test]
    fn replace_is_all_or_nothing_and_resets_the_graph() {
        let mut p = Publication::default();
        p.publish(layer(1, 0, 10, 1)).unwrap();
        p.take_changes(false);
        let duplicate = [layer(2, 0, 20, 1), layer(3, 1, 20, 1)];
        assert_eq!(p.replace(&duplicate), Err(Collision { key: 20, section: 2, slot: 0 }));
        assert_eq!(p.row(1).keys, [10, 0, 0], "a rejected replace keeps the old rows");
        assert_eq!(rows(p.take_changes(false)), vec![]);
        p.replace(&[layer(2, 0, 20, 1), layer(2, 2, 21, 1)]).unwrap();
        assert_eq!(p.row(1), Row::default());
        let Changes::Reset(all) = p.take_changes(false) else { panic!("expected a reset") };
        assert_eq!(all.len(), 1);
        assert_eq!(p.publish(layer(5, 0, 21, 2)), Err(Collision { key: 21, section: 2, slot: 2 }));
    }

    #[test]
    fn clear_drops_pending_changes_for_a_reset() {
        let mut p = Publication::default();
        p.take_changes(false);
        p.publish(layer(1, 0, 10, 1)).unwrap();
        p.clear();
        assert_eq!(p.take_changes(false), Changes::Reset(vec![]));
        assert_eq!(p.publish(layer(2, 0, 10, 1)), Ok(()), "clear frees every key");
    }

    #[test]
    fn section_coordinates_match_section_pos() {
        // SectionPos.asLong(x, y, z) = (x & 0x3FFFFF) << 42 | (y & 0xFFFFF) | (z & 0x3FFFFF) << 20.
        let pack = |x: i64, y: i64, z: i64| ((x & 0x3F_FFFF) << 42) | (y & 0xF_FFFF) | ((z & 0x3F_FFFF) << 20);
        assert_eq!(section_coordinates(pack(-3, 7, 12)), [-3, 7, 12]);
        assert_eq!(section_coordinates(pack(2_000_000, -4, -2_000_000)), [2_000_000, -4, -2_000_000]);
    }
}
