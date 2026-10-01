//! Texture groups and the keys that select per-group GUI resources.

use super::*;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::guirender::frontend) enum TextureGroup {
    Alpha,
    Invert,
    Dynamic(u64),
    /// A copied continuous image whose semantic producer requires filtering.
    /// The panorama shares its Rust-owned image with other dynamic GUI users;
    /// only this explicit sampler policy differs.
    DynamicLinear(u64),
    /// Standard/decal glint image: explicit linear repeating sampling.
    DynamicGlint(u64),
    DynamicOpaque(u64),
    DynamicVignette(u64),
    DynamicInvert(u64),
    DynamicPremultiplied(u64),
    DynamicAdditive(u64),
    DynamicLequalDepth(u64),
    /// Item-local translucent material; cutoff is shader state, not a GUI stratum.
    DynamicItemRaster(u64),
    DynamicItemCutout(u64),
}

impl TextureGroup {
    pub(in crate::render::guirender::frontend) fn label(self) -> String {
        match self {
            Self::Alpha => "gui-alpha".to_string(),
            Self::Invert => "gui-invert".to_string(),
            Self::Dynamic(asset_id) => format!("gui-image-{asset_id}"),
            Self::DynamicLinear(asset_id) => format!("gui-image-linear-{asset_id}"),
            Self::DynamicGlint(asset_id) => format!("gui-image-glint-{asset_id}"),
            Self::DynamicOpaque(asset_id) => format!("gui-image-opaque-{asset_id}"),
            Self::DynamicVignette(asset_id) => format!("gui-image-vignette-{asset_id}"),
            Self::DynamicInvert(asset_id) => format!("gui-image-invert-{asset_id}"),
            Self::DynamicPremultiplied(asset_id) => format!("gui-image-premultiplied-{asset_id}"),
            Self::DynamicAdditive(asset_id) => format!("gui-image-additive-{asset_id}"),
            Self::DynamicLequalDepth(asset_id) => format!("gui-image-lequal-depth-{asset_id}"),
            Self::DynamicItemRaster(asset_id) => format!("gui-item-raster-{asset_id}"),
            Self::DynamicItemCutout(asset_id) => format!("gui-item-cutout-{asset_id}"),
        }
    }

    pub(in crate::render::guirender::frontend) fn blend(self) -> BlendMode {
        match self {
            Self::Alpha => BlendMode::Alpha,
            Self::Invert => BlendMode::Invert,
            Self::Dynamic(_) => BlendMode::Alpha,
            Self::DynamicLinear(_) => BlendMode::Alpha,
            Self::DynamicGlint(_) => BlendMode::SrcColorAdditive,
            Self::DynamicOpaque(_) => BlendMode::Disabled,
            Self::DynamicVignette(_) => BlendMode::InverseSrcColorModulate,
            Self::DynamicInvert(_) => BlendMode::Invert,
            Self::DynamicPremultiplied(_) => BlendMode::Premultiplied,
            Self::DynamicAdditive(_) => BlendMode::Additive,
            Self::DynamicLequalDepth(_) => BlendMode::Alpha,
            Self::DynamicItemRaster(_) => BlendMode::Alpha,
            Self::DynamicItemCutout(_) => BlendMode::Disabled,
        }
    }

    pub(in crate::render::guirender::frontend) fn sampling(self) -> SamplerFilter {
        match self {
            Self::DynamicLinear(_) | Self::DynamicGlint(_) => SamplerFilter::Linear,
            _ => SamplerFilter::Nearest,
        }
    }
}

pub(in crate::render::guirender::frontend) fn dynamic_texture_group(stratum: u32, asset_id: u64) -> TextureGroup {
    if stratum == GUI_OPAQUE_BLIT_STRATUM {
        TextureGroup::DynamicOpaque(asset_id)
    } else if stratum == GUI_VIGNETTE_BLIT_STRATUM {
        TextureGroup::DynamicVignette(asset_id)
    } else if stratum == GUI_INVERT_RECTANGLE_STRATUM {
        TextureGroup::DynamicInvert(asset_id)
    } else if stratum == GUI_CROSSHAIR_INVERT_STRATUM {
        TextureGroup::DynamicInvert(asset_id)
    } else if stratum == GUI_PREMULTIPLIED_BLIT_STRATUM {
        TextureGroup::DynamicPremultiplied(asset_id)
    } else if stratum == GUI_ADDITIVE_BLIT_STRATUM {
        TextureGroup::DynamicAdditive(asset_id)
    } else if stratum == GUI_LEQUAL_DEPTH_BLIT_STRATUM {
        TextureGroup::DynamicLequalDepth(asset_id)
    } else {
        TextureGroup::Dynamic(asset_id)
    }
}

pub(in crate::render::guirender::frontend) fn dynamic_mesh_texture_group(draw: &GuiMeshPreparedDraw) -> TextureGroup {
    if draw.material_mode == GuiMeshMaterialMode::Panorama {
        TextureGroup::DynamicLinear(draw.asset_id)
    } else if draw.material_mode == GuiMeshMaterialMode::Glint {
        TextureGroup::DynamicGlint(draw.asset_id)
    } else {
        dynamic_texture_group(draw.stratum, draw.asset_id)
    }
}

#[derive(Clone, Copy)]
pub(in crate::render::guirender::frontend) enum GuiImageOwnership {
    Owned {
        upload_buffer: Handle,
    },
    SharedRaw {
        key: (u64, GuiRawImageFormat),
    },
    /// The GUI atlas cache owns the view; the world owner owns the image.
    /// This binding owns only its sampler and draw resources.
    AtlasView,
}

#[derive(Clone, Copy)]
pub(in crate::render::guirender::frontend) struct SharedDynamicGuiTexture {
    pub(in crate::render::guirender::frontend) upload_buffer: Handle,
    pub(in crate::render::guirender::frontend) texture: Handle,
    pub(in crate::render::guirender::frontend) nearest_sampler: Handle,
    pub(in crate::render::guirender::frontend) linear_sampler: Handle,
    pub(in crate::render::guirender::frontend) texture_view: Handle,
}

impl SharedDynamicGuiTexture {
    pub(in crate::render::guirender::frontend) fn sampler(self, sampling: SamplerFilter) -> Handle {
        match sampling {
            SamplerFilter::Linear => self.linear_sampler,
            SamplerFilter::Nearest => self.nearest_sampler,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::guirender::frontend) enum TextureGroupKey {
    Alpha,
    Invert,
    Dynamic(u64),
    DynamicLinear(u64),
    DynamicGlint(u64),
    DynamicOpaque(u64),
    DynamicVignette(u64),
    DynamicInvert(u64),
    DynamicPremultiplied(u64),
    DynamicAdditive(u64),
    DynamicLequalDepth(u64),
    DynamicItemRaster(u64),
    DynamicItemCutout(u64),
}

impl TextureGroupKey {
    pub(in crate::render::guirender::frontend) fn dynamic_asset_id(self) -> Option<u64> {
        match self {
            Self::Dynamic(asset_id)
            | Self::DynamicLinear(asset_id)
            | Self::DynamicGlint(asset_id)
            | Self::DynamicOpaque(asset_id)
            | Self::DynamicVignette(asset_id)
            | Self::DynamicInvert(asset_id)
            | Self::DynamicPremultiplied(asset_id)
            | Self::DynamicAdditive(asset_id)
            | Self::DynamicLequalDepth(asset_id)
            | Self::DynamicItemRaster(asset_id)
            | Self::DynamicItemCutout(asset_id) => Some(asset_id),
            Self::Alpha | Self::Invert => None,
        }
    }

    pub(in crate::render::guirender::frontend) fn is_dynamic(self) -> bool {
        matches!(
            self,
            Self::Dynamic(_)
                | Self::DynamicLinear(_)
                | Self::DynamicGlint(_)
                | Self::DynamicOpaque(_)
                | Self::DynamicVignette(_)
                | Self::DynamicInvert(_)
                | Self::DynamicPremultiplied(_)
                | Self::DynamicAdditive(_)
                | Self::DynamicLequalDepth(_)
                | Self::DynamicItemRaster(_)
                | Self::DynamicItemCutout(_)
        )
    }
}

impl From<TextureGroup> for TextureGroupKey {
    fn from(value: TextureGroup) -> Self {
        match value {
            TextureGroup::Alpha => Self::Alpha,
            TextureGroup::Invert => Self::Invert,
            TextureGroup::Dynamic(asset_id) => Self::Dynamic(asset_id),
            TextureGroup::DynamicLinear(asset_id) => Self::DynamicLinear(asset_id),
            TextureGroup::DynamicGlint(asset_id) => Self::DynamicGlint(asset_id),
            TextureGroup::DynamicOpaque(asset_id) => Self::DynamicOpaque(asset_id),
            TextureGroup::DynamicVignette(asset_id) => Self::DynamicVignette(asset_id),
            TextureGroup::DynamicInvert(asset_id) => Self::DynamicInvert(asset_id),
            TextureGroup::DynamicPremultiplied(asset_id) => Self::DynamicPremultiplied(asset_id),
            TextureGroup::DynamicAdditive(asset_id) => Self::DynamicAdditive(asset_id),
            TextureGroup::DynamicLequalDepth(asset_id) => Self::DynamicLequalDepth(asset_id),
            TextureGroup::DynamicItemRaster(asset_id) => Self::DynamicItemRaster(asset_id),
            TextureGroup::DynamicItemCutout(asset_id) => Self::DynamicItemCutout(asset_id),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(in crate::render::guirender::frontend) struct ResourceKey {
    pub(in crate::render::guirender::frontend) group: TextureGroupKey,
    pub(in crate::render::guirender::frontend) color_format: ColorFormat,
    pub(in crate::render::guirender::frontend) depth_format: Option<TextureFormat>,
}

impl ResourceKey {
    pub(in crate::render::guirender::frontend) fn new(
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
    ) -> Self {
        Self {
            group: TextureGroupKey::from(group),
            color_format,
            depth_format,
        }
    }
}
