/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
