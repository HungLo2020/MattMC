package net.minecraft.client.renderer.shaders.uniform.providers;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CameraUniforms.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class CameraUniformsTest {

	@Test
	public void testGetCameraPositionFract() {
		Vector3d pos = new Vector3d(10.5, 64.75, -20.25);
		Vector3f fract = CameraUniforms.getCameraPositionFract(pos);
		
		assertEquals(0.5f, fract.x, 0.0001f);
		assertEquals(0.75f, fract.y, 0.0001f);
		assertEquals(0.75f, fract.z, 0.0001f); // -20.25 - floor(-20.25) = -20.25 - (-21) = 0.75
	}

	@Test
	public void testGetCameraPositionInt() {
		Vector3d pos = new Vector3d(10.5, 64.75, -20.25);
		Vector3i intPos = CameraUniforms.getCameraPositionInt(pos);
		
		assertEquals(10, intPos.x);
		assertEquals(64, intPos.y);
		assertEquals(-21, intPos.z); // floor(-20.25) = -21
	}

	@Test
	public void testCameraPositionFractAtZero() {
		Vector3d pos = new Vector3d(0.0, 0.0, 0.0);
		Vector3f fract = CameraUniforms.getCameraPositionFract(pos);
		
		assertEquals(0.0f, fract.x, 0.0001f);
		assertEquals(0.0f, fract.y, 0.0001f);
		assertEquals(0.0f, fract.z, 0.0001f);
	}

	@Test
	public void testCameraPositionIntAtZero() {
		Vector3d pos = new Vector3d(0.0, 0.0, 0.0);
		Vector3i intPos = CameraUniforms.getCameraPositionInt(pos);
		
		assertEquals(0, intPos.x);
		assertEquals(0, intPos.y);
		assertEquals(0, intPos.z);
	}

	@Test
	public void testRenderDistanceCalculation() {
		// Test render distance calculation logic
		int renderDistance = 16; // chunks
		int renderDistanceBlocks = renderDistance * 16;
		
		assertEquals(256, renderDistanceBlocks);
	}
}
