package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static net.vulkanic.world.LivingEntityBaseModelOwnershipPolicy.Disposition.JAVA_COMPATIBILITY;
import static net.vulkanic.world.LivingEntityBaseModelOwnershipPolicy.Disposition.RUST_AVAILABLE;
import static net.vulkanic.world.LivingEntityBaseModelOwnershipPolicy.Disposition.RUST_UNAVAILABLE;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.DISABLED;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LivingEntityBaseModelOwnershipPolicyTest {
	@Test
	void migratedFamilyOwnershipIsIndependentOfCurrentStateEligibility() {
		assertEquals(
			RUST_VULKAN_WHOLE_FRAME,
			LivingEntityBaseModelOwnershipPolicy.selectOwnershipForTests(true, true, true, false, false)
		);
	}

	@Test
	void unportedFamilyNeverClaimsRustOwnership() {
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.selectOwnershipForTests(false, true, true, false, false)
		);
	}

	@Test
	void controlsAndNonWholeFrameRoutesKeepCompatibilitySemantics() {
		assertEquals(
			DISABLED,
			LivingEntityBaseModelOwnershipPolicy.selectOwnershipForTests(true, true, true, true, false)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.selectOwnershipForTests(true, true, true, false, true)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.selectOwnershipForTests(true, true, false, false, false)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.selectOwnershipForTests(true, false, false, false, false)
		);
	}

	@Test
	void migratedRustOwnedBaseModelSeparatesAdmissionFromOwnership() {
		assertEquals(
			RUST_AVAILABLE,
			LivingEntityBaseModelOwnershipPolicy.classify(false, true, true, RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			RUST_UNAVAILABLE,
			LivingEntityBaseModelOwnershipPolicy.classify(false, true, false, RUST_VULKAN_WHOLE_FRAME)
		);
	}

	@Test
	void unportedAndSemanticSubmissionsRemainJavaObservable() {
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(false, false, false, RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(true, true, false, RUST_VULKAN_WHOLE_FRAME)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(false, true, false, WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY)
		);
		assertEquals(
			JAVA_COMPATIBILITY,
			LivingEntityBaseModelOwnershipPolicy.classify(false, true, true, DISABLED)
		);
	}
}
