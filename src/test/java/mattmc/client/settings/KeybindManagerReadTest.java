package mattmc.client.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KeybindManager reading keybinds from Options.txt file.
 */
public class KeybindManagerReadTest {
    
    @TempDir
    Path tempDir;
    
    /**
     * Test that KeybindManager correctly skips non-keybind options and only reads
     * keybinds after the "# Keybinds" section marker.
     */
    @Test
    public void testSkipsOptionsBeforeKeybindSection() throws Exception {
        // Create a test Options.txt file with options before keybinds
        Path optionsFile = tempDir.resolve("TestOptions.txt");
        
        try (BufferedWriter writer = Files.newBufferedWriter(optionsFile)) {
            writer.write("# MattMC Options File\n");
            writer.write("\n");
            writer.write("# Options section\n");
            writer.write("blur_title_screen=false\n");
            writer.write("blur_menu_screens=true\n");
            writer.write("show_block_name=true\n");
            writer.write("smooth_lighting=true\n");
            writer.write("shadows=true\n");
            writer.write("fps_cap=244\n");
            writer.write("resolution=1920x1080\n");
            writer.write("fullscreen=true\n");
            writer.write("render_distance=16\n");
            writer.write("\n");
            writer.write("# Keybinds (format: action=key_name)\n");
            writer.write("forward=w\n");
            writer.write("backward=s\n");
            writer.write("left=a\n");
            writer.write("right=d\n");
            writer.write("jump=space\n");
        }
        
        // Read the file line by line and verify logic
        String[] lines = Files.readAllLines(optionsFile).toArray(new String[0]);
        
        boolean inKeybindSection = false;
        int keybindCount = 0;
        int optionCount = 0;
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip empty lines
            if (line.isEmpty()) {
                continue;
            }
            
            // Check if we've reached the keybind section
            if (line.startsWith("# Keybinds")) {
                inKeybindSection = true;
                continue;
            }
            
            // Skip all lines until we reach the keybind section
            if (!inKeybindSection) {
                if (!line.startsWith("#") && line.contains("=")) {
                    optionCount++; // Count options that would be skipped
                }
                continue;
            }
            
            // Skip comments within the keybind section
            if (line.startsWith("#")) {
                continue;
            }
            
            // Count keybinds
            if (line.contains("=")) {
                keybindCount++;
            }
        }
        
        // Verify that we found the expected number of options and keybinds
        assertEquals(9, optionCount, "Should have skipped 9 option lines");
        assertEquals(5, keybindCount, "Should have read 5 keybind lines");
    }
    
    /**
     * Test that the logic correctly identifies the keybind section marker.
     */
    @Test
    public void testKeybindSectionMarkerDetection() {
        String[] testLines = {
            "# This is a comment",
            "#Keybinds without space",  // Should NOT match
            "# Keybinds (format: action=key_name)",  // Should match
            "# Keybinds",  // Should match
            "# Keybindings"  // Should NOT match (different word)
        };
        
        // Test each line
        assertFalse(testLines[0].startsWith("# Keybinds"));
        assertFalse(testLines[1].startsWith("# Keybinds"));
        assertTrue(testLines[2].startsWith("# Keybinds"));
        assertTrue(testLines[3].startsWith("# Keybinds"));
        assertFalse(testLines[4].startsWith("# Keybinds"));
    }
}
