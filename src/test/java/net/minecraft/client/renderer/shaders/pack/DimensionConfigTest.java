package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DimensionConfig (IRIS dimension parsing logic).
 */
public class DimensionConfigTest {
    
    @Test
    public void testDefaultDimensionDetection() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        when(source.fileExists("world-1/composite.fsh")).thenReturn(true);
        when(source.fileExists("world1/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        assertEquals("world0", config.getDimensionFolder("minecraft:overworld"));
        assertEquals("world-1", config.getDimensionFolder("minecraft:the_nether"));
        assertEquals("world1", config.getDimensionFolder("minecraft:the_end"));
    }
    
    @Test
    public void testDefaultFallbackToWorld0() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        // Wildcard should map to world0
        assertEquals("world0", config.getDimensionFolder("custom:dimension"));
    }
    
    @Test
    public void testDimensionPropertiesParsing() throws IOException {
        // IRIS format: dimension.<foldername>=<dimension IDs>
        String dimensionProps = "dimension.custom_world=minecraft:overworld\n" +
                               "dimension.nether_world=minecraft:the_nether\n" +
                               "dimension.end_world=minecraft:the_end\n";
        
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.readFile("dimension.properties")).thenReturn(Optional.of(dimensionProps));
        
        DimensionConfig config = DimensionConfig.load(source);
        
        assertEquals("custom_world", config.getDimensionFolder("minecraft:overworld"));
        assertEquals("nether_world", config.getDimensionFolder("minecraft:the_nether"));
        assertEquals("end_world", config.getDimensionFolder("minecraft:the_end"));
    }
    
    @Test
    public void testMultipleNamespaceMappings() throws IOException {
        // IRIS supports multiple dimension IDs mapping to same folder
        String dimensionProps = "dimension.overworld_folder=minecraft:overworld custom:overworld\n";
        
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.readFile("dimension.properties")).thenReturn(Optional.of(dimensionProps));
        
        DimensionConfig config = DimensionConfig.load(source);
        
        assertEquals("overworld_folder", config.getDimensionFolder("minecraft:overworld"));
        assertEquals("overworld_folder", config.getDimensionFolder("custom:overworld"));
    }
    
    @Test
    public void testWildcardMapping() throws IOException {
        String dimensionProps = "dimension.default_folder=* minecraft:overworld\n";
        
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.readFile("dimension.properties")).thenReturn(Optional.of(dimensionProps));
        
        DimensionConfig config = DimensionConfig.load(source);
        
        // Wildcard should work for any dimension
        assertEquals("default_folder", config.getDimensionFolder("minecraft:overworld"));
        assertEquals("default_folder", config.getDimensionFolder("custom:dimension"));
    }
    
    @Test
    public void testNoMappingReturnsEmpty() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        // No mapping should return empty string (use root)
        assertEquals("", config.getDimensionFolder("minecraft:overworld"));
    }
    
    @Test
    public void testGetDimensionIds() throws IOException {
        String dimensionProps = "dimension.world0=minecraft:overworld\n" +
                               "dimension.world-1=minecraft:the_nether\n";
        
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.readFile("dimension.properties")).thenReturn(Optional.of(dimensionProps));
        
        DimensionConfig config = DimensionConfig.load(source);
        
        List<String> ids = config.getDimensionIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("world0"));
        assertTrue(ids.contains("world-1"));
    }
    
    @Test
    public void testHasDimensionSpecificShaders() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        assertTrue(config.hasDimensionSpecificShaders());
    }
    
    @Test
    public void testNoDimensionSpecificShaders() {
        ShaderPackSource source = mock(ShaderPackSource.class);
        
        DimensionConfig config = DimensionConfig.load(source);
        
        assertFalse(config.hasDimensionSpecificShaders());
    }
    
    @Test
    public void testDimensionPropertiesIOException() throws IOException {
        ShaderPackSource source = mock(ShaderPackSource.class);
        when(source.readFile("dimension.properties")).thenThrow(new IOException("Test exception"));
        when(source.fileExists("world0/composite.fsh")).thenReturn(true);
        
        // Should fall back to default detection
        DimensionConfig config = DimensionConfig.load(source);
        
        assertEquals("world0", config.getDimensionFolder("minecraft:overworld"));
    }
}
