package net.minecraft.client.renderer.entity;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.CreakingModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.CreakingEyesLayer;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.creaking.Creaking;

@Environment(EnvType.CLIENT)
public class CreakingRenderer<T extends Creaking> extends MobRenderer<T, CreakingRenderState, CreakingModel> {
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/creaking/creaking.png");

	public CreakingRenderer(EntityRendererProvider.Context context) {
		super(context, new CreakingModel(context.bakeLayer(ModelLayers.CREAKING)), 0.6F);
		this.addLayer(new CreakingEyesLayer(this, context.getModelSet()));
	}

	public ResourceLocation getTextureLocation(CreakingRenderState creakingRenderState) {
		return TEXTURE_LOCATION;
	}

	public CreakingRenderState createRenderState() {
		return new CreakingRenderState();
	}

	public void extractRenderState(T creaking, CreakingRenderState creakingRenderState, float f) {
		super.extractRenderState(creaking, creakingRenderState, f);
		creakingRenderState.attackAnimationState.copyFrom(creaking.attackAnimationState);
		creakingRenderState.invulnerabilityAnimationState.copyFrom(creaking.invulnerabilityAnimationState);
		creakingRenderState.deathAnimationState.copyFrom(creaking.deathAnimationState);
		if (creaking.isTearingDown()) {
			creakingRenderState.deathTime = 0.0F;
			creakingRenderState.hasRedOverlay = false;
			creakingRenderState.eyesGlowing = creaking.hasGlowingEyes();
		} else {
			creakingRenderState.eyesGlowing = creaking.isActive();
		}

		creakingRenderState.canMove = creaking.canMove();
	}
}
