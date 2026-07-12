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

pub(super) fn ensure_table_slot<T>(table: &mut Vec<Option<T>>, id: i32) -> Result<usize, i32> {
    let index = usize::try_from(id).map_err(|_| ERR_INVALID_ARGUMENT)?;
    if table.len() <= index {
        table.resize_with(index + 1, || None);
    }
    Ok(index)
}

pub(super) fn state_by_id(
    states: &[Option<NativeMeshingState>],
    state_id: i32,
) -> Option<NativeMeshingState> {
    states
        .get(usize::try_from(state_id).ok()?)?
        .as_ref()
        .copied()
}

pub(super) fn selector_by_id(
    selectors: &[Option<NativeModelSelector>],
    selector_id: i32,
) -> Option<&NativeModelSelector> {
    selectors.get(usize::try_from(selector_id).ok()?)?.as_ref()
}

pub(super) fn model_by_id(
    models: &[Option<Vec<StaticModelQuadRecord>>],
    model_id: i32,
) -> Option<&[StaticModelQuadRecord]> {
    models
        .get(usize::try_from(model_id).ok()?)?
        .as_ref()
        .map(Vec::as_slice)
}
