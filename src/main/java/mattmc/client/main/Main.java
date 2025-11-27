package mattmc.client.main;

import mattmc.client.MattMC;
import mattmc.client.renderer.backend.RenderBackendFactory;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.gui.screens.TitleScreen;
import mattmc.util.AppPaths;
import mattmc.client.settings.KeybindManager;
import mattmc.client.settings.OptionsManager;

import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    // Initialize data directory BEFORE the logger so logback can use mattmc.dataDir property
    private static final Path dataDir;
    
    static {
        try {
            dataDir = AppPaths.ensureDataDirInJarParent("MattMC");
            // Set property before any logger is created so logback can use it
            System.setProperty("mattmc.dataDir", dataDir.toString());
        } catch (IOException | IllegalArgumentException e) {
            throw new ExceptionInInitializerError("Failed to create data directory: " + e.getMessage());
        }
    }
    
    // Logger initialized AFTER mattmc.dataDir is set
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String VERSION = "0.0.10";
    
    public static void main(String[] args) {
        logger.info("MattMC data dir: {}", dataDir);

        // Load keybinds from Options.txt
        KeybindManager.loadKeybinds();
        
        // Load options (blur settings, etc.)
        OptionsManager.loadOptions();

        // Boot the game with configured resolution using the backend factory
        // This avoids directly importing OpenGL-specific classes
        int width = OptionsManager.getResolutionWidth();
        int height = OptionsManager.getResolutionHeight();
        
        RenderBackendFactory factory = RenderBackendFactory.createDefault();
        WindowHandle window = factory.createWindow(width, height, "MattMC");
        
        try (AutoCloseable windowCloseable = factory.getWindowCloseable()) {
            var game = new MattMC(window, factory);
            // If your MattMC wants to know the dataDir, you can add a setter/ctor and pass it in.
            game.setScreen(new TitleScreen(game));
            game.run();
        } catch (Exception e) {
            logger.error("Error during game execution: {}", e.getMessage(), e);
            throw new RuntimeException("Game execution failed", e);
        }
    }
}
