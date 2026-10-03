//! Local orb geometry for the entity-lighting mesh path. Not admitted yet:
//! transport and the normal entity shader must be wired and paired against Frozen.

#[cfg(test)]
mod tests;
use crate::render::worldrender::*;

/// Immutable gameplay appearance. Model/camera transforms belong to the mesh
/// instance; no Java-expanded vertices, raster policy or native handles enter here.
#[derive(Clone, Copy, Debug)]
pub(crate) struct ExperienceOrbAppearance {
    pub icon: u32,
    pub red: u8,
    pub blue: u8,
    pub packed_light: u32,
}

/// Entity placement before the renderer-specific billboard recipe. Rust owns
/// the 0.1 vertical offset, camera-facing rotation and 0.3 model scale.
#[derive(Clone, Copy, Debug)]
pub(crate) struct ExperienceOrbPlacement {
    pub entity_transform: [f32; 16],
    pub camera_orientation: [f32; 4],
    pub entity_id: i32,
}

impl ExperienceOrbPlacement {
    pub(crate) fn instance(
        self,
        mesh_key: u64,
        mesh_generation: u64,
        viewport: [u32; 2],
    ) -> GalResult<WorldMeshInstanceRequest> {
        if mesh_key == 0
            || mesh_generation == 0
            || viewport
                .iter()
                .any(|&axis| axis == 0 || axis > crate::render::scene::SEMANTIC_MAX_VIEWPORT_AXIS as u32)
        {
            return Err(GalError::invalid_argument(
                "orb requires a mesh identity and bounded viewport",
            ));
        }
        if self
            .entity_transform
            .iter()
            .chain(self.camera_orientation.iter())
            .any(|v| !v.is_finite())
            || [
                self.entity_transform[3],
                self.entity_transform[7],
                self.entity_transform[11],
                self.entity_transform[15],
            ] != [0.0, 0.0, 0.0, 1.0]
        {
            return Err(GalError::invalid_argument(
                "orb placement requires a finite affine entity transform",
            ));
        }
        let [x, y, z, w] = self.camera_orientation;
        let (xx, yy, zz, ww) = (x * x, y * y, z * z, w * w);
        let norm = xx + yy + zz + ww;
        if !norm.is_finite() || norm <= 1.0e-8 {
            return Err(GalError::invalid_argument(
                "orb camera orientation must be nonzero and bounded",
            ));
        }
        // JOML Matrix4f.rotate(Quaternionfc), used by Frozen's PoseStack,
        // preserves quaternion magnitudes; do not substitute Vector3f's
        // normalized quaternion transform here.
        let (xy, xz, yz, xw, yw, zw) = (x * y, x * z, y * z, x * w, y * w, z * w);
        let local = [
            (ww + xx - yy - zz) * 0.3,
            2.0 * (xy + zw) * 0.3,
            2.0 * (xz - yw) * 0.3,
            0.0,
            2.0 * (xy - zw) * 0.3,
            (ww - xx + yy - zz) * 0.3,
            2.0 * (yz + xw) * 0.3,
            0.0,
            2.0 * (xz + yw) * 0.3,
            2.0 * (yz - xw) * 0.3,
            (ww - xx - yy + zz) * 0.3,
            0.0,
            0.0,
            0.1,
            0.0,
            1.0,
        ];
        let transform = matrix4_column_major_multiply(self.entity_transform, local);
        if transform.iter().any(|v| !v.is_finite()) {
            return Err(GalError::invalid_argument(
                "orb billboard transform overflow",
            ));
        }
        Ok(WorldMeshInstanceRequest {
            entity_culling: None,

            model_submission_order: None,
            item_foil: None,
            decal_foil: None,
            stratum: WORLD_STRATUM_ENTITY_MESH,
            mesh_key,
            mesh_generation,
            mesh_section_index: WORLD_MESH_SECTION_ALL,
            terrain_visible_facing_mask: 0x7f,
            // Frozen ITEM_ENTITY_TRANSLUCENT_CULL retains LEQUAL + depth writes.
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            cull_policy: WORLD_CULL_BACK,
            winding: WORLD_WINDING_CCW,
            color_argb: 0xffffffff,
            // The copied value is vanilla's runtime entity number, not a
            // shader-pack entity ID; the selected source resolves orbs from
            // `minecraft:experience_orb` and rejects any Java-supplied ID.
            entity_id: 0,
            entity_color_argb: 0,
            packed_light: 0,
            outline_color_argb: 0,
            flags: 0,
            block_entity_id: -1,
            transform,
            viewport_width: viewport[0],
            viewport_height: viewport[1],
        })
    }
}

impl ExperienceOrbAppearance {
    /// Build an explicit, Rust-owned mesh. Keys are GAL resource identities,
    /// never borrowed GPU handles. The caller owns lifetime and instance state.
    pub(crate) fn mesh(self, mesh_key: u64, mesh_generation: u64) -> GalResult<WorldMeshAsset> {
        let mesh = WorldMeshAsset {
            mesh_key,
            mesh_generation,
            vertex_layout_version: WORLD_MESH_VERTEX_LAYOUT_V2,
            index_type: IndexType::U16,
            vertices: self.vertices()?.to_vec(),
            index_bytes: INDICES.into_iter().flat_map(u16::to_le_bytes).collect(),
            sections: vec![WorldMeshSection {
                material_id: WORLD_MATERIAL_ID_TRANSLUCENT_CUTOUT_TEXTURED,
                texture_id: WORLD_MATERIAL_TEXTURE_EXPERIENCE_ORB,
                material_mode: WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT,
                cull_policy: WORLD_CULL_BACK,
                winding: WORLD_WINDING_CCW,
                index_offset: 0,
                index_count: 6,
                source_facing: 6,
            }],
            entity_identity: "minecraft:experience_orb".to_owned(),
        };
        validate_mesh_asset(&mesh)?;
        Ok(mesh)
    }

    pub(in crate::render::worldrender) fn vertices(self) -> GalResult<[WorldMeshVertex; 4]> {
        // Vanilla ExperienceOrb.getIcon selects 0..10 from its 4-column sheet.
        if self.icon > 10 {
            return Err(GalError::invalid_argument(
                "unsupported experience orb icon",
            ));
        }
        let u = (self.icon % 4) as f32 * 0.25;
        let v = (self.icon / 4) as f32 * 0.25;
        let color = 0x8000_ff00 | (self.red as u32) << 16 | self.blue as u32;
        Ok([
            ([-0.5, -0.25, 0.0], [u, v + 0.25]),
            ([0.5, -0.25, 0.0], [u + 0.25, v + 0.25]),
            ([0.5, 0.75, 0.0], [u + 0.25, v]),
            ([-0.5, 0.75, 0.0], [u, v]),
        ]
        .map(|(position, uv)| WorldMeshVertex {
            position,
            uv,
            shader_atlas_uv: uv,
            shader_block_id: -1,
            shader_material_type: 0,
            terrain_material_bits: 0,
            mid_block_packed: 0,
            color_argb: color,
            // Frozen submits (0,1,0), NOT the geometric +Z face normal.
            // The entity mesh instance transforms this for directional lighting.
            normal_packed: 0x0000_7f00,
            light: self.packed_light,
        }))
    }
}

pub(in crate::render::worldrender) const INDICES: [u16; 6] = [0, 1, 2, 2, 3, 0];

