package net.vulkanic.world;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;

import java.util.function.BooleanSupplier;

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
			// Java compatibility is a private OpenGL-only lowering. A stale route
			// value must not authorize Java rendering after Vulkan selection or while
			// the Rust whole-frame handoff is already active but backend selection has
			// not settled yet.
			return this == JAVA_COMPATIBILITY
				&& !VulkanicAPI.isVulkanBackendSelected()
				&& !RustGalVulkanWholeFrameMode.enabled();
		}
	}

	public static Route currentBlockOutlineRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentCrackRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldCrack.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldCrack.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentWorldBorderRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBorder.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBorder.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentBackgroundRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBackground.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBackground.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentMaterialRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Experience-orb billboards are selected only with complete Rust Vulkan frame
	 * ownership. Java OpenGL/Iris retain their private compatibility route;
	 * unadmitted Vulkan remains unavailable.
	 */
	public static Route currentExperienceOrbRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldExperienceOrb.disabled")) {
			return Route.DISABLED;
		}
		// The presenter shell owns the callsite before backend-selection state has
		// settled; a legacy diagnostic flag must not reopen Java compatibility
		// rendering during that handoff.
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return Route.RUST_VULKAN_WHOLE_FRAME;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldExperienceOrb.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Beacon beams are copied as ordinary translucent material quads only when
	 * Rust owns the complete Vulkan frame. Java OpenGL/Iris retain the existing
	 * custom-geometry producer; unadmitted Vulkan remains unavailable.
	 */
	public static Route currentBeaconBeamRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBeaconBeam.disabled")) {
			return Route.DISABLED;
		}
		// Beacon extraction can run during the same pre-selection handoff as the
		// rest of the Rust whole-frame shell. Keep the copied beam producer Rust-
		// owned instead of allowing a transient query to resolve to Java.
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return Route.RUST_VULKAN_WHOLE_FRAME;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBeaconBeam.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** VoxelMap beacon-only vertical beams have their own semantic producer. */
	public static Route currentVoxelMapBeaconRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalVoxelMapBeacon.disabled")) return Route.DISABLED;
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalVoxelMapBeacon.legacyControl")) return legacyCompatibilityRoute();
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Guardian attack beams use Rust only with their complete copied semantic primitive. */
	public static Route currentGuardianBeamRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldGuardianBeam.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return Route.RUST_VULKAN_WHOLE_FRAME;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldGuardianBeam.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** End Crystal beams use Rust only with their complete copied semantic primitive. */
	public static Route currentCrystalBeamRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldCrystalBeam.disabled")) return Route.DISABLED;
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldCrystalBeam.legacyControl")) return legacyCompatibilityRoute();
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Small textured billboard primitives use Rust only with their explicit copied quad ABI. */
	public static Route currentTexturedBillboardRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldTexturedBillboard.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldTexturedBillboard.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentFishingLineRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFishingLine.disabled")) return Route.DISABLED;
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFishingLine.legacyControl")) return legacyCompatibilityRoute();
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Debug hitbox lines use the same explicit Rust line primitive when enabled. */
	public static Route currentDebugLineRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldDebugLines.disabled")) return Route.DISABLED;
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldDebugLines.legacyControl")) return legacyCompatibilityRoute();
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Procedural colored quads use Rust only with complete frame ownership. */
	public static Route currentProceduralQuadRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldProceduralQuads.disabled")) return Route.DISABLED;
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldProceduralQuads.legacyControl")) return legacyCompatibilityRoute();
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Vanilla entity-fire uses Rust only with complete Vulkan frame ownership. */
	public static Route currentEntityFlameRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityFlame.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityFlame.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Vanilla entity shadows are copied as ordinary translucent material quads
	 * only when Rust owns the complete Vulkan frame. Java OpenGL retains the
	 * existing feature renderer; selected Vulkan is Rust-owned or unavailable,
	 * never a Java Vulkan compatibility route.
	 */
	public static Route currentEntityShadowRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityShadow.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityShadow.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Vanilla leash ribbons are copied from the extracted endpoint/light
	 * semantics only while Rust owns the complete Vulkan frame. Java OpenGL
	 * retains the existing renderer; unadmitted Vulkan remains unavailable.
	 */
	public static Route currentEntityLeashRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityLeash.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldEntityLeash.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Ordinary dropped-item baked quads use the shared indexed entity-mesh
	 * family only when the Rust Vulkan whole-frame presenter owns the frame.
	 * Java OpenGL retains its existing item renderer; unadmitted Vulkan remains
	 * unavailable.
	 */
	public static Route currentItemEntityMeshRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.disabled")) {
			return Route.DISABLED;
		}
		// Ownership is established by the Rust presenter shell before the Vulkan
		// selection bit necessarily settles. Keep legacy diagnostics from turning
		// this callsite back into a Java route during that interval.
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		Route ownership = currentItemEntityOwnershipRoute();
		return !eligible && ownership.usesRustWholeFrameVulkan() ? Route.DISABLED : ownership;
	}

	/**
	 * Returns the owner of dropped-item entity submissions independently of
	 * representability or the per-family disabled admission switch. A Rust
	 * whole-frame presenter still owns an unavailable item and must fail closed
	 * instead of allowing the collector to silently omit it.
	 */
	public static Route currentItemEntityOwnershipRoute() {
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldItemEntity.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Returns the owner of the first-person item callsite independently of the
	 * current item's representability or resource residency. A Rust whole-frame
	 * presenter owns this callsite even when the selected source hand executor is
	 * unavailable; unsupported hand variants then fail closed rather than leaking
	 * a Java submission into the Rust frame.
	 */
	public static Route currentFirstPersonItemOwnershipRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalFirstPersonItem.disabled")) {
			return Route.DISABLED;
		}
		// The whole-frame handoff owns first-person extraction before the backend
		// selection bit necessarily settles. Do not let a transient pre-selection
		// query resolve this callsite to Java compatibility rendering.
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return Route.RUST_VULKAN_WHOLE_FRAME;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalFirstPersonItem.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Per-item admission for the dedicated Rust-owned hand source pass. This is
	 * deliberately narrower than ownership: an ineligible item is unavailable
	 * to Rust, while {@link #currentFirstPersonItemOwnershipRoute()} decides
	 * whether Java is still legally allowed to render the callsite.
	 */
	public static Route currentFirstPersonItemRoute(boolean eligible) {
		Route ownership = currentFirstPersonItemOwnershipRoute();
		if (!eligible && ownership.usesRustWholeFrameVulkan()) {
			return Route.DISABLED;
		}
		if (!eligible && ownership != Route.DISABLED) {
			return legacyCompatibilityRoute();
		}
		return ownership;
	}

	/**
	 * Name tags use the private Rust world-text pass only when Rust owns the
	 * complete Vulkan frame. Java OpenGL retains its existing text renderer;
	 * unadmitted Vulkan remains unavailable and there is no borrowed font-atlas
	 * or same-frame fallback route.
	 */
	public static Route currentWorldTextRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldText.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldText.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Weather is a normal translucent material producer. Rust owns it whenever
	 * it owns the complete Vulkan frame; only OpenGL with Iris remains a Java
	 * compatibility route through the shared shader-aware policy.
	 */
	public static Route currentWeatherRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWeather.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWeather.legacyControl")) {
			return legacyCompatibilityRoute();
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
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalClouds.legacyControl")) {
			return legacyCompatibilityRoute();
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
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBlockDisplay.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentFallingBlockRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	public static Route currentPistonMovingBlockRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPiston.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPiston.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Primed TNT reuses the indexed baked-block route for its copied block mesh,
	 * flashing overlay, and optional outline-only instance. The producer decides
	 * whether the semantic block state is representable before invoking this
	 * policy; unsupported special-model variants remain unavailable rather than
	 * reopening a Java Vulkan pass.
	 */
	public static Route currentPrimedTntRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPrimedTnt.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPrimedTnt.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Returns the owner of the Arrow callsite independently of whether the
	 * current Arrow state is representable as the copied indexed-mesh semantic
	 * payload. Once Rust owns the whole Vulkan frame, failed Arrow admission is
	 * unavailable for that frame and must never authorize a Java entity draw.
	 */
	public static Route currentArrowOwnershipRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldArrow.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldArrow.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/** Per-Arrow admission for the Rust indexed-mesh producer. */
	public static Route currentArrowRoute(boolean eligible) {
		Route ownership = currentArrowOwnershipRoute();
		if (!eligible && ownership.usesRustWholeFrameVulkan()) {
			return Route.DISABLED;
		}
		if (!eligible && ownership != Route.DISABLED) {
			return Route.JAVA_COMPATIBILITY;
		}
		return ownership;
	}

	/**
	 * Bounded opaque/cutout {@code ModelPart} extraction for the Rust-owned
	 * whole-frame route. States that cannot be represented as a copied indexed
	 * mesh remain Java-compatible only outside Rust Vulkan ownership; under a
	 * Rust-owned Vulkan frame they are unavailable rather than fallback draws.
	 */
	public static Route currentModelMeshRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelMesh.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		Route ownership = selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
		return !eligible && ownership.usesRustWholeFrameVulkan() ? Route.DISABLED : ownership;
	}

	/**
	 * Bounded opaque/cutout {@code ModelPart} extraction for the Rust-owned
	 * whole-frame route. This is separate from {@link #currentModelMeshRoute}
	 * because ModelPart submits originate from block-entity and special-model
	 * producers rather than an EntityModel wrapper. Unsupported, sheeted,
	 * outlined, or translucent parts remain Java-compatible
	 * outside Rust Vulkan ownership and fail closed once that route owns the
	 * frame.
	 */
	public static Route currentModelPartMeshRoute(boolean eligible) {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelPart.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) {
			return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldModelPart.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		// The producer's explicit eligibility check is the admission boundary.
		// Once a complete Rust whole-frame route is selected, do not retain a
		// stale diagnostic opt-in that would silently leave an otherwise supported
		// ModelPart on Java's compatibility path.
		Route ownership = selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
		return !eligible && ownership.usesRustWholeFrameVulkan() ? Route.DISABLED : ownership;
	}

	public static Route currentStaticTerrainRoute() {
		if (Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.disabled")) {
			return Route.DISABLED;
		}
		if (RustGalVulkanWholeFrameMode.enabled()) return Route.RUST_VULKAN_WHOLE_FRAME;
		if (Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.legacyControl")) {
			return legacyCompatibilityRoute();
		}
		return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());
	}

	/**
	 * Distant Horizons is outside the vanilla Rust Vulkan migration slice.  It
	 * must therefore remain unavailable while the Rust whole-frame route owns a
	 * vanilla frame: admitting it here would make an ordinary Vulkan run depend
	 * on a separate, incomplete renderer family.  This is deliberately a route
	 * decision rather than a Java fallback; callers receive {@link Route#DISABLED}
	 * and no DH draw is authorized.
	 */
	public static Route currentDistantHorizonsOpaqueRoute() {
		return selectDistantHorizonsRoute(
			VulkanicAPI.isVulkanBackendSelected(),
			RustGalVulkanWholeFrameMode.enabled(),
			Boolean.getBoolean("mattmc.dev.rustGalDistantHorizons.disabled"),
			Boolean.getBoolean("mattmc.dev.rustGalDistantHorizons.legacyControl")
		);
	}

	static Route selectDistantHorizonsRouteForTests(
		boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled, boolean legacyControl
	) {
		return selectDistantHorizonsRoute(
			vulkanBackendSelected, wholeFrameVulkanEnabled, diagnosticsDisabled, legacyControl
		);
	}

	private static Route selectDistantHorizonsRoute(
		boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled, boolean legacyControl
	) {
		if (wholeFrameVulkanEnabled || vulkanBackendSelected || diagnosticsDisabled) {
			return Route.DISABLED;
		}
		return legacyControl ? legacyCompatibilityRoute()
			: selectWholeFrameRoute(false, false);
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
			return vulkanBackendSelected ? Route.DISABLED : Route.JAVA_COMPATIBILITY;
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
			return vulkanBackendSelected ? Route.DISABLED : Route.JAVA_COMPATIBILITY;
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
			return vulkanBackendSelected ? Route.DISABLED : Route.JAVA_COMPATIBILITY;
		}
		return selectShaderAffectedRoute(vulkanBackendSelected, wholeFrameVulkanEnabled, () -> irisPackActive);
	}

	static Route selectShaderAffectedRouteForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		BooleanSupplier irisPackActive
	) {
		return selectShaderAffectedRoute(vulkanBackendSelected, wholeFrameVulkanEnabled, irisPackActive);
	}

	private static Route selectRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		if (vulkanBackendSelected) {
			// A selected Vulkan device never authorizes Java rendering. Until the
			// explicit Rust presenter is admitted, keep the capability unavailable
			// rather than silently reopening the legacy Java Vulkan path.
			return wholeFrameVulkanEnabled ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
		}
		return Route.RUST_OPENGL_BORROWED_CONTEXT;
	}

	/**
	 * Iris runtime state is relevant only to the borrowed OpenGL compatibility
	 * route. Vulkan ownership is decided entirely from Vulkanic/MattMC state and
	 * must not consult Iris after selecting either unavailable Vulkan or the Rust
	 * Vulkan whole-frame renderer.
	 */
	private static Route selectShaderAffectedRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		return selectShaderAffectedRoute(
			vulkanBackendSelected,
			wholeFrameVulkanEnabled,
			net.irisshaders.iris.Iris::isPackInUseQuick
		);
	}

	private static Route selectShaderAffectedRoute(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		BooleanSupplier irisPackActive
	) {
		Route selected = selectRoute(vulkanBackendSelected, wholeFrameVulkanEnabled);
		if (!selected.usesRustOpenGl()) {
			return selected;
		}
		return irisPackActive.getAsBoolean() ? Route.JAVA_COMPATIBILITY : selected;
	}

	private static Route selectWholeFrameRoute(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		if (vulkanBackendSelected && wholeFrameVulkanEnabled) {
			return Route.RUST_VULKAN_WHOLE_FRAME;
		}
		return vulkanBackendSelected ? Route.DISABLED : Route.JAVA_COMPATIBILITY;
	}

	private static boolean rustWholeFrameShellActive() {
		return RustGalVulkanWholeFrameMode.enabledForBackend(VulkanicAPI.isVulkanBackendSelected());
	}

	/**
	 * A diagnostic legacy switch must never reopen a Java draw once the Rust
	 * Vulkan presenter owns the frame. Outside that shell it retains its
	 * compatibility meaning for the separate Java/OpenGL routes.
	 */
	private static Route legacyCompatibilityRoute() {
		return VulkanicAPI.isVulkanBackendSelected() ? Route.DISABLED : Route.JAVA_COMPATIBILITY;
	}
}
