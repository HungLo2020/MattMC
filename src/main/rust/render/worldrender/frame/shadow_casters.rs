//! Off-camera static-terrain shadow casters.
//!
//! Java copies only each caster's resident mesh identity, section origin and
//! layer depth policy. The frontend expands resident casters into the same
//! shadow-only terrain instances the per-record stream used to carry, after the
//! camera-visible stream, so source shadow selection sees one instance list.

use crate::render::worldrender::terrain::placement::TerrainSectionPlacement;
use crate::render::worldrender::*;

impl WorldPrimitiveFrontend {
    /// Drains `frame.static_terrain_shadow_casters` into `frame.mesh_instances`.
    ///
    /// A caster draws the generation Rust has acknowledged for its key. When a
    /// newer copied generation has not crossed yet, the previous acknowledged
    /// generation keeps casting, as the retained Java active instance did; a key
    /// with no acknowledged asset is skipped until it is uploaded.
    pub(crate) fn admit_static_terrain_shadow_casters(
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
