package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RustGalWholeFrameTerrainSourceTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));







	private static void assertViewDistanceActionInvalidatesSettledReceipt(String source, int actionStart, int actionEnd) {
		int renderDistance = source.indexOf("minecraft.options.renderDistance().set", actionStart);
		int simulationDistance = source.indexOf("minecraft.options.simulationDistance().set", renderDistance);
		int invalidation = source.indexOf("invalidateRustWholeFrameTerrainReadiness();", simulationDistance);
		assertTrue(renderDistance >= actionStart
			&& simulationDistance > renderDistance
			&& invalidation > simulationDistance
			&& invalidation < actionEnd,
			"a view-distance action must invalidate the pre-change settled receipt");
	}



















	@Test
	void terrainSelectionDistanceMatchesFrozensOpaqueFogRule() {
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 256.0f, true));
		assertEquals(48.5f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 48.0f, true));
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 0.5f, 48.0f, true));
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 48.0f, false));
	}


}
