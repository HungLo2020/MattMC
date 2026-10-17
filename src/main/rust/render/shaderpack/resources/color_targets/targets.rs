//! GAL color textures backing the pack's targets, and the per-generation target cache.

use super::*;

/// A source-derived identity for one private set of shader-pack color images.
/// It deliberately uses semantic generations and extents rather than a
/// frame target, attachment number, native image, or backend handle.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct ShaderPackColorTargetIdentity {
    pub world_generation: u64,
    pub shader_pack_generation: u64,
    pub extent: Extent3d,
    /// Sorted semantic names which require previous/current images because a
    /// source fullscreen stage reads and writes the same named color target.
    pub feedback_target_names: Vec<String>,
    /// Sorted semantic names whose active source program explicitly requests
    /// a complete mip chain. Allocation alone never declares those mips
    /// initialized; a later source pass must generate them before sampling.
    pub mipmapped_target_names: Vec<String>,
}

impl ShaderPackColorTargetIdentity {
    pub(crate) fn new(
        world_generation: u64,
        shader_pack_generation: u64,
        extent: Extent3d,
        feedback_target_names: impl IntoIterator<Item = String>,
        mipmapped_target_names: impl IntoIterator<Item = String>,
    ) -> GalResult<Self> {
        let mut feedback_target_names = feedback_target_names.into_iter().collect::<Vec<_>>();
        feedback_target_names.sort();
        feedback_target_names.dedup();
        let mut mipmapped_target_names = mipmapped_target_names.into_iter().collect::<Vec<_>>();
        mipmapped_target_names.sort();
        mipmapped_target_names.dedup();
        let identity = Self {
            world_generation,
            shader_pack_generation,
            extent,
            feedback_target_names,
            mipmapped_target_names,
        };
        identity.validate()?;
        Ok(identity)
    }

    pub(super) fn validate(&self) -> GalResult<()> {
        if self.world_generation == 0 || self.shader_pack_generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack color target identity requires non-zero world and shader-pack generations",
            ));
        }
        if self.extent.width == 0 || self.extent.height == 0 || self.extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "shader-pack color targets require a non-zero two-dimensional extent",
            ));
        }
        if self
            .feedback_target_names
            .iter()
            .chain(self.mipmapped_target_names.iter())
            .any(|name| {
                name.is_empty()
                    || !name.bytes().all(|byte| {
                        byte == b'_' || byte.is_ascii_lowercase() || byte.is_ascii_digit()
                    })
            })
        {
            return Err(GalError::invalid_argument(
                "shader-pack color target feedback/mipmap names must be normalized semantic identifiers",
            ));
        }
        Ok(())
    }
}

/// Rust-owned handles for one semantic color target. A feedback target has a
/// distinct previous image; non-feedback targets intentionally own only one
/// image so the runtime cannot accidentally add temporal copies to unrelated
/// source stages.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct ShaderPackColorTarget {
    pub source_slot: u32,
    pub format: TextureFormat,
    pub clear_each_frame: bool,
    pub clear_color_bits: Option<[u32; 4]>,
    pub mip_levels: u32,
    pub current_texture: Handle,
    /// A sampled view may cover the full source-requested mip chain. Render
    /// targets always use this explicit base-mip attachment view instead.
    pub current_attachment_view: Handle,
    pub current_view: Handle,
    pub previous_texture: Option<Handle>,
    pub previous_attachment_view: Option<Handle>,
    pub previous_view: Option<Handle>,
}

/// A complete private source-generation color target set. The map key is a
/// semantic resource name, never `colortexN` or a backend attachment index.
#[derive(Clone, Debug)]
pub(crate) struct ShaderPackColorTargets {
    pub identity: ShaderPackColorTargetIdentity,
    pub(super) targets: BTreeMap<String, ShaderPackColorTarget>,
    pub(super) clear_passes: ShaderPackColorClearPasses,
}

pub(super) fn color_resource_generation(
    identity: &ShaderPackColorTargetIdentity,
    name: &str,
    binding: SourceColorBinding,
) -> u64 {
    let identity_text = format!(
        "{}:{}:{}x{}:{}:{}:{}:{}:{}",
        identity.world_generation,
        identity.shader_pack_generation,
        identity.extent.width,
        identity.extent.height,
        name,
        binding.feedback,
        binding.mipmapped,
        identity.feedback_target_names.join(","),
        identity.mipmapped_target_names.join(","),
    );
    u64::from(xxh32(identity_text.as_bytes(), 0)).max(1)
}

impl ShaderPackColorTargets {
    /// True when both sets name the same images and views per target.
    pub(crate) fn same_images(&self, other: &Self) -> bool {
        self.targets == other.targets
    }

    pub(crate) fn target(&self, name: &str) -> Option<ShaderPackColorTarget> {
        self.targets.get(name).copied()
    }

    pub(crate) fn targets(&self) -> impl Iterator<Item = (&str, ShaderPackColorTarget)> {
        self.targets
            .iter()
            .map(|(name, target)| (name.as_str(), *target))
    }

    /// Semantic roles backed by this complete Rust-owned target generation.
    /// These are source-graph outputs, not a promise that any particular
    /// stage has already created sampler bindings for them. Callers use this
    /// only when validating complete graph residency; program-local bindings
    /// continue to be staged and checked separately at execution time.
    pub(crate) fn declared_roles(&self) -> impl Iterator<Item = TerrainSourceResourceRole> + '_ {
        self.targets
            .keys()
            .cloned()
            .map(TerrainSourceResourceRole::ShaderPackColor)
    }

    pub(super) fn create(
        gal: &mut VulkanicGal,
        identity: ShaderPackColorTargetIdentity,
        manifest: &ShaderPackColorTargetManifest,
    ) -> GalResult<Self> {
        identity.validate()?;
        if manifest.generation() != identity.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "shader-pack color target manifest generation {} does not match identity generation {}",
                manifest.generation(),
                identity.shader_pack_generation
            )));
        }
        manifest.require_gal_schema_formats()?;
        for feedback_name in &identity.feedback_target_names {
            if manifest.target(feedback_name).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "source feedback target '{feedback_name}' is absent from the shader-pack color manifest"
                )));
            }
        }
        for mipmapped_name in &identity.mipmapped_target_names {
            if manifest.target(mipmapped_name).is_none() {
                return Err(GalError::invalid_argument(format!(
                    "source mipmapped target '{mipmapped_name}' is absent from the shader-pack color manifest"
                )));
            }
        }

        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let mut targets = BTreeMap::new();
            for declaration in manifest.targets() {
                let name = declaration.name();
                let _ = declaration.gal_schema_color_format();
                let mip_levels = if identity
                    .mipmapped_target_names
                    .binary_search_by(|candidate| candidate.as_str().cmp(name))
                    .is_ok()
                {
                    full_mip_levels(identity.extent)
                } else {
                    1
                };
                let (format, current_texture) = create_compatible_color_texture(
                    gal,
                    &identity,
                    name,
                    declaration.format,
                    mip_levels,
                    "current",
                    &mut created,
                )?;
                let current_view = create_color_view(
                    gal,
                    &identity,
                    name,
                    format,
                    mip_levels,
                    "current",
                    current_texture,
                    &mut created,
                )?;
                let current_attachment_view = if mip_levels > 1 {
                    create_color_view(
                        gal,
                        &identity,
                        name,
                        format,
                        1,
                        "current-attachment",
                        current_texture,
                        &mut created,
                    )?
                } else {
                    current_view
                };
                let (previous_texture, previous_attachment_view, previous_view) = if identity
                    .feedback_target_names
                    .binary_search_by(|candidate| candidate.as_str().cmp(name))
                    .is_ok()
                {
                    let (previous_format, texture) = create_compatible_color_texture(
                        gal,
                        &identity,
                        name,
                        declaration.format,
                        mip_levels,
                        "previous",
                        &mut created,
                    )?;
                    if previous_format != format {
                        return Err(GalError::backend(format!(
                            "shader-pack color target '{name}' selected inconsistent current {:?} and previous {:?} storage formats",
                            format, previous_format
                        )));
                    }
                    let view = create_color_view(
                        gal,
                        &identity,
                        name,
                        format,
                        mip_levels,
                        "previous",
                        texture,
                        &mut created,
                    )?;
                    let attachment_view = if mip_levels > 1 {
                        Some(create_color_view(
                            gal,
                            &identity,
                            name,
                            format,
                            1,
                            "previous-attachment",
                            texture,
                            &mut created,
                        )?)
                    } else {
                        None
                    };
                    (Some(texture), attachment_view, Some(view))
                } else {
                    (None, None, None)
                };
                if targets
                    .insert(
                        name.to_string(),
                        ShaderPackColorTarget {
                            source_slot: declaration.source_slot,
                            format,
                            clear_each_frame: declaration.clear_each_frame,
                            clear_color_bits: declaration.clear_color_bits,
                            mip_levels,
                            current_texture,
                            current_attachment_view,
                            current_view,
                            previous_texture,
                            previous_attachment_view,
                            previous_view,
                        },
                    )
                    .is_some()
                {
                    return Err(GalError::invalid_argument(format!(
                        "shader-pack color manifest repeats semantic target '{name}'"
                    )));
                }
            }
            let clear_passes = ShaderPackColorClearPasses::create(gal, &identity, &targets, &mut created)?;
            Ok(Self { identity, targets, clear_passes })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        self.clear_passes.destroy(gal);
        for target in self.targets.into_values() {
            for handle in [
                target.previous_attachment_view,
                target.previous_view,
                (target.current_attachment_view != target.current_view)
                    .then_some(target.current_attachment_view),
                target.current_view.into(),
                target.previous_texture,
                target.current_texture.into(),
            ]
            .into_iter()
            .flatten()
            {
                // Cached fullscreen/final-output consumers of the old
                // generation may still bind these views.
                let _ = gal.retire(handle);
            }
        }
    }
}

pub(super) fn create_compatible_color_texture(
    gal: &mut VulkanicGal,
    identity: &ShaderPackColorTargetIdentity,
    name: &str,
    declared_format: ShaderPackColorFormat,
    mip_levels: u32,
    phase: &str,
    created: &mut Vec<Handle>,
) -> GalResult<(TextureFormat, Handle)> {
    let candidates = declared_format.compatible_storage_formats();
    for (index, format) in candidates.iter().copied().enumerate() {
        match create_color_texture(gal, identity, name, format, mip_levels, phase, created) {
            Ok(texture) => return Ok((format, texture)),
            Err(error)
                if error.code == StatusCode::UnsupportedFeature && index + 1 < candidates.len() =>
            {
                continue;
            }
            Err(error) => return Err(error),
        }
    }
    Err(GalError::unsupported_feature(format!(
        "shader-pack color target '{name}' has no supported Rust-owned storage format for {declared_format:?}"
    )))
}

pub(super) fn create_color_texture(
    gal: &mut VulkanicGal,
    identity: &ShaderPackColorTargetIdentity,
    name: &str,
    format: TextureFormat,
    mip_levels: u32,
    phase: &str,
    created: &mut Vec<Handle>,
) -> GalResult<Handle> {
    let handle = gal.create_texture(TextureDesc {
        label: format!(
            "shader-pack-color.world{}-pack{}.{}.{}.texture",
            identity.world_generation, identity.shader_pack_generation, name, phase
        ),
        dimension: TextureDimension::D2,
        format,
        extent: identity.extent,
        mip_levels,
        array_layers: 1,
        usages: vec![
            TextureUsage::ColorAttachment,
            TextureUsage::Sampled,
            TextureUsage::TransferSrc,
            TextureUsage::TransferDst,
        ],
    })?;
    created.push(handle);
    Ok(handle)
}

pub(super) fn create_color_view(
    gal: &mut VulkanicGal,
    identity: &ShaderPackColorTargetIdentity,
    name: &str,
    format: TextureFormat,
    mip_levels: u32,
    phase: &str,
    texture: Handle,
    created: &mut Vec<Handle>,
) -> GalResult<Handle> {
    let handle = gal.create_texture_view(TextureViewDesc {
        label: format!(
            "shader-pack-color.world{}-pack{}.{}.{}.view",
            identity.world_generation, identity.shader_pack_generation, name, phase
        ),
        texture,
        format,
        base_mip: 0,
        mip_count: mip_levels,
        base_layer: 0,
        layer_count: 1,
    })?;
    created.push(handle);
    Ok(handle)
}

pub(super) fn full_mip_levels(extent: Extent3d) -> u32 {
    let largest_dimension = extent.width.max(extent.height);
    u32::BITS - largest_dimension.leading_zeros()
}

/// A two-phase private target cache. The caller must confirm the combined
/// submission that used a pending replacement before it becomes active;
/// rejected submissions discard only their newly created targets.
#[derive(Debug, Default)]
pub(crate) struct ShaderPackColorTargetCache {
    pub(super) active: Option<ShaderPackColorTargets>,
    pub(super) pending: Option<ShaderPackColorTargets>,
    pub(super) confirmed_frame: Option<ShaderPackColorFrameState>,
}

impl ShaderPackColorTargetCache {
    pub(crate) fn has_pending_targets(&self) -> bool {
        self.pending.is_some()
    }

    pub(crate) fn stage(
        &mut self,
        gal: &mut VulkanicGal,
        identity: ShaderPackColorTargetIdentity,
        manifest: &ShaderPackColorTargetManifest,
    ) -> GalResult<ShaderPackColorTargets> {
        if let Some(pending) = &self.pending {
            if pending.identity != identity {
                return Err(GalError::backend(
                    "shader-pack color target replacement is awaiting submission confirmation",
                ));
            }
            return Ok(pending.clone());
        }
        if let Some(active) = &self.active {
            if active.identity == identity {
                return Ok(active.clone());
            }
        }
        let targets = ShaderPackColorTargets::create(gal, identity, manifest)?;
        self.pending = Some(targets.clone());
        Ok(targets)
    }

    pub(crate) fn confirm_submission(&mut self, gal: &mut VulkanicGal) {
        let Some(replacement) = self.pending.take() else {
            return;
        };
        if let Some(previous) = self.active.replace(replacement) {
            previous.destroy(gal);
        }
        // A replacement owns different images, so even a semantically similar
        // source target set must not inherit history across its generation.
        self.confirmed_frame = None;
    }

    /// Starts an exact target-generation source frame. The returned plan is
    /// private semantic scheduling state; it cannot make a target valid until
    /// `confirm_frame_submission` follows a successful combined submission.
    pub(crate) fn begin_frame(
        &self,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<ShaderPackColorFramePlan> {
        let prior = self
            .confirmed_frame
            .as_ref()
            .filter(|state| state.identity == targets.identity);
        ShaderPackColorFramePlan::begin(targets, prior)
    }

    /// Atomically advances target allocation and semantic color history after
    /// the exact combined source submission succeeds. The caller must discard
    /// the frame plan on failure; no rejected draw can seed feedback history.
    pub(crate) fn confirm_frame_submission(
        &mut self,
        gal: &mut VulkanicGal,
        frame: ShaderPackColorFramePlan,
    ) -> GalResult<()> {
        let frame_state = frame.into_confirmed_state();
        let matches_active = self
            .active
            .as_ref()
            .is_some_and(|active| active.identity == frame_state.identity);
        let matches_pending = self
            .pending
            .as_ref()
            .is_some_and(|pending| pending.identity == frame_state.identity);
        if !matches_active && !matches_pending {
            return Err(GalError::invalid_argument(
                "shader-pack color frame confirmation has no matching active or pending target generation",
            ));
        }
        if matches_pending {
            self.confirm_submission(gal);
        }
        self.confirmed_frame = Some(frame_state);
        Ok(())
    }

    pub(crate) fn discard_submission(&mut self, gal: &mut VulkanicGal) {
        if let Some(pending) = self.pending.take() {
            pending.destroy(gal);
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.discard_submission(gal);
        self.confirmed_frame = None;
        if let Some(active) = self.active.take() {
            active.destroy(gal);
        }
    }
}
