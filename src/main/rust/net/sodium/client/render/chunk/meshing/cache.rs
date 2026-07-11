use super::*;

static STATIC_MODEL_CACHE: OnceLock<Mutex<HashMap<i32, Vec<StaticModelQuadRecord>>>> =
    OnceLock::new();
static NATIVE_MODEL_SELECTORS: OnceLock<Mutex<HashMap<i32, NativeModelSelector>>> = OnceLock::new();
static NATIVE_MESHING_STATES: OnceLock<Mutex<HashMap<i32, NativeMeshingState>>> = OnceLock::new();

pub(super) fn static_model_cache() -> &'static Mutex<HashMap<i32, Vec<StaticModelQuadRecord>>> {
    STATIC_MODEL_CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

pub(super) fn native_model_selectors() -> &'static Mutex<HashMap<i32, NativeModelSelector>> {
    NATIVE_MODEL_SELECTORS.get_or_init(|| Mutex::new(HashMap::new()))
}

pub(super) fn native_meshing_states() -> &'static Mutex<HashMap<i32, NativeMeshingState>> {
    NATIVE_MESHING_STATES.get_or_init(|| Mutex::new(HashMap::new()))
}
