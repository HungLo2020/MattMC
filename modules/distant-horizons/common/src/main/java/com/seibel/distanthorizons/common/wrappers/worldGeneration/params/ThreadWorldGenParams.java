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


package com.seibel.distanthorizons.common.wrappers.worldGeneration.params;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.WorldGenStructFeatManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.StructureCheck;

public final class ThreadWorldGenParams
{
	private static final ThreadLocal<ThreadWorldGenParams> LOCAL_PARAM_REF = new ThreadLocal<>();
	
	
	final ServerLevel level;
	public WorldGenStructFeatManager structFeatManager = null;
	
	public StructureCheck structCheck;
	
	boolean isValid = true;
	
	// used for some older MC versions
	private static GlobalWorldGenParams previousGlobalWorldGenParams = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public static ThreadWorldGenParams getOrMake(GlobalWorldGenParams globalParams)
	{
		ThreadWorldGenParams threadParam = LOCAL_PARAM_REF.get();
		if (threadParam != null
			&& threadParam.isValid 
			&& threadParam.level == globalParams.mcServerLevel)
		{
			return threadParam;
		}
		
		threadParam = new ThreadWorldGenParams(globalParams);
		LOCAL_PARAM_REF.set(threadParam);
		return threadParam;
	}
	
	private ThreadWorldGenParams(GlobalWorldGenParams param)
	{
		previousGlobalWorldGenParams = param;
		
		this.level = param.mcServerLevel;
		
		this.structCheck = new StructureCheck(param.chunkScanner, param.registry, param.structures,
				param.mcServerLevel.dimension(), param.generator, param.randomState, this.level, param.generator.getBiomeSource(), param.worldSeed,
				param.dataFixer);
	}
	
	
	
	//==========//
	// builders //
	//==========//
	
	public void makeStructFeatManager(WorldGenLevel genLevel, GlobalWorldGenParams param)
	{
		this.structFeatManager = new WorldGenStructFeatManager(param.worldOptions, genLevel, this.structCheck);
	}
	
	public void recreateStructureCheck() { /* do nothing */ }	
	
	
	
}