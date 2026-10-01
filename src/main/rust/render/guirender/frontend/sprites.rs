//! Bundled GUI sprites: definitions, decoding and the packed sprite atlas.

use super::*;

#[derive(Clone, Copy)]
pub(super) struct SpriteDef {
    pub(super) id: u32,
    pub(super) stratum: u32,
    pub(super) name: &'static str,
    pub(super) path: &'static str,
    pub(super) width: u32,
    pub(super) height: u32,
    pub(super) group: TextureGroup,
}

#[derive(Clone, Copy)]
pub(super) struct AtlasRegion {
    pub(super) x: u32,
    pub(super) y: u32,
}

#[derive(Clone)]
pub(super) struct TextureAtlas {
    pub(super) width: u32,
    pub(super) height: u32,
    pub(super) bytes: Vec<u8>,
    pub(super) regions: BTreeMap<u32, AtlasRegion>,
}

pub(super) fn validate_request(request: &GuiSpriteRequest, def: &SpriteDef) -> GalResult<()> {
    validate_gui_projection(
        [request.gui_width, request.gui_height],
        request.projection_extent,
    )?;
    if !request.progress_fraction.is_finite() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI sprite progress fraction must be finite",
        ));
    }
    if request.width == 0
        || request.height == 0
        || request.gui_width == 0
        || request.gui_height == 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI sprite dimensions and viewport must be non-zero",
        ));
    }
    if def.id != GUI_POST_EFFECT_INVERT_ID
        && (request.width > def.width || request.height > def.height)
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI sprite '{}' requested dimensions {}x{} exceed semantic sprite {}x{}",
                def.name, request.width, request.height, def.width, def.height
            ),
        ));
    }
    if request.fill_direction > 3 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown GUI fill direction {}", request.fill_direction),
        ));
    }
    Ok(())
}

pub(super) fn build_atlas(
    group: TextureGroup,
    asset_overrides: &BTreeMap<u32, Vec<u8>>,
) -> GalResult<TextureAtlas> {
    let sprites: Vec<&SpriteDef> = SPRITES
        .iter()
        .filter(|sprite| sprite.group == group)
        .collect();
    let mut width = 1u32;
    let mut height = 0u32;
    let mut row_width = 0u32;
    let mut row_height = 0u32;
    for sprite in &sprites {
        if row_width > 0 && row_width + sprite.width > 4096 {
            width = width.max(row_width);
            height += row_height;
            row_width = 0;
            row_height = 0;
        }
        row_width += sprite.width;
        row_height = row_height.max(sprite.height);
    }
    width = width.max(row_width);
    height += row_height;
    let mut bytes = vec![0u8; (width * height * 4) as usize];
    let mut regions = BTreeMap::new();
    let mut x_offset = 0u32;
    let mut y_offset = 0u32;
    row_height = 0;
    for sprite in sprites {
        if x_offset > 0 && x_offset + sprite.width > 4096 {
            x_offset = 0;
            y_offset += row_height;
            row_height = 0;
        }
        let sprite_bytes = load_sprite(sprite, asset_overrides)?;
        for y in 0..sprite.height {
            let src = (y * sprite.width * 4) as usize;
            let dst = ((y_offset + y) * width * 4 + x_offset * 4) as usize;
            bytes[dst..dst + (sprite.width * 4) as usize]
                .copy_from_slice(&sprite_bytes[src..src + (sprite.width * 4) as usize]);
        }
        regions.insert(
            sprite.id,
            AtlasRegion {
                x: x_offset,
                y: y_offset,
            },
        );
        x_offset += sprite.width;
        row_height = row_height.max(sprite.height);
    }
    Ok(TextureAtlas {
        width,
        height,
        bytes,
        regions,
    })
}

pub(super) fn load_sprite(sprite: &SpriteDef, asset_overrides: &BTreeMap<u32, Vec<u8>>) -> GalResult<Vec<u8>> {
    if sprite.id == GUI_POST_EFFECT_INVERT_ID
        || sprite.id == GUI_POST_EFFECT_CREEPER_ID
        || sprite.id == GUI_POST_EFFECT_SPIDER_ID
    {
        // Vanilla's bundled invert post chain uses InverseAmount = 0.8.
        // BlendMode::Invert computes `src * (1-dst) + dst * (1-src)`, so a
        // Rust-owned 0.8 source reproduces that semantic mix without sampling
        // the destination through a backend-specific attachment.
        return Ok(vec![204u8, 204u8, 204u8, 255u8]);
    }
    let bytes = asset_overrides
        .get(&sprite.id)
        .map(Vec::as_slice)
        .or_else(|| bundled_sprite_bytes(sprite.path))
        .ok_or_else(|| {
            GalError::backend(format!("missing bundled GUI sprite '{}'", sprite.path))
        })?;
    decode_sprite_bytes(sprite, bytes)
}

pub(super) fn decode_sprite_bytes(sprite: &SpriteDef, bytes: &[u8]) -> GalResult<Vec<u8>> {
    let mut decoder = png::Decoder::new(BufReader::new(bytes));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder.read_info().map_err(|error| {
        GalError::backend(format!(
            "failed to decode GUI sprite '{}': {error}",
            sprite.path
        ))
    })?;
    let header = reader.info();
    let header_pixels = (header.width as u64)
        .checked_mul(header.height as u64)
        .ok_or_else(|| {
            GalError::backend(format!("GUI sprite '{}' dimensions overflow", sprite.name))
        })?;
    if header.width != sprite.width || header.height != sprite.height {
        return Err(GalError::backend(format!(
            "unexpected GUI sprite dimensions for '{}': {}x{}, expected {}x{}",
            sprite.name, header.width, header.height, sprite.width, sprite.height
        )));
    }
    if header_pixels == 0 || header_pixels > GUI_MAX_RAW_IMAGE_PIXELS as u64 {
        return Err(GalError::backend(format!(
            "GUI sprite '{}' decoded pixel count {header_pixels} exceeds {GUI_MAX_RAW_IMAGE_PIXELS}",
            sprite.name
        )));
    }
    let mut buf = vec![0; reader.output_buffer_size()];
    let info = reader.next_frame(&mut buf).map_err(|error| {
        GalError::backend(format!(
            "failed to read GUI sprite '{}': {error}",
            sprite.path
        ))
    })?;
    let data = &buf[..info.buffer_size()];
    match info.color_type {
        png::ColorType::Rgba => Ok(data.to_vec()),
        png::ColorType::Rgb => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(3) {
                rgba.extend_from_slice(&[pixel[0], pixel[1], pixel[2], 255]);
            }
            Ok(rgba)
        }
        png::ColorType::GrayscaleAlpha => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(2) {
                rgba.extend_from_slice(&[pixel[0], pixel[0], pixel[0], pixel[1]]);
            }
            Ok(rgba)
        }
        png::ColorType::Grayscale => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for value in data {
                rgba.extend_from_slice(&[*value, *value, *value, 255]);
            }
            Ok(rgba)
        }
        png::ColorType::Indexed => Err(GalError::backend(format!(
            "indexed GUI sprite '{}' was not expanded by the PNG decoder",
            sprite.name
        ))),
    }
}

pub(super) fn bundled_sprite_bytes(path: &str) -> Option<&'static [u8]> {
    match path {
        "/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/armor_empty.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/armor_empty.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/armor_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/armor_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/armor_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/armor_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/air.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/air.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/air_bursting.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/air_bursting.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/air_empty.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/air_empty.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_empty.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/food_empty.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_half.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/food_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_full.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/food_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png" => Some(include_bytes!("../../../../resources/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png").as_slice()),
        _ => None,
    }
}

pub(super) fn sprite_def(sprite_id: u32) -> GalResult<&'static SpriteDef> {
    SPRITES
        .iter()
        .find(|sprite| sprite.id == sprite_id)
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown GUI sprite id {sprite_id}"),
            )
        })
}
