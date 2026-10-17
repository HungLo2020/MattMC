package net.minecraft.world.entity.animal.frog;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnCondition;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;

public record FrogVariant(ClientAsset.ResourceTexture assetInfo, SpawnPrioritySelectors spawnConditions)
	implements PriorityProvider<SpawnContext, SpawnCondition> {
	public static final Codec<FrogVariant> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.FROG_VARIANT, false, net.minecraft.resources.NativeDataProjections::frog, net.minecraft.resources.NativeDataProjections::frogOut);
	public static final Codec<FrogVariant> NETWORK_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.FROG_VARIANT, true, net.minecraft.resources.NativeDataProjections::frog, net.minecraft.resources.NativeDataProjections::frogOut);
	public static final Codec<Holder<FrogVariant>> CODEC = RegistryFixedCodec.create(Registries.FROG_VARIANT);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<FrogVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.FROG_VARIANT);

	@Override
	public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
		return this.spawnConditions.selectors();
	}
}
