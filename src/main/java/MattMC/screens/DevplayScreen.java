package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.world.Block;
import MattMC.world.BlockType;
import MattMC.world.Chunk;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/* Devplay: renders a 16x16 chunk with flat terrain at y=64. ESC returns to Singleplayer. */
public final class DevplayScreen implements Screen {
    // Mouse sensitivity multiplier (1.0 = default, 0.5 = half speed, 2.0 = double speed)
    private static final float MOUSE_SENSITIVITY = 1.0f;
    
    private final Game game;
    private final Window window;
    
    private final Chunk chunk;
    
    // Player/Camera position and orientation
    private float playerX = 8f;      // Center of chunk
    private float playerY = 80f;     // Above surface
    private float playerZ = 40f;     // Back from chunk
    private float yaw = 0f;          // Horizontal rotation (left/right)
    private float pitch = 0f;        // Vertical rotation (up/down)
    
    // Mouse state
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;
    
    private double lastFrameTimeSec = now();

    public DevplayScreen(Game game) {
        this.game = game;
        this.window = game.window();
        
        // Create and generate chunk at origin (0, 0)
        this.chunk = new Chunk(0, 0);
        this.chunk.generateFlatTerrain(64); // Surface at y=64

        // Capture mouse for FPS-style controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        
        // Resize -> update viewport
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // ESC to release mouse and go back
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                game.setScreen(new SingleplayerScreen(game));
            }
        });
        
        // Mouse callback for looking around
        glfwSetCursorPosCallback(window.handle(), (win, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
                return;
            }
            
            double xOffset = xpos - lastMouseX;
            double yOffset = ypos - lastMouseY; // Reversed: y-coordinates range from bottom to top
            lastMouseX = xpos;
            lastMouseY = ypos;
            
            // Apply sensitivity
            xOffset *= MOUSE_SENSITIVITY * 0.1;
            yOffset *= MOUSE_SENSITIVITY * 0.1;
            
            yaw += (float) xOffset;
            pitch += (float) yOffset;
            
            // Clamp pitch to prevent screen flip
            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;
        });
        
        // Mouse button callback for breaking/placing blocks
        glfwSetMouseButtonCallback(window.handle(), (win, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    // Break block
                    breakBlock();
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    // Place block
                    placeBlock();
                }
            }
        });
    }

    @Override
    public void tick() {
        double now = now();
        double dt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (dt < 0) dt = 0;
        if (dt > 0.5) dt = 0.5;
        
        float moveSpeed = 10f * (float)dt;
        
        long win = window.handle();
        
        // Calculate forward and right vectors based on yaw
        float yawRad = (float) Math.toRadians(yaw);
        float forwardX = (float) Math.sin(yawRad);
        float forwardZ = -(float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);
        
        // WASD movement (relative to view direction)
        if (glfwGetKey(win, GLFW_KEY_W) == GLFW_PRESS) {
            playerX += forwardX * moveSpeed;
            playerZ += forwardZ * moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_S) == GLFW_PRESS) {
            playerX -= forwardX * moveSpeed;
            playerZ -= forwardZ * moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_A) == GLFW_PRESS) {
            playerX -= rightX * moveSpeed;
            playerZ -= rightZ * moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_D) == GLFW_PRESS) {
            playerX += rightX * moveSpeed;
            playerZ += rightZ * moveSpeed;
        }
        
        // Vertical movement
        if (glfwGetKey(win, GLFW_KEY_SPACE) == GLFW_PRESS) {
            playerY += moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || glfwGetKey(win, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS) {
            playerY -= moveSpeed;
        }
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();

        // Clear color + depth (sky blue)
        glClearColor(0.53f, 0.81f, 0.92f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Perspective projection
        float aspect = Math.max(1f, (float) w / Math.max(1, h));
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float fov = 70f, zn = 0.1f, zf = 500f;
        float top = (float) (Math.tan(Math.toRadians(fov * 0.5)) * zn);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        glFrustum(left, right, bottom, top, zn, zf);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Apply camera transformations (pitch, yaw, then position)
        glRotatef(pitch, 1f, 0f, 0f);
        glRotatef(yaw, 0f, 1f, 0f);
        glTranslatef(-playerX, -playerY, -playerZ);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        renderChunk(chunk);
        
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        
        // Draw crosshair on top of everything
        drawCrosshair(w, h);
    }

    /**
     * Render all blocks in a chunk.
     * Uses face culling - only draws faces that are exposed to air.
     * 
     * Note: This iterates all 98,304 possible block positions but skips air blocks
     * immediately with 'continue'. For larger worlds, consider using a sparse data
     * structure or chunk sections like Minecraft does (16x16x16 sections).
     */
    private void renderChunk(Chunk chunk) {
        for (int x = 0; x < Chunk.WIDTH; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.DEPTH; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.isAir()) continue;  // Skip air blocks (most of the chunk)
                    
                    // Calculate world position
                    float wx = x;
                    float wy = Chunk.chunkYToWorldY(y);
                    float wz = z;
                    
                    // Only render visible faces (adjacent to air)
                    renderBlockAt(wx, wy, wz, block, chunk, x, y, z);
                }
            }
        }
    }
    
    /**
     * Render a single block at the given world position.
     * Only renders faces that are exposed to air (face culling).
     */
    private void renderBlockAt(float x, float y, float z, Block block, Chunk chunk, int cx, int cy, int cz) {
        BlockType type = block.type();
        int color = type.color();
        
        // Check each face and only render if adjacent block is air
        // Top face (+Y)
        if (shouldRenderFace(chunk, cx, cy + 1, cz)) {
            setColor(color, 1f);
            drawTopFace(x, y, z);
        }
        
        // Bottom face (-Y)
        if (shouldRenderFace(chunk, cx, cy - 1, cz)) {
            setColor(darkenColor(color), 1f);
            drawBottomFace(x, y, z);
        }
        
        // North face (-Z)
        if (shouldRenderFace(chunk, cx, cy, cz - 1)) {
            setColor(adjustColorBrightness(color, 0.8f), 1f);
            drawNorthFace(x, y, z);
        }
        
        // South face (+Z)
        if (shouldRenderFace(chunk, cx, cy, cz + 1)) {
            setColor(adjustColorBrightness(color, 0.8f), 1f);
            drawSouthFace(x, y, z);
        }
        
        // West face (-X)
        if (shouldRenderFace(chunk, cx - 1, cy, cz)) {
            setColor(adjustColorBrightness(color, 0.6f), 1f);
            drawWestFace(x, y, z);
        }
        
        // East face (+X)
        if (shouldRenderFace(chunk, cx + 1, cy, cz)) {
            setColor(adjustColorBrightness(color, 0.6f), 1f);
            drawEastFace(x, y, z);
        }
        
        // Draw black outline around the cube
        drawBlockOutline(x, y, z);
    }
    
    /**
     * Check if a face should be rendered (is the adjacent block air?).
     */
    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z) {
        // If out of bounds, consider it air (should render)
        if (x < 0 || x >= Chunk.WIDTH || y < 0 || y >= Chunk.HEIGHT || z < 0 || z >= Chunk.DEPTH) {
            return true;
        }
        return chunk.getBlock(x, y, z).isAir();
    }
    
    // Block face rendering methods (each face is 1x1x1 cube)
    private void drawTopFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y1 = y + 1;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        // Counter-clockwise when viewed from above
        glVertex3f(x0, y1, z0); glVertex3f(x0, y1, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z1); glVertex3f(x1, y1, z0);
        glEnd();
    }
    
    private void drawBottomFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        // Counter-clockwise when viewed from below
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        glEnd();
    }
    
    private void drawNorthFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z;
        glBegin(GL_TRIANGLES);
        glVertex3f(x1, y0, z0); glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        glEnd();
    }
    
    private void drawSouthFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z1 = z + 1;
        glBegin(GL_TRIANGLES);
        glVertex3f(x0, y0, z1); glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glEnd();
    }
    
    private void drawWestFace(float x, float y, float z) {
        float x0 = x;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        glVertex3f(x0, y0, z0); glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);
        glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        glEnd();
    }
    
    private void drawEastFace(float x, float y, float z) {
        float x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glEnd();
    }
    
    /**
     * Draw a black outline around a cube to make blocks more distinguishable.
     * Draws the 12 edges of the cube.
     */
    private void drawBlockOutline(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Set black color for outline
        setColor(0x000000, 1f);
        
        glBegin(GL_LINES);
        // Bottom 4 edges
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y0, z0);
        
        // Top 4 edges
        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        
        // 4 vertical edges
        glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);
        glEnd();
    }
    
    private int darkenColor(int rgb) {
        return adjustColorBrightness(rgb, 0.5f);
    }
    
    private int adjustColorBrightness(int rgb, float factor) {
        int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int)((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }

    /**
     * Draw crosshair in the center of the screen.
     */
    private void drawCrosshair(int screenWidth, int screenHeight) {
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Draw white crosshair
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float size = 10f;
        float thickness = 2f;
        
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        // Horizontal line
        glVertex2f(centerX - size, centerY - thickness/2);
        glVertex2f(centerX + size, centerY - thickness/2);
        glVertex2f(centerX + size, centerY + thickness/2);
        glVertex2f(centerX - size, centerY + thickness/2);
        // Vertical line
        glVertex2f(centerX - thickness/2, centerY - size);
        glVertex2f(centerX + thickness/2, centerY - size);
        glVertex2f(centerX + thickness/2, centerY + size);
        glVertex2f(centerX - thickness/2, centerY + size);
        glEnd();
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Ray cast to find the block the player is looking at and break it.
     */
    private void breakBlock() {
        BlockHitResult hit = raycastBlock(5.0f);
        if (hit != null) {
            chunk.setBlock(hit.x, hit.y, hit.z, Block.AIR);
        }
    }
    
    /**
     * Ray cast to find where to place a block and place it.
     */
    private void placeBlock() {
        BlockHitResult hit = raycastBlock(5.0f);
        if (hit != null && hit.adjacentX >= 0 && hit.adjacentY >= 0 && hit.adjacentZ >= 0) {
            // Place block at the adjacent position (the face we hit)
            Block existing = chunk.getBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ);
            if (existing.isAir()) {
                chunk.setBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ, new Block(BlockType.STONE));
            }
        }
    }
    
    /**
     * Perform ray casting to find the block the player is looking at.
     * Returns null if no block is found within maxDistance.
     */
    private BlockHitResult raycastBlock(float maxDistance) {
        // Calculate ray direction from pitch and yaw
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        
        float dirX = (float) (Math.cos(pitchRad) * Math.sin(yawRad));
        float dirY = (float) (-Math.sin(pitchRad));
        float dirZ = (float) (-Math.cos(pitchRad) * Math.cos(yawRad));
        
        // DDA algorithm for voxel traversal
        float rayX = playerX;
        float rayY = playerY;
        float rayZ = playerZ;
        
        float stepSize = 0.1f;
        int steps = (int) (maxDistance / stepSize);
        
        int lastBlockX = -1, lastBlockY = -1, lastBlockZ = -1;
        
        for (int i = 0; i < steps; i++) {
            rayX += dirX * stepSize;
            rayY += dirY * stepSize;
            rayZ += dirZ * stepSize;
            
            int blockX = (int) Math.floor(rayX);
            int blockY = (int) Math.floor(rayY);
            int blockZ = (int) Math.floor(rayZ);
            
            // Convert world Y to chunk Y
            int chunkY = Chunk.worldYToChunkY(blockY);
            
            // Check if within chunk bounds
            if (blockX >= 0 && blockX < Chunk.WIDTH && 
                chunkY >= 0 && chunkY < Chunk.HEIGHT && 
                blockZ >= 0 && blockZ < Chunk.DEPTH) {
                
                Block block = chunk.getBlock(blockX, chunkY, blockZ);
                if (!block.isAir()) {
                    // Found a solid block - return it and the last air position
                    return new BlockHitResult(blockX, chunkY, blockZ, lastBlockX, lastBlockY, lastBlockZ);
                }
            }
            
            lastBlockX = blockX;
            lastBlockY = chunkY;
            lastBlockZ = blockZ;
        }
        
        return null;
    }
    
    /**
     * Simple data class to hold ray cast hit result.
     */
    private static class BlockHitResult {
        final int x, y, z;              // Block that was hit
        final int adjacentX, adjacentY, adjacentZ;  // Adjacent air block (for placing)
        
        BlockHitResult(int x, int y, int z, int adjX, int adjY, int adjZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.adjacentX = adjX;
            this.adjacentY = adjY;
            this.adjacentZ = adjZ;
        }
    }
    
    private static double now() { return System.nanoTime() * 1e-9; }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}