// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import java.util.function.Consumer;

/**
 * Interface for notifying when a value changes that requires uniform updates.
 * 
 * Based on IRIS's ValueUpdateNotifier interface
 * Reference: frnsrc/Iris-1.21.9/.../gl/state/ValueUpdateNotifier.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public interface ValueUpdateNotifier {
	void setListener(Consumer<Object> listener);
}
