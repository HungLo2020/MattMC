package net.irisshaders.iris.uniforms;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixSafetyTest {
	@Test
	void singularMatrixInverseFallsBackToFiniteIdentity() {
		Matrix4f inverse = MatrixSafety.invertOrIdentity(new Matrix4f().zero(), new Matrix4f());

		assertTrue(MatrixSafety.isFinite(inverse));
		assertTrue(new Matrix4f().identity().equals(inverse));
	}

	@Test
	void finiteInvertibleMatrixUsesActualInverse() {
		Matrix4f matrix = new Matrix4f().translation(2.0f, 4.0f, 8.0f);
		Matrix4f expected = matrix.invert(new Matrix4f());
		Matrix4f actual = MatrixSafety.invertOrIdentity(matrix, new Matrix4f());

		assertTrue(MatrixSafety.isFinite(actual));
		assertTrue(expected.equals(actual));
		assertFalse(new Matrix4f().identity().equals(actual));
	}
}
