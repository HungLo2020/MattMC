package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A buffer for accumulating draw commands before submitting them to a rendering backend.
 * 
 * <p>This class provides a thin wrapper around {@code List<DrawCommand>} to avoid passing
 * raw lists around the codebase. It allows the rendering front-end (game logic) to build
 * up a frame's worth of draw commands, which can then be submitted to the backend.
 * 
 * <p><b>Usage Pattern:</b>
 * <pre>
 * CommandBuffer buffer = new CommandBuffer();
 * 
 * // Front-end builds commands
 * chunkLogic.buildCommands(world, camera, buffer);
 * itemLogic.buildCommands(items, buffer);
 * uiLogic.buildCommands(ui, buffer);
 * 
 * // Backend processes commands
 * backend.beginFrame();
 * for (DrawCommand cmd : buffer.getCommands()) {
 *     backend.submit(cmd);
 * }
 * backend.endFrame();
 * 
 * // Reuse buffer for next frame
 * buffer.clear();
 * </pre>
 * 
 * <p><b>Design Note:</b> This class is intentionally simple and can be extended in the
 * future to support features like:
 * <ul>
 *   <li>Automatic sorting by render pass or material</li>
 *   <li>Command deduplication</li>
 *   <li>Statistics gathering (command count per pass, etc.)</li>
 *   <li>Memory pooling for reduced GC pressure</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. It should only be used from
 * the rendering thread.
 * 
 * @since Stage 3 of rendering refactor
 * @see DrawCommand
 * @see RenderBackend
 */
public class CommandBuffer {
    private final List<DrawCommand> commands;
    
    /**
     * Creates a new empty command buffer.
     */
    public CommandBuffer() {
        this.commands = new ArrayList<>();
    }
    
    /**
     * Creates a new command buffer with the specified initial capacity.
     * 
     * @param initialCapacity the initial capacity of the internal list
     */
    public CommandBuffer(int initialCapacity) {
        this.commands = new ArrayList<>(initialCapacity);
    }
    
    /**
     * Adds a draw command to this buffer.
     * 
     * @param command the command to add, must not be null
     * @throws NullPointerException if command is null
     */
    public void add(DrawCommand command) {
        if (command == null) {
            throw new NullPointerException("DrawCommand cannot be null");
        }
        commands.add(command);
    }
    
    /**
     * Adds all commands from another buffer to this buffer.
     * 
     * @param other the buffer whose commands to add, must not be null
     * @throws NullPointerException if other is null
     */
    public void addAll(CommandBuffer other) {
        if (other == null) {
            throw new NullPointerException("CommandBuffer cannot be null");
        }
        commands.addAll(other.commands);
    }
    
    /**
     * Returns an unmodifiable view of the commands in this buffer.
     * 
     * @return unmodifiable list of commands
     */
    public List<DrawCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }
    
    /**
     * Returns the number of commands in this buffer.
     * 
     * @return the command count
     */
    public int size() {
        return commands.size();
    }
    
    /**
     * Checks if this buffer is empty.
     * 
     * @return true if the buffer contains no commands
     */
    public boolean isEmpty() {
        return commands.isEmpty();
    }
    
    /**
     * Removes all commands from this buffer.
     * The buffer can be reused after calling clear().
     */
    public void clear() {
        commands.clear();
    }
    
    // ===== Sorting and Batching Methods =====
    
    /**
     * Sorts commands by material ID to minimize state changes in OpenGL.
     * 
     * <p>Batching similar materials together reduces the number of shader/texture
     * binding operations, which is a significant performance optimization for
     * rendering large numbers of objects.
     * 
     * <p><b>Use case:</b> Call this before submitting commands to the backend
     * to reduce state changes when all commands are in the same render pass.
     */
    public void sortByMaterial() {
        commands.sort(Comparator.comparingInt(cmd -> cmd.materialId));
    }
    
    /**
     * Sorts commands by render pass.
     * 
     * <p>This ensures proper rendering order:
     * <ol>
     *   <li>OPAQUE - rendered first with depth testing</li>
     *   <li>TRANSPARENT - rendered after opaque with blending</li>
     *   <li>SHADOW - for shadow map generation</li>
     *   <li>UI - rendered last on top of everything</li>
     * </ol>
     * 
     * <p><b>Use case:</b> Call this when commands from different render passes
     * are mixed in the buffer and need to be separated for proper rendering.
     */
    public void sortByRenderPass() {
        commands.sort(Comparator.comparing(cmd -> cmd.pass));
    }
    
    /**
     * Sorts commands optimally for rendering: first by render pass, then by material.
     * 
     * <p>This is the most efficient sorting strategy as it:
     * <ul>
     *   <li>Ensures correct render pass ordering (opaque → transparent → UI)</li>
     *   <li>Minimizes state changes within each pass by batching materials</li>
     * </ul>
     * 
     * <p><b>Use case:</b> Call this before submitting a full frame's worth of
     * commands to the backend for optimal rendering performance.
     */
    public void sortForRendering() {
        commands.sort(Comparator
            .comparing((DrawCommand cmd) -> cmd.pass)
            .thenComparingInt(cmd -> cmd.materialId));
    }
    
    /**
     * Sorts commands by depth (transform index) for proper transparent object ordering.
     * 
     * <p>Transparent objects must typically be rendered back-to-front to achieve
     * correct blending results. This method sorts by transform index, which can
     * be used as a proxy for depth when transforms are registered in depth order.
     * 
     * <p><b>Note:</b> For true depth sorting, the caller should ensure transform
     * indices correspond to depth ordering, or use a custom comparator.
     * 
     * @param backToFront if true, sorts from highest to lowest transform index
     *                    (back to front); if false, sorts front to back
     */
    public void sortByDepth(boolean backToFront) {
        if (backToFront) {
            commands.sort(Comparator.comparingInt((DrawCommand cmd) -> cmd.transformIndex).reversed());
        } else {
            commands.sort(Comparator.comparingInt(cmd -> cmd.transformIndex));
        }
    }
    
    /**
     * Returns the command at the specified index.
     * 
     * @param index the index of the command to return
     * @return the command at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public DrawCommand get(int index) {
        return commands.get(index);
    }
    
    /**
     * Returns a string representation of this buffer for debugging.
     * Includes the number of commands and a sample of the first few.
     */
    @Override
    public String toString() {
        int count = commands.size();
        if (count == 0) {
            return "CommandBuffer{empty}";
        } else if (count <= 3) {
            return "CommandBuffer{" + count + " commands: " + commands + "}";
        } else {
            return "CommandBuffer{" + count + " commands, first 3: " + 
                   commands.subList(0, 3) + "...}";
        }
    }
}
