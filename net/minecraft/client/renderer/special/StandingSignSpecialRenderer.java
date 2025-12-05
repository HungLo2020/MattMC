package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class StandingSignSpecialRenderer implements NoDataSpecialModelRenderer {
	private final MaterialSet materials;
	private final Model.Simple model;
	private final Material material;

	public StandingSignSpecialRenderer(MaterialSet materialSet, Model.Simple simple, Material material) {
		this.materials = materialSet;
		this.model = simple;
		this.material = material;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		SignRenderer.submitSpecial(this.materials, poseStack, submitNodeCollector, i, j, this.model, this.material);
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		SignRenderer.applyInHandTransforms(poseStack);
		this.model.root().getExtentsForGui(poseStack, set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(WoodType woodType, Optional<ResourceLocation> texture) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<StandingSignSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					WoodType.CODEC.fieldOf("wood_type").forGetter(StandingSignSpecialRenderer.Unbaked::woodType),
					ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(StandingSignSpecialRenderer.Unbaked::texture)
				)
				.apply(instance, StandingSignSpecialRenderer.Unbaked::new)
		);

		public Unbaked(WoodType woodType) {
			this(woodType, Optional.empty());
		}

		@Override
		public MapCodec<StandingSignSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			Model.Simple simple = SignRenderer.createSignModel(bakingContext.entityModelSet(), this.woodType, true);
			Material material = (Material)this.texture.map(Sheets.SIGN_MAPPER::apply).orElseGet(() -> Sheets.getSignMaterial(this.woodType));
			return new StandingSignSpecialRenderer(bakingContext.materials(), simple, material);
		}
	}
}
