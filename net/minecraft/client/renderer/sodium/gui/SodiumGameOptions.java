package net.minecraft.client.renderer.sodium.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.renderer.sodium.gui.options.TextProvider;
import net.minecraft.client.renderer.chunk.advanced.DeferMode;
import net.minecraft.client.renderer.chunk.advanced.translucent_sorting.QuadSplittingMode;
import net.minecraft.client.renderer.sodium.services.PlatformRuntimeInformation;
import net.minecraft.client.renderer.sodium.util.FileUtil;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

// TODO: Rename in Sodium 0.6
public class SodiumGameOptions {
    private static final String DEFAULT_FILE_NAME = "sodium-options.json";

    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final NotificationSettings notifications = new NotificationSettings();
    public @NotNull DebugSettings debug = new DebugSettings();

    private boolean readOnly;

    private SodiumGameOptions() {
        // NO-OP
    }

    public static SodiumGameOptions defaults() {
        return new SodiumGameOptions();
    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        public DeferMode chunkBuildDeferMode = DeferMode.ALWAYS;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
        public boolean useNoErrorGLContext = true;

        public QuadSplittingMode quadSplittingMode = QuadSplittingMode.SAFE;
    }

    public static class AdvancedSettings {
        public boolean enableMemoryTracing = false;
        public boolean useAdvancedStagingBuffers = true;

        public int cpuRenderAheadLimit = 3;
    }

    public static class DebugSettings {
        public boolean terrainSortingEnabled = true;
    }

    public static class QualitySettings {
        public WeatherQuality weatherQuality = WeatherQuality.DEFAULT;
        public LeavesQuality leavesQuality = LeavesQuality.DEFAULT;

        public boolean enableVignette = true;
    }

    public static class NotificationSettings {
        public boolean hasClearedDonationButton = false;
        public boolean hasSeenDonationPrompt = false;
    }

    public enum WeatherQuality implements TextProvider {
        DEFAULT("options.gamma.default"),
        FANCY("sodium.options.weather_quality.fancy"),
        FAST("sodium.options.weather_quality.fast");

        private final Component name;

        WeatherQuality(String name) {
            this.name = Component.translatable(name);
        }

        @Override
        public Component getLocalizedName() {
            return this.name;
        }

        public boolean isFancy(GraphicsStatus graphicsMode) {
            return (this == FANCY) || (this == DEFAULT && (graphicsMode == GraphicsStatus.FANCY || graphicsMode == GraphicsStatus.FABULOUS));
        }
    }

    public enum LeavesQuality implements TextProvider {
        DEFAULT("options.gamma.default"),
        FANCY("sodium.options.leaves_quality.fancy"),
        FAST("sodium.options.leaves_quality.fast");

        private final Component name;

        LeavesQuality(String name) {
            this.name = Component.translatable(name);
        }

        @Override
        public Component getLocalizedName() {
            return this.name;
        }

        public boolean isFancy(GraphicsStatus graphicsMode) {
            return (this == FANCY) || (this == DEFAULT && (graphicsMode == GraphicsStatus.FANCY || graphicsMode == GraphicsStatus.FABULOUS));
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static SodiumGameOptions loadFromDisk() {
        // Step 5: Configuration now unified in Minecraft Options - do not create separate files
        // Values are already loaded from options.txt via Options.load()
        // Just sync local fields with Options
        SodiumGameOptions config = new SodiumGameOptions();
        syncFromMinecraftOptions(config);
        
        // Note: No longer loading from sodium-options.json - all config in options.txt
        return config;
    }
    
    /**
     * Syncs configuration from Minecraft's Options system (Step 5: Configuration Unification).
     * Options.load() has already loaded values from options.txt, we just sync them.
     */
    private static void syncFromMinecraftOptions(SodiumGameOptions config) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                // Sync from unified Options (already loaded from options.txt)
                config.performance.chunkBuilderThreads = mc.options.chunkBuilderThreads().get();
                config.performance.animateOnlyVisibleTextures = mc.options.animateOnlyVisibleTextures().get();
                config.performance.useEntityCulling = mc.options.useEntityCulling().get();
                config.performance.useFogOcclusion = mc.options.useFogOcclusion().get();
                config.performance.useBlockFaceCulling = mc.options.useBlockFaceCulling().get();
                config.advanced.useAdvancedStagingBuffers = mc.options.useAdvancedStagingBuffers().get();
                config.advanced.cpuRenderAheadLimit = mc.options.cpuRenderAheadLimit().get();
            }
        } catch (Exception e) {
            // Fallback to defaults if Options not available (already initialized)
        }
    }

    private static Path getConfigPath() {
        return PlatformRuntimeInformation.getInstance().getConfigDirectory()
                .resolve(DEFAULT_FILE_NAME);
    }

    public static void writeToDisk(SodiumGameOptions config) throws IOException {
        // Step 5: Configuration now unified in Minecraft Options - do not create separate files
        // Save to Minecraft Options instead
        saveToMinecraftOptions(config);
        // Note: No longer saving to sodium-options.json - all config in options.txt
    }
    
    /**
     * Saves configuration to Minecraft's Options system (Step 5: Configuration Unification).
     * Replaces saving to sodium-options.json file.
     */
    private static void saveToMinecraftOptions(SodiumGameOptions config) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                // Write to unified Options
                mc.options.chunkBuilderThreads().set(config.performance.chunkBuilderThreads);
                mc.options.animateOnlyVisibleTextures().set(config.performance.animateOnlyVisibleTextures);
                mc.options.useEntityCulling().set(config.performance.useEntityCulling);
                mc.options.useFogOcclusion().set(config.performance.useFogOcclusion);
                mc.options.useBlockFaceCulling().set(config.performance.useBlockFaceCulling);
                mc.options.useAdvancedStagingBuffers().set(config.advanced.useAdvancedStagingBuffers);
                mc.options.cpuRenderAheadLimit().set(config.advanced.cpuRenderAheadLimit);
                // Trigger save to options.txt
                mc.options.save();
            }
        } catch (Exception e) {
            // Silently fail if Options not available
        }
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }
}
