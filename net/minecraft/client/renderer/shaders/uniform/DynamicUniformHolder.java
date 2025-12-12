// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Interface for uniform holders that support dynamic (per-frame) uniform updates with notifiers.
 * 
 * Based on IRIS's DynamicUniformHolder interface
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/DynamicUniformHolder.java
 */
public interface DynamicUniformHolder extends UniformHolder {
	DynamicUniformHolder uniform1f(String name, FloatSupplier value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform1f(String name, IntSupplier value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform1f(String name, DoubleSupplier value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform1i(String name, IntSupplier value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform2f(String name, Supplier<Vector2f> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform2i(String name, Supplier<Vector2i> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform3f(String name, Supplier<Vector3f> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform4f(String name, Supplier<Vector4f> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform4fArray(String name, Supplier<float[]> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniform4i(String name, Supplier<Vector4i> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniformMatrix(String name, Supplier<Matrix4fc> value, ValueUpdateNotifier notifier);

	DynamicUniformHolder uniformMatrix3(String name, Supplier<Matrix3fc> value, ValueUpdateNotifier notifier);
}
