package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public abstract class AbstractButton extends AbstractWidget {
	protected static final int TEXT_MARGIN = 2;
	protected static final WidgetSprites SPRITES = new WidgetSprites(
		ResourceLocation.withDefaultNamespace("widget/button"),
		ResourceLocation.withDefaultNamespace("widget/button_disabled"),
		ResourceLocation.withDefaultNamespace("widget/button_highlighted")
	);

	public AbstractButton(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	public abstract void onPress(InputWithModifiers inputWithModifiers);

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		Minecraft minecraft = Minecraft.getInstance();
		guiGraphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			SPRITES.get(this.active, this.isHoveredOrFocused()),
			this.getX(),
			this.getY(),
			this.getWidth(),
			this.getHeight(),
			ARGB.white(this.alpha)
		);
		int k = ARGB.color(this.alpha, this.active ? -1 : -6250336);
		this.renderString(guiGraphics, minecraft.font, k);
		if (this.isHovered()) {
			guiGraphics.requestCursor(this.isActive() ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
		}
	}

	public void renderString(GuiGraphics guiGraphics, Font font, int i) {
		this.renderScrollingString(guiGraphics, font, 2, i);
	}

	@Override
	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
		this.onPress(mouseButtonEvent);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (!this.isActive()) {
			return false;
		} else if (keyEvent.isSelection()) {
			this.playDownSound(Minecraft.getInstance().getSoundManager());
			this.onPress(keyEvent);
			return true;
		} else {
			return false;
		}
	}
}
