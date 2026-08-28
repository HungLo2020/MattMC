package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static net.vulkanic.world.WorldRenderRoutePolicy.Route.DISABLED;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ItemEntityRenderOwnershipPolicyTest {
	@Test
	void rustVulkanOwnershipDoesNotDependOnLayerEligibility() {
		assertEquals(
			RUST_VULKAN_WHOLE_FRAME,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, true, false, false)
		);
		assertEquals(
			RUST_VULKAN_WHOLE_FRAME,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(false, true, false, false),
			"Rust whole-frame handoff must own dropped items before backend selection settles"
		);
	}

	@Test
	void selectedVulkanWithoutRustAdmissionFailsClosed() {
		assertEquals(
			DISABLED,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, false, false, false)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(false, false, false, false)
		);
		assertEquals(
			DISABLED,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, true, false, true)
		);
		assertEquals(
			DISABLED,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, true, true, false)
		);
	}
}
