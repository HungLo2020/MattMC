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

package net.fabricmc.fabric.mixin.item;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Decoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.core.RegistryOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder;
import net.minecraft.server.packs.Resource;

import net.fabricmc.fabric.impl.item.EnchantmentUtil;

@Mixin(RegistryDataLoader.class)
abstract class RegistryLoaderMixin {
	@WrapOperation(
			method = "parseAndAdd",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/registry/WritableRegistry;add(Lnet/minecraft/registry/ResourceKey;Ljava/lang/Object;Lnet/minecraft/registry/entry/HolderInfo;)Lnet/minecraft/registry/entry/Holder$Reference;"
			)
	)
	@SuppressWarnings("unchecked")
	private static <T> Holder.Reference<T> enchantmentKey(
			WritableRegistry<T> instance,
			ResourceKey<T> objectKey,
			Object object,
			HolderInfo registryEntryInfo,
			Operation<Holder.Reference<T>> original,
			WritableRegistry<T> registry,
			Decoder<T> decoder,
			RegistryOps<JsonElement> ops,
			ResourceKey<T> registryKey,
			Resource resource,
			HolderInfo entryInfo
	) {
		if (object instanceof Enchantment enchantment) {
			Enchantment modified = EnchantmentUtil.modify((ResourceKey<Enchantment>) objectKey, enchantment, EnchantmentUtil.determineSource(resource));

			if (modified != null) {
				object = modified;

				// Clear the knownPackInfo to force the server to sync the data pack to the client
				registryEntryInfo = new HolderInfo(Optional.empty(), registryEntryInfo.lifecycle());
			}
		}

		return original.call(instance, registryKey, object, registryEntryInfo);
	}
}
