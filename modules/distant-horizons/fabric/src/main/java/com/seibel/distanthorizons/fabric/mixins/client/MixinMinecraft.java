package com.seibel.distanthorizons.fabric.mixins.client;

import net.distant_horizons.api.enums.config.EDhApiUpdateBranch;
import net.distant_horizons.common.wrappers.gui.updater.UpdateModScreen;
import net.distant_horizons.common.wrappers.world.ClientLevelWrapper;
import net.distant_horizons.core.api.internal.ClientApi;
import net.distant_horizons.core.config.Config;
import net.distant_horizons.core.dependencyInjection.SingletonInjector;
import net.distant_horizons.core.jar.installer.GitlabGetter;
import net.distant_horizons.core.jar.installer.ModrinthGetter;
import net.distant_horizons.core.jar.updater.SelfUpdater;
import net.distant_horizons.core.logging.DhLoggerBuilder;
import net.distant_horizons.core.wrapperInterfaces.IVersionConstants;
import net.distant_horizons.coreapi.ModInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.distant_horizons.core.logging.DhLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * At the moment this is only used for the auto updater
 *
 * @author coolGi
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft
{
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	@Shadow
	public abstract boolean isLocalServer();
	
	@Unique
	private ClientLevel lastLevel;
	
	/** 
	 * Can be enabled for testing the auto updater UI. <br/>
	 * will always show the auto updater if set to true. 
	 */
	@Unique
	private static final boolean DEBUG_ALWAYS_SHOW_UPDATER = false;

	
	
	
	@Redirect(
			method = "Lnet/minecraft/client/Minecraft;onGameLoadFinished(Lnet/minecraft/client/Minecraft$GameLoadCookie;)V",
			at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V")
	)
	private void buildInitialScreens(Runnable runnable)
	{
		boolean showUpdater = SelfUpdater.onStart(); // always needs to be called, otherwise auto update setup won't be completed
		
		// TODO merge logic for forge, neo, and fabric
		if (
				(
					// Don't do anything if the user doesn't want it
					showUpdater
					&& Config.Client.Advanced.AutoUpdater.enableAutoUpdater.get()
				)
				|| DEBUG_ALWAYS_SHOW_UPDATER
			)
		{
			runnable = () -> 
			{
				String versionId;
				EDhApiUpdateBranch updateBranch = EDhApiUpdateBranch.convertAutoToStableOrNightly(Config.Client.Advanced.AutoUpdater.updateBranch.get());
				if (updateBranch == EDhApiUpdateBranch.STABLE)
				{
					versionId = ModrinthGetter.getLatestIDForVersion(SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion());
				}
				else
				{
					versionId = GitlabGetter.INSTANCE.projectPipelines.get(0).get("sha");
				}
				
				if (versionId != null)
				{
					try
					{
						Minecraft.getInstance().setScreen(new UpdateModScreen(
								// TODO: Change to runnable, instead of tittle screen
								new TitleScreen(false), // We don't want to use the vanilla title screen as it would fade the buttons
								versionId
						));
					}
					catch (Exception e)
					{
						// info instead of error since this can be ignored and probably just means
						// there isn't a new DH version available
						LOGGER.info("Unable to show DH update screen, reason: ["+e.getMessage()+"].");
					}
				}
				else
				{
					LOGGER.info("Unable to find new DH update for the ["+updateBranch+"] branch. Assuming DH is up to date...");
				}
			};
		}
		
		runnable.run();
	}
	
	@Inject(at = @At("HEAD"), method = "updateLevelInEngines")
	public void updateLevelInEngines(ClientLevel level, CallbackInfo ci)
	{
		if (this.lastLevel != null && level != this.lastLevel)
		{
			ClientApi.INSTANCE.clientLevelUnloadEvent(ClientLevelWrapper.getWrapper(this.lastLevel));
		}
		
		if (level != null)
		{
			ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(level, true));
		}
		
		this.lastLevel = level;
	}
	
	@Inject(at = @At("HEAD"), method = "close()V")
	public void close(CallbackInfo ci) { SelfUpdater.onClose(); }
	
}
