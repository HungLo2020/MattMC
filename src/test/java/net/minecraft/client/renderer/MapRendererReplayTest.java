package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

class MapRendererReplayTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void primarySubmissionPublishesOneImageAndOneDecoration() {
        var state = decoratedMap();
        var collector = mock(SubmitNodeCollector.class);
        when(collector.submitMapTexturedQuadSemantic(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(true);
        var poses = new PoseStack();
        renderer().render(state, poses, collector, true, 15728640);
        assertTrue(poses.isEmpty(), "primary map submission preserves the caller pose stack");
        verify(collector).submitMapTexturedQuadSemantic(any(), eq(state.texture), any(), any(), eq(-1), eq(15728640));
        verify(collector).submitMapTexturedQuadSemantic(any(), eq(Sheets.MAP_DECORATIONS_SHEET), any(), any(), eq(-1), eq(15728640));
        verify(collector, times(2)).submitMapTexturedQuadSemantic(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void coverageAndTextReplayPreservesLabelsWithoutDuplicatingGeometry() throws Exception {
        var state = decoratedMap();
        var label = Component.literal("Map landmark");
        state.decorations.getFirst().name = label;
        var collector = mock(SubmitNodeCollector.class);
        when(collector.isSemanticCoverageOnly()).thenReturn(true);
        when(collector.order(1)).thenReturn(collector);
        var minecraft = mock(Minecraft.class);
        var font = mock(Font.class);
        var field = Minecraft.class.getDeclaredField("font");
        field.setAccessible(true);
        field.set(minecraft, font);
        when(font.width(label)).thenReturn(24);
        var poses = new PoseStack();
        poses.translate(2.0F, -3.0F, 5.0F);
        var callerPose = new Matrix4f(poses.last().pose());
        float scale = 6.0F / 9.0F;
        var expectedLabelPose = new Matrix4f(callerPose)
            .translate(24 / 2.0F + 64.0F - 24 * scale / 2.0F, -16 / 2.0F + 64.0F + 4.0F, -0.025F)
            .scale(scale, scale, -1.0F).translate(0.0F, 0.0F, 0.1F);
        doAnswer(invocation -> {
            PoseStack submitted = invocation.getArgument(0);
            assertTrue(expectedLabelPose.equals(submitted.last().pose(), 1.0E-6F),
                "labels retain the caller-space Frozen transform during text replay");
            return true;
        }).when(collector).submitTextSemantic(any(), anyFloat(), anyFloat(), any(), anyBoolean(),
            any(), anyInt(), anyInt(), anyInt(), anyInt());
        try (var client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            renderer().render(state, poses, collector, true, 15728640);
        }
        assertTrue(poses.isEmpty(), "text replay preserves the caller pose stack");
        assertEquals(callerPose, poses.last().pose());
        verify(collector, never()).submitMapTexturedQuadSemantic(any(), any(), any(), any(), anyInt(), anyInt());
        verify(collector).submitTextSemantic(any(), eq(0.0F), eq(0.0F), any(), eq(false),
            eq(Font.DisplayMode.NORMAL), eq(15728640), eq(-1), eq(Integer.MIN_VALUE), eq(0));
    }

    private static MapRenderer renderer() {
        var atlases = mock(AtlasManager.class);
        when(atlases.getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)).thenReturn(mock(TextureAtlas.class));
        return new MapRenderer(atlases, null);
    }

    private static MapRenderState decoratedMap() {
        var state = new MapRenderState();
        state.texture = ResourceLocation.withDefaultNamespace("map/1234570");
        var decoration = new MapRenderState.MapDecorationRenderState();
        decoration.decorationIdentity = ResourceLocation.withDefaultNamespace("red_x");
        decoration.renderOnFrame = true;
        decoration.x = 24;
        decoration.y = -16;
        decoration.rot = 5;
        decoration.atlasSprite = mock(TextureAtlasSprite.class);
        when(decoration.atlasSprite.atlasLocation()).thenReturn(Sheets.MAP_DECORATIONS_SHEET);
        when(decoration.atlasSprite.getU1()).thenReturn(1.0F);
        when(decoration.atlasSprite.getV1()).thenReturn(1.0F);
        state.decorations.add(decoration);
        return state;
    }
}
