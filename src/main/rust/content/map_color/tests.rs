use super::*;
// Actual CPU NativeImage bytes from untouched Frozen 7a4d181717dc0c7da9086f1a17687dfd522ce417.
// Each packed byte 0..255 was passed through MapColor and NativeImage.setPixel.
const FROZEN_RGBA: &[u8; 1024] = include_bytes!("frozen-native-rgba.bin");
#[test]
fn packed_palette_and_expansion_match_real_frozen_image_bytes() {
    let codes: Vec<u8> = (0..=255).collect();
    assert_eq!(FROZEN_RGBA.as_slice(), expand_rgba(&codes));
    for (code, pixel) in FROZEN_RGBA.chunks_exact(4).enumerate() {
        assert_eq!(pixel, PACKED_RGBA[code]);
        assert_eq!(u32::from_be_bytes([pixel[3],pixel[0],pixel[1],pixel[2]]), PACKED_ARGB[code]);
    }
}
#[test]
fn typed_palette_ids_shades_and_unassigned_slots_stay_canonical() {
    for (id, color) in MapColor::ALL.into_iter().enumerate() {
        assert_eq!(id, color as usize); assert_eq!(Some(color), MapColor::from_slot(id as u8));
        for brightness in Brightness::ALL {
            assert_eq!(PACKED_ARGB[color.packed(brightness) as usize], color.argb(brightness));
        }
    }
    assert_eq!(Some(MapColor::None), MapColor::from_slot(62));
    assert_eq!(Some(MapColor::None), MapColor::from_slot(63));
    assert_eq!(None, MapColor::from_slot(64));
    assert!(PACKED_ARGB[248..].iter().all(|&color| color == 0));
}
#[test]
fn palette_projection_is_stable_bounded_and_borrowed() {
    unsafe {
        let mut count = -1;
        for (kind, expected) in [(0,4),(1,62),(2,4),(3,256)] {
            let first = ffi::mattmc_map_palette_buffer(kind,&mut count);
            assert!(!first.is_null()); assert_eq!(expected,count);
            assert_eq!(first,ffi::mattmc_map_palette_buffer(kind,&mut count));
        }
        assert!(ffi::mattmc_map_palette_buffer(4,&mut count).is_null()); assert_eq!(0,count);
        assert!(ffi::mattmc_map_palette_buffer(0,std::ptr::null_mut()).is_null());
    }
}
