package net.minecraft.client.renderer.sodium.render.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

// Kept for mod compatibility, to be removed in next major release.
@Deprecated(forRemoval = true)
public class SpriteUtil {
    @Deprecated(forRemoval = true)
    public static void markSpriteActive(@Nullable TextureAtlasSprite sprite) {
        if (sprite != null) {
            net.minecraft.client.renderer.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }

    @Deprecated(forRemoval = true)
    public static boolean hasAnimation(@Nullable TextureAtlasSprite sprite) {
        if (sprite != null) {
            return net.minecraft.client.renderer.sodium.api.texture.SpriteUtil.INSTANCE.hasAnimation(sprite);
        }

        return false;
    }
}
