package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Markings;

@Environment(EnvType.CLIENT)
public class HorseMarkingLayer extends RenderLayer<HorseRenderState, HorseModel> {
	private static final ResourceLocation INVISIBLE_TEXTURE = ResourceLocation.withDefaultNamespace("invisible");
	private static final Map<Markings, ResourceLocation> LOCATION_BY_MARKINGS = Maps.newEnumMap(
		Map.of(
			Markings.NONE,
			INVISIBLE_TEXTURE,
			Markings.WHITE,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_white.png"),
			Markings.WHITE_FIELD,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_whitefield.png"),
			Markings.WHITE_DOTS,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_whitedots.png"),
			Markings.BLACK_DOTS,
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_markings_blackdots.png")
		)
	);

	public HorseMarkingLayer(RenderLayerParent<HorseRenderState, HorseModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, HorseRenderState horseRenderState, float f, float g) {
		ResourceLocation resourceLocation = (ResourceLocation)LOCATION_BY_MARKINGS.get(horseRenderState.markings);
		if (resourceLocation != INVISIBLE_TEXTURE && !horseRenderState.isInvisible) {
			submitNodeCollector.order(1)
				.submitModel(
					this.getParentModel(),
					horseRenderState,
					poseStack,
					RenderType.entityTranslucent(resourceLocation),
					i,
					LivingEntityRenderer.getOverlayCoords(horseRenderState, 0.0F),
					-1,
					null,
					horseRenderState.outlineColor,
					null
				);
		}
	}
}
