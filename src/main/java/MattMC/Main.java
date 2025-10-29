package MattMC;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.screens.TitleScreen;

public final class Main {
    public static void main(String[] args) {
        try (var window = new Window(1280, 720, "MattMC")) {
            var game = new Game(window);
            game.setScreen(new TitleScreen(game));
            game.run();
        }
    }
}
