package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.ColdChickenModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.ChickenVariant;
import net.minecraft.world.entity.animal.ChickenVariant.ModelType;

@Environment(EnvType.CLIENT)
public class ChickenRenderer extends MobRenderer<Chicken, ChickenRenderState, ChickenModel> {
	private final Map<ModelType, AdultAndBabyModelPair<ChickenModel>> models;

	public ChickenRenderer(EntityRendererProvider.Context context) {
		super(context, new ChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), 0.3F);
		this.models = bakeModels(context);
	}

	private static Map<ModelType, AdultAndBabyModelPair<ChickenModel>> bakeModels(EntityRendererProvider.Context context) {
		return Maps.newEnumMap(
			Map.of(
				ModelType.NORMAL,
				new AdultAndBabyModelPair<>(new ChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), new ChickenModel(context.bakeLayer(ModelLayers.CHICKEN_BABY))),
				ModelType.COLD,
				new AdultAndBabyModelPair<>(
					new ColdChickenModel(context.bakeLayer(ModelLayers.COLD_CHICKEN)), new ColdChickenModel(context.bakeLayer(ModelLayers.COLD_CHICKEN_BABY))
				)
			)
		);
	}

	public void submit(ChickenRenderState chickenRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (chickenRenderState.variant != null) {
			this.model = ((AdultAndBabyModelPair<ChickenModel>)this.models.get(chickenRenderState.variant.modelAndTexture().model())).getModel(chickenRenderState.isBaby);
			super.submit(chickenRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	public ResourceLocation getTextureLocation(ChickenRenderState chickenRenderState) {
		return chickenRenderState.variant == null ? MissingTextureAtlasSprite.getLocation() : chickenRenderState.variant.modelAndTexture().asset().texturePath();
	}

	public ChickenRenderState createRenderState() {
		return new ChickenRenderState();
	}

	public void extractRenderState(Chicken chicken, ChickenRenderState chickenRenderState, float f) {
		super.extractRenderState(chicken, chickenRenderState, f);
		chickenRenderState.flap = Mth.lerp(f, chicken.oFlap, chicken.flap);
		chickenRenderState.flapSpeed = Mth.lerp(f, chicken.oFlapSpeed, chicken.flapSpeed);
		chickenRenderState.variant = (ChickenVariant)chicken.getVariant().value();
	}
}
