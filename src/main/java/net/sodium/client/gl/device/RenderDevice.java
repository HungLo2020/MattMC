package net.sodium.client.gl.device;

import net.sodium.client.gl.functions.DeviceFunctions;
import net.vulkanic.GraphicsCapabilities;

public interface RenderDevice {
    static RenderDevice instance() {
        return RenderDeviceHolder.instance();
    }

    CommandList createCommandList();

    static void enterManagedCode() {
        RenderDevice.instance().makeActive();
    }

    static void exitManagedCode() {
        RenderDevice.instance().makeInactive();
    }

    void makeActive();
    void makeInactive();

    GraphicsCapabilities getCapabilities();

    DeviceFunctions getDeviceFunctions();

    int getSubTexelPrecisionBits();
    int getMaxTextureLodBias();
}
