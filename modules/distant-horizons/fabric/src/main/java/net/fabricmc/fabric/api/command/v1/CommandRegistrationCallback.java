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

package net.fabricmc.fabric.api.command.v1;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Backwards compatibility stub for Command API v1.
 * Redirects to v2 implementation for 1.21.10 compatibility.
 * 
 * @deprecated Use {@link net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback} instead
 */
@Deprecated
public interface CommandRegistrationCallback {
	Event<CommandRegistrationCallback> EVENT = EventFactory.createArrayBacked(CommandRegistrationCallback.class, callbacks -> (dispatcher, registryAccess, environment) -> {
		for (CommandRegistrationCallback callback : callbacks) {
			callback.register(dispatcher, registryAccess, environment);
		}
	});

	/**
	 * Called when commands are being registered.
	 *
	 * @param dispatcher the command dispatcher
	 * @param registryAccess the registry access
	 * @param environment the command selection environment
	 */
	void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
}
