use super::*;

pub(crate) const WORLD_MATERIAL_REGISTRY_VERSION: u32 = 1;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum MaterialSamplerPolicy {
    NearestClamp,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum MaterialMipPolicy {
    SingleMip,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum MaterialTintChannel {
    VertexColor,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct SemanticMaterial {
    pub(crate) key: u32,
    pub(crate) resource_location: &'static str,
    pub(crate) mode: u32,
    pub(crate) cutout_threshold: f32,
    pub(crate) sampler: MaterialSamplerPolicy,
    pub(crate) mip: MaterialMipPolicy,
    pub(crate) tint: MaterialTintChannel,
    pub(crate) emissive: bool,
    pub(crate) fullbright: bool,
    pub(crate) legacy_keys: &'static [u32],
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct SemanticTexture {
    pub(crate) key: u32,
    pub(crate) resource_location: &'static str,
    pub(crate) default_png: &'static [u8],
    pub(crate) legacy_keys: &'static [u32],
}

const MATERIALS: &[SemanticMaterial] = &[
    SemanticMaterial {
        key: WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
        resource_location: "minecraft:material/opaque_textured",
        mode: WORLD_MATERIAL_MODE_OPAQUE,
        cutout_threshold: 0.0,
        sampler: MaterialSamplerPolicy::NearestClamp,
        mip: MaterialMipPolicy::SingleMip,
        tint: MaterialTintChannel::VertexColor,
        emissive: false,
        fullbright: false,
        legacy_keys: &[1],
    },
    SemanticMaterial {
        key: WORLD_MATERIAL_ID_CUTOUT_TEXTURED,
        resource_location: "minecraft:material/cutout_textured",
        mode: WORLD_MATERIAL_MODE_CUTOUT,
        cutout_threshold: 0.5,
        sampler: MaterialSamplerPolicy::NearestClamp,
        mip: MaterialMipPolicy::SingleMip,
        tint: MaterialTintChannel::VertexColor,
        emissive: false,
        fullbright: false,
        legacy_keys: &[2],
    },
    SemanticMaterial {
        key: WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT,
        resource_location: "minecraft:material/block_marker_cutout",
        mode: WORLD_MATERIAL_MODE_CUTOUT,
        cutout_threshold: 0.5,
        sampler: MaterialSamplerPolicy::NearestClamp,
        mip: MaterialMipPolicy::SingleMip,
        tint: MaterialTintChannel::VertexColor,
        emissive: false,
        fullbright: true,
        legacy_keys: &[100],
    },
];

const TEXTURES: &[SemanticTexture] = &[
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_STONE,
        resource_location: "minecraft:textures/block/stone.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/block/stone.png"
        ),
        legacy_keys: &[1],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_DIRT,
        resource_location: "minecraft:textures/block/dirt.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/block/dirt.png"
        ),
        legacy_keys: &[2],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_OAK_LEAVES,
        resource_location: "minecraft:textures/block/oak_leaves.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/block/oak_leaves.png"
        ),
        legacy_keys: &[3],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_DEEPSLATE,
        resource_location: "minecraft:textures/block/deepslate.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/block/deepslate.png"
        ),
        legacy_keys: &[4],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_WHITE_WOOL,
        resource_location: "minecraft:textures/block/white_wool.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/block/white_wool.png"
        ),
        legacy_keys: &[5],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER,
        resource_location: "minecraft:textures/item/barrier.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/barrier.png"
        ),
        legacy_keys: &[100],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_00,
        resource_location: "minecraft:textures/item/light_00.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_00.png"
        ),
        legacy_keys: &[200],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_01,
        resource_location: "minecraft:textures/item/light_01.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_01.png"
        ),
        legacy_keys: &[201],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_02,
        resource_location: "minecraft:textures/item/light_02.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_02.png"
        ),
        legacy_keys: &[202],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_03,
        resource_location: "minecraft:textures/item/light_03.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_03.png"
        ),
        legacy_keys: &[203],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_04,
        resource_location: "minecraft:textures/item/light_04.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_04.png"
        ),
        legacy_keys: &[204],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_05,
        resource_location: "minecraft:textures/item/light_05.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_05.png"
        ),
        legacy_keys: &[205],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_06,
        resource_location: "minecraft:textures/item/light_06.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_06.png"
        ),
        legacy_keys: &[206],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_07,
        resource_location: "minecraft:textures/item/light_07.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_07.png"
        ),
        legacy_keys: &[207],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_08,
        resource_location: "minecraft:textures/item/light_08.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_08.png"
        ),
        legacy_keys: &[208],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_09,
        resource_location: "minecraft:textures/item/light_09.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_09.png"
        ),
        legacy_keys: &[209],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_10,
        resource_location: "minecraft:textures/item/light_10.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_10.png"
        ),
        legacy_keys: &[210],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_11,
        resource_location: "minecraft:textures/item/light_11.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_11.png"
        ),
        legacy_keys: &[211],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_12,
        resource_location: "minecraft:textures/item/light_12.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_12.png"
        ),
        legacy_keys: &[212],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_13,
        resource_location: "minecraft:textures/item/light_13.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_13.png"
        ),
        legacy_keys: &[213],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_14,
        resource_location: "minecraft:textures/item/light_14.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_14.png"
        ),
        legacy_keys: &[214],
    },
    SemanticTexture {
        key: WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15,
        resource_location: "minecraft:textures/item/light_15.png",
        default_png: include_bytes!(
            "../../../../resources/assets/minecraft/textures/item/light_15.png"
        ),
        legacy_keys: &[215],
    },
];

pub(crate) fn material(key: u32) -> Option<&'static SemanticMaterial> {
    let canonical = canonical_material_key(key)?;
    MATERIALS.iter().find(|entry| entry.key == canonical)
}

pub(crate) fn texture(key: u32) -> Option<&'static SemanticTexture> {
    let canonical = canonical_texture_key(key)?;
    TEXTURES.iter().find(|entry| entry.key == canonical)
}

pub(crate) fn canonical_material_key(key: u32) -> Option<u32> {
    MATERIALS
        .iter()
        .find(|entry| entry.key == key || entry.legacy_keys.contains(&key))
        .map(|entry| entry.key)
}

pub(crate) fn canonical_texture_key(key: u32) -> Option<u32> {
    TEXTURES
        .iter()
        .find(|entry| entry.key == key || entry.legacy_keys.contains(&key))
        .map(|entry| entry.key)
}

pub(crate) fn is_known_material_key(key: u32) -> bool {
    canonical_material_key(key).is_some()
}

pub(crate) fn is_known_texture_key(key: u32) -> bool {
    canonical_texture_key(key).is_some()
}

pub(crate) fn material_matches_mode(material_key: u32, mode: u32) -> bool {
    material(material_key)
        .map(|entry| entry.mode == mode)
        .unwrap_or(false)
}

pub(crate) fn cutout_threshold(material_key: u32) -> f32 {
    material(material_key)
        .map(|entry| entry.cutout_threshold)
        .unwrap_or(0.0)
}
