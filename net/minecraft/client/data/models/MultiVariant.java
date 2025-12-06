package net.minecraft.client.data.models;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.VariantMutator;
import net.minecraft.client.resources.model.WeightedVariants;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

@Environment(EnvType.CLIENT)
public record MultiVariant(WeightedList<Variant> variants) {
	public MultiVariant(WeightedList<Variant> variants) {
		if (variants.isEmpty()) {
			throw new IllegalArgumentException("Variant list must contain at least one element");
		} else {
			this.variants = variants;
		}
	}

	public MultiVariant with(VariantMutator variantMutator) {
		return new MultiVariant(this.variants.map(variantMutator));
	}

	public BlockStateModel.Unbaked toUnbaked() {
		List<Weighted<Variant>> list = this.variants.unwrap();
		return (BlockStateModel.Unbaked)(list.size() == 1
			? new SingleVariant.Unbaked((Variant)((Weighted)list.getFirst()).value())
			: new WeightedVariants.Unbaked(this.variants.map(SingleVariant.Unbaked::new)));
	}
}
