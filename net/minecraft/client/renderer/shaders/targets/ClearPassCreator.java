package net.minecraft.client.renderer.shaders.targets;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector2i;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates clear passes for render targets, grouping them by color and dimensions
 * to minimize OpenGL state changes.
 * 
 * <p>Following IRIS 1.21.9 ClearPassCreator.java pattern
 */
public class ClearPassCreator {
    
    /**
     * Creates clear passes for the specified render targets.
     * 
     * <p>Buffers with the same clear color and dimensions are grouped into a single
     * clear pass to reduce OpenGL calls.
     * 
     * @param bufferManager The G-buffer manager containing all render targets
     * @param fullClear Whether to clear all buffers regardless of configuration
     * @param clearColors Map of buffer index to clear color (null for default)
     * @return Immutable list of clear passes
     */
    public static ImmutableList<ClearPass> createClearPasses(
            GBufferManager bufferManager,
            boolean fullClear,
            Map<Integer, Vector4f> clearColors) {
        
        final int maxDrawBuffers = getMaxDrawBuffers();

        // Sort buffers by their clear color and dimensions so we can group up glClear calls
        Map<Vector2i, Map<ClearPassInformation, IntList>> clearByColor = new HashMap<>();

        // Process each buffer (0-15 for colortex0-15)
        for (int buffer = 0; buffer < 16; buffer++) {
            RenderTarget target = bufferManager.get(buffer);
            if (target == null) {
                continue;
            }

            // Only clear if fullClear is enabled or buffer is explicitly configured to clear
            if (!fullClear && !clearColors.containsKey(buffer)) {
                continue;
            }

            // Determine clear color
            Vector4f clearColor = getClearColor(buffer, clearColors);

            // Group by dimensions and clear color
            Vector2i dimensions = new Vector2i(target.getWidth(), target.getHeight());
            ClearPassInformation clearInfo = new ClearPassInformation(clearColor, target.getWidth(), target.getHeight());
            
            clearByColor
                .computeIfAbsent(dimensions, size -> new HashMap<>())
                .computeIfAbsent(clearInfo, color -> new IntArrayList())
                .add(buffer);
        }

        // Create clear passes for each group
        List<ClearPass> clearPasses = new ArrayList<>();

        clearByColor.forEach((dimensions, colorGroups) -> 
            colorGroups.forEach((clearInfo, buffers) -> {
                int startIndex = 0;

                while (startIndex < buffers.size()) {
                    // Clear up to the maximum number of draw buffers per each clear pass.
                    // This allows us to handle having more than 8 buffers with the same clear color
                    // on systems with a max draw buffers of 8 (most systems).
                    int batchSize = Math.min(buffers.size() - startIndex, maxDrawBuffers);
                    int[] clearBuffers = new int[batchSize];

                    for (int i = 0; i < batchSize; i++) {
                        clearBuffers[i] = buffers.getInt(startIndex);
                        startIndex++;
                    }

                    // Create clear passes for both main and alternate textures (ping-pong)
                    // Main textures clear pass
                    clearPasses.add(new ClearPass(
                        clearInfo.getColor(),
                        clearInfo::getWidth,
                        clearInfo::getHeight,
                        bufferManager.createClearFramebuffer(true, clearBuffers),
                        GL11.GL_COLOR_BUFFER_BIT
                    ));

                    // Alternate textures clear pass
                    clearPasses.add(new ClearPass(
                        clearInfo.getColor(),
                        clearInfo::getWidth,
                        clearInfo::getHeight,
                        bufferManager.createClearFramebuffer(false, clearBuffers),
                        GL11.GL_COLOR_BUFFER_BIT
                    ));
                }
            })
        );

        return ImmutableList.copyOf(clearPasses);
    }

    /**
     * Gets the default clear color for a buffer.
     * 
     * @param buffer Buffer index
     * @param clearColors Configured clear colors
     * @return Clear color (RGBA)
     */
    private static Vector4f getClearColor(int buffer, Map<Integer, Vector4f> clearColors) {
        // Check if explicitly configured
        if (clearColors.containsKey(buffer)) {
            Vector4f configured = clearColors.get(buffer);
            if (configured != null) {
                return configured;
            }
        }

        // Default clear colors following IRIS pattern
        if (buffer == 0) {
            // colortex0 is cleared to fog color (handled by caller, null means use default)
            return new Vector4f(0.0f, 0.0f, 0.0f, 1.0f); // Default to black with full alpha
        } else if (buffer == 1) {
            // colortex1 is cleared to solid white with full alpha
            return new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            // All other buffers are cleared to solid black with zero alpha
            return new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    /**
     * Gets the maximum number of draw buffers supported by the OpenGL implementation.
     * 
     * @return Maximum draw buffers (typically 8 or 16)
     */
    private static int getMaxDrawBuffers() {
        return GL21.glGetInteger(GL21.GL_MAX_DRAW_BUFFERS);
    }
}
