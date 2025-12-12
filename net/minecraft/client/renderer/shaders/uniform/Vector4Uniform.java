// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL32C;

import java.util.function.Supplier;

/**
 * A uniform that holds a 4D vector value.
 * 
 * COPIED VERBATIM from IRIS's Vector4Uniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector4Uniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector4Uniform extends Uniform {
	private final Vector4f cachedValue;
	private final Supplier<Vector4f> value;

	Vector4Uniform(int location, Supplier<Vector4f> value) {
		this(location, value, null);
	}

	Vector4Uniform(int location, Supplier<Vector4f> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector4f();
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
		Vector4f newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue.set(newValue.x(), newValue.y(), newValue.z(), newValue.w());
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform4f(location, cachedValue.x(), cachedValue.y(), cachedValue.z(), cachedValue.w());
		}
	}
}
