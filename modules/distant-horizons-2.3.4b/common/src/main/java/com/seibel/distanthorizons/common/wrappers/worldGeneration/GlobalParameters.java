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

package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.mojang.datafixers.DataFixer;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.level.IDhServerLevel;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.WorldData;

public final class GlobalParameters
{
	public final ChunkGenerator generator;
	public final IDhServerLevel lodLevel;
	public final ServerLevel level;
	public final Registry<Biome> biomes;
	public final RegistryAccess registry;
	public final long worldSeed;
	public final DataFixer fixerUpper;
	
		public final StructureTemplateManager structures;
	public final RandomState randomState;
		
	public final WorldOptions worldOptions;
	
		public final BiomeManager biomeManager;
	public final ChunkScanAccess chunkScanner; // FIXME: Figure out if this is actually needed
		
	public GlobalParameters(IDhServerLevel lodLevel)
	{
		this.lodLevel = lodLevel;
		
		this.level = ((ServerLevelWrapper) lodLevel.getServerLevelWrapper()).getWrappedMcObject();
		MinecraftServer server = this.level.getServer();
		WorldData worldData = server.getWorldData();
		this.registry = server.registryAccess();

				this.worldOptions = worldData.worldGenOptions();
		this.biomes = this.registry.lookupOrThrow(Registries.BIOME);
		this.worldSeed = this.worldOptions.seed();
				
				this.biomeManager = new BiomeManager(this.level, BiomeManager.obfuscateSeed(this.worldSeed));
		this.chunkScanner = this.level.getChunkSource().chunkScanner();
				this.structures = server.getStructureManager();
		this.generator = this.level.getChunkSource().getGenerator();
		this.fixerUpper = server.getFixerUpper();
		this.randomState = this.level.getChunkSource().randomState();
	}
	
}