package net.sodium.client.compatibility.environment;

import net.vulkanic.VulkanicAPI;

import java.util.Objects;

public record GlContextInfo(String vendor, String renderer, String version) {
    public static GlContextInfo create() {
        String vendor = Objects.requireNonNull(VulkanicAPI.queryStringInfo(VulkanicAPI.GL_VENDOR),
                "GL_VENDOR is NULL");
        String renderer = Objects.requireNonNull(VulkanicAPI.queryStringInfo(VulkanicAPI.GL_RENDERER),
                "GL_RENDERER is NULL");
        String version = Objects.requireNonNull(VulkanicAPI.queryStringInfo(VulkanicAPI.GL_VERSION),
                "GL_VERSION is NULL");

        return new GlContextInfo(vendor, renderer, version);
    }
}
