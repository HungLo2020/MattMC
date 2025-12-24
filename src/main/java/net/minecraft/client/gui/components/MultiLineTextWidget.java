package net.minecraft.client.gui.components;

import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.SingleKeyCache;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MultiLineTextWidget extends AbstractStringWidget {
	private OptionalInt maxWidth = OptionalInt.empty();
	private OptionalInt maxRows = OptionalInt.empty();
	private final SingleKeyCache<MultiLineTextWidget.CacheKey, MultiLineLabel> cache;
	private boolean centered = false;
	private boolean allowHoverComponents = false;
	@Nullable
	private Consumer<Style> componentClickHandler = null;

	public MultiLineTextWidget(Component component, Font font) {
		this(0, 0, component, font);
	}

	public MultiLineTextWidget(int i, int j, Component component, Font font) {
		super(i, j, 0, 0, component, font);
		this.cache = Util.singleKeyCache(
			cacheKey -> cacheKey.maxRows.isPresent()
				? MultiLineLabel.create(font, cacheKey.maxWidth, cacheKey.maxRows.getAsInt(), cacheKey.message)
				: MultiLineLabel.create(font, cacheKey.message, cacheKey.maxWidth)
		);
		this.active = false;
	}

	public MultiLineTextWidget setColor(int i) {
		super.setColor(i);
		return this;
	}

	public MultiLineTextWidget setMaxWidth(int i) {
		this.maxWidth = OptionalInt.of(i);
		return this;
	}

	public MultiLineTextWidget setMaxRows(int i) {
		this.maxRows = OptionalInt.of(i);
		return this;
	}

	public MultiLineTextWidget setCentered(boolean bl) {
		this.centered = bl;
		return this;
	}

	public MultiLineTextWidget configureStyleHandling(boolean bl, @Nullable Consumer<Style> consumer) {
		this.allowHoverComponents = bl;
		this.componentClickHandler = consumer;
		return this;
	}

	@Override
	public int getWidth() {
		return ((MultiLineLabel)this.cache.getValue(this.getFreshCacheKey())).getWidth();
	}

	@Override
	public int getHeight() {
		return ((MultiLineLabel)this.cache.getValue(this.getFreshCacheKey())).getLineCount() * 9;
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		MultiLineLabel multiLineLabel = (MultiLineLabel)this.cache.getValue(this.getFreshCacheKey());
		int k = this.getX();
		int l = this.getY();
		int m = 9;
		int n = this.getColor();
		if (this.centered) {
			int o = k + this.getWidth() / 2;
			multiLineLabel.render(guiGraphics, MultiLineLabel.Align.CENTER, o, l, m, true, n);
		} else {
			multiLineLabel.render(guiGraphics, MultiLineLabel.Align.LEFT, k, l, m, true, n);
		}

		if (this.isHovered() && this.allowHoverComponents) {
			Style style = this.getComponentStyleAt(i, j);
			guiGraphics.renderComponentHoverEffect(this.getFont(), style, i, j);
		}
	}

	@Nullable
	private Style getComponentStyleAt(double d, double e) {
		MultiLineLabel multiLineLabel = (MultiLineLabel)this.cache.getValue(this.getFreshCacheKey());
		int i = this.getX();
		int j = this.getY();
		int k = 9;
		if (this.centered) {
			int l = i + this.getWidth() / 2;
			return multiLineLabel.getStyle(MultiLineLabel.Align.CENTER, l, j, k, d, e);
		} else {
			return multiLineLabel.getStyle(MultiLineLabel.Align.LEFT, i, j, k, d, e);
		}
	}

	@Override
	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.componentClickHandler != null) {
			Style style = this.getComponentStyleAt(mouseButtonEvent.x(), mouseButtonEvent.y());
			if (style != null) {
				this.componentClickHandler.accept(style);
				return;
			}
		}

		super.onClick(mouseButtonEvent, bl);
	}

	private MultiLineTextWidget.CacheKey getFreshCacheKey() {
		return new MultiLineTextWidget.CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
	}

	@Environment(EnvType.CLIENT)
	record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
	}
}
