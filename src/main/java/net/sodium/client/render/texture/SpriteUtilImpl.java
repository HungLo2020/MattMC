package net.sodium.client.render.texture;

import net.sodium.api.texture.SpriteUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SpriteUtilImpl implements SpriteUtil {
    @Override
    public void markSpriteActive(@NotNull TextureAtlasSprite sprite) {
        Objects.requireNonNull(sprite);

        if (net.vulkanic.world.WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
            net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
                sprite.semanticAnimationResource(), sprite.atlasLocation(), sprite.contents().name());
            return;
        }

        SpriteContentsExtension.setActive(sprite.contents(), true);
    }

    @Override
    public boolean hasAnimation(@NotNull TextureAtlasSprite sprite) {
        Objects.requireNonNull(sprite);

        return SpriteContentsExtension.hasAnimation(sprite.contents());
    }
}
