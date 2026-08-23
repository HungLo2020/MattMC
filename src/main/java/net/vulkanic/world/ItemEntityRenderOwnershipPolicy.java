package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;

/**
 * Ownership policy for dropped-item entity rendering.
 *
 * <p>This decision is intentionally independent of the current item's semantic
 * representability. Once Rust Vulkan owns the frame, an unsupported dropped
 * item layer is unavailable for that frame; eligibility must never authorize a
 * Java/Fabric fallback after ownership has been selected.</p>
 */
public final class ItemEntityRenderOwnershipPolicy {
	private ItemEntityRenderOwnershipPolicy() {
	}

	public static WorldRenderRoutePolicy.Route currentOwnershipRoute() {
		return selectOwnership(
			VulkanicAPI.isVulkanBackendSelected(),
			RustGalVulkanWholeFrameMode.enabledForBackend(VulkanicAPI.isVulkanBackendSelected()),
			Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.disabled"),
			Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.legacyControl")
		);
	}

	static WorldRenderRoutePolicy.Route selectOwnershipForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean disabled,
		boolean legacyControl
	) {
		return selectOwnership(vulkanBackendSelected, wholeFrameVulkanEnabled, disabled, legacyControl);
	}

	private static WorldRenderRoutePolicy.Route selectOwnership(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean disabled,
		boolean legacyControl
	) {
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
}
