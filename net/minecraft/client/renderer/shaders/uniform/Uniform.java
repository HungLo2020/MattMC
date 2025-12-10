// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

/**
 * Base class for all shader uniforms.
 * 
 * COPIED VERBATIM from IRIS's Uniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Uniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public abstract class Uniform {
	protected final int location;
	protected final ValueUpdateNotifier notifier;

	Uniform(int location) {
		this(location, null);
	}

	Uniform(int location, ValueUpdateNotifier notifier) {
		this.location = location;
		this.notifier = notifier;
	}

	public abstract void update();

	public final int getLocation() {
		return location;
	}

	public final ValueUpdateNotifier getNotifier() {
		return notifier;
	}
}
