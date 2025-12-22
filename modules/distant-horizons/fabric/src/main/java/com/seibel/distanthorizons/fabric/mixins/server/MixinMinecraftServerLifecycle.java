package com.seibel.distanthorizons.fabric.mixins.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to invoke Fabric ServerLifecycleEvents.
 * This is needed because the standard Fabric API mixins are not included in this build.
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServerLifecycle {
	
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
}
