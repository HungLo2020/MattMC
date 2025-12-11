package net.minecraft.client.renderer.shaders.api.v0;

import net.minecraft.client.renderer.shaders.Iris;
import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API class for IRIS shader functionality.
 * Provides external access to shader configuration and state.
 * 
 * This implementation connects the Iris GUI to the ShaderSystem
 * for actual shader pack loading and rendering.
 */
public class IrisApi {
	private static final Logger LOGGER = LoggerFactory.getLogger(IrisApi.class);
	private static final IrisApi INSTANCE = new IrisApi();
	private static final Config CONFIG = new Config();
	
	public static IrisApi getInstance() {
		return INSTANCE;
	}
	
	public boolean isShaderPackInUse() {
		ShaderSystem system = ShaderSystem.getInstance();
		return system.isInitialized() && 
			   system.getConfig() != null && 
			   system.getConfig().areShadersEnabled() &&
			   system.getConfig().getSelectedPack() != null;
	}
	
	public String getCurrentPackName() {
		return Iris.getCurrentPackName();
	}

	public Config getConfig() {
		return CONFIG;
	}

	/**
	 * Config class that bridges Iris GUI settings to ShaderSystem.
	 */
	public static class Config {
		public void setShadersEnabledAndApply(boolean enabled) {
			LOGGER.info("setShadersEnabledAndApply called with enabled={}", enabled);
			
			// Update the Iris config first (for GUI state)
			Iris.getIrisConfig().setShadersEnabled(enabled);
			
			// Then sync to ShaderSystem for actual rendering
			ShaderSystem system = ShaderSystem.getInstance();
			if (system.isInitialized() && system.getConfig() != null) {
				// Get the selected pack from Iris config
				String packName = Iris.getIrisConfig().getShaderPackName().orElse(null);
				
				// Sync to ShaderSystem config
				system.getConfig().setShadersEnabled(enabled);
				if (packName != null) {
					system.getConfig().setSelectedPack(packName);
				}
				
				LOGGER.info("Synced shader config to ShaderSystem - enabled={}, pack={}", enabled, packName);
				
				// Trigger pipeline reload
				if (system.getPipelineManager() != null) {
					LOGGER.info("Reloading shader pipelines");
					system.getPipelineManager().reloadPipelines();
				}
			} else {
				LOGGER.warn("ShaderSystem not initialized, cannot apply shader changes");
			}
			
			// Save Iris config
			try {
				Iris.getIrisConfig().save();
			} catch (java.io.IOException e) {
				LOGGER.error("Failed to save Iris config", e);
			}
		}
		
		public boolean areShadersEnabled() {
			return Iris.getIrisConfig().areShadersEnabled();
		}
	}
}
