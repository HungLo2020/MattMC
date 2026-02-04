// Rust FFI implementation of ColorUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.ColorUtil
// Author: Cola, Leonardo Amato (original Java), migrated to Rust for performance
//
// Color format reference:
// Minecraft color format:       0xAA BB GG RR
// DH mod color format:          0xAA RR GG BB
// OpenGL RGBA format native order: 0xRR GG BB AA
// OpenGL RGBA format Java Order:   0xAA BB GG RR

use std::os::raw::c_int;

// ===== RGB/ARGB Construction Functions =====

/// Converts RGB components to a 32-bit integer (alpha = 255)
/// Format: 0xAARRGGBB where AA = 0xFF
#[no_mangle]
pub extern "C" fn colorutil_rgb_to_int(red: c_int, green: c_int, blue: c_int) -> c_int {
    (0xFF << 24) | (red << 16) | (green << 8) | blue
}

/// Converts ARGB components to a 32-bit integer
/// Format: 0xAARRGGBB
#[no_mangle]
pub extern "C" fn colorutil_argb_to_int(alpha: c_int, red: c_int, green: c_int, blue: c_int) -> c_int {
    (alpha << 24) | (red << 16) | (green << 8) | blue
}

/// Converts ARGB float components (0.0-1.0) to a 32-bit integer
/// Format: 0xAARRGGBB
#[no_mangle]
pub extern "C" fn colorutil_argb_to_int_f(alpha: f32, red: f32, green: f32, blue: f32) -> c_int {
    let a = (alpha * 255.0) as c_int;
    let r = (red * 255.0) as c_int;
    let g = (green * 255.0) as c_int;
    let b = (blue * 255.0) as c_int;
    colorutil_argb_to_int(a, r, g, b)
}

// ===== Component Getters =====

/// Extracts the alpha component (0-255) from a color integer
#[no_mangle]
pub extern "C" fn colorutil_get_alpha(color: c_int) -> c_int {
    (color >> 24) & 0xFF
}

/// Extracts the red component (0-255) from a color integer
#[no_mangle]
pub extern "C" fn colorutil_get_red(color: c_int) -> c_int {
    (color >> 16) & 0xFF
}

/// Extracts the green component (0-255) from a color integer
#[no_mangle]
pub extern "C" fn colorutil_get_green(color: c_int) -> c_int {
    (color >> 8) & 0xFF
}

/// Extracts the blue component (0-255) from a color integer
#[no_mangle]
pub extern "C" fn colorutil_get_blue(color: c_int) -> c_int {
    color & 0xFF
}

// ===== Component Setters =====

/// Sets the alpha component of a color
/// newAlpha should be a value between 0 and 255
#[no_mangle]
pub extern "C" fn colorutil_set_alpha(color: c_int, new_alpha: c_int) -> c_int {
    (new_alpha << 24) | (colorutil_get_red(color) << 16) | (colorutil_get_green(color) << 8) | colorutil_get_blue(color)
}

/// Sets the red component of a color
/// newRed should be a value between 0 and 255
#[no_mangle]
pub extern "C" fn colorutil_set_red(color: c_int, new_red: c_int) -> c_int {
    (colorutil_get_alpha(color) << 24) | (new_red << 16) | (colorutil_get_green(color) << 8) | colorutil_get_blue(color)
}

/// Sets the green component of a color
/// newGreen should be a value between 0 and 255
#[no_mangle]
pub extern "C" fn colorutil_set_green(color: c_int, new_green: c_int) -> c_int {
    (colorutil_get_alpha(color) << 24) | (colorutil_get_red(color) << 16) | (new_green << 8) | colorutil_get_blue(color)
}

/// Sets the blue component of a color
/// newBlue should be a value between 0 and 255
#[no_mangle]
pub extern "C" fn colorutil_set_blue(color: c_int, new_blue: c_int) -> c_int {
    (colorutil_get_alpha(color) << 24) | (colorutil_get_red(color) << 16) | (colorutil_get_green(color) << 8) | new_blue
}

// ===== Color Manipulation Functions =====

/// Applies shade (integer) to a color by adding/subtracting from RGB components
/// Negative shade darkens, positive shade lightens
#[no_mangle]
pub extern "C" fn colorutil_apply_shade_int(color: c_int, shade: c_int) -> c_int {
    let alpha = colorutil_get_alpha(color);
    
    if shade < 0 {
        let red = 0.max(colorutil_get_red(color) + shade);
        let green = 0.max(colorutil_get_green(color) + shade);
        let blue = 0.max(colorutil_get_blue(color) + shade);
        (alpha << 24) | (red << 16) | (green << 8) | blue
    } else {
        let red = 255.min(colorutil_get_red(color) + shade);
        let green = 255.min(colorutil_get_green(color) + shade);
        let blue = 255.min(colorutil_get_blue(color) + shade);
        (alpha << 24) | (red << 16) | (green << 8) | blue
    }
}

/// Applies shade (float) to a color by multiplying RGB components
/// Shade < 1.0 darkens, shade > 1.0 lightens
#[no_mangle]
pub extern "C" fn colorutil_apply_shade_float(color: c_int, shade: f32) -> c_int {
    let alpha = colorutil_get_alpha(color);
    
    if shade < 1.0 {
        let red = 0.max((colorutil_get_red(color) as f32 * shade) as c_int);
        let green = 0.max((colorutil_get_green(color) as f32 * shade) as c_int);
        let blue = 0.max((colorutil_get_blue(color) as f32 * shade) as c_int);
        (alpha << 24) | (red << 16) | (green << 8) | blue
    } else {
        let red = 255.min((colorutil_get_red(color) as f32 * shade) as c_int);
        let green = 255.min((colorutil_get_green(color) as f32 * shade) as c_int);
        let blue = 255.min((colorutil_get_blue(color) as f32 * shade) as c_int);
        (alpha << 24) | (red << 16) | (green << 8) | blue
    }
}

// ===== Color Blending Functions =====

/// Multiply ARGB with RGB colors
#[no_mangle]
pub extern "C" fn colorutil_multiply_argb_with_rgb(argb: c_int, rgb: c_int) -> c_int {
    let alpha = colorutil_get_alpha(argb);
    let red = (colorutil_get_red(argb) * colorutil_get_red(rgb)) / 255;
    let green = (colorutil_get_green(argb) * colorutil_get_green(rgb)) / 255;
    let blue = (colorutil_get_blue(argb) * colorutil_get_blue(rgb)) / 255;
    (alpha << 24) | (red << 16) | (green << 8) | blue
}

/// Multiply two ARGB colors
#[no_mangle]
pub extern "C" fn colorutil_multiply_argb_with_argb(color1: c_int, color2: c_int) -> c_int {
    let alpha = (colorutil_get_alpha(color1) * colorutil_get_alpha(color2)) / 255;
    let red = (colorutil_get_red(color1) * colorutil_get_red(color2)) / 255;
    let green = (colorutil_get_green(color1) * colorutil_get_green(color2)) / 255;
    let blue = (colorutil_get_blue(color1) * colorutil_get_blue(color2)) / 255;
    (alpha << 24) | (red << 16) | (green << 8) | blue
}

// ===== Color Space Conversion Functions =====

/// Converts ARGB to AHSV color space
/// Returns: [alpha (0.0-1.0), hue (0.0-360.0), saturation (0.0-1.0), value (0.0-1.0)]
/// 
/// Note: In FFI, we cannot return arrays directly, so this function takes an output array pointer
/// The output array must have at least 4 elements allocated by the caller
#[no_mangle]
pub extern "C" fn colorutil_argb_to_ahsv(color: c_int, output: *mut f32) {
    let a = colorutil_get_alpha(color) as f32 / 255.0;
    let r = colorutil_get_red(color) as f32 / 255.0;
    let g = colorutil_get_green(color) as f32 / 255.0;
    let b = colorutil_get_blue(color) as f32 / 255.0;
    
    let min = r.min(g).min(b);
    let max = r.max(g).max(b);
    let v = max;
    let delta = max - min;
    
    let s = if max != 0.0 { delta / max } else { 0.0 };
    
    let h = if delta == 0.0 {
        0.0
    } else if max == 0.0 {
        // Return early for black color
        unsafe {
            *output.offset(0) = a;
            *output.offset(1) = 0.0;
            *output.offset(2) = 0.0;
            *output.offset(3) = 0.0;
        }
        return;
    } else {
        let mut h_val = if r == max {
            (g - b) / delta
        } else if g == max {
            2.0 + (b - r) / delta
        } else {
            4.0 + (r - g) / delta
        };
        
        h_val *= 60.0;
        if h_val < 0.0 {
            h_val += 360.0;
        }
        h_val
    };
    
    unsafe {
        *output.offset(0) = a;
        *output.offset(1) = h;
        *output.offset(2) = s;
        *output.offset(3) = v;
    }
}

/// Converts AHSV to ARGB color space
/// Parameters: alpha (0.0-1.0), hue (0.0-360.0), saturation (0.0-1.0), value (0.0-1.0)
#[no_mangle]
pub extern "C" fn colorutil_ahsv_to_argb(a: f32, h: f32, s: f32, v: f32) -> c_int {
    // Clamp inputs
    let a = a.min(1.0);
    let h = if h > 360.0 { h - 350.0 } else { h };
    let s = s.min(1.0);
    let v = v.min(1.0);
    
    if s == 0.0 {
        // Achromatic (grey)
        return colorutil_argb_to_int_f(a, v, v, v);
    }
    
    let h_sector = h / 60.0;
    let i = h_sector.floor() as i32;
    let f = h_sector - (i as f32);
    let p = v * (1.0 - s);
    let q = v * (1.0 - s * f);
    let t = v * (1.0 - s * (1.0 - f));
    
    match i {
        0 => colorutil_argb_to_int_f(a, v, t, p),
        1 => colorutil_argb_to_int_f(a, q, v, p),
        2 => colorutil_argb_to_int_f(a, p, v, t),
        3 => colorutil_argb_to_int_f(a, p, q, v),
        4 => colorutil_argb_to_int_f(a, t, p, v),
        _ => colorutil_argb_to_int_f(a, v, p, q), // case 5
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rgb_to_int() {
        assert_eq!(colorutil_rgb_to_int(255, 0, 0), 0xFFFF0000u32 as i32);
        assert_eq!(colorutil_rgb_to_int(0, 255, 0), 0xFF00FF00u32 as i32);
        assert_eq!(colorutil_rgb_to_int(0, 0, 255), 0xFF0000FFu32 as i32);
    }

    #[test]
    fn test_argb_to_int() {
        assert_eq!(colorutil_argb_to_int(128, 255, 0, 0), 0x80FF0000u32 as i32);
        assert_eq!(colorutil_argb_to_int(255, 255, 255, 255), 0xFFFFFFFFu32 as i32);
    }

    #[test]
    fn test_component_extraction() {
        let color = 0x80FF00AAu32 as i32;
        assert_eq!(colorutil_get_alpha(color), 0x80);
        assert_eq!(colorutil_get_red(color), 0xFF);
        assert_eq!(colorutil_get_green(color), 0x00);
        assert_eq!(colorutil_get_blue(color), 0xAA);
    }

    #[test]
    fn test_component_setters() {
        let color = 0xFF000000u32 as i32;
        let color = colorutil_set_red(color, 128);
        assert_eq!(colorutil_get_red(color), 128);
        
        let color = colorutil_set_green(color, 64);
        assert_eq!(colorutil_get_green(color), 64);
        
        let color = colorutil_set_blue(color, 32);
        assert_eq!(colorutil_get_blue(color), 32);
    }

    #[test]
    fn test_apply_shade_int() {
        let white = colorutil_rgb_to_int(255, 255, 255);
        let darkened = colorutil_apply_shade_int(white, -50);
        assert_eq!(colorutil_get_red(darkened), 205);
        assert_eq!(colorutil_get_green(darkened), 205);
        assert_eq!(colorutil_get_blue(darkened), 205);
    }

    #[test]
    fn test_multiply_colors() {
        let color1 = colorutil_rgb_to_int(255, 128, 64);
        let color2 = colorutil_rgb_to_int(128, 255, 255);
        let result = colorutil_multiply_argb_with_rgb(color1, color2);
        
        // 255 * 128 / 255 = 128
        // 128 * 255 / 255 = 128
        // 64 * 255 / 255 = 64
        assert_eq!(colorutil_get_red(result), 128);
        assert_eq!(colorutil_get_green(result), 128);
        assert_eq!(colorutil_get_blue(result), 64);
    }
}
