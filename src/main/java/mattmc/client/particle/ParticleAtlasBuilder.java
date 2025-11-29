package mattmc.client.particle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mattmc.util.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds a particle texture atlas by scanning particle texture directories.
 * 
 * <p>This mirrors Minecraft's atlas loading system where the atlas is generated
 * at runtime from textures under textures/particle/ configured via an atlas JSON.
 * 
 * <p>The atlas JSON (at atlases/particles.json) specifies sources:
 * <pre>
 * {
 *     "sources": [
 *         {
 *             "type": "directory",
 *             "source": "particle",
 *             "prefix": ""
 *         }
 *     ]
 * }
 * </pre>
 */
public class ParticleAtlasBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ParticleAtlasBuilder.class);
    private static final Gson GSON = new GsonBuilder().create();
    
    /** Default namespace for particle textures. */
    private static final String DEFAULT_NAMESPACE = "mattmc";
    
    /** Minimum texture size for the atlas. */
    private static final int MIN_ATLAS_SIZE = 64;
    
    /** Maximum texture size for the atlas. */
    private static final int MAX_ATLAS_SIZE = 4096;
    
    /**
     * Result of building a particle atlas.
     */
    public static class AtlasResult {
        /** The combined atlas image. */
        public final BufferedImage atlasImage;
        
        /** Width of the atlas. */
        public final int atlasWidth;
        
        /** Height of the atlas. */
        public final int atlasHeight;
        
        /** Sprites with their UV coordinates (keyed by resource location). */
        public final Map<ResourceLocation, SpriteEntry> sprites;
        
        /** The missing sprite placeholder. */
        public final SpriteEntry missingSprite;
        
        public AtlasResult(BufferedImage atlasImage, int atlasWidth, int atlasHeight,
                          Map<ResourceLocation, SpriteEntry> sprites, SpriteEntry missingSprite) {
            this.atlasImage = atlasImage;
            this.atlasWidth = atlasWidth;
            this.atlasHeight = atlasHeight;
            this.sprites = sprites;
            this.missingSprite = missingSprite;
        }
    }
    
    /**
     * Entry for a sprite in the atlas.
     */
    public static class SpriteEntry {
        public final ResourceLocation location;
        public final float u0, v0, u1, v1;
        public final int width, height;
        
        public SpriteEntry(ResourceLocation location, float u0, float v0, float u1, float v1, int width, int height) {
            this.location = location;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
        }
    }
    
    /**
     * Build the particle atlas by scanning the particle texture directory.
     * 
     * <p>This is called at runtime when the game starts, mirroring Minecraft's behavior.
     * 
     * @return The built atlas result
     */
    public static AtlasResult buildAtlas() {
        logger.info("Building particle texture atlas from textures/particle/...");
        
        // Load atlas configuration
        AtlasConfig config = loadAtlasConfig();
        
        // Collect all particle textures
        Map<ResourceLocation, BufferedImage> textures = collectTextures(config);
        logger.info("Found {} particle textures", textures.size());
        
        // Pack textures into atlas
        return packAtlas(textures);
    }
    
    /**
     * Load the atlas configuration from atlases/particles.json.
     */
    private static AtlasConfig loadAtlasConfig() {
        String configPath = "/assets/" + DEFAULT_NAMESPACE + "/atlases/particles.json";
        try (InputStream is = ParticleAtlasBuilder.class.getResourceAsStream(configPath)) {
            if (is != null) {
                JsonObject json = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                return AtlasConfig.fromJson(json);
            }
        } catch (Exception e) {
            logger.warn("Failed to load atlas config, using defaults", e);
        }
        
        // Default config: scan particle directory
        return AtlasConfig.defaultConfig();
    }
    
    /**
     * Collect all particle textures based on the atlas configuration.
     */
    private static Map<ResourceLocation, BufferedImage> collectTextures(AtlasConfig config) {
        Map<ResourceLocation, BufferedImage> textures = new LinkedHashMap<>();
        
        for (AtlasSource source : config.sources) {
            if ("directory".equals(source.type)) {
                collectFromDirectory(textures, source.source, source.prefix);
            }
        }
        
        return textures;
    }
    
    /**
     * Collect textures from a directory source.
     */
    private static void collectFromDirectory(Map<ResourceLocation, BufferedImage> textures, 
                                             String sourceDir, String prefix) {
        // Scan the textures/particle directory for .png files
        String basePath = "/assets/" + DEFAULT_NAMESPACE + "/textures/" + sourceDir;
        
        // Get list of files in the directory
        List<String> files = listResourceFiles(basePath, ".png");
        
        for (String filename : files) {
            String name = filename.substring(0, filename.length() - 4); // Remove .png
            String path = basePath + "/" + filename;
            
            try (InputStream is = ParticleAtlasBuilder.class.getResourceAsStream(path)) {
                if (is != null) {
                    BufferedImage image = ImageIO.read(is);
                    if (image != null) {
                        ResourceLocation location = new ResourceLocation(DEFAULT_NAMESPACE, prefix + name);
                        textures.put(location, image);
                        logger.debug("Loaded particle texture: {}", location);
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to load particle texture: {}", path, e);
            }
        }
    }
    
    /**
     * List resource files in a directory.
     * Since we can't list JAR resources directly, we use a manifest approach.
     */
    private static List<String> listResourceFiles(String basePath, String suffix) {
        List<String> files = new ArrayList<>();
        
        // Try to find a particle texture manifest
        String manifestPath = basePath + "/manifest.txt";
        try (InputStream is = ParticleAtlasBuilder.class.getResourceAsStream(manifestPath)) {
            if (is != null) {
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (!line.isEmpty() && line.endsWith(suffix)) {
                            files.add(line);
                        }
                    }
                }
                return files;
            }
        } catch (Exception e) {
            logger.warn("Failed to read manifest file: {}", manifestPath, e);
        }
        
        // No manifest found - this is expected for new resource packs
        // The manifest.txt file should be generated at build time or included in the JAR
        logger.debug("No manifest.txt found at {}, no textures will be loaded from this directory", basePath);
        
        return files;
    }
    
    /**
     * Pack textures into an atlas.
     */
    private static AtlasResult packAtlas(Map<ResourceLocation, BufferedImage> textures) {
        // Determine atlas size
        int textureCount = textures.size() + 1; // +1 for missing texture
        int maxTextureSize = 16; // Default particle texture size
        
        // Find the largest texture dimension
        for (BufferedImage img : textures.values()) {
            maxTextureSize = Math.max(maxTextureSize, Math.max(img.getWidth(), img.getHeight()));
        }
        
        // Calculate grid size
        int gridCols = (int) Math.ceil(Math.sqrt(textureCount));
        int gridRows = (int) Math.ceil((double) textureCount / gridCols);
        
        // Calculate atlas size (power of 2)
        int atlasWidth = nextPowerOf2(gridCols * maxTextureSize);
        int atlasHeight = nextPowerOf2(gridRows * maxTextureSize);
        
        // Clamp to limits
        atlasWidth = Math.min(Math.max(atlasWidth, MIN_ATLAS_SIZE), MAX_ATLAS_SIZE);
        atlasHeight = Math.min(Math.max(atlasHeight, MIN_ATLAS_SIZE), MAX_ATLAS_SIZE);
        
        // Create atlas image
        BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlasImage.createGraphics();
        
        // Clear to transparent
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, atlasWidth, atlasHeight);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        
        Map<ResourceLocation, SpriteEntry> sprites = new LinkedHashMap<>();
        
        // Add missing texture first
        BufferedImage missingTexture = createMissingTexture(maxTextureSize);
        g.drawImage(missingTexture, 0, 0, null);
        SpriteEntry missingSprite = new SpriteEntry(
            new ResourceLocation(DEFAULT_NAMESPACE, "missing"),
            0, 0,
            (float) maxTextureSize / atlasWidth,
            (float) maxTextureSize / atlasHeight,
            maxTextureSize, maxTextureSize
        );
        
        // Pack textures
        int x = maxTextureSize; // Start after missing texture
        int y = 0;
        
        for (Map.Entry<ResourceLocation, BufferedImage> entry : textures.entrySet()) {
            if (x + maxTextureSize > atlasWidth) {
                x = 0;
                y += maxTextureSize;
            }
            
            BufferedImage img = entry.getValue();
            
            // Draw texture at position
            g.drawImage(img, x, y, maxTextureSize, maxTextureSize, null);
            
            // Create sprite entry
            SpriteEntry sprite = new SpriteEntry(
                entry.getKey(),
                (float) x / atlasWidth,
                (float) y / atlasHeight,
                (float) (x + maxTextureSize) / atlasWidth,
                (float) (y + maxTextureSize) / atlasHeight,
                maxTextureSize, maxTextureSize
            );
            sprites.put(entry.getKey(), sprite);
            
            x += maxTextureSize;
        }
        
        g.dispose();
        
        logger.info("Built particle atlas: {}x{} with {} sprites", atlasWidth, atlasHeight, sprites.size());
        
        return new AtlasResult(atlasImage, atlasWidth, atlasHeight, sprites, missingSprite);
    }
    
    /**
     * Create the missing texture (magenta/black checkerboard).
     */
    private static BufferedImage createMissingTexture(int size) {
        // Ensure minimum size to prevent division by zero
        size = Math.max(size, 2);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int halfSize = Math.max(size / 2, 1); // Ensure halfSize is at least 1
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean checker = ((x / halfSize) + (y / halfSize)) % 2 == 0;
                int color = checker ? 0xFFFF00FF : 0xFF000000; // Magenta or black
                image.setRGB(x, y, color);
            }
        }
        return image;
    }
    
    /**
     * Calculate next power of 2.
     */
    private static int nextPowerOf2(int value) {
        int power = 1;
        while (power < value) {
            power *= 2;
        }
        return power;
    }
    
    /**
     * Atlas configuration.
     */
    private static class AtlasConfig {
        final List<AtlasSource> sources;
        
        AtlasConfig(List<AtlasSource> sources) {
            this.sources = sources;
        }
        
        static AtlasConfig fromJson(JsonObject json) {
            List<AtlasSource> sources = new ArrayList<>();
            
            if (json.has("sources")) {
                JsonArray sourcesArray = json.getAsJsonArray("sources");
                for (JsonElement element : sourcesArray) {
                    if (element.isJsonObject()) {
                        JsonObject sourceObj = element.getAsJsonObject();
                        String type = sourceObj.has("type") ? sourceObj.get("type").getAsString() : "directory";
                        String source = sourceObj.has("source") ? sourceObj.get("source").getAsString() : "particle";
                        String prefix = sourceObj.has("prefix") ? sourceObj.get("prefix").getAsString() : "";
                        sources.add(new AtlasSource(type, source, prefix));
                    }
                }
            }
            
            return new AtlasConfig(sources);
        }
        
        static AtlasConfig defaultConfig() {
            return new AtlasConfig(Collections.singletonList(
                new AtlasSource("directory", "particle", "")
            ));
        }
    }
    
    /**
     * Atlas source configuration.
     */
    private static class AtlasSource {
        final String type;
        final String source;
        final String prefix;
        
        AtlasSource(String type, String source, String prefix) {
            this.type = type;
            this.source = source;
            this.prefix = prefix;
        }
    }
}
