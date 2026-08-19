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
		float[] projection = DistantHorizonsSemanticCollector.rowMajorToColumnMajor(new float[] {
			0.70715946F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.25717247F, 0.0F, 0.0F,
			0.0F, 0.0F, -1.00639319F, -7.36495113F,
			0.0F, 0.0F, -1.0F, 0.0F
		});
		float[] inverse = DistantHorizonsSemanticCollector.invertColumnMajorMatrix(projection);
		assertNotNull(inverse);
		assertTrue(DistantHorizonsSemanticCollector.matrixInverseResidual(projection, inverse) <= 0.001F);
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
		DistantHorizonsSemanticCollector.recordBuiltColumn(44L, new DhBlockPos(10, 64, -20), List.of(source), List.of(), List.of(), List.of());
		source.putShort(0, (short) 99);
		var snapshot = DistantHorizonsSemanticCollector.snapshotForTest(44L);
		assertEquals(10, snapshot.originX());
		assertEquals(64, snapshot.originY());
		assertEquals(-20, snapshot.originZ());
		assertEquals(1, snapshot.opaque().size());
		var vertex = snapshot.opaque().getFirst().vertices().getFirst();
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
			58L, new DhBlockPos(16, 64, -32),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of()
		);
		var pendingUpdate = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertTrue(pendingUpdate == null || pendingUpdate.assets().isEmpty());
		DistantHorizonsSemanticCollector.removeColumn(58L);
		assertEquals(16, DistantHorizonsSemanticCollector.snapshotForTest(58L).originX());
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(58L, 1, 0);
		assertTrue(DistantHorizonsSemanticCollector.hasObservedVisibleOpaqueColumnCoveringBlock(17, -29));
	}

	@Test
	void exactMaterialProvenanceIsCopiedAlongsideButOutsideTheLegacyVertexAbi() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		var grass = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");
		var build = new LodQuadBuilder.VertexBufferBuild(
			List.of(quadBuffer(7, 8, 9, 0xB7, 11, 12, 13, 14, 15, 16)), List.of(new int[] { 1 })
		);
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		DistantHorizonsSemanticCollector.recordBuiltColumn(77L, new DhBlockPos(10, 64, -20), List.of(grass), build, empty, empty, empty);
		var provenance = DistantHorizonsSemanticCollector.materialProvenanceForTest(77L);
		assertEquals(grass, provenance.semanticMaterials().getFirst());
		assertEquals(1, provenance.opaque().getFirst()[0]);
		assertEquals(16, DistantHorizonsSemanticCollector.snapshotForTest(77L).opaque().getFirst().vertices().getFirst().normalIndex());
	}

	@Test
	void materialProvenanceParticipatesInTheExistingBoundedColumnRetention() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		var grass = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block", "minecraft:plains");
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		var first = new LodQuadBuilder.VertexBufferBuild(List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 14, 15, 16)), List.of(new int[] { 1 }));
		var second = new LodQuadBuilder.VertexBufferBuild(List.of(quadBuffer(4, 5, 6, 0xB7, 21, 22, 23, 24, 25, 26)), List.of(new int[] { 1 }));
		DistantHorizonsSemanticCollector.recordBuiltColumn(701L, new DhBlockPos(0, 64, 0), List.of(grass), first, empty, empty, empty);
		DistantHorizonsSemanticCollector.recordBuiltColumn(702L, new DhBlockPos(16, 64, 0), List.of(grass), second, empty, empty, empty);
		DistantHorizonsSemanticCollector.trimRetainedColumnsForTest(8, 160L);
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(701L));
		assertNull(DistantHorizonsSemanticCollector.materialProvenanceForTest(701L));
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(702L));
	}

	@Test
	void captureRejectsMisalignedCpuVertexDataBeforeItCanBecomeANativeAsset() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		ByteBuffer malformed = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES - 1);
		assertThrows(IllegalArgumentException.class, () -> DistantHorizonsSemanticCollector.recordBuiltColumn(
			77L, new DhBlockPos(0, 0, 0), List.of(malformed), List.of(), List.of(), List.of()
		));
		assertNull(DistantHorizonsSemanticCollector.snapshotForTest(77L));
	}

	@Test
	void semanticTransportSplitsLargeLegacyBuffersAtQuadAlignedBoundaries() {
		ByteBuffer source = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 12).order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 12; vertex++) {
			source.putShort((short)vertex).putShort((short)2).putShort((short)3).putShort((short)0xB7);
			source.put((byte)11).put((byte)12).put((byte)13).put((byte)255).put((byte)15).put((byte)5).putShort((short)0);
		}
		source.flip();
		var segments = DistantHorizonsSemanticCollector.copyBuffersForTest(List.of(source), 8);
		assertEquals(2, segments.size());
		assertEquals(8, segments.getFirst().vertices().size());
		assertEquals(4, segments.get(1).vertices().size());
	}

	@Test
	void pendingAssetPublicationIsBoundedAndAcknowledgesOnlyThePublishedSlice() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		for (long columnKey = 0L; columnKey < 17L; columnKey++) {
			DistantHorizonsSemanticCollector.recordBuiltColumn(columnKey, new DhBlockPos((int)columnKey * 16, 64, 0),
				List.of(quadBuffer((int)columnKey, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of());
		}
		var first = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(16, first.assets().size());
		DistantHorizonsSemanticCollector.acknowledgeForTest(first);
		var second = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(1, second.assets().size());
		assertEquals(16L, second.assets().getFirst().columnKey());
	}

	@Test
	void visibleUnpublishedColumnIsPublishedBeforeOlderUnrelatedPendingColumns() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		for (long columnKey = 0L; columnKey < 17L; columnKey++) {
			DistantHorizonsSemanticCollector.recordBuiltColumn(columnKey, new DhBlockPos((int)columnKey * 16, 64, 0),
				List.of(quadBuffer((int)columnKey, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of());
		}
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(0, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(16L).opaqueSegments());
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		var update = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(16L, update.assets().getFirst().columnKey());
		DistantHorizonsSemanticCollector.recordBuiltColumn(16L, new DhBlockPos(256, 64, 0),
			List.of(quadBuffer(99, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of());
		assertFalse(DistantHorizonsSemanticCollector.pendingUpdateForTest().assets().stream().anyMatch(asset -> asset.columnKey() == 16L));
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(16L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertEquals(update.assets().getFirst().columnGeneration(), DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration());

		var replacement = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertEquals(16L, replacement.assets().getFirst().columnKey());
		DistantHorizonsSemanticCollector.acknowledgeForTest(replacement);
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(16L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertEquals(replacement.assets().getFirst().columnGeneration(), DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration());
	}

	@Test
	void visibleSegmentsFollowTheActualLodBufferOrderWithoutRetainingVbos() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(77L, new DhBlockPos(16, 64, 32),
			List.of(ByteBuffer.allocate(0), quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)),
			List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)), List.of(), List.of());
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(77L, 1, 0);
		DistantHorizonsSemanticCollector.recordVisibleSegment(77L, 1, 1);
		DistantHorizonsSemanticCollector.recordVisibleSegment(77L, 2, 0);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		var visible = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(2, visible.size());
		assertEquals(1, visible.getFirst().layer());
		assertEquals(0, visible.getFirst().segmentIndex());
		assertEquals(2, visible.get(1).layer());
		assertEquals(1, visible.get(1).segmentIndex());
		assertTrue(DistantHorizonsSemanticCollector.consumeRenderFrame().enabled());
	}

	@Test
	void visibleMaterialColumnsUseGlobalAssetSegmentIndexesAcrossLayers() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(93L, new DhBlockPos(0, 64, 0),
			List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16), quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)),
			List.of(quadBuffer(7, 8, 9, 0xD9, 31, 32, 33, 255, 35, 36)), List.of(),
			List.of(quadBuffer(10, 11, 12, 0xEA, 41, 42, 43, 200, 45, 46)));
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		var segments = DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(93L);
		assertEquals(2, segments.opaqueSegments());
		assertEquals(1, segments.transparentSegments());
		assertEquals(1, segments.waterSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		var visible = DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertEquals(4, visible.size());
		assertEquals(0, visible.getFirst().segmentIndex());
		assertEquals(1, visible.get(1).segmentIndex());
		assertEquals(2, visible.get(2).segmentIndex());
		assertEquals(3, visible.get(3).segmentIndex());
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
		var build = new LodQuadBuilder.VertexBufferBuild(List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(new int[] { 1 }));
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		DistantHorizonsSemanticCollector.recordBuiltColumn(targetColumn, new DhBlockPos(DhSectionPos.getMinCornerBlockX(targetColumn), 64, DhSectionPos.getMinCornerBlockZ(targetColumn)), List.of(grass, redstone, terracotta, leaves), build, empty, empty, empty);
		DistantHorizonsSemanticCollector.recordBuiltColumn(unrelatedColumn, new DhBlockPos(DhSectionPos.getMinCornerBlockX(unrelatedColumn), 64, DhSectionPos.getMinCornerBlockZ(unrelatedColumn)), List.of(grass, redstone, terracotta, leaves), build, empty, empty, empty);
		publishPendingForTest();

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(unrelatedColumn);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueColumnCoveringBlock(DhSectionPos.getMinCornerBlockX(targetColumn) + 4, DhSectionPos.getMinCornerBlockZ(targetColumn) + 4));

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(targetColumn);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleOpaqueColumnCoveringBlock(DhSectionPos.getMinCornerBlockX(targetColumn) + 4, DhSectionPos.getMinCornerBlockZ(targetColumn) + 4));
	}

	@Test
	void capturePaletteEvidenceRequiresMaterialIdsOnTheConsumedOpaqueQuadSidecars() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		long columnKey = DhSectionPos.encode((byte) 6, 1, 2);
		var grass = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:grass_block_STATE_", "minecraft:plains");
		var redstone = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:redstone_ore_STATE_", "minecraft:plains");
		var terracotta = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:yellow_terracotta_STATE_", "minecraft:plains");
		var leaves = new ColumnRenderSource.SemanticMaterialIdentity("minecraft:oak_leaves_STATE_", "minecraft:plains");
		var empty = new LodQuadBuilder.VertexBufferBuild(List.of(), List.of());
		DhBlockPos origin = new DhBlockPos(DhSectionPos.getMinCornerBlockX(columnKey), 64, DhSectionPos.getMinCornerBlockZ(columnKey));
		List<String> palette = List.of("minecraft:grass_block", "minecraft:redstone_ore", "minecraft:yellow_terracotta", "minecraft:oak_leaves");
		var exactBuild = new LodQuadBuilder.VertexBufferBuild(List.of(fourQuadBuffer()), List.of(new int[] { 1, 2, 3, 4 }));
		DistantHorizonsSemanticCollector.recordBuiltColumn(columnKey, origin, List.of(grass, redstone, terracotta, leaves), exactBuild, empty, empty, empty);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey);
		assertTrue(DistantHorizonsSemanticCollector.hasCompleteVisibleExactAtlasCoverage());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		DistantHorizonsSemanticCollector.consumeVisibleSegments();
		assertTrue(DistantHorizonsSemanticCollector.hasLastConsumedVisibleColumnCoveringBlockWithExecutedOpaqueSemanticMaterialIdentities(DhSectionPos.getMinCornerBlockX(columnKey) + 4, DhSectionPos.getMinCornerBlockZ(columnKey) + 4, palette));

		ByteBuffer unavailableVertices = fourQuadBuffer();
		unavailableVertices.put(8, (byte) 0x7f);
		var unavailableBuild = new LodQuadBuilder.VertexBufferBuild(List.of(unavailableVertices), List.of(new int[] { 1, 0, 3, 4 }));
		DistantHorizonsSemanticCollector.recordBuiltColumn(columnKey, origin, List.of(grass, redstone, terracotta, leaves), unavailableBuild, empty, empty, empty);
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey);
		assertFalse(DistantHorizonsSemanticCollector.hasCompleteVisibleExactAtlasCoverage());
		DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("incomplete-exact-atlas-coverage", 1, 0, 0);
		assertEquals(List.of(), DistantHorizonsSemanticCollector.consumeVisibleSegments());
		assertFalse(DistantHorizonsSemanticCollector.hasLastConsumedVisibleColumnCoveringBlockWithExecutedOpaqueSemanticMaterialIdentities(DhSectionPos.getMinCornerBlockX(columnKey) + 4, DhSectionPos.getMinCornerBlockZ(columnKey) + 4, palette));
	}

	@Test
	void visibleColumnsWaitForAnAcknowledgedAssetGeneration() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(101L, new DhBlockPos(0, 64, 0), List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of());
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(0, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		assertTrue(DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns());
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginVisibleFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		DistantHorizonsSemanticCollector.recordBuiltColumn(101L, new DhBlockPos(0, 64, 0), List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)), List.of(), List.of(), List.of());
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(101L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertEquals(2L, DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration());
	}

	@Test
	void assetReplacementPrunesVisibleReferencesFromThePreviousGenerationBeforeSubmit() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(211L, new DhBlockPos(0, 64, 0), List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of());
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(211L);
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		DistantHorizonsSemanticCollector.recordBuiltColumn(211L, new DhBlockPos(0, 64, 0), List.of(quadBuffer(4, 5, 6, 0xC8, 21, 22, 23, 255, 25, 26)), List.of(), List.of(), List.of());
		publishPendingForTest();
		assertEquals(List.of(), DistantHorizonsSemanticCollector.consumeVisibleSegments());
		assertFalse(DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot().selected());
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(211L).opaqueSegments());
		DistantHorizonsSemanticCollector.markRustOpaqueRouteSelected();
		assertEquals(2L, DistantHorizonsSemanticCollector.consumeVisibleSegments().getFirst().columnGeneration());
	}

	@Test
	void rustNonWaterSelectionIsAnExplicitSemanticFrameDecision() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(91L, new DhBlockPos(0, 64, 0), List.of(quadBuffer(1, 2, 3, 0xB7, 11, 12, 13, 255, 15, 16)), List.of(), List.of(), List.of());
		publishPendingForTest();
		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		DistantHorizonsSemanticCollector.recordVisibleSegment(91L, 1, 0);
		DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
		assertTrue(DistantHorizonsSemanticCollector.consumeRenderFrame().enabled());
		assertEquals(1, DistantHorizonsSemanticCollector.consumeVisibleSegments().size());
	}

	@Test
	void normalizesDhRowMajorMatricesForTheColumnMajorSemanticAbi() {
		float[] rowMajor = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
		assertArrayEquals(new float[] {1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15, 4, 8, 12, 16}, DistantHorizonsSemanticCollector.rowMajorToColumnMajor(rowMajor));
		assertThrows(IllegalArgumentException.class, () -> DistantHorizonsSemanticCollector.rowMajorToColumnMajor(new float[15]));
	}

	private static void publishPendingForTest() {
		DistantHorizonsSemanticCollector.PendingAssetUpdate update = DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(update, "a test publication must have a pending immutable asset update");
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);
	}

	private static ByteBuffer quadBuffer(int x, int y, int z, int metadata, int red, int green, int blue, int alpha, int materialId, int normalIndex) {
		ByteBuffer buffer = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 4).order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 4; vertex++) {
			buffer.putShort((short)x).putShort((short)y).putShort((short)z).putShort((short)metadata);
			buffer.put((byte)red).put((byte)green).put((byte)blue).put((byte)alpha).put((byte)materialId).put((byte)normalIndex).putShort((short)0);
		}
		return buffer.flip();
	}

	private static ByteBuffer twoQuadBuffer(int x) {
		ByteBuffer first = quadBuffer(x, 2, 3, 0xB7, 1, 1, 1, 255, 1, 1);
		ByteBuffer second = quadBuffer(x + 1, 2, 3, 0xB7, 1, 1, 1, 255, 1, 1);
		ByteBuffer combined = ByteBuffer.allocate(first.remaining() + second.remaining()).order(ByteOrder.nativeOrder());
		combined.put(first).put(second).flip();
		return combined;
	}

	private static ByteBuffer fourQuadBuffer() {
		ByteBuffer combined = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 16).order(ByteOrder.nativeOrder());
		for (int quad = 0; quad < 4; quad++) {
			for (int[] corner : new int[][] {{quad, 2, 3}, {quad + 1, 2, 3}, {quad + 1, 2, 4}, {quad, 2, 4}}) {
				combined.putShort((short)corner[0]).putShort((short)corner[1]).putShort((short)corner[2]).putShort((short)0xB7);
				combined.put((byte)1).put((byte)1).put((byte)1).put((byte)255).put((byte)1).put((byte)1).putShort((short)0);
			}
		}
		return combined.flip();
	}
}
