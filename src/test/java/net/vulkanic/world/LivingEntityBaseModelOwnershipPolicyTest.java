package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static net.vulkanic.world.LivingEntityBaseModelOwnershipPolicy.Disposition.JAVA_COMPATIBILITY;
import static net.vulkanic.world.LivingEntityBaseModelOwnershipPolicy.Disposition.RUST_AVAILABLE;
import static net.vulkanic.world.LivingEntityBaseModelOwnershipPolicy.Disposition.RUST_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LivingEntityBaseModelOwnershipPolicyTest {
	@Test
	void migratedRustOwnedBaseModelSeparatesAdmissionFromOwnership() {
		assertEquals(
			RUST_AVAILABLE,
			LivingEntityBaseModelOwnershipPolicy.classify(
				false, true, true, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			LivingEntityBaseModelOwnershipPolicy.classify(
				false, true, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			)
		);
	}

	@Test
	void unportedFamiliesRemainJavaOwnedEvenInRustWholeFrame() {
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(
				false, false, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			)
		);
	}

	@Test
	void semanticAndCompatibilityRoutesRemainObservable() {
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(
				true, true, false, WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(
				false, true, false, WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY
			)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(
				false, true, true, WorldRenderRoutePolicy.Route.DISABLED
			)
		);
	}
}
