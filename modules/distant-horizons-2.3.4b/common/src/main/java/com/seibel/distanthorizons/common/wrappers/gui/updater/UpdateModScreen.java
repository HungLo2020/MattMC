package com.seibel.distanthorizons.common.wrappers.gui.updater;

import com.seibel.distanthorizons.api.enums.config.EDhApiUpdateBranch;
import com.seibel.distanthorizons.common.wrappers.gui.DhScreen;
import com.seibel.distanthorizons.common.wrappers.gui.TexturedButtonWidget;
import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.jar.installer.ModrinthGetter;
import com.seibel.distanthorizons.core.jar.updater.SelfUpdater;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;

import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.*;

import java.util.*;

/**
 * The screen that pops up if the mod has an update.
 *
 * @author coolGi
 */
// TODO: After finishing the config, rewrite this in Java Swing as well
// and also maybe add this suggestion https://discord.com/channels/881614130614767666/1035863487110467625/1035949054485594192
public class UpdateModScreen extends DhScreen
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	private Screen parent;
	private String newVersionID;
	
	private String currentVer;
	private String nextVer;
	
	
	public UpdateModScreen(Screen parent, String newVersionID) throws IllegalArgumentException
	{
		super(Translatable(ModInfo.ID + ".updater.title"));
		this.parent = parent;
		this.newVersionID = newVersionID;
		
		
		EDhApiUpdateBranch updateBranch = EDhApiUpdateBranch.convertAutoToStableOrNightly(Config.Client.Advanced.AutoUpdater.updateBranch.get());
		if (updateBranch == EDhApiUpdateBranch.STABLE)
        {
	        this.currentVer = ModInfo.VERSION;
	        this.nextVer = ModrinthGetter.releaseNames.get(this.newVersionID);
        }
	    else
        {
	        this.currentVer = ModJarInfo.Git_Commit.substring(0,7);
	        this.nextVer = this.newVersionID.substring(0,7);
        }
		
		// done to prevent trying to update to "null"
		// (this can happen if no versions are available to check/download from modrinth/gitlab)
		if (this.nextVer == null)
		{
			throw new IllegalArgumentException("No new version found with the ID ["+newVersionID+"].");
		}
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		
		try
		{
			
			
			// Logo image
			this.addBtn(new TexturedButtonWidget(
					// Where the button is on the screen
					this.width / 2 - 95, this.height / 2 - 110,
					// Width and height of the button
					195, 65,
					// Offset
					0, 0,
					// Some textuary stuff
					0, 
					ResourceLocation.fromNamespaceAndPath(ModInfo.ID, "logo.png"),
					195, 65,
					// Create the button and tell it where to go
					// For now it goes to the client option by default
					(buttonWidget) -> System.out.println("Nice, you found an easter egg :)"), // TODO: Add a proper easter egg to pressing the logo (maybe with confetti)
					// Add a title to the button
					Translatable(ModInfo.ID + ".updater.title"),
					// Dont render the background of the button
					false
			));
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to setup update mod screen, error: ["+e.getMessage()+"].", e);
		}
		
		if (!ModInfo.IS_DEV_BUILD)
		{
			this.addBtn(new TexturedButtonWidget(
					// Where the button is on the screen
					this.width / 2 - 97, this.height / 2 + 8,
					// Width and height of the button
					20, 20,
					// Offset
					0, 0,
					// Some textuary stuff
					0, 
					ResourceLocation.fromNamespaceAndPath(ModInfo.ID, "textures/gui/changelog.png"),
					20, 20,
					// Create the button and tell it where to go
					(buttonWidget) -> Objects.requireNonNull(minecraft).setScreen(new ChangelogScreen(this, this.newVersionID)), // TODO: Add a proper easter egg to pressing the logo (maybe with confetti)
					// Add a title to the button
					Translatable(ModInfo.ID + ".updater.title")
			));
		}
		
		
		this.addBtn( // Update
				MakeBtn(Translatable(ModInfo.ID + ".updater.update"), this.width / 2 - 75, this.height / 2 + 8, 150, 20, (btn) -> {
					SelfUpdater.updateMod();
					this.onClose();
				})
		);
		this.addBtn( // Silent update
				MakeBtn(Translatable(ModInfo.ID + ".updater.silent"), this.width / 2 - 75, this.height / 2 + 30, 150, 20, (btn) -> {
					Config.Client.Advanced.AutoUpdater.enableSilentUpdates.set(true);
					SelfUpdater.updateMod();
					this.onClose();
				})
		);
		this.addBtn( // Later (not now)
				MakeBtn(Translatable(ModInfo.ID + ".updater.later"), this.width / 2 + 2, this.height / 2 + 70, 100, 20, (btn) -> {
					this.onClose();
				})
		);
		this.addBtn( // Never
				MakeBtn(Translatable(ModInfo.ID + ".updater.never"), this.width / 2 - 102, this.height / 2 + 70, 100, 20, (btn) -> {
					Config.Client.Advanced.AutoUpdater.enableAutoUpdater.set(false);
					this.onClose();
				})
		);
		
	}
	
	@Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta)
	{
				// background blur is already being rendered, rendering again causes the game to crash
				
		// TODO: add the tooltips for the buttons
		super.render(matrices, mouseX, mouseY, delta); // Render the buttons
		// TODO: Add tooltips
		
		// Render the text's
		this.DhDrawCenteredString(matrices, this.font, 
				Translatable(ModInfo.ID + ".updater.text1"), 
				this.width / 2, this.height / 2 - 35,
				0xFFFFFFFF // ARGB
		);
		this.DhDrawCenteredString(matrices, this.font, 
				Translatable(ModInfo.ID + ".updater.text2", this.currentVer, this.nextVer), 
				this.width / 2, this.height / 2 - 20, 
				0xFF52FD52 // ARGB
		);
	}
	
	@Override
	public void onClose()
	{
		Objects.requireNonNull(this.minecraft).setScreen(this.parent); // Go to the parent screen
	}
	
}