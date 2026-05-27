package net.minecraft.client.gui.screens;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class ToolsScreen extends Screen {
	private static final Component TITLE = Component.literal("Tools");
	private final Screen lastScreen;

	public ToolsScreen(Screen lastScreen) {
		super(TITLE);
		this.lastScreen = lastScreen;
	}

	@Override
	protected void init() {
		int rowY = this.height / 4 + 48;

		Button regionEditorButton = this.addRenderableWidget(
			Button.builder(Component.literal("Region Editor"), button -> {
				// Placeholder: Region Editor implementation will be added later.
			})
				.bounds(this.width / 2 - 100, rowY, 200, 20)
				.build()
		);
		regionEditorButton.active = false;
		regionEditorButton.setTooltip(Tooltip.create(Component.literal("Coming soon")));

		rowY += 36;
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen))
				.bounds(this.width / 2 - 100, rowY, 200, 20)
				.build()
		);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
	}
}
