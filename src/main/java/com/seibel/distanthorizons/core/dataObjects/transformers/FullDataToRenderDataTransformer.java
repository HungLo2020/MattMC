package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.Nullable;
import java.util.HashSet;
import java.util.List;

/**
 * Converts a {@link FullDataSourceV2} into a {@link ColumnRenderSource}. Rust
 * converts every column in one call ({@code NativeDhRenderConversion}); Java
 * answers its contextual block and color queries and, for semantic diagnostic
 * builds, records each column's material sidecars.
 */
public class FullDataToRenderDataTransformer
{
	// Pure semantic helpers can run before platform dependencies are registered.
	private static final class WrapperDependencies
	{
		private static final IWrapperFactory FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	}
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
		
		final long pos = fullDataSource.getPos();
		final int vertSize = Config.Client.Advanced.Graphics.Quality.verticalQuality.get().calculateMaxVerticalData(fullDataSource.getDataDetailLevel());
		final ColumnRenderSource columnSource = ColumnRenderSource.createEmpty(pos, vertSize, levelWrapper.getMinHeight());
		try
		{
			if (fullDataSource.isEmpty)
			{
				return columnSource;
			}
			columnSource.markNotEmpty();
			
			boolean preserveSemanticMaterials =
				net.vulkanic.world.DistantHorizonsSemanticCollector.semanticMaterialPreservationRequired();
			boolean ignoreNonCollidingBlocks = (Config.Client.Advanced.Graphics.Quality.blocksToIgnore.get() == EDhApiBlocksToAvoid.NON_COLLIDING);
			boolean colorBelowWithAvoidedBlocks = Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.get();
			IWrapperFactory wrapperFactory = WrapperDependencies.FACTORY;
			HashSet<IBlockStateWrapper> blockStatesToIgnore = wrapperFactory.getRendererIgnoredBlocks(levelWrapper);
			HashSet<IBlockStateWrapper> caveBlockStatesToIgnore = wrapperFactory.getRendererIgnoredCaveBlocks(levelWrapper);
			if (snowLayerBlockStates == null)
			{
				// ignore snow layers 1-3, everything above should be considered a full block
				HashSet<IBlockStateWrapper> snow = new HashSet<>();
				snow.add(wrapperFactory.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:1}", levelWrapper));
				snow.add(wrapperFactory.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:2}", levelWrapper));
				snow.add(wrapperFactory.deserializeBlockStateWrapperOrGetDefault("minecraft:snow_STATE_{layers:3}", levelWrapper));
				snowLayerBlockStates = snow;
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
			
			try (var scope = net.vulkanic.world.NativeDhConversionScope.create(fullDataSource))
			{
				RenderColumnConversionContext.convert(flags, caveCullingMaxY, levelWrapper, fullDataSource, columnSource,
					blockStatesToIgnore, caveBlockStatesToIgnore, snowLayerBlockStates, preserveSemanticMaterials, scope);
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
	
	
	
	//=================//
	// semantic column //
	//=================//
	
	/**
	 * Semantic builds only: reduces a tall column with its material sidecars and
	 * records the column's span, uniformity and contributor provenance.
	 *
	 * @param expandedLength the tall column's length in the scratch source, or 0 when it converted in place
	 */
	static void finishSemanticColumn(
		FullDataSourceV2 fullDataSource, ColumnRenderSource columnSource, int renderSourceX, int renderSourceZ,
		int blockX, int blockZ, net.vulkanic.world.NativeDhRenderSource scratch, int expandedLength,
		int[] expandedSemanticMaterials, byte[] expandedVariantStates, long[] expandedVariantPositions)
	{
		ColumnArrayView columnArrayView = columnSource.getVerticalDataPointView(renderSourceX, renderSourceZ);
		if (expandedLength > 0)
		{
			ColumnArrayView newColumnArrayView = new ColumnArrayView(scratch, expandedLength, 0, expandedLength);
			int[] reducedSemanticMaterials = new int[columnArrayView.size()];
			RenderDataPointUtil.mergeMultiData(newColumnArrayView, expandedSemanticMaterials,
				columnArrayView, reducedSemanticMaterials);
			for (int index = 0; index < columnArrayView.verticalSize(); index++)
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
