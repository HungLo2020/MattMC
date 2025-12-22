package com.seibel.distanthorizons.fabric.mixins.server;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to invoke Fabric ServerChunkEvents.CHUNK_LOAD and CHUNK_GENERATE.
 * This is needed because the standard Fabric API mixins are not included in this build.
 */
@Mixin(ChunkMap.class)
public class MixinChunkGenerating {
	private static final Logger LOGGER = LogManager.getLogger();
	
	@Shadow
	@Final
	ServerLevel level;
	
	/**
	 * Invoke CHUNK_LOAD and CHUNK_GENERATE events when a chunk is generated.
	 * Injects when getChunk is called with FULL status (chunk is fully loaded).
	 */
	@Inject(method = "getChunk", at = @At(value = "RETURN"))
	private void onChunkLoad(int x, int z, ChunkStatus status, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
		// Only process FULL status chunks
		if (status != ChunkStatus.FULL) {
			return;
		}
		
		ChunkAccess chunk = cir.getReturnValue();
		if (chunk instanceof LevelChunk levelChunk) {
			LOGGER.info("[DH-CHUNK-LOAD] Chunk loading at {}, invoking ServerChunkEvents.CHUNK_LOAD", chunk.getPos());
			ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(this.level, levelChunk);
			
			// Check if this is a newly generated chunk (not loaded from disk)
			// ImposterProtoChunk means it was loaded from disk, otherwise it's newly generated
			if (!(chunk instanceof ImposterProtoChunk)) {
				LOGGER.info("[DH-CHUNK-GENERATE] Chunk generating at {}, invoking ServerChunkEvents.CHUNK_GENERATE", chunk.getPos());
				ServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(this.level, levelChunk);
			}
		}
	}
}
