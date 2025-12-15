/*
 * Copyright (C) 2024 MattMC
 *
 * This file is part of MattMC.
 *
 * MattMC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.minecraft.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native Minecraft registry for key bindings.
 * This replaces the Fabric API KeyBindingHelper with a first-class Minecraft API.
 * 
 * <p>Part of Step 3 of the deep integration plan - consolidating Fabric API stubs
 * into native Minecraft systems.</p>
 */
public final class KeyBindingRegistry {
    private static final List<KeyMapping> REGISTERED_BINDINGS = new ArrayList<>();
    
    private KeyBindingRegistry() {
        throw new UnsupportedOperationException("KeyBindingRegistry is a utility class");
    }
    
    /**
     * Registers a key binding and returns it.
     * The binding will be added to Minecraft's key mapping list on game initialization.
     * 
     * @param keyMapping The key mapping to register
     * @return The registered key mapping (same instance)
     */
    public static KeyMapping registerKeyBinding(KeyMapping keyMapping) {
        if (keyMapping == null) {
            throw new IllegalArgumentException("Key mapping cannot be null");
        }
        REGISTERED_BINDINGS.add(keyMapping);
        return keyMapping;
    }
    
    /**
     * Gets all registered key bindings.
     * 
     * @return Unmodifiable list of all registered key bindings
     */
    public static List<KeyMapping> getRegisteredBindings() {
        return Collections.unmodifiableList(REGISTERED_BINDINGS);
    }
    
    /**
     * Clears all registered key bindings.
     * This should only be called during testing or cleanup.
     */
    public static void clearBindings() {
        REGISTERED_BINDINGS.clear();
    }
}
