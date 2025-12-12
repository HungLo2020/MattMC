// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL32C;

import java.util.function.Supplier;

/**
 * A uniform that holds a 3D integer vector value.
 * 
 * COPIED VERBATIM from IRIS's Vector3IntegerUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Vector3IntegerUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class Vector3IntegerUniform extends Uniform {
	private final Vector3i cachedValue;
	private final Supplier<Vector3i> value;

	Vector3IntegerUniform(int location, Supplier<Vector3i> value) {
		super(location);

		this.cachedValue = new Vector3i();
		this.value = value;
	}

	Vector3IntegerUniform(int location, Supplier<Vector3i> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector3i();
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
		Vector3i newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue.set(newValue.x(), newValue.y(), newValue.z());
			RenderSystem.assertOnRenderThread();
			GL32C.glUniform3i(location, cachedValue.x(), cachedValue.y(), cachedValue.z());
		}
	}
}
