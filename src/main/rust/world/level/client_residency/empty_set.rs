//! Frozen fastutil default long-set layout. Preserve iteration order, zero-key
//! handling, shrink thresholds and collision relocation for the public escape.
pub(super) struct EmptySet {
    pub(super) keys: Vec<u64>,
    pub(super) size: usize,
    pub(super) zero: bool,
}
fn mix(value: u64) -> usize {
    let h = value.wrapping_mul(0x9e3779b97f4a7c15); let h = h ^ (h >> 32);
    (h ^ (h >> 16)) as usize
}
impl EmptySet {
    pub(super) fn new() -> Self { Self { keys: vec![0; 32], size: 0, zero: false } }
    fn max_fill(&self) -> usize { (self.keys.len() * 3).div_ceil(4).min(self.keys.len() - 1) }
    pub(super) fn add(&mut self, value: u64) -> bool {
        if value == 0 { if self.zero { return false; } self.zero = true; }
        else {
            let mask = self.keys.len() - 1; let mut pos = mix(value) & mask;
            while self.keys[pos] != 0 { if self.keys[pos] == value { return false; } pos = (pos + 1) & mask; }
            self.keys[pos] = value;
        }
        let old_size = self.size; self.size += 1;
        if old_size >= self.max_fill() { self.rehash(self.keys.len() * 2); }
        true
    }
    pub(super) fn remove(&mut self, value: u64) -> bool {
        if value == 0 { if !self.zero { return false; } self.zero = false; }
        else {
            let mask = self.keys.len() - 1; let mut pos = mix(value) & mask;
            while self.keys[pos] != value { if self.keys[pos] == 0 { return false; } pos = (pos + 1) & mask; }
            loop {
                let last = pos; pos = (last + 1) & mask;
                loop {
                    let current = self.keys[pos];
                    if current == 0 { self.keys[last] = 0; break; }
                    let slot = mix(current) & mask;
                    if if last <= pos { last >= slot || slot > pos } else { last >= slot && slot > pos } { break; }
                    pos = (pos + 1) & mask;
                }
                if self.keys[pos] == 0 { break; }
                self.keys[last] = self.keys[pos];
            }
        }
        self.size -= 1;
        if self.keys.len() > 32 && self.size < self.max_fill() / 4 { self.rehash(self.keys.len() / 2); }
        true
    }
    fn rehash(&mut self, length: usize) {
        let mut keys = vec![0; length]; let mask = length - 1;
        for &value in self.keys.iter().rev().filter(|&&v| v != 0) {
            let mut pos = mix(value) & mask; while keys[pos] != 0 { pos = (pos + 1) & mask; } keys[pos] = value;
        }
        self.keys = keys;
    }
    pub(super) fn ordered(&self) -> Vec<u64> {
        self.zero.then_some(0).into_iter().chain(self.keys.iter().rev().copied().filter(|&v| v != 0)).collect()
    }
}
