// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL32C;

import java.util.function.Supplier;

/**
 * A uniform that holds a 4D integer vector value.
 * 
 * COPIED VERBATIM from IRIS's Vector4IntegerJomlUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector4IntegerJomlUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector4IntegerJomlUniform extends Uniform {
	private final Supplier<Vector4i> value;
	private Vector4i cachedValue;

	Vector4IntegerJomlUniform(int location, Supplier<Vector4i> value) {
		this(location, value, null);
	}

	Vector4IntegerJomlUniform(int location, Supplier<Vector4i> value, ValueUpdateNotifier notifier) {
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
		Vector4i newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue = newValue;
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform4i(this.location, newValue.x, newValue.y, newValue.z, newValue.w);
		}
	}
	
	private void updateValue(Object ignored) {
		updateValue();
	}
}
