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
	void rejectsInvalidIdentityAndFrame() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(0L, 1L, 1L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(1L, 0L, 1L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(1L, 1L, 0L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.retireBeforeFrame(0L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.rollbackTo(null));
	}
}
