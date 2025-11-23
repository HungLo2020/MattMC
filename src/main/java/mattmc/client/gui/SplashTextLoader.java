package mattmc.client.gui;

import mattmc.util.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loads and provides random splash text from the assets.
 */
public class SplashTextLoader {
    private static final String SPLASH_TEXT_PATH = "/assets/splashtext";
    private static final List<String> splashTexts = new ArrayList<>();
    private static final Random random = new Random();
    
    static {
        loadSplashTexts();
    }
    
    /**
     * Load splash texts from the file.
     */
    private static void loadSplashTexts() {
        List<String> lines = ResourceLoader.loadTextLines(SPLASH_TEXT_PATH);
        
        if (lines.isEmpty()) {
            // Fallback if file not found or empty
            splashTexts.add("Awesome!");
            return;
        }
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                splashTexts.add(line);
            }
        }
        
        // Ensure we have at least one splash text
        if (splashTexts.isEmpty()) {
            splashTexts.add("Awesome!");
        }
    }
    
    /**
     * Get a random splash text.
     * @return A random splash text string
     */
    public static String getRandomSplashText() {
        if (splashTexts.isEmpty()) {
            return "Awesome!";
        }
        return splashTexts.get(random.nextInt(splashTexts.size()));
    }
}
