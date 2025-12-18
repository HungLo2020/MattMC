package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.world.level.chunk.LevelChunk;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener
{
	@Unique
	private static final Logger DH_DEBUG_LOGGER = LogManager.getLogger("DH-DEBUG");
	
	@Shadow
	private ClientLevel level;
	
	@Inject(method = "handleLogin", at = @At("RETURN"))
	void onHandleLoginEnd(CallbackInfo ci) 
	{ 
		DH_DEBUG_LOGGER.info("[DH-DEBUG] MixinClientPacketListener.handleLogin FIRED - Starting world connection");
		try {
			DH_DEBUG_LOGGER.info("[DH-DEBUG] Calling ClientApi.onClientOnlyConnected()");
			ClientApi.INSTANCE.onClientOnlyConnected();
			DH_DEBUG_LOGGER.info("[DH-DEBUG] onClientOnlyConnected() completed successfully");
			
			DH_DEBUG_LOGGER.info("[DH-DEBUG] Calling ClientApi.clientLevelLoadEvent() with level: {}", this.level);
			ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(this.level, true));
			DH_DEBUG_LOGGER.info("[DH-DEBUG] clientLevelLoadEvent() completed successfully");
		} catch (Exception e) {
			DH_DEBUG_LOGGER.error("[DH-DEBUG] Exception in handleLogin mixin: ", e);
		}
	}
	
	@Inject(method = "close", at = @At("HEAD"))
	void onCleanupStart(CallbackInfo ci)
	{
		DH_DEBUG_LOGGER.info("[DH-DEBUG] MixinClientPacketListener.close FIRED - Disconnecting");
		ClientApi.INSTANCE.onClientOnlyDisconnected();
	}
	
		@Inject(method = "enableChunkLight", at = @At("TAIL"))
	void onEnableChunkLight(LevelChunk chunk, int x, int z, CallbackInfo ci)
	{
		DH_DEBUG_LOGGER.debug("[DH-DEBUG] MixinClientPacketListener.enableChunkLight FIRED - chunk at [{}, {}]", x, z);
		IClientLevelWrapper clientLevel = ClientLevelWrapper.getWrapper((ClientLevel) chunk.getLevel());
		SharedApi.INSTANCE.chunkLoadEvent(new ChunkWrapper(chunk, clientLevel), clientLevel);
	}

		
}
