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

package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructureCheck;

import net.minecraft.world.level.levelgen.structure.StructureStart;


import net.minecraft.world.level.chunk.status.ChunkStatus;



public class WorldGenStructFeatManager extends < MC_1_19_2 StructureFeatureManager
{
	final WorldGenLevel genLevel;
	
	WorldOptions worldOptions;
	
	StructureCheck structureCheck;
	
	public WorldGenStructFeatManager(
			WorldOptions worldOptions,
			WorldGenLevel genLevel, StructureCheck structureCheck)
	{
		
		super(genLevel, worldOptions, structureCheck);
		this.genLevel = genLevel;
		this.worldOptions = worldOptions;
	}
	
	@Override
	public WorldGenStructFeatManager forWorldGenRegion(WorldGenRegion worldGenRegion)
	{
		if (worldGenRegion == genLevel)
			return this;
		return new WorldGenStructFeatManager(worldOptions, worldGenRegion, structureCheck);
	}
	
	private ChunkAccess _getChunk(int x, int z, ChunkStatus status)
	{
		if (genLevel == null) return null;
		return genLevel.getChunk(x, z, status, false);
	}
	
	@Override
	public boolean hasAnyStructureAt(BlockPos blockPos)
	{
		SectionPos sectionPos = SectionPos.of(blockPos);
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return false;
		return chunk.hasAnyStructureReferences();
	}
	
	@Override
	public List<StructureStart> startsForStructure(ChunkPos sectionPos, Predicate<Structure> predicate)
	{
		ChunkAccess chunk = _getChunk(sectionPos.x, sectionPos.z, ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return List.of();
		
		// Copied from StructureFeatureManager::startsForFeature(...)
		Map<Structure, LongSet> map = chunk.getAllReferences();
		
		ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
		Iterator<Map.Entry<Structure, LongSet>> var5 = map.entrySet().iterator();
		
		while (var5.hasNext())
		{
			Map.Entry<Structure, LongSet> entry = var5.next();
			Structure configuredStructureFeature = entry.getKey();
			if (predicate.test(configuredStructureFeature))
			{
				LongSet var10002 = (LongSet) entry.getValue();
				Objects.requireNonNull(builder);
				this.fillStartsForStructure(configuredStructureFeature, var10002, builder::add);
			}
		}
		
		return builder.build();
	}
	
	@Override
	public List<StructureStart> startsForStructure(SectionPos sectionPos, Structure structure)
	{
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return (List<StructureStart>) Stream.empty();
		
		// Copied from StructureFeatureManager::startsForFeature(...)
		LongSet longSet = chunk.getReferencesForStructure(structure);
		ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
		Objects.requireNonNull(builder);
		this.fillStartsForStructure(structure, longSet, builder::add);
		return builder.build();
	}
	
	@Override
	public Map<Structure, LongSet> getAllStructuresAt(BlockPos blockPos)
	{
		SectionPos sectionPos = SectionPos.of(blockPos);
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return (Map<Structure, LongSet>) Stream.empty();
		return chunk.getAllReferences();
	}
}
