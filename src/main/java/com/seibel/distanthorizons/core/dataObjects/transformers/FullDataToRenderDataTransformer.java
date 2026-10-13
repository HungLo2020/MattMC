package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;

/**
 * Handles converting {@link FullDataSourceV2}'s to {@link ColumnRenderSource}.
 */
public class FullDataToRenderDataTransformer
{
	
	// Pure semantic helpers can run before platform dependencies are registered.
	private static final class WrapperDependencies
	{
		private static final IWrapperFactory FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	}
	
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
		try (var reader = fullDataSource.getClass() == FullDataSourceV2.class
			? fullDataSource.nativeSource().conversionReader() : null)
		{
			if (fullDataSource.isEmpty)
			{
				return columnSource;
			}

			columnSource.markNotEmpty();
			int baseX = DhSectionPos.getMinCornerBlockX(pos);
			int baseZ = DhSectionPos.getMinCornerBlockZ(pos);
			boolean preserveSemanticMaterials =
				net.vulkanic.world.DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired();

			try (var conversionScope = net.vulkanic.world.NativeDhConversionScope.create(levelWrapper, fullDataSource))
			{
				for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
				{
					for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
					{
						ColumnArrayView columnArrayView = columnSource.getVerticalDataPointView(x, z);
						LongList dataColumn = reader == null ? fullDataSource.getColumnAtRelPos(x, z)
							: reader.column(x * FullDataSourceV2.WIDTH + z);

						updateColumnWithScope(
								levelWrapper, fullDataSource, columnSource, x, z,
								// bitshift is to account for LODs with a detail level greater than 0 so the block pos is correct
								baseX + BitShiftUtil.pow(x,dataDetail), baseZ + BitShiftUtil.pow(z,dataDetail),
								columnArrayView, dataColumn, preserveSemanticMaterials, conversionScope);
					}
				}

			}

			columnSource.fillDebugFlag(0, 0, ColumnRenderSource.WIDTH, ColumnRenderSource.WIDTH, ColumnRenderSource.DebugSourceFlag.FULL);

			return columnSource;
		}
		catch (RuntimeException | Error failure)
		{
			columnSource.close();
			throw failure;
		}
	}

	/** Updates the given {@link ColumnArrayView} to match the incoming Full data {@link LongList} */
	public static void updateOrReplaceRenderDataViewColumnWithFullDataColumn(
			IClientLevelWrapper levelWrapper,
			FullDataSourceV2 fullDataSource, ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
			int blockX, int blockZ,
			ColumnArrayView columnArrayView, 
			LongList fullDataColumn)
	{
		updateOrReplaceRenderDataViewColumnWithFullDataColumn(levelWrapper, fullDataSource, columnSource,
			renderSourceX, renderSourceZ, blockX, blockZ, columnArrayView, fullDataColumn,
			net.vulkanic.world.DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired());
	}

	/**
	 * As above. {@code preserveSemanticMaterials} false builds exactly the
	 * legacy (Frozen) column: same-color neighbors merge regardless of block
	 * identity and no semantic sidecars are computed.
	 */
	public static void updateOrReplaceRenderDataViewColumnWithFullDataColumn(
			IClientLevelWrapper levelWrapper,
			FullDataSourceV2 fullDataSource, ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
			int blockX, int blockZ,
			ColumnArrayView columnArrayView, 
			LongList fullDataColumn, boolean preserveSemanticMaterials)
	{
		updateColumnWithScope(levelWrapper, fullDataSource, columnSource, renderSourceX, renderSourceZ,
			blockX, blockZ, columnArrayView, fullDataColumn, preserveSemanticMaterials, null);
	}

	private static void updateColumnWithScope(
			IClientLevelWrapper levelWrapper,
			FullDataSourceV2 fullDataSource, ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
			int blockX, int blockZ,
			ColumnArrayView columnArrayView,
			LongList fullDataColumn, boolean preserveSemanticMaterials,
			net.vulkanic.world.NativeDhConversionScope conversionScope)
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
				blockX, blockZ, columnArrayView, fullDataColumn, preserveSemanticMaterials, null, null, null, conversionScope
			);
		}
		else
		{
			LongList nativeScratch = conversionScope != null
				&& fullDataColumn instanceof net.minecraft.world.level.chunk.NativeDhConversionReader
				? conversionScope.temporaryOutput(fullDataLength) : null;
			PhantomArrayListCheckout checkout = nativeScratch == null ? ARRAY_LIST_POOL.checkoutArrays(0, 0, 1) : null;
			LongList dataArrayList = nativeScratch != null ? nativeScratch : checkout.getLongArray(0, fullDataLength);
			
			try
			{
				// expand the ColumnArrayView to fit the new larger max vertical size
				ColumnArrayView newColumnArrayView = new ColumnArrayView(dataArrayList, fullDataLength, 0, fullDataLength);
				int[] expandedSemanticMaterials = preserveSemanticMaterials ? new int[fullDataLength] : null;
				byte[] expandedVariantStates = preserveSemanticMaterials ? new byte[fullDataLength] : null;
				long[] expandedVariantPositions = preserveSemanticMaterials ? new long[fullDataLength] : null;
				setRenderColumnView(
					levelWrapper, fullDataSource, columnSource, renderSourceX, renderSourceZ,
					blockX, blockZ, newColumnArrayView, fullDataColumn, preserveSemanticMaterials, expandedSemanticMaterials,
					expandedVariantStates, expandedVariantPositions, conversionScope
				);
				
				int[] reducedSemanticMaterials = preserveSemanticMaterials ? new int[columnArrayView.size()] : null;
				if (preserveSemanticMaterials)
				{
					RenderDataPointUtil.mergeMultiData(newColumnArrayView, expandedSemanticMaterials,
						columnArrayView, reducedSemanticMaterials);
				}
				else
				{
					RenderDataPointUtil.mergeMultiData(newColumnArrayView, columnArrayView);
				}
				for (int index = 0; preserveSemanticMaterials && index < columnArrayView.verticalSize(); index++)
				{
					List<ColumnRenderSource.SemanticMaterialSpan> spans = reducedSemanticMaterialSpans(
						newColumnArrayView,
						expandedSemanticMaterials,
						expandedVariantStates,
						expandedVariantPositions,
						columnArrayView.get(index)
					);
					columnSource.setSemanticMaterialId(
						renderSourceX, renderSourceZ, index, reducedSemanticMaterials[index]
					);
					// Preserve a model-selection position when the complete reduced
					// interval agrees on one exact source. Reduction may still combine
					// different positions or unavailable sources; those remain explicit
					// mixed/unavailable states rather than receiving a guessed seed.
					byte reducedVariantState = reducedSemanticVariantState(
						reducedSemanticMaterials[index], spans
					);
					long reducedVariantPosition = reducedVariantState == ColumnRenderSource.SEMANTIC_VARIANT_EXACT
						? spans.getFirst().variantPosition() : 0L;
					columnSource.setSemanticVariantProvenance(
						renderSourceX, renderSourceZ, index,
						reducedVariantState, reducedVariantPosition
					);
					if (spans.size() > 1)
					{
						columnSource.setSemanticMaterialSpans(renderSourceX, renderSourceZ, index, spans);
					}
				}
			}
			finally
			{
				if (checkout != null) ARRAY_LIST_POOL.returnCheckout(checkout);
			}
		}
		if (!preserveSemanticMaterials)
		{
			return;
		}
		boolean horizontalUniform = fullDataSource.getDataDetailLevel() == 0
			|| fullDataSource.hasSemanticHorizontalUniformity(renderSourceX, renderSourceZ);
		LongList[] horizontalContributors = fullDataSource.getRetainedSemanticHorizontalContributors(renderSourceX, renderSourceZ);
		columnSource.setSemanticHorizontalContributors(renderSourceX, renderSourceZ, horizontalContributors);
		for (int index = 0; index < columnArrayView.verticalSize(); index++) {
			columnSource.setSemanticHorizontalUniformity(
				renderSourceX, renderSourceZ, index, horizontalUniform
			);
			if (horizontalContributors != null && !horizontalUniform) {
				columnSource.setSemanticHorizontalContributorSpans(
					renderSourceX, renderSourceZ, index,
					resolveHorizontalContributorSpans(
						fullDataSource, columnSource, horizontalContributors,
						blockX, blockZ, fullDataSource.getDataDetailLevel(), columnArrayView.get(index)
					)
				);
			}
		}
	}

	private static ColumnRenderSource.SemanticHorizontalContributor[] resolveHorizontalContributorSpans(
		FullDataSourceV2 fullDataSource, ColumnRenderSource columnSource,
		LongList[] contributors, int blockX, int blockZ, byte dataDetail, long reducedData
	)
	{
		if (contributors.length != 4 || !RenderDataPointUtil.doesDataPointExist(reducedData)) return null;
		int childWidth = dataDetail <= 0 ? 1 : BitShiftUtil.pow(1, dataDetail - 1);
		ColumnRenderSource.SemanticHorizontalContributor[] result =
			new ColumnRenderSource.SemanticHorizontalContributor[4];
		for (int contributorIndex = 0; contributorIndex < 4; contributorIndex++) {
			LongList column = contributors[contributorIndex];
			if (column == null || column.isEmpty()) continue;
			int childX = blockX + (contributorIndex / 2) * childWidth;
			int childZ = blockZ + (contributorIndex % 2) * childWidth;
			java.util.ArrayList<ColumnRenderSource.SemanticMaterialSpan> spans = new java.util.ArrayList<>();
			for (int point = 0; point < column.size(); point++) {
				long data = column.getLong(point);
				if (!RenderDataPointUtil.doesDataPointExist(data)) continue;
				int id = FullDataPointUtil.getId(data);
				try {
					String block = fullDataSource.mapping.getBlockStateWrapper(id).getSerialString();
					String biome = fullDataSource.mapping.getBiomeWrapper(id).getSerialString();
					int materialId = columnSource.internSemanticMaterial(block, biome);
					int minY = FullDataPointUtil.getBottomY(data);
					int maxY = minY + FullDataPointUtil.getHeight(data);
					spans.add(new ColumnRenderSource.SemanticMaterialSpan(
						minY, maxY, materialId,
						ColumnRenderSource.SEMANTIC_VARIANT_EXACT,
						ColumnRenderSource.packSemanticVariantPosition(childX, minY + columnSource.yOffset, childZ)
					));
				} catch (RuntimeException ignored) {
					// An invalid source ID is unavailable, never a guessed identity.
				}
			}
			if (!spans.isEmpty()) result[contributorIndex] =
				new ColumnRenderSource.SemanticHorizontalContributor(spans);
		}
		return result;
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

	/**
	 * Derives the only variant state that is safe to publish for a reduced
	 * render-data point. A position is exact only when the reduced material and
	 * every contributing source span agree on both identity and model seed.
	 */
	static byte reducedSemanticVariantState(
		int reducedMaterialId,
		List<ColumnRenderSource.SemanticMaterialSpan> spans
	)
	{
		if (spans == null || spans.isEmpty() || reducedMaterialId <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
		{
			return ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE;
		}
		ColumnRenderSource.SemanticMaterialSpan first = spans.getFirst();
		if (first.materialId() <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
			|| first.variantState() != ColumnRenderSource.SEMANTIC_VARIANT_EXACT)
		{
			return first.variantState() == ColumnRenderSource.SEMANTIC_VARIANT_MIXED
				? ColumnRenderSource.SEMANTIC_VARIANT_MIXED
				: ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE;
		}
		for (ColumnRenderSource.SemanticMaterialSpan span : spans)
		{
			if (span.materialId() <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
			{
				return ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE;
			}
			if (span.materialId() != reducedMaterialId
				|| span.variantState() != ColumnRenderSource.SEMANTIC_VARIANT_EXACT)
			{
				return ColumnRenderSource.SEMANTIC_VARIANT_MIXED;
			}
			if (span.variantPosition() != first.variantPosition())
			{
				return ColumnRenderSource.SEMANTIC_VARIANT_MIXED;
			}
		}
		return ColumnRenderSource.SEMANTIC_VARIANT_EXACT;
	}
	private static void setRenderColumnView(
			IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
			ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
			int blockX, int blockZ,
			ColumnArrayView renderColumnData, LongList fullColumnData,
			boolean preserveSemanticMaterials, int[] semanticMaterialScratch,
			byte[] semanticVariantStateScratch, long[] semanticVariantPositionScratch,
			net.vulkanic.world.NativeDhConversionScope conversionScope)
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
		
		IWrapperFactory wrapperFactory = WrapperDependencies.FACTORY;
		HashSet<IBlockStateWrapper> blockStatesToIgnore = wrapperFactory.getRendererIgnoredBlocks(levelWrapper);
		HashSet<IBlockStateWrapper> caveBlockStatesToIgnore = wrapperFactory.getRendererIgnoredCaveBlocks(levelWrapper);
		
		// build snow block cache if needed
		if (snowLayerBlockStates == null)
		{
			snowLayerBlockStates = new HashSet<>();
			// ignore snow layers 1-3, everything above should be considered a full block
			snowLayerBlockStates.add(wrapperFactory.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:1}", levelWrapper));
			snowLayerBlockStates.add(wrapperFactory.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:2}", levelWrapper));
			snowLayerBlockStates.add(wrapperFactory.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:3}", levelWrapper));
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
		
		int flags = (ignoreNonCollidingBlocks ? 1 : 0) | (colorBelowWithAvoidedBlocks ? 2 : 0)
			| (caveCullingEnabled ? 4 : 0) | (preserveSemanticMaterials ? 8 : 0)
			| (RenderDataPointUtil.RUN_VALIDATION ? 16 : 0);
		RenderColumnConversionContext.convert(flags, caveCullingMaxY,
			levelWrapper, fullDataSource, columnSource,
				renderSourceX, renderSourceZ, blockX, blockZ, renderColumnData, fullColumnData,
				blockStatesToIgnore, caveBlockStatesToIgnore, snowLayerBlockStates,
				semanticMaterialScratch, semanticVariantStateScratch, semanticVariantPositionScratch, conversionScope);
	}

	static boolean sameSemanticMaterial(int leftMaterialId, int rightMaterialId)
	{
		return leftMaterialId == rightMaterialId;
	}

	static int semanticMaterialAt(
		ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		int[] semanticMaterialScratch, int index
	)
	{
		return semanticMaterialScratch == null
			? columnSource.getSemanticMaterialId(renderSourceX, renderSourceZ, index)
			: semanticMaterialScratch[index];
	}

	static void setSemanticMaterialAt(
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

	static void setSemanticVariantAt(
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

	static void mergeSemanticVariantAt(
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
