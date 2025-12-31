package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.MushroomCowMushroomLayer;
import net.minecraft.client.renderer.entity.state.MushroomCowRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.MushroomCow.Variant;

@Environment(EnvType.CLIENT)
public class MushroomCowRenderer extends AgeableMobRenderer<MushroomCow, MushroomCowRenderState, CowModel> {
	private static final Map<Variant, ResourceLocation> TEXTURES = Util.make(Maps.<Variant, ResourceLocation>newHashMap(), hashMap -> {
		hashMap.put(Variant.BROWN, ResourceLocation.withDefaultNamespace("textures/entity/cow/brown_mooshroom.png"));
		hashMap.put(Variant.RED, ResourceLocation.withDefaultNamespace("textures/entity/cow/red_mooshroom.png"));
	});

	public MushroomCowRenderer(EntityRendererProvider.Context context) {
		super(context, new CowModel(context.bakeLayer(ModelLayers.MOOSHROOM)), new CowModel(context.bakeLayer(ModelLayers.MOOSHROOM_BABY)), 0.7F);
		this.addLayer(new MushroomCowMushroomLayer(this, context.getBlockRenderDispatcher()));
	}

	public ResourceLocation getTextureLocation(MushroomCowRenderState mushroomCowRenderState) {
		return (ResourceLocation)TEXTURES.get(mushroomCowRenderState.variant);
	}

	public MushroomCowRenderState createRenderState() {
		return new MushroomCowRenderState();
	}

	public void extractRenderState(MushroomCow mushroomCow, MushroomCowRenderState mushroomCowRenderState, float f) {
		super.extractRenderState(mushroomCow, mushroomCowRenderState, f);
		mushroomCowRenderState.variant = mushroomCow.getVariant();
	}
}
