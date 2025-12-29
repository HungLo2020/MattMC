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

package net.fabricmc.fabric.impl.registry.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Unmodifiable;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.RegistryDataLoader;
import net.minecraft.core.SerializableRegistries;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;

public final class DynamicRegistriesImpl {
	private static final List<RegistryDataLoader.Entry<?>> DYNAMIC_REGISTRIES = new ArrayList<>(RegistryDataLoader.DYNAMIC_REGISTRIES);
	public static final Set<ResourceKey<?>> FABRIC_DYNAMIC_REGISTRY_KEYS = new HashSet<>();
	public static final Set<ResourceKey<? extends Registry<?>>> DYNAMIC_REGISTRY_KEYS = new HashSet<>();
	public static final Set<ResourceKey<? extends Registry<?>>> SKIP_EMPTY_SYNC_REGISTRIES = new HashSet<>();

	static {
		for (RegistryDataLoader.Entry<?> vanillaEntry : RegistryDataLoader.DYNAMIC_REGISTRIES) {
			DYNAMIC_REGISTRY_KEYS.add(vanillaEntry.key());
		}
	}

	private DynamicRegistriesImpl() {
	}

	public static @Unmodifiable List<RegistryDataLoader.Entry<?>> getDynamicRegistries() {
		return List.copyOf(DYNAMIC_REGISTRIES);
	}

	public static <T> RegistryDataLoader.Entry<T> register(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(serverCodec, "Server codec cannot be null");

		if (!DYNAMIC_REGISTRY_KEYS.add(key)) {
			throw new IllegalArgumentException("Dynamic registry " + key + " has already been registered!");
		}

		var entry = new RegistryDataLoader.Entry<>(key, serverCodec, false);
		DYNAMIC_REGISTRIES.add(entry);
		FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
		return entry;
	}

	public static <T> void addSyncedRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> clientCodec, DynamicRegistries.SyncOption... options) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(clientCodec, "Client codec cannot be null");
		Objects.requireNonNull(options, "Options cannot be null");

		if (!(RegistryDataLoader.SYNCED_REGISTRIES instanceof ArrayList<RegistryDataLoader.Entry<?>>)) {
			RegistryDataLoader.SYNCED_REGISTRIES = new ArrayList<>(RegistryDataLoader.SYNCED_REGISTRIES);
		}

		RegistryDataLoader.SYNCED_REGISTRIES.add(new RegistryDataLoader.Entry<>(key, clientCodec, false));

		if (!(SerializableRegistries.SYNCED_REGISTRIES instanceof HashSet<ResourceKey<? extends Registry<?>>>)) {
			SerializableRegistries.SYNCED_REGISTRIES = new HashSet<>(SerializableRegistries.SYNCED_REGISTRIES);
		}

		SerializableRegistries.SYNCED_REGISTRIES.add(key);

		for (DynamicRegistries.SyncOption option : options) {
			if (option == DynamicRegistries.SyncOption.SKIP_WHEN_EMPTY) {
				SKIP_EMPTY_SYNC_REGISTRIES.add(key);
			}
		}
	}
}
