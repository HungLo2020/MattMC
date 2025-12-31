package net.minecraft.client.gui.components;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class FocusableTextWidget extends MultiLineTextWidget {
	public static final int DEFAULT_PADDING = 4;
	private final boolean alwaysShowBorder;
	private final FocusableTextWidget.BackgroundFill backgroundFill;
	private final int padding;

	public FocusableTextWidget(int i, Component component, Font font) {
		this(i, component, font, 4);
	}

	public FocusableTextWidget(int i, Component component, Font font, int j) {
		this(i, component, font, true, FocusableTextWidget.BackgroundFill.ALWAYS, j);
	}

	public FocusableTextWidget(int i, Component component, Font font, boolean bl, FocusableTextWidget.BackgroundFill backgroundFill, int j) {
		super(component, font);
		this.setMaxWidth(i);
		this.setCentered(true);
		this.active = true;
		this.alwaysShowBorder = bl;
		this.backgroundFill = backgroundFill;
		this.padding = j;
	}

	public void containWithin(int i) {
		this.setMaxWidth(i - this.padding * 4);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		int k = this.getX() - this.padding;
		int l = this.getY() - this.padding;
		int m = this.getWidth() + this.padding * 2;
		int n = this.getHeight() + this.padding * 2;
		int o = ARGB.color(this.alpha, this.alwaysShowBorder ? (this.isFocused() ? -1 : -6250336) : -1);
		switch (this.backgroundFill) {
			case ALWAYS:
				guiGraphics.fill(k + 1, l, k + m, l + n, ARGB.color(this.alpha, -16777216));
				break;
			case ON_FOCUS:
				if (this.isFocused()) {
					guiGraphics.fill(k + 1, l, k + m, l + n, ARGB.color(this.alpha, -16777216));
				}
			case NEVER:
		}

		if (this.isFocused() || this.alwaysShowBorder) {
			guiGraphics.submitOutline(k, l, m, n, o);
		}

		super.renderWidget(guiGraphics, i, j, f);
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	@Environment(EnvType.CLIENT)
	public static enum BackgroundFill {
		ALWAYS,
		ON_FOCUS,
		NEVER;
	}
}
