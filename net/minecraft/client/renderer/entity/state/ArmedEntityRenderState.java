package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public class ArmedEntityRenderState extends LivingEntityRenderState {
	public HumanoidArm mainArm = HumanoidArm.RIGHT;
	public HumanoidModel.ArmPose rightArmPose = HumanoidModel.ArmPose.EMPTY;
	public final ItemStackRenderState rightHandItem = new ItemStackRenderState();
	public HumanoidModel.ArmPose leftArmPose = HumanoidModel.ArmPose.EMPTY;
	public final ItemStackRenderState leftHandItem = new ItemStackRenderState();

	public ItemStackRenderState getMainHandItem() {
		return this.mainArm == HumanoidArm.RIGHT ? this.rightHandItem : this.leftHandItem;
	}

	public static void extractArmedEntityRenderState(LivingEntity livingEntity, ArmedEntityRenderState armedEntityRenderState, ItemModelResolver itemModelResolver) {
		armedEntityRenderState.mainArm = livingEntity.getMainArm();
		itemModelResolver.updateForLiving(
			armedEntityRenderState.rightHandItem, livingEntity.getItemHeldByArm(HumanoidArm.RIGHT), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, livingEntity
		);
		itemModelResolver.updateForLiving(
			armedEntityRenderState.leftHandItem, livingEntity.getItemHeldByArm(HumanoidArm.LEFT), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, livingEntity
		);
	}
}
