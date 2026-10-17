package net.minecraft.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GraphicsAuditLeafParticleFixtureTest {
	@Test
	void leavesSitInARowAheadOfTheEye() {
		var positions = GraphicsAuditLeafParticleFixture.positions(new Vec3(0, 64, 0), new Vec3(0, 0, -2), 6);
		assertEquals(6, positions.size());
		for (Vec3 position : positions) {
			// Three blocks ahead, at eye height.
			assertEquals(-3.0, position.z, 1.0e-9);
			assertEquals(64.0, position.y, 1.0e-9);
		}
		// Centred and evenly spaced across the view.
		assertEquals(0.0, positions.get(0).x + positions.get(5).x, 1.0e-9);
		double spacing = positions.get(1).x - positions.get(0).x;
		assertEquals(GraphicsAuditLeafParticleFixture.SPACING, Math.abs(spacing), 1.0e-9);
	}

	@Test
	void straightUpOrDownStillHasASideways() {
		var positions = GraphicsAuditLeafParticleFixture.positions(Vec3.ZERO, new Vec3(0, -1, 0), 2);
		assertEquals(positions.get(0).y, positions.get(1).y, 1.0e-9);
		assertEquals(GraphicsAuditLeafParticleFixture.SPACING, positions.get(1).x - positions.get(0).x, 1.0e-9);
	}

	@Test
	void missingLookDirectionIsRejected() {
		assertThrows(IllegalArgumentException.class,
			() -> GraphicsAuditLeafParticleFixture.positions(Vec3.ZERO, Vec3.ZERO, 6));
	}
}
