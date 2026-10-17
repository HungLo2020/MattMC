package net.minecraft.world.waypoints;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;

public interface Waypoint {
	int MAX_RANGE = 60000000;

	public static class Icon {
		public static final Codec<Waypoint.Icon> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ResourceKey.codec(WaypointStyleAssets.ROOT_ID).fieldOf("style").forGetter(icon -> icon.style),
					ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color").forGetter(icon -> icon.color)
				)
				.apply(instance, Waypoint.Icon::new)
		);
		public static final StreamCodec<ByteBuf, Waypoint.Icon> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(WaypointStyleAssets.ROOT_ID),
			icon -> icon.style,
			ByteBufCodecs.optional(ByteBufCodecs.RGB_COLOR),
			icon -> icon.color,
			Waypoint.Icon::new
		);
		public static final Waypoint.Icon NULL = new Waypoint.Icon();
		public ResourceKey<WaypointStyleAsset> style = WaypointStyleAssets.DEFAULT;
		public Optional<Integer> color = Optional.empty();

		public Icon() {
		}

		private Icon(ResourceKey<WaypointStyleAsset> resourceKey, Optional<Integer> optional) {
			this.style = resourceKey;
			this.color = optional;
		}

		public boolean hasData() {
			return this.style != WaypointStyleAssets.DEFAULT || this.color.isPresent();
		}

		public Waypoint.Icon cloneAndAssignStyle(LivingEntity livingEntity) {
			ResourceKey<WaypointStyleAsset> resourceKey = this.getOverrideStyle();
			Optional<Integer> optional = this.color
				.or(
					() -> Optional.ofNullable(livingEntity.getTeam()).map(playerTeam -> playerTeam.getColor().getColor()).map(integer -> integer == 0 ? -13619152 : integer)
				);
			return resourceKey == this.style && optional.isEmpty() ? this : new Waypoint.Icon(resourceKey, optional);
		}

		private ResourceKey<WaypointStyleAsset> getOverrideStyle() {
			return this.style != WaypointStyleAssets.DEFAULT ? this.style : WaypointStyleAssets.DEFAULT;
		}
	}
}
