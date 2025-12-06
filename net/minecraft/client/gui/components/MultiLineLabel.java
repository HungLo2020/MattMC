package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface MultiLineLabel {
	MultiLineLabel EMPTY = new MultiLineLabel() {
		@Override
		public int render(GuiGraphics guiGraphics, MultiLineLabel.Align align, int i, int j, int k, boolean bl, int l) {
			return j;
		}

		@Override
		public Style getStyle(MultiLineLabel.Align align, int i, int j, int k, double d, double e) {
			return null;
		}

		@Override
		public int getLineCount() {
			return 0;
		}

		@Override
		public int getWidth() {
			return 0;
		}
	};

	static MultiLineLabel create(Font font, Component... components) {
		return create(font, Integer.MAX_VALUE, Integer.MAX_VALUE, components);
	}

	static MultiLineLabel create(Font font, int i, Component... components) {
		return create(font, i, Integer.MAX_VALUE, components);
	}

	static MultiLineLabel create(Font font, Component component, int i) {
		return create(font, i, Integer.MAX_VALUE, component);
	}

	static MultiLineLabel create(Font font, int i, int j, Component... components) {
		return components.length == 0 ? EMPTY : new MultiLineLabel() {
			@Nullable
			private List<MultiLineLabel.TextAndWidth> cachedTextAndWidth;
			@Nullable
			private Language splitWithLanguage;

			@Override
			public int render(GuiGraphics guiGraphics, MultiLineLabel.Align align, int i, int j, int k, boolean bl, int l) {
				int m = j;

				for (MultiLineLabel.TextAndWidth textAndWidth : this.getSplitMessage()) {
					int n = align.calculateLeft(i, textAndWidth.width);
					guiGraphics.drawString(font, textAndWidth.text, n, m, l);
					m += k;
				}

				return m;
			}

			@Nullable
			@Override
			public Style getStyle(MultiLineLabel.Align align, int i, int j, int k, double d, double e) {
				List<MultiLineLabel.TextAndWidth> list = this.getSplitMessage();
				int l = Mth.floor((e - j) / k);
				if (l >= 0 && l < list.size()) {
					MultiLineLabel.TextAndWidth textAndWidth = (MultiLineLabel.TextAndWidth)list.get(l);
					int m = align.calculateLeft(i, textAndWidth.width);
					if (d < m) {
						return null;
					} else {
						int n = Mth.floor(d - m);
						return font.getSplitter().componentStyleAtWidth(textAndWidth.text, n);
					}
				} else {
					return null;
				}
			}

			private List<MultiLineLabel.TextAndWidth> getSplitMessage() {
				Language language = Language.getInstance();
				if (this.cachedTextAndWidth != null && language == this.splitWithLanguage) {
					return this.cachedTextAndWidth;
				} else {
					this.splitWithLanguage = language;
					List<FormattedText> list = new ArrayList();

					for (Component component : components) {
						list.addAll(font.splitIgnoringLanguage(component, i));
					}

					this.cachedTextAndWidth = new ArrayList();
					int ix = Math.min(list.size(), j);
					List<FormattedText> list2 = list.subList(0, ix);

					for (int jx = 0; jx < list2.size(); jx++) {
						FormattedText formattedText = (FormattedText)list2.get(jx);
						FormattedCharSequence formattedCharSequence = Language.getInstance().getVisualOrder(formattedText);
						if (jx == list2.size() - 1 && ix == j && ix != list.size()) {
							FormattedText formattedText2 = font.substrByWidth(formattedText, font.width(formattedText) - font.width(CommonComponents.ELLIPSIS));
							FormattedText formattedText3 = FormattedText.composite(new FormattedText[]{formattedText2, CommonComponents.ELLIPSIS});
							this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(Language.getInstance().getVisualOrder(formattedText3), font.width(formattedText3)));
						} else {
							this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(formattedCharSequence, font.width(formattedCharSequence)));
						}
					}

					return this.cachedTextAndWidth;
				}
			}

			@Override
			public int getLineCount() {
				return this.getSplitMessage().size();
			}

			@Override
			public int getWidth() {
				return Math.min(i, this.getSplitMessage().stream().mapToInt(MultiLineLabel.TextAndWidth::width).max().orElse(0));
			}
		};
	}

	int render(GuiGraphics guiGraphics, MultiLineLabel.Align align, int i, int j, int k, boolean bl, int l);

	@Nullable
	Style getStyle(MultiLineLabel.Align align, int i, int j, int k, double d, double e);

	int getLineCount();

	int getWidth();

	@Environment(EnvType.CLIENT)
	public static enum Align {
		LEFT {
			@Override
			int calculateLeft(int i, int j) {
				return i;
			}
		},
		CENTER {
			@Override
			int calculateLeft(int i, int j) {
				return i - j / 2;
			}
		},
		RIGHT {
			@Override
			int calculateLeft(int i, int j) {
				return i - j;
			}
		};

		abstract int calculateLeft(int i, int j);
	}

	@Environment(EnvType.CLIENT)
	public record TextAndWidth(FormattedCharSequence text, int width) {
	}
}
