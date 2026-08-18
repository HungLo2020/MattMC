package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;

/**
 * Ownership policy for the bounded living-entity base-model family that already
 * has dedicated copied indexed-mesh Rust semantics.
 *
 * <p>The migrated-family decision is separate from current-state eligibility.
 * Unported living models remain Java-owned. Once a migrated base model is owned
 * by the Rust Vulkan whole-frame renderer, a rejected semantic state is
 * unavailable for that frame and must not authorize a Java base-model submit.</p>
 */
public final class LivingEntityBaseModelOwnershipPolicy {
	private LivingEntityBaseModelOwnershipPolicy() {
	}

	public enum Disposition {
		JAVA_COMPATIBILITY,
		RUST_AVAILABLE,
		RUST_UNAVAILABLE
	}

	public static WorldRenderRoutePolicy.Route currentOwnershipRoute(boolean migratedFamily) {
		if (!migratedFamily) {
			return WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
		}
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
		boolean semanticSubmission,
		boolean migratedFamily,
		boolean eligible,
		WorldRenderRoutePolicy.Route ownership
	) {
		if (semanticSubmission || !migratedFamily || !ownership.usesRustWholeFrameVulkan()) {
			return Disposition.JAVA_COMPATIBILITY;
		}
		return eligible ? Disposition.RUST_AVAILABLE : Disposition.RUST_UNAVAILABLE;
	}
}
