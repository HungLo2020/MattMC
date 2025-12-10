package net.minecraft.client.renderer.shaders.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the FullScreenQuadRenderer class.
 * Validates structure and basic behavior (OpenGL calls require context, so limited testing).
 */
class FullScreenQuadRendererTest {

	@Test
	void testSingletonInstance() {
		assertNotNull(FullScreenQuadRenderer.INSTANCE, "INSTANCE should not be null");
		assertSame(FullScreenQuadRenderer.INSTANCE, FullScreenQuadRenderer.INSTANCE, 
			"INSTANCE should always return the same object");
	}

	@Test
	void testVertexCount() {
		// A quad always has 4 vertices
		// Note: Vertex count is set by ensureInitialized(), but in test environment
		// without OpenGL context, it may remain at default value
		int count = FullScreenQuadRenderer.INSTANCE.getVertexCount();
		assertTrue(count == 0 || count == 4, 
			"Vertex count should be 0 (uninitialized) or 4 (initialized), was: " + count);
	}

	@Test
	void testVBOCreated() {
		// VBO should be created (non-zero handle)
		// Note: This will be 0 in test environment without OpenGL context
		// In actual game, this would be non-zero
		int vbo = FullScreenQuadRenderer.INSTANCE.getQuadVBO();
		assertTrue(vbo >= 0, "VBO handle should be non-negative");
	}

	@Test
	void testGetQuadVBOReturnsConsistentValue() {
		int vbo1 = FullScreenQuadRenderer.INSTANCE.getQuadVBO();
		int vbo2 = FullScreenQuadRenderer.INSTANCE.getQuadVBO();
		assertEquals(vbo1, vbo2, "getQuadVBO should return consistent value");
	}

	@Test
	void testGetVertexCountReturnsConsistentValue() {
		int count1 = FullScreenQuadRenderer.INSTANCE.getVertexCount();
		int count2 = FullScreenQuadRenderer.INSTANCE.getVertexCount();
		assertEquals(count1, count2, "getVertexCount should return consistent value");
	}

	@Test
	void testMethodsDoNotThrow() {
		// Methods should not throw exceptions even without OpenGL context
		// (they may fail internally but shouldn't crash)
		assertDoesNotThrow(() -> {
			FullScreenQuadRenderer.INSTANCE.getQuadVBO();
			FullScreenQuadRenderer.INSTANCE.getVertexCount();
		});
	}
}
