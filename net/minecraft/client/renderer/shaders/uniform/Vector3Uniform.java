// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL32C;

import java.util.function.Supplier;

/**
 * A uniform that holds a 3D vector value.
 * 
 * COPIED VERBATIM from IRIS's Vector3Uniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector3Uniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector3Uniform extends Uniform {
	private final Vector3f cachedValue;
	private final Supplier<Vector3f> value;

	Vector3Uniform(int location, Supplier<Vector3f> value) {
		super(location);

		this.cachedValue = new Vector3f();
		this.value = value;
	}

	Vector3Uniform(int location, Supplier<Vector3f> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector3f();
		this.value = value;
	}

	static Vector3Uniform converted(int location, Supplier<Vector3d> value) {
		Vector3f held = new Vector3f();

		return new Vector3Uniform(location, () -> {
			Vector3d updated = value.get();

			held.set((float) updated.x, (float) updated.y, (float) updated.z);

			return held;
		});
	}

	static Vector3Uniform truncated(int location, Supplier<Vector4f> value) {
		Vector3f held = new Vector3f();

		return new Vector3Uniform(location, () -> {
			Vector4f updated = value.get();

			held.set(updated.x(), updated.y(), updated.z());

			return held;
		});
	}

	@Override
	public void update() {
		updateValue();

		if (notifier != null) {
			notifier.setListener(this::updateValue);
		}
	}

	private void updateValue() {
		Vector3f newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue.set(newValue.x(), newValue.y(), newValue.z());
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform3f(location, cachedValue.x(), cachedValue.y(), cachedValue.z());
		}
	}
}
