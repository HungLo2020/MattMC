package mattmc.client.renderer;

import java.util.ArrayList;
import java.util.Collections;
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
