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

package net.fabricmc.fabric.mixin.transfer;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.item.inventory.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Level;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;

/**
 * Allows hoppers to interact with ItemVariant storages.
 */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
	@Shadow
	private Direction facing;

	@Inject(
			at = @At(
					value = "INVOKE_ASSIGN",
					target = "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory(Lnet/minecraft/world/Level;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/HopperBlockEntity;)Lnet/minecraft/inventory/Container;"
			),
			method = "insert",
			cancellable = true
	)
	private static void hookInsert(Level world, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir, @Local Container targetInventory) {
		// Let vanilla handle the transfer if it found an inventory.
		if (targetInventory != null) return;

		// Otherwise inject our transfer logic.
		Direction direction = ((HopperBlockEntityMixin) (Object) blockEntity).facing;
		BlockPos targetPos = pos.offset(direction);
		Storage<ItemVariant> target = ItemStorage.SIDED.find(world, targetPos, direction.getOpposite());

		if (target != null) {
			long moved = StorageUtil.move(
					InventoryStorage.of(blockEntity, direction),
					target,
					iv -> true,
					1,
					null
			);
			cir.setReturnValue(moved == 1);
		}
	}

	@Inject(
			at = @At(
					value = "INVOKE_ASSIGN",
					target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/world/Level;Lnet/minecraft/block/entity/Hopper;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Lnet/minecraft/inventory/Container;"
			),
			method = "extract(Lnet/minecraft/world/Level;Lnet/minecraft/block/entity/Hopper;)Z",
			cancellable = true
	)
	private static void hookExtract(Level world, Hopper hopper, CallbackInfoReturnable<Boolean> cir, @Local Container inputInventory) {
		// Let vanilla handle the transfer if it found an inventory.
		if (inputInventory != null) return;

		// Otherwise inject our transfer logic.
		BlockPos sourcePos = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0D, hopper.getHopperZ());
		Storage<ItemVariant> source = ItemStorage.SIDED.find(world, sourcePos, Direction.DOWN);

		if (source != null) {
			long moved = StorageUtil.move(
					source,
					InventoryStorage.of(hopper, Direction.UP),
					iv -> true,
					1,
					null
			);
			cir.setReturnValue(moved == 1);
		}
	}
}
