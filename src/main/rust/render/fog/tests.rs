use super::{policy::*, state::Water};
#[test]
fn water_changes_target_after_returning_the_previous_transition() {
    let mut water = Water::default();
    assert_eq!(water.color(100, 0xff000000u32 as i32), 0xff000000u32 as i32);
    assert_eq!(water.color(200, 0xffffffffu32 as i32), 0xff000000u32 as i32);
    assert_eq!(
        water.color(2700, 0xffffffffu32 as i32),
        0xff7f7f7fu32 as i32
    );
    water.changed = -1;
    assert_eq!(water.color(3000, 0xffffffffu32 as i32), -1);
}
#[test]
fn range_rules_preserve_fields_owned_by_other_stages() {
    let mut out = [11.0, 12.0, 13.0, 14.0, 15.0, 16.0];
    ranges(4, 256.0, 10.0, 0.0, 0, &mut out);
    assert_eq!(out, [32.625, 12.0, 130.5, 14.0, 104.4, 104.4]);
    ranges(7, 256.0, 0.0, 0.0, 0, &mut out);
    assert_eq!(out[1], 230.4);
    assert_eq!(out[3], 256.0);
}
#[test]
fn zero_channels_skip_vision_and_special_media_skip_void_darkness() {
    assert_eq!(
        finish_color(0xff000000u32 as i32, 4, 0.0, 0.0, 1.0),
        [0.0, 0.0, 0.0, 1.0]
    );
    assert_eq!(finish_color(-1, 0, 1.0, 0.0, 0.0), [1.0; 4]);
    assert_eq!(finish_color(-1, 4, 1.0, 0.0, 1.0), [0.0, 0.0, 0.0, 1.0]);
    assert!(max(f32::NAN, 0.0).is_nan());
    assert_eq!(min(-0.0, 0.0).to_bits(), (-0.0f32).to_bits());
}

#[test]
fn color_conversion_masks_each_floored_channel_before_combining() {
    assert_eq!(pack([-1.0, 2.0, 1.0]), 0xff01feffu32 as i32);
    assert_eq!(pack([f32::NAN; 3]), 0xff000000u32 as i32);
}
