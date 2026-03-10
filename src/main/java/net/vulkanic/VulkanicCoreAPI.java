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