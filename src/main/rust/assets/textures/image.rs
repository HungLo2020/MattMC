//! RGBA8 images and PNG decoding with `NativeImage.read`'s results.
//!
//! Java decoded with stb_image forced to four channels after
//! `PngInfo.validateHeader`. stb_image ignores chunk CRCs and the zlib
//! checksum, applies no gamma, expands palettes, grayscale and `tRNS` to
//! RGBA, and keeps the high byte of 16-bit samples; the `png` crate is set
//! up to do the same.

use std::fmt;

/// stb_image's dimension limit (`STBI_MAX_DIMENSIONS`).
const MAX_DIMENSION: u32 = 1 << 24;

/// An RGBA8 image, rows top to bottom (`NativeImage`'s memory layout).
#[derive(Clone, PartialEq, Eq)]
pub(crate) struct Image {
    pub(crate) width: u32,
    pub(crate) height: u32,
    pub(crate) pixels: Vec<u8>,
}

impl fmt::Debug for Image {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "Image[{}x{}]", self.width, self.height)
    }
}

impl Image {
    /// A zeroed image (`new NativeImage(w, h, true)`).
    pub(crate) fn new(width: u32, height: u32) -> Image {
        Image { width, height, pixels: vec![0; width as usize * height as usize * 4] }
    }

    #[inline]
    fn offset(&self, x: u32, y: u32) -> usize {
        (y as usize * self.width as usize + x as usize) * 4
    }

    /// The pixel as `NativeImage` stores it: little-endian ABGR.
    #[inline]
    pub(crate) fn abgr(&self, x: u32, y: u32) -> u32 {
        let at = self.offset(x, y);
        u32::from_le_bytes([self.pixels[at], self.pixels[at + 1], self.pixels[at + 2], self.pixels[at + 3]])
    }

    #[inline]
    pub(crate) fn set_abgr(&mut self, x: u32, y: u32, abgr: u32) {
        let at = self.offset(x, y);
        self.pixels[at..at + 4].copy_from_slice(&abgr.to_le_bytes());
    }

    /// `copyRect(target, x, y, 0, 0, w, h, false, false)`: the region must
    /// lie inside the image, as `getPixelABGR` checks every read.
    pub(crate) fn crop(&self, x: i32, y: i32, width: i32, height: i32) -> Result<Image, String> {
        if width <= 0 || height <= 0 {
            return Err(format!("Invalid texture size: {width}x{height}"));
        }
        let fits = |start: i32, length: i32, limit: u32| start >= 0 && i64::from(start) + i64::from(length) <= i64::from(limit);
        if !fits(x, width, self.width) || !fits(y, height, self.height) {
            let (bad_x, bad_y) = if x < 0 || y < 0 { (x, y) } else { (x + width - 1, y + height - 1) };
            return Err(format!("({bad_x}, {bad_y}) outside of image bounds ({}, {})", self.width, self.height));
        }
        let mut target = Image::new(width as u32, height as u32);
        let row = width as usize * 4;
        for line in 0..height as u32 {
            let from = self.offset(x as u32, y as u32 + line);
            let to = target.offset(0, line);
            target.pixels[to..to + row].copy_from_slice(&self.pixels[from..from + row]);
        }
        Ok(target)
    }
}

/// `PngInfo.validateHeader`.
fn validate_header(bytes: &[u8]) -> Result<(), String> {
    const SIGNATURE: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    let word = |at: usize| bytes.get(at..at + 4).map(|word| u32::from_be_bytes(word.try_into().unwrap()));
    if bytes.get(..8) != Some(&SIGNATURE[..]) {
        return Err("Bad PNG Signature".into());
    }
    if word(8) != Some(13) {
        return Err("Bad length for IHDR chunk!".into());
    }
    if word(12) != Some(u32::from_be_bytes(*b"IHDR")) {
        return Err("Bad type for IHDR chunk!".into());
    }
    Ok(())
}

/// `NativeImage.read(bytes)` as RGBA8. Errors carry Java's message.
pub(crate) fn decode_png(bytes: &[u8]) -> Result<Image, String> {
    validate_header(bytes)?;
    decode_body(bytes).map_err(|reason| format!("Could not load image: {reason}"))
}

fn decode_body(bytes: &[u8]) -> Result<Image, String> {
    let mut decoder = png::Decoder::new(bytes);
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    decoder.ignore_checksums(true);
    let mut reader = decoder.read_info().map_err(|error| error.to_string())?;
    let (width, height) = {
        let info = reader.info();
        (info.width, info.height)
    };
    if width == 0 || height == 0 || width > MAX_DIMENSION || height > MAX_DIMENSION {
        return Err("too large".into());
    }
    let mut buffer = vec![0; reader.output_buffer_size()];
    let frame = reader.next_frame(&mut buffer).map_err(|error| error.to_string())?;
    let data = &buffer[..frame.buffer_size()];
    let pixel_count = width as usize * height as usize;
    let mut pixels = Vec::with_capacity(pixel_count * 4);
    match frame.color_type {
        png::ColorType::Rgba => pixels.extend_from_slice(&data[..pixel_count * 4]),
        png::ColorType::Rgb => {
            for rgb in data[..pixel_count * 3].chunks_exact(3) {
                pixels.extend_from_slice(&[rgb[0], rgb[1], rgb[2], 255]);
            }
        }
        png::ColorType::GrayscaleAlpha => {
            for gray in data[..pixel_count * 2].chunks_exact(2) {
                pixels.extend_from_slice(&[gray[0], gray[0], gray[0], gray[1]]);
            }
        }
        png::ColorType::Grayscale => {
            for &gray in &data[..pixel_count] {
                pixels.extend_from_slice(&[gray, gray, gray, 255]);
            }
        }
        png::ColorType::Indexed => return Err("unexpanded palette".into()),
    }
    Ok(Image { width, height, pixels })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn encode(width: u32, height: u32, color: png::ColorType, depth: png::BitDepth, data: &[u8], trns: Option<&[u8]>) -> Vec<u8> {
        let mut bytes = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut bytes, width, height);
            encoder.set_color(color);
            encoder.set_depth(depth);
            if let Some(trns) = trns {
                encoder.set_trns(trns.to_vec());
            }
            if color == png::ColorType::Indexed {
                encoder.set_palette(vec![10, 20, 30, 40, 50, 60]);
            }
            let mut writer = encoder.write_header().unwrap();
            writer.write_image_data(data).unwrap();
        }
        bytes
    }

    #[test]
    fn decodes_like_stb() {
        let gray = decode_png(&encode(2, 1, png::ColorType::Grayscale, png::BitDepth::Eight, &[7, 200], None)).unwrap();
        assert_eq!(vec![7, 7, 7, 255, 200, 200, 200, 255], gray.pixels);
        // 16-bit samples keep their high byte.
        let wide = decode_png(&encode(1, 1, png::ColorType::Rgb, png::BitDepth::Sixteen, &[0x12, 0xff, 0x34, 0x01, 0x56, 0x80], None)).unwrap();
        assert_eq!(vec![0x12, 0x34, 0x56, 255], wide.pixels);
        // Palette with a shorter tRNS: later entries stay opaque.
        let indexed = decode_png(&encode(2, 1, png::ColorType::Indexed, png::BitDepth::Eight, &[0, 1], Some(&[9]))).unwrap();
        assert_eq!(vec![10, 20, 30, 9, 40, 50, 60, 255], indexed.pixels);
        // 1-bit grayscale scales to 0/255.
        let bits = decode_png(&encode(8, 1, png::ColorType::Grayscale, png::BitDepth::One, &[0b1010_0000], None)).unwrap();
        assert_eq!([255, 255, 255, 255, 0, 0, 0, 255], bits.pixels[..8]);
        // A corrupted CRC is ignored, as stb_image does.
        let mut corrupt = encode(1, 1, png::ColorType::Rgba, png::BitDepth::Eight, &[1, 2, 3, 4], None);
        corrupt[29] ^= 0xff;
        assert_eq!(vec![1, 2, 3, 4], decode_png(&corrupt).unwrap().pixels);
        assert_eq!(Err("Bad PNG Signature".into()), decode_png(b"GIF89a"));
    }

    #[test]
    fn crop_requires_the_region_inside() {
        let mut image = Image::new(4, 4);
        image.set_abgr(1, 2, 0xdead_beef);
        let cropped = image.crop(1, 2, 2, 2).unwrap();
        assert_eq!(0xdead_beef, cropped.abgr(0, 0));
        assert!(image.crop(3, 0, 2, 1).is_err());
        assert!(image.crop(0, 0, 0, 1).is_err());
    }
}
