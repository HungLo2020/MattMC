package net.vulkanic.world;

import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
	@AfterEach
	void resetCollector() {
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		System.clearProperty(DistantHorizonsSemanticCollector.LEGACY_OBSERVATION_PROPERTY);
		DistantHorizonsSemanticCollector.resetForTest();
	}

	@Test
	void captureIsDisabledWithoutTheExplicitPrivateSwitch() {
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			9L,
			new DhBlockPos(1, 2, 3),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 14, 15, 16)),
			List.of(),
			List.of(),
			List.of()
		);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(9L));
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
		assertEquals(16, vertex.normalIndex());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.opaque().add(null));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.opaque().getFirst().vertices().add(vertex));
	}

	@Test
	void legacyObservationRetainsOnlyCopiedSnapshotsAfterLegacyVboRetirement() {
		System.setProperty(DistantHorizonsSemanticCollector.LEGACY_OBSERVATION_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			58L,
			new DhBlockPos(16, 64, -32),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(),
			List.of(),
			List.of()
		);

		var pendingUpdate = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertTrue(pendingUpdate == null || pendingUpdate.assets().isEmpty(),
			"legacy observation must not create a Rust asset update");
		DistantHorizonsSemanticCollector.removeColumn(58L);
		assertEquals(16, DistantHorizonsSemanticCollector.snapshotForTest(58L).originX());
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(58L, 1, 0);
		assertTrue(DistantHorizonsSemanticCollector.hasObservedVisibleOpaqueColumnCoveringBlock(17, -29));
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
		assertEquals(16, DistantHorizonsSemanticCollector.snapshotForTest(77L).opaque().get(0).vertices().get(0).normalIndex());
		assertEquals(0, DistantHorizonsSemanticCollector.snapshotForTest(77L).opaque().get(0).vertices().get(0).padding());
	}

	@Test
	void materialProvenanceParticipatesInTheExistingBoundedColumnRetention() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ColumnRenderSource.SemanticMaterialIdentity grass =
			new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");
		LodQuadBuilder.VertexBufferBuild first = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 14, 15, 16)), List.of(new int[] { 1 })
		);
		LodQuadBuilder.VertexBufferBuild second = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(4, 5, 6, 0xB7, 21, 22, 23, 24, 25, 26)), List.of(new int[] { 1 })
		);
		LodQuadBuilder.VertexBufferBuild empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());

		DistantHorizonsSemanticCollector.recordBuiltColumn(701L, new DhBlockPos(0, 64, 0), List.of(grass), first, empty, empty, empty);
		DistantHorizonsSemanticCollector.recordBuiltColumn(702L, new DhBlockPos(16, 64, 0), List.of(grass), second, empty, empty, empty);

		// Two legacy vertex buffers fit in 160 bytes; the copied semantic sidecars
		// must still be counted, so the LRU oldest column is retired.
		DistantHorizonsSemanticCollector.trimRetainedColumnsForTest(8, 160L);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(701L));
		assertNull(DistantHorizonsSemanticCollector.materialProvenanceForTest(701L));
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(702L));
		assertEquals(grass, DistantHorizonsSemanticCollector.materialProvenanceForTest(702L).semanticMaterials().getFirst());
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
	void retentionKeepsOneOversizedColumnUntilTheQuadtreeCanConsumeIt() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			100L,
			new DhBlockPos(0, 64, 0),
			List.of(twoQuadBuffer(1)), List.of(), List.of(), List.of()
		);

		DistantHorizonsSemanticCollector.trimRetainedColumnsForTest(8, 64L);
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(100L));

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			101L,
			new DhBlockPos(16, 64, 0),
			List.of(quadBuffer(2, 2, 2, 0xB7, 1, 1, 1, 255, 1, 1)), List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.trimRetainedColumnsForTest(8, 64L);
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(100L));
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(101L));
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
		assertEquals(4, first.assets().getFirst().segments().getFirst().vertices().size());
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
		assertEquals(4, first.assets().size());
		assertEquals(0L, first.assets().getFirst().columnKey());
		assertEquals(3L, first.assets().getLast().columnKey());
		DistantHorizonsSemanticCollector.acknowledgeForTest(first);

		DistantHorizonsSemanticCollector.PendingAssetUpdate second = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(4, second.assets().size());
		assertEquals(4L, second.assets().getFirst().columnKey());
	}

	@Test
	void visibleUnpublishedColumnIsPublishedBeforeOlderUnrelatedPendingColumns() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		for (long columnKey = 0L; columnKey < 17L; columnKey++) {
			DistantHorizonsSemanticCollector.recordBuiltColumn(
				columnKey,
				new DhBlockPos((int)columnKey * 16, 64, 0),
				List.of(quadBuffer((int)columnKey, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
				List.of(), List.of(), List.of()
			);
		}

		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(0, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(16L).opaqueSegments());
		// Publication can run after DH begins its next visibility traversal; the
		// real visible demand must survive that frame boundary until acknowledged.
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();

		DistantHorizonsSemanticCollector.PendingAssetUpdate update = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(1, update.assets().size(),
			"visible-demand uploads must not spend the bounded slice on unrelated background columns");
		assertEquals(16L, update.assets().getFirst().columnKey(),
			"the actual visible column must not be starved behind build-order backlog");
		assertFalse(update.assets().stream().anyMatch(asset -> asset.columnKey() == 15L),
			"the bounded update must defer an unrelated pending column instead");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			16L,
			new DhBlockPos(16 * 16, 64, 0),
			List.of(quadBuffer(99, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(), List.of(), List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate concurrent = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertTrue(concurrent == null || concurrent.assets().stream().noneMatch(asset -> asset.columnKey() == 16L),
			"a live asset update must reserve its column until acknowledgement, even when a newer build arrives");
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(16L).opaqueSegments(),
			"the acknowledged asset remains drawable while its replacement is pending");
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertEquals(update.assets().getFirst().columnGeneration(),
			DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration(),
			"visibility must retain the coherent acknowledged generation until replacement acknowledgement");
		DistantHorizonsSemanticCollector.PendingAssetUpdate replacement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(16L, replacement.assets().getFirst().columnKey(),
			"a newer visible generation must retain publication priority after its older generation is acknowledged");
		DistantHorizonsSemanticCollector.acknowledgeForTest(replacement);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(16L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertEquals(replacement.assets().getFirst().columnGeneration(),
			DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration(),
			"visibility switches atomically to the acknowledged replacement generation");
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
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments(),
			"a newer build must not create an empty frame while generation one is still acknowledged");
		assertFalse(DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns());

		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		List<net.vulkanic.bridge.VulkanicGalBridge.WorldLodColumnInstanceRecord> visible =
			DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(2L, visible.getFirst().columnGeneration());
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
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(211L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();

		DistantHorizonsSemanticCollector.recordBuiltColumn(
			211L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(), List.of(), List.of()
		);
		publishPendingForTest();

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
