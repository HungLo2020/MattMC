package net.minecraft.client.gui.components;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public abstract class AbstractStringWidget extends AbstractWidget {
	private final Font font;
	private int color = -1;

	public AbstractStringWidget(int i, int j, int k, int l, Component component, Font font) {
		super(i, j, k, l, component);
		this.font = font;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}

	public AbstractStringWidget setColor(int i) {
		this.color = i;
		return this;
	}

	protected final Font getFont() {
		return this.font;
	}

	protected final int getColor() {
		return ARGB.color(this.alpha, this.color);
	}

	@Override
	public void setMessage(Component component) {
		super.setMessage(component);
		this.setWidth(this.getFont().width(component.getVisualOrderText()));
	}
}
