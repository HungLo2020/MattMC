package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.alexsmobs.client.render.state.SealRenderState;
import com.github.alexthe666.alexsmobs.entity.util.Maths;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class ModelSeal extends AdvancedEntityModel<SealRenderState> {
    public final AdvancedModelBox root;
    public final AdvancedModelBox body;
    public final AdvancedModelBox tail;
    public final AdvancedModelBox leftLeg;
    public final AdvancedModelBox rightLeg;
    public final AdvancedModelBox leftArm;
    public final AdvancedModelBox rightArm;
    public final AdvancedModelBox head;
    public final AdvancedModelBox leftWhisker;
    public final AdvancedModelBox rightWhisker;
    
    public ModelSeal() {
        texWidth = 128;
        texHeight = 128;

        root = new AdvancedModelBox(this, "root");
        root.setPos(0.0F, 24.0F, 0.0F);

        body = new AdvancedModelBox(this, "body");
        body.setRotationPoint(0.0F, -3.0F, 0.0F);
        root.addChild(body);
        body.setTextureOffset(0, 0).addBox(-6.5F, -6.0F, -9.0F, 13.0F, 9.0F, 18.0F, 0.0F, false);

        tail = new AdvancedModelBox(this, "tail");
        tail.setRotationPoint(0.0F, 1.0F, 9.0F);
        body.addChild(tail);
        tail.setTextureOffset(0, 28).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 6.0F, 14.0F, 0.0F, false);

        leftLeg = new AdvancedModelBox(this, "leftLeg");
        leftLeg.setRotationPoint(2.0F, -0.2F, 13.4F);
        tail.addChild(leftLeg);
        setRotationAngle(leftLeg, 0.0F, 0.3491F, 0.0F);
        leftLeg.setTextureOffset(45, 0).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 4.0F, 8.0F, 0.0F, false);

        rightLeg = new AdvancedModelBox(this, "rightLeg");
        rightLeg.setRotationPoint(-2.0F, -0.2F, 13.4F);
        tail.addChild(rightLeg);
        setRotationAngle(rightLeg, 0.0F, -0.3491F, 0.0F);
        rightLeg.setTextureOffset(45, 0).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 4.0F, 8.0F, 0.0F, true);

        leftArm = new AdvancedModelBox(this, "leftArm");
        leftArm.setRotationPoint(7.5F, 2.5F, -4.0F);
        body.addChild(leftArm);
        leftArm.setTextureOffset(31, 28).addBox(-1.0F, -0.5F, -2.0F, 8.0F, 1.0F, 5.0F, 0.0F, false);
        leftArm.setTextureOffset(0, 7).addBox(7.0F, 0.5F, -2.0F, 0.0F, 2.0F, 5.0F, 0.0F, false);

        rightArm = new AdvancedModelBox(this, "rightArm");
        rightArm.setRotationPoint(-7.5F, 2.5F, -4.0F);
        body.addChild(rightArm);
        rightArm.setTextureOffset(31, 28).addBox(-7.0F, -0.5F, -2.0F, 8.0F, 1.0F, 5.0F, 0.0F, true);
        rightArm.setTextureOffset(0, 7).addBox(-7.0F, 0.5F, -2.0F, 0.0F, 2.0F, 5.0F, 0.0F, true);

        head = new AdvancedModelBox(this, "head");
        head.setRotationPoint(0.0F, -1.0F, -5.0F);
        body.addChild(head);
        head.setTextureOffset(35, 39).addBox(-3.5F, -3.0F, -9.0F, 7.0F, 6.0F, 10.0F, 0.0F, false);
        head.setTextureOffset(0, 0).addBox(-2.5F, 0.0F, -12.0F, 5.0F, 3.0F, 3.0F, 0.0F, false);

        leftWhisker = new AdvancedModelBox(this, "leftWhisker");
        leftWhisker.setRotationPoint(2.5F, 2.0F, -11.0F);
        head.addChild(leftWhisker);
        setRotationAngle(leftWhisker, 0.0F, -0.2182F, 0.0F);
        leftWhisker.setTextureOffset(0, 7).addBox(0.0F, -2.0F, 0.0F, 2.0F, 3.0F, 0.0F, 0.0F, false);

        rightWhisker = new AdvancedModelBox(this, "rightWhisker");
        rightWhisker.setRotationPoint(-2.5F, 2.0F, -11.0F);
        head.addChild(rightWhisker);
        setRotationAngle(rightWhisker, 0.0F, 0.2182F, 0.0F);
        rightWhisker.setTextureOffset(0, 7).addBox(-2.0F, -2.0F, 0.0F, 2.0F, 3.0F, 0.0F, 0.0F, true);
        this.updateDefaultPose();
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(root, body, tail, leftLeg, rightLeg, head, leftArm, rightArm, leftWhisker, rightWhisker);
    }

    @Override
    public void setupAnim(SealRenderState renderState) {
        this.resetToDefaultPose();
        float walkSpeed = renderState.isBaby ? 0.25F : 0.5F;
        float walkDegree = 1F;
        float swimSpeed = 0.5F;
        float swimDegree = 0.5F;
        // Render state already contains interpolated values
        float baskProgress = renderState.baskProgress;
        float swimAngle = renderState.swimAngle;
        float diggingProgress = renderState.digProgress;
        float bobbingProgress = renderState.bobbingProgress;
        int baskType = renderState.isTearsEasterEgg ? -1 : renderState.entityId % 5;
        progressRotationPrev(body, diggingProgress,  Maths.rad(70), 0, 0, 5F);
        progressRotationPrev(head, diggingProgress,  Maths.rad(10), 0, 0, 5F);
        progressRotationPrev(tail, diggingProgress,  Maths.rad(-10), 0, 0, 5F);
        progressRotationPrev(leftArm, diggingProgress, 0,  Maths.rad(30), 0, 5F);
        progressRotationPrev(rightArm, diggingProgress,  0,  Maths.rad(-30), 0, 5F);
        progressPositionPrev(body, diggingProgress, 0, -12F, 2, 5F);
        progressPositionPrev(leftArm, diggingProgress, -1, 0, -2, 5F);
        progressPositionPrev(rightArm, diggingProgress, 1, 0, -2, 5F);
        this.head.rotationPointZ += (float) (Math.sin(renderState.ageInTicks * 0.7F) * (double) 0.5F * bobbingProgress);
        if(diggingProgress > 0){
            float amount = diggingProgress * 0.2F;
            this.swing(rightArm, 0.6F, 0.85F, true, 1F, -0.1F, renderState.ageInTicks, amount);
            this.swing(leftArm, 0.6F, 0.85F, false, 1F, -0.1F, renderState.ageInTicks, amount);
            this.walk(tail, 0.6F, 0.1F, false, 3F, -0.1F, renderState.ageInTicks, amount);
            this.bob(body, 0.3F, 3F, true, renderState.ageInTicks, amount);
        }
        if (baskProgress > 0 && !renderState.isTearsEasterEgg) {
            this.walk(head, 0.05F, 0.2F, true, 1F, -0.1F, renderState.ageInTicks, 1);
            if (baskType == 0) {
                progressRotationPrev(body, baskProgress, 0, 0, Maths.rad(70), 5F);
                progressRotationPrev(head, baskProgress, 0, Maths.rad(-20), Maths.rad(20), 5F);
                progressRotationPrev(leftArm, baskProgress, 0, 0, Maths.rad(110), 5F);
                progressRotationPrev(rightArm, baskProgress, 0, 0, Maths.rad(-120), 5F);
                progressRotationPrev(tail, baskProgress, 0, Maths.rad(15), Maths.rad(-20), 5F);
                progressRotationPrev(leftLeg, baskProgress, 0, Maths.rad(-15), 0, 5F);
                progressRotationPrev(rightLeg, baskProgress, 0, Maths.rad(35), Maths.rad(30), 5F);
                progressPositionPrev(leftArm, baskProgress, -2, 0, 0, 5F);
                progressPositionPrev(rightArm, baskProgress, 1, 0, 0, 5F);
                progressPositionPrev(head, baskProgress, 0, 0, 1, 5F);
                progressPositionPrev(body, baskProgress, 0, -4, 1, 5F);
                this.flap(rightArm, 0.05F, 0.2F, true, 3F, -0.1F, renderState.ageInTicks, 1);
                this.flap(leftArm, 0.05F, 0.2F, true, 3F, -0.1F, renderState.ageInTicks, 1);
            } else if (baskType == 1) {
                progressRotationPrev(body, baskProgress, 0, 0, Maths.rad(-70), 5F);
                progressRotationPrev(head, baskProgress, 0, Maths.rad(20), Maths.rad(-20), 5F);
                progressRotationPrev(rightArm, baskProgress, 0, 0, Maths.rad(-110), 5F);
                progressRotationPrev(leftArm, baskProgress, 0, 0, Maths.rad(120), 5F);
                progressRotationPrev(tail, baskProgress, 0, Maths.rad(-15), Maths.rad(20), 5F);
                progressRotationPrev(rightLeg, baskProgress, 0, Maths.rad(15), 0, 5F);
                progressRotationPrev(leftLeg, baskProgress, 0, Maths.rad(-35), Maths.rad(-30), 5F);
                progressPositionPrev(rightArm, baskProgress, 2, 0, 0, 5F);
                progressPositionPrev(leftArm, baskProgress, -1, 0, 0, 5F);
                progressPositionPrev(head, baskProgress, 0, 0, 1, 5F);
                progressPositionPrev(body, baskProgress, 0, -4, 0, 5F);
                this.flap(rightArm, 0.05F, 0.2F, false, 3F, -0.1F, renderState.ageInTicks, 1);
                this.flap(leftArm, 0.05F, 0.2F, false, 3F, -0.1F, renderState.ageInTicks, 1);
            } else if (baskType == 2) {
                progressRotationPrev(rightArm, baskProgress, 0, 0, Maths.rad(30), 5F);
                progressRotationPrev(leftArm, baskProgress, 0, 0, Maths.rad(-40), 5F);
                progressRotationPrev(body, baskProgress, 0, 0, Maths.rad(160), 5F);
                progressRotationPrev(tail, baskProgress, Maths.rad(15), 0, 0, 5F);
                progressRotationPrev(head, baskProgress, Maths.rad(-10), Maths.rad(20), Maths.rad(30), 5F);
                progressPositionPrev(body, baskProgress, 0, -4, 0, 5F);
                progressPositionPrev(rightArm, baskProgress, 1, 0, 0, 5F);
                progressPositionPrev(leftArm, baskProgress, -1, 0, 0, 5F);
                this.flap(rightArm, 0.05F, 0.2F, true, 3F, -0.1F, renderState.ageInTicks, 1);
                this.flap(leftArm, 0.05F, 0.2F, false, 3F, -0.1F, renderState.ageInTicks, 1);
            } else if (baskType == 3) {
                progressRotationPrev(body, baskProgress, 0, Maths.rad(20), 0, 5F);
                progressRotationPrev(tail, baskProgress, 0, Maths.rad(25), 0, 5F);
                progressRotationPrev(head, baskProgress, 0, Maths.rad(-20), Maths.rad(25), 5F);
                progressRotationPrev(rightArm, baskProgress, 0, Maths.rad(-20), 0, 5F);
                progressRotationPrev(leftArm, baskProgress, 0, Maths.rad(30), 0, 5F);
                progressRotationPrev(leftLeg, baskProgress, 0, Maths.rad(30), 0, 5F);
                progressRotationPrev(rightLeg, baskProgress, 0, Maths.rad(30), 0, 5F);
                progressPositionPrev(head, baskProgress, 0, -1, 0, 5F);
                this.flap(rightArm, 0.05F, 0.2F, true, 3F, -0.1F, renderState.ageInTicks, 1);
                this.flap(leftArm, 0.05F, 0.2F, false, 3F, -0.1F, renderState.ageInTicks, 1);
            } else if (baskType == 4) {
                progressRotationPrev(body, baskProgress, 0, Maths.rad(-20), 0, 5F);
                progressRotationPrev(tail, baskProgress, 0, Maths.rad(-25), 0, 5F);
                progressRotationPrev(head, baskProgress, 0, Maths.rad(20), Maths.rad(-25), 5F);
                progressRotationPrev(rightArm, baskProgress, 0, Maths.rad(30), 0, 5F);
                progressRotationPrev(leftArm, baskProgress, 0, Maths.rad(-20), 0, 5F);
                progressPositionPrev(head, baskProgress, 0, -1, 0, 5F);
                progressRotationPrev(leftLeg, baskProgress, 0, Maths.rad(-30), 0, 5F);
                progressRotationPrev(rightLeg, baskProgress, 0, Maths.rad(-30), 0, 5F);
                this.flap(rightArm, 0.05F, 0.2F, true, 3F, -0.1F, renderState.ageInTicks, 1);
                this.flap(leftArm, 0.05F, 0.2F, false, 3F, -0.1F, renderState.ageInTicks, 1);
            }
        }
        AdvancedModelBox[] bodyParts = new AdvancedModelBox[]{head, body, tail};
        if (!renderState.isInWater) {
            float f = walkSpeed;
            float f1 = walkDegree * 0.3F;
            this.body.rotationPointY += 1.4F * Math.min(0, (float) (Math.sin(renderState.walkAnimationPos * f) * (double) renderState.walkAnimationSpeed * (double) f1 * 9D - (renderState.walkAnimationSpeed * f1 * 9D)));
            this.body.rotationPointZ += (float) (Math.sin(renderState.walkAnimationPos * f - 1.5F) * (double) renderState.walkAnimationSpeed * (double) f1 * 9D - (renderState.walkAnimationSpeed * f1 * 9D));
            this.head.rotationPointZ += (float) (Math.sin(renderState.walkAnimationPos * f - 2F) * (double) renderState.walkAnimationSpeed * (double) f1 * 2F - (renderState.walkAnimationSpeed * f1 * 2F));
            this.walk(body, walkSpeed, walkDegree * 0.1F, false, 1F, 0.04F, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.walk(head, walkSpeed, walkDegree * 0.1F, true, 1F, 0.04F, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.walk(tail, walkSpeed, walkDegree * 0.15F, true, 1F, 0.06F, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.flap(rightArm, walkSpeed, walkDegree, true, 3F, 0, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.flap(leftArm, walkSpeed, walkDegree, false, 3F, 0, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.swing(rightArm, walkSpeed, walkDegree, false, 2F, -0.2F, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.swing(leftArm, walkSpeed, walkDegree, true, 2F, -0.2F, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
        } else {
            this.body.rotateAngleX += renderState.xRot * Mth.DEG_TO_RAD;
            this.body.rotationPointY += (float) (Math.sin(renderState.walkAnimationPos * swimSpeed) * (double) renderState.walkAnimationSpeed * (double) swimDegree * 9D - (renderState.walkAnimationSpeed * swimDegree * 9D));
            this.chainWave(bodyParts, swimSpeed, swimDegree, -3F, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.flap(rightArm, swimSpeed, swimDegree * 2.5F, true, 3F, 0, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.flap(leftArm, swimSpeed, swimDegree * 2.5F, false, 3F, 0, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.walk(leftLeg, swimSpeed, swimDegree, false, -4F, 0, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
            this.walk(rightLeg, swimSpeed, swimDegree, false, -4F, 0, renderState.walkAnimationPos, renderState.walkAnimationSpeed);
        }
        if(renderState.isTearsEasterEgg && !renderState.isInWater){
            this.swing(head, 0.1F, 0.6F, true, 3F, 0.0F, renderState.ageInTicks, 1);
            this.walk(head, 0.1F, 0.1F, true, 2F, 0.3F, renderState.ageInTicks, 1);
        }
        this.faceTarget(renderState.yRot, renderState.xRot, 1, head);
        float yawAmount = swimAngle / 57.295776F * 0.5F;
        body.rotateAngleZ += yawAmount;

    }

    public void setRotationAngle(AdvancedModelBox advancedModelBox, float x, float y, float z) {
        advancedModelBox.rotateAngleX = x;
        advancedModelBox.rotateAngleY = y;
        advancedModelBox.rotateAngleZ = z;
    }
}