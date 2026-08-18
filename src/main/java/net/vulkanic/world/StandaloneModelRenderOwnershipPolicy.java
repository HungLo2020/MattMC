package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;

/**
 * Ownership and admission policy for dedicated standalone model producers that
 * already have a complete copied indexed-mesh Rust path.
 *
 * <p>Ownership is independent of whether the current model state is
 * representable. Once Rust owns the Vulkan frame, an unsupported standalone
 * model is unavailable for that frame and must not escape through Java.</p>
 */
public final class StandaloneModelRenderOwnershipPolicy {
	private StandaloneModelRenderOwnershipPolicy() {
	}

	public enum Disposition {
		JAVA_COMPATIBILITY,
		RUST_AVAILABLE,
		RUST_UNAVAILABLE
	}

	public static WorldRenderRoutePolicy.Route currentOwnershipRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.disabled")) {
			return WorldRenderRoutePolicy.Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.legacyControl")) {
			return WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
		}
		boolean vulkanBackendSelected = VulkanicAPI.isVulkanBackendSelected();
		return RustGalVulkanWholeFrameMode.enabledForBackend(vulkanBackendSelected)
			? WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			: WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
	}

	public static Disposition classify(
		boolean semanticCoverageOnly,
		boolean eligible,
		WorldRenderRoutePolicy.Route ownership
	) {
		if (semanticCoverageOnly || !ownership.usesRustWholeFrameVulkan()) {
			return Disposition.JAVA_COMPATIBILITY;
		}
		return eligible ? Disposition.RUST_AVAILABLE : Disposition.RUST_UNAVAILABLE;
	}
}
