package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import net.minecraft.client.Minecraft;
import net.vulkanic.world.DistantHorizonsSemanticCollector;
import net.vulkanic.world.WorldRenderRoutePolicy;

import java.util.List;

/**
 * Copies DH's visible LOD render list into the Rust whole-frame route.
 * Rust VulkanicGAL owns all LOD drawing; this class only traverses DH's
 * render list and publishes the bounded semantic demand for one frame.
 */
public class LodRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererEventToFile)
			.build();
	public static final DhLogger RATE_LIMITED_LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererEventToFile)
			.maxCountPerSecond(4)
			.build();
	
	public static final LodRenderer INSTANCE = new LodRenderer();
	
	private LodRenderer() { }
	
	/**
	 * Executes only DH's real render-list traversal for the Rust Vulkan
	 * whole-frame route. Unlike {@link #render}, this method can never issue a
	 * Java draw when the non-water subset is not admitted: the shell has already
	 * selected its sole Rust presenter.
	 */
	public boolean collectRustOpaqueRouteForWholeFrame(RenderParams renderParams, IProfilerWrapper profiler)
	{
		return DistantHorizonsSemanticCollector.beginVisibleFrame(renderParams)
			&& this.trySelectRustNonWaterRoute(renderParams, profiler);
	}

	/**
	 * Performs a read-only traversal of DH's real, already-built render list
	 * before selecting the bounded Rust material route. Opaque, transparent, and
	 * water-surface streams retain separate Rust-owned material passes. The caller
	 * never switches route after a Rust selection.
	 */
	private boolean trySelectRustNonWaterRoute(RenderParams renderParams, IProfilerWrapper profiler)
	{
		// Capture-only diagnostic: skip DH's render-list traversal to test whether
		// preflight itself changes vanilla Sodium terrain readiness. This is gated
		// to graphics-audit runs and cannot affect normal route selection.
		if (Boolean.parseBoolean(System.getenv().getOrDefault("MATTMC_RUST_DH_SKIP_PREFLIGHT", "false"))
			&& Boolean.parseBoolean(System.getenv().getOrDefault("MATTMC_GRAPHICS_AUDIT", "false")))
		{
			DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("audit-skipped-preflight", 0, 0, 0);
			return false;
		}
			String unsupportedFeature = unsupportedRustWholeFrameFeature();
		if (unsupportedFeature != null)
		{
			DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("unsupported-dh-render-feature:" + unsupportedFeature, 0, 0, 0);
			return false;
		}
		var lightmap = Minecraft.getInstance().gameRenderer.lightTexture()
			.ensureRustSemanticLightmapInputs(renderParams.partialTicks);
		if (lightmap == null)
		{
			DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("waiting-for-rust-lightmap", 0, 0, 0);
			return false;
		}
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.refreshShaderEnvironmentLightmap();
		if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.pendingShaderEnvironmentLightmapGeneration()
			!= lightmap.generation())
		{
			DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("shader-environment-lightmap-refresh-failed", 0, 0, 0);
			return false;
		}
		RenderBufferHandler buffers = renderParams.renderBufferHandler;
		if (buffers == null)
		{
			DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("render-buffer-handler-missing", 0, 0, 0);
			return false;
		}
		// Do not publish background DH assets before the visible render list is
		// known.  The collector deliberately gives visible demand priority, but
		// an empty demand set means "all pending columns"; flushing here would
		// synchronously expand/upload unrelated background columns on the render
		// thread before route admission.  The bounded post-traversal flush below
		// publishes only the columns this frame can actually draw.
		profiler.push("LOD Rust semantic extraction");
		try
		{
			// Quadtree readiness tracks copied CPU columns so DH does not continue
			// requesting the same work. Visibility is established by the traversal
			// below before any upload is selected; publishing here would spend the
			// bounded Rust upload budget on arbitrary background columns.
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.distant-horizons.render-list");
			buffers.buildRenderList(renderParams);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.distant-horizons.render-list");
			List<Long> semanticColumns = buffers.getSemanticColumnRenderPositions();
			if (semanticColumns.isEmpty())
			{
				DistantHorizonsSemanticCollector.recordRenderListObservation(0);
				DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected("no-visible-columns", 0, 0, 0);
				return false;
			}
			int opaqueSegments = 0;
			int transparentSegments = 0;
			int waterSegments = 0;
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.distant-horizons.visible-segments");
			for (long columnKey : semanticColumns)
			{
				DistantHorizonsSemanticCollector.VisibleColumnSegments segments =
					DistantHorizonsSemanticCollector.recordVisibleMaterialColumn(columnKey);
				opaqueSegments += segments.opaqueSegments();
				transparentSegments += segments.transparentSegments();
				waterSegments += segments.waterSegments();
			}
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.distant-horizons.visible-segments");
			// The traversal above is the first point at which pending asset demand is
			// known to be visible. Publish that bounded demand before freezing route
			// semantics; the draw itself still waits for the next coherent frame.
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.distant-horizons.asset-preflight");
			net.vulkanic.gui.RustGalFrameCoordinator.flushPendingWorldLodAssetsForSemanticPreflight();
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.distant-horizons.asset-preflight");
			DistantHorizonsSemanticCollector.recordRenderListObservation(semanticColumns.size());
			if (opaqueSegments + transparentSegments + waterSegments == 0)
			{
				DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected(
					DistantHorizonsSemanticCollector.hasUnpublishedVisibleColumns()
						? "waiting-for-asset-generation"
						: "no-visible-supported-segments",
					opaqueSegments, transparentSegments, waterSegments
				);
				return false;
			}
			// Java DH's generic-object renderer cannot run once Rust owns the
			// presenter: it would try to bind Java buffers and issue a second
			// backend draw. Copy its primitive box semantics before route
			// admission so custom objects, beacons, and DH generic cloud groups
			// remain part of the selected frame.
			if (renderParams.genericRenderer != null
				&& !collectRustGenericSemantics(renderParams))
			{
				DistantHorizonsSemanticCollector.recordRustNonWaterRouteRejected(
					"unsupported-dh-generic-object-semantics", opaqueSegments, transparentSegments, waterSegments);
				return false;
			}
			/*
			 * Rust owns both sides of a partial material result: resolved quads use
			 * the exact atlas overlay and unresolved quads retain their copied DH
			 * reduced-color index range. Requiring complete atlas coverage here
			 * would make that already-implemented complementary path unreachable.
			 * The sidecar and vertex ranges are still validated by the Rust update
			 * and frame planner before admission; this only removes the obsolete
			 * Java-side all-or-nothing gate.
			 */
			DistantHorizonsSemanticCollector.markRustNonWaterRouteSelected();
			return true;
		}
		finally
		{
			profiler.pop();
		}
	}

	private static boolean collectRustGenericSemantics(RenderParams renderParams)
	{
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.distant-horizons.generic-semantics");
		try
		{
			return renderParams.genericRenderer.collectRustSemantic(renderParams);
		}
		finally
		{
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.distant-horizons.generic-semantics");
		}
	}

	/**
	 * Reject only features that the Rust-owned whole-frame DH slice does not yet
	 * model. An active Iris pack is intentionally not part of this decision:
	 * this preflight copies DH CPU semantics into the Rust frame and never binds
	 * an Iris program, framebuffer, texture, or vertex stream.
	 */
	private static String unsupportedRustWholeFrameFeature()
	{
		/*
		 * These preferences select Java-only post-processing or custom-object
		 * paths around DH's terrain render list. The whole-frame route neither
		 * invokes those Java passes nor borrows their state: its source-derived
		 * Rust pass graph owns the submitted LOD material streams. They therefore
		 * cannot veto semantic LOD collection. Debug wireframes follow the same
		 * admission rule: DebugRenderer traverses the real registry and copies
		 * bounded box edges into the Rust-owned semantic line stream.
		 */
		// The legacy SSAO hook is intentionally fenced while Rust owns the
		// presenter. The semantic frame now carries the explicit SSAO contract;
		// Rust consumes it in the DH compositor, so this feature no longer needs
		// to reject the route or open a Java framebuffer.
		return null;
	}

}
