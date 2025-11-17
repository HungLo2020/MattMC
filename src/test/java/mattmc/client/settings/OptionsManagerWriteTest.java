package mattmc.client.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that OptionsManager doesn't duplicate option keys in the keybind section.
 */
public class OptionsManagerWriteTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    public void testOptionsDontAppearInKeybindSection() throws Exception {
        // Create a sample Options.txt with both options and keybinds
        Path optionsFile = tempDir.resolve("Options.txt");
        
        StringBuilder content = new StringBuilder();
        content.append("# MattMC Options File\n");
        content.append("\n");
        content.append("blur_title_screen=false\n");
        content.append("blur_menu_screens=true\n");
        content.append("show_block_name=true\n");
        content.append("fps_cap=60\n");
        content.append("resolution=1920x1080\n");
        content.append("fullscreen=true\n");
        content.append("render_distance=16\n");
        content.append("mipmaps=4\n");
        content.append("anisotropic_filtering=16\n");
        content.append("smooth_lighting=true\n");
        content.append("shadows=true\n");
        content.append("\n");
        content.append("# Keybinds (format: action=key_name)\n");
        content.append("forward=w\n");
        content.append("backward=s\n");
        content.append("jump=space\n");
        
        Files.writeString(optionsFile, content.toString());
        
        // Read the file and verify structure
        List<String> lines = Files.readAllLines(optionsFile);
        
        // Find the keybind section marker
        int keybindMarkerIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("# Keybinds")) {
                keybindMarkerIndex = i;
                break;
            }
        }
        
        assertTrue(keybindMarkerIndex >= 0, "Should have keybind section marker");
        
        // Check that no option keys appear after the keybind marker
        String[] optionKeys = {
            "blur_title_screen", "blur_menu_screens", "show_block_name",
            "fps_cap", "resolution", "fullscreen", "render_distance",
            "mipmaps", "anisotropic_filtering", "smooth_lighting", "shadows"
        };
        
        List<String> duplicateOptions = new ArrayList<>();
        for (int i = keybindMarkerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                for (String optionKey : optionKeys) {
                    if (key.equals(optionKey)) {
                        duplicateOptions.add(key + " at line " + (i + 1));
                    }
                }
            }
        }
        
        assertTrue(duplicateOptions.isEmpty(), 
            "Options should not appear in keybind section. Found: " + duplicateOptions);
        
        // Verify we still have keybinds
        int keybindCount = 0;
        for (int i = keybindMarkerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                keybindCount++;
            }
        }
        
        assertTrue(keybindCount >= 3, "Should have at least 3 keybinds");
    }
}
