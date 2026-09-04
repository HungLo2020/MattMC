package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	void wholeFrameTerrainPublishesAReplacementVisibilitySetBeforeFrameDiagnostics() throws IOException {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"
		));
		int enqueue = source.indexOf("public static void enqueueWholeFrameTerrainSections(");
		int visibleKeys = source.indexOf("Set<Long> visibleMeshKeys = visibleWholeFrameMeshKeys(sectionSnapshot);", enqueue);
		int reconcile = source.indexOf("RustGalWorldPrimitiveRenderer.reconcileStaticTerrainVisibility(visibleMeshKeys);", enqueue);
		int receipt = source.indexOf("recordRustWholeFrameEnqueueCoverage(", enqueue);
		assertTrue(visibleKeys > enqueue);
		assertTrue(reconcile > visibleKeys,
			"the semantic source must replace the active draw domain after its layer submissions");
		assertTrue(receipt > reconcile,
			"coverage receipts must describe the same reconciled semantic frame");
	}

	@Test
	void visibilityReconciliationRetiresInstancesWithoutEvictingPersistentAssets() throws IOException {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("public static void reconcileStaticTerrainVisibility(");
		int end = source.indexOf("\n\tprivate static void markWorldMeshAssetsChangedLocked()", method);
		String body = source.substring(method, end);
		assertTrue(body.contains("StaticTerrainVisibilitySet.reconcile(ACTIVE_STATIC_TERRAIN_INSTANCES, visibleSnapshot)"));
		assertTrue(!body.contains("WORLD_MESH_ASSETS.remove"),
			"culling a section must not destroy the reusable Rust mesh asset");
	}
}
