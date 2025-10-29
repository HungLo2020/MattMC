package MattMC;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.screens.TitleScreen;
import MattMC.util.AppPaths;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        // Ensure data dir in parent of the JAR directory (or classes dir when in IDE)
        Path dataDir;
        try {
            dataDir = AppPaths.ensureDataDirInJarParent("MattMC");
            // expose it for other systems that may want to read it
            System.setProperty("mattmc.dataDir", dataDir.toString());
            System.out.println("MattMC data dir: " + dataDir);
        } catch (Exception e) {
            // Hard fail if we can't make the data directory
            System.err.println("Failed to create data directory: " + e);
            e.printStackTrace();
            System.exit(1);
            return;
        }

        // Boot the game
        try (var window = new Window(1280, 720, "MattMC")) {
            var game = new Game(window);
            // If your Game wants to know the dataDir, you can add a setter/ctor and pass it in.
            game.setScreen(new TitleScreen(game));
            game.run();
        }
    }
}
