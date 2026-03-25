package net.sodium.client.compatibility.environment;

import net.vulkanic.VulkanicAPI;

import java.util.Objects;

public record GlContextInfo(String vendor, String renderer, String version) {
    public static GlContextInfo create() {
        var ctx = VulkanicAPI.getCommandContext();
        String vendor = Objects.requireNonNull(VulkanicAPI.getString(ctx, VulkanicAPI.GL_VENDOR),
                "GL_VENDOR is NULL");
        String renderer = Objects.requireNonNull(VulkanicAPI.getString(ctx, VulkanicAPI.GL_RENDERER),
                "GL_RENDERER is NULL");
        String version = Objects.requireNonNull(VulkanicAPI.getString(ctx, VulkanicAPI.GL_VERSION),
                "GL_VERSION is NULL");

        return new GlContextInfo(vendor, renderer, version);
    }
}
