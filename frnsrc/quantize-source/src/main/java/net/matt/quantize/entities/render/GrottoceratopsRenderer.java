package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.GrottoceratopsModel;
import net.matt.quantize.entities.mobs.GrottoceratopsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GrottoceratopsRenderer extends MobRenderer<GrottoceratopsEntity, GrottoceratopsModel> {
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/grottoceratops/grottoceratops.png");
    private static final ResourceLocation TEXTURE_BABY = new ResourceIdentifier("textures/model/entity/grottoceratops/grottoceratops_baby.png");
    private static final ResourceLocation TEXTURE_RETRO = new ResourceIdentifier("textures/model/entity/grottoceratops/grottoceratops_retro.png");
    private static final ResourceLocation TEXTURE_RETRO_BABY = new ResourceIdentifier("textures/model/entity/grottoceratops/grottoceratops_retro_baby.png");
    private static final ResourceLocation TEXTURE_TECTONIC = new ResourceIdentifier("textures/model/entity/grottoceratops/grottoceratops_tectonic.png");
    private static final ResourceLocation TEXTURE_TECTONIC_BABY = new ResourceIdentifier("textures/model/entity/grottoceratops/grottoceratops_tectonic_baby.png");

    public GrottoceratopsRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new GrottoceratopsModel(), 1.1F);
    }

    protected void scale(GrottoceratopsEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }

    public ResourceLocation getTextureLocation(GrottoceratopsEntity entity) {
        return entity.getAltSkin() == 1 ? entity.isBaby() ? TEXTURE_RETRO_BABY : TEXTURE_RETRO : entity.getAltSkin() == 2 ? entity.isBaby() ? TEXTURE_TECTONIC_BABY : TEXTURE_TECTONIC : entity.isBaby() ? TEXTURE_BABY : TEXTURE;
    }
}

