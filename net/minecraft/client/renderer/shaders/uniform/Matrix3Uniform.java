// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import net.minecraft.client.renderer.shaders.gl.IrisRenderSystem;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

/**
 * Uniform for 3x3 matrix values.
 * 
 * Based on IRIS's Matrix3Uniform class
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/Matrix3Uniform.java
 */
public class Matrix3Uniform extends Uniform {
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(9);
	private final Supplier<Matrix3fc> value;
	private final Matrix3f cachedValue;

	Matrix3Uniform(int location, Supplier<Matrix3fc> value) {
		super(location);

		this.cachedValue = new Matrix3f();
		this.value = value;
	}

	Matrix3Uniform(int location, Supplier<Matrix3fc> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Matrix3f();
		this.value = value;
	}

	@Override
	public void update() {
		updateValue();

		if (notifier != null) {
			notifier.setListener(this::updateValue);
		}
	}

	public void updateValue() {
		Matrix3fc newValue = value.get();

		if (!cachedValue.equals(newValue)) {
			cachedValue.set(newValue);

			cachedValue.get(buffer);
			buffer.rewind();

			IrisRenderSystem.uniformMatrix3fv(location, false, buffer);
		}
	}
}
