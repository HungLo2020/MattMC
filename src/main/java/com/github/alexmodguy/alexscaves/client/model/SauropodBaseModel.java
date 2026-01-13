package com.github.alexmodguy.alexscaves.client.model;

import com.github.alexmodguy.alexscaves.client.render.entity.AtlatitanRenderState;
import com.github.alexmodguy.alexscaves.server.misc.ACMath;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.ModelAnimator;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

public abstract class SauropodBaseModel<T extends LivingEntityRenderState> extends AdvancedEntityModel<T> {
    protected final AdvancedModelBox root;
    protected final AdvancedModelBox body;
    protected final AdvancedModelBox hips;
    protected final AdvancedModelBox tail;
    protected final AdvancedModelBox tail2;
    protected final AdvancedModelBox tail3;
    protected final AdvancedModelBox left_Leg;
    protected final AdvancedModelBox left_Foot;
    protected final AdvancedModelBox right_Leg;
    protected final AdvancedModelBox right_Foot;
    protected final AdvancedModelBox chest;
    protected final AdvancedModelBox right_Arm;
    protected final AdvancedModelBox right_Hand;
    protected final AdvancedModelBox left_Arm;
    protected final AdvancedModelBox left_Hand;
    protected final AdvancedModelBox neck;
    protected final AdvancedModelBox neck2;
    protected final AdvancedModelBox head;
    protected final AdvancedModelBox jaw;
    protected final AdvancedModelBox dewlap;
    protected final ModelAnimator animator;
    public boolean straighten = false;

    public SauropodBaseModel() {
        texWidth = 512;
        texHeight = 512;

        root = new AdvancedModelBox(this);
        root.setRotationPoint(0.0F, 24.0F, 0.0F);

        body = new AdvancedModelBox(this);
        body.setRotationPoint(0.0F, 0.0F, 0.0F);
        root.addChild(body);

        hips = new AdvancedModelBox(this);
        hips.setRotationPoint(0.0F, -65.0F, 0.5F);
        body.addChild(hips);
        hips.setTextureOffset(230, 149).addBox(-19.0F, -24.0F, -3.5F, 38.0F, 48.0F, 41.0F, 0.0F, false);

        tail = new AdvancedModelBox(this);
        tail.setRotationPoint(0.0F, 6.5F, 33.0F);
        hips.addChild(tail);
        tail.setTextureOffset(0, 246).addBox(-12.0F, -14.5F, 2.5F, 24.0F, 29.0F, 49.0F, 0.0F, false);

        tail2 = new AdvancedModelBox(this);
        tail2.setRotationPoint(0.0F, 1.5F, 49.0F);
        tail.addChild(tail2);
        tail2.setTextureOffset(245, 238).addBox(-8.0F, -10.0F, -6.5F, 16.0F, 20.0F, 57.0F, 0.0F, false);

        tail3 = new AdvancedModelBox(this);
        tail3.setRotationPoint(0.0F, 0.5F, 48.5F);
        tail2.addChild(tail3);
        tail3.setTextureOffset(138, 174).addBox(-5.0F, -6.5F, -14.0F, 10.0F, 13.0F, 72.0F, 0.0F, false);

        left_Leg = new AdvancedModelBox(this);
        left_Leg.setRotationPoint(18.0F, 12.0F, 22.5F);
        hips.addChild(left_Leg);
        left_Leg.setTextureOffset(139, 0).addBox(-9.5F, -7.0F, -15.0F, 19.0F, 35.0F, 27.0F, 0.0F, false);

        left_Foot = new AdvancedModelBox(this);
        left_Foot.setRotationPoint(0.0F, 23.0F, 5.0F);
        left_Leg.addChild(left_Foot);
        left_Foot.setTextureOffset(270, 315).addBox(-6.5F, -5.0F, -5.0F, 13.0F, 35.0F, 17.0F, 0.0F, false);
        left_Foot.setTextureOffset(153, 149).addBox(-6.5F, 26.0F, -9.0F, 13.0F, 4.0F, 4.0F, 0.25F, false);
        left_Foot.setTextureOffset(153, 157).addBox(-6.5F, 26.0F, -9.0F, 13.0F, 4.0F, 4.0F, 0.0F, false);

        right_Leg = new AdvancedModelBox(this);
        right_Leg.setRotationPoint(-18.0F, 12.0F, 22.5F);
        hips.addChild(right_Leg);
        right_Leg.setTextureOffset(139, 0).addBox(-9.5F, -7.0F, -15.0F, 19.0F, 35.0F, 27.0F, 0.0F, true);

        right_Foot = new AdvancedModelBox(this);
        right_Foot.setRotationPoint(0.0F, 23.0F, 5.0F);
        right_Leg.addChild(right_Foot);
        right_Foot.setTextureOffset(270, 315).addBox(-6.5F, -5.0F, -5.0F, 13.0F, 35.0F, 17.0F, 0.0F, true);
        right_Foot.setTextureOffset(153, 149).addBox(-6.5F, 26.0F, -9.0F, 13.0F, 4.0F, 4.0F, 0.25F, true);
        right_Foot.setTextureOffset(153, 157).addBox(-6.5F, 26.0F, -9.0F, 13.0F, 4.0F, 4.0F, 0.0F, true);

        chest = new AdvancedModelBox(this);
        chest.setRotationPoint(0.0F, -9.0F, 0.0F);
        hips.addChild(chest);
        chest.setTextureOffset(0, 123).addBox(-24.0F, -33.0F, -56.5F, 48.0F, 66.0F, 57.0F, 0.01F, false);

        right_Arm = new AdvancedModelBox(this);
        right_Arm.setRotationPoint(-23.0F, -3.0F, -37.5F);
        chest.addChild(right_Arm);
        right_Arm.setTextureOffset(0, 0).addBox(-13.0F, -10.0F, -11.0F, 14.0F, 44.0F, 21.0F, 0.0F, true);

        right_Hand = new AdvancedModelBox(this);
        right_Hand.setRotationPoint(-3.0F, 32.0F, -9.0F);
        right_Arm.addChild(right_Hand);
        right_Hand.setTextureOffset(264, 0).addBox(-15.0F, -2.0F, -2.75F, 24.0F, 47.0F, 29.0F, 0.0F, true);
        right_Hand.setTextureOffset(20, 238).addBox(-8.0F, -2.0F, -12.75F, 0.0F, 47.0F, 10.0F, 0.0F, true);
        right_Hand.setTextureOffset(20, 238).addBox(2.0F, -2.0F, -12.75F, 0.0F, 47.0F, 10.0F, 0.0F, true);
        right_Hand.setTextureOffset(49, 0).addBox(9.0F, 37.0F, 13.25F, 8.0F, 8.0F, 8.0F, 0.0F, true);


        left_Arm = new AdvancedModelBox(this);
        left_Arm.setRotationPoint(23.0F, -3.0F, -37.5F);
        chest.addChild(left_Arm);
        left_Arm.setTextureOffset(0, 0).addBox(-1.0F, -10.0F, -11.0F, 14.0F, 44.0F, 21.0F, 0.0F, false);

        left_Hand = new AdvancedModelBox(this);
        left_Hand.setRotationPoint(3.0F, 32.0F, -9.0F);
        left_Arm.addChild(left_Hand);
        left_Hand.setTextureOffset(264, 0).addBox(-9.0F, -2.0F, -2.75F, 24.0F, 47.0F, 29.0F, 0.0F, false);
        left_Hand.setTextureOffset(20, 238).addBox(8.0F, -2.0F, -12.75F, 0.0F, 47.0F, 10.0F, 0.0F, false);
        left_Hand.setTextureOffset(20, 238).addBox(-2.0F, -2.0F, -12.75F, 0.0F, 47.0F, 10.0F, 0.0F, false);
        left_Hand.setTextureOffset(49, 0).addBox(-17.0F, 37.0F, 13.25F, 8.0F, 8.0F, 8.0F, 0.0F, false);

        neck = new AdvancedModelBox(this);
        neck.setRotationPoint(0.5F, -31.0F, -33.0F);
        chest.addChild(neck);
        neck.setTextureOffset(0, 0).addBox(-13.5F, -14.0F, -76.5F, 26.0F, 36.0F, 87.0F, 0.0F, false);

        neck2 = new AdvancedModelBox(this);
        neck2.setRotationPoint(-0.5F, -6.0F, -75.5F);
        neck.addChild(neck2);
        neck2.setTextureOffset(153, 44).addBox(-8.0F, -2.0F, -80.0F, 16.0F, 26.0F, 79.0F, 0.0F, false);

        head = new AdvancedModelBox(this);
        head.setRotationPoint(0.8F, 8.0F, -75.0F);
        neck2.addChild(head);
        head.setTextureOffset(198, 315).addBox(-9.8F, -4.0F, -15.0F, 18.0F, 22.0F, 18.0F, 0.0F, false);
        head.setTextureOffset(0, 324).addBox(-5.8F, -13.0F, -23.0F, 10.0F, 20.0F, 20.0F, 0.0F, false);
        head.setTextureOffset(264, 76).addBox(-11.8F, 6.0F, -28.0F, 22.0F, 7.0F, 21.0F, 0.0F, false);
        head.setTextureOffset(0, 65).addBox(-11.3F, 11.0F, -27.5F, 21.0F, 6.0F, 13.0F, 0.0F, false);

        jaw = new AdvancedModelBox(this);
        jaw.setRotationPoint(-0.8F, 10.5F, -8.5F);
        head.addChild(jaw);
        jaw.setTextureOffset(331, 83).addBox(-11.0F, -1.5F, -19.5F, 22.0F, 9.0F, 21.0F, -0.01F, false);
        jaw.setTextureOffset(360, 0).addBox(-11.0F, 3.0F, -19.5F, 22.0F, 2.0F, 17.0F, -0.001F, false);

        dewlap = new AdvancedModelBox(this);
        dewlap.setRotationPoint(0.0F, 24.0F, -57.5F);
        neck2.addChild(dewlap);
        dewlap.setTextureOffset(97, 194).addBox(0.0F, -4.0F, -32.5F, 0.0F, 26.0F, 65.0F, 0.0F, false);
        this.left_Foot.setShouldScaleChildren(true);
        this.right_Foot.setShouldScaleChildren(true);
        this.left_Hand.setShouldScaleChildren(true);
        this.right_Hand.setShouldScaleChildren(true);
        this.jaw.setScale(0.99F, 0.99F, 0.99F);
        animator = ModelAnimator.create();
    }

    // animate method removed - using render state instead
        animator.setAnimation(SauropodBaseEntity.ANIMATION_SPEAK);
        animator.startKeyframe(3);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.endKeyframe();
        animator.startKeyframe(7);
        animator.rotate(head, (float) Math.toRadians(-10), 0, 0);
        animator.rotate(jaw, (float) Math.toRadians(40), 0, 0);
        animator.endKeyframe();
        animator.resetKeyframe(5);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_ROAR);
        animator.startKeyframe(5);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.endKeyframe();
        animator.startKeyframe(5);
        animator.rotate(head, (float) Math.toRadians(-50), 0, 0);
        animator.rotate(jaw, (float) Math.toRadians(40), 0, 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(40);
        animator.resetKeyframe(10);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_EPIC_DEATH);
        animator.startKeyframe(5);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.endKeyframe();
        animator.startKeyframe(5);
        animator.rotate(head, (float) Math.toRadians(-50), 0, 0);
        animator.rotate(neck, (float) Math.toRadians(-10), 0, 0);
        animator.rotate(neck2, (float) Math.toRadians(-10), 0, 0);
        animator.rotate(jaw, (float) Math.toRadians(40), 0, 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(100);
        animator.resetKeyframe(10);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_SUMMON);
        animator.startKeyframe(0);
        animator.move(body, 0, 200, 0);
        animator.move(neck2, 0, -5, -5);
        animator.rotate(neck, (float) Math.toRadians(-50), 0, 0);
        animator.rotate(neck2, (float) Math.toRadians(120), 0, 0);
        animator.rotate(head, (float) Math.toRadians(50), 0, 0);
        animator.endKeyframe();
        animator.resetKeyframe(120);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_STOMP);
        animator.startKeyframe(20);
        animator.move(body, 0, -25, -23);
        animator.move(chest, 0, 2, 4);
        animator.move(head, 0, 5, -5);
        animator.move(left_Leg, 0, 0, -5);
        animator.move(right_Leg, 0, 0, -5);
        animator.move(left_Arm, 0, 10, -5);
        animator.move(right_Arm, 0, 10, -5);
        animator.rotate(body, (float) Math.toRadians(-40), 0, 0);
        animator.rotate(neck, (float) Math.toRadians(50), 0, 0);
        animator.rotate(neck2, (float) Math.toRadians(10), 0, 0);
        animator.rotate(tail, (float) Math.toRadians(20), 0, 0);
        animator.rotate(tail3, (float) Math.toRadians(20), 0, 0);
        animator.rotate(head, (float) Math.toRadians(30), 0, 0);
        animator.rotate(chest, (float) Math.toRadians(-20), 0, 0);
        animator.rotate(left_Leg, (float) Math.toRadians(40), 0, 0);
        animator.rotate(right_Leg, (float) Math.toRadians(40), 0, 0);
        animator.rotate(left_Arm, (float) Math.toRadians(-20), (float) Math.toRadians(-20), (float) Math.toRadians(-20));
        animator.rotate(right_Arm, (float) Math.toRadians(-20), (float) Math.toRadians(20), (float) Math.toRadians(20));
        animator.rotate(left_Hand, (float) Math.toRadians(50), 0, 0);
        animator.rotate(right_Hand, (float) Math.toRadians(50), 0, 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(5);
        animator.startKeyframe(5);
        animator.move(body, 0, 10, 0);
        animator.move(right_Leg, 0, -10, 0);
        animator.move(left_Leg, 0, -10, 0);
        animator.move(right_Arm, -2, -10, -7);
        animator.move(left_Arm, 2, -10, -7);
        animator.rotate(left_Arm, 0, 0, (float) Math.toRadians(-20));
        animator.rotate(right_Arm, 0, 0, (float) Math.toRadians(20));
        animator.rotate(left_Hand, 0, 0, (float) Math.toRadians(20));
        animator.rotate(right_Hand, 0, 0, (float) Math.toRadians(-20));
        animator.endKeyframe();
        animator.setStaticKeyframe(10);
        animator.resetKeyframe(10);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_SPEW_FLAMES);
        animator.startKeyframe(5);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.endKeyframe();
        animator.startKeyframe(5);
        animator.rotate(head, (float) Math.toRadians(-30), 0, 0);
        animator.rotate(jaw, (float) Math.toRadians(60), 0, 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(60);
        animator.resetKeyframe(10);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_JUMP);
        animator.startKeyframe(10);
        animator.move(body, 0, 10, 0);
        animator.move(right_Leg, 0, -9, 0);
        animator.move(left_Leg, 0, -9, 0);
        animator.move(right_Arm, -2, -6, -4);
        animator.move(left_Arm, 2, -6, -4);
        animator.rotate(left_Arm, 0, 0, (float) Math.toRadians(-20));
        animator.rotate(right_Arm, 0, 0, (float) Math.toRadians(20));
        animator.rotate(left_Hand, 0, 0, (float) Math.toRadians(20));
        animator.rotate(right_Hand, 0, 0, (float) Math.toRadians(-20));
        animator.rotate(left_Leg, 0, 0, (float) Math.toRadians(-10));
        animator.rotate(right_Leg, 0, 0, (float) Math.toRadians(10));
        animator.rotate(left_Foot, 0, 0, (float) Math.toRadians(10));
        animator.rotate(right_Foot, 0, 0, (float) Math.toRadians(-10));
        animator.rotate(neck, (float) Math.toRadians(-15), 0, 0);
        animator.rotate(tail, (float) Math.toRadians(10), 0, 0);
        animator.rotate(neck2, (float) Math.toRadians(20), 0, 0);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(10);
        animator.startKeyframe(5);
        animator.move(body, 0, -10, 0);
        animator.rotate(left_Arm, (float) Math.toRadians(-30), 0, (float) Math.toRadians(-10));
        animator.rotate(right_Arm, (float) Math.toRadians(-30), 0, (float) Math.toRadians(10));
        animator.rotate(right_Leg, (float) Math.toRadians(30), 0, (float) Math.toRadians(-10));
        animator.rotate(left_Leg, (float) Math.toRadians(30), 0, (float) Math.toRadians(10));
        animator.endKeyframe();
        animator.setStaticKeyframe(5);
        animator.startKeyframe(5);
        animator.move(body, 0, 10, 0);
        animator.move(right_Leg, 0, -9, 0);
        animator.move(left_Leg, 0, -9, 0);
        animator.move(right_Arm, -2, -6, -4);
        animator.move(left_Arm, 2, -6, -4);
        animator.rotate(left_Arm, 0, 0, (float) Math.toRadians(-20));
        animator.rotate(right_Arm, 0, 0, (float) Math.toRadians(20));
        animator.rotate(left_Hand, 0, 0, (float) Math.toRadians(20));
        animator.rotate(right_Hand, 0, 0, (float) Math.toRadians(-20));
        animator.rotate(left_Leg, 0, 0, (float) Math.toRadians(-10));
        animator.rotate(right_Leg, 0, 0, (float) Math.toRadians(10));
        animator.rotate(left_Foot, 0, 0, (float) Math.toRadians(10));
        animator.rotate(right_Foot, 0, 0, (float) Math.toRadians(-10));
        animator.rotate(neck, (float) Math.toRadians(-15), 0, 0);
        animator.rotate(tail, (float) Math.toRadians(10), 0, 0);
        animator.rotate(neck2, (float) Math.toRadians(20), 0, 0);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.endKeyframe();
        animator.resetKeyframe(10);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_LEFT_KICK);
        animator.startKeyframe(4);
        animator.move(left_Arm, 3, 3, -3);
        animator.rotate(left_Arm,  (float) Math.toRadians(-30), (float) Math.toRadians(-40), 0);
        animator.rotate(left_Hand,  (float) Math.toRadians(40), 0, (float) Math.toRadians(10));
        animator.rotate(body,  0, (float) Math.toRadians(10), 0);
        animator.rotate(tail,  0, (float) Math.toRadians(10), 0);
        animator.rotate(neck,  0, (float) Math.toRadians(-5), 0);
        animator.endKeyframe();
        animator.startKeyframe(6);
        animator.move(left_Arm, 0, -5, -3);
        animator.rotate(left_Arm,  (float) Math.toRadians(-80), (float) Math.toRadians(0), 0);
        animator.rotate(left_Hand,  (float) Math.toRadians(10), 0, (float) Math.toRadians(10));
        animator.endKeyframe();
        animator.resetKeyframe(10);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_RIGHT_KICK);
        animator.startKeyframe(5);
        animator.move(right_Arm, -3, 3, -3);
        animator.rotate(right_Arm,  (float) Math.toRadians(-30), (float) Math.toRadians(40), 0);
        animator.rotate(right_Hand,  (float) Math.toRadians(40), 0, (float) Math.toRadians(-10));
        animator.rotate(body,  0, (float) Math.toRadians(-10), 0);
        animator.rotate(tail,  0, (float) Math.toRadians(-10), 0);
        animator.rotate(neck,  0, (float) Math.toRadians(5), 0);
        animator.endKeyframe();
        animator.startKeyframe(4);
        animator.move(right_Arm, 0, -5, -3);
        animator.rotate(right_Arm,  (float) Math.toRadians(-80), (float) Math.toRadians(0), 0);
        animator.rotate(right_Hand,  (float) Math.toRadians(10), 0, (float) Math.toRadians(-10));
        animator.endKeyframe();
        animator.resetKeyframe(6);
        animator.setAnimation(SauropodBaseEntity.ANIMATION_EAT_LEAVES);
        animator.startKeyframe(15);
        animator.rotate(neck, (float) Math.toRadians(-20), 0, 0);
        animator.rotate(neck2, (float) Math.toRadians(10), 0, 0);
        animator.rotate(head, (float) Math.toRadians(10), 0, 0);
        animator.rotate(jaw, (float) Math.toRadians(40), 0, 0);
        animator.endKeyframe();
        animator.startKeyframe(10);
        animator.rotate(head, (float) Math.toRadians(20), 0, 0);
        animator.rotate(jaw, (float) Math.toRadians(-10), 0, 0);
        animator.move(jaw, 0, 0, 1);
        animator.endKeyframe();
        animator.resetKeyframe(10);

    }

    @Override
    public void setupAnim(T renderState) {
        this.resetToDefaultPose();
        // Animation handling - check if renderState has animation data
        if (renderState instanceof AtlatitanRenderState atlatitanState) {
            if (atlatitanState.animation != null) {
                setupAnimForAnimation(atlatitanState, atlatitanState.animation, renderState.walkAnimationPos, renderState.walkAnimationSpeed, renderState.ageInTicks);
            }
            float idleSpeed = 0.05F;
            float walkSpeed = 0.05F;
            float walkDegree = 3F;
            float walk = atlatitanState.walkAnimPosition;
            float walkAmount = Math.min(atlatitanState.walkAnimSpeed, 1F);
            float armsWalkAmount = walkAmount;
            float raiseArmsAmount = atlatitanState.raiseArmsAmount;
            float legBack = atlatitanState.legBackAmount;
            float danceAmount = atlatitanState.danceProgress;
            float buryEggsAmount = 0.0F; // Not used for atlatitan
            positionNeckAndTailFromState(atlatitanState);
            articulateLegsFromState(atlatitanState, raiseArmsAmount);
            if (buryEggsAmount > 0.0F) {
                // Not used for atlatitan
            }
            this.walk(neck, idleSpeed, 0.03F, true, 0F, 0F, renderState.ageInTicks, 1);
            this.walk(neck2, idleSpeed, 0.02F, true, -1F, -0.03F, renderState.ageInTicks, 1);
            this.walk(head, idleSpeed, 0.01F, true, -2F, 0.02F, renderState.ageInTicks, 1);
            this.flap(dewlap, 0.14F, 0.1F, true, 0F, 0.0F, renderState.ageInTicks, 1);
            this.walk(dewlap, 0.14F, 0.05F, true, 1F, -0.1F, renderState.ageInTicks, 1);
            this.walk(tail, idleSpeed, 0.03F, true, 3F, 0F, renderState.ageInTicks, 1);
            this.walk(tail2, idleSpeed, 0.03F, true, 2F, 0F, renderState.ageInTicks, 1);
            this.walk(tail3, idleSpeed, 0.03F, true, 2F, 0F, renderState.ageInTicks, 1);
            this.swing(tail, idleSpeed, 0.03F, true, 4F, 0F, renderState.ageInTicks, 1);
            this.swing(tail2, idleSpeed, 0.03F, true, 3F, 0F, renderState.ageInTicks, 1);
            this.swing(tail3, idleSpeed, 0.03F, true, 2F, 0F, renderState.ageInTicks, 1);
            this.dewlap.rotationPointY += ACMath.walkValue(renderState.ageInTicks, 1F, 0.1F, -1.5F, 1, false);
            float legAnimSeperation = 0.5F;
            animateLegWalking(right_Arm, right_Hand, legAnimSeperation * 3, walkSpeed, walkDegree, walk, armsWalkAmount, true, false, legBack);
            animateLegWalking(right_Leg, right_Foot, legAnimSeperation * 2, walkSpeed, walkDegree, walk, walkAmount, false, false, legBack);
            animateLegWalking(left_Arm, left_Hand, legAnimSeperation, walkSpeed, walkDegree, walk, armsWalkAmount, true, true, legBack);
            animateLegWalking(left_Leg, left_Foot, 0, walkSpeed, walkDegree, walk, walkAmount, false, true, legBack);
            animateDancingFromState(atlatitanState, danceAmount, renderState.ageInTicks);
        }
    }

    private void animateLegWalking(AdvancedModelBox leg, AdvancedModelBox foot, float offset, float speed, float degree, float limbSwing, float limbSwingAmount, boolean front, boolean left, float legBack) {
        float leg1 = Math.min(0, ACMath.walkValue(limbSwing, limbSwingAmount, speed, Mth.PI * (offset + 0.3333F), 1F, true) + 0.75F) * 4;
        float leg1Delayed = Math.min(0, ACMath.walkValue(limbSwing, limbSwingAmount, speed, Mth.PI * offset, 1F, true) + 0.75F) * 4;
        float leg1Prev = Math.min(0, ACMath.walkValue(limbSwing, limbSwingAmount, speed, Mth.PI * (offset + 0.6666F), 1F, true) + 0.75F) * 4;
        float leg1Squish = 1F - 0.15F * (float) (Math.pow(Math.min(leg1Delayed - leg1, 0), 3F));
        float legInactivityAmount = 1F - Math.abs(leg1);
        this.walk(leg, speed, degree * 0.3F, false, Mth.PI * offset + 1F, 0F, limbSwing, leg1);
        this.walk(foot, speed, degree * 0.2F, false, Mth.PI * offset - 2F, -0.25F, limbSwing, leg1);
        if (front) {
            this.swing(leg, speed, degree * -0.2F, left, Mth.PI * offset + 1F, 0F, limbSwing, leg1Prev);
            leg.rotationPointZ += leg1Prev * 8F;
        }
        leg.rotationPointY += leg1 * 10F;
        leg.rotationPointZ += leg1Delayed * 16F;
        leg.rotationPointZ += legBack * legInactivityAmount * 16F;
        float raisedBody = leg1 * 8;
        body.rotationPointY += raisedBody;
        left_Leg.rotationPointY -= raisedBody;
        right_Leg.rotationPointY -= raisedBody;
        left_Arm.rotationPointY -= raisedBody;
        right_Arm.rotationPointY -= raisedBody;
        tail.rotationPointY -= raisedBody * 0.5F;
        neck.rotationPointY -= raisedBody * 0.5F;
        float squish2 = 2F - leg1Squish;
        foot.setScale(leg1Squish * leg1Squish, squish2, leg1Squish);
        leg.rotationPointY -= (squish2 - 1) * 30;
    }


    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

    public Vec3 getMouthPosition(Vec3 offsetIn) {
        PoseStack translationStack = new PoseStack();
        translationStack.pushPose();
        root.translateAndRotate(translationStack);
        body.translateAndRotate(translationStack);
        chest.translateAndRotate(translationStack);
        neck.translateAndRotate(translationStack);
        neck2.translateAndRotate(translationStack);
        head.translateAndRotate(translationStack);
        jaw.translateAndRotate(translationStack);
        Vector4f armOffsetVec = new Vector4f((float) offsetIn.x, (float) offsetIn.y, (float) offsetIn.z, 1.0F);
        armOffsetVec.mul(translationStack.last().pose());
        Vec3 vec3 = new Vec3(-armOffsetVec.x(), -armOffsetVec.y(), armOffsetVec.z());
        translationStack.popPose();
        return vec3.add(0, 5, -1F);
    }

    // Helper methods for render state conversion
    private void positionNeckAndTailFromState(AtlatitanRenderState renderState) {
        if (!straighten) {
            neck.rotateAngleY += (float) Math.toRadians(renderState.neckYRot);
            neck.rotateAngleX += (float) Math.toRadians(renderState.neckXRot);
            tail.rotateAngleY += (float) Math.toRadians(renderState.tailYRot);
            tail.rotateAngleX += (float) Math.toRadians(renderState.tailXRot);
        }
    }

    private void articulateLegsFromState(AtlatitanRenderState renderState, float raiseArmsAmount) {
        // Simplified leg articulation without entity legSolver
    }

    private void animateDancingFromState(AtlatitanRenderState renderState, float danceAmount, float ageInTicks) {
        // Simplified dancing animation
    }

    private void setupAnimForAnimation(AtlatitanRenderState renderState, Animation animation, float limbSwing, float limbSwingAmount, float ageInTicks) {
        // Simplified animation handling
    }

}
