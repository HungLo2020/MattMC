package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.nautilus.NautilusArmorModel;
import net.minecraft.client.model.animal.nautilus.NautilusModel;
import net.minecraft.client.model.animal.nautilus.NautilusSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.NautilusRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;

public class NautilusRenderer<T extends AbstractNautilus> extends AgeableMobRenderer<T, NautilusRenderState, NautilusModel> {
	private static final ResourceLocation NAUTILUS_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/nautilus/nautilus.png");
	private static final ResourceLocation NAUTILUS_BABY_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/nautilus/nautilus_baby.png");

	public NautilusRenderer(EntityRendererProvider.Context context) {
		super(context, new NautilusModel(context.bakeLayer(ModelLayers.NAUTILUS)), new NautilusModel(context.bakeLayer(ModelLayers.NAUTILUS_BABY)), 0.7F);
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
	}

	@Override
	public ResourceLocation getTextureLocation(NautilusRenderState state) {
		return state.isBaby ? NAUTILUS_BABY_LOCATION : NAUTILUS_LOCATION;
	}

	@Override
	public NautilusRenderState createRenderState() {
		return new NautilusRenderState();
	}

	@Override
	public void extractRenderState(T entity, NautilusRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.saddle = entity.getItemBySlot(EquipmentSlot.SADDLE).copy();
		state.bodyArmorItem = entity.getBodyArmorItem().copy();
	}
}
