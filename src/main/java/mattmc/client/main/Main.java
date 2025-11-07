package mattmc.client.main;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.gui.screens.TitleScreen;
import mattmc.util.AppPaths;
import mattmc.client.settings.KeybindManager;
import mattmc.client.settings.OptionsManager;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String VERSION = "0.0.10";
    
    public static void main(String[] args) {
        // Ensure data dir in parent of the JAR directory (or classes dir when in IDE)
        Path dataDir;
        try {
            dataDir = AppPaths.ensureDataDirInJarParent("MattMC");
            // expose it for other systems that may want to read it
            System.setProperty("mattmc.dataDir", dataDir.toString());
            logger.info("MattMC data dir: {}", dataDir);
        } catch (Exception e) {
            // Hard fail if we can't make the data directory
            logger.error("Failed to create data directory: {}", e);
            e.printStackTrace();
            System.exit(1);
            return;
        }

        // Load keybinds from Options.txt
        KeybindManager.loadKeybinds();
        
        // Load options (blur settings, etc.)
        OptionsManager.loadOptions();

        // Boot the game with configured resolution
        int width = OptionsManager.getResolutionWidth();
        int height = OptionsManager.getResolutionHeight();
        try (var window = new Window(width, height, "MattMC")) {
            var game = new Minecraft(window);
            // If your Minecraft wants to know the dataDir, you can add a setter/ctor and pass it in.
            game.setScreen(new TitleScreen(game));
            game.run();
        }
    }
}
