package mattmc.util;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;

/**
 * Tests for the ResourceLoader utility class.
 */
public class ResourceLoaderTest {
    
    @Test
    public void testGetResourceStream_ExistingResource() {
        // Test with an existing resource (splash text file)
        InputStream stream = ResourceLoader.getResourceStream("/assets/splashtext");
        assertNotNull(stream, "Should load existing resource");
        assertDoesNotThrow(() -> stream.close());
    }
    
    @Test
    public void testGetResourceStream_NonExistingResource() {
        // Test with a non-existing resource
        InputStream stream = ResourceLoader.getResourceStream("/nonexistent/resource.txt");
        assertNull(stream, "Should return null for non-existing resource");
    }
    
    @Test
    public void testLoadTextResource_ExistingResource() {
        // Test loading splash text
        String content = ResourceLoader.loadTextResource("/assets/splashtext");
        assertNotNull(content, "Should load text resource");
        assertFalse(content.isEmpty(), "Content should not be empty");
    }
    
    @Test
    public void testLoadTextResource_NonExistingResource() {
        String content = ResourceLoader.loadTextResource("/nonexistent/file.txt");
        assertNull(content, "Should return null for non-existing resource");
    }
    
    @Test
    public void testLoadTextLines_ExistingResource() {
        // Test loading splash text as lines
        List<String> lines = ResourceLoader.loadTextLines("/assets/splashtext");
        assertNotNull(lines, "Should load text lines");
        assertFalse(lines.isEmpty(), "Lines list should not be empty");
    }
    
    @Test
    public void testLoadTextLines_NonExistingResource() {
        List<String> lines = ResourceLoader.loadTextLines("/nonexistent/file.txt");
        assertNotNull(lines, "Should return empty list for non-existing resource");
        assertTrue(lines.isEmpty(), "Lines list should be empty for non-existing resource");
    }
    
    @Test
    public void testLoadJsonResource_ExistingResource() {
        // Test loading a JSON block model
        Gson gson = new Gson();
        // Using a simple structure that matches blockstate JSON
        Object result = ResourceLoader.loadJsonResource("/assets/blockstates/dirt.json", gson, Object.class);
        assertNotNull(result, "Should load JSON resource");
    }
    
    @Test
    public void testLoadJsonResource_NonExistingResource() {
        Gson gson = new Gson();
        Object result = ResourceLoader.loadJsonResource("/nonexistent/model.json", gson, Object.class);
        assertNull(result, "Should return null for non-existing JSON resource");
    }
    
    @Test
    public void testResourceExists_ExistingResource() {
        boolean exists = ResourceLoader.resourceExists("/assets/splashtext");
        assertTrue(exists, "Should return true for existing resource");
    }
    
    @Test
    public void testResourceExists_NonExistingResource() {
        boolean exists = ResourceLoader.resourceExists("/nonexistent/resource.txt");
        assertFalse(exists, "Should return false for non-existing resource");
    }
    
    @Test
    public void testLoadBinaryResource_ExistingResource() {
        // Test loading a texture image (PNG)
        byte[] data = ResourceLoader.loadBinaryResource("/assets/textures/block/dirt.png");
        assertNotNull(data, "Should load binary resource");
        assertTrue(data.length > 0, "Binary data should not be empty");
        
        // Check PNG header (first 8 bytes: 89 50 4E 47 0D 0A 1A 0A)
        assertEquals((byte)0x89, data[0], "Should start with PNG signature");
        assertEquals((byte)0x50, data[1], "Should start with PNG signature");
        assertEquals((byte)0x4E, data[2], "Should start with PNG signature");
        assertEquals((byte)0x47, data[3], "Should start with PNG signature");
    }
    
    @Test
    public void testLoadBinaryResource_NonExistingResource() {
        byte[] data = ResourceLoader.loadBinaryResource("/nonexistent/image.png");
        assertNull(data, "Should return null for non-existing binary resource");
    }
    
    @Test
    public void testLoadTextResource_UTF8Encoding() {
        // Test that UTF-8 encoding is properly handled
        String content = ResourceLoader.loadTextResource("/assets/splashtext");
        assertNotNull(content);
        // The content should not have encoding issues
        assertFalse(content.contains("�"), "Should not have encoding error characters");
    }
    
    @Test
    public void testLoadTextLines_PreservesLineContent() {
        // Test that line content is preserved correctly
        List<String> lines = ResourceLoader.loadTextLines("/assets/splashtext");
        assertNotNull(lines);
        for (String line : lines) {
            assertNotNull(line, "Each line should not be null");
        }
    }
    
    /**
     * Test data class for JSON parsing.
     */
    private static class TestData {
        String name;
        int value;
    }
}
