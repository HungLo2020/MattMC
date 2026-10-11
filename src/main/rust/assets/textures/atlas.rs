//! One atlas build (`SpriteLoader.loadAndStitch`): sprite sources run,
//! sprites load in parallel, then the atlas is stitched, and mip levels are
//! generated per sprite. Java copies the finished sprites into its own
//! images and releases the build; first-frame snapshots are composed from
//! those images.

use super::image::{decode_png, Image};
use super::sources::{self, abgr_to_argb, argb_to_abgr, Supplier};
use super::sprite::{self, Animated, Animation};
use super::stitcher::{self, DoesNotFit, Entry, Layout};
use crate::assets::resources::manager::{Filters, Manager};
use crate::content::tags::Location;
use crate::core::json::{self, Json};
use crate::core::{java_log, parallel};

/// A loaded sprite (`SpriteContents`).
#[derive(Debug)]
pub(crate) struct Sprite {
    pub(crate) name: Location,
    /// Frame size.
    pub(crate) width: u32,
    pub(crate) height: u32,
    /// The whole sheet after Sodium's preprocessing (mip level 0).
    pub(crate) image: Image,
    /// Mip levels 1 and up, once generated.
    pub(crate) mips: Vec<Image>,
    pub(crate) animated: Option<Animated>,
    pub(crate) transparent: bool,
    pub(crate) translucent: bool,
    /// The sidecar's JSON, kept when Java decodes extra sections from it.
    pub(crate) metadata: Option<String>,
}

impl Sprite {
    /// `new SpriteContents(name, frameSize, image, animation, ...)`.
    fn new(name: Location, frame: (u32, u32), mut image: Image, animation: Option<&Animation>, metadata: Option<String>) -> Sprite {
        let animated = animation.and_then(|animation| sprite::animated(&name, frame, (image.width, image.height), animation));
        sprite::fill_transparent_pixels(&mut image);
        let (transparent, translucent) = sprite::scan_transparency(&image);
        Sprite { name, width: frame.0, height: frame.1, image, mips: Vec::new(), animated, transparent, translucent, metadata }
    }

    /// `MissingTextureAtlasSprite.create()`.
    fn missing() -> Sprite {
        let image = sprite::missing_image();
        Sprite::new(Location::new("minecraft", "missingno"), (16, 16), image, None, None)
    }

    /// The image at mip `level` (0 is the sheet).
    pub(crate) fn level(&self, level: usize) -> Option<&Image> {
        if level == 0 {
            Some(&self.image)
        } else {
            self.mips.get(level - 1)
        }
    }
}

/// `Mth.floor(double)`.
fn floor(value: f64) -> i32 {
    let truncated = value as i32;
    if value < f64::from(truncated) {
        truncated - 1
    } else {
        truncated
    }
}

/// `Mth.log2` of a positive int: the floor of log2.
fn log2(value: u32) -> u32 {
    31 - value.leading_zeros()
}

/// `SpriteResourceLoader.loadSprite` for a texture file.
fn load_file(manager: &Manager, name: &Location, file: &Location, found: &crate::assets::resources::manager::Found, keep_metadata: bool) -> Option<Sprite> {
    let mut animation = None;
    let mut kept = None;
    if let Some(sidecar) = found.metadata {
        let path = format!("{}.mcmeta", file.path);
        let parsed = manager.read(sidecar, &file.namespace, &path).map_err(|error| error.to_string()).and_then(|bytes| {
            // `ResourceMetadata.fromJsonStream`: `GsonHelper.parse`, the first value, an object.
            let value = json::parse_value(&bytes).map_err(|error| error.to_string())?;
            let object = match &value {
                Json::Object(object) => object,
                Json::Null => return Err("JSON data was null or empty".to_owned()),
                other => return Err(format!("Expected a JSON object, was {other}")),
            };
            // `jsonObject.has` is true for a null member, which then fails to decode.
            let animation = object.get("animation").map(Animation::decode).transpose()?;
            Ok((bytes, animation))
        });
        match parsed {
            Ok((bytes, decoded)) => {
                animation = decoded;
                if keep_metadata {
                    // Java decodes its extra sections from the file's own text.
                    kept = Some(String::from_utf8_lossy(&bytes).into_owned());
                }
            }
            Err(error) => {
                java_log::error(&format!("Unable to parse metadata from {name}\n{error}"));
                return None;
            }
        }
    }
    let image = match manager.read(found.resource, &file.namespace, &file.path).map_err(|error| error.to_string()).and_then(|bytes| decode_png(&bytes)) {
        Ok(image) => image,
        Err(error) => {
            java_log::error(&format!("Using missing texture, unable to load {name}\n{error}"));
            return None;
        }
    };
    let frame = match &animation {
        Some(animation) => {
            let frame = animation.frame_size(image.width, image.height);
            if image.width % frame.0 != 0 || image.height % frame.1 != 0 {
                java_log::error(&format!(
                    "Image {name} size {},{} is not multiple of frame size {},{}",
                    image.width, image.height, frame.0, frame.1
                ));
                return None;
            }
            frame
        }
        None => (image.width, image.height),
    };
    Some(Sprite::new(name.clone(), frame, image, animation.as_ref(), kept))
}

/// One supplier's sprite: `None` when Java's supplier returned null.
fn load(manager: &Manager, name: &Location, supplier: &Supplier, keep_metadata: bool) -> Option<Sprite> {
    match supplier {
        Supplier::File { file, found } => load_file(manager, name, file, found, keep_metadata),
        Supplier::Unstitch { image, region, divisor_x, divisor_y } => {
            let cropped = image.get(manager).and_then(|image| {
                let width = f64::from(image.width) / divisor_x;
                let height = f64::from(image.height) / divisor_y;
                let (x, y) = (floor(region.x * width), floor(region.y * height));
                let (w, h) = (floor(region.width * width), floor(region.height * height));
                image.crop(x, y, w, h)
            });
            match cropped {
                Ok(cropped) => {
                    let frame = (cropped.width, cropped.height);
                    Some(Sprite::new(region.sprite.clone(), frame, cropped, None, None))
                }
                Err(error) => {
                    java_log::error(&format!("Failed to unstitch region {}\n{error}", region.sprite));
                    Some(Sprite::missing())
                }
            }
        }
        Supplier::Paletted { image, permutation } => {
            let applied = image.get(manager).map_err(Some).and_then(|base| {
                let mapping = permutation.mapping(manager).map_err(|()| None)?;
                let mut mapped = Image::new(base.width, base.height);
                for (from, to) in base.pixels.chunks_exact(4).zip(mapped.pixels.chunks_exact_mut(4)) {
                    let argb = abgr_to_argb(u32::from_le_bytes(from.try_into().unwrap()));
                    let alpha = argb >> 24;
                    let result = if alpha == 0 {
                        argb
                    } else {
                        let rgb = argb & 0x00FF_FFFF;
                        let target = mapping.get(&rgb).copied().unwrap_or(0xFF00_0000 | rgb);
                        (((alpha * (target >> 24)) / 255) << 24) | (target & 0x00FF_FFFF)
                    };
                    to.copy_from_slice(&argb_to_abgr(result).to_le_bytes());
                }
                Ok(mapped)
            });
            match applied {
                Ok(mapped) => {
                    let frame = (mapped.width, mapped.height);
                    Some(Sprite::new(name.clone(), frame, mapped, None, None))
                }
                Err(error) => {
                    let detail = error.map(|error| format!("\n{error}")).unwrap_or_default();
                    java_log::error(&format!("unable to apply palette to {name}{detail}"));
                    None
                }
            }
        }
    }
}

/// `StitcherException` details for Java's crash report.
#[derive(Debug)]
pub(crate) struct StitchFailure {
    pub(crate) sprite: usize,
}

/// An atlas build.
pub(crate) struct Build {
    /// The atlas texture (`SpriteLoader`'s location), for messages.
    pub(crate) atlas: Location,
    pub(crate) sprites: Vec<Sprite>,
    pub(crate) layout: Option<Layout>,
    /// Placements as Java's sprite map holds them: one per name, in first
    /// insertion order with the last placement's sprite (`HashMap.put`).
    pub(crate) placed: Vec<(usize, u32, u32)>,
    pub(crate) mip_level: u32,
    pub(crate) width: u32,
    pub(crate) height: u32,
}

impl Build {
    /// `SpriteSourceList.load(...).list(...)` then every supplier, missing
    /// sprite first. Sources run on the caller's thread (filters and
    /// regexes may call Java); images load in parallel.
    pub(crate) fn load(manager: &Manager, filters: &dyn Filters, atlas: Location, definition: &Location, keep_metadata: bool) -> Result<Build, String> {
        let definitions = sources::load_definitions(manager, filters, definition);
        let suppliers = sources::evaluate(manager, filters, &definitions)?;
        let loaded = parallel::map(&suppliers, |(name, supplier)| load(manager, name, supplier, keep_metadata));
        let mut sprites = Vec::with_capacity(loaded.len() + 1);
        sprites.push(Sprite::missing());
        sprites.extend(loaded.into_iter().flatten());
        Ok(Build { atlas, sprites, layout: None, placed: Vec::new(), mip_level: 0, width: 0, height: 0 })
    }

    /// Drops sprites Java rejected (their extra metadata failed to decode).
    pub(crate) fn drop_sprites(&mut self, rejected: &[usize]) {
        let mut keep = vec![true; self.sprites.len()];
        for &index in rejected {
            if let Some(slot) = keep.get_mut(index) {
                *slot = false;
            }
        }
        let mut index = 0;
        self.sprites.retain(|_| {
            index += 1;
            keep[index - 1]
        });
    }

    /// `SpriteLoader.stitch`: the mip level is limited by every sprite's
    /// smallest power-of-two factor and the smallest sprite side; the atlas
    /// never shrinks below `minimum`.
    pub(crate) fn stitch(&mut self, mip: u32, max_size: u32, minimum: (u32, u32)) -> Result<(), StitchFailure> {
        let mut smallest = u32::MAX;
        let mut limit = 1u32 << mip;
        for sprite in &self.sprites {
            smallest = smallest.min(sprite.width.min(sprite.height));
            let factor = (sprite.width & sprite.width.wrapping_neg()).min(sprite.height & sprite.height.wrapping_neg());
            if factor < limit {
                java_log::warn(&format!(
                    "Texture {} with size {}x{} limits mip level from {} to {}",
                    sprite.name,
                    sprite.width,
                    sprite.height,
                    log2(limit),
                    log2(factor.max(1))
                ));
                limit = factor;
            }
        }
        let minimum_power = smallest.min(limit);
        let reduced = log2(minimum_power.max(1));
        let level = if reduced < mip {
            java_log::warn(&format!(
                "{}: dropping miplevel from {mip} to {reduced}, because of minimum power of two: {minimum_power}",
                self.atlas
            ));
            reduced
        } else {
            mip
        };
        let entries: Vec<Entry<'_>> = self
            .sprites
            .iter()
            .enumerate()
            .map(|(index, sprite)| Entry { index, width: sprite.width, height: sprite.height, namespace: &sprite.name.namespace, path: &sprite.name.path })
            .collect();
        let layout = stitcher::stitch(&entries, max_size, max_size, mip).map_err(|DoesNotFit { index }| StitchFailure { sprite: index })?;
        self.width = layout.width.max(minimum.0);
        self.height = layout.height.max(minimum.1);
        self.mip_level = level;
        let mut slots: std::collections::HashMap<&Location, usize> = std::collections::HashMap::new();
        let mut placed: Vec<(usize, u32, u32)> = Vec::with_capacity(layout.placements.len());
        for &(index, x, y) in &layout.placements {
            match slots.get(&self.sprites[index].name) {
                Some(&slot) => placed[slot] = (index, x, y),
                None => {
                    slots.insert(&self.sprites[index].name, placed.len());
                    placed.push((index, x, y));
                }
            }
        }
        self.placed = placed;
        self.layout = Some(layout);
        Ok(())
    }

    /// `increaseMipLevel` for every sprite, in parallel.
    pub(crate) fn generate_mips(&mut self) {
        let levels = self.mip_level as usize;
        if levels == 0 {
            return;
        }
        parallel::for_each_mut(&mut self.sprites, |sprite| {
            let mut chain = sprite::mip_chain(&sprite.image, levels);
            chain.remove(0);
            sprite.mips = chain;
        });
    }
}

/// One sprite as the first-frame snapshot reads it: its image at the
/// snapshot's mip level, frame size at level 0, first frame tile and
/// position at level 0.
pub(crate) struct Placement<'a> {
    pub(crate) pixels: &'a [u8],
    pub(crate) image_width: u32,
    pub(crate) image_height: u32,
    pub(crate) sprite_width: u32,
    pub(crate) sprite_height: u32,
    pub(crate) frame: (u32, u32),
    pub(crate) x: u32,
    pub(crate) y: u32,
}

/// `TextureAtlas.semanticRawSnapshot` for one mip level: each sprite's
/// first frame at its position, other pixels zero; `None` when a sprite
/// does not fit (Java returns no snapshot).
pub(crate) fn compose(width: u32, height: u32, level: u32, placements: &[Placement<'_>]) -> Option<Vec<u8>> {
    let width = (width >> level).max(1);
    let height = (height >> level).max(1);
    let mut atlas = vec![0u8; width as usize * height as usize * 4];
    for placement in placements {
        let sprite_width = (placement.sprite_width >> level).max(1);
        let sprite_height = (placement.sprite_height >> level).max(1);
        let (source_x, source_y) = (placement.frame.0 * sprite_width, placement.frame.1 * sprite_height);
        let (target_x, target_y) = (placement.x >> level, placement.y >> level);
        if target_x + sprite_width > width
            || target_y + sprite_height > height
            || source_x + sprite_width > placement.image_width
            || source_y + sprite_height > placement.image_height
        {
            return None;
        }
        let row = sprite_width as usize * 4;
        for line in 0..sprite_height as usize {
            let from = ((source_y as usize + line) * placement.image_width as usize + source_x as usize) * 4;
            let to = ((target_y as usize + line) * width as usize + target_x as usize) * 4;
            atlas[to..to + row].copy_from_slice(&placement.pixels[from..from + row]);
        }
    }
    Some(atlas)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_floor_and_log2() {
        assert_eq!(-1, floor(-0.5));
        assert_eq!(2, floor(2.9));
        assert_eq!(0, floor(f64::NAN));
        assert_eq!(4, log2(16));
        assert_eq!(4, log2(24));
    }

    #[test]
    fn stitches_and_snapshots() {
        let mut build = Build {
            atlas: Location::new("minecraft", "test"),
            sprites: vec![Sprite::missing(), Sprite::new(Location::new("minecraft", "a"), (8, 8), Image::new(8, 8), None, None)],
            layout: None,
            placed: Vec::new(),
            mip_level: 0,
            width: 0,
            height: 0,
        };
        build.stitch(4, 1024, (0, 0)).unwrap();
        // The 8x8 sprite limits mip level to 3.
        assert_eq!(3, build.mip_level);
        build.generate_mips();
        assert_eq!(3, build.sprites[0].mips.len());
        let placements: Vec<Placement<'_>> = build
            .placed
            .iter()
            .map(|&(index, x, y)| {
                let sprite = &build.sprites[index];
                Placement { pixels: &sprite.image.pixels, image_width: sprite.image.width, image_height: sprite.image.height, sprite_width: sprite.width, sprite_height: sprite.height, frame: (0, 0), x, y }
            })
            .collect();
        let snapshot = compose(build.width, build.height, 0, &placements).unwrap();
        assert_eq!((build.width * build.height * 4) as usize, snapshot.len());
        // Both pad to 16x16 at mip 4, so "a" sorts first; the missing sprite
        // follows at x = 16: black, then magenta from its column 8.
        assert_eq!(vec![(1, 0, 0), (0, 16, 0)], build.placed);
        let at = |x: usize| (x * 4)..(x * 4 + 4);
        assert_eq!([0, 0, 0, 0], snapshot[at(0)]);
        assert_eq!([0, 0, 0, 255], snapshot[at(16)]);
        assert_eq!([0xF8, 0, 0xF8, 255], snapshot[at(24)]);
    }
}
