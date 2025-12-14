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

package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

#if MC_VER >= MC_1_18_2
import net.minecraft.core.Holder;
#endif

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TintWithoutLevelOverrider implements BlockAndTintGetter
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	#if MC_VER < MC_1_18_2
	public static final ConcurrentMap<String, Biome> BIOME_BY_RESOURCE_STRING = new ConcurrentHashMap<>();
	#else
	public static final ConcurrentMap<String, Holder<Biome>> BIOME_BY_RESOURCE_STRING = new ConcurrentHashMap<>();
    #endif
	
	
	@NotNull
	private final BiomeWrapper biomeWrapper;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public TintWithoutLevelOverrider(@NotNull BiomeWrapper biomeWrapper, IClientLevelWrapper clientLevelWrapper)
	{ this.biomeWrapper = biomeWrapper; }
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public int getBlockTint(@NotNull BlockPos blockPos, @NotNull ColorResolver colorResolver)
	{
		String biomeString = this.biomeWrapper.getSerialString();
		if (biomeString == null 
			|| biomeString.isEmpty() 
			|| biomeString.equals(BiomeWrapper.EMPTY_BIOME_STRING))
		{
			// default to "plains" for empty/invalid biomes
			biomeString = "minecraft:plains";
		}
		
		
		return colorResolver.getColor(unwrap(getClientBiome(biomeString)), blockPos.getX(), blockPos.getZ());
	}
	private static Biome unwrap(#if MC_VER >= MC_1_18_2 Holder<Biome> #else Biome #endif biome)
	{
		#if MC_VER >= MC_1_18_2
		return biome.value();
		#else
		return biome;
		#endif
	}
	
	/**
	 * <p>Previously, this class might have immediately unwrapped the Holder like this:</p>
	 * <pre>{@code
	 * // Inside constructor (OLD WAY - PROBLEMATIC):
	 * Holder<Biome> biomeHolder = getTheHolderFromSomewhere();
	 * this.biome = biomeHolder.value(); // <-- PROBLEM HERE
	 * }</pre>
	 *
	 * <p>This approach is problematic because the {@link net.minecraft.core.Holder} system,
	 * particularly {@code Holder.Reference}, is designed for <strong>late binding</strong>. Here's why storing
	 * the Holder itself is now necessary:</p>
	 * <ol>
	 *   <li>A {@code Holder.Reference<Biome>} might be created initially just with a
	 *       {@link net.minecraft.resources.ResourceKey} (like {@code minecraft:plains}), but its actual
	 *       {@link net.minecraft.core.Holder#value() value()} (the {@code Biome} object itself) might be {@code null}
	 *       at construction time.</li>
	 *   <li>Later, during game loading, registry population, or potentially due to modifications by other mods
	 *       (e.g., Polytone), the system calls internal binding methods (like {@code bindValue(Biome)})
	 *       on the {@code Holder} instance. This sets or <strong>updates</strong> the internal reference to the
	 *       actual {@code Biome} object.</li>
	 *   <li>Crucially, the binding process might assign a completely <strong>new</strong> {@code Biome} object
	 *       instance to the {@code Holder} reference, replacing any previous one.</li>
	 * </ol>
	 *
	 * <p>If we unwrapped the {@code Holder} using {@code .value()} within the constructor (the old way),
	 * our class's internal {@code biome} field would permanently store a reference to whatever {@code Biome}
	 * object the {@code Holder} pointed to *at that exact moment*. It would have no link back to the
	 * {@code Holder} and would be unaware if the {@code Holder} was later updated to point to a different
	 * (or the initially missing) {@code Biome} object. This would lead to using stale or even {@code null} data.</p>
	 *
	 * <p>By storing the {@code Holder<Biome>} itself, this class can call {@link net.minecraft.core.Holder#value()}
	 * whenever the biome information is needed, ensuring it always retrieves the most current {@code Biome}
	 * instance associated with the holder at that time.</p>
	 */
	private static #if MC_VER < MC_1_18_2 Biome #else Holder<Biome> #endif getClientBiome(String biomeResourceString)
	{
		// cache the client biomes so we don't have to re-parse the resource location every time
		return BIOME_BY_RESOURCE_STRING.compute(biomeResourceString, 
			(resourceString, existingBiome) -> 
			{ 
				if (existingBiome != null)
				{
					return existingBiome;
				}
				
				ClientLevel clientLevel = Minecraft.getInstance().level;
				if (clientLevel == null)
				{
					// shouldn't happen, but just in case
					throw new IllegalStateException("Attempted to get client biome when no client level was loaded.");
				}
				
				BiomeWrapper.BiomeDeserializeResult result;
				try
				{
					result = BiomeWrapper.deserializeBiome(resourceString, clientLevel.registryAccess());
				}
				catch (Exception e)
				{
					LOGGER.warn("Unable to deserialize client biome ["+resourceString+"], using fallback...");
					
					try
					{
						result = BiomeWrapper.deserializeBiome("minecraft:plains", clientLevel.registryAccess());
					}
					catch (IOException ex)
					{
						// should never happen, if it does this log will explode, but just in case
						LOGGER.error("Unable to deserialize fallback client biome [minecraft:plains], returning NULL.");
						return null;
					}
				}
				
				if (result.success)
				{
					existingBiome = result.biome;
				}
				
				return existingBiome;
			});
	}
	
	
	
	//================//
	// unused methods //
	//================//
	
	@Override
	public float getShade(@NotNull Direction direction, boolean shade)
	{
		throw new UnsupportedOperationException("ERROR: getShade() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	@Override
	public @NotNull LevelLightEngine getLightEngine()
	{
		throw new UnsupportedOperationException("ERROR: getLightEngine() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	@Nullable
	@Override
	public BlockEntity getBlockEntity(@NotNull BlockPos pos)
	{
		throw new UnsupportedOperationException("ERROR: getBlockEntity() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	
	@Override
	public @NotNull BlockState getBlockState(@NotNull BlockPos pos)
	{
		throw new UnsupportedOperationException("ERROR: getBlockState() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	@Override
	public @NotNull FluidState getFluidState(@NotNull BlockPos pos)
	{
		throw new UnsupportedOperationException("ERROR: getFluidState() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	
	
	
	//==============//
	// post MC 1.17 //
	//==============//
	
	#if MC_VER >= MC_1_17_1
	
	@Override
	public int getHeight()
	{ throw new UnsupportedOperationException("ERROR: getHeight() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMinBuildHeight() 
	{ throw new UnsupportedOperationException("ERROR: getMinBuildHeight() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	#else
	@Override
	public int getMinY()
	{ throw new UnsupportedOperationException("ERROR: getMinY() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	#endif
	
	#endif
	
}
