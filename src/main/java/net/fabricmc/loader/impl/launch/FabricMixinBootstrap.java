package net.fabricmc.loader.impl.launch;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

/**
 * Stubbed version of FabricMixinBootstrap - all mixins have been inlined into hook-based architecture.
 * This class is kept for API compatibility but performs no actual mixin initialization.
 */
public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() { }

	private static boolean initialized = false;

	public static void init(EnvType side, FabricLoaderImpl loader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		// Verify that no mixin configs are present
		for (ModContainerImpl mod : loader.getModsInternal()) {
			if (!mod.getMetadata().getMixinConfigs(side).isEmpty()) {
				Log.warn(LogCategory.MIXIN, "Mod %s declares mixin configs but mixin system is disabled (all mixins converted to hooks)", mod.getMetadata().getId());
			}
		}

		Log.info(LogCategory.MIXIN, "Mixin system bypassed - all mixins converted to hook-based architecture");
		initialized = true;
	}
}
