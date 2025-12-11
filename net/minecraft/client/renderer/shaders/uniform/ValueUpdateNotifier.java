// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

/**
 * Interface for notifying when a value changes that requires uniform updates.
 * 
 * Based on IRIS's ValueUpdateNotifier interface
 * Reference: frnsrc/Iris-1.21.9/.../gl/state/ValueUpdateNotifier.java
 */
public interface ValueUpdateNotifier {
	/**
	 * Sets up a listener with this notifier.
	 */
	void setListener(Runnable listener);
}
