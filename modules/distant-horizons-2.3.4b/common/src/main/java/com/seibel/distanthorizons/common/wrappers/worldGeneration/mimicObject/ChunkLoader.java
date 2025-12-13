/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import net.minecraft.world.level.chunk.Strategy;

import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.Registry;
#if MC_VER >= MC_1_19_4
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
#endif
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

#if MC_VER < MC_1_21_3
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
#else
#endif

import net.minecraft.world.level.levelgen.Heightmap;
#if MC_VER >= MC_1_18_2
import net.minecraft.world.level.levelgen.blending.BlendingData;
#if MC_VER < MC_1_19_2
import net.minecraft.world.level.levelgen.feature.StructureFeature;
#endif
import net.minecraft.world.ticks.LevelChunkTicks;
#endif
#if MC_VER >= MC_1_18_2
import net.minecraft.core.Holder;
#if MC_VER < MC_1_19_2
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
#endif
#endif

#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
import net.minecraft.world.level.material.Fluids;
#endif

#if MC_VER == MC_1_20_6
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
#elif MC_VER >= MC_1_21_1
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
#endif

import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;


public class ChunkLoader
{
	private static final AtomicBoolean ZERO_CHUNK_POS_ERROR_LOGGED_REF = new AtomicBoolean(false);
	
	
	#if MC_VER >= MC_1_21_8
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(BlockState.CODEC, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState());
	#elif MC_VER >= MC_1_19_2
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
	#elif MC_VER >= MC_1_18_2
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codec(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
	#endif
	private static final String TAG_UPGRADE_DATA = "UpgradeData";
	private static final String BLOCK_TICKS_TAG_18 = "block_ticks";
	private static final String FLUID_TICKS_TAG_18 = "fluid_ticks";
	private static final String BLOCK_TICKS_TAG_PRE18 = "TileTicks";
	private static final String FLUID_TICKS_TAG_PRE18 = "LiquidTicks";
	private static final ConfigBasedLogger LOGGER = BatchGenerationEnvironment.LOAD_LOGGER;
	
	private static boolean lightingSectionErrorLogged = false;
	
	private static final ConcurrentHashMap<String, Object> LOGGED_ERROR_MESSAGE_MAP = new ConcurrentHashMap<>();
	
	
	
	//============//
	// read chunk //
	//============//
	
	public static LevelChunk read(WorldGenLevel level, ChunkPos chunkPos, CompoundTag chunkData)
	{
		#if MC_VER < MC_1_18_2
		CompoundTag tagLevel = chunkData.getCompound("Level");
		#else
		CompoundTag tagLevel = chunkData;
		#endif
		
		int chunkX = tagGetInt(tagLevel,"xPos");
		int chunkZ = tagGetInt(tagLevel, "zPos");
		ChunkPos actualPos = new ChunkPos(chunkX, chunkZ);
		
		if (!Objects.equals(chunkPos, actualPos))
		{
			if (chunkX == 0 && chunkZ == 0)
			{
				if (!ZERO_CHUNK_POS_ERROR_LOGGED_REF.getAndSet(true))
				{
					// explicit chunkPos toString is necessary otherwise the JDK 17 compiler breaks
					LOGGER.warn("Chunk file at ["+chunkPos.toString()+"] doesn't have a chunk pos. \n" +
						"This might happen if the world was created using an external program. \n" +
						"DH will attempt to parse the chunk anyway and won't log this message again.\n" +
						"If issues arise please try optimizing your world to fix this issue. \n" +
						"World optimization can be done from the singleplayer world selection screen."+
						"");
				}
			}
			else
			{
				// everything is on one line to fix a JDK 17 compiler issue
				// if the issue is ever resolved, feel free to make this multi-line for readability
				LOGGER.error("Chunk file at ["+chunkPos.toString()+"] is in the wrong location. \nPlease try optimizing your world to fix this issue. \nWorld optimization can be done from the singleplayer world selection screen. \n(Expected pos: ["+chunkPos.toString()+"], actual ["+actualPos.toString()+"])");
				return null;
			}
		}
		
		#if MC_VER < MC_1_20_6
		ChunkStatus.ChunkType chunkType;
		#else
		ChunkType chunkType;
		#endif
		chunkType = readChunkType(tagLevel);
		
		#if MC_VER < MC_1_18_2
			if (chunkType != ChunkStatus.ChunkType.LEVELCHUNK)
				return null;
		#elif MC_VER < MC_1_21_6
			
			BlendingData blendingData = readBlendingData(tagLevel);
			#if MC_VER < MC_1_19_2
			if (chunkType == ChunkStatus.ChunkType.PROTOCHUNK && (blendingData == null || !blendingData.oldNoise()))
				return null;
			#else
			if (chunkType == #if MC_VER < MC_1_20_6 ChunkStatus.ChunkType.PROTOCHUNK #else ChunkType.PROTOCHUNK #endif && blendingData == null)
				return null;
			#endif
		#else

		// ignore blending data, there appears to be an issue with parsing it in 1.21.6
		BlendingData blendingData = null;
		
		if (chunkType == ChunkType.PROTOCHUNK)
		{
			return null;
		}
		#endif
		
		long inhabitedTime = tagGetLong(tagLevel, "InhabitedTime");
		
		//================== Read params for making the LevelChunk ==================
		
		UpgradeData upgradeData = UpgradeData.EMPTY;
		// commented out 2025-06-04 as a test to see if the upgrade data
		// is actually necessary for DH or if it can be ignored
		// (if it can't be ignored we'll need to handle null responses from tagGetCompoundTag())
		//
		//#if MC_VER < MC_1_17_1
		//upgradeData = tagLevel.contains(TAG_UPGRADE_DATA, 10)
		//		? new UpgradeData(tagGetCompoundTag(tagLevel, TAG_UPGRADE_DATA))
		//		: UpgradeData.EMPTY;
		//#elif MC_VER < MC_1_21_5
		//upgradeData = tagLevel.contains(TAG_UPGRADE_DATA, 10)
		//		? new UpgradeData(tagGetCompoundTag(tagLevel, TAG_UPGRADE_DATA), level)
		//		: UpgradeData.EMPTY;
		//#else
		//upgradeData = tagLevel.contains(TAG_UPGRADE_DATA)
		//		? new UpgradeData(tagGetCompoundTag(tagLevel, TAG_UPGRADE_DATA), level)
		//		: UpgradeData.EMPTY;
		//#endif
		
		
		boolean isLightOn = tagGetBoolean(tagLevel, "isLightOn");
		#if MC_VER < MC_1_18_2
		ChunkBiomeContainer chunkBiomeContainer = new ChunkBiomeContainer(
				level.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)#if MC_VER >= MC_1_17_1 , level #endif ,
				chunkPos, level.getLevel().getChunkSource().getGenerator().getBiomeSource(),
				tagLevel.contains("Biomes", 11) ? tagLevel.getIntArray("Biomes") : null);
		
		TickList<Block> blockTicks = tagLevel.contains(BLOCK_TICKS_TAG_PRE18, 9)
				? ChunkTickList.create(tagLevel.getList(BLOCK_TICKS_TAG_PRE18, 10), Registry.BLOCK::getKey, Registry.BLOCK::get)
				: new ProtoTickList<Block>(block -> (block == null || block.defaultBlockState().isAir()), chunkPos,
				tagLevel.getList("ToBeTicked", 9)#if MC_VER >= MC_1_17_1 , level #endif );
		
		TickList<Fluid> fluidTicks = tagLevel.contains(FLUID_TICKS_TAG_PRE18, 9)
				? ChunkTickList.create(tagLevel.getList(FLUID_TICKS_TAG_PRE18, 10), Registry.FLUID::getKey, Registry.FLUID::get)
				: new ProtoTickList<Fluid>(fluid -> (fluid == null || fluid == Fluids.EMPTY), chunkPos,
				tagLevel.getList("LiquidsToBeTicked", 9)#if MC_VER >= MC_1_17_1 , level #endif );
		#else
			#if MC_VER < MC_1_19_4
				LevelChunkTicks<Block> blockTicks = LevelChunkTicks.load(tagLevel.getList(BLOCK_TICKS_TAG_18, 10),
						string -> Registry.BLOCK.getOptional(ResourceLocation.tryParse(string)), chunkPos);
				LevelChunkTicks<Fluid> fluidTicks = LevelChunkTicks.load(tagLevel.getList(FLUID_TICKS_TAG_18, 10),
						string -> Registry.FLUID.getOptional(ResourceLocation.tryParse(string)), chunkPos);
			#elif MC_VER < MC_1_21_4
				LevelChunkTicks<Block> blockTicks = LevelChunkTicks.load(tagLevel.getList(BLOCK_TICKS_TAG_18, 10),
						(string -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(string))), chunkPos);
				LevelChunkTicks<Fluid> fluidTicks = LevelChunkTicks.load(tagLevel.getList(FLUID_TICKS_TAG_18, 10),
						string -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(string)), chunkPos);
			#else
				// do we need the ticks for what we're doing?
				LevelChunkTicks<Block> blockTicks = new LevelChunkTicks<>();
				LevelChunkTicks<Fluid> fluidTicks = new LevelChunkTicks<>();
			#endif
		#endif
		
		LevelChunkSection[] levelChunkSections = readSections(level, chunkPos, tagLevel);
		
		// ====================== Make the chunk =========================
		#if MC_VER < MC_1_18_2
		LevelChunk chunk = new LevelChunk((Level) level.getLevel(), chunkPos, chunkBiomeContainer, upgradeData, blockTicks,
				fluidTicks, inhabitedTime, levelChunkSections, null);
		#else
		LevelChunk chunk = new LevelChunk((Level) level, chunkPos, upgradeData, blockTicks,
				fluidTicks, inhabitedTime, levelChunkSections, null, blendingData);
		#endif
		// Set some states after object creation
		chunk.setLightCorrect(isLightOn);
		readHeightmaps(chunk, chunkData);
		//readPostPocessings(chunk, chunkData);
		return chunk;
	}
	private static LevelChunkSection[] readSections(LevelAccessor level, ChunkPos chunkPos, CompoundTag chunkData)
	{
		#if MC_VER >= MC_1_18_2
		#if MC_VER < MC_1_19_4
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
		#elif MC_VER < MC_1_21_3
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		#else
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		#endif
			#if MC_VER < MC_1_18_2
			Codec<PalettedContainer<Biome>> biomeCodec = PalettedContainer.codec(
					biomes, biomes.byNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getOrThrow(Biomes.PLAINS));
			#elif MC_VER < MC_1_19_2
			Codec<PalettedContainer<Holder<Biome>>> biomeCodec = PalettedContainer.codec(
				biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getHolderOrThrow(Biomes.PLAINS));
			#elif MC_VER < MC_1_21_3
			Codec<PalettedContainer<Holder<Biome>>> biomeCodec = PalettedContainer.codecRW(
				biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getHolderOrThrow(Biomes.PLAINS));
			#else
			Codec<PalettedContainer<Holder<Biome>>> biomeCodec = PalettedContainer.codecRW(
				biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getOrThrow(Biomes.PLAINS));
			#endif
		#endif
		
		int sectionYIndex = #if MC_VER < MC_1_17_1 16; #else level.getSectionsCount(); #endif
		LevelChunkSection[] chunkSections = new LevelChunkSection[sectionYIndex];
		
		ListTag tagSections = tagGetListTag(chunkData, "Sections", 10);
		if (tagSections == null || tagSections.isEmpty())
		{
			tagSections = tagGetListTag(chunkData, "sections", 10);
		}
		
		
		if (tagSections != null)
		{
			for (int j = 0; j < tagSections.size(); ++j)
			{
				CompoundTag tagSection = tagGetCompoundTag(tagSections, j);
				if (tagSection == null)
				{
					continue;
				}
				
				final int sectionYPos = tagGetByte(tagSection, "Y");

				#if MC_VER < MC_1_18_2
				if (tagSection.contains("Palette", 9) && tagSection.contains("BlockStates", 12))
				{
					LevelChunkSection levelChunkSection = new LevelChunkSection(sectionYPos << 4);
					levelChunkSection.getStates().read(tagSection.getList("Palette", 10),
							tagSection.getLongArray("BlockStates"));
					levelChunkSection.recalcBlockCounts();
					if (!levelChunkSection.isEmpty())
						chunkSections[#if MC_VER < MC_1_17_1 sectionYPos #else level.getSectionIndexFromSectionY(sectionYPos) #endif ]
								= levelChunkSection;
				}
				#else
				int sectionId = level.getSectionIndexFromSectionY(sectionYPos);
				if (sectionId >= 0 && sectionId < chunkSections.length)
				{
					PalettedContainer<BlockState> blockStateContainer;
					#if MC_VER < MC_1_18_2
					PalettedContainer<Biome> biomeContainer;
					#else
					PalettedContainer<Holder<Biome>> biomeContainer;
					#endif
					
					
					boolean containsBlockStates;
					#if MC_VER < MC_1_21_5
					containsBlockStates = tagSection.contains("block_states", 10);
					#else
						containsBlockStates = tagSection.contains("block_states");
					#endif
					
					if (containsBlockStates)
					{
						#if MC_VER < MC_1_20_6 
						blockStateContainer = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, tagGetCompoundTag(tagSection, "block_states"))
							.promotePartial(string -> logBlockDeserializationWarning(chunkPos, sectionYPos, string))
							.getOrThrow(false, (message) -> logParsingWarningOnce(message));
						#else
						blockStateContainer = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, tagGetCompoundTag(tagSection, "block_states"))
								.promotePartial(string -> logBlockDeserializationWarning(chunkPos, sectionYPos, string))
								.getOrThrow((message) -> logErrorAndReturnException(message));
						#endif
					}
					else
					{
						blockStateContainer = new PalettedContainer<BlockState>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
					}
				
				
					#if MC_VER < MC_1_18_2
					biomeContainer = tagSection.contains("biomes", 10)
							? biomeCodec.parse(NbtOps.INSTANCE, tagSection.getCompound("biomes")).promotePartial(string -> logErrors(chunkPos, sectionYPos, string)).getOrThrow(false, (message) -> logWarningOnce(message))
							: new PalettedContainer<Biome>(biomes, biomes.getOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES);
					#else
					
					
					boolean containsBiomes;
					#if MC_VER < MC_1_21_5
					containsBiomes = tagSection.contains("biomes", 10);
					#else
						containsBiomes = tagSection.contains("biomes");
					#endif
					
					if (containsBiomes)
					{
						#if MC_VER < MC_1_20_6 
						biomeContainer = biomeCodec.parse(NbtOps.INSTANCE, tagGetCompoundTag(tagSection, "biomes"))
							.promotePartial(string -> logBiomeDeserializationWarning(chunkPos, sectionYIndex, (String) string))
							.getOrThrow(false, (message) -> logParsingWarningOnce(message));
						#else
						biomeContainer = biomeCodec.parse(NbtOps.INSTANCE, tagGetCompoundTag(tagSection, "biomes"))
								.promotePartial(string -> logBiomeDeserializationWarning(chunkPos, sectionYIndex, (String) string))
								.getOrThrow((message) -> logErrorAndReturnException(message));
						#endif
					}
					else
					{
						biomeContainer = new PalettedContainer<Holder<Biome>>(biomes.asHolderIdMap(), 
							#if MC_VER < MC_1_21_3
							biomes.getHolderOrThrow(Biomes.PLAINS), 
							#else
								biomes.getOrThrow(Biomes.PLAINS),
							#endif
								PalettedContainer.Strategy.SECTION_BIOMES);
					}
				
					#endif
					
					#if MC_VER < MC_1_20_1
					chunkSections[sectionId] = new LevelChunkSection(sectionYPos, blockStateContainer, biomeContainer);
					#else
					chunkSections[sectionId] = new LevelChunkSection(blockStateContainer, biomeContainer);
					#endif
				}
				#endif
				
			}	
		}
		return chunkSections;
	}
	private static 
		#if MC_VER < MC_1_20_6 ChunkStatus.ChunkType
		#elif MC_VER < MC_1_21_1 ChunkType
		#else ChunkType #endif 
	readChunkType(CompoundTag tagLevel)
	{
		String statusString = tagGetString(tagLevel,"Status");
		if (statusString != null)
		{
			ChunkStatus chunkStatus = ChunkStatus.byName(statusString);
			if (chunkStatus != null)
			{
				return chunkStatus.getChunkType();
			}
		}
		
		#if MC_VER <= MC_1_20_4
		return ChunkStatus.ChunkType.PROTOCHUNK;
		#else
		return ChunkType.PROTOCHUNK;
		#endif
	}
	private static void readHeightmaps(LevelChunk chunk, CompoundTag chunkData)
	{
		CompoundTag tagHeightmaps = tagGetCompoundTag(chunkData, "Heightmaps");
		if (tagHeightmaps != null)
		{
			for (Heightmap.Types type : ChunkStatus.FULL.heightmapsAfter())
			{
				String heightmap = type.getSerializationKey();
				#if MC_VER < MC_1_21_5
				if (tagHeightmaps.contains(heightmap, 12))
				{
					chunk.setHeightmap(type, tagHeightmaps.getLongArray(heightmap));
				}
				#else
				if (tagHeightmaps.contains(heightmap))
				{
					Optional<long[]> optionalHeightmap = tagHeightmaps.getLongArray(heightmap);
					if (optionalHeightmap.isPresent())
					{
						chunk.setHeightmap(type, optionalHeightmap.get());
					}
				}
				#endif
			}
			
			Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
		}
	}
	// commented out as a test as of 2025-06-04 to see if this is actually necessary for DH
	// DH probably doesn't need any chunk post-processing data
	//private static void readPostPocessings(LevelChunk chunk, CompoundTag chunkData)
	//{
	//	ListTag tagPostProcessings = tagGetListTag(chunkData,"PostProcessing", 9);
	//	if (tagPostProcessings != null)
	//	{
	//		for (int i = 0; i < tagPostProcessings.size(); ++i)
	//		{
	//			ListTag listTag3 = tagGetListTag(tagPostProcessings, i);
	//			for (int j = 0; j < listTag3.size(); ++j)
	//			{
	//			#if MC_VER < MC_1_21_3
	//				chunk.addPackedPostProcess(listTag3.getShort(j), i);
	//			#else
	//			chunk.addPackedPostProcess(ShortList.of(tagGetShort(listTag3, j)), i);
	//			#endif
	//			}
	//		}
	//	}
	//}
	#if MC_VER >= MC_1_18_2
	private static BlendingData readBlendingData(CompoundTag chunkData)
	{
		BlendingData blendingData = null;
		
		
		boolean containsBlendingData;
		#if MC_VER < MC_1_21_5
		containsBlendingData = chunkData.contains("blending_data", 10);
		#else
		containsBlendingData = chunkData.contains("blending_data");
		#endif
		
		if (containsBlendingData)
		{
			@SuppressWarnings({"unchecked", "rawtypes"})
			Dynamic<CompoundTag> blendingDataTag = new Dynamic(NbtOps.INSTANCE, chunkData.getCompound("blending_data"));
			
			try
			{
				#if MC_VER < MC_1_21_3
				blendingData = BlendingData.CODEC.parse(blendingDataTag).resultOrPartial((message) -> logParsingWarningOnce(message)).orElse(null);
				#else
				// blending data appears to have changed as of 1.21.6 causing a class cast exception here due to it being wrapped in a Java.Optional
				blendingData = BlendingData.unpack(BlendingData.Packed.CODEC.parse(blendingDataTag).resultOrPartial((message) -> logParsingWarningOnce(message)).orElse(null));
				#endif
			}
			catch (Exception e)
			{
				String message = e.getMessage();
				if (message == null || message.trim().isEmpty())
				{
					message = "Failed to parse blending data";
				}
				
				logParsingWarningOnce(message, e);
			}
		}
		return blendingData;
	}
	#endif
	
	
	
	//=====================//
	// read chunk lighting //
	//=====================//
	
	/**
	 * https://minecraft.wiki/w/Chunk_format
	 */
	public static CombinedChunkLightStorage readLight(ChunkAccess chunk, CompoundTag chunkData)
	{
		#if MC_VER <= MC_1_17_1
		// MC 1.16 and 1.17 doesn't have the necessary NBT info
		return null;
		#else
		
		CombinedChunkLightStorage combinedStorage = new CombinedChunkLightStorage(ChunkWrapper.getInclusiveMinBuildHeight(chunk), ChunkWrapper.getExclusiveMaxBuildHeight(chunk));
		ChunkLightStorage blockLightStorage = combinedStorage.blockLightStorage;
		ChunkLightStorage skyLightStorage = combinedStorage.skyLightStorage;
		
		boolean foundSkyLight = false;
		
		
		
		//===================//
		// get NBT tags info //
		//===================//
		
		Tag chunkSectionTags = chunkData.get("sections");
		if (chunkSectionTags == null)
		{
			if (!lightingSectionErrorLogged)
			{
				lightingSectionErrorLogged = true;
				LOGGER.error("No sections found for chunk at pos ["+chunk.getPos()+"] chunk data may be out of date.");
			}
			return null;
		}
		else if (!(chunkSectionTags instanceof ListTag))
		{
			if (!lightingSectionErrorLogged)
			{
				lightingSectionErrorLogged = true;
				LOGGER.error("Chunk section tag list have unexpected type ["+chunkSectionTags.getClass().getName()+"], expected ["+ListTag.class.getName()+"].");
			}
			return null;
		}
		ListTag chunkSectionListTag = (ListTag) chunkSectionTags;
		
		
		
		//===================//
		// get lighting info //
		//===================//
		
		for (int sectionIndex = 0; sectionIndex < chunkSectionListTag.size(); sectionIndex++)
		{
			Tag chunkSectionTag = chunkSectionListTag.get(sectionIndex);
			if (!(chunkSectionTag instanceof CompoundTag))
			{
				if (!lightingSectionErrorLogged)
				{
					lightingSectionErrorLogged = true;
					LOGGER.error("Chunk section tag has an unexpected type ["+chunkSectionTag.getClass().getName()+"], expected ["+CompoundTag.class.getName()+"].");
				}
				return null;
			}
			CompoundTag chunkSectionCompoundTag = (CompoundTag) chunkSectionTag;
			
			
			// if null all lights = 0
			byte[] blockLightNibbleArray = tagGetByteArray(chunkSectionCompoundTag, "BlockLight");
			byte[] skyLightNibbleArray = tagGetByteArray(chunkSectionCompoundTag, "SkyLight");
			
			if (blockLightNibbleArray != null 
				&& skyLightNibbleArray != null)
			{
				// if any sky light was found then all lights above will be max brightness
				if (skyLightNibbleArray.length != 0)
				{
					foundSkyLight = true;
				}
				
				for (int relX = 0; relX < LodUtil.CHUNK_WIDTH; relX++)
				{
					for (int relZ = 0; relZ < LodUtil.CHUNK_WIDTH; relZ++)
					{
						// chunk sections are also 16 blocks tall
						for (int relY = 0; relY < LodUtil.CHUNK_WIDTH; relY++)
						{
							int blockPosIndex = relY*16*16 + relZ*16 + relX;
							byte blockLight = (blockLightNibbleArray.length == 0) ? 0 : getNibbleAtIndex(blockLightNibbleArray, blockPosIndex);
							byte skyLight = (skyLightNibbleArray.length == 0) ? 0 : getNibbleAtIndex(skyLightNibbleArray, blockPosIndex);
							if (skyLightNibbleArray.length == 0 && foundSkyLight)
							{
								skyLight = LodUtil.MAX_MC_LIGHT;
							}
							
							int y = relY + (sectionIndex * LodUtil.CHUNK_WIDTH) + ChunkWrapper.getInclusiveMinBuildHeight(chunk);
							blockLightStorage.set(relX, y, relZ, blockLight);
							skyLightStorage.set(relX, y, relZ, skyLight);
						}
					}
				}
			}
		}
		
		return combinedStorage;
		#endif
	}
	/** source: https://minecraft.wiki/w/Chunk_format#Block_Format */
	private static byte getNibbleAtIndex(byte[] arr, int index)
	{
		if (index % 2 == 0)
		{
			return (byte)(arr[index/2] & 0x0F);
		}
		else
		{
			return (byte)((arr[index/2]>>4) & 0x0F);
		}
	}
	
	
	
	//=========//
	// logging //
	//=========//
	
	private static void logBlockDeserializationWarning(ChunkPos chunkPos, int sectionYIndex, String message)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) ->
		{
			LOGGER.warn("Unable to deserialize blocks for chunk section [" + chunkPos.x + ", " + sectionYIndex + ", " + chunkPos.z + "], error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.");
			
			return newMessage;
		});
	}
	private static void logBiomeDeserializationWarning(ChunkPos chunkPos, int sectionYIndex, String message)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) -> 
		{
			LOGGER.warn("Unable to deserialize biomes for chunk section [" + chunkPos.x + ", " + sectionYIndex + ", " + chunkPos.z + "], error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.");
			
			return newMessage;
		});
	}
	
	private static void logParsingWarningOnce(String message) { logParsingWarningOnce(message, null); }
	private static void logParsingWarningOnce(String message, Exception e)
	{
		if (message == null)
		{
			return;
		}
		
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) ->
		{
			LOGGER.warn("Parsing error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.",
					e);
			
			return newMessage;
		});
	}
	
	private static RuntimeException logErrorAndReturnException(String message)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) ->
		{
			LOGGER.warn("Parsing error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.");
			
			return newMessage;
		});
		
		// Currently we want to ignore these errors, if returning null is a problem, we can change this later
		return null; //new RuntimeException(message);
	}
	
	
	
	//====================//
	// tag helper methods //
	//====================//
	
	// TODO move into separate file (this file is getting long)
	// these tag helpers are to simplify tag accessing between MC versions
	
	/** defaults to "false" if the tag isn't present */
	private static boolean tagGetBoolean(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getBoolean(key);
		#else
		return tag.getBoolean(key).orElse(false);
		#endif
	}
	
	/** defaults to "0" if the tag isn't present */
	private static byte tagGetByte(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getByte(key);
		#else
		return tag.getByte(key).orElse((byte)0);
		#endif
	}
	
	/** defaults to "0" if the tag isn't present */
	private static short tagGetShort(ListTag tag, int index)
	{
		#if MC_VER < MC_1_21_5
		return tag.getShort(index);
		#else
		return tag.getShort(index).orElse((short)0);
		#endif
	}
	
	/** defaults to "0" if the tag isn't present */
	private static int tagGetInt(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getInt(key);
		#else
		return tag.getInt(key).orElse(0);
		#endif
	}
	
	/** defaults to "0" if the tag isn't present */
	private static long tagGetLong(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getInt(key);
		#else
		return tag.getLong(key).orElse(0L);
		#endif
	}
	
	
	
	/** defaults to null if the tag isn't present */
	@Nullable
	private static String tagGetString(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getString(key);
		#else
		return tag.getString(key).orElse(null);
		#endif
	}
	
	/** defaults to null if the tag isn't present */
	@Nullable
	private static byte[] tagGetByteArray(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getByteArray(key);
		#else
		return tag.getByteArray(key).orElse(null);
		#endif
	}
	
	
	
	/** defaults to null if the tag isn't present */
	@Nullable
	private static CompoundTag tagGetCompoundTag(CompoundTag tag, String key)
	{
		#if MC_VER < MC_1_21_5
		return tag.getCompound(key);
		#else
		return tag.getCompound(key).orElse(null);
		#endif
	}
	/** defaults to null if the tag isn't present */
	@Nullable
	private static CompoundTag tagGetCompoundTag(ListTag tag, int index)
	{
		#if MC_VER < MC_1_21_5
		return tag.getCompound(index);
		#else
		return tag.getCompound(index).orElse(null);
		#endif
	}
	
	/** 
	 * defaults to null if the tag isn't present
	 * @param elementType unused after MC 1.21.5
	 */
	@Nullable
	private static ListTag tagGetListTag(CompoundTag tag, String key, int elementType)
	{
		#if MC_VER < MC_1_21_5
		return tag.getList(key, elementType);
		#else
		return tag.getList(key).orElse(null);
		#endif
	}
	
	/** defaults to null if the tag isn't present */
	@Nullable
	private static ListTag tagGetListTag(ListTag tag, int index)
	{
		#if MC_VER < MC_1_21_5
		return tag.getList(index);
		#else
		return tag.getList(index).orElse(null);
		#endif
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class CombinedChunkLightStorage
	{
		public ChunkLightStorage blockLightStorage;
		public ChunkLightStorage skyLightStorage;
		
		public CombinedChunkLightStorage(int minY, int maxY)
		{
			this.blockLightStorage = ChunkLightStorage.createBlockLightStorage(minY, maxY);
			this.skyLightStorage = ChunkLightStorage.createSkyLightStorage(minY, maxY);
		}
	}
	
}

