package net.minecraft.client.renderer.shaders.shadows;

import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PackShadowDirectivesTest {

    @Test
    public void testDefaultValues() {
        PackShadowDirectives directives = new PackShadowDirectives();

        assertEquals(1024, directives.getResolution());
        assertEquals(160.0f, directives.getDistance());
        assertEquals(2.0f, directives.getIntervalSize());
        assertTrue(directives.getShouldRenderTerrain());
        assertTrue(directives.getShouldRenderTranslucent());
        assertTrue(directives.getShouldRenderEntities());
    }

    @Test
    public void testDepthSamplingSettings() {
        PackShadowDirectives directives = new PackShadowDirectives();

        assertEquals(2, directives.getDepthSamplingSettings().length);
        assertNotNull(directives.getDepthSamplingSettings()[0]);
        assertNotNull(directives.getDepthSamplingSettings()[1]);
    }

    @Test
    public void testDepthSamplingDefaults() {
        PackShadowDirectives.DepthSamplingSettings settings = 
            new PackShadowDirectives.DepthSamplingSettings();

        assertFalse(settings.getHardwareFiltering());
        assertFalse(settings.getMipmap());
        assertFalse(settings.getNearest());
    }

    @Test
    public void testDepthSamplingSetters() {
        PackShadowDirectives.DepthSamplingSettings settings = 
            new PackShadowDirectives.DepthSamplingSettings();

        settings.setHardwareFiltering(true);
        settings.setMipmap(true);
        settings.setNearest(true);

        assertTrue(settings.getHardwareFiltering());
        assertTrue(settings.getMipmap());
        assertTrue(settings.getNearest());
    }

    @Test
    public void testColorSamplingDefaults() {
        PackShadowDirectives.SamplingSettings settings = 
            new PackShadowDirectives.SamplingSettings();

        assertEquals(InternalTextureFormat.RGBA, settings.getFormat());
        assertFalse(settings.getClear());
        assertFalse(settings.getMipmap());
        assertFalse(settings.getNearest());
    }

    @Test
    public void testColorSamplingSetters() {
        PackShadowDirectives.SamplingSettings settings = 
            new PackShadowDirectives.SamplingSettings();

        settings.setFormat(InternalTextureFormat.RGB16F);
        settings.setClear(true);
        settings.setMipmap(true);
        settings.setNearest(true);

        assertEquals(InternalTextureFormat.RGB16F, settings.getFormat());
        assertTrue(settings.getClear());
        assertTrue(settings.getMipmap());
        assertTrue(settings.getNearest());
    }

    @Test
    public void testColorSamplingSettingsMap() {
        PackShadowDirectives directives = new PackShadowDirectives();

        assertNotNull(directives.getColorSamplingSettings());
        assertTrue(directives.getColorSamplingSettings().isEmpty());
    }

    @Test
    public void testMaxBufferConstants() {
        assertEquals(8, PackShadowDirectives.MAX_SHADOW_COLOR_BUFFERS_IRIS);
        assertEquals(2, PackShadowDirectives.MAX_SHADOW_COLOR_BUFFERS_OF);
    }
}
