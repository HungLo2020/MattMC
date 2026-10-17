package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class DynamicWorldMeshLifetimeTest {
	@Test
	void retainsCurrentAndPreviousFrameThenRetiresExactGeneration() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		lifetime.observe(11L, 101L, 1L);
		lifetime.observe(12L, 102L, 2L);

		assertEquals(List.of(), lifetime.retireBeforeFrame(2L));
		assertEquals(
			List.of(new DynamicWorldMeshLifetime.Retirement(11L, 101L)),
			lifetime.retireBeforeFrame(3L)
		);
		assertEquals(1, lifetime.size());
	}

	@Test
	void nestedCheckpointsRestoreOnlyChangesAfterTheirMarks() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		lifetime.observe(21L, 201L, 1L);
		lifetime.observe(22L, 202L, 1L);
		var outer = lifetime.checkpoint(2L);
		lifetime.observe(21L, 201L, 2L);
		var inner = lifetime.checkpoint(2L);
		lifetime.observe(22L, 202L, 2L);
		lifetime.observe(23L, 203L, 2L);
		lifetime.rollbackTo(inner);
		assertEquals(2, lifetime.size());
		lifetime.observe(24L, 204L, 2L);
		lifetime.rollbackTo(outer);
		assertEquals(List.of(
			new DynamicWorldMeshLifetime.Retirement(21L, 201L),
			new DynamicWorldMeshLifetime.Retirement(22L, 202L)
		), lifetime.retireBeforeFrame(3L));
	}

	@Test
	void rollbackRestoresForgottenKeysAndRetirementOrder() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		lifetime.observe(11L, 101L, 1L);
		lifetime.observe(12L, 102L, 1L);
		var checkpoint = lifetime.checkpoint(2L);
		lifetime.forget(11L);
		lifetime.observe(13L, 103L, 2L);
		lifetime.rollbackTo(checkpoint);
		assertEquals(List.of(
			new DynamicWorldMeshLifetime.Retirement(11L, 101L),
			new DynamicWorldMeshLifetime.Retirement(12L, 102L)
		), lifetime.retireBeforeFrame(3L));
	}

	@Test
	void preservesSignedIdentitiesAndSnapshotAcrossOtherFrameObservations() {
		var lifetime = new DynamicWorldMeshLifetime();
		lifetime.observe(Long.MIN_VALUE, -1L, 1L);
		lifetime.observe(2L, 3L, 1L);
		var mark = lifetime.checkpoint(2L);
		lifetime.forget(Long.MIN_VALUE);
		lifetime.observe(2L, 4L, 3L);
		lifetime.observe(5L, 6L, 3L);
		lifetime.rollbackTo(mark);
		assertEquals(List.of(new DynamicWorldMeshLifetime.Retirement(Long.MIN_VALUE, -1L),
			new DynamicWorldMeshLifetime.Retirement(2L, 3L)), lifetime.retireBeforeFrame(3L));
	}

	@Test
	void returnedRetirementsStayOwnedAcrossLaterNativeCalls() {
		var lifetime = new DynamicWorldMeshLifetime();
		lifetime.observe(11L, 12L, 1L);
		var first = lifetime.retireBeforeFrame(3L);
		lifetime.observe(21L, 22L, 3L);
		assertEquals(List.of(new DynamicWorldMeshLifetime.Retirement(21L, 22L)), lifetime.retireBeforeFrame(5L));
		assertEquals(List.of(new DynamicWorldMeshLifetime.Retirement(11L, 12L)), first);
	}

	@Test
	void registeredRigTouchesRetainedPartIdentitiesInOrderAndRollsBack() {
		long rig = 0x7d01_0001L;
		var lifetime = new DynamicWorldMeshLifetime();
		net.vulkanic.bridge.VulkanicGalBridge.registerModelRig(rig, new int[]{-1,0,0,0},
			new long[]{0,11,12,11}, new long[]{0,101,102,101});
		try {
			lifetime.observeRig(rig, 1L);
			assertEquals(2, lifetime.size());
			var mark = lifetime.checkpoint(2L);
			lifetime.observeRig(rig, 2L);
			assertThrows(IllegalArgumentException.class, () -> lifetime.observeRig(rig + 1, 2L));
			assertThrows(IllegalArgumentException.class, () -> lifetime.observeRig(rig, 0L));
			lifetime.rollbackTo(mark);
			assertEquals(List.of(new DynamicWorldMeshLifetime.Retirement(11L,101L),
				new DynamicWorldMeshLifetime.Retirement(12L,102L)), lifetime.retireBeforeFrame(3L));
		} finally { net.vulkanic.bridge.VulkanicGalBridge.releaseModelRig(rig); }
	}

	@Test
	void rejectsInvalidIdentityAndFrame() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(0L, 1L, 1L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(1L, 0L, 1L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(1L, 1L, 0L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.retireBeforeFrame(0L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.rollbackTo(null));
	}
}
