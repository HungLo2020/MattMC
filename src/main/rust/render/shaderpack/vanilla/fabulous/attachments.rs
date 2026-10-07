//! Fabulous attachment resources, intermediate targets and the attachment set's construction.

use super::*;

pub(super) const FABULOUS_DEPTH_FORMAT: TextureFormat = TextureFormat::Depth32Float;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) enum FabulousTargetRole {
    Main,
    Translucent,
    ItemEntity,
    Particles,
    Clouds,
    Weather,
}

impl FabulousTargetRole {
    /// Maps copied semantic material-source identity to its distinct
    /// Fabulous attachment. Unknown source programs remain unavailable rather
    /// than collapsing into the generic translucent target.
    pub(crate) const fn for_material_source(source_program: u32) -> Option<Self> {
        match source_program {
            crate::render::scene::material::WORLD_MATERIAL_SOURCE_UNSPECIFIED
            | crate::render::scene::material::WORLD_MATERIAL_SOURCE_TEXTURED
            | crate::render::scene::material::WORLD_MATERIAL_SOURCE_ENTITY_MODEL => {
                Some(Self::Translucent)
            }
            crate::render::scene::material::WORLD_MATERIAL_SOURCE_PARTICLES => {
                Some(Self::Particles)
            }
            crate::render::scene::material::WORLD_MATERIAL_SOURCE_CLOUDS => {
                Some(Self::Clouds)
            }
            crate::render::scene::material::WORLD_MATERIAL_SOURCE_WEATHER => {
                Some(Self::Weather)
            }
            _ => None,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct FabulousAttachmentResources {
    pub(crate) role: &'static str,
    pub(crate) color_texture: Handle,
    pub(crate) color_view: Handle,
    pub(crate) depth_texture: Handle,
    pub(crate) depth_view: Handle,
    pub(crate) render_target: Handle,
    pub(crate) render_pass: Handle,
    pub(crate) sampler: Handle,
    pub(crate) extent: Extent3d,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct FabulousIntermediateTarget {
    pub(crate) texture: Handle,
    pub(crate) view: Handle,
    pub(crate) render_target: Handle,
    pub(crate) render_pass: Handle,
    pub(crate) sampler: Handle,
    pub(crate) extent: Extent3d,
}

impl FabulousIntermediateTarget {
    pub(super) fn create(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        let prefix = "fabulous.final";
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let texture = gal.create_texture(TextureDesc {
                label: format!("{prefix}.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                // The bundled transparency graph's final pass is an identity
                // nearest-filter blit (unit ColorModulate).  Keep the
                // intermediate explicitly transferable so that pass can be
                // lowered as the equivalent one-presenter copy to the
                // acquired frame image instead of relying on another
                // fullscreen raster pipeline.
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(texture);
            let view = gal.create_texture_view(TextureViewDesc {
                label: format!("{prefix}.view"),
                texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let render_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{prefix}.target"),
                color_views: vec![view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(render_target);
            let render_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{prefix}.pass"),
                target: render_target,
                color_formats: vec![color_format],
                depth_format: None,
            })?;
            created.push(render_pass);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{prefix}.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            Ok(Self {
                texture,
                view,
                render_target,
                render_pass,
                sampler,
                extent,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub(super) fn handles_in_destroy_order(self) -> [Handle; 5] {
        [
            self.sampler,
            self.render_pass,
            self.render_target,
            self.view,
            self.texture,
        ]
    }
}

impl FabulousAttachmentResources {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        role: &'static str,
        extent: Extent3d,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        Self::create_with_depth_format(
            gal,
            role,
            extent,
            color_format,
            FABULOUS_DEPTH_FORMAT,
            false,
        )
    }

    /// Creates an attachment with an explicit depth/stencil format and,
    /// optionally, transfer-capable color storage.  The optical hand target
    /// uses this rather than changing the six shader-pack attachments: their
    /// depth images remain shader-readable `Depth32Float` resources, while
    /// the hand mask gets a real `Depth24Stencil8` domain.
    pub(crate) fn create_with_depth_format(
        gal: &mut VulkanicGal,
        role: &'static str,
        extent: Extent3d,
        color_format: TextureFormat,
        depth_format: TextureFormat,
        transfer_color: bool,
    ) -> GalResult<Self> {
        if role.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "Fabulous attachment role must be non-empty",
            ));
        }
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "Fabulous attachment extent must be a non-empty 2D image",
            ));
        }
        let prefix = format!("fabulous.{role}");
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let mut color_usages = vec![TextureUsage::ColorAttachment, TextureUsage::Sampled];
            if transfer_color {
                color_usages.extend([TextureUsage::TransferSrc, TextureUsage::TransferDst]);
            }
            let color_texture = gal.create_texture(TextureDesc {
                label: format!("{prefix}.color.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: color_usages,
            })?;
            created.push(color_texture);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{prefix}.color.view"),
                texture: color_texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let depth_texture = gal.create_texture(TextureDesc {
                label: format!("{prefix}.depth.texture"),
                dimension: TextureDimension::D2,
                format: depth_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::DepthStencilAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })?;
            created.push(depth_texture);
            let depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{prefix}.depth.view"),
                texture: depth_texture,
                format: depth_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(depth_view);
            let render_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{prefix}.target"),
                color_views: vec![color_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(render_target);
            let render_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{prefix}.pass"),
                target: render_target,
                color_formats: vec![color_format],
                depth_format: Some(depth_format),
            })?;
            created.push(render_pass);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{prefix}.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            Ok(Self {
                role,
                color_texture,
                color_view,
                depth_texture,
                depth_view,
                render_target,
                render_pass,
                sampler,
                extent,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub(crate) fn handles_in_destroy_order(self) -> [Handle; 7] {
        [
            self.sampler,
            self.render_pass,
            self.render_target,
            self.depth_view,
            self.color_view,
            self.depth_texture,
            self.color_texture,
        ]
    }
}

/// All six external targets for one frame extent. Creation is atomic from the
/// caller's perspective: a failure destroys every resource created so far.
#[derive(Debug)]
pub(crate) struct FabulousAttachmentSet {
    pub(crate) final_target: FabulousIntermediateTarget,
    pub(crate) bindings: Option<FabulousTransparencyBindings>,
    pub(crate) pipelines: Option<FabulousTransparencyPipelines>,
    pub(crate) main: FabulousAttachmentResources,
    pub(crate) translucent: FabulousAttachmentResources,
    pub(crate) item_entity: FabulousAttachmentResources,
    pub(crate) particles: FabulousAttachmentResources,
    pub(crate) clouds: FabulousAttachmentResources,
    pub(crate) weather: FabulousAttachmentResources,
    pub(crate) translucent_color_format: TextureFormat,
    pub(crate) external_color_format: TextureFormat,
    /// Private first-person optical target. It is not part of the shader-pack
    /// external inventory and is only consumed once a semantic optical plan
    /// is admitted by the hand frontend.
    pub(crate) optical_hand: FabulousAttachmentResources,
}

impl FabulousAttachmentSet {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        Self::create_with_external_formats(gal, extent, color_format, color_format, color_format)
    }

    pub(crate) fn create_with_translucent_format(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
        translucent_color_format: TextureFormat,
    ) -> GalResult<Self> {
        Self::create_with_external_formats(
            gal,
            extent,
            color_format,
            translucent_color_format,
            translucent_color_format,
        )
    }

    pub(crate) fn create_with_external_formats(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
        translucent_color_format: TextureFormat,
        external_color_format: TextureFormat,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let final_target = FabulousIntermediateTarget::create(gal, extent, color_format)?;
            created.extend(final_target.handles_in_destroy_order());
            // The private optical hand copy boundary reads/writes only the
            // Rust-owned main color attachment. Keep transfer usage explicit
            // on that resource; every other external attachment remains
            // render/sample-only.
            let main = FabulousAttachmentResources::create_with_depth_format(
                gal,
                "minecraft:main",
                extent,
                color_format,
                FABULOUS_DEPTH_FORMAT,
                true,
            )?;
            created.extend(main.handles_in_destroy_order());
            // The normal deferred terrain capture is an explicit RGBA8
            // attachment. Keep the Fabulous translucent role in that same
            // format so its transfer copy remains format-valid even when
            // the acquired Vulkan swapchain is BGRA8. The main/final roles
            // continue to use the acquired target format and are composed
            // through the declared fullscreen blit pipeline.
            let translucent = FabulousAttachmentResources::create_with_depth_format(
                gal,
                "minecraft:translucent",
                extent,
                translucent_color_format,
                FABULOUS_DEPTH_FORMAT,
                translucent_color_format == TextureFormat::Rgba8Unorm,
            )?;
            created.extend(translucent.handles_in_destroy_order());
            let mut create = |role| {
                FabulousAttachmentResources::create(gal, role, extent, external_color_format)
            };
            let item_entity = create("minecraft:item_entity")?;
            created.extend(item_entity.handles_in_destroy_order());
            let particles = create("minecraft:particles")?;
            created.extend(particles.handles_in_destroy_order());
            let clouds = create("minecraft:clouds")?;
            created.extend(clouds.handles_in_destroy_order());
            let weather = create("minecraft:weather")?;
            created.extend(weather.handles_in_destroy_order());
            let optical_hand = FabulousAttachmentResources::create_with_depth_format(
                gal,
                "mattmc:optical_hand",
                extent,
                color_format,
                TextureFormat::Depth24Stencil8,
                true,
            )?;
            created.extend(optical_hand.handles_in_destroy_order());
            let provisional = Self {
                final_target,
                bindings: None,
                pipelines: None,
                main,
                translucent,
                item_entity,
                particles,
                clouds,
                weather,
                optical_hand,
                translucent_color_format,
                external_color_format,
            };
            let bindings = FabulousTransparencyBindings::create(gal, &provisional)?;
            let pipelines = FabulousTransparencyPipelines::create(gal, &bindings, color_format)?;
            Ok(Self {
                bindings: Some(bindings),
                pipelines: Some(pipelines),
                ..provisional
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub(crate) fn external_inventory(&self) -> FabulousExternalTargetInventory {
        fn binding(
            resource: &FabulousAttachmentResources,
        ) -> VanillaPostEffectExternalTargetBinding {
            VanillaPostEffectExternalTargetBinding {
                render_pass: resource.render_pass,
                render_target: resource.render_target,
                color_attachment: resource.color_view,
                depth_attachment: Some(resource.depth_view),
                sampler: resource.sampler,
                color_usage: TextureUsageState::ShaderRead,
                depth_usage: Some(TextureUsageState::ShaderRead),
            }
        }
        FabulousExternalTargetInventory {
            main: binding(&self.main),
            translucent: binding(&self.translucent),
            item_entity: binding(&self.item_entity),
            particles: binding(&self.particles),
            clouds: binding(&self.clouds),
            weather: binding(&self.weather),
        }
    }
}
