package com.mojang.logging;

import org.slf4j.event.Level;
import java.util.function.BiConsumer;

/**
 * Stub class for Mojang logging integration.
 * This class is used by decompiled Minecraft source code.
 */
public class LogListeners {
    public static void registerConsumer(java.util.function.Consumer<String> consumer) {
        // Stub implementation
    }
    
    public static void unregisterConsumer(java.util.function.Consumer<String> consumer) {
        // Stub implementation
    }

    public static void addListener(String name, BiConsumer<String, Level> listener) {
        // Stub implementation for Tracy integration
    }
}
