package net.matt.quantize.mixin;

import net.matt.quantize.Quantize;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(WolfRenderer.class)
public class WolfEntityRendererMixin {
    private static final ResourceLocation WILD_PALE_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf.png");
    private static final ResourceLocation TAMED_PALE_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_tame.png");
    private static final ResourceLocation ANGRY_PALE_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_angry.png");

    private static final ResourceLocation WILD_ASHEN_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_ashen.png");
    private static final ResourceLocation TAMED_ASHEN_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_ashen_tame.png");
    private static final ResourceLocation ANGRY_ASHEN_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_ashen_angry.png");

    private static final ResourceLocation WILD_BLACK_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_black.png");
    private static final ResourceLocation TAMED_BLACK_TEXTURE = new ResourceIdentifier("textures/modeltextures/entity/wolf/wolf_black_tame.png");
    private static final ResourceLocation ANGRY_BLACK_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_black_angry.png");

    private static final ResourceLocation WILD_CHESTNUT_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_chestnut.png");
    private static final ResourceLocation TAMED_CHESTNUT_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_chestnut_tame.png");
    private static final ResourceLocation ANGRY_CHESTNUT_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_chestnut_angry.png");

    private static final ResourceLocation WILD_RUSTY_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_rusty.png");
    private static final ResourceLocation TAMED_RUSTY_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_rusty_tame.png");
    private static final ResourceLocation ANGRY_RUSTY_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_rusty_angry.png");

    private static final ResourceLocation WILD_SNOWY_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_snowy.png");
    private static final ResourceLocation TAMED_SNOWY_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_snowy_tame.png");
    private static final ResourceLocation ANGRY_SNOWY_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_snowy_angry.png");

    private static final ResourceLocation WILD_SPOTTED_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_spotted.png");
    private static final ResourceLocation TAMED_SPOTTED_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_spotted_tame.png");
    private static final ResourceLocation ANGRY_SPOTTED_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_spotted_angry.png");

    private static final ResourceLocation WILD_STRIPED_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_striped.png");
    private static final ResourceLocation TAMED_STRIPED_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_striped_tame.png");
    private static final ResourceLocation ANGRY_STRIPED_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_striped_angry.png");

    private static final ResourceLocation WILD_WOODS_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_woods.png");
    private static final ResourceLocation TAMED_WOODS_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_woods_tame.png");
    private static final ResourceLocation ANGRY_WOODS_TEXTURE = new ResourceIdentifier("textures/model/entity/wolf/wolf_woods_angry.png");


    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/animal/Wolf;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    public void getWolfTexture (Wolf wolfEntity, CallbackInfoReturnable<ResourceLocation> cir) {
        CompoundTag compound = new CompoundTag();
        wolfEntity.addAdditionalSaveData(compound);

        if (compound.contains("Variant")) {
            int wolfVariant = compound.getInt("Variant");
            ResourceLocation customTexture = getCustomTextureForVariant(wolfVariant, wolfEntity);
            cir.setReturnValue(customTexture);
        }
    }


    private ResourceLocation getCustomTextureForVariant(int variant, Wolf wolfEntity) {
        ResourceLocation texture;

        if(wolfEntity.isTame()) {
            texture = switch (variant) {
                default -> TAMED_PALE_TEXTURE;
                case 1 -> TAMED_WOODS_TEXTURE;
                case 2 -> TAMED_ASHEN_TEXTURE;
                case 3 -> TAMED_BLACK_TEXTURE;
                case 4 -> TAMED_CHESTNUT_TEXTURE;
                case 5 -> TAMED_RUSTY_TEXTURE;
                case 6 -> TAMED_SPOTTED_TEXTURE;
                case 7 -> TAMED_STRIPED_TEXTURE;
                case 8 -> TAMED_SNOWY_TEXTURE;
            };
        } else {
            if(wolfEntity.getRemainingPersistentAngerTime() > 0) {
                texture = switch (variant) {
                    default -> ANGRY_PALE_TEXTURE;
                    case 1 -> ANGRY_WOODS_TEXTURE;
                    case 2 -> ANGRY_ASHEN_TEXTURE;
                    case 3 -> ANGRY_BLACK_TEXTURE;
                    case 4 -> ANGRY_CHESTNUT_TEXTURE;
                    case 5 -> ANGRY_RUSTY_TEXTURE;
                    case 6 -> ANGRY_SPOTTED_TEXTURE;
                    case 7 -> ANGRY_STRIPED_TEXTURE;
                    case 8 -> ANGRY_SNOWY_TEXTURE;
                };
            } else {
                texture = switch (variant) {
                    default -> WILD_PALE_TEXTURE;
                    case 1 -> WILD_WOODS_TEXTURE;
                    case 2 -> WILD_ASHEN_TEXTURE;
                    case 3 -> WILD_BLACK_TEXTURE;
                    case 4 -> WILD_CHESTNUT_TEXTURE;
                    case 5 -> WILD_RUSTY_TEXTURE;
                    case 6 -> WILD_SPOTTED_TEXTURE;
                    case 7 -> WILD_STRIPED_TEXTURE;
                    case 8 -> WILD_SNOWY_TEXTURE;
                };
            }
        }

        return texture;
    }
}
