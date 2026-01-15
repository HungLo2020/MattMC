package net.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling;

import net.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import net.distanthorizons.common.wrappers.worldGeneration.params.GlobalWorldGenParams;
import net.distanthorizons.common.wrappers.worldGeneration.mimicObject.RegionFileStorageExternalCache;
import net.distanthorizons.core.config.Config;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.logging.DhLogger;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.pos.DhChunkPos;
import net.distanthorizons.core.util.ExceptionUtil;
import net.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import net.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.level.chunk.PalettedContainerFactory;

public class ChunkFileReader implements AutoCloseable
{
	
	public static final DhLogger LOGGER = new DhLoggerBuilder()
		.name("LOD World Gen")
		.fileLevelConfig(Config.Common.Logging.logWorldGenEventToFile)
		.build();
	
	public static final DhLogger CHUNK_LOAD_LOGGER = new DhLoggerBuilder()
		.name("LOD Chunk Loading")
		.fileLevelConfig(Config.Common.Logging.logWorldGenChunkLoadEventToFile)
		.build();
	
	private static final IModChecker MOD_CHECKER = SingletonInjector.INSTANCE.get(IModChecker.class);
	
	public final GlobalWorldGenParams params;
	
	/**
	 * will be true if C2ME is installed (since they require us to
	 * pull chunks using their async method), or if there
	 * was an issue with the sync pulling method.
	 */
	private boolean pullExistingChunkUsingMcAsyncMethod = false;
	
	private final AtomicReference<RegionFileStorageExternalCache> regionFileStorageCacheRef = new AtomicReference<>();
	public RegionFileStorageExternalCache getOrCreateRegionFileCache(RegionFileStorage storage)
	{
		RegionFileStorageExternalCache cache = this.regionFileStorageCacheRef.get();
		if (cache == null)
		{
			cache = new RegionFileStorageExternalCache(storage);
			if (!this.regionFileStorageCacheRef.compareAndSet(null, cache))
			{
				cache = this.regionFileStorageCacheRef.get();
			}
		}
		return cache;
	}
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkFileReader(GlobalWorldGenParams params)
	{
		this.params = params;
		
		if (MOD_CHECKER.isModLoaded("c2me"))
		{
			LOGGER.info("C2ME detected: DH's pre-existing chunk accessing will use methods handled by C2ME.");
			this.pullExistingChunkUsingMcAsyncMethod = true;
		}
		
	}
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/**
	 * If the given chunk pos already exists in the world, that chunk will be returned,
	 * otherwise this will return an empty chunk.
	 */
	public CompletableFuture<ChunkWrapper> createEmptyOrPreExistingChunkWrapperAsync(
		int chunkX, int chunkZ,
		Map<DhChunkPos, ChunkLightStorage> chunkSkyLightingByDhPos,
		Map<DhChunkPos, ChunkLightStorage> chunkBlockLightingByDhPos,
		Map<DhChunkPos, ChunkWrapper> generatedChunkWrapperByDhPos)
	{
		ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
		DhChunkPos dhChunkPos = new DhChunkPos(chunkX, chunkZ);
		
		if (generatedChunkWrapperByDhPos.containsKey(dhChunkPos))
		{
			return CompletableFuture.completedFuture(generatedChunkWrapperByDhPos.get(dhChunkPos));
		}
		
		return this.getChunkNbtDataAsync(chunkPos)
			.thenApply((CompoundTag chunkData) ->
			{
				ChunkWrapper newChunkWrapper = this.loadOrMakeChunkWrapper(chunkPos, chunkData);
				
				// attempt to get chunk lighting
				ChunkCompoundTagParser.CombinedChunkLightStorage combinedLights = ChunkCompoundTagParser.readLight(newChunkWrapper.getChunk(), chunkData);
				if (combinedLights != null)
				{
					// may be empty, empty checks are handled later
					chunkSkyLightingByDhPos.put(dhChunkPos, combinedLights.skyLightStorage);
					chunkBlockLightingByDhPos.put(dhChunkPos, combinedLights.blockLightStorage);
				}
				
				return newChunkWrapper;
			})
			// separate handle so we can cleanly handle missing chunks and/or thrown errors 
			.handle((ChunkWrapper newChunkWrapper, Throwable throwable) ->
			{
				if (newChunkWrapper != null)
				{
					return newChunkWrapper;
				}
				else
				{
					return this.CreateProtoChunkWrapper(this.params.mcServerLevel, chunkPos);
				}
			})
			.thenApply((ChunkWrapper newChunkWrapper) ->
			{
				generatedChunkWrapperByDhPos.put(dhChunkPos, newChunkWrapper);
				return newChunkWrapper;
			});
	}
	private CompletableFuture<CompoundTag> getChunkNbtDataAsync(ChunkPos chunkPos)
	{
		ServerLevel level = this.params.mcServerLevel;
		
		try
		{
			IOWorker ioWorker = level.getChunkSource().chunkMap.worker;
			
			
			// storage will be null if C2ME is installed
			if (!this.pullExistingChunkUsingMcAsyncMethod 
				&& ioWorker.storage != null)
			{
				try
				{
					RegionFileStorage storage = this.params.mcServerLevel.getChunkSource().chunkMap.worker.storage;
					RegionFileStorageExternalCache cache = this.getOrCreateRegionFileCache(storage);
					return CompletableFuture.completedFuture(cache.read(chunkPos));
				}
				catch (NullPointerException e)
				{
					// this shouldn't happen, if anything is null it should be
					// ioWorker.storage
					// but just in case
					LOGGER.error("Unexpected issue pulling pre-existing chunk ["+chunkPos+"], falling back to async chunk pulling. This may cause server-tick lag.", e);
					this.pullExistingChunkUsingMcAsyncMethod = true;
					
					// try again now using the async method
					return this.getChunkNbtDataAsync(chunkPos);
				}
			}
			else
			{
				// log if we unexpectedly weren't able to run the sync chunk pulling
				if (!this.pullExistingChunkUsingMcAsyncMethod)
				{
					// this shouldn't happen, but just in case
					LOGGER.info("Unable to pull pre-existing chunk using synchronous method. Falling back to async method. this may cause server-tick lag.");
					this.pullExistingChunkUsingMcAsyncMethod = true;
				}
				
				//GET_CHUNK_COUNT_REF.incrementAndGet();
				
				// When running in vanilla MC on versions before 1.21.4,  
				// DH would attempt to run loadAsync on this same thread via a threading mixin,
				// to prevent causing lag on the server thread.
				// However, if a mod like C2ME is installed this will run on a C2ME thread instead.
				return ioWorker.loadAsync(chunkPos)
					.thenApply(optional ->
					{
						// Debugging note:
						// If there are reports of extreme memory use when C2ME is installed, that probably means
						// this method is queuing a lot of tasks (1,000+), which causes C2ME to explode.
						
						//GET_CHUNK_COUNT_REF.decrementAndGet();
						//PREF_LOGGER.info("chunk getter count ["+F3Screen.NUMBER_FORMAT.format(GET_CHUNK_COUNT_REF.get())+"]");
						return optional.orElse(null);
					})
					.exceptionally((throwable) ->
					{
						// unwrap the CompletionException if necessary
						Throwable actualThrowable = throwable;
						while (actualThrowable instanceof CompletionException completionException)
						{
							actualThrowable = completionException.getCause();
						}
						
						boolean isShutdownException = ExceptionUtil.isShutdownException(actualThrowable);
						if (!isShutdownException)
						{
							CHUNK_LOAD_LOGGER.warn("DistantHorizons: Couldn't load or make chunk ["+chunkPos+"], error: ["+actualThrowable.getMessage()+"].", actualThrowable);
						}
						
						return null;
					});
			}
		}
		catch (ClosedByInterruptException ignore)
		{
			// this just means the world generator is being shut down
			return CompletableFuture.completedFuture(null);
		}
		catch (Exception e)
		{
			CHUNK_LOAD_LOGGER.warn("Couldn't load or make chunk [" + chunkPos + "]. Error: [" + e.getMessage() + "].", e);
			return CompletableFuture.completedFuture(null);
		}
	}
	private ChunkWrapper loadOrMakeChunkWrapper(ChunkPos chunkPos, CompoundTag chunkTagData)
	{
		ServerLevel mcServerLevel = this.params.mcServerLevel;
		
		if (chunkTagData == null)
		{
			return this.CreateProtoChunkWrapper(mcServerLevel, chunkPos);
		}
		else
		{
			try
			{
				ChunkWrapper chunkWrapper = ChunkCompoundTagParser.createFromTag(mcServerLevel, this.params.dhServerLevel, chunkPos, chunkTagData);
				if (chunkWrapper == null)
				{
					chunkWrapper = this.CreateProtoChunkWrapper(mcServerLevel, chunkPos);
				}
				return chunkWrapper;
			}
			catch (Exception e)
			{
				CHUNK_LOAD_LOGGER.error(
					"DistantHorizons: couldn't load or make chunk at [" + chunkPos + "]." +
						"Please try optimizing your world to fix this issue. \n" +
						"World optimization can be done from the singleplayer world selection screen.\n" +
						"Error: [" + e.getMessage() + "]."
					, e);
				
				return this.CreateProtoChunkWrapper(mcServerLevel, chunkPos);
			}
		}
	}
	
	public ChunkWrapper CreateProtoChunkWrapper(ServerLevel level, ChunkPos chunkPos)
	{
		ProtoChunk chunk = CreateProtoChunk(level, chunkPos);
		return new ChunkWrapper(chunk, this.params.dhServerLevel.getLevelWrapper(), false);
	}
	public static ProtoChunk CreateProtoChunk(ServerLevel level, ChunkPos chunkPos)
	{
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, PalettedContainerFactory.create(level.registryAccess()), null);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close() 
	{
		RegionFileStorageExternalCache regionStorage = this.regionFileStorageCacheRef.get();
		if (regionStorage != null)
		{
			try
			{
				regionStorage.close();
			}
			catch (ClosedChannelException ignore) { /* world generator is being shut down */ }
			catch (IOException e)
			{
				LOGGER.error("Failed to close region file storage cache, error: ["+e.getMessage()+"].", e);
			}
		}
	}
	
	
	
}
