package com.seibel.distanthorizons.fabric.mixins.server;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Mixin to invoke Fabric ServerLifecycleEvents and ServerWorldEvents.
 * This is needed because the standard Fabric API mixins are not included in this build.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerLifecycle {
	
	@Shadow
	public abstract ServerLevel getLevel(ResourceKey<Level> resourceKey);
	
	/**
	 * Invoke SERVER_STARTING event when the server starts running.
	 * Injects at the head of runServer() method.
	 */
	@Inject(method = "runServer", at = @At("HEAD"))
	private void onServerStarting(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(server);
	}
	
	/**
	 * Invoke SERVER_STARTED event after the server has completed setup.
	 * Injects after the server starts ticking.
	 */
	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V", ordinal = 0))
	private void onServerStarted(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(server);
	}
	
	/**
	 * Invoke SERVER_STOPPING event when the server begins shutdown.
	 */
	@Inject(method = "stopServer", at = @At("HEAD"))
	private void onServerStopping(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(server);
	}
	
	/**
	 * Invoke SERVER_STOPPED event after the server has stopped.
	 */
	@Inject(method = "stopServer", at = @At("TAIL"))
	private void onServerStopped(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
	}
	
	/**
	 * Invoke ServerWorldEvents.LOAD when a world is loaded (overworld).
	 * Injects after the overworld is added to the levels map.
	 */
	@Inject(method = "createLevels", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0, shift = At.Shift.AFTER))
	private void onOverworldLoad(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		if (overworld != null) {
			ServerWorldEvents.LOAD.invoker().onWorldLoad(server, overworld);
		}
	}
	
	/**
	 * Invoke ServerWorldEvents.LOAD when a world is loaded (nether/end/custom dimensions).
	 * Injects after each dimension is added to the levels map.
	 */
	@Inject(method = "createLevels", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1, shift = At.Shift.AFTER))
	private void onDimensionLoad(CallbackInfo ci, @Local(ordinal = 1) ServerLevel serverLevel2) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		if (serverLevel2 != null) {
			ServerWorldEvents.LOAD.invoker().onWorldLoad(server, serverLevel2);
		}
	}
}
