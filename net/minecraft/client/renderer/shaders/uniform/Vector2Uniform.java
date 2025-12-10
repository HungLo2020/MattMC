// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL32C;

import java.util.function.Supplier;

/**
 * A uniform that holds a 2D vector value.
 * 
 * COPIED VERBATIM from IRIS's Vector2Uniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector2Uniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector2Uniform extends Uniform {
	private final Supplier<Vector2f> value;
	private Vector2f cachedValue;

	Vector2Uniform(int location, Supplier<Vector2f> value) {
		super(location);

		this.cachedValue = null;
		this.value = value;
	}

	Vector2Uniform(int location, Supplier<Vector2f> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = null;
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
		Vector2f newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue = newValue;
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform2f(this.location, newValue.x, newValue.y);
		}
	}
	
	private void updateValue(Object ignored) {
		updateValue();
	}
}
