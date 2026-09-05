package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the cloud producer's pre-allocation resource admission boundary. */
final class CloudSemanticAdmissionTest {
	@Test
	void cloudExpansionPreflightsWorstCaseFacesBeforeTemporaryListAllocation() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static void enqueueWorldCloudFaces(");
		int preflight = source.indexOf("long worstCaseFaces = candidateCells * 12L;", method);
		int rejection = source.indexOf("cloud route exceeds bounded material-quad frame capacity before expansion", preflight);
		int allocation = source.indexOf("List<VulkanicGalBridge.WorldMaterialQuadRecord> quads = new ArrayList<>();", method);
		assertTrue(method >= 0 && preflight > method && rejection > preflight && allocation > rejection,
			"cloud admission must reject oversized worst-case expansion before allocating temporary quads");
	}

	@Test
	void fancyCloudSemanticRecordsPreserveFrozenDepthWriteContract() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int fancyEmitter = source.indexOf("private static void appendCloudFace(");
		int fancyCull = source.indexOf("CULL_BACK", fancyEmitter);
		int sharedEmitter = source.indexOf("private static void appendCloudFaceWithCull(");
		int generatedWhite = source.indexOf("MATERIAL_TEXTURE_GENERATED_WHITE", sharedEmitter);
		int depthWrite = source.indexOf("DEPTH_POLICY_TEST_WRITE", generatedWhite);
		int cullPolicy = source.indexOf("cullPolicy", depthWrite);
		int fastCloud = source.indexOf("CULL_NONE", source.indexOf("if (!fancy)"));
		assertTrue(
			fancyEmitter >= 0 && fancyCull > fancyEmitter
				&& sharedEmitter > fancyCull && generatedWhite > sharedEmitter
				&& depthWrite > generatedWhite && cullPolicy > depthWrite && fastCloud >= 0,
			"Rust cloud semantics must preserve Frozen CLOUDS LEQUAL depth writes, use back-face culling for fancy clouds, and retain FLAT_CLOUDS no-cull"
		);
	}

	@Test
	void cloudParityMayReplayOnlyExplicitFrozenLightmapScalars() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		int lightmap = source.indexOf("public static net.minecraft.client.renderer.LightTexture.RustSemanticLightmapInputs lightmapInputsForCapture(");
		int guard = source.indexOf("hasBaselineLightmapInputsForCurrentPose()", lightmap);
		int helper = source.indexOf("private static boolean hasBaselineLightmapInputsForCurrentPose()");
		int property = source.indexOf("LIGHTMAP_CAPTURE_INPUTS", helper);
		assertTrue(lightmap >= 0 && guard > lightmap && helper > guard && property > helper,
			"cloud parity must replay a Frozen lightmap only when an explicit per-pose scalar receipt exists");
		String method = source.substring(lightmap, helper);
		assertTrue(!method.contains("WEATHER_SCENARIO"),
			"lightmap replay is shared vanilla capture state, not a weather-only rendering switch");
	}
}
