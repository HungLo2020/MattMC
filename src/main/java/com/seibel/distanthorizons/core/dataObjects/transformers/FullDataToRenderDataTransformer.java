package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;

/**
 * Handles converting {@link FullDataSourceV2}'s to {@link ColumnRenderSource}.
 */
public class FullDataToRenderDataTransformer
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	
	private static final LongOpenHashSet BROKEN_POS_SET = new LongOpenHashSet();
	private static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Data Transformer");
	
	private static HashSet<IBlockStateWrapper> snowLayerBlockStates = null;
	
	
	
	//==============================//
	// public transformer interface //
	//==============================//
	
	@Nullable
	public static ColumnRenderSource transformFullDataToRenderSource(
			@Nullable FullDataSourceV2 fullDataSource, @Nullable IClientLevelWrapper levelWrapper)
	{
		if (fullDataSource == null)
		{
			return null;
		}
		else if (levelWrapper == null)
		{
			// if the client is no longer loaded in the world, render sources cannot be created 
			return null;
		}
		
		
		try
		{
			return transformCompleteFullDataToColumnData(levelWrapper, fullDataSource);
		}
		catch (InterruptedException e)
		{
			return null;
		}
	}
	
	
	
	//==============//
	// transformers //
	//==============//
	
	/**
	 * Creates a LodNode for a chunk in the given world.
	 *
	 * @throws IllegalArgumentException thrown if either the chunk or world is null.
	 * @throws InterruptedException Can be caused by interrupting the thread upstream.
	 * Generally thrown if the method is running after the client leaves the current world.
	 */
	private static ColumnRenderSource transformCompleteFullDataToColumnData(
			IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource) throws InterruptedException
	{
 		final long pos = fullDataSource.getPos();
		final byte dataDetail = fullDataSource.getDataDetailLevel();
		
		final int vertSize = Config.Client.Advanced.Graphics.Quality.verticalQuality.get().calculateMaxVerticalData(fullDataSource.getDataDetailLevel());
		
		
		
		final ColumnRenderSource columnSource = ColumnRenderSource.createEmpty(pos, vertSize, levelWrapper.getMinHeight());
		if (fullDataSource.isEmpty)
		{
			return columnSource;
		}
		
		columnSource.markNotEmpty();
		int baseX = DhSectionPos.getMinCornerBlockX(pos);
		int baseZ = DhSectionPos.getMinCornerBlockZ(pos);
		
		for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
		{
			for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
			{
				ColumnArrayView columnArrayView = columnSource.getVerticalDataPointView(x, z);
				LongArrayList dataColumn = fullDataSource.getColumnAtRelPos(x, z);
				
				updateOrReplaceRenderDataViewColumnWithFullDataColumn(
						levelWrapper, fullDataSource, columnSource, x, z,
						// bitshift is to account for LODs with a detail level greater than 0 so the block pos is correct
						baseX + BitShiftUtil.pow(x,dataDetail), baseZ + BitShiftUtil.pow(z,dataDetail), 
						columnArrayView, dataColumn);
			}
		}
		
		columnSource.fillDebugFlag(0, 0, ColumnRenderSource.WIDTH, ColumnRenderSource.WIDTH, ColumnRenderSource.DebugSourceFlag.FULL);
		
		return columnSource;
	}
	
	/** Updates the given {@link ColumnArrayView} to match the incoming Full data {@link LongArrayList} */
	public static void updateOrReplaceRenderDataViewColumnWithFullDataColumn(
			IClientLevelWrapper levelWrapper,
			FullDataSourceV2 fullDataSource, ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
			int blockX, int blockZ,
			ColumnArrayView columnArrayView, 
			LongArrayList fullDataColumn)
	{
		columnSource.clearSemanticMaterialsForColumn(renderSourceX, renderSourceZ);
		// we can't do anything if the full data is missing or empty
		if (fullDataColumn == null 
			|| fullDataColumn.size() == 0)
		{
			return;
		}
		
		int fullDataLength = fullDataColumn.size();
		if (fullDataLength <= columnArrayView.verticalSize())
		{
			// Directly use the arrayView since it fits.
			setRenderColumnView(
				levelWrapper, fullDataSource, columnSource, renderSourceX, renderSourceZ,
				blockX, blockZ, columnArrayView, fullDataColumn, true, null, null, null
			);
		}
		else
		{
			PhantomArrayListCheckout checkout = ARRAY_LIST_POOL.checkoutArrays(0, 0, 1);
			LongArrayList dataArrayList = checkout.getLongArray(0, fullDataLength);
			
			try
			{
				// expand the ColumnArrayView to fit the new larger max vertical size
				ColumnArrayView newColumnArrayView = new ColumnArrayView(dataArrayList, fullDataLength, 0, fullDataLength);
				int[] expandedSemanticMaterials = new int[fullDataLength];
				byte[] expandedVariantStates = new byte[fullDataLength];
				long[] expandedVariantPositions = new long[fullDataLength];
				setRenderColumnView(
					levelWrapper, fullDataSource, columnSource, renderSourceX, renderSourceZ,
					blockX, blockZ, newColumnArrayView, fullDataColumn, true, expandedSemanticMaterials,
					expandedVariantStates, expandedVariantPositions
				);
				
				int[] reducedSemanticMaterials = new int[columnArrayView.size()];
				RenderDataPointUtil.mergeMultiData(
						newColumnArrayView, expandedSemanticMaterials,
						columnArrayView, reducedSemanticMaterials
				);
				for (int index = 0; index < columnArrayView.verticalSize(); index++)
				{
					columnSource.setSemanticMaterialId(
						renderSourceX, renderSourceZ, index, reducedSemanticMaterials[index]
					);
					// Reduction may combine source intervals whose weighted-model seeds
					// differ. Keep the material sidecar but make variant provenance
					// explicitly unavailable rather than inventing a representative seed.
					columnSource.setSemanticVariantProvenance(
						renderSourceX, renderSourceZ, index,
						ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L
					);
					List<ColumnRenderSource.SemanticMaterialSpan> spans = reducedSemanticMaterialSpans(
						newColumnArrayView,
						expandedSemanticMaterials,
						expandedVariantStates,
						expandedVariantPositions,
						columnArrayView.get(index)
					);
					if (spans.size() > 1)
					{
						columnSource.setSemanticMaterialSpans(renderSourceX, renderSourceZ, index, spans);
					}
				}
			}
			finally
			{
				ARRAY_LIST_POOL.returnCheckout(checkout);
			}
		}
	}

	/**
	 * Preserves the exact source intervals covered by one reduced entry. The
	 * primary material sidecar intentionally remains mixed after reduction; a
	 * later Rust-owned face expansion can consume these copied intervals rather
	 * than guessing one block texture for the whole coarse face.
	 */
	static List<ColumnRenderSource.SemanticMaterialSpan> reducedSemanticMaterialSpans(
		ColumnArrayView sourceData, int[] sourceMaterials, byte[] sourceVariantStates,
		long[] sourceVariantPositions, long reducedData
	)
	{
		if (sourceMaterials.length < sourceData.size()
			|| sourceVariantStates.length < sourceData.size()
			|| sourceVariantPositions.length < sourceData.size())
		{
			throw new IllegalArgumentException("Semantic source sidecars do not cover the reduced column input");
		}
		if (!RenderDataPointUtil.doesDataPointExist(reducedData))
		{
			return List.of();
		}
		int reducedMinY = RenderDataPointUtil.getYMin(reducedData);
		int reducedMaxY = RenderDataPointUtil.getYMax(reducedData);
		List<ColumnRenderSource.SemanticMaterialSpan> spans = new java.util.ArrayList<>();
		for (int sourceIndex = 0; sourceIndex < sourceData.size(); sourceIndex++)
		{
			long source = sourceData.get(sourceIndex);
			if (!RenderDataPointUtil.doesDataPointExist(source))
			{
				continue;
			}
			int minY = Math.max(reducedMinY, RenderDataPointUtil.getYMin(source));
			int maxY = Math.min(reducedMaxY, RenderDataPointUtil.getYMax(source));
			if (maxY <= minY)
			{
				continue;
			}
			int materialId = sourceMaterials[sourceIndex];
			byte variantState = sourceVariantStates[sourceIndex];
			long variantPosition = variantState == ColumnRenderSource.SEMANTIC_VARIANT_EXACT
				? sourceVariantPositions[sourceIndex] : 0L;
			if (!spans.isEmpty())
			{
				ColumnRenderSource.SemanticMaterialSpan previous = spans.getLast();
				if (previous.maxY() == minY
					&& previous.materialId() == materialId
					&& previous.variantState() == variantState
					&& previous.variantPosition() == variantPosition)
				{
					spans.set(spans.size() - 1, new ColumnRenderSource.SemanticMaterialSpan(
						previous.minY(), maxY, materialId, variantState, variantPosition
					));
					continue;
				}
			}
			spans.add(new ColumnRenderSource.SemanticMaterialSpan(
				minY, maxY, materialId, variantState, variantPosition
			));
		}
		spans.sort(java.util.Comparator.comparingInt(ColumnRenderSource.SemanticMaterialSpan::minY));
		return List.copyOf(spans);
	}
	private static void setRenderColumnView(
			IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
			ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
			int blockX, int blockZ,
			ColumnArrayView renderColumnData, LongArrayList fullColumnData,
			boolean preserveSemanticMaterials, int[] semanticMaterialScratch,
			byte[] semanticVariantStateScratch, long[] semanticVariantPositionScratch)
	{
		if (semanticMaterialScratch != null && semanticMaterialScratch.length < renderColumnData.size())
		{
			throw new IllegalArgumentException("Semantic material scratch does not cover the render column");
		}
		if ((semanticVariantStateScratch == null) != (semanticVariantPositionScratch == null)
			|| (semanticVariantStateScratch != null && (
				semanticVariantStateScratch.length < renderColumnData.size()
				|| semanticVariantPositionScratch.length < renderColumnData.size())))
		{
			throw new IllegalArgumentException("Semantic variant scratch does not cover the render column");
		}
		//===============//
		// config values //
		//===============//
		
		boolean ignoreNonCollidingBlocks = (Config.Client.Advanced.Graphics.Quality.blocksToIgnore.get() == EDhApiBlocksToAvoid.NON_COLLIDING);
		boolean colorBelowWithAvoidedBlocks = Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.get();
		
		HashSet<IBlockStateWrapper> blockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredBlocks(levelWrapper);
		HashSet<IBlockStateWrapper> caveBlockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredCaveBlocks(levelWrapper);
		
		// build snow block cache if needed
		if (snowLayerBlockStates == null)
		{
			snowLayerBlockStates = new HashSet<>();
			// ignore snow layers 1-3, everything above should be considered a full block
			snowLayerBlockStates.add(WRAPPER_FACTORY.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:1}", levelWrapper));
			snowLayerBlockStates.add(WRAPPER_FACTORY.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:2}", levelWrapper));
			snowLayerBlockStates.add(WRAPPER_FACTORY.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:3}", levelWrapper));
		}
		
		int caveCullingMaxY = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - levelWrapper.getMinHeight();
		boolean caveCullingEnabled = 
			Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get()
			&& (
				// dimensions with a ceiling will be all caves so we don't want cave culling
				!levelWrapper.hasCeiling()
				// the end has a lot of overhangs with 0 lighting above the void, which look broken with
				// the current cave culling logic (this could probably be improved, but just skipping it works best for now)
				&& !levelWrapper.getDimensionType().isTheEnd()
			);
		
		boolean isColumnVoid = true;
		
		int colorToApplyToNextBlock = -1;
		int lastColor = 0;
		int lastBottom = -10_000;
		
		int skylightToApplyToNextBlock = -1;
		int blocklightToApplyToNextBlock = -1;
		int renderDataIndex = 0;
		
		
		
		//==================================//
		// convert full data to render data //
		//==================================//
		
		FullDataPointIdMap fullDataMapping = fullDataSource.mapping;
		
		DhBlockPosMutable mutableBlockPos = new DhBlockPosMutable(blockX, 0, blockZ);
		
		// goes from the top down
		for (int fullDataIndex = 0; fullDataIndex < fullColumnData.size(); fullDataIndex++)
		{
			long fullData = fullColumnData.getLong(fullDataIndex);
			
			int bottomY = FullDataPointUtil.getBottomY(fullData);
			int blockHeight = FullDataPointUtil.getHeight(fullData);
			int topY = bottomY + blockHeight;
			int id = FullDataPointUtil.getId(fullData);
			int blockLight = FullDataPointUtil.getBlockLight(fullData);
			int skyLight = FullDataPointUtil.getSkyLight(fullData);
			
			mutableBlockPos.setY(bottomY + levelWrapper.getMinHeight());
			
			IBiomeWrapper biome;
			IBlockStateWrapper block;
			try
			{
				biome = fullDataMapping.getBiomeWrapper(id);
				block = fullDataMapping.getBlockStateWrapper(id);
			}
			catch (IndexOutOfBoundsException e)
			{
				if (!BROKEN_POS_SET.contains(fullDataMapping.getPos()))
				{
					BROKEN_POS_SET.add(fullDataMapping.getPos());
					String levelId = levelWrapper.getDhIdentifier();
					LOGGER.warn("Unable to get data point with id ["+id+"] " +
							"(Max possible ID: ["+fullDataMapping.getMaxValidId()+"]) " +
							"for pos ["+fullDataMapping.getPos()+"] in level ["+levelId+"]. " +
							"Error: ["+e.getMessage()+"]. " +
							"Further errors for this position won't be logged.");
				}
				
				// don't render broken data
				continue;
			}
			
			
			
			//====================//
			// ignored block and  //
			// cave culling check //
			//====================//
			
			boolean ignoreBlock = blockStatesToIgnore.contains(block);
			boolean caveBlock = caveBlockStatesToIgnore.contains(block); // TODO caves should also ignore transparent/non-solid blocks (IE grass and plants) wthout each being defined
			if (caveBlock)
			{
				if (caveCullingEnabled
						// assume this data point is underground if it has no sky-light
						&& skyLight == LodUtil.MIN_MC_LIGHT
						// ignore caves above a certain height to prevent floating islands from having walls underneath them
						&& topY < caveCullingMaxY
						// cave culling shouldn't happen when at the top of the world
						&& renderDataIndex != 0 && fullDataIndex != 0
						// cave culling can't happen when at the bottom of the world
						&& (fullDataIndex + 1) < fullColumnData.size())
				{
					// we need to get the next sky/block lights because
					// the air block here will always have a light of 0/0 due to only the top of the LOD's light being saved.
					long nextFullData = fullColumnData.getLong(fullDataIndex + 1);
					int nextSkyLight = FullDataPointUtil.getSkyLight(nextFullData);
					
					if (nextSkyLight == LodUtil.MIN_MC_LIGHT
							&& ColorUtil.getAlpha(lastColor) == 255)
					{
						// replace the previous block with new bottom
						long columnData = renderColumnData.get(renderDataIndex - 1);
						columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
						renderColumnData.set(renderDataIndex - 1, columnData);
					}
					
					continue;
				}
				
				
				if (ignoreBlock)
				{
					// this is a merged block and a cave block, so it should never be rendered
					continue;
				}
			}
			else if (ignoreBlock)
			{
				// this is an ignored block, but shouldn't be merged like a cave block
				continue;
			}
			
			
			
			//=======================//
			// non-solid block check //
			//=======================//
			
			boolean ignoreNonSolidBlock =
				ignoreNonCollidingBlocks
				&& !block.isSolid()
				&& !block.isLiquid()
				&& block.getOpacity() != LodUtil.BLOCK_FULLY_OPAQUE;
			
			// merge snow into the block below it
			if (snowLayerBlockStates.contains(block))
			{
				// sometimes a snow datapoint will be multiple blocks tall,
				// in that case we just want to drop the top by 1
				blockHeight -= 1;
				if (blockHeight == 0)
				{
					// this snow block was entirely removed, just color the block below it
					ignoreNonSolidBlock = true;
				}
			}
			
			if (ignoreNonSolidBlock)
			{
				if (colorBelowWithAvoidedBlocks)
				{
					int tempColor = levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block);
					
					// don't transfer the color when alpha is 0
					// this prevents issues if grass is transparent
					if (ColorUtil.getAlpha(tempColor) != 0)
					{
						colorToApplyToNextBlock = ColorUtil.setAlpha(tempColor, 255);
						skylightToApplyToNextBlock = skyLight;
						blocklightToApplyToNextBlock = blockLight;
						// The visible surface will use another block's geometry but this
						// avoided block's color. It has no exact texture identity.
					}
				}
				
				// skip this non-colliding block
				continue;
			}
			
			
			int color;
			int semanticMaterialId = ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE;
			byte semanticVariantState = ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE;
			long semanticVariantPosition = 0L;
			if (colorToApplyToNextBlock == -1 || block.isLiquid())
			{
				// use this block's color
				color = levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block);
				colorToApplyToNextBlock = -1;
				if (preserveSemanticMaterials)
				{
					semanticMaterialId = columnSource.internSemanticMaterial(
						block.getSerialString(), biome.getSerialString()
					);
					if (semanticMaterialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
					{
						semanticVariantState = ColumnRenderSource.SEMANTIC_VARIANT_EXACT;
						semanticVariantPosition = ColumnRenderSource.packSemanticVariantPosition(
							blockX, bottomY + levelWrapper.getMinHeight(), blockZ
						);
					}
				}
			}
			else
			{
				// use the previous block's color
				color = colorToApplyToNextBlock;
				colorToApplyToNextBlock = -1;
				skyLight = skylightToApplyToNextBlock;
				blockLight = blocklightToApplyToNextBlock;
			}
			
			
			
			//=============================//
			// merge same-colored adjacent //
			//=============================//
			
			// check if they share a top-bottom face and if they have same color
			if (color == lastColor 
				&& bottomY + blockHeight == lastBottom
				&& renderDataIndex > 0
				&& (!preserveSemanticMaterials || sameSemanticMaterial(
					semanticMaterialAt(
						columnSource, renderSourceX, renderSourceZ, semanticMaterialScratch, renderDataIndex - 1
					),
					semanticMaterialId
				)))
			{
				//replace the previous block with new bottom
				long columnData = renderColumnData.get(renderDataIndex - 1);
				columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
				renderColumnData.set(renderDataIndex - 1, columnData);
				if (preserveSemanticMaterials)
				{
					mergeSemanticVariantAt(
						columnSource, renderSourceX, renderSourceZ,
						semanticVariantStateScratch, semanticVariantPositionScratch,
						renderDataIndex - 1, semanticVariantState, semanticVariantPosition
					);
				}
			}
			else
			{
				// add the block
				isColumnVoid = false;
				long columnData = RenderDataPointUtil.createDataPoint(bottomY + blockHeight, bottomY, color, skyLight, blockLight, block.getMaterialId());
				renderColumnData.set(renderDataIndex, columnData);
				if (preserveSemanticMaterials)
				{
					setSemanticMaterialAt(
						columnSource, renderSourceX, renderSourceZ, semanticMaterialScratch,
						renderDataIndex, semanticMaterialId
					);
					setSemanticVariantAt(
						columnSource, renderSourceX, renderSourceZ,
						semanticVariantStateScratch, semanticVariantPositionScratch,
						renderDataIndex, semanticVariantState, semanticVariantPosition
					);
				}
				renderDataIndex++;
			}
			lastBottom = bottomY;
			lastColor = color;
		}
		
		
		if (isColumnVoid)
		{
			renderColumnData.set(0, RenderDataPointUtil.EMPTY_DATA);
		}
	}

	static boolean sameSemanticMaterial(int leftMaterialId, int rightMaterialId)
	{
		return leftMaterialId == rightMaterialId;
	}

	private static int semanticMaterialAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		int[] semanticMaterialScratch, int index
	)
	{
		return semanticMaterialScratch == null
			? columnSource.getSemanticMaterialId(renderSourceX, renderSourceZ, index)
			: semanticMaterialScratch[index];
	}

	private static void setSemanticMaterialAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		int[] semanticMaterialScratch, int index, int materialId
	)
	{
		if (semanticMaterialScratch == null)
		{
			columnSource.setSemanticMaterialId(renderSourceX, renderSourceZ, index, materialId);
		}
		else
		{
			semanticMaterialScratch[index] = materialId;
		}
	}

	private static byte semanticVariantStateAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		byte[] semanticVariantStateScratch, int index
	)
	{
		return semanticVariantStateScratch == null
			? columnSource.getSemanticVariantState(renderSourceX, renderSourceZ, index)
			: semanticVariantStateScratch[index];
	}

	private static long semanticVariantPositionAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		long[] semanticVariantPositionScratch, int index
	)
	{
		return semanticVariantPositionScratch == null
			? columnSource.getSemanticVariantPosition(renderSourceX, renderSourceZ, index)
			: semanticVariantPositionScratch[index];
	}

	private static void setSemanticVariantAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		byte[] semanticVariantStateScratch, long[] semanticVariantPositionScratch,
		int index, byte state, long position
	)
	{
		if (semanticVariantStateScratch == null)
		{
			columnSource.setSemanticVariantProvenance(renderSourceX, renderSourceZ, index, state, position);
		}
		else
		{
			semanticVariantStateScratch[index] = state;
			semanticVariantPositionScratch[index] = state == ColumnRenderSource.SEMANTIC_VARIANT_EXACT ? position : 0L;
		}
	}

	private static void mergeSemanticVariantAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		byte[] semanticVariantStateScratch, long[] semanticVariantPositionScratch,
		int index, byte incomingState, long incomingPosition
	)
	{
		byte currentState = semanticVariantStateAt(
			columnSource, renderSourceX, renderSourceZ, semanticVariantStateScratch, index
		);
		long currentPosition = semanticVariantPositionAt(
			columnSource, renderSourceX, renderSourceZ, semanticVariantPositionScratch, index
		);
		if (currentState == ColumnRenderSource.SEMANTIC_VARIANT_EXACT
			&& incomingState == ColumnRenderSource.SEMANTIC_VARIANT_EXACT
			&& currentPosition == incomingPosition)
		{
			return;
		}
		byte mergedState = currentState == ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE
			|| incomingState == ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE
			? ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE
			: ColumnRenderSource.SEMANTIC_VARIANT_MIXED;
		setSemanticVariantAt(
			columnSource, renderSourceX, renderSourceZ,
			semanticVariantStateScratch, semanticVariantPositionScratch,
			index, mergedState, 0L
		);
	}

	
	
	
}
