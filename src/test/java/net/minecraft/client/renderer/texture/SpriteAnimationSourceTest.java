package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SpriteAnimationSourceTest {
    @Test
    void copiesFullSheetAndDeclaredTimelineWithoutCreatingTicker() throws Exception {
        NativeImage image = new NativeImage(2, 4, false);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 2; x++) image.setPixel(x, y, y < 2 ? 0xff123456 : 0xffabcdef);
        }
        AnimationMetadataSection metadata = new AnimationMetadataSection(Optional.of(List.of(
            new AnimationFrame(1, Optional.of(3)), new AnimationFrame(0, Optional.of(7)),
            new AnimationFrame(1, Optional.of(2)))), Optional.of(2), Optional.of(2), 1, true);
        try (SpriteContents sprite = new SpriteContents(ResourceLocation.withDefaultNamespace("audit/animated"),
            new FrameSize(2, 2), image, Optional.of(metadata), List.of())) {
            var source = sprite.semanticAnimationSource().orElseThrow();
            assertEquals(2, source.frameWidth());
            assertEquals(2, source.frameHeight());
            assertEquals(1, source.frameRowSize());
            assertTrue(source.interpolate());
            assertEquals(List.of(new SpriteContents.SemanticAnimationFrame(1, 3),
                new SpriteContents.SemanticAnimationFrame(0, 7),
                new SpriteContents.SemanticAnimationFrame(1, 2)), source.frames());
            assertEquals(1, source.mips().size());
            byte[] rgba = source.mips().getFirst().rgba();
            assertArrayEquals(new byte[]{0x12, 0x34, 0x56, (byte)255}, java.util.Arrays.copyOfRange(rgba, 0, 4));
            assertArrayEquals(new byte[]{(byte)0xab, (byte)0xcd, (byte)0xef, (byte)255},
                java.util.Arrays.copyOfRange(rgba, 16, 20));
            var ticker = SpriteContents.class.getDeclaredField("createdTicker");
            ticker.setAccessible(true);
            assertNull(ticker.get(sprite));
            image.setPixel(0, 0, 0xff000000);
            rgba[0] = 0;
            assertEquals(0x12, source.mips().getFirst().rgba()[0]);
            assertThrows(UnsupportedOperationException.class, () -> source.frames().clear());
            assertThrows(UnsupportedOperationException.class, () -> source.mips().clear());
        }
    }

    @Test
    void mipRecordCopiesCallerBytesAndRejectsOverflowDimensions() {
        byte[] bytes = {1, 2, 3, 4};
        var mip = new SpriteContents.SemanticAnimationMip(1, 1, bytes);
        bytes[0] = 99;
        assertEquals(1, mip.rgba()[0]);
        assertThrows(IllegalArgumentException.class,
            () -> new SpriteContents.SemanticAnimationMip(1 << 30, 1 << 30, new byte[0]));
        assertThrows(IllegalArgumentException.class,
            () -> new SpriteContents.SemanticAnimationMip(2, 1, new byte[4]));
    }
}
