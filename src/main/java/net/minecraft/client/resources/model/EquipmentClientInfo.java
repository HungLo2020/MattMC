package net.minecraft.client.resources.model;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public record EquipmentClientInfo(Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layers) {
	private static final Codec<List<EquipmentClientInfo.Layer>> LAYER_LIST_CODEC = ExtraCodecs.nonEmptyList(EquipmentClientInfo.Layer.CODEC.listOf());
	public static final Codec<EquipmentClientInfo> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.nonEmptyMap(Codec.unboundedMap(EquipmentClientInfo.LayerType.CODEC, LAYER_LIST_CODEC)).fieldOf("layers").forGetter(EquipmentClientInfo::layers)
			)
			.apply(instance, EquipmentClientInfo::new)
	);

	public static EquipmentClientInfo.Builder builder() {
		return new EquipmentClientInfo.Builder();
	}

	public List<EquipmentClientInfo.Layer> getLayers(EquipmentClientInfo.LayerType layerType) {
		return (List<EquipmentClientInfo.Layer>)this.layers.getOrDefault(layerType, List.of());
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layersByType = new EnumMap(EquipmentClientInfo.LayerType.class);

		Builder() {
		}

		public EquipmentClientInfo.Builder addHumanoidLayers(ResourceLocation resourceLocation) {
			return this.addHumanoidLayers(resourceLocation, false);
		}

		public EquipmentClientInfo.Builder addHumanoidLayers(ResourceLocation resourceLocation, boolean bl) {
			this.addLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, EquipmentClientInfo.Layer.leatherDyeable(resourceLocation, bl));
			this.addMainHumanoidLayer(resourceLocation, bl);
			return this;
		}

		public EquipmentClientInfo.Builder addMainHumanoidLayer(ResourceLocation resourceLocation, boolean bl) {
			return this.addLayers(EquipmentClientInfo.LayerType.HUMANOID, EquipmentClientInfo.Layer.leatherDyeable(resourceLocation, bl));
		}

		public EquipmentClientInfo.Builder addLayers(EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer... layers) {
			Collections.addAll((Collection)this.layersByType.computeIfAbsent(layerType, layerTypex -> new ArrayList()), layers);
			return this;
		}

		public EquipmentClientInfo build() {
			return new EquipmentClientInfo(
				(Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>>)(Map<?, ?>)this.layersByType
					.entrySet()
					.stream()
					.collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> List.copyOf((Collection<EquipmentClientInfo.Layer>)entry.getValue())))
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Dyeable(Optional<Integer> colorWhenUndyed) {
		public static final Codec<EquipmentClientInfo.Dyeable> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color_when_undyed").forGetter(EquipmentClientInfo.Dyeable::colorWhenUndyed))
				.apply(instance, EquipmentClientInfo.Dyeable::new)
		);
	}

	@Environment(EnvType.CLIENT)
	public record Layer(ResourceLocation textureId, Optional<EquipmentClientInfo.Dyeable> dyeable, boolean usePlayerTexture) {
		public static final Codec<EquipmentClientInfo.Layer> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ResourceLocation.CODEC.fieldOf("texture").forGetter(EquipmentClientInfo.Layer::textureId),
					EquipmentClientInfo.Dyeable.CODEC.optionalFieldOf("dyeable").forGetter(EquipmentClientInfo.Layer::dyeable),
					Codec.BOOL.optionalFieldOf("use_player_texture", false).forGetter(EquipmentClientInfo.Layer::usePlayerTexture)
				)
				.apply(instance, EquipmentClientInfo.Layer::new)
		);

		public Layer(ResourceLocation resourceLocation) {
			this(resourceLocation, Optional.empty(), false);
		}

		public static EquipmentClientInfo.Layer leatherDyeable(ResourceLocation resourceLocation, boolean bl) {
			return new EquipmentClientInfo.Layer(resourceLocation, bl ? Optional.of(new EquipmentClientInfo.Dyeable(Optional.of(-6265536))) : Optional.empty(), false);
		}

		public static EquipmentClientInfo.Layer onlyIfDyed(ResourceLocation resourceLocation, boolean bl) {
			return new EquipmentClientInfo.Layer(resourceLocation, bl ? Optional.of(new EquipmentClientInfo.Dyeable(Optional.empty())) : Optional.empty(), false);
		}

		public ResourceLocation getTextureLocation(EquipmentClientInfo.LayerType layerType) {
			return this.textureId.withPath(string -> "textures/entity/equipment/" + layerType.getSerializedName() + "/" + string + ".png");
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum LayerType implements StringRepresentable {
		HUMANOID("humanoid"),
		HUMANOID_LEGGINGS("humanoid_leggings"),
		WINGS("wings"),
		WOLF_BODY("wolf_body"),
		HORSE_BODY("horse_body"),
		LLAMA_BODY("llama_body"),
		PIG_SADDLE("pig_saddle"),
		STRIDER_SADDLE("strider_saddle"),
		CAMEL_SADDLE("camel_saddle"),
		HORSE_SADDLE("horse_saddle"),
		DONKEY_SADDLE("donkey_saddle"),
		MULE_SADDLE("mule_saddle"),
		ZOMBIE_HORSE_SADDLE("zombie_horse_saddle"),
		SKELETON_HORSE_SADDLE("skeleton_horse_saddle"),
		HAPPY_GHAST_BODY("happy_ghast_body");

		public static final Codec<EquipmentClientInfo.LayerType> CODEC = StringRepresentable.fromEnum(EquipmentClientInfo.LayerType::values);
		private final String id;

		private LayerType(final String string2) {
			this.id = string2;
		}

		public String getSerializedName() {
			return this.id;
		}

		public String trimAssetPrefix() {
			return "trims/entity/" + this.id;
		}
	}
}
