package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelUnderminerDwarf;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerUnderminerItem;
import com.github.alexthe666.alexsmobs.entity.EntityUnderminer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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

public class RenderUnderminer extends MobRenderer<EntityUnderminer, UnderminerRenderState, ModelUnderminerDwarf> {
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
    private static final List<RenderType> DESTROY_TYPES = BREAKING_LOCATIONS.stream()
            .map(AMRenderTypes::getGhostCrumbling).collect(Collectors.toList());
    public static boolean renderWithPickaxe = false;

    public RenderUnderminer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelUnderminerDwarf(), 0.4F);
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
        
        // Adjust shadow radius based on hiding progress
        if (!state.isFullyHidden) {
            float hide = (state.prevHidingProgress + (state.hidingProgress - state.prevHidingProgress) * partialTick) * 0.1F;
            float alpha = (1F - hide) * 0.6F;
            this.shadowRadius = 0.9F * alpha;
        } else {
            this.shadowRadius = 0;
        }
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

    @Nullable
    protected RenderType getRenderType(UnderminerRenderState state, boolean normal, boolean invis, boolean outline) {
        ResourceLocation resourcelocation = this.getTextureLocation(state);
        return outline ? RenderType.outline(resourcelocation) : AMRenderTypes.getUnderminer(resourcelocation);
    }

    public ResourceLocation getTextureLocation(UnderminerRenderState state) {
        return state.isDwarf ? TEXTURE_DWARF : state.variant == 0 ? TEXTURE_0 : TEXTURE_1;
    }

}
