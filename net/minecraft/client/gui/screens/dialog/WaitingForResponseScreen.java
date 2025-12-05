package net.minecraft.client.gui.screens.dialog;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WaitingForResponseScreen extends Screen {
	private static final Component TITLE = Component.translatable("gui.waitingForResponse.title");
	private static final Component[] BUTTON_LABELS = new Component[]{
		Component.empty(),
		Component.translatable("gui.waitingForResponse.button.inactive", new Object[]{4}),
		Component.translatable("gui.waitingForResponse.button.inactive", new Object[]{3}),
		Component.translatable("gui.waitingForResponse.button.inactive", new Object[]{2}),
		Component.translatable("gui.waitingForResponse.button.inactive", new Object[]{1}),
		CommonComponents.GUI_BACK
	};
	private static final int BUTTON_VISIBLE_AFTER = 1;
	private static final int BUTTON_ACTIVE_AFTER = 5;
	@Nullable
	private final Screen previousScreen;
	private final HeaderAndFooterLayout layout;
	private final Button closeButton;
	private int ticks;

	public WaitingForResponseScreen(@Nullable Screen screen) {
		super(TITLE);
		this.previousScreen = screen;
		this.layout = new HeaderAndFooterLayout(this, 33, 0);
		this.closeButton = Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(200).build();
	}

	@Override
	protected void init() {
		super.init();
		this.layout.addTitleHeader(TITLE, this.font);
		this.layout.addToContents(this.closeButton);
		this.closeButton.visible = false;
		this.closeButton.active = false;
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.closeButton.active) {
			int i = this.ticks++ / 20;
			this.closeButton.visible = i >= 1;
			this.closeButton.setMessage(BUTTON_LABELS[i]);
			if (i == 5) {
				this.closeButton.active = true;
				this.triggerImmediateNarration(true);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return this.closeButton.active;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.previousScreen);
	}

	@Nullable
	public Screen previousScreen() {
		return this.previousScreen;
	}
}
