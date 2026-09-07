package net.minecraft.client.renderer.texture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Immutable resource extraction only; Rust owns clocks, visibility and uploads. */
public record SemanticAtlasAnimationSource(long generation, int width, int height,
    int mipCount, List<Sprite> sprites) {
    public SemanticAtlasAnimationSource {
        sprites = List.copyOf(sprites);
    }

    public record Sprite(int id, ResourceLocation name, int x, int y,
        SpriteContents.SemanticAnimationSource source) {}

    static SemanticAtlasAnimationSource copy(long generation, int width, int height,
        int mipCount, Map<ResourceLocation, TextureAtlasSprite> atlas) {
        if (generation <= 0 || width <= 0 || height <= 0
            || (long)width * height > 16L * 1024L * 1024L
            || mipCount <= 0 || mipCount > 32 || atlas.size() > 65536) {
            throw new IllegalArgumentException("Invalid semantic animation atlas");
        }
        // IDs are resource-incarnation-local, independent of map iteration.
        var animated = atlas.entrySet().stream()
            .filter(entry -> entry.getValue().contents().animatedTexture != null)
            .sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList();
        if (animated.size() > 16384) {
            throw new IllegalArgumentException("Animation sprite count exceeded");
        }
        long bytes = 0;
        long declarations = 0;
        // Whole-atlas preflight precedes the first pixel copy.
        for (var entry : animated) {
            TextureAtlasSprite sprite = entry.getValue();
            SpriteContents contents = sprite.contents();
            if (contents.byMipLevel.length != mipCount || sprite.getX() < 0 || sprite.getY() < 0
                || (long)sprite.getX() + contents.width() > width
                || (long)sprite.getY() + contents.height() > height) {
                throw new IllegalArgumentException("Animation placement or mip count mismatch");
            }
            declarations += contents.animatedTexture.frames.size() + mipCount;
            if (declarations > 65536 || contents.animatedTexture.frames.isEmpty()
                || contents.animatedTexture.frames.size() > 16384) {
                throw new IllegalArgumentException("Animation declaration count exceeded");
            }
            for (int mip = 0; mip < mipCount; mip++) {
                var image = contents.byMipLevel[mip];
                long alignment = 1L << mip;
                int frameWidth = contents.width() >> mip;
                int frameHeight = contents.height() >> mip;
                if (image == null || frameWidth <= 0 || frameHeight <= 0
                    || sprite.getX() % alignment != 0 || sprite.getY() % alignment != 0
                    || contents.width() % alignment != 0 || contents.height() % alignment != 0
                    || image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() % frameWidth != 0 || image.getHeight() % frameHeight != 0) {
                    throw new IllegalArgumentException("Invalid animation mip sheet or alignment");
                }
                bytes = Math.addExact(bytes, Math.multiplyExact(
                    Math.multiplyExact((long)image.getWidth(), image.getHeight()), 4L));
                if (bytes > 96L * 1024L * 1024L) {
                    throw new IllegalArgumentException("Animation atlas source byte bound exceeded");
                }
            }
        }
        List<Sprite> sprites = new ArrayList<>(animated.size());
        for (var entry : animated) {
            var sprite = entry.getValue();
            sprites.add(new Sprite(sprites.size() + 1, entry.getKey(), sprite.getX(), sprite.getY(),
                sprite.contents().semanticAnimationSource().orElseThrow()));
        }
        return new SemanticAtlasAnimationSource(generation, width, height, mipCount, sprites);
    }
}
