package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.Holder;

import net.minecraft.resources.ResourceLocation;

public class BlockStateWrapper implements IBlockStateWrapper
{
	/** example "minecraft:water" */
	public static final String RESOURCE_LOCATION_SEPARATOR = ":";
	/** example "minecraft:water_STATE_{level:0}" */
	public static final String STATE_STRING_SEPARATOR = "_STATE_";
	
	
	// must be defined before AIR, otherwise a null pointer will be thrown
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
    public static final ConcurrentHashMap<BlockState, BlockStateWrapper> WRAPPER_BY_BLOCK_STATE = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, BlockStateWrapper> WRAPPER_BY_RESOURCE_LOCATION = new ConcurrentHashMap<>();
	
	public static final String AIR_STRING = "AIR";
	public static final BlockStateWrapper AIR = new BlockStateWrapper(null, null);
	
	public static final String DIRT_RESOURCE_LOCATION_STRING = "minecraft:dirt";
	public static final String WATER_RESOURCE_LOCATION_STRING = "minecraft:water";
	
	public static HashSet<IBlockStateWrapper> rendererIgnoredBlocks = null;
	public static HashSet<IBlockStateWrapper> rendererIgnoredCaveBlocks = null;
	
	/** keep track of broken blocks so we don't log every time */
	private static final HashSet<ResourceLocation> BROKEN_RESOURCE_LOCATIONS = new HashSet<>();
	
	
	
	// properties //
	
	@Nullable
	public final BlockState blockState;
	/** technically final, but since it requires a method call to generate it can't be marked as such */
	private String serialString;
	private final int hashCode;
	/** Should be between {@link LodUtil#BLOCK_FULLY_OPAQUE} and {@link LodUtil#BLOCK_FULLY_OPAQUE} */
	private final int opacity;
	/** used by the Iris shader mod to determine how each LOD should be rendered */
	private byte blockMaterialId = 0;
	
	private final boolean isBeaconBlock; 
	private final boolean isBeaconBaseBlock;
	private final boolean allowsBeaconBeamPassage;
	/** null if this block can't tint beacons */
	private final Color beaconTintColor; 
	private final Color mapColor;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static BlockStateWrapper fromBlockState(BlockState blockState, ILevelWrapper levelWrapper)
	{
		if (blockState == null || blockState.isAir())
		{
			return AIR;
		}
		
		
		if (WRAPPER_BY_BLOCK_STATE.containsKey(blockState))
		{
			return WRAPPER_BY_BLOCK_STATE.get(blockState);
		}
		else
		{
			BlockStateWrapper newWrapper = new BlockStateWrapper(blockState, levelWrapper);
			WRAPPER_BY_BLOCK_STATE.put(blockState, newWrapper);
			return newWrapper;
		}
	}
	
	/** 
	 * Can be faster than {@link BlockStateWrapper#fromBlockState(BlockState, ILevelWrapper)} 
	 * in cases where the same block state is expected to be referenced multiple times.
	 */
	public static BlockStateWrapper fromBlockState(BlockState blockState, ILevelWrapper levelWrapper, IBlockStateWrapper guess)
	{
		BlockState guessBlockState = (guess == null || guess.isAir()) ? null : (BlockState) guess.getWrappedMcObject();
		BlockState inputBlockState = (blockState == null || blockState.isAir()) ? null : blockState;
		
		if (guess instanceof BlockStateWrapper
			&& guessBlockState == inputBlockState)
		{
			return (BlockStateWrapper) guess;
		}
		else
		{
			return fromBlockState(blockState, levelWrapper);
		}
	}
	
	private BlockStateWrapper(BlockState blockState, ILevelWrapper levelWrapper)
	{
		this.blockState = blockState;
		this.serialString = this.serialize(levelWrapper);
		this.hashCode = Objects.hash(this.serialString);
		this.blockMaterialId = this.calculateEDhApiBlockMaterialId().index;
		this.opacity = this.calculateOpacity();
		
		String lowercaseSerial = this.serialString.toLowerCase();
		
		
		// beacon blocks
		boolean isBeaconBaseBlock = false;
		for (int i = 0; i < LodUtil.BEACON_BASE_BLOCK_NAME_LIST.size(); i++)
		{
			String baseBlockName = LodUtil.BEACON_BASE_BLOCK_NAME_LIST.get(i);
			if (lowercaseSerial.contains(baseBlockName))
			{
				isBeaconBaseBlock = true;
				break;
			}
		}
		this.isBeaconBaseBlock = isBeaconBaseBlock;
		this.isBeaconBlock = lowercaseSerial.contains("minecraft:beacon");
		
		// beacon tint color
		Color beaconTintColor = null;
		if (this.blockState != null
			// beacon blocks also show up here, but since they block the beacon beam we don't want their color		
			&& !this.isBeaconBlock)
		{
			Block block = this.blockState.getBlock();
			if (block instanceof BeaconBeamBlock)
			{
				int colorInt;
				colorInt = ((BeaconBeamBlock) block).getColor().getMapColor().col;
				
				beaconTintColor = ColorUtil.toColorObjRGB(colorInt);
			}
		}
		this.beaconTintColor = beaconTintColor;
		
		
		// allow/deny beacon beam passage 
		boolean allowsBeaconBeamPassage;
		if (this.blockState != null)
		{
			// get block properties (defaults to the values used by air)
			boolean canOcclude = this.getCanOcclude();
			boolean propagatesSkyLightDown = this.getPropagatesSkyLightDown();
			
			if (lowercaseSerial.contains("minecraft:bedrock"))
			{
				// bedrock is a special case fully opaque block that does allow beacons through
				allowsBeaconBeamPassage = true;
			}
			else if (propagatesSkyLightDown || !canOcclude)
			{
				// stairs, cake, fences, etc.
				allowsBeaconBeamPassage = true;
			}
			else
			{
				// non-opaque blocks (glass, mob spawners, etc.)
				// all allow beacons through
				allowsBeaconBeamPassage = (this.opacity != LodUtil.BLOCK_FULLY_OPAQUE);
			}
		}
		else
		{
			// air allows beacons through
			allowsBeaconBeamPassage = true;
		}
		this.allowsBeaconBeamPassage = allowsBeaconBeamPassage;
		
		
		int mcColor = 0;
		if (this.blockState != null)
		{
			mcColor = this.blockState.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
			this.mapColor = ColorUtil.toColorObjRGB(mcColor);
		}
		else
		{
			this.mapColor = new Color(0,0,0,0);
		}
		
		//LOGGER.trace("Created BlockStateWrapper ["+this.serialString+"] for ["+blockState+"] with material ID ["+this.EDhApiBlockMaterialId+"]");
	}
	
	
	
	//====================//
	// LodBuilder methods //
	//====================//
	
	/** 
	 * Requires a {@link ILevelWrapper} since {@link BlockStateWrapper#deserialize(String,ILevelWrapper)} also requires one. 
	 * This way the method won't accidentally be called before the deserialization can be completed.
	 */
	public static HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (rendererIgnoredBlocks != null)
		{
			return rendererIgnoredBlocks;
		}
		
		HashSet<String> baseIgnoredBlock = new HashSet<>();
		baseIgnoredBlock.add(AIR_STRING);
		rendererIgnoredBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderBlockCsv, baseIgnoredBlock, levelWrapper);
		return rendererIgnoredBlocks;
	}
	/**
	 * Requires a {@link ILevelWrapper} since {@link BlockStateWrapper#deserialize(String,ILevelWrapper)} also requires one. 
	 * This way the method won't accidentally be called before the deserialization can be completed.
	 */
	public static HashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (rendererIgnoredCaveBlocks != null)
		{
			return rendererIgnoredCaveBlocks;
		}
		
		HashSet<String> baseIgnoredBlock = new HashSet<>();
		baseIgnoredBlock.add(AIR_STRING);
		rendererIgnoredCaveBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderCaveBlockCsv, baseIgnoredBlock, levelWrapper);
		return rendererIgnoredCaveBlocks;
	}
	
	public static void clearRendererIgnoredBlocks() { rendererIgnoredBlocks = null; }
	public static void clearRendererIgnoredCaveBlocks() { rendererIgnoredCaveBlocks = null; }
	
	
	
	// lod builder helpers //
	
	private static HashSet<IBlockStateWrapper> getAllBlockWrappers(ConfigEntry<String> config, HashSet<String> baseResourceLocations, ILevelWrapper levelWrapper)
	{
		// get the base blocks 
		HashSet<String> blockStringList = new HashSet<>();
		if (baseResourceLocations != null)
		{
			blockStringList.addAll(baseResourceLocations);	
		}
		
		// get the config blocks
		String ignoreBlockCsv = config.get();
		if (ignoreBlockCsv != null)
		{
			blockStringList.addAll(Arrays.asList(ignoreBlockCsv.split(",")));
		}
		
		return getAllBlockWrappers(blockStringList, levelWrapper);
	}
	private static HashSet<IBlockStateWrapper> getAllBlockWrappers(HashSet<String> blockResourceLocationSet, ILevelWrapper levelWrapper)
	{
		// deserialize each of the given resource locations
		HashSet<IBlockStateWrapper> blockStateWrappers = new HashSet<>();
		for (String blockResourceLocation : blockResourceLocationSet)
		{
			try
			{
				if (blockResourceLocation == null)
				{
					// shouldn't happen, but just in case
					continue;
				}
				String cleanedResourceLocation = blockResourceLocation.trim();
				if (cleanedResourceLocation.length() == 0)
				{
					continue;
				}
				
				
				BlockStateWrapper defaultBlockStateToIgnore = (BlockStateWrapper) deserialize(cleanedResourceLocation, levelWrapper);
				blockStateWrappers.add(defaultBlockStateToIgnore);
				
				if (defaultBlockStateToIgnore != AIR)
				{
					// add all possible blockstates (to account for light blocks with different light values and such)
					List<BlockState> blockStatesToIgnore = defaultBlockStateToIgnore.blockState.getBlock().getStateDefinition().getPossibleStates();
					for (BlockState blockState : blockStatesToIgnore)
					{
						BlockStateWrapper newBlockToIgnore = BlockStateWrapper.fromBlockState(blockState, levelWrapper);
						blockStateWrappers.add(newBlockToIgnore);
					}
				}
				else
				{
					// air is a special case so it must be handled separately
					blockStateWrappers.add(AIR);
				}
			}
			catch (IOException e)
			{
				LOGGER.warn("Unable to deserialize block with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
			}
			catch (Exception e)
			{
				LOGGER.warn("Unexpected error deserializing block with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
			}
		}
		
		return blockStateWrappers;
	}
	
	
	
	//=================//
	// wrapper methods //
	//=================//
	
	@Override
	public int getOpacity() { return this.opacity; }
	private int calculateOpacity()
	{
		// get block properties (defaults to the values used by air)
		boolean canOcclude = this.getCanOcclude();
		boolean propagatesSkyLightDown = this.getPropagatesSkyLightDown();
		
		
		
		// this method isn't perfect, but works well enough for our use case
		int opacity;
		if (this.isAir())
		{
			opacity = LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		else if (this.isLiquid() && !canOcclude)
		{
			// probably not a waterlogged block (which should block light entirely)
			
			// +1 to indicate that the block is translucent (in between transparent and opaque) 
			opacity = LodUtil.BLOCK_FULLY_TRANSPARENT + 1;
		}
		else if (propagatesSkyLightDown && !canOcclude)
		{
			// probably glass or some other fully transparent block
			
			// !canOcclude is required to ignore stairs and slabs since
			// propagateSkyLightDown is true for them, but they're solid and don't actually let light through
			
			opacity = LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		else
		{
			// default for all other blocks
			opacity = LodUtil.BLOCK_FULLY_OPAQUE;
		}
		
		
		return opacity;
	}
	private boolean getCanOcclude()
	{
		// defaults to the value used by air
		boolean canOcclude = false;
		if (this.blockState != null)
		{
			canOcclude = this.blockState.canOcclude();
		}
		
		return canOcclude;
	}
	private boolean getPropagatesSkyLightDown()
	{
		// defaults to the value used by air
		boolean propagatesSkyLightDown = true;
		if (this.blockState != null)
		{
			propagatesSkyLightDown = this.blockState.propagatesSkylightDown();
		}
		
		return propagatesSkyLightDown;
	}
	
	
	
	
	@Override
	public int getLightEmission() { return (this.blockState != null) ? this.blockState.getLightEmission() : 0; }
	
	@Override
	public String getSerialString() { return this.serialString; }
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		
		if (obj == null || this.getClass() != obj.getClass())
		{
			return false;
		}
		
		BlockStateWrapper that = (BlockStateWrapper) obj;
		// the serialized value is used so we can test the contents instead of the references
		return Objects.equals(this.getSerialString(), that.getSerialString());
	}
	
	@Override
	public int hashCode() { return this.hashCode; } 
	
	
	@Override
	public Object getWrappedMcObject() { return this.blockState; }
	
	@Override
	public boolean isAir() { return this.isAir(this.blockState); }
	public boolean isAir(BlockState blockState) { return blockState == null || blockState.isAir(); }
	
	@Override
	public boolean isSolid()
	{
		if (this.isAir())
		{
			return false;
		}
		
		return !this.blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}
	
	@Override
	public boolean isLiquid()
	{
		if (this.isAir())
		{
			return false;
		}
		
		return !this.blockState.getFluidState().isEmpty();
	}
	
	@Override
	public boolean isBeaconBlock() { return this.isBeaconBlock; }
	@Override
	public boolean isBeaconBaseBlock() { return this.isBeaconBaseBlock; }
	@Override
	public boolean isBeaconTintBlock() { return this.beaconTintColor != null; }
	@Override
	public boolean allowsBeaconBeamPassage() { return this.allowsBeaconBeamPassage; }
	
	@Override
	public Color getMapColor() { return this.mapColor; }
	@Override
	public Color getBeaconTintColor() { return this.beaconTintColor; }
	
	@Override
	public byte getMaterialId() { return this.blockMaterialId; }
	
	@Override
	public String toString() { return this.getSerialString(); }
	
	
	
	//=======================//
	// serialization methods //
	//=======================//
	
	private String serialize(ILevelWrapper levelWrapper)
	{
		if (this.blockState == null)
		{
			return AIR_STRING;
		}
		
		
		
		// older versions of MC have a static registry
		Level level = (Level)levelWrapper.getWrappedMcObject();
		net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
		
		ResourceLocation resourceLocation;
		
		resourceLocation = registryAccess.lookupOrThrow(Registries.BLOCK).getKey(this.blockState.getBlock());
		
		
		
		if (resourceLocation == null)
		{
			LOGGER.warn("No ResourceLocation found, unable to serialize: " + this.blockState);
			return AIR_STRING;
		}
		
		this.serialString = resourceLocation.getNamespace() + RESOURCE_LOCATION_SEPARATOR + resourceLocation.getPath()
				+ STATE_STRING_SEPARATOR + serializeBlockStateProperties(this.blockState);
		
		return this.serialString;
	}
	
	
	/** will only work if a level is currently loaded */
	public static IBlockStateWrapper deserialize(String resourceStateString, ILevelWrapper levelWrapper) throws IOException
	{
		// we need the final string for the concurrent hash map later
		final String finalResourceStateString = resourceStateString;
		
		if (finalResourceStateString.equals(AIR_STRING) 
			|| finalResourceStateString.equals("")) // the empty string shouldn't normally happen, but just in case
		{
			return AIR;
		}
		
		// attempt to use the existing wrapper
		if (WRAPPER_BY_RESOURCE_LOCATION.containsKey(finalResourceStateString))
		{
			return WRAPPER_BY_RESOURCE_LOCATION.get(finalResourceStateString);
		}
		
		
		
		// if no wrapper is found, default to air
		BlockStateWrapper foundWrapper = AIR;
		try
		{
			// try to parse out the BlockState
			String blockStatePropertiesString = null; // will be null if no properties were included
			int stateSeparatorIndex = resourceStateString.indexOf(STATE_STRING_SEPARATOR);
			if (stateSeparatorIndex != -1)
			{
				// blockstate properties found
				blockStatePropertiesString = resourceStateString.substring(stateSeparatorIndex + STATE_STRING_SEPARATOR.length());
				resourceStateString = resourceStateString.substring(0, stateSeparatorIndex);
			}
			
			// parse the resource location
			int separatorIndex = resourceStateString.indexOf(RESOURCE_LOCATION_SEPARATOR);
			if (separatorIndex == -1)
			{
				throw new IOException("Unable to parse Resource Location out of string: [" + resourceStateString + "].");
			}
			
			ResourceLocation resourceLocation;
			
			try
			{
				resourceLocation = ResourceLocation.fromNamespaceAndPath(resourceStateString.substring(0, separatorIndex), resourceStateString.substring(separatorIndex + 1));
			}
			catch (Exception e)
			{
				throw new IOException("No Resource Location found for the string: [" + resourceStateString + "] Error: [" + e.getMessage() + "].");
			}
			
			
			
			// attempt to get the BlockState from all possible BlockStates
			try
			{
				
				LodUtil.assertTrue(levelWrapper != null && levelWrapper.getWrappedMcObject() != null);
				Level level = (Level)levelWrapper.getWrappedMcObject();
				
				Block block;
				net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
				Optional<Holder.Reference<Block>> optionalBlockHolder = registryAccess.lookupOrThrow(Registries.BLOCK).get(resourceLocation);
				block = optionalBlockHolder.isPresent() ? optionalBlockHolder.get().value() : null;
				
				
				if (block == null)
				{
					// shouldn't normally happen, but here to make the compiler happy
					if (!BROKEN_RESOURCE_LOCATIONS.contains(resourceLocation))
					{
						BROKEN_RESOURCE_LOCATIONS.add(resourceLocation);
						LOGGER.warn("Unable to find BlockState with the resourceLocation [" + resourceLocation + "] and properties: [" + blockStatePropertiesString + "]. Air will be used instead, some data may be lost.");
					}
					
					return AIR;
				}
				
				
				// attempt to find the blockstate from all possibilities
				BlockState foundState = null;
				if (blockStatePropertiesString != null)
				{
					List<BlockState> possibleStateList = block.getStateDefinition().getPossibleStates();
					for (BlockState possibleState : possibleStateList)
					{
						String possibleStatePropertiesString = serializeBlockStateProperties(possibleState);
						if (possibleStatePropertiesString.equals(blockStatePropertiesString))
						{
							foundState = possibleState;
							break;
						}
					}
				}
				
				// use the default if no state was found or given
				if (foundState == null)
				{
					if (blockStatePropertiesString != null)
					{
						// we should have found a blockstate, but didn't
						if (!BROKEN_RESOURCE_LOCATIONS.contains(resourceLocation))
						{
							BROKEN_RESOURCE_LOCATIONS.add(resourceLocation);
							LOGGER.warn("Unable to find BlockState for Block [" + resourceLocation + "] with properties: [" + blockStatePropertiesString + "]. Using the default block state.");
						}
					}
					
					foundState = block.defaultBlockState();
				}
				
				foundWrapper = new BlockStateWrapper(foundState, levelWrapper);
				return foundWrapper;
			}
			catch (Exception e)
			{
				throw new IOException("Failed to deserialize the string [" + finalResourceStateString + "] into a BlockStateWrapper: " + e.getMessage(), e);
			}
		}
		finally
		{
			// put if absent in case two threads deserialize at the same time
			// unfortunately we can't put everything in a computeIfAbsent() since we also throw exceptions
			WRAPPER_BY_RESOURCE_LOCATION.putIfAbsent(finalResourceStateString, foundWrapper);
		}
	}
	
	/** used to compare and save BlockStates based on their properties */
	private static String serializeBlockStateProperties(BlockState blockState)
	{
		// get the property list for this block (doesn't contain this block state's values, just the names and possible values)
		java.util.Collection<net.minecraft.world.level.block.state.properties.Property<?>> blockPropertyCollection = blockState.getProperties();
		
		// alphabetically sort the list so they are always in the same order
		List<net.minecraft.world.level.block.state.properties.Property<?>> sortedBlockPropteryList = new ArrayList<>(blockPropertyCollection);
		sortedBlockPropteryList.sort((a, b) -> a.getName().compareTo(b.getName()));
		
		
		StringBuilder stringBuilder = new StringBuilder();
		for (net.minecraft.world.level.block.state.properties.Property<?> property : sortedBlockPropteryList)
		{
			String propertyName = property.getName();
			
			String value = "NULL";
			if (blockState.hasProperty(property))
			{
				value = blockState.getValue(property).toString();
			}
			
			stringBuilder.append("{");
			stringBuilder.append(propertyName).append(RESOURCE_LOCATION_SEPARATOR).append(value);
			stringBuilder.append("}");
		}
		
		return stringBuilder.toString();
	}
	
	
	
	//==============//
	// Iris methods //
	//==============//
	
	private EDhApiBlockMaterial calculateEDhApiBlockMaterialId() 
	{
		if (this.blockState == null)
		{
			return EDhApiBlockMaterial.AIR;
		}
		
		
		String serialString = this.getSerialString().toLowerCase();
		
		if (this.blockState.is(BlockTags.LEAVES) 
			|| serialString.contains("bamboo") 
			|| serialString.contains("cactus")
			|| serialString.contains("chorus_flower")
			|| serialString.contains("mushroom")
			) 
		{
			return EDhApiBlockMaterial.LEAVES;
		}
		else if (this.blockState.is(Blocks.LAVA))
		{
			return EDhApiBlockMaterial.LAVA;
		}
		else if (this.isLiquid() || this.blockState.is(Blocks.WATER))
		{
			return EDhApiBlockMaterial.WATER;
		}
		else if (this.blockState.getSoundType() == SoundType.WOOD
				|| serialString.contains("root")
				|| this.blockState.getSoundType() == SoundType.CHERRY_WOOD
				) 
		{
			return EDhApiBlockMaterial.WOOD;
		}
		else if (this.blockState.getSoundType() == SoundType.METAL
				|| this.blockState.getSoundType() == SoundType.COPPER
				|| this.blockState.getSoundType() == SoundType.COPPER_BULB
				|| this.blockState.getSoundType() == SoundType.COPPER_GRATE
				) 
		{
			return EDhApiBlockMaterial.METAL;
		}
		else if (serialString.contains("grass_block"))
		{
			return EDhApiBlockMaterial.GRASS;
		}
		else if (
			serialString.contains("dirt")
			|| serialString.contains("gravel")
			|| serialString.contains("mud")
			|| serialString.contains("podzol")
			|| serialString.contains("mycelium")
			)
		{
			return EDhApiBlockMaterial.DIRT;
		}
		else if (this.blockState.getSoundType() == SoundType.DEEPSLATE
				|| this.blockState.getSoundType() == SoundType.DEEPSLATE_BRICKS
				|| this.blockState.getSoundType() == SoundType.DEEPSLATE_TILES 
				|| this.blockState.getSoundType() == SoundType.POLISHED_DEEPSLATE
				|| serialString.contains("deepslate") ) 
		{
			return EDhApiBlockMaterial.DEEPSLATE;
		} 
		else if (this.serialString.contains("snow"))
		{
			return EDhApiBlockMaterial.SNOW;
		} 
		else if (serialString.contains("sand"))
		{
			return EDhApiBlockMaterial.SAND;
		}
		else if (serialString.contains("terracotta"))
		{
			return EDhApiBlockMaterial.TERRACOTTA;
		} 
		else if (this.blockState.is(BlockTags.BASE_STONE_NETHER)) 
		{
			return EDhApiBlockMaterial.NETHER_STONE;
		} 
		else if (serialString.contains("stone")
				|| serialString.contains("ore")) 
		{
			return EDhApiBlockMaterial.STONE;
		}
		else if (this.blockState.getLightEmission() > 0) 
		{
			return EDhApiBlockMaterial.ILLUMINATED;
		}
		else
		{
			return EDhApiBlockMaterial.UNKNOWN;
		}
	}
	
}
