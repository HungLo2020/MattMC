//! Retained semantic item layers and frame-local instantiation. No GPU state.
use super::{
    validate_batches, validate_batches_with_geometry, GalError, GalResult, GuiItemCache,
    GuiItemFoil, GuiMeshBatchRequest, GuiPersistentGeometry, StatusCode, GUI_MESH_MAX_BATCHES,
    GUI_MESH_MAX_FRAME_PAYLOAD_BYTES,
};
use std::sync::{
    atomic::{AtomicU32, AtomicUsize, Ordering},
    Arc, LazyLock,
};

const MAX_RETAINED_BYTES: usize = 64 * 1024 * 1024;
struct MemoryBudget {
    live: AtomicUsize,
    limit: usize,
}
static BUDGET: LazyLock<Arc<MemoryBudget>> = LazyLock::new(|| {
    Arc::new(MemoryBudget {
        live: AtomicUsize::new(0),
        limit: MAX_RETAINED_BYTES,
    })
});
static GENERATION: AtomicU32 = AtomicU32::new(1);

pub(crate) struct GuiItemPlacement {
    pub stratum: u32,
    pub sequence: u64,
    pub pose: [f32; 6],
    pub bounds: [i32; 4],
    pub extent: [u32; 2],
    pub clip_mode: u32,
    pub clip: [i32; 4],
    pub cache: Option<GuiItemCache>,
}

pub(crate) struct GuiItemTemplate {
    batches: Vec<GuiMeshBatchRequest>,
    foil_layers: Vec<Option<usize>>,
    foil_sources: Vec<bool>,
    source_layers: usize,
    bytes: usize,
    geometry_bytes: u64,
    budget: Arc<MemoryBudget>,
}

fn invalid(message: &'static str) -> GalError {
    GalError::ffi(StatusCode::InvalidArgument, message)
}

impl GuiItemTemplate {
    pub(crate) fn new(
        batches: Vec<GuiMeshBatchRequest>,
        foil_layers: Vec<Option<usize>>,
        source_layers: usize,
    ) -> GalResult<Self> {
        Self::with_budget(batches, foil_layers, source_layers, BUDGET.clone())
    }

    fn with_budget(
        mut batches: Vec<GuiMeshBatchRequest>,
        foil_layers: Vec<Option<usize>>,
        source_layers: usize,
        budget: Arc<MemoryBudget>,
    ) -> GalResult<Self> {
        if batches.is_empty()
            || batches.len() != foil_layers.len()
            || source_layers == 0
            || source_layers > GUI_MESH_MAX_BATCHES
        {
            return Err(invalid("invalid retained item layer membership"));
        }
        validate_batches(&batches)?;
        let mut foil_sources = vec![false; source_layers];
        let group = (batches[0].stratum, batches[0].sequence);
        let layout = (batches[0].block_item_raster, batches[0].item_raster_scale);
        for (batch, selector) in batches.iter().zip(&foil_layers) {
            if (batch.stratum, batch.sequence) != group
                || (batch.block_item_raster.is_none() && batch.item_raster_scale == 0)
                || (batch.block_item_raster, batch.item_raster_scale) != layout
                || batch.item_foil.is_some() != selector.is_some()
                || selector.is_some_and(|index| index >= source_layers)
            {
                return Err(invalid(
                    "retained template requires one complete native-layout item",
                ));
            }
            if let Some(index) = selector {
                foil_sources[*index] = true;
            }
        }
        // Charge referenced geometry conservatively, even when shared. This
        // bounds retained template inputs independently of Java collection.
        let geometry_bytes = batches
            .iter()
            .try_fold(0u64, |sum, batch| {
                sum.checked_add(std::mem::size_of_val(&batch.vertices[..]) as u64)?
                    .checked_add(std::mem::size_of_val(&batch.indices[..]) as u64)
            })
            .ok_or_else(|| invalid("retained item payload overflows"))?;
        let base_bytes = batches
            .capacity()
            .checked_mul(std::mem::size_of::<GuiMeshBatchRequest>())
            .and_then(|value| {
                foil_layers
                    .capacity()
                    .checked_mul(std::mem::size_of::<Option<usize>>())
                    .and_then(|selectors| value.checked_add(selectors))
            })
            .and_then(|value| value.checked_add(std::mem::size_of::<Self>()))
            .and_then(|value| value.checked_add(foil_sources.capacity()))
            .ok_or_else(|| invalid("retained item storage overflows"))?;
        let bytes = batches
            .iter()
            .try_fold(base_bytes, |sum, batch| {
                sum.checked_add(
                    batch
                        .vertices
                        .capacity()
                        .checked_mul(std::mem::size_of::<super::GuiMeshVertex>())?,
                )?
                .checked_add(
                    batch
                        .indices
                        .capacity()
                        .checked_mul(std::mem::size_of::<u32>())?,
                )?
                .checked_add(128) // conservative shared-vector allocation/header overhead
            })
            .ok_or_else(|| invalid("retained item storage overflows"))?;
        let generation = GENERATION
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |value| {
                (value < 0x4000_0000).then_some(value + 1)
            })
            .map_err(|_| invalid("retained item identities exhausted"))?
            | 0x8000_0000;
        budget
            .live
            .fetch_update(Ordering::AcqRel, Ordering::Relaxed, |value| {
                value
                    .checked_add(bytes)
                    .filter(|next| *next <= budget.limit)
            })
            .map_err(|_| {
                GalError::ffi(
                    StatusCode::ResourceLimit,
                    "retained item memory budget exhausted",
                )
            })?;
        for batch in &mut batches {
            batch.persistent_geometry = Some(GuiPersistentGeometry {
                vertices: batch.vertices.as_ptr() as u64,
                indices: batch.indices.as_ptr() as u64,
                generation,
            });
        }
        Ok(Self {
            batches,
            foil_layers,
            foil_sources,
            source_layers,
            bytes,
            geometry_bytes,
            budget,
        })
    }

    pub(crate) fn payload_bytes(&self) -> usize {
        self.bytes
    }
    pub(crate) fn batch_count(&self) -> usize {
        self.batches.len()
    }
    pub(crate) fn geometry_bytes(&self) -> u64 {
        self.geometry_bytes
    }
    pub(crate) fn source_layers(&self) -> usize {
        self.source_layers
    }

    /// Dynamic facts preserve source-layer foil clocks; no per-quad input.
    pub(crate) fn append(
        &self,
        placement: &GuiItemPlacement,
        foils: &[Option<GuiItemFoil>],
        output: &mut Vec<GuiMeshBatchRequest>,
        frame_payload_bytes: &mut u64,
    ) -> GalResult<()> {
        if foils.len() != self.source_layers
            || output.len().saturating_add(self.batches.len()) > GUI_MESH_MAX_BATCHES
        {
            return Err(invalid("invalid retained item frame capacity"));
        }
        if foils
            .iter()
            .zip(&self.foil_sources)
            .any(|(foil, expected)| foil.is_some() != *expected)
        {
            return Err(invalid("retained item source foil membership differs"));
        }
        let next_payload = frame_payload_bytes
            .checked_add(self.geometry_bytes)
            .filter(|bytes| *bytes <= GUI_MESH_MAX_FRAME_PAYLOAD_BYTES)
            .ok_or_else(|| invalid("retained item frame payload exceeds its limit"))?;
        let start = output.len();
        output.reserve(self.batches.len());
        for (prototype, selector) in self.batches.iter().zip(&self.foil_layers) {
            let mut batch = prototype.clone();
            batch.stratum = placement.stratum;
            batch.sequence = placement.sequence;
            batch.gui_pose = placement.pose;
            batch.bounds = placement.bounds;
            batch.gui_extent = placement.extent;
            batch.projection_extent = placement.extent.map(|axis| axis as f32);
            batch.clip_mode = placement.clip_mode;
            [
                batch.clip_left,
                batch.clip_top,
                batch.clip_width,
                batch.clip_height,
            ] = placement.clip;
            batch.item_cache = placement.cache;
            batch.item_foil = selector.and_then(|index| foils[index]);
            if selector.is_some() && batch.item_foil.is_none() {
                output.truncate(start);
                return Err(invalid("retained item frame omitted foil semantics"));
            }
            output.push(batch);
        }
        if let Err(error) = validate_batches_with_geometry(&output[start..], |_| false) {
            output.truncate(start);
            return Err(error);
        }
        *frame_payload_bytes = next_payload;
        Ok(())
    }
}

impl Drop for GuiItemTemplate {
    fn drop(&mut self) {
        self.budget.live.fetch_sub(self.bytes, Ordering::AcqRel);
    }
}

#[cfg(test)]
mod tests;
