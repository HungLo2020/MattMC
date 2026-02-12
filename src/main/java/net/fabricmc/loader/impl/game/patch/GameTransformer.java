package net.fabricmc.loader.impl.game.patch;

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.impl.launch.FabricLauncher;

/**
 * Simplified game transformer for integrated mod approach.
 * No patching needed - all code is compiled together.
 */
public class GameTransformer {
	public GameTransformer() {
		// No patches needed for integrated mod approach
	}

	public void locateEntrypoints(FabricLauncher launcher, List<Path> gameJars) {
		// No entrypoint patching needed for integrated mod approach
	}

	public byte[] transform(String className) {
		// No transformation needed
		return null;
	}
}
