package net.minecraft.client.renderer.item;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiRasterSourceDiagnosticTest {
    @Test void diagnosticIsOptInBoundedAndDoesNotMutateQuads() {
        String key = "mattmc.dev.guiItemRasterTrace";
        String previous = System.getProperty(key);
        try (var pixels = new net.blaze3d.platform.NativeImage(32, 32, true)) {
            var state = new ItemStackRenderState();
            var quads = state.newLayer().prepareQuadList();
            var contents = new SpriteContents(ResourceLocation.withDefaultNamespace("item/apple"),
                new net.minecraft.client.resources.metadata.animation.FrameSize(32, 32), pixels);
            var sprite = new TextureAtlasSprite(ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                contents, 64, 64, 0, 0) {};
            var quad = new BakedQuad(new int[32], -1, Direction.SOUTH, sprite, false, 0);
            for (int i = 0; i < 12; i++) quads.add(quad);
            var before = java.util.List.copyOf(quads);
            System.clearProperty(key);
            assertEquals("disabled", state.describeGuiRasterSourceForDiagnostics());
            System.setProperty(key, "true");
            String description = state.describeGuiRasterSourceForDiagnostics();
            assertEquals(8, description.split(" sprite=", -1).length - 1);
            assertTrue(description.contains("minecraft:item/apple region=0,0,32,32"));
            assertEquals(before, quads);
            assertFalse(state.isEmpty());
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
}
