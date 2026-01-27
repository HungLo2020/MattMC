package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelAnaconda;
import com.github.alexthe666.alexsmobs.entity.EntityAnacondaPart;
import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceLocation;

public class RenderAnacondaPart extends EntityRenderer<EntityAnacondaPart, EntityRenderState> {
    private final ModelAnaconda neckModel = new ModelAnaconda(AnacondaPartIndex.NECK);
    private final ModelAnaconda bodyModel = new ModelAnaconda(AnacondaPartIndex.BODY);
    private final ModelAnaconda tailModel = new ModelAnaconda(AnacondaPartIndex.TAIL);

    public RenderAnacondaPart(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void extractRenderState(EntityAnacondaPart entity, EntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    public ResourceLocation getTextureLocation(EntityRenderState state) {
        return ResourceLocation.withDefaultNamespace("textures/entity/anaconda.png");
    }
}
