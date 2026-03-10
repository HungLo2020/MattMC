package net.vulkanic;

import java.util.Objects;

/**
 * Backend-neutral frontend API surface.
 *
 * <p>This is the preferred entrypoint for new callsites that should avoid raw GL
 * integer knobs at the Vulkanic frontend boundary.</p>
 */
public final class VulkanicCoreAPI {

    private VulkanicCoreAPI() {
    }

    public static void bindTexture(CommandContext ctx, VulkanicTextureTarget target, int textureId) {
        VulkanicAPI.bindTexture(ctx, target, textureId);
    }

    public static void bindBuffer(CommandContext ctx, VulkanicBufferTarget target, int buffer) {
        VulkanicAPI.bindBuffer(ctx, target, buffer);
    }

    public static void setCapabilityEnabled(CommandContext ctx, VulkanicCapability capability, boolean enabled) {
        VulkanicAPI.setCapabilityEnabled(ctx, capability, enabled);
    }

    public static void setCullFaceMode(CommandContext ctx, VulkanicCullFaceMode mode) {
        VulkanicAPI.setCullFaceMode(ctx, mode);
    }

    public static void setDepthFunc(CommandContext ctx, VulkanicDepthCompareOp op) {
        VulkanicAPI.setDepthFunc(ctx, op);
    }

    public static void setBlendFunction(
        CommandContext ctx,
        VulkanicBlendFactor srcRgb,
        VulkanicBlendFactor dstRgb,
        VulkanicBlendFactor srcAlpha,
        VulkanicBlendFactor dstAlpha
    ) {
        VulkanicAPI.setBlendFunction(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void setBlendEquation(CommandContext ctx, VulkanicBlendEquation equation) {
        VulkanicAPI.setBlendEquation(ctx, equation);
    }

    public static void blendFunc(CommandContext ctx, VulkanicBlendFactor sfactor, VulkanicBlendFactor dfactor) {
        VulkanicAPI.blendFunc(ctx, sfactor, dfactor);
    }

    public static void setStencilFunc(CommandContext ctx, VulkanicStencilCompareOp func, int ref, int mask) {
        VulkanicAPI.setStencilFunc(ctx, func, ref, mask);
    }

    public static void setStencilOp(
        CommandContext ctx,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        VulkanicAPI.setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp);
    }

    public static void setStencilWriteMask(CommandContext ctx, int mask) {
        VulkanicAPI.setStencilWriteMask(ctx, mask);
    }

    public static void setStencilFuncSeparate(CommandContext ctx, VulkanicStencilFace face, VulkanicStencilCompareOp func, int ref, int mask) {
        VulkanicAPI.setStencilFuncSeparate(ctx, face, func, ref, mask);
    }

    public static void setStencilOpSeparate(
        CommandContext ctx,
        VulkanicStencilFace face,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        VulkanicAPI.setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp);
    }

    public static void setStencilWriteMaskSeparate(CommandContext ctx, VulkanicStencilFace face, int mask) {
        VulkanicAPI.setStencilWriteMaskSeparate(ctx, face, mask);
    }

    public static void texParameteri(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName parameter,
        int value
    ) {
        Objects.requireNonNull(parameter, "parameter must not be null");
        VulkanicAPI.texParameteri(ctx, target, parameter, value);
    }

    public static int getInteger(CommandContext ctx, VulkanicIntegerQuery query) {
        return VulkanicAPI.getInteger(ctx, query);
    }
}