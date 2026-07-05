package net.irisshaders.iris.uniforms;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class MatrixSafety {
	private MatrixSafety() {
	}

	public static boolean isFinite(Matrix4fc matrix) {
		return Float.isFinite(matrix.m00())
			&& Float.isFinite(matrix.m01())
			&& Float.isFinite(matrix.m02())
			&& Float.isFinite(matrix.m03())
			&& Float.isFinite(matrix.m10())
			&& Float.isFinite(matrix.m11())
			&& Float.isFinite(matrix.m12())
			&& Float.isFinite(matrix.m13())
			&& Float.isFinite(matrix.m20())
			&& Float.isFinite(matrix.m21())
			&& Float.isFinite(matrix.m22())
			&& Float.isFinite(matrix.m23())
			&& Float.isFinite(matrix.m30())
			&& Float.isFinite(matrix.m31())
			&& Float.isFinite(matrix.m32())
			&& Float.isFinite(matrix.m33());
	}

	public static Matrix4f invertOrIdentity(Matrix4fc matrix, Matrix4f destination) {
		matrix.invert(destination);
		if (!isFinite(destination)) {
			destination.identity();
		}
		return destination;
	}
}
