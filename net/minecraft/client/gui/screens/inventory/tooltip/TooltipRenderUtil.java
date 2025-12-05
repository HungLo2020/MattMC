package net.minecraft.client.gui.screens.inventory.tooltip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TooltipRenderUtil {
	private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("tooltip/background");
	private static final ResourceLocation FRAME_SPRITE = ResourceLocation.withDefaultNamespace("tooltip/frame");
	public static final int MOUSE_OFFSET = 12;
	private static final int PADDING = 3;
	public static final int PADDING_LEFT = 3;
	public static final int PADDING_RIGHT = 3;
	public static final int PADDING_TOP = 3;
	public static final int PADDING_BOTTOM = 3;
	private static final int MARGIN = 9;

	public static void renderTooltipBackground(GuiGraphics guiGraphics, int i, int j, int k, int l, @Nullable ResourceLocation resourceLocation) {
		int m = i - 3 - 9;
		int n = j - 3 - 9;
		int o = k + 3 + 3 + 18;
		int p = l + 3 + 3 + 18;
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getBackgroundSprite(resourceLocation), m, n, o, p);
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getFrameSprite(resourceLocation), m, n, o, p);
	}

	private static ResourceLocation getBackgroundSprite(@Nullable ResourceLocation resourceLocation) {
		return resourceLocation == null ? BACKGROUND_SPRITE : resourceLocation.withPath(string -> "tooltip/" + string + "_background");
	}

	private static ResourceLocation getFrameSprite(@Nullable ResourceLocation resourceLocation) {
		return resourceLocation == null ? FRAME_SPRITE : resourceLocation.withPath(string -> "tooltip/" + string + "_frame");
	}
}
