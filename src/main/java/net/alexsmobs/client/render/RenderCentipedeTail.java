package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCaveCentipede;
import net.alexsmobs.entity.EntityCentipedeTail;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RenderCentipedeTail extends MobRenderer<EntityCentipedeTail, CentipedeTailRenderState, ModelCaveCentipede> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cave_centipede.png");

    public RenderCentipedeTail(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCaveCentipede(2), 0.5F);
    }

    @Override
    public CentipedeTailRenderState createRenderState() {
        return new CentipedeTailRenderState();
    }

    @Override
    public void extractRenderState(EntityCentipedeTail entity, CentipedeTailRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.bodyIndex = entity.getBodyIndex();
        state.isDeadOrDying = entity.isDeadOrDying();
        state.deathTime = entity.deathTime;
    }

    protected void scale(CentipedeTailRenderState state, PoseStack matrixStackIn) {
        if (state.isDeadOrDying) {
            float f = ((float) state.deathTime + state.ageInTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }
            matrixStackIn.translate(0, f * 1.15F, 0);
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(f * 180.0F));
        }
    }

    public ResourceLocation getTextureLocation(CentipedeTailRenderState state) {
        return TEXTURE;
    }
}
