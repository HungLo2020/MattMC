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

package net.fabricmc.fabric.api.event.registry;

import java.util.EnumSet;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.MutableRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.ResourceKey;
import net.minecraft.core.SimpleDefaultedRegistry;
import net.minecraft.core.SimpleRegistry;
import net.minecraft.core.RegistryEntryInfo;
import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.mixin.registry.sync.RegistriesAccessor;

/**
 * Used to create custom registries, with specified registry attributes.
 *
 * <p>See the following example for creating a {@link Registry} of String objects.
 *
 * <pre>
 * {@code
 *  ResourceKey<Registry<String>> registryKey = ResourceKey.ofRegistry(ResourceLocation.of("modid", "registry_name"));
 *  Registry<String> registry = FabricRegistryBuilder.createSimple(registryKey)
 * 													.attribute(RegistryAttribute.SYNCED)
 * 													.buildAndRegister();
 * 	}
 * </pre>
 *
 * <p>Tags for the entries of a custom registry must be placed in
 * {@code /tags/<registry namespace>/<registry path>/}. For example, the tags for the example
 * registry above would be placed in {@code /tags/modid/registry_name/}.
 *
 * @param <T> The type stored in the Registry
 * @param <R> The registry type
 */
public final class FabricRegistryBuilder<T, R extends MutableRegistry<T>> {
	/**
	 * Create a new {@link FabricRegistryBuilder}, the registry has the {@link RegistryAttribute#MODDED} attribute by default.
	 *
	 * @param registry The base registry type such as {@link net.minecraft.core.SimpleRegistry} or {@link net.minecraft.core.DefaultedRegistry}
	 * @param <T> The type stored in the Registry
	 * @param <R> The registry type
	 * @return An instance of FabricRegistryBuilder
	 */
	public static <T, R extends MutableRegistry<T>> FabricRegistryBuilder<T, R> from(R registry) {
		return new FabricRegistryBuilder<>(registry);
	}

	/**
	 * Create a new {@link FabricRegistryBuilder} using a {@link SimpleRegistry}, the registry has the {@link RegistryAttribute#MODDED} attribute by default.
	 *
	 * @param registryKey The registry {@link ResourceKey}
	 * @param <T> The type stored in the Registry
	 * @return An instance of FabricRegistryBuilder
	 */
	public static <T> FabricRegistryBuilder<T, SimpleRegistry<T>> createSimple(ResourceKey<Registry<T>> registryKey) {
		return from(new SimpleRegistry<>(registryKey, Lifecycle.stable(), false));
	}

	/**
	 * Create a new {@link FabricRegistryBuilder} using a {@link DefaultedRegistry}, the registry has the {@link RegistryAttribute#MODDED} attribute by default.
	 *
	 * @param registryKey The registry {@link ResourceKey}
	 * @param defaultId The default registry id
	 * @param <T> The type stored in the Registry
	 * @return An instance of FabricRegistryBuilder
	 */
	public static <T> FabricRegistryBuilder<T, SimpleDefaultedRegistry<T>> createDefaulted(ResourceKey<Registry<T>> registryKey, ResourceLocation defaultId) {
		return from(new SimpleDefaultedRegistry<T>(defaultId.toString(), registryKey, Lifecycle.stable(), false));
	}

	/**
	 * Create a new {@link FabricRegistryBuilder} using a {@link SimpleRegistry}, the registry has the {@link RegistryAttribute#MODDED} attribute by default.
	 *
	 * @param registryId The registry {@link ResourceLocation} used as the registry id
	 * @param <T> The type stored in the Registry
	 * @return An instance of FabricRegistryBuilder
	 * @deprecated Please migrate to {@link FabricRegistryBuilder#createSimple(ResourceKey)}
	 */
	@Deprecated
	public static <T> FabricRegistryBuilder<T, SimpleRegistry<T>> createSimple(Class<T> type, ResourceLocation registryId) {
		return createSimple(ResourceKey.ofRegistry(registryId));
	}

	/**
	 * Create a new {@link FabricRegistryBuilder} using a {@link DefaultedRegistry}, the registry has the {@link RegistryAttribute#MODDED} attribute by default.
	 *
	 * @param registryId The registry {@link ResourceLocation} used as the registry id
	 * @param defaultId The default registry id
	 * @param <T> The type stored in the Registry
	 * @return An instance of FabricRegistryBuilder
	 * @deprecated Please migrate to {@link FabricRegistryBuilder#createDefaulted(ResourceKey, ResourceLocation)}
	 */
	@Deprecated
	public static <T> FabricRegistryBuilder<T, SimpleDefaultedRegistry<T>> createDefaulted(Class<T> type, ResourceLocation registryId, ResourceLocation defaultId) {
		return createDefaulted(ResourceKey.ofRegistry(registryId), defaultId);
	}

	private final R registry;
	private final EnumSet<RegistryAttribute> attributes = EnumSet.noneOf(RegistryAttribute.class);

	private FabricRegistryBuilder(R registry) {
		this.registry = registry;
		attribute(RegistryAttribute.MODDED);
	}

	/**
	 * Add a {@link RegistryAttribute} to the registry.
	 *
	 * @param attribute the {@link RegistryAttribute} to add to the registry
	 * @return the instance of {@link FabricRegistryBuilder}
	 */
	public FabricRegistryBuilder<T, R> attribute(RegistryAttribute attribute) {
		attributes.add(attribute);
		return this;
	}

	/**
	 * Applies the attributes to the registry and registers it.
	 * @return the registry instance with the attributes applied
	 */
	public R buildAndRegister() {
		final ResourceKey<?> key = registry.getKey();

		for (RegistryAttribute attribute : attributes) {
			RegistryAttributeHolder.get(key).addAttribute(attribute);
		}

		//noinspection unchecked
		RegistriesAccessor.getROOT().add((ResourceKey<MutableRegistry<?>>) key, registry, RegistryEntryInfo.DEFAULT);

		return registry;
	}
}
