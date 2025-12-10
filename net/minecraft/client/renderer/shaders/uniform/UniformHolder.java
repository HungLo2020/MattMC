// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Interface for holding and managing uniforms.
 * 
 * COPIED VERBATIM from IRIS's UniformHolder.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/UniformHolder.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public interface UniformHolder {
	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value);

	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, IntSupplier value);

	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value);

	UniformHolder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value);

	UniformHolder uniform1b(UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value);

	UniformHolder uniform2f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value);

	UniformHolder uniform2i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2i> value);

	UniformHolder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3f> value);

	UniformHolder uniform3i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3i> value);

	UniformHolder uniformTruncated3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value);

	UniformHolder uniform3d(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value);

	UniformHolder uniform4f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4f> value);

	UniformHolder uniform4fArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value);

	UniformHolder uniformMatrix(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4fc> value);

	UniformHolder uniformMatrixFromArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value);

	UniformHolder externallyManagedUniform(String name, UniformType type);
}
