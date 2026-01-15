package net.caffeinemc.mods.sodium.client.util;

import net.blaze3d.platform.NativeImage;

import java.util.Locale;

public class NativeImageHelper {
    public static long getPointerRGBA(NativeImage nativeImage) {
        if (nativeImage.format() != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Tried to get pointer to RGBA pixel data on NativeImage of wrong format; have %s", nativeImage.format()));
        }

        return nativeImage.pixels; // Direct field access - pixels is public
    }
}
