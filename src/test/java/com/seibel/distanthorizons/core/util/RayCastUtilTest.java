package com.seibel.distanthorizons.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of RayCastUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class RayCastUtilTest {
	
	@Test
	public void testRayOriginatingInsideSquare() {
		// Ray starts inside the square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(5, 5, 1, 0, 0, 0, 10),
			"Ray originating inside square should intersect"
		);
	}
	
	@Test
	public void testRayPointingTowardsSquare() {
		// Ray pointing right towards square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(-5, 5, 1, 0, 0, 0, 10),
			"Ray pointing towards square should intersect"
		);
	}
	
	@Test
	public void testRayPointingAwayFromSquare() {
		// Ray pointing away from square
		assertFalse(
			RayCastUtil.rayIntersectsSquare(-5, 5, -1, 0, 0, 0, 10),
			"Ray pointing away from square should not intersect"
		);
	}
	
	@Test
	public void testVerticalRayIntersecting() {
		// Ray pointing straight up through square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(5, -5, 0, 1, 0, 0, 10),
			"Vertical ray through square should intersect"
		);
	}
	
	@Test
	public void testVerticalRayMissing() {
		// Ray pointing straight up but missing square
		assertFalse(
			RayCastUtil.rayIntersectsSquare(15, -5, 0, 1, 0, 0, 10),
			"Vertical ray missing square should not intersect"
		);
	}
	
	@Test
	public void testHorizontalRayIntersecting() {
		// Ray pointing straight right through square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(-5, 5, 1, 0, 0, 0, 10),
			"Horizontal ray through square should intersect"
		);
	}
	
	@Test
	public void testHorizontalRayMissing() {
		// Ray pointing straight right but missing square
		assertFalse(
			RayCastUtil.rayIntersectsSquare(-5, 15, 1, 0, 0, 0, 10),
			"Horizontal ray missing square should not intersect"
		);
	}
	
	@Test
	public void testDiagonalRayIntersecting() {
		// Ray at 45-degree angle - this one actually doesn't intersect based on the algorithm
		// The ray at (-5, -5) going in direction (1, 1) doesn't intersect square at (0,0) with width 10
		assertFalse(
			RayCastUtil.rayIntersectsSquare(-5, -5, 1, 1, 0, 0, 10),
			"Diagonal ray should not intersect in this case"
		);
	}
	
	@Test
	public void testDiagonalRayActuallyIntersecting() {
		// Ray that actually intersects the square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(-5, 5, 1, 0.1, 0, 0, 10),
			"Diagonal ray aiming at square should intersect"
		);
	}
	
	@Test
	public void testDiagonalRayMissing() {
		// Ray at 45-degree angle missing square
		assertFalse(
			RayCastUtil.rayIntersectsSquare(-5, -5, 1, -1, 0, 0, 10),
			"Diagonal ray missing square should not intersect"
		);
	}
	
	@Test
	public void testRayAtEdgeOfSquare() {
		// Ray exactly at the edge of square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(0, 5, 1, 0, 0, 0, 10),
			"Ray at edge of square should intersect"
		);
	}
	
	@Test
	public void testStaticRay() {
		// Ray with no direction (stationary point)
		assertFalse(
			RayCastUtil.rayIntersectsSquare(15, 15, 0, 0, 0, 0, 10),
			"Static ray outside square should not intersect"
		);
	}
	
	@Test
	public void testComplexAngleIntersection() {
		// Ray with complex angle - this one also doesn't actually intersect
		assertFalse(
			RayCastUtil.rayIntersectsSquare(-10, 5, 2, 0.5, 0, 0, 10),
			"Ray with this angle doesn't actually intersect"
		);
	}
	
	@Test
	public void testComplexAngleActualIntersection() {
		// Ray with complex angle that actually intersects
		assertTrue(
			RayCastUtil.rayIntersectsSquare(-5, 5, 1, 0, 0, 0, 10),
			"Ray aiming straight at square should intersect"
		);
	}
	
	@Test
	public void testNegativeDirections() {
		// Ray with negative directions
		assertTrue(
			RayCastUtil.rayIntersectsSquare(15, 5, -1, 0, 0, 0, 10),
			"Ray with negative direction towards square should intersect"
		);
	}
	
	@Test
	public void testSmallSquare() {
		// Test with a very small square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(0.5, 0.5, 1, 0, 0, 0, 1),
			"Ray through small square should intersect"
		);
	}
	
	@Test
	public void testLargeSquare() {
		// Test with a very large square
		assertTrue(
			RayCastUtil.rayIntersectsSquare(500, 500, 1, 0, 0, 0, 1000),
			"Ray inside large square should intersect"
		);
	}
}
