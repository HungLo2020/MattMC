package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelBison;
import com.github.alexthe666.alexsmobs.client.model.ModelBisonBaby;
import com.github.alexthe666.alexsmobs.entity.EntityBison;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderBison extends MobRenderer<EntityBison, BisonRenderState, ModelBison> {
    private static final ResourceLocation TEXTURE_BABY = ResourceLocation.withDefaultNamespace("textures/entity/bison_baby.png");
    private static final ResourceLocation TEXTURE_BABY_SNOWY = ResourceLocation.withDefaultNamespace("textures/entity/bison_baby_snowy.png");
    private static final ResourceLocation TEXTURE_SNOWY = ResourceLocation.withDefaultNamespace("textures/entity/bison_snowy.png");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/bison.png");
    private static final ResourceLocation TEXTURE_SHEARED = ResourceLocation.withDefaultNamespace("textures/entity/bison_sheared.png");
    private final ModelBison modelBison = new ModelBison();
    private final ModelBisonBaby modelBaby = new ModelBisonBaby();

    public RenderBison(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelBison(), 0.8F);
    }

    @Override
    public BisonRenderState createRenderState() {
        return new BisonRenderState();
    }

    @Override
    public void extractRenderState(EntityBison entity, BisonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.chargeProgress = entity.prevChargeProgress + (entity.chargeProgress - entity.prevChargeProgress) * partialTick;
        state.isSheared = entity.isSheared();
        state.isSnowy = entity.isSnowy();
        state.isBaby = entity.isBaby();
    }



    public ResourceLocation getTextureLocation(BisonRenderState state) {
        if (state.isBaby) {
            return state.isSnowy ? TEXTURE_BABY_SNOWY : TEXTURE_BABY;
        } else {
            if (state.isSheared) {
                return TEXTURE_SHEARED;
            } else {
                return state.isSnowy ? TEXTURE_SNOWY : TEXTURE;
            }
        }
    }
}
