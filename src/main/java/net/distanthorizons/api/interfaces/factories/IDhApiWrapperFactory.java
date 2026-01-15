package net.distanthorizons.api.interfaces.factories;

import net.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import net.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import net.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import net.distanthorizons.api.DhApi;

import java.io.IOException;

/**
 * This handles creating abstract wrapper objects.
 *
 * @author James Seibel
 * @version 2023-12-16
 * @since API 2.0.0
 */
public interface IDhApiWrapperFactory
{
	/**
	 * Constructs a {@link IDhApiBiomeWrapper} for use by other DhApi methods.
	 * 
	 * @param objectArray Expects the following Minecraft objects (in order): <br>
	 * - {@literal [net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>] }<br>
	 * 
	 * @param levelWrapper Expects a {@link IDhApiLevelWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiLevelWrapper} will not be accepted.
	 * 
	 * @throws ClassCastException if any of the given parameters is of the wrong type. 
	 * If thrown the error message will contain the list of expected object types in order. 
	 * 
	 * @since API 2.0.0
	 */
	IDhApiBiomeWrapper getBiomeWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper) throws ClassCastException;
	
	/**
	 * Constructs a {@link IDhApiBlockStateWrapper} for use by other DhApi methods.
	 *
	 * @param objectArray Expects the following Minecraft objects (in order): <br>
	 * - [net.minecraft.world.level.block.state.BlockState]<br>
	 *
	 * @param levelWrapper Expects a {@link IDhApiBlockStateWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiBlockStateWrapper} will not be accepted.
	 *
	 * @throws ClassCastException if any of the given parameters is of the wrong type. 
	 * If thrown the error message will contain the list of expected object types in order. 
	 * 
	 * @since API 2.0.0
	 */
	IDhApiBlockStateWrapper getBlockStateWrapper(Object[] objectArray, IDhApiLevelWrapper levelWrapper) throws ClassCastException;
	
	/**
	 * Returns the {@link IDhApiBlockStateWrapper} representing air.
	 * @since API 2.0.0
	 */
	IDhApiBlockStateWrapper getAirBlockStateWrapper();
	
	
	
	/**
	 * Constructs a {@link IDhApiBiomeWrapper} for use by other DhApi methods.
	 *
	 * @param resourceLocationString example: "minecraft:plains"
	 *
	 * @param levelWrapper Expects a {@link IDhApiLevelWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiLevelWrapper} will not be accepted.
	 *
	 * @throws IOException if the resourceLocationString wasn't able to be parsed or converted into a valid {@link IDhApiBiomeWrapper}
	 * @throws ClassCastException if the wrong levelWrapper type was given
	 *
	 * @since API 3.0.0
	 */
	IDhApiBiomeWrapper getBiomeWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException;
	
	/**
	 * Constructs a {@link IDhApiBlockStateWrapper} for use by other DhApi methods.
	 * This returns the default blockstate for the given resource location.
	 *
	 * @param resourceLocationString examples: "minecraft:bedrock", "minecraft:stone", "minecraft:grass_block"
	 * @param levelWrapper Expects a {@link IDhApiBlockStateWrapper} returned by one of DH's {@link DhApi.Delayed#worldProxy} methods. <br>
	 *                      A custom implementation of {@link IDhApiBlockStateWrapper} will not be accepted.
	 *
	 * @throws IOException if the resourceLocationString wasn't able to be parsed or converted into a valid {@link IDhApiBlockStateWrapper}
	 * @throws ClassCastException if the wrong levelWrapper type was given
	 *
	 * @since API 3.0.0
	 */
	IDhApiBlockStateWrapper getDefaultBlockStateWrapper(String resourceLocationString, IDhApiLevelWrapper levelWrapper) throws IOException, ClassCastException;
	
}
