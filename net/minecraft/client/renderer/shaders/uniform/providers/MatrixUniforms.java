// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Supplier;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Implements uniforms relating to transformation matrices.
 * 
 * Based on IRIS's MatrixUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/MatrixUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (~18 uniforms):
 * - gbufferModelView, gbufferProjection: Main rendering matrices
 * - gbufferModelViewInverse, gbufferProjectionInverse: Inverse matrices
 * - gbufferPreviousModelView, gbufferPreviousProjection: Previous frame matrices
 * - shadowModelView, shadowProjection: Shadow rendering matrices
 * - shadowModelViewInverse, shadowProjectionInverse: Inverse shadow matrices
 * 
 * Note: Simplified version - full IRIS version includes DH (Distant Horizons) matrices
 */
public final class MatrixUniforms {
	private MatrixUniforms() {
	}

	public static void addMatrixUniforms(UniformHolder uniforms) {
		// Add gbuffer matrices (main rendering)
		addMatrix(uniforms, "ModelView", () -> CapturedRenderingState.INSTANCE.getGbufferModelView());
		addMatrix(uniforms, "Projection", () -> CapturedRenderingState.INSTANCE.getGbufferProjection());
		
		// Shadow matrices would be added here if shadow rendering is implemented
		// addShadowMatrix(uniforms, "ModelView", shadowModelViewSupplier);
		// addShadowMatrix(uniforms, "Projection", shadowProjectionSupplier);
	}

	private static void addMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
		uniforms
			.uniformMatrix(PER_FRAME, "gbuffer" + name, supplier)
			.uniformMatrix(PER_FRAME, "gbuffer" + name + "Inverse", new Inverted(supplier))
			.uniformMatrix(PER_FRAME, "gbufferPrevious" + name, new Previous(supplier));
	}

	private static void addShadowMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
		uniforms
			.uniformMatrix(PER_FRAME, "shadow" + name, supplier)
			.uniformMatrix(PER_FRAME, "shadow" + name + "Inverse", new Inverted(supplier));
	}

	private record Inverted(Supplier<Matrix4fc> parent) implements Supplier<Matrix4fc> {
		@Override
		public Matrix4fc get() {
			Matrix4fc parentMatrix = parent.get();
			if (parentMatrix == null) {
				return new Matrix4f(); // Return identity matrix if parent is null
			}
			
			// PERF: Don't copy + allocate this matrix every time?
			Matrix4f copy = new Matrix4f(parentMatrix);
			copy.invert();
			return copy;
		}
	}

	private static class Previous implements Supplier<Matrix4fc> {
		private final Supplier<Matrix4fc> parent;
		private Matrix4f previous;

		Previous(Supplier<Matrix4fc> parent) {
			this.parent = parent;
			this.previous = new Matrix4f();
		}

		@Override
		public Matrix4fc get() {
			Matrix4fc parentMatrix = parent.get();
			if (parentMatrix == null) {
				return previous; // Return last known value if parent is null
			}
			
			// PERF: Don't copy + allocate these matrices every time?
			Matrix4f copy = new Matrix4f(parentMatrix);
			Matrix4f previousCopy = new Matrix4f(this.previous);

			this.previous = copy;

			return previousCopy;
		}
	}
}
