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
    private final Game game;
    private final Window window;
    
    private final Chunk chunk;
    
    // Camera controls
    private float cameraX = 8f;      // Center of chunk
    private float cameraY = 80f;     // Above surface
    private float cameraZ = 40f;     // Back from chunk
    private float cameraYaw = 0f;    // Rotation around Y axis
    private float cameraPitch = -20f; // Look down slightly
    
    private double lastFrameTimeSec = now();

    public DevplayScreen(Game game) {
        this.game = game;
        this.window = game.window();
        
        // Create and generate chunk at origin (0, 0)
        this.chunk = new Chunk(0, 0);
        this.chunk.generateFlatTerrain(64); // Surface at y=64

        // Resize -> update viewport
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // ESC to go back, WASD/Arrow keys for camera movement
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                game.setScreen(new SingleplayerScreen(game));
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
        float rotSpeed = 60f * (float)dt;
        
        // Camera controls
        long win = window.handle();
        
        // WASD movement
        if (glfwGetKey(win, GLFW_KEY_W) == GLFW_PRESS) {
            cameraZ -= moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_S) == GLFW_PRESS) {
            cameraZ += moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_A) == GLFW_PRESS) {
            cameraX -= moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_D) == GLFW_PRESS) {
            cameraX += moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_SPACE) == GLFW_PRESS) {
            cameraY += moveSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
            cameraY -= moveSpeed;
        }
        
        // Arrow keys for camera rotation
        if (glfwGetKey(win, GLFW_KEY_LEFT) == GLFW_PRESS) {
            cameraYaw -= rotSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_RIGHT) == GLFW_PRESS) {
            cameraYaw += rotSpeed;
        }
        if (glfwGetKey(win, GLFW_KEY_UP) == GLFW_PRESS) {
            cameraPitch = Math.max(-89f, cameraPitch - rotSpeed);
        }
        if (glfwGetKey(win, GLFW_KEY_DOWN) == GLFW_PRESS) {
            cameraPitch = Math.min(89f, cameraPitch + rotSpeed);
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

        // Apply camera transformations
        glRotatef(cameraPitch, 1f, 0f, 0f);
        glRotatef(cameraYaw, 0f, 1f, 0f);
        glTranslatef(-cameraX, -cameraY, -cameraZ);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        renderChunk(chunk);
        
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
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

    private static double now() { return System.nanoTime() * 1e-9; }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}