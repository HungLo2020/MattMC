package net.matt.quantize.entities.layer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.matt.quantize.utils.ResourceIdentifier;

import org.jetbrains.annotations.NotNull;
import net.matt.quantize.Quantize;
//import org.violetmoon.quark.base.Quark;
import net.matt.quantize.entities.models.CrabModel;
import net.matt.quantize.entities.mobs.Crab;

public class CrabMoldLayer extends RenderLayer<Crab, CrabModel> {

	private static final ResourceLocation MOLD_LAYER = new ResourceIdentifier("textures/model/entity/crab/mold_layer.png");

	public CrabMoldLayer(RenderLayerParent<Crab, CrabModel> renderer) {
		super(renderer);
	}

	@Override
	public void render(@NotNull PoseStack matrix, @NotNull MultiBufferSource buffer, int light, Crab crab, float limbAngle, float limbDistance, float tickDelta, float customAngle, float headYaw, float headPitch) {
		if(crab.getVariant() >= Crab.COLORS)
			renderColoredCutoutModel(getParentModel(), MOLD_LAYER, matrix, buffer, light, crab, 1F, 1F, 1F);
	}

}
