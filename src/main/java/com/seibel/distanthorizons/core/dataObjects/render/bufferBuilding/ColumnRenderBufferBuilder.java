package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.vulkanic.world.DistantHorizonsSemanticCollector;
import net.vulkanic.world.NativeDhRenderSource;

import java.util.concurrent.CompletableFuture;

/**
 * Builds a {@link ColumnRenderSource}'s LOD geometry. The whole traversal
 * (columns, neighbors, debug colors, semantic provenance and boxes) runs in
 * one Rust call; Java gathers the per-build settings.
 */
public class ColumnRenderBufferBuilder
{
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final String DISABLE_CAPTURE_HORIZONTAL_CONTRIBUTOR_BOXES =
		"MATTMC_CAPTURE_DH_DISABLE_HORIZONTAL_CONTRIBUTOR_BOXES";
	
	// Parameter block layout shared with render/dh_quads/section.rs.
	static final int P_HEIGHT = 0, P_DETAIL = 1, P_NEIGHBOR_HEIGHT = 2, P_NEIGHBOR_DETAIL = 6, P_SAME_DETAIL = 10,
		P_COLOR_MODE = 11, P_SATURATION = 12, P_BRIGHTNESS = 13, P_DETAIL_COLOR = 14, P_CAVE_Y = 15, P_FLAGS = 16,
		P_SHADE_FACTS = 17, P_POLICY = 18, P_WANT_X = 19, P_WANT_Z = 20, P_WANT_INDEX = 21, P_BLOCK_X = 22,
		P_BLOCK_Z = 23, P_Y_OFFSET = 24, P_MATERIALS = 25, P_INTERN_BASE = 26;
	static final long F_TRANSPARENCY = 1, F_FAKE_OCEAN = 2, F_WHOLE_FRAME = 4, F_EXACT_TOPOLOGY = 8,
		F_CONTRIBUTOR_BOXES = 16, F_DEBUG_FILTER = 32;
	
	/**
	 * Keeps the heterogeneous coarse-cell geometry probe capture-only. Exact
	 * material topology may emit contributor boxes; this switch can isolate
	 * their raster coverage from copied material and compositor stages while
	 * comparing against Frozen's coarse box.
	 */
	private static boolean horizontalContributorBoxesEnabled()
	{
		return !(Boolean.parseBoolean(System.getenv().getOrDefault(
			"MATTMC_GRAPHICS_AUDIT", "false"))
			&& Boolean.parseBoolean(System.getenv().getOrDefault(
				DISABLE_CAPTURE_HORIZONTAL_CONTRIBUTOR_BOXES, "false")));
	}
	
	
	
	//==============//
	// vbo building //
	//==============//
	
	public static CompletableFuture<LodBufferContainer> uploadBuffersAsync(
			IDhClientLevel clientLevel,
			long pos,
			LodQuadBuilder quadBuilder
		)
	{
		DhBlockPos minBlockPos = new DhBlockPos(DhSectionPos.getMinCornerBlockX(pos), clientLevel.getLevelWrapper().getMinHeight(), DhSectionPos.getMinCornerBlockZ(pos));
		LodBufferContainer bufferContainer = new LodBufferContainer(pos, minBlockPos);
		CompletableFuture<LodBufferContainer> uploadFuture = bufferContainer.makeAndUploadBuffersAsync(quadBuilder);
		uploadFuture.whenComplete((uploadedBuffer, exception) -> 
		{
			// Without the Rust route nothing consumes the build, so the
			// container completes unpublished and owns nothing to retain.
			if (uploadedBuffer != null && !uploadedBuffer.renderDataReady())
			{
				uploadedBuffer.close();
			}
		});
		return uploadFuture;
	}
	
	/**
	 * Builds and greedily merges every box of {@code renderSource}.
	 *
	 * @param adjRegions neighbor sources by compass index, null when absent
	 * @param isSameDetailLevel whether each neighbor renders at this detail level, by compass index
	 */
	public static void makeLodRenderData(
			LodQuadBuilder quadBuilder, ColumnRenderSource renderSource, IDhClientLevel clientLevel,
			ColumnRenderSource[] adjRegions, boolean[] isSameDetailLevel)
	{
		long[] params = new long[NativeDhQuadBuilder.SECTION_PARAMS];
		
		// can be used to limit which section positions are built and thus rendered
		boolean debugFilter = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugEnable.get();
		if (debugFilter)
		{
			if (DhSectionPos.getDetailLevel(renderSource.pos) != Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugDetailLevel.get()
				|| DhSectionPos.getX(renderSource.pos) != Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXPos.get()
				|| DhSectionPos.getZ(renderSource.pos) != Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZPos.get())
			{
				return;
			}
			params[P_WANT_X] = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXRow.get();
			params[P_WANT_Z] = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZRow.get();
			params[P_WANT_INDEX] = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugColumnIndex.get();
		}
		
		byte detail = renderSource.getDataDetailLevel();
		params[P_HEIGHT] = renderSource.verticalDataCount;
		params[P_DETAIL] = detail;
		NativeDhRenderSource[] neighbors = new NativeDhRenderSource[4];
		for (int direction = 0; direction < 4; direction++)
		{
			ColumnRenderSource adjacent = adjRegions[direction];
			neighbors[direction] = adjacent == null ? null : adjacent.nativeData();
			params[P_NEIGHBOR_HEIGHT + direction] = adjacent == null ? -1 : adjacent.verticalDataCount;
			params[P_NEIGHBOR_DETAIL + direction] = adjacent == null ? -1 : adjacent.getDataDetailLevel();
			if (isSameDetailLevel[direction]) params[P_SAME_DETAIL] |= 1L << direction;
		}
		
		EDhApiDebugRendering debugging = Config.Client.Advanced.Debugging.debugRendering.get();
		int[] flagColors = null;
		switch (debugging)
		{
			case OFF -> params[P_COLOR_MODE] = 0;
			case SHOW_DETAIL -> { params[P_COLOR_MODE] = 1; params[P_DETAIL_COLOR] = LodUtil.DEBUG_DETAIL_LEVEL_COLORS[detail] & 0xffffffffL; }
			case SHOW_BLOCK_MATERIAL -> params[P_COLOR_MODE] = 2;
			case SHOW_OVERLAPPING_QUADS -> params[P_COLOR_MODE] = 3;
			case SHOW_RENDER_SOURCE_FLAG ->
			{
				params[P_COLOR_MODE] = 4;
				flagColors = new int[ColumnRenderSource.WIDTH * ColumnRenderSource.WIDTH];
				for (int column = 0; column < flagColors.length; column++)
				{
					ColumnRenderSource.DebugSourceFlag flag = renderSource.debugSourceFlags[column];
					flagColors[column] = flag == null ? ColorUtil.RED : flag.color;
				}
			}
			default -> throw new IllegalArgumentException("Unknown debug mode: " + debugging);
		}
		params[P_SATURATION] = Float.floatToRawIntBits(Config.Client.Advanced.Graphics.Quality.saturationMultiplier.get().floatValue());
		params[P_BRIGHTNESS] = Float.floatToRawIntBits(Config.Client.Advanced.Graphics.Quality.brightnessMultiplier.get().floatValue());
		
		params[P_CAVE_Y] = Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get()
			? Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - clientLevel.getLevelWrapper().getMinHeight()
			: Integer.MIN_VALUE;
		var transparency = Config.Client.Advanced.Graphics.Quality.transparency.get();
		long flags = 0;
		if (transparency.transparencyEnabled) flags |= F_TRANSPARENCY;
		if (transparency.fakeTransparencyEnabled) flags |= F_FAKE_OCEAN;
		if (DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()) flags |= F_WHOLE_FRAME;
		if (DistantHorizonsSemanticCollector.usesExactMaterialTopologyBuild()) flags |= F_EXACT_TOPOLOGY;
		if (horizontalContributorBoxesEnabled()) flags |= F_CONTRIBUTOR_BOXES;
		if (debugFilter) flags |= F_DEBUG_FILTER;
		params[P_FLAGS] = flags;
		if (!(MC_RENDER instanceof IMinecraftRenderWrapper.NativeShadeFacts facts))
		{
			throw new IllegalStateException("The render wrapper provides no DH shade facts");
		}
		params[P_SHADE_FACTS] = facts.getNativeShadeFacts();
		params[P_POLICY] = NativeDhQuadBuilder.policy();
		params[P_BLOCK_X] = DhSectionPos.getMinCornerBlockX(renderSource.pos);
		params[P_BLOCK_Z] = DhSectionPos.getMinCornerBlockZ(renderSource.pos);
		params[P_Y_OFFSET] = renderSource.yOffset;
		
		short[] minX = new short[ColumnRenderSource.WIDTH];
		short[] minZ = new short[ColumnRenderSource.WIDTH];
		for (int relative = 0; relative < ColumnRenderSource.WIDTH; relative++)
		{
			long cell = DhSectionPos.encode(detail, relative, relative);
			minX[relative] = (short) DhSectionPos.getMinCornerBlockX(cell);
			minZ[relative] = (short) DhSectionPos.getMinCornerBlockZ(cell);
		}
		
		if (DistantHorizonsSemanticCollector.hasWaterSourceInputProbes())
		{
			recordWaterSourceInputs(renderSource, clientLevel, params);
		}
		quadBuilder.buildSection(renderSource, params, neighbors, minX, minZ, flagColors);
		quadBuilder.mergeQuads();
	}
	
	/** Capture-only: reports the same source entries the build visits to the configured water probes. */
	private static void recordWaterSourceInputs(ColumnRenderSource renderSource, IDhClientLevel clientLevel, long[] params)
	{
		boolean filter = (params[P_FLAGS] & F_DEBUG_FILTER) != 0;
		byte detail = renderSource.getDataDetailLevel();
		for (int relX = 0; relX < ColumnRenderSource.WIDTH; relX++)
		{
			for (int relZ = 0; relZ < ColumnRenderSource.WIDTH; relZ++)
			{
				if (filter && (params[P_WANT_X] >= 0 && relX != params[P_WANT_X] || params[P_WANT_Z] >= 0 && relZ != params[P_WANT_Z])) continue;
				for (int i = 0; i < renderSource.verticalDataCount; i++)
				{
					if (filter && params[P_WANT_INDEX] >= 0 && i != params[P_WANT_INDEX]) continue;
					long data = renderSource.getDataPoint(relX, relZ, i);
					if (RenderDataPointUtil.hasZeroHeight(data) || !RenderDataPointUtil.doesDataPointExist(data)) break;
					DistantHorizonsSemanticCollector.recordWaterSourceInput(
						renderSource.pos, detail,
						DhSectionPos.getMinCornerBlockX(renderSource.pos) + (relX << detail),
						clientLevel.getLevelWrapper().getMinHeight(),
						DhSectionPos.getMinCornerBlockZ(renderSource.pos) + (relZ << detail),
						data, renderSource.getSemanticMaterialId(relX, relZ, i));
				}
			}
		}
	}
	
}
