package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelElephant;
import net.alexsmobs.client.render.layer.LayerElephantItem;
import net.alexsmobs.client.render.layer.LayerElephantOverlays;
import net.alexsmobs.entity.EntityElephant;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderElephant extends MobRenderer<EntityElephant, ElephantRenderState, ModelElephant> {
    private static final ResourceLocation TEXTURE_TUSK = ResourceLocation.withDefaultNamespace("textures/entity/elephant/elephant_tusks.png");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/elephant/elephant.png");

    public RenderElephant(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelElephant(0), 1.4F);
        this.addLayer(new LayerElephantOverlays(this));
        this.addLayer(new LayerElephantItem(this));
    }

    @Override
    public ElephantRenderState createRenderState() {
        return new ElephantRenderState();
    }

    @Override
    public void extractRenderState(EntityElephant entity, ElephantRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.standProgress = entity.prevStandProgress + (entity.standProgress - entity.prevStandProgress) * partialTick;
        state.tusked = entity.isTusked();
        state.sitting = entity.isSitting();
        state.standing = entity.isStanding();
        state.chested = entity.isChested();
        state.carpetColor = entity.getColor() != null ? entity.getColor().getId() : -1;
        this.itemModelResolver.updateForLiving(state.mainHandItem, entity.getMainHandItem(), net.minecraft.world.item.ItemDisplayContext.GROUND, entity);
    }

    protected void scale(ElephantRenderState state, PoseStack matrixStackIn) {
       if(state.tusked){
           matrixStackIn.scale(1.1F, 1.1F, 1.1F);
       }
    }


    public ResourceLocation getTextureLocation(ElephantRenderState state) {
        return state.tusked && !state.isBaby ? TEXTURE_TUSK : TEXTURE;
    }
}
