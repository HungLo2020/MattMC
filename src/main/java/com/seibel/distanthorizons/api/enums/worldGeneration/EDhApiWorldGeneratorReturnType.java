package com.seibel.distanthorizons.api.enums.worldGeneration;

import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * VANILLA_CHUNKS, <br>
 * API_CHUNKS <br>
 * API_DATA_SOURCES <br>
 * 
 * @author Builderb0y, James Seibel
 * @version 2024-10-5
 * @since API 2.0.0
 */
public enum EDhApiWorldGeneratorReturnType
{
	/**
	 * when this constant is returned by {@link IDhApiWorldGenerator#getReturnType()},
	 * {@link IDhApiWorldGenerator#generateChunks(int, int, int, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer)}
	 * will be used when generating terrain.
	 * 
	 * @since API 2.0.0
	 */
	VANILLA_CHUNKS,
	
	/**
	 * when this constant is returned by {@link IDhApiWorldGenerator#getReturnType()},
	 * {@link IDhApiWorldGenerator#generateApiChunks(int, int, int, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer)}
	 * will be used when generating terrain.
	 * 
	 * @since API 2.0.0
	 */
	API_CHUNKS,
	
	/**
	 * when this constant is returned by {@link IDhApiWorldGenerator#getReturnType()},
	 * {@link IDhApiWorldGenerator#generateLod(int, int, int, int, byte, IDhApiFullDataSource, EDhApiDistantGeneratorMode, ExecutorService, Consumer)}
	 * will be used when generating terrain.
	 * 
	 * @since API 4.0.0
	 */
	API_DATA_SOURCES;
	
}
