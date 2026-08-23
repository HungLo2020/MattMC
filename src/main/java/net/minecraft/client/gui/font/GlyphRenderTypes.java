package net.minecraft.client.gui.font;

import net.blaze3d.pipeline.RenderPipeline;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public record GlyphRenderTypes(RenderType normal, RenderType seeThrough, RenderType polygonOffset, RenderPipeline guiPipeline) {
	public static GlyphRenderTypes createForIntensityTexture(ResourceLocation resourceLocation) {
		return new GlyphRenderTypes(
			RenderType.textIntensity(resourceLocation),
			RenderType.textIntensitySeeThrough(resourceLocation),
			RenderType.textIntensityPolygonOffset(resourceLocation),
			RenderPipelines.GUI_TEXT_INTENSITY
		);
	}

	public static GlyphRenderTypes createForColorTexture(ResourceLocation resourceLocation) {
		return new GlyphRenderTypes(
			RenderType.text(resourceLocation), RenderType.textSeeThrough(resourceLocation), RenderType.textPolygonOffset(resourceLocation), RenderPipelines.GUI_TEXT
		);
	}

	public RenderType select(Font.DisplayMode displayMode) {
		RenderType renderType = switch (displayMode) {
			case NORMAL -> this.normal;
			case SEE_THROUGH -> this.seeThrough;
			case POLYGON_OFFSET -> this.polygonOffset;
		};
		
		// Iris block-entity render-state wrapping is compatibility-only. Rust
		// semantic text carries its own producer identity and must not consult
		// ImmediateState while selecting a glyph pipeline.
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& net.irisshaders.iris.vertices.ImmediateState.isRenderingBEs) {
			renderType = net.irisshaders.iris.layer.OuterWrappedRenderType.wrapExactlyOnce("iris:block_entity", renderType, net.irisshaders.iris.layer.BlockEntityRenderStateShard.INSTANCE);
		}
		
		return renderType;
	}
}
