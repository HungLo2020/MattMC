package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Used to populate the buffers in a {@link ColumnRenderSource} object.
 *
 * @see ColumnRenderSource
 */
public class ColumnRenderBufferBuilder
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Column Buffer Builder");
	
	
	
	//==============//
	// vbo building //
	//==============//
	
	/** @link adjData should be null for adjacent sections that cross detail level boundaries */
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
			// A selected Rust whole-frame route completes this CPU build without a
			// Java VBO by design. Its copied semantic asset is now owned by the
			// collector, so treating it as a failed legacy upload would immediately
			// retire the asset before the real quadtree can select it.
			if (uploadedBuffer != null
				&& !uploadedBuffer.buffersUploaded
				&& shouldCloseUnuploadedContainer(
					net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()
				))
			{
				uploadedBuffer.close();
			}
		});
		return uploadFuture;
	}

	static boolean shouldCloseUnuploadedContainer(boolean rustWholeFrameSemanticBuild)
	{
		return !rustWholeFrameSemanticBuild;
	}
	public static void makeLodRenderData(
			LodQuadBuilder quadBuilder, ColumnRenderSource renderSource, IDhClientLevel clientLevel,
			ColumnRenderSource[] adjRegions, boolean[] isSameDetailLevel)
	{
		//=============//
		// debug check //
		//=============//
		
		// can be used to limit which section positions are build and thus, rendered
		// useful when debugging a specific section
		boolean columnBuilderDebugEnabled = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugEnable.get();
		if (columnBuilderDebugEnabled)
		{
			if (DhSectionPos.getDetailLevel(renderSource.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugDetailLevel.get()
				&& DhSectionPos.getX(renderSource.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXPos.get()
				&& DhSectionPos.getZ(renderSource.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZPos.get())
			{
				int breakpoint = 0;
			}
			else
			{
				return;
			}
		}
		
		
		
		//===================//
		// build each column //
		//===================//
		
		// pooled arrays for ColumnBox use
		try (PhantomArrayListCheckout phantomArrayCheckout = ARRAY_LIST_POOL.checkoutArrays(0, 0, 2))
		{
			byte thisDetailLevel = renderSource.getDataDetailLevel();
			for (int relX = 0; relX < ColumnRenderSource.WIDTH; relX++)
			{
				for (int relZ = 0; relZ < ColumnRenderSource.WIDTH; relZ++)
				{
					// ignore empty/null columns
					ColumnArrayView columnRenderData = renderSource.getVerticalDataPointView(relX, relZ);
					if (columnRenderData.size() == 0
							|| !RenderDataPointUtil.doesDataPointExist(columnRenderData.get(0))
							|| RenderDataPointUtil.hasZeroHeight(columnRenderData.get(0)))
					{
						continue;
					}
					
					
					
					//=============//
					// debug limit //
					//=============//
					
					// can be used to limit the buffer building to a specific relative position.
					// useful for debugging a single column
					if (columnBuilderDebugEnabled)
					{
						int wantedX = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXRow.get();
						if (wantedX >= 0 && relX != wantedX)
						{
							continue;
						}
						int wantedZ = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZRow.get();
						if (wantedZ >= 0 && relZ != wantedZ)
						{
							continue;
						}
					}
					
					
					
					//==================================//
					// get adjacent render data columns //
					//==================================//
					
					ColumnArrayView[] adjColumnViews = new ColumnArrayView[EDhDirection.CARDINAL_COMPASS.length];
					for (EDhDirection direction : EDhDirection.CARDINAL_COMPASS)
					{
						try
						{
							int xAdj = relX + direction.normal.x;
							int zAdj = relZ + direction.normal.z;
							boolean isCrossRenderSourceBoundary =
									(xAdj < 0 || xAdj >= ColumnRenderSource.WIDTH) ||
											(zAdj < 0 || zAdj >= ColumnRenderSource.WIDTH);
							
							ColumnRenderSource adjRenderSource;
							byte adjDetailLevel;
							
							
							
							//=========================//
							// get the adjacent render //
							// source if present       //
							//=========================//
							
							if (!isCrossRenderSourceBoundary)
							{
								// the adjacent position is inside this same render source
								adjRenderSource = renderSource;
								adjDetailLevel = thisDetailLevel;
							}
							else
							{
								// the adjacent position is outside this render source
								
								// skip empty sections
								adjRenderSource = adjRegions[direction.compassIndex];
								if (adjRenderSource == null)
								{
									continue;
								}
								
								adjDetailLevel = adjRenderSource.getDataDetailLevel();
								if (adjDetailLevel == thisDetailLevel)
								{
									// if the adjacent position is outside this render source,
									// wrap the position around so it's inside the adjacent source
									
									if (xAdj < 0)
									{
										xAdj += ColumnRenderSource.WIDTH;
									}
									if (xAdj >= ColumnRenderSource.WIDTH)
									{
										xAdj -= ColumnRenderSource.WIDTH;
									}
									
									if (zAdj < 0)
									{
										zAdj += ColumnRenderSource.WIDTH;
									}
									if (zAdj >= ColumnRenderSource.WIDTH)
									{
										zAdj -= ColumnRenderSource.WIDTH;
									}
								}
							}
							
							
							
							//========================//
							// get the adjacent views //
							//========================//
							
							// the old logic handled additional cases, but they never appeared to fire,
							// so just these two cases should be fine
							boolean expectedDetailLevels = (adjDetailLevel == thisDetailLevel) || (adjDetailLevel > thisDetailLevel);
							if (!expectedDetailLevels)
							{
								LodUtil.assertNotReach("Mismatch between adjacent detail level ["+adjDetailLevel+"] and this render source's detail level ["+thisDetailLevel+"]. Detail levels should be adj >= this.");
							}
							
							adjColumnViews[direction.compassIndex] = adjRenderSource.getVerticalDataPointView(xAdj, zAdj);
						}
						catch (RuntimeException e)
						{
							LOGGER.warn("Failed to get adj data for relative pos: [" + thisDetailLevel + ":" + relX + "," + relZ + "] at [" + direction + "], Error: [" + e.getMessage() + "].", e);
						}
					} // for adjacent directions
					
					
					
					//==========================//
					// build this render column //
					//==========================//
					
					ColumnRenderSource.DebugSourceFlag debugSourceFlag = renderSource.debugGetFlag(relX, relZ);
					
					for (int i = 0; i < columnRenderData.size(); i++)
					{
						// can be uncommented to limit which vertical LOD is generated
						if (Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugEnable.get())
						{
							int wantedColumnIndex = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugColumnIndex.get();
							if (wantedColumnIndex >= 0
									&& i != wantedColumnIndex)
							{
								continue;
							}
						}
						
						long data = columnRenderData.get(i);
						// If the data is not render-able (Void or non-existing) we stop since there is
						// no data left in this position
						if (RenderDataPointUtil.hasZeroHeight(data)
								|| !RenderDataPointUtil.doesDataPointExist(data))
						{
							break;
						}
						net.vulkanic.world.DistantHorizonsSemanticCollector.recordWaterSourceInput(
							renderSource.pos,
							thisDetailLevel,
							DhSectionPos.getMinCornerBlockX(renderSource.pos) + (relX << thisDetailLevel),
							clientLevel.getLevelWrapper().getMinHeight(),
							DhSectionPos.getMinCornerBlockZ(renderSource.pos) + (relZ << thisDetailLevel),
							data,
							renderSource.getSemanticMaterialId(relX, relZ, i)
						);
						
						long topDataPoint = (i - 1) >= 0 ? columnRenderData.get(i - 1) : RenderDataPointUtil.EMPTY_DATA;
						long bottomDataPoint = (i + 1) < columnRenderData.size() ? columnRenderData.get(i + 1) : RenderDataPointUtil.EMPTY_DATA;
						SemanticMaterialProvenance semanticMaterial = semanticMaterialProvenanceForDetailLevel(
							net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild(),
							thisDetailLevel,
							renderSource.getSemanticMaterialId(relX, relZ, i),
							renderSource.getSemanticVariantState(relX, relZ, i),
							renderSource.getSemanticVariantPosition(relX, relZ, i),
							renderSource.hasSemanticHorizontalUniformity(relX, relZ, i)
						);
					ColumnRenderSource.SemanticHorizontalContributor[] horizontalContributors =
						renderSource.getSemanticHorizontalContributorSpans(relX, relZ, i);
					List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans =
						resolveSemanticMaterialSpans(renderSource, thisDetailLevel, relX, relZ, i, quadBuilder);
					if (thisDetailLevel > 0 && !renderSource.hasSemanticHorizontalUniformity(relX, relZ, i))
					{
						List<ColumnRenderSource.SemanticMaterialSpan> commonHorizontalSpans =
							recoverCommonHorizontalSpans(data, horizontalContributors);
						if (!commonHorizontalSpans.isEmpty())
						{
							semanticMaterialSpans = commonHorizontalSpans.stream()
								.map(span -> withBuilderMaterial(renderSource, quadBuilder, span))
								.toList();
						}
						semanticMaterial = recoverCommonHorizontalProvenance(
							renderSource, data, horizontalContributors, semanticMaterial
						);
					}
					int semanticMaterialId = semanticMaterial.materialId();
						if (semanticMaterialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
						{
							semanticMaterialId = quadBuilder.internSemanticMaterial(
								renderSource.getSemanticMaterialIdentity(semanticMaterialId)
							);
						}
						addRenderDataPointToBuilder(
								renderSource, clientLevel, phantomArrayCheckout,
								data, topDataPoint, bottomDataPoint,
								adjColumnViews, isSameDetailLevel,
							thisDetailLevel, relX, relZ, semanticMaterialId,
							semanticMaterial.variantState(), semanticMaterial.variantPosition(),
							semanticMaterialSpans,
						horizontalContributors,
							quadBuilder, debugSourceFlag);
					}
					
				}// for z
			}// for x
		}// phantom checkout
		
		quadBuilder.mergeQuads();
	}

	/**
	 * Exact atlas provenance is only truthful for block-resolution DH geometry.
	 * At wider detail levels one emitted face represents multiple horizontal block
	 * cells, while this sidecar has only one material identity. Repeating that
	 * one sprite across the whole LOD face would assign a valid texture to the
	 * wrong terrain. Keep the reduced DH material path for those faces until the
	 * transport can express horizontal contributor coverage.
	 */
	static SemanticMaterialProvenance semanticMaterialProvenanceForDetailLevel(
		boolean rustWholeFrameSemanticBuild, byte detailLevel, int materialId,
		byte variantState, long variantPosition
	)
	{
		return semanticMaterialProvenanceForDetailLevel(
			rustWholeFrameSemanticBuild, detailLevel, materialId, variantState, variantPosition,
			detailLevel == 0
		);
	}

	static SemanticMaterialProvenance semanticMaterialProvenanceForDetailLevel(
		boolean rustWholeFrameSemanticBuild, byte detailLevel, int materialId,
		byte variantState, long variantPosition, boolean horizontalUniform
	)
	{
		if (rustWholeFrameSemanticBuild && detailLevel > 0 && !horizontalUniform)
		{
			return new SemanticMaterialProvenance(
				ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE,
				ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE,
				0L
			);
		}
		return new SemanticMaterialProvenance(materialId, variantState, variantPosition);
	}

	static record SemanticMaterialProvenance(int materialId, byte variantState, long variantPosition) { }

	/**
	 * Recovers a coarse cell's semantic identity only when every bounded
	 * horizontal contributor proves the same complete vertical source interval.
	 * This does not make heterogeneous terrain uniform: layered, missing, or
	 * differently seeded contributors remain explicitly unavailable/mixed.
	 */
	static SemanticMaterialProvenance recoverCommonHorizontalProvenance(
		ColumnRenderSource renderSource,
		long renderData,
		ColumnRenderSource.SemanticHorizontalContributor[] contributors,
		SemanticMaterialProvenance fallback
	)
	{
		if (contributors == null || contributors.length != 4 || !RenderDataPointUtil.doesDataPointExist(renderData))
		{
			return fallback;
		}
		int minY = RenderDataPointUtil.getYMin(renderData);
		int maxY = RenderDataPointUtil.getYMax(renderData);
		ColumnRenderSource.SemanticMaterialSpan first = null;
		for (ColumnRenderSource.SemanticHorizontalContributor contributor : contributors)
		{
			List<ColumnRenderSource.SemanticMaterialSpan> spans = clippedContributorSpans(contributor, minY, maxY);
			if (spans.size() != 1)
			{
				return fallback;
			}
			ColumnRenderSource.SemanticMaterialSpan span = spans.getFirst();
			if (span.minY() != minY || span.maxY() != maxY
				|| span.materialId() <= ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE
				|| span.variantState() != ColumnRenderSource.SEMANTIC_VARIANT_EXACT)
			{
				return fallback;
			}
			if (first == null)
			{
				first = span;
			}
			else if (span.materialId() != first.materialId()
				|| span.variantPosition() != first.variantPosition())
			{
				return fallback;
			}
		}
		return new SemanticMaterialProvenance(
			first.materialId(), ColumnRenderSource.SEMANTIC_VARIANT_EXACT, first.variantPosition()
		);
	}

	/** Returns a shared, clipped vertical layer sequence when all contributors agree. */
	static List<ColumnRenderSource.SemanticMaterialSpan> recoverCommonHorizontalSpans(
		long renderData,
		ColumnRenderSource.SemanticHorizontalContributor[] contributors
	)
	{
		if (contributors == null || contributors.length != 4 || !RenderDataPointUtil.doesDataPointExist(renderData))
		{
			return List.of();
		}
		int minY = RenderDataPointUtil.getYMin(renderData);
		int maxY = RenderDataPointUtil.getYMax(renderData);
		List<ColumnRenderSource.SemanticMaterialSpan> first = null;
		for (ColumnRenderSource.SemanticHorizontalContributor contributor : contributors)
		{
			List<ColumnRenderSource.SemanticMaterialSpan> clipped = clippedContributorSpans(contributor, minY, maxY);
			if (clipped.isEmpty() || clipped.getFirst().minY() != minY
				|| clipped.getLast().maxY() != maxY)
			{
				return List.of();
			}
			if (first == null) first = clipped;
			else if (!first.equals(clipped)) return List.of();
		}
		return first == null ? List.of() : first;
	}

	/**
	 * A vertically reduced block-resolution DH entry can cover several real
	 * source blocks. Convert its material intervals into builder-local IDs for
	 * the CPU sidecar; no texture or backend object crosses this boundary.
	 */
	private static List<ColumnRenderSource.SemanticMaterialSpan> resolveSemanticMaterialSpans(
		ColumnRenderSource renderSource, byte detailLevel, int relX, int relZ, int verticalIndex,
		LodQuadBuilder quadBuilder
	)
	{
		// Coarse LOD may retain vertical source intervals only after the producer
		// proves that its complete horizontal footprint is one semantic column.
		// Without that proof, these spans describe only one contributor and cannot
		// safely select a texture for the whole coarse face.
		if (!net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()
			|| (detailLevel > 0 && !renderSource.hasSemanticHorizontalUniformity(relX, relZ, verticalIndex)))
		{
			return List.of();
		}
		List<ColumnRenderSource.SemanticMaterialSpan> sourceSpans =
			renderSource.getSemanticMaterialSpans(relX, relZ, verticalIndex);
		if (sourceSpans.isEmpty())
		{
			return List.of();
		}
		List<ColumnRenderSource.SemanticMaterialSpan> resolved = new ArrayList<>(sourceSpans.size());
		for (ColumnRenderSource.SemanticMaterialSpan span : sourceSpans)
		{
			int materialId = span.materialId();
			if (materialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
			{
				materialId = quadBuilder.internSemanticMaterial(
					renderSource.getSemanticMaterialIdentity(materialId)
				);
			}
			resolved.add(new ColumnRenderSource.SemanticMaterialSpan(
				span.minY(), span.maxY(), materialId, span.variantState(), span.variantPosition()
			));
		}
		return List.copyOf(resolved);
	}

	private static void addRenderDataPointToBuilder(
			ColumnRenderSource renderSource, IDhClientLevel clientLevel, PhantomArrayListCheckout phantomArrayCheckout,
			long renderData, long topRenderData, long bottomRenderData, 
			ColumnArrayView[] adjColumnViews, boolean[] isSameDetailLevel,
			byte detailLevel, int renderSourceOffsetPosX, int renderSourceOffsetPosZ, int semanticMaterialId,
			byte semanticVariantState, long semanticVariantPosition,
			List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans,
			ColumnRenderSource.SemanticHorizontalContributor[] horizontalContributors,
			LodQuadBuilder quadBuilder, ColumnRenderSource.DebugSourceFlag debugSource)
	{
		long sectionPos = DhSectionPos.encode(detailLevel, renderSourceOffsetPosX, renderSourceOffsetPosZ);
		
		short blockWidth = (short) DhSectionPos.getDetailLevelWidthInBlocks(detailLevel);
		short blockMinX = (short) DhSectionPos.getMinCornerBlockX(sectionPos);
		short blockMinY = RenderDataPointUtil.getYMin(renderData);
		short blockMinZ = (short) DhSectionPos.getMinCornerBlockZ(sectionPos);
		short blockMaxY = (short) (RenderDataPointUtil.getYMax(renderData) - blockMinY);
		
		if (blockMaxY == 0)
		{
			return;
		}
		else if (blockMaxY < 0)
		{
			throw new IllegalArgumentException("Negative y size for the renderDataPoint! Data: [" + RenderDataPointUtil.toString(renderData) + "].");
		}
		
		byte blockMaterialId = RenderDataPointUtil.getBlockMaterialId(renderData);
		
		
		
		int color;
		boolean fullBright = false;
		EDhApiDebugRendering debugging = Config.Client.Advanced.Debugging.debugRendering.get();
		switch (debugging)
		{
			case OFF:
			{
				float saturationMultiplier = Config.Client.Advanced.Graphics.Quality.saturationMultiplier.get().floatValue();
				float brightnessMultiplier = Config.Client.Advanced.Graphics.Quality.brightnessMultiplier.get().floatValue();
				if (saturationMultiplier == 1.0 && brightnessMultiplier == 1.0)
				{
					color = RenderDataPointUtil.getColor(renderData);
				}
				else
				{
					float[] ahsv = ColorUtil.argbToAhsv(RenderDataPointUtil.getColor(renderData));
					color = ColorUtil.ahsvToArgb(ahsv[0], ahsv[1], ahsv[2] * saturationMultiplier, ahsv[3] * brightnessMultiplier);
				}
				break;
			}
			case SHOW_DETAIL:
			{
				color = LodUtil.DEBUG_DETAIL_LEVEL_COLORS[detailLevel];
				fullBright = true;
				break;
			}
			case SHOW_BLOCK_MATERIAL:
			{
				
				switch (EDhApiBlockMaterial.getFromIndex(blockMaterialId))
				{
					case UNKNOWN:
					case AIR: // shouldn't normally be rendered, but just in case
						color = ColorUtil.HOT_PINK;
						break;
					
					case LEAVES:
						color = ColorUtil.DARK_GREEN;
						break;
					case STONE:
						color = ColorUtil.GRAY;
						break;
					case WOOD:
						color = ColorUtil.BROWN;
						break;
					case METAL:
						color = ColorUtil.DARK_GRAY;
						break;
					case DIRT:
						color = ColorUtil.LIGHT_BROWN;
						break;
					case LAVA:
						color = ColorUtil.ORANGE;
						break;
					case DEEPSLATE:
						color = ColorUtil.BLACK;
						break;
					case SNOW:
						color = ColorUtil.WHITE;
						break;
					case SAND:
						color = ColorUtil.TAN;
						break;
					case TERRACOTTA:
						color = ColorUtil.DARK_ORANGE;
						break;
					case NETHER_STONE:
						color = ColorUtil.DARK_RED;
						break;
					case WATER:
						color = ColorUtil.BLUE;
						break;
					case GRASS:
						color = ColorUtil.GREEN;
						break;
					case ILLUMINATED:
						color = ColorUtil.YELLOW;
						break;
					
					default:
						// undefined color
						color = ColorUtil.CYAN;
						break;
				}
				
				fullBright = true;
				break;
			}
			case SHOW_OVERLAPPING_QUADS:
			{
				color = ColorUtil.WHITE;
				fullBright = true;
				break;
			}
			case SHOW_RENDER_SOURCE_FLAG:
			{
				color = debugSource == null ? ColorUtil.RED : debugSource.color;
				fullBright = true;
				break;
			}
			default:
				throw new IllegalArgumentException("Unknown debug mode: " + debugging);
		}
		
		if (detailLevel > 0 && emitHorizontalContributorBoxes(
			quadBuilder, renderSource, phantomArrayCheckout, clientLevel, blockMinX, blockMinY, blockMinZ, blockWidth, blockMaxY,
							color, blockMaterialId, RenderDataPointUtil.getLightSky(renderData),
							fullBright ? LodUtil.MAX_MC_LIGHT : RenderDataPointUtil.getLightBlock(renderData),
							horizontalContributors, topRenderData, bottomRenderData, adjColumnViews, isSameDetailLevel,
							caveCullingMaxY(clientLevel)))
		{
			return;
		}

		ColumnBox.addBoxQuadsToBuilder(
				quadBuilder, phantomArrayCheckout, clientLevel,
				blockWidth, blockMaxY,
				blockMinX, blockMinY, blockMinZ,
				color,
				blockMaterialId,
			semanticMaterialId,
			semanticVariantState,
			semanticVariantPosition,
			semanticMaterialSpans,
			RenderDataPointUtil.getLightSky(renderData),
				fullBright ? LodUtil.MAX_MC_LIGHT : RenderDataPointUtil.getLightBlock(renderData),
				topRenderData, bottomRenderData, adjColumnViews, isSameDetailLevel);
	}

	/** Emits a complete four-cell footprint without creating internal side faces. */
	private static boolean emitHorizontalContributorBoxes(
		LodQuadBuilder builder, ColumnRenderSource renderSource, PhantomArrayListCheckout phantomArrayCheckout, IDhClientLevel clientLevel,
		short minX, short minY, short minZ, short width, short height,
		int color, byte blockMaterialId, byte skyLight, byte blockLight,
		ColumnRenderSource.SemanticHorizontalContributor[] contributors,
		long topData, long bottomData, ColumnArrayView[] adjacent, boolean[] adjacentSameDetail,
		int caveCullingMaxY)
	{
		if (contributors == null || contributors.length != 4 || width < 2 || height <= 0) return false;
		// Until per-contributor neighbour culling is available, only use this
		// path for isolated cells; all ordinary terrain keeps ColumnBox's exact
		// legacy occlusion and transparency rules.
		if (adjacent == null || adjacentSameDetail == null
			|| (Config.Client.Advanced.Graphics.Quality.transparency.get().fakeTransparencyEnabled)) return false;
		boolean transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
		short maxY = (short) (minY + height);
		short childWidth = (short) (width / 2);
		for (int index = 0; index < 4; index++)
		{
			ColumnRenderSource.SemanticHorizontalContributor contributor = contributors[index];
			if (contributor == null || contributor.spans().isEmpty()) return false;
		}
		for (int dx = 0; dx < 2; dx++)
		{
			for (int dz = 0; dz < 2; dz++)
			{
				int index = dx * 2 + dz;
				ColumnRenderSource.SemanticHorizontalContributor contributor = contributors[index];
				int x = minX + dx * childWidth;
				int z = minZ + dz * childWidth;
				List<ColumnRenderSource.SemanticMaterialSpan> spans = clippedContributorSpans(contributor, minY, maxY);
				if (spans.isEmpty() || spans.get(0).minY() > minY
					|| spans.get(spans.size() - 1).maxY() < maxY) return false;
				for (int spanIndex = 0; spanIndex < spans.size(); spanIndex++) {
					ColumnRenderSource.SemanticMaterialSpan span = spans.get(spanIndex);
					int spanId = builder.internSemanticMaterial(renderSource.getSemanticMaterialIdentity(span.materialId()));
					short spanMinY = (short) span.minY();
					short spanHeight = (short) (span.maxY() - span.minY());
					boolean isTopSpan = span.maxY() == maxY;
					boolean isBottomSpan = span.minY() == minY;
					if (isTopSpan) {
						boolean skipTop = RenderDataPointUtil.doesDataPointExist(topData)
							&& RenderDataPointUtil.getYMin(topData) == maxY
							&& !ColumnBox.isTransparent(topData, transparencyEnabled)
							&& (!ColumnBox.isWaterMaterial(blockMaterialId)
								|| ColumnBox.isWaterSurfaceOccludingMaterial(RenderDataPointUtil.getBlockMaterialId(topData)));
						if (!skipTop) builder.addQuadUp((short) x, maxY, (short) z,
							childWidth, childWidth, color, blockMaterialId, skyLight, blockLight,
							spanId, span.variantState(), span.variantPosition());
					}
					if (isBottomSpan) {
						boolean skipBottom = RenderDataPointUtil.doesDataPointExist(bottomData)
							&& RenderDataPointUtil.getYMax(bottomData) == minY
							&& !ColumnBox.isTransparent(bottomData, transparencyEnabled);
						if (!skipBottom) builder.addQuadDown((short) x, minY, (short) z, childWidth, childWidth,
							color, blockMaterialId, skyLight, blockLight,
							spanId, span.variantState(), span.variantPosition());
					}
					ColumnRenderSource.SemanticMaterialSpan builderSpan = withBuilderMaterial(renderSource, builder, span);
					if (dz == 0) addContributorSide(builder, phantomArrayCheckout, EDhDirection.NORTH, x, spanMinY, z,
						childWidth, spanHeight, color, blockMaterialId, skyLight, blockLight,
						builderSpan, adjacent[EDhDirection.NORTH.compassIndex], adjacentSameDetail[EDhDirection.NORTH.compassIndex], caveCullingMaxY);
					if (dz == 1) addContributorSide(builder, phantomArrayCheckout, EDhDirection.SOUTH, x, spanMinY, z,
						childWidth, spanHeight, color, blockMaterialId, skyLight, blockLight,
						builderSpan, adjacent[EDhDirection.SOUTH.compassIndex], adjacentSameDetail[EDhDirection.SOUTH.compassIndex], caveCullingMaxY);
					if (dx == 0) addContributorSide(builder, phantomArrayCheckout, EDhDirection.WEST, x, spanMinY, z,
						childWidth, spanHeight, color, blockMaterialId, skyLight, blockLight,
						builderSpan, adjacent[EDhDirection.WEST.compassIndex], adjacentSameDetail[EDhDirection.WEST.compassIndex], caveCullingMaxY);
					if (dx == 1) addContributorSide(builder, phantomArrayCheckout, EDhDirection.EAST, x, spanMinY, z,
						childWidth, spanHeight, color, blockMaterialId, skyLight, blockLight,
						builderSpan, adjacent[EDhDirection.EAST.compassIndex], adjacentSameDetail[EDhDirection.EAST.compassIndex], caveCullingMaxY);
				}
			}
		}
		return true;
	}

	private static int caveCullingMaxY(IDhClientLevel clientLevel)
	{
		if (!Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get()) return Integer.MIN_VALUE;
		return Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get()
			- clientLevel.getLevelWrapper().getMinHeight();
	}

	private static ColumnRenderSource.SemanticMaterialSpan spanAt(
		ColumnRenderSource.SemanticHorizontalContributor contributor, int y)
	{
		for (ColumnRenderSource.SemanticMaterialSpan span : contributor.spans())
			if (y >= span.minY() && y < span.maxY()) return span;
		return null;
	}

	static List<ColumnRenderSource.SemanticMaterialSpan> clippedContributorSpans(
		ColumnRenderSource.SemanticHorizontalContributor contributor, int minY, int maxY)
	{
		List<ColumnRenderSource.SemanticMaterialSpan> clipped = new ArrayList<>();
		for (ColumnRenderSource.SemanticMaterialSpan span : contributor.spans()) {
			int clippedMin = Math.max(minY, span.minY());
			int clippedMax = Math.min(maxY, span.maxY());
			if (clippedMax > clippedMin) {
				if (!clipped.isEmpty() && clipped.getLast().maxY() != clippedMin) return List.of();
				clipped.add(new ColumnRenderSource.SemanticMaterialSpan(clippedMin, clippedMax,
					span.materialId(), span.variantState(), span.variantPosition()));
			}
		}
		return List.copyOf(clipped);
	}

	private static ColumnRenderSource.SemanticMaterialSpan withBuilderMaterial(
		ColumnRenderSource renderSource, LodQuadBuilder builder, ColumnRenderSource.SemanticMaterialSpan span)
	{
		return new ColumnRenderSource.SemanticMaterialSpan(span.minY(), span.maxY(),
			builder.internSemanticMaterial(renderSource.getSemanticMaterialIdentity(span.materialId())),
			span.variantState(), span.variantPosition());
	}

	private static void addContributorSide(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, EDhDirection direction, int x, short y, int z,
		short width, short height, int color, byte material, byte sky, byte block,
		ColumnRenderSource.SemanticMaterialSpan span, ColumnArrayView adjacent, boolean sameDetail, int caveCullingMaxY)
	{
		if (adjacent == null) {
			builder.addQuadAdj(direction, (short) x, y, (short) z, width, height,
				color, material, sky, block, span.materialId(), span.variantState(), span.variantPosition());
		} else {
			ColumnBox.makeAdjVerticalQuad(builder, phantomArrayCheckout, adjacent, sameDetail,
				caveCullingMaxY,
				direction, (short) x, y, (short) z, width, height,
				color, material, span.materialId(), span.variantState(), span.variantPosition(),
				java.util.List.of(span), block);
		}
	}
	
}
