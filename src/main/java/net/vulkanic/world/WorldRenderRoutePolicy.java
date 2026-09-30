package net.vulkanic.world;


public final class WorldRenderRoutePolicy {
	private WorldRenderRoutePolicy() {
	}

	public enum Route {
		DISABLED,
		RUST_VULKAN_WHOLE_FRAME;

		public boolean usesRustWholeFrameVulkan() {
			return this == RUST_VULKAN_WHOLE_FRAME;
		}

	}

	public static Route currentBlockOutlineRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentCrackRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentWorldBorderRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentBackgroundRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentMaterialRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Experience-orb billboards are selected only with complete Rust Vulkan frame
	 * ownership. Java OpenGL/Iris retain their private compatibility route;
	 * unadmitted Vulkan remains unavailable.
	 */
	public static Route currentExperienceOrbRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Beacon beams are copied as ordinary translucent material quads only when
	 * Rust owns the complete Vulkan frame. Java OpenGL/Iris retain the existing
	 * custom-geometry producer; unadmitted Vulkan remains unavailable.
	 */
	public static Route currentBeaconBeamRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** Guardian attack beams use Rust only with their complete copied semantic primitive. */
	public static Route currentGuardianBeamRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** End Crystal beams use Rust only with their complete copied semantic primitive. */
	public static Route currentCrystalBeamRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** Small textured billboard primitives use Rust only with their explicit copied quad ABI. */
	public static Route currentTexturedBillboardRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentFishingLineRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** Debug hitbox lines use the same explicit Rust line primitive when enabled. */
	public static Route currentDebugLineRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** Procedural colored quads use Rust only with complete frame ownership. */
	public static Route currentProceduralQuadRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** Vanilla entity-fire uses Rust only with complete Vulkan frame ownership. */
	public static Route currentEntityFlameRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Vanilla entity shadows are copied as ordinary translucent material quads
	 * only when Rust owns the complete Vulkan frame. Java OpenGL retains the
	 * existing feature renderer; selected Vulkan is Rust-owned or unavailable,
	 * never a Java Vulkan compatibility route.
	 */
	public static Route currentEntityShadowRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Vanilla leash ribbons are copied from the extracted endpoint/light
	 * semantics only while Rust owns the complete Vulkan frame. Java OpenGL
	 * retains the existing renderer; unadmitted Vulkan remains unavailable.
	 */
	public static Route currentEntityLeashRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Ordinary dropped-item baked quads use the shared indexed entity-mesh
	 * family only when the Rust Vulkan whole-frame presenter owns the frame.
	 * Java OpenGL retains its existing item renderer; unadmitted Vulkan remains
	 * unavailable.
	 */
	public static Route currentItemEntityMeshRoute(boolean eligible) {
		return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
	}

	/**
	 * Returns the owner of dropped-item entity submissions independently of
	 * representability or the per-family disabled admission switch. A Rust
	 * whole-frame presenter still owns an unavailable item and must fail closed
	 * instead of allowing the collector to silently omit it.
	 */
	public static Route currentItemEntityOwnershipRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Returns the owner of the first-person item callsite independently of the
	 * current item's representability or resource residency. A Rust whole-frame
	 * presenter owns this callsite even when the selected source hand executor is
	 * unavailable; unsupported hand variants then fail closed rather than leaking
	 * a Java submission into the Rust frame.
	 */
	public static Route currentFirstPersonItemOwnershipRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Per-item admission for the dedicated Rust-owned hand source pass. This is
	 * deliberately narrower than ownership: an ineligible item is unavailable
	 * to Rust, while {@link #currentFirstPersonItemOwnershipRoute()} decides
	 * whether Java is still legally allowed to render the callsite.
	 */
	public static Route currentFirstPersonItemRoute(boolean eligible) {
		return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
	}

	/**
	 * Name tags use the private Rust world-text pass only when Rust owns the
	 * complete Vulkan frame. Java OpenGL retains its existing text renderer;
	 * unadmitted Vulkan remains unavailable and there is no borrowed font-atlas
	 * or same-frame fallback route.
	 */
	public static Route currentWorldTextRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Weather is a normal translucent material producer. Rust owns it whenever
	 * it owns the complete Vulkan frame; only OpenGL with Iris remains a Java
	 * compatibility route through the shared shader-aware policy.
	 */
	public static Route currentWeatherRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Clouds are collected as copied vanilla face semantics only for the Rust
	 * Vulkan whole-frame route. Java OpenGL keeps its normal renderer until a
	 * private OpenGL cloud lowering exists; Java Vulkan never receives a mixed
	 * cloud draw once this route is selected.
	 */
	public static Route currentCloudRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentBlockDisplayRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentFallingBlockRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	public static Route currentPistonMovingBlockRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Primed TNT reuses the indexed baked-block route for its copied block mesh,
	 * flashing overlay, and optional outline-only instance. The producer decides
	 * whether the semantic block state is representable before invoking this
	 * policy; unsupported special-model variants remain unavailable rather than
	 * reopening a Java Vulkan pass.
	 */
	public static Route currentPrimedTntRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Returns the owner of the Arrow callsite independently of whether the
	 * current Arrow state is representable as the copied indexed-mesh semantic
	 * payload. Once Rust owns the whole Vulkan frame, failed Arrow admission is
	 * unavailable for that frame and must never authorize a Java entity draw.
	 */
	public static Route currentArrowOwnershipRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/** Per-Arrow admission for the Rust indexed-mesh producer. */
	public static Route currentArrowRoute(boolean eligible) {
		return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
	}

	/**
	 * Bounded opaque/cutout {@code ModelPart} extraction for the Rust-owned
	 * whole-frame route. States that cannot be represented as a copied indexed
	 * mesh remain Java-compatible only outside Rust Vulkan ownership; under a
	 * Rust-owned Vulkan frame they are unavailable rather than fallback draws.
	 */
	public static Route currentModelMeshRoute(boolean eligible) {
		return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
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
		return eligible ? Route.RUST_VULKAN_WHOLE_FRAME : Route.DISABLED;
	}

	public static Route currentStaticTerrainRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Selects the sole DH presenter for a Rust-owned frame.  DH's Java render
	 * hooks remain suppressed by the whole-frame shell; this route only admits
	 * the copied semantic traversal and Rust material streams.  An explicit
	 * disable switch remains available for isolation and migration diagnostics.
	 */
	public static Route currentDistantHorizonsOpaqueRoute() {
		return Route.RUST_VULKAN_WHOLE_FRAME;
	}
}
