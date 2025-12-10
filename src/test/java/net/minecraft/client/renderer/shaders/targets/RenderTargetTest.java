package net.minecraft.client.renderer.shaders.targets;

import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import net.minecraft.client.renderer.shaders.texture.PixelFormat;
import net.minecraft.client.renderer.shaders.texture.PixelType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Structure tests for RenderTarget class (no OpenGL calls).
 */
public class RenderTargetTest {

    @Test
    public void testBuilderExists() {
        RenderTarget.Builder builder = RenderTarget.builder();
        assertNotNull(builder);
    }

    @Test
    public void testBuilderSetName() {
        RenderTarget.Builder builder = RenderTarget.builder().setName("test");
        assertNotNull(builder);
    }

    @Test
    public void testBuilderSetDimensions() {
        RenderTarget.Builder builder = RenderTarget.builder().setDimensions(1024, 768);
        assertNotNull(builder);
    }

    @Test
    public void testBuilderSetInternalFormat() {
        RenderTarget.Builder builder = RenderTarget.builder()
            .setInternalFormat(InternalTextureFormat.RGBA32F);
        assertNotNull(builder);
    }

    @Test
    public void testBuilderSetPixelFormat() {
        RenderTarget.Builder builder = RenderTarget.builder()
            .setPixelFormat(PixelFormat.RGBA);
        assertNotNull(builder);
    }

    @Test
    public void testBuilderSetPixelType() {
        RenderTarget.Builder builder = RenderTarget.builder()
            .setPixelType(PixelType.FLOAT);
        assertNotNull(builder);
    }

    @Test
    public void testBuilderChaining() {
        RenderTarget.Builder builder = RenderTarget.builder()
            .setName("test")
            .setDimensions(1920, 1080)
            .setInternalFormat(InternalTextureFormat.RGBA16F)
            .setPixelFormat(PixelFormat.RGBA)
            .setPixelType(PixelType.FLOAT);
        assertNotNull(builder);
    }
}
