package net.minecraft.client.gui.components;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class SpriteIconButton extends Button {
	protected final WidgetSprites sprite;
	protected final int spriteWidth;
	protected final int spriteHeight;

	SpriteIconButton(
		int i,
		int j,
		Component component,
		int k,
		int l,
		WidgetSprites widgetSprites,
		Button.OnPress onPress,
		@Nullable Component component2,
		@Nullable Button.CreateNarration createNarration
	) {
		super(0, 0, i, j, component, onPress, createNarration == null ? DEFAULT_NARRATION : createNarration);
		if (component2 != null) {
			this.setTooltip(Tooltip.create(component2));
		}

		this.spriteWidth = k;
		this.spriteHeight = l;
		this.sprite = widgetSprites;
	}

	public static SpriteIconButton.Builder builder(Component component, Button.OnPress onPress, boolean bl) {
		return new SpriteIconButton.Builder(component, onPress, bl);
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final Component message;
		private final Button.OnPress onPress;
		private final boolean iconOnly;
		private int width = 150;
		private int height = 20;
		@Nullable
		private WidgetSprites sprite;
		private int spriteWidth;
		private int spriteHeight;
		@Nullable
		private Component tooltip;
		@Nullable
		private Button.CreateNarration narration;

		public Builder(Component component, Button.OnPress onPress, boolean bl) {
			this.message = component;
			this.onPress = onPress;
			this.iconOnly = bl;
		}

		public SpriteIconButton.Builder width(int i) {
			this.width = i;
			return this;
		}

		public SpriteIconButton.Builder size(int i, int j) {
			this.width = i;
			this.height = j;
			return this;
		}

		public SpriteIconButton.Builder sprite(ResourceLocation resourceLocation, int i, int j) {
			this.sprite = new WidgetSprites(resourceLocation);
			this.spriteWidth = i;
			this.spriteHeight = j;
			return this;
		}

		public SpriteIconButton.Builder sprite(WidgetSprites widgetSprites, int i, int j) {
			this.sprite = widgetSprites;
			this.spriteWidth = i;
			this.spriteHeight = j;
			return this;
		}

		public SpriteIconButton.Builder withTootip() {
			this.tooltip = this.message;
			return this;
		}

		public SpriteIconButton.Builder narration(Button.CreateNarration createNarration) {
			this.narration = createNarration;
			return this;
		}

		public SpriteIconButton build() {
			if (this.sprite == null) {
				throw new IllegalStateException("Sprite not set");
			} else {
				return (SpriteIconButton)(this.iconOnly
					? new SpriteIconButton.CenteredIcon(
						this.width, this.height, this.message, this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.tooltip, this.narration
					)
					: new SpriteIconButton.TextAndIcon(
						this.width, this.height, this.message, this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.tooltip, this.narration
					));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class CenteredIcon extends SpriteIconButton {
		protected CenteredIcon(
			int i,
			int j,
			Component component,
			int k,
			int l,
			WidgetSprites widgetSprites,
			Button.OnPress onPress,
			@Nullable Component component2,
			@Nullable Button.CreateNarration createNarration
		) {
			super(i, j, component, k, l, widgetSprites, onPress, component2, createNarration);
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			super.renderWidget(guiGraphics, i, j, f);
			int k = this.getX() + this.getWidth() / 2 - this.spriteWidth / 2;
			int l = this.getY() + this.getHeight() / 2 - this.spriteHeight / 2;
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED, this.sprite.get(this.isActive(), this.isHoveredOrFocused()), k, l, this.spriteWidth, this.spriteHeight, this.alpha
			);
		}

		@Override
		public void renderString(GuiGraphics guiGraphics, Font font, int i) {
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TextAndIcon extends SpriteIconButton {
		protected TextAndIcon(
			int i,
			int j,
			Component component,
			int k,
			int l,
			WidgetSprites widgetSprites,
			Button.OnPress onPress,
			@Nullable Component component2,
			@Nullable Button.CreateNarration createNarration
		) {
			super(i, j, component, k, l, widgetSprites, onPress, component2, createNarration);
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			super.renderWidget(guiGraphics, i, j, f);
			int k = this.getX() + this.getWidth() - this.spriteWidth - 2;
			int l = this.getY() + this.getHeight() / 2 - this.spriteHeight / 2;
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED, this.sprite.get(this.isActive(), this.isHoveredOrFocused()), k, l, this.spriteWidth, this.spriteHeight, this.alpha
			);
		}

		@Override
		public void renderString(GuiGraphics guiGraphics, Font font, int i) {
			int j = this.getX() + 2;
			int k = this.getX() + this.getWidth() - this.spriteWidth - 4;
			int l = this.getX() + this.getWidth() / 2;
			renderScrollingString(guiGraphics, font, this.getMessage(), l, j, this.getY(), k, this.getY() + this.getHeight(), i);
		}
	}
}
