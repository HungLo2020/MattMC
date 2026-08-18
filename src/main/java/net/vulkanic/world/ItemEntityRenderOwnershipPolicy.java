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
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.disabled")) {
			return WorldRenderRoutePolicy.Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.legacyControl")) {
			return WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
		}
		boolean vulkanBackendSelected = VulkanicAPI.isVulkanBackendSelected();
		return RustGalVulkanWholeFrameMode.enabledForBackend(vulkanBackendSelected)
			? WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME
			: WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY;
	}
}
