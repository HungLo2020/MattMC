package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.AbstractEquineModel;
import net.minecraft.client.model.EquineSaddleModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

@Environment(EnvType.CLIENT)
public class UndeadHorseRenderer extends AbstractHorseRenderer<AbstractHorse, EquineRenderState, AbstractEquineModel<EquineRenderState>> {
	private final ResourceLocation texture;

	public UndeadHorseRenderer(EntityRendererProvider.Context context, UndeadHorseRenderer.Type type) {
		super(context, new HorseModel(context.bakeLayer(type.model)), new HorseModel(context.bakeLayer(type.babyModel)));
		this.texture = type.texture;
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				type.saddleLayer,
				equineRenderState -> equineRenderState.saddle,
				new EquineSaddleModel(context.bakeLayer(type.saddleModel)),
				new EquineSaddleModel(context.bakeLayer(type.babySaddleModel))
			)
		);
	}

	public ResourceLocation getTextureLocation(EquineRenderState equineRenderState) {
		return this.texture;
	}

	public EquineRenderState createRenderState() {
		return new EquineRenderState();
	}

	@Environment(EnvType.CLIENT)
	public static enum Type {
		SKELETON(
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_skeleton.png"),
			ModelLayers.SKELETON_HORSE,
			ModelLayers.SKELETON_HORSE_BABY,
			EquipmentClientInfo.LayerType.SKELETON_HORSE_SADDLE,
			ModelLayers.SKELETON_HORSE_SADDLE,
			ModelLayers.SKELETON_HORSE_BABY_SADDLE
		),
		ZOMBIE(
			ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_zombie.png"),
			ModelLayers.ZOMBIE_HORSE,
			ModelLayers.ZOMBIE_HORSE_BABY,
			EquipmentClientInfo.LayerType.ZOMBIE_HORSE_SADDLE,
			ModelLayers.ZOMBIE_HORSE_SADDLE,
			ModelLayers.ZOMBIE_HORSE_BABY_SADDLE
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
