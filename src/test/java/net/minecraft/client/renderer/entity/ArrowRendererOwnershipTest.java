package net.minecraft.client.renderer.entity;

import net.vulkanic.world.WorldRenderRoutePolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.minecraft.client.renderer.entity.ArrowRenderer.ArrowSubmitDisposition.DISABLED;
import static net.minecraft.client.renderer.entity.ArrowRenderer.ArrowSubmitDisposition.JAVA_COMPATIBILITY;
import static net.minecraft.client.renderer.entity.ArrowRenderer.ArrowSubmitDisposition.RUST_AVAILABLE;
import static net.minecraft.client.renderer.entity.ArrowRenderer.ArrowSubmitDisposition.RUST_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArrowRendererOwnershipTest {
	@Test
	void rustOwnerSeparatesSupportedAdmissionFromUnavailableState() {
		assertEquals(
			RUST_AVAILABLE,
			ArrowRenderer.classifyArrowSubmit(false, true, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			ArrowRenderer.classifyArrowSubmit(false, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
	}

	@Test
	void javaAndDisabledOwnersKeepTheirExistingDisposition() {
		assertEquals(
			JAVA_COMPATIBILITY,
			ArrowRenderer.classifyArrowSubmit(false, false, WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			ArrowRenderer.classifyArrowSubmit(false, true, WorldRenderRoutePolicy.Route.RUST_OPENGL_BORROWED_CONTEXT)
		);
		assertEquals(
			DISABLED,
			ArrowRenderer.classifyArrowSubmit(false, true, WorldRenderRoutePolicy.Route.DISABLED)
		);
	}

	@Test
	void semanticCoverageRemainsObservableWithoutAuthorizingRuntimeFallback() {
		assertEquals(
			JAVA_COMPATIBILITY,
			ArrowRenderer.classifyArrowSubmit(true, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
	}

	@Test
	void productionCallsiteUsesExplicitOwnershipAndHasNoUnsupportedArrowCrashFallback() throws IOException {
		String source = Files.readString(Path.of(
			System.getProperty("user.dir"),
			"src/main/java/net/minecraft/client/renderer/entity/ArrowRenderer.java"
		));
		int ownershipQuery = source.indexOf("WorldRenderRoutePolicy.currentArrowOwnershipRoute()");
		int classification = source.indexOf("classifyArrowSubmit(", ownershipQuery);
		int unavailable = source.indexOf("ArrowSubmitDisposition.RUST_UNAVAILABLE", classification);
		int javaSubmit = source.indexOf("submitNodeCollector.submitModel(", unavailable);

		assertTrue(ownershipQuery >= 0, "Arrow callsite must resolve ownership explicitly");
		assertTrue(classification > ownershipQuery, "Arrow disposition must be classified after ownership is resolved");
		assertTrue(unavailable > classification, "Rust-unavailable handling must be explicit");
		assertTrue(javaSubmit > unavailable, "Java submit must remain outside the Rust-unavailable branch");
		assertFalse(source.contains("currentArrowRoute(true)"), "Arrow ownership must not be inferred by pretending admission succeeded");
		assertFalse(
			source.contains("Rust whole-frame Arrow encountered unsupported semantic state before route selection"),
			"Unsupported Rust-owned Arrow state must fail closed rather than crash as routing control flow"
		);
	}
}
