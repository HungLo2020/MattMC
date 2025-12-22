package com.seibel.distanthorizons.fabric.mixins.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Mixin to invoke Fabric ServerChunkEvents.CHUNK_LOAD when chunks become accessible.
 * This is needed because the standard Fabric API mixins are not included in this build.
 * 
 * Based on Fabric API's ChunkHolderMixin and ChunkGeneratingMixin.
 * 
 * Note: ChunkHolder doesn't have a ServerLevel field, so we use ChunkMapAccessor
 * to access the level from the ChunkMap parameter passed to updateFutures().
 */
@Mixin(ChunkHolder.class)
public abstract class MixinChunkHolderChunkEvents {
	private static final Logger LOGGER = LoggerFactory.getLogger("DH-ChunkEvents");
	
	@Shadow
	private int oldTicketLevel;
	
	@Shadow
	private int ticketLevel;
	
	@Shadow
	public abstract LevelChunk getChunkToSend();
	
	@Shadow
	public abstract ChunkAccess getLatestChunk();
	
	@Unique
	private boolean fabric_wasFullChunk = false;
	
	@Unique
	private boolean fabric_loadEventFired = false;
	
	/**
	 * Fire CHUNK_LOAD and CHUNK_GENERATE events when chunks are promoted.
	 * We inject into promoteChunk which is called when a chunk reaches FULL status from generation or loading.
	 */
	@Inject(method = "promoteChunk", at = @At("RETURN"))
	private void onPromoteChunk(ChunkMap chunkMap, Executor executor, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
		// Only fire events once per chunk
		if (fabric_loadEventFired) {
			return;
		}
		
		// Get the chunk that was just promoted
		ChunkAccess chunk = this.getLatestChunk();
		
		if (chunk instanceof LevelChunk levelChunk) {
			// Use the accessor to get the level from ChunkMap
			ServerLevel level = ((ChunkMapAccessor) chunkMap).distanthorizons$getLevel();
			
			LOGGER.info("[DH-CHUNK-LOAD] Firing CHUNK_LOAD event for chunk at {}", levelChunk.getPos());
			
			try {
				ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, levelChunk);
				
				// For newly generated chunks (not loaded from disk), also fire CHUNK_GENERATE
				// In Minecraft, chunks loaded from disk are already LevelChunks, newly generated ones are ProtoChunks first
				if (!fabric_wasFullChunk) {
					LOGGER.info("[DH-CHUNK-GENERATE] Firing CHUNK_GENERATE event for chunk at {}", levelChunk.getPos());
					ServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(level, levelChunk);
				}
				
				fabric_loadEventFired = true;
				fabric_wasFullChunk = true;
			} catch (Exception e) {
				LOGGER.error("[DH-CHUNK-LOAD] Error invoking chunk events for {}: {}", levelChunk.getPos(), e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Track when chunks are demoted to reset our tracking flags.
	 */
	@Inject(method = "demoteChunk", at = @At("HEAD"))
	private void onDemoteChunk(ChunkMap chunkMap, FullChunkStatus fullChunkStatus, CallbackInfo ci) {
		// Reset flags when chunk is unloaded
		fabric_loadEventFired = false;
		fabric_wasFullChunk = false;
		LOGGER.info("[DH-CHUNK-UPDATE] Chunk demoted, resetting tracking flags");
	}
}
