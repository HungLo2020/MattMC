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

package net.fabricmc.fabric.mixin.itemgroup;

import static net.minecraft.world.item.ItemGroups.BUILDING_BLOCKS;
import static net.minecraft.world.item.ItemGroups.COLORED_BLOCKS;
import static net.minecraft.world.item.ItemGroups.COMBAT;
import static net.minecraft.world.item.ItemGroups.FOOD_AND_DRINK;
import static net.minecraft.world.item.ItemGroups.FUNCTIONAL;
import static net.minecraft.world.item.ItemGroups.HOTBAR;
import static net.minecraft.world.item.ItemGroups.INGREDIENTS;
import static net.minecraft.world.item.ItemGroups.INVENTORY;
import static net.minecraft.world.item.ItemGroups.NATURAL;
import static net.minecraft.world.item.ItemGroups.OPERATOR;
import static net.minecraft.world.item.ItemGroups.REDSTONE;
import static net.minecraft.world.item.ItemGroups.SEARCH;
import static net.minecraft.world.item.ItemGroups.SPAWN_EGGS;
import static net.minecraft.world.item.ItemGroups.TOOLS;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.ItemGroup;
import net.minecraft.world.item.ItemGroups;
import net.minecraft.core.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.impl.itemgroup.FabricItemGroupImpl;

@Mixin(ItemGroups.class)
public class ItemGroupsMixin {
	@Unique
	private static final int TABS_PER_PAGE = FabricItemGroupImpl.TABS_PER_PAGE;

	@Inject(method = "collect", at = @At("HEAD"), cancellable = true)
	private static void deferDuplicateCheck(CallbackInfo ci) {
		/*
		 * Defer the duplication checks to when fabric performs them (see mixin below).
		 * It is preserved just in case, but fabric's pagination logic should prevent any from happening anyway.
		 */
		ci.cancel();
	}

	@Inject(method = "updateEntries", at = @At("TAIL"))
	private static void paginateGroups(CallbackInfo ci) {
		final List<ResourceKey<ItemGroup>> vanillaGroups = List.of(BUILDING_BLOCKS, COLORED_BLOCKS, NATURAL, FUNCTIONAL, REDSTONE, HOTBAR, SEARCH, TOOLS, COMBAT, FOOD_AND_DRINK, INGREDIENTS, SPAWN_EGGS, OPERATOR, INVENTORY);

		int count = 0;

		Comparator<Holder.Reference<ItemGroup>> entryComparator = (e1, e2) -> {
			// Non-displayable groups should come last for proper pagination
			int displayCompare = Boolean.compare(e1.value().shouldDisplay(), e2.value().shouldDisplay());

			if (displayCompare != 0) {
				return -displayCompare;
			} else {
				// Ensure a deterministic order
				return compareNamespaceFirst(e1.registryKey().getValue(), e2.registryKey().getValue());
			}
		};
		final List<Holder.Reference<ItemGroup>> sortedItemGroups = Registries.ITEM_GROUP.streamEntries()
				.sorted(entryComparator)
				.toList();

		for (Holder.Reference<ItemGroup> reference : sortedItemGroups) {
			final ItemGroup itemGroup = reference.value();
			final FabricItemGroupImpl fabricItemGroup = (FabricItemGroupImpl) itemGroup;

			if (vanillaGroups.contains(reference.registryKey())) {
				// Vanilla group goes on the first page.
				fabricItemGroup.fabric_setPage(0);
				continue;
			}

			final ItemGroupAccessor itemGroupAccessor = (ItemGroupAccessor) itemGroup;
			fabricItemGroup.fabric_setPage((count / TABS_PER_PAGE) + 1);
			int pageIndex = count % TABS_PER_PAGE;
			ItemGroup.Row row = pageIndex < (TABS_PER_PAGE / 2) ? ItemGroup.Row.TOP : ItemGroup.Row.BOTTOM;
			itemGroupAccessor.setRow(row);
			itemGroupAccessor.setColumn(row == ItemGroup.Row.TOP ? pageIndex % TABS_PER_PAGE : (pageIndex - TABS_PER_PAGE / 2) % (TABS_PER_PAGE));

			count++;
		}

		// Overlapping group detection logic, with support for pages.
		record ItemGroupPosition(ItemGroup.Row row, int column, int page) { }
		var map = new HashMap<ItemGroupPosition, String>();

		for (ResourceKey<ItemGroup> registryKey : Registries.ITEM_GROUP.getKeys()) {
			final ItemGroup itemGroup = Registries.ITEM_GROUP.getValueOrThrow(registryKey);
			final FabricItemGroupImpl fabricItemGroup = (FabricItemGroupImpl) itemGroup;
			final String displayName = itemGroup.getDisplayName().getString();
			final var position = new ItemGroupPosition(itemGroup.getRow(), itemGroup.getColumn(), fabricItemGroup.fabric_getPage());
			final String existingName = map.put(position, displayName);

			if (existingName != null) {
				throw new IllegalArgumentException("Duplicate position: (%s) for item groups %s vs %s".formatted(position, displayName, existingName));
			}
		}
	}

	// ResourceLocation#compareTo checks the path first, but we want to check the namespace first so that groups added by the
	// same mod appear next to each other.
	@Unique
	private static int compareNamespaceFirst(ResourceLocation a, ResourceLocation b) {
		int c = a.getNamespace().compareTo(b.getNamespace());

		if (c != 0) {
			return c;
		}

		return a.getPath().compareTo(b.getPath());
	}
}
