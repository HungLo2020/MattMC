//! C ABI for atlas builds (`NativeAtlasBuild`).
//!
//! A build is a boxed `Mutex<Build>` behind a `u64` handle that Java
//! releases. Records are little-endian with u32-length texts.

use std::sync::Mutex;

use super::atlas::Build;
use crate::assets::ffi_util::{give, str_arg, write_text, Writer};
use crate::assets::resources::ffi::with_manager;
use crate::content::tags::Location;

unsafe fn build<'a>(handle: u64) -> Option<&'a Mutex<Build>> {
    (handle != 0).then(|| unsafe { &*(handle as *const Mutex<Build>) })
}

fn lock(build: &Mutex<Build>) -> std::sync::MutexGuard<'_, Build> {
    build.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Runs the atlas definition's sprite sources (`definition`, a resource
/// location) against a resource manager and loads every sprite for the
/// atlas `texture` (named in messages). Returns the build handle, or 0 with the error copied (an
/// invalid generated sprite id, as Java's `ResourceLocation` would throw).
///
/// # Safety
/// `manager` live for the call; strings valid; `error` writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_load(
    manager: u64,
    texture: *const u8,
    texture_len: u64,
    definition: *const u8,
    definition_len: u64,
    keep_metadata: i32,
    error: *mut u8,
    error_capacity: u64,
) -> u64 {
    let parse = |text: Option<&str>| text.and_then(|text| Location::parse(text).ok());
    let (Some(texture), Some(definition)) = (parse(unsafe { str_arg(texture, texture_len) }), parse(unsafe { str_arg(definition, definition_len) })) else {
        unsafe { write_text("invalid atlas id", error, error_capacity) };
        return 0;
    };
    let result = unsafe { with_manager(manager, |manager, filters| Build::load(manager, filters, texture, &definition, keep_metadata != 0)) };
    match result {
        Some(Ok(build)) => Box::into_raw(Box::new(Mutex::new(build))) as u64,
        Some(Err(message)) => {
            unsafe { write_text(&message, error, error_capacity) };
            0
        }
        None => {
            unsafe { write_text("invalid resource manager", error, error_capacity) };
            0
        }
    }
}

/// Sprites with kept sidecar metadata: records of sprite index (u32),
/// name (namespace and path texts) and the JSON text.
///
/// # Safety
/// `handle` live; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_metadata(handle: u64, out_ptr: *mut *mut u8, out_len: *mut u64) {
    let mut writer = Writer::default();
    if let Some(build) = unsafe { build(handle) } {
        for (index, sprite) in lock(build).sprites.iter().enumerate() {
            if let Some(metadata) = &sprite.metadata {
                writer.u32(index as u32);
                writer.text(&sprite.name.namespace);
                writer.text(&sprite.name.path);
                writer.text(metadata);
            }
        }
    }
    unsafe { give(writer.bytes, out_ptr, out_len) };
}

/// Drops sprites by index (Java rejected their extra metadata).
///
/// # Safety
/// `handle` live; `indices` valid for `count`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_drop(handle: u64, indices: *const u32, count: u64) {
    let Some(build) = (unsafe { build(handle) }) else { return };
    if indices.is_null() || count == 0 {
        return;
    }
    let indices: Vec<usize> = unsafe { std::slice::from_raw_parts(indices, count as usize) }.iter().map(|&index| index as usize).collect();
    lock(build).drop_sprites(&indices);
}

/// Stitches the atlas. On success (0) the record is: width, height, mip
/// level, sprite count, then per sprite in Java's map order: index, x, y,
/// frame width and height, sheet width and height, flags (bit 0
/// transparent, 1 translucent, 2 interpolated), namespace, path, frames per
/// row and the u32-counted frames (index, time; empty when not animated).
/// On failure (1) the record is the failing sprite's name, width and
/// height, then every sprite as u32-counted `name[WxH]` texts.
///
/// # Safety
/// `handle` live; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_stitch(
    handle: u64,
    mip: u32,
    max_size: u32,
    min_width: u32,
    min_height: u32,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let Some(build) = (unsafe { build(handle) }) else { return -1 };
    let mut build = lock(build);
    let mut writer = Writer::default();
    let status = match build.stitch(mip, max_size, (min_width, min_height)) {
        Ok(()) => {
            writer.u32(build.width);
            writer.u32(build.height);
            writer.u32(build.mip_level);
            writer.u32(build.placed.len() as u32);
            for &(index, x, y) in &build.placed {
                let sprite = &build.sprites[index];
                writer.u32(index as u32);
                writer.u32(x);
                writer.u32(y);
                writer.u32(sprite.width);
                writer.u32(sprite.height);
                writer.u32(sprite.image.width);
                writer.u32(sprite.image.height);
                let interpolate = sprite.animated.as_ref().is_some_and(|animated| animated.interpolate);
                writer.u32(u32::from(sprite.transparent) | u32::from(sprite.translucent) << 1 | u32::from(interpolate) << 2);
                writer.text(&sprite.name.namespace);
                writer.text(&sprite.name.path);
                match &sprite.animated {
                    Some(animated) => {
                        writer.u32(animated.row_size);
                        writer.u32(animated.frames.len() as u32);
                        for frame in &animated.frames {
                            writer.i32(frame.index);
                            writer.i32(frame.time);
                        }
                    }
                    None => {
                        writer.u32(0);
                        writer.u32(0);
                    }
                }
            }
            0
        }
        Err(failure) => {
            let sprite = &build.sprites[failure.sprite];
            writer.text(&sprite.name.to_string());
            writer.u32(sprite.width);
            writer.u32(sprite.height);
            writer.u32(build.sprites.len() as u32);
            for sprite in &build.sprites {
                writer.text(&format!("{}[{}x{}]", sprite.name, sprite.width, sprite.height));
            }
            1
        }
    };
    unsafe { give(writer.bytes, out_ptr, out_len) };
    status
}

/// Generates every sprite's mip levels (in parallel).
///
/// # Safety
/// `handle` live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_generate_mips(handle: u64) {
    if let Some(build) = unsafe { build(handle) } {
        lock(build).generate_mips();
    }
}

/// Copies mip `level` of each listed sprite into Java image memory
/// (`pointers[i]`, `lengths[i]` bytes, RGBA rows). Returns the number of
/// images that did not match their buffer.
///
/// # Safety
/// `handle` live; arrays valid for `count`; each pointer writable for its length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_copy(handle: u64, level: u32, count: u64, indices: *const u32, pointers: *const u64, lengths: *const u64) -> u64 {
    let Some(build) = (unsafe { build(handle) }) else { return count };
    if count == 0 {
        return 0;
    }
    let build = lock(build);
    let (indices, pointers, lengths) = unsafe {
        (
            std::slice::from_raw_parts(indices, count as usize),
            std::slice::from_raw_parts(pointers, count as usize),
            std::slice::from_raw_parts(lengths, count as usize),
        )
    };
    let mut mismatched = 0;
    for ((&index, &pointer), &length) in indices.iter().zip(pointers).zip(lengths) {
        let image = build.sprites.get(index as usize).and_then(|sprite| sprite.level(level as usize));
        match image {
            Some(image) if image.pixels.len() as u64 == length && pointer != 0 => unsafe {
                std::ptr::copy_nonoverlapping(image.pixels.as_ptr(), pointer as *mut u8, image.pixels.len());
            },
            _ => mismatched += 1,
        }
    }
    mismatched
}

/// Frees a build.
///
/// # Safety
/// `handle` from `mattmc_atlas_load`, released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Mutex<Build>) });
    }
}

/// Prepares pixels for a `SpriteContents` built in Java: validates the
/// declared animation (logging Java's warnings) and applies Sodium's
/// transparent-pixel fill in place. `frames` holds (index, time or -1)
/// pairs, or is absent (`frame_count` -1) for one frame per tile. The record
/// is flags (bit 0 transparent, 1 translucent), frames per row and the
/// u32-counted valid frames (index, time); no frames when not animated.
///
/// # Safety
/// `pixels` writable for `width * height * 4` bytes; other buffers valid;
/// outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sprite_prepare(
    namespace: *const u8,
    namespace_len: u64,
    path: *const u8,
    path_len: u64,
    pixels: *mut u8,
    width: u32,
    height: u32,
    frame_width: u32,
    frame_height: u32,
    animated: i32,
    frames: *const i32,
    frame_count: i32,
    frame_time: i32,
    interpolate: i32,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let (Some(namespace), Some(path)) = (unsafe { str_arg(namespace, namespace_len) }, unsafe { str_arg(path, path_len) }) else { return -1 };
    if pixels.is_null() || width == 0 || height == 0 || frame_width == 0 || frame_height == 0 {
        return -1;
    }
    let length = width as usize * height as usize * 4;
    let memory = unsafe { std::slice::from_raw_parts_mut(pixels, length) };
    let name = Location::new(namespace, path);
    let animation = (animated != 0).then(|| super::sprite::Animation {
        frames: (frame_count >= 0).then(|| {
            let pairs = if frame_count == 0 { &[][..] } else { unsafe { std::slice::from_raw_parts(frames, frame_count as usize * 2) } };
            pairs.chunks_exact(2).map(|pair| super::sprite::DeclaredFrame { index: pair[0], time: (pair[1] >= 0).then_some(pair[1]) }).collect()
        }),
        width: None,
        height: None,
        frame_time,
        interpolate: interpolate != 0,
    });
    let animated = animation.as_ref().and_then(|animation| super::sprite::animated(&name, (frame_width, frame_height), (width, height), animation));
    let mut image = super::image::Image { width, height, pixels: memory.to_vec() };
    super::sprite::fill_transparent_pixels(&mut image);
    memory.copy_from_slice(&image.pixels);
    let (transparent, translucent) = super::sprite::scan_transparency(&image);
    let mut writer = Writer::default();
    writer.u32(u32::from(transparent) | u32::from(translucent) << 1);
    match animated {
        Some(animated) => {
            writer.u32(animated.row_size);
            writer.u32(animated.frames.len() as u32);
            for frame in animated.frames {
                writer.i32(frame.index);
                writer.i32(frame.time);
            }
        }
        None => {
            writer.u32(0);
            writer.u32(0);
        }
    }
    unsafe { give(writer.bytes, out_ptr, out_len) };
    0
}

/// `TextureAtlas.semanticRawSnapshot` for mip `level` from Java's sprite
/// images: per sprite nine u64 values (image pointer, image width and
/// height, sprite width and height, first frame column and row, x, y).
/// Returns 0 with RGBA rows, or 1 when a sprite does not fit (Java's null).
///
/// # Safety
/// Each pointer readable for its image; `records` valid for `count * 9`;
/// outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_atlas_compose(
    width: u32,
    height: u32,
    level: u32,
    records: *const u64,
    count: u64,
    out_ptr: *mut *mut u8,
    out_len: *mut u64,
) -> i32 {
    let records = if count == 0 { &[][..] } else { unsafe { std::slice::from_raw_parts(records, count as usize * 9) } };
    let mut placements = Vec::with_capacity(count as usize);
    for record in records.chunks_exact(9) {
        let (image_width, image_height) = (record[1] as u32, record[2] as u32);
        if record[0] == 0 || image_width == 0 || image_height == 0 {
            return 1;
        }
        let pixels = unsafe { std::slice::from_raw_parts(record[0] as *const u8, image_width as usize * image_height as usize * 4) };
        placements.push(super::atlas::Placement {
            pixels,
            image_width,
            image_height,
            sprite_width: record[3] as u32,
            sprite_height: record[4] as u32,
            frame: (record[5] as u32, record[6] as u32),
            x: record[7] as u32,
            y: record[8] as u32,
        });
    }
    match super::atlas::compose(width, height, level, &placements) {
        Some(pixels) => {
            unsafe { give(pixels, out_ptr, out_len) };
            0
        }
        None => 1,
    }
}
