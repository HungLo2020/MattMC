package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
	@Shadow
	private ClientLevel level;
	
	@Inject(method = "handleLogin", at = @At("RETURN"))
	void onHandleLoginEnd(CallbackInfo ci) 
	{ 
		ClientApi.INSTANCE.onClientOnlyConnected(); 
		ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(this.level, true));
	}
	
	@Inject(method = "close", at = @At("HEAD"))
	void onCleanupStart(CallbackInfo ci)
	{
		ClientApi.INSTANCE.onClientOnlyDisconnected();
	}
	
		@Inject(method = "enableChunkLight", at = @At("TAIL"))
	void onEnableChunkLight(LevelChunk chunk, int x, int z, CallbackInfo ci)
	{
		IClientLevelWrapper clientLevel = ClientLevelWrapper.getWrapper((ClientLevel) chunk.getLevel());
		SharedApi.INSTANCE.chunkLoadEvent(new ChunkWrapper(chunk, clientLevel), clientLevel);
	}

		
}
