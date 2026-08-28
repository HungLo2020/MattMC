package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression contracts for the vanilla translucent-fluid migration slice.
 * These intentionally inspect the admission boundary: a fluid must become
 * copied semantic material data before Rust publication, and an unsupported
 * primitive must fail closed rather than reaching a Java Vulkan draw.
 */
final class FluidSemanticContractTest {
	private static String read(String relativePath) throws Exception {
		return Files.readString(Path.of(relativePath));
	}

	@Test
	void builtinWaterUsesExplicitStillFlowOrOverlayMaterialSelection() throws Exception {
		String source = read("src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java");
		int selector = source.indexOf("private static FluidSpriteAsset waterTextureForPrimitive");
		int still = source.indexOf("allVerticesWithin(vertices, still)", selector);
		int overlay = source.indexOf("allVerticesWithin(vertices, overlay)", still);
		int flow = source.indexOf("allVerticesWithin(vertices, flow)", overlay);
		int reject = source.indexOf("built-in water primitive UVs do not match still, flow, or overlay sprites", flow);
		assertTrue(selector >= 0 && still > selector && overlay > still && flow > overlay && reject > flow,
			"built-in water must select an explicit copied sprite or reject malformed UV semantics");
	}

	@Test
	void waterVerticesAreRewrittenToRustLocalUvAndMaterialIdentity() throws Exception {
		String source = read("src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java");
		int method = source.indexOf("private static int translucentTextureForPrimitive");
		int rewrite = source.indexOf("vertices.set(index, new VulkanicGalBridge.WorldMeshVertexRecord", method);
		int localU = source.indexOf("clamp01(asset.localU(original.u()))", rewrite);
		int material = source.indexOf("waterShaderMaterialType(asset.textureId())", localU);
		assertTrue(method >= 0 && rewrite > method && localU > rewrite && material > localU,
			"water primitives must cross the boundary with Rust-local UVs and explicit material identity");
	}

	@Test
	void unsupportedFluidMetadataIsRejectedBeforeSectionPublication() throws Exception {
		String source = read("src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java");
		int admission = source.indexOf("private static void acceptLayer");
		int reject = source.indexOf("asset.unsupportedPrimitiveCount() > 0", admission);
		int register = source.indexOf("registerStaticTerrainMeshAsset", admission);
		assertTrue(admission >= 0 && reject > admission && register > reject,
			"unsupported fluid metadata must be rejected before Rust mesh registration");
	}

	@Test
	void wholeFrameTerrainAdmissionDoesNotInvokeJavaTranslucentRendering() throws Exception {
		String levelRenderer = read("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
		int enqueue = levelRenderer.indexOf("enqueueRustGalStaticTerrainForWholeFrame");
		assertTrue(enqueue >= 0,
			"Rust whole-frame terrain route must have an explicit semantic enqueue callsite");
		String weather = read("src/main/java/net/minecraft/client/renderer/WeatherEffectRenderer.java");
		int render = weather.indexOf("public void render(");
		int fence = weather.indexOf("Java weather rendering is unavailable", render);
		assertTrue(render >= 0 && fence > render,
			"Java weather/translucent rendering must be fenced when Rust owns Vulkan presentation");
	}
}
