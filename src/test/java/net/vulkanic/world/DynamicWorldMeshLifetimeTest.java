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
	void reuseRefreshesLifetimeAndRollbackRestoresPriorUsage() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		lifetime.observe(21L, 201L, 1L);
		DynamicWorldMeshLifetime checkpoint = lifetime.copy();
		lifetime.observe(21L, 201L, 3L);
		lifetime.observe(22L, 202L, 3L);
		lifetime.restore(checkpoint);

		assertEquals(
			List.of(new DynamicWorldMeshLifetime.Retirement(21L, 201L)),
			lifetime.retireBeforeFrame(3L)
		);
		assertEquals(0, lifetime.size());
	}

	@Test
	void rejectsInvalidIdentityAndFrame() {
		DynamicWorldMeshLifetime lifetime = new DynamicWorldMeshLifetime();
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(0L, 1L, 1L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(1L, 0L, 1L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.observe(1L, 1L, 0L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.retireBeforeFrame(0L));
		assertThrows(IllegalArgumentException.class, () -> lifetime.restore(null));
	}
}
