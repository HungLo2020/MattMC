package net.matt.quantize.entities.render;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;

public class RenderChickenVariant extends ChickenRenderer {
    private static final ResourceLocation[] VARIANTS = new ResourceLocation[] {
            new ResourceIdentifier("textures/model/entity/chicken/chicken.png"), // vanilla as one variant
            new ResourceIdentifier("textures/model/entity/chicken/chicken_warm.png"),
            new ResourceIdentifier("textures/model/entity/chicken/chicken_cold.png"),
            new ResourceIdentifier("textures/model/entity/chicken/amber.png"),
            new ResourceIdentifier("textures/model/entity/chicken/bone.png"),
            new ResourceIdentifier("textures/model/entity/chicken/bronzed.png"),
            new ResourceIdentifier("textures/model/entity/chicken/duck.png"),
            new ResourceIdentifier("textures/model/entity/chicken/gold_crested.png"),
            new ResourceIdentifier("textures/model/entity/chicken/midnight.png"),
            new ResourceIdentifier("textures/model/entity/chicken/skewbald.png"),
            new ResourceIdentifier("textures/model/entity/chicken/stormy.png")
    };

    public RenderChickenVariant(EntityRendererProvider.Context ctx) {
        super(ctx); // keeps vanilla model & layers
    }

    @Override
    public ResourceLocation getTextureLocation(Chicken chicken) {
        int idx = Math.floorMod(chicken.getUUID().hashCode(), VARIANTS.length);
        return VARIANTS[idx];
    }
}
