package net.minecraft.world.entity.animal;

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

public record ChickenVariant(ModelAndTexture<ChickenVariant.ModelType> modelAndTexture, SpawnPrioritySelectors spawnConditions)
	implements PriorityProvider<SpawnContext, SpawnCondition> {
	public static final Codec<ChickenVariant> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.CHICKEN_VARIANT, false, net.minecraft.resources.NativeDataProjections::chicken, net.minecraft.resources.NativeDataProjections::chickenOut);
	public static final Codec<ChickenVariant> NETWORK_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.CHICKEN_VARIANT, true, net.minecraft.resources.NativeDataProjections::chicken, net.minecraft.resources.NativeDataProjections::chickenOut);
	public static final Codec<Holder<ChickenVariant>> CODEC = RegistryFixedCodec.create(Registries.CHICKEN_VARIANT);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<ChickenVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.CHICKEN_VARIANT);

	@Override
	public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
		return this.spawnConditions.selectors();
	}

	public static enum ModelType implements StringRepresentable {
		NORMAL("normal"),
		COLD("cold");

		private final String name;

		private ModelType(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
