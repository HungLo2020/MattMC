package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;

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
		return WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Disposition classify(
		boolean semanticCoverageOnly,
		boolean eligible,
		WorldRenderRoutePolicy.Route ownership
	) {
		if (semanticCoverageOnly) {
			return Disposition.JAVA_COMPATIBILITY;
		}
		if (ownership == WorldRenderRoutePolicy.Route.DISABLED) {
			return Disposition.RUST_UNAVAILABLE;
		}
		if (!ownership.usesRustWholeFrameVulkan()) {
			return Disposition.JAVA_COMPATIBILITY;
		}
		return eligible ? Disposition.RUST_AVAILABLE : Disposition.RUST_UNAVAILABLE;
	}
}
