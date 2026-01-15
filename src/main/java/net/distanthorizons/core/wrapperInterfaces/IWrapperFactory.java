package net.distanthorizons.core.wrapperInterfaces;

import net.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import net.distanthorizons.core.level.IDhLevel;
import net.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import net.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import net.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.distanthorizons.core.wrapperInterfaces.worldGeneration.IBatchGeneratorEnvironmentWrapper;
import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

import java.io.IOException;
import java.util.HashSet;

/**
 * This handles creating abstract wrapper objects.
 *
 * @author James Seibel
 * @version 2022-12-5
 */
public interface IWrapperFactory extends IDhApiWrapperFactory, IBindable
{
	IBatchGeneratorEnvironmentWrapper createBatchGenerator(IDhLevel targetLevel);
	
	IBiomeWrapper deserializeBiomeWrapper(String str, ILevelWrapper levelWrapper) throws IOException;
	IBiomeWrapper getPlainsBiomeWrapper(ILevelWrapper levelWrapper); // TODO it would be nice to remove the level wrapper if possible to put this in line with getAirBlockStateWrapper() but it isn't necessary 
	default IBiomeWrapper deserializeBiomeWrapperOrGetDefault(String str, ILevelWrapper levelWrapper)
	{
		IBiomeWrapper biome;
		try
		{
			biome = this.deserializeBiomeWrapper(str, levelWrapper);
		}
		catch (IOException e)
		{
			biome = this.getPlainsBiomeWrapper(levelWrapper);
		}
		
		return biome;
	}
	
	
	IBlockStateWrapper deserializeBlockStateWrapper(String str, ILevelWrapper levelWrapper) throws IOException;
	IBlockStateWrapper getAirBlockStateWrapper();
	default IBlockStateWrapper deserializeBlockStateWrapperOrGetDefault(String str, ILevelWrapper levelWrapper)
	{
		IBlockStateWrapper blockState;
		try
		{
			blockState = this.deserializeBlockStateWrapper(str, levelWrapper);
		}
		catch (IOException e)
		{
			blockState = this.getAirBlockStateWrapper();
		}
		
		return blockState;
	}
	
	
	/**
	 * Returns the set of {@link IBlockStateWrapper}'s that shouldn't be rendered. <br>
	 * Generally this contains blocks like: air, barriers, light blocks, etc. 
	 */
	HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper);
	/**
	 * Returns the set of {@link IBlockStateWrapper}'s that shouldn't be rendered in caves. <br>
	 * Generally this contains blocks like: air, rails, glow lichen, etc. 
	 */
	HashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper);
	
	/** clears the cached values */
	void resetRendererIgnoredCaveBlocks();
	/** clears the cached values */
	void resetRendererIgnoredBlocksSet();
	
	
	/**
	 * Specifically designed to be used with the API.
	 *
	 * @throws ClassCastException with instructions on expected objects if the object couldn't be cast
	 */
	IChunkWrapper createChunkWrapper(Object[] objectArray) throws ClassCastException;
	
}
