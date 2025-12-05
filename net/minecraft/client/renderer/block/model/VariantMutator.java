package net.minecraft.client.renderer.block.model;

import com.mojang.math.Quadrant;
import java.util.function.UnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface VariantMutator extends UnaryOperator<Variant> {
	VariantMutator.VariantProperty<Quadrant> X_ROT = Variant::withXRot;
	VariantMutator.VariantProperty<Quadrant> Y_ROT = Variant::withYRot;
	VariantMutator.VariantProperty<ResourceLocation> MODEL = Variant::withModel;
	VariantMutator.VariantProperty<Boolean> UV_LOCK = Variant::withUvLock;

	default VariantMutator then(VariantMutator variantMutator) {
		return variant -> (Variant)variantMutator.apply((Variant)this.apply(variant));
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface VariantProperty<T> {
		Variant apply(Variant variant, T object);

		default VariantMutator withValue(T object) {
			return variant -> this.apply(variant, object);
		}
	}
}
