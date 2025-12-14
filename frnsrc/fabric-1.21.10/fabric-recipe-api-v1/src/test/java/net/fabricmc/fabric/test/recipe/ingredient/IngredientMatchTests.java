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

package net.fabricmc.fabric.test.recipe.ingredient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;

public class IngredientMatchTests {
	@BeforeAll
	static void beforeAll() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	public void testAllIngredient() {
		Ingredient allIngredient = DefaultCustomIngredients.all(Ingredient.ofItems(Items.APPLE, Items.CARROT), Ingredient.ofItems(Items.STICK, Items.CARROT));

		assertEquals(1, allIngredient.getMatchingItems().toList().size());
		assertEquals(Items.CARROT, allIngredient.getMatchingItems().toList().getFirst().value());
		assertFalse(allIngredient.getMatchingItems().toList().isEmpty());

		assertFalse(allIngredient.test(new ItemStack(Items.APPLE)));
		assertTrue(allIngredient.test(new ItemStack(Items.CARROT)));
		assertFalse(allIngredient.test(new ItemStack(Items.STICK)));

		Ingredient emptyAllIngredient = DefaultCustomIngredients.all(Ingredient.ofItems(Items.APPLE), Ingredient.ofItems(Items.STICK));

		assertEquals(0, emptyAllIngredient.getMatchingItems().toList().size());
		assertTrue(emptyAllIngredient.getMatchingItems().toList().isEmpty());

		assertFalse(emptyAllIngredient.test(new ItemStack(Items.APPLE)));
		assertFalse(emptyAllIngredient.test(new ItemStack(Items.STICK)));
	}

	@Test
	public void testAnyIngredient() {
		Ingredient anyIngredient = DefaultCustomIngredients.any(Ingredient.ofItems(Items.APPLE, Items.CARROT), Ingredient.ofItems(Items.STICK, Items.CARROT));

		assertEquals(4, anyIngredient.getMatchingItems().toList().size());
		assertEquals(Items.APPLE, anyIngredient.getMatchingItems().toList().getFirst().value());
		assertEquals(Items.CARROT, anyIngredient.getMatchingItems().toList().get(1).value());
		assertEquals(Items.STICK, anyIngredient.getMatchingItems().toList().get(2).value());
		assertEquals(Items.CARROT, anyIngredient.getMatchingItems().toList().get(3).value());
		assertFalse(anyIngredient.getMatchingItems().toList().isEmpty());

		assertTrue(anyIngredient.test(new ItemStack(Items.APPLE)));
		assertTrue(anyIngredient.test(new ItemStack(Items.CARROT)));
		assertTrue(anyIngredient.test(new ItemStack(Items.STICK)));
	}

	@Test
	public void testDifferenceIngredient() {
		Ingredient differenceIngredient = DefaultCustomIngredients.difference(Ingredient.ofItems(Items.APPLE, Items.CARROT), Ingredient.ofItems(Items.STICK, Items.CARROT));

		assertEquals(1, differenceIngredient.getMatchingItems().toList().size());
		assertEquals(Items.APPLE, differenceIngredient.getMatchingItems().toList().getFirst().value());
		assertFalse(differenceIngredient.getMatchingItems().toList().isEmpty());

		assertTrue(differenceIngredient.test(new ItemStack(Items.APPLE)));
		assertFalse(differenceIngredient.test(new ItemStack(Items.CARROT)));
		assertFalse(differenceIngredient.test(new ItemStack(Items.STICK)));
	}

	@Test
	public void testComponentIngredient() {
		final Ingredient baseIngredient = Ingredient.ofItems(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.STICK);
		final Ingredient undamagedIngredient = DefaultCustomIngredients.components(
				baseIngredient,
				builder -> builder.add(DataComponentTypes.DAMAGE, 0)
		);
		final Ingredient noNameUndamagedIngredient = DefaultCustomIngredients.components(
				baseIngredient,
				builder -> builder
						.add(DataComponentTypes.DAMAGE, 0)
						.remove(DataComponentTypes.CUSTOM_NAME)
		);

		ItemStack renamedUndamagedDiamondPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		renamedUndamagedDiamondPickaxe.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Renamed"));
		assertTrue(undamagedIngredient.test(renamedUndamagedDiamondPickaxe));
		assertFalse(noNameUndamagedIngredient.test(renamedUndamagedDiamondPickaxe));

		assertEquals(3, undamagedIngredient.getMatchingItems().toList().size());
		ItemStack result0 = undamagedIngredient.getMatchingItems().toList().getFirst().value().getDefaultStack();
		ItemStack result1 = undamagedIngredient.getMatchingItems().toList().get(1).value().getDefaultStack();

		assertEquals(Items.DIAMOND_PICKAXE, result0.getItem());
		assertEquals(Items.NETHERITE_PICKAXE, result1.getItem());
		assertEquals(ComponentChanges.EMPTY, result0.getComponentChanges());
		assertEquals(ComponentChanges.EMPTY, result1.getComponentChanges());
		assertFalse(undamagedIngredient.getMatchingItems().toList().isEmpty());

		// Undamaged is fine
		assertTrue(undamagedIngredient.test(new ItemStack(Items.DIAMOND_PICKAXE)));
		assertTrue(undamagedIngredient.test(new ItemStack(Items.NETHERITE_PICKAXE)));

		// Damaged is not fine
		ItemStack damagedDiamondPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		damagedDiamondPickaxe.setDamage(10);
		assertFalse(undamagedIngredient.test(damagedDiamondPickaxe));

		// Checking for DAMAGE component requires the item is damageable in the first place
		assertFalse(undamagedIngredient.test(new ItemStack(Items.STICK)));

		// Custom data is strictly matched, like any other component with multiple fields
		final NbtCompound requiredData = new NbtCompound();
		requiredData.putInt("keyA", 1);
		final NbtCompound extraData = requiredData.copy();
		extraData.putInt("keyB", 2);

		final Ingredient customDataIngredient = DefaultCustomIngredients.components(
				baseIngredient,
				builder -> builder.add(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(requiredData))
		);
		ItemStack requiredDataStack = new ItemStack(Items.DIAMOND_PICKAXE);
		requiredDataStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(requiredData));
		ItemStack extraDataStack = new ItemStack(Items.DIAMOND_PICKAXE);
		extraDataStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(extraData));
		assertTrue(customDataIngredient.test(requiredDataStack));
		assertFalse(customDataIngredient.test(extraDataStack));

		// Default value is ignored in components(ItemStack)
		final Ingredient damagedPickaxeIngredient = DefaultCustomIngredients.components(renamedUndamagedDiamondPickaxe);
		ItemStack renamedDamagedDiamondPickaxe = renamedUndamagedDiamondPickaxe.copy();
		renamedDamagedDiamondPickaxe.setDamage(10);
		assertTrue(damagedPickaxeIngredient.test(renamedUndamagedDiamondPickaxe));
		assertTrue(damagedPickaxeIngredient.test(renamedDamagedDiamondPickaxe));
	}

	@Test
	public void testCustomDataIngredient() {
		final NbtCompound requiredNbt = Util.make(new NbtCompound(), nbt -> {
			nbt.putInt("keyA", 1);
		});
		final NbtCompound acceptedNbt = Util.make(requiredNbt.copy(), nbt -> {
			nbt.putInt("keyB", 2);
		});
		final NbtCompound rejectedNbt1 = Util.make(new NbtCompound(), nbt -> {
			nbt.putInt("keyA", -1);
		});
		final NbtCompound rejectedNbt2 = Util.make(new NbtCompound(), nbt -> {
			nbt.putInt("keyB", 2);
		});

		final Ingredient baseIngredient = Ingredient.ofItems(Items.STICK);
		final Ingredient customDataIngredient = DefaultCustomIngredients.customData(baseIngredient, requiredNbt);

		ItemStack stack = new ItemStack(Items.STICK);
		assertFalse(customDataIngredient.test(stack));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(requiredNbt));
		assertTrue(customDataIngredient.test(stack));
		// This is a non-strict matching
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(acceptedNbt));
		assertTrue(customDataIngredient.test(stack));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rejectedNbt1));
		assertFalse(customDataIngredient.test(stack));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rejectedNbt2));
		assertFalse(customDataIngredient.test(stack));

		List<RegistryEntry<Item>> matchingItems = customDataIngredient.getMatchingItems().toList();
		assertEquals(1, matchingItems.size());
		assertEquals(Items.STICK, matchingItems.getFirst().value());
		// Test disabled as the vanilla API no longer exposes the stack with data.
		// assertEquals(NbtComponent.of(requiredNbt), matchingItems.getFirst().value().getDefaultStack().get(DataComponentTypes.CUSTOM_DATA));
	}
}
