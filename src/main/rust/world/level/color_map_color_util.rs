use std::slice;
use std::sync::{OnceLock, RwLock};

const DRY_FOLIAGE_FALLBACK: i32 = -10732494;

static DRY_FOLIAGE_PIXELS: OnceLock<RwLock<Vec<i32>>> = OnceLock::new();

fn dry_foliage_pixels() -> &'static RwLock<Vec<i32>> {
    DRY_FOLIAGE_PIXELS.get_or_init(|| RwLock::new(vec![0; 65536]))
}

pub fn get(d: f64, mut e: f64, pixels: &[i32], fallback: i32) -> i32 {
    e *= d;
    let j = ((1.0 - d) * 255.0) as i32;
    let k = ((1.0 - e) * 255.0) as i32;
    let index = (k << 8) | j;

    if index < 0 {
        return fallback;
    }

    pixels.get(index as usize).copied().unwrap_or(fallback)
}

pub fn init_dry_foliage(pixels: &[i32]) {
    let mut dry_foliage_pixels = dry_foliage_pixels()
        .write()
        .expect("dry foliage color map lock poisoned");
    dry_foliage_pixels.clear();
    dry_foliage_pixels.extend_from_slice(pixels);
}

pub fn get_dry_foliage(d: f64, e: f64) -> i32 {
    let dry_foliage_pixels = dry_foliage_pixels()
        .read()
        .expect("dry foliage color map lock poisoned");
    get(d, e, &dry_foliage_pixels, DRY_FOLIAGE_FALLBACK)
}

#[no_mangle]
pub extern "C" fn mattmc_world_level_color_map_color_util_get(
    d: f64,
    e: f64,
    pixels: *const i32,
    length: i32,
    fallback: i32,
) -> i32 {
    if pixels.is_null() || length <= 0 {
        return fallback;
    }

    let pixels = unsafe { slice::from_raw_parts(pixels, length as usize) };
    get(d, e, pixels, fallback)
}

#[no_mangle]
pub extern "C" fn mattmc_world_level_color_map_color_util_init_dry_foliage(
    pixels: *const i32,
    length: i32,
) -> i32 {
    if pixels.is_null() || length < 0 {
        return 0;
    }

    let pixels = unsafe { slice::from_raw_parts(pixels, length as usize) };
    init_dry_foliage(pixels);
    1
}

#[no_mangle]
pub extern "C" fn mattmc_world_level_color_map_color_util_get_dry_foliage(d: f64, e: f64) -> i32 {
    get_dry_foliage(d, e)
}

#[cfg(test)]
mod tests {
    use super::get;

    #[test]
    fn matches_colormap_indexing() {
        let mut pixels = vec![0; 65536];
        let index = 223usize << 8 | 191usize;
        pixels[index] = 0x12345678;

        assert_eq!(0x12345678, get(0.25, 0.5, &pixels, -65281));
    }

    #[test]
    fn returns_fallback_when_index_exceeds_map() {
        assert_eq!(-65281, get(0.25, 0.5, &[1, 2, 3], -65281));
    }

    #[test]
    fn dry_foliage_lookup_uses_rust_owned_pixels() {
        let mut pixels = vec![0; 65536];
        let index = 223usize << 8 | 191usize;
        pixels[index] = 0x23456789;

        super::init_dry_foliage(&pixels);

        assert_eq!(0x23456789, super::get_dry_foliage(0.25, 0.5));
    }
}
