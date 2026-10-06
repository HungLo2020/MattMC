//! Rust-owned cell traversal of the NOISE fill. The fill keeps both
//! interpolation slices from the noise router, builds each cell's interpolator
//! corners (`selectCellYZ`), evaluates the cell density cache with the same
//! validated cell program `NativeCellDensity` runs, copies the ore vein corners
//! and fills the cell, in `doFill`'s order. Java answers only aquifer material
//! requests and fills a slice the router declines.
use crate::world::level::levelgen::aquifer::substance::OwnedAquifer;
use super::{Cell, Corners, Error, NoiseFill};
use crate::world::level::levelgen::density::cell::density_cell_array;
use crate::world::level::levelgen::router::Router;
use crate::world::level::levelgen::synth::State;

pub(crate) struct Layout {
    pub interpolators: usize,
    pub columns: usize,
    pub points: usize,
    pub min_cell_y: i32,
    pub min_x: i32,
    pub min_z: i32,
    pub width: i32,
    pub height: i32,
}

pub(crate) struct Traversal {
    router: *mut Router,
    program: *const u8,
    inputs: Vec<usize>,
    ore: Option<[usize; 3]>,
    layout: Layout,
    /// Low and high X slices: interpolator, then Z column, then corner Y.
    slices: [Vec<f64>; 2],
    density: Vec<f64>,
    frame: Vec<f64>,
    /// Structure terrain adjustment added to each cell density, as Java's cell
    /// cache adds its Beardifier: packed geometry and the shared kernel.
    beardifier: Option<(Vec<i32>, *const f32)>,
    beard: Vec<f64>,
    /// The next cell of the current X column: Z-major, Y descending.
    cursor: usize,
    /// The cursor's cell stopped for materials; its densities are current.
    pending: bool,
    /// The chunk's aquifer, when Rust owns its state for the fill: cells
    /// needing batch materials get them here instead of from Java.
    aquifer: Option<OwnedAquifer>,
    native_materials: Vec<i32>,
}

/// Where a column of cells stopped.
pub(crate) enum Step {
    Done,
    /// The cell at (x, y, z) needs aquifer batch materials for its densities.
    Materials([i32; 3]),
}

impl Traversal {
    /// # Safety
    /// `router` outlives the traversal and is used only through it while bound;
    /// `program` is a live immutable cell program accepted by density_validate
    /// with one cell input per entry of `inputs`.
    pub(crate) unsafe fn new(router: *mut Router, program: *const u8, inputs: Vec<usize>, ore: Option<[usize; 3]>, layout: Layout) -> Self {
        let size = layout.interpolators * layout.columns * layout.points;
        let cell = (layout.width * layout.width * layout.height) as usize;
        let frame = vec![0.; inputs.len() * 8];
        Traversal { router, program, inputs, ore, layout, slices: [vec![0.; size], vec![0.; size]], density: vec![0.; cell], frame, beardifier: None, beard: vec![0.; cell],
            cursor: 0, pending: false, aquifer: None, native_materials: vec![0; cell * 2] }
    }

    /// Hands the chunk's aquifer state to the traversal for the rest of the fill.
    pub(crate) fn set_aquifer(&mut self, aquifer: OwnedAquifer) {
        self.aquifer = Some(aquifer);
    }

    pub(crate) fn aquifer(&self) -> Option<&OwnedAquifer> {
        self.aquifer.as_ref()
    }

    /// # Safety
    /// `kernel` holds `KERNEL_SIZE` floats that outlive the traversal.
    pub(crate) unsafe fn set_beardifier(&mut self, geometry: Vec<i32>, kernel: *const f32) {
        self.beardifier = Some((geometry, kernel));
    }

    pub(crate) fn slice_len(&self) -> usize {
        self.slices[0].len()
    }

    pub(crate) fn density(&self) -> &[f64] {
        &self.density
    }

    /// Fills the low or high slice at block X; false when Java must upload it.
    pub(crate) fn fill_slice(&mut self, high: bool, x: i32) -> bool {
        let router = unsafe { &mut *self.router };
        router.slice(x, &mut self.slices[high as usize]).is_ok()
    }

    pub(crate) fn upload(&mut self, high: bool, values: &[f64]) {
        self.slices[high as usize].copy_from_slice(values);
    }

    /// `copyCellCorners` of one interpolator after `selectCellYZ(cellY, cellZ)`.
    fn corners(&self, interpolator: usize, cell_z: usize, cell_y: usize) -> Corners {
        let (low, high) = (&self.slices[0], &self.slices[1]);
        let l = &self.layout;
        let at = |slice: &[f64], column: usize, y: usize| slice[(interpolator * l.columns + column) * l.points + y];
        let (j, i) = (cell_z, cell_y);
        Corners([
            at(low, j, i),
            at(high, j, i),
            at(low, j, i + 1),
            at(high, j, i + 1),
            at(low, j + 1, i),
            at(high, j + 1, i),
            at(low, j + 1, i + 1),
            at(high, j + 1, i + 1),
        ])
    }

    /// Runs the column of cells at cell X from the cursor. `materials` belong
    /// to the cell the previous call stopped at. When done, the high slice
    /// becomes the low one, as `swapSlices` does.
    pub(crate) fn cells(&mut self, fill: &mut NoiseFill, cell_x: i32, mut materials: Option<&[i32]>, gap: *const State) -> Result<Step, Error> {
        let rows = self.layout.points - 1;
        let total = (self.layout.columns - 1) * rows;
        let (width, height) = (self.layout.width, self.layout.height);
        let size = self.density.len();
        while self.cursor < total {
            let cell_z = self.cursor / rows;
            let cell_y = rows - 1 - self.cursor % rows;
            for k in 0..self.inputs.len() {
                let corners = self.corners(self.inputs[k], cell_z, cell_y);
                self.frame[k * 8..k * 8 + 8].copy_from_slice(&corners.0);
            }
            // A resumed cell already holds its densities (and Beardifier term).
            let resumed = std::mem::take(&mut self.pending);
            let mut start = if resumed { size } else { 0 };
            while start < size {
                let count = (size - start).min(256);
                let visit = unsafe {
                    density_cell_array(self.program, self.frame.as_ptr(), self.density.as_mut_ptr().add(start), start as u32, count as u32,
                        width as u32, height as u32)
                };
                if visit < -1 {
                    return Err(Error::CellProgram);
                }
                start += count;
            }
            if !resumed {
                if let Some((geometry, kernel)) = &self.beardifier {
                    let kernel = unsafe { std::slice::from_raw_parts(*kernel, crate::world::level::levelgen::density::beardifier::KERNEL_SIZE) };
                    let (x, y, z) = (
                        self.layout.min_x.wrapping_add(cell_x.wrapping_mul(width)),
                        self.layout.min_cell_y.wrapping_add(cell_y as i32).wrapping_mul(height),
                        self.layout.min_z.wrapping_add((cell_z as i32).wrapping_mul(width)),
                    );
                    if !crate::world::level::levelgen::density::beardifier::cell(geometry, kernel, &mut self.beard, x, y, z, width, height) {
                        return Err(Error::CellProgram);
                    }
                    // Ap2 ADD over the cell: final density, then the Beardifier.
                    for (density, beard) in self.density.iter_mut().zip(&self.beard) {
                        *density += *beard;
                    }
                }
            }
            let (toggle, ridged_a, ridged_b) = match self.ore {
                Some([t, a, b]) => (self.corners(t, cell_z, cell_y), self.corners(a, cell_z, cell_y), self.corners(b, cell_z, cell_y)),
                None => (Corners([0.; 8]), Corners([0.; 8]), Corners([0.; 8])),
            };
            let x = self.layout.min_x.wrapping_add(cell_x.wrapping_mul(width));
            let y = self.layout.min_cell_y.wrapping_add(cell_y as i32).wrapping_mul(height);
            let z = self.layout.min_z.wrapping_add((cell_z as i32).wrapping_mul(width));
            let cell = Cell { x, y, z, density: &self.density, materials: materials.unwrap_or(&[]), toggle, ridged_a, ridged_b, gap };
            if materials.is_none() && fill.needs_materials(&cell) {
                let Some(aquifer) = self.aquifer.as_mut() else {
                    self.pending = true;
                    return Ok(Step::Materials([x, y, z]));
                };
                // The batch the cell's first such block would prepare, decided natively.
                aquifer.cell_materials(x, y, z, width, height, &self.density, &mut self.native_materials).map_err(Error::Aquifer)?;
                fill.fill_cell(&Cell { materials: &self.native_materials, ..cell })?;
            } else {
                fill.fill_cell(&cell)?;
            }
            materials = None;
            self.cursor += 1;
        }
        self.cursor = 0;
        self.slices.swap(0, 1);
        Ok(Step::Done)
    }
}
