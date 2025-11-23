package mattmc.util;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized utility for loading resources from classpath.
 * Handles common resource loading patterns with proper error handling.
 * 
 * <p>All resource paths must start with a forward slash (/) and are relative
 * to the classpath root. For example: "/assets/textures/blocks/dirt.png"
 * 
 * <p>This class provides consistent error handling, UTF-8 encoding by default,
 * and proper resource cleanup via try-with-resources patterns.
 * 
 * <p>Example usage:
 * <pre>{@code
 * String shader = ResourceLoader.loadTextResource("/assets/shaders/basic.vert");
 * BlockModel model = ResourceLoader.loadJsonResource("/assets/models/block/dirt.json", gson, BlockModel.class);
 * }</pre>
 */
public final class ResourceLoader {
    private static final Logger logger = LoggerFactory.getLogger(ResourceLoader.class);
    
    private ResourceLoader() {} // Prevent instantiation
    
    /**
     * Load a resource as InputStream.
     * 
     * @param resourcePath Path to resource (must start with /)
     * @return InputStream or null if not found
     */
    public static InputStream getResourceStream(String resourcePath) {
        InputStream stream = ResourceLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            logger.warn("Resource not found: {}", resourcePath);
        }
        return stream;
    }
    
    /**
     * Load a resource as InputStream using ClassLoader (for paths without leading slash).
     * This is useful for compatibility with code that uses ClassLoader.getResourceAsStream().
     * 
     * @param resourcePath Path to resource (no leading slash, e.g., "assets/textures/block/dirt.png")
     * @return InputStream or null if not found
     */
    public static InputStream getResourceStreamFromClassLoader(String resourcePath) {
        InputStream stream = ResourceLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            logger.warn("Resource not found: {}", resourcePath);
        }
        return stream;
    }
    
    /**
     * Load a text resource as String.
     * 
     * @param resourcePath Path to resource (must start with /)
     * @return Resource content as String, or null if not found
     */
    public static String loadTextResource(String resourcePath) {
        try (InputStream is = getResourceStream(resourcePath)) {
            if (is == null) return null;
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            logger.error("Failed to load text resource: {}", resourcePath, e);
            return null;
        }
    }
    
    /**
     * Load a text resource as list of lines.
     * 
     * @param resourcePath Path to resource (must start with /)
     * @return List of lines, or empty list if not found
     */
    public static List<String> loadTextLines(String resourcePath) {
        try (InputStream is = getResourceStream(resourcePath)) {
            if (is == null) return List.of();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        } catch (IOException e) {
            logger.error("Failed to load text lines: {}", resourcePath, e);
            return List.of();
        }
    }
    
    /**
     * Load and parse a JSON resource.
     * 
     * @param resourcePath Path to JSON resource (must start with /)
     * @param gson Gson instance to use for parsing
     * @param clazz Target class type
     * @return Parsed object or null if loading/parsing failed
     */
    public static <T> T loadJsonResource(String resourcePath, Gson gson, Class<T> clazz) {
        try (InputStream is = getResourceStream(resourcePath)) {
            if (is == null) return null;
            
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, clazz);
            }
        } catch (Exception e) {
            logger.error("Failed to load/parse JSON resource: {}", resourcePath, e);
            return null;
        }
    }
    
    /**
     * Check if a resource exists.
     * 
     * @param resourcePath Path to resource (must start with /)
     * @return true if resource exists
     */
    public static boolean resourceExists(String resourcePath) {
        try (InputStream is = getResourceStream(resourcePath)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Load binary resource as byte array.
     * 
     * @param resourcePath Path to resource (must start with /)
     * @return Byte array or null if not found
     */
    public static byte[] loadBinaryResource(String resourcePath) {
        try (InputStream is = getResourceStream(resourcePath)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (IOException e) {
            logger.error("Failed to load binary resource: {}", resourcePath, e);
            return null;
        }
    }
}
