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
import net.minecraft.client.KeyBindingRegistry;

import java.util.List;

/**
 * Helper for registering key bindings.
 * 
 * @deprecated Use {@link KeyBindingRegistry} instead. This is a compatibility layer that
 * delegates to the native Minecraft API. Part of Step 3 of the deep integration plan.
 */
@Deprecated
public final class KeyBindingHelper {
    private KeyBindingHelper() { }
    
    /**
     * Registers a key binding and returns it.
     * The binding will be added to Minecraft's key mapping list on game initialization.
     * 
     * @deprecated Use {@link KeyBindingRegistry#registerKeyBinding(KeyMapping)} instead.
     */
    @Deprecated
    public static KeyMapping registerKeyBinding(KeyMapping keyMapping) {
        return KeyBindingRegistry.registerKeyBinding(keyMapping);
    }
    
    /**
     * Gets all registered key bindings.
     * 
     * @deprecated Use {@link KeyBindingRegistry#getRegisteredBindings()} instead.
     */
    @Deprecated
    public static List<KeyMapping> getRegisteredBindings() {
        return KeyBindingRegistry.getRegisteredBindings();
    }
}
