use std::slice;

pub fn get(d: f64, mut e: f64, pixels: &[i32], fallback: i32) -> i32 {
    e *= d;
    let j = ((1.0 - d) * 255.0) as i32;
    let k = ((1.0 - e) * 255.0) as i32;
    let index = (k << 8) | j;

    if index < 0 {
        return fallback;
    }

    pixels
        .get(index as usize)
        .copied()
        .unwrap_or(fallback)
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
}
