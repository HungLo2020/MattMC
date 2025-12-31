package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record MethodInfo(String description, Optional<ParamInfo> params, Optional<ResultInfo> result) {
	public static final Codec<Optional<ParamInfo>> PARAMS_CODEC = ParamInfo.CODEC
		.codec()
		.listOf()
		.xmap(list -> list.stream().findAny(), optional -> (List)optional.map(List::of).orElse(List.of()));
	public static final MapCodec<MethodInfo> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.STRING.fieldOf("description").forGetter(MethodInfo::description),
				PARAMS_CODEC.fieldOf("params").forGetter(MethodInfo::params),
				ResultInfo.CODEC.codec().optionalFieldOf("result").forGetter(MethodInfo::result)
			)
			.apply(instance, MethodInfo::new)
	);

	public MethodInfo(String string, @Nullable ParamInfo paramInfo, @Nullable ResultInfo resultInfo) {
		this(string, Optional.ofNullable(paramInfo), Optional.ofNullable(resultInfo));
	}

	public MethodInfo.Named named(ResourceLocation resourceLocation) {
		return new MethodInfo.Named(resourceLocation, this);
	}

	public record Named(ResourceLocation name, MethodInfo contents) {
		public static final Codec<MethodInfo.Named> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ResourceLocation.CODEC.fieldOf("name").forGetter(MethodInfo.Named::name), MethodInfo.MAP_CODEC.forGetter(MethodInfo.Named::contents)
				)
				.apply(instance, MethodInfo.Named::new)
		);
	}
}
