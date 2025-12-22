package com.seibel.distanthorizons.fabric.mixins.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

/**
 * Mixin to invoke Fabric ServerChunkEvents.CHUNK_LOAD when chunks become accessible.
 * This is needed because the standard Fabric API mixins are not included in this build.
 * 
 * Based on Fabric API's ChunkHolderMixin and ChunkGeneratingMixin.
 */
@Mixin(ChunkHolder.class)
public abstract class MixinChunkHolderChunkEvents {
	private static final Logger LOGGER = LoggerFactory.getLogger("DH-ChunkEvents");
	
	@Shadow
	@Final
	private ServerLevel level;
	
	@Shadow
	private int oldTicketLevel;
	
	@Shadow
	private int ticketLevel;
	
	@Shadow
	public abstract LevelChunk getChunkToSend();
	
	@Unique
	private boolean fabric_wasFullChunk = false;
	
	/**
	 * Fire CHUNK_LOAD event when a chunk becomes accessible (reaches FULL status).
	 * Injects into updateFutures() method which is called when chunk ticket levels change.
	 */
	@Inject(method = "updateFutures", at = @At("TAIL"))
	private void onChunkLoad(ChunkMap chunkMap, Executor executor, CallbackInfo ci) {
		FullChunkStatus oldStatus = net.minecraft.server.level.ChunkLevel.fullStatus(this.oldTicketLevel);
		FullChunkStatus newStatus = net.minecraft.server.level.ChunkLevel.fullStatus(this.ticketLevel);
		
		boolean wasAccessible = oldStatus.isOrAfter(FullChunkStatus.FULL);
		boolean isAccessible = newStatus.isOrAfter(FullChunkStatus.FULL);
		
		// Fire CHUNK_LOAD when chunk becomes accessible (wasn't accessible before, but is now)
		if (!wasAccessible && isAccessible) {
			LevelChunk chunk = this.getChunkToSend();
			if (chunk != null) {
				LOGGER.info("[DH-CHUNK-LOAD] Chunk load event: {} (ticket level {} -> {})", chunk.getPos(), oldTicketLevel, ticketLevel);
				
				try {
					ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(this.level, chunk);
					
					// Fire CHUNK_GENERATE if this is a newly generated chunk (not loaded from disk)
					// We detect this by checking if the chunk was previously unloaded
					if (!fabric_wasFullChunk) {
						LOGGER.info("[DH-CHUNK-GENERATE] Chunk generate event: {}", chunk.getPos());
						ServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(this.level, chunk);
					}
				} catch (Exception e) {
					LOGGER.error("[DH-CHUNK-LOAD] Error invoking chunk load event for {}: {}", chunk.getPos(), e.getMessage(), e);
				}
				
				fabric_wasFullChunk = true;
			}
		} else if (wasAccessible && !isAccessible) {
			// Chunk is being unloaded
			fabric_wasFullChunk = false;
		}
	}
}
