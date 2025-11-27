package mattmc.client.renderer.backend;

import mattmc.client.renderer.item.ItemRenderer;

/**
 * Interface for graphics API-specific rendering backends.
 * 
 * <p>This interface abstracts away the details of specific graphics APIs (OpenGL, Vulkan, etc.)
 * and provides a common interface for submitting rendering work. The rendering front-end
 * (game logic) works entirely with {@link DrawCommand} objects and this interface, without
 * needing to know about the underlying graphics API.
 * 
 * <h2>Architecture</h2>
 * <p>The rendering system is split into three layers:
 * <ol>
 *   <li><b>Game/World Layer:</b> Game logic that knows about blocks, chunks, entities, etc.
 *       but has no knowledge of graphics APIs.</li>
 *   <li><b>Rendering Front-End:</b> Decides what to draw and creates {@link DrawCommand}
 *       objects. Still API-agnostic.</li>
 *   <li><b>Rendering Back-End (this interface):</b> Translates abstract draw commands into
 *       actual graphics API calls. API-specific implementations live here.</li>
 * </ol>
 * 
 * <h2>Typical Usage Pattern</h2>
 * <pre>
 * // Start of frame
 * backend.beginFrame();
 * 
 * // Game logic builds draw commands
 * List&lt;DrawCommand&gt; commands = buildDrawCommands();
 * 
 * // Submit commands to backend
 * for (DrawCommand cmd : commands) {
 *     backend.submit(cmd);
 * }
 * 
 * // End of frame
 * backend.endFrame();
 * </pre>
 * 
 * <h2>Implementations</h2>
 * <p>Current and future implementations:
 * <ul>
 *   <li><b>OpenGLRenderBackend</b> (implemented in Stage 2): The current production
 *       backend that translates commands to OpenGL calls. Exists but not yet wired
 *       into the main render loop until Stage 3+.</li>
 *   <li><b>DebugRenderBackend</b> (to be implemented in Stage 6): A headless backend that
 *       records commands for inspection without requiring a graphics context. Useful for
 *       testing and debugging.</li>
 *   <li><b>VulkanRenderBackend</b> (future, not to be implemented yet): A future backend
 *       for Vulkan support. The design should make this feasible but it's not part of
 *       the current refactor.</li>
 * </ul>
 * 
 * <p><b>Design Constraints:</b> To maintain compatibility with future backends:
 * <ul>
 *   <li>This interface must not expose or accept any OpenGL-specific types (GLuint,
 *       GLint, etc.)</li>
 *   <li>All resources must be referenced through abstract IDs, not API handles</li>
 *   <li>Frame structure must be simple enough to map to different API models</li>
 * </ul>
 * 
 * <p><b>Design Note:</b> This abstraction is designed to support future Vulkan implementation.
 * However, <em>Vulkan must not be implemented yet</em>. The focus is on OpenGL plus testability
 * first. The design ensures Vulkan will be feasible when we're ready for it.
 * 
 * <p><b>TODO for Vulkan backend (future work, do not implement yet):</b>
 * <ul>
 *   <li>beginFrame() would acquire swapchain image and begin command buffer recording</li>
 *   <li>submit() would record draw commands into the command buffer</li>
 *   <li>endFrame() would end recording, submit to queue, and present</li>
 *   <li>Resource IDs would map to Vulkan buffer/pipeline/descriptor handles</li>
 * </ul>
 * 
 * @since Stage 1 of rendering refactor
 * @see DrawCommand
 * @see RenderPass
 */
public interface RenderBackend {
    /**
     * Called at the beginning of a frame, before any draw commands are submitted.
     * 
     * <p>This method allows the backend to perform any necessary setup for the frame:
     * <ul>
     *   <li><b>OpenGL:</b> Clear buffers, reset state, prepare for rendering</li>
     *   <li><b>Vulkan (future):</b> Acquire next swapchain image, begin command buffer</li>
     *   <li><b>Debug:</b> Clear recorded command list</li>
     * </ul>
     * 
     * <p>After this call, the backend should be ready to accept {@link #submit(DrawCommand)}
     * calls.
     * 
     * <p><b>Nested Calls:</b> This method supports nested calls via reference counting.
     * Multiple renderers can safely call beginFrame()/endFrame() pairs independently.
     * The frame is only actually started on the first call and ended on the last matching
     * endFrame() call. This enables a gradual migration to centralized frame management
     * while maintaining backward compatibility with existing code.
     * 
     * @see #endFrame()
     */
    void beginFrame();
    
    /**
     * Submits a single draw command to the backend for rendering.
     * 
     * <p>The backend is responsible for:
     * <ul>
     *   <li>Translating the abstract {@link DrawCommand} into API-specific calls</li>
     *   <li>Looking up mesh, material, and transform resources from the provided IDs</li>
     *   <li>Configuring API state appropriately (shaders, textures, blending, etc.)</li>
     *   <li>Issuing the actual draw call</li>
     * </ul>
     * 
     * <p><b>Implementation Note:</b> Backends may choose to batch or reorder commands
     * for efficiency (e.g., grouping by material to reduce state changes), but should
     * respect render pass ordering to maintain correct rendering results.
     * 
     * <p><b>Thread Safety:</b> This method is expected to be called from the rendering
     * thread only. Implementations do not need to be thread-safe.
     * 
     * @param cmd the draw command to submit, must not be null
     * @throws NullPointerException if cmd is null
     * @see DrawCommand
     */
    void submit(DrawCommand cmd);
    
    /**
     * Called at the end of a frame, after all draw commands have been submitted.
     * 
     * <p>This method allows the backend to perform any necessary finalization:
     * <ul>
     *   <li><b>OpenGL:</b> Flush commands, swap buffers (if managed by backend), cleanup</li>
     *   <li><b>Vulkan (future):</b> End command buffer, submit to queue, present frame</li>
     *   <li><b>Debug:</b> Finalize command recording for inspection</li>
     * </ul>
     * 
     * <p>After this call, no more {@link #submit(DrawCommand)} calls should be made until
     * the next {@link #beginFrame()}.
     * 
     * <p><b>Nested Calls:</b> This method supports nested calls via reference counting.
     * Each endFrame() must match a previous beginFrame() call. Only the final endFrame()
     * (when nesting depth returns to 0) actually performs cleanup operations.
     * 
     * @see #beginFrame()
     */
    void endFrame();
    
    /**
     * Setup 2D orthographic projection for UI rendering.
     * 
     * <p>This method configures the projection matrix for 2D screen-space rendering,
     * where coordinates map directly to screen pixels. The coordinate system has:
     * <ul>
     *   <li>Origin (0,0) at the top-left corner</li>
     *   <li>X-axis increases to the right</li>
     *   <li>Y-axis increases downward</li>
     *   <li>Z-axis unused (2D rendering)</li>
     * </ul>
     * 
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li><b>OpenGL:</b> Sets up orthographic projection with glOrtho, pushes matrices</li>
     *   <li><b>Vulkan (future):</b> Would configure viewport and scissor for 2D rendering</li>
     *   <li><b>Debug:</b> Records projection state for inspection</li>
     * </ul>
     * 
     * <p>This method must be paired with {@link #restore2DProjection()} to restore the
     * previous projection state after 2D rendering is complete.
     * 
     * @param screenWidth the width of the screen/viewport in pixels
     * @param screenHeight the height of the screen/viewport in pixels
     * @see #restore2DProjection()
     */
    void setup2DProjection(int screenWidth, int screenHeight);
    
    /**
     * Restore the previous projection state after 2D rendering.
     * 
     * <p>This method restores the projection matrix that was active before
     * {@link #setup2DProjection(int, int)} was called. It must be called after
     * completing 2D rendering to return to the previous rendering mode (typically 3D).
     * 
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li><b>OpenGL:</b> Pops the projection and modelview matrices</li>
     *   <li><b>Vulkan (future):</b> Would restore previous viewport/scissor state</li>
     *   <li><b>Debug:</b> Records projection state restoration</li>
     * </ul>
     * 
     * @see #setup2DProjection(int, int)
     */
    void restore2DProjection();
    
    /**
     * Get the display resolution as a formatted string.
     * 
     * <p>This method retrieves the current window/display resolution from the backend.
     * Different backends may query this information differently:
     * <ul>
     *   <li><b>OpenGL:</b> Uses GLFW to query window size</li>
     *   <li><b>Vulkan (future):</b> Would query surface capabilities</li>
     *   <li><b>Debug:</b> Returns a mock resolution</li>
     * </ul>
     * 
     * @param windowHandle Platform-specific window handle (e.g., GLFW window pointer)
     * @return Display resolution string (e.g., "1920x1080")
     */
    String getDisplayResolution(long windowHandle);
    
    /**
     * Get the graphics processing unit (GPU) name.
     * 
     * <p>This method retrieves the name/model of the GPU from the backend.
     * Different backends query this differently:
     * <ul>
     *   <li><b>OpenGL:</b> Uses glGetString(GL_RENDERER)</li>
     *   <li><b>Vulkan (future):</b> Would query physical device properties</li>
     *   <li><b>Debug:</b> Returns a mock GPU name</li>
     * </ul>
     * 
     * @return Graphics card/GPU name, or "Unknown" if not available
     */
    String getGPUName();
    
    /**
     * Get the current GPU usage percentage.
     * 
     * <p>This method attempts to retrieve GPU utilization information.
     * Note that GPU usage is not always reliably available through standard APIs.
     * 
     * @return GPU usage percentage (0-100), or -1 if not available
     */
    int getGPUUsage();
    
    /**
     * Get the current GPU VRAM usage as a formatted string.
     * 
     * <p>This method attempts to retrieve video memory usage information.
     * Note that VRAM usage is not always reliably available through standard APIs.
     * 
     * @return VRAM usage string (e.g., "2048 MB / 4096 MB"), or "N/A" if not available
     */
    String getGPUVRAMUsage();
    
    /**
     * Apply a Gaussian blur effect to a rectangular region of the screen.
     * 
     * <p>This method captures the current screen content in the specified region,
     * applies a Gaussian blur with optional darkening/tinting, and renders it back
     * to the same region. This is commonly used for UI backgrounds to create
     * a "frosted glass" effect.
     * 
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li><b>OpenGL:</b> Uses framebuffers, shaders, and texture sampling for blur</li>
     *   <li><b>Vulkan (future):</b> Would use compute shaders or render passes</li>
     *   <li><b>Debug:</b> Records blur command for inspection</li>
     * </ul>
     * 
     * <p>The blur effect applies a two-pass Gaussian blur (horizontal then vertical)
     * and optionally darkens the result to create a subtle overlay effect suitable
     * for UI backgrounds.
     * 
     * @param x X position of the rectangular region (top-left corner)
     * @param y Y position of the rectangular region (top-left corner)
     * @param width Width of the rectangular region
     * @param height Height of the rectangular region
     * @param screenWidth Full screen width for proper coordinate mapping
     * @param screenHeight Full screen height for proper coordinate mapping
     */
    void applyRegionalBlur(float x, float y, float width, float height, 
                          int screenWidth, int screenHeight);
    
    /**
     * Draw a rounded rectangle border (outline only, not filled).
     * 
     * <p>This method draws a border around a rectangle with rounded corners.
     * The border is drawn as a continuous line with specified width and color.
     * This is commonly used for UI elements like tooltips and overlays.
     * 
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li><b>OpenGL:</b> Uses GL_LINE_STRIP with trigonometric calculations for corners</li>
     *   <li><b>Vulkan (future):</b> Would use line rendering or geometry shaders</li>
     *   <li><b>Debug:</b> Records border command for inspection</li>
     * </ul>
     * 
     * @param x X position of the rectangle (top-left corner)
     * @param y Y position of the rectangle (top-left corner)
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @param radius Corner radius for rounding
     * @param borderWidth Width of the border line
     * @param r Red component of border color (0.0-1.0)
     * @param g Green component of border color (0.0-1.0)
     * @param b Blue component of border color (0.0-1.0)
     * @param a Alpha component of border color (0.0-1.0)
     */
    void drawRoundedRectBorder(float x, float y, float width, float height, float radius,
                               float borderWidth, float r, float g, float b, float a);
    
    /**
     * Resets the current drawing color to white (1.0, 1.0, 1.0, 1.0).
     * 
     * <p>This is useful when drawing operations (like borders) set specific colors
     * that need to be reset before drawing text or other elements that should appear
     * in their default white color.
     * 
     * <p>Backend-specific implementations:
     * <ul>
     *   <li><b>OpenGL:</b> Calls glColor4f(1.0, 1.0, 1.0, 1.0)</li>
     *   <li><b>Vulkan (future):</b> Would set color uniform or push constant</li>
     *   <li><b>Debug:</b> Records color reset command</li>
     * </ul>
     */
    void resetColor();
    
    /**
     * Set the current drawing color from an RGB value and alpha.
     * 
     * <p>This method extracts RGB components from the integer value and sets
     * the current drawing color for subsequent rendering operations.
     * 
     * <p>Backend-specific implementations:
     * <ul>
     *   <li><b>OpenGL:</b> Calls glColor4f with extracted RGB components</li>
     *   <li><b>Vulkan (future):</b> Would set color uniform or push constant</li>
     *   <li><b>Debug:</b> Records color set command</li>
     * </ul>
     * 
     * @param rgb RGB color value (0xRRGGBB format)
     * @param alpha Alpha value (0.0 to 1.0)
     */
    void setColor(int rgb, float alpha);
    
    /**
     * Fill a rectangle with the current color.
     * 
     * <p>Draws a filled rectangle using the current drawing color.
     * The rectangle is specified by its top-left corner and dimensions.
     * 
     * <p>Backend-specific implementations:
     * <ul>
     *   <li><b>OpenGL:</b> Uses GL_QUADS to render a filled quad</li>
     *   <li><b>Vulkan (future):</b> Would use a quad mesh or geometry shader</li>
     *   <li><b>Debug:</b> Records rectangle fill command</li>
     * </ul>
     * 
     * @param x X position of the rectangle (top-left corner)
     * @param y Y position of the rectangle (top-left corner)
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     */
    void fillRect(float x, float y, float width, float height);
    
    /**
     * Draw a rectangle outline (border only, no fill).
     * 
     * @param x X position of the rectangle (top-left corner)
     * @param y Y position of the rectangle (top-left corner)
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     */
    void drawRect(float x, float y, float width, float height);
    
    /**
     * Draw a line between two points.
     * 
     * @param x1 Start X position
     * @param y1 Start Y position
     * @param x2 End X position
     * @param y2 End Y position
     */
    void drawLine(float x1, float y1, float x2, float y2);
    
    /**
     * Enable alpha blending for transparent rendering.
     */
    void enableBlend();
    
    /**
     * Disable alpha blending.
     */
    void disableBlend();
    
    /**
     * Draw text at the specified position with the given scale.
     * 
     * <p>Renders text using the backend's font system. The position represents the
     * top-left corner of the text bounding box.
     * 
     * @param text the text to render
     * @param x X position (left edge)
     * @param y Y position (top edge)
     * @param scale text scale multiplier
     */
    void drawText(String text, float x, float y, float scale);
    
    /**
     * Draw text centered horizontally at the specified position.
     * 
     * @param text the text to render
     * @param centerX center X position
     * @param y Y position (top edge)
     * @param scale text scale multiplier
     */
    void drawCenteredText(String text, float centerX, float y, float scale);
    
    /**
     * Get the width of text at the given scale.
     * 
     * @param text the text to measure
     * @param scale text scale multiplier
     * @return width in pixels
     */
    float getTextWidth(String text, float scale);
    
    /**
     * Get the height of text at the given scale.
     * 
     * @param text the text to measure
     * @param scale text scale multiplier
     * @return height in pixels
     */
    float getTextHeight(String text, float scale);
    
    // === Button Rendering ===
    
    /**
     * Draw a button widget at its current position.
     * 
     * <p>The button appearance changes based on its hover state.
     * 
     * @param button the button to render
     */
    void drawButton(mattmc.client.gui.components.Button button);
    
    /**
     * Draw a button widget at its current position with optional selection state.
     * 
     * <p>The button appearance changes based on hover and selection state.
     * 
     * @param button the button to render
     * @param selected whether the button is selected/clicked
     */
    void drawButton(mattmc.client.gui.components.Button button, boolean selected);
    
    // === Texture/Image Rendering ===
    
    /**
     * Load a texture resource (if not already loaded) and return its ID.
     * 
     * @param path resource path to the texture
     * @return texture ID for use with other methods
     */
    int loadTexture(String path);
    
    /**
     * Draw a texture at the specified position with scaling.
     * 
     * @param textureId texture ID from loadTexture
     * @param x X position (left edge)
     * @param y Y position (top edge)
     * @param width target width
     * @param height target height
     */
    void drawTexture(int textureId, float x, float y, float width, float height);
    
    /**
     * Get the native width of a loaded texture.
     * 
     * @param textureId texture ID from loadTexture
     * @return width in pixels
     */
    int getTextureWidth(int textureId);
    
    /**
     * Get the native height of a loaded texture.
     * 
     * @param textureId texture ID from loadTexture
     * @return height in pixels
     */
    int getTextureHeight(int textureId);
    
    /**
     * Release a texture resource.
     * 
     * @param textureId texture ID from loadTexture
     */
    void releaseTexture(int textureId);
    
    // === Matrix Operations ===
    
    /**
     * Push the current matrix state onto the stack.
     */
    void pushMatrix();
    
    /**
     * Pop the matrix state from the stack.
     */
    void popMatrix();
    
    /**
     * Translate (move) the current matrix.
     * 
     * @param x translation in X
     * @param y translation in Y
     * @param z translation in Z
     */
    void translateMatrix(float x, float y, float z);
    
    /**
     * Rotate the current matrix around an axis.
     * 
     * @param angle rotation angle in degrees
     * @param x X component of rotation axis
     * @param y Y component of rotation axis
     * @param z Z component of rotation axis
     */
    void rotateMatrix(float angle, float x, float y, float z);
    
    /**
     * Update a frustum from the current graphics API state.
     * 
     * <p>This method allows the backend to extract the current projection and modelview
     * matrices and use them to update the provided frustum. This maintains the backend-agnostic
     * design by allowing the frontend to use frustum culling without directly accessing
     * graphics API state.
     * 
     * <p>The backend implementation will:
     * <ul>
     *   <li><b>OpenGL:</b> Read GL_PROJECTION_MATRIX and GL_MODELVIEW_MATRIX from OpenGL state</li>
     *   <li><b>Vulkan (future):</b> Use the cached projection/view matrices</li>
     *   <li><b>Debug:</b> Use identity matrices or stored test values</li>
     * </ul>
     * 
     * @param frustum the frustum to update with current matrices
     */
    void updateFrustum(mattmc.client.renderer.Frustum frustum);

    // === Window Control ===
    
    /**
     * Set the cursor input mode.
     * 
     * @param windowHandle the native window handle
     * @param mode cursor mode (CURSOR_NORMAL, CURSOR_DISABLED, etc.)
     */
    void setCursorMode(long windowHandle, int mode);
    
    /**
     * Set whether the window should close.
     * 
     * @param windowHandle the native window handle
     * @param shouldClose true to signal window should close
     */
    void setWindowShouldClose(long windowHandle, boolean shouldClose);
    
    // === Input Callback Methods ===
    
    /**
     * Set cursor position callback for mouse movement.
     * 
     * @param windowHandle the native window handle
     * @param callback the callback to invoke when cursor moves, receives (x, y) coordinates
     */
    void setCursorPosCallback(long windowHandle, CursorPosCallback callback);
    
    /**
     * Set mouse button callback for mouse clicks.
     * 
     * @param windowHandle the native window handle
     * @param callback the callback to invoke when mouse button is pressed/released
     */
    void setMouseButtonCallback(long windowHandle, MouseButtonCallback callback);
    
    /**
     * Set framebuffer size callback for window resizing.
     * 
     * @param windowHandle the native window handle
     * @param callback the callback to invoke when framebuffer is resized
     */
    void setFramebufferSizeCallback(long windowHandle, FramebufferSizeCallback callback);
    
    /**
     * Set keyboard callback for key presses.
     * 
     * @param windowHandle the native window handle
     * @param callback the callback to invoke when a key is pressed/released
     */
    void setKeyCallback(long windowHandle, KeyCallback callback);
    
    /**
     * Set character callback for text input.
     * 
     * @param windowHandle the native window handle
     * @param callback the callback to invoke when a character is typed
     */
    void setCharCallback(long windowHandle, CharCallback callback);
    
    /**
     * Set scroll callback for mouse wheel scrolling.
     * 
     * @param windowHandle the native window handle
     * @param callback the callback to invoke when scroll wheel is used
     */
    void setScrollCallback(long windowHandle, ScrollCallback callback);
    
    /**
     * Update the viewport dimensions.
     * 
     * @param x viewport X position
     * @param y viewport Y position
     * @param width viewport width
     * @param height viewport height
     */
    void setViewport(int x, int y, int width, int height);
    
    // === Callback Interfaces ===
    
    @FunctionalInterface
    interface CursorPosCallback {
        void invoke(double x, double y);
    }
    
    @FunctionalInterface
    interface MouseButtonCallback {
        void invoke(int button, int action, int mods);
    }
    
    @FunctionalInterface
    interface FramebufferSizeCallback {
        void invoke(int width, int height);
    }
    
    @FunctionalInterface
    interface KeyCallback {
        void invoke(int key, int scancode, int action, int mods);
    }
    
    @FunctionalInterface
    interface CharCallback {
        void invoke(int codepoint);
    }
    
    @FunctionalInterface
    interface ScrollCallback {
        void invoke(double xoffset, double yoffset);
    }
    
    // === Input Constants ===
    
    /** Mouse button left */
    int MOUSE_BUTTON_LEFT = 0;
    /** Mouse button right */
    int MOUSE_BUTTON_RIGHT = 1;
    /** Mouse button middle */
    int MOUSE_BUTTON_MIDDLE = 2;
    
    /** Key/button action: released */
    int ACTION_RELEASE = 0;
    /** Key/button action: pressed */
    int ACTION_PRESS = 1;
    /** Key/button action: repeat (held down) */
    int ACTION_REPEAT = 2;
    
    // === Cursor Mode Constants ===
    
    /** Normal cursor mode (visible and not captured) */
    int CURSOR_NORMAL = 0x00034001; // GLFW_CURSOR_NORMAL
    /** Hidden cursor mode */
    int CURSOR_HIDDEN = 0x00034002; // GLFW_CURSOR_HIDDEN
    /** Disabled cursor mode (captured and invisible, used for FPS controls) */
    int CURSOR_DISABLED = 0x00034003; // GLFW_CURSOR_DISABLED
    
    // === Key Constants ===
    
    /** Escape key */
    int KEY_ESCAPE = 256;
    
    /** Enter/Return key */
    int KEY_ENTER = 257;
    
    /** Backspace key */
    int KEY_BACKSPACE = 259;
    
    /** Space key */
    int KEY_SPACE = 32;
    
    /** F3 key */
    int KEY_F3 = 292;
    
    /** F4 key */
    int KEY_F4 = 293;
    
    /** Slash key */
    int KEY_SLASH = 47;
    
    // === 3D Rendering Methods ===
    
    /**
     * Set up perspective projection for 3D rendering.
     * 
     * @param fov field of view in degrees
     * @param aspect aspect ratio (width/height)
     * @param nearPlane near clipping plane distance
     * @param farPlane far clipping plane distance
     */
    void setupPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane);
    
    /**
     * Set the clear color for the frame buffer.
     * 
     * @param r red component (0-1)
     * @param g green component (0-1)
     * @param b blue component (0-1)
     * @param a alpha component (0-1)
     */
    void setClearColor(float r, float g, float b, float a);
    
    /**
     * Clear the color and depth buffers.
     */
    void clearBuffers();
    
    /**
     * Enable depth testing for 3D rendering.
     */
    void enableDepthTest();
    
    /**
     * Disable depth testing.
     */
    void disableDepthTest();
    
    /**
     * Enable back-face culling.
     */
    void enableCullFace();
    
    /**
     * Disable face culling.
     */
    void disableCullFace();
    
    /**
     * Enable lighting for 3D rendering.
     */
    void enableLighting();
    
    /**
     * Disable lighting.
     */
    void disableLighting();
    
    /**
     * Set up a directional light (sun light).
     * 
     * @param dirX light direction X component
     * @param dirY light direction Y component
     * @param dirZ light direction Z component
     * @param brightness overall light brightness (0-1)
     */
    void setupDirectionalLight(float dirX, float dirY, float dirZ, float brightness);
    
    /**
     * Reset the modelview matrix to identity.
     */
    void loadIdentityMatrix();
    
    /**
     * Begin rendering 3D lines (for block outlines).
     */
    void begin3DLines();
    
    /**
     * End rendering 3D lines.
     */
    void end3DLines();
    
    /**
     * Add a 3D line vertex (used between begin3DLines and end3DLines).
     * 
     * @param x vertex X position
     * @param y vertex Y position
     * @param z vertex Z position
     */
    void addLineVertex(float x, float y, float z);
    
    /**
     * Enable texturing.
     */
    void enableTexture2D();
    
    /**
     * Disable texturing.
     */
    void disableTexture2D();
    
    /**
     * Check if texturing is currently enabled.
     * 
     * @return true if texture 2D is enabled
     */
    boolean isTexture2DEnabled();
    
    // === Factory Methods ===
    
    /**
     * Create a panorama renderer for the menu background.
     * 
     * <p>The panorama renderer displays a rotating skybox background, typically used
     * in menu screens. The renderer can optionally apply a blur effect.
     * 
     * @param basePath base path for the cubemap textures (without index and extension)
     * @param extension file extension for the texture files (e.g., ".png")
     * @return a new panorama renderer instance
     */
    mattmc.client.renderer.panorama.PanoramaRenderer createPanoramaRenderer(String basePath, String extension);
    
    /**
     * Get the item renderer for this backend.
     * 
     * <p>The item renderer is responsible for rendering items in the UI (hotbar, inventory, etc.).
     * This method provides access to the backend-specific implementation without directly
     * coupling to specific implementation classes.
     * 
     * <p><b>Usage:</b>
     * <pre>
     * ItemRenderer itemRenderer = backend.getItemRenderer();
     * itemRenderer.render(itemStack, x, y, size, backend);
     * </pre>
     * 
     * @return the item renderer for this backend
     */
    ItemRenderer getItemRenderer();
    
    /**
     * Tick texture animations.
     * 
     * <p>This method should be called once per game tick (typically 20 times per second)
     * to advance animated textures. Animated textures are those with associated .mcmeta
     * files that define animation properties.
     * 
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li><b>OpenGL:</b> Updates texture atlas regions using glTexSubImage2D</li>
     *   <li><b>Vulkan (future):</b> Would update texture memory or use array textures</li>
     *   <li><b>Debug:</b> Records tick command</li>
     * </ul>
     */
    void tickTextureAnimations();
}
