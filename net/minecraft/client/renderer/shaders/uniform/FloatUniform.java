// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL32C;

/**
 * A uniform that holds a single float value.
 * 
 * COPIED VERBATIM from IRIS's FloatUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/FloatUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class FloatUniform extends Uniform {
	private final FloatSupplier value;
	private float cachedValue;

	FloatUniform(int location, FloatSupplier value) {
		this(location, value, null);
	}

	FloatUniform(int location, FloatSupplier value, ValueUpdateNotifier notifier) {
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
		float newValue = value.getAsFloat();

		if (cachedValue != newValue) {
			cachedValue = newValue;
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform1f(location, newValue);
		}
	}
	
	private void updateValue(Object ignored) {
		updateValue();
	}
}
