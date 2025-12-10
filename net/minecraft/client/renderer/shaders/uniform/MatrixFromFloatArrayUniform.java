// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL46C;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * A uniform that holds a 4x4 matrix value from a float array.
 * 
 * COPIED VERBATIM from IRIS's MatrixFromFloatArrayUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/MatrixFromFloatArrayUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class MatrixFromFloatArrayUniform extends Uniform {
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private final Supplier<float[]> value;
	private float[] cachedValue;

	MatrixFromFloatArrayUniform(int location, Supplier<float[]> value) {
		super(location);

		this.cachedValue = null;
		this.value = value;
	}

	@Override
	public void update() {
		float[] newValue = value.get();

		if (!Arrays.equals(newValue, cachedValue)) {
			cachedValue = Arrays.copyOf(newValue, 16);

			buffer.put(cachedValue);
			buffer.rewind();

			GL46C.glUniformMatrix4fv(location, false, buffer);
		}
	}
}
