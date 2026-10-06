//! Compiled fullscreen pipelines and their bounded cache.

use super::*;

/// Static GAL objects for a prepared fullscreen source pass. Per-frame UBO
/// writes and pass commands are intentionally a later executor concern; this
/// owner proves that source-declared fullscreen stages compile through the
/// same explicit VulkanicGAL pipeline path on Vulkan and OpenGL.
#[derive(Debug)]
pub(crate) struct CompiledFullscreenSourcePass {
    pub target: Handle,
    pub pass: Handle,
    pub source_data_layout: Handle,
    pub pack_resources_layout: Handle,
    pub pipeline_layout: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub pipeline: Handle,
    /// When set, the layouts, shaders, and pipeline above are owned by a
    /// [`FullscreenPipelineCache`] entry and outlive this frame's pass.
    pub(super) shared: Option<std::sync::Arc<FullscreenPipelineObjects>>,
    /// False when a stage cache owns `target` and `pass`.
    pub(super) owns_target: bool,
}

/// View-independent GAL objects for one compiled fullscreen source stage.
/// They depend only on the lowered program and its color formats, so one
/// set serves every frame; the render target and pass (which reference the
/// frame's color views) remain per-plan.
#[derive(Debug)]
pub(crate) struct FullscreenPipelineObjects {
    pub(super) source_data_layout: Handle,
    pub(super) pack_resources_layout: Handle,
    pub(super) pipeline_layout: Handle,
    pub(super) vertex_shader: Handle,
    pub(super) fragment_shader: Handle,
    pub(super) pipeline: Handle,
}

impl FullscreenPipelineObjects {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.vertex_shader,
            self.fragment_shader,
            self.pipeline_layout,
            self.source_data_layout,
            self.pack_resources_layout,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub(super) struct FullscreenPipelineKey {
    pub(super) program_identity: String,
    pub(super) shader_pack_generation: u64,
    pub(super) source_stage_path: String,
    pub(super) color_formats: Vec<TextureFormat>,
    pub(super) celestial_blend: bool,
}

/// One cached pipeline together with the exact compile inputs it was built
/// from; a hit requires identical shader stages and resource layouts.
#[derive(Debug)]
pub(super) struct CachedFullscreenPipeline {
    pub(super) vertex: crate::render::shaderpack::programs::ShaderStageSource,
    pub(super) fragment: crate::render::shaderpack::programs::ShaderStageSource,
    pub(super) source_data_bindings: Vec<crate::render::vulkanic::resources::ResourceBindingDesc>,
    pub(super) pack_resources_bindings: Vec<crate::render::vulkanic::resources::ResourceBindingDesc>,
    pub(super) objects: std::sync::Arc<FullscreenPipelineObjects>,
}

/// Retains compiled fullscreen pipelines across frames. Entries are valid
/// only for the source-candidate epochs that lowered their programs; any
/// epoch change drops them all. An entry still referenced by a live plan is
/// destroyed by that plan's last owner.
#[derive(Debug, Default)]
pub(crate) struct FullscreenPipelineCache {
    pub(super) epochs: std::cell::Cell<Option<(u64, u64)>>,
    pub(super) entries: std::cell::RefCell<
        std::collections::HashMap<FullscreenPipelineKey, Vec<CachedFullscreenPipeline>>,
    >,
    /// Frame-invariant objects of recently staged stages, by stage path.
    pub(super) stages: std::cell::RefCell<
        std::collections::HashMap<String, Vec<CachedFullscreenStage>>,
    >,
    /// Released stages still leased to a live plan; destroyed once returned.
    pub(super) retired_stages: std::cell::RefCell<Vec<CachedFullscreenStage>>,
    /// Plans returned at the end of their frame, reused while their inputs
    /// are unchanged. Shared with the plans so ending a frame parks them.
    pub(super) parked: ParkedFullscreenPlans,
}

pub(super) type ParkedFullscreenPlans = std::sync::Arc<std::sync::Mutex<ParkedPlans>>;

#[derive(Debug, Default)]
pub(super) struct ParkedPlans {
    /// Advanced whenever the cache releases its stages; a plan staged before
    /// that must not come back.
    pub(super) release: u64,
    pub(super) plans: Vec<(FullscreenPlanKey, FullscreenSourceExecutionPlan)>,
}

/// Everything a staged plan was built from. Equal keys (and unchanged cache
/// epochs) mean staging again would rebuild the same plan.
#[derive(Debug)]
pub(super) struct FullscreenPlanKey {
    pub(super) program_identity: String,
    pub(super) shader_pack_generation: u64,
    pub(super) source_stage_path: String,
    pub(super) epochs: (u64, u64),
    pub(super) extent: crate::render::vulkanic::resources::Extent3d,
    pub(super) targets: ShaderPackColorTargets,
    pub(super) external_inputs: Vec<TerrainSourceOwnedResourceSet>,
}

impl FullscreenPlanKey {
    fn same_plan(&self, other: &Self) -> bool {
        self.epochs == other.epochs
            && self.shader_pack_generation == other.shader_pack_generation
            && self.extent == other.extent
            && self.program_identity == other.program_identity
            && self.source_stage_path == other.source_stage_path
            && self.targets.identity == other.targets.identity
            && self.targets.same_images(&other.targets)
            && self.external_inputs == other.external_inputs
    }
}

/// A plan's way back into its cache's parked plans.
#[derive(Debug)]
pub(crate) struct PlanParking {
    pub(super) store: ParkedFullscreenPlans,
    pub(super) release: u64,
    pub(super) key: FullscreenPlanKey,
}

impl PlanParking {
    /// Parks `plan`, or hands it back when its stage already has the bounded
    /// number of parked variants (or the store is poisoned).
    pub(super) fn park(
        self,
        plan: FullscreenSourceExecutionPlan,
    ) -> Result<(), FullscreenSourceExecutionPlan> {
        let Ok(mut parked) = self.store.lock() else {
            return Err(plan);
        };
        if parked.release != self.release {
            return Err(plan);
        }
        let variants = parked
            .plans
            .iter()
            .filter(|(key, _)| key.source_stage_path == self.key.source_stage_path)
            .count();
        if variants >= FULLSCREEN_STAGE_CACHE_VARIANTS {
            return Err(plan);
        }
        parked.plans.push((self.key, plan));
        Ok(())
    }
}

pub(super) const FULLSCREEN_PIPELINE_CACHE_ENTRIES: usize = 128;
/// Target sets a stage alternates between (feedback targets swap images).
pub(super) const FULLSCREEN_STAGE_CACHE_VARIANTS: usize = 2;

/// Everything a staged fullscreen pass creates except its pack-resources
/// set, which binds the frame's inputs: color samplers, render target and
/// pass, uniform buffers and source-data set. They depend only on the
/// program, its pipeline and the exact color target images, so a stage
/// re-staged against the same targets reuses them instead of recreating
/// about five GAL objects per stage per frame. The uniform buffers are
/// rewritten by each draw; `lease` admits one live plan at a time so a
/// stage staged twice in one frame never shares them.
#[derive(Debug)]
pub(super) struct CachedFullscreenStage {
    pub(super) program_identity: String,
    pub(super) shader_pack_generation: u64,
    pub(super) epochs: (u64, u64),
    pub(super) targets: ShaderPackColorTargets,
    pub(super) color_resources: ShaderPackSourceColorResources,
    pub(super) target: Handle,
    pub(super) pass: Handle,
    pub(super) objects: std::sync::Arc<FullscreenPipelineObjects>,
    pub(super) texture_transform_buffer: Handle,
    pub(super) scalar_uniform_buffer: Option<Handle>,
    pub(super) source_data_set: Handle,
    pub(super) lease: std::sync::Arc<std::sync::atomic::AtomicBool>,
}

impl CachedFullscreenStage {
    pub(super) fn matches(
        &self,
        program: &LoweredFullscreenSourceProgram,
        targets: &ShaderPackColorTargets,
        epochs: (u64, u64),
    ) -> bool {
        self.epochs == epochs
            && self.shader_pack_generation == program.shader_pack_generation
            && self.program_identity == program.identity.as_str()
            && self.targets.identity == targets.identity
            && self.targets.same_images(targets)
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        // Dependents first: sets and passes reference buffers, targets and views.
        for handle in [self.source_data_set, self.pass, self.target, self.texture_transform_buffer]
            .into_iter()
            .chain(self.scalar_uniform_buffer)
        {
            let _ = gal.destroy(handle);
        }
        self.color_resources.destroy(gal);
        if let Ok(objects) = std::sync::Arc::try_unwrap(self.objects) {
            objects.destroy(gal);
        }
    }
}

impl FullscreenPipelineCache {
    /// Takes the parked plan staged from exactly `key`'s inputs.
    pub(super) fn unpark(&self, key: &FullscreenPlanKey) -> Option<FullscreenSourceExecutionPlan> {
        let mut parked = self.parked.lock().ok()?;
        let index = parked.plans.iter().position(|(parked_key, _)| parked_key.same_plan(key))?;
        Some(parked.plans.swap_remove(index).1)
    }

    /// Where a plan staged now may park at the end of its frame.
    pub(super) fn parking(&self, key: FullscreenPlanKey) -> Option<PlanParking> {
        let release = self.parked.lock().ok()?.release;
        Some(PlanParking { store: self.parked.clone(), release, key })
    }

    /// Destroys every parked plan, returning their stage leases, and refuses
    /// plans staged before now.
    fn destroy_parked(&self, gal: &mut VulkanicGal) {
        let parked = {
            let mut parked = match self.parked.lock() {
                Ok(parked) => parked,
                Err(poisoned) => poisoned.into_inner(),
            };
            parked.release += 1;
            std::mem::take(&mut parked.plans)
        };
        for (_, plan) in parked {
            // Parking is cleared on unpark, so this destroys rather than re-parks.
            plan.destroy(gal);
        }
    }

    /// Drops every cached stage. Required before the color targets they
    /// reference are retired. A stage leased to a live plan is destroyed when
    /// that plan returns it.
    pub(crate) fn release_stages(&self, gal: &mut VulkanicGal) {
        self.destroy_parked(gal);
        let stages = std::mem::take(&mut *self.stages.borrow_mut());
        self.retire_stages(gal, stages.into_values().flatten());
    }

    pub(super) fn retire_stages(
        &self,
        gal: &mut VulkanicGal,
        stages: impl IntoIterator<Item = CachedFullscreenStage>,
    ) {
        let mut retired = self.retired_stages.borrow_mut();
        retired.extend(stages);
        for stage in std::mem::take(&mut *retired) {
            if stage.lease.load(std::sync::atomic::Ordering::Acquire) {
                retired.push(stage);
            } else {
                stage.destroy(gal);
            }
        }
    }

    pub(super) fn release_all(&self, gal: &mut VulkanicGal) {
        self.release_stages(gal);
        let entries = std::mem::take(&mut *self.entries.borrow_mut());
        for cached in entries.into_values().flatten() {
            if let Ok(objects) = std::sync::Arc::try_unwrap(cached.objects) {
                objects.destroy(gal);
            }
        }
    }

    pub(crate) fn destroy(&self, gal: &mut VulkanicGal) {
        self.release_all(gal);
        for stage in std::mem::take(&mut *self.retired_stages.borrow_mut()) {
            stage.destroy(gal);
        }
        self.epochs.set(None);
    }
}

impl CompiledFullscreenSourcePass {
    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        if let Some(shared) = self.shared {
            if self.owns_target {
                let _ = gal.destroy(self.pass);
                let _ = gal.destroy(self.target);
            }
            if let Ok(objects) = std::sync::Arc::try_unwrap(shared) {
                objects.destroy(gal);
            }
            return;
        }
        for handle in [
            self.pipeline,
            self.pass,
            self.target,
            self.vertex_shader,
            self.fragment_shader,
            self.pipeline_layout,
            self.source_data_layout,
            self.pack_resources_layout,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}
