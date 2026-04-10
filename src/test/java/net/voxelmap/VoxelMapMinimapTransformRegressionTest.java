package net.voxelmap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VoxelMapMinimapTransformRegressionTest {
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

    @Test
    public void testMinimapHelperMatchesOriginalYAxisAndOffsetConvention() throws IOException {
        String mapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/Map.java"));
        String circularSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/util/CircularMaskBlitRenderState.java"));
        String squareSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/util/SquareMapBlitRenderState.java"));

        assertTrue(mapSource.contains("float sourceOffsetY = -this.percentY * 512.0F / 64.0F;"),
                "Map minimap helper should preserve the original Y offset sign from the direct transform path");
        assertTrue(circularSource.contains("float sourceX = cos * dx - sin * dy + this.sourceOffsetX();"),
                "Circular minimap helper should flip GUI-space Y before applying the legacy rotation");
        assertTrue(circularSource.contains("float sourceY = -sin * dx - cos * dy + this.sourceOffsetY();"),
                "Circular minimap helper should preserve the legacy Y-up source-space transform");
        assertTrue(circularSource.contains("float v = (256.0F - sourceY) / 512.0F;"),
                "Circular minimap helper should preserve the original top-origin V mapping");
        assertTrue(squareSource.contains("float sourceX = cos * dx - sin * dy + this.sourceOffsetX();"),
                "Square minimap helper should flip GUI-space Y before applying the legacy rotation");
        assertTrue(squareSource.contains("float sourceY = -sin * dx - cos * dy + this.sourceOffsetY();"),
                "Square minimap helper should preserve the legacy Y-up source-space transform");
        assertTrue(squareSource.contains("float v = (256.0F - sourceY) / 512.0F;"),
                "Square minimap helper should preserve the original top-origin V mapping");
    }
}