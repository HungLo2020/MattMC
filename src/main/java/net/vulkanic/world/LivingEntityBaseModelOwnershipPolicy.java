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
		boolean vulkanBackendSelected = VulkanicAPI.isVulkanBackendSelected();
		return selectOwnership(
			migratedFamily,
			vulkanBackendSelected,
			RustGalVulkanWholeFrameMode.enabledForBackend(vulkanBackendSelected),
			Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.disabled"),
			Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.legacyControl")
		);
	}

	static WorldRenderRoutePolicy.Route selectOwnershipForTests(
		boolean migratedFamily,
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean disabled,
		boolean legacyControl
	) {
		return selectOwnership(migratedFamily, vulkanBackendSelected, wholeFrameVulkanEnabled, disabled, legacyControl);
	}

	private static WorldRenderRoutePolicy.Route selectOwnership(
		boolean migratedFamily,
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean disabled,
		boolean legacyControl
	) {
		if (!migratedFamily) {
			return vulkanBackendSelected
				? WorldRenderRoutePolicy.Route.DISABLED
				: WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
		}
		if (disabled) {
			return WorldRenderRoutePolicy.Route.DISABLED;
		}
		if (legacyControl) {
			return vulkanBackendSelected
				? WorldRenderRoutePolicy.Route.DISABLED
				: WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
		}
		return vulkanBackendSelected && wholeFrameVulkanEnabled
			? WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			: vulkanBackendSelected
				? WorldRenderRoutePolicy.Route.DISABLED
				: WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
	}

	public static Disposition classify(
		boolean semanticSubmission,
		boolean migratedFamily,
		boolean eligible,
		WorldRenderRoutePolicy.Route ownership
	) {
		if (semanticSubmission) {
			return Disposition.JAVA_COMPATIBILITY;
		}
		if (ownership == WorldRenderRoutePolicy.Route.DISABLED) {
			return Disposition.RUST_UNAVAILABLE;
		}
		if (!migratedFamily) {
			return ownership.usesRustWholeFrameVulkan()
				? Disposition.RUST_UNAVAILABLE
				: Disposition.JAVA_COMPATIBILITY;
		}
		if (!ownership.usesRustWholeFrameVulkan()) {
			return Disposition.JAVA_COMPATIBILITY;
		}
		return eligible ? Disposition.RUST_AVAILABLE : Disposition.RUST_UNAVAILABLE;
	}
}
