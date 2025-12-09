package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for dimension configuration with ResourceManager.
 */
public class DimensionConfigIntegrationTest {
    
    @Test
    public void testLoadDimensionConfigFromTestPack() {
        // Create mock ResourceManager
        ResourceManager resourceManager = mock(ResourceManager.class);
        
        // Create ResourceShaderPackSource for test_shader pack
        ResourceShaderPackSource source = new ResourceShaderPackSource(resourceManager, "test_shader");
        
        // Mock the file existence checks for dimension detection
        // In actual runtime, these would be real resources
        ShaderPackSource mockSource = mock(ShaderPackSource.class);
        when(mockSource.fileExists("world0/composite.fsh")).thenReturn(true);
        when(mockSource.fileExists("world-1/composite.fsh")).thenReturn(true);
        when(mockSource.fileExists("world1/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(mockSource);
        
        // Verify default dimension mapping
        assertEquals("world0", config.getDimensionFolder(DimensionId.OVERWORLD));
        assertEquals("world-1", config.getDimensionFolder(DimensionId.NETHER));
        assertEquals("world1", config.getDimensionFolder(DimensionId.END));
    }
    
    @Test
    public void testDimensionFolderResolution() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        // Test various dimension ID formats
        assertEquals("world0", config.getDimensionFolder("minecraft:overworld"));
        assertEquals("world0", config.getDimensionFolder(new NamespacedId("minecraft:overworld")));
        assertEquals("world0", config.getDimensionFolder(new NamespacedId("minecraft", "overworld")));
    }
    
    @Test
    public void testGetAllDimensionIds() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        when(source.fileExists("world-1/composite.fsh")).thenReturn(true);
        when(source.fileExists("world1/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        List<String> ids = config.getDimensionIds();
        assertEquals(3, ids.size());
        assertTrue(ids.contains("world0"));
        assertTrue(ids.contains("world-1"));
        assertTrue(ids.contains("world1"));
    }
    
    @Test
    public void testDimensionMapAccess() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        // Verify dimension map is accessible
        var dimensionMap = config.getDimensionMap();
        assertNotNull(dimensionMap);
        assertTrue(dimensionMap.containsKey(DimensionId.OVERWORLD));
        assertEquals("world0", dimensionMap.get(DimensionId.OVERWORLD));
    }
}
