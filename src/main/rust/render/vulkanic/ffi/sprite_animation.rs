//! Owned animation resource staging and explicit semantic tick delivery.
//! No normal-game animation admission is implied by these transport endpoints.
use super::*;
use crate::render::vulkanic::sprite_interpolation::{
    OwnedAtlasAnimationUpdate, OwnedSpriteAnimation, SpriteAnimationClock, SpriteAnimationFrame,
    SpriteAtlasRegion, SpriteMipSheet,
};

const MAX_BYTES: usize = 96 * 1024 * 1024;

/// Scalar ABI plus a bounded copied list; no new native layout or GPU handles.
/// accepted_out is zero on backpressure/error, one only after tick acceptance.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_atlas_animation_tick(
    context_id: u64, texture_id: u32, generation: u64, tick: u64,
    visible_ids: *const u32, visible_count: u64, animate_only_visible: u32,
    accepted_out: *mut u32, status_out: *mut FfiStatusResult,
) -> i32 {
    if !accepted_out.is_null() { accepted_out.write(0); }
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(StatusCode::StaleHandle, format!("unknown context id {context_id}"));
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        context.ffi_calls += 1;
        context.ffi_output_bytes = context.ffi_output_bytes.saturating_add(
            (size_of::<FfiStatusResult>() + size_of::<u32>()) as u64);
        let result = (|| {
            if accepted_out.is_null() || texture_id == 0 || generation == 0
                || visible_count > 16384 || animate_only_visible > 1 {
                return Err(GalError::invalid_argument("invalid animation tick transport"));
            }
            let ids = read_limited_slice(FfiSlice { ptr: visible_ids, count: visible_count },
                true, "animation visible sprite ids")?;
            let visible: std::collections::BTreeSet<u32> = ids.iter().copied().collect();
            if visible.len() != ids.len() || visible.contains(&0) {
                return Err(GalError::invalid_argument("invalid animation visibility identities"));
            }
            context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(40 + visible_count * 4);
            context.world_primitive_frontend.advance_atlas_animation_before_frame(&mut context.gal,
                crate::render::vulkanic::sprite_interpolation::AtlasAnimationTickEvent {
                    texture_id, generation, tick, visible, animate_only_visible: animate_only_visible != 0,
                })
        })();
        match result {
            Ok(accepted) => {
                accepted_out.write(u32::from(accepted));
                let mut status = status_ok(context);
                status.submission_id = context.gal.latest_submission_id().0;
                write_status_out(status_out, status);
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_atlas_animation_stage_assets(
    context_id: u64,
    request: *const FfiAtlasAnimationAssetUpdate,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        context.ffi_calls += 1;
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_atlas_animation_update(request).and_then(|update| {
            let input_bytes = size_of::<FfiAtlasAnimationAssetUpdate>() as u64
                + update
                    .sprites
                    .iter()
                    .map(|sprite| {
                        size_of::<FfiSpriteAnimationSource>() as u64
                            + (sprite.clock.declared_frame_count()
                                * size_of::<FfiWorldMeshAnimationFrameRecord>())
                                as u64
                            + sprite
                                .sheets
                                .iter()
                                .map(|sheet| {
                                    size_of::<FfiSpriteAnimationMip>() as u64
                                        + sheet.rgba.len() as u64
                                })
                                .sum::<u64>()
                    })
                    .sum::<u64>();
            context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(input_bytes);
            context
                .world_primitive_frontend
                .stage_atlas_animation_assets(update)
        });
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}

fn invalid() -> GalError {
    GalError::invalid_argument("invalid or over-budget atlas animation declaration")
}

pub(crate) unsafe fn decode_atlas_animation_update(
    request: *const FfiAtlasAnimationAssetUpdate,
) -> GalResult<OwnedAtlasAnimationUpdate> {
    let request = read_struct(request, "atlas animation update")?;
    validate_header::<FfiAtlasAnimationAssetUpdate>(request.header)?;
    if request.texture_id == 0
        || request.generation == 0
        || request.reserved0 != 0
        || request.sprites.count > 16_384
    {
        return Err(invalid());
    }
    let sprites = read_limited_slice(request.sprites, true, "atlas animation sprites")?;
    let mut ids = std::collections::BTreeSet::new();
    let mut bytes = 0usize;
    let mut frame_count = 0usize;
    let mut mip_count = 0usize;
    // Preflight the entire nested request, not just each image in isolation.
    // No pixel payload is allocated until aggregate bounds are established.
    for sprite in sprites {
        validate_item_size::<FfiSpriteAnimationSource>(
            sprite.byte_size,
            "sprite animation source",
        )?;
        if sprite.sprite_id == 0
            || !ids.insert(sprite.sprite_id)
            || sprite.reserved0 != 0
            || sprite.interpolate > 1
            || sprite.frame_width == 0
            || sprite.frame_height == 0
            || sprite.atlas_x.checked_add(sprite.frame_width).is_none()
            || sprite.atlas_y.checked_add(sprite.frame_height).is_none()
            || sprite.frames.count == 0
            || sprite.frames.count > 16_384
            || sprite.mips.count == 0
            || sprite.mips.count
                > u64::from(sprite.frame_width.min(sprite.frame_height).ilog2() + 1)
        {
            return Err(invalid());
        }
        let alignment = 1u32 << (sprite.mips.count - 1);
        if [
            sprite.atlas_x,
            sprite.atlas_y,
            sprite.frame_width,
            sprite.frame_height,
        ]
        .iter()
        .any(|v| v % alignment != 0)
        {
            return Err(invalid());
        }
        let frames = read_limited_slice(sprite.frames, false, "sprite animation frames")?;
        let mips = read_limited_slice(sprite.mips, false, "sprite animation mips")?;
        frame_count = frame_count.checked_add(frames.len()).ok_or_else(invalid)?;
        mip_count = mip_count.checked_add(mips.len()).ok_or_else(invalid)?;
        if frame_count > 65_536 || mip_count > 65_536 {
            return Err(invalid());
        }
        let mut grid = None;
        for (level, mip) in mips.iter().enumerate() {
            validate_item_size::<FfiSpriteAnimationMip>(mip.byte_size, "sprite animation mip")?;
            let fw = sprite.frame_width >> level;
            let fh = sprite.frame_height >> level;
            if mip.reserved0 != 0
                || mip.width == 0
                || mip.height == 0
                || mip.width % fw != 0
                || mip.height % fh != 0
            {
                return Err(invalid());
            }
            let pixels = u64::from(mip.width) * u64::from(mip.height);
            if pixels > MAX_BYTES as u64 / 4 || mip.rgba.len != pixels * 4 {
                return Err(invalid());
            }
            bytes = bytes
                .checked_add(mip.rgba.len as usize)
                .ok_or_else(invalid)?;
            if bytes > MAX_BYTES {
                return Err(invalid());
            }
            let next_grid = (mip.width / fw, mip.height / fh);
            if grid.is_some_and(|prior| prior != next_grid) {
                return Err(invalid());
            }
            grid = Some(next_grid);
            // Validate pointers while still preflighting, but do not retain them.
            read_bytes(mip.rgba, false, "sprite animation RGBA")?;
        }
        let (columns, rows) = grid.ok_or_else(invalid)?;
        let sheet_frames = u64::from(columns) * u64::from(rows);
        for frame in frames {
            validate_item_size::<FfiWorldMeshAnimationFrameRecord>(
                frame.byte_size,
                "sprite frame",
            )?;
            if frame.reserved0 != 0
                || u64::from(frame.frame_index) >= sheet_frames
                || frame.duration_ticks == 0
                || frame.duration_ticks > i32::MAX as u32
            {
                return Err(invalid());
            }
        }
    }
    let mut owned = Vec::with_capacity(sprites.len());
    for sprite in sprites {
        let raw_mips = read_limited_slice(sprite.mips, false, "sprite animation mips")?;
        let raw_frames = read_limited_slice(sprite.frames, false, "sprite animation frames")?;
        let first = &raw_mips[0];
        let count = (first.width / sprite.frame_width) * (first.height / sprite.frame_height);
        let frames = raw_frames
            .iter()
            .map(|frame| SpriteAnimationFrame {
                index: frame.frame_index,
                duration_ticks: frame.duration_ticks,
            })
            .collect();
        let clock = SpriteAnimationClock::new(
            frames,
            count,
            sprite.interpolate != 0,
            request.initial_tick,
        )?;
        let sheets = raw_mips
            .iter()
            .map(|mip| {
                Ok(SpriteMipSheet {
                    width: mip.width,
                    height: mip.height,
                    rgba: read_bounded_bytes(mip.rgba, false, MAX_BYTES, "sprite animation RGBA")?,
                })
            })
            .collect::<GalResult<Vec<_>>>()?;
        owned.push(OwnedSpriteAnimation {
            sprite_id: sprite.sprite_id,
            region: SpriteAtlasRegion {
                x: sprite.atlas_x,
                y: sprite.atlas_y,
                width: sprite.frame_width,
                height: sprite.frame_height,
            },
            clock,
            sheets,
        });
    }
    Ok(OwnedAtlasAnimationUpdate {
        texture_id: request.texture_id,
        generation: request.generation,
        sprites: owned,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::sprite_interpolation::{
        SpriteFrameUpdate, apply_sprite_sheet_update,
    };

    #[test]
    fn atlas_animation_ffi_copies_sources_and_drives_owned_clock_and_pixels() {
        let mut pixels = vec![10, 20, 30, 7, 110, 120, 130, 99];
        let mips = [FfiSpriteAnimationMip {
            byte_size: size_of::<FfiSpriteAnimationMip>() as u32,
            width: 2,
            height: 1,
            reserved0: 0,
            rgba: FfiBytes {
                ptr: pixels.as_ptr(),
                len: 8,
            },
        }];
        let frames = [
            FfiWorldMeshAnimationFrameRecord {
                byte_size: size_of::<FfiWorldMeshAnimationFrameRecord>() as u32,
                frame_index: 0,
                duration_ticks: 2,
                reserved0: 0,
            },
            FfiWorldMeshAnimationFrameRecord {
                byte_size: size_of::<FfiWorldMeshAnimationFrameRecord>() as u32,
                frame_index: 1,
                duration_ticks: 2,
                reserved0: 0,
            },
        ];
        let mut sprites = [FfiSpriteAnimationSource {
            byte_size: size_of::<FfiSpriteAnimationSource>() as u32,
            sprite_id: 1,
            atlas_x: 0,
            atlas_y: 0,
            frame_width: 1,
            frame_height: 1,
            interpolate: 1,
            reserved0: 0,
            frames: FfiSlice {
                ptr: frames.as_ptr(),
                count: 2,
            },
            mips: FfiSlice {
                ptr: mips.as_ptr(),
                count: 1,
            },
        }];
        let mut request = FfiAtlasAnimationAssetUpdate {
            header: FfiHeader {
                byte_size: size_of::<FfiAtlasAnimationAssetUpdate>() as u32,
                version: FFI_ABI_VERSION,
            },
            texture_id: 1,
            generation: 2,
            initial_tick: 40,
            reserved0: 0,
            sprites: FfiSlice {
                ptr: sprites.as_ptr(),
                count: 1,
            },
        };
        let mut owned = unsafe { decode_atlas_animation_update(&request) }.unwrap();
        pixels.fill(0);
        assert_eq!(owned.texture_id, 1);
        assert_eq!(owned.generation, 2);
        let sprite = &mut owned.sprites[0];
        assert_eq!(sprite.sprite_id, 1);
        assert_eq!(
            sprite.clock.initial_frame(),
            SpriteFrameUpdate::Copy { index: 0 }
        );
        let update = sprite.clock.tick(41, true, true).unwrap().unwrap();
        let mut atlas = vec![vec![0; 4]];
        apply_sprite_sheet_update(1, 1, &mut atlas, &sprite.region, &sprite.sheets, update)
            .unwrap();
        assert_eq!(atlas[0], [60, 70, 80, 7]);
        sprites[0].interpolate = 2;
        request.sprites.ptr = sprites.as_ptr();
        assert!(unsafe { decode_atlas_animation_update(&request) }.is_err());
        sprites[0].interpolate = 1;
        request.sprites.ptr = sprites.as_ptr();
        request.header.version = 29;
        assert!(unsafe { decode_atlas_animation_update(&request) }.is_err());
    }

    #[test]
    fn atlas_animation_ffi_layouts_are_explicit_and_resource_only() {
        assert_eq!(size_of::<FfiSpriteAnimationMip>(), 32);
        assert_eq!(size_of::<FfiSpriteAnimationSource>(), 64);
        assert_eq!(size_of::<FfiAtlasAnimationAssetUpdate>(), 48);
        for (id, size, fields) in [(102, 32, 5), (103, 64, 10), (104, 48, 6)] {
            let layout = super::super::layout::layout_for_struct(id).unwrap();
            assert_eq!(layout.byte_size, size);
            assert_eq!(layout.field_count, fields);
        }
    }
}
