package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ResultInfo(String name, Schema schema) {
	public static final MapCodec<ResultInfo> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Codec.STRING.fieldOf("name").forGetter(ResultInfo::name), Schema.CODEC.fieldOf("schema").forGetter(ResultInfo::schema))
			.apply(instance, ResultInfo::new)
	);
}
