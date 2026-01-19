package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelBlobfish;
import com.github.alexthe666.alexsmobs.client.model.ModelBlobfishDepressurized;
import com.github.alexthe666.alexsmobs.entity.EntityBlobfish;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;

public class RenderBlobfish extends MobRenderer<EntityBlobfish, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/blobfish.png");
    private static final ResourceLocation TEXTURE_DEPRESSURIZED = ResourceLocation.withDefaultNamespace("textures/entity/blobfish_depressurized.png");
    private final ModelBlobfish modelFish;
    private final ModelBlobfishDepressurized modelDepressurized;
    private boolean useDepressurized = false;
    private float blobfishScale = 1.0F;

    public RenderBlobfish(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelBlobfish(), 0.35F);
        this.modelFish = (ModelBlobfish)this.model;
        this.modelDepressurized = new ModelBlobfishDepressurized();
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public void extractRenderState(EntityBlobfish entity, LivingEntityRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        useDepressurized = entity.isDepressurized();
        blobfishScale = entity.getBlobfishScale();
        
        // Switch the model based on depressurized state before rendering
        if (useDepressurized) {
            this.model = modelDepressurized;
        } else {
            this.model = modelFish;
        }
    }

    @Override
    protected void scale(LivingEntityRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(blobfishScale, blobfishScale, blobfishScale);
    }

    public ResourceLocation getTextureLocation(LivingEntityRenderState renderState) {
        return useDepressurized ? TEXTURE_DEPRESSURIZED : TEXTURE;
    }
}
