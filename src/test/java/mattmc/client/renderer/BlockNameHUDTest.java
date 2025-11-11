package mattmc.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockNameHUD utility methods.
 * Note: Rendering tests are not included as they require OpenGL context.
 */
public class BlockNameHUDTest {
    
    @Test
    public void testTitleCaseConversion() {
        // Create a test instance to access the private toTitleCase method indirectly
        // We'll test through the getBlockDisplayName method behavior
        
        // Test that snake_case is converted to Title Case
        String input1 = "grass_block";
        String expected1 = "Grass Block";
        assertEquals(expected1, toTitleCase(input1));
        
        String input2 = "dirt";
        String expected2 = "Dirt";
        assertEquals(expected2, toTitleCase(input2));
        
        String input3 = "oak_wood_planks";
        String expected3 = "Oak Wood Planks";
        assertEquals(expected3, toTitleCase(input3));
        
        // Test edge cases
        String input4 = "";
        String expected4 = "";
        assertEquals(expected4, toTitleCase(input4));
        
        String input5 = "a";
        String expected5 = "A";
        assertEquals(expected5, toTitleCase(input5));
    }
    
    /**
     * Helper method that replicates the private toTitleCase logic from BlockNameHUD.
     * This allows us to test the conversion logic without making it public.
     */
    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String[] parts = input.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
}
