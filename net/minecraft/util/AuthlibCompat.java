package net.minecraft.util;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Temporary compatibility helpers to bridge Authlib API changes between versions.
 * 
 * Some source files expect record-style accessors {@code GameProfile.id()} and {@code GameProfile.name()} (Authlib 6.x),
 * while others or older dependencies expose classic getters {@code getId()} and {@code getName()}.
 * 
 * These helpers will use reflection to call the record-style accessors when present, and fall back to getters otherwise.
 */
public final class AuthlibCompat {
    private AuthlibCompat() {}

    public static UUID id(GameProfile profile) {
        if (profile == null) return null;
        try {
            Method m = profile.getClass().getMethod("id");
            Object v = m.invoke(profile);
            if (v instanceof UUID) {
                return (UUID) v;
            }
        } catch (Throwable ignored) {
            // fall through to getters
        }
        return profile.getId();
    }

    public static String name(GameProfile profile) {
        if (profile == null) return null;
        try {
            Method m = profile.getClass().getMethod("name");
            Object v = m.invoke(profile);
            if (v instanceof String) {
                return (String) v;
            }
        } catch (Throwable ignored) {
            // fall through to getters
        }
        return profile.getName();
    }
}
