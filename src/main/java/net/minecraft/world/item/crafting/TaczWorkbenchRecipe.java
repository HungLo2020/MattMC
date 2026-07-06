package net.minecraft.world.item.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TaczGunDefinitions;

public record TaczWorkbenchRecipe(ResourceLocation id, Category category, ResourceLocation group, List<Ingredient> ingredients, ItemStack result) {
	private static final Map<Category, List<TaczWorkbenchRecipe>> RECIPES = loadRecipes();
	private static final List<TaczWorkbenchRecipe> ALL_RECIPES = RECIPES.values().stream().flatMap(List::stream).toList();

	public static List<TaczWorkbenchRecipe> recipes(Category category) {
		return RECIPES.getOrDefault(category, List.of());
	}

	public static List<TaczWorkbenchRecipe> allRecipes() {
		return ALL_RECIPES;
	}

	public boolean canCraft(Inventory inventory) {
		for (Ingredient ingredient : this.ingredients) {
			if (countMatching(inventory, ingredient) < ingredient.count()) {
				return false;
			}
		}

		return true;
	}

	public void craft(Player player) {
		if (!this.canCraft(player.getInventory())) {
			return;
		}

		if (!player.getAbilities().instabuild) {
			for (Ingredient ingredient : this.ingredients) {
				consume(player.getInventory(), ingredient);
			}
		}

		ItemStack crafted = this.result.copy();
		crafted.onCraftedBy(player, crafted.getCount());
		if (!player.getInventory().add(crafted)) {
			player.drop(crafted, false);
		}
	}

	public List<Component> materialLines(Inventory inventory) {
		List<Component> lines = new ArrayList<>();
		for (Ingredient ingredient : this.ingredients) {
			int available = countMatching(inventory, ingredient);
			lines.add(Component.literal(available + "/" + ingredient.count() + " ").append(ingredient.displayName()));
		}
		return lines;
	}

	private static int countMatching(Inventory inventory, Ingredient ingredient) {
		int count = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (ingredient.matches(stack)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	private static void consume(Inventory inventory, Ingredient ingredient) {
		int remaining = ingredient.count();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (remaining <= 0) {
				return;
			}

			if (ingredient.matches(stack)) {
				int removed = Math.min(remaining, stack.getCount());
				stack.shrink(removed);
				remaining -= removed;
			}
		}
		inventory.setChanged();
	}

	private static Map<Category, List<TaczWorkbenchRecipe>> loadRecipes() {
		Map<Category, List<TaczWorkbenchRecipe>> recipes = new EnumMap<>(Category.class);
		for (Category category : Category.values()) {
			List<TaczWorkbenchRecipe> categoryRecipes = new ArrayList<>();
			for (String itemId : category.itemIds()) {
				TaczWorkbenchRecipe recipe = loadRecipe(category, itemId);
				if (recipe != null && !recipe.result().isEmpty()) {
					categoryRecipes.add(recipe);
				}
			}
			recipes.put(category, List.copyOf(categoryRecipes));
		}

		return Collections.unmodifiableMap(recipes);
	}

	private static TaczWorkbenchRecipe loadRecipe(Category category, String itemId) {
		String path = "data/minecraft/recipes/" + category.directory + "/" + itemId + ".json";
		try (InputStream stream = TaczWorkbenchRecipe.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return null;
			}

			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray materials = root.getAsJsonArray("materials");
			List<Ingredient> ingredients = new ArrayList<>();
			for (JsonElement element : materials) {
				JsonObject material = element.getAsJsonObject();
				JsonObject item = material.getAsJsonObject("item");
				int count = material.has("count") ? material.get("count").getAsInt() : 1;
				if (item.has("tag")) {
					ingredients.add(Ingredient.tag(ResourceLocation.parse(item.get("tag").getAsString()), count));
				} else {
					ingredients.add(Ingredient.item(ResourceLocation.parse(item.get("item").getAsString()), count));
				}
			}

			JsonObject result = root.getAsJsonObject("result");
			ResourceLocation resultId = ResourceLocation.parse(result.get("id").getAsString());
			ResourceLocation group = result.has("group") ? ResourceLocation.parse(result.get("group").getAsString()) : loadIndexGroup(category, itemId);
			int resultCount = result.has("count") ? result.get("count").getAsInt() : 1;
			Item resultItem = BuiltInRegistries.ITEM.getValue(resultId);
			ItemStack resultStack = resultItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(resultItem, resultCount);
			return new TaczWorkbenchRecipe(ResourceLocation.withDefaultNamespace(category.directory + "/" + itemId), category, group, List.copyOf(ingredients), resultStack);
		} catch (Exception exception) {
			return null;
		}
	}

	private static ResourceLocation loadIndexGroup(Category category, String itemId) {
		String path = "data/minecraft/index/" + category.indexDirectory + "/" + itemId + ".json";
		try (InputStream stream = TaczWorkbenchRecipe.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return category.defaultGroup;
			}

			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			return root.has("type") ? ResourceLocation.withDefaultNamespace(root.get("type").getAsString()) : category.defaultGroup;
		} catch (Exception exception) {
			return category.defaultGroup;
		}
	}

	public enum Category {
		GUN("gun", "guns", "rifle", TaczGunDefinitions.GUNS.stream().map(TaczGunDefinitions.Gun::id).toList()),
		AMMO("ammo", "ammo", "ammo", TaczGunDefinitions.AMMO.stream().map(TaczGunDefinitions.Ammo::id).toList()),
		ATTACHMENT("attachments", "attachments", "scope", TaczGunDefinitions.ATTACHMENTS.stream().map(TaczGunDefinitions.Attachment::id).toList());

		private final String directory;
		private final String indexDirectory;
		private final ResourceLocation defaultGroup;
		private final List<String> itemIds;

		Category(String directory, String indexDirectory, String defaultGroup, List<String> itemIds) {
			this.directory = directory;
			this.indexDirectory = indexDirectory;
			this.defaultGroup = ResourceLocation.withDefaultNamespace(defaultGroup);
			this.itemIds = itemIds;
		}

		public List<String> itemIds() {
			return this.itemIds;
		}
	}

	public static final class Ingredient {
		private static final Map<ResourceLocation, Item> TAG_DISPLAY_ITEMS = new HashMap<>();
		private final ResourceLocation id;
		private final TagKey<Item> tag;
		private final Item item;
		private final int count;

		private Ingredient(ResourceLocation id, TagKey<Item> tag, Item item, int count) {
			this.id = id;
			this.tag = tag;
			this.item = item;
			this.count = count;
		}

		public static Ingredient tag(ResourceLocation id, int count) {
			return new Ingredient(id, TagKey.create(Registries.ITEM, id), null, count);
		}

		public static Ingredient item(ResourceLocation id, int count) {
			return new Ingredient(id, null, BuiltInRegistries.ITEM.getValue(id), count);
		}

		public int count() {
			return this.count;
		}

		public boolean matches(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}

			return this.tag != null ? stack.is(this.tag) : stack.is(this.item);
		}

		public Component displayName() {
			Item displayItem = this.displayItem();
			return displayItem == Items.AIR ? Component.literal(this.id.toString()) : displayItem.getName(new ItemStack(displayItem));
		}

		public ItemStack displayStack() {
			Item displayItem = this.displayItem();
			return displayItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(displayItem);
		}

		private Item displayItem() {
			if (this.item != null) {
				return this.item;
			}

			return TAG_DISPLAY_ITEMS.computeIfAbsent(this.id, id -> {
				for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(this.tag)) {
					return holder.value();
				}
				return Items.AIR;
			});
		}
	}
}
