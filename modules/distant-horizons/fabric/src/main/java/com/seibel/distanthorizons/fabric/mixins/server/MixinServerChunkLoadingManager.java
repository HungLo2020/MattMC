package com.seibel.distanthorizons.fabric.mixins.server;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to invoke Fabric ServerChunkEvents.CHUNK_UNLOAD.
 * This is needed because the standard Fabric API mixins are not included in this build.
 */
@Mixin(ChunkMap.class)
public class MixinServerChunkLoadingManager {
	private static final Logger LOGGER = LogManager.getLogger();
	
	@Shadow
	@Final
	ServerLevel level;
	
	/**
	 * Invoke CHUNK_UNLOAD event when a chunk is saved (unloaded).
	 * Injects after the chunk is serialized to disk.
	 */
	@Inject(method = "save", at = @At(value = "RETURN"))
	private void onChunkUnload(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		if (chunk instanceof LevelChunk levelChunk) {
			LOGGER.info("[DH-CHUNK-UNLOAD] Chunk unloading at {}, invoking ServerChunkEvents.CHUNK_UNLOAD", chunk.getPos());
			ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(this.level, levelChunk);
		}
	}
}
