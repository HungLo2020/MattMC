package net.minecraft.client.renderer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.minecraft.client.renderer.ItemInHandRenderer.FirstPersonItemSubmitDisposition.JAVA_COMPATIBILITY;
import static net.minecraft.client.renderer.ItemInHandRenderer.FirstPersonItemSubmitDisposition.RUST_SUBMITTED;
import static net.minecraft.client.renderer.ItemInHandRenderer.FirstPersonItemSubmitDisposition.RUST_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemInHandRendererFirstPersonOwnershipTest {
	private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

	@Test
	void successfulRustSubmissionConsumesTheCallsite() {
		assertEquals(RUST_SUBMITTED, ItemInHandRenderer.classifyFirstPersonItemSubmit(true, true));
		assertEquals(RUST_SUBMITTED, ItemInHandRenderer.classifyFirstPersonItemSubmit(true, false));
	}

	@Test
	void rustWholeFrameFailureFailsClosedInsteadOfFallingBackToJava() {
		assertEquals(RUST_UNAVAILABLE, ItemInHandRenderer.classifyFirstPersonItemSubmit(false, true));
	}

	@Test
	void javaOwnedRouteRetainsCompatibilitySubmission() {
		assertEquals(JAVA_COMPATIBILITY, ItemInHandRenderer.classifyFirstPersonItemSubmit(false, false));
	}

	@Test
	void rustFirstPersonAdmissionRejectsNullCopiedLayersBeforeExtraction() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"
		));
		int method = source.indexOf("public static boolean enqueueFirstPersonItemMesh(");
		int loop = source.indexOf("itemState.forEachSemanticLayer(layer ->", method);
		int guard = source.indexOf("layer == null", loop);
		int extraction = source.indexOf("layer.renderType()", guard);
		assertTrue(method >= 0 && loop > method && guard > loop && extraction > guard,
			"first-person semantic layers must be null-checked before extraction");
	}

	@Test
	void rustWholeFrameHandsPreserveVanillaCameraSpacePoseContract() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/GameRenderer.java"
		));
		int begin = source.indexOf("RustGalWorldPrimitiveRenderer.beginFirstPersonFrame(handProjection, view);");
		int render = source.indexOf("this.itemInHandRenderer.renderRustVulkanHands(", begin);
		assertTrue(begin >= 0 && render > begin,
			"Rust whole-frame hand extraction must run from the first-person semantic callsite");
		String region = source.substring(begin, Math.min(source.length(), render + 420));
		assertTrue(region.contains("PoseStack handPoseStack = new PoseStack()")
			&& region.contains("handPoseStack.last().pose().set(view).invert()"),
			"the semantic hand writer must receive the explicit inverse camera-space pose used by vanilla");
		int inverse = region.indexOf("handPoseStack.last().pose().set(view).invert()");
		int hurtBob = region.indexOf("this.bobHurt(handPoseStack, f)");
		int viewBob = region.indexOf("this.bobView(handPoseStack, f)");
		assertTrue(inverse >= 0 && hurtBob > inverse && viewBob > hurtBob,
			"Rust first-person extraction must preserve Frozen's inverse-view, hurt-bob, then view-bob pose ordering");
		assertTrue(region.contains("view,") && region.contains("projection"),
			"the explicit frame view and projection must be passed to Rust hand extraction");
		assertTrue(region.contains("handPoseStack"),
			"Rust hand extraction must use the prepared semantic hand pose rather than an unrelated identity pose");
	}
}
