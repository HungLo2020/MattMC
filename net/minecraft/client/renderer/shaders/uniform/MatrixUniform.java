// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL46C;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

/**
 * A uniform that holds a 4x4 matrix value.
 * 
 * COPIED VERBATIM from IRIS's MatrixUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/MatrixUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class MatrixUniform extends Uniform {
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private final Supplier<Matrix4fc> value;
	private final Matrix4f cachedValue;

	MatrixUniform(int location, Supplier<Matrix4fc> value) {
		super(location);

		this.cachedValue = new Matrix4f();
		this.value = value;
	}

	MatrixUniform(int location, Supplier<Matrix4fc> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Matrix4f();
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
		Matrix4fc newValue = value.get();

		if (!cachedValue.equals(newValue)) {
			cachedValue.set(newValue);

			cachedValue.get(buffer);
			buffer.rewind();

			GL46C.glUniformMatrix4fv(location, false, buffer);
		}
	}
	
	private void updateValue(Object ignored) {
		updateValue();
	}
}
