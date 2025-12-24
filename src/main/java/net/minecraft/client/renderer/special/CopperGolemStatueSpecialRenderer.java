package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.CopperGolemStatueModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemOxidationLevels;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.CopperGolemStatueBlock.Pose;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class CopperGolemStatueSpecialRenderer implements NoDataSpecialModelRenderer {
	private final CopperGolemStatueModel model;
	private final ResourceLocation texture;
	static final Map<Pose, ModelLayerLocation> MODELS = Map.of(
		Pose.STANDING,
		ModelLayers.COPPER_GOLEM,
		Pose.SITTING,
		ModelLayers.COPPER_GOLEM_SITTING,
		Pose.STAR,
		ModelLayers.COPPER_GOLEM_STAR,
		Pose.RUNNING,
		ModelLayers.COPPER_GOLEM_RUNNING
	);

	public CopperGolemStatueSpecialRenderer(CopperGolemStatueModel copperGolemStatueModel, ResourceLocation resourceLocation) {
		this.model = copperGolemStatueModel;
		this.texture = resourceLocation;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		this.positionModel(poseStack);
		submitNodeCollector.submitModel(this.model, Direction.SOUTH, poseStack, RenderType.entityCutoutNoCull(this.texture), i, j, -1, null, k, null);
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		this.positionModel(poseStack);
		this.model.root().getExtentsForGui(poseStack, set);
	}

	private void positionModel(PoseStack poseStack) {
		poseStack.translate(0.5F, 1.5F, 0.5F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(ResourceLocation texture, Pose pose) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<CopperGolemStatueSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ResourceLocation.CODEC.fieldOf("texture").forGetter(CopperGolemStatueSpecialRenderer.Unbaked::texture),
					Pose.CODEC.fieldOf("pose").forGetter(CopperGolemStatueSpecialRenderer.Unbaked::pose)
				)
				.apply(instance, CopperGolemStatueSpecialRenderer.Unbaked::new)
		);

		public Unbaked(WeatherState weatherState, Pose pose) {
			this(CopperGolemOxidationLevels.getOxidationLevel(weatherState).texture(), pose);
		}

		@Override
		public MapCodec<CopperGolemStatueSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			CopperGolemStatueModel copperGolemStatueModel = new CopperGolemStatueModel(
				bakingContext.entityModelSet().bakeLayer((ModelLayerLocation)CopperGolemStatueSpecialRenderer.MODELS.get(this.pose))
			);
			return new CopperGolemStatueSpecialRenderer(copperGolemStatueModel, this.texture);
		}
	}
}
