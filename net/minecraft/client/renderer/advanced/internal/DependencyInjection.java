package net.minecraft.client.renderer.advanced.internal;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Constructor;

/**
 * <p><b>Advanced Rendering API</b> - Part of Minecraft's integrated advanced rendering system.
 * Originally from Sodium, now a first-class Minecraft API.</p>
 *
 * @since Step 6: Sodium Core API Migration
 * @apiNote This API is internal and part of the advanced rendering subsystem.
 * Internal use only.
 */
public class DependencyInjection {
    public static <T> T load(Class<T> apiClass, String implClassName) {
        Class<?> implClass;

        try {
            implClass = Class.forName(implClassName);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not find implementation", e);
        }

        if (!apiClass.isAssignableFrom(implClass)) {
            throw new RuntimeException("Class %s does not implement interface %s"
                    .formatted(implClass.getName(), apiClass.getName()));
        }

        Constructor<?> implConstructor;

        try {
            implConstructor = implClass.getConstructor();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not find default constructor", e);
        }

        Object implInstance;

        try {
            implInstance = implConstructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not instantiate implementation", e);
        }

        return apiClass.cast(implInstance);
    }
}
