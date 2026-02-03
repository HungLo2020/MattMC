package net.alexsmobs.client.model;

import net.alexsmobs.client.render.ShoebillRenderState;
import net.alexsmobs.entity.util.Maths;
import net.citadel.client.model.AdvancedEntityModel;
import net.citadel.client.model.AdvancedModelBox;
import net.citadel.client.model.ModelAnimator;
import net.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.Mth;

public class ModelShoebill extends AdvancedEntityModel<ShoebillRenderState> {
    private final AdvancedModelBox root;
    private final AdvancedModelBox body;
    private final AdvancedModelBox tail;
    private final AdvancedModelBox leftWing;
    private final AdvancedModelBox leftWingFeathers;
    private final AdvancedModelBox rightWing;
    private final AdvancedModelBox rightWingFeathers;
    private final AdvancedModelBox leftLeg;
    private final AdvancedModelBox leftFoot;
    private final AdvancedModelBox rightLeg;
    private final AdvancedModelBox rightFoot;
    private final AdvancedModelBox headPivot;
    private final AdvancedModelBox head;
    private final AdvancedModelBox backHair;
    private final AdvancedModelBox hair_r1;
    private final AdvancedModelBox beak;
    private final AdvancedModelBox jaw;
    private final AdvancedModelBox jaw_r1;
    private final ModelAnimator animator;

    public ModelShoebill() {
        texWidth = 128;
        texHeight = 128;

        root = new AdvancedModelBox(this);
        root.setRotationPoint(0.0F, 24.0F, 0.0F);


        body = new AdvancedModelBox(this);
        body.setRotationPoint(0.0F, -17.0F, 2.0F);
        root.addChild(body);
        setRotationAngle(body, -0.9599F, 0.0F, 0.0F);
        body.setTextureOffset(0, 0).addBox(-4.0F, -3.0F, -7.0F, 8.0F, 6.0F, 13.0F, 0.0F, false);

        tail = new AdvancedModelBox(this);
        tail.setRotationPoint(0.0F, -2.0F, 6.0F);
        body.addChild(tail);
        tail.setTextureOffset(42, 27).addBox(-3.0F, -1.0F, 0.0F, 6.0F, 2.0F, 9.0F, 0.0F, false);

        leftWing = new AdvancedModelBox(this);
        leftWing.setRotationPoint(4.0F, 0.3F, -3.4F);
        body.addChild(leftWing);
        leftWing.setTextureOffset(0, 20).addBox(-1.0F, -3.0F, -2.0F, 2.0F, 6.0F, 13.0F, 0.0F, false);

        leftWingFeathers = new AdvancedModelBox(this);
        leftWingFeathers.setRotationPoint(0.0F, 0.0F, 9.0F);
        leftWing.addChild(leftWingFeathers);
        leftWingFeathers.setTextureOffset(31, 8).addBox(0.2F, -4.0F, -3.0F, 0.0F, 6.0F, 12.0F, 0.0F, false);

        rightWing = new AdvancedModelBox(this);
        rightWing.setRotationPoint(-4.0F, 0.3F, -3.4F);
        body.addChild(rightWing);
        rightWing.setTextureOffset(0, 20).addBox(-1.0F, -3.0F, -2.0F, 2.0F, 6.0F, 13.0F, 0.0F, true);

        rightWingFeathers = new AdvancedModelBox(this);
        rightWingFeathers.setRotationPoint(0.0F, 0.0F, 9.0F);
        rightWing.addChild(rightWingFeathers);
        rightWingFeathers.setTextureOffset(31, 8).addBox(-0.2F, -4.0F, -3.0F, 0.0F, 6.0F, 12.0F, 0.0F, true);

        leftLeg = new AdvancedModelBox(this);
        leftLeg.setRotationPoint(2.5F, 3.0F, 4.0F);
        body.addChild(leftLeg);
        setRotationAngle(leftLeg, 0.9599F, 0.0F, 0.0F);
        leftLeg.setTextureOffset(18, 20).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 12.0F, 0.0F, 0.0F, false);

        leftFoot = new AdvancedModelBox(this);
        leftFoot.setRotationPoint(0.0F, 12.0F, 0.0F);
        leftLeg.addChild(leftFoot);
        leftFoot.setTextureOffset(30, 0).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 0.0F, 5.0F, 0.0F, false);

        rightLeg = new AdvancedModelBox(this);
        rightLeg.setRotationPoint(-2.5F, 3.0F, 4.0F);
        body.addChild(rightLeg);
        setRotationAngle(rightLeg, 0.9599F, 0.0F, 0.0F);
        rightLeg.setTextureOffset(18, 20).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 12.0F, 0.0F, 0.0F, true);

        rightFoot = new AdvancedModelBox(this);
        rightFoot.setRotationPoint(0.0F, 12.0F, 0.0F);
        rightLeg.addChild(rightFoot);
        rightFoot.setTextureOffset(30, 0).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 0.0F, 5.0F, 0.0F, true);

        headPivot = new AdvancedModelBox(this);
        headPivot.setRotationPoint(0.0F, 1.0F, -4.0F);
        setRotationAngle(headPivot, -0.6109F, 0.0F, 0.0F);
        body.addChild(headPivot);

        head = new AdvancedModelBox(this);
        head.setRotationPoint(0.0F, 0.0F, 0.0F);
        headPivot.addChild(head);
        head.setTextureOffset(20, 29).addBox(-2.5F, -3.0F, -11.0F, 5.0F, 5.0F, 11.0F, 0.0F, false);
        head.setTextureOffset(34, 47).addBox(-2.5F, 2.0F, -11.0F, 5.0F, 1.0F, 1.0F, 0.0F, false);
        head.setTextureOffset(0, 0).addBox(0.0F, -6.0F, -12.0F, 0.0F, 6.0F, 5.0F, 0.0F, false);

        backHair = new AdvancedModelBox(this);
        backHair.setRotationPoint(0.0F, -3.0F, -10.0F);
        head.addChild(backHair);


        hair_r1 = new AdvancedModelBox(this);
        hair_r1.setRotationPoint(0.0F, 0.0F, -1.0F);
        backHair.addChild(hair_r1);
        setRotationAngle(hair_r1, -0.5236F, 0.0F, 0.0F);
        hair_r1.setTextureOffset(30, 6).addBox(-2.5F, -5.0F, 0.0F, 5.0F, 5.0F, 0.0F, 0.0F, false);

        beak = new AdvancedModelBox(this);
        beak.setRotationPoint(0.0F, 2.0F, -9.0F);
        head.addChild(beak);
        setRotationAngle(beak, 0.3927F, 0.0F, 0.0F);
        beak.setTextureOffset(0, 40).addBox(-3.0F, -1.0F, 0.0F, 6.0F, 8.0F, 3.0F, 0.0F, false);
        beak.setTextureOffset(6, 0).addBox(-1.0F, 7.0F, 3.0F, 2.0F, 0.0F, 1.0F, 0.0F, false);

        jaw = new AdvancedModelBox(this);
        jaw.setRotationPoint(0.0F, 1.0F, 3.0F);
        beak.addChild(jaw);


        jaw_r1 = new AdvancedModelBox(this);
        jaw_r1.setRotationPoint(0.0F, 0.0F, 0.3F);
        jaw.addChild(jaw_r1);
        setRotationAngle(jaw_r1, -0.1745F, 0.0F, 0.0F);
        jaw_r1.setTextureOffset(0, 20).addBox(-2.5F, -1.4F, -0.3F, 5.0F, 7.0F, 1.0F, -0.03F, false);
        this.updateDefaultPose();
        animator = new ModelAnimator();
    }

    public void animate(ShoebillRenderState state, int animationId, int animationTick) {
        this.resetToDefaultPose();
        // Animations are handled differently in render state architecture
        // We can't use the old IAnimatedEntity system directly
        // For now, skip keyframe animations and just use procedural animation
    }

    public void setRotationAngle(AdvancedModelBox AdvancedModelBox, float x, float y, float z) {
        AdvancedModelBox.rotateAngleX = x;
        AdvancedModelBox.rotateAngleY = y;
        AdvancedModelBox.rotateAngleZ = z;
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(root, body, leftLeg, rightLeg, leftWing, rightWing, tail, headPivot, head, beak, jaw, backHair, leftFoot, rightFoot, hair_r1, jaw_r1, leftWingFeathers, rightWingFeathers);
    }

    @Override
    public void setupAnim(ShoebillRenderState state) {
        this.animate(state, state.currentAnimationId, state.animationTick);
        float walkSpeed = 0.7F;
        float walkDegree = 0.4F;
        float idleSpeed = 0.05F;
        float idleDegree = 0.2F;
        float flapSpeed = 0.4F;
        float flapDegree = 0.2F;
        float flyProgress = state.flyProgress;
        float scaledLimbSwing = Math.min(1.0F, state.walkAnimationSpeed * 1.6F);
        float runProgress = Math.max(5F * scaledLimbSwing - flyProgress, 0);
        float ageInTicks = state.ageInTicks;
        progressRotationPrev(body, runProgress, Maths.rad(25), 0, 0, 5F);
        progressRotationPrev(rightLeg, runProgress, Maths.rad(-25), 0, 0, 5F);
        progressRotationPrev(leftLeg, runProgress, Maths.rad(-25), 0, 0, 5F);
        progressRotationPrev(head, runProgress, Maths.rad(-30), 0, 0, 5F);
        progressRotationPrev(body, flyProgress, Maths.rad(35), 0, 0, 5F);
        progressRotationPrev(rightLeg, flyProgress, Maths.rad(25), 0, 0, 5F);
        progressRotationPrev(leftLeg, flyProgress, Maths.rad(25), 0, 0, 5F);
        progressRotationPrev(rightFoot, flyProgress, Maths.rad(25), 0, 0, 5F);
        progressRotationPrev(leftFoot, flyProgress, Maths.rad(25), 0, 0, 5F);
        progressRotationPrev(rightWing, flyProgress, Maths.rad(90), 0, Maths.rad(-80), 5F);
        progressRotationPrev(leftWing, flyProgress, Maths.rad(90), 0, Maths.rad(80), 5F);
        progressRotationPrev(head, flyProgress, Maths.rad(-20), 0, 0, 5F);
        progressRotationPrev(tail, flyProgress, Maths.rad(10), 0, 0, 5F);
        progressPositionPrev(rightLeg, flyProgress, 0, -1F, 0, 5f);
        progressPositionPrev(leftLeg, flyProgress, 0, -1F, 0, 5f);
        progressPositionPrev(body, flyProgress, 0, 5F, 0, 5f);
        progressPositionPrev(head, flyProgress, 0, 1.5F, 0, 5f);

        this.walk(head, -idleSpeed, idleDegree * 0.2F, false, 2F, 0F, ageInTicks, 1);
        this.flap(tail, idleSpeed * 2F, idleDegree * 0.5F, true, 0F, 0F, ageInTicks, 1);
        if(flyProgress > 0){
            this.walk(rightLeg, walkSpeed, walkDegree * 0.2F, false, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.walk(leftLeg, walkSpeed, walkDegree * 0.2F, true, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.flap(rightWing, flapSpeed, flapDegree * 5, true, 0F, 0F, ageInTicks, 1);
            this.flap(leftWing, flapSpeed, flapDegree * 5, false, 0F, 0F, ageInTicks, 1);
            this.walk(head, flapSpeed, flapDegree * 0.85F, true, 0F, 0F, ageInTicks, 1);
            this.bob(body, flapSpeed * 0.3F, flapDegree * 4, true, ageInTicks, 1);
        }else{
            this.walk(rightLeg, walkSpeed, walkDegree * 1.85F, false, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.walk(leftLeg, walkSpeed, walkDegree * 1.85F, true, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.walk(rightFoot, walkSpeed, walkDegree * 1.85F, true, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.walk(leftFoot, walkSpeed, walkDegree * 1.85F, false, 0F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.walk(head, walkSpeed, walkDegree * 0.85F, true, 2F, 0F, state.walkAnimationPos, state.walkAnimationSpeed);
            this.walk(tail, walkSpeed * 0.5F, walkDegree * 0.15F, true, -2F, 0.2F, state.walkAnimationPos, state.walkAnimationSpeed);
        }
        this.head.rotateAngleZ += state.yRot * Mth.DEG_TO_RAD;

    }

}