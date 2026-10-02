//! Mesh item raster keys, shared programs and stream geometry residency.

use super::*;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::guirender::frontend) struct GuiMeshRasterKey {
    pub(in crate::render::guirender::frontend) asset_id: u64,
    pub(in crate::render::guirender::frontend) material_mode: GuiMeshMaterialMode,
    pub(in crate::render::guirender::frontend) front_face: crate::render::vulkanic::resources::FrontFace,
    /// The raster pass owns one frame uniform buffer. Its placement and
    /// lighting inputs are therefore part of the cache identity: reusing the
    /// same texture pipeline for a different PIP target must not overwrite an
    /// earlier mesh draw's extent before the command list executes.
    pub(in crate::render::guirender::frontend) render_extent: [u32; 2],
    pub(in crate::render::guirender::frontend) alpha_cutoff_bits: u32,
    pub(in crate::render::guirender::frontend) lighting_mode: GuiMeshLightingMode,
    /// Panorama is rasterized directly into the acquired frame target. Its
    /// pipeline must therefore be distinct from an otherwise-identical PIP
    /// raster whose attachment format is the private RGBA8 target.
    pub(in crate::render::guirender::frontend) direct_target_format: Option<TextureFormat>,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::guirender::frontend) struct GuiMeshSharedProgramKey {
    pub(in crate::render::guirender::frontend) color_format: TextureFormat,
    pub(in crate::render::guirender::frontend) depth_format: Option<TextureFormat>,
    pub(in crate::render::guirender::frontend) material_mode: GuiMeshMaterialMode,
    pub(in crate::render::guirender::frontend) front_face: crate::render::vulkanic::resources::FrontFace,
}

pub(in crate::render::guirender::frontend) fn gui_mesh_raster_key(draw: &GuiMeshPreparedDraw) -> GuiMeshRasterKey {
    GuiMeshRasterKey {
        asset_id: draw.asset_id,
        material_mode: draw.material_mode,
        front_face: draw.front_face,
        render_extent: draw.render_extent,
        alpha_cutoff_bits: draw.alpha_cutoff.to_bits(),
        lighting_mode: draw.lighting_mode,
        direct_target_format: None,
    }
}

pub(in crate::render::guirender::frontend) fn direct_gui_mesh_raster_key(
    draw: &GuiMeshPreparedDraw,
    target_format: TextureFormat,
) -> GuiMeshRasterKey {
    let mut key = gui_mesh_raster_key(draw);
    key.direct_target_format = Some(target_format);
    key
}

/// One private GUI-mesh stream allocation. It remains unavailable until the
/// submission that references it has completed, then can be reused by later
/// semantic mesh work without overwriting in-flight GPU reads.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::guirender::frontend) struct GuiMeshGeometryResidency {
    pub(in crate::render::guirender::frontend) stream: GuiMeshStreamRange,
    pub(in crate::render::guirender::frontend) vertex_bytes: u64,
    pub(in crate::render::guirender::frontend) index_bytes: u64,
    pub(in crate::render::guirender::frontend) usage: crate::render::vulkanic::commands::SubmissionUsage,
}

impl GuiFrontend {
    pub(in crate::render::guirender::frontend) fn reclaim_completed_mesh_geometry(&mut self, completed: SubmissionId) {
        let released: Vec<_> = self
            .mesh_geometry_cache
            .iter()
            .filter_map(|(key, residency)| {
                (!residency.usage.has_pending_commands()
                    && residency.usage.last_submission() <= completed)
                    .then(|| (*key, residency.clone()))
            })
            .collect();
        for (key, residency) in released {
            self.mesh_geometry_cache.remove(&key);
            self.mesh_geometry_free_ranges
                .entry(())
                .or_default()
                .push(residency);
        }
        for ranges in self.mesh_geometry_free_ranges.values_mut() {
            ranges.sort_by_key(|range| (range.stream.vertex_offset, range.stream.index_offset));
            let mut merged: Vec<GuiMeshGeometryResidency> = Vec::with_capacity(ranges.len());
            for range in ranges.drain(..) {
                if let Some(previous) = merged.last_mut() {
                    let vertex_end = previous.stream.vertex_offset + previous.vertex_bytes;
                    let index_end = previous.stream.index_offset + previous.index_bytes;
                    if vertex_end == range.stream.vertex_offset
                        && index_end == range.stream.index_offset
                    {
                        previous.vertex_bytes += range.vertex_bytes;
                        previous.index_bytes += range.index_bytes;
                        continue;
                    }
                }
                merged.push(range);
            }
            *ranges = merged;
        }
    }

    pub(in crate::render::guirender::frontend) fn allocate_mesh_geometry(
        &mut self,
        gal: &mut VulkanicGal,
        raster_key: GuiMeshRasterKey,
        vertex_bytes: u64,
        index_bytes: u64,
    ) -> GalResult<GuiMeshGeometryResidency> {
        self.mesh_geometry_free_ranges
            .entry(())
            .or_insert_with(|| {
                vec![GuiMeshGeometryResidency {
                    stream: GuiMeshStreamRange::default(),
                    vertex_bytes: crate::render::guirender::mesh::GUI_MESH_MAX_VERTEX_BYTES,
                    index_bytes: crate::render::guirender::mesh::GUI_MESH_MAX_INDEX_BYTES,
                    usage: crate::render::vulkanic::commands::SubmissionUsage::default(),
                }]
            });
        let index = self.mesh_geometry_free_ranges[&()]
            .iter()
            .position(|range| {
                range.vertex_bytes >= vertex_bytes && range.index_bytes >= index_bytes
            });
        let range = if let Some(index) = index {
            self.mesh_geometry_free_ranges
                .get_mut(&())
                .expect("range entry was initialized")
                .remove(index)
        } else {
            let oldest = self
                .mesh_geometry_cache
                .iter()
                .filter(|(_, residency)| !residency.usage.has_pending_commands())
                .map(|(_, residency)| residency.usage.last_submission())
                .min()
                .ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh geometry request exceeds the fixed stream capacity",
                    )
                })?;
            // This is bounded, explicit backpressure: wait for exactly the
            // oldest range that can make space, never overwrite an in-flight
            // stream and never grow the private buffers beyond their cap.
            gal.retire_through(oldest)?;
            self.reclaim_completed_mesh_geometry(oldest);
            return self.allocate_mesh_geometry(gal, raster_key, vertex_bytes, index_bytes);
        };
        let allocation = GuiMeshGeometryResidency {
            stream: range.stream,
            vertex_bytes,
            index_bytes,
            usage: crate::render::vulkanic::commands::SubmissionUsage::default(),
        };
        let remaining_vertex_bytes = range.vertex_bytes - vertex_bytes;
        let remaining_index_bytes = range.index_bytes - index_bytes;
        if remaining_vertex_bytes != 0 || remaining_index_bytes != 0 {
            self.mesh_geometry_free_ranges
                .get_mut(&())
                .expect("range entry remains initialized")
                .push(GuiMeshGeometryResidency {
                    stream: GuiMeshStreamRange {
                        vertex_offset: allocation.stream.vertex_offset + vertex_bytes,
                        index_offset: allocation.stream.index_offset + index_bytes,
                    },
                    vertex_bytes: remaining_vertex_bytes,
                    index_bytes: remaining_index_bytes,
                    usage: crate::render::vulkanic::commands::SubmissionUsage::default(),
                });
        }
        Ok(allocation)
    }

    pub(in crate::render::guirender::frontend) fn ensure_mesh_shared_program(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: TextureFormat,
        depth_format: Option<TextureFormat>,
        material_mode: GuiMeshMaterialMode,
        front_face: crate::render::vulkanic::resources::FrontFace,
    ) -> GalResult<GuiMeshSharedProgram> {
        let key = GuiMeshSharedProgramKey {
            color_format,
            depth_format,
            material_mode,
            front_face,
        };
        if let Some(program) = self.mesh_shared_programs.get(&key) {
            return Ok(*program);
        }
        if self.mesh_shared_programs.len() >= GUI_MAX_MESH_SHARED_PROGRAMS {
            return Err(GalError::unsupported_feature(format!(
                "GUI mesh shared-program cache exceeds bounded limit {}",
                GUI_MAX_MESH_SHARED_PROGRAMS
            )));
        }
        let program = GuiMeshSharedProgram::create(
            gal,
            &format!("minecraft.gui.mesh.program.{material_mode:?}.{front_face:?}"),
            color_format,
            depth_format,
            material_mode,
            front_face,
        )?;
        self.mesh_shared_programs.insert(key, program);
        Ok(program)
    }
}
