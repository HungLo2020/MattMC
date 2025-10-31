package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.player.BlockInteraction;
import MattMC.player.Player;
import MattMC.player.PlayerController;
import MattMC.renderer.ChunkRenderer;
import MattMC.renderer.UIRenderer;
import MattMC.world.BlockType;
import MattMC.world.Chunk;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Devplay screen - renders a 16x16 chunk with flat terrain at y=64.
 * Now refactored with separate Player, PlayerController, ChunkRenderer, etc.
 * ESC returns to Singleplayer.
 */
public final class DevplayScreen implements Screen {
    private final Game game;
    private final Window window;
    
    // Game components (following Minecraft's architecture)
    private final Chunk chunk;
    private final Player player;
    private final PlayerController playerController;
    private final BlockInteraction blockInteraction;
    private final ChunkRenderer chunkRenderer;
    private final UIRenderer uiRenderer;
    
    private double lastFrameTimeSec = now();

    public DevplayScreen(Game game) {
        this.game = game;
        this.window = game.window();
        
        // Initialize game components
        this.chunk = new Chunk(0, 0);
        this.chunk.generateFlatTerrain(64); // Surface at y=64
        
        this.player = new Player(8f, 80f, 40f); // Center of chunk, above surface
        this.playerController = new PlayerController(player);
        this.blockInteraction = new BlockInteraction(player, chunk);
        this.chunkRenderer = new ChunkRenderer();
        this.uiRenderer = new UIRenderer();

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
            playerController.handleMouseMovement(xpos, ypos);
        });
        
        // Mouse button callback for breaking/placing blocks
        glfwSetMouseButtonCallback(window.handle(), (win, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    blockInteraction.breakBlock();
                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    blockInteraction.placeBlock(BlockType.STONE);
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
        
        // Update player movement based on input
        playerController.updateMovement(window.handle(), (float)dt);
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
        glRotatef(player.getPitch(), 1f, 0f, 0f);
        glRotatef(player.getYaw(), 0f, 1f, 0f);
        glTranslatef(-player.getX(), -player.getY(), -player.getZ());

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        chunkRenderer.renderChunk(chunk);
        
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        
        // Draw crosshair on top of everything
        uiRenderer.drawCrosshair(w, h);
    }

    private static double now() { return System.nanoTime() * 1e-9; }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}
