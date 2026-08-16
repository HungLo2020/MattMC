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

	/**
	 * Experience-orb billboards are selected only with complete Rust Vulkan frame
	 * ownership. Java OpenGL/Iris and normal Java Vulkan retain the Java route.
	 */
	public static Route currentExperienceOrbRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldExperienceOrb.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldExperienceOrb.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Beacon beams are copied as ordinary translucent material quads only when
	 * Rust owns the complete Vulkan frame. Java OpenGL/Iris and normal Java
	 * Vulkan retain the existing custom-geometry producer.
	 */
	public static Route currentBeaconBeamRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBeaconBeam.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBeaconBeam.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Vanilla entity-fire uses Rust only with complete Vulkan frame ownership. */
	public static Route currentEntityFlameRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityFlame.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityFlame.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Vanilla entity shadows are copied as ordinary translucent material quads
	 * only when Rust owns the complete Vulkan frame. Java OpenGL and normal Java
	 * Vulkan retain the existing feature renderer; a selected Rust frame never
	 * emits both paths.
	 */
	public static Route currentEntityShadowRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityShadow.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityShadow.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Vanilla leash ribbons are copied from the extracted endpoint/light
	 * semantics only while Rust owns the complete Vulkan frame. Java OpenGL and
	 * normal Java Vulkan retain the existing renderer without mixed ownership.
	 */
	public static Route currentEntityLeashRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityLeash.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityLeash.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Ordinary dropped-item baked quads use the shared indexed entity-mesh
	 * family only when the Rust Vulkan whole-frame presenter owns the frame.
	 * Java OpenGL and normal Java Vulkan retain their existing item renderer.
	 */
	public static Route currentItemEntityMeshRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.legacyControl") || !eligible) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * First-person items require the dedicated Rust-owned hand source pass.
	 * Until the selected-source executor is explicitly requested, Java retains
	 * ownership before any item submit is selected; this avoids a mixed
	 * presenter or a speculative hand record in the ordinary whole-frame graph.
	 */
	public static Route currentFirstPersonItemRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalFirstPersonItem.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalFirstPersonItem.legacyControl")
			|| !eligible
			|| !RustGalWorldPrimitiveRenderer.requiresSelectedSourceFeatureCoverage()) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Name tags use the private Rust world-text pass only when Rust owns the
	 * complete Vulkan frame. Java OpenGL and normal Java Vulkan retain their
	 * existing text renderer; there is no borrowed font-atlas or same-frame
	 * fallback route.
	 */
	public static Route currentWorldTextRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldText.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldText.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Weather is a normal translucent material producer. Rust owns it whenever
	 * it owns the complete Vulkan frame; OpenGL with Iris and normal Java Vulkan
	 * remain Java compatibility routes through the shared shader-aware policy.
	 */
	public static Route currentWeatherRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWeather.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWeather.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Clouds are collected as copied vanilla face semantics only for the Rust
	 * Vulkan whole-frame route. Java OpenGL keeps its normal renderer until a
	 * private OpenGL cloud lowering exists; Java Vulkan never receives a mixed
	 * cloud draw once this route is selected.
	 */
	public static Route currentCloudRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalClouds.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalClouds.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		// The Rust shader runtime owns the selected pack's cloud disposition.
		// It either submits copied vanilla cloud faces or executes the admitted
		// Rust fullscreen cloud stage; a legacy diagnostic opt-in must not keep
		// normal Rust whole-frame gameplay on a second Java cloud path.
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
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

	/**
	 * Primed TNT reuses the indexed baked-block route only for its ordinary
	 * no-overlay state. The producer decides whether that semantic state is
	 * present before invoking this policy; flashing and outlined TNT remain on
	 * their existing Java compatibility route without a same-frame fallback.
	 */
	public static Route currentPrimedTntRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPrimedTnt.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPrimedTnt.legacyControl")) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Arrow models are the first ordinary entity-model producer on the shared
	 * indexed-mesh family. They are owned only by the Rust whole-frame route:
	 * Java OpenGL, including Iris, remains the compatibility owner until its
	 * private full-frame path is selected.
	 */
	public static Route currentArrowRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldArrow.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldArrow.legacyControl") || !eligible) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Bounded opaque/cutout {@code ModelPart} extraction for the Rust-owned
	 * whole-frame route. Java keeps every state that cannot be represented as a
	 * copied indexed mesh on its compatibility path before any draw is selected.
	 */
	public static Route currentModelMeshRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.legacyControl") || !eligible) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Bounded opaque/cutout {@code ModelPart} extraction for the Rust-owned
	 * whole-frame route. This is separate from {@link #currentModelMeshRoute}
	 * because ModelPart submits originate from block-entity and special-model
	 * producers rather than an EntityModel wrapper. Unsupported, animated,
	 * foil, sheeted, outlined, or translucent parts remain entirely Java-owned
	 * before route selection.
	 */
	public static Route currentModelPartMeshRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelPart.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelPart.legacyControl")
			|| !eligible) {
			return Route.JAVA_COMPATIBILITY;
		}
		// The producer's explicit eligibility check is the admission boundary.
		// Once a complete Rust whole-frame route is selected, do not retain a
		// stale diagnostic opt-in that would silently leave an otherwise supported
		// ModelPart on Java's compatibility path.
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	private static boolean selectedRustSourceWholeFrameRequested() {
		if (!System.getProperty(
			"mattmc.dev.deterministicCameraCapture.requiredRustSourceExecutionDir", ""
		).trim().isEmpty()) {
			return true;
		}
		String value = System.getenv("MATTMC_RUST_SELECTED_SOURCE_EXECUTION");
		return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
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

	/**
	 * The DH route is deliberately narrower than the ordinary whole-frame world
	 * route. The real DH render-list preflight selects Rust only after it proves
	 * every visible segment is representable. Rejected frames remain wholly Java
	 * owned before drawing; this method never authorizes a same-frame fallback.
	 */
	public static Route currentDistantHorizonsOpaqueRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalDistantHorizons.disabled")) {
			return Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalDistantHorizons.legacyControl")) {
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

	public static Route selectShaderAffectedRouteForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean irisPackActive,
		boolean diagnosticsDisabled,
		boolean legacyControl
	) {
		if (diagnosticsDisabled) {
			return Route.DISABLED;
		}
		if (legacyControl) {
			return Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(vulkanBackendSelected, wholeFrameVulkanEnabled, irisPackActive);
	}

	private static Route selectRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		if (vulkanBackendSelected) {
			return wholeFrameVulkanEnabled ? Route.RUST_VULKAN_WHOLE_FRAME : Route.JAVA_COMPATIBILITY;
		}
		return Route.RUST_OPENGL_BORROWED_CONTEXT;
	}

	private static Route selectShaderAffectedRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		return selectShaderAffectedRoute(
			vulkanBackendSelected,
			wholeFrameVulkanEnabled,
			net.irisshaders.iris.Iris.isPackInUseQuick()
		);
	}

	private static Route selectShaderAffectedRoute(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean irisPackActive
	) {
		Route selected = selectRoute(vulkanBackendSelected, wholeFrameVulkanEnabled);
		if (selected.usesRustOpenGl() && irisPackActive) {
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
