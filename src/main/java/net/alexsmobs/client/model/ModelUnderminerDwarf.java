package net.alexsmobs.client.model;

import net.alexsmobs.client.render.UnderminerRenderState;
import net.citadel.client.model.AdvancedEntityModel;
import net.citadel.client.model.AdvancedModelBox;
import net.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.Mth;

public class ModelUnderminerDwarf extends AdvancedEntityModel<UnderminerRenderState> {
    private final AdvancedModelBox body;
    private final AdvancedModelBox head;
    private final AdvancedModelBox helmet;
    private final AdvancedModelBox beard;
    private final AdvancedModelBox leftArm;
    private final AdvancedModelBox rightArm;
    private final AdvancedModelBox leftLeg;
    private final AdvancedModelBox rightLeg;

    public ModelUnderminerDwarf() {
        texWidth = 128;
        texHeight = 128;

        body = new AdvancedModelBox(this, "body");
        body.setRotationPoint(0.0F, 12.0F, 0.0F);
        body.setTextureOffset(0, 36).addBox(-5.0F, -10.0F, -3.0F, 10.0F, 11.0F, 6.0F, 0.0F, false);

        head = new AdvancedModelBox(this, "head");
        head.setRotationPoint(0.0F, -10.02F, 0.0F);
        body.addChild(head);
        head.setTextureOffset(30, 24).addBox(-5.0F, -8.0F, -5.0F, 10.0F, 8.0F, 9.0F, 0.0F, false);
        head.setTextureOffset(0, 15).addBox(-5.0F, -8.0F, -5.0F, 10.0F, 8.0F, 9.0F, 0.1F, false);

        helmet = new AdvancedModelBox(this, "helmet");
        helmet.setRotationPoint(0.0F, 0.0F, 0.0F);
        head.addChild(helmet);
        helmet.setTextureOffset(0, 0).addBox(-6.0F, -10.0F, -5.5F, 12.0F, 4.0F, 10.0F, 0.1F, false);

        beard = new AdvancedModelBox(this, "beard");
        beard.setRotationPoint(0.0F, 0.1F, -4.1F);
        head.addChild(beard);
        beard.setTextureOffset(0, 54).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 9.0F, 2.0F, 0.0F, false);

        leftArm = new AdvancedModelBox(this, "leftArm");
        leftArm.setRotationPoint(7.0F, -9.0F, 0.0F);
        body.addChild(leftArm);
        leftArm.setTextureOffset(45, 0).addBox(-2.0F, -1.0F, -2.5F, 4.0F, 13.0F, 5.0F, 0.0F, false);

        rightArm = new AdvancedModelBox(this, "rightArm");
        rightArm.setRotationPoint(-7.0F, -9.0F, 0.0F);
        body.addChild(rightArm);
        rightArm.setTextureOffset(45, 0).addBox(-2.0F, -1.0F, -2.5F, 4.0F, 13.0F, 5.0F, 0.0F, true);

        leftLeg = new AdvancedModelBox(this, "leftLeg");
        leftLeg.setRotationPoint(2.0F, 2.0F, 0.0F);
        body.addChild(leftLeg);
        leftLeg.setTextureOffset(33, 42).addBox(-2.0F, -1.0F, -3.0F, 5.0F, 11.0F, 6.0F, 0.0F, false);

        rightLeg = new AdvancedModelBox(this, "rightLeg");
        rightLeg.setRotationPoint(-2.0F, 2.0F, 0.0F);
        body.addChild(rightLeg);
        rightLeg.setTextureOffset(33, 42).addBox(-3.0F, -1.0F, -3.0F, 5.0F, 11.0F, 6.0F, 0.0F, true);
        this.updateDefaultPose();
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(body);
    }

    @Override
    public void setupAnim(UnderminerRenderState state) {
        this.resetToDefaultPose();
        float limbSwing = state.walkAnimationPos;
        float limbSwingAmount = state.walkAnimationSpeed;
        float ageInTicks = state.ageInTicks;
        float netHeadYaw = state.yRot;
        float headPitch = state.xRot;
        
        // Basic head rotation
        this.head.rotateAngleY = netHeadYaw * Mth.DEG_TO_RAD * 0.2F;
        this.head.rotateAngleX = headPitch * Mth.DEG_TO_RAD * 0.2F;
        
        // Basic arm swing
        this.rightArm.rotateAngleX = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 2.0F * limbSwingAmount * 0.5F;
        this.leftArm.rotateAngleX = Mth.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F;
        this.rightArm.rotateAngleZ = 0.0F;
        this.leftArm.rotateAngleZ = 0.0F;
        
        // Basic leg swing
        this.rightLeg.rotateAngleX = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.leftLeg.rotateAngleX = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount;
        this.rightLeg.rotateAngleY = 0.0F;
        this.leftLeg.rotateAngleY = 0.0F;
        this.rightLeg.rotateAngleZ = 0.0F;
        this.leftLeg.rotateAngleZ = 0.0F;
        
        // Idle animations
        this.rightArm.rotateAngleZ += 1.0F * (Mth.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
        this.rightArm.rotateAngleX += 1.0F * Mth.sin(ageInTicks * 0.067F) * 0.05F;
        this.leftArm.rotateAngleZ += -1.0F * (Mth.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
        this.leftArm.rotateAngleX += -1.0F * Mth.sin(ageInTicks * 0.067F) * 0.05F;
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(body, head, beard, helmet, rightLeg, leftLeg, rightArm, leftArm);
    }

    public AdvancedModelBox getHead() {
        return this.head;
    }

    public void setRotationAngle(AdvancedModelBox AdvancedModelBox, float x, float y, float z) {
        AdvancedModelBox.rotateAngleX = x;
        AdvancedModelBox.rotateAngleY = y;
        AdvancedModelBox.rotateAngleZ = z;
    }
}