package net.minecraft.client.gui.screens.packs;

import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TransferableSelectionList extends ObjectSelectionList<TransferableSelectionList.Entry> {
	static final ResourceLocation SELECT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/select_highlighted");
	static final ResourceLocation SELECT_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/select");
	static final ResourceLocation UNSELECT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/unselect_highlighted");
	static final ResourceLocation UNSELECT_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/unselect");
	static final ResourceLocation MOVE_UP_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_up_highlighted");
	static final ResourceLocation MOVE_UP_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_up");
	static final ResourceLocation MOVE_DOWN_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_down_highlighted");
	static final ResourceLocation MOVE_DOWN_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_down");
	static final Component INCOMPATIBLE_TITLE = Component.translatable("pack.incompatible");
	static final Component INCOMPATIBLE_CONFIRM_TITLE = Component.translatable("pack.incompatible.confirm.title");
	private static final int ENTRY_PADDING = 2;
	private final Component title;
	final PackSelectionScreen screen;

	public TransferableSelectionList(Minecraft minecraft, PackSelectionScreen packSelectionScreen, int i, int j, Component component) {
		super(minecraft, i, j, 33, 36);
		this.screen = packSelectionScreen;
		this.title = component;
		this.centerListVertically = false;
	}

	@Override
	public int getRowWidth() {
		return this.width - 4;
	}

	@Override
	protected int scrollBarX() {
		return this.getRight() - 6;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		return this.getSelected() != null ? this.getSelected().keyPressed(keyEvent) : super.keyPressed(keyEvent);
	}

	public void updateList(Stream<PackSelectionModel.Entry> stream, @Nullable PackSelectionModel.EntryBase entryBase) {
		this.clearEntries();
		Component component = Component.empty().append(this.title).withStyle(new ChatFormatting[]{ChatFormatting.UNDERLINE, ChatFormatting.BOLD});
		this.addEntry(new TransferableSelectionList.HeaderEntry(this.minecraft.font, component), (int)(9.0F * 1.5F));
		this.setSelected(null);
		stream.forEach(entry -> {
			TransferableSelectionList.PackEntry packEntry = new TransferableSelectionList.PackEntry(this.minecraft, this, entry);
			this.addEntry(packEntry);
			if (entryBase != null && entryBase.getId().equals(entry.getId())) {
				this.screen.setFocused(this);
				this.setFocused(packEntry);
			}
		});
	}

	@Environment(EnvType.CLIENT)
	public abstract class Entry extends ObjectSelectionList.Entry<TransferableSelectionList.Entry> {
		@Override
		public int getWidth() {
			return super.getWidth() - (TransferableSelectionList.this.scrollbarVisible() ? 6 : 0);
		}

		public abstract String getPackId();
	}

	@Environment(EnvType.CLIENT)
	public class HeaderEntry extends TransferableSelectionList.Entry {
		private final Font font;
		private final Component text;

		public HeaderEntry(final Font font, final Component component) {
			this.font = font;
			this.text = component;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.drawCenteredString(this.font, this.text, this.getX() + this.getWidth() / 2, this.getContentYMiddle() - 9 / 2, -1);
		}

		@Override
		public Component getNarration() {
			return this.text;
		}

		@Override
		public String getPackId() {
			return "";
		}
	}

	@Environment(EnvType.CLIENT)
	public class PackEntry extends TransferableSelectionList.Entry {
		private static final int MAX_DESCRIPTION_WIDTH_PIXELS = 157;
		private static final int MAX_NAME_WIDTH_PIXELS = 157;
		private static final String TOO_LONG_NAME_SUFFIX = "...";
		private final TransferableSelectionList parent;
		protected final Minecraft minecraft;
		private final PackSelectionModel.Entry pack;
		private final FormattedCharSequence nameDisplayCache;
		private final MultiLineLabel descriptionDisplayCache;
		private final FormattedCharSequence incompatibleNameDisplayCache;
		private final MultiLineLabel incompatibleDescriptionDisplayCache;

		public PackEntry(final Minecraft minecraft, final TransferableSelectionList transferableSelectionList2, final PackSelectionModel.Entry entry) {
			this.minecraft = minecraft;
			this.pack = entry;
			this.parent = transferableSelectionList2;
			this.nameDisplayCache = cacheName(minecraft, entry.getTitle());
			this.descriptionDisplayCache = cacheDescription(minecraft, entry.getExtendedDescription());
			this.incompatibleNameDisplayCache = cacheName(minecraft, TransferableSelectionList.INCOMPATIBLE_TITLE);
			this.incompatibleDescriptionDisplayCache = cacheDescription(minecraft, entry.getCompatibility().getDescription());
		}

		private static FormattedCharSequence cacheName(Minecraft minecraft, Component component) {
			int i = minecraft.font.width(component);
			if (i > 157) {
				FormattedText formattedText = FormattedText.composite(
					new FormattedText[]{minecraft.font.substrByWidth(component, 157 - minecraft.font.width("...")), FormattedText.of("...")}
				);
				return Language.getInstance().getVisualOrder(formattedText);
			} else {
				return component.getVisualOrderText();
			}
		}

		private static MultiLineLabel cacheDescription(Minecraft minecraft, Component component) {
			return MultiLineLabel.create(minecraft.font, 157, 2, component);
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", new Object[]{this.pack.getTitle()});
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			PackCompatibility packCompatibility = this.pack.getCompatibility();
			if (!packCompatibility.isCompatible()) {
				int k = this.getContentX() - 1;
				int l = this.getContentY() - 1;
				int m = this.getContentRight() + 1;
				int n = this.getContentBottom() + 1;
				guiGraphics.fill(k, l, m, n, -8978432);
			}

			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.pack.getIconTexture(), this.getContentX(), this.getContentY(), 0.0F, 0.0F, 32, 32, 32, 32);
			FormattedCharSequence formattedCharSequence = this.nameDisplayCache;
			MultiLineLabel multiLineLabel = this.descriptionDisplayCache;
			if (this.showHoverOverlay() && (this.minecraft.options.touchscreen().get() || bl || this.parent.getSelected() == this && this.parent.isFocused())) {
				guiGraphics.fill(this.getContentX(), this.getContentY(), this.getContentX() + 32, this.getContentY() + 32, -1601138544);
				int m = i - this.getContentX();
				int n = j - this.getContentY();
				if (!this.pack.getCompatibility().isCompatible()) {
					formattedCharSequence = this.incompatibleNameDisplayCache;
					multiLineLabel = this.incompatibleDescriptionDisplayCache;
				}

				if (this.pack.canSelect()) {
					if (m < 32) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.SELECT_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
					} else {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.SELECT_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
					}
				} else {
					if (this.pack.canUnselect()) {
						if (m < 16) {
							guiGraphics.blitSprite(
								RenderPipelines.GUI_TEXTURED, TransferableSelectionList.UNSELECT_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32
							);
						} else {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.UNSELECT_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						}
					}

					if (this.pack.canMoveUp()) {
						if (m < 32 && m > 16 && n < 16) {
							guiGraphics.blitSprite(
								RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_UP_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32
							);
						} else {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_UP_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						}
					}

					if (this.pack.canMoveDown()) {
						if (m < 32 && m > 16 && n > 16) {
							guiGraphics.blitSprite(
								RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_DOWN_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32
							);
						} else {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_DOWN_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						}
					}
				}
			}

			guiGraphics.drawString(this.minecraft.font, formattedCharSequence, this.getContentX() + 32 + 2, this.getContentY() + 1, -1);
			multiLineLabel.render(guiGraphics, MultiLineLabel.Align.LEFT, this.getContentX() + 32 + 2, this.getContentY() + 12, 10, true, -8355712);
		}

		@Override
		public String getPackId() {
			return this.pack.getId();
		}

		private boolean showHoverOverlay() {
			return !this.pack.isFixedPosition() || !this.pack.isRequired();
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (keyEvent.isConfirmation()) {
				this.keyboardSelection();
				return true;
			} else {
				if (keyEvent.hasShiftDown()) {
					if (keyEvent.isUp()) {
						this.keyboardMoveUp();
						return true;
					}

					if (keyEvent.isDown()) {
						this.keyboardMoveDown();
						return true;
					}
				}

				return super.keyPressed(keyEvent);
			}
		}

		public void keyboardSelection() {
			if (this.pack.canSelect()) {
				this.handlePackSelection();
			} else if (this.pack.canUnselect()) {
				this.pack.unselect();
			}
		}

		private void keyboardMoveUp() {
			if (this.pack.canMoveUp()) {
				this.pack.moveUp();
			}
		}

		private void keyboardMoveDown() {
			if (this.pack.canMoveDown()) {
				this.pack.moveDown();
			}
		}

		private void handlePackSelection() {
			if (this.pack.getCompatibility().isCompatible()) {
				this.pack.select();
			} else {
				Component component = this.pack.getCompatibility().getConfirmation();
				this.minecraft.setScreen(new ConfirmScreen(bl -> {
					this.minecraft.setScreen(this.parent.screen);
					if (bl) {
						this.pack.select();
					}
				}, TransferableSelectionList.INCOMPATIBLE_CONFIRM_TITLE, component));
			}
		}

		@Override
		public boolean shouldTakeFocusAfterInteraction() {
			return TransferableSelectionList.this.children().stream().anyMatch(entry -> entry.getPackId().equals(this.getPackId()));
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			double d = mouseButtonEvent.x() - this.getX();
			double e = mouseButtonEvent.y() - this.getY();
			if (this.showHoverOverlay() && d <= 32.0) {
				this.parent.screen.clearSelected();
				if (this.pack.canSelect()) {
					this.handlePackSelection();
					return true;
				}

				if (d < 16.0 && this.pack.canUnselect()) {
					this.pack.unselect();
					return true;
				}

				if (d > 16.0 && e < 16.0 && this.pack.canMoveUp()) {
					this.pack.moveUp();
					return true;
				}

				if (d > 16.0 && e > 16.0 && this.pack.canMoveDown()) {
					this.pack.moveDown();
					return true;
				}
			}

			return super.mouseClicked(mouseButtonEvent, bl);
		}
	}
}
