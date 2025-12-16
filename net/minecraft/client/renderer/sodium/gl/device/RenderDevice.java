package net.minecraft.client.renderer.sodium.gl.device;

import net.minecraft.client.renderer.gl.advanced.functions.DeviceFunctions;
import org.lwjgl.opengl.GLCapabilities;

public interface RenderDevice {
    RenderDevice INSTANCE = new GLRenderDevice();

    CommandList createCommandList();

    static void enterManagedCode() {
        RenderDevice.INSTANCE.makeActive();
    }

    static void exitManagedCode() {
        RenderDevice.INSTANCE.makeInactive();
    }

    void makeActive();
    void makeInactive();

    GLCapabilities getCapabilities();

    DeviceFunctions getDeviceFunctions();

    int getSubTexelPrecisionBits();
    int getMaxTextureLodBias();
}
