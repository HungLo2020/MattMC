/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.worldGeneration.step;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.gridList.ArrayGridList;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import com.seibel.distanthorizons.core.logging.DhLogger;

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif

import java.util.ArrayList;
import java.util.ConcurrentModificationException;


public final class StepFeatures extends AbstractWorldGenStep
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static final ChunkStatus STATUS = ChunkStatus.FEATURES;
	
	private final BatchGenerationEnvironment environment;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public StepFeatures(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	@Override
	public ChunkStatus getChunkStatus() { return STATUS; }
	
	@Override
	public void generateGroup(
			ThreadWorldGenParams tParams, DhLitWorldGenRegion worldGenRegion,
			ArrayGridList<ChunkWrapper> chunkWrappers)
	{
		ArrayList<ChunkWrapper> chunksToDo = this.getChunkWrappersToGenerate(chunkWrappers);
		for (ChunkWrapper chunkWrapper : chunksToDo)
		{
			ChunkAccess chunk = chunkWrapper.getChunk();
			
			
			try
			{
				#if MC_VER < MC_1_18_2
				worldGenRegion.setOverrideCenter(chunk.getPos());
				environment.globalParams.generator.applyBiomeDecoration(worldGenRegion, tParams.structFeatManager);
				#else
				if (worldGenRegion.hasChunk(chunkWrapper.getChunkPos().getX(), chunkWrapper.getChunkPos().getZ()))
				{
					this.environment.globalParams.generator.applyBiomeDecoration(worldGenRegion, chunk, tParams.structFeatManager.forWorldGenRegion(worldGenRegion));
				}
				else
				{
					LOGGER.warn("Unable to generate features for chunk at pos ["+chunkWrapper.getChunkPos()+"], world gen region doesn't contain the chunk.");
				}
				#endif
				
				Heightmap.primeHeightmaps(chunk, STATUS.heightmapsAfter());
			}
			catch (ConcurrentModificationException e) // ReportedException
			{
				// TODO
			}
			catch (Exception e)
			{
				LOGGER.warn("Unexpected issue when generating features for chunk at pos ["+chunkWrapper.getChunkPos()+"], error: ["+e.getMessage()+"].", e);
				// FIXME: Features concurrent modification issue. Something about cocobeans might just
				//  error out. For now just retry.
			}
		}
	}
	
}