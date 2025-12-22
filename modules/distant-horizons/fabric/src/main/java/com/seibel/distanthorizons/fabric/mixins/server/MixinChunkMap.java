package com.seibel.distanthorizons.fabric.mixins.server;

import com.seibel.distanthorizons.common.commonMixins.MixinChunkMapCommon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap
{
	@Unique
	private static final Logger LOGGER = LoggerFactory.getLogger("DH-ChunkEvents");
	
	@Unique
	private static final String CHUNK_SERIALIZER_WRITE
			= "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write(" +
			"Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)" +
			"Lnet/minecraft/nbt/CompoundTag;";
	
	@Shadow
	@Final
	ServerLevel level;
	
	@Shadow
	protected abstract boolean save(ChunkAccess chunkAccess);
	
	// firing at INVOKE causes issues with C2ME and is probably unnecessary since we
	// don't need the chunk(s) before MC has finished saving them
	@Inject(method = "save", at = @At(value = "RETURN", target = CHUNK_SERIALIZER_WRITE))
	private void onChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> ci)
	{ MixinChunkMapCommon.onChunkSave(this.level, chunk, ci); }
	
	/**
	 * Fire CHUNK_UNLOAD event when chunks are being saved.
	 * Injects into save() method directly - this is called when chunks are unloaded.
	 * This is needed because the standard Fabric API mixins are not included in this build.
	 */
	@Inject(method = "save", at = @At("HEAD"))
	private void onChunkUnload(ChunkAccess chunkAccess, CallbackInfoReturnable<Boolean> ci) {
		if (chunkAccess instanceof LevelChunk levelChunk) {
			LOGGER.info("[DH-CHUNK-UNLOAD] Chunk unload event: {}", levelChunk.getPos());
			
			try {
				ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(this.level, levelChunk);
			} catch (Exception e) {
				LOGGER.error("[DH-CHUNK-UNLOAD] Error invoking chunk unload event for {}: {}", levelChunk.getPos(), e.getMessage(), e);
			}
		}
	}
	
}
