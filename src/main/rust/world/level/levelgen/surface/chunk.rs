//! The SURFACE stage over Rust-owned chunk storage ([`ProtoStorage`]): columns
//! run the existing evaluator over the storage, and each completed block is
//! written as `BlockColumn.setBlock` writes it (the chunk's `setBlockState`,
//! then a post-processing mark for fluids). Java callbacks still answer
//! non-native conditions; `steep` is answered here from the storage's
//! heightmap, once per column, as its shared lazy Java condition caches it.
use super::evaluator::surface_step;
use super::frame::*;
use crate::world::level::levelgen::noise_fill::section::ENTRIES;
use crate::world::level::levelgen::noise_fill::{FLAG_AIR, FLAG_FLUID};
use crate::world::level::levelgen::proto_chunk::{self, ProtoStorage};

pub(crate) struct Biomes {
    pub seed: i64,
    pub qx: i32,
    pub qy: i32,
    pub qz: i32,
    pub size_x: usize,
    pub size_y: usize,
    pub size_z: usize,
    pub ids: Vec<i32>,
}

pub(crate) struct SurfaceChunk {
    /// The chunk's storage, which Java keeps alive while this stage runs.
    storage: *mut ProtoStorage,
    default_block: i32,
    biomes: Option<Biomes>,
    steep: Vec<bool>,
    // The current column.
    x: i32,
    z: i32,
    count: usize,
    committed: usize,
    column_flags: Vec<i32>,
    output: Vec<i32>,
    column_biomes: Vec<i32>,
    /// Java's `context.steep` is one lazy condition shared by every steep slot.
    steep_answer: i8,
}

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    Input,
    Evaluation(i32),
    State(i32),
}

impl From<proto_chunk::Error> for Error {
    fn from(error: proto_chunk::Error) -> Self {
        match error {
            proto_chunk::Error::Input => Error::Input,
            proto_chunk::Error::State(state) => Error::State(state),
        }
    }
}

impl SurfaceChunk {
    /// # Safety
    /// `storage` is live and used only through this stage while it runs.
    pub(crate) unsafe fn new(storage: *mut ProtoStorage, default_block: i32, biomes: Option<Biomes>, steep: Vec<bool>) -> Result<Self, Error> {
        if storage.is_null() {
            return Err(Error::Input);
        }
        if let Some(b) = &biomes {
            if b.ids.len() != b.size_x * b.size_y * b.size_z {
                return Err(Error::Input);
            }
        }
        Ok(Self {
            storage,
            default_block,
            biomes,
            steep,
            x: 0,
            z: 0,
            count: 0,
            committed: 0,
            column_flags: Vec::new(),
            output: Vec::new(),
            column_biomes: Vec::new(),
            steep_answer: -1,
        })
    }

    fn storage(&self) -> &ProtoStorage {
        unsafe { &*self.storage }
    }

    fn storage_mut(&mut self) -> &mut ProtoStorage {
        unsafe { &mut *self.storage }
    }

    /// `BlockColumn.setBlock` of buildSurface: the chunk's setBlockState, then
    /// (inside the build height) a post-processing mark for any fluid.
    fn set_block(&mut self, x: i32, y: i32, z: i32, state: i32) -> Result<(), Error> {
        let storage = self.storage_mut();
        if y < storage.min_y() || y >= storage.min_y() + 16 * storage.section_count() as i32 {
            return Ok(());
        }
        let flags = storage.flag(state)?;
        storage.set_block_state(x, y, z, state)?;
        if flags & FLAG_FLUID != 0 {
            storage.mark(x, y, z);
        }
        Ok(())
    }

    /// Starts a column as `NativeSurface.column` prepares one: flags scanned
    /// from the sections and quart biomes selected from the table.
    pub(crate) fn begin(&mut self, x: i32, z: i32, top: i32, uses_biomes: bool) -> Result<usize, Error> {
        // The storage is separate memory: its borrow does not hold `self`.
        let storage: &ProtoStorage = unsafe { &*self.storage };
        let min_y = storage.min_y();
        let count = top - min_y + 1;
        if count <= 0 {
            self.count = 0;
            return Ok(0);
        }
        let count = count as usize;
        if count > ENTRIES {
            return Err(Error::Input);
        }
        self.x = x;
        self.z = z;
        self.count = count;
        self.committed = count;
        self.column_flags.resize(count, 0);
        self.output.clear();
        self.output.resize(count, -1);
        self.column_biomes.clear();
        self.column_biomes.resize(count, 0);
        self.steep_answer = -1;
        let default_flags = storage.flag(self.default_block)?;
        let default_flag = if default_flags & FLAG_AIR != 0 { 0 } else if default_flags & FLAG_FLUID != 0 { 1 } else { 3 };
        let mut y = 0;
        while y < count {
            let absolute = min_y + y as i32;
            let end = count.min(y + 16 - (absolute & 15) as usize);
            match storage.section_at(absolute) {
                Some(section) if section.non_empty != 0 => {
                    for at in y..end {
                        let world_y = min_y + at as i32;
                        let state = section.get((((world_y & 15) << 8) | ((z & 15) << 4) | (x & 15)) as usize);
                        let flags = storage.flag(state)?;
                        self.column_flags[at] = if state == self.default_block {
                            default_flag
                        } else if flags & FLAG_AIR != 0 {
                            0
                        } else if flags & FLAG_FLUID != 0 {
                            1
                        } else {
                            2
                        };
                    }
                }
                _ => self.column_flags[y..end].fill(0),
            }
            y = end;
        }
        if uses_biomes {
            let b = self.biomes.as_ref().ok_or(Error::Input)?;
            let mut indices = vec![0i32; count];
            let status = unsafe {
                crate::world::level::biome::fiddled_distance::surface_biomes(b.seed, x, z, min_y, count as i32, indices.as_mut_ptr())
            };
            if status != 0 {
                return Err(Error::Evaluation(status));
            }
            // NativeSurface.prepareBiomes: quart (base + column, baseY + index % qCount).
            let base_y = (min_y - 2) >> 2;
            let q_count = (((min_y + count as i32 - 3) >> 2) - base_y + 2) as usize;
            let (base_x, base_z) = ((x - 2) >> 2, (z - 2) >> 2);
            for (at, index) in indices.iter().enumerate() {
                let index = *index as usize;
                let column = (index / q_count) as i32;
                let qx = base_x + (column >> 1) - b.qx;
                let qy = base_y + (index % q_count) as i32 - b.qy;
                let qz = base_z + (column & 1) - b.qz;
                if qx < 0 || qy < 0 || qz < 0 || qx as usize >= b.size_x || qy as usize >= b.size_y || qz as usize >= b.size_z {
                    return Err(Error::Input);
                }
                self.column_biomes[at] = b.ids[(qx as usize * b.size_z + qz as usize) * b.size_y + qy as usize];
            }
        }
        Ok(count)
    }

    /// Runs the column until it finishes (1) or needs a Java answer (2),
    /// committing completed blocks first, as `NativeSurface.column` does.
    pub(crate) fn run(&mut self, program: &[i32], frame: &mut [i32], cache: &mut [i32], secondary: f64) -> Result<i32, Error> {
        loop {
            let status = unsafe {
                surface_step(program.as_ptr(), program.len() as i32, self.column_flags.as_ptr(), self.output.as_mut_ptr(),
                    self.column_biomes.as_ptr(), self.count as i32, frame.as_mut_ptr(), cache.as_mut_ptr(), secondary)
            };
            if status < 0 {
                return Err(Error::Evaluation(status));
            }
            // Commit only completed blocks: a request sees prior writes, never
            // the current rule's undecided result.
            let boundary = (frame[Y_INDEX] + 1).max(0) as usize;
            for y in (boundary..self.committed).rev() {
                if self.output[y] >= 0 {
                    let state = self.output[y];
                    let min_y = self.storage().min_y();
                    self.set_block(self.x, min_y + y as i32, self.z, state)?;
                }
            }
            self.committed = boundary;
            match status {
                1 => return Ok(1),
                2 => {
                    let request = frame[REQUEST];
                    if request >= 0 && self.steep.get(request as usize).copied().unwrap_or(false) {
                        if self.steep_answer < 0 {
                            self.steep_answer = self.steep() as i8;
                        }
                        frame[ANSWER] = self.steep_answer as i32;
                        frame[ANSWER_READY] = 1;
                        continue;
                    }
                    return Ok(2);
                }
                _ => continue,
            }
        }
    }

    /// `SurfaceRules.Context.SteepMaterialCondition.compute` on the storage's heightmap.
    fn steep(&self) -> bool {
        let (i, j) = (self.x & 15, self.z & 15);
        let height = |x: i32, z: i32| self.storage().height(x, z);
        let (k, l) = ((j - 1).max(0), (j + 1).min(15));
        let (m, n) = (height(i, k), height(i, l));
        if n >= m + 4 {
            return true;
        }
        let (o, p) = ((i - 1).max(0), (i + 1).min(15));
        height(o, j) >= height(p, j) + 4
    }
}
