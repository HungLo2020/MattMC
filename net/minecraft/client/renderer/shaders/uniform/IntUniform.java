// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL32C;

import java.util.function.IntSupplier;

/**
 * A uniform that holds a single integer value.
 * 
 * COPIED VERBATIM from IRIS's IntUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/IntUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class IntUniform extends Uniform {
	private final IntSupplier value;
	private int cachedValue;

	IntUniform(int location, IntSupplier value) {
		this(location, value, null);
	}

	IntUniform(int location, IntSupplier value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = 0;
		this.value = value;
	}

	@Override
	public void update() {
		updateValue();

		if (notifier != null) {
			notifier.setListener(this::updateValue);
		}
	}

	private void updateValue() {
		int newValue = value.getAsInt();

		if (cachedValue != newValue) {
			cachedValue = newValue;
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform1i(location, newValue);
		}
	}
}
