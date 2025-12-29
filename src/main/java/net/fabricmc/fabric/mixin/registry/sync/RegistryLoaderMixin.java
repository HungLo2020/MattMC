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

package net.fabricmc.fabric.mixin.registry.sync;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.RegistryDataLoader;
import net.minecraft.core.HolderLookup;

import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.impl.registry.sync.DynamicRegistryViewImpl;

@Mixin(RegistryDataLoader.class)
public class RegistryLoaderMixin {
	@Unique
	private static final ThreadLocal<Boolean> IS_SERVER = ThreadLocal.withInitial(() -> false);

	/**
	 * Sets IS_SERVER flag. Note that this must be reset after call, as the render thread
	 * invokes this method as well.
	 */
	@WrapOperation(method = "loadFromResource(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/registry/RegistryAccess$Immutable;", at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/RegistryDataLoader;load(Lnet/minecraft/registry/RegistryDataLoader$RegistryLoadable;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/registry/RegistryAccess$Immutable;"))
	private static RegistryAccess.Immutable wrapIsServerCall(@Coerce Object registryLoadable, List<HolderLookup.Impl<?>> baseRegistries, List<RegistryDataLoader.Entry<?>> entries, Operation<RegistryAccess.Immutable> original) {
		try {
			IS_SERVER.set(true);
			return original.call(registryLoadable, baseRegistries, entries);
		} finally {
			IS_SERVER.set(false);
		}
	}

	@Inject(
			method = "load(Lnet/minecraft/registry/RegistryDataLoader$RegistryLoadable;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/registry/RegistryAccess$Immutable;",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
					ordinal = 0
			)
	)
	private static void beforeLoad(@Coerce Object registryLoadable, List<HolderLookup.Impl<?>> baseRegistries, List<RegistryDataLoader.Entry<?>> entries, CallbackInfoReturnable<RegistryAccess.Immutable> cir, @Local(ordinal = 2) List<RegistryDataLoader.Loader<?>> registriesList) {
		if (!IS_SERVER.get()) return;

		Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries = new IdentityHashMap<>(registriesList.size());

		for (RegistryDataLoader.Loader<?> entry : registriesList) {
			registries.put(entry.registry().getKey(), entry.registry());
		}

		DynamicRegistrySetupCallback.EVENT.invoker().onRegistrySetup(new DynamicRegistryViewImpl(registries));
	}
}
