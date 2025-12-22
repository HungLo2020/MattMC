package com.seibel.distanthorizons.fabric.mixins.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
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
	
	@Unique
	private boolean fabric_wasFullChunk = false;
	
	@Unique
	private boolean fabric_loadEventFired = false;
	
	/**
	 * Fire CHUNK_LOAD and CHUNK_GENERATE events when chunks are promoted to FULL status.
	 * We inject right after the fullChunkFuture is scheduled for promotion (line 282 in updateFutures).
	 */
	@Inject(
		method = "updateFutures",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkHolder;scheduleFullChunkPromotion(Lnet/minecraft/server/level/ChunkMap;Ljava/util/concurrent/CompletableFuture;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/FullChunkStatus;)V",
			ordinal = 0
		)
	)
	private void onChunkPromotedToFull(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {
		if (!fabric_loadEventFired) {
			LevelChunk levelChunk = this.getChunkToSend();
			
			if (levelChunk != null) {
				// Use the accessor to get the level from ChunkMap
				ServerLevel level = ((ChunkMapAccessor) chunkMap).distanthorizons$getLevel();
				
				LOGGER.info("[DH-CHUNK-LOAD] Firing CHUNK_LOAD event for chunk at {}", levelChunk.getPos());
				
				try {
					ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, levelChunk);
					
					// For newly generated chunks (not loaded from disk), also fire CHUNK_GENERATE
					// Track if this chunk was previously at FULL status to distinguish new generation from loading
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
	}
	
	/**
	 * Reset tracking flags when chunk is demoted from FULL status.
	 */
	@Inject(
		method = "updateFutures",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/CompletableFuture;complete(Ljava/lang/Object;)Z",
			ordinal = 0
		)
	)
	private void onChunkDemotedFromFull(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {
		fabric_loadEventFired = false;
		fabric_wasFullChunk = false;
		LOGGER.info("[DH-CHUNK-UPDATE] Chunk demoted from FULL, resetting tracking flags");
	}
}
