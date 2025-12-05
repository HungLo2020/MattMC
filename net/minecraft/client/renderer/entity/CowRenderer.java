package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.CowVariant;
import net.minecraft.world.entity.animal.CowVariant.ModelType;

@Environment(EnvType.CLIENT)
public class CowRenderer extends MobRenderer<Cow, CowRenderState, CowModel> {
	private final Map<ModelType, AdultAndBabyModelPair<CowModel>> models;

	public CowRenderer(EntityRendererProvider.Context context) {
		super(context, new CowModel(context.bakeLayer(ModelLayers.COW)), 0.7F);
		this.models = bakeModels(context);
	}

	private static Map<ModelType, AdultAndBabyModelPair<CowModel>> bakeModels(EntityRendererProvider.Context context) {
		return Maps.newEnumMap(
			Map.of(
				ModelType.NORMAL,
				new AdultAndBabyModelPair<>(new CowModel(context.bakeLayer(ModelLayers.COW)), new CowModel(context.bakeLayer(ModelLayers.COW_BABY))),
				ModelType.WARM,
				new AdultAndBabyModelPair<>(new CowModel(context.bakeLayer(ModelLayers.WARM_COW)), new CowModel(context.bakeLayer(ModelLayers.WARM_COW_BABY))),
				ModelType.COLD,
				new AdultAndBabyModelPair<>(new CowModel(context.bakeLayer(ModelLayers.COLD_COW)), new CowModel(context.bakeLayer(ModelLayers.COLD_COW_BABY)))
			)
		);
	}

	public ResourceLocation getTextureLocation(CowRenderState cowRenderState) {
		return cowRenderState.variant == null ? MissingTextureAtlasSprite.getLocation() : cowRenderState.variant.modelAndTexture().asset().texturePath();
	}

	public CowRenderState createRenderState() {
		return new CowRenderState();
	}

	public void extractRenderState(Cow cow, CowRenderState cowRenderState, float f) {
		super.extractRenderState(cow, cowRenderState, f);
		cowRenderState.variant = (CowVariant)cow.getVariant().value();
	}

	public void submit(CowRenderState cowRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (cowRenderState.variant != null) {
			this.model = ((AdultAndBabyModelPair<CowModel>)this.models.get(cowRenderState.variant.modelAndTexture().model())).getModel(cowRenderState.isBaby);
			super.submit(cowRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}
}
