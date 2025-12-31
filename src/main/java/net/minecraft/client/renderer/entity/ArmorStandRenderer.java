package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.ArmorStandArmorModel;
import net.minecraft.client.model.ArmorStandModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ArmorStandRenderer extends LivingEntityRenderer<ArmorStand, ArmorStandRenderState, ArmorStandArmorModel> {
	public static final ResourceLocation DEFAULT_SKIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/armorstand/wood.png");
	private final ArmorStandArmorModel bigModel = this.getModel();
	private final ArmorStandArmorModel smallModel;

	public ArmorStandRenderer(EntityRendererProvider.Context context) {
		super(context, new ArmorStandModel(context.bakeLayer(ModelLayers.ARMOR_STAND)), 0.0F);
		this.smallModel = new ArmorStandModel(context.bakeLayer(ModelLayers.ARMOR_STAND_SMALL));
		this.addLayer(
			new HumanoidArmorLayer<>(
				this,
				ArmorModelSet.bake(ModelLayers.ARMOR_STAND_ARMOR, context.getModelSet(), ArmorStandArmorModel::new),
				ArmorModelSet.bake(ModelLayers.ARMOR_STAND_SMALL_ARMOR, context.getModelSet(), ArmorStandArmorModel::new),
				context.getEquipmentRenderer()
			)
		);
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
	}

	public ResourceLocation getTextureLocation(ArmorStandRenderState armorStandRenderState) {
		return DEFAULT_SKIN_LOCATION;
	}

	public ArmorStandRenderState createRenderState() {
		return new ArmorStandRenderState();
	}

	public void extractRenderState(ArmorStand armorStand, ArmorStandRenderState armorStandRenderState, float f) {
		super.extractRenderState(armorStand, armorStandRenderState, f);
		HumanoidMobRenderer.extractHumanoidRenderState(armorStand, armorStandRenderState, f, this.itemModelResolver);
		armorStandRenderState.yRot = Mth.rotLerp(f, armorStand.yRotO, armorStand.getYRot());
		armorStandRenderState.isMarker = armorStand.isMarker();
		armorStandRenderState.isSmall = armorStand.isSmall();
		armorStandRenderState.showArms = armorStand.showArms();
		armorStandRenderState.showBasePlate = armorStand.showBasePlate();
		armorStandRenderState.bodyPose = armorStand.getBodyPose();
		armorStandRenderState.headPose = armorStand.getHeadPose();
		armorStandRenderState.leftArmPose = armorStand.getLeftArmPose();
		armorStandRenderState.rightArmPose = armorStand.getRightArmPose();
		armorStandRenderState.leftLegPose = armorStand.getLeftLegPose();
		armorStandRenderState.rightLegPose = armorStand.getRightLegPose();
		armorStandRenderState.wiggle = (float)(armorStand.level().getGameTime() - armorStand.lastHit) + f;
	}

	public void submit(
		ArmorStandRenderState armorStandRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		this.model = armorStandRenderState.isSmall ? this.smallModel : this.bigModel;
		super.submit(armorStandRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected void setupRotations(ArmorStandRenderState armorStandRenderState, PoseStack poseStack, float f, float g) {
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - f));
		if (armorStandRenderState.wiggle < 5.0F) {
			poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(armorStandRenderState.wiggle / 1.5F * (float) Math.PI) * 3.0F));
		}
	}

	protected boolean shouldShowName(ArmorStand armorStand, double d) {
		return armorStand.isCustomNameVisible();
	}

	@Nullable
	protected RenderType getRenderType(ArmorStandRenderState armorStandRenderState, boolean bl, boolean bl2, boolean bl3) {
		if (!armorStandRenderState.isMarker) {
			return super.getRenderType(armorStandRenderState, bl, bl2, bl3);
		} else {
			ResourceLocation resourceLocation = this.getTextureLocation(armorStandRenderState);
			if (bl2) {
				return RenderType.entityTranslucent(resourceLocation, false);
			} else {
				return bl ? RenderType.entityCutoutNoCull(resourceLocation, false) : null;
			}
		}
	}
}
