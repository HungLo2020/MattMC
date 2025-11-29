package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelGeladaMonkey;
import net.matt.quantize.entities.mobs.EntityGeladaMonkey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderGeladaMonkey extends MobRenderer<EntityGeladaMonkey, ModelGeladaMonkey> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/gelada_monkey/gelada_monkey.png");
    private static final ResourceLocation TEXTURE_ANGRY = new ResourceIdentifier("textures/model/entity/gelada_monkey/gelada_monkey_angry.png");
    private static final ResourceLocation TEXTURE_LEADER = new ResourceIdentifier("textures/model/entity/gelada_monkey/gelada_monkey_leader.png");
    private static final ResourceLocation TEXTURE_LEADER_ANGRY = new ResourceIdentifier("textures/model/entity/gelada_monkey/gelada_monkey_leader_angry.png");

    public RenderGeladaMonkey(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGeladaMonkey(), 0.45F);
    }

    protected void scale(EntityGeladaMonkey entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(entitylivingbaseIn.getGeladaScale(), entitylivingbaseIn.getGeladaScale(), entitylivingbaseIn.getGeladaScale());
    }


    public ResourceLocation getTextureLocation(EntityGeladaMonkey entity) {
        return entity.isLeader() ? entity.isAggro() ? TEXTURE_LEADER_ANGRY : TEXTURE_LEADER : entity.isAggro() ? TEXTURE_ANGRY : TEXTURE;
    }
}
