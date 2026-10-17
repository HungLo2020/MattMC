package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RustGalStaticTerrainVisibilityTest {
	@Test
	void frameVisibilityReplacementRemovesOnlyNoLongerVisibleTerrainInstances() {
		Map<Long, String> active = new LinkedHashMap<>();
		active.put(11L, "solid-near");
		active.put(22L, "cutout-near");
		active.put(33L, "solid-previous-camera");

		int removed = StaticTerrainVisibilitySet.reconcile(
			active, Set.of(11L, 22L)
		);

		assertEquals(1, removed);
		assertEquals(Map.of(11L, "solid-near", 22L, "cutout-near"), active);
	}

	@Test
	void emptyVisibilityReplacementRetiresAllActiveDrawInstancesButNotTheirAssets() {
		Map<Long, String> activeDrawInstances = new LinkedHashMap<>();
		activeDrawInstances.put(41L, "retained-draw-instance");
		Map<Long, String> residentAssets = new LinkedHashMap<>();
		residentAssets.put(41L, "resident-mesh-asset");

		assertEquals(1, StaticTerrainVisibilitySet.reconcile(
			activeDrawInstances, Set.of()
		));
		assertEquals(Map.of(), activeDrawInstances);
		assertEquals(Map.of(41L, "resident-mesh-asset"), residentAssets,
			"visibility retirement must not evict the reusable mesh resource");
	}

	@Test
	void visibilityReconciliationRejectsMissingSemanticInputs() {
		assertThrows(IllegalArgumentException.class, () ->
			StaticTerrainVisibilitySet.reconcile(null, Set.of())
		);
		assertThrows(IllegalArgumentException.class, () ->
			StaticTerrainVisibilitySet.reconcile(Map.of(), null)
		);
	}

	@Test
	void acknowledgedGenerationRemainsDrawableWhileReplacementIsRegistered() {
		assertTrue(StaticTerrainVisibilitySet.isAcceptedGeneration(
			7L, 7L, 8L, 7L
		));
		assertTrue(!StaticTerrainVisibilitySet.isAcceptedGeneration(
			8L, 7L, 8L, 7L
		));
		assertTrue(StaticTerrainVisibilitySet.isAcceptedGeneration(
			8L, 8L, 8L, 8L
		));
	}

	@Test
	void postConsumePublicationCannotReplaceAFrozenFrameMeshGeneration() {
		Map<Long, Long> frozen = Map.of(41L, 7L);

		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 41L, 7L));
		assertTrue(!StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 41L, 8L));
		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 42L, 8L));
	}

	@Test
	void primitiveProtectionKeepsReferencedGenerationsAndAllowsUnreferencedMeshes() {
		var frozen = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
		StaticTerrainVisibilitySet.protectGeneration(frozen, 41L, 7L);
		StaticTerrainVisibilitySet.protectGeneration(frozen, 41L, 7L);
		StaticTerrainVisibilitySet.protectGeneration(frozen, Long.MIN_VALUE, Long.MAX_VALUE);
		assertEquals(2, frozen.size());
		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 41L, 7L));
		assertTrue(!StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 41L, 8L));
		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 42L, 8L));
		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, Long.MIN_VALUE, Long.MAX_VALUE));
		assertTrue(!StaticTerrainVisibilitySet.mayPublishGeneration(frozen, Long.MIN_VALUE, 7L));
	}

	@Test
	void conflictingGenerationDoesNotReplaceFrozenProtection() {
		var frozen = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
		StaticTerrainVisibilitySet.protectGeneration(frozen, 41L, 7L);
		assertThrows(IllegalStateException.class, () ->
			StaticTerrainVisibilitySet.protectGeneration(frozen, 41L, 8L));
		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 41L, 7L));
		assertTrue(!StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 41L, 8L));
	}

	@Test
	void zeroGenerationAndZeroKeyAreNeverConfusedWithAbsentProtection() {
		var frozen = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
		StaticTerrainVisibilitySet.protectGeneration(frozen, 0L, 0L);
		assertThrows(IllegalStateException.class, () ->
			StaticTerrainVisibilitySet.protectGeneration(frozen, 0L, 1L));
		assertTrue(!StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 0L, 1L));
		assertTrue(StaticTerrainVisibilitySet.mayPublishGeneration(frozen, 1L, 0L));
	}

	@Test
	void primitiveVisibilityWithdrawalPreservesSurvivorOrder() {
		var active = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<String>();
		active.put(0L, "zero");
		active.put(Long.MIN_VALUE, "negative");
		active.put(42L, "withdrawn");
		active.put(Long.MAX_VALUE, "positive");
		var visible = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(
			new long[] {Long.MIN_VALUE, Long.MAX_VALUE});
		assertEquals(2, StaticTerrainVisibilitySet.reconcile(active, visible));
		assertArrayEquals(new long[] {Long.MIN_VALUE, Long.MAX_VALUE}, active.keySet().toLongArray());
		assertEquals("negative", active.get(Long.MIN_VALUE));
		assertEquals("positive", active.get(Long.MAX_VALUE));
		assertEquals(2, StaticTerrainVisibilitySet.reconcile(active,
			new it.unimi.dsi.fastutil.longs.LongOpenHashSet()));
		assertTrue(active.isEmpty());
	}

	@Test
	void boundedAdmissionEvictsEldestDespiteLookupAndReplacement() {
		var active = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<String>();
		StaticTerrainVisibilitySet.rememberBounded(active, 0L, "oldest", 3);
		StaticTerrainVisibilitySet.rememberBounded(active, Long.MIN_VALUE, "middle", 3);
		StaticTerrainVisibilitySet.rememberBounded(active, Long.MAX_VALUE, "last", 3);
		assertEquals("oldest", active.get(0L));
		StaticTerrainVisibilitySet.rememberBounded(active, 0L, "replacement", 3);
		StaticTerrainVisibilitySet.rememberBounded(active, 41L, "new", 3);
		assertEquals(3, active.size());
		assertTrue(!active.containsKey(0L));
		assertArrayEquals(new long[] {Long.MIN_VALUE, Long.MAX_VALUE, 41L}, active.keySet().toLongArray());
		StaticTerrainVisibilitySet.rememberBounded(active, Long.MIN_VALUE, "updated", 3);
		assertEquals("updated", active.get(Long.MIN_VALUE));
		assertArrayEquals(new long[] {Long.MIN_VALUE, Long.MAX_VALUE, 41L}, active.keySet().toLongArray());
	}

}
