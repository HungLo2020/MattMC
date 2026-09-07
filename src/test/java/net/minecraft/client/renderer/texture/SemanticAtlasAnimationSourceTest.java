package net.minecraft.client.renderer.texture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.blaze3d.platform.NativeImage;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticAtlasAnimationSourceTest {
    private static final ResourceLocation ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");

    private static SpriteContents animated(String name) {
        return animated(name, new int[1]);
    }

    private static SpriteContents animated(String name, int[] copies) {
        var image = new NativeImage(1, 2, false);
        image.setPixel(0, 0, 0xff123456);
        image.setPixel(0, 1, 0xffabcdef);
        return new SpriteContents(ResourceLocation.withDefaultNamespace(name), new FrameSize(1, 1), image,
            Optional.of(new AnimationMetadataSection(Optional.of(List.of(
                new AnimationFrame(1, Optional.of(3)), new AnimationFrame(0, Optional.of(7)))),
                Optional.of(1), Optional.of(1), 1, true)), List.of()) {
            @Override public Optional<SemanticAnimationSource> semanticAnimationSource() {
                copies[0]++;
                return super.semanticAnimationSource();
            }
        };
    }

    @Test
    void extractsStableResourceIdentitiesAndPlacementWithoutTickerOrGpu() throws Exception {
        try (var a = animated("audit/a"); var z = animated("audit/z")) {
            var atlas = new LinkedHashMap<ResourceLocation, TextureAtlasSprite>();
            atlas.put(z.name(), new TextureAtlasSprite(ATLAS, z, 4, 2, 3, 1));
            atlas.put(a.name(), new TextureAtlasSprite(ATLAS, a, 4, 2, 1, 0));
            var copy = SemanticAtlasAnimationSource.copy(7, 4, 2, 1, atlas);
            assertEquals(7, copy.generation());
            assertEquals(List.of(a.name(), z.name()), copy.sprites().stream().map(s -> s.name()).toList());
            assertEquals(1, copy.sprites().getFirst().id());
            assertEquals(2, copy.sprites().getLast().id());
            assertEquals(3, copy.sprites().getLast().x());
            assertEquals(1, copy.sprites().getLast().y());
            assertEquals(List.of(new SpriteContents.SemanticAnimationFrame(1, 3),
                new SpriteContents.SemanticAnimationFrame(0, 7)), copy.sprites().getFirst().source().frames());
            assertTrue(copy.sprites().getFirst().source().interpolate());
            assertArrayEquals(new byte[]{0x12, 0x34, 0x56, (byte)255, (byte)0xab, (byte)0xcd, (byte)0xef, (byte)255},
                copy.sprites().getFirst().source().mips().getFirst().rgba());
            a.byMipLevel[0].setPixel(0, 0, 0);
            assertEquals(0x12, copy.sprites().getFirst().source().mips().getFirst().rgba()[0]);
            var ticker = SpriteContents.class.getDeclaredField("createdTicker");
            ticker.setAccessible(true);
            assertNull(ticker.get(a));
            assertNull(ticker.get(z));
            assertThrows(UnsupportedOperationException.class, () -> copy.sprites().clear());
        }
    }

    @Test
    void rejectsBadPlacementAndMipCountBeforeAnyPixelExtraction() {
        int[] copies = new int[1];
        try (var a = animated("audit/a", copies); var z = animated("audit/z")) {
            var outside = Map.of(a.name(), new TextureAtlasSprite(ATLAS, a, 4, 2, 4, 0));
            assertThrows(IllegalArgumentException.class, () -> SemanticAtlasAnimationSource.copy(1, 4, 2, 1, outside));
            var inside = Map.of(a.name(), new TextureAtlasSprite(ATLAS, a, 4, 2, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> SemanticAtlasAnimationSource.copy(1, 4, 2, 2, inside));
            assertThrows(IllegalArgumentException.class, () -> SemanticAtlasAnimationSource.copy(0, 4, 2, 1, inside));
            var invalidLaterSprite = new LinkedHashMap<>(inside);
            invalidLaterSprite.put(z.name(), new TextureAtlasSprite(ATLAS, z, 4, 2, 4, 0));
            assertThrows(IllegalArgumentException.class,
                () -> SemanticAtlasAnimationSource.copy(1, 4, 2, 1, invalidLaterSprite));
            assertEquals(0, copies[0], "later invalid placement must reject before copying the valid first sheet");
        }
    }
}
