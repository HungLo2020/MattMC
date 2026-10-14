package net.minecraft.world.entity.animal.nautilus;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnCondition;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;

public record ZombieNautilusVariant(ModelAndTexture<ZombieNautilusVariant.ModelType> modelAndTexture, SpawnPrioritySelectors spawnConditions)
	implements PriorityProvider<SpawnContext, SpawnCondition> {
	public static final Codec<ZombieNautilusVariant> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.ZOMBIE_NAUTILUS_VARIANT, false, net.minecraft.resources.NativeDataProjections::zombieNautilus, net.minecraft.resources.NativeDataProjections::zombieNautilusOut);
	public static final Codec<ZombieNautilusVariant> NETWORK_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.ZOMBIE_NAUTILUS_VARIANT, true, net.minecraft.resources.NativeDataProjections::zombieNautilus, net.minecraft.resources.NativeDataProjections::zombieNautilusOut);
	public static final Codec<Holder<ZombieNautilusVariant>> CODEC = RegistryFixedCodec.create(Registries.ZOMBIE_NAUTILUS_VARIANT);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<ZombieNautilusVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(
		Registries.ZOMBIE_NAUTILUS_VARIANT
	);

	@Override
	public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
		return this.spawnConditions.selectors();
	}

	public static enum ModelType implements StringRepresentable {
		NORMAL("normal"),
		WARM("warm");

		private final String name;

		private ModelType(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
