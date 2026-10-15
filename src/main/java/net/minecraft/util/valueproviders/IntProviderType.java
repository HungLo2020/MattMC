package net.minecraft.util.valueproviders;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public interface IntProviderType<P extends IntProvider> {
	IntProviderType<ConstantInt> CONSTANT = register("constant");
	IntProviderType<UniformInt> UNIFORM = register("uniform");
	IntProviderType<BiasedToBottomInt> BIASED_TO_BOTTOM = register("biased_to_bottom");
	IntProviderType<ClampedInt> CLAMPED = register("clamped");
	IntProviderType<WeightedListInt> WEIGHTED_LIST = register("weighted_list");
	IntProviderType<ClampedNormalInt> CLAMPED_NORMAL = register("clamped_normal");

	MapCodec<P> codec();

	/** A registered type; its fields are the native codec's case of that name. */
	@SuppressWarnings("unchecked")
	static <P extends IntProvider> IntProviderType<P> register(String string) {
		MapCodec<P> mapCodec = (MapCodec<P>)(MapCodec<?>)IntProvider.CODEC.dispatchCase("minecraft:" + string);
		return Registry.register(BuiltInRegistries.INT_PROVIDER_TYPE, string, () -> mapCodec);
	}
}
