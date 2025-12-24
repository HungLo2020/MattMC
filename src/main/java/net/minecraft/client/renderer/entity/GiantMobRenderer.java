package net.minecraft.client.renderer.entity;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.GiantZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Giant;

@Environment(EnvType.CLIENT)
public class GiantMobRenderer extends MobRenderer<Giant, ZombieRenderState, HumanoidModel<ZombieRenderState>> {
	private static final ResourceLocation ZOMBIE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

	public GiantMobRenderer(EntityRendererProvider.Context context, float f) {
		super(context, new GiantZombieModel(context.bakeLayer(ModelLayers.GIANT)), 0.5F * f);
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(
			new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.GIANT_ARMOR, context.getModelSet(), GiantZombieModel::new), context.getEquipmentRenderer())
		);
	}

	public ResourceLocation getTextureLocation(ZombieRenderState zombieRenderState) {
		return ZOMBIE_LOCATION;
	}

	public ZombieRenderState createRenderState() {
		return new ZombieRenderState();
	}

	public void extractRenderState(Giant giant, ZombieRenderState zombieRenderState, float f) {
		super.extractRenderState(giant, zombieRenderState, f);
		HumanoidMobRenderer.extractHumanoidRenderState(giant, zombieRenderState, f, this.itemModelResolver);
	}
}
