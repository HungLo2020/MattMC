//! CPU map image encoding. Palette definitions remain in content; GPU images in render.
use crate::content::map_color::PACKED_RGBA;
use std::io::Cursor;

pub const MAP_SIDE: usize = 128;
pub const MAP_PIXELS: usize = MAP_SIDE * MAP_SIDE;
/// Includes the full 256-entry palette, alpha table and worst-case indexed data.
pub const MAX_MAP_PNG_BYTES: usize = 20 * 1024;

/// Encodes indexed pixels directly, without an intermediate RGBA image.
pub fn encode_png(colors: &[u8]) -> Result<Vec<u8>, String> {
    if colors.len() != MAP_PIXELS {
        return Err("map image requires exactly 128x128 indexed colors".into());
    }
    let palette: Vec<u8> = PACKED_RGBA.iter().flat_map(|pixel| pixel[..3].iter().copied()).collect();
    let alpha: Vec<u8> = PACKED_RGBA.iter().map(|pixel| pixel[3]).collect();
    let mut bytes = vec![0; MAX_MAP_PNG_BYTES];
    let length = {
        // A fixed-capacity writer makes the output bound effective during encoding.
        let mut cursor = Cursor::new(bytes.as_mut_slice());
        let mut encoder = png::Encoder::new(&mut cursor, MAP_SIDE as u32, MAP_SIDE as u32);
        encoder.set_color(png::ColorType::Indexed);
        encoder.set_depth(png::BitDepth::Eight);
        encoder.set_palette(palette);
        encoder.set_trns(alpha);
        encoder.set_compression(png::Compression::Fast);
        encoder.set_filter(png::FilterType::NoFilter);
        let mut writer = encoder.write_header().map_err(|error| error.to_string())?;
        writer.write_image_data(colors).map_err(|error| error.to_string())?;
        writer.finish().map_err(|error| error.to_string())?;
        cursor.position() as usize
    };
    bytes.truncate(length);
    Ok(bytes)
}

/// Copies one bounded CPU PNG into caller-owned storage. No retained image or GPU handle.
/// Returns encoded length, -1 for invalid input, -2 for encoding failure, -3 for
/// insufficient output storage. Failure leaves caller output unchanged.
/// # Safety
/// Non-null input/output must describe readable/writable buffers of their stated
/// lengths for the call. They may overlap because encoding completes before copying.
#[no_mangle]
pub unsafe extern "C" fn mattmc_map_image_png(
    input: *const u8, input_length: i32, output: *mut u8, output_capacity: i32,
) -> i32 {
    if input.is_null() || input_length != MAP_PIXELS as i32 || output.is_null()
        || output_capacity <= 0 || output_capacity > MAX_MAP_PNG_BYTES as i32 {
        return -1;
    }
    let colors = std::slice::from_raw_parts(input, MAP_PIXELS);
    let Ok(png) = encode_png(colors) else { return -2; };
    if png.len() > output_capacity as usize { return -3; }
    std::ptr::copy_nonoverlapping(png.as_ptr(), output, png.len());
    png.len() as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn indexed_png_decodes_to_exact_frozen_rgba_including_transparency() {
        let codes: Vec<u8> = (0..MAP_PIXELS).map(|i| i as u8).collect();
        let png = encode_png(&codes).unwrap();
        assert!(png.len() < MAX_MAP_PNG_BYTES);
        let mut decoder = png::Decoder::new(png.as_slice());
        decoder.set_transformations(png::Transformations::EXPAND);
        let mut reader = decoder.read_info().unwrap();
        let mut rgba = vec![0; reader.output_buffer_size()];
        let frame = reader.next_frame(&mut rgba).unwrap();
        assert_eq!(png::ColorType::Rgba, frame.color_type);
        let frozen = include_bytes!("../content/map_color/frozen-native-rgba.bin");
        assert_eq!(frozen.repeat(64), rgba[..frame.buffer_size()]);
    }

    #[test]
    fn png_output_stays_bounded_for_incompressible_map_colors() {
        let mut seed = 0x1234_5678u32;
        let data: Vec<u8> = (0..MAP_PIXELS).map(|_| {
            seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5; seed as u8
        }).collect();
        assert!(encode_png(&data).unwrap().len() <= MAX_MAP_PNG_BYTES);
        assert!(encode_png(&data[..MAP_PIXELS-1]).is_err());
    }

    #[test]
    fn ffi_rejects_invalid_buffers_without_modifying_caller_output() {
        let colors = vec![1; MAP_PIXELS]; let mut output = [99; 4];
        unsafe {
            assert_eq!(-1, mattmc_map_image_png(std::ptr::null(), MAP_PIXELS as i32, output.as_mut_ptr(), 4));
            assert_eq!(-1, mattmc_map_image_png(colors.as_ptr(), 1, output.as_mut_ptr(), 4));
            assert_eq!(-3, mattmc_map_image_png(colors.as_ptr(), MAP_PIXELS as i32, output.as_mut_ptr(), 4));
        }
        assert_eq!([99; 4], output);
        let mut complete = vec![0; MAX_MAP_PNG_BYTES];
        let count = unsafe { mattmc_map_image_png(colors.as_ptr(), MAP_PIXELS as i32, complete.as_mut_ptr(), MAX_MAP_PNG_BYTES as i32) };
        assert!(count > 0); complete.truncate(count as usize);
        assert_eq!(encode_png(&colors).unwrap(), complete);
    }
}
