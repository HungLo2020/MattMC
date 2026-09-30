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
	void disabledOwnerKeepsItsDisposition() {
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

}
