//! Resolve pinned item CPU owners once, before ordinary semantic validation.
use super::*;
use std::borrow::Cow;

pub(crate) unsafe fn resolve_item_pose(
    instance: &FfiWorldMeshInstanceRecord,
    first_person: bool,
) -> GalResult<Cow<'_, FfiWorldMeshInstanceRecord>> {
    let invalid = || GalError::invalid_argument("invalid native world item pose");
    if instance.native_item_transform == 0 {
        if instance.native_item_transform_mode != 0
            || instance.native_item_parent_properties != 0
            || instance
                .native_item_parent_normal
                .iter()
                .any(|v| v.to_bits() != 0)
            || instance.native_item_parent_trusted != 0
        {
            return Err(invalid());
        }
        return Ok(Cow::Borrowed(instance));
    }
    let mode = instance.native_item_transform_mode;
    if !(1..=4).contains(&mode)
        || (mode >= 3) != first_person
        || instance.native_item_transform
            % std::mem::align_of::<crate::render::items::Owner>() as u64
            != 0
        || instance.native_item_parent_properties & 2 == 0
        || instance.native_item_parent_properties & !31 != 0
        || instance.native_item_parent_trusted > 1
        || instance.terrain_placement_mode != 0
        || instance.flags & super::model_rigs::WORLD_MESH_INSTANCE_FLAG_MODEL_RIG != 0
        || ![
            WORLD_STRATUM_ENTITY_MESH,
            crate::render::scene::strata::WORLD_STRATUM_ENTITY_SHADOW_CASTER,
        ]
        .contains(&instance.stratum)
        || instance.block_entity_id < -1
        || !instance
            .transform
            .iter()
            .chain(&instance.native_item_parent_normal)
            .all(|v| v.is_finite() && v.abs() <= 1.0e10)
    {
        return Err(invalid());
    }
    if instance.decal_foil_mode != 0
        && (instance.decal_foil_mode != if first_person { 2 } else { 1 }
            || instance.decal_normal_mode != 0
            || instance.decal_model_pose.iter().any(|v| v.to_bits() != 0)
            || instance.decal_normal_pose.iter().any(|v| v.to_bits() != 0))
    {
        return Err(invalid());
    }
    // The caller pins this CPU owner through decode/async join. The frontend
    // receives only copied poses, never this pointer or its lifetime.
    let owner = unsafe { &*(instance.native_item_transform as *const crate::render::items::Owner) };
    if owner.safe_for_world == 0 {
        return Err(invalid());
    }
    let parent = crate::render::items::world_pose::ResolvedPose {
        model: instance.transform,
        normal: instance.native_item_parent_normal,
        trusted: instance.native_item_parent_trusted != 0,
    };
    let pose = owner
        .resolve(mode, parent, instance.native_item_parent_properties)
        .ok_or_else(invalid)?;
    let mut resolved = *instance;
    resolved.transform = pose.model;
    if resolved.decal_foil_mode != 0 {
        resolved.decal_model_pose = pose.model;
        resolved.decal_normal_pose = pose.normal;
        resolved.decal_normal_mode = u32::from(pose.trusted_normals == 0);
    }
    resolved.native_item_transform = 0;
    resolved.native_item_transform_mode = 0;
    resolved.native_item_parent_properties = 0;
    resolved.native_item_parent_normal = [0.; 9];
    resolved.native_item_parent_trusted = 0;
    if crate::render::shared::launch_configuration::mattmc_render_launch_configuration() & 2 != 0 {
        use std::sync::atomic::{AtomicU32, Ordering};
        static SEEN: AtomicU32 = AtomicU32::new(0);
        let bit = 1 << (mode - 1 + if instance.decal_foil_mode != 0 { 4 } else { 0 });
        if SEEN.fetch_or(bit, Ordering::Relaxed) & bit == 0 {
            crate::core::console::stderr(format_args!(
                "[MattMC items] native CPU pose decoded mode={mode} first_person={first_person} decal={}",
                instance.decal_foil_mode != 0
            ));
        }
    }
    Ok(Cow::Owned(resolved))
}
