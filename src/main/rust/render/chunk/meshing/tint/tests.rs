use super::*;

#[test]
fn argb_to_abgr_preserves_alpha_and_swaps_red_blue() {
    assert_eq!(0xff0000ffu32 as i32, argb_to_abgr(0xffff0000u32 as i32));
    assert_eq!(0xff00ff00u32 as i32, argb_to_abgr(0xff00ff00u32 as i32));
    assert_eq!(0xffff0000u32 as i32, argb_to_abgr(0xff0000ffu32 as i32));
    assert_eq!(0xffffffffu32 as i32, argb_to_abgr(0xffffffffu32 as i32));
    assert_eq!(0x80332211u32 as i32, argb_to_abgr(0x80112233u32 as i32));
    assert_eq!(0xff6f9935u32 as i32, argb_to_abgr(0xff35996fu32 as i32));
    assert_eq!(0xffe4763fu32 as i32, argb_to_abgr(0xff3f76e4u32 as i32));
}
