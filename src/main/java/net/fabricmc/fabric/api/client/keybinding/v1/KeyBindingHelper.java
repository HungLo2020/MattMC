/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for registering key bindings.
 */
public final class KeyBindingHelper {
    private static final List<KeyMapping> REGISTERED_BINDINGS = new ArrayList<>();
    
    private KeyBindingHelper() { }
    
    /**
     * Registers a key binding and returns it.
     * The binding will be added to Minecraft's key mapping list on game initialization.
     */
    public static KeyMapping registerKeyBinding(KeyMapping keyMapping) {
        REGISTERED_BINDINGS.add(keyMapping);
        return keyMapping;
    }
    
    /**
     * Gets all registered key bindings.
     */
    public static List<KeyMapping> getRegisteredBindings() {
        return REGISTERED_BINDINGS;
    }
}
