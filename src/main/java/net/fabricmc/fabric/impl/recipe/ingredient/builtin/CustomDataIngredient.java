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

package net.fabricmc.fabric.impl.recipe.ingredient.builtin;

import java.util.Objects;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.component.DataComponents;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.core.entry.RegistryEntry;
import net.minecraft.server.packss.ResourceLocation;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;

public class CustomDataIngredient implements CustomIngredient {
	public static final CustomIngredientSerializer<CustomDataIngredient> SERIALIZER = new Serializer();

	private final Ingredient base;
	private final CompoundTag nbt;

	public CustomDataIngredient(Ingredient base, CompoundTag nbt) {
		if (nbt == null || nbt.isEmpty()) throw new IllegalArgumentException("NBT cannot be null; use components ingredient for strict matching");

		this.base = base;
		this.nbt = nbt;
	}

	@Override
	public boolean test(ItemStack stack) {
		if (!base.test(stack)) return false;

		NbtComponent nbt = stack.get(DataComponents.CUSTOM_DATA);

		return nbt != null && nbt.matches(this.nbt);
	}

	@Override
	public Stream<RegistryEntry<Item>> getMatchingItems() {
		return base.getMatchingItems();
	}

	@Override
	public SlotDisplay toDisplay() {
		return new SlotDisplay.CompositeSlotDisplay(
				base.getMatchingItems().map(this::createEntryDisplay).toList()
		);
	}

	private SlotDisplay createEntryDisplay(RegistryEntry<Item> entry) {
		ItemStack stack = entry.value().getDefaultStack();
		stack.apply(DataComponents.CUSTOM_DATA, NbtComponent.DEFAULT, existingNbt -> NbtComponent.of(existingNbt.copyNbt().copyFrom(nbt)));
		return new SlotDisplay.StackSlotDisplay(stack);
	}

	@Override
	public boolean requiresTesting() {
		return true;
	}

	@Override
	public CustomIngredientSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	private Ingredient getBase() {
		return base;
	}

	private CompoundTag getNbt() {
		return nbt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CustomDataIngredient that = (CustomDataIngredient) o;
		return base.equals(that.base) && nbt.equals(that.nbt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(base, nbt);
	}

	private static class Serializer implements CustomIngredientSerializer<CustomDataIngredient> {
		private static final ResourceLocation ID = ResourceLocation.of("fabric", "custom_data");

		private static final MapCodec<CustomDataIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
				instance.group(
						Ingredient.CODEC.fieldOf("base").forGetter(CustomDataIngredient::getBase),
						StringNbtReader.NBT_COMPOUND_CODEC.fieldOf("nbt").forGetter(CustomDataIngredient::getNbt)
				).apply(instance, CustomDataIngredient::new)
		);

		private static final PacketCodec<RegistryByteBuf, CustomDataIngredient> PACKET_CODEC = PacketCodec.tuple(
				Ingredient.PACKET_CODEC, CustomDataIngredient::getBase,
				PacketCodecs.NBT_COMPOUND, CustomDataIngredient::getNbt,
				CustomDataIngredient::new
		);

		@Override
		public ResourceLocation getIdentifier() {
			return ID;
		}

		@Override
		public MapCodec<CustomDataIngredient> getCodec() {
			return CODEC;
		}

		@Override
		public PacketCodec<RegistryByteBuf, CustomDataIngredient> getPacketCodec() {
			return PACKET_CODEC;
		}
	}
}
