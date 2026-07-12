use super::*;

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
