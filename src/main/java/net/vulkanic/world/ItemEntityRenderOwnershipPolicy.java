package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;

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
		return WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME;
	}
}
