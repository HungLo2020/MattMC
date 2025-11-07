package mattmc.client.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        try (InputStream in = SplashTextLoader.class.getResourceAsStream(SPLASH_TEXT_PATH)) {
            if (in == null) {
                // Fallback if file not found
                splashTexts.add("Awesome!");
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        splashTexts.add(line);
                    }
                }
            }
        } catch (IOException e) {
            // Use fallback on error
            splashTexts.clear();
            splashTexts.add("Awesome!");
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
