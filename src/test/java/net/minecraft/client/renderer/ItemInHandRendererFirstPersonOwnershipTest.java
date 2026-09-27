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
	void productionCallsiteSeparatesOwnershipFromRustReadiness() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java"
		));
		int ownership = source.indexOf("WorldRenderRoutePolicy.currentFirstPersonItemOwnershipRoute()");
		int enqueue = source.indexOf("boolean rustSubmitted = RustGalWorldPrimitiveRenderer.enqueueFirstPersonItemMesh(");
		int classify = source.indexOf("FirstPersonItemSubmitDisposition disposition = classifyFirstPersonItemSubmit(", enqueue);
		int javaSubmit = source.indexOf("itemStackRenderState.submit(", enqueue);

		assertTrue(ownership >= 0, "first-person Java callsite must resolve ownership before deciding fallback");
		assertTrue(enqueue > ownership, "ownership must be resolved before the Rust enqueue attempt");
		assertTrue(classify > enqueue, "Rust enqueue result must be classified against whole-frame ownership");
		assertTrue(javaSubmit > classify, "Java compatibility submit must remain behind the ownership classifier");
		String guardedRegion = source.substring(classify, javaSubmit);
		assertTrue(
			guardedRegion.contains("disposition != FirstPersonItemSubmitDisposition.JAVA_COMPATIBILITY"),
			"Java item submission must be unreachable for submitted or unavailable Rust whole-frame outcomes"
		);
		assertTrue(guardedRegion.contains("Rust whole-frame first-person item route has no semantic mesh"),
			"an unavailable Rust hand item must abort rather than present an incomplete frame");
		assertTrue(source.contains("RustGalWorldPrimitiveRenderer.beginFirstPersonGuiCapture()"),
			"Rust first-person hands must install the semantic textured-quad sink");
		assertTrue(source.contains("collectFirstPersonTextSemantics"),
			"map decoration labels must use the copied Rust text contract");
		assertTrue(source.contains("itemStack.getItem() instanceof TaczMvpGunItem")
			&& source.contains("renderTaczGlockFirstPerson"),
			"TACZ hands must use their copied semantic producer on the Rust route");
		assertTrue(!source.contains("rustWholeFrame && (itemStack.isEmpty()"),
			"empty-hand model parts must remain admitted to the Rust semantic route");
	}

	@Test
	void unexpectedNonFirstPersonContextCannotReopenJavaOnVulkan() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java"
		));
		int context = source.indexOf("itemDisplayContext.firstPerson()");
		int disabled = source.indexOf("WorldRenderRoutePolicy.Route.DISABLED", context);
		assertTrue(context >= 0 && disabled > context,
			"non-first-person item contexts must be unavailable on selected Vulkan");
		assertTrue(source.substring(context, Math.min(source.length(), disabled + 320))
			.contains("isVulkanBackendSelected()"),
			"the non-first-person ownership branch must be selected-Vulkan aware");
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
