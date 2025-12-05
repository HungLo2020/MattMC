package net.minecraft.client.renderer.block.model;

import com.mojang.math.Quadrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public record Variant(ResourceLocation modelLocation, Variant.SimpleModelState modelState) implements BlockModelPart.Unbaked {
	public static final MapCodec<Variant> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("model").forGetter(Variant::modelLocation), Variant.SimpleModelState.MAP_CODEC.forGetter(Variant::modelState)
			)
			.apply(instance, Variant::new)
	);
	public static final Codec<Variant> CODEC = MAP_CODEC.codec();

	public Variant(ResourceLocation resourceLocation) {
		this(resourceLocation, Variant.SimpleModelState.DEFAULT);
	}

	public Variant withXRot(Quadrant quadrant) {
		return this.withState(this.modelState.withX(quadrant));
	}

	public Variant withYRot(Quadrant quadrant) {
		return this.withState(this.modelState.withY(quadrant));
	}

	public Variant withUvLock(boolean bl) {
		return this.withState(this.modelState.withUvLock(bl));
	}

	public Variant withModel(ResourceLocation resourceLocation) {
		return new Variant(resourceLocation, this.modelState);
	}

	public Variant withState(Variant.SimpleModelState simpleModelState) {
		return new Variant(this.modelLocation, simpleModelState);
	}

	public Variant with(VariantMutator variantMutator) {
		return (Variant)variantMutator.apply(this);
	}

	@Override
	public BlockModelPart bake(ModelBaker modelBaker) {
		return SimpleModelWrapper.bake(modelBaker, this.modelLocation, this.modelState.asModelState());
	}

	@Override
	public void resolveDependencies(ResolvableModel.Resolver resolver) {
		resolver.markDependency(this.modelLocation);
	}

	@Environment(EnvType.CLIENT)
	public record SimpleModelState(Quadrant x, Quadrant y, boolean uvLock) {
		public static final MapCodec<Variant.SimpleModelState> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Quadrant.CODEC.optionalFieldOf("x", Quadrant.R0).forGetter(Variant.SimpleModelState::x),
					Quadrant.CODEC.optionalFieldOf("y", Quadrant.R0).forGetter(Variant.SimpleModelState::y),
					Codec.BOOL.optionalFieldOf("uvlock", false).forGetter(Variant.SimpleModelState::uvLock)
				)
				.apply(instance, Variant.SimpleModelState::new)
		);
		public static final Variant.SimpleModelState DEFAULT = new Variant.SimpleModelState(Quadrant.R0, Quadrant.R0, false);

		public ModelState asModelState() {
			BlockModelRotation blockModelRotation = BlockModelRotation.by(this.x, this.y);
			return (ModelState)(this.uvLock ? blockModelRotation.withUvLock() : blockModelRotation);
		}

		public Variant.SimpleModelState withX(Quadrant quadrant) {
			return new Variant.SimpleModelState(quadrant, this.y, this.uvLock);
		}

		public Variant.SimpleModelState withY(Quadrant quadrant) {
			return new Variant.SimpleModelState(this.x, quadrant, this.uvLock);
		}

		public Variant.SimpleModelState withUvLock(boolean bl) {
			return new Variant.SimpleModelState(this.x, this.y, bl);
		}
	}
}
