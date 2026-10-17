package net.minecraft.util.valueproviders;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public interface FloatProviderType<P extends FloatProvider> {
	FloatProviderType<ConstantFloat> CONSTANT = register("constant");
	FloatProviderType<UniformFloat> UNIFORM = register("uniform");
	FloatProviderType<ClampedNormalFloat> CLAMPED_NORMAL = register("clamped_normal");
	FloatProviderType<TrapezoidFloat> TRAPEZOID = register("trapezoid");

	MapCodec<P> codec();

	/** A registered type; its fields are the native codec's case of that name. */
	@SuppressWarnings("unchecked")
	static <P extends FloatProvider> FloatProviderType<P> register(String string) {
		MapCodec<P> mapCodec = (MapCodec<P>)(MapCodec<?>)FloatProvider.CODEC.dispatchCase("minecraft:" + string);
		return Registry.register(BuiltInRegistries.FLOAT_PROVIDER_TYPE, string, () -> mapCodec);
	}
}
