package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelCapuchinMonkey;
import net.matt.quantize.entities.layer.LayerCapuchinItem;
import net.matt.quantize.entities.mobs.EntityCapuchinMonkey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCapuchinMonkey extends MobRenderer<EntityCapuchinMonkey, ModelCapuchinMonkey> {
    private static final ResourceLocation TEXTURE_0 = new ResourceIdentifier("textures/model/entity/capuchin_monkey/capuchin_monkey_0.png");
    private static final ResourceLocation TEXTURE_1 = new ResourceIdentifier("textures/model/entity/capuchin_monkey/capuchin_monkey_1.png");
    private static final ResourceLocation TEXTURE_2 = new ResourceIdentifier("textures/model/entity/capuchin_monkey/capuchin_monkey_2.png");
    private static final ResourceLocation TEXTURE_3 = new ResourceIdentifier("textures/model/entity/capuchin_monkey/capuchin_monkey_3.png");

    public RenderCapuchinMonkey(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCapuchinMonkey(), 0.25F);
        this.addLayer(new LayerCapuchinItem(this));
    }

    protected void scale(EntityCapuchinMonkey entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }

    public ResourceLocation getTextureLocation(EntityCapuchinMonkey entity) {
        return switch (entity.getVariant()) {
            case 1 -> TEXTURE_1;
            case 2 -> TEXTURE_2;
            case 3 -> TEXTURE_3;
            default -> TEXTURE_0;
        };
    }
}
