package net.minecraft.client.color.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs.LateBoundIdMapper;

@Environment(EnvType.CLIENT)
public class ItemTintSources {
	public static final LateBoundIdMapper<ResourceLocation, MapCodec<? extends ItemTintSource>> ID_MAPPER = new LateBoundIdMapper();
	public static final Codec<ItemTintSource> CODEC = ID_MAPPER.codec(ResourceLocation.CODEC).dispatch(ItemTintSource::type, mapCodec -> mapCodec);

	public static void bootstrap() {
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("custom_model_data"), CustomModelDataSource.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("constant"), Constant.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("dye"), Dye.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("grass"), GrassColorSource.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("firework"), Firework.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("potion"), Potion.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("map_color"), MapColor.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("team"), TeamColor.MAP_CODEC);
	}
}
