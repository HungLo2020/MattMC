package net.matt.quantize.entities.render;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

public class RenderZombieVariant extends ZombieRenderer {
    @SuppressWarnings("removal")
    private static final ResourceLocation[] VARIANTS = new ResourceLocation[] {
            new ResourceLocation("textures/entity/zombie/zombie.png"), // vanilla as one variant
            new ResourceIdentifier("textures/model/entity/zombie/alex.png"),
            new ResourceIdentifier("textures/model/entity/zombie/ari.png"),
            new ResourceIdentifier("textures/model/entity/zombie/efe.png"),
            new ResourceIdentifier("textures/model/entity/zombie/kai.png"),
            new ResourceIdentifier("textures/model/entity/zombie/makena.png"),
            new ResourceIdentifier("textures/model/entity/zombie/noor.png"),
            new ResourceIdentifier("textures/model/entity/zombie/sunny.png"),
            new ResourceIdentifier("textures/model/entity/zombie/zuri.png")

    };

    public RenderZombieVariant(EntityRendererProvider.Context ctx) {
        super(ctx); // keeps vanilla model & layers
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie zombie) {
        int idx = Math.floorMod(zombie.getUUID().hashCode(), VARIANTS.length);
        return VARIANTS[idx];
    }
}
