package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.DonkeyModel;
import net.minecraft.client.model.EquineSaddleModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.DonkeyRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;

@Environment(EnvType.CLIENT)
public class DonkeyRenderer<T extends AbstractChestedHorse> extends AbstractHorseRenderer<T, DonkeyRenderState, DonkeyModel> {
	private final ResourceLocation texture;

	public DonkeyRenderer(EntityRendererProvider.Context context, DonkeyRenderer.Type type) {
		super(context, new DonkeyModel(context.bakeLayer(type.model)), new DonkeyModel(context.bakeLayer(type.babyModel)));
		this.texture = type.texture;
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				type.saddleLayer,
				donkeyRenderState -> donkeyRenderState.saddle,
				new EquineSaddleModel(context.bakeLayer(type.saddleModel)),
				new EquineSaddleModel(context.bakeLayer(type.babySaddleModel))
			)
		);
	}

	public ResourceLocation getTextureLocation(DonkeyRenderState donkeyRenderState) {
		return this.texture;
	}

	public DonkeyRenderState createRenderState() {
		return new DonkeyRenderState();
	}

	public void extractRenderState(T abstractChestedHorse, DonkeyRenderState donkeyRenderState, float f) {
		super.extractRenderState(abstractChestedHorse, donkeyRenderState, f);
		donkeyRenderState.hasChest = abstractChestedHorse.hasChest();
	}

	@Environment(EnvType.CLIENT)
	public static enum Type {
		DONKEY(
			ResourceLocation.withDefaultNamespace("textures/entity/horse/donkey.png"),
			ModelLayers.DONKEY,
			ModelLayers.DONKEY_BABY,
			EquipmentClientInfo.LayerType.DONKEY_SADDLE,
			ModelLayers.DONKEY_SADDLE,
			ModelLayers.DONKEY_BABY_SADDLE
		),
		MULE(
			ResourceLocation.withDefaultNamespace("textures/entity/horse/mule.png"),
			ModelLayers.MULE,
			ModelLayers.MULE_BABY,
			EquipmentClientInfo.LayerType.MULE_SADDLE,
			ModelLayers.MULE_SADDLE,
			ModelLayers.MULE_BABY_SADDLE
		);

		final ResourceLocation texture;
		final ModelLayerLocation model;
		final ModelLayerLocation babyModel;
		final EquipmentClientInfo.LayerType saddleLayer;
		final ModelLayerLocation saddleModel;
		final ModelLayerLocation babySaddleModel;

		private Type(
			final ResourceLocation resourceLocation,
			final ModelLayerLocation modelLayerLocation,
			final ModelLayerLocation modelLayerLocation2,
			final EquipmentClientInfo.LayerType layerType,
			final ModelLayerLocation modelLayerLocation3,
			final ModelLayerLocation modelLayerLocation4
		) {
			this.texture = resourceLocation;
			this.model = modelLayerLocation;
			this.babyModel = modelLayerLocation2;
			this.saddleLayer = layerType;
			this.saddleModel = modelLayerLocation3;
			this.babySaddleModel = modelLayerLocation4;
		}
	}
}
