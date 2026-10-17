package net.minecraft.world.entity.animal.wolf;

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

public record WolfVariant(WolfVariant.AssetInfo assetInfo, SpawnPrioritySelectors spawnConditions) implements PriorityProvider<SpawnContext, SpawnCondition> {
	public static final Codec<WolfVariant> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.WOLF_VARIANT, false, net.minecraft.resources.NativeDataProjections::wolf, net.minecraft.resources.NativeDataProjections::wolfOut);
	public static final Codec<WolfVariant> NETWORK_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.WOLF_VARIANT, true, net.minecraft.resources.NativeDataProjections::wolf, net.minecraft.resources.NativeDataProjections::wolfOut);
	public static final Codec<Holder<WolfVariant>> CODEC = RegistryFixedCodec.create(Registries.WOLF_VARIANT);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<WolfVariant>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.WOLF_VARIANT);

	@Override
	public List<PriorityProvider.Selector<SpawnContext, SpawnCondition>> selectors() {
		return this.spawnConditions.selectors();
	}

	public record AssetInfo(ClientAsset.ResourceTexture wild, ClientAsset.ResourceTexture tame, ClientAsset.ResourceTexture angry) {
	}
}
