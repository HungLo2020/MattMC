package net.minecraft.client.renderer.shader.pack;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for discovering and managing shader packs baked into the game resources.
 * Scans assets/minecraft/shaders/ at runtime to find available shader packs.
 */
@Environment(EnvType.CLIENT)
public class ShaderPackRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SHADER_PACKS_RESOURCE_PATH = "shaders";
    
    // Directories to exclude from shader pack discovery (vanilla Minecraft shaders)
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of("core", "post", "include");
    
    private final ResourceManager resourceManager;
    private final List<ShaderPackMetadata> availablePacks;
    private ShaderPack activePack;
    
    public ShaderPackRepository(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.availablePacks = new ArrayList<>();
    }
    
    /**
     * Scans the resources directory for shader packs.
     * Each directory in assets/minecraft/shaders/ (except core, post, include) is treated as a shader pack.
     */
    public void scanForPacks() {
        availablePacks.clear();
        
        LOGGER.info("Scanning for baked-in shader packs in resources...");
        
        // List all resources under shaders/
        Map<ResourceLocation, Resource> shaderResources = resourceManager.listResources(
            SHADER_PACKS_RESOURCE_PATH,
            location -> {
                String path = location.getPath();
                // Exclude vanilla shader directories
                for (String excluded : EXCLUDED_DIRECTORIES) {
                    if (path.contains("/" + excluded + "/")) {
                        return false;
                    }
                }
                return true;
            }
        );
        
        // Extract unique shader pack names from resource paths
        Set<String> shaderPackNames = new HashSet<>();
        for (ResourceLocation location : shaderResources.keySet()) {
            String path = location.getPath();
            // Path format: "shaders/pack_name/..."
            String[] pathParts = path.split("/");
            if (pathParts.length >= 2 && pathParts[0].equals(SHADER_PACKS_RESOURCE_PATH)) {
                String packName = pathParts[1];
                if (!EXCLUDED_DIRECTORIES.contains(packName)) {
                    shaderPackNames.add(packName);
                }
            }
        }
        
        // Create metadata for each discovered shader pack
        for (String packName : shaderPackNames) {
            ShaderPackMetadata metadata = loadMetadata(packName);
            if (metadata != null) {
                availablePacks.add(metadata);
                LOGGER.debug("Found shader pack: {}", packName);
            }
        }
        
        LOGGER.info("Found {} baked-in shader pack(s)", availablePacks.size());
    }
    
    /**
     * Loads metadata for a shader pack.
     * Attempts to read pack.mcmeta, falls back to default metadata if not found.
     */
    private ShaderPackMetadata loadMetadata(String packName) {
        // Try to load pack.mcmeta for this shader pack
        ResourceLocation packMetaLocation = ResourceLocation.withDefaultNamespace(
            "shaders/" + packName + "/pack.mcmeta"
        );
        
        // For now, use default metadata
        // TODO: Parse pack.mcmeta if present
        return ShaderPackMetadata.createDefault(packName);
    }
    
    /**
     * Gets the list of all available shader packs.
     */
    public List<ShaderPackMetadata> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }
    
    /**
     * Loads a shader pack asynchronously.
     */
    public CompletableFuture<ShaderPack> loadShaderPack(ShaderPackMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Loading shader pack: {}", metadata.name());
                ShaderPackLoader loader = new ShaderPackLoader(resourceManager, metadata.resourcePath());
                return loader.load();
            } catch (Exception e) {
                LOGGER.error("Failed to load shader pack: {}", metadata.name(), e);
                return null;
            }
        });
    }
    
    /**
     * Sets the currently active shader pack.
     */
    public void setActivePack(ShaderPack pack) {
        if (this.activePack != null) {
            this.activePack.close();
        }
        this.activePack = pack;
        if (pack != null) {
            LOGGER.info("Activated shader pack: {}", pack.getName());
        } else {
            LOGGER.info("Deactivated shader pack (using vanilla rendering)");
        }
    }
    
    /**
     * Gets the currently active shader pack, if any.
     */
    public Optional<ShaderPack> getActivePack() {
        return Optional.ofNullable(activePack);
    }
    
    /**
     * Closes all resources.
     */
    public void close() {
        if (activePack != null) {
            activePack.close();
            activePack = null;
        }
    }
}
