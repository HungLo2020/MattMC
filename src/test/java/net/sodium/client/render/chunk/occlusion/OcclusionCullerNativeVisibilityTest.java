package net.sodium.client.render.chunk.occlusion;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OcclusionCullerNativeVisibilityTest {
    @Test
    void rustVisibilityEncodingReplacesJavaVisibilityEncodingClass() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/net/sodium/client/render/chunk/occlusion/VisibilityEncoding.java")));
    }

    @Test
    void rustVisibilityEncodingPreservesDirectionalConnections() {
        VisibilitySet visibilitySet = new VisibilitySet();
        visibilitySet.set(Direction.DOWN, Direction.NORTH, true);
        visibilitySet.set(Direction.WEST, Direction.EAST, true);

        long visibility = OcclusionCuller.encodeVisibility(visibilitySet);

        assertEquals(GraphDirectionSet.of(GraphDirection.NORTH),
                OcclusionCuller.getVisibilityConnections(visibility, GraphDirectionSet.of(GraphDirection.DOWN), true));
        assertEquals(GraphDirectionSet.of(GraphDirection.DOWN),
                OcclusionCuller.getVisibilityConnections(visibility, GraphDirectionSet.of(GraphDirection.NORTH), true));
        assertEquals(GraphDirectionSet.of(GraphDirection.DOWN)
                        | GraphDirectionSet.of(GraphDirection.NORTH)
                        | GraphDirectionSet.of(GraphDirection.WEST)
                        | GraphDirectionSet.of(GraphDirection.EAST),
                OcclusionCuller.getVisibilityConnections(visibility, GraphDirectionSet.NONE, false));
    }

    @Test
    void rustVisibilityEncodingPreservesAllDirectionsVisibility() {
        VisibilitySet visibilitySet = new VisibilitySet();
        visibilitySet.add(EnumSet.allOf(Direction.class));

        long visibility = OcclusionCuller.encodeVisibility(visibilitySet);

        assertEquals(GraphDirectionSet.ALL,
                OcclusionCuller.getVisibilityConnections(visibility, GraphDirectionSet.NONE, false));
        assertEquals(GraphDirectionSet.ALL,
                OcclusionCuller.getVisibilityConnections(visibility, GraphDirectionSet.of(GraphDirection.DOWN), true));
    }

}
