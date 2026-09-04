package net.vulkanic;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TaczSemanticIsolationTest {
	@Test
	void reticleDiagnosticsFenceCompatibilityStateBeforeAnyIrisQueries() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		int method = source.indexOf("private static void logReticleDebug(");
		int guard = source.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		int irisRead = source.indexOf("HandRenderer.INSTANCE.isActive()", method);
		assertTrue(method >= 0 && guard > method && irisRead > guard,
			"TACZ reticle diagnostics must fence Vulkan before reading Iris compatibility state");
	}

	@Test
	void rustWholeFrameGunRouteUsesOnlySemanticMeshSubmission() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		int route = source.indexOf("if (rustWholeFrame) {");
		int routeEnd = source.indexOf("} else if (scopedAttachment != null)", route);
		assertTrue(route >= 0 && routeEnd > route, "missing TACZ Rust whole-frame admission branch");
		String rustBranch = source.substring(route, routeEnd);
		assertTrue(rustBranch.contains("submitSemanticBedrockRoots"),
			"Rust TACZ gun meshes must use the semantic Bedrock quad ABI");
		assertTrue(rustBranch.contains("submitAttachments"),
			"Rust TACZ attachments must remain on the semantic attachment route");
		assertTrue(!rustBranch.contains("submitCustomGeometrySemantic"),
			"Rust TACZ admission must not enqueue arbitrary Java custom geometry");
	}

	@Test
	void opticalAttachmentsUseExplicitStencilMaterialModes() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		int method = source.indexOf("private boolean submitSemanticOpticalAttachment(");
		int methodEnd = source.indexOf("\n\tprivate boolean submitSemanticBedrockRootsWithMode(", method);
		assertTrue(method >= 0 && methodEnd > method, "missing TACZ optical semantic submission method");
		String optical = source.substring(method, methodEnd);
		assertTrue(optical.contains("MATERIAL_MODE_OPTICAL_STENCIL_WRITE"),
			"TACZ ocular geometry must explicitly write the Rust-owned stencil value");
		assertTrue(optical.contains("MATERIAL_MODE_OPTICAL_STENCIL_TEST"),
			"TACZ attachment geometry must explicitly test the Rust-owned stencil value");
	}

	@Test
	void semanticBedrockAdmissionRejectsNonFiniteTransformedGeometry() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		assertTrue(source.contains("Rust TACZ semantic Bedrock mesh contains non-finite transformed geometry"),
			"TACZ semantic admission must fail closed before publishing NaN or infinite vertex payloads");
	}

	@Test
	void semanticBedrockBatchUsesOneColorPerQuadForRustAbi() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		int append = source.indexOf("private void append(org.joml.Matrix4f transform, BedrockPolygon polygon");
		int appendEnd = source.indexOf("private static final class SemanticBedrockBudget", append);
		assertTrue(append >= 0 && appendEnd > append, "missing semantic Bedrock batch append method");
		String body = source.substring(append, appendEnd);
		assertTrue(body.contains("colorList.add(0xFFFFFFFF);"),
			"semantic Bedrock batches must provide one color record for each quad");
		assertTrue(!body.contains("for (int vertex = 0; vertex < 4; vertex++) colorList.add"),
			"per-vertex colors violate the Rust first-person quad ABI cardinality");
	}

	@Test
	void guiTaczCaptureNormalizesRustQuadColorsToPerVertexGuiColors() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/gui/RustGalGuiItemRenderer.java"));
		int add = source.indexOf("private boolean add(ResourceLocation texture, float[] vertices");
		int addEnd = source.indexOf("private int totalQuads()", add);
		assertTrue(add >= 0 && addEnd > add, "missing TACZ GUI semantic capture method");
		String body = source.substring(add, addEnd);
		assertTrue(body.contains("colors.length != quadCount"),
			"GUI capture must accept the Rust one-color-per-quad contract");
		assertTrue(body.contains("perVertexColors"),
			"GUI capture must expand semantic quad colors for its vertex records");
	}
}
