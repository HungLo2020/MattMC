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

    public static void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        VulkanicAPI.setBlendFunction(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void setBlendEquation(CommandContext ctx, int mode) {
        VulkanicAPI.setBlendEquation(ctx, mode);
    }

    public static void blendFunc(CommandContext ctx, int sfactor, int dfactor) {
        VulkanicAPI.blendFunc(ctx, sfactor, dfactor);
    }

    public static void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        VulkanicAPI.blendFuncSeparatei(ctx, buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public static void setBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha) {
        VulkanicAPI.setBlendEquationSeparate(ctx, modeRGB, modeAlpha);
    }

    public static void setStencilFunc(CommandContext ctx, int func, int ref, int mask) {
        VulkanicAPI.setStencilFunc(ctx, func, ref, mask);
    }

    public static void setStencilOp(CommandContext ctx, int stencilFailOp, int depthFailOp, int depthPassOp) {
        VulkanicAPI.setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp);
    }

    public static void setStencilWriteMask(CommandContext ctx, int mask) {
        VulkanicAPI.setStencilWriteMask(ctx, mask);
    }

    public static void setStencilFuncSeparate(CommandContext ctx, int face, int func, int ref, int mask) {
        VulkanicAPI.setStencilFuncSeparate(ctx, face, func, ref, mask);
    }

    public static void setStencilOpSeparate(CommandContext ctx, int face, int stencilFailOp, int depthFailOp, int depthPassOp) {
        VulkanicAPI.setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp);
    }

    public static void setStencilWriteMaskSeparate(CommandContext ctx, int face, int mask) {
        VulkanicAPI.setStencilWriteMaskSeparate(ctx, face, mask);
    }

    public static void texParameteri(CommandContext ctx, int target, int pname, int param) {
        VulkanicAPI.texParameteri(ctx, target, pname, param);
    }

    public static int getInteger(CommandContext ctx, int pname) {
        return VulkanicAPI.getInteger(ctx, pname);
    }
}