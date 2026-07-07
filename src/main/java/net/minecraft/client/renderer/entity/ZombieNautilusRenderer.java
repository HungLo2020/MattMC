package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.nautilus.NautilusArmorModel;
import net.minecraft.client.model.animal.nautilus.NautilusModel;
import net.minecraft.client.model.animal.nautilus.NautilusSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.nautilus.ZombieNautilusCoralModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.NautilusRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant.ModelType;

public class ZombieNautilusRenderer extends MobRenderer<ZombieNautilus, NautilusRenderState, NautilusModel> {
	private final Map<ModelType, NautilusModel> models;

	public ZombieNautilusRenderer(EntityRendererProvider.Context context) {
		super(context, new NautilusModel(context.bakeLayer(ModelLayers.ZOMBIE_NAUTILUS)), 0.7F);
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				EquipmentClientInfo.LayerType.NAUTILUS_BODY,
				state -> state.bodyArmorItem,
				new NautilusArmorModel(context.bakeLayer(ModelLayers.NAUTILUS_ARMOR)),
				null
			)
		);
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				EquipmentClientInfo.LayerType.NAUTILUS_SADDLE,
				state -> state.saddle,
				new NautilusSaddleModel(context.bakeLayer(ModelLayers.NAUTILUS_SADDLE)),
				null
			)
		);
		this.models = bakeModels(context);
	}

	private static Map<ModelType, NautilusModel> bakeModels(EntityRendererProvider.Context context) {
		return Maps.newEnumMap(
			Map.of(
				ModelType.NORMAL,
				new NautilusModel(context.bakeLayer(ModelLayers.ZOMBIE_NAUTILUS)),
				ModelType.WARM,
				new ZombieNautilusCoralModel(context.bakeLayer(ModelLayers.ZOMBIE_NAUTILUS_CORAL))
			)
		);
	}

	@Override
	public void submit(NautilusRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (state.variant != null) {
			this.model = this.models.get(state.variant.modelAndTexture().model());
			super.submit(state, poseStack, submitNodeCollector, camera);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(NautilusRenderState state) {
		return state.variant == null ? MissingTextureAtlasSprite.getLocation() : state.variant.modelAndTexture().asset().texturePath();
	}

	@Override
	public NautilusRenderState createRenderState() {
		return new NautilusRenderState();
	}

	@Override
	public void extractRenderState(ZombieNautilus entity, NautilusRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.saddle = entity.getItemBySlot(EquipmentSlot.SADDLE).copy();
		state.bodyArmorItem = entity.getBodyArmorItem().copy();
		state.variant = entity.getVariant().value();
	}
}
