package net.matt.quantize.entities.render;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import net.matt.quantize.modules.entities.ModelHandler;
import net.matt.quantize.entities.layer.CrabMoldLayer;
import net.matt.quantize.entities.models.CrabModel;
import net.matt.quantize.entities.mobs.Crab;

public class CrabRenderer extends MobRenderer<Crab, CrabModel> {

	private static final ResourceLocation[] TEXTURES = new ResourceLocation[] {
			new ResourceIdentifier("textures/model/entity/crab/red.png"),
			new ResourceIdentifier("textures/model/entity/crab/blue.png"),
			new ResourceIdentifier("textures/model/entity/crab/green.png")
	};

	public CrabRenderer(EntityRendererProvider.Context context) {
		super(context, ModelHandler.model(ModelHandler.crab), 0.4F);
		addLayer(new CrabMoldLayer(this));
	}

	@NotNull
	@Override
	public ResourceLocation getTextureLocation(@NotNull Crab entity) {
		return TEXTURES[entity.getVariant() % TEXTURES.length];
	}
}
