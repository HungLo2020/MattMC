//! Scene-driven draws for static chunk terrain on the shader route.
//!
//! A resident section mesh is described once, when its geometry lands in a
//! page: every facing group with its absolute index range and pass state
//! (`SceneTerrainGroup`, kept on the retained mesh record). Each frame then
//! walks only the visible sections: one instance record per section, and its
//! facing-selected groups appended to a run per (pass state, page). A run is
//! one indirect draw, so the per-frame cost is a tight loop over visible
//! sections with no generic batching or per-section validation.
//!
//! Sections that are not yet described (first frame after an upload, a
//! changed material mapping, a pending geometry upload) keep the general
//! path, which makes them resident and records them for the next frame.

use super::*;

/// Material kinds a terrain group can draw with; indexes per-kind inputs.
pub(crate) const SCENE_TERRAIN_KIND_OPAQUE: u8 = 0;
pub(crate) const SCENE_TERRAIN_KIND_CUTOUT: u8 = 1;
pub(crate) const SCENE_TERRAIN_KIND_TRANSLUCENT: u8 = 2;

/// One facing group of a resident section mesh: the unit a camera facing
/// mask selects and one indirect command draws.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct SceneTerrainGroup {
    /// Bit in `terrain_visible_facing_mask` that admits this group.
    pub(crate) facing_mask: u8,
    pub(crate) kind: u8,
    pub(crate) cull_policy: u32,
    pub(crate) winding: u32,
    /// Absolute first index in the page's index buffer.
    pub(crate) first_index: u32,
    pub(crate) index_count: u32,
}

/// Describes every group of a mesh resident at `index_base` bytes into its
/// page's index buffer, or `None` when a section cannot be drawn by the
/// scene path (its mesh then stays on the general path).
pub(crate) fn scene_terrain_groups(
    asset_sections: &[WorldMeshSection],
    source_sections: &[SourceTerrainMeshSection],
    index_base: u64,
) -> Option<Arc<[SceneTerrainGroup]>> {
    if asset_sections.len() != source_sections.len() || source_sections.is_empty() {
        return None;
    }
    asset_sections
        .iter()
        .zip(source_sections)
        .map(|(asset, source)| {
            let kind = match source.material_mode {
                WORLD_MATERIAL_MODE_OPAQUE => SCENE_TERRAIN_KIND_OPAQUE,
                WORLD_MATERIAL_MODE_CUTOUT => SCENE_TERRAIN_KIND_CUTOUT,
                WORLD_MATERIAL_MODE_TRANSLUCENT => SCENE_TERRAIN_KIND_TRANSLUCENT,
                _ => return None,
            };
            if asset.source_facing > 6 || asset.index_count != source.index_count || source.index_count == 0 {
                return None;
            }
            let offset = index_base.checked_add(source_draw_index_offset(source.index_offset, None))?;
            if offset % 4 != 0 {
                return None;
            }
            Some(SceneTerrainGroup {
                facing_mask: 1u8 << asset.source_facing,
                kind,
                cull_policy: asset.cull_policy,
                winding: asset.winding,
                first_index: u32::try_from(offset / 4).ok()?,
                index_count: source.index_count,
            })
        })
        .collect()
}

/// Whether a frame instance is camera-visible static chunk terrain the scene
/// path may draw (camera-sorted and outline-only instances keep their paths).
pub(crate) fn is_scene_terrain_instance(instance: &WorldMeshInstanceRequest) -> bool {
    instance.stratum == WORLD_STRATUM_TERRAIN
        && instance.mesh_section_index == WORLD_MESH_SECTION_ALL
        && instance.flags
            & (WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS
                | WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY
                | WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY)
            == 0
}

/// Whether a frame instance is an off-camera static terrain shadow caster
/// the scene path may draw.
pub(crate) fn is_scene_terrain_caster(instance: &WorldMeshInstanceRequest) -> bool {
    instance.stratum == WORLD_STRATUM_TERRAIN
        && instance.mesh_section_index == WORLD_MESH_SECTION_ALL
        && instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY != 0
        && instance.flags & (WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS | WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY) == 0
}

/// The frame instances (frame indices, ascending) the scene path draws.
#[derive(Clone, Debug, Default)]
pub(crate) struct SceneTerrainFrame {
    /// Camera-visible sections.
    pub(crate) camera: Vec<usize>,
    /// Off-camera shadow casters, before the light-frustum test.
    pub(crate) casters: Vec<usize>,
}

impl SceneTerrainFrame {
    /// Whether `index` is drawn by the scene path; `cursor` walks a sorted
    /// index sequence, so a caller iterating in frame order pays O(1) each.
    pub(crate) fn excludes(&self) -> impl FnMut(usize) -> bool + '_ {
        let mut camera = self.camera.iter().copied().peekable();
        let mut casters = self.casters.iter().copied().peekable();
        move |index| {
            while camera.peek().is_some_and(|&next| next < index) {
                camera.next();
            }
            while casters.peek().is_some_and(|&next| next < index) {
                casters.next();
            }
            camera.peek() == Some(&index) || casters.peek() == Some(&index)
        }
    }
}

/// Per-kind camera pass inputs, indexed by `SCENE_TERRAIN_KIND_*`.
pub(crate) struct SceneTerrainPass<'a> {
    pub(crate) program: &'a LoweredTerrainSourceProgram,
    pub(crate) resources: &'a TerrainSourceOwnedResourceSet,
    pub(crate) color_formats: &'a [TextureFormat],
    pub(crate) uniforms: &'a PreparedSourceTerrainUniforms<'a>,
}

pub(crate) struct SceneTerrainShadowPass<'a> {
    pub(crate) program: &'a LoweredTerrainSourceProgram,
    pub(crate) resources: &'a TerrainSourceOwnedResourceSet,
    pub(crate) uniforms: &'a PreparedSourceTerrainUniforms<'a>,
    pub(crate) alpha_cutoff: Option<f32>,
    /// Per kind: whether it casts (translucent follows the pack's policy).
    pub(crate) casting_kinds: [bool; 3],
    /// Off-camera faces of camera sections and light-frustum casters are
    /// drawn only for an overworld shadow pass.
    pub(crate) supplement: bool,
}

/// Index totals per kind drawn by the scene path; the scene's share of the
/// frame's terrain coverage receipt.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(crate) struct SceneTerrainCoverage {
    pub(crate) indices: [u64; 3],
}

/// Everything the scene path drew this frame.
#[derive(Default)]
pub(crate) struct SceneTerrainDraws {
    pub(crate) camera: Vec<TerrainMeshDraw>,
    pub(crate) shadow_only: Vec<TerrainShadowMeshDraw>,
    pub(crate) coverage: SceneTerrainCoverage,
}

#[derive(Clone, Copy, Eq, PartialEq)]
struct RunKey {
    kind: u8,
    cull_policy: u32,
    winding: u32,
    page: Handle,
    index_buffer: Handle,
}

struct Run {
    key: RunKey,
    commands: Vec<PageIndexedDrawCommand>,
    index_count: u64,
}

/// Groups commands into runs: opaque and cutout by state and page in any
/// order; translucent in submission order, starting a run on any change.
#[derive(Default)]
struct RunBuilder {
    runs: Vec<Run>,
    last_translucent: Option<usize>,
}

impl RunBuilder {
    fn push(&mut self, key: RunKey, command: PageIndexedDrawCommand) {
        let run = if key.kind == SCENE_TERRAIN_KIND_TRANSLUCENT {
            match self.last_translucent.filter(|&run| self.runs[run].key == key) {
                Some(run) => run,
                None => {
                    self.runs.push(Run { key, commands: Vec::new(), index_count: 0 });
                    self.last_translucent = Some(self.runs.len() - 1);
                    self.runs.len() - 1
                }
            }
        } else {
            match self.runs.iter().position(|run| run.key == key) {
                Some(run) => run,
                None => {
                    self.runs.push(Run { key, commands: Vec::new(), index_count: 0 });
                    self.runs.len() - 1
                }
            }
        };
        let run = &mut self.runs[run];
        run.index_count += u64::from(command.index_count);
        run.commands.push(command);
    }

    /// Runs in draw order: opaque, cutout, then translucent in submission order.
    fn finish(mut self) -> Vec<Run> {
        self.runs.sort_by_key(|run| run.key.kind);
        self.runs
    }
}

impl WorldPrimitiveFrontend {
    /// Whether this mesh is described and resident, so the scene path can
    /// draw it this frame without the general path.
    pub(crate) fn scene_terrain_mesh_ready(&self, instance: &WorldMeshInstanceRequest, material_ids: bool) -> bool {
        let Some(record) = self.retained_source_terrain_meshes.get(&instance.mesh_key) else {
            return false;
        };
        record.generation == instance.mesh_generation
            && record.material_ids == material_ids
            && record.groups.is_some()
            && (self.pending_lowered_source_terrain_geometry_uploads.is_empty()
                || !self.pending_lowered_source_terrain_geometry_uploads.contains_key(&LoweredSourceTerrainDataKey {
                    mesh_key: instance.mesh_key,
                    mesh_generation: instance.mesh_generation,
                    abi: SourceGeometryAbi::Terrain,
                }))
    }

    /// The instances the scene path draws this frame. Everything else stays
    /// with the general batch path.
    pub(crate) fn partition_scene_terrain_instances(
        &self,
        gal: &VulkanicGal,
        frame: &WorldPrimitiveFrame,
    ) -> SceneTerrainFrame {
        let mut scene = SceneTerrainFrame::default();
        if !source_terrain_multidraw_enabled()
            || !gal.capabilities().supports(BackendFeature::IndirectDraw)
            || crate::core::environment::var_os("MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE").is_some()
        {
            return scene;
        }
        let material_ids = self.source_terrain_material_ids_active();
        for (index, instance) in frame.mesh_instances.iter().enumerate() {
            let list = if is_scene_terrain_instance(instance) {
                &mut scene.camera
            } else if is_scene_terrain_caster(instance) {
                &mut scene.casters
            } else {
                continue;
            };
            if instance.transform.iter().all(|component| component.is_finite())
                && self.scene_terrain_mesh_ready(instance, material_ids)
            {
                list.push(index);
            }
        }
        scene
    }

    fn scene_terrain_groups_of(&self, instance: &WorldMeshInstanceRequest) -> Option<&Arc<[SceneTerrainGroup]>> {
        self.retained_source_terrain_meshes
            .get(&instance.mesh_key)
            .and_then(|record| record.groups.as_ref())
    }

    /// Material kinds whose groups the camera sections draw this frame
    /// (facing-selected), indexed by `SCENE_TERRAIN_KIND_*`.
    pub(crate) fn scene_terrain_kinds(&self, frame: &WorldPrimitiveFrame, camera: &[usize]) -> [bool; 3] {
        let mut kinds = [false; 3];
        for &index in camera {
            let instance = &frame.mesh_instances[index];
            if let Some(groups) = self.scene_terrain_groups_of(instance) {
                for group in groups.iter().filter(|group| instance.terrain_visible_facing_mask & group.facing_mask != 0) {
                    kinds[group.kind as usize] = true;
                }
            }
            if kinds == [true; 3] {
                break;
            }
        }
        kinds
    }

    /// Upper bound on the indirect commands the scene path appends: camera
    /// groups twice (camera and supplement faces) and every caster group.
    pub(crate) fn scene_terrain_command_bound(&self, frame: &WorldPrimitiveFrame, scene: &SceneTerrainFrame) -> u64 {
        let groups = |indices: &[usize]| -> u64 {
            indices
                .iter()
                .filter_map(|&index| self.scene_terrain_groups_of(&frame.mesh_instances[index]))
                .map(|groups| groups.len() as u64)
                .sum()
        };
        2 * groups(&scene.camera) + groups(&scene.casters)
    }

    /// Draws the scene's sections: one instance record each (camera sections,
    /// then `casters`), camera runs with their shadow twins, and shadow-only
    /// runs for the camera sections' unselected faces and for `casters`
    /// (already admitted by the light frustum).
    pub(crate) fn prepare_scene_terrain_draws(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        camera: &[usize],
        casters: &[usize],
        passes: [Option<&SceneTerrainPass<'_>>; 3],
        shadow: Option<&SceneTerrainShadowPass<'_>>,
    ) -> GalResult<SceneTerrainDraws> {
        let mut drawn = SceneTerrainDraws::default();
        let casters = if shadow.is_some_and(|shadow| shadow.supplement) { casters } else { &[] };
        if camera.is_empty() && casters.is_empty() {
            return Ok(drawn);
        }
        let frame_id = frame.frame_id;
        if self.source_terrain_multidraw_frame != Some(frame_id) {
            return Err(GalError::backend("scene terrain draws require an armed multi-draw frame"));
        }
        let programs = passes.iter().flatten().map(|pass| pass.program).chain(shadow.map(|shadow| shadow.program));
        for program in programs {
            if program.execution_interface.instance_stride as usize != TERRAIN_SOURCE_INSTANCE_BYTES {
                return Err(GalError::invalid_argument(
                    "scene terrain programs must share the terrain instance record layout",
                ));
            }
        }

        // Shared uniform blocks of every pass the runs use, staged once.
        let mut pass_uniforms = [None; 3];
        for (kind, pass) in passes.iter().enumerate() {
            if let Some(pass) = pass {
                pass_uniforms[kind] = Some(self.stage_source_terrain_pass_uniforms(
                    gal,
                    frame_id,
                    pass.program,
                    pass.uniforms.legacy_texture_transforms(),
                    pass.uniforms.scalar_uniforms(),
                )?);
            }
        }
        let shadow_uniforms = match shadow {
            Some(shadow) => Some(self.stage_source_terrain_pass_uniforms(
                gal,
                frame_id,
                shadow.program,
                shadow.uniforms.legacy_texture_transforms(),
                shadow.uniforms.scalar_uniforms(),
            )?),
            None => None,
        };

        // One record per section, camera sections first, in frame order.
        let mut records = std::mem::take(&mut self.retained_source_terrain_instance_scratch);
        records.clear();
        records.reserve((camera.len() + casters.len()) * TERRAIN_SOURCE_INSTANCE_BYTES);
        for &index in camera.iter().chain(casters) {
            let instance = &frame.mesh_instances[index];
            for component in instance.transform {
                push_f32(&mut records, component);
            }
            for component in argb_to_rgba(instance.color_argb) {
                push_f32(&mut records, component);
            }
        }
        let anchor = passes
            .iter()
            .zip(pass_uniforms)
            .find_map(|(pass, uniforms)| Some((pass.as_ref()?.program, uniforms?)))
            .or_else(|| Some((shadow?.program, shadow_uniforms?)));
        let staged = match anchor {
            Some((program, uniforms)) => {
                self.stage_source_terrain_instance_block(gal, frame_id, program, uniforms, &records)
            }
            None => Err(GalError::invalid_argument("scene terrain draws require a camera or shadow pass")),
        };
        self.retained_source_terrain_instance_scratch = records;
        let (stream_buffer, instance_range, base_instance) = staged?;

        let mut camera_runs = RunBuilder::default();
        let mut shadow_runs = RunBuilder::default();
        let casting_kinds = shadow.map_or([false; 3], |shadow| shadow.casting_kinds);
        let supplement = shadow.is_some_and(|shadow| shadow.supplement);
        for (ordinal, &index) in camera.iter().chain(casters).enumerate() {
            let instance = &frame.mesh_instances[index];
            let record = self
                .retained_source_terrain_meshes
                .get(&instance.mesh_key)
                .ok_or_else(|| GalError::backend("scene terrain mesh record vanished during the frame"))?;
            let groups = record.groups.as_ref().expect("partition admits only described meshes");
            let first_instance = base_instance + ordinal as u32;
            let is_caster = ordinal >= camera.len();
            let (camera_mask, shadow_mask) = if is_caster {
                (0, 0x7f)
            } else if supplement {
                (instance.terrain_visible_facing_mask, !instance.terrain_visible_facing_mask & 0x7f)
            } else {
                (instance.terrain_visible_facing_mask, 0)
            };
            for group in groups.iter() {
                let key = RunKey {
                    kind: group.kind,
                    cull_policy: group.cull_policy,
                    winding: group.winding,
                    page: record.page,
                    index_buffer: record.index_buffer,
                };
                let command = PageIndexedDrawCommand {
                    index_count: group.index_count,
                    instance_count: 1,
                    first_index: group.first_index,
                    vertex_offset: 0,
                    first_instance,
                };
                if camera_mask & group.facing_mask != 0 {
                    if passes[group.kind as usize].is_none() {
                        return Err(GalError::unsupported_feature(
                            "scene terrain section uses a material kind without an admitted program",
                        ));
                    }
                    camera_runs.push(key, command);
                } else if shadow_mask & group.facing_mask != 0 && casting_kinds[group.kind as usize] {
                    shadow_runs.push(key, command);
                }
            }
        }

        for run in camera_runs.finish() {
            let kind = run.key.kind as usize;
            let pass = passes[kind].expect("checked while building runs");
            let (legacy_offset, scalar_offset) = pass_uniforms[kind].expect("staged with its pass");
            let set = self.source_terrain_page_frame_data_set(
                gal,
                frame_id,
                pass.program,
                run.key.page,
                stream_buffer,
                instance_range,
            )?;
            let (pack_set, pipeline, pipeline_layout) = self.retained_source_terrain_draw_state(
                gal,
                pass.program,
                pass.resources,
                scene_kind_material_mode(run.key.kind),
                run.key.cull_policy,
                run.key.winding,
                Some(pass.color_formats),
                None,
            )?;
            let index_count = u32::try_from(run.index_count)
                .map_err(|_| GalError::invalid_argument("scene terrain run index count exceeds u32"))?;
            let indexed_indirect = self.append_source_terrain_multidraw_commands(frame_id, &run.commands)?;
            let shadow_draw = match (shadow, shadow_uniforms) {
                (Some(shadow), Some(uniforms)) if casting_kinds[kind] => {
                    Some(self.scene_terrain_shadow_draw(gal, frame_id, shadow, uniforms, &run, stream_buffer, instance_range)?)
                }
                _ => None,
            };
            let shadow_participation = if shadow_draw.is_some() {
                TerrainShadowParticipation::Required
            } else {
                TerrainShadowParticipation::Unavailable
            };
            drawn.coverage.indices[kind] += run.index_count;
            drawn.camera.push(TerrainMeshDraw {
                shadow: shadow_draw,
                pipeline,
                offscreen_pipeline: None,
                pipeline_layout,
                resource_set: set,
                resource_set_dynamic_offsets: scene_dynamic_offsets(legacy_offset, scalar_offset),
                shader_resource_set: Some(TerrainShaderResourceSet { set_index: 1, set: pack_set }),
                index_buffer: run.key.index_buffer,
                index_offset: 0,
                index_type: IndexType::U32,
                index_count,
                instance_count: 1,
                indexed_indirect: Some(indexed_indirect),
                stratum: WORLD_STRATUM_TERRAIN,
                material_mode: terrain_material_pass_mode(scene_kind_material_mode(run.key.kind))?,
                shadow_participation,
            });
        }
        if let (Some(shadow), Some(uniforms)) = (shadow, shadow_uniforms) {
            for run in shadow_runs.finish() {
                let draw = self.scene_terrain_shadow_draw(gal, frame_id, shadow, uniforms, &run, stream_buffer, instance_range)?;
                let index_count = u32::try_from(run.index_count)
                    .map_err(|_| GalError::invalid_argument("scene terrain run index count exceeds u32"))?;
                let indexed_indirect = self.append_source_terrain_multidraw_commands(frame_id, &run.commands)?;
                drawn.shadow_only.push(TerrainShadowMeshDraw {
                    shadow: draw,
                    index_buffer: run.key.index_buffer,
                    index_offset: 0,
                    index_type: IndexType::U32,
                    index_count,
                    instance_count: 1,
                    indexed_indirect: Some(indexed_indirect),
                    material_mode: terrain_material_pass_mode(scene_kind_material_mode(run.key.kind))?,
                });
            }
        }
        Ok(drawn)
    }

    /// The shadow program's binding for one run's geometry page.
    #[allow(clippy::too_many_arguments)]
    fn scene_terrain_shadow_draw(
        &mut self,
        gal: &mut VulkanicGal,
        frame_id: u64,
        shadow: &SceneTerrainShadowPass<'_>,
        (legacy_offset, scalar_offset): (u64, Option<u64>),
        run: &Run,
        stream_buffer: Handle,
        instance_range: u64,
    ) -> GalResult<TerrainShadowDraw> {
        let set = self.source_terrain_page_frame_data_set(
            gal,
            frame_id,
            shadow.program,
            run.key.page,
            stream_buffer,
            instance_range,
        )?;
        let (pack_set, pipeline, pipeline_layout) = self.retained_source_terrain_draw_state(
            gal,
            shadow.program,
            shadow.resources,
            scene_kind_material_mode(run.key.kind),
            run.key.cull_policy,
            run.key.winding,
            None,
            shadow.alpha_cutoff,
        )?;
        Ok(TerrainShadowDraw {
            pipeline,
            pipeline_layout,
            resource_set: set,
            resource_set_dynamic_offsets: scene_dynamic_offsets(legacy_offset, scalar_offset),
            shader_resource_set: Some(TerrainShaderResourceSet { set_index: 1, set: pack_set }),
        })
    }
}

fn scene_kind_material_mode(kind: u8) -> u32 {
    match kind {
        SCENE_TERRAIN_KIND_OPAQUE => WORLD_MATERIAL_MODE_OPAQUE,
        SCENE_TERRAIN_KIND_CUTOUT => WORLD_MATERIAL_MODE_CUTOUT,
        _ => WORLD_MATERIAL_MODE_TRANSLUCENT,
    }
}

/// Set-zero dynamic offsets in the order the retained bindings use: legacy
/// uniforms, scalar uniforms (if any), then the instance stream from zero.
fn scene_dynamic_offsets(legacy: u64, scalar: Option<u64>) -> SmallVec<[u64; 3]> {
    let mut offsets = SmallVec::new();
    offsets.push(legacy);
    if let Some(scalar) = scalar {
        offsets.push(scalar);
    }
    offsets.push(0);
    offsets
}

#[cfg(test)]
mod tests {
    use super::*;

    fn asset_section(facing: u32, index_count: u32) -> WorldMeshSection {
        WorldMeshSection {
            material_id: 0,
            texture_id: 0,
            material_mode: WORLD_MATERIAL_MODE_OPAQUE,
            cull_policy: 1,
            winding: 0,
            index_offset: 0,
            index_count,
            source_facing: facing,
        }
    }

    fn source_section(material_mode: u32, index_offset: u64, index_count: u32) -> SourceTerrainMeshSection {
        SourceTerrainMeshSection {
            material_id: 0,
            texture_id: 0,
            material_mode,
            cull_policy: 1,
            winding: 0,
            index_offset,
            index_count,
        }
    }

    #[test]
    fn groups_place_every_facing_at_its_absolute_page_index() {
        let groups = scene_terrain_groups(
            &[asset_section(0, 6), asset_section(6, 12)],
            &[
                source_section(WORLD_MATERIAL_MODE_CUTOUT, 0, 6),
                source_section(WORLD_MATERIAL_MODE_TRANSLUCENT, 24, 12),
            ],
            400,
        )
        .expect("describable");
        assert_eq!(
            &[
                SceneTerrainGroup {
                    facing_mask: 1,
                    kind: SCENE_TERRAIN_KIND_CUTOUT,
                    cull_policy: 1,
                    winding: 0,
                    first_index: 100,
                    index_count: 6,
                },
                SceneTerrainGroup {
                    facing_mask: 1 << 6,
                    kind: SCENE_TERRAIN_KIND_TRANSLUCENT,
                    cull_policy: 1,
                    winding: 0,
                    first_index: 106,
                    index_count: 12,
                },
            ][..],
            &groups[..]
        );
    }

    #[test]
    fn meshes_the_scene_cannot_draw_keep_the_general_path() {
        // Mismatched section tables, unsupported material modes, counts that
        // disagree and unaligned bases are all rejected.
        assert!(scene_terrain_groups(&[asset_section(0, 6)], &[], 0).is_none());
        assert!(scene_terrain_groups(&[asset_section(0, 6)], &[source_section(99, 0, 6)], 0).is_none());
        assert!(scene_terrain_groups(&[asset_section(0, 6)], &[source_section(WORLD_MATERIAL_MODE_OPAQUE, 0, 3)], 0)
            .is_none());
        assert!(scene_terrain_groups(&[asset_section(0, 6)], &[source_section(WORLD_MATERIAL_MODE_OPAQUE, 0, 6)], 2)
            .is_none());
        assert!(scene_terrain_groups(&[asset_section(7, 6)], &[source_section(WORLD_MATERIAL_MODE_OPAQUE, 0, 6)], 0)
            .is_none());
    }
}
