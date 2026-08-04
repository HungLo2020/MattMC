package net.vulkanic.world;

import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RustGalTerrainRendererLightingContractTest {
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
	public void translucentPrimitiveMetadataRejectsAllUnsupportedPayload() {
		int[] metadata = primitiveMetadata(NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID);

		assertThrows(IllegalArgumentException.class,
			() -> RustGalTerrainRenderer.buildOrderedTranslucentMesh(sortedQuads(0), metadata, testVertices(1), 4));
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
			0
		);
	}
}
