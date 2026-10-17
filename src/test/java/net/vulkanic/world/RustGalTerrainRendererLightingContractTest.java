package net.vulkanic.world;

import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.nio.file.Path;

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
	void shaderPackDepthFarUsesEffectiveRenderDistanceScalar() {
		String source;
		try {
			source = Files.readString(Path.of(
				"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		} catch (java.io.IOException error) {
			throw new AssertionError("unable to read Rust world renderer source", error);
		}
		assertTrue(source.contains("shaderPackDepthFarForRenderDistance("));
		assertTrue(source.contains("Math.max(1.0F, effectiveRenderDistance * 16.0F)"));
	}




	@Test
	void wholeFrameTerrainSnapshotDeduplicatesCanonicalSectionPositions() {
		RenderSection built = new RenderSection(null, 3, 4, 5);
		built.setInfo(BuiltSectionInfo.EMPTY);
		RenderSection duplicatePosition = new RenderSection(null, 3, 4, 5);
		duplicatePosition.setInfo(BuiltSectionInfo.EMPTY);
		RenderSection unbuilt = new RenderSection(null, 9, 9, 9);

		List<RenderSection> snapshot = RustGalTerrainRenderer.snapshotBuiltTerrainSections(
			Arrays.asList(null, built, duplicatePosition, built, unbuilt, null)
		);

		assertEquals(List.of(built), snapshot,
			"semantic terrain extraction must submit each canonical section position once");
		assertEquals(List.of(), RustGalTerrainRenderer.snapshotBuiltTerrainSections(null));
	}

	@Test
	void wholeFrameTerrainSnapshotRejectsMoreThanRustResidencyBound() {
		List<RenderSection> sections = new ArrayList<>(4097);
		for (int index = 0; index < 4097; index++) {
			RenderSection section = new RenderSection(null, index, 0, 0);
			section.setInfo(BuiltSectionInfo.EMPTY);
			sections.add(section);
		}

		assertThrows(IllegalStateException.class,
			() -> RustGalTerrainRenderer.snapshotBuiltTerrainSections(sections),
			"semantic terrain extraction must fail closed before exceeding Rust section residency");
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
	public void copiedPbrAtlasIsBoundedBeforeBaseAndDerivedImageAllocation() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"
		));
		assertTrue(source.contains("MAX_RUST_ATLAS_PIXELS = 16_777_216L"));
		int ensure = source.indexOf("private static void ensureAtlasPayload()");
		int ensureImage = source.indexOf("new BufferedImage(atlas.width, atlas.height", ensure);
		assertTrue(ensure >= 0 && ensureImage > ensure);
		assertTrue(source.substring(ensure, ensureImage).contains("atlas pixel bound exceeded"));
		int pbr = source.indexOf("private static byte[] buildPbrAtlasPayload");
		int pbrImage = source.indexOf("new BufferedImage(atlas.width, atlas.height", pbr);
		assertTrue(pbr >= 0 && pbrImage > pbr);
		assertTrue(source.substring(pbr, pbrImage).contains("Rust PBR atlas pixel bound exceeded"));
		assertTrue(source.substring(ensure, pbr).contains("hasPbrResources(atlas, \"_n\")"));
		assertTrue(source.substring(ensure, pbr).contains("hasPbrResources(atlas, \"_s\")"));
	}

	@Test
	public void wholeFrameShaderEnvironmentUsesFreshVanillaFogInsteadOfSodiumHookCache() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("private static FogRenderer.RustFogParameters shaderPackFogParameters(ClientLevel level, Camera camera)");
		assertTrue(method >= 0, "the semantic fog bridge must remain a private Rust-owned record builder");
		int nextMethod = source.indexOf("\n\tprivate static", method + 1);
		String body = source.substring(method, nextMethod < 0 ? source.length() : nextMethod);

		assertTrue(body.contains("collectFogParametersForRust("));
		assertTrue(body.contains("getDeltaTracker()"));
		assertTrue(!body.contains("sodium$getFogParameters()"));
		assertTrue(!body.contains("instanceof FogStorage"));
	}

	@Test
	public void wholeFrameShaderEnvironmentMirrorsIrisDistantHorizonsRenderDistance() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("private static int shaderPackDistantHorizonsRenderDistance()");
		int nextMethod = source.indexOf("\n\tprivate static", method + 1);
		String body = source.substring(method, nextMethod < 0 ? source.length() : nextMethod);

		// Iris DHCompat: DH radius in blocks while DH rendering is enabled,
		// else the effective vanilla distance in chunks. Never the larger of both.
		assertTrue(body.contains("DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16"));
		assertTrue(body.contains("DhApi.Delayed.configs.graphics().renderingEnabled().getValue()"));
		assertTrue(body.contains("return Minecraft.getInstance().options.getEffectiveRenderDistance();"));
		assertFalse(body.contains("Math.max("));
		assertTrue(!source.contains("DHCompat.getRenderDistance()"));
	}

	@Test
	public void visibleTerrainQueuesPendingReplacementBeforeThePreConsumeAssetFlush() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"
		));
		int enqueue = source.indexOf("private static boolean enqueueSectionLayer");
		int submission = source.indexOf("boolean submitted = RustGalWorldPrimitiveRenderer.enqueueStaticTerrainSectionInstance", enqueue);
		assertTrue(enqueue >= 0 && submission > enqueue);
		String beforeSubmission = source.substring(enqueue, submission);
		assertFalse(beforeSubmission.contains("isStaticTerrainMeshGenerationUploaded"),
			"a visible replacement must be queued before its asset upload so the coordinator can publish and consume it atomically");
		int methodEnd = source.indexOf("private static float[] cameraRelativeTranslationTransform", submission);
		String afterSubmission = source.substring(submission, methodEnd);
		assertTrue(afterSubmission.contains("isStaticTerrainMeshGenerationUploaded"));
		assertTrue(afterSubmission.contains("asset-upload-pending"));

		String coordinator = Files.readString(Path.of(
			"src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"
		));
		int wholeFrame = coordinator.indexOf("if (wholeFrameVulkan)");
		int assetFlush = coordinator.indexOf("flushPendingWorldAssetsLocked();", wholeFrame);
		int consume = coordinator.indexOf("primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();", wholeFrame);
		assertTrue(assetFlush > wholeFrame && consume > assetFlush,
			"the queued replacement must be uploaded before consumeFrame admits its generation");
	}

	@Test
	public void staticTerrainPreservesFrozenSodiumBackFaceCullingContract() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"
		));
		int enqueue = source.indexOf("private static boolean enqueueSectionLayer");
		int submission = source.indexOf("boolean submitted = RustGalWorldPrimitiveRenderer.enqueueStaticTerrainMeshInstance", enqueue);
		assertTrue(enqueue >= 0 && submission > enqueue);
		String setup = source.substring(enqueue, submission + 900);
		assertTrue(setup.contains("RustGalWorldPrimitiveRenderer.CULL_BACK"),
				"Frozen Sodium terrain pipelines retain their default back-face culling for every terrain layer");
		assertTrue(!setup.contains("RustGalWorldPrimitiveRenderer.CULL_NONE"),
				"the semantic terrain route must not accumulate both sides of copied glass or fluid faces");

		// Draw ranges are assembled in Rust (worldrender/terrain/assembly.rs).
		String assembly = Files.readString(Path.of("src/main/rust/render/worldrender/terrain/assembly.rs"));
		assertEquals(2, assembly.split("cull_policy: WORLD_CULL_BACK", -1).length - 1,
				"opaque/cutout and translucent ranges keep back-face culling");
		assertTrue(!assembly.contains("WORLD_CULL_NONE"));
	}


	@Test
	public void uploadedTerrainCanEnqueueAfterCpuPayloadRelease() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("public static boolean enqueueStaticTerrainMeshInstance(");
		int lock = source.indexOf("synchronized (LOCK)", method);
		int validation = source.indexOf("boolean generationMatches", lock);
		assertTrue(method >= 0 && lock > method && validation > lock);
		String body = source.substring(lock, source.indexOf("if (!generationMatches)", validation));
		assertTrue(body.contains("STATIC_TERRAIN_MESH_RESIDENCY.get(meshKey)"));
		assertTrue(body.contains("residency.meshGeneration() == meshGeneration"));
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
				0,
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
				32000, vertex.shaderMaterialType(), vertex.terrainMaterialBits(), vertex.colorArgb(), vertex.normalPacked(), vertex.light(), vertex.midBlockPacked()
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
				32000, 2, vertex.terrainMaterialBits(), vertex.colorArgb(), vertex.normalPacked(), vertex.light(), vertex.midBlockPacked()
			));
		}

		RustGalTerrainRenderer.applyPrimitiveSemanticFallback(metadata, vertices, 4, true, false);

		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			assertEquals(12, vertex.shaderBlockId());
			assertEquals(1, vertex.shaderMaterialType());
		}
	}

	@Test
	public void normalVulkanWaterBindingDoesNotDependOnPrivateAnimationAdmission() throws Exception {
		String property = "mattmc.dev.rustGalAtlasAnimation";
		String previous = System.getProperty(property);
		try {
			for (String value : new String[] { "false", "true" }) {
				System.setProperty(property, value);
				for (var route : WorldRenderRoutePolicy.Route.values()) {
					assertEquals(route.usesRustWholeFrameVulkan()
						? RustGalTerrainRenderer.WaterTextureBinding.BLOCK_ATLAS
						: RustGalTerrainRenderer.WaterTextureBinding.SEPARATE_SHEETS,
						RustGalTerrainRenderer.waterTextureBinding(route));
				}
			}
			System.clearProperty(property);
			assertEquals(RustGalTerrainRenderer.WaterTextureBinding.BLOCK_ATLAS,
				RustGalTerrainRenderer.waterTextureBinding(WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME));
			String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"));
			assertTrue(source.contains("waterTextureBinding(WorldRenderRoutePolicy.currentStaticTerrainRoute())"));
			assertFalse(source.contains("AtlasAnimationResource.privateTickDeliveryEnabled()"));
		} finally {
			if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
		}
	}

	@Test
	void atlasGenerationCommitsOnlyAfterRustMeshAdmission() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"));
		int register = source.indexOf("RustGalWorldPrimitiveRenderer.registerStaticTerrainMeshAsset");
		int confirm = source.indexOf("confirmAtlasPayloadRegistered(atlasGenerationForRegistration)", register);
		assertTrue(register >= 0 && confirm > register,
			"atlas generation must commit only after Rust mesh registration succeeds");
	}

	@Test
	void unchangedTerrainTexturePayloadsDoNotRemainDirtyAfterEveryMeshRegistration() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static void registerStaticTerrainMeshAsset(");
		int textureLoop = source.indexOf("for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : textures)", method);
		int compare = source.indexOf("changed |= registerChangedTexture(WORLD_MESH_TEXTURES, DIRTY_WORLD_MESH_TEXTURES, texture)", textureLoop);
		int end = source.indexOf("STATIC_TERRAIN_MESH_RESIDENCY.put", textureLoop);
		assertTrue(method >= 0 && textureLoop > method && compare > textureLoop && compare < end,
			"unchanged static-terrain texture payloads must not be re-dirtied every frame");
	}

	@Test
	void unchangedTerrainMeshPayloadsDoNotAdvanceUploadGeneration() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static void registerStaticTerrainMeshAsset(");
		int residency = source.indexOf("STATIC_TERRAIN_MESH_RESIDENCY.put", method);
		int helper = source.indexOf("sameStaticTerrainPayload(previous, asset)", residency);
		int definition = source.indexOf("private static boolean sameStaticTerrainPayload(");
		assertTrue(method >= 0 && residency > method && helper > residency && definition > helper,
			"payload-equivalent terrain rebuilds must retain the existing uploaded generation");
	}

	@Test
	void steadyTerrainVisibilityLookupsReuseAReadOnlyProbe() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"));
		int workload = source.indexOf("private static void recordTerrainIndexWorkload(");
		int visibility = source.indexOf("private static LongOpenHashSet visibleWholeFrameMeshKeys(", workload);
		int snapshot = source.indexOf("static List<RenderSection> snapshotBuiltTerrainSections(", visibility);
		assertTrue(workload >= 0 && visibility > workload && snapshot > visibility);
		assertFalse(source.substring(workload, snapshot).contains("new LayerKey("),
			"steady whole-frame accounting and visibility must not allocate map keys per section/layer");
		assertTrue(source.contains("ThreadLocal<LayerLookup> SECTION_ASSET_LOOKUP"));
		assertTrue(source.contains("return SECTION_ASSETS.get(lookup);"));
		assertTrue(source.contains("private record LayerKey(long sectionPos, ChunkSectionLayer layer) implements LayerAddress"),
			"only immutable layer keys may be published into the concurrent asset map");

		Class<?> keyType = Class.forName("net.vulkanic.world.RustGalTerrainRenderer$LayerKey");
		var keyConstructor = keyType.getDeclaredConstructor(long.class, ChunkSectionLayer.class);
		keyConstructor.setAccessible(true);
		Object immutableKey = keyConstructor.newInstance(0x1234_5678_9abcL, ChunkSectionLayer.CUTOUT_MIPPED);
		Class<?> lookupType = Class.forName("net.vulkanic.world.RustGalTerrainRenderer$LayerLookup");
		var lookupConstructor = lookupType.getDeclaredConstructor();
		lookupConstructor.setAccessible(true);
		Object lookup = lookupConstructor.newInstance();
		var set = lookupType.getDeclaredMethod("set", long.class, ChunkSectionLayer.class);
		set.setAccessible(true);
		set.invoke(lookup, 0x1234_5678_9abcL, ChunkSectionLayer.CUTOUT_MIPPED);
		HashMap<Object, String> map = new HashMap<>();
		map.put(immutableKey, "asset");
		assertEquals("asset", map.get(lookup), "the reusable probe must retrieve an immutable stored key");
		assertEquals(immutableKey, lookup);
		assertEquals(lookup, immutableKey);
	}

	private static int[] primitiveMetadata(int... kinds) {
		int stride = NativeSectionMeshBuilder.PRIMITIVE_METADATA_RECORD_INTS;
		int[] metadata = new int[kinds.length * stride];
		for (int index = 0; index < kinds.length; index++) {
			metadata[index * stride] = kinds[index];
		}
		return metadata;
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
			0,
			0xffffffff,
			0,
			0,
			0
		);
	}
}
