package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static net.vulkanic.world.WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.RUST_OPENGL_BORROWED_CONTEXT;
import static net.vulkanic.world.WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorldRenderRoutePolicyIrisIsolationTest {
	@Test
	void rustVulkanWholeFrameDoesNotConsultIrisRuntime() {
		AtomicInteger irisQueries = new AtomicInteger();
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(
			true,
			true,
			() -> {
				irisQueries.incrementAndGet();
				throw new AssertionError("Rust Vulkan whole-frame route must not query Iris runtime state");
			}
		);

		assertEquals(RUST_VULKAN_WHOLE_FRAME, route);
		assertEquals(0, irisQueries.get());
	}

	@Test
	void javaVulkanCompatibilityDoesNotConsultIrisRuntime() {
		AtomicInteger irisQueries = new AtomicInteger();
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(
			true,
			false,
			() -> {
				irisQueries.incrementAndGet();
				throw new AssertionError("Java Vulkan compatibility selection must not query Iris runtime state");
			}
		);

		assertEquals(JAVA_COMPATIBILITY, route);
		assertEquals(0, irisQueries.get());
	}

	@Test
	void borrowedRustOpenGlStillUsesIrisPackState() {
		AtomicInteger irisQueries = new AtomicInteger();

		WorldRenderRoutePolicy.Route shadersActive = WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(
			false,
			false,
			() -> {
				irisQueries.incrementAndGet();
				return true;
			}
		);
		WorldRenderRoutePolicy.Route shadersInactive = WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(
			false,
			false,
			() -> {
				irisQueries.incrementAndGet();
				return false;
			}
		);

		assertEquals(JAVA_COMPATIBILITY, shadersActive);
		assertEquals(RUST_OPENGL_BORROWED_CONTEXT, shadersInactive);
		assertEquals(2, irisQueries.get());
	}
}
