package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs.LateBoundIdMapper;

@Environment(EnvType.CLIENT)
public class ConditionalItemModelProperties {
	public static final LateBoundIdMapper<ResourceLocation, MapCodec<? extends ConditionalItemModelProperty>> ID_MAPPER = new LateBoundIdMapper();
	public static final MapCodec<ConditionalItemModelProperty> MAP_CODEC = ID_MAPPER.codec(ResourceLocation.CODEC)
		.dispatchMap("property", ConditionalItemModelProperty::type, mapCodec -> mapCodec);

	public static void bootstrap() {
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("custom_model_data"), CustomModelDataProperty.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("using_item"), IsUsingItem.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("broken"), Broken.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("damaged"), Damaged.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("fishing_rod/cast"), FishingRodCast.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("has_component"), HasComponent.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("bundle/has_selected_item"), BundleHasSelectedItem.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("selected"), IsSelected.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("carried"), IsCarried.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("extended_view"), ExtendedView.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("keybind_down"), IsKeybindDown.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("view_entity"), IsViewEntity.MAP_CODEC);
		ID_MAPPER.put(ResourceLocation.withDefaultNamespace("component"), ComponentMatches.MAP_CODEC);
	}
}
