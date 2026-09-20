package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
