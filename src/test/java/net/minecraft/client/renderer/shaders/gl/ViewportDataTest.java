package net.minecraft.client.renderer.shaders.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ViewportData record.
 * Validates IRIS 1.21.9 compatibility for viewport scaling and positioning.
 */
class ViewportDataTest {

	@Test
	void testViewportDataCreation() {
		ViewportData data = new ViewportData(2.0f, 0.5f, 0.25f);
		assertEquals(2.0f, data.scale());
		assertEquals(0.5f, data.viewportX());
		assertEquals(0.25f, data.viewportY());
	}

	@Test
	void testDefaultViewportData() {
		ViewportData defaultData = ViewportData.defaultValue();
		assertNotNull(defaultData);
		assertEquals(1.0f, defaultData.scale(), "Default scale should be 1.0");
		assertEquals(0.0f, defaultData.viewportX(), "Default X offset should be 0.0");
		assertEquals(0.0f, defaultData.viewportY(), "Default Y offset should be 0.0");
	}

	@Test
	void testDefaultViewportDataIsSingleton() {
		// Default should return the same instance (optimization)
		ViewportData default1 = ViewportData.defaultValue();
		ViewportData default2 = ViewportData.defaultValue();
		assertSame(default1, default2, "Default should return singleton instance");
	}

	@Test
	void testViewportDataEquality() {
		ViewportData data1 = new ViewportData(1.5f, 0.1f, 0.2f);
		ViewportData data2 = new ViewportData(1.5f, 0.1f, 0.2f);
		ViewportData data3 = new ViewportData(2.0f, 0.1f, 0.2f);
		
		assertEquals(data1, data2, "Equal data should be equal");
		assertNotEquals(data1, data3, "Different data should not be equal");
	}

	@Test
	void testViewportDataHashCode() {
		ViewportData data1 = new ViewportData(1.5f, 0.1f, 0.2f);
		ViewportData data2 = new ViewportData(1.5f, 0.1f, 0.2f);
		
		assertEquals(data1.hashCode(), data2.hashCode(), "Equal objects should have same hash code");
	}

	@Test
	void testViewportScaleValues() {
		// Test common scale values
		ViewportData fullRes = new ViewportData(1.0f, 0.0f, 0.0f);
		assertEquals(1.0f, fullRes.scale());
		
		ViewportData halfRes = new ViewportData(0.5f, 0.0f, 0.0f);
		assertEquals(0.5f, halfRes.scale());
		
		ViewportData doubleRes = new ViewportData(2.0f, 0.0f, 0.0f);
		assertEquals(2.0f, doubleRes.scale());
	}

	@Test
	void testViewportOffsetValues() {
		// Test viewport offsets
		ViewportData centered = new ViewportData(1.0f, 0.5f, 0.5f);
		assertEquals(0.5f, centered.viewportX());
		assertEquals(0.5f, centered.viewportY());
		
		ViewportData topLeft = new ViewportData(1.0f, 0.0f, 0.0f);
		assertEquals(0.0f, topLeft.viewportX());
		assertEquals(0.0f, topLeft.viewportY());
	}

	@Test
	void testViewportDataToString() {
		ViewportData data = new ViewportData(1.5f, 0.25f, 0.5f);
		String str = data.toString();
		
		assertNotNull(str);
		assertTrue(str.contains("1.5"), "ToString should contain scale value");
		assertTrue(str.contains("0.25"), "ToString should contain X offset");
		assertTrue(str.contains("0.5"), "ToString should contain Y offset");
	}
}
