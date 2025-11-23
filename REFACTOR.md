# MattMC Refactoring Opportunities

This document provides a detailed analysis of refactoring opportunities in the MattMC codebase. The focus is on extracting commonly used patterns into smaller utility classes to improve code maintainability, reduce duplication, and enhance readability without compromising performance or breaking existing functionality.

## Executive Summary

After analyzing the 153 Java files (~27,000 lines of code) in the MattMC project, I've identified **7 major categories** of refactoring opportunities that could significantly improve code quality:

1. **RGB Color Manipulation** - 15+ duplicate implementations
2. **Window/Framebuffer Coordinate Conversion** - 10+ duplicate implementations  
3. **OpenGL State Management** - Scattered across 30+ files
4. **Resource Loading Patterns** - 11+ similar implementations
5. **Chunk Coordinate Conversions** - Repeated calculations throughout
6. **GLFW Callback Setup** - Boilerplate repeated in every screen
7. **Math and Validation Utilities** - Common operations scattered everywhere

**Estimated Impact**: These refactorings could reduce codebase by ~500-800 lines while improving maintainability and consistency.

---

## 1. RGB Color Manipulation Utilities

### Current Problem

The RGB color extraction and conversion code appears in **at least 15 locations** across the codebase:

**Files with duplicate RGB extraction:**
- `TitleScreen.java` - lines 151-154
- `PauseScreen.java` - lines 110-113
- `CreateWorldScreen.java` - lines 167-170
- `SelectWorldScreen.java` - lines 133-136
- `AbstractMenuScreen.java` - lines 110-115
- `InventoryRenderer.java` - multiple occurrences
- `ColorUtils.java` - lines 28-32, 46-57, 67-71
- `UIRenderHelper.java` - lines 48-53

**Pattern:**
```java
private void setColor(int rgb, float a) {
    float r = ((rgb >> 16) & 0xFF) / 255f;
    float g = ((rgb >> 8) & 0xFF) / 255f;
    float b = (rgb & 0xFF) / 255f;
    glColor4f(r, g, b, a);
}
```

### Recommendation

**Consolidate into enhanced `ColorUtils` class:**

```java
package mattmc.util;

public final class ColorUtils {
    
    private ColorUtils() {} // Prevent instantiation
    
    /**
     * Extract red component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Red component (0-255)
     */
    public static int extractRed(int rgb) {
        return (rgb >> 16) & 0xFF;
    }
    
    /**
     * Extract green component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Green component (0-255)
     */
    public static int extractGreen(int rgb) {
        return (rgb >> 8) & 0xFF;
    }
    
    /**
     * Extract blue component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Blue component (0-255)
     */
    public static int extractBlue(int rgb) {
        return rgb & 0xFF;
    }
    
    /**
     * Convert packed RGB to normalized float array [r, g, b].
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Float array with values 0.0-1.0
     */
    public static float[] toNormalizedRGB(int rgb) {
        return new float[] {
            extractRed(rgb) / 255f,
            extractGreen(rgb) / 255f,
            extractBlue(rgb) / 255f
        };
    }
    
    /**
     * Convert packed RGB to normalized float array [r, g, b, a].
     * @param rgb Packed RGB color (0xRRGGBB)
     * @param alpha Alpha value (0.0-1.0)
     * @return Float array with values 0.0-1.0
     */
    public static float[] toNormalizedRGBA(int rgb, float alpha) {
        return new float[] {
            extractRed(rgb) / 255f,
            extractGreen(rgb) / 255f,
            extractBlue(rgb) / 255f,
            alpha
        };
    }
    
    /**
     * Pack separate RGB components into single integer.
     * @param r Red (0-255)
     * @param g Green (0-255)
     * @param b Blue (0-255)
     * @return Packed RGB integer
     */
    public static int packRGB(int r, int g, int b) {
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    
    /**
     * Interpolate between two colors.
     * @param color1 First color
     * @param color2 Second color
     * @param t Interpolation factor (0.0-1.0)
     * @return Interpolated color
     */
    public static int lerp(int color1, int color2, float t) {
        float r1 = extractRed(color1) / 255f;
        float g1 = extractGreen(color1) / 255f;
        float b1 = extractBlue(color1) / 255f;
        
        float r2 = extractRed(color2) / 255f;
        float g2 = extractGreen(color2) / 255f;
        float b2 = extractBlue(color2) / 255f;
        
        int r = (int)((r1 + (r2 - r1) * t) * 255f);
        int g = (int)((g1 + (g2 - g1) * t) * 255f);
        int b = (int)((b1 + (b2 - b1) * t) * 255f);
        
        return packRGB(r, g, b);
    }
    
    // Keep existing methods from current ColorUtils:
    // - darkenColor()
    // - adjustColorBrightness()
    // - applyTint()
    // - setGLColor()
}
```

**Migration:**
1. Enhance existing `mattmc.client.renderer.ColorUtils` with new methods
2. Move to `mattmc.util.ColorUtils` for broader accessibility
3. Update all 15+ call sites to use `ColorUtils.toNormalizedRGBA()` or `ColorUtils.setGLColor()`
4. Remove duplicate `setColor()` methods from screen classes

**Benefits:**
- Eliminates 15+ duplicate implementations (~60 lines of duplicate code)
- Single source of truth for color operations
- Easier to add new color utilities (lerp, HSV conversion, etc.)
- No performance impact - simple inlined operations

---

## 2. Window/Framebuffer Coordinate Conversion

### Current Problem

Window-to-framebuffer coordinate conversion (for HiDPI displays) is duplicated in **10+ screen classes**:

**Files with duplicate conversion:**
- `TitleScreen.java` - lines 117-127
- `PauseScreen.java` - lines 104-114
- `CreateWorldScreen.java` - similar pattern
- `SelectWorldScreen.java` - similar pattern
- `AbstractMenuScreen.java` - lines 56-66
- `InventoryRenderer.java` - multiple occurrences
- `InventorySlotManager.java`
- `CreativeInventoryManager.java`

**Pattern:**
```java
float mxFB, myFB;
try (MemoryStack stack = stackPush()) {
    IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
    IntBuffer fbW  = stack.mallocInt(1),  fbH  = stack.mallocInt(1);
    glfwGetWindowSize(window.handle(), winW, winH);
    glfwGetFramebufferSize(window.handle(), fbW, fbH);
    float sx = fbW.get(0) / Math.max(1f, winW.get(0));
    float sy = fbH.get(0) / Math.max(1f, winH.get(0));
    mxFB = (float) mouseXWin * sx;
    myFB = (float) mouseYWin * sy;
}
```

### Recommendation

**Create new `mattmc.client.util.CoordinateUtils` class:**

```java
package mattmc.client.util;

import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Utility class for coordinate system conversions in windowing contexts.
 * Handles conversions between window coordinates and framebuffer coordinates,
 * which is essential for HiDPI (Retina) display support.
 */
public final class CoordinateUtils {
    
    private CoordinateUtils() {} // Prevent instantiation
    
    /**
     * Represents a 2D coordinate pair.
     */
    public static class Point2D {
        public final float x;
        public final float y;
        
        public Point2D(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
    
    /**
     * Represents scaling factors for coordinate conversion.
     */
    public static class ScaleFactors {
        public final float scaleX;
        public final float scaleY;
        
        public ScaleFactors(float scaleX, float scaleY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }
    
    /**
     * Get the scale factors between window coordinates and framebuffer coordinates.
     * This is necessary for HiDPI displays where framebuffer may be larger than window.
     * 
     * @param windowHandle GLFW window handle
     * @return Scale factors for X and Y axes
     */
    public static ScaleFactors getFramebufferScale(long windowHandle) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1),  fbH  = stack.mallocInt(1);
            
            glfwGetWindowSize(windowHandle, winW, winH);
            glfwGetFramebufferSize(windowHandle, fbW, fbH);
            
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            
            return new ScaleFactors(sx, sy);
        }
    }
    
    /**
     * Convert window coordinates to framebuffer coordinates.
     * Essential for accurate mouse hit-testing on HiDPI displays.
     * 
     * @param windowHandle GLFW window handle
     * @param windowX Window X coordinate
     * @param windowY Window Y coordinate
     * @return Framebuffer coordinates
     */
    public static Point2D windowToFramebuffer(long windowHandle, double windowX, double windowY) {
        ScaleFactors scale = getFramebufferScale(windowHandle);
        return new Point2D(
            (float) windowX * scale.scaleX,
            (float) windowY * scale.scaleY
        );
    }
    
    /**
     * Convert framebuffer coordinates to window coordinates.
     * 
     * @param windowHandle GLFW window handle
     * @param framebufferX Framebuffer X coordinate
     * @param framebufferY Framebuffer Y coordinate
     * @return Window coordinates
     */
    public static Point2D framebufferToWindow(long windowHandle, float framebufferX, float framebufferY) {
        ScaleFactors scale = getFramebufferScale(windowHandle);
        return new Point2D(
            framebufferX / scale.scaleX,
            framebufferY / scale.scaleY
        );
    }
}
```

**Migration:**
1. Create new `mattmc.client.util.CoordinateUtils` class
2. Update all screen classes to use `CoordinateUtils.windowToFramebuffer()`
3. Consider caching scale factors in Window class for performance

**Example usage:**
```java
// Before:
float mxFB, myFB;
try (MemoryStack stack = stackPush()) {
    IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
    IntBuffer fbW  = stack.mallocInt(1),  fbH  = stack.mallocInt(1);
    glfwGetWindowSize(window.handle(), winW, winH);
    glfwGetFramebufferSize(window.handle(), fbW, fbH);
    float sx = fbW.get(0) / Math.max(1f, winW.get(0));
    float sy = fbH.get(0) / Math.max(1f, winH.get(0));
    mxFB = (float) mouseXWin * sx;
    myFB = (float) mouseYWin * sy;
}

// After:
CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
    window.handle(), mouseXWin, mouseYWin
);
float mxFB = fbCoords.x;
float myFB = fbCoords.y;
```

**Benefits:**
- Eliminates ~120 lines of duplicate boilerplate code
- Centralizes HiDPI handling logic
- Makes coordinate conversion explicit and documented
- Easier to optimize (e.g., cache scale factors)
- No performance impact - simple wrapper around GLFW calls

---

## 3. OpenGL State Management Utilities

### Current Problem

OpenGL state management is scattered across **30+ files** with repeated patterns:

**Common patterns found:**
- Matrix push/pop sequences (34 occurrences)
- 2D orthographic projection setup (8+ files)
- Blend mode setup (20+ occurrences)
- Texture binding sequences
- State save/restore patterns

**Example from multiple files:**
```java
// Pattern 1: 2D projection setup (UIRenderHelper.java, CrosshairRenderer.java, etc.)
glMatrixMode(GL_PROJECTION);
glPushMatrix();
glLoadIdentity();
glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
glMatrixMode(GL_MODELVIEW);
glPushMatrix();
glLoadIdentity();

// Pattern 2: Common blend mode (multiple files)
glEnable(GL_BLEND);
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

// Pattern 3: Depth testing setup
glEnable(GL_DEPTH_TEST);
glDepthFunc(GL_LEQUAL);
```

### Recommendation

**Enhance existing `mattmc.client.renderer.UIRenderHelper` or create `GLStateUtils`:**

```java
package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Utility class for managing common OpenGL state configurations.
 * Provides convenience methods for frequently used state setups
 * to reduce boilerplate and ensure consistency.
 */
public final class GLStateUtils {
    
    private GLStateUtils() {} // Prevent instantiation
    
    /**
     * Setup standard 2D orthographic projection for UI rendering.
     * Saves current matrix state.
     * 
     * @param width Screen/viewport width
     * @param height Screen/viewport height
     */
    public static void begin2DMode(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
    }
    
    /**
     * Restore previous projection and modelview matrices.
     * Must be called after begin2DMode().
     */
    public static void end2DMode() {
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Enable standard alpha blending (transparency).
     * Uses GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA blend function.
     */
    public static void enableAlphaBlending() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Disable blending.
     */
    public static void disableBlending() {
        glDisable(GL_BLEND);
    }
    
    /**
     * Enable standard depth testing with LEQUAL function.
     */
    public static void enableDepthTest() {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
    }
    
    /**
     * Disable depth testing.
     */
    public static void disableDepthTest() {
        glDisable(GL_DEPTH_TEST);
    }
    
    /**
     * Set up standard 3D rendering state.
     * Enables depth testing, face culling, and configures common settings.
     */
    public static void setup3DRenderState() {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
    }
    
    /**
     * Set up standard 2D rendering state.
     * Disables depth testing, enables alpha blending.
     */
    public static void setup2DRenderState() {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
    }
    
    /**
     * Matrix stack helper for safe matrix operations.
     */
    public static class MatrixScope implements AutoCloseable {
        public MatrixScope() {
            glPushMatrix();
        }
        
        @Override
        public void close() {
            glPopMatrix();
        }
    }
    
    /**
     * Create a scope that automatically pushes/pops matrix.
     * Use with try-with-resources for automatic cleanup.
     * 
     * @return MatrixScope that pops matrix on close
     */
    public static MatrixScope matrixScope() {
        return new MatrixScope();
    }
}
```

**Migration:**
1. Update `UIRenderHelper` to use these methods or create new `GLStateUtils`
2. Replace scattered state management with utility calls
3. Update documentation to reference standard state setups

**Example usage:**
```java
// Before:
glMatrixMode(GL_PROJECTION);
glPushMatrix();
glLoadIdentity();
glOrtho(0, width, height, 0, -1, 1);
glMatrixMode(GL_MODELVIEW);
glPushMatrix();
glLoadIdentity();
// ... render UI ...
glPopMatrix();
glMatrixMode(GL_PROJECTION);
glPopMatrix();
glMatrixMode(GL_MODELVIEW);

// After (option 1 - explicit):
GLStateUtils.begin2DMode(width, height);
// ... render UI ...
GLStateUtils.end2DMode();

// After (option 2 - try-with-resources):
try (var scope = GLStateUtils.matrixScope()) {
    GLStateUtils.begin2DMode(width, height);
    // ... render UI ...
}
```

**Benefits:**
- Reduces ~200+ lines of boilerplate OpenGL state setup
- Ensures consistent state management patterns
- Self-documenting code - method names describe intent
- Easier to debug state issues - single point of control
- Try-with-resources pattern prevents matrix stack leaks

---

## 4. Resource Loading Utilities

### Current Problem

Resource loading patterns are duplicated across **11+ files**:

**Files with similar resource loading:**
- `ResourceManager.java` - 3 occurrences
- `KeybindManager.java` - default options loading
- `TrueTypeFont.java` - font loading
- `SplashTextLoader.java` - text file loading
- `CubeMap.java` - texture loading
- `ShaderLoader.java` - shader source loading
- `TextureManager.java` - texture loading
- `Texture.java` - image loading
- `TextureAtlas.java` - atlas texture loading

**Common patterns:**
```java
// Pattern 1: Load resource to string
try (InputStream is = Class.class.getResourceAsStream(path)) {
    if (is == null) {
        logger.error("Resource not found: {}", path);
        return null;
    }
    try (Reader reader = new InputStreamReader(is)) {
        // Read content...
    }
}

// Pattern 2: Load and parse JSON
try (InputStream is = Class.class.getResourceAsStream(path)) {
    if (is == null) return null;
    try (Reader reader = new InputStreamReader(is)) {
        return gson.fromJson(reader, TypeClass.class);
    }
}
```

### Recommendation

**Create new `mattmc.util.ResourceLoader` class:**

```java
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
```

**Migration:**
1. Create `mattmc.util.ResourceLoader` class
2. Update all resource loading code to use centralized methods
3. Consider keeping specialized loaders (TextureLoader, ShaderLoader) for domain-specific logic

**Example usage:**
```java
// Before:
try (InputStream is = Class.class.getResourceAsStream("/path/to/file.txt")) {
    if (is == null) {
        logger.error("File not found");
        return null;
    }
    try (Reader reader = new InputStreamReader(is)) {
        // Read and process...
    }
}

// After:
String content = ResourceLoader.loadTextResource("/path/to/file.txt");
if (content != null) {
    // Process content...
}
```

**Benefits:**
- Eliminates ~80 lines of duplicate error-handling boilerplate
- Consistent resource loading patterns
- Centralized logging for missing resources
- Proper UTF-8 handling by default
- Easier to add resource caching if needed

---

## 5. Chunk Coordinate Conversion Utilities

### Current Problem

Chunk coordinate conversions are scattered throughout the codebase with magic numbers (16, 384, -64, etc.):

**Current implementation in `ChunkUtils.java`:**
- Only has `chunkKey()` method
- Constants are defined in `LevelChunk` but not easily accessible for calculations

**Common operations needed:**
- World coordinates ↔ Chunk coordinates
- Chunk-local coordinates ↔ World coordinates
- Section index calculations
- Y-coordinate validations

### Recommendation

**Enhance `mattmc.world.level.chunk.ChunkUtils` class:**

```java
package mattmc.world.level.chunk;

/**
 * Utility methods for chunk coordinate conversions and operations.
 * Centralizes common chunk-related calculations to reduce magic numbers
 * and ensure consistency across the codebase.
 */
public final class ChunkUtils {
    
    // Chunk dimensions (reference LevelChunk constants)
    public static final int CHUNK_WIDTH = LevelChunk.WIDTH;       // 16
    public static final int CHUNK_DEPTH = LevelChunk.DEPTH;       // 16
    public static final int CHUNK_HEIGHT = LevelChunk.HEIGHT;     // 384
    public static final int SECTION_HEIGHT = LevelChunk.SECTION_HEIGHT; // 16
    public static final int MIN_Y = LevelChunk.MIN_Y;             // -64
    public static final int MAX_Y = LevelChunk.MAX_Y;             // 319
    
    private ChunkUtils() {} // Utility class - no instantiation
    
    // === Coordinate Conversions ===
    
    /**
     * Convert world X coordinate to chunk X coordinate.
     * @param worldX World X coordinate
     * @return Chunk X coordinate
     */
    public static int worldToChunkX(int worldX) {
        return worldX >> 4; // Divide by 16
    }
    
    /**
     * Convert world Z coordinate to chunk Z coordinate.
     * @param worldZ World Z coordinate
     * @return Chunk Z coordinate
     */
    public static int worldToChunkZ(int worldZ) {
        return worldZ >> 4; // Divide by 16
    }
    
    /**
     * Convert chunk X coordinate to world X coordinate (western edge).
     * @param chunkX Chunk X coordinate
     * @return World X coordinate of chunk's western edge
     */
    public static int chunkToWorldX(int chunkX) {
        return chunkX << 4; // Multiply by 16
    }
    
    /**
     * Convert chunk Z coordinate to world Z coordinate (northern edge).
     * @param chunkZ Chunk Z coordinate
     * @return World Z coordinate of chunk's northern edge
     */
    public static int chunkToWorldZ(int chunkZ) {
        return chunkZ << 4; // Multiply by 16
    }
    
    /**
     * Get chunk-local X coordinate from world X coordinate.
     * @param worldX World X coordinate
     * @return Chunk-local X (0-15)
     */
    public static int worldToLocalX(int worldX) {
        return worldX & 15; // Modulo 16
    }
    
    /**
     * Get chunk-local Z coordinate from world Z coordinate.
     * @param worldZ World Z coordinate
     * @return Chunk-local Z (0-15)
     */
    public static int worldToLocalZ(int worldZ) {
        return worldZ & 15; // Modulo 16
    }
    
    /**
     * Get chunk-local Y coordinate from world Y coordinate.
     * @param worldY World Y coordinate (-64 to 319)
     * @return Chunk-local Y (0-383), or -1 if out of bounds
     */
    public static int worldToLocalY(int worldY) {
        int localY = worldY - MIN_Y;
        return (localY >= 0 && localY < CHUNK_HEIGHT) ? localY : -1;
    }
    
    /**
     * Get world Y coordinate from chunk-local Y coordinate.
     * @param localY Chunk-local Y (0-383)
     * @return World Y coordinate (-64 to 319)
     */
    public static int localToWorldY(int localY) {
        return localY + MIN_Y;
    }
    
    // === Section Calculations ===
    
    /**
     * Get section index from chunk-local Y coordinate.
     * @param localY Chunk-local Y (0-383)
     * @return Section index (0-23)
     */
    public static int getSectionIndex(int localY) {
        return localY / SECTION_HEIGHT;
    }
    
    /**
     * Get section-local Y coordinate.
     * @param localY Chunk-local Y (0-383)
     * @return Y within section (0-15)
     */
    public static int getSectionLocalY(int localY) {
        return localY % SECTION_HEIGHT;
    }
    
    /**
     * Get section index from world Y coordinate.
     * @param worldY World Y coordinate (-64 to 319)
     * @return Section index (0-23), or -1 if out of bounds
     */
    public static int worldYToSectionIndex(int worldY) {
        int localY = worldToLocalY(worldY);
        return localY >= 0 ? getSectionIndex(localY) : -1;
    }
    
    // === Validation ===
    
    /**
     * Check if world Y coordinate is valid.
     * @param worldY World Y coordinate
     * @return true if within valid range (-64 to 319)
     */
    public static boolean isValidWorldY(int worldY) {
        return worldY >= MIN_Y && worldY <= MAX_Y;
    }
    
    /**
     * Check if chunk-local coordinates are valid.
     * @param localX Local X (should be 0-15)
     * @param localY Local Y (should be 0-383)
     * @param localZ Local Z (should be 0-15)
     * @return true if all coordinates are valid
     */
    public static boolean isValidLocalCoords(int localX, int localY, int localZ) {
        return localX >= 0 && localX < CHUNK_WIDTH &&
               localY >= 0 && localY < CHUNK_HEIGHT &&
               localZ >= 0 && localZ < CHUNK_DEPTH;
    }
    
    // === Existing Methods ===
    
    /**
     * Convert chunk coordinates to a unique long key for storage.
     * Uses the same format as Minecraft: upper 32 bits for X, lower 32 bits for Z.
     */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Extract chunk X coordinate from chunk key.
     */
    public static int chunkXFromKey(long key) {
        return (int)(key >> 32);
    }
    
    /**
     * Extract chunk Z coordinate from chunk key.
     */
    public static int chunkZFromKey(long key) {
        return (int)key;
    }
    
    /**
     * Check if a chunk section is empty (all air blocks).
     * Performs a thorough check of all blocks in the section.
     */
    public static boolean isSectionEmpty(LevelChunk chunk, int startY, int endY) {
        // Existing implementation remains unchanged
        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int y = startY; y < endY; y++) {
                for (int z = 0; z < CHUNK_DEPTH; z++) {
                    if (!chunk.getBlock(x, y, z).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
```

**Migration:**
1. Enhance existing `ChunkUtils` with coordinate conversion methods
2. Search codebase for direct coordinate calculations and replace with utility calls
3. Update all files that perform chunk coordinate math

**Example usage:**
```java
// Before:
int chunkX = worldX >> 4;
int chunkZ = worldZ >> 4;
int localX = worldX & 15;
int localZ = worldZ & 15;
int localY = worldY - LevelChunk.MIN_Y;

// After:
int chunkX = ChunkUtils.worldToChunkX(worldX);
int chunkZ = ChunkUtils.worldToChunkZ(worldZ);
int localX = ChunkUtils.worldToLocalX(worldX);
int localZ = ChunkUtils.worldToLocalZ(worldZ);
int localY = ChunkUtils.worldToLocalY(worldY);
```

**Benefits:**
- Eliminates magic numbers (16, 384, -64) throughout codebase
- Self-documenting code - method names describe operation
- Single source of truth for chunk math
- Easier to change chunk dimensions if needed
- No performance impact - methods will be inlined by JIT

---

## 6. GLFW Callback Setup Utilities

### Current Problem

GLFW callback setup is duplicated in **every screen class** (~10 classes):

**Pattern found in all screen classes:**
```java
glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { 
    mouseXWin = x; 
    mouseYWin = y; 
});

glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
    if (button == GLFW_MOUSE_BUTTON_LEFT) 
        mouseDown = (action == GLFW_PRESS);
});

glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
    glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
    recomputeLayout();
});

glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
    // Key handling...
});
```

### Recommendation

**Create new `mattmc.client.input.InputCallbackHelper` class:**

```java
package mattmc.client.input;

import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Helper for setting up common GLFW input callbacks.
 * Reduces boilerplate in screen classes.
 */
public final class InputCallbackHelper {
    
    private InputCallbackHelper() {} // Prevent instantiation
    
    /**
     * Functional interface for mouse position updates.
     */
    @FunctionalInterface
    public interface MousePositionListener {
        void onMouseMove(double x, double y);
    }
    
    /**
     * Functional interface for mouse button updates.
     */
    @FunctionalInterface
    public interface MouseButtonListener {
        void onMouseButton(int button, int action, int mods);
    }
    
    /**
     * Functional interface for key events.
     */
    @FunctionalInterface
    public interface KeyListener {
        void onKey(int key, int scancode, int action, int mods);
    }
    
    /**
     * Functional interface for framebuffer resize events.
     */
    @FunctionalInterface
    public interface FramebufferSizeListener {
        void onFramebufferSize(int width, int height);
    }
    
    /**
     * Setup standard mouse position callback.
     */
    public static void setupMousePosition(long windowHandle, MousePositionListener listener) {
        glfwSetCursorPosCallback(windowHandle, (h, x, y) -> listener.onMouseMove(x, y));
    }
    
    /**
     * Setup standard mouse button callback.
     */
    public static void setupMouseButton(long windowHandle, MouseButtonListener listener) {
        glfwSetMouseButtonCallback(windowHandle, 
            (h, button, action, mods) -> listener.onMouseButton(button, action, mods));
    }
    
    /**
     * Setup standard left-click-only callback.
     */
    public static void setupLeftMouseButton(long windowHandle, Runnable onPress, Runnable onRelease) {
        glfwSetMouseButtonCallback(windowHandle, (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS && onPress != null) {
                    onPress.run();
                } else if (action == GLFW_RELEASE && onRelease != null) {
                    onRelease.run();
                }
            }
        });
    }
    
    /**
     * Setup standard key callback.
     */
    public static void setupKey(long windowHandle, KeyListener listener) {
        glfwSetKeyCallback(windowHandle, 
            (win, key, scancode, action, mods) -> listener.onKey(key, scancode, action, mods));
    }
    
    /**
     * Setup framebuffer size callback with automatic viewport update.
     */
    public static void setupFramebufferSize(long windowHandle, 
                                           FramebufferSizeListener listener) {
        glfwSetFramebufferSizeCallback(windowHandle, (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            listener.onFramebufferSize(newW, newH);
        });
    }
    
    /**
     * Setup framebuffer size callback with automatic viewport update and no listener.
     */
    public static void setupFramebufferSizeViewportOnly(long windowHandle) {
        glfwSetFramebufferSizeCallback(windowHandle, (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
        });
    }
    
    /**
     * Clear all input callbacks for a window.
     */
    public static void clearCallbacks(long windowHandle) {
        glfwSetCursorPosCallback(windowHandle, null);
        glfwSetMouseButtonCallback(windowHandle, null);
        glfwSetKeyCallback(windowHandle, null);
        glfwSetScrollCallback(windowHandle, null);
        glfwSetCharCallback(windowHandle, null);
        glfwSetFramebufferSizeCallback(windowHandle, null);
    }
}
```

**Alternative: Screen base class approach**

Instead of utility methods, could also enhance `Screen` interface with default methods or create an `AbstractScreen` base class. However, the utility approach is more flexible and doesn't require inheritance.

**Migration:**
1. Create `mattmc.client.input.InputCallbackHelper` class
2. Update screen classes to use helper methods
3. Consider adding to `Screen` interface as default methods

**Example usage:**
```java
// Before:
glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { 
    mouseXWin = x; 
    mouseYWin = y; 
});
glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
    if (button == GLFW_MOUSE_BUTTON_LEFT) 
        mouseDown = (action == GLFW_PRESS);
});

// After:
InputCallbackHelper.setupMousePosition(window.handle(), 
    (x, y) -> { mouseXWin = x; mouseYWin = y; });
InputCallbackHelper.setupLeftMouseButton(window.handle(),
    () -> mouseDown = true,
    () -> mouseDown = false);
```

**Benefits:**
- Reduces callback boilerplate by ~50%
- More readable and self-documenting
- Easier to add callback features (like debouncing)
- Type-safe functional interfaces
- Centralized place to fix callback-related bugs

**Note:** This refactoring is lower priority as existing code works well, but improves consistency.

---

## 7. Math and Validation Utilities

### Current Problem

Common math operations and validations are scattered:

**Current issues:**
- No centralized math utilities (lerp, clamp, etc.)
- Validation code repeated (null checks, range checks)
- Float/double comparison without epsilon
- Min/max chains that could be simplified

**Examples found:**
```java
// Manual clamping
int value = Math.max(0, Math.min(255, computed));

// No helper for common checks
if (obj == null) throw new IllegalArgumentException("Cannot be null");

// Float comparisons without epsilon
if (floatValue == 0.0f) // Risky!
```

### Recommendation

**Create new `mattmc.util.MathUtils` class:**

```java
package mattmc.util;

/**
 * Common mathematical operations and utilities.
 */
public final class MathUtils {
    
    public static final float EPSILON = 1e-6f;
    public static final double EPSILON_D = 1e-10;
    
    private MathUtils() {} // Prevent instantiation
    
    /**
     * Clamp an integer value between min and max (inclusive).
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a float value between min and max (inclusive).
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a double value between min and max (inclusive).
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Linear interpolation between two values.
     * @param a Start value
     * @param b End value
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated value
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * Linear interpolation between two values.
     * @param a Start value
     * @param b End value
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated value
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
    
    /**
     * Check if two floats are approximately equal.
     */
    public static boolean approximately(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }
    
    /**
     * Check if two doubles are approximately equal.
     */
    public static boolean approximately(double a, double b) {
        return Math.abs(a - b) < EPSILON_D;
    }
    
    /**
     * Check if a float is approximately zero.
     */
    public static boolean isZero(float value) {
        return Math.abs(value) < EPSILON;
    }
    
    /**
     * Check if a double is approximately zero.
     */
    public static boolean isZero(double value) {
        return Math.abs(value) < EPSILON_D;
    }
    
    /**
     * Floor division (always rounds down, even for negative numbers).
     */
    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }
    
    /**
     * Floor modulo (always positive remainder).
     */
    public static int floorMod(int a, int b) {
        return Math.floorMod(a, b);
    }
}
```

**Create new `mattmc.util.Validate` class:**

```java
package mattmc.util;

/**
 * Validation utilities for common precondition checks.
 * Similar to Apache Commons Validate or Guava Preconditions.
 */
public final class Validate {
    
    private Validate() {} // Prevent instantiation
    
    /**
     * Check that object is not null.
     * @throws IllegalArgumentException if null
     */
    public static <T> T notNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
    
    /**
     * Check that object is not null.
     * @throws NullPointerException if null
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }
    
    /**
     * Check that string is not null or empty.
     * @throws IllegalArgumentException if null or empty
     */
    public static String notEmpty(String str, String message) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }
    
    /**
     * Check that value is within range [min, max].
     * @throws IllegalArgumentException if out of range
     */
    public static int inRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message + " (value=" + value + 
                ", min=" + min + ", max=" + max + ")");
        }
        return value;
    }
    
    /**
     * Check that condition is true.
     * @throws IllegalArgumentException if false
     */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Check that condition is false.
     * @throws IllegalArgumentException if true
     */
    public static void isFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Check that collection/array is not empty.
     */
    public static <T> T[] notEmpty(T[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }
}
```

**Migration:**
1. Create `mattmc.util.MathUtils` and `mattmc.util.Validate`
2. Search for validation patterns and replace with utility calls
3. Add clamp() calls where min/max chains exist

**Example usage:**
```java
// Before:
int r = Math.max(0, Math.min(255, computed));
if (name == null || name.isEmpty()) {
    throw new IllegalArgumentException("Name cannot be empty");
}

// After:
int r = MathUtils.clamp(computed, 0, 255);
Validate.notEmpty(name, "Name cannot be empty");
```

**Benefits:**
- More expressive and readable code
- Reduces repetitive validation boilerplate
- Self-documenting - method names describe intent
- Consistent error messages
- Easier to add common math operations

---

## 8. Additional Smaller Refactoring Opportunities

### 8.1 Logger Initialization Pattern

**Current:** Logger initialized in every class with boilerplate
```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);
```

**Recommendation:** While this is standard practice and works well, could consider Lombok's `@Slf4j` annotation if adding Lombok dependency is acceptable. However, **this is NOT recommended** as it adds external dependency for minimal benefit.

**Action:** Keep current approach - it's explicit and standard Java practice.

---

### 8.2 ArrayList/HashMap Initialization

**Observation:** Many collections initialized without initial capacity

**Examples:**
- `new ArrayList<>()` - 50 occurrences
- `new HashMap<>()` - 36 occurrences

**Recommendation:** Add initial capacity hints for collections where size is known or predictable, especially in hot paths.

**Example:**
```java
// Before:
List<Button> buttons = new ArrayList<>();

// After (if we know typical size):
List<Button> buttons = new ArrayList<>(5); // Typical screen has ~5 buttons
```

**Action:** Low priority - JVM handles this well, but could improve in critical paths.

---

### 8.3 Constants Consolidation

**Observation:** Some constants duplicated across files:
- Screen colors (0xFFFFFF, 0x000000, etc.)
- Common dimensions
- Magic numbers for rendering

**Recommendation:** Create `mattmc.client.gui.GuiConstants` for UI-related constants:

```java
package mattmc.client.gui;

public final class GuiConstants {
    private GuiConstants() {}
    
    // Common colors
    public static final int COLOR_WHITE = 0xFFFFFF;
    public static final int COLOR_BLACK = 0x000000;
    public static final int COLOR_GRAY = 0x808080;
    public static final int COLOR_LIGHT_GRAY = 0xC0C0C0;
    public static final int COLOR_DARK_GRAY = 0x404040;
    
    // Button defaults
    public static final int DEFAULT_BUTTON_WIDTH = 200;
    public static final int DEFAULT_BUTTON_HEIGHT = 40;
    public static final int DEFAULT_BUTTON_SPACING = 8;
    
    // Text rendering
    public static final float DEFAULT_TITLE_SCALE = 2.5f;
    public static final float DEFAULT_TEXT_SCALE = 1.0f;
}
```

**Action:** Medium priority - improves consistency and maintainability.

---

### 8.4 Try-With-Resources Patterns

**Observation:** Good use of try-with-resources throughout codebase

**Current state:** Well-implemented in most places (NBTUtil, resource loading, etc.)

**Recommendation:** Audit remaining manual close() calls and convert to try-with-resources where possible.

**Action:** Low priority - current code is already good.

---

## Implementation Priority

### High Priority (Immediate Impact)
1. **RGB Color Utilities** - Highest duplication (15+ instances), easy win
2. **Window/Framebuffer Coordinate Conversion** - 10+ duplicates, reduces bugs
3. **Chunk Coordinate Utilities** - Eliminates magic numbers, improves readability

### Medium Priority (Nice to Have)
4. **OpenGL State Management** - Large impact but requires careful testing
5. **Resource Loading Utilities** - Good consolidation opportunity
6. **Constants Consolidation** - Improves consistency

### Low Priority (Future Consideration)
7. **GLFW Callback Utilities** - Current code works well, this is sugar
8. **Math/Validation Utilities** - Incremental improvements

---

## Implementation Guidelines

### Before Starting Any Refactoring

1. **Create comprehensive tests** for affected areas
2. **Document current behavior** to ensure no changes
3. **Profile critical paths** to ensure no performance regression
4. **Create feature branch** for each major refactoring category

### During Refactoring

1. **Make one change at a time** - don't combine refactorings
2. **Run tests after each file** - catch issues early
3. **Use IDE refactoring tools** - Find Usages, Rename, etc.
4. **Keep commits small** - easier to review and revert
5. **Update JavaDoc** - document new utilities thoroughly

### After Refactoring

1. **Run full test suite** - ensure no regressions
2. **Run performance benchmarks** - verify no slowdowns
3. **Update documentation** - reference new utilities
4. **Code review** - get team feedback
5. **Monitor for issues** - watch for unexpected behavior

---

## Performance Considerations

### Will Not Impact Performance

- **Color utilities** - Simple bit operations, will be inlined by JIT
- **Coordinate conversion** - Arithmetic operations, compiler-optimized
- **Math utilities** - Methods will be inlined, identical to manual code
- **Validation utilities** - Only used in non-hot paths

### Monitor Carefully

- **OpenGL state management** - Must verify no extra GL calls
- **Resource loading** - Ensure buffering remains efficient
- **Callback setup** - Lambda allocation is already present in current code

### Best Practices

- Keep utility methods **small and focused** - JIT can inline them
- Avoid **allocations in hot paths** - use primitive types
- Mark utility classes **final** - helps JIT optimization
- Use **@SuppressWarnings** judiciously for unavoidable warnings

---

## Conclusion

The MattMC codebase is well-structured with good separation of concerns. The refactoring opportunities identified here focus on:

1. **Reducing duplication** - DRY principle
2. **Improving readability** - Self-documenting code
3. **Centralizing common patterns** - Single source of truth
4. **Maintaining performance** - No compromise on speed

**Estimated total impact:**
- **500-800 lines** of duplicate code eliminated
- **Improved maintainability** through centralized utilities
- **Better readability** with self-documenting method names
- **No performance impact** - all changes are zero-cost abstractions

The most impactful refactorings are RGB color utilities, coordinate conversions, and chunk coordinate utilities, as they eliminate the most duplication and magic numbers while being straightforward to implement and test.
