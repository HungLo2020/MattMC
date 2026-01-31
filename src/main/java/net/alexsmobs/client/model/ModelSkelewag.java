package net.alexsmobs.client.model;

import net.alexsmobs.client.render.SkelewagRenderState;
import net.alexsmobs.entity.util.Maths;
import net.citadel.client.model.AdvancedEntityModel;
import net.citadel.client.model.AdvancedModelBox;
import net.citadel.client.model.basic.BasicModelPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.Mth;

public class ModelSkelewag extends AdvancedEntityModel<SkelewagRenderState> {
    private final AdvancedModelBox root;
    private final AdvancedModelBox body;
    private final AdvancedModelBox head;
    private final AdvancedModelBox flag;
    private final AdvancedModelBox left_fin;
    private final AdvancedModelBox right_fin;
    private final AdvancedModelBox tail;
    private final AdvancedModelBox tail_fin;

    public ModelSkelewag() {
        texWidth = 128;
        texHeight = 128;

        root = new AdvancedModelBox(this, "root");
        root.setRotationPoint(0.0F, 24.0F, 0.0F);


        body = new AdvancedModelBox(this, "body");
        body.setRotationPoint(0.0F, -9.0F, -3.0F);
        root.addChild(body);
        body.setTextureOffset(17, 11).addBox(-1.0F, -1.0F, -16.0F, 2.0F, 2.0F, 20.0F, 0.0F, false);
        body.setTextureOffset(50, 66).addBox(-0.5F, -16.0F, -15.0F, 1.0F, 15.0F, 2.0F, 0.0F, false);
        body.setTextureOffset(23, 34).addBox(-0.5F, -12.0F, -11.0F, 1.0F, 11.0F, 2.0F, 0.0F, false);
        body.setTextureOffset(19, 6).addBox(-0.5F, -9.0F, -7.0F, 1.0F, 8.0F, 2.0F, 0.0F, false);
        body.setTextureOffset(26, 6).addBox(-0.5F, -7.0F, -3.0F, 1.0F, 6.0F, 2.0F, 0.0F, false);
        body.setTextureOffset(0, 0).addBox(0.0F, -4.0F, 1.0F, 0.0F, 3.0F, 2.0F, 0.0F, false);
        body.setTextureOffset(45, 34).addBox(-2.0F, 1.0F, -12.0F, 4.0F, 6.0F, 10.0F, 0.0F, false);

        head = new AdvancedModelBox(this, "head");
        head.setRotationPoint(-0.5F, 0.0F, -17.0F);
        body.addChild(head);
        head.setTextureOffset(23, 54).addBox(-2.0F, -1.0F, -7.0F, 5.0F, 7.0F, 8.0F, 0.0F, false);
        head.setTextureOffset(50, 51).addBox(-0.5F, -1.0F, -19.0F, 2.0F, 2.0F, 12.0F, 0.0F, false);
        head.setTextureOffset(42, 17).addBox(0.0F, -1.0F, -31.0F, 1.0F, 1.0F, 12.0F, 0.0F, false);

        flag = new AdvancedModelBox(this, "flag");
        flag.setRotationPoint(0.5F, -10.0F, -15.0F);
        body.addChild(flag);
        setRotationAngle(flag, 0.0F, 0.1309F, 0.0F);
        flag.setTextureOffset(0, 0).addBox(0.0F, -5.0F, 0.0F, 0.0F, 12.0F, 18.0F, 0.0F, false);

        left_fin = new AdvancedModelBox(this, "left_fin");
        left_fin.setRotationPoint(2.0F, 7.0F, -10.0F);
        body.addChild(left_fin);
        setRotationAngle(left_fin, 0.0F, 0.0F, 0.7854F);
        left_fin.setTextureOffset(19, 0).addBox(0.0F, 0.0F, -2.0F, 10.0F, 1.0F, 4.0F, 0.0F, false);

        right_fin = new AdvancedModelBox(this, "right_fin");
        right_fin.setRotationPoint(-2.0F, 7.0F, -10.0F);
        body.addChild(right_fin);
        setRotationAngle(right_fin, 0.0F, 0.0F, -0.7854F);
        right_fin.setTextureOffset(19, 0).addBox(-10.0F, 0.0F, -2.0F, 10.0F, 1.0F, 4.0F, 0.0F, true);

        tail = new AdvancedModelBox(this, "tail");
        tail.setRotationPoint(0.0F, 0.0F, 5.0F);
        body.addChild(tail);
        tail.setTextureOffset(23, 34).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 17.0F, 0.0F, false);
        tail.setTextureOffset(9, 0).addBox(0.0F, -3.0F, 0.0F, 0.0F, 2.0F, 2.0F, 0.0F, false);
        tail.setTextureOffset(57, 17).addBox(0.0F, -2.0F, 4.0F, 0.0F, 1.0F, 10.0F, 0.0F, false);
        tail.setTextureOffset(42, 0).addBox(-2.0F, 1.0F, 2.0F, 4.0F, 5.0F, 11.0F, 0.0F, false);
        tail.setTextureOffset(0, 0).addBox(0.0F, 4.0F, 5.0F, 0.0F, 6.0F, 8.0F, 0.0F, false);

        tail_fin = new AdvancedModelBox(this, "tail_fin");
        tail_fin.setRotationPoint(0.0F, 0.0F, 15.0F);
        tail.addChild(tail_fin);
        tail_fin.setTextureOffset(0, 31).addBox(0.0F, -12.0F, 0.0F, 0.0F, 25.0F, 11.0F, 0.0F, false);
        this.updateDefaultPose();
    }

    @Override
    public void setupAnim(SkelewagRenderState state) {
        this.resetToDefaultPose();
        float idleSpeed = 0.2F;
        float idleDegree = 0.3F;
        float swimSpeed = 0.55F;
        float swimDegree = 0.5F;
        float landProgress = state.onLandProgress;
        float ageInTicks = state.ageInTicks;
        
        progressRotationPrev(body, landProgress, 0, 0, Maths.rad(-90), 5F);
        progressPositionPrev(body, landProgress, 0, 3, 6, 5F);
        
        if(state.deathTime > 0) {
            float fallApartProgress = state.deathTime / 20F;
            progressPositionPrev(tail, fallApartProgress, 0, 0, 4, 1F);
            progressPositionPrev(tail_fin, fallApartProgress, 0, 0, 4, 1F);
            progressPositionPrev(right_fin, fallApartProgress, 0, 1, 2, 1F);
            progressPositionPrev(left_fin, fallApartProgress, 0, 1, 2, 1F);
            progressPositionPrev(head, fallApartProgress, 0, 0, -1, 1F);
            progressRotationPrev(right_fin, fallApartProgress, 0,  Maths.rad(25), 0, 1F);
            progressRotationPrev(left_fin, fallApartProgress, 0,  Maths.rad(-25), 0, 1F);
        }
        
        AdvancedModelBox[] tailBoxes = new AdvancedModelBox[]{body, tail, tail_fin};
        this.chainSwing(tailBoxes, idleSpeed, idleDegree * 0.1F, 3, ageInTicks, 1);
        this.bob(body, idleSpeed, idleDegree, false, ageInTicks, 1);
        this.bob(left_fin, idleSpeed, idleDegree, false, ageInTicks, 1);
        this.bob(right_fin, idleSpeed, idleDegree, false, ageInTicks, 1);
        this.swing(flag, idleSpeed, idleDegree * 0.2F, false, 3, 0.05F, ageInTicks, 1);
        this.chainSwing(tailBoxes, swimSpeed, swimDegree, -2, state.walkAnimationPos, state.walkAnimationSpeed);
        this.swing(head, swimSpeed, swimDegree, true, -0.5F, 0, state.walkAnimationPos, state.walkAnimationSpeed);
        this.flap(left_fin, swimSpeed, swimDegree, true, -1, -0, state.walkAnimationPos, state.walkAnimationSpeed);
        this.flap(right_fin, swimSpeed, swimDegree, false, -1, -0, state.walkAnimationPos, state.walkAnimationSpeed);
        this.bob(left_fin, swimSpeed, -1.5F * swimDegree, false, state.walkAnimationPos, state.walkAnimationSpeed);
        this.bob(right_fin, swimSpeed, -1.5F * swimDegree, false, state.walkAnimationPos, state.walkAnimationSpeed);
        this.swing(flag, swimSpeed, swimDegree * 0.6F, false, 2, 0.3F, state.walkAnimationPos, state.walkAnimationSpeed);
        this.body.rotateAngleX += state.xRot * Mth.DEG_TO_RAD;
        this.head.rotateAngleX -= state.xRot * 0.5F * Mth.DEG_TO_RAD;
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.of(root);
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.of(root, body, tail, tail_fin, head, left_fin, right_fin, flag);
    }

    public void setRotationAngle(AdvancedModelBox AdvancedModelBox, float x, float y, float z) {
        AdvancedModelBox.rotateAngleX = x;
        AdvancedModelBox.rotateAngleY = y;
        AdvancedModelBox.rotateAngleZ = z;
    }
}