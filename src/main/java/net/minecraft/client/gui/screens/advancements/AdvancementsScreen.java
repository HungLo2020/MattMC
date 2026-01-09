package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AdvancementsScreen extends Screen implements ClientAdvancements.Listener {
	private static final ResourceLocation WINDOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
	public static final int WINDOW_WIDTH = 252;
	public static final int WINDOW_HEIGHT = 140;
	private static final int WINDOW_INSIDE_X = 9;
	private static final int WINDOW_INSIDE_Y = 18;
	public static final int WINDOW_INSIDE_WIDTH = 234;
	public static final int WINDOW_INSIDE_HEIGHT = 113;
	private static final int WINDOW_TITLE_X = 8;
	private static final int WINDOW_TITLE_Y = 6;
	private static final int BACKGROUND_TEXTURE_WIDTH = 256;
	private static final int BACKGROUND_TEXTURE_HEIGHT = 256;
	public static final int BACKGROUND_TILE_WIDTH = 16;
	public static final int BACKGROUND_TILE_HEIGHT = 16;
	public static final int BACKGROUND_TILE_COUNT_X = 14;
	public static final int BACKGROUND_TILE_COUNT_Y = 7;
	private static final double SCROLL_SPEED = 16.0;
	private static final Component VERY_SAD_LABEL = Component.translatable("advancements.sad_label");
	private static final Component NO_ADVANCEMENTS_LABEL = Component.translatable("advancements.empty");
	private static final Component TITLE = Component.translatable("gui.advancements");
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	@Nullable
	private final Screen lastScreen;
	private final ClientAdvancements advancements;
	private final Map<AdvancementHolder, AdvancementTab> tabs = Maps.<AdvancementHolder, AdvancementTab>newLinkedHashMap();
	@Nullable
	private AdvancementTab selectedTab;
	private boolean isScrolling;
	private int windowWidth;
	private int windowHeight;
	private int windowInsideWidth;
	private int windowInsideHeight;

	public AdvancementsScreen(ClientAdvancements clientAdvancements) {
		this(clientAdvancements, null);
	}

	public AdvancementsScreen(ClientAdvancements clientAdvancements, @Nullable Screen screen) {
		super(TITLE);
		this.advancements = clientAdvancements;
		this.lastScreen = screen;
	}

	private void calculateWindowSize() {
		// Scale to use most of the screen (about 90% width and 85% height)
		// Ensure it's at least double the original size
		int targetWidth = (int)(this.width * 0.9);
		int targetHeight = (int)(this.height * 0.85);
		
		// Apply minimum size (at least 2x original) and maximum (90% of screen)
		this.windowWidth = Math.max(WINDOW_WIDTH * 2, Math.min(targetWidth, this.width - 20));
		this.windowHeight = Math.max(WINDOW_HEIGHT * 2, Math.min(targetHeight, this.height - 40));
		
		// Calculate inside dimensions maintaining the same border proportions
		this.windowInsideWidth = this.windowWidth - (WINDOW_WIDTH - WINDOW_INSIDE_WIDTH);
		this.windowInsideHeight = this.windowHeight - (WINDOW_HEIGHT - WINDOW_INSIDE_HEIGHT);
	}

	@Override
	protected void init() {
		this.calculateWindowSize();
		this.layout.addTitleHeader(TITLE, this.font);
		this.tabs.clear();
		this.selectedTab = null;
		this.advancements.setListener(this);
		if (this.selectedTab == null && !this.tabs.isEmpty()) {
			AdvancementTab advancementTab = (AdvancementTab)this.tabs.values().iterator().next();
			this.advancements.setSelectedTab(advancementTab.getRootNode().holder(), true);
		} else {
			this.advancements.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getRootNode().holder(), true);
		}

		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	@Override
	public void removed() {
		this.advancements.setListener(null);
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			clientPacketListener.send(ServerboundSeenAdvancementsPacket.closedScreen());
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (mouseButtonEvent.button() == 0) {
			int i = (this.width - this.windowWidth) / 2;
			int j = (this.height - this.windowHeight) / 2;

			for (AdvancementTab advancementTab : this.tabs.values()) {
				if (advancementTab.isMouseOver(i, j, mouseButtonEvent.x(), mouseButtonEvent.y())) {
					this.advancements.setSelectedTab(advancementTab.getRootNode().holder(), true);
					break;
				}
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.minecraft.options.keyAdvancements.matches(keyEvent)) {
			this.minecraft.setScreen(null);
			this.minecraft.mouseHandler.grabMouse();
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		int k = (this.width - this.windowWidth) / 2;
		int l = (this.height - this.windowHeight) / 2;
		guiGraphics.nextStratum();
		this.renderInside(guiGraphics, k, l);
		guiGraphics.nextStratum();
		this.renderWindow(guiGraphics, k, l);
		this.renderTooltips(guiGraphics, i, j, k, l);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		if (mouseButtonEvent.button() != 0) {
			this.isScrolling = false;
			return false;
		} else {
			if (!this.isScrolling) {
				this.isScrolling = true;
			} else if (this.selectedTab != null) {
				this.selectedTab.scroll(d, e);
			}

			return true;
		}
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (this.selectedTab != null) {
			this.selectedTab.scroll(f * 16.0, g * 16.0);
			return true;
		} else {
			return false;
		}
	}

	private void renderInside(GuiGraphics guiGraphics, int i, int j) {
		AdvancementTab advancementTab = this.selectedTab;
		if (advancementTab == null) {
			guiGraphics.fill(i + WINDOW_INSIDE_X, j + WINDOW_INSIDE_Y, i + WINDOW_INSIDE_X + this.windowInsideWidth, j + WINDOW_INSIDE_Y + this.windowInsideHeight, -16777216);
			int k = i + WINDOW_INSIDE_X + this.windowInsideWidth / 2;
			guiGraphics.drawCenteredString(this.font, NO_ADVANCEMENTS_LABEL, k, j + WINDOW_INSIDE_Y + this.windowInsideHeight / 2 - 9 / 2, -1);
			guiGraphics.drawCenteredString(this.font, VERY_SAD_LABEL, k, j + WINDOW_INSIDE_Y + this.windowInsideHeight - 9, -1);
		} else {
			advancementTab.drawContents(guiGraphics, i + WINDOW_INSIDE_X, j + WINDOW_INSIDE_Y);
		}
	}

	public void renderWindow(GuiGraphics guiGraphics, int i, int j) {
		// Draw the window frame by tiling the texture
		// Top-left corner
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i, j, 0.0F, 0.0F, WINDOW_INSIDE_X, WINDOW_INSIDE_Y, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		
		// Top edge
		for (int x = WINDOW_INSIDE_X; x < this.windowWidth - WINDOW_INSIDE_X; x += BACKGROUND_TILE_WIDTH) {
			int width = Math.min(BACKGROUND_TILE_WIDTH, this.windowWidth - WINDOW_INSIDE_X - x);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i + x, j, WINDOW_INSIDE_X, 0.0F, width, WINDOW_INSIDE_Y, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		}
		
		// Top-right corner
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i + this.windowWidth - WINDOW_INSIDE_X, j, 
			WINDOW_WIDTH - WINDOW_INSIDE_X, 0.0F, WINDOW_INSIDE_X, WINDOW_INSIDE_Y, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		
		// Left edge
		for (int y = WINDOW_INSIDE_Y; y < this.windowHeight - WINDOW_INSIDE_Y; y += BACKGROUND_TILE_HEIGHT) {
			int height = Math.min(BACKGROUND_TILE_HEIGHT, this.windowHeight - WINDOW_INSIDE_Y - y);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i, j + y, 0.0F, WINDOW_INSIDE_Y, WINDOW_INSIDE_X, height, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		}
		
		// Center (background)
		for (int y = WINDOW_INSIDE_Y; y < this.windowHeight - WINDOW_INSIDE_Y; y += BACKGROUND_TILE_HEIGHT) {
			int height = Math.min(BACKGROUND_TILE_HEIGHT, this.windowHeight - WINDOW_INSIDE_Y - y);
			for (int x = WINDOW_INSIDE_X; x < this.windowWidth - WINDOW_INSIDE_X; x += BACKGROUND_TILE_WIDTH) {
				int width = Math.min(BACKGROUND_TILE_WIDTH, this.windowWidth - WINDOW_INSIDE_X - x);
				guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i + x, j + y, WINDOW_INSIDE_X, WINDOW_INSIDE_Y, width, height, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
			}
		}
		
		// Right edge
		for (int y = WINDOW_INSIDE_Y; y < this.windowHeight - WINDOW_INSIDE_Y; y += BACKGROUND_TILE_HEIGHT) {
			int height = Math.min(BACKGROUND_TILE_HEIGHT, this.windowHeight - WINDOW_INSIDE_Y - y);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i + this.windowWidth - WINDOW_INSIDE_X, j + y, 
				WINDOW_WIDTH - WINDOW_INSIDE_X, WINDOW_INSIDE_Y, WINDOW_INSIDE_X, height, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		}
		
		// Bottom-left corner
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i, j + this.windowHeight - WINDOW_INSIDE_Y, 
			0.0F, WINDOW_HEIGHT - WINDOW_INSIDE_Y, WINDOW_INSIDE_X, WINDOW_INSIDE_Y, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		
		// Bottom edge
		for (int x = WINDOW_INSIDE_X; x < this.windowWidth - WINDOW_INSIDE_X; x += BACKGROUND_TILE_WIDTH) {
			int width = Math.min(BACKGROUND_TILE_WIDTH, this.windowWidth - WINDOW_INSIDE_X - x);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i + x, j + this.windowHeight - WINDOW_INSIDE_Y, 
				WINDOW_INSIDE_X, WINDOW_HEIGHT - WINDOW_INSIDE_Y, width, WINDOW_INSIDE_Y, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		}
		
		// Bottom-right corner
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i + this.windowWidth - WINDOW_INSIDE_X, j + this.windowHeight - WINDOW_INSIDE_Y, 
			WINDOW_WIDTH - WINDOW_INSIDE_X, WINDOW_HEIGHT - WINDOW_INSIDE_Y, WINDOW_INSIDE_X, WINDOW_INSIDE_Y, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
		
		if (this.tabs.size() > 1) {
			for (AdvancementTab advancementTab : this.tabs.values()) {
				advancementTab.drawTab(guiGraphics, i, j, advancementTab == this.selectedTab);
			}

			for (AdvancementTab advancementTab : this.tabs.values()) {
				advancementTab.drawIcon(guiGraphics, i, j);
			}
		}

		guiGraphics.drawString(this.font, this.selectedTab != null ? this.selectedTab.getTitle() : TITLE, i + WINDOW_TITLE_X, j + WINDOW_TITLE_Y, -12566464, false);
	}

	private void renderTooltips(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		if (this.selectedTab != null) {
			guiGraphics.pose().pushMatrix();
			guiGraphics.pose().translate(k + WINDOW_INSIDE_X, l + WINDOW_INSIDE_Y);
			guiGraphics.nextStratum();
			this.selectedTab.drawTooltips(guiGraphics, i - k - WINDOW_INSIDE_X, j - l - WINDOW_INSIDE_Y, k, l);
			guiGraphics.pose().popMatrix();
		}

		if (this.tabs.size() > 1) {
			for (AdvancementTab advancementTab : this.tabs.values()) {
				if (advancementTab.isMouseOver(k, l, i, j)) {
					guiGraphics.setTooltipForNextFrame(this.font, advancementTab.getTitle(), i, j);
				}
			}
		}
	}

	public void onAddAdvancementRoot(AdvancementNode advancementNode) {
		AdvancementTab advancementTab = AdvancementTab.create(this.minecraft, this, this.tabs.size(), advancementNode);
		if (advancementTab != null) {
			this.tabs.put(advancementNode.holder(), advancementTab);
		}
	}

	public void onRemoveAdvancementRoot(AdvancementNode advancementNode) {
	}

	public void onAddAdvancementTask(AdvancementNode advancementNode) {
		AdvancementTab advancementTab = this.getTab(advancementNode);
		if (advancementTab != null) {
			advancementTab.addAdvancement(advancementNode);
		}
	}

	public void onRemoveAdvancementTask(AdvancementNode advancementNode) {
	}

	@Override
	public void onUpdateAdvancementProgress(AdvancementNode advancementNode, AdvancementProgress advancementProgress) {
		AdvancementWidget advancementWidget = this.getAdvancementWidget(advancementNode);
		if (advancementWidget != null) {
			advancementWidget.setProgress(advancementProgress);
		}
	}

	@Override
	public void onSelectedTabChanged(@Nullable AdvancementHolder advancementHolder) {
		this.selectedTab = (AdvancementTab)this.tabs.get(advancementHolder);
	}

	public void onAdvancementsCleared() {
		this.tabs.clear();
		this.selectedTab = null;
	}

	@Nullable
	public AdvancementWidget getAdvancementWidget(AdvancementNode advancementNode) {
		AdvancementTab advancementTab = this.getTab(advancementNode);
		return advancementTab == null ? null : advancementTab.getWidget(advancementNode.holder());
	}

	@Nullable
	private AdvancementTab getTab(AdvancementNode advancementNode) {
		AdvancementNode advancementNode2 = advancementNode.root();
		return (AdvancementTab)this.tabs.get(advancementNode2.holder());
	}

	public int getWindowInsideWidth() {
		return this.windowInsideWidth;
	}

	public int getWindowInsideHeight() {
		return this.windowInsideHeight;
	}
}
