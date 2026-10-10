use super::*;
use std::sync::Mutex;

pub struct World(Mutex<LiveSkyFields>);

/// # Safety
/// Colors is a live aligned immutable span. Its contents are copied once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_create(
    colors: *const u32,
    count: i32,
    min_y: i32,
    height: i32,
    fallback: i32,
    capacity: i32,
    epoch: u64,
) -> *mut World {
    if colors.is_null()
        || colors as usize % 4 != 0
        || !(1..=65535).contains(&count)
        || height <= 0
        || capacity <= 0
        || fallback < 0
    {
        return std::ptr::null_mut();
    }
    let colors: Arc<[u32]> = std::slice::from_raw_parts(colors, count as usize).into();
    LiveSkyFields::new(
        epoch,
        min_y,
        height as usize,
        colors,
        fallback as usize,
        capacity as usize,
    )
    .map_or(std::ptr::null_mut(), |world| {
        Box::into_raw(Box::new(World(Mutex::new(world))))
    })
}

/// # Safety
/// Release exactly one world after all synchronous calls finish.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_release(world: *mut World) {
    if !world.is_null() {
        drop(Box::from_raw(world));
    }
}

/// # Safety
/// Live world and aligned owner-pointer span; each nonnull pointer is a live
/// Arc owner from mattmc_live_biome_create/copy. Java retains every owner through
/// this call. Native residency clones those typed owners, not the input span.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_replace(
    world: *const World,
    epoch: u64,
    x: i32,
    z: i32,
    owners: *const *const Owner,
    count: i32,
) -> i32 {
    let Some(world) = world.as_ref() else {
        return -1;
    };
    if owners.is_null()
        || owners as usize % std::mem::align_of::<*const Owner>() != 0
        || !(1..=1024).contains(&count)
    {
        return -1;
    }
    let pointers = std::slice::from_raw_parts(owners, count as usize);
    if pointers
        .iter()
        .any(|p| p.is_null() || *p as usize % std::mem::align_of::<Owner>() != 0)
    {
        return -1;
    }
    let owners: Arc<[Arc<Owner>]> = pointers
        .iter()
        .map(|&p| {
            Arc::increment_strong_count(p);
            Arc::from_raw(p)
        })
        .collect::<Vec<_>>()
        .into();
    if world.0.lock().unwrap().replace(epoch, (x, z), owners) {
        0
    } else {
        -1
    }
}

/// # Safety
/// Live world. Mark a loaded compatibility chunk so queries decline rather
/// than treating unsupported biome data as a missing chunk.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_compatibility(
    world: *const World,
    epoch: u64,
    x: i32,
    z: i32,
) -> i32 {
    if world
        .as_ref()
        .is_some_and(|w| w.0.lock().unwrap().compatibility(epoch, (x, z)))
    {
        0
    } else {
        -1
    }
}

/// # Safety
/// Live world. Removal drops native residency pins and invalidates its window.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_remove(
    world: *const World,
    epoch: u64,
    x: i32,
    z: i32,
) -> i32 {
    if world
        .as_ref()
        .is_some_and(|w| w.0.lock().unwrap().remove(epoch, (x, z)))
    {
        0
    } else {
        -1
    }
}

/// # Safety
/// Live world. Terminal bridge disablement releases every native residency pin.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_clear(world: *const World, epoch: u64) -> i32 {
    if world
        .as_ref()
        .is_some_and(|w| w.0.lock().unwrap().clear(epoch))
    {
        0
    } else {
        -1
    }
}

/// # Safety
/// Live world; the view center follows ClientChunkCache independently of residency.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_range(
    world: *const World,
    epoch: u64,
    x: i32,
    z: i32,
    radius: i32,
) -> i32 {
    if world
        .as_ref()
        .is_some_and(|w| w.0.lock().unwrap().set_range(epoch, [x, z], radius))
    {
        0
    } else {
        -1
    }
}

/// # Safety
/// Live world and three aligned writable doubles, disjoint from native storage.
/// Decline leaves output untouched. No Java grid or GPU resource is involved.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_world_sample(
    world: *const World,
    epoch: u64,
    x: f64,
    y: f64,
    z: f64,
    output: *mut f64,
) -> i32 {
    if output.is_null() || output as usize % 8 != 0 {
        return -1;
    }
    let Some(world) = world.as_ref() else {
        return -1;
    };
    let Some(color) = world.0.lock().unwrap().query(epoch, [x, y, z]) else {
        return -1;
    };
    std::ptr::copy_nonoverlapping(color.as_ptr(), output, 3);
    0
}
