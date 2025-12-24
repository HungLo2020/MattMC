package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;

public record FunctionCallback(ResourceLocation functionId) implements TimerCallback<MinecraftServer> {
	public static final MapCodec<FunctionCallback> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ResourceLocation.CODEC.fieldOf("Name").forGetter(FunctionCallback::functionId)).apply(instance, FunctionCallback::new)
	);

	public void handle(MinecraftServer minecraftServer, TimerQueue<MinecraftServer> timerQueue, long l) {
		ServerFunctionManager serverFunctionManager = minecraftServer.getFunctions();
		serverFunctionManager.get(this.functionId)
			.ifPresent(commandFunction -> serverFunctionManager.execute(commandFunction, serverFunctionManager.getGameLoopSender()));
	}

	@Override
	public MapCodec<FunctionCallback> codec() {
		return CODEC;
	}
}
