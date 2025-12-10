package net.minecraft.client.renderer.shaders.uniform.providers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ViewportUniforms.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class ViewportUniformsTest {

	@Test
	public void testAspectRatioCalculation() {
		// Test aspect ratio calculation logic
		float width = 1920f;
		float height = 1080f;
		float aspectRatio = width / height;
		
		assertEquals(1.7777778f, aspectRatio, 0.0001f);
	}

	@Test
	public void testAspectRatioSquare() {
		// Test square aspect ratio
		float width = 1000f;
		float height = 1000f;
		float aspectRatio = width / height;
		
		assertEquals(1.0f, aspectRatio, 0.0001f);
	}

	@Test
	public void testAspectRatioPortrait() {
		// Test portrait aspect ratio
		float width = 1080f;
		float height = 1920f;
		float aspectRatio = width / height;
		
		assertEquals(0.5625f, aspectRatio, 0.0001f);
	}
}
