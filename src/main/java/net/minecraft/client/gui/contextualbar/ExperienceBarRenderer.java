package net.minecraft.client.gui.contextualbar;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.gui.RustGalGuiRenderer;

@Environment(EnvType.CLIENT)
public class ExperienceBarRenderer implements ContextualBarRenderer {
	private static final ResourceLocation EXPERIENCE_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
	private static final ResourceLocation EXPERIENCE_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
	private final Minecraft minecraft;

	public ExperienceBarRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		LocalPlayer localPlayer = this.minecraft.player;
		int i = this.left(this.minecraft.getWindow());
		int j = this.top(this.minecraft.getWindow());
		int k = localPlayer.getXpNeededForNextLevel();
		if (k > 0) {
			int l = filledWidth(localPlayer.experienceProgress);
			if (RustGalGuiRenderer.isMigratedGuiDisabledForDiagnostics()) {
				// Dev-only measurement control: no migrated or legacy experience bar draw.
			} else if (RustGalGuiRenderer.isMigratedGuiLegacyControl()) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE, i, j, 182, 5);
				if (l > 0) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, i, j, l, 5);
				}
			} else {
				RustGalGuiRenderer.enqueueExperienceBar(this.minecraft, guiGraphics, i, j, 182, 5, localPlayer.experienceProgress, l);
			}
		}
	}

	public static int filledWidth(float progressFraction) {
		return (int)(progressFraction * 183.0F);
	}

	@Override
	public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
	}
}
