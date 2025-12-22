package com.seibel.distanthorizons.fabric.mixins.server;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Mixin to invoke Fabric ServerChunkEvents.CHUNK_UNLOAD when chunks are being unloaded.
 * This is needed because the standard Fabric API mixins are not included in this build.
 * 
 * Based on Fabric API's ServerChunkLoadingManagerMixin (which uses method_60440 in Yarn mappings).
 * In Mojang mappings, this corresponds to the processUnloads() method.
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMapUnload {
	private static final Logger LOGGER = LoggerFactory.getLogger("DH-ChunkEvents");
	
	@Shadow
	@Final
	ServerLevel level;
	
	@Shadow
	protected abstract boolean save(ChunkAccess chunkAccess);
	
	/**
	 * Fire CHUNK_UNLOAD event when chunks are being saved/unloaded.
	 * Injects into processUnloads() just before save() is called.
	 */
	@Inject(method = "processUnloads", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z"))
	private void onChunkUnload(BooleanSupplier booleanSupplier, CallbackInfo ci, @Local ChunkHolder chunkHolder) {
		if (chunkHolder != null) {
			LevelChunk chunk = chunkHolder.getChunkToSend();
			if (chunk != null) {
				LOGGER.info("[DH-CHUNK-UNLOAD] Chunk unload event: {}", chunk.getPos());
				
				try {
					ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(this.level, chunk);
				} catch (Exception e) {
					LOGGER.error("[DH-CHUNK-UNLOAD] Error invoking chunk unload event for {}: {}", chunk.getPos(), e.getMessage(), e);
				}
			}
		}
	}
}
