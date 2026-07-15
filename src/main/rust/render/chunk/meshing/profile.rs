//! Native meshing profile counters and opt-in substage timing gates.
//!
//! Profiles are Rust-owned diagnostic state copied out through the FFI; production meshing must not
//! depend on the profiling toggles for behavior.

use std::sync::OnceLock;
use std::time::Instant;

pub(super) const PROFILE_STAGE_COUNT: usize = 56;
pub(super) const PROFILE_COUNT_COUNT: usize = 24;
pub(super) const PROFILE_EXPORT_LONGS: usize = PROFILE_STAGE_COUNT + PROFILE_COUNT_COUNT;
pub(super) const PROFILE_SECTION_SCAN: usize = 0;
pub(super) const PROFILE_MODEL_LOOKUP_EMIT: usize = 1;
pub(super) const PROFILE_FLUID_VIS_HEIGHT: usize = 2;
pub(super) const PROFILE_FLUID_GEOM_UV: usize = 3;
#[allow(dead_code)]
pub(super) const PROFILE_LIGHT_AO_TINT: usize = 4;
pub(super) const PROFILE_MATERIAL_PASS: usize = 5;
pub(super) const PROFILE_QUAD_STAGING: usize = 6;
pub(super) const PROFILE_TRANSLUCENT_INGEST: usize = 7;
#[allow(dead_code)]
pub(super) const PROFILE_TRANSLUCENT_METADATA: usize = 8;
#[allow(dead_code)]
pub(super) const PROFILE_SORTING: usize = 9;
pub(super) const PROFILE_VERTEX_PACKING: usize = 10;
#[allow(dead_code)]
pub(super) const PROFILE_INDEX_EMISSION: usize = 11;
pub(super) const PROFILE_FINAL_ASSEMBLY: usize = 12;
pub(super) const PROFILE_STATIC_STATE_SELECTOR_LOOKUP: usize = 13;
pub(super) const PROFILE_STATIC_WEIGHTED_MULTIPART_RESOLUTION: usize = 14;
pub(super) const PROFILE_STATIC_CACHED_MODEL_LOOKUP: usize = 15;
pub(super) const PROFILE_STATIC_CULLING: usize = 16;
pub(super) const PROFILE_STATIC_QUAD_ITERATION: usize = 17;
pub(super) const PROFILE_STATIC_LIGHTING_AO: usize = 18;
pub(super) const PROFILE_STATIC_TINT: usize = 19;
pub(super) const PROFILE_STATIC_POSITION_OFFSET_TRANSFORM: usize = 20;
pub(super) const PROFILE_STATIC_SPRITE_MATERIAL_PASS: usize = 21;
pub(super) const PROFILE_STATIC_NATIVE_QUAD_CREATION: usize = 22;
pub(super) const PROFILE_STATIC_STAGING: usize = 23;
pub(super) const PROFILE_FLUID_TOP_FACE_CONSTRUCTION: usize = 24;
pub(super) const PROFILE_FLUID_SIDE_FACE_CONSTRUCTION: usize = 25;
pub(super) const PROFILE_FLUID_BOTTOM_FACE_CONSTRUCTION: usize = 26;
pub(super) const PROFILE_FLUID_CORNER_HEIGHT_USE: usize = 27;
pub(super) const PROFILE_FLUID_STILL_FLOWING_UV: usize = 28;
pub(super) const PROFILE_FLUID_OVERLAY_SELECTION: usize = 29;
pub(super) const PROFILE_FLUID_LIGHTING_TINT: usize = 30;
pub(super) const PROFILE_FLUID_NORMAL_BACKFACE: usize = 31;
pub(super) const PROFILE_FLUID_MATERIAL_SPRITE_ROUTING: usize = 32;
pub(super) const PROFILE_FLUID_NATIVE_QUAD_APPEND: usize = 33;
pub(super) const PROFILE_SCAN_ACTIVE_RECORD_ITERATION: usize = 34;
pub(super) const PROFILE_SCAN_RECORD_DECODING: usize = 35;
pub(super) const PROFILE_SCAN_DISPATCH: usize = 36;
pub(super) const PROFILE_SCAN_CACHE_LOOKUP: usize = 37;
pub(super) const PROFILE_SCAN_CULLING: usize = 38;
pub(super) const PROFILE_SCAN_LIGHTING_AO: usize = 39;
pub(super) const PROFILE_SCAN_TINTING: usize = 40;
pub(super) const PROFILE_SCAN_MODEL_EMISSION: usize = 41;
pub(super) const PROFILE_SCAN_FLUID_EMISSION: usize = 42;
#[allow(dead_code)]
pub(super) const PROFILE_SCAN_PASS_MATERIAL_ROUTING: usize = 43;
pub(super) const PROFILE_SCAN_QUAD_APPEND: usize = 44;
pub(super) const PROFILE_STAGING_QUAD_APPEND: usize = 45;
pub(super) const PROFILE_STAGING_PENDING_WRITE: usize = 46;
pub(super) const PROFILE_STAGING_FLUSH: usize = 47;
pub(super) const PROFILE_STAGING_VERTEX_ENCODING: usize = 48;
#[allow(dead_code)]
pub(super) const PROFILE_STAGING_INDEX_WRITE: usize = 49;
#[allow(dead_code)]
pub(super) const PROFILE_STAGING_FINAL_BUFFER_ASSEMBLY: usize = 50;
pub(super) const PROFILE_COUNT_SCANNED_BLOCKS: usize = 0;
pub(super) const PROFILE_COUNT_NATIVE_MODEL_BLOCKS: usize = 1;
pub(super) const PROFILE_COUNT_NATIVE_MODEL_QUADS: usize = 2;
pub(super) const PROFILE_COUNT_FLUID_BLOCKS: usize = 3;
pub(super) const PROFILE_COUNT_FLUID_FACES: usize = 4;
pub(super) const PROFILE_COUNT_TRANSLUCENT_QUADS: usize = 5;
#[allow(dead_code)]
pub(super) const PROFILE_COUNT_SORTED_QUADS: usize = 6;
pub(super) const PROFILE_COUNT_EMITTED_QUADS: usize = 7;
pub(super) const PROFILE_COUNT_GENERIC_NATIVE_QUADS: usize = 9;
pub(super) const PROFILE_COUNT_GENERIC_NATIVE_BYTES_RETAINED: usize = 11;
pub(super) const PROFILE_COUNT_SELECTOR_RESOLUTIONS: usize = 12;
pub(super) const PROFILE_COUNT_SELECTOR_CACHE_HITS: usize = 13;
pub(super) const PROFILE_COUNT_SELECTOR_CACHE_MISSES: usize = 14;
pub(super) const PROFILE_COUNT_MULTIPART_CHILDREN_TESTED: usize = 15;
pub(super) const PROFILE_COUNT_MULTIPART_CHILDREN_SELECTED: usize = 16;
pub(super) const PROFILE_COUNT_WEIGHTED_ENTRIES_VISITED: usize = 17;
pub(super) const PROFILE_COUNT_MODEL_CACHE_HITS: usize = 18;
pub(super) const PROFILE_COUNT_MODEL_CACHE_MISSES: usize = 19;
pub(super) const PROFILE_COUNT_TEMP_VECTOR_CLEARS: usize = 20;
pub(super) const PROFILE_COUNT_TRANSLUCENT_RETAINED_BYTES: usize = 21;
pub(super) const PROFILE_COUNT_TRANSLUCENT_ANALYZER_ENTRIES: usize = 22;
pub(super) const PROFILE_COUNT_TRANSLUCENT_VALIDITY_BYTES: usize = 23;

pub(super) static STATIC_MODEL_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
pub(super) static FLUID_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
pub(super) static SCAN_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();
pub(super) static STAGING_SUBSTAGE_PROFILE_ENABLED: OnceLock<bool> = OnceLock::new();

#[derive(Clone, Copy, Debug)]
pub(super) struct NativeMeshingProfile {
    pub(super) stage_nanos: [u64; PROFILE_STAGE_COUNT],
    pub(super) counts: [u64; PROFILE_COUNT_COUNT],
}

impl Default for NativeMeshingProfile {
    fn default() -> Self {
        Self {
            stage_nanos: [0; PROFILE_STAGE_COUNT],
            counts: [0; PROFILE_COUNT_COUNT],
        }
    }
}

impl NativeMeshingProfile {
    #[inline(always)]
    pub(super) fn reset(&mut self) {
        self.stage_nanos.fill(0);
        self.counts.fill(0);
    }

    #[inline(always)]
    pub(super) fn add_stage(&mut self, stage: usize, started_at: Instant) {
        self.stage_nanos[stage] = self.stage_nanos[stage]
            .saturating_add(started_at.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64);
    }

    #[inline(always)]
    pub(super) fn add_optional_stage(&mut self, stage: usize, started_at: Option<Instant>) {
        if let Some(started_at) = started_at {
            self.add_stage(stage, started_at);
        }
    }

    #[inline(always)]
    pub(super) fn add_count(&mut self, counter: usize, value: usize) {
        self.counts[counter] = self.counts[counter].saturating_add(value as u64);
    }
}

#[inline(always)]
pub(super) fn static_model_substage_profile_enabled() -> bool {
    *STATIC_MODEL_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_STATIC_MODEL_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
pub(super) fn fluid_substage_profile_enabled() -> bool {
    *FLUID_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_FLUID_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
pub(super) fn scan_substage_profile_enabled() -> bool {
    *SCAN_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_SCAN_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
pub(super) fn staging_substage_profile_enabled() -> bool {
    *STAGING_SUBSTAGE_PROFILE_ENABLED.get_or_init(|| {
        matches!(
            std::env::var("MATTMC_PROFILE_STAGING_SUBSTAGES")
                .as_deref()
                .map(str::to_ascii_lowercase),
            Ok(value) if value == "1" || value == "true" || value == "yes" || value == "on"
        )
    })
}

#[inline(always)]
pub(super) fn profile_start(enabled: bool) -> Option<Instant> {
    enabled.then(Instant::now)
}
