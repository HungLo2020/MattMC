package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelUnderminerDwarf;
import com.github.alexthe666.alexsmobs.client.model.layered.AMModelLayers;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerUnderminerItem;
import com.github.alexthe666.alexsmobs.entity.EntityUnderminer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RenderUnderminer extends MobRenderer<EntityUnderminer, UnderminerRenderState, EntityModel<UnderminerRenderState>> {
    private static final ResourceLocation TEXTURE_DWARF = ResourceLocation
            .withDefaultNamespace("textures/entity/underminer_dwarf.png");
    private static final ResourceLocation TEXTURE_0 = ResourceLocation
            .withDefaultNamespace("textures/entity/underminer_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation
            .withDefaultNamespace("textures/entity/underminer_1.png");
    public static final List<ResourceLocation> BREAKING_LOCATIONS = IntStream.range(0, 10)
            .mapToObj((destroyStage) -> ResourceLocation
                    .withDefaultNamespace("textures/block/ghostly_pickaxe/destroy_stage_" + destroyStage + ".png"))
            .collect(Collectors.toList());
    private final ModelUnderminerDwarf DWARF_MODEL;
    private final HumanoidModel<UnderminerRenderState> NORMAL_MODEL;
    private static final List<RenderType> DESTROY_TYPES = BREAKING_LOCATIONS.stream()
            .map(AMRenderTypes::getGhostCrumbling).collect(Collectors.toList());
    public static boolean renderWithPickaxe = false;

    public RenderUnderminer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelUnderminerDwarf(), 0.4F);
        DWARF_MODEL = (ModelUnderminerDwarf) this.model;
        NORMAL_MODEL = new HumanoidModel<>(
                Minecraft.getInstance().getEntityModels().bakeLayer(AMModelLayers.UNDERMINER));
        this.addLayer(new LayerUnderminerItem(this));
    }

    @Override
    public UnderminerRenderState createRenderState() {
        return new UnderminerRenderState();
    }

    @Override
    public void extractRenderState(EntityUnderminer entity, UnderminerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isDwarf = entity.isDwarf();
        state.variant = entity.getVariant();
        state.hidingProgress = entity.hidingProgress;
        state.prevHidingProgress = entity.prevHidingProgress;
        state.isFullyHidden = entity.isFullyHidden();
        state.miningPos = entity.getMiningPos();
        state.miningProgress = entity.getMiningProgress();
    }

    protected void scale(UnderminerRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.925F, 0.925F, 0.925F);
    }

    public boolean shouldRender(EntityUnderminer livingEntityIn, Frustum camera, double camX, double camY,
            double camZ) {
        if (super.shouldRender(livingEntityIn, camera, camX, camY, camZ)) {
            return true;
        } else {
            if (livingEntityIn.getMiningPos() != null) {
                BlockPos pos = livingEntityIn.getMiningPos();
                if (pos != null) {
                    Vec3 vector3d = Vec3.atLowerCornerOf(pos);
                    Vec3 vector3dCorner = Vec3.atLowerCornerOf(pos).add(1, 1, 1);
                    return camera.isVisible(new AABB(vector3d.x, vector3d.y, vector3d.z, vector3dCorner.x,
                            vector3dCorner.y, vector3dCorner.z));
                }
            }
            return false;
        }
    }

    @Override
    public void render(UnderminerRenderState state, PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn) {
        if (state.isDwarf) {
            this.model = DWARF_MODEL;
        } else {
            this.model = NORMAL_MODEL;
        }
        
        if (!state.isFullyHidden) {
            float hide = (state.prevHidingProgress + (state.hidingProgress - state.prevHidingProgress) * 1.0F) * 0.1F;
            float alpha = (1F - hide) * 0.6F;
            this.shadowRadius = 0.9F * alpha;
        } else {
            this.shadowRadius = 0;
        }
        
        super.render(state, matrixStackIn, bufferIn, packedLightIn);
        
        // Render mining breaking texture
        BlockPos miningPos = state.miningPos;
        if (miningPos != null) {
            matrixStackIn.pushPose();
            double d0 = state.x;
            double d1 = state.y;
            double d2 = state.z;

            matrixStackIn.translate((double) miningPos.getX() - d0, (double) miningPos.getY() - d1,
                    (double) miningPos.getZ() - d2);
            int progress = (int) Math
                    .round((DESTROY_TYPES.size() - 1) * (float) Mth.clamp(state.miningProgress, 0F, 1.0F));
            PoseStack.Pose posestack$pose = matrixStackIn.last();
            VertexConsumer vertexconsumer1 = new SheetedDecalTextureGenerator(
                    bufferIn.getBuffer(DESTROY_TYPES.get(progress)), posestack$pose,
                    1.0F);
            matrixStackIn.popPose();
        }
    }

    @Nullable
    protected RenderType getRenderType(UnderminerRenderState state, boolean normal, boolean invis, boolean outline) {
        ResourceLocation resourcelocation = this.getTextureLocation(state);
        return outline ? RenderType.outline(resourcelocation) : AMRenderTypes.getUnderminer(resourcelocation);
    }

    public ResourceLocation getTextureLocation(UnderminerRenderState state) {
        return state.isDwarf ? TEXTURE_DWARF : state.variant == 0 ? TEXTURE_0 : TEXTURE_1;
    }

}
