use super::*;
use crate::content::block::{BlockRegistry, FluidKind, OffsetType, StateFlags, StateId};

pub(super) type StaticModelTable = Vec<Option<Vec<StaticModelQuadRecord>>>;
pub(super) type NativeModelSelectorTable = Vec<Option<NativeModelSelector>>;
pub(super) type NativeMeshingStateTable = Vec<Option<NativeMeshingState>>;

static STATIC_MODEL_CACHE: OnceLock<Mutex<StaticModelTable>> = OnceLock::new();
static NATIVE_MODEL_SELECTORS: OnceLock<Mutex<NativeModelSelectorTable>> = OnceLock::new();
static NATIVE_MESHING_STATES: OnceLock<Mutex<NativeMeshingStateTable>> = OnceLock::new();

pub(super) fn static_model_cache() -> &'static Mutex<StaticModelTable> {
    STATIC_MODEL_CACHE.get_or_init(|| Mutex::new(Vec::new()))
}

pub(super) fn native_model_selectors() -> &'static Mutex<NativeModelSelectorTable> {
    NATIVE_MODEL_SELECTORS.get_or_init(|| Mutex::new(Vec::new()))
}

pub(super) fn native_meshing_states() -> &'static Mutex<NativeMeshingStateTable> {
    NATIVE_MESHING_STATES.get_or_init(|| Mutex::new(Vec::new()))
}

#[inline]
pub(super) fn ensure_table_slot<T>(table: &mut Vec<Option<T>>, id: i32) -> Result<usize, i32> {
    let index = usize::try_from(id).map_err(|_| ERR_INVALID_ARGUMENT)?;
    if table.len() <= index {
        table.resize_with(index + 1, || None);
    }
    Ok(index)
}

#[inline(always)]
pub(super) fn state_by_id(
    states: &[Option<NativeMeshingState>],
    state_id: i32,
) -> Option<NativeMeshingState> {
    if state_id < 0 {
        return None;
    }
    states.get(state_id as usize)?.as_ref().copied()
}

#[inline(always)]
pub(super) fn selector_by_id(
    selectors: &[Option<NativeModelSelector>],
    selector_id: i32,
) -> Option<&NativeModelSelector> {
    if selector_id < 0 {
        return None;
    }
    selectors.get(selector_id as usize)?.as_ref()
}

#[inline(always)]
pub(super) fn model_by_id(
    models: &[Option<Vec<StaticModelQuadRecord>>],
    model_id: i32,
) -> Option<&[StaticModelQuadRecord]> {
    if model_id < 0 {
        return None;
    }
    models.get(model_id as usize)?.as_ref().map(Vec::as_slice)
}

/// The columns of a meshing state that rendering owns: model selection,
/// materials and passes, shader-pack IDs, tint, skip groups and fluid sprites.
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct RenderColumns {
    pub(super) selector_id: i32,
    /// Only [`STATE_FLAGS_RENDER_OWNED`] bits.
    pub(super) flags: i32,
    pub(super) material_bits: i32,
    pub(super) pass_id: i32,
    pub(super) render_type: i32,
    pub(super) block_id: i32,
    pub(super) fluid_material_bits: i32,
    pub(super) fluid_pass_id: i32,
    pub(super) fluid_block_id: i32,
    pub(super) skip_group: i32,
    pub(super) skip_mask: i32,
    pub(super) tint_type: i32,
    pub(super) fluid_still: FluidSprite,
    pub(super) fluid_flow: FluidSprite,
    pub(super) fluid_overlay: FluidSprite,
    pub(super) fluid_overlay_valid: i32,
}

/// A meshing state from rendering's columns and the block registry's facts,
/// exactly as `NativeStaticBlockModelRegistry.registerState` computes them in
/// Java. `None` for an unknown state, a custom `BlockState` subclass, or a
/// native fluid the registry does not know as water or lava.
pub(super) fn state_from_registry(registry: &BlockRegistry, state_id: i32, render: RenderColumns, control: i32) -> Option<NativeMeshingState> {
    let index = usize::try_from(state_id).ok().filter(|&i| i < registry.state_count())?;
    let state = StateId(index as u16);
    let facts = registry.flags(state);
    if facts.contains(StateFlags::CUSTOM) {
        return None;
    }
    let java_fluids = control & STATE_CONTROL_JAVA_FLUIDS != 0;
    let mut flags = render.flags & STATE_FLAGS_RENDER_OWNED;
    for (fact, flag) in [
        (StateFlags::AIR, STATE_FLAG_AIR),
        (StateFlags::SOLID_RENDER, STATE_FLAG_SOLID_RENDER | STATE_FLAG_FULL_OCCLUSION),
        (StateFlags::BLOCK_ENTITY, STATE_FLAG_BLOCK_ENTITY),
        (StateFlags::CAN_OCCLUDE, STATE_FLAG_CAN_OCCLUDE),
        (StateFlags::BLOCKS_MOTION, STATE_FLAG_BLOCKS_MOTION),
    ] {
        if facts.contains(fact) {
            flags |= flag;
        }
    }
    if !java_fluids && facts.contains(StateFlags::HAS_FLUID) {
        flags |= STATE_FLAG_FLUID;
    }
    let fluid_type = if control & STATE_CONTROL_NATIVE_FLUID == 0 {
        0
    } else {
        match registry.fluid(state) {
            FluidKind::Water => FLUID_WATER,
            FluidKind::Lava => FLUID_LAVA,
            FluidKind::None | FluidKind::Other => return None,
        }
    };
    let block = registry.block(registry.block_of(state));
    Some(NativeMeshingState {
        selector_id: render.selector_id,
        flags,
        material_bits: render.material_bits,
        pass_id: render.pass_id,
        block_emission: registry.emission(state) as i32,
        render_type: render.render_type,
        block_id: render.block_id,
        fluid_material_bits: render.fluid_material_bits,
        fluid_pass_id: render.fluid_pass_id,
        fluid_block_id: render.fluid_block_id,
        skip_group: render.skip_group,
        skip_mask: render.skip_mask,
        fluid_type,
        fluid_own_height: registry.fluid_height(state),
        fluid_falling: i32::from(facts.contains(StateFlags::FLUID_FALLING)),
        offset_type: match registry.offset(state) {
            OffsetType::None => OFFSET_NONE,
            OffsetType::Xz => OFFSET_XZ,
            OffsetType::Xyz => OFFSET_XYZ,
        },
        max_horizontal_offset: block.max_horizontal_offset(),
        max_vertical_offset: block.max_vertical_offset(),
        tint_type: render.tint_type,
        fluid_still: render.fluid_still,
        fluid_flow: render.fluid_flow,
        fluid_overlay: render.fluid_overlay,
        fluid_overlay_valid: render.fluid_overlay_valid,
    })
}
