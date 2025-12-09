package net.minecraft.client.gui.screens;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.net.URI;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.TabOrderedElement;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.CopyToClipboard;
import net.minecraft.network.chat.ClickEvent.Custom;
import net.minecraft.network.chat.ClickEvent.OpenFile;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.ClickEvent.ShowDialog;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public abstract class Screen extends AbstractContainerEventHandler implements Renderable {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static final ResourceLocation MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_background.png");
	public static final ResourceLocation HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/header_separator.png");
	public static final ResourceLocation FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/footer_separator.png");
	private static final ResourceLocation INWORLD_MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_background.png");
	public static final ResourceLocation INWORLD_HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_header_separator.png");
	public static final ResourceLocation INWORLD_FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_footer_separator.png");
	protected static final float FADE_IN_TIME = 2000.0F;
	protected final Component title;
	private final List<GuiEventListener> children = Lists.<GuiEventListener>newArrayList();
	private final List<NarratableEntry> narratables = Lists.<NarratableEntry>newArrayList();
	@Nullable
	protected Minecraft minecraft;
	private boolean initialized;
	public int width;
	public int height;
	private final List<Renderable> renderables = Lists.<Renderable>newArrayList();
	protected Font font;
	private final ScreenNarrationCollector narrationState = new ScreenNarrationCollector();
	protected final Executor screenExecutor = runnable -> this.minecraft.execute(() -> {
		if (this.minecraft.screen == this) {
			runnable.run();
		}
	});

	protected Screen(Component component) {
		this.title = component;
	}

	public Component getTitle() {
		return this.title;
	}

	public Component getNarrationMessage() {
		return this.getTitle();
	}

	public final void renderWithTooltipAndSubtitles(GuiGraphics guiGraphics, int i, int j, float f) {
		guiGraphics.nextStratum();
		this.renderBackground(guiGraphics, i, j, f);
		guiGraphics.nextStratum();
		this.render(guiGraphics, i, j, f);
		guiGraphics.renderDeferredElements();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		for (Renderable renderable : this.renderables) {
			renderable.render(guiGraphics, i, j, f);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (keyEvent.isEscape() && this.shouldCloseOnEsc()) {
			this.onClose();
			return true;
		} else if (super.keyPressed(keyEvent)) {
			return true;
		} else {
			FocusNavigationEvent focusNavigationEvent = (FocusNavigationEvent)(switch (keyEvent.key()) {
				case 258 -> this.createTabEvent(!keyEvent.hasShiftDown());
				default -> null;
				case 262 -> this.createArrowEvent(ScreenDirection.RIGHT);
				case 263 -> this.createArrowEvent(ScreenDirection.LEFT);
				case 264 -> this.createArrowEvent(ScreenDirection.DOWN);
				case 265 -> this.createArrowEvent(ScreenDirection.UP);
			});
			if (focusNavigationEvent != null) {
				ComponentPath componentPath = super.nextFocusPath(focusNavigationEvent);
				if (componentPath == null && focusNavigationEvent instanceof FocusNavigationEvent.TabNavigation) {
					this.clearFocus();
					componentPath = super.nextFocusPath(focusNavigationEvent);
				}

				if (componentPath != null) {
					this.changeFocus(componentPath);
				}
			}

			return false;
		}
	}

	private FocusNavigationEvent.TabNavigation createTabEvent(boolean bl) {
		return new FocusNavigationEvent.TabNavigation(bl);
	}

	private FocusNavigationEvent.ArrowNavigation createArrowEvent(ScreenDirection screenDirection) {
		return new FocusNavigationEvent.ArrowNavigation(screenDirection);
	}

	protected void setInitialFocus() {
		if (this.minecraft.getLastInputType().isKeyboard()) {
			FocusNavigationEvent.TabNavigation tabNavigation = new FocusNavigationEvent.TabNavigation(true);
			ComponentPath componentPath = super.nextFocusPath(tabNavigation);
			if (componentPath != null) {
				this.changeFocus(componentPath);
			}
		}
	}

	protected void setInitialFocus(GuiEventListener guiEventListener) {
		ComponentPath componentPath = ComponentPath.path(this, guiEventListener.nextFocusPath(new FocusNavigationEvent.InitialFocus()));
		if (componentPath != null) {
			this.changeFocus(componentPath);
		}
	}

	public void clearFocus() {
		ComponentPath componentPath = this.getCurrentFocusPath();
		if (componentPath != null) {
			componentPath.applyFocus(false);
		}
	}

	@VisibleForTesting
	protected void changeFocus(ComponentPath componentPath) {
		this.clearFocus();
		componentPath.applyFocus(true);
	}

	public boolean shouldCloseOnEsc() {
		return true;
	}

	public void onClose() {
		this.minecraft.setScreen(null);
	}

	protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T guiEventListener) {
		this.renderables.add(guiEventListener);
		return this.addWidget(guiEventListener);
	}

	protected <T extends Renderable> T addRenderableOnly(T renderable) {
		this.renderables.add(renderable);
		return renderable;
	}

	protected <T extends GuiEventListener & NarratableEntry> T addWidget(T guiEventListener) {
		this.children.add(guiEventListener);
		this.narratables.add(guiEventListener);
		return guiEventListener;
	}

	protected void removeWidget(GuiEventListener guiEventListener) {
		if (guiEventListener instanceof Renderable) {
			this.renderables.remove((Renderable)guiEventListener);
		}

		if (guiEventListener instanceof NarratableEntry) {
			this.narratables.remove((NarratableEntry)guiEventListener);
		}

		if (this.getFocused() == guiEventListener) {
			this.clearFocus();
		}

		this.children.remove(guiEventListener);
	}

	protected void clearWidgets() {
		this.renderables.clear();
		this.children.clear();
		this.narratables.clear();
	}

	public static List<Component> getTooltipFromItem(Minecraft minecraft, ItemStack itemStack) {
		return itemStack.getTooltipLines(
			TooltipContext.of(minecraft.level), minecraft.player, minecraft.options.advancedItemTooltips ? Default.ADVANCED : Default.NORMAL
		);
	}

	protected void insertText(String string, boolean bl) {
	}

	public boolean handleComponentClicked(Style style) {
		ClickEvent clickEvent = style.getClickEvent();
		if (this.minecraft.hasShiftDown()) {
			if (style.getInsertion() != null) {
				this.insertText(style.getInsertion(), false);
			}
		} else if (clickEvent != null) {
			this.handleClickEvent(this.minecraft, clickEvent);
			return true;
		}

		return false;
	}

	protected void handleClickEvent(Minecraft minecraft, ClickEvent clickEvent) {
		defaultHandleGameClickEvent(clickEvent, minecraft, this);
	}

	protected static void defaultHandleGameClickEvent(ClickEvent clickEvent, Minecraft minecraft, @Nullable Screen screen) {
		LocalPlayer localPlayer = (LocalPlayer)Objects.requireNonNull(minecraft.player, "Player not available");
		switch (clickEvent) {
			case RunCommand var6:
				RunCommand var10000 = var6;
				String var12;

				try {
					var12 = var10000.command();
				} catch (Throwable var10) {
					throw new MatchException(var10.toString(), var10);
				}

				String var11 = var12;
				clickCommandAction(localPlayer, var11, screen);
				break;
			case ShowDialog showDialog:
				localPlayer.connection.showDialog(showDialog.dialog(), screen);
				break;
			case Custom custom:
				localPlayer.connection.send(new ServerboundCustomClickActionPacket(custom.id(), custom.payload()));
				if (minecraft.screen != screen) {
					minecraft.setScreen(screen);
				}
				break;
			default:
				defaultHandleClickEvent(clickEvent, minecraft, screen);
		}
	}

	protected static void defaultHandleClickEvent(ClickEvent clickEvent, Minecraft minecraft, @Nullable Screen screen) {
		boolean bl = switch (clickEvent) {
			case OpenUrl var6 -> {
				OpenUrl var23 = var6;
				URI var24;

				try {
					var24 = var23.uri();
				} catch (Throwable var16) {
					throw new MatchException(var16.toString(), var16);
				}

				URI var17 = var24;
				clickUrlAction(minecraft, screen, var17);
				yield false;
			}
			case OpenFile openFile -> {
				Util.getPlatform().openFile(openFile.file());
				yield true;
			}
			case SuggestCommand var9 -> {
				SuggestCommand var21 = var9;
				String var22;

				try {
					var22 = var21.command();
				} catch (Throwable var15) {
					throw new MatchException(var15.toString(), var15);
				}

				String var18 = var22;
				if (screen != null) {
					screen.insertText(var18, true);
				}

				yield true;
			}
			case CopyToClipboard var11 -> {
				CopyToClipboard var19 = var11;
				String var20;

				try {
					var20 = var19.value();
				} catch (Throwable var14) {
					throw new MatchException(var14.toString(), var14);
				}

				String var13 = var20;
				minecraft.keyboardHandler.setClipboard(var13);
				yield true;
			}
			default -> {
				LOGGER.error("Don't know how to handle {}", clickEvent);
				yield true;
			}
		};
		if (bl && minecraft.screen != screen) {
			minecraft.setScreen(screen);
		}
	}

	protected static boolean clickUrlAction(Minecraft minecraft, @Nullable Screen screen, URI uRI) {
		if (!minecraft.options.chatLinks().get()) {
			return false;
		} else {
			if (minecraft.options.chatLinksPrompt().get()) {
				minecraft.setScreen(new ConfirmLinkScreen(bl -> {
					if (bl) {
						Util.getPlatform().openUri(uRI);
					}

					minecraft.setScreen(screen);
				}, uRI.toString(), false));
			} else {
				Util.getPlatform().openUri(uRI);
			}

			return true;
		}
	}

	protected static void clickCommandAction(LocalPlayer localPlayer, String string, @Nullable Screen screen) {
		localPlayer.connection.sendUnattendedCommand(Commands.trimOptionalPrefix(string), screen);
	}

	public final void init(Minecraft minecraft, int i, int j) {
		this.minecraft = minecraft;
		this.font = minecraft.font;
		this.width = i;
		this.height = j;
		if (!this.initialized) {
			this.init();
			this.setInitialFocus();
		} else {
			this.repositionElements();
		}

		this.initialized = true;
	}

	protected void rebuildWidgets() {
		this.clearWidgets();
		this.clearFocus();
		this.init();
		this.setInitialFocus();
	}

	protected void fadeWidgets(float f) {
		for (GuiEventListener guiEventListener : this.children()) {
			if (guiEventListener instanceof AbstractWidget abstractWidget) {
				abstractWidget.setAlpha(f);
			}
		}
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return this.children;
	}

	protected void init() {
	}

	public void tick() {
	}

	public void removed() {
	}

	public void added() {
	}

	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.isInGameUi()) {
			this.renderTransparentBackground(guiGraphics);
		} else {
			if (this.minecraft.level == null) {
				this.renderPanorama(guiGraphics, f);
			}

			this.renderBlurredBackground(guiGraphics);
			this.renderMenuBackground(guiGraphics);
		}

		this.minecraft.gui.renderDeferredSubtitles();
	}

	protected void renderBlurredBackground(GuiGraphics guiGraphics) {
		float f = this.minecraft.options.getMenuBackgroundBlurriness();
		if (f >= 1.0F) {
			guiGraphics.blurBeforeThisStratum();
		}
	}

	protected void renderPanorama(GuiGraphics guiGraphics, float f) {
		this.minecraft.gameRenderer.getPanorama().render(guiGraphics, this.width, this.height, this.panoramaShouldSpin());
	}

	protected void renderMenuBackground(GuiGraphics guiGraphics) {
		this.renderMenuBackground(guiGraphics, 0, 0, this.width, this.height);
	}

	protected void renderMenuBackground(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		renderMenuBackgroundTexture(guiGraphics, this.minecraft.level == null ? MENU_BACKGROUND : INWORLD_MENU_BACKGROUND, i, j, 0.0F, 0.0F, k, l);
	}

	public static void renderMenuBackgroundTexture(GuiGraphics guiGraphics, ResourceLocation resourceLocation, int i, int j, float f, float g, int k, int l) {
		int m = 32;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, resourceLocation, i, j, f, g, k, l, 32, 32);
	}

	public void renderTransparentBackground(GuiGraphics guiGraphics) {
		guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
	}

	public boolean isPauseScreen() {
		return true;
	}

	public boolean isInGameUi() {
		return false;
	}

	protected boolean panoramaShouldSpin() {
		return true;
	}

	public boolean isAllowedInPortal() {
		return this.isPauseScreen();
	}

	protected void repositionElements() {
		this.rebuildWidgets();
	}

	public void resize(Minecraft minecraft, int i, int j) {
		this.width = i;
		this.height = j;
		this.repositionElements();
	}

	public void fillCrashDetails(CrashReport crashReport) {
		CrashReportCategory crashReportCategory = crashReport.addCategory("Affected screen", 1);
		crashReportCategory.setDetail("Screen name", () -> this.getClass().getCanonicalName());
	}

	protected boolean isValidCharacterForName(String string, int i, int j) {
		int k = string.indexOf(58);
		int l = string.indexOf(47);
		if (i == 58) {
			return (l == -1 || j <= l) && k == -1;
		} else {
			return i == 47 ? j > k : i == 95 || i == 45 || i >= 97 && i <= 122 || i >= 48 && i <= 57 || i == 46;
		}
	}

	@Override
	public boolean isMouseOver(double d, double e) {
		return true;
	}

	public void onFilesDrop(List<Path> list) {
	}

	public void scheduleNarration(long l, boolean bl) {
		// Narration disabled
	}

	public void afterMouseMove() {
		// Narration disabled
	}

	public void afterMouseAction() {
		// Narration disabled
	}

	public void afterKeyboardAction() {
		// Narration disabled
	}

	public void handleDelayedNarration() {
		// Narration disabled
	}

	public void triggerImmediateNarration(boolean bl) {
		// Narration disabled
	}

	protected boolean shouldNarrateNavigation() {
		return false;
	}

	protected void updateNarrationState(NarrationElementOutput narrationElementOutput) {
		// Narration disabled
	}

	protected void updateNarratedWidget(NarrationElementOutput narrationElementOutput) {
		// Narration disabled
	}

	protected Component getUsageNarration() {
		return Component.empty();
	}

	@Nullable
	public static Screen.NarratableSearchResult findNarratableWidget(List<? extends NarratableEntry> list, @Nullable NarratableEntry narratableEntry) {
		return null;
	}

	public void updateNarratorStatus(boolean bl) {
		// Narration disabled
	}

	public Font getFont() {
		return this.font;
	}

	public boolean showsActiveEffects() {
		return false;
	}

	public boolean canInterruptWithAnotherScreen() {
		return this.shouldCloseOnEsc();
	}

	@Override
	public ScreenRectangle getRectangle() {
		return new ScreenRectangle(0, 0, this.width, this.height);
	}

	@Nullable
	public Music getBackgroundMusic() {
		return null;
	}

	@Environment(EnvType.CLIENT)
	public record NarratableSearchResult(NarratableEntry entry, int index, NarratableEntry.NarrationPriority priority) {
	}
}
