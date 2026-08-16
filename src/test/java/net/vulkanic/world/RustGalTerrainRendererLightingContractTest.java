package net.vulkanic.world;

import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RustGalTerrainRendererLightingContractTest {
	@Test
	void specularResourceSuffixPreservesResourceLocationExtensions() {
		assertEquals("block/sand_s", RustGalTerrainRenderer.appendPbrSuffix("block/sand", "_s"));
		assertEquals("optifine/cit/metal_s.png", RustGalTerrainRenderer.appendPbrSuffix("optifine/cit/metal.png", "_s"));
		assertEquals("custom/stone_s.png", RustGalTerrainRenderer.appendPbrSuffix("custom/stone.png", "_s"));
	}

	@Test
	void preservesIrisPackedBlockMaterialSemantics() {
		int packed = (10200 + 1 << 1) | 1;
		assertEquals(10200, RustGalTerrainRenderer.decodeIrisShaderBlockId(packed));
		assertEquals(1, RustGalTerrainRenderer.decodeIrisShaderRenderType(packed));
	}

	@Test
	public void compactTerrainColorConvertsSodiumAbgrToSemanticArgb() {
		int compactAbgr = 0x80402010;

		assertEquals(0x80102040, RustGalTerrainRenderer.decodeCompactTerrainColorForRust(compactAbgr, false));
	}

	@Test
	public void separateAoTerrainColorConsumesAlphaAsAmbientOcclusion() {
		int compactAbgr = 0x80402010;

		assertEquals(0xff081020, RustGalTerrainRenderer.decodeCompactTerrainColorForRust(compactAbgr, true));
	}

	@Test
	public void fullSeparateAoLeavesOpaqueWhiteVertexWhite() {
		int compactAbgr = 0xffffffff;

		assertEquals(0xffffffff, RustGalTerrainRenderer.decodeCompactTerrainColorForRust(compactAbgr, true));
	}

	@Test
	public void compactTerrainAtlasCoordinatesRemainTopOriginForCopiedAtlas() {
		int copiedAtlasV = Math.round(0.710938F * (1 << 15));
		float decoded = RustGalTerrainRenderer.decodeTexture(copiedAtlasV);

		assertEquals(0.710938F, decoded, 1.0F / (1 << 15));
		assertTrue(Math.abs(decoded - (1.0F - 0.710938F)) > 0.2F,
			"a vertically mirrored compact V coordinate selects an unrelated copied-atlas row");
	}

	@Test
	public void wholeFrameShaderEnvironmentUsesFreshVanillaFogInsteadOfSodiumHookCache() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("private static FogParameters shaderPackFogParameters()");
		int nextMethod = source.indexOf("\n\tprivate static", method + 1);
		String body = source.substring(method, nextMethod < 0 ? source.length() : nextMethod);

		assertTrue(body.contains("gameRenderer.fogRenderer.sodium$getFogParameters()"));
		assertTrue(!body.contains("instanceof FogStorage"));
	}

	@Test
	public void wholeFrameShaderEnvironmentUsesBlockDistanceForDistantHorizonsFog() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("private static int shaderPackDistantHorizonsRenderDistance()");
		int nextMethod = source.indexOf("\n\tprivate static", method + 1);
		String body = source.substring(method, nextMethod < 0 ? source.length() : nextMethod);

		assertTrue(body.contains("DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16"));
		assertTrue(body.contains("options.getEffectiveRenderDistance() * 16"));
		assertTrue(!source.contains("DHCompat.getRenderDistance()"));
	}

	@Test
	public void primitiveMetadataRestoresCompactTerrainShaderSemantics() {
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>();
		for (int index = 0; index < 4; index++) {
			vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
				2.0F + index * 0.25F,
				3.5F,
				4.25F,
				0.0F,
				0.0F,
				0.0F,
				0.0F,
				-1,
				-1,
				0xffffffff,
				0,
				0,
				0
			));
		}
		int[] metadata = primitiveMetadata(NativeSectionMeshBuilder.PRIMITIVE_KIND_UNKNOWN);
		metadata[2] = 12;
		metadata[3] = 2;
		metadata[4] = 3;
		metadata[5] = 4;
		metadata[6] = 0;
		metadata[9] = 9;

		RustGalTerrainRenderer.applyPrimitiveSemanticFallback(metadata, vertices, 4, false, false);

		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			assertEquals(12, vertex.shaderBlockId());
			assertEquals(0, vertex.shaderMaterialType());
		}
		assertEquals(0x09100020, vertices.get(0).midBlockPacked());
	}

	@Test
	public void primitiveMetadataOverridesPrivatePackedBlockIdentity() {
		int[] metadata = primitiveMetadata(NativeSectionMeshBuilder.PRIMITIVE_KIND_UNKNOWN);
		metadata[2] = 12;
		metadata[6] = 0;
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = testVertices(1);
		for (int index = 0; index < vertices.size(); index++) {
			VulkanicGalBridge.WorldMeshVertexRecord vertex = vertices.get(index);
			vertices.set(index, new VulkanicGalBridge.WorldMeshVertexRecord(
				vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(), vertex.atlasU(), vertex.atlasV(),
				32000, vertex.shaderMaterialType(), vertex.colorArgb(), vertex.normalPacked(), vertex.light(), vertex.midBlockPacked()
			));
		}

		RustGalTerrainRenderer.applyPrimitiveSemanticFallback(metadata, vertices, 4, true, false);

		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			assertEquals(12, vertex.shaderBlockId());
			assertEquals(0, vertex.shaderMaterialType());
		}
	}

	@Test
	public void primitiveMetadataOverridesPrivatePackedRenderType() {
		int[] metadata = primitiveMetadata(NativeSectionMeshBuilder.PRIMITIVE_KIND_UNKNOWN);
		metadata[2] = 12;
		metadata[6] = 1;
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = testVertices(1);
		for (int index = 0; index < vertices.size(); index++) {
			VulkanicGalBridge.WorldMeshVertexRecord vertex = vertices.get(index);
			vertices.set(index, new VulkanicGalBridge.WorldMeshVertexRecord(
				vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(), vertex.atlasU(), vertex.atlasV(),
				32000, 2, vertex.colorArgb(), vertex.normalPacked(), vertex.light(), vertex.midBlockPacked()
			));
		}

		RustGalTerrainRenderer.applyPrimitiveSemanticFallback(metadata, vertices, 4, true, false);

		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			assertEquals(12, vertex.shaderBlockId());
			assertEquals(1, vertex.shaderMaterialType());
		}
	}

	@Test
	public void translucentPrimitiveMetadataBuildsOrderedMixedMaterialRanges() {
		byte[] sorted = sortedQuads(0, 1, 2);
		int[] metadata = primitiveMetadata(
			NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER,
			NativeSectionMeshBuilder.PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT,
			NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER
		);
		RustGalTerrainRenderer.installTestingFluidSpriteAssetsForUnitTests();

		RustGalTerrainRenderer.OrderedTranslucentMesh mesh =
			RustGalTerrainRenderer.buildOrderedTranslucentMesh(sorted, metadata, testVertices(3), 12);
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = mesh.sections();

		assertEquals(sorted.length, mesh.indexBytes().length);
		assertEquals(3, mesh.sourcePrimitiveCount());
		assertEquals(1, mesh.nonFluidPrimitiveCount());
		assertEquals(2, mesh.waterPrimitiveCount());
		assertEquals(0, mesh.unsupportedPrimitiveCount());
		assertEquals(3, mesh.retainedPrimitiveCount());
		assertEquals(0, mesh.omittedPrimitiveCount());
		assertEquals(18, mesh.sourceIndexCount());
		assertEquals(18, mesh.retainedIndexCount());
		assertEquals(3, sections.size(), "water/glass/water sorted order must not be grouped by material");
		assertEquals(2, mesh.materialSwitchCount());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_WATER_TRANSLUCENT, sections.get(0).materialId());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_STILL, sections.get(0).textureId());
		assertEquals(0, sections.get(0).indexOffset());
		assertEquals(6, sections.get(0).indexCount());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_TRANSLUCENT_TEXTURED, sections.get(1).materialId());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS, sections.get(1).textureId());
		assertEquals(24, sections.get(1).indexOffset());
		assertEquals(6, sections.get(1).indexCount());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_WATER_TRANSLUCENT, sections.get(2).materialId());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_FLOW, sections.get(2).textureId());
		assertEquals(48, sections.get(2).indexOffset());
		assertEquals(6, sections.get(2).indexCount());
	}

	@Test
	public void translucentPrimitiveMetadataFiltersUnsupportedFluidWithoutReorderingRetainedQuads() {
		byte[] sorted = sortedQuads(0, 1, 2);
		int[] metadata = primitiveMetadata(
			NativeSectionMeshBuilder.PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT,
			NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID,
			NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER
		);
		RustGalTerrainRenderer.installTestingFluidSpriteAssetsForUnitTests();

		RustGalTerrainRenderer.OrderedTranslucentMesh mesh =
			RustGalTerrainRenderer.buildOrderedTranslucentMesh(sorted, metadata, testVertices(3), 12);
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = mesh.sections();

		assertEquals(48, mesh.indexBytes().length, "one unsupported primitive should be removed from the sorted payload");
		assertEquals(3, mesh.sourcePrimitiveCount());
		assertEquals(1, mesh.nonFluidPrimitiveCount());
		assertEquals(1, mesh.waterPrimitiveCount());
		assertEquals(1, mesh.unsupportedPrimitiveCount());
		assertEquals(2, mesh.retainedPrimitiveCount());
		assertEquals(1, mesh.omittedPrimitiveCount());
		assertEquals(18, mesh.sourceIndexCount());
		assertEquals(12, mesh.retainedIndexCount());
		assertEquals(6, mesh.omittedIndexCount());
		assertEquals(2, sections.size());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_TRANSLUCENT_TEXTURED, sections.get(0).materialId());
		assertEquals(0, sections.get(0).indexOffset());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_ID_WATER_TRANSLUCENT, sections.get(1).materialId());
		assertEquals(RustGalWorldPrimitiveRenderer.MATERIAL_TEXTURE_WATER_FLOW, sections.get(1).textureId());
		assertEquals(24, sections.get(1).indexOffset());
	}

	@Test
	public void translucentPrimitiveMetadataRejectsMalformedSortReferences() {
		int[] metadata = primitiveMetadata(
			NativeSectionMeshBuilder.PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT,
			NativeSectionMeshBuilder.PRIMITIVE_KIND_BUILTIN_WATER
		);
		RustGalTerrainRenderer.installTestingFluidSpriteAssetsForUnitTests();
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = testVertices(2);

		assertThrows(IllegalArgumentException.class,
			() -> RustGalTerrainRenderer.buildOrderedTranslucentMesh(sortedQuads(0, 0), metadata, new ArrayList<>(vertices), 8));
		assertThrows(IllegalArgumentException.class,
			() -> RustGalTerrainRenderer.buildOrderedTranslucentMesh(sortedQuads(0), metadata, new ArrayList<>(vertices), 8));

		byte[] interleaved = sortedQuads(0);
		interleaved[4] = 7;
		assertThrows(IllegalArgumentException.class,
			() -> RustGalTerrainRenderer.buildOrderedTranslucentMesh(interleaved, metadata, new ArrayList<>(vertices), 8));
	}

	@Test
	public void facingLocalStaticSortIndicesNormalizeIntoTheCopiedGlobalVertexStream() {
		byte[] facingLocal = sortedQuads(0, 0);

		byte[] normalized = RustGalTerrainRenderer.normalizeTranslucentSortedIndexBytes(
			facingLocal,
			8,
			new int[] { 1, 1 }
		);

		assertArrayEquals(sortedQuads(0, 1), normalized);
		assertArrayEquals(
			sortedQuads(1, 0),
			RustGalTerrainRenderer.normalizeTranslucentSortedIndexBytes(sortedQuads(1, 0), 8, new int[] { 1, 1 }),
			"already-global dynamic or topological sorter payloads must pass through unchanged"
		);
		assertThrows(IllegalArgumentException.class,
			() -> RustGalTerrainRenderer.normalizeTranslucentSortedIndexBytes(sortedQuads(0, 0), 8, new int[] { 2 }));
	}

	@Test
	public void translucentPrimitiveMetadataRepresentsAllUnsupportedPayloadAsAnEmptyFilteredRange() {
		int[] metadata = primitiveMetadata(NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID);

		RustGalTerrainRenderer.OrderedTranslucentMesh mesh =
			RustGalTerrainRenderer.buildOrderedTranslucentMesh(sortedQuads(0), metadata, testVertices(1), 4);

		assertEquals(1, mesh.sourcePrimitiveCount());
		assertEquals(1, mesh.unsupportedPrimitiveCount());
		assertEquals(0, mesh.retainedPrimitiveCount());
		assertEquals(1, mesh.omittedPrimitiveCount());
		assertEquals(0, mesh.retainedIndexCount());
		assertEquals(6, mesh.omittedIndexCount());
		assertTrue(mesh.sections().isEmpty());
	}

	private static int[] primitiveMetadata(int... kinds) {
		int stride = NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS;
		int[] metadata = new int[kinds.length * stride];
		for (int index = 0; index < kinds.length; index++) {
			metadata[index * stride] = kinds[index];
		}
		return metadata;
	}

	private static byte[] sortedQuads(int... primitiveIds) {
		byte[] bytes = new byte[primitiveIds.length * 24];
		int cursor = 0;
		for (int primitiveId : primitiveIds) {
			int base = primitiveId * 4;
			for (int value : new int[] { base, base + 1, base + 2, base + 2, base + 3, base }) {
				bytes[cursor++] = (byte)(value & 0xff);
				bytes[cursor++] = (byte)((value >>> 8) & 0xff);
				bytes[cursor++] = (byte)((value >>> 16) & 0xff);
				bytes[cursor++] = (byte)((value >>> 24) & 0xff);
			}
		}
		return bytes;
	}

	private static List<VulkanicGalBridge.WorldMeshVertexRecord> testVertices(int primitiveCount) {
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>(primitiveCount * 4);
		for (int primitive = 0; primitive < primitiveCount; primitive++) {
			float baseU = switch (primitive) {
				case 0 -> 0.05F;
				case 2 -> 0.30F;
				default -> 0.80F;
			};
			float baseV = 0.05F;
			vertices.add(vertex(baseU, baseV));
			vertices.add(vertex(baseU + 0.05F, baseV));
			vertices.add(vertex(baseU + 0.05F, baseV + 0.05F));
			vertices.add(vertex(baseU, baseV + 0.05F));
		}
		return vertices;
	}

	private static VulkanicGalBridge.WorldMeshVertexRecord vertex(float u, float v) {
		return new VulkanicGalBridge.WorldMeshVertexRecord(
			0.0F,
			0.0F,
			0.0F,
			u,
			v,
			u,
			v,
			0,
			0,
			0xffffffff,
			0,
			0,
			0
		);
	}
}
