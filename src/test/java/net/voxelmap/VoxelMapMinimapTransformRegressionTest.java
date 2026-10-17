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
    public void testVoxelMapInitializesObserverDependenciesBeforePersistentMap() throws IOException {
        String voxelMapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/VoxelMap.java"));
        int notifier = voxelMapSource.indexOf("this.settingsAndLightingChangeNotifier = new SettingsAndLightingChangeNotifier();");
        int persistentMap = voxelMapSource.indexOf("this.persistentMap = new PersistentMap();");

        assertTrue(notifier >= 0 && persistentMap >= 0 && notifier < persistentMap,
                "VoxelMap must construct the settings notifier before PersistentMap registers observers");
    }

    @Test
    public void testRustVulkanFullscreenMapUsesTheSemanticDynamicTextureRoute() throws IOException {
        String mapSource = Files.readString(SRC_MAIN_JAVA.resolve("net/voxelmap/Map.java"));
        assertTrue(mapSource.contains("if (this.fullscreenMap)"),
                "Fullscreen VoxelMap must have an explicit Rust semantic branch");
        assertTrue(mapSource.contains("this.publishRustMapImage(mapTexture, dynamicMap)")
                        && mapSource.contains("RustGalGuiRawImageAssets.registerDynamicTexture(identity, texture, changed)"),
                "Fullscreen VoxelMap must publish its CPU dynamic map texture to Rust-owned GUI assets");
        assertTrue(mapSource.contains("drawContext.submitRustSemanticBlit(mapTexture"),
                "Fullscreen VoxelMap must submit a semantic blit instead of reopening Java GPU rendering");
        assertTrue(mapSource.contains("this.drawArrow(drawContext, this.scWidth / 2, this.scHeight / 2"),
                "Fullscreen VoxelMap must retain the centered player arrow");
    }
}
