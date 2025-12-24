package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.PandaModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.PandaHoldsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Panda.Gene;

@Environment(EnvType.CLIENT)
public class PandaRenderer extends AgeableMobRenderer<Panda, PandaRenderState, PandaModel> {
	private static final Map<Gene, ResourceLocation> TEXTURES = Maps.newEnumMap(
		Map.of(
			Gene.NORMAL,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/panda.png"),
			Gene.LAZY,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/lazy_panda.png"),
			Gene.WORRIED,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/worried_panda.png"),
			Gene.PLAYFUL,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/playful_panda.png"),
			Gene.BROWN,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/brown_panda.png"),
			Gene.WEAK,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/weak_panda.png"),
			Gene.AGGRESSIVE,
			ResourceLocation.withDefaultNamespace("textures/entity/panda/aggressive_panda.png")
		)
	);

	public PandaRenderer(EntityRendererProvider.Context context) {
		super(context, new PandaModel(context.bakeLayer(ModelLayers.PANDA)), new PandaModel(context.bakeLayer(ModelLayers.PANDA_BABY)), 0.9F);
		this.addLayer(new PandaHoldsItemLayer(this));
	}

	public ResourceLocation getTextureLocation(PandaRenderState pandaRenderState) {
		return (ResourceLocation)TEXTURES.getOrDefault(pandaRenderState.variant, (ResourceLocation)TEXTURES.get(Gene.NORMAL));
	}

	public PandaRenderState createRenderState() {
		return new PandaRenderState();
	}

	public void extractRenderState(Panda panda, PandaRenderState pandaRenderState, float f) {
		super.extractRenderState(panda, pandaRenderState, f);
		HoldingEntityRenderState.extractHoldingEntityRenderState(panda, pandaRenderState, this.itemModelResolver);
		pandaRenderState.variant = panda.getVariant();
		pandaRenderState.isUnhappy = panda.getUnhappyCounter() > 0;
		pandaRenderState.isSneezing = panda.isSneezing();
		pandaRenderState.sneezeTime = panda.getSneezeCounter();
		pandaRenderState.isEating = panda.isEating();
		pandaRenderState.isScared = panda.isScared();
		pandaRenderState.isSitting = panda.isSitting();
		pandaRenderState.sitAmount = panda.getSitAmount(f);
		pandaRenderState.lieOnBackAmount = panda.getLieOnBackAmount(f);
		pandaRenderState.rollAmount = panda.isBaby() ? 0.0F : panda.getRollAmount(f);
		pandaRenderState.rollTime = panda.rollCounter > 0 ? panda.rollCounter + f : 0.0F;
	}

	protected void setupRotations(PandaRenderState pandaRenderState, PoseStack poseStack, float f, float g) {
		super.setupRotations(pandaRenderState, poseStack, f, g);
		if (pandaRenderState.rollTime > 0.0F) {
			float h = Mth.frac(pandaRenderState.rollTime);
			int i = Mth.floor(pandaRenderState.rollTime);
			int j = i + 1;
			float k = 7.0F;
			float l = pandaRenderState.isBaby ? 0.3F : 0.8F;
			if (i < 8.0F) {
				float m = 90.0F * i / 7.0F;
				float n = 90.0F * j / 7.0F;
				float o = this.getAngle(m, n, j, h, 8.0F);
				poseStack.translate(0.0F, (l + 0.2F) * (o / 90.0F), 0.0F);
				poseStack.mulPose(Axis.XP.rotationDegrees(-o));
			} else if (i < 16.0F) {
				float m = (i - 8.0F) / 7.0F;
				float n = 90.0F + 90.0F * m;
				float p = 90.0F + 90.0F * (j - 8.0F) / 7.0F;
				float o = this.getAngle(n, p, j, h, 16.0F);
				poseStack.translate(0.0F, l + 0.2F + (l - 0.2F) * (o - 90.0F) / 90.0F, 0.0F);
				poseStack.mulPose(Axis.XP.rotationDegrees(-o));
			} else if (i < 24.0F) {
				float m = (i - 16.0F) / 7.0F;
				float n = 180.0F + 90.0F * m;
				float p = 180.0F + 90.0F * (j - 16.0F) / 7.0F;
				float o = this.getAngle(n, p, j, h, 24.0F);
				poseStack.translate(0.0F, l + l * (270.0F - o) / 90.0F, 0.0F);
				poseStack.mulPose(Axis.XP.rotationDegrees(-o));
			} else if (i < 32) {
				float m = (i - 24.0F) / 7.0F;
				float n = 270.0F + 90.0F * m;
				float p = 270.0F + 90.0F * (j - 24.0F) / 7.0F;
				float o = this.getAngle(n, p, j, h, 32.0F);
				poseStack.translate(0.0F, l * ((360.0F - o) / 90.0F), 0.0F);
				poseStack.mulPose(Axis.XP.rotationDegrees(-o));
			}
		}

		float h = pandaRenderState.sitAmount;
		if (h > 0.0F) {
			poseStack.translate(0.0F, 0.8F * h, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(h, pandaRenderState.xRot, pandaRenderState.xRot + 90.0F)));
			poseStack.translate(0.0F, -1.0F * h, 0.0F);
			if (pandaRenderState.isScared) {
				float q = (float)(Math.cos(pandaRenderState.ageInTicks * 1.25F) * Math.PI * 0.05F);
				poseStack.mulPose(Axis.YP.rotationDegrees(q));
				if (pandaRenderState.isBaby) {
					poseStack.translate(0.0F, 0.8F, 0.55F);
				}
			}
		}

		float q = pandaRenderState.lieOnBackAmount;
		if (q > 0.0F) {
			float r = pandaRenderState.isBaby ? 0.5F : 1.3F;
			poseStack.translate(0.0F, r * q, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(q, pandaRenderState.xRot, pandaRenderState.xRot + 180.0F)));
		}
	}

	private float getAngle(float f, float g, int i, float h, float j) {
		return i < j ? Mth.lerp(h, f, g) : f;
	}
}
