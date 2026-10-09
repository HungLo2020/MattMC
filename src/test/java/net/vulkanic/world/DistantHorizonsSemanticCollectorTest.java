package net.vulkanic.world;

import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistantHorizonsSemanticCollectorTest {
	@Test
	void identicalContainerReplacementKeepsItsSharedGenerationUntilTheLastOwnerCloses() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		LodBufferContainer first = buildSemanticContainer(77L, 0xff557733);
		publishPendingForTest();
		long generation = DistantHorizonsSemanticCollector.snapshotForTest(77L).generation();
		LodBufferContainer replacement = buildSemanticContainer(77L, 0xff557733);
		assertEquals(generation, DistantHorizonsSemanticCollector.snapshotForTest(77L).generation());

		first.close();
		first.close();
		assertTrue(replacement.rustSemanticBuildLifecycleCurrent(),
			"closing the previous container must not retire the replacement's reused generation");
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		replacement.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(77L, generation));
		var retirement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(generation, retirement.retirements().getFirst().columnGeneration());
		DistantHorizonsSemanticCollector.acknowledgeForTest(retirement);
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(77L));
	}

	private static LodBufferContainer buildSemanticContainer(long key, int color) {
		LodQuadBuilder builder = new LodQuadBuilder(false, null);
		builder.addQuadUp((short) 0, (short) 1, (short) 0, (short) 1, (short) 1,
			color, (byte) 1, (byte) 15, (byte) 0);
		LodBufferContainer container = new LodBufferContainer(key, new DhBlockPos(0, 64, 0));
		assertEquals(container, container.makeAndUploadBuffersAsync(builder).join());
		assertTrue(container.renderDataReady());
		return container;
	}

	@Test
	void closingTheNewestIdenticalContainerFirstKeepsOtherOwnersAlive() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		LodBufferContainer first = buildSemanticContainer(78L, 0xff557733);
		LodBufferContainer second = buildSemanticContainer(78L, 0xff557733);
		LodBufferContainer third = buildSemanticContainer(78L, 0xff557733);
		publishPendingForTest();
		third.close();
		second.close();
		second.close();
		assertTrue(first.rustSemanticBuildLifecycleCurrent());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		first.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(78L));
		assertEquals(1, DistantHorizonsSemanticCollector.pendingUpdateForTest().retirements().size());
	}

	@Test
	void lateContainerCloseCannotRetireChangedOrReloadedGenerations() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		LodBufferContainer first = buildSemanticContainer(79L, 0xff557733);
		publishPendingForTest();
		LodBufferContainer changed = buildSemanticContainer(79L, 0xff885533);
		publishPendingForTest();
		first.close();
		assertTrue(changed.rustSemanticBuildLifecycleCurrent());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		DistantHorizonsSemanticCollector.invalidateForResourceReload();
		assertFalse(changed.rustSemanticBuildLifecycleCurrent());
		LodBufferContainer reloaded = buildSemanticContainer(79L, 0xff885533);
		publishPendingForTest();
		changed.close();
		assertTrue(reloaded.rustSemanticBuildLifecycleCurrent());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		reloaded.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(79L));
	}

	@Test
	void containerRetirementWorksAfterNativeAcknowledgementReleasesCopiedGeometry() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		LodBufferContainer container = buildSemanticContainer(80L, 0xff557733);
		var update = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(80L));
		assertTrue(container.rustSemanticBuildLifecycleCurrent());
		container.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(80L));
		DistantHorizonsSemanticCollector.acknowledgeForTest(
			DistantHorizonsSemanticCollector.pendingUpdateForTest());
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(80L));
	}

	@Test
	void snapshotCachePressureCannotRetireColumnsStillOwnedByTheQuadtree() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		LodBufferContainer first = buildSemanticContainer(81L, 0xff557733);
		LodBufferContainer second = buildSemanticContainer(82L, 0xff557733);
		publishPendingForTest();
		// Neither column is in the current visible list: live siblings and parents
		// still need to remain ready for the next quadtree transition.
		DistantHorizonsSemanticCollector.trimRetainedColumnsForTest(1, 1);
		assertTrue(first.rustSemanticBuildLifecycleCurrent());
		assertTrue(second.rustSemanticBuildLifecycleCurrent());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		first.close();
		second.close();
		var retirement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(2, retirement.retirements().size());
		DistantHorizonsSemanticCollector.acknowledgeForTest(retirement);
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(81L));
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(82L));
	}

	@Test
	void repeatedCloseOfAnEmptyContainerCannotRetireALaterNonEmptyBuild() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		LodBufferContainer empty = new LodBufferContainer(83L, new DhBlockPos(0, 64, 0));
		empty.makeAndUploadBuffersAsync(new LodQuadBuilder(false, null)).join();
		assertTrue(empty.rustSemanticBuildHasNoDrawableGeometry());
		LodBufferContainer nonEmpty = buildSemanticContainer(83L, 0xff557733);
		publishPendingForTest();
		empty.close();
		empty.close();
		assertTrue(nonEmpty.rustSemanticBuildLifecycleCurrent());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		nonEmpty.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(83L));
	}

	@Test
	void closingAContainerThatNeverRecordedCannotRetireAnotherSectionsGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		// e.g. a section closed before its build ran, or a failed build
		LodBufferContainer unbuilt = new LodBufferContainer(84L, new DhBlockPos(0, 64, 0));
		LodBufferContainer live = buildSemanticContainer(84L, 0xff557733);
		publishPendingForTest();
		unbuilt.close();
		assertTrue(live.rustSemanticBuildLifecycleCurrent(),
			"a container without a lease owns no generation and must not retire the position by key");
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		live.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(84L));
	}

	@Test
	void quadtreeRenderabilityWaitsForAcknowledgedRustAssetPublication() throws Exception {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.resetForTest();
		long columnKey = 73L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);

		assertFalse(DistantHorizonsSemanticCollector.requestColumnPublication(columnKey),
			"a CPU-built child must not disable its covering parent before native acknowledgement");
		var first = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(columnKey, first.assets().getFirst().columnKey());
		DistantHorizonsSemanticCollector.acknowledgeForTest(first);
		assertTrue(DistantHorizonsSemanticCollector.requestColumnPublication(columnKey));

		String renderSection = Files.readString(Path.of(
			"src/main/java/com/seibel/distanthorizons/core/render/LodRenderSection.java"));
		assertTrue(renderSection.contains("DistantHorizonsSemanticCollector.requestColumnPublication(this.pos)"),
			"DH parent/child transitions must use acknowledged Rust readiness rather than CPU cache membership");
	}

	@Test
	void normalPublicationKeepsCompactReadinessAfterReleasingCopiedGeometry() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.resetForTest();
		long columnKey = 74L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		var update = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		long generation = update.assets().getFirst().columnGeneration();
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(columnKey));
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(columnKey, generation));
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		assertTrue(DistantHorizonsSemanticCollector.requestColumnPublication(columnKey));
		DistantHorizonsSemanticCollector.removeColumn(columnKey, generation);
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(columnKey, generation));
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		DistantHorizonsSemanticCollector.acknowledgeForTest(
			DistantHorizonsSemanticCollector.pendingUpdateForTest());
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
	}

	@Test
	void primitiveColumnMembershipTracksTheSemanticSnapshotLifecycle() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.resetForTest();
		long columnKey = 9_223_372_036_854_000_000L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);

		assertTrue(DistantHorizonsSemanticCollector.hasColumn(columnKey));
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		DistantHorizonsSemanticCollector.PendingAssetUpdate update = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(1, update.assets().size());
		long columnGeneration = update.assets().getFirst().columnGeneration();
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(columnKey, columnGeneration));
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(columnKey, columnGeneration + 1));
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		DistantHorizonsSemanticCollector.removeColumn(columnKey);
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(columnKey));
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(columnKey, columnGeneration));
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey),
			"the acknowledged descriptor must live until Rust accepts its retirement");
		DistantHorizonsSemanticCollector.acknowledgeForTest(
			DistantHorizonsSemanticCollector.pendingUpdateForTest());
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
	}

	@Test
	void lateContainerCloseCannotRetireNewerSemanticGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 44L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		long oldGeneration = DistantHorizonsSemanticCollector.snapshotForTest(columnKey).generation();
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 9, 8, 7, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		long newGeneration = DistantHorizonsSemanticCollector.snapshotForTest(columnKey).generation();

		DistantHorizonsSemanticCollector.removeColumn(columnKey, oldGeneration);
		assertEquals(newGeneration, DistantHorizonsSemanticCollector.snapshotForTest(columnKey).generation());
		DistantHorizonsSemanticCollector.removeColumn(columnKey, newGeneration);
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(columnKey));
	}

	@Test
	void completedEmptyBuildKeepsPreviousAssetUntilContainerSwapClosesItsOwner() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 46L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		long previousGeneration = DistantHorizonsSemanticCollector.snapshotForTest(columnKey).generation();
		publishPendingForTest();

		LodQuadBuilder.SemanticVertexBufferBuild empty =
			new LodQuadBuilder.SemanticVertexBufferBuild(List.of(), List.of(), List.of(), List.of());
		long emptyGeneration = DistantHorizonsSemanticCollector.recordRustSemanticBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0), List.of(),
			new LodQuadBuilder.SemanticQuadCoverage(0, 0, 0),
			new LodQuadBuilder.SemanticQuadCoverage(0, 0, 0),
			empty, empty, empty, empty
		);

		assertEquals(0L, emptyGeneration);
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey),
			"worker-side empty completion must not erase the active asset before the container swap");
		assertEquals(previousGeneration,
			DistantHorizonsSemanticCollector.snapshotForTest(columnKey).generation());

		DistantHorizonsSemanticCollector.removeColumn(columnKey, previousGeneration);
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		DistantHorizonsSemanticCollector.acknowledgeForTest(
			DistantHorizonsSemanticCollector.pendingUpdateForTest());
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
	}

	@Test
	void closingVisibleColumnBeforePreflightCannotSubmitItsRetiredNativeAsset() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 45L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(columnKey).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();

		DistantHorizonsSemanticCollector.removeColumn(columnKey);
		var retirement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(retirement);
		assertEquals(columnKey, retirement.retirements().getFirst().columnKey());
		DistantHorizonsSemanticCollector.acknowledgeForTest(retirement);

		var consumed = DistantHorizonsSemanticCollector.consumeVisibleFrame();
		assertTrue(consumed.visibleSegments().isEmpty());
		assertEquals(0, consumed.renderFrame().flags()
			& DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED);
		assertFalse(DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot().selected());
	}

	@Test
	void resourceReloadRetiresPublishedDhColumnsBeforeNewAtlasProvenanceCanBeUsed() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.resetForTest();
		long columnKey = 17L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate update =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));

		DistantHorizonsSemanticCollector.invalidateForResourceReload();

		assertFalse(DistantHorizonsSemanticCollector.hasColumn(columnKey));
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		DistantHorizonsSemanticCollector.PendingAssetUpdate retirement =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(retirement);
		assertEquals(1, retirement.retirements().size());
		assertEquals(columnKey, retirement.retirements().getFirst().columnKey());
		var reset = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals(1L, reset.lifecycleResetCount());
		assertEquals(1L, reset.resourceReloadResetCount());
		assertEquals(0L, reset.worldUnloadResetCount());
		assertEquals("resource-reload", reset.lastLifecycleResetReason());
		assertEquals(1, reset.lastLifecyclePublishedRetirements());
		assertEquals(0, reset.lastLifecycleInvalidatedInFlight());
		assertEquals(1, reset.pendingRetirements());
		assertEquals(2L, reset.lastLifecycleGenerationFloor());

		DistantHorizonsSemanticCollector.acknowledgeForTest(retirement);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 9, 8, 7, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate rebuilt =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(rebuilt);
		DistantHorizonsSemanticCollector.acknowledgeForTest(rebuilt);
		var republished = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals(1, republished.lastLifecycleRetirementsAcknowledged());
		assertEquals(0, republished.lastLifecycleRetirementsSupersededByReplacement());
		assertEquals(0, republished.lastLifecycleRetirementsOutstanding());
		assertEquals(0, republished.pendingRetirements());
		assertEquals(0, republished.invalidatedInFlight());
		assertTrue(republished.minimumPublishedGeneration() >= republished.lastLifecycleGenerationFloor());
	}

	@Test
	void resourceReloadReplacementResolvesTheExactOldGenerationWithoutRedundantRetirement() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 19L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		var original = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.acknowledgeForTest(original);
		DistantHorizonsSemanticCollector.invalidateForResourceReload();

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 9, 8, 7, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		var replacement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(replacement);
		assertEquals(1, replacement.assets().size());
		assertEquals(0, replacement.retirements().size());
		DistantHorizonsSemanticCollector.acknowledgeForTest(replacement);

		var receipt = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals(1, receipt.lastLifecyclePublishedRetirements());
		assertEquals(0, receipt.lastLifecycleRetirementsAcknowledged());
		assertEquals(1, receipt.lastLifecycleRetirementsSupersededByReplacement());
		assertEquals(0, receipt.lastLifecycleRetirementsOutstanding());
		assertEquals(0, receipt.pendingRetirements());
		assertTrue(receipt.minimumPublishedGeneration() >= receipt.lastLifecycleGenerationFloor());
	}

	@Test
	void emptyTeardownCannotEraseTheLastMaterialWorldUnloadReceipt() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 20L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		var original = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.acknowledgeForTest(original);
		DistantHorizonsSemanticCollector.clear();
		var retirement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.acknowledgeForTest(retirement);

		var beforeEmptyTeardown = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		DistantHorizonsSemanticCollector.clear();
		var afterEmptyTeardown = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();

		assertEquals(beforeEmptyTeardown.lifecycleResetCount() + 1, afterEmptyTeardown.lifecycleResetCount());
		assertEquals(beforeEmptyTeardown.worldUnloadResetCount() + 1, afterEmptyTeardown.worldUnloadResetCount());
		assertEquals("world-unload", afterEmptyTeardown.lastLifecycleResetReason());
		assertEquals(1, afterEmptyTeardown.lastLifecyclePublishedRetirements());
		assertEquals(1, afterEmptyTeardown.lastLifecycleRetirementsAcknowledged());
		assertEquals(0, afterEmptyTeardown.lastLifecycleRetirementsOutstanding());
		assertEquals(beforeEmptyTeardown.lastLifecycleGenerationFloor(), afterEmptyTeardown.lastLifecycleGenerationFloor());
	}

	@Test
	void lateAcknowledgementCannotPublishAReusedColumnKeyAfterWorldReset() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.resetForTest();
		long columnKey = 23L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(0, 0, 0, 0xB7, 1, 2, 3, 255, 1, 2)), List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate oldUpdate =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(oldUpdate);

		// The new world can rebuild the same DH column key before the old native
		// transaction returns. Its acknowledgement must never install old bytes.
		DistantHorizonsSemanticCollector.clear();
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 3, 4)), List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.acknowledgeForTest(oldUpdate);
		assertFalse(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));

		DistantHorizonsSemanticCollector.PendingAssetUpdate newUpdate =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(newUpdate);
		assertEquals(1, newUpdate.assets().size());
		assertEquals(2L, newUpdate.assets().getFirst().columnGeneration());
		DistantHorizonsSemanticCollector.acknowledgeForTest(newUpdate);
		assertTrue(DistantHorizonsSemanticCollector.hasPublishedColumn(columnKey));
		assertEquals(2L, DistantHorizonsSemanticCollector.snapshotForTest(columnKey).generation());
	}

	/** The per-frame render list in one ledger call matches the per-column
	 * calls it replaced: publication requests in walk order, visibility in
	 * near-to-far order (stable for equal distances), and segment admission. */
	@Test
	void visibleFrameMatchesThePerColumnRenderListCalls() {
		assertVisibleFrameParity(false);
	}

	/** Exact-atlas coverage keeps Java's per-column admission after the call. */
	@Test
	void exactAtlasVisibleFrameMatchesThePerColumnRenderListCalls() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		assertVisibleFrameParity(true);
	}

	private static void assertVisibleFrameParity(boolean exactAtlas) {
		int centerX = 96;
		int centerZ = -40;
		// Walk order differs from distance order; two pairs tie on distance.
		long[] walk = {
			DhSectionPos.encode((byte) 6, 3, 0), DhSectionPos.encode((byte) 6, 1, -1),
			DhSectionPos.encode((byte) 7, 0, 0), DhSectionPos.encode((byte) 6, 1, 0),
			DhSectionPos.encode((byte) 6, 2, -1), DhSectionPos.encode((byte) 6, -4, 5),
			DhSectionPos.encode((byte) 6, 1, -2)
		};
		java.util.function.Supplier<Object> perColumn = () -> {
			long[] walked = buildRenderListFixture(walk);
			DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
			int unpublished = 0;
			List<Long> candidates = new java.util.ArrayList<>();
			for (int index = 0; index < walked.length; index += 2) {
				long key = walked[index];
				// rustSemanticBuildLifecycleCurrent, then the candidate's requests.
				if (!DistantHorizonsSemanticCollector.hasColumn(key, walked[index + 1])) continue;
				candidates.add(key);
				if (!DistantHorizonsSemanticCollector.hasPublishedColumn(key)
					&& !DistantHorizonsSemanticCollector.requestColumnPublication(key)) {
					unpublished++;
				}
			}
			List<Long> sorted = new java.util.ArrayList<>(candidates);
			sorted.sort(java.util.Comparator.comparingInt(key ->
				Math.abs(DhSectionPos.getCenterBlockPosX(key) - centerX) + Math.abs(DhSectionPos.getCenterBlockPosZ(key) - centerZ)));
			DistantHorizonsSemanticCollector.recordRenderListVisibilityStats(sorted.size(), unpublished, sorted);
			int[] counts = new int[3];
			for (long key : sorted) {
				var segments = DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(key);
				counts[0] += segments.opaqueSegments();
				counts[1] += segments.transparentSegments();
				counts[2] += segments.waterSegments();
			}
			return renderListOutcome(sorted.stream().mapToLong(Long::longValue).toArray(), unpublished, counts);
		};
		java.util.function.Supplier<Object> oneCall = () -> {
			long[] walked = buildRenderListFixture(walk);
			DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
			long[] reused = java.util.Arrays.copyOf(walked, walked.length + 6);
			var frame = DistantHorizonsSemanticCollector.collectVisibleFrame(reused, walked.length / 2, centerX, centerZ);
			assertEquals(!exactAtlas, frame.admitted());
			assertEquals(0, frame.requestFailures());
			var segments = frame.segments();
			int[] counts = { segments.opaqueSegments(), segments.transparentSegments(), segments.waterSegments() };
			if (!frame.admitted()) {
				for (long key : frame.nearToFar()) {
					var admitted = DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(key);
					counts[0] += admitted.opaqueSegments();
					counts[1] += admitted.transparentSegments();
					counts[2] += admitted.waterSegments();
				}
			}
			return renderListOutcome(frame.nearToFar(), frame.unpublished(), counts);
		};
		String expected = perColumn.get().toString();
		DistantHorizonsSemanticCollector.resetForTest();
		assertEquals(expected, oneCall.get().toString());
		assertTrue(expected.contains("unpublished=1"), expected);
		assertFalse(expected.contains("counts=[0, 0, 0]"), expected);
		assertFalse(expected.startsWith("order=" + java.util.Arrays.toString(walk)), "the fixture must reorder: " + expected);
	}

	/** Publishes the first five walk columns, then rebuilds one (pending
	 * replacement) and builds one more (never published); the last walk entry
	 * is a container whose generation was closed. Returns the walk as (key,
	 * generation) pairs, including the replaced container. Run against a fresh
	 * collector so generations repeat. */
	private static long[] buildRenderListFixture(long[] walk) {
		LodBufferContainer[] containers = new LodBufferContainer[walk.length];
		for (int index = 0; index < 5; index++) {
			containers[index] = buildSemanticContainer(walk[index], 0xff557733 + index);
		}
		publishPendingForTest();
		LodBufferContainer replaced = containers[1];
		containers[1] = buildSemanticContainer(walk[1], 0xff000001);
		containers[5] = buildSemanticContainer(walk[5], 0xff000002);
		containers[6] = buildSemanticContainer(walk[6], 0xff000003);
		long closed = containers[6].rustSemanticWalkGeneration();
		containers[6].close();
		long[] walked = new long[(walk.length + 1) * 2];
		for (int index = 0; index < walk.length; index++) {
			walked[index * 2] = walk[index];
			walked[index * 2 + 1] = index == 6 ? closed : containers[index].rustSemanticWalkGeneration();
		}
		walked[walk.length * 2] = walk[1];
		walked[walk.length * 2 + 1] = replaced.rustSemanticWalkGeneration();
		return walked;
	}

	private static String renderListOutcome(long[] nearToFar, int unpublished, int[] counts) {
		var route = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		var frame = DistantHorizonsSemanticCollector.consumeRenderFrame();
		var visible = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		var update = DistantHorizonsSemanticCollector.pendingVisibleUpdateForTest();
		return "order=" + java.util.Arrays.toString(nearToFar) + " unpublished=" + unpublished
			+ " counts=" + java.util.Arrays.toString(counts) + " route=" + route
			+ " enabled=" + frame.enabled() + " visible=" + visible
			+ " update=" + (update == null ? "none" : update.assets().stream()
				.map(asset -> asset.columnKey() + ":" + asset.columnGeneration()).toList());
	}

	@AfterEach
	void resetCollector() {
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		System.clearProperty(DistantHorizonsSemanticCollector.LEGACY_OBSERVATION_PROPERTY);
		DistantHorizonsSemanticCollector.resetForTest();
	}

	@Test
	void disabledDhRendererDoesNotDivertBackgroundBuildsIntoRustSemantics() {
		String wholeFrameProperty = "mattmc.dev.rustGalVulkanWholeFrame";
		String previousWholeFrame = System.getProperty(wholeFrameProperty);
		EDhApiRendererMode previousMode = Config.Client.Advanced.Debugging.rendererMode.get();
		try {
			System.setProperty(wholeFrameProperty, "true");
			Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(EDhApiRendererMode.DISABLED);
			assertFalse(DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild());

			Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(EDhApiRendererMode.DEFAULT);
			assertTrue(DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild());
		} finally {
			Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(previousMode);
			if (previousWholeFrame == null) {
				System.clearProperty(wholeFrameProperty);
			} else {
				System.setProperty(wholeFrameProperty, previousWholeFrame);
			}
		}
	}

	@Test
	void waterSourceInputReceiptDistinguishesConvertedWaterFromNonWaterCoverage() {
		BlockPos witness = new BlockPos(104, 97, 529);
		DistantHorizonsSemanticCollector.configureWaterSourceInputProbes(List.of(witness));
		long water = RenderDataPointUtil.createDataPoint(
			162, 161, 0xFFFFFFFF, 15, 0, EDhApiBlockMaterial.WATER.index
		);
		DistantHorizonsSemanticCollector.recordWaterSourceInput(
			42L, (byte) 0, 104, -64, 529, water, ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
		);

		var waterReceipt = DistantHorizonsSemanticCollector.waterSourceInputReceipt(List.of(witness));
		assertTrue(waterReceipt.matched());
		assertEquals("ok", waterReceipt.status());
		assertEquals(EDhApiBlockMaterial.WATER.index, waterReceipt.traces().getFirst().dhMaterialId());

		long opaque = RenderDataPointUtil.createDataPoint(
			162, 161, 0xFFFFFFFF, 15, 0, EDhApiBlockMaterial.DIRT.index
		);
		DistantHorizonsSemanticCollector.recordWaterSourceInput(
			42L, (byte) 0, 104, -64, 529, opaque, ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
		);
		var opaqueReceipt = DistantHorizonsSemanticCollector.waterSourceInputReceipt(List.of(witness));
		assertFalse(opaqueReceipt.matched());
		assertEquals("render-data-covering-fixture-is-not-water", opaqueReceipt.status());
	}

	@Test
	void waterSourceReceiptAcceptsTheReducedSurfaceUpperBoundaryOnlyForWater() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long water = RenderDataPointUtil.createDataPoint(
			162, 161, 0xFFFFFFFF, 15, 0, EDhApiBlockMaterial.WATER.index
		);
		int upperY = -64 + RenderDataPointUtil.getYMax(water);
		BlockPos probe = new BlockPos(104, upperY, 529);
		DistantHorizonsSemanticCollector.configureWaterSourceInputProbes(List.of(probe));
		DistantHorizonsSemanticCollector.recordWaterSourceInput(
			42L, (byte) 0, 104, -64, 529, water,
			ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
		);
		assertTrue(DistantHorizonsSemanticCollector.waterSourceInputReceipt(List.of(probe)).matched());
	}

	@Test
	void projectionInverseUsesTheCanonicalColumnMajorSemanticLayout() {
		// The same perspective shape captured from the DH semantic boundary:
		// it is row-major here and becomes the ABI's column-major representation.
		float[] projection = DistantHorizonsSemanticCollector.rowMajorToColumnMajor(new float[] {
			0.70715946F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.25717247F, 0.0F, 0.0F,
			0.0F, 0.0F, -1.00639319F, -7.36495113F,
			0.0F, 0.0F, -1.0F, 0.0F
		});

		float[] inverse = DistantHorizonsSemanticCollector.invertColumnMajorMatrix(projection);

		assertNotNull(inverse);
		assertTrue(
			DistantHorizonsSemanticCollector.matrixInverseResidual(projection, inverse) <= 0.001F,
			"the copied DH projection inverse must reconstruct identity in ABI layout"
		);
	}

	@Test
	void copiedDhModelViewPreservesTheAuthoritativeRenderParamTransform() {
		float[] authoritativeRowMajor = new float[] {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 0.5F, -0.8660254F, 0.0F,
			0.0F, 0.8660254F, 0.5F, 0.0F,
			-150.5F, -107.62F, -530.5F, 1.0F
		};

		assertArrayEquals(
			DistantHorizonsSemanticCollector.rowMajorToColumnMajor(authoritativeRowMajor),
			DistantHorizonsSemanticCollector.copyDhModelViewForRust(authoritativeRowMajor)
		);
	}

	@Test
	void singularProjectionIsRejectedBeforeItCanReachTheShaderContract() {
		assertNull(DistantHorizonsSemanticCollector.invertColumnMajorMatrix(new float[16]));
	}

	@Test
	void captureCopiesSemanticBuffersBeforeLegacyUploadCanMutateThem() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ByteBuffer source = quadBuffer(7, 8, 9, 0xB7, 11, 12, 13, 14, 15, 16);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			44L,
			new DhBlockPos(10, 64, -20),
			List.of(source),
			List.of(),
			List.of(),
			List.of()
		);
		source.putShort(0, (short) 99);

		DistantHorizonsSemanticCollector.LodColumnSnapshot snapshot =
			DistantHorizonsSemanticCollector.snapshotForTest(44L);
		assertEquals(10, snapshot.originX());
		assertEquals(64, snapshot.originY());
		assertEquals(-20, snapshot.originZ());
		assertEquals(1, snapshot.opaque().size());
		DistantHorizonsSemanticCollector.LodVertex vertex = snapshot.opaque().getFirst().vertices().getFirst();
		assertEquals(7, vertex.localX());
		assertEquals(8, vertex.localY());
		assertEquals(9, vertex.localZ());
		assertEquals(0xB7, vertex.packedLightAndMicroOffset());
		assertEquals(7, vertex.skyLight());
		assertEquals(11, vertex.blockLight());
		assertEquals(0, vertex.microOffset());
		assertEquals(11, vertex.red());
		assertEquals(12, vertex.green());
		assertEquals(13, vertex.blue());
		assertEquals(14, vertex.alpha());
		assertEquals(15, vertex.materialId());
		assertEquals(5, vertex.normalIndex());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.opaque().add(null));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.opaque().getFirst().vertices().add(vertex));
	}

	@Test
	void packedValidationPreservesMaterialNormalAndUnsignedCoordinateBoundsWithoutPerVertexAllocations() {
		byte[] packet = new byte[65_536 * DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES];
		java.util.Arrays.fill(packet, 0, 8, (byte)0xFF);
		packet[12] = 15;
		packet[13] = 5;
		var segment = DistantHorizonsSemanticCollector.LodBufferSnapshot.fromPacked(0, packet);
		var allocation = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().threadId();
		long before = allocation.getThreadAllocatedBytes(thread);
		var snapshot = new DistantHorizonsSemanticCollector.LodColumnSnapshot(9L, 1L, 0, 0, 0,
			List.of(segment), List.of(), List.of(), List.of());
		long allocated = allocation.getThreadAllocatedBytes(thread) - before;
		assertEquals(65_536, snapshot.opaque().getFirst().vertices().size());
		assertTrue(allocated < 256 * 1024, "packed admission allocated per-vertex objects: " + allocated);
		for (int offset : new int[]{12, 13}) {
			byte[] invalid = new byte[4 * DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES];
			invalid[offset] = (byte)(offset == 12 ? 16 : 6);
			var malformed = DistantHorizonsSemanticCollector.LodBufferSnapshot.fromPacked(0, invalid);
			assertThrows(IllegalArgumentException.class, () -> new DistantHorizonsSemanticCollector.LodColumnSnapshot(
				9L, 1L, 0, 0, 0, List.of(malformed), List.of(), List.of(), List.of()));
		}
	}

	@Test
	void rustOwnedPacketsBecomeTheBoundedSemanticAssetWithoutLegacyDirectBuffers() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		byte[] packet = new byte[DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 4];
		quadBuffer(7, 8, 9, 0xB7, 11, 12, 13, 14, 1, 5).get(packet);
		LodQuadBuilder.SemanticVertexBufferBuild opaque = new LodQuadBuilder.SemanticVertexBufferBuild(
			List.of(packet), List.of(new int[] { 1 }), List.of(new byte[] { ColumnRenderSource.SEMANTIC_VARIANT_EXACT }),
			List.of(new long[] { BlockPos.asLong(7, 8, 9) })
		);
		LodQuadBuilder.SemanticVertexBufferBuild empty = new LodQuadBuilder.SemanticVertexBufferBuild(List.of(), List.of(), List.of(), List.of());
		ColumnRenderSource.SemanticMaterialIdentity grass =
			new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");

		DistantHorizonsSemanticCollector.recordRustSemanticBuiltColumn(
			101L, new DhBlockPos(10, 64, -20), List.of(grass),
			new LodQuadBuilder.SemanticQuadCoverage(1, 0, 0),
			LodQuadBuilder.semanticQuadCoverage(opaque, empty, empty, empty),
			opaque, empty, empty, empty
		);

		var snapshot = DistantHorizonsSemanticCollector.snapshotForTest(101L);
		assertEquals(1, snapshot.opaque().size());
		assertEquals(7, snapshot.opaque().getFirst().vertices().getFirst().localX());
		assertEquals(1, DistantHorizonsSemanticCollector.materialProvenanceForTest(101L).opaque().getFirst()[0]);
		assertEquals(1, DistantHorizonsSemanticCollector.pendingUpdateForTest().assets().size());
	}

	@Test
	void exactMaterialProvenanceIsCopiedAlongsideButOutsideTheLegacyVertexAbi() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ByteBuffer source = quadBuffer(7, 8, 9, 0xB7, 11, 12, 13, 14, 15, 16);
		ColumnRenderSource.SemanticMaterialIdentity grass =
			new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");
		LodQuadBuilder.VertexBufferBuild build = new LodQuadBuilder.VertexBufferBuild(
			List.of(source), List.of(new int[] { 1 })
		);

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			77L,
			new DhBlockPos(10, 64, -20),
			List.of(grass), build,
			new LodQuadBuilder.VertexBufferBuild(List.of(), List.of()),
			new LodQuadBuilder.VertexBufferBuild(List.of(), List.of()),
			new LodQuadBuilder.VertexBufferBuild(List.of(), List.of())
		);

		DistantHorizonsSemanticCollector.LodMaterialProvenanceSnapshot provenance =
			DistantHorizonsSemanticCollector.materialProvenanceForTest(77L);
		assertEquals(grass, provenance.semanticMaterials().get(0));
		assertEquals(1, provenance.opaque().get(0)[0]);
		assertEquals(5, DistantHorizonsSemanticCollector.snapshotForTest(77L).opaque().get(0).vertices().get(0).normalIndex());
		assertEquals(0, DistantHorizonsSemanticCollector.snapshotForTest(77L).opaque().get(0).vertices().get(0).padding());
	}

	@Test
	void identicalSemanticRebuildReusesThePublishedColumnGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ColumnRenderSource.SemanticMaterialIdentity grass =
			new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");
		LodQuadBuilder.VertexBufferBuild build = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 14, 15, 16)), List.of(new int[] { 1 })
		);
		LodQuadBuilder.VertexBufferBuild empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			77L, new DhBlockPos(10, 64, -20), List.of(grass), build, empty, empty, empty
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate first =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(first);
		DistantHorizonsSemanticCollector.acknowledgeForTest(first);
		long publishedGeneration = DistantHorizonsSemanticCollector.snapshotForTest(77L).generation();

		// New arrays and a new build object represent the normal DH rebuild path.
		// Value-identical provenance must still be treated as the same semantic asset.
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			77L,
			new DhBlockPos(10, 64, -20),
			List.of(new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains")),
			new LodQuadBuilder.VertexBufferBuild(
				List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 14, 15, 16)), List.of(new int[] { 1 })
			),
			empty, empty, empty
		);

		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		assertEquals(publishedGeneration, DistantHorizonsSemanticCollector.snapshotForTest(77L).generation());
	}

	@Test
	void captureRejectsMisalignedCpuVertexDataBeforeItCanBecomeANativeAsset() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ByteBuffer malformed = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES - 1);
		assertThrows(
			IllegalArgumentException.class,
			() -> DistantHorizonsSemanticCollector.recordBuiltColumn(
				77L,
				new DhBlockPos(0, 0, 0),
				List.of(malformed),
				List.of(),
				List.of(),
				List.of()
			)
		);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(77L));
	}

	@Test
	void semanticTransportSplitsLargeLegacyBuffersAtQuadAlignedBoundaries() {
		ByteBuffer source = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 12)
			.order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 12; vertex++) {
			source.putShort((short)vertex);
			source.putShort((short)2);
			source.putShort((short)3);
			source.putShort((short)0xB7);
			source.put((byte)11);
			source.put((byte)12);
			source.put((byte)13);
			source.put((byte)255);
			source.put((byte)15);
			source.put((byte)5);
			source.putShort((short)0);
		}
		source.flip();

		List<DistantHorizonsSemanticCollector.LodBufferSnapshot> segments =
			DistantHorizonsSemanticCollector.copyBuffersForTest(List.of(source), 8);

		assertEquals(2, segments.size());
		assertEquals(0, segments.getFirst().sourceBufferIndex());
		assertEquals(8, segments.getFirst().vertices().size());
		assertEquals(0, segments.get(1).sourceBufferIndex());
		assertEquals(4, segments.get(1).vertices().size());
		assertEquals(8, segments.get(1).vertices().getFirst().localX());
	}

	@Test
	void materialProvenanceFollowsBoundedTransportSegmentsFromOneSourceBuffer() {
		ByteBuffer source = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 12)
			.order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 12; vertex++) {
			source.putShort((short)vertex);
			source.putShort((short)2);
			source.putShort((short)3);
			source.putShort((short)0xB7);
			source.put((byte)11);
			source.put((byte)12);
			source.put((byte)13);
			source.put((byte)255);
			source.put((byte)1);
			source.put((byte)1);
			source.putShort((short)0);
		}
		source.flip();

		var snapshot = new DistantHorizonsSemanticCollector.LodColumnSnapshot(
			91L, 1L, 0, 64, 0,
			DistantHorizonsSemanticCollector.copyBuffersForTest(List.of(source), 8),
			List.of(), List.of(), List.of()
		);
		var provenance = new DistantHorizonsSemanticCollector.LodMaterialProvenanceSnapshot(
			List.of(new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains")),
			List.of(new int[] { 1, 1, 1 }), List.of(), List.of(), List.of()
		);

		var transport = snapshot.toBridgeMaterialProvenance(provenance);
		assertEquals(2, transport.segments().size());
		assertEquals(0, transport.segments().getFirst().segmentIndex());
		assertEquals(2, transport.segments().getFirst().quadMaterialIds().length);
		assertEquals(1, transport.segments().get(1).segmentIndex());
		assertEquals(1, transport.segments().get(1).quadMaterialIds().length);
	}

	@Test
	void captureRejectsIncompleteQuadsAndRetiresClosedColumns() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ByteBuffer incompleteQuad = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES)
			.order(ByteOrder.nativeOrder());
		assertThrows(
			IllegalArgumentException.class,
			() -> DistantHorizonsSemanticCollector.recordBuiltColumn(
				88L,
				new DhBlockPos(0, 0, 0),
				List.of(incompleteQuad),
				List.of(),
				List.of(),
				List.of()
			)
		);

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			89L,
			new DhBlockPos(0, 0, 0),
			List.of(quadBuffer(1, 1, 1, 0, 1, 1, 1, 255, 1, 1)),
			List.of(),
			List.of(),
			List.of()
		);
		assertEquals(1, DistantHorizonsSemanticCollector.snapshotForTest(89L).opaque().size());
		DistantHorizonsSemanticCollector.removeColumn(89L);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(89L));
	}

	@Test
	void pendingUpdatesAreCoarseAndRetireOnlyTheLastPublishedGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			0L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate first = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(1, first.assets().size());
		assertEquals(0L, first.assets().getFirst().columnKey());
		assertEquals(1, first.assets().getFirst().segments().size());
		var segment = first.assets().getFirst().segments().getFirst();
		assertTrue(segment.hasPackedVertices());
		assertEquals(0, segment.vertices().size());
		assertEquals(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 4, segment.packedVertexBytes().length);
		assertEquals(0, first.retirements().size());
		DistantHorizonsSemanticCollector.acknowledgeForTest(first);

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			0L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate replacement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(1, replacement.assets().size());
		assertEquals(2L, replacement.assets().getFirst().columnGeneration());
		assertEquals(0, replacement.retirements().size());
		DistantHorizonsSemanticCollector.acknowledgeForTest(replacement);

		DistantHorizonsSemanticCollector.removeColumn(0L);
		DistantHorizonsSemanticCollector.PendingAssetUpdate retirement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(0, retirement.assets().size());
		assertEquals(1, retirement.retirements().size());
		assertEquals(2L, retirement.retirements().getFirst().columnGeneration());
	}

	@Test
	void closedPublishedColumnCannotReenterFrameBeforePreflightRetirement() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 481036337670L;
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		var publication = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.acknowledgeForTest(publication);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey).opaqueSegments());

		DistantHorizonsSemanticCollector.removeColumn(columnKey);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(0, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey).opaqueSegments(),
			"a published descriptor awaiting retirement must not create an instance for the next Rust frame");
		var retirement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(columnKey, retirement.retirements().getFirst().columnKey());
		DistantHorizonsSemanticCollector.acknowledgeForTest(retirement);
		assertEquals(0, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey).opaqueSegments());
	}

	@Test
	void pendingAssetPublicationIsBoundedAndAcknowledgesOnlyThePublishedSlice() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		for (long columnKey = 0L; columnKey < 17L; columnKey++) {
			DistantHorizonsSemanticCollector.recordBuiltColumn(
				columnKey,
				new DhBlockPos((int)columnKey * 16, 64, 0),
				List.of(quadBuffer((int)columnKey, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
				List.of(), List.of(), List.of()
			);
		}

		DistantHorizonsSemanticCollector.PendingAssetUpdate first = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(16, first.assets().size(), "one provenance extraction transaction must stay heap-bounded");
		assertEquals(0L, first.assets().getFirst().columnKey());
		DistantHorizonsSemanticCollector.acknowledgeForTest(first);

		DistantHorizonsSemanticCollector.PendingAssetUpdate second = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(1, second.assets().size());
		assertEquals(16L, second.assets().getFirst().columnKey());
	}

	@Test
	void visibleSegmentsFollowTheActualLodBufferOrderWithoutRetainingVbos() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			77L,
			new DhBlockPos(16, 64, 32),
			List.of(ByteBuffer.allocate(0), quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(),
			List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(77L, 1, 0);
		DistantHorizonsSemanticCollector.recordVisibleSegment(77L, 1, 1);
		DistantHorizonsSemanticCollector.recordVisibleSegment(77L, 2, 0);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();

		List<net.vulkanic.bridge.VulkanicGalBridge.WorldLodColumnInstanceRecord> visible =
			DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(2, visible.size());
		assertEquals(1, visible.getFirst().layer());
		assertEquals(0, visible.getFirst().segmentIndex());
		assertEquals(0, visible.getFirst().order());
		assertEquals(2, visible.get(1).layer());
		assertEquals(1, visible.get(1).segmentIndex());
		assertEquals(1, visible.get(1).order());
		assertEquals(List.of(), DistantHorizonsSemanticCollector.consumeVisibleSegments());
		assertTrue(DistantHorizonsSemanticCollector.consumeRenderFrame().enabled());
	}

	@Test
	void visibleMaterialColumnsUseGlobalAssetSegmentIndexesAcrossLayers() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			93L,
			new DhBlockPos(0, 64, 0),
			List.of(
				quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16),
				quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)
			),
			List.of(quadBuffer(7, 8, 9, 0xD9, 31, 32, 33, 255, 35, 36)),
			List.of(),
			List.of(quadBuffer(10, 11, 12, 0xEA, 41, 42, 43, 200, 45, 46))
		);
		publishPendingForTest();

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		var segments = DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(93L);

		assertEquals(2, segments.opaqueSegments());
		assertEquals(1, segments.transparentSegments());
		assertEquals(1, segments.waterSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		List<net.vulkanic.bridge.VulkanicGalBridge.WorldLodColumnInstanceRecord> visible =
			DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(4, visible.size());
		assertEquals(93L, visible.getFirst().columnKey());
		assertEquals(1, visible.getFirst().layer());
		assertEquals(0, visible.getFirst().segmentIndex());
		assertEquals(1, visible.get(1).segmentIndex());
		assertEquals(2, visible.get(2).layer());
		assertEquals(2, visible.get(2).segmentIndex());
		assertEquals(4, visible.get(3).layer());
		assertEquals(3, visible.get(3).segmentIndex());
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(93L));
	}

	@Test
	void executedFrameRetainsTheExactConsumedSegmentsAcrossAnEmptyLaterTraversal() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			94L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(94L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		assertEquals(1, DistantHorizonsSemanticCollector.consumeVisibleSegments().size());

		DistantHorizonsSemanticCollector.recordRustOpaqueRouteExecution(42L, 99L, 7L, 1, true);
		assertEquals(1, DistantHorizonsSemanticCollector.executedVisibleSegmentsForTest(42L).size());

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(List.of(), DistantHorizonsSemanticCollector.consumeVisibleSegments());
		assertEquals(1, DistantHorizonsSemanticCollector.executedVisibleSegmentsForTest(42L).size());
		assertEquals(94L, DistantHorizonsSemanticCollector.executedVisibleSegmentsForTest(42L).getFirst().columnKey());
	}

	@Test
	void frameCompletingAfterTheWorldUnloadsDropsItsDhReceiptInsteadOfFailing() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.resetForTest();
		long columnKey = DhSectionPos.encode((byte) 6, 0, 0);
		DhBlockPos origin = new DhBlockPos(
			DhSectionPos.getMinCornerBlockX(columnKey), 64, DhSectionPos.getMinCornerBlockZ(columnKey)
		);
		var stone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:stone_STATE_", "minecraft:plains");
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		var original = new LodQuadBuilder.VertexBufferBuild(
			List.of(fourQuadBuffer()), List.of(new int[] { 1, 1, 1, 1 })
		);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, origin, List.of(stone), original, empty, empty, empty
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(columnKey);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		var submitted = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		long lifecycle = DistantHorizonsSemanticCollector.consumeVisibleFrame().lifecycle();
		int executions = DistantHorizonsSemanticCollector.routeExecutionCount();

		// Pipelined completion within the same lifecycle: recorded even though
		// the route is no longer selected by the time the frame completes.
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordRustMaterialRouteExecution(
			41L, 98L, 6L, submitted.size(), submitted.size(), 0, 0, true, submitted, lifecycle
		);
		assertEquals(executions + 1, DistantHorizonsSemanticCollector.routeExecutionCount());

		// Save and quit: the world unloads while that frame is still queued.
		DistantHorizonsSemanticCollector.clear();
		long staleBefore = DistantHorizonsSemanticCollector.staleRouteExecutionReceipts();
		DistantHorizonsSemanticCollector.recordRustMaterialRouteExecution(
			42L, 99L, 7L, submitted.size(), submitted.size(), 0, 0, true, submitted, lifecycle
		);
		assertEquals(staleBefore + 1, DistantHorizonsSemanticCollector.staleRouteExecutionReceipts());
		assertEquals(List.of(), DistantHorizonsSemanticCollector.executedVisibleSegmentsForTest(42L),
			"a receipt from an unloaded world must not be recorded against the next lifecycle");
	}

	@Test
	void executedFrameRetainsMaterialSidecarsAcrossAPostHandoffColumnReplacement() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = DhSectionPos.encode((byte) 6, 0, 0);
		DhBlockPos origin = new DhBlockPos(
			DhSectionPos.getMinCornerBlockX(columnKey), 64, DhSectionPos.getMinCornerBlockZ(columnKey)
		);
		var stone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:stone_STATE_", "minecraft:plains");
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		var original = new LodQuadBuilder.VertexBufferBuild(
			List.of(fourQuadBuffer()), List.of(new int[] { 1, 1, 1, 1 })
		);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, origin, List.of(stone), original, empty, empty, empty
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(columnKey);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		var submitted = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		DistantHorizonsSemanticCollector.recordRustMaterialRouteExecution(
			42L, 99L, 7L, submitted.size(), submitted.size(), 0, 0, true, submitted
		);

		var replacement = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(48, 48, 48, 0xB7, 1, 1, 1, 255, 1, 1)), List.of(new int[] { 1 })
		);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, origin, List.of(stone), replacement, empty, empty, empty
		);

		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey), 66, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:stone"
		));
	}

	@Test
	void capturePaletteEvidenceRequiresTheConsumedColumnThatCoversTheTarget() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long targetColumn = DhSectionPos.encode((byte) 6, 1, 2);
		long unrelatedColumn = DhSectionPos.encode((byte) 6, 4, 2);
		var grass = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block_STATE_", "minecraft:plains");
		var redstone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:redstone_ore_STATE_", "minecraft:plains");
		var terracotta = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:yellow_terracotta_STATE_", "minecraft:plains");
		var leaves = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:oak_leaves_STATE_", "minecraft:plains");
		var build = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(new int[] { 1 })
		);
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			targetColumn,
			new DhBlockPos(DhSectionPos.getMinCornerBlockX(targetColumn), 64, DhSectionPos.getMinCornerBlockZ(targetColumn)),
			List.of(grass, redstone, terracotta, leaves), build, empty, empty, empty
		);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			unrelatedColumn,
			new DhBlockPos(DhSectionPos.getMinCornerBlockX(unrelatedColumn), 64, DhSectionPos.getMinCornerBlockZ(unrelatedColumn)),
			List.of(grass, redstone, terracotta, leaves), build, empty, empty, empty
		);
		publishPendingForTest();

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(unrelatedColumn);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueColumnCoveringBlock(
			DhSectionPos.getMinCornerBlockX(targetColumn) + 4,
			DhSectionPos.getMinCornerBlockZ(targetColumn) + 4
		));
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleColumnCoveringBlockWithSemanticMaterialIdentities(
			DhSectionPos.getMinCornerBlockX(targetColumn) + 4,
			DhSectionPos.getMinCornerBlockZ(targetColumn) + 4,
			List.of("minecraft:grass_block", "minecraft:redstone_ore", "minecraft:yellow_terracotta", "minecraft:oak_leaves")
		));

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(targetColumn);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueColumnCoveringBlock(
			DhSectionPos.getMinCornerBlockX(targetColumn) + 4,
			DhSectionPos.getMinCornerBlockZ(targetColumn) + 4
		));
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleColumnCoveringBlockWithSemanticMaterialIdentities(
			DhSectionPos.getMinCornerBlockX(targetColumn) + 4,
			DhSectionPos.getMinCornerBlockZ(targetColumn) + 4,
			List.of("minecraft:grass_block", "minecraft:redstone_ore", "minecraft:yellow_terracotta", "minecraft:oak_leaves")
		));
	}

	@Test
	void capturePaletteEvidenceRequiresMaterialIdsOnTheConsumedOpaqueQuadSidecars() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = DhSectionPos.encode((byte) 6, 1, 2);
		var grass = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block_STATE_", "minecraft:plains");
		var redstone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:redstone_ore_STATE_", "minecraft:plains");
		var terracotta = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:yellow_terracotta_STATE_", "minecraft:plains");
		var leaves = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:oak_leaves_STATE_", "minecraft:plains");
		var exactBuild = new LodQuadBuilder.VertexBufferBuild(
			List.of(fourQuadBuffer()), List.of(new int[] { 1, 2, 3, 4 })
		);
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		DhBlockPos origin = new DhBlockPos(
			DhSectionPos.getMinCornerBlockX(columnKey), 64, DhSectionPos.getMinCornerBlockZ(columnKey)
		);
		List<String> palette = List.of(
			"minecraft:grass_block", "minecraft:redstone_ore", "minecraft:yellow_terracotta", "minecraft:oak_leaves"
		);

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, origin, List.of(grass, redstone, terracotta, leaves), exactBuild, empty, empty, empty
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey);
		assertTrue(DistantHorizonsSemanticCollector.hasCompleteVisibleExactAtlasCoverage(),
			"the exact material fixture must pass the same pre-submit atlas admission used by the Rust route");
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleColumnCoveringBlockWithExecutedOpaqueSemanticMaterialIdentities(
			DhSectionPos.getMinCornerBlockX(columnKey) + 4,
			DhSectionPos.getMinCornerBlockZ(columnKey) + 4,
			palette
		));
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey), DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:grass_block"
		));
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey), 66, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:grass_block"
		));
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey), 67, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:grass_block"
		));
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey) + 1, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:redstone_ore"
		));
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey) + 2, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:yellow_terracotta"
		));
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey) + 3, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:oak_leaves"
		));

		ByteBuffer unavailableVertices = fourQuadBuffer();
		unavailableVertices.put(8, (byte) 0x7f);
		var unavailableBuild = new LodQuadBuilder.VertexBufferBuild(
			List.of(unavailableVertices), List.of(new int[] { 1, 0, 3, 4 })
		);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, origin, List.of(grass, redstone, terracotta, leaves), unavailableBuild, empty, empty, empty
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		var unavailableSegments = DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey);
		assertFalse(DistantHorizonsSemanticCollector.hasCompleteVisibleExactAtlasCoverage(),
			"unavailable quad-sidecar material identity must reject the strict Rust route before submission");
		DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected(
			"incomplete-exact-atlas-coverage",
			unavailableSegments.opaqueSegments(),
			unavailableSegments.transparentSegments(),
			unavailableSegments.waterSegments()
		);
		assertEquals(List.of(), DistantHorizonsSemanticCollector.consumeVisibleSegments(),
			"a rejected exact-atlas frame must never expose its pending segments as Rust-consumed work");
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleColumnCoveringBlockWithExecutedOpaqueSemanticMaterialIdentities(
			DhSectionPos.getMinCornerBlockX(columnKey) + 4,
			DhSectionPos.getMinCornerBlockZ(columnKey) + 4,
			palette
		));
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueSemanticMaterialAtBlock(
			DhSectionPos.getMinCornerBlockX(columnKey) + 1, DhSectionPos.getMinCornerBlockZ(columnKey) + 3, "minecraft:redstone_ore"
		));
	}

	@Test
	void legacyTextureReceiptUsesObservedJavaDrawSegmentsNotRustConsumedSegments() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		var receipt = DistantHorizonsSemanticCollector.legacyTextureProbeReceipt(List.of(
			new DistantHorizonsSemanticCollector.DistantHorizonsTextureProbe(
				0, 64, 0, "minecraft:grass_block", List.of("minecraft:block/grass_block_top"), List.of()
			)
		));
		assertFalse(receipt.matched());
		assertEquals("0,0:no-spatial-observed-material", receipt.status());
		assertEquals("no-spatial-observed-material", receipt.probes().getFirst().status());
	}

	@Test
	void rustNonWaterSelectionIsAnExplicitSemanticFrameDecision() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			91L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(),
			List.of(quadBuffer(7, 8, 9, 0xD9, 31, 32, 33, 200, 35, 36))
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(91L, 1, 0);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();

		var frame = DistantHorizonsSemanticCollector.consumeRenderFrame();
		assertTrue(frame.enabled());
		assertEquals(
			DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED,
			frame.flags() & DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED
		);
		assertEquals(1, DistantHorizonsSemanticCollector.consumeVisibleSegments().size());
		var route = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals("selected", route.decision());
		assertEquals("all-visible-material-segments-supported", route.reason());
		assertEquals(1, route.opaqueSegments());
		assertEquals(0, route.transparentSegments());
		assertEquals(0, route.waterSegments());
		assertTrue(route.selected());
	}

	@Test
	void productionFrameKeepsSegmentsInRustAndReportsCountsWithoutDiagnosticRecords() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			92L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of());
		publishPendingForTest();
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(92L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		var consumed = DistantHorizonsSemanticCollector.consumeRetainedVisibleFrame();
		assertTrue(consumed.visibleSegments().isEmpty(), "ordinary frames must not construct Java records");
		assertTrue(consumed.nativeReference().id() > 0L);
		assertEquals(consumed.lifecycle(), consumed.nativeReference().lifecycle());
		assertEquals(1, consumed.nativeReference().instanceCount());
		assertEquals(1, consumed.nativeReference().opaqueCount());
		assertEquals(0, consumed.nativeReference().transparentCount());
		assertEquals(0, consumed.nativeReference().waterCount());
		var stale = DistantHorizonsSemanticCollector.consumeRetainedVisibleFrame();
		assertEquals(0L, stale.nativeReference().id());
		assertEquals(0, stale.nativeReference().instanceCount());
	}

	@Test
	void nativeFrameReadbackPreservesCaptureRecordsAndReferenceIdentity() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			93L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of());
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(93L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		var consumed = DistantHorizonsSemanticCollector.consumeRetainedVisibleFrame();
		assertEquals(1, consumed.visibleSegments().size());
		assertEquals(93L, consumed.visibleSegments().getFirst().columnKey());
		assertTrue(consumed.nativeReference().id() > 0L);
		assertEquals(consumed.visibleSegments().size(), consumed.nativeReference().instanceCount());
		assertThrows(UnsupportedOperationException.class, () -> consumed.visibleSegments().clear());
	}

	@Test
	void pairedConsumptionCannotMixVisibleSegmentsWithAConsumedOrResetRenderFrame() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			92L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(92L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();

		var consumed = DistantHorizonsSemanticCollector.consumeVisibleFrame();
		assertEquals(1, consumed.visibleSegments().size());
		assertTrue(consumed.renderFrame().enabled());
		assertNotEquals(0,
			consumed.renderFrame().flags()
				& DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_OPAQUE_ROUTE_SELECTED);

		// A later visible reference cannot reuse the old selection after its
		// render-frame record has already been consumed.
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(92L);
		var stale = DistantHorizonsSemanticCollector.consumeVisibleFrame();
		assertEquals(List.of(), stale.visibleSegments());
		assertFalse(stale.renderFrame().enabled());
	}

	@Test
	void rejectedNonWaterRoutePreservesOpaqueTransparentAndWaterTotals() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected(
			"visible-water-segments", 2, 3, 4
		);

		var route = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals("rejected", route.decision());
		assertEquals("visible-water-segments", route.reason());
		assertEquals(2, route.opaqueSegments());
		assertEquals(3, route.transparentSegments());
		assertEquals(4, route.waterSegments());
		assertFalse(route.selected());
	}

	@Test
	void successfulOpaqueRouteExecutionRetainsCaptureCorrelationAfterFrameConsumption() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			94L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(94L, 1, 0);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		assertTrue(DistantHorizonsSemanticCollector.consumeRenderFrame().enabled());

		DistantHorizonsSemanticCollector.recordRustOpaqueRouteExecution(42L, 99L, 7L, 1, true);

		var route = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals(42L, route.lastExecutedWorldFrame());
		assertEquals(99L, route.lastExecutedSubmission());
		assertEquals(7L, route.lastExecutedCaptureFrame());
		assertEquals(1, route.lastExecutedInstances());
		assertEquals(1, route.lastExecutedOpaqueInstances());
		assertEquals(0, route.lastExecutedTransparentInstances());
		assertEquals(0, route.lastExecutedWaterInstances());
		assertTrue(route.lastExecutedFrameSemanticsEnabled());
		assertFalse(route.frameSemanticsEnabled());
	}

	@Test
	void waterReceiptRequiresTheExecutedWaterQuadToCoverTheFixtureCell() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = 95L;
		DhBlockPos origin = new DhBlockPos(10, 64, 20);
		var water = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:water_STATE_", "minecraft:plains");
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		var waterBuild = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(2, 3, 4, 0xB7, 1, 2, 3, 255, 1, 1)), List.of(new int[] { 1 })
		);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			columnKey, origin, List.of(water), empty, empty, empty, waterBuild
		);
		publishPendingForTest();
		// The fixture identifies the water cell; its horizontal water-up quad is
		// emitted on the cell's top plane at y + 1.
		var cachedReceipt = DistantHorizonsSemanticCollector.waterCachedProbeReceipt(List.of(new BlockPos(12, 66, 24)));
		assertTrue(cachedReceipt.matched());
		assertEquals("minecraft:water_STATE_", cachedReceipt.probes().getFirst().materialIdentity());
		var sourceReceipt = DistantHorizonsSemanticCollector.waterSourceProbeReceipt(List.of(new BlockPos(12, 66, 24)));
		assertTrue(sourceReceipt.matched());
		assertEquals(0L, sourceReceipt.executedWorldFrame());
		assertEquals("minecraft:water_STATE_", sourceReceipt.probes().getFirst().materialIdentity());
		assertFalse(DistantHorizonsSemanticCollector.waterProbeReceipt(List.of(new BlockPos(12, 66, 24))).matched(),
			"published source evidence must not masquerade as completed Rust execution");
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey).waterSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertTrue(DistantHorizonsSemanticCollector.consumeRenderFrame().enabled());
		var submitted = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		DistantHorizonsSemanticCollector.recordRustMaterialRouteExecution(
			42L, 99L, 7L, 1, 0, 0, 1, true, submitted
		);

		var receipt = DistantHorizonsSemanticCollector.waterProbeReceipt(List.of(new BlockPos(12, 66, 24)));
		assertTrue(receipt.matched());
		assertEquals(42L, receipt.executedWorldFrame());
		assertEquals("minecraft:water_STATE_", receipt.probes().getFirst().materialIdentity());
		assertEquals(0, receipt.probes().getFirst().segmentIndex());

		assertFalse(DistantHorizonsSemanticCollector.waterProbeReceipt(List.of(new BlockPos(12, 68, 24))).matched());
	}

	@Test
	void rustMaterialSelectionAdmitsSideTransparencyAndWaterBeforeSubmission() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			92L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(),
			List.of(quadBuffer(7, 8, 9, 0xD9, 31, 32, 33, 200, 35, 36))
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(92L, 1, 0);
		DistantHorizonsSemanticCollector.recordVisibleSegment(92L, 2, 0);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		var admitted = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(2, admitted.size());
		assertEquals(2, admitted.get(1).layer());
		assertEquals(1, admitted.get(1).segmentIndex());
		var admittedRoute = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertTrue(admittedRoute.selected());
		assertEquals(1, admittedRoute.opaqueSegments());
		assertEquals(1, admittedRoute.transparentSegments());

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(92L, 4, 0);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		var waterFrame = DistantHorizonsSemanticCollector.consumeRenderFrame();
		assertTrue(waterFrame.enabled());
		assertEquals(1, DistantHorizonsSemanticCollector.consumeVisibleSegments().size());
		var waterRoute = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertTrue(waterRoute.selected());
		assertEquals(1, waterRoute.waterSegments());
	}

	@Test
	void visibleColumnsWaitForAnAcknowledgedAssetGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			101L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);

		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(0, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		assertTrue(DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns());

		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		assertFalse(DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns());

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			101L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments(),
			"a newer build must not create an empty frame while generation one is still acknowledged");
		assertFalse(DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		assertEquals(1L, DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration());

		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		List<net.vulkanic.bridge.VulkanicGalBridge.WorldLodColumnInstanceRecord> visible =
			DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(2L, visible.getFirst().columnGeneration());
	}

	@Test
	void selectedReplacementKeepsCoverageUntilTheVisibleFrameHasBeenConsumed() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(213L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of());
		publishPendingForTest();
		long selectedGeneration = DistantHorizonsSemanticCollector.snapshotForTest(213L).generation();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(213L);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();

		// This replacement changes stream topology as well as generation. Merely
		// changing a generation on the old segment indices would be incorrect.
		DistantHorizonsSemanticCollector.recordBuiltColumn(213L, new DhBlockPos(0, 64, 0),
			List.of(), List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(), List.of(quadBuffer(7, 8, 9, 0xD9, 31, 32, 33, 255, 35, 36)));
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest(),
			"preflight must not replace an asset already selected for this frame");
		var frozen = DistantHorizonsSemanticCollector.consumeVisibleFrame();
		assertEquals(1, frozen.visibleSegments().size());
		assertEquals(selectedGeneration, frozen.visibleSegments().getFirst().columnGeneration());
		assertEquals(1, frozen.visibleSegments().getFirst().layer());
		assertNotEquals(0, frozen.renderFrame().flags()
			& DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED);

		// The coordinator flushes again only after presenting the frozen frame.
		publishPendingForTest();
		long replacementGeneration = DistantHorizonsSemanticCollector.snapshotForTest(213L).generation();
		assertNotEquals(selectedGeneration, replacementGeneration);
		assertEquals(selectedGeneration, frozen.visibleSegments().getFirst().columnGeneration());
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		var counts = DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(213L);
		assertEquals(0, counts.opaqueSegments());
		assertEquals(1, counts.transparentSegments());
		assertEquals(1, counts.waterSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		var next = DistantHorizonsSemanticCollector.consumeVisibleFrame();
		assertEquals(List.of(2, 4), next.visibleSegments().stream().map(i -> i.layer()).toList());
		assertEquals(List.of(0, 1), next.visibleSegments().stream().map(i -> i.segmentIndex()).toList());
		assertTrue(next.visibleSegments().stream().allMatch(i -> i.columnGeneration() == replacementGeneration));
	}

	@Test
	void protectingASelectedColumnStillAdmitsUnrelatedColumnsAndKeepsTheLatestRebuild() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		buildSemanticContainer(214L, 0xff557733);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(214L);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		buildSemanticContainer(214L, 0xff885533);
		buildSemanticContainer(215L, 0xff337755);
		var independent = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(independent);
		assertEquals(List.of(215L), independent.assets().stream().map(a -> a.columnKey()).toList(),
			"a protected rebuild must not block unrelated publication");
		DistantHorizonsSemanticCollector.acknowledgeForTest(independent);
		buildSemanticContainer(214L, 0xff776622);
		long latestGeneration = DistantHorizonsSemanticCollector.snapshotForTest(214L).generation();
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
		assertEquals(1, DistantHorizonsSemanticCollector.consumeVisibleFrame().visibleSegments().size());
		var latest = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(latest);
		assertEquals(List.of(latestGeneration), latest.assets().stream().map(a -> a.columnGeneration()).toList());
		DistantHorizonsSemanticCollector.acknowledgeForTest(latest);
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());
	}

	@Test
	void assetReplacementPrunesVisibleReferencesFromThePreviousGenerationBeforeSubmit() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			211L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			211L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(), List.of(), List.of()
		);
		// Exercise the defensive late-ack path separately: this update was
		// already selected before a visibility traversal referenced the old asset.
		var inFlight = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(211L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		DistantHorizonsSemanticCollector.acknowledgeForTest(inFlight);

		assertEquals(List.of(), DistantHorizonsSemanticCollector.consumeVisibleSegments());
		assertEquals(0, DistantHorizonsSemanticCollector.consumeRenderFrame().flags()
			& DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED);
		var route = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertEquals("asset-generation-advanced-before-submit", route.reason());
		assertFalse(route.selected());

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(211L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		assertEquals(2L, DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration());
	}

	@Test
	void pendingAssetReplacementKeepsTheAcknowledgedVisibleGenerationUntilTheNextFrameBoundary() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			212L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(212L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			212L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(), List.of(), List.of()
		);

		assertEquals(1, DistantHorizonsSemanticCollector.consumeVisibleSegments().size());
		assertNotEquals(0, DistantHorizonsSemanticCollector.consumeRenderFrame().flags()
			& DistantHorizonsSemanticCollector.RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED);
		var route = DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		assertTrue(route.selected());
	}

	@Test
	void unchangedRebuildKeepsThePublishedGenerationVisible() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ByteBuffer first = quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16);
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			111L, new DhBlockPos(0, 64, 0), List.of(first), List.of(), List.of(), List.of()
		);
		publishPendingForTest();
		assertEquals(1L, DistantHorizonsSemanticCollector.snapshotForTest(111L).generation());

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			111L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		assertEquals(1L, DistantHorizonsSemanticCollector.snapshotForTest(111L).generation());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest());

		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(111L).opaqueSegments());
		assertFalse(DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns());
	}

	@Test
	void provenanceOnlyRebuildPublishesANewerGenerationForRust() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		var grass = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");
		var stone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:stone", "minecraft:plains");
		var opaque = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(new int[] {1})
		);
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());

		DistantHorizonsSemanticCollector.recordBuiltColumn(113L, new DhBlockPos(0, 64, 0),
			List.of(grass), opaque, empty, empty, empty);
		publishPendingForTest();
		assertEquals(1L, DistantHorizonsSemanticCollector.snapshotForTest(113L).generation());

		DistantHorizonsSemanticCollector.recordBuiltColumn(113L, new DhBlockPos(0, 64, 0),
			List.of(stone), opaque, empty, empty, empty);
		assertEquals(2L, DistantHorizonsSemanticCollector.snapshotForTest(113L).generation());
		assertEquals(2L, DistantHorizonsSemanticCollector.pendingUpdateForTest().assets().getFirst().columnGeneration());
	}

	@Test
	void identicalCopiedPrimitiveSidecarsReuseThePublishedGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		var stone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:stone", "minecraft:plains");
		var opaque = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(new int[] {1})
		);
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());

		DistantHorizonsSemanticCollector.recordBuiltColumn(114L, new DhBlockPos(0, 64, 0),
			List.of(stone), opaque, empty, empty, empty);
		publishPendingForTest();
		assertEquals(1L, DistantHorizonsSemanticCollector.snapshotForTest(114L).generation());

		// Rebuild all builder-side arrays so this exercises value equality rather
		// than reusing the same Java array instances.
		DistantHorizonsSemanticCollector.recordBuiltColumn(114L, new DhBlockPos(0, 64, 0),
			List.of(new ColumnRenderSource.SemanticMaterialIdentity("minecraft:stone", "minecraft:plains")),
			new LodQuadBuilder.VertexBufferBuild(
				List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(new int[] {1})
			),
			empty, empty, empty);

		assertEquals(1L, DistantHorizonsSemanticCollector.snapshotForTest(114L).generation());
		assertNull(DistantHorizonsSemanticCollector.pendingUpdateForTest(),
			"identical semantic sidecars must not churn a Rust asset generation");
	}

	@Test
	void columnCoverageReportsTheFirstChangedSemanticVertexField() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			0L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			0L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xC8, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);

		var coverage = DistantHorizonsSemanticCollector.columnCoverageDiagnosticsAtBlock(0, 0);
		assertEquals(1, coverage.cachedColumns());
		assertTrue(coverage.samples().getFirst().contains("opaque[0].vertex[0].packed-light-micro=183->200"));
	}

	@Test
	void normalizesDhRowMajorMatricesForTheColumnMajorSemanticAbi() {
		float[] rowMajor = {
			1, 2, 3, 4,
			5, 6, 7, 8,
			9, 10, 11, 12,
			13, 14, 15, 16
		};

		assertArrayEquals(new float[] {
			1, 5, 9, 13,
			2, 6, 10, 14,
			3, 7, 11, 15,
			4, 8, 12, 16
		}, DistantHorizonsSemanticCollector.rowMajorToColumnMajor(rowMajor));
		assertThrows(IllegalArgumentException.class,
			() -> DistantHorizonsSemanticCollector.rowMajorToColumnMajor(new float[15]));
	}

	private static void publishPendingForTest() {
		DistantHorizonsSemanticCollector.PendingAssetUpdate update =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
	}

	private static ByteBuffer quadBuffer(
		int x,
		int y,
		int z,
		int metadata,
		int red,
		int green,
		int blue,
		int alpha,
		int materialId,
		int normalIndex
	) {
		// These fixtures intentionally use varied legacy values at callsites, but
		// the Rust semantic ABI admits material IDs 0..15 and face normals 0..5.
		// Keep the production validator strict while ensuring every fixture models
		// a value that can actually cross the Rust boundary.
		materialId = Math.min(Math.max(materialId, 0), 15);
		normalIndex = Math.min(Math.max(normalIndex, 0), 5);
		ByteBuffer buffer = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 4).order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 4; vertex++) {
			buffer.putShort((short)x);
			buffer.putShort((short)y);
			buffer.putShort((short)z);
			buffer.putShort((short)metadata);
			buffer.put((byte)red);
			buffer.put((byte)green);
			buffer.put((byte)blue);
			buffer.put((byte)alpha);
			buffer.put((byte)materialId);
			buffer.put((byte)normalIndex);
			buffer.putShort((short)0);
		}
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer twoQuadBuffer(int x) {
		ByteBuffer first = quadBuffer(x, 2, 3, 0xB7, 1, 1, 1, 255, 1, 1);
		ByteBuffer second = quadBuffer(x + 1, 2, 3, 0xB7, 1, 1, 1, 255, 1, 1);
		ByteBuffer combined = ByteBuffer.allocate(first.remaining() + second.remaining()).order(ByteOrder.nativeOrder());
		combined.put(first).put(second).flip();
		return combined;
	}

	private static ByteBuffer fourQuadBuffer() {
		ByteBuffer combined = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 16)
			.order(ByteOrder.nativeOrder());
		for (int quad = 0; quad < 4; quad++) {
			for (int[] corner : new int[][] {
				{quad, 2, 3}, {quad + 1, 2, 3}, {quad + 1, 2, 4}, {quad, 2, 4}
			}) {
				combined.putShort((short)corner[0]);
				combined.putShort((short)corner[1]);
				combined.putShort((short)corner[2]);
				combined.putShort((short)0xB7);
				combined.put((byte)1).put((byte)1).put((byte)1).put((byte)255);
				combined.put((byte)1).put((byte)1).putShort((short)0);
			}
		}
		return combined.flip();
	}
}
