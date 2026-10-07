//! Frame entry points: standalone GUI submits and append-to-target variants.

use super::*;

impl GuiFrontend {
    pub fn submit_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
    ) -> GalResult<GuiSubmitStats> {
        self.submit_frame_with_affine_quads(gal, generation, frame_target, requests, Vec::new())
    }

    pub fn submit_frame_with_affine_quads(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
    ) -> GalResult<GuiSubmitStats> {
        self.submit_frame_with_affine_quads_and_mesh_batches(
            gal,
            generation,
            frame_target,
            requests,
            affine_quads,
            Vec::new(),
        )
    }

    pub fn submit_frame_with_affine_quads_and_mesh_batches(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
    ) -> GalResult<GuiSubmitStats> {
        self.submit_frame_with_tiled_quads(
            gal,
            generation,
            frame_target,
            requests,
            affine_quads,
            mesh_batches,
            Vec::new(),
        )
    }

    pub(crate) fn submit_frame_with_tiled_quads(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<GuiSubmitStats> {
        self.submit_frame_with_owned_atlases(
            gal,
            None,
            generation,
            frame_target,
            requests,
            affine_quads,
            mesh_batches,
            tiled_quads,
        )
    }

    pub(crate) fn submit_frame_with_owned_atlases(
        &mut self,
        gal: &mut VulkanicGal,
        world: Option<&mut (dyn GuiAtlasOwner + 'static)>,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<GuiSubmitStats> {
        validate_gui_frame_sequences(&requests, &affine_quads, &mesh_batches, &tiled_quads)?;
        let (ops, mut stats) = self.append_frame_ops_with_owned_atlases_to_target(
            gal,
            world,
            generation,
            frame_target,
            frame_target,
            None,
            None,
            None,
            false,
            requests,
            affine_quads,
            mesh_batches,
            tiled_quads,
        )?;
        let token = gal.submit(SubmissionBatch {
            label: "minecraft.gui.frame".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "minecraft.gui.frame.commands".to_string(),
                operations: ops,
            })],
        })?;
        stats.submission_id = token.submission.0;
        Ok(stats)
    }

    pub fn append_frame_ops(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_affine_quads(gal, generation, frame_target, requests, Vec::new())
    }

    pub fn append_frame_ops_with_affine_quads(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_affine_quads_to_target(
            gal,
            generation,
            frame_target,
            frame_target,
            None,
            None,
            None,
            false,
            requests,
            affine_quads,
        )
    }

    /// Ordered variant for the private standard-3D item family. Mesh items
    /// split ordinary GUI batches at their scheduler sequence so a 3D PIP
    /// composite can never jump ahead of text or sprites in the same stratum.
    pub(crate) fn append_frame_ops_with_blur_boundary(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        boundary_stratum: i32,
        blur_radius: i32,
        pre_present_y_flip: bool,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_tiled_blur_boundary(
            gal,
            generation,
            render_target,
            color_attachment,
            requests,
            affine_quads,
            mesh_batches,
            Vec::new(),
            boundary_stratum,
            blur_radius,
            pre_present_y_flip,
        )
    }

    pub(crate) fn append_frame_ops_with_tiled_blur_boundary(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
        boundary_stratum: i32,
        blur_radius: i32,
        pre_present_y_flip: bool,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_owned_atlases_and_blur_boundary(
            gal,
            None,
            generation,
            render_target,
            color_attachment,
            requests,
            affine_quads,
            mesh_batches,
            tiled_quads,
            boundary_stratum,
            blur_radius,
            pre_present_y_flip,
        )
    }

    pub(crate) fn append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_tiled_quads_to_target(
            gal,
            generation,
            render_target,
            color_attachment,
            render_pass,
            depth_attachment,
            depth_format,
            pre_present_y_flip,
            requests,
            affine_quads,
            mesh_batches,
            Vec::new(),
        )
    }

    pub(crate) fn append_frame_ops_with_tiled_quads_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_owned_atlases_to_target(
            gal,
            None,
            generation,
            render_target,
            color_attachment,
            render_pass,
            depth_attachment,
            depth_format,
            pre_present_y_flip,
            requests,
            affine_quads,
            mesh_batches,
            tiled_quads,
        )
    }

    /// Replays GUI semantics into a frame-local diagnostic target without
    /// placing the target in the normal frame-pass cache. The caller retires
    /// the returned pass after the capture submission completes.
    pub(crate) fn append_frame_ops_to_transient_diagnostic_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_to_transient_tiled_diagnostic_target(
            gal,
            generation,
            render_target,
            color_attachment,
            requests,
            affine_quads,
            mesh_batches,
            Vec::new(),
        )
    }

    pub(crate) fn append_frame_ops_to_transient_tiled_diagnostic_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        preflight_tiled_affine_count(&tiled_quads, affine_quads.len())?;
        validate_gui_frame_sequences(&requests, &affine_quads, &mesh_batches, &tiled_quads)?;
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.gui.diagnostic-frame.pass".to_string(),
            target: render_target,
            color_formats: vec![gal.pass_target_color_format(render_target)?],
            depth_format: None,
        })?;
        match self.append_frame_ops_with_tiled_quads_to_target(
            gal,
            generation,
            render_target,
            color_attachment,
            Some(pass),
            None,
            None,
            false,
            requests,
            affine_quads,
            mesh_batches,
            tiled_quads,
        ) {
            Ok((ops, mut stats)) => {
                stats.transient_diagnostic_passes.push(pass);
                Ok((ops, stats))
            }
            Err(error) => {
                let _ = gal.retire(pass);
                Err(error)
            }
        }
    }
}
