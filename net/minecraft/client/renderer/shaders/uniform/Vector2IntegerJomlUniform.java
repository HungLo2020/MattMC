// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL32C;

import java.util.function.Supplier;

/**
 * A uniform that holds a 2D integer vector value.
 * 
 * COPIED VERBATIM from IRIS's Vector2IntegerJomlUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector2IntegerJomlUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector2IntegerJomlUniform extends Uniform {
	private final Supplier<Vector2i> value;
	private Vector2i cachedValue;

	Vector2IntegerJomlUniform(int location, Supplier<Vector2i> value) {
		this(location, value, null);
	}

	Vector2IntegerJomlUniform(int location, Supplier<Vector2i> value, ValueUpdateNotifier notifier) {
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
		Vector2i newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue = newValue;
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform2i(this.location, newValue.x, newValue.y);
		}
	}
	
	private void updateValue(Object ignored) {
		updateValue();
	}
}
