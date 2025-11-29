package net.matt.quantize.entities.render;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;

public class RenderSkeletonVariant extends SkeletonRenderer {
    private static final ResourceLocation[] VARIANTS = new ResourceLocation[] {
            new ResourceIdentifier("textures/model/entity/skeleton/skeleton.png"), // vanilla as one variant
            new ResourceIdentifier("textures/model/entity/skeleton/dungeons.png"),
            new ResourceIdentifier("textures/model/entity/skeleton/mossy.png"),
            new ResourceIdentifier("textures/model/entity/skeleton/sandy.png"),
            new ResourceIdentifier("textures/model/entity/skeleton/weathered.png"),

    };

    public RenderSkeletonVariant(EntityRendererProvider.Context ctx) {
        super(ctx); // keeps vanilla model & layers
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractSkeleton skeleton) {
        int idx = Math.floorMod(skeleton.getUUID().hashCode(), VARIANTS.length);
        return VARIANTS[idx];
    }
}
