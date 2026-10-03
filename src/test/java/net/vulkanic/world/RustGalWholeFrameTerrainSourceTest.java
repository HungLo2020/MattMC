package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.occlusion.GraphDirection;
import net.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import net.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.sodium.client.render.viewport.Viewport;
import net.sodium.client.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

	@Test
	void spectatorInsideSolidTerrainAndSmartCullOffBypassPortalOcclusion() {
		assertTrue(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(true, false));
		assertFalse(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(true, true));
		assertFalse(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(false, false));
		assertFalse(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(false, true));
	}

	@Test
	void stationaryOcclusionPolicyChangesInvalidateTraversalAndSettledVisibilityIdentity() {
		long occluded = RustGalWholeFrameTerrainSource.terrainVisibilitySignature(123456789L, 160.0f, true);
		long unoccluded = RustGalWholeFrameTerrainSource.terrainVisibilitySignature(123456789L, 160.0f, false);
		assertNotEquals(occluded, unoccluded);
		assertEquals(occluded,
			RustGalWholeFrameTerrainSource.terrainVisibilitySignature(123456789L, 160.0f, true));
		assertNotEquals(unoccluded,
			RustGalWholeFrameTerrainSource.terrainVisibilitySignature(123456789L, 144.0f, false));
	}

	@Test
	void sealedCameraRootOpensAllNeighborsOnlyWhenOcclusionIsDisabled() {
		long sealed = OcclusionCuller.encodeVisibility(new VisibilitySet());
		assertEquals(GraphDirectionSet.NONE,
			RustGalWholeFrameTerrainSource.rootVisibilityConnections(sealed, true));
		assertEquals(GraphDirectionSet.ALL,
			RustGalWholeFrameTerrainSource.rootVisibilityConnections(sealed, false));
	}

	@Test
	void disabledOcclusionTraversesSealedAndPartiallyOpenNonRootSectionsWithoutMutatingTheirPortals() {
		RenderSection sealed = sectionWithPortals(new VisibilitySet(), 0);
		VisibilitySet portal = new VisibilitySet();
		portal.set(Direction.WEST, Direction.EAST, true);
		RenderSection partial = sectionWithPortals(portal, 1);
		RenderSection[] sections = {sealed, partial};
		int[] incoming = {GraphDirectionSet.of(GraphDirection.WEST), GraphDirectionSet.of(GraphDirection.WEST)};
		Viewport viewport = new Viewport(new Frustum() {
			public boolean testAab(float a, float b, float c, float d, float e, float f) { return true; }
			public int intersectAab(float a, float b, float c, float d, float e, float f) { return 0; }
		}, new Vector3d(8, 8, 8));
		int[] enabled = new int[2];
		int[] bypassed = new int[3];
		bypassed[2] = 123;
		RustGalWholeFrameTerrainSource.terrainVisibilityConnectionsForCameraBatch(
			sections, incoming, viewport, 2, enabled, true);
		assertEquals(GraphDirectionSet.NONE, enabled[0]);
		assertNotEquals(GraphDirectionSet.ALL, enabled[1]);
		RustGalWholeFrameTerrainSource.terrainVisibilityConnectionsForCameraBatch(
			sections, incoming, viewport, 2, bypassed, false);
		assertArrayEquals(new int[]{GraphDirectionSet.ALL, GraphDirectionSet.ALL, 123}, bypassed);
		int[] restored = new int[2];
		RustGalWholeFrameTerrainSource.terrainVisibilityConnectionsForCameraBatch(
			sections, incoming, viewport, 2, restored, true);
		assertArrayEquals(enabled, restored);
	}

	private static RenderSection sectionWithPortals(VisibilitySet portals, int x) {
		BuiltSectionInfo.Builder builder = new BuiltSectionInfo.Builder();
		builder.setOcclusionData(portals);
		RenderSection section = new RenderSection(null, x, 0, 0);
		section.setInfo(builder.build());
		return section;
	}


}
