package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ParamInfo(String name, Schema schema, boolean required) {
	public static final MapCodec<ParamInfo> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(ParamInfo::name),
				Schema.CODEC.fieldOf("schema").forGetter(ParamInfo::schema),
				Codec.BOOL.fieldOf("required").forGetter(ParamInfo::required)
			)
			.apply(instance, ParamInfo::new)
	);

	public ParamInfo(String string, Schema schema) {
		this(string, schema, true);
	}
}
