package net.vulkanic;

/**
 * Legacy GL-compat frontend surface.
 *
 * <p>This shim preserves integer-based call patterns for transitional code while
 * new code moves to {@link VulkanicCoreAPI} typed methods.</p>
 */
public final class VulkanicLegacyGLCompat {

    private VulkanicLegacyGLCompat() {
    }

    public static void bindTexture(CommandContext ctx, int target, int textureId) {
        VulkanicAPI.bindTexture(ctx, target, textureId);
    }

    public static void bindBuffer(CommandContext ctx, int target, int buffer) {
        VulkanicAPI.bindBuffer(ctx, target, buffer);
    }

    public static void setCapabilityEnabled(CommandContext ctx, int capability, boolean enabled) {
        VulkanicAPI.setCapabilityEnabled(ctx, capability, enabled);
    }

    public static void setCullFaceMode(CommandContext ctx, int mode) {
        VulkanicAPI.setCullFaceMode(ctx, mode);
    }

    public static void setDepthFunc(CommandContext ctx, int func) {
        VulkanicAPI.setDepthFunc(ctx, func);
    }

    public static void texParameteri(CommandContext ctx, int target, int pname, int param) {
        VulkanicAPI.texParameteri(ctx, target, pname, param);
    }

    public static int getInteger(CommandContext ctx, int pname) {
        return VulkanicAPI.getInteger(ctx, pname);
    }
}