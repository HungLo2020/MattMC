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

import com.llamalad7.mixinextras.sugar.Local;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.entry.RegistryEntry;
import net.minecraft.world.inventory.AnvilScreenHandler;
import net.minecraft.world.inventory.ForgingScreenHandler;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ScreenHandlerType;
import net.minecraft.world.inventory.slot.ForgingSlotsManager;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;

@Mixin(AnvilScreenHandler.class)
abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
	AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, Inventory playerInventory, ContainerLevelAccess context, ForgingSlotsManager forgingSlotsManager) {
		super(type, syncId, playerInventory, context, forgingSlotsManager);
	}

	@Redirect(
			method = "updateResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/Enchantment;isAcceptableItem(Lnet/minecraft/item/ItemStack;)Z"
			)
	)
	private boolean callAllowEnchantingEvent(Enchantment instance, ItemStack stack, @Local RegistryEntry<Enchantment> registryEntry) {
		return stack.canBeEnchantedWith(registryEntry, EnchantingContext.ACCEPTABLE);
	}
}
