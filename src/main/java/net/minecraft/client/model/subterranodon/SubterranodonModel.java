package net.minecraft.client.model.subterranodon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.SubterranodonRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

/**
 * Subterranodon Model - Ported from AlexsCaves mod.
 * Original model by AlexModGuy using Citadel's AdvancedEntityModel.
 * Adapted to vanilla Minecraft's ModelPart system.
 * 
 * Texture size: 256x256
 */
@Environment(EnvType.CLIENT)
public class SubterranodonModel extends EntityModel<SubterranodonRenderState> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart leftWing;
    private final ModelPart leftHand;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightHand;
    private final ModelPart rightWingTip;
    private final ModelPart leftLeg;
    private final ModelPart leftTalon;
    private final ModelPart rightLeg;
    private final ModelPart rightTalon;
    private final ModelPart tail;
    private final ModelPart tailTip;
    
    public SubterranodonModel(ModelPart root) {
        super(root);
        this.root = root;
        this.body = root.getChild("body");
        this.neck = this.body.getChild("neck");
        this.head = this.neck.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.leftWing = this.body.getChild("left_wing");
        this.leftHand = this.leftWing.getChild("left_hand");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWing = this.body.getChild("right_wing");
        this.rightHand = this.rightWing.getChild("right_hand");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.leftLeg = this.body.getChild("left_leg");
        this.leftTalon = this.leftLeg.getChild("left_talon");
        this.rightLeg = this.body.getChild("right_leg");
        this.rightTalon = this.rightLeg.getChild("right_talon");
        this.tail = this.body.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
    }
    
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        
        // Body
        PartDefinition body = partDefinition.addOrReplaceChild("body",
            CubeListBuilder.create()
                .texOffs(0, 46)
                .addBox(-5.0F, -5.0F, -7.0F, 10.0F, 10.0F, 14.0F),
            PartPose.offset(0.0F, 19.0F, -1.0F));
        
        // Neck
        PartDefinition neck = body.addOrReplaceChild("neck",
            CubeListBuilder.create()
                .texOffs(74, 27)
                .addBox(-2.0F, -1.0F, -8.5F, 4.0F, 5.0F, 8.0F),
            PartPose.offset(0.0F, -4.0F, -6.5F));
        
        // Head
        PartDefinition head = neck.addOrReplaceChild("head",
            CubeListBuilder.create()
                .texOffs(48, 46)
                .addBox(0.0F, -15.0F, -13.0F, 0.0F, 12.0F, 19.0F)
                .texOffs(0, 79)
                .addBox(-3.0F, -6.0F, -8.0F, 6.0F, 7.0F, 9.0F)
                .texOffs(74, 10)
                .addBox(-1.0F, -3.0F, -22.0F, 2.0F, 3.0F, 14.0F)
                .texOffs(51, 78)
                .addBox(-2.0F, -7.0F, -32.0F, 4.0F, 9.0F, 10.0F),
            PartPose.offset(0.0F, 1.0F, -5.5F));
        
        // Jaw
        head.addOrReplaceChild("jaw",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.5F, -3.0F, -23.5F, 3.0F, 8.0F, 9.0F)
                .texOffs(71, 62)
                .addBox(-0.5F, -1.0F, -15.0F, 1.0F, 3.0F, 15.0F),
            PartPose.offset(0.0F, -1.0F, -8.0F));
        
        // Left Wing
        PartDefinition leftWing = body.addOrReplaceChild("left_wing",
            CubeListBuilder.create()
                .texOffs(12, 36)
                .addBox(0.5F, 0.5F, 2.0F, 26.0F, 0.0F, 10.0F)
                .texOffs(72, 42)
                .addBox(0.5F, -1.0F, -2.0F, 26.0F, 3.0F, 4.0F),
            PartPose.offset(4.5F, -3.0F, -5.0F));
        
        // Left Hand
        leftWing.addOrReplaceChild("left_hand",
            CubeListBuilder.create()
                .texOffs(30, 87)
                .addBox(-1.5F, 0.0F, -4.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offset(25.0F, 0.5F, -2.0F));
        
        // Left Wing Tip
        leftWing.addOrReplaceChild("left_wing_tip",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(0.0F, 0.0F, 1.0F, 26.0F, 0.0F, 24.0F)
                .texOffs(72, 49)
                .addBox(0.0F, -1.0F, -1.0F, 26.0F, 2.0F, 2.0F),
            PartPose.offset(26.5F, 0.5F, -1.0F));
        
        // Right Wing
        PartDefinition rightWing = body.addOrReplaceChild("right_wing",
            CubeListBuilder.create()
                .texOffs(12, 36).mirror()
                .addBox(-26.5F, 0.5F, 2.0F, 26.0F, 0.0F, 10.0F)
                .texOffs(72, 42).mirror()
                .addBox(-26.5F, -1.0F, -2.0F, 26.0F, 3.0F, 4.0F),
            PartPose.offset(-4.5F, -3.0F, -5.0F));
        
        // Right Hand
        rightWing.addOrReplaceChild("right_hand",
            CubeListBuilder.create()
                .texOffs(30, 87)
                .addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 4.0F),
            PartPose.offset(-25.0F, 2.0F, -2.0F));
        
        // Right Wing Tip
        rightWing.addOrReplaceChild("right_wing_tip",
            CubeListBuilder.create()
                .texOffs(72, 49).mirror()
                .addBox(-26.0F, -1.0F, -1.0F, 26.0F, 2.0F, 2.0F)
                .texOffs(0, 0).mirror()
                .addBox(-26.0F, 0.0F, 1.0F, 26.0F, 0.0F, 24.0F),
            PartPose.offset(-26.5F, 0.5F, -1.0F));
        
        // Left Leg
        PartDefinition leftLeg = body.addOrReplaceChild("left_leg",
            CubeListBuilder.create()
                .texOffs(25, 70)
                .addBox(-2.0F, 0.0F, -0.5F, 5.0F, 0.0F, 13.0F),
            PartPose.offset(2.0F, -2.5F, 6.5F));
        
        // Left Talon
        leftLeg.addOrReplaceChild("left_talon",
            CubeListBuilder.create()
                .texOffs(98, 30)
                .addBox(-2.5F, 0.0F, 0.0F, 5.0F, 4.0F, 4.0F),
            PartPose.offset(0.5F, 0.0F, 12.5F));
        
        // Right Leg
        PartDefinition rightLeg = body.addOrReplaceChild("right_leg",
            CubeListBuilder.create()
                .texOffs(25, 70).mirror()
                .addBox(-3.0F, 0.0F, -0.5F, 5.0F, 0.0F, 13.0F),
            PartPose.offset(-2.0F, -2.5F, 6.5F));
        
        // Right Talon
        rightLeg.addOrReplaceChild("right_talon",
            CubeListBuilder.create()
                .texOffs(98, 30).mirror()
                .addBox(-2.5F, 0.0F, 0.0F, 5.0F, 4.0F, 4.0F),
            PartPose.offset(-0.5F, 0.0F, 12.5F));
        
        // Tail
        PartDefinition tail = body.addOrReplaceChild("tail",
            CubeListBuilder.create()
                .texOffs(41, 46)
                .addBox(-3.0F, 0.0F, -0.5F, 6.0F, 0.0F, 19.0F),
            PartPose.offset(0.0F, -3.5F, 6.5F));
        
        // Tail Tip
        tail.addOrReplaceChild("tail_tip",
            CubeListBuilder.create()
                .texOffs(0, 51)
                .addBox(0.0F, -5.0F, 0.0F, 0.0F, 9.0F, 19.0F)
                .texOffs(29, 46)
                .addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 19.0F),
            PartPose.offset(0.0F, 0.0F, 18.5F));
        
        return LayerDefinition.create(meshDefinition, 256, 256);
    }
    
    @Override
    public void setupAnim(SubterranodonRenderState state) {
        super.setupAnim(state);
        
        // Reset all rotations to default
        this.body.resetPose();
        this.neck.resetPose();
        this.head.resetPose();
        this.jaw.resetPose();
        this.leftWing.resetPose();
        this.leftHand.resetPose();
        this.leftWingTip.resetPose();
        this.rightWing.resetPose();
        this.rightHand.resetPose();
        this.rightWingTip.resetPose();
        this.leftLeg.resetPose();
        this.leftTalon.resetPose();
        this.rightLeg.resetPose();
        this.rightTalon.resetPose();
        this.tail.resetPose();
        this.tailTip.resetPose();
        
        // Animation values
        float walkSpeed = 1.0F;
        float walkDegree = 1.0F;
        float flyProgress = state.isFlying ? 1.0F : 0.0F;
        float groundProgress = 1.0F - flyProgress;
        float flapAmount = state.isFlying ? (state.flapAmount * 0.2F) : 0.0F;
        float hoverProgress = state.isHovering ? 1.0F : 0.0F;
        float ageInTicks = state.ageInTicks;
        
        // Ground pose adjustments
        if (groundProgress > 0) {
            this.body.y += groundProgress * -8;
            this.body.z += groundProgress * 2;
            this.rightWing.x += groundProgress * 3;
            this.rightWing.y += groundProgress * -1;
            this.rightWing.z += groundProgress * 1;
            this.leftWing.x += groundProgress * -3;
            this.leftWing.y += groundProgress * -1;
            this.leftWing.z += groundProgress * 1;
            this.leftLeg.xRot += groundProgress * (float)Math.toRadians(-70);
            this.rightLeg.xRot += groundProgress * (float)Math.toRadians(-70);
            this.leftTalon.xRot += groundProgress * (float)Math.toRadians(-20);
            this.rightTalon.xRot += groundProgress * (float)Math.toRadians(-20);
            this.leftWing.yRot += groundProgress * (float)Math.toRadians(10);
            this.leftWing.zRot += groundProgress * (float)Math.toRadians(35);
            this.rightWing.yRot += groundProgress * (float)Math.toRadians(-10);
            this.rightWing.zRot += groundProgress * (float)Math.toRadians(-35);
            this.leftWingTip.yRot += groundProgress * (float)Math.toRadians(-10);
            this.leftWingTip.zRot += groundProgress * (float)Math.toRadians(-130);
            this.rightWingTip.yRot += groundProgress * (float)Math.toRadians(10);
            this.rightWingTip.zRot += groundProgress * (float)Math.toRadians(130);
            this.leftHand.yRot += groundProgress * (float)Math.toRadians(-10);
            this.leftHand.zRot += groundProgress * (float)Math.toRadians(-35);
            this.rightHand.yRot += groundProgress * (float)Math.toRadians(10);
            this.rightHand.zRot += groundProgress * (float)Math.toRadians(35);
            this.tail.xRot += groundProgress * (float)Math.toRadians(-20);
            this.tailTip.xRot += groundProgress * (float)Math.toRadians(10);
        }
        
        // Flying pose adjustments
        if (flyProgress > 0) {
            // Wing flapping
            if (flapAmount > 0) {
                float flapAngle = Mth.cos(ageInTicks * 0.5F) * flapAmount * (float)Math.toRadians(60);
                this.rightWing.zRot = -flapAngle;
                this.leftWing.zRot = flapAngle;
                float wingTipFlap = Mth.cos(ageInTicks * 0.5F) * flapAmount * 0.5F * (float)Math.toRadians(30);
                this.rightWingTip.zRot = -wingTipFlap;
                this.leftWingTip.zRot = wingTipFlap;
            }
            
            // Hovering pose
            if (hoverProgress > 0) {
                this.body.xRot += hoverProgress * (float)Math.toRadians(-50);
                this.neck.xRot += hoverProgress * (float)Math.toRadians(30);
                this.head.xRot += hoverProgress * (float)Math.toRadians(30);
                this.leftLeg.xRot += hoverProgress * (float)Math.toRadians(-40);
                this.rightLeg.xRot += hoverProgress * (float)Math.toRadians(-40);
                this.leftTalon.xRot += hoverProgress * (float)Math.toRadians(-20);
                this.rightTalon.xRot += hoverProgress * (float)Math.toRadians(-20);
            }
            
            // Leg movement in flight
            float legSwing = Mth.cos(ageInTicks * 0.3F) * 0.2F * flyProgress;
            this.leftLeg.xRot += legSwing;
            this.rightLeg.xRot += legSwing;
        }
        
        // Head look
        this.head.yRot = state.yRot * ((float)Math.PI / 180.0F) * 0.5F;
        this.head.xRot += state.xRot * ((float)Math.PI / 180.0F) * 0.5F;
        this.neck.yRot = state.yRot * ((float)Math.PI / 180.0F) * 0.3F;
        this.neck.xRot += state.xRot * ((float)Math.PI / 180.0F) * 0.3F;
        
        // Walking animation
        if (state.walkAnimationSpeed > 0 && groundProgress > 0) {
            float walkAnim = state.walkAnimationPos * walkSpeed;
            this.leftLeg.xRot += Mth.cos(walkAnim) * walkDegree * 0.6F * state.walkAnimationSpeed * groundProgress;
            this.rightLeg.xRot -= Mth.cos(walkAnim) * walkDegree * 0.6F * state.walkAnimationSpeed * groundProgress;
            this.leftTalon.xRot += Mth.cos(walkAnim + 1.0F) * walkDegree * 0.6F * state.walkAnimationSpeed * groundProgress;
            this.rightTalon.xRot -= Mth.cos(walkAnim + 1.0F) * walkDegree * 0.6F * state.walkAnimationSpeed * groundProgress;
        }
        
        // Tail sway
        float tailSway = Mth.cos(ageInTicks * 0.1F) * 0.2F;
        this.tail.yRot = tailSway * 0.8F;
        this.tailTip.yRot = tailSway * 0.2F;
    }
    
    /**
     * Gets the position of a leg for passenger positioning.
     * Used by SubterranodonRiderLayer.
     */
    public Vec3 getLegPosition(boolean right, Vec3 offset) {
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        
        // Apply body transformation
        this.body.translateAndRotate(poseStack);
        
        // Apply leg transformation
        if (right) {
            this.rightLeg.translateAndRotate(poseStack);
        } else {
            this.leftLeg.translateAndRotate(poseStack);
        }
        
        // Transform offset vector
        Vector4f vec = new Vector4f((float)offset.x, (float)offset.y, (float)offset.z, 1.0F);
        vec.mul(poseStack.last().pose());
        
        poseStack.popPose();
        
        return new Vec3(vec.x(), vec.y(), vec.z());
    }
}
