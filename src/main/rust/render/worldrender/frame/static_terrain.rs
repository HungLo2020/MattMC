//! Admission of the frame's compact static terrain.
//!
//! Java copies only each section layer's mesh identity, integer origin and
//! layer policy: camera-visible sections in draw order, and off-camera shadow
//! casters. The frontend expands the resident ones into the terrain instances
//! the per-record stream used to carry: camera sections before every other
//! instance, casters after them, so later stages see one instance list.
//!
//! Each section draws the generation Rust has acknowledged for its key. When a
//! newer copied generation has not crossed yet, the previous acknowledged
//! generation keeps drawing (no terrain hole while an upload is pending); a key
//! with no acknowledged asset is skipped until it is uploaded.

use crate::render::worldrender::terrain::placement::TerrainSectionPlacement;
use crate::render::worldrender::*;

impl WorldPrimitiveFrontend {
    /// Drains the frame's compact camera sections and shadow casters into
    /// `frame.mesh_instances`.
    pub(crate) fn admit_static_terrain(&self, frame: &mut WorldPrimitiveFrame) -> GalResult<()> {
        let sections = std::mem::take(&mut frame.static_terrain_sections);
        expand_static_terrain_sections(frame, &sections, |mesh_key| {
            self.mesh_asset_drawable_generations.get(&mesh_key).map(|&(generation, _)| generation)
        })?;
        self.admit_static_terrain_shadow_casters(frame)
    }

    fn admit_static_terrain_shadow_casters(
        &self,
        frame: &mut WorldPrimitiveFrame,
    ) -> GalResult<()> {
        let mut casters = std::mem::take(&mut frame.static_terrain_shadow_casters);
        // Java's section-map order shifts as terrain streams; key order keeps
        // identical caster sets identical for the cached batch plans.
        casters.casters.sort_unstable_by_key(|caster| caster.mesh_key);
        expand_static_terrain_shadow_casters(frame, &casters, |mesh_key| {
            self.mesh_asset_drawable_generations.get(&mesh_key).map(|&(generation, _)| generation)
        })
    }
}

/// Inserts the resident camera sections, in order, before every other frame
/// instance: the position their per-record stream used to occupy.
pub(in crate::render::worldrender) fn expand_static_terrain_sections(
    frame: &mut WorldPrimitiveFrame,
    sections: &StaticTerrainSections,
    resident_generation: impl Fn(u64) -> Option<u64>,
) -> GalResult<()> {
    if sections.sections.is_empty() {
        return Ok(());
    }
    let mut expanded = Vec::with_capacity(sections.sections.len() + frame.mesh_instances.len());
    for section in &sections.sections {
        let Some(mesh_generation) = resident_generation(section.mesh_key) else {
            continue;
        };
        let placement = TerrainSectionPlacement {
            origin: section.origin,
            camera: sections.camera,
        };
        expanded.push(WorldMeshInstanceRequest {
            entity_culling: None,
            model_submission_order: None,
            item_foil: None,
            decal_foil: None,
            stratum: WORLD_STRATUM_TERRAIN,
            mesh_key: section.mesh_key,
            mesh_generation,
            mesh_section_index: WORLD_MESH_SECTION_ALL,
            terrain_visible_facing_mask: placement.visible_facing_mask(),
            depth_policy: section.depth_policy,
            cull_policy: WORLD_CULL_BACK,
            winding: WORLD_WINDING_CCW,
            color_argb: 0xFFFF_FFFF,
            entity_id: 0,
            entity_color_argb: 0,
            packed_light: 0,
            outline_color_argb: 0,
            flags: section.flags,
            block_entity_id: -1,
            transform: placement.lower()?,
            viewport_width: frame.viewport_width,
            viewport_height: frame.viewport_height,
        });
    }
    expanded.append(&mut frame.mesh_instances);
    frame.mesh_instances = expanded;
    Ok(())
}

pub(in crate::render::worldrender) fn expand_static_terrain_shadow_casters(
    frame: &mut WorldPrimitiveFrame,
    casters: &StaticTerrainShadowCasters,
    resident_generation: impl Fn(u64) -> Option<u64>,
) -> GalResult<()> {
    if casters.casters.is_empty() {
        return Ok(());
    }
    frame.mesh_instances.reserve(casters.casters.len());
    for caster in &casters.casters {
        let Some(mesh_generation) = resident_generation(caster.mesh_key) else {
            continue;
        };
        let placement = TerrainSectionPlacement {
            origin: caster.origin,
            camera: casters.camera,
        };
        frame.mesh_instances.push(WorldMeshInstanceRequest {
            entity_culling: None,
            model_submission_order: None,
            item_foil: None,
            decal_foil: None,
            stratum: WORLD_STRATUM_TERRAIN,
            mesh_key: caster.mesh_key,
            mesh_generation,
            mesh_section_index: WORLD_MESH_SECTION_ALL,
            // Shadow selections draw every facing of an off-camera caster
            // (batching forces 0x7f); a fixed mask also keeps plan keys stable.
            terrain_visible_facing_mask: 0x7f,
            depth_policy: caster.depth_policy,
            cull_policy: WORLD_CULL_BACK,
            winding: WORLD_WINDING_CCW,
            color_argb: 0xFFFF_FFFF,
            entity_id: 0,
            entity_color_argb: 0,
            packed_light: 0,
            outline_color_argb: 0,
            flags: WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY,
            block_entity_id: -1,
            transform: placement.lower()?,
            viewport_width: frame.viewport_width,
            viewport_height: frame.viewport_height,
        });
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn camera_sections_expand_in_order_before_other_instances() {
        let camera = [100.75, 70.5, -3000.5];
        let mut frame = crate::render::worldrender::tests::frame(Vec::new());
        let existing = frame.mesh_instances.len();
        let section = |mesh_key, origin, flags| StaticTerrainSection {
            mesh_key,
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            origin,
            flags,
        };
        let sections = StaticTerrainSections {
            camera,
            sections: vec![
                section(21, [96, 64, -3008], 0),
                section(22, [0, 0, 0], 0),
                section(23, [112, 48, -2992], WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS),
            ],
        };
        // Key 22 has never crossed; key 23 still draws its previous generation.
        expand_static_terrain_sections(&mut frame, &sections, |key| match key {
            21 => Some(5),
            23 => Some(4),
            _ => None,
        })
        .unwrap();
        assert_eq!(frame.mesh_instances.len(), existing + 2);
        for (instance, (key, generation, origin, flags)) in frame.mesh_instances.iter().zip([
            (21, 5, [96, 64, -3008], 0),
            (23, 4, [112, 48, -2992], WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS),
        ]) {
            let placement = TerrainSectionPlacement { origin, camera };
            assert_eq!((instance.mesh_key, instance.mesh_generation, instance.flags), (key, generation, flags));
            assert_eq!(instance.stratum, WORLD_STRATUM_TERRAIN);
            assert_eq!(instance.transform, placement.lower().unwrap());
            assert_eq!(instance.terrain_visible_facing_mask, placement.visible_facing_mask());
            assert_eq!((instance.cull_policy, instance.winding), (WORLD_CULL_BACK, WORLD_WINDING_CCW));
            assert_eq!((instance.color_argb, instance.block_entity_id), (0xFFFF_FFFF, -1));
            assert_eq!(instance.mesh_section_index, WORLD_MESH_SECTION_ALL);
        }
    }

    #[test]
    fn casters_expand_to_resident_shadow_only_terrain_after_existing_instances() {
        let mut frame = crate::render::worldrender::tests::frame(Vec::new());
        let camera = [100.75, -20.25, -3000.5];
        let caster = |mesh_key, origin| StaticTerrainShadowCaster {
            mesh_key,
            mesh_generation: 7,
            origin,
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
        };
        let casters = StaticTerrainShadowCasters {
            camera,
            casters: vec![caster(11, [96, -32, -3008]), caster(12, [0, 0, 0]), caster(13, [112, -16, -2992])],
        };
        // Key 12 has never crossed; key 13 still holds its previous generation.
        expand_static_terrain_shadow_casters(&mut frame, &casters, |key| match key {
            11 => Some(7),
            13 => Some(6),
            _ => None,
        })
        .unwrap();
        assert_eq!(frame.mesh_instances.len(), 2);
        for (instance, (key, generation, origin)) in frame.mesh_instances.iter().zip([
            (11, 7, [96, -32, -3008]),
            (13, 6, [112, -16, -2992]),
        ]) {
            let placement = TerrainSectionPlacement { origin, camera };
            assert_eq!(instance.mesh_key, key);
            assert_eq!(instance.mesh_generation, generation);
            assert_eq!(instance.stratum, WORLD_STRATUM_TERRAIN);
            assert_eq!(instance.flags, WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY);
            assert_eq!(instance.cull_policy, WORLD_CULL_BACK);
            assert_eq!(instance.mesh_section_index, WORLD_MESH_SECTION_ALL);
            assert_eq!(instance.block_entity_id, -1);
            assert_eq!(instance.transform, placement.lower().unwrap());
            assert_eq!(instance.terrain_visible_facing_mask, 0x7f);
            assert_eq!(
                (instance.viewport_width, instance.viewport_height),
                (frame.viewport_width, frame.viewport_height)
            );
        }
        assert!(frame.static_terrain_shadow_casters.casters.is_empty());
    }
}
