package com.seibel.distanthorizons.common.wrappers.worldGeneration.params;

import com.mojang.datafixers.DataFixer;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.level.IDhServerLevel;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;

import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.WorldData;

import net.minecraft.core.registries.Registries;

import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * Handles parameters that are relevant for the entire MC world.
 * 
 * @see ThreadWorldGenParams
 */
public final class GlobalWorldGenParams
{
	public final ChunkGenerator generator;
	public final IDhServerLevel dhServerLevel;
	public final ServerLevel mcServerLevel;
	public final Registry<Biome> biomes;
	public final RegistryAccess registry;
	public final long worldSeed;
	public final DataFixer dataFixer;
	
	public final StructureTemplateManager structures;
	public final RandomState randomState;
	
	public final WorldOptions worldOptions;
	
	public final BiomeManager biomeManager;
	public final ChunkScanAccess chunkScanner;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public GlobalWorldGenParams(IDhServerLevel dhServerLevel)
	{
		this.dhServerLevel = dhServerLevel;
		
		this.mcServerLevel = ((ServerLevelWrapper) dhServerLevel.getServerLevelWrapper()).getWrappedMcObject();
		MinecraftServer server = this.mcServerLevel.getServer();
		WorldData worldData = server.getWorldData();
		this.registry = server.registryAccess();
		
		this.worldOptions = worldData.worldGenOptions();
		this.biomes = this.registry.lookupOrThrow(Registries.BIOME);
		this.worldSeed = this.worldOptions.seed();
		
		this.biomeManager = new BiomeManager(this.mcServerLevel, BiomeManager.obfuscateSeed(this.worldSeed));
		this.chunkScanner = this.mcServerLevel.getChunkSource().chunkScanner();
		
		this.structures = server.getStructureManager();
		this.generator = this.mcServerLevel.getChunkSource().getGenerator();
		this.dataFixer = server.getFixerUpper();
		
		this.randomState = this.mcServerLevel.getChunkSource().randomState();
	}
	
	
	
}