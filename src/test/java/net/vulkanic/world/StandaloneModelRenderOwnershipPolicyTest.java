package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static net.vulkanic.world.StandaloneModelRenderOwnershipPolicy.Disposition.JAVA_COMPATIBILITY;
import static net.vulkanic.world.StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE;
import static net.vulkanic.world.StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StandaloneModelRenderOwnershipPolicyTest {
	@Test
	void rustOwnershipSeparatesAdmissionFromAvailability() {
		assertEquals(
			RUST_AVAILABLE,
			StandaloneModelRenderOwnershipPolicy.classify(false, true, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			StandaloneModelRenderOwnershipPolicy.classify(false, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
	}

	@Test
	void compatibilityAndDisabledOwnersRemainExplicitlyUnavailableOnRust() {
		assertEquals(
			JAVA_COMPATIBILITY,
			StandaloneModelRenderOwnershipPolicy.classify(false, false, WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			StandaloneModelRenderOwnershipPolicy.classify(false, false, WorldRenderRoutePolicy.Route.DISABLED)
		);
	}

	@Test
	void semanticCoverageNeverEnqueuesRuntimeRustWork() {
		assertEquals(
			JAVA_COMPATIBILITY,
			StandaloneModelRenderOwnershipPolicy.classify(true, true, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			StandaloneModelRenderOwnershipPolicy.classify(true, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME)
		);
	}
}
