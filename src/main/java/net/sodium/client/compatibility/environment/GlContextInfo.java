package net.sodium.client.compatibility.environment;

import net.vulkanic.VulkanicAPI;

import java.util.Objects;

public record GlContextInfo(String vendor, String renderer, String version) {
    public static GlContextInfo create() {
        String vendor = Objects.requireNonNull(VulkanicAPI.queryStringInfo(0x1F00),
                "GL_VENDOR is NULL");
        String renderer = Objects.requireNonNull(VulkanicAPI.queryStringInfo(0x1F01),
                "GL_RENDERER is NULL");
        String version = Objects.requireNonNull(VulkanicAPI.queryStringInfo(0x1F02),
                "GL_VERSION is NULL");

        return new GlContextInfo(vendor, renderer, version);
    }
}
