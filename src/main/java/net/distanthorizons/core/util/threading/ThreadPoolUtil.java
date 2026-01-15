package net.distanthorizons.core.util.threading;

import net.distanthorizons.core.util.ThreadUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;

/**
 * Holds each thread pool the system uses.
 * 
 * @see ThreadUtil
 */
public class ThreadPoolUtil
{
	//=========================//
	// standalone thread pools //
	//=========================//
	
	// standalone thread pools all handle independent systems
	// and don't interfere with any other pool
	
	private static PriorityTaskPicker taskPicker;
	
	private static PriorityTaskPicker.Executor fileHandlerThreadPool;
	@Nullable
	public static PriorityTaskPicker.Executor getFileHandlerExecutor() { return fileHandlerThreadPool; }
	
	private static PriorityTaskPicker.Executor renderSectionLoadThreadPool;
	@Nullable
	public static PriorityTaskPicker.Executor getRenderLoadingExecutor() { return renderSectionLoadThreadPool; }
	
	private static PriorityTaskPicker.Executor updatePropagatorThreadPool;
	@Nullable
	public static PriorityTaskPicker.Executor getUpdatePropagatorExecutor() { return updatePropagatorThreadPool; }
	
	public static final DhThreadFactory WORLD_GEN_THREAD_FACTORY = new DhThreadFactory("World Gen", Thread.MIN_PRIORITY, false);
	private static PriorityTaskPicker.Executor worldGenThreadPool;
	@Nullable
	public static PriorityTaskPicker.Executor getWorldGenExecutor() { return worldGenThreadPool; }
	
	public static final String CLEANUP_THREAD_NAME = "Cleanup";
	private static final ThreadPoolExecutor cleanupThreadPool = ThreadUtil.makeSingleThreadPool(CLEANUP_THREAD_NAME);
	/** not null since cleanup always needs to be run even when DH has been shut down */
	@NotNull
	public static ThreadPoolExecutor getCleanupExecutor() { return cleanupThreadPool; }
	
	public static final String BEACON_CULLING_THREAD_NAME = "Beacon Culling";
	private static ThreadPoolExecutor beaconCullingThreadPool;
	@Nullable
	public static ThreadPoolExecutor getBeaconCullingExecutor() { return beaconCullingThreadPool; }
	
	private static PriorityTaskPicker.Executor networkCompressionThreadPool;
	@Nullable
	public static PriorityTaskPicker.Executor getNetworkCompressionExecutor() { return networkCompressionThreadPool; }
	
	
	
	public static final String FULL_DATA_MIGRATION_THREAD_NAME = "Full Data Migration";
	private static ThreadPoolExecutor fullDataMigrationThreadPool;
	@Nullable
	public static ThreadPoolExecutor getFullDataMigrationExecutor() { return fullDataMigrationThreadPool; }
	
	
	private static PriorityTaskPicker.Executor chunkToLodBuilderThreadPool;
	@Nullable
	public static PriorityTaskPicker.Executor getChunkToLodBuilderExecutor() { return chunkToLodBuilderThreadPool; }
	
	
	
	//=================//
	// setup / cleanup //
	//=================//
	
	public static void setupThreadPools()
	{
		//==================//
		// main thread pool //
		//==================//
		
		if (taskPicker != null)
		{
			taskPicker.shutdownNow();
		}
		taskPicker = new PriorityTaskPicker();
		
		networkCompressionThreadPool = taskPicker.createExecutor("Network");
		fileHandlerThreadPool = taskPicker.createExecutor("IO");
		renderSectionLoadThreadPool = taskPicker.createExecutor("Render Loader");
		chunkToLodBuilderThreadPool = taskPicker.createExecutor("LOD Builder");
		updatePropagatorThreadPool = taskPicker.createExecutor("Update Propagator");
		worldGenThreadPool = taskPicker.createExecutor("World Gen");
		
		
		
		//=========================//
		// standalone thread pools //
		//=========================//
		
		if (beaconCullingThreadPool != null)
		{
			beaconCullingThreadPool.shutdown();
		}
		beaconCullingThreadPool = ThreadUtil.makeSingleThreadPool(BEACON_CULLING_THREAD_NAME);
		
		if (fullDataMigrationThreadPool != null)
		{
			fullDataMigrationThreadPool.shutdown();
		}
		fullDataMigrationThreadPool = ThreadUtil.makeSingleThreadPool(FULL_DATA_MIGRATION_THREAD_NAME);
		
	}
	
	public static void shutdownThreadPools()
	{
		// standalone threads
		taskPicker.shutdownNow();
		beaconCullingThreadPool.shutdown();
		fullDataMigrationThreadPool.shutdown();
	}
	
}
