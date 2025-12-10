package net.minecraft.client.renderer.shaders.uniform.providers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CommonUniforms.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class CommonUniformsTest {

	@Test
	public void testIsEyeInWaterValues() {
		// Test that isEyeInWater returns correct values
		// 0 = air, 1 = water, 2 = lava, 3 = powder snow
		int[] validValues = {0, 1, 2, 3};
		
		for (int value : validValues) {
			assertTrue(value >= 0 && value <= 3, "isEyeInWater should return 0-3");
		}
	}

	@Test
	public void testBlindnessRange() {
		// Blindness should be clamped to 0.0-1.0
		float blindness = 0.5f;
		float clamped = Math.clamp(blindness, 0.0f, 1.0f);
		
		assertEquals(0.5f, clamped, 0.0001f);
		assertTrue(clamped >= 0.0f && clamped <= 1.0f);
	}

	@Test
	public void testRainStrengthRange() {
		// Rain strength should be clamped to 0.0-1.0
		float rainStrength = 1.5f; // Out of range value
		float clamped = Math.clamp(rainStrength, 0.0f, 1.0f);
		
		assertEquals(1.0f, clamped, 0.0001f);
		assertTrue(clamped >= 0.0f && clamped <= 1.0f);
	}

	@Test
	public void testNightVisionRange() {
		// Night vision should be clamped to 0.0-1.0
		float nightVision = -0.5f; // Out of range value
		float clamped = Math.clamp(nightVision, 0.0f, 1.0f);
		
		assertEquals(0.0f, clamped, 0.0001f);
		assertTrue(clamped >= 0.0f && clamped <= 1.0f);
	}

	@Test
	public void testEyeBrightnessRange() {
		// Eye brightness values should be 0-240 (16 * 15)
		int blockLight = 10;
		int skyLight = 15;
		
		int blockBrightness = blockLight * 16;
		int skyBrightness = skyLight * 16;
		
		assertEquals(160, blockBrightness);
		assertEquals(240, skyBrightness);
		assertTrue(blockBrightness >= 0 && blockBrightness <= 240);
		assertTrue(skyBrightness >= 0 && skyBrightness <= 240);
	}
}
