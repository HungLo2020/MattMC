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
		int ownership = source.indexOf("WorldRenderRoutePolicy.currentFirstPersonItemRoute(true)");
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
	}
}
