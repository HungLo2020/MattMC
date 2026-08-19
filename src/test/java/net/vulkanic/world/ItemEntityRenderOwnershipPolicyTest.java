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
	}

	@Test
	void javaAndDiagnosticControlsRetainCompatibilityOwnership() {
		assertEquals(
			JAVA_COMPATIBILITY,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, false, false, false)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(false, false, false, false)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, true, false, true)
		);
		assertEquals(
			DISABLED,
			ItemEntityRenderOwnershipPolicy.selectOwnershipForTests(true, true, true, false)
		);
	}
}
