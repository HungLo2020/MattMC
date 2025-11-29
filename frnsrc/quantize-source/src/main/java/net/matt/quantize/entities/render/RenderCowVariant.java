package net.matt.quantize.entities.render;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;

public class RenderCowVariant extends CowRenderer {
    private static final ResourceLocation[] VARIANTS = new ResourceLocation[] {
            new ResourceIdentifier("textures/model/entity/cow/cow.png"),              // vanilla as one variant
            new ResourceIdentifier("textures/model/entity/cow/albino.png"),
            new ResourceIdentifier("textures/model/entity/cow/ashen.png"),
            new ResourceIdentifier("textures/model/entity/cow/cookie.png"),
            new ResourceIdentifier("textures/model/entity/cow/cream.png"),
            new ResourceIdentifier("textures/model/entity/cow/dairy.png"),
            new ResourceIdentifier("textures/model/entity/cow/pinto.png"),
            new ResourceIdentifier("textures/model/entity/cow/sunset.png"),
            new ResourceIdentifier("textures/model/entity/cow/umbra.png"),
            new ResourceIdentifier("textures/model/entity/cow/wooly.png")
    };

    public RenderCowVariant(EntityRendererProvider.Context ctx) {
        super(ctx); // keeps vanilla model & layers
    }

    @Override
    public ResourceLocation getTextureLocation(Cow cow) {
        int idx = Math.floorMod(cow.getUUID().hashCode(), VARIANTS.length);
        return VARIANTS[idx];
    }
}
