package net.sodium.client.gl.device;

import net.sodium.client.gl.functions.DeviceFunctions;
import net.vulkanic.GraphicsCapabilities;

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

    GraphicsCapabilities getCapabilities();

    DeviceFunctions getDeviceFunctions();

    int getSubTexelPrecisionBits();
    int getMaxTextureLodBias();
}
