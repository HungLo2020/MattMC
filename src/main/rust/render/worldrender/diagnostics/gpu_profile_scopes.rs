//! GPU profiling scopes of the world renderer.
//!
//! The GAL and its backends time and count work per opaque scope index; this
//! module decides which world passes and pipelines belong to which scope and
//! what each scope is called. Scope numbers double as pipeline-statistics
//! codes in diagnostic output.

use crate::render::vulkanic::resources::{GpuProfileClassifier, GpuProfileTag, GpuProfiledObject};

pub(crate) const SHADOW_DEPTH: u8 = 0;
pub(crate) const TERRAIN_OPAQUE: u8 = 1;
pub(crate) const TERRAIN_CUTOUT: u8 = 2;
pub(crate) const DEFERRED_LIGHTING: u8 = 3;
pub(crate) const COMPOSITE_0: u8 = 4;
pub(crate) const COMPOSITE_1: u8 = 5;
pub(crate) const FINAL_OUTPUT: u8 = 6;
pub(crate) const DISTANT_HORIZONS_OPAQUE: u8 = 7;
pub(crate) const DISTANT_HORIZONS_TRANSPARENT_SIDE: u8 = 8;
pub(crate) const DISTANT_HORIZONS_TRANSPARENT_UP: u8 = 9;
pub(crate) const DISTANT_HORIZONS_WATER: u8 = 10;
pub(crate) const DISTANT_HORIZONS_TRANSPARENT_SHARED: u8 = 11;

pub(crate) const WORLD_GPU_PROFILE_CLASSIFIER: GpuProfileClassifier = GpuProfileClassifier {
    classify,
    statistics_scope_name: scope_name,
};

fn classify(object: GpuProfiledObject, label: &str) -> GpuProfileTag {
    match object {
        GpuProfiledObject::RenderPass => {
            let timing = pass_timing_scope(label);
            GpuProfileTag {
                timing_scope: timing,
                statistics_scope: timing,
                timing_ends_at_untimed_pipeline: false,
            }
        }
        GpuProfiledObject::GraphicsPipeline => {
            let timing = pipeline_timing_scope(label);
            GpuProfileTag {
                timing_scope: timing,
                statistics_scope: pipeline_statistics_scope(label).or(timing),
                // The DH opaque span measures only its contiguous pipelines;
                // transparent and water pipelines close it.
                timing_ends_at_untimed_pipeline: timing == Some(DISTANT_HORIZONS_OPAQUE),
            }
        }
    }
}

fn pass_timing_scope(label: &str) -> Option<u8> {
    let label = label.trim();
    if label.contains("shadow_depth") || label.contains("shadow-pass") {
        Some(SHADOW_DEPTH)
    } else if label.contains("terrain_opaque") {
        Some(TERRAIN_OPAQUE)
    } else if label.contains("terrain_cutout") {
        Some(TERRAIN_CUTOUT)
    } else if label.contains("deferred_lighting") || label.contains("deferred-lighting-pass") {
        Some(DEFERRED_LIGHTING)
    } else if label.contains("composite_0") || label.contains("composite-0-pass") {
        Some(COMPOSITE_0)
    } else if label.contains("composite_1") || label.contains("composite-1-pass") {
        Some(COMPOSITE_1)
    } else if label.contains("final_output") || label.contains("final-output-pass") {
        Some(FINAL_OUTPUT)
    } else {
        None
    }
}

fn pipeline_timing_scope(label: &str) -> Option<u8> {
    let label = label.trim();
    if label.contains("world-lod-forward-opaque") {
        Some(DISTANT_HORIZONS_OPAQUE)
    } else if label.contains("world-lod-transparent-up") {
        Some(DISTANT_HORIZONS_TRANSPARENT_SHARED)
    } else if label.contains("shadow_depth") || label.contains("shadow-pipeline") {
        Some(SHADOW_DEPTH)
    } else if label.contains("terrain_opaque")
        || (label.contains("world-mesh-gbuffer") && label.contains("-mode1-"))
    {
        Some(TERRAIN_OPAQUE)
    } else if label.contains("terrain_cutout")
        || (label.contains("world-mesh-gbuffer") && label.contains("-mode2-"))
    {
        Some(TERRAIN_CUTOUT)
    } else if label.contains("deferred_lighting") || label.contains("deferred-lighting.pipeline") {
        Some(DEFERRED_LIGHTING)
    } else if label.contains("composite_0") || label.contains("composite-0.pipeline") {
        Some(COMPOSITE_0)
    } else if label.contains("composite_1") || label.contains("composite-1.pipeline") {
        Some(COMPOSITE_1)
    } else if label.contains("final_output") || label.contains("final-output.pipeline") {
        Some(FINAL_OUTPUT)
    } else {
        None
    }
}

/// Statistics-only buckets that differ from a pipeline's timing scope.
fn pipeline_statistics_scope(label: &str) -> Option<u8> {
    if label.contains("world-lod-transparent-side") {
        Some(DISTANT_HORIZONS_TRANSPARENT_SIDE)
    } else if label.contains("world-lod-transparent-up") {
        Some(DISTANT_HORIZONS_TRANSPARENT_UP)
    } else if label.contains("world-lod-water-surface") {
        Some(DISTANT_HORIZONS_WATER)
    } else {
        None
    }
}

fn scope_name(scope: u8) -> &'static str {
    match scope {
        SHADOW_DEPTH => "shadow-depth",
        TERRAIN_OPAQUE => "terrain-opaque",
        TERRAIN_CUTOUT => "terrain-cutout",
        DEFERRED_LIGHTING => "deferred-lighting",
        COMPOSITE_0 => "composite-0",
        COMPOSITE_1 => "composite-1",
        FINAL_OUTPUT => "final-output",
        DISTANT_HORIZONS_OPAQUE => "distant-horizons-opaque",
        DISTANT_HORIZONS_TRANSPARENT_SIDE => "distant-horizons-transparent-side",
        // The no-shader Frozen-compatible route deliberately reuses this
        // pipeline for side, up, and water buckets under one raster policy.
        DISTANT_HORIZONS_TRANSPARENT_UP => "distant-horizons-transparent-up-or-shared",
        DISTANT_HORIZONS_WATER => "distant-horizons-water",
        DISTANT_HORIZONS_TRANSPARENT_SHARED => "distant-horizons-transparent-shared-timestamped",
        _ => "unknown",
    }
}

#[cfg(test)]
mod tests {
    use crate::render::worldrender::diagnostics::gpu_profile_scopes::*;
    use crate::render::vulkanic::resources::GPU_PROFILE_SCOPE_COUNT;

    fn pass(label: &str) -> Option<u8> {
        classify(GpuProfiledObject::RenderPass, label).timing_scope
    }

    fn pipeline(label: &str) -> GpuProfileTag {
        classify(GpuProfiledObject::GraphicsPipeline, label)
    }

    #[test]
    fn pass_labels_classify_shader_graph_passes() {
        assert_eq!(Some(SHADOW_DEPTH), pass("vulkanic:pass/shadow_depth"));
        assert_eq!(Some(TERRAIN_OPAQUE), pass("vulkanic:pass/terrain_opaque"));
        assert_eq!(Some(FINAL_OUTPUT), pass("vulkanic:pass/final_output"));
        assert_eq!(None, pass("minecraft.world.clear"));
        assert_eq!(
            GpuProfileTag::default(),
            classify(GpuProfiledObject::RenderPass, "minecraft.world.clear")
        );
    }

    #[test]
    fn labels_classify_actual_runtime_resource_names() {
        assert_eq!(Some(SHADOW_DEPTH), pass("world-gbuffer.shadow-pass"));
        assert_eq!(Some(DEFERRED_LIGHTING), pass("world-gbuffer.deferred-lighting-pass"));
        assert_eq!(
            Some(COMPOSITE_0),
            pipeline("world-gbuffer.composite-0.pipeline").timing_scope
        );
        assert_eq!(
            Some(TERRAIN_OPAQUE),
            pipeline("world-mesh-gbuffer-stratum4-sand-gen7-section0-texture3-mode1-depth2-cull1.pipeline")
                .timing_scope
        );
        assert_eq!(
            Some(TERRAIN_CUTOUT),
            pipeline("world-mesh-gbuffer-stratum4-leaves-gen7-section0-texture9-mode2-depth2-cull1.pipeline")
                .timing_scope
        );
        for label in [
            "world-lod-forward-opaque.offscreen.pipeline",
            "world-lod-forward-opaque.pipeline",
        ] {
            let tag = pipeline(label);
            assert_eq!(Some(DISTANT_HORIZONS_OPAQUE), tag.timing_scope);
            assert_eq!(Some(DISTANT_HORIZONS_OPAQUE), tag.statistics_scope);
            assert!(tag.timing_ends_at_untimed_pipeline);
        }
        for (label, statistics) in [
            ("world-lod-transparent-side.pipeline", DISTANT_HORIZONS_TRANSPARENT_SIDE),
            ("world-lod-transparent-up.pipeline", DISTANT_HORIZONS_TRANSPARENT_UP),
            ("world-lod-water-surface.pipeline", DISTANT_HORIZONS_WATER),
        ] {
            let tag = pipeline(label);
            assert_eq!(
                label
                    .contains("transparent-up")
                    .then_some(DISTANT_HORIZONS_TRANSPARENT_SHARED),
                tag.timing_scope
            );
            assert_eq!(Some(statistics), tag.statistics_scope);
            assert!(!tag.timing_ends_at_untimed_pipeline);
            assert_ne!("unknown", scope_name(statistics));
        }
    }

    #[test]
    fn every_scope_fits_the_gal_scope_budget_and_is_named() {
        for scope in SHADOW_DEPTH..=DISTANT_HORIZONS_TRANSPARENT_SHARED {
            assert!(usize::from(scope) < GPU_PROFILE_SCOPE_COUNT);
            assert_ne!("unknown", scope_name(scope));
        }
    }
}
