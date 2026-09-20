package net.vulkanic.bridge;

import net.vulkanic.gui.AbsorptionHeartRequest;
import net.vulkanic.gui.AbsorptionHeartVariant;
import net.vulkanic.gui.AirBubbleRequest;
import net.vulkanic.gui.AirBubbleState;
import net.vulkanic.gui.ArmorIconState;
import net.vulkanic.gui.GuiHeartState;
import net.vulkanic.gui.HungerIconRequest;
import net.vulkanic.gui.HungerIconState;
import net.vulkanic.gui.HungerIconVariant;
import net.vulkanic.gui.MountHeartRequest;
import net.vulkanic.gui.MountHeartState;
import net.vulkanic.gui.MountHeartVariant;
import net.vulkanic.gui.PlayerHeartRequest;
import net.vulkanic.gui.PlayerHeartVariant;
import net.vulkanic.gui.RustGalFrameCoordinator;
import net.vulkanic.gui.RustGalGuiRenderer;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanicGalBridgeAbiTest {
	@Test
	void packedGuiMeshSemanticsRemainOwnedAndEncodeWithoutRecordExpansion() throws Exception {
		float[] positions = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
		float[] atlasUvs = {0, 0, 1, 0, 1, 1, 0, 1};
		float[] localUvs = {0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F};
		int[] colors = {0xff010203, 0xff040506, 0xff070809, 0xff0a0b0c};
		int[] normals = {1, 2, 3, 4};
		int[] sourceIndices = {0, 1, 2, 2, 3, 0};
		var vertices = VulkanicGalBridge.packedGuiMeshVertices(
			positions, atlasUvs, localUvs, colors, normals, 4);
		var indices = VulkanicGalBridge.packedGuiMeshIndices(sourceIndices, 6);
		positions[0] = 99;
		atlasUvs[0] = 99;
		localUvs[0] = 99;
		colors[0] = 0;
		normals[0] = 0;
		sourceIndices[0] = 3;
		assertArrayEquals(new float[] {1, 2, 3}, vertices.getFirst().position());
		assertArrayEquals(new float[] {0, 0}, vertices.getFirst().atlasUv());
		assertArrayEquals(new float[] {0.1F, 0.2F}, vertices.getFirst().localUv());
		assertEquals(0xff010203, vertices.getFirst().colorArgb());
		assertEquals(1, vertices.getFirst().normalPacked());
		assertEquals(0, indices.getFirst());
		assertThrows(UnsupportedOperationException.class, () -> indices.add(4));

		var encodeVertices = VulkanicGalBridge.class.getDeclaredMethod("encodeGuiMeshVertices", Arena.class, List.class);
		var encodeIndices = VulkanicGalBridge.class.getDeclaredMethod("encodeGuiMeshIndices", Arena.class, List.class);
		encodeVertices.setAccessible(true);
		encodeIndices.setAccessible(true);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment encodedVertices = (MemorySegment) encodeVertices.invoke(null, arena, vertices);
			MemorySegment first = VulkanicGalBridge.Abi.item(
				encodedVertices, VulkanicGalBridge.Struct.GUI_MESH_VERTEX, 0);
			assertEquals(1.0F, first.get(java.lang.foreign.ValueLayout.JAVA_FLOAT,
				VulkanicGalBridge.Struct.GUI_MESH_VERTEX.offset(0)));
			assertEquals(0xff010203, VulkanicGalBridge.Struct.GUI_MESH_VERTEX.getInt(first, 3));
			assertEquals(1, VulkanicGalBridge.Struct.GUI_MESH_VERTEX.getInt(first, 4));
			MemorySegment encodedIndices = (MemorySegment) encodeIndices.invoke(null, arena, indices);
			assertEquals(0, encodedIndices.getAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, 0));
			assertEquals(3, encodedIndices.getAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, 4));
		}
		assertThrows(IllegalArgumentException.class, () -> VulkanicGalBridge.packedGuiMeshVertices(
			new float[] {Float.NaN, 0, 0}, new float[] {0, 0}, new float[] {0, 0}, new int[] {-1}, new int[] {0}, 1));
	}

	@Test
	void distantHorizonsTintPackingPreservesJavaRgbChannelOrder() {
		assertEquals((0x4f << 3) | (0xa1 << 11) | (0x3c << 19),
			VulkanicGalBridge.packWorldLodTint(0xff4fa13c));
	}

	@Test
	void settledDistantHorizonsInstancesReuseExactCopiedStaging() throws Exception {
		try (var bridge = VulkanicGalBridge.create("rust-vulkan")) {
			var pack = VulkanicGalBridge.class.getDeclaredMethod(
				"encodeWorldLodInstances", List.class);
			pack.setAccessible(true);
			var first = List.of(
				new VulkanicGalBridge.WorldLodColumnInstanceRecord(17L, 3L, 1, 2, 4),
				new VulkanicGalBridge.WorldLodColumnInstanceRecord(23L, 3L, 3, 0, 5));
			MemorySegment initial = (MemorySegment) pack.invoke(bridge, first);
			MemorySegment settled = (MemorySegment) pack.invoke(bridge, List.copyOf(first));
			assertEquals(initial.address(), settled.address(), "settled DH lists should reuse the native slice");
			MemorySegment item = VulkanicGalBridge.Abi.item(
				initial, VulkanicGalBridge.Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD, 1);
			assertEquals(23L, VulkanicGalBridge.Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.getLong(item, 4));

			var changed = List.of(
				new VulkanicGalBridge.WorldLodColumnInstanceRecord(17L, 3L, 1, 2, 4),
				new VulkanicGalBridge.WorldLodColumnInstanceRecord(23L, 4L, 3, 0, 5));
			MemorySegment updated = (MemorySegment) pack.invoke(bridge, changed);
			assertEquals(initial.address(), updated.address(), "generation changes should reuse bounded capacity");
			MemorySegment updatedItem = VulkanicGalBridge.Abi.item(
				updated, VulkanicGalBridge.Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD, 1);
			assertEquals(4L, VulkanicGalBridge.Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.getLong(updatedItem, 5));

			MemorySegment empty = (MemorySegment) pack.invoke(bridge, List.of());
			assertTrue(empty.equals(MemorySegment.NULL));
		}
	}

	@Test
	void settledTerrainInstancesReuseIdentityWhileFrameCameraLanesAdvance() throws Exception {
		try (var bridge = VulkanicGalBridge.create("rust-vulkan")) {
			var pack = VulkanicGalBridge.class.getDeclaredMethod(
				"encodeWorldMeshInstances", List.class, VulkanicGalBridge.TerrainFrameCamera.class);
			pack.setAccessible(true);
			var placement = new VulkanicGalBridge.TerrainSectionPlacement(112, 80, 528);
			var instance = VulkanicGalBridge.WorldMeshInstanceRecord.staticTerrain(
				41L, 7L, 0, 1, 0, 0, 1280, 720, placement);
			MemorySegment initial = (MemorySegment) pack.invoke(
				bridge, List.of(instance), new VulkanicGalBridge.TerrainFrameCamera(150.5, 101.62, 530.5));
			MemorySegment item = VulkanicGalBridge.Abi.item(
				initial, VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD, 0);
			assertEquals(112, item.get(java.lang.foreign.ValueLayout.JAVA_INT,
				VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD.offset(18)));
			assertEquals(101.62, item.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE,
				VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD.offset(19) + 8L));

			MemorySegment moved = (MemorySegment) pack.invoke(
				bridge, List.of(instance), new VulkanicGalBridge.TerrainFrameCamera(151.25, 102.125, 531.75));
			assertEquals(initial.address(), moved.address(), "camera motion should reuse terrain staging");
			assertEquals(112, item.get(java.lang.foreign.ValueLayout.JAVA_INT,
				VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD.offset(18)));
			assertEquals(151.25, item.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE,
				VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD.offset(19)));
			assertEquals(102.125, item.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE,
				VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD.offset(19) + 8L));
			assertEquals(531.75, item.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE,
				VulkanicGalBridge.Struct.WORLD_MESH_INSTANCE_RECORD.offset(19) + 16L));
		}
	}

	@Test
	void itemRasterPreservesBoundedAuthoredUvSubrectangles() {
		var quad = new VulkanicGalBridge.GuiAffineQuadRecord(1,7L,
			0,0,16,0,0,16,0,0.25F,0,0.75F,0.5F,-1,100,100)
			.withMaterialMode(1).withItemRasterScale(2).withSequence(3).withClip(0,0,20,20);
		assertEquals(0.25F,quad.u0());
		assertEquals(0.75F,quad.u1());
		assertEquals(0.5F,quad.v1());
		for (float u0 : new float[] {-0.001F,0.75F,Float.NaN}) {
			assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.GuiAffineQuadRecord(1,7L,
				0,0,16,0,0,16,0,u0,0,0.75F,0.5F,-1,100,100).withMaterialMode(1).withItemRasterScale(2));
		}
	}
	@Test
	void affineMaterialSurvivesSchedulingAndClipping() {
		var unlit = new VulkanicGalBridge.GuiAffineQuadRecord(1, 7L,
			0, 0, 16, 0, 0, 16, 0, 0, 0, 1, 1, -1, 100, 100);
		assertEquals(0, unlit.materialMode());
		var geometry = new VulkanicGalBridge.GuiItemRasterGeometryRecord(4,2,12,2,4,14);
		var item = unlit.withMaterialMode(1).withItemRasterScale(3).withItemRasterGeometry(geometry)
			.withSequence(4).withStratum(10).withClip(0, 0, 20, 20);
		assertEquals(geometry, item.itemRasterGeometry());
		assertEquals(geometry, item.withMaterialMode(2).withItemRasterScale(2).itemRasterGeometry());
		assertThrows(IllegalArgumentException.class, () -> unlit.withItemRasterGeometry(geometry));
		float[] copied = geometry.corners();
		copied[0] = 0;
		assertEquals(4, geometry.x0());
		assertThrows(IllegalArgumentException.class,
			() -> new VulkanicGalBridge.GuiItemRasterGeometryRecord(0,0,16,4,4,16));
		assertEquals(3, item.itemRasterScale());
		assertThrows(IllegalArgumentException.class, () -> unlit.withItemRasterScale(3));
		assertThrows(IllegalArgumentException.class, () -> item.withItemRasterScale(257));
		assertEquals(1, item.materialMode());
		assertEquals(4, item.sequence());
		assertEquals(10, item.stratum());
		assertEquals(20, item.clipWidth());
		assertEquals(2, item.withMaterialMode(2).withSequence(9).materialMode());
		assertThrows(IllegalArgumentException.class, () -> unlit.withMaterialMode(3));
	}

	private static String readRustFfiModules() throws Exception {
		Path root = Path.of("src/main/rust/render/vulkanic/ffi");
		StringBuilder source = new StringBuilder();
		try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths
				.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".rs"))
				.sorted()
				.toList()) {
				source.append(Files.readString(path)).append('\n');
			}
		}
		return source.toString();
	}

	@Test
	void javaLayoutsAreQueriedFromRustAbi() {
		for (VulkanicGalBridge.Struct struct : VulkanicGalBridge.Struct.values()) {
			assertTrue(struct.byteSize() > 0, "byte size should be reported for " + struct);
			assertTrue(struct.alignment() > 0, "alignment should be reported for " + struct);
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment segment = struct.allocate(arena);
				assertEquals(struct.byteSize(), segment.byteSize(), "allocation should use Rust byte size for " + struct);
			}
		}
	}
























































	private static int occurrences(String source, String needle) {
		int count = 0;
		for (int offset = 0; (offset = source.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
		return count;
	}













	@Test
	void malformedBackendKindIsRejectedDeterministically() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment request = VulkanicGalBridge.Struct.CONTEXT_CREATE.allocate(arena);
			VulkanicGalBridge.Abi.writeHeader(request, VulkanicGalBridge.Struct.CONTEXT_CREATE);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 1, 9999);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 2, 0);
			VulkanicGalBridge.Abi.writeBytes(arena, request, VulkanicGalBridge.Struct.CONTEXT_CREATE, 3, "bad-backend");
			MemorySegment result = VulkanicGalBridge.Struct.CONTEXT_RESULT.allocate(arena);

			int status = VulkanicGalBridge.Native.contextCreate(request, result);

			assertEquals(-5, status);
			assertEquals(-5, VulkanicGalBridge.Struct.CONTEXT_RESULT.getInt(result, 1));
		}
	}



	@Test
	void shaderAffectedWeatherRouteKeepsIrisAndNormalJavaVulkanCompatibilityOwned() {
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, true, false, false),
			"a selected Rust Vulkan whole-frame route owns weather even with a shader pack configured"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, false, false, false, false),
			"unadmitted Vulkan must remain unavailable rather than reopening Java rendering"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(false, false, true, false, false),
			"Iris OpenGL must remain Java-compatible until Rust owns the complete shader frame"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_OPENGL_BORROWED_CONTEXT,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(false, false, false, false, false),
			"non-Iris OpenGL may select Rust's explicit borrowed-context route"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, false, true, false)
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, false, false, true)
			, "legacy route controls must fail closed while Rust Vulkan owns the whole frame"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectRouteForTests(true, true, false, true),
			"generic legacy route controls must not reopen Java Vulkan rendering"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectWholeFrameRouteForTests(true, true, false, true),
			"whole-frame legacy route controls must remain unavailable under Rust Vulkan"
		);
	}











	@Test
	void staticTerrainBuildMetadataRouteDoesNotDependOnBackendSelectionTiming() {
		String previousWholeFrame = System.getProperty("mattmc.dev.rustGalVulkanWholeFrame");
		String previousDisabled = System.getProperty("mattmc.dev.rustGalStaticTerrain.disabled");
		String previousLegacy = System.getProperty("mattmc.dev.rustGalStaticTerrain.legacyControl");
		try {
			System.setProperty("mattmc.dev.rustGalVulkanWholeFrame", "true");
			System.clearProperty("mattmc.dev.rustGalStaticTerrain.disabled");
			System.clearProperty("mattmc.dev.rustGalStaticTerrain.legacyControl");
			assertTrue(WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata());

			System.setProperty("mattmc.dev.rustGalStaticTerrain.disabled", "true");
			assertFalse(WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata());
			System.clearProperty("mattmc.dev.rustGalStaticTerrain.disabled");

			System.setProperty("mattmc.dev.rustGalStaticTerrain.legacyControl", "true");
			assertFalse(WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata());
		} finally {
			restoreProperty("mattmc.dev.rustGalVulkanWholeFrame", previousWholeFrame);
			restoreProperty("mattmc.dev.rustGalStaticTerrain.disabled", previousDisabled);
			restoreProperty("mattmc.dev.rustGalStaticTerrain.legacyControl", previousLegacy);
		}
	}


	private static void restoreProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

































































































	@Test
	void lodBridgeRejectsValuesOutsideRustLayerAndVertexDomains() {
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldLodVertexRecord(
			0, 0, 0, 0, 0xffffffff, 16, 0));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldLodVertexRecord(
			0, 0, 0, 0, 0xffffffff, 0, 6));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldLodColumnInstanceRecord(
			1L, 1L, 5, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldLodSegmentMaterialProvenanceRecord(
			5, 0, new int[] {1}));
	}





































































































































































	private static void assertApiDeletionGuarded(String source, String method) {
		int start = source.indexOf(method);
		int guard = source.indexOf("rejectJavaVulkanWholeFrameOperation", start);
		assertTrue(start >= 0 && guard > start, method + " must reject before compatibility-state deletion");
	}












	private static void assertGuarded(String source, String method, String guard) {
		int start = source.indexOf(method);
		int next = source.indexOf("\n\tpublic ", start + method.length());
		int guardAt = source.indexOf(guard, start);
		assertTrue(start >= 0 && guardAt > start && (next < 0 || guardAt < next),
			method + " must reject Java GPU ownership mutations in whole-frame mode");
	}



























































































	@Test
	void semanticBlockEntityScopesRestoreNestedIdentityAndRejectUnderflow() {
		assertEquals(-1, VulkanicGalBridge.activeSemanticBlockEntityId());
		VulkanicGalBridge.beginSemanticBlockEntity(17);
		try {
			assertEquals(17, VulkanicGalBridge.activeSemanticBlockEntityId());
			VulkanicGalBridge.beginSemanticBlockEntity(29);
			assertEquals(29, VulkanicGalBridge.activeSemanticBlockEntityId());
			VulkanicGalBridge.endSemanticBlockEntity();
			assertEquals(17, VulkanicGalBridge.activeSemanticBlockEntityId());
		} finally {
			if (VulkanicGalBridge.activeSemanticBlockEntityId() != -1) {
				VulkanicGalBridge.endSemanticBlockEntity();
			}
		}
		assertEquals(-1, VulkanicGalBridge.activeSemanticBlockEntityId());
		assertThrows(IllegalStateException.class, VulkanicGalBridge::endSemanticBlockEntity);
	}

	@Test
	void equalDepthMeshPolicyRequiresAnOrdinaryEntityLayer() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		float[] identity = new org.joml.Matrix4f().get(new float[16]);
		var layer = new VulkanicGalBridge.WorldMeshInstanceRecord(
			67, 41L, 2L, -1, 3, 0, 0, 0xffffffff, identity, 640, 480, 0, 0, 0, 0, -1);
		assertEquals(3, layer.depthPolicy());
		assertThrows(IllegalArgumentException.class, () -> layer.withItemFoil(
			new VulkanicGalBridge.StandardItemFoilRecord(12345L, 0.5, 0.5f)));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldMeshInstanceRecord(
			60, 41L, 2L, -1, 3, 0, 0, 0xffffffff, identity, 640, 480, 0, 0, 0, 0, -1));
		for (int invalid : new int[]{-1, 4, Integer.MAX_VALUE}) {
			assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldMeshInstanceRecord(
				67, 41L, 2L, -1, invalid, 0, 0, 0xffffffff, identity, 640, 480, 0, 0, 0, 0, -1));
		}
	}

	@Test
	void terrainOriginAndFrameCameraRemainSeparateAndRejectAmbiguousOwnership() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var placement = new VulkanicGalBridge.TerrainSectionPlacement(112, 80, 528);
		var frameCamera = new VulkanicGalBridge.TerrainFrameCamera(150.5, 101.62, 530.5);
		assertEquals(101.62, frameCamera.y());
		assertThrows(IllegalArgumentException.class,
			() -> new VulkanicGalBridge.TerrainFrameCamera(Double.NaN, 0, 0));
		assertNotEquals((double)(float)101.62, frameCamera.y());
		var original = new VulkanicGalBridge.WorldMeshInstanceRecord(
			60, 41L, 2L, -1, 0, 0, 0, 0xffffffff, new float[16], 640, 480, 0, 0, 0, 0, -1);
		var semantic = original.withTerrainPlacement(placement);
		assertEquals(placement, semantic.terrainPlacement());
		assertNull(original.terrainPlacement());
		assertArrayEquals(new float[] {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}, semantic.transform());
		float[] detached = semantic.transform();
		detached[12] = 99;
		assertEquals(0, semantic.transform()[12]);
		var frame = new RustGalWorldPrimitiveRenderer.PrimitiveFrame(640, 480,
			semantic.transform(), semantic.transform(), new VulkanicGalBridge.WorldBackgroundRecord(false, 0, 0, 0, 0, 640, 480),
			List.of(), List.of(), List.of(), List.of(), List.of(semantic));
		var resized = RustGalWorldPrimitiveRenderer.withViewport(frame, 1280, 720);
		assertEquals(placement, resized.meshInstances().getFirst().terrainPlacement());
		assertEquals(1280, resized.meshInstances().getFirst().viewportWidth());
		assertArrayEquals(semantic.transform(), resized.meshInstances().getFirst().transform());
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.TerrainSectionPlacement(1, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldMeshInstanceRecord(
			60, 41L, 2L, -1, 0, 0, 0, 0xffffffff, detached, 640, 480, 0, 0, 0, 0, -1, placement));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldMeshInstanceRecord(
			67, 41L, 2L, -1, 0, 0, 0, 0xffffffff, semantic.transform(), 640, 480, 0, 0, 0, 0, -1, placement));
	}

	@Test
	void viewportResizePreservesWorldAndFirstPersonFoilSemantics() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		float[] transform = new org.joml.Matrix4f().translation(0.3f, -0.2f, 0.7f).get(new float[16]);
		var plain = new VulkanicGalBridge.WorldMeshInstanceRecord(
			67, 41L, 2L, -1, 0, 0, 0, 0xffffffff, transform, 640, 480).withModelSubmissionOrder(-4);
		var foil = plain.withItemFoil(new VulkanicGalBridge.StandardItemFoilRecord(12345L, 0.25, 0.5f));
		var worldDecal = foil.withDecalFoil(new VulkanicGalBridge.WorldDecalFoilRecord(false, false,
			transform, new float[]{2,0,0,0,3,0,0,0,-4}));
		var handDecal = foil.withDecalFoil(new VulkanicGalBridge.WorldDecalFoilRecord(true, true,
			transform, new float[]{1,0,0,0,1,0,0,0,1}));
		var frame = new RustGalWorldPrimitiveRenderer.PrimitiveFrame(640, 480, transform, transform,
			new VulkanicGalBridge.WorldBackgroundRecord(false, 0, 0, 0, 0, 640, 480),
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(plain, foil, worldDecal), List.of(),
			VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled(),
			VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled(),
			VulkanicGalBridge.WorldFeatureCoverageRecord.empty(), List.of(),
			VulkanicGalBridge.WorldLodRenderFrameRecord.disabled(), 0,
			VulkanicGalBridge.WorldFirstPersonFrameRecord.disabled(), List.of(plain, foil, handDecal));
		assertSame(frame, RustGalWorldPrimitiveRenderer.withViewport(frame, 640, 480));
		var resized = RustGalWorldPrimitiveRenderer.withViewport(frame, 1280, 720);
		assertSame(worldDecal.decalFoil(), resized.meshInstances().get(2).decalFoil());
		assertSame(handDecal.decalFoil(), resized.firstPersonMeshInstances().get(2).decalFoil());
		for (var instances : List.of(resized.meshInstances(), resized.firstPersonMeshInstances())) {
			assertNull(instances.getFirst().itemFoil());
			assertEquals(foil.itemFoil(), instances.get(1).itemFoil());
			for (var instance : instances) {
				assertEquals(-4, instance.modelSubmissionOrder());
				assertEquals(1280, instance.viewportWidth());
				assertEquals(720, instance.viewportHeight());
				assertArrayEquals(transform, instance.transform());
				assertEquals(plain.meshKey(), instance.meshKey());
				assertEquals(plain.meshGeneration(), instance.meshGeneration());
			}
		}
		assertEquals(640, frame.firstPersonMeshInstances().get(1).viewportWidth());
		assertEquals(foil.itemFoil(), frame.firstPersonMeshInstances().get(1).itemFoil());
	}

	@Test
	void firstPersonMeshConvenienceRecordUsesExplicitNoScopeIdentity() {
		assertEquals(67, VulkanicGalBridge.WORLD_MESH_ENTITY_STRATUM);
		assertEquals(71, VulkanicGalBridge.WORLD_MESH_ORDINARY_BLOCK_STRATUM);
		VulkanicGalBridge.WorldMeshInstanceRecord firstPerson = new VulkanicGalBridge.WorldMeshInstanceRecord(
			VulkanicGalBridge.WORLD_MESH_ENTITY_STRATUM, 41L, 2L, 0,
			0, 0, 0, 0xffffffff, new float[16], 640, 480
		);
		assertEquals(0, firstPerson.flags());
		assertEquals(-1, firstPerson.blockEntityId());
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldMeshInstanceRecord(
			VulkanicGalBridge.WORLD_MESH_ENTITY_STRATUM, 41L, 2L, 0,
			0, 0, 0, 0xffffffff, new float[16], 640, 480,
			0, 0, 0, -1
		));
		assertThrows(IllegalArgumentException.class, () -> new VulkanicGalBridge.WorldTextQuadRecord(
			1L, 1L, 1L, false, 0, 0, 0xffffffff, 0.0, new float[16],
			new float[12], new float[8], -2
		));
	}


}
