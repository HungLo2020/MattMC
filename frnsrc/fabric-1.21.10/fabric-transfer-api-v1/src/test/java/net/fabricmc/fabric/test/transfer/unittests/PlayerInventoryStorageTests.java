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

package net.fabricmc.fabric.test.transfer.unittests;

import static net.fabricmc.fabric.test.transfer.TestUtil.assertEquals;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public class PlayerInventoryStorageTests extends AbstractTransferApiTest {
	@BeforeAll
	static void beforeAll() {
		bootstrap();
	}

	@Test
	public void testStackingOffer() {
		// Ensure that offer stacks as expected.
		testStacking(playerInv -> playerInv::offer);
	}

	@Test
	public void testStackingInsert() {
		// Also test that the behavior of insert matches that of offer.
		testStacking(playerInv -> playerInv::insert);
	}

	private void testStacking(Function<PlayerInventoryStorage, InsertionFunction> inserterBuilder) {
		// A bit hacky... but nothing should try using the null player entity as long as we don't call drop.
		PlayerInventory inv = new PlayerInventory(null, new EntityEquipment());
		InsertionFunction inserter = inserterBuilder.apply(PlayerInventoryStorage.of(inv));

		// Fill everything with stone besides the first two inventory slots.
		inv.setSelectedSlot(3);
		inv.setSelectedStack(new ItemStack(Items.STONE, 63));
		inv.setStack(PlayerInventory.OFF_HAND_SLOT, new ItemStack(Items.STONE, 62));

		for (int i = 4; i < PlayerInventory.MAIN_SIZE; ++i) {
			inv.setStack(i, new ItemStack(Items.STONE, 61));
		}

		ItemVariant stone = ItemVariant.of(Items.STONE);

		try (Transaction tx = Transaction.openOuter()) {
			assertEquals(1L, inserter.insert(stone, 1, tx));

			// Should have gone into the main stack
			assertEquals(64, inv.getStack(3).getCount());
		}

		try (Transaction tx = Transaction.openOuter()) {
			assertEquals(2L, inserter.insert(stone, 2, tx));

			// Should have gone into the main and offhand stacks.
			assertEquals(64, inv.getStack(3).getCount());
			assertEquals(63, inv.getStack(PlayerInventory.OFF_HAND_SLOT).getCount());
		}

		long toInsertStacking = 1 + 2 + (PlayerInventory.MAIN_SIZE - 4) * 3;

		// Should be just enough to fill existing stacks, but not touch slots 0, 1 and 2.
		try (Transaction tx = Transaction.openOuter()) {
			assertEquals(toInsertStacking, inserter.insert(stone, toInsertStacking, tx));

			assertEquals(64, inv.getStack(3).getCount());
			assertEquals(64, inv.getStack(PlayerInventory.OFF_HAND_SLOT).getCount());

			for (int i = 4; i < PlayerInventory.MAIN_SIZE; ++i) {
				assertEquals(64, inv.getStack(i).getCount());
			}

			for (int i = 0; i < 3; ++i) {
				assertEquals(true, inv.getStack(i).isEmpty());
			}

			// Now insertion should fill the remaining stacks
			assertEquals(150L, inserter.insert(stone, 150, tx));
			assertEquals(64, inv.getStack(0).getCount());
			assertEquals(64, inv.getStack(1).getCount());
			assertEquals(22, inv.getStack(2).getCount());

			// Only 64 - 22 = 42 room left!
			assertEquals(42L, inserter.insert(stone, Long.MAX_VALUE, tx));
		}
	}

	private interface InsertionFunction {
		long insert(ItemVariant variant, long maxAmount, TransactionContext transaction);
	}
}
