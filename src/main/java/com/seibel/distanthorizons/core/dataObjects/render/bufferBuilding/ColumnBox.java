package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ColumnBox
{
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	/** 
	 * if the skylight has this value that means
	 * that block position is covered/occluded by an adjacent block/column.
	 */
	private static final byte SKYLIGHT_COVERED = -1;
	
	
	
	//=========//
	// builder //
	//=========//
	
	public static void addBoxQuadsToBuilder(
			LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IDhClientLevel clientLevel,
			short width, short yHeight,
			short minX, short minY, short minZ,
			int color, byte irisBlockMaterialId, int semanticMaterialId, byte semanticVariantState,
			long semanticVariantPosition, byte skyLight, byte blockLight,
			long topData, long bottomData, ColumnArrayView[] adjData, boolean[] isAdjDataSameDetailLevel)
	{
		addBoxQuadsToBuilder(
			builder, phantomArrayCheckout, clientLevel, width, yHeight, minX, minY, minZ,
			color, irisBlockMaterialId, semanticMaterialId, semanticVariantState, semanticVariantPosition,
			List.of(), skyLight, blockLight, topData, bottomData, adjData, isAdjDataSameDetailLevel
		);
	}

	/**
	 * Emits the legacy box geometry while preserving source-material intervals
	 * for Rust's exact-atlas semantic sidecar.  Only the Rust whole-frame path
	 * supplies spans; Java's established color-only path continues through the
	 * overload above unchanged.
	 */
	public static void addBoxQuadsToBuilder(
			LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IDhClientLevel clientLevel,
			short width, short yHeight,
			short minX, short minY, short minZ,
			int color, byte irisBlockMaterialId, int semanticMaterialId, byte semanticVariantState,
			long semanticVariantPosition, List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans,
			byte skyLight, byte blockLight,
			long topData, long bottomData, ColumnArrayView[] adjData, boolean[] isAdjDataSameDetailLevel)
	{
		//================//
		// variable setup //
		//================//
		
		short maxX = (short) (minX + width);
		short maxY = (short) (minY + yHeight);
		short maxZ = (short) (minZ + width);
		byte skyLightTop = skyLight;
		byte skyLightBot = RenderDataPointUtil.doesDataPointExist(bottomData) ? RenderDataPointUtil.getLightSky(bottomData) : 0;
		
		boolean transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
		boolean fakeOceanFloor = Config.Client.Advanced.Graphics.Quality.transparency.get().fakeTransparencyEnabled;
		
		boolean isTransparent = isTransparent(color, irisBlockMaterialId, transparencyEnabled);
		boolean overVoid = !RenderDataPointUtil.doesDataPointExist(bottomData);
		boolean isTopTransparent = isTransparent(topData, transparencyEnabled);
		boolean isBottomTransparent = isTransparent(bottomData, transparencyEnabled);
		
		// defaulting to a value far below what we can normally render means we
		// don't need to have an additional "is cave culling enabled" check
		int caveCullingMaxY = Integer.MIN_VALUE;
		if (Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get())
		{
			caveCullingMaxY = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - clientLevel.getLevelWrapper().getMinHeight();
		}
		
		
		
		// if there isn't any data below this LOD, make this LOD's color opaque to prevent seeing void through transparent blocks
		// Note: this LOD should still be considered transparent for this method's checks, otherwise rendering bugs may occur
		if (!RenderDataPointUtil.doesDataPointExist(bottomData))
		{
			color = ColorUtil.setAlpha(color, 255);
		}
		
		
		// fake ocean transparency
		if (transparencyEnabled && fakeOceanFloor)
		{
			if (!isTransparent && isTopTransparent && RenderDataPointUtil.doesDataPointExist(topData))
			{
				skyLightTop = (byte) MathUtil.clamp(0, 15 - (RenderDataPointUtil.getYMax(topData) - minY), 15);
				yHeight = (short) (RenderDataPointUtil.getYMax(topData) - minY - 1);
			}
			else if (isTransparent && !isBottomTransparent && RenderDataPointUtil.doesDataPointExist(bottomData))
			{
				minY = (short) (minY + yHeight - 1);
				yHeight = 1;
			}
			
			maxY = (short) (minY + yHeight);
		}
		
		
		
		//==========================//
		// add top and bottom faces //
		//==========================//
		
		// top face
		{
			boolean skipTop = RenderDataPointUtil.doesDataPointExist(topData)
					&& (RenderDataPointUtil.getYMin(topData) == maxY)
					&& !isTopTransparent
					&& (!isWaterMaterial(irisBlockMaterialId)
						|| isWaterSurfaceOccludingMaterial(RenderDataPointUtil.getBlockMaterialId(topData)));
			if (!skipTop)
			{
				SemanticFaceMaterial faceMaterial = semanticMaterialAt(
					semanticMaterialSpans, maxY - 1, semanticMaterialId, semanticVariantState, semanticVariantPosition
				);
				builder.addQuadUp(minX, maxY, minZ, width, width, ColorUtil.applyShade(color, MC_RENDER.getShade(EDhDirection.UP)), irisBlockMaterialId, skyLightTop, blockLight, faceMaterial.materialId(), faceMaterial.variantState(), faceMaterial.variantPosition());
			}
		}
		
		// bottom face 
		{
			boolean skipBottom = RenderDataPointUtil.doesDataPointExist(bottomData)
					&& (RenderDataPointUtil.getYMax(bottomData) == minY)
					&& !isBottomTransparent;
			if (!skipBottom)
			{
				SemanticFaceMaterial faceMaterial = semanticMaterialAt(
					semanticMaterialSpans, minY, semanticMaterialId, semanticVariantState, semanticVariantPosition
				);
				builder.addQuadDown(minX, minY, minZ, width, width, ColorUtil.applyShade(color, MC_RENDER.getShade(EDhDirection.DOWN)), irisBlockMaterialId, skyLightBot, blockLight, faceMaterial.materialId(), faceMaterial.variantState(), faceMaterial.variantPosition());
			}
		}
		
		
		
		//========================================//
		// add North, south, east, and west faces //
		//========================================//
		
		// NORTH face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.NORTH.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.NORTH.compassIndex];
			// if the adjacent column is null that generally means the adjacent area hasn't been generated yet
			if (adjCol == null)
			{
				// Add an adjacent face if this is opaque face or transparent over the void.
				if (!isTransparent || overVoid)
				{
					addVerticalFaces(
						builder,
							EDhDirection.NORTH, 
							minX, minY, minZ, 
							width, yHeight, 
							color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans);
				}
			}
			else
			{
				makeAdjVerticalQuad(
						builder, phantomArrayCheckout, 
						adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.NORTH, 
						minX, minY, minZ, width, yHeight,
					color, irisBlockMaterialId, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans, blockLight);
			}
		}
		
		// SOUTH face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.SOUTH.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.SOUTH.compassIndex];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					addVerticalFaces(
						builder,
							EDhDirection.SOUTH, 
							minX, minY, maxZ, 
							width, yHeight, 
							color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans);
				}
			}
			else
			{
				makeAdjVerticalQuad(
						builder, phantomArrayCheckout,
						adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.SOUTH,
						minX, minY, maxZ, width, yHeight,
					color, irisBlockMaterialId, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans, blockLight);
			}
		}
		
		// WEST face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.WEST.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.WEST.compassIndex];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					addVerticalFaces(
						builder,
							EDhDirection.WEST, 
							minX, minY, minZ, 
							width, yHeight, 
							color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans);
				}
			}
			else
			{
				makeAdjVerticalQuad(
						builder, phantomArrayCheckout,
						adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.WEST, 
						minX, minY, minZ, width, yHeight,
					color, irisBlockMaterialId, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans, blockLight);
			}
		}
		
		// EAST face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.EAST.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.EAST.compassIndex];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					addVerticalFaces(
						builder,
							EDhDirection.EAST, 
							maxX, minY, minZ, 
							width, yHeight, 
							color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans);
				}
			}
			else
			{
				makeAdjVerticalQuad(
						builder, phantomArrayCheckout,
						adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.EAST, 
						maxX, minY, minZ, width, yHeight,
					color, irisBlockMaterialId, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans, blockLight);
			}
		}
	}
	
	static void makeAdjVerticalQuad(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout,
		@NotNull ColumnArrayView adjColumnView, boolean adjacentIsSameDetailLevel, int caveCullingMaxY, EDhDirection direction,
			short x, short yMin, short z, short horizontalWidth, short ySize,
			int color, byte irisBlockMaterialId, int semanticMaterialId, byte semanticVariantState,
			long semanticVariantPosition, List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans,
			byte blockLight)
	{
		// pooled arrays
		LongArrayList segments = phantomArrayCheckout.getLongArray(0, 0);
		LongArrayList newSegments = phantomArrayCheckout.getLongArray(1, 0);
		
		
		
		//==================//
		// create face with //
		// no adjacent data //
		//==================//
		
		color = ColorUtil.applyShade(color, MC_RENDER.getShade(direction));
		
		if (adjColumnView.size == 0
			|| RenderDataPointUtil.hasZeroHeight(adjColumnView.get(0)))
		{
			addVerticalFaces(builder, direction, x, yMin, z, horizontalWidth, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans);
			return;
		}
		
		
		
		//=================================//
		// determine face visibility/light //
		//=================================//
		
		boolean transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
		boolean inputTransparent = isTransparent(color, irisBlockMaterialId, transparencyEnabled);
		short yMax = (short) (yMin + ySize);
		
		
		int adjCount = adjColumnView.size();
		
		// Start with the entire range at max light
		segments.add(YSegmentUtil.encode(yMin, yMax, LodUtil.MAX_MC_LIGHT));
		
		// Process each adjacent datapoint and split segments as needed
		for (int adjIndex = 0; adjIndex < adjCount; adjIndex++)
		{
			long adjPoint = adjColumnView.get(adjIndex);
			short adjMinY = RenderDataPointUtil.getYMin(adjPoint);
			short adjMaxY = RenderDataPointUtil.getYMax(adjPoint);
			
			// skip empty adjacent points
			// or points below this one
			if (!RenderDataPointUtil.doesDataPointExist(adjPoint)
				|| RenderDataPointUtil.hasZeroHeight(adjPoint)
				|| yMax <= adjMinY)
			{
				continue;
			}
			
			
			long adjAbovePoint = (adjIndex != 0) ? adjColumnView.get(adjIndex - 1) : RenderDataPointUtil.EMPTY_DATA;
			long adjBelowPoint = (adjIndex + 1 < adjCount) ? adjColumnView.get(adjIndex + 1) : RenderDataPointUtil.EMPTY_DATA;
			
			boolean adjOverVoid = !RenderDataPointUtil.doesDataPointExist(adjBelowPoint);
			boolean adjTransparent = 
				!adjOverVoid
				&& isTransparent(adjPoint, transparencyEnabled);
			
			byte adjSkyLight = RenderDataPointUtil.getLightSky(adjPoint);
			byte lightToApply;
			
			if (!adjTransparent)
			{
				// Adjacent is opaque
				boolean adjacentCoversThis =
					!adjacentIsSameDetailLevel
						&& RenderDataPointUtil.getYMax(adjPoint) >= caveCullingMaxY
						&&
						(
							(x == 0 && direction == EDhDirection.WEST)
							|| (z == 0 && direction == EDhDirection.NORTH)
							|| (x == 256 && direction == EDhDirection.EAST)
							|| (z == 256 && direction == EDhDirection.SOUTH)
						);
				
				lightToApply = adjacentCoversThis ? adjSkyLight : SKYLIGHT_COVERED;
			}
			else
			{
				// Adjacent is transparent, use below light
				lightToApply = RenderDataPointUtil.getLightSky(adjBelowPoint);
			}
			
			
			// Apply light to the range [adjMinY, adjMaxY)
			applyLightToRange(segments, newSegments, adjMinY, adjMaxY, lightToApply);
			
			// Fill overhang area [adjMaxY, adjAboveMinY) with adjSkyLight
			short adjAboveMinY = RenderDataPointUtil.getYMin(adjAbovePoint);
			if (adjMaxY < adjAboveMinY)
			{
				applyLightToRange(segments, newSegments, adjMaxY, adjAboveMinY, adjSkyLight);
			}
		}
		
		
		
		//=======================//
		// Create vertical faces //
		// from segments         //
		//=======================//
		
		for (int i = 0; i < segments.size(); i++)
		{
			long segment = segments.getLong(i);
			tryAddVerticalFaceWithSkyLightToBuilder(
				builder, direction,
				x, z, horizontalWidth,
				color, irisBlockMaterialId, semanticMaterialId, semanticVariantState, semanticVariantPosition, semanticMaterialSpans, blockLight,
				YSegmentUtil.getSkyLight(segment), inputTransparent, YSegmentUtil.getEndY(segment), YSegmentUtil.getStartY(segment)
			);
		}
	}
	
	/**
	 * Apply the new light value over the given y range,
	 * splitting segments as needed
	 * <p>
	 * source: claude.ai
	 */
	private static void applyLightToRange(
			LongArrayList segments, LongArrayList newSegments, 
			short rangeStart, short rangeEnd, 
			byte newLight)
	{
		// clear the pooled array that the new segments will go into
		newSegments.clear();
		
		for (int i = 0; i < segments.size(); i++)
		{
			long seg = segments.getLong(i);
			short endY = YSegmentUtil.getEndY(seg);
			short startY = YSegmentUtil.getStartY(seg);
			byte skyLight = YSegmentUtil.getSkyLight(seg);
			
			// No overlap
			if (endY <= rangeStart 
				|| startY >= rangeEnd)
			{
				newSegments.add(seg);
				continue;
			}
			
			// Partial or complete overlap - need to split
			
			// Part before the range
			if (startY < rangeStart)
			{
				newSegments.add(YSegmentUtil.encode(startY, rangeStart, skyLight));
			}
			
			// Overlapping part - take minimum light
			short overlapStart = (short)Math.max(startY, rangeStart);
			short overlapEnd = (short)Math.min(endY, rangeEnd);
			byte minLight = (byte) Math.min(newLight, skyLight);
			newSegments.add(YSegmentUtil.encode(overlapStart, overlapEnd, minLight));
			
			// Part after the range
			if (endY > rangeEnd)
			{
				newSegments.add(YSegmentUtil.encode(rangeEnd, endY, skyLight));
			}
		}
		
		segments.clear();
		segments.addAll(newSegments);
	}
	
	private static void tryAddVerticalFaceWithSkyLightToBuilder(
			LodQuadBuilder builder, EDhDirection direction,
			short x, short z, short horizontalWidth,
			int color, byte irisBlockMaterialId, int semanticMaterialId, byte semanticVariantState,
			long semanticVariantPosition, List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans,
			byte blockLight,
			byte lastSkyLight, boolean inputTransparent, int quadTopY, int quadBottomY
			)
	{
		// invalid positions will have a negative skylight
		if (lastSkyLight < 0)
		{
			return;
		}
		
		// Don't add transparent vertical faces
		// unless the adjacent position is empty.
		// This is done to prevent walls between water blocks in the ocean.
		if (inputTransparent
			&& (lastSkyLight != LodUtil.MAX_MC_LIGHT))
		{
			return;
		}
		
		// don't add negative/empty height faces
		short height = (short) (quadTopY - quadBottomY);
		if (height <= 0)
		{
			return;
		}
		
		addVerticalFaces(
			builder, direction, x, (short) quadBottomY, z, horizontalWidth, height,
			color, irisBlockMaterialId, lastSkyLight, blockLight, semanticMaterialId,
			semanticVariantState, semanticVariantPosition, semanticMaterialSpans
		);
	}

	private static SemanticFaceMaterial semanticMaterialAt(
		List<ColumnRenderSource.SemanticMaterialSpan> spans, int y, int fallbackMaterialId,
		byte fallbackVariantState, long fallbackVariantPosition
	)
	{
		for (ColumnRenderSource.SemanticMaterialSpan span : spans)
		{
			if (span.minY() <= y && y < span.maxY())
			{
				return new SemanticFaceMaterial(span.materialId(), span.variantState(), span.variantPosition());
			}
		}
		return new SemanticFaceMaterial(fallbackMaterialId, fallbackVariantState, fallbackVariantPosition);
	}

	private static void addVerticalFaces(
		LodQuadBuilder builder, EDhDirection direction, short x, short yMin, short z,
		short horizontalWidth, short ySize, int color, byte irisBlockMaterialId, byte skyLight,
		byte blockLight, int fallbackMaterialId, byte fallbackVariantState,
		long fallbackVariantPosition, List<ColumnRenderSource.SemanticMaterialSpan> spans
	)
	{
		for (SemanticFaceSegment segment : semanticVerticalFaceSegments(
			yMin, ySize, fallbackMaterialId, fallbackVariantState, fallbackVariantPosition, spans
		))
		{
			builder.addQuadAdj(
				direction, x, (short) segment.minY(), z, horizontalWidth, (short) segment.height(),
				color, irisBlockMaterialId, skyLight, blockLight, segment.materialId(),
				segment.variantState(), segment.variantPosition()
			);
		}
	}

	/** Pure semantic splitter used before any legacy vertex bytes are written. */
	static List<SemanticFaceSegment> semanticVerticalFaceSegments(
		short minY, short ySize, int fallbackMaterialId, byte fallbackVariantState,
		long fallbackVariantPosition, List<ColumnRenderSource.SemanticMaterialSpan> spans
	)
	{
		int top = minY + ySize;
		int cursor = minY;
		List<SemanticFaceSegment> segments = new ArrayList<>();
		for (ColumnRenderSource.SemanticMaterialSpan span : spans)
		{
			int start = Math.max(cursor, span.minY());
			int end = Math.min(top, span.maxY());
			if (end <= start)
			{
				continue;
			}
			if (cursor < start)
			{
				segments.add(new SemanticFaceSegment(
					cursor, start - cursor, fallbackMaterialId, fallbackVariantState, fallbackVariantPosition
				));
			}
			segments.add(new SemanticFaceSegment(
				start, end - start, span.materialId(), span.variantState(), span.variantPosition()
			));
			cursor = end;
		}
		if (cursor < top)
		{
			segments.add(new SemanticFaceSegment(
				cursor, top - cursor, fallbackMaterialId, fallbackVariantState, fallbackVariantPosition
			));
		}
		return List.copyOf(segments);
	}

	private record SemanticFaceMaterial(int materialId, byte variantState, long variantPosition) { }

	record SemanticFaceSegment(int minY, int height, int materialId, byte variantState, long variantPosition) { }

	private static boolean isTransparent(int color, byte materialId, boolean transparencyEnabled)
	{
		return transparencyEnabled
			&& (ColorUtil.getAlpha(color) < 255 || isTransparentMaterial(materialId));
	}

	static boolean isTransparent(long dataPoint, boolean transparencyEnabled)
	{
		return RenderDataPointUtil.doesDataPointExist(dataPoint)
			&& transparencyEnabled
			&& (RenderDataPointUtil.getAlpha(dataPoint) < 255
				|| isTransparentMaterial(RenderDataPointUtil.getBlockMaterialId(dataPoint)));
	}

	private static boolean isTransparentMaterial(int materialId)
	{
		return isWaterMaterial(materialId) || isLeavesMaterial(materialId);
	}

	static boolean isWaterMaterial(int materialId)
	{
		return materialId == EDhApiBlockMaterial.WATER.index;
	}

	private static boolean isLeavesMaterial(int materialId)
	{
		return materialId == EDhApiBlockMaterial.LEAVES.index;
	}

	static boolean isWaterSurfaceOccludingMaterial(int materialId)
	{
		switch (EDhApiBlockMaterial.getFromIndex(materialId))
		{
			case STONE:
			case WOOD:
			case METAL:
			case DIRT:
			case LAVA:
			case SAND:
			case DEEPSLATE:
				return true;
			default:
				return false;
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** 
	 * encodes height/light data into a long
	 * to reduce object allocations.
	 */
	private static class YSegmentUtil
	{
		private static final int HEIGHT_WIDTH = Short.SIZE;
		private static final int SKY_LIGHT_WIDTH = Byte.SIZE;
		
		private static final int START_Y_MASK = (int) Math.pow(2, HEIGHT_WIDTH) - 1;
		private static final int END_Y_MASK = (int) Math.pow(2, HEIGHT_WIDTH) - 1;
		private static final int SKY_LIGHT_MASK = (int) Math.pow(2, SKY_LIGHT_WIDTH) - 1;
		
		private static final int START_Y_OFFSET = 0;
		private static final int END_Y_OFFSET = START_Y_OFFSET + HEIGHT_WIDTH;
		private static final int SKY_LIGHT_OFFSET = END_Y_OFFSET + HEIGHT_WIDTH;
		
		
		
		public static long encode(short startY, short endY, byte skyLight)
		{
			long data = 0L;
			data |= (long) (startY & START_Y_MASK) << START_Y_OFFSET;
			data |= (long) (endY & END_Y_MASK) << END_Y_OFFSET;
			data |= (long) (skyLight & SKY_LIGHT_MASK) << SKY_LIGHT_OFFSET;
			return data;
		}
		
		public static short getStartY(long data) { return (short) ((data >> START_Y_OFFSET) & START_Y_MASK); }
		public static short getEndY(long data) { return (short) ((data >> END_Y_OFFSET) & END_Y_MASK); }
		public static byte getSkyLight(long data) { return (byte) ((data >> SKY_LIGHT_OFFSET) & SKY_LIGHT_MASK); }
		
	}
	
	
	
}
