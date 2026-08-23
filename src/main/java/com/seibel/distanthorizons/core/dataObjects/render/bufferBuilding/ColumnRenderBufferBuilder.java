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
						int semanticMaterialId = semanticMaterial.materialId();
						if (semanticMaterialId > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE)
						{
							semanticMaterialId = quadBuilder.internSemanticMaterial(
								renderSource.getSemanticMaterialIdentity(semanticMaterialId)
							);
						}
						List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans =
							resolveSemanticMaterialSpans(renderSource, thisDetailLevel, relX, relZ, i, quadBuilder);
						
						addRenderDataPointToBuilder(
								clientLevel, phantomArrayCheckout,
								data, topDataPoint, bottomDataPoint,
								adjColumnViews, isSameDetailLevel,
							thisDetailLevel, relX, relZ, semanticMaterialId,
							semanticMaterial.variantState(), semanticMaterial.variantPosition(),
							semanticMaterialSpans,
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
			IDhClientLevel clientLevel, PhantomArrayListCheckout phantomArrayCheckout,
			long renderData, long topRenderData, long bottomRenderData, 
			ColumnArrayView[] adjColumnViews, boolean[] isSameDetailLevel,
			byte detailLevel, int renderSourceOffsetPosX, int renderSourceOffsetPosZ, int semanticMaterialId,
			byte semanticVariantState, long semanticVariantPosition,
			List<ColumnRenderSource.SemanticMaterialSpan> semanticMaterialSpans,
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
	
}
