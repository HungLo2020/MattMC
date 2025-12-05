package net.minecraft.client.gui.font;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
		return switch (displayMode) {
			case NORMAL -> this.normal;
			case SEE_THROUGH -> this.seeThrough;
			case POLYGON_OFFSET -> this.polygonOffset;
		};
	}
}
