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

    @Test
    public void testRustVulkanMinimapRemainsFailClosedUntilSemanticTextureRouteExists() throws IOException {
        String guiSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/Gui.java"));

        assertTrue(guiSource.contains("RustGalVulkanWholeFrameMode.enabled()"),
                "The minimap callsite must inspect the Rust whole-frame Vulkan route");
        String constantsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/VoxelConstants.java"));
        assertTrue(constantsSource.contains("renderRustSemanticOverlay(guiGraphics)"),
                "Rust Vulkan must invoke the VoxelMap semantic producer at the HUD callsite");
        assertTrue(constantsSource.contains("overlay is unavailable for the current waypoint/settings state"),
                "Rust Vulkan must fail closed instead of invoking VoxelMap's Java GPU renderer");
    }

    @Test
    public void testVoxelMapInitializesObserverDependenciesBeforePersistentMap() throws IOException {
        String voxelMapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/VoxelMap.java"));
        int notifier = voxelMapSource.indexOf("this.settingsAndLightingChangeNotifier = new SettingsAndLightingChangeNotifier();");
        int persistentMap = voxelMapSource.indexOf("this.persistentMap = new PersistentMap();");

        assertTrue(notifier >= 0 && persistentMap >= 0 && notifier < persistentMap,
                "VoxelMap must construct the settings notifier before PersistentMap registers observers");
    }

    @Test
    public void testRustVulkanWaypointAtlasUsesCpuSemanticStitching() throws IOException {
        String atlasSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/textures/TextureAtlas.java"));

        assertTrue(atlasSource.contains("finishSemanticStitch()"),
                "Rust Vulkan waypoint atlases must have an explicit CPU semantic stitching path");
        assertTrue(atlasSource.contains("RustGalGuiRawImageAssets.registerDynamicTexture"),
                "The waypoint atlas must publish a copied semantic image identity");
        assertTrue(atlasSource.contains("if (RustGalVulkanWholeFrameMode.enabled())"),
                "Rust Vulkan atlas construction must branch before Java GPU allocation");
        int semanticBranch = atlasSource.indexOf("if (RustGalVulkanWholeFrameMode.enabled())");
        int gpuAllocation = atlasSource.indexOf("texture = net.vulkanic.VulkanicAPI.createTexture");
        assertTrue(semanticBranch >= 0 && gpuAllocation > semanticBranch,
                "Rust Vulkan atlas stitching must branch before Java GPU texture creation");

        String mapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/Map.java"));
        assertTrue(mapSource.contains("drawWaypointSemantic"),
                "Rust Vulkan minimap waypoint icons must have a semantic producer");
        assertTrue(mapSource.contains("submitRustSemanticBlit(icon"),
                "Semantic waypoint icons must use explicit copied resource identities");
        assertTrue(mapSource.contains("\"markersmall\"") && mapSource.contains("\"waypointsmall\""),
                "Semantic waypoint icon identities must preserve the resource pack's lowercase paths");
    }

    @Test
    public void testRustVulkanFullscreenMapUsesTheSemanticDynamicTextureRoute() throws IOException {
        String mapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/Map.java"));
        assertTrue(mapSource.contains("if (this.fullscreenMap)"),
                "Fullscreen VoxelMap must have an explicit Rust semantic branch");
        assertTrue(mapSource.contains("RustGalGuiRawImageAssets.registerDynamicTexture(mapTexture, dynamicMap)"),
                "Fullscreen VoxelMap must publish its CPU dynamic map texture to Rust-owned GUI assets");
        assertTrue(mapSource.contains("drawContext.submitRustSemanticBlit(mapTexture"),
                "Fullscreen VoxelMap must submit a semantic blit instead of reopening Java GPU rendering");
        assertTrue(mapSource.contains("this.drawArrow(drawContext, this.scWidth / 2, this.scHeight / 2"),
                "Fullscreen VoxelMap must retain the centered player arrow");
    }
}
