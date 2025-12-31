package net.minecraft.world.level.timers;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;

public class TimerCallbacks<C> {
	public static final TimerCallbacks<MinecraftServer> SERVER_CALLBACKS = new TimerCallbacks<MinecraftServer>()
		.register(ResourceLocation.withDefaultNamespace("function"), FunctionCallback.CODEC)
		.register(ResourceLocation.withDefaultNamespace("function_tag"), FunctionTagCallback.CODEC);
	private final ExtraCodecs.LateBoundIdMapper<ResourceLocation, MapCodec<? extends TimerCallback<C>>> idMapper = new ExtraCodecs.LateBoundIdMapper<>();
	private final Codec<TimerCallback<C>> codec = this.idMapper.codec(ResourceLocation.CODEC).dispatch("Type", TimerCallback::codec, Function.identity());

	public TimerCallbacks<C> register(ResourceLocation resourceLocation, MapCodec<? extends TimerCallback<C>> mapCodec) {
		this.idMapper.put(resourceLocation, mapCodec);
		return this;
	}

	public Codec<TimerCallback<C>> codec() {
		return this.codec;
	}
}
