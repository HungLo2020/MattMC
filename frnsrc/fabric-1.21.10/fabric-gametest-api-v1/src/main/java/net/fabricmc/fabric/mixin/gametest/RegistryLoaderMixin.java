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

package net.fabricmc.fabric.mixin.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;

import net.fabricmc.fabric.impl.gametest.FabricGameTestModInitializer;

@Mixin(RegistryLoader.class)
public class RegistryLoaderMixin {
	@Unique
	private static final AtomicBoolean LOADING_DYNAMIC_REGISTRIES = new AtomicBoolean(false);

	@Inject(method = "loadFromResource(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/registry/DynamicRegistryManager$Immutable;", at = @At("HEAD"))
	private static void loadFromResources(ResourceManager resourceManager, List<RegistryWrapper.Impl<?>> registries, List<RegistryLoader.Entry<?>> entries, CallbackInfoReturnable<DynamicRegistryManager.Immutable> cir) {
		LOADING_DYNAMIC_REGISTRIES.set(entries.stream().anyMatch(entry -> entry.key() == RegistryKeys.TEST_INSTANCE));
	}

	@Inject(
			method = "load(Lnet/minecraft/registry/RegistryLoader$RegistryLoadable;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/registry/DynamicRegistryManager$Immutable;",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
					ordinal = 1
			)
	)
	private static void beforeFreeze(@Coerce Object loadable, List<RegistryWrapper.Impl<?>> wrappers, List<RegistryLoader.Entry<?>> entries, CallbackInfoReturnable<DynamicRegistryManager.Immutable> cir, @Local(ordinal = 2) List<RegistryLoader.Loader<?>> registriesList) {
		if (LOADING_DYNAMIC_REGISTRIES.getAndSet(false)) {
			FabricGameTestModInitializer.registerDynamicEntries(registriesList);
		}
	}
}
