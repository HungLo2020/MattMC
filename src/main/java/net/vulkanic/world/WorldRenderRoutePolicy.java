package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;

public final class WorldRenderRoutePolicy {
	private WorldRenderRoutePolicy() {
	}

	public enum Route {
		DISABLED,
		JAVA_COMPATIBILITY,
		RUST_OPENGL_BORROWED_CONTEXT,
		RUST_VULKAN_WHOLE_FRAME;

		public boolean usesRustOpenGl() {
			return this == RUST_OPENGL_BORROWED_CONTEXT;
		}

		public boolean usesRustWholeFrameVulkan() {
			return this == RUST_VULKAN_WHOLE_FRAME;
		}

		public boolean usesJavaCompatibility() {
			return this == JAVA_COMPATIBILITY;
		}
	}

	public static Route currentBlockOutlineRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentCrackRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldCrack.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldCrack.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentWorldBorderRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBorder.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBorder.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentBackgroundRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBackground.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBackground.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentMaterialRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentBlockDisplayRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBlockDisplay.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBlockDisplay.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentFallingBlockRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentPistonMovingBlockRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPiston.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPiston.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentStaticTerrainRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static boolean staticTerrainBuildRequiresRustWholeFrameMetadata() {
		if (Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.disabled")) {
			return false;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.legacyControl")) {
			return false;
		}
		return RustGalVulkanWholeFrameMode.enabled();
	}

	public static Route selectRouteForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled,
		boolean legacyControl
	) {
		if (diagnosticsDisabled) {
			return Route.DISABLED;
		}
		if (legacyControl) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectRoute(vulkanBackendSelected, wholeFrameVulkanEnabled);
	}

	public static Route selectWholeFrameRouteForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled,
		boolean legacyControl
	) {
		if (diagnosticsDisabled) {
			return Route.DISABLED;
		}
		if (legacyControl) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(vulkanBackendSelected, wholeFrameVulkanEnabled);
	}

	private static Route selectRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		if (vulkanBackendSelected) {
			return wholeFrameVulkanEnabled ? Route.RUST_VULKAN_WHOLE_FRAME : Route.JAVA_COMPATIBILITY;
		}
		return Route.RUST_OPENGL_BORROWED_CONTEXT;
	}

	private static Route selectShaderAffectedRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		Route selected = selectRoute(vulkanBackendSelected, wholeFrameVulkanEnabled);
		if (selected.usesRustOpenGl() && net.irisshaders.iris.Iris.isPackInUseQuick()) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selected;
	}

	private static Route selectWholeFrameRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		if (vulkanBackendSelected && wholeFrameVulkanEnabled) {
			return Route.RUST_VULKAN_WHOLE_FRAME;
		}
		return Route.JAVA_COMPATIBILITY;
	}

	private static boolean rustWholeFrameShellActive() {
		return RustGalVulkanWholeFrameMode.enabledForBackend(VulkanicAPI.isVulkanBackendSelected());
	}
}
