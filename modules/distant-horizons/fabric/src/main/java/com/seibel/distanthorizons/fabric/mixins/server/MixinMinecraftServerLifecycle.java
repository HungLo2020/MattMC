package com.seibel.distanthorizons.fabric.mixins.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
	
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	static {
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ########## MixinMinecraftServerLifecycle CLASS LOADED ##########");
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] This mixin will invoke Fabric server lifecycle events");
	}
	
	@Shadow
	public abstract ServerLevel getLevel(ResourceKey<Level> resourceKey);
	
	/**
	 * Invoke SERVER_STARTING event when the server starts running.
	 * Injects before initServer() is called in runServer() method.
	 */
	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"))
	private void onServerStarting(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ========== MIXIN: onServerStarting CALLED ==========");
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] Server: " + server);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] Is Dedicated: " + server.isDedicatedServer());
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] About to invoke SERVER_STARTING event...");
		ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(server);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] SERVER_STARTING event invoked successfully");
	}
	
	/**
	 * Invoke SERVER_STARTED event after the server has completed setup.
	 * Injects after buildServerStatus() is called in runServer() method.
	 */
	@Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;", shift = At.Shift.AFTER))
	private void onServerStarted(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ========== MIXIN: onServerStarted CALLED ==========");
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] Server: " + server);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] Is Dedicated: " + server.isDedicatedServer());
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] Thread: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] About to invoke SERVER_STARTED event...");
		ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(server);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] SERVER_STARTED event invoked successfully");
	}
	
	/**
	 * Invoke SERVER_STOPPING event when the server begins shutdown.
	 */
	@Inject(method = "stopServer", at = @At("HEAD"))
	private void onServerStopping(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ========== MIXIN: onServerStopping CALLED ==========");
		ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(server);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] SERVER_STOPPING event invoked successfully");
	}
	
	/**
	 * Invoke SERVER_STOPPED event after the server has stopped.
	 */
	@Inject(method = "stopServer", at = @At("TAIL"))
	private void onServerStopped(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ========== MIXIN: onServerStopped CALLED ==========");
		ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] SERVER_STOPPED event invoked successfully");
	}
	
	/**
	 * Invoke ServerWorldEvents.LOAD when a world is loaded.
	 * Uses WrapOperation to intercept Map.put() calls when worlds are added.
	 */
	@WrapOperation(method = "createLevels", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private <K, V> V onWorldLoad(Map<K, V> levels, K registryKey, V serverLevel, Operation<V> original) {
		final V result = original.call(levels, registryKey, serverLevel);
		MinecraftServer server = (MinecraftServer) (Object) this;
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ========== MIXIN: onWorldLoad CALLED ==========");
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] World being loaded: " + serverLevel);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] About to invoke ServerWorldEvents.LOAD...");
		ServerWorldEvents.LOAD.invoker().onWorldLoad(server, (ServerLevel) serverLevel);
		//LOGGER.info("[DH-MIXIN-LIFECYCLE] ServerWorldEvents.LOAD invoked successfully");
		return result;
	}
}
