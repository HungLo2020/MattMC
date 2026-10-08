package net.minecraft.client.dev;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphicsAuditGroundFoilSourcesTest {
    private static BakedQuad quad() {
        BakedQuad quad = mock(BakedQuad.class, RETURNS_DEEP_STUBS);
        when(quad.sprite().contents().name()).thenReturn(ResourceLocation.parse("minecraft:item/clock_00"));
        when(quad.direction()).thenReturn(Direction.SOUTH);
        when(quad.getX(0)).thenReturn(1F);
        return quad;
    }

    @Test void snapshotsAreBoundedImmutableAndRequireSpecialResolvedLayers() {
        ItemStackRenderState state = new ItemStackRenderState();
        assertFalse(GraphicsAuditGroundFoilSources.capture(state, 1).get("complete").getAsBoolean());
        var layer = state.newLayer();
        layer.setFoilType(ItemStackRenderState.FoilType.SPECIAL);
        BakedQuad quad = quad();
        layer.prepareQuadList().add(quad);
        var first = GraphicsAuditGroundFoilSources.capture(state, 12);
        assertTrue(first.get("complete").getAsBoolean());
        assertEquals(12, first.get("renderedFrameIndex").getAsLong());
        assertEquals(1, first.getAsJsonArray("quads").size());
        when(quad.getX(0)).thenReturn(.5F);
        var second = GraphicsAuditGroundFoilSources.capture(state, 13);
        assertEquals(1F, first.getAsJsonArray("quads").get(0).getAsJsonObject().getAsJsonArray("positions").get(0).getAsFloat());
        assertEquals(.5F, second.getAsJsonArray("quads").get(0).getAsJsonObject().getAsJsonArray("positions").get(0).getAsFloat());
        layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        assertFalse(GraphicsAuditGroundFoilSources.capture(state, 14).get("complete").getAsBoolean());
        layer.setFoilType(ItemStackRenderState.FoilType.SPECIAL);
        for (int i = 1; i < 129; i++) layer.prepareQuadList().add(quad);
        var oversized = GraphicsAuditGroundFoilSources.capture(state, 15);
        assertFalse(oversized.get("complete").getAsBoolean());
        assertEquals(0, oversized.getAsJsonArray("quads").size());
    }

    @Test void invalidFrameAndNonfiniteSourceCannotProvideCompleteEvidence() {
        ItemStackRenderState state = new ItemStackRenderState();
        var layer = state.newLayer(); layer.setFoilType(ItemStackRenderState.FoilType.SPECIAL);
        BakedQuad quad = quad(); layer.prepareQuadList().add(quad);
        assertFalse(GraphicsAuditGroundFoilSources.capture(state, 0).get("complete").getAsBoolean());
        when(quad.getTexU(0)).thenReturn(Float.NaN);
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditGroundFoilSources.capture(state, 1));
    }
}
