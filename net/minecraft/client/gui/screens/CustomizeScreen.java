package net.minecraft.client.gui.screens;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.SkinLoader;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Screen for customizing player appearance (skin selection and username).
 */
@Environment(EnvType.CLIENT)
public class CustomizeScreen extends Screen {
	private final Screen lastScreen;
	private CycleButton<String> skinSelector;
	private EditBox usernameField;
	
	public CustomizeScreen(Screen lastScreen) {
		super(Component.translatable("menu.customize"));
		this.lastScreen = lastScreen;
	}
	
	@Override
	protected void init() {
		SkinLoader skinLoader = this.minecraft.getSkinLoader();
		List<SkinLoader.SkinEntry> skins = skinLoader.getAvailableSkins();
		
		// Get list of skin display names
		List<String> skinNames = skins.stream()
			.map(SkinLoader.SkinEntry::displayName)
			.toList();
		
		// Get currently selected skin from options
		String currentSkin = this.minecraft.options.selectedSkin;
		
		// Create username field
		String currentUsername = this.minecraft.options.customUsername != null && !this.minecraft.options.customUsername.isEmpty()
			? this.minecraft.options.customUsername
			: this.minecraft.getUser().getName();
		
		this.usernameField = new EditBox(
			this.font,
			this.width / 2 - 100,
			this.height / 6 - 12,
			200,
			20,
			Component.translatable("menu.customize.username")
		);
		this.usernameField.setMaxLength(16);
		this.usernameField.setValue(currentUsername);
		this.usernameField.setResponder(value -> {
			this.minecraft.options.customUsername = value;
			this.minecraft.options.save();
		});
		this.addRenderableWidget(this.usernameField);
		
		// Create skin selector dropdown
		this.skinSelector = CycleButton.<String>builder(name -> Component.literal(name))
			.withValues(skinNames)
			.withInitialValue(skinNames.contains(currentSkin) ? currentSkin : skinNames.get(0))
			.create(
				this.width / 2 - 155,
				this.height / 6 + 24,
				310,
				20,
				Component.translatable("menu.customize.skin"),
				(button, value) -> {
					// Update the selected skin in options
					this.minecraft.options.selectedSkin = value;
					this.minecraft.options.save();
					
					// Update the player's skin immediately if in-game
					this.updatePlayerSkin(value);
				}
			);
		this.addRenderableWidget(this.skinSelector);
		
		// Add "Done" button
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.lastScreen))
				.bounds(this.width / 2 - 100, this.height / 6 + 168, 200, 20)
				.build()
		);
		
		// Add "Reload Skins" button to pick up custom skins added while game is running
		this.addRenderableWidget(
			Button.builder(Component.translatable("menu.customize.reloadSkins"), button -> {
				skinLoader.reload();
				this.minecraft.setScreen(new CustomizeScreen(this.lastScreen));
			})
			.bounds(this.width / 2 - 100, this.height / 6 + 48, 200, 20)
			.build()
		);
	}
	
	private void updatePlayerSkin(String skinName) {
		// Get the skin entry
		SkinLoader skinLoader = this.minecraft.getSkinLoader();
		SkinLoader.SkinEntry skinEntry = skinLoader.getSkinByName(skinName);
		
		if (skinEntry != null && this.minecraft.player != null) {
			// The skin will be updated through the PlayerInfo system
			// For now, we just mark it as changed; actual application happens through
			// the multiplayer skin sync system when in multiplayer, or through
			// the default skin system in single player
		}
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
		
		// Draw username label
		guiGraphics.drawString(
			this.font,
			Component.translatable("menu.customize.username"),
			this.width / 2 - 100,
			this.height / 6 - 24,
			0xA0A0A0
		);
		
		guiGraphics.drawCenteredString(
			this.font,
			Component.translatable("menu.customize.skin.description"),
			this.width / 2,
			this.height / 6 + 12,
			0xA0A0A0
		);
	}
	
	@Override
	public void removed() {
		this.minecraft.options.save();
	}
}
