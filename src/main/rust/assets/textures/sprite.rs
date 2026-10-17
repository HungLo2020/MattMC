//! Sprite contents: animation declarations, Sodium's preprocessing of
//! decoded pixels, and mip chains (`SpriteContents`, `MipmapGenerator`).

use super::color::{linear_to_srgb, srgb_to_linear};
use super::image::Image;
use crate::content::tags::Location;
use crate::core::dfu;
use crate::core::json::Json;
use crate::core::java_log;

/// `AnimationFrame`: an index and an optional duration in ticks.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct DeclaredFrame {
    pub(crate) index: i32,
    pub(crate) time: Option<i32>,
}

/// `AnimationMetadataSection`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Animation {
    pub(crate) frames: Option<Vec<DeclaredFrame>>,
    pub(crate) width: Option<i32>,
    pub(crate) height: Option<i32>,
    pub(crate) frame_time: i32,
    pub(crate) interpolate: bool,
}

/// `AnimationFrame.CODEC`: a non-negative index, or `{index, time?}`.
fn declared_frame(value: &Json) -> Result<DeclaredFrame, String> {
    match dfu::non_negative(value) {
        Ok(index) => Ok(DeclaredFrame { index, time: None }),
        Err(first) => {
            let full = dfu::object(value).and_then(|object| {
                Ok(DeclaredFrame {
                    index: dfu::required(object, "index", dfu::non_negative)?,
                    time: dfu::optional(object, "time", dfu::positive)?,
                })
            });
            full.map_err(|second| format!("Failed to parse either. First: {first}; Second: {second}"))
        }
    }
}

impl Animation {
    /// `AnimationMetadataSection.CODEC`.
    pub(crate) fn decode(value: &Json) -> Result<Animation, String> {
        let object = dfu::object(value)?;
        Ok(Animation {
            frames: dfu::optional(object, "frames", |frames| dfu::list(frames, declared_frame))?,
            width: dfu::optional(object, "width", dfu::positive)?,
            height: dfu::optional(object, "height", dfu::positive)?,
            frame_time: dfu::optional(object, "frametime", dfu::positive)?.unwrap_or(1),
            interpolate: dfu::optional(object, "interpolate", dfu::boolean)?.unwrap_or(false),
        })
    }

    /// `calculateFrameSize(width, height)`.
    pub(crate) fn frame_size(&self, width: u32, height: u32) -> (u32, u32) {
        match (self.width, self.height) {
            (Some(frame_width), Some(frame_height)) => (frame_width as u32, frame_height as u32),
            (Some(frame_width), None) => (frame_width as u32, height),
            (None, Some(frame_height)) => (width, frame_height as u32),
            (None, None) => {
                let side = width.min(height);
                (side, side)
            }
        }
    }
}

/// `SpriteContents.FrameInfo`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Frame {
    pub(crate) index: i32,
    pub(crate) time: i32,
}

/// `SpriteContents.AnimatedTexture` (only for two or more valid frames).
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Animated {
    pub(crate) frames: Vec<Frame>,
    pub(crate) row_size: u32,
    pub(crate) interpolate: bool,
}

/// `createAnimatedTexture`, with its warnings.
pub(crate) fn animated(name: &Location, frame: (u32, u32), image: (u32, u32), animation: &Animation) -> Option<Animated> {
    let row_size = image.0 / frame.0;
    let count = (row_size * (image.1 / frame.1)) as i32;
    let default_time = animation.frame_time;
    let frames = match &animation.frames {
        None => (0..count).map(|index| Frame { index, time: default_time }).collect::<Vec<_>>(),
        Some(declared) => {
            let mut frames = Vec::with_capacity(declared.len());
            let mut used = vec![false; count.max(0) as usize];
            for (position, frame) in declared.iter().enumerate() {
                let frame = Frame { index: frame.index, time: frame.time.unwrap_or(default_time) };
                let mut valid = true;
                if frame.time <= 0 {
                    java_log::warn(&format!("Invalid frame duration on sprite {name} frame {position}: {}", frame.time));
                    valid = false;
                }
                if frame.index < 0 || frame.index >= count {
                    java_log::warn(&format!("Invalid frame index on sprite {name} frame {position}: {}", frame.index));
                    valid = false;
                }
                if valid {
                    used[frame.index as usize] = true;
                    frames.push(frame);
                }
            }
            let unused: Vec<String> = (0..count).filter(|&index| !used[index as usize]).map(|index| index.to_string()).collect();
            if !unused.is_empty() {
                java_log::warn(&format!("Unused frames in sprite {name}: [{}]", unused.join(", ")));
            }
            frames
        }
    };
    (frames.len() > 1).then_some(Animated { frames, row_size, interpolate: animation.interpolate })
}

#[inline]
fn alpha(abgr: u32) -> u32 {
    abgr >> 24
}

#[inline]
fn channel(abgr: u32, shift: u32) -> u8 {
    (abgr >> shift) as u8
}

#[inline]
fn pack(r: u8, g: u8, b: u8, a: u32) -> u32 {
    ((a & 0xff) << 24) | (u32::from(b) << 16) | (u32::from(g) << 8) | u32::from(r)
}

/// Sodium's `fillInTransparentPixelColors`: fully transparent pixels take
/// the alpha-weighted average color (in linear space) of the others.
pub(crate) fn fill_transparent_pixels(image: &mut Image) {
    let (mut r, mut g, mut b, mut total) = (0.0f32, 0.0f32, 0.0f32, 0.0f32);
    for pixel in image.pixels.chunks_exact(4) {
        if pixel[3] != 0 {
            let weight = f32::from(pixel[3]);
            r += srgb_to_linear(pixel[0]) * weight;
            g += srgb_to_linear(pixel[1]) * weight;
            b += srgb_to_linear(pixel[2]) * weight;
            total += weight;
        }
    }
    if total == 0.0 {
        return;
    }
    r /= total;
    g /= total;
    b /= total;
    let average = [linear_to_srgb(r), linear_to_srgb(g), linear_to_srgb(b), 0];
    for pixel in image.pixels.chunks_exact_mut(4) {
        if pixel[3] == 0 {
            pixel.copy_from_slice(&average);
        }
    }
}

/// Sodium's transparency scan: `(transparent, translucent)`, where any
/// pixel at alpha 25 or below is transparent and below 255 translucent, and
/// translucency implies transparency.
pub(crate) fn scan_transparency(image: &Image) -> (bool, bool) {
    let (mut transparent, mut translucent) = (false, false);
    for pixel in image.pixels.chunks_exact(4) {
        if pixel[3] <= 25 {
            transparent = true;
        } else if pixel[3] < 255 {
            translucent = true;
        }
    }
    (transparent | translucent, translucent)
}

/// `MipmapGenerator.weightedAverageColor` over ABGR pixels.
fn weighted_average(one: u32, two: u32) -> u32 {
    let (alpha_one, alpha_two) = (alpha(one), alpha(two));
    if alpha_one == alpha_two {
        return average(one, two, alpha_one);
    }
    if alpha_one == 0 {
        return (two & 0x00FF_FFFF) | ((alpha_two >> 2) << 24);
    }
    if alpha_two == 0 {
        return (one & 0x00FF_FFFF) | ((alpha_one >> 2) << 24);
    }
    let scale = 1.0f32 / (alpha_one + alpha_two) as f32;
    let weight_one = alpha_one as f32 * scale;
    let weight_two = alpha_two as f32 * scale;
    let mut linear = [0.0f32; 3];
    for (slot, shift) in [0u32, 8, 16].into_iter().enumerate() {
        let a = srgb_to_linear(channel(one, shift)) * weight_one;
        let b = srgb_to_linear(channel(two, shift)) * weight_two;
        linear[slot] = a + b;
    }
    let average_alpha = (alpha_one + alpha_two) >> 1;
    pack(linear_to_srgb(linear[0]), linear_to_srgb(linear[1]), linear_to_srgb(linear[2]), average_alpha)
}

/// `MipmapGenerator.averageRgb`: an unweighted average in linear space.
fn average(one: u32, two: u32, alpha: u32) -> u32 {
    let mut out = [0u8; 3];
    for (slot, shift) in [0u32, 8, 16].into_iter().enumerate() {
        let a = srgb_to_linear(channel(one, shift));
        let b = srgb_to_linear(channel(two, shift));
        out[slot] = linear_to_srgb((a + b) * 0.5);
    }
    pack(out[0], out[1], out[2], alpha)
}

/// `MipmapGenerator.generateMipLevels`: each level is a 2x2 box filter of
/// the previous one, blending each row pair, then the two results.
pub(crate) fn mip_chain(base: &Image, levels: usize) -> Vec<Image> {
    let mut chain = Vec::with_capacity(levels + 1);
    chain.push(base.clone());
    for _ in 1..=levels {
        let previous = chain.last().unwrap();
        let (width, height) = (previous.width >> 1, previous.height >> 1);
        let mut next = Image::new(width, height);
        for x in 0..width {
            for y in 0..height {
                let top = weighted_average(previous.abgr(x * 2, y * 2), previous.abgr(x * 2 + 1, y * 2));
                let bottom = weighted_average(previous.abgr(x * 2, y * 2 + 1), previous.abgr(x * 2 + 1, y * 2 + 1));
                next.set_abgr(x, y, weighted_average(top, bottom));
            }
        }
        chain.push(next);
    }
    chain
}

/// `MissingTextureAtlasSprite.generateMissingImage(16, 16)`: magenta and
/// black quarters.
pub(crate) fn missing_image() -> Image {
    let mut image = Image::new(16, 16);
    for y in 0..16 {
        for x in 0..16 {
            let magenta = (y < 8) ^ (x < 8);
            image.set_abgr(x, y, if magenta { 0xFFF8_00F8 } else { 0xFF00_0000 });
        }
    }
    image
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::json::parse_document;

    fn json(text: &str) -> Json {
        parse_document(text.as_bytes()).unwrap()
    }

    fn location(path: &str) -> Location {
        Location::new("minecraft", path)
    }

    #[test]
    fn animation_metadata_and_frames() {
        let animation = Animation::decode(&json(r#"{"frames": [0, {"index": 2, "time": 5}, 1], "frametime": 3, "interpolate": true}"#)).unwrap();
        assert_eq!(Some(vec![
            DeclaredFrame { index: 0, time: None },
            DeclaredFrame { index: 2, time: Some(5) },
            DeclaredFrame { index: 1, time: None },
        ]), animation.frames);
        assert_eq!((16, 16), animation.frame_size(16, 48));
        let water = animated(&location("water"), (16, 16), (16, 48), &animation).unwrap();
        assert_eq!(vec![Frame { index: 0, time: 3 }, Frame { index: 2, time: 5 }, Frame { index: 1, time: 3 }], water.frames);
        assert!(Animation::decode(&json(r#"{"frames": [-1]}"#)).is_err());
        assert!(Animation::decode(&json(r#"{"interpolate": 1}"#)).is_err());
        assert!(Animation::decode(&json(r#"{"frametime": 0}"#)).is_err());
        // One valid frame is not animated.
        let single = Animation::decode(&json(r#"{"frames": [0, 9]}"#)).unwrap();
        assert!(animated(&location("one"), (16, 16), (16, 32), &single).is_none());
    }

    #[test]
    fn preprocessing_matches_sodium() {
        let mut image = Image::new(2, 1);
        image.set_abgr(0, 0, 0xFF10_2030);
        image.set_abgr(1, 0, 0x0000_0000);
        fill_transparent_pixels(&mut image);
        assert_eq!(0x0010_2030, image.abgr(1, 0), "the opaque color, at alpha 0");
        assert_eq!((true, false), scan_transparency(&image));
        // A transparent neighbour keeps the other color at a quarter alpha.
        assert_eq!(0x3F11_2233, weighted_average(0x0000_0000, 0xFF11_2233));
        let chain = mip_chain(&missing_image(), 2);
        assert_eq!((4, 4), (chain[2].width, chain[2].height));
    }
}
