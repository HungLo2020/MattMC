// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL32C;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * A uniform that holds a 4D vector value from a float array.
 * 
 * COPIED VERBATIM from IRIS's Vector4ArrayUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector4ArrayUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector4ArrayUniform extends Uniform {
	private final Supplier<float[]> value;
	private float[] cachedValue;

	Vector4ArrayUniform(int location, Supplier<float[]> value) {
		this(location, value, null);
	}

	Vector4ArrayUniform(int location, Supplier<float[]> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new float[4];
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
		float[] newValue = value.get();

		if (!Arrays.equals(newValue, cachedValue)) {
			cachedValue = newValue;
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform4f(location, cachedValue[0], cachedValue[1], cachedValue[2], cachedValue[3]);
		}
	}
}
