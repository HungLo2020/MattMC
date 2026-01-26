package com.github.alexthe666.alexsmobs.client.model;

import com.github.alexthe666.alexsmobs.client.render.RhinocerosRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityRhinoceros;
import com.github.alexthe666.alexsmobs.entity.util.Maths;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.ModelAnimator;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

public class ModelRhinoceros extends AdvancedEntityModel<RhinocerosRenderState> {
    private final AdvancedModelBox root;
    private final AdvancedModelBox body;
    private final AdvancedModelBox leftLeg;
    private final AdvancedModelBox rightLeg;
    private final AdvancedModelBox chest;
    private final AdvancedModelBox head;
    private final AdvancedModelBox horns;
    private final AdvancedModelBox leftEar;
    private final AdvancedModelBox rightEar;
    private final AdvancedModelBox leftArm;
    private final AdvancedModelBox rightArm;
    private final ModelAnimator animator;

    public ModelRhinoceros() {
        texWidth = 128;
        texHeight = 128;

        root = new AdvancedModelBox(this, "root");
        root.setRotationPoint(0.0F, 24.0F, 0.0F);


        body = new AdvancedModelBox(this, "body");
        body.setRotationPoint(0.0F, -19.0F, 4.0F);
        root.addChild(body);
        body.setTextureOffset(0, 44).addBox(-9.0F, -10.0F, -6.0F, 18.0F, 20.0F, 21.0F, 0.0F, false);

        leftLeg = new AdvancedModelBox(this, "leftLeg");
        leftLeg.setRotationPoint(6.0F, 9.0F, 12.0F);
        body.addChild(leftLeg);
        leftLeg.setTextureOffset(70, 77).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 11.0F, 9.0F, 0.0F, false);

        rightLeg = new AdvancedModelBox(this, "rightLeg");
        rightLeg.setRotationPoint(-6.0F, 9.0F, 12.0F);
        body.addChild(rightLeg);
        rightLeg.setTextureOffset(70, 77).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 11.0F, 9.0F, 0.0F, true);

        chest = new AdvancedModelBox(this, "chest");
        chest.setRotationPoint(0.0F, -4.0F, -10.0F);
        body.addChild(chest);
        chest.setTextureOffset(0, 0).addBox(-11.0F, -10.0F, -14.0F, 22.0F, 23.0F, 20.0F, 0.0F, false);

        head = new AdvancedModelBox(this, "head");
        head.setRotationPoint(0.0F, 3.0F, -14.0F);
        chest.addChild(head);
        setRotationAngle(head, 0.3927F, 0.0F, 0.0F);
        head.setTextureOffset(76, 35).addBox(-6.0F, -6.0F, -8.0F, 12.0F, 14.0F, 9.0F, 0.0F, false);
        head.setTextureOffset(65, 0).addBox(-4.0F, 0.0F, -18.0F, 8.0F, 8.0F, 10.0F, 0.0F, false);

        horns = new AdvancedModelBox(this, "horns");
        horns.setRotationPoint(0.0F, 0, 0.0F);
        head.addChild(horns);
        horns.setTextureOffset(0, 0).addBox(-2.0F, -12.0F, -18.0F, 4.0F, 12.0F, 5.0F, 0.0F, false);
        horns.setTextureOffset(0, 44).addBox(-2.0F, -4.0F, -13.0F, 4.0F, 4.0F, 4.0F, 0.0F, false);

        leftEar = new AdvancedModelBox(this, "leftEar");
        leftEar.setRotationPoint(6.0F, -5.0F, -4.0F);
        head.addChild(leftEar);
        setRotationAngle(leftEar, -0.2443F, -0.2443F, 0.7679F);
        leftEar.setTextureOffset(0, 53).addBox(-1.0F, -5.0F, 0.0F, 3.0F, 6.0F, 1.0F, 0.0F, false);

        rightEar = new AdvancedModelBox(this, "rightEar");
        rightEar.setRotationPoint(-6.0F, -5.0F, -4.0F);
        head.addChild(rightEar);
        setRotationAngle(rightEar, -0.2443F, 0.2443F, -0.7679F);
        rightEar.setTextureOffset(0, 53).addBox(-2.0F, -5.0F, 0.0F, 3.0F, 6.0F, 1.0F, 0.0F, true);

        leftArm = new AdvancedModelBox(this, "leftArm");
        leftArm.setRotationPoint(7.3F, 11.0F, -8.0F);
        chest.addChild(leftArm);
        leftArm.setTextureOffset(79, 59).addBox(-4.0F, 2.0F, -4.0F, 7.0F, 10.0F, 7.0F, 0.0F, false);

        rightArm = new AdvancedModelBox(this, "rightArm");
        rightArm.setRotationPoint(-7.3F, 11.0F, -8.0F);
        chest.addChild(rightArm);
        rightArm.setTextureOffset(79, 59).addBox(-3.0F, 2.0F, -4.0F, 7.0F, 10.0F, 7.0F, 0.0F, true);
        this.updateDefaultPose();
        animator = ModelAnimator.create();
    }

    @Override
    public void setupAnim(RhinocerosRenderState state) {
        this.resetToDefaultPose();
        float walkSpeed = 0.7F;
        float walkDegree = 0.6F;
        float idleSpeed = 0.1F;
        float idleDegree = 0.1F;
        float ageInTicks = state.ageInTicks;
        
        // Basic walk animation
        this.walk(leftArm, walkSpeed, walkDegree * 1.2F, true, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
        this.walk(rightArm, walkSpeed, walkDegree * 1.2F, false, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
        this.walk(leftLeg, walkSpeed, walkDegree * 1.2F, false, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
        this.walk(rightLeg, walkSpeed, walkDegree * 1.2F, true, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
        this.bob(body, walkSpeed, walkDegree * 2F, true, state.walkAnimationPos, state.walkAnimationSpeed);
        this.bob(head, walkSpeed, walkDegree * 2F, true, state.walkAnimationPos, state.walkAnimationSpeed);
        
        // Idle animations
        this.walk(head, idleSpeed, idleDegree * 0.5F, false, 0F, 0.05F, ageInTicks, 1);
        this.flap(leftEar, idleSpeed, idleDegree, false, -1F, 0F, ageInTicks, 1);
        this.walk(rightEar, idleSpeed, idleDegree, false, -1F, 0F, ageInTicks, 1);
        
        // Head rotation
        this.head.rotateAngleY += state.yRot / 57.295776F * 0.8F;
        this.head.rotateAngleX += state.xRot / 57.295776F;
        
        // Simple animations based on state
        // TODO: Implement proper animation system with ModelAnimator in the future
        // For now, animations will be basic
    }

    public void setRotationAngle(AdvancedModelBox AdvancedModelBox, float x, float y, float z) {
        AdvancedModelBox.rotateAngleX = x;
        AdvancedModelBox.rotateAngleY = y;
        AdvancedModelBox.rotateAngleZ = z;
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(root, body, head, chest, leftArm, leftEar, leftLeg, rightArm, rightEar, rightLeg, horns);
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

}
