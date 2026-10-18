package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {
	protected static final int SLOT_INPUT = 0;
	protected static final int SLOT_FUEL = 1;
	protected static final int SLOT_RESULT = 2;
	public static final int DATA_LIT_TIME = 0;
	private static final int[] SLOTS_FOR_UP = new int[]{0};
	private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
	private static final int[] SLOTS_FOR_SIDES = new int[]{1};
	public static final int DATA_LIT_DURATION = 1;
	public static final int DATA_COOKING_PROGRESS = 2;
	public static final int DATA_COOKING_TOTAL_TIME = 3;
	public static final int NUM_DATA_VALUES = 4;
	public static final int BURN_TIME_STANDARD = 200;
	public static final int BURN_COOL_SPEED = 2;
	private static final Codec<Map<ResourceKey<Recipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(Recipe.KEY_CODEC, Codec.INT);
	private static final short DEFAULT_COOKING_TIMER = 0;
	private static final short DEFAULT_COOKING_TOTAL_TIME = 0;
	private static final short DEFAULT_LIT_TIME_REMAINING = 0;
	private static final short DEFAULT_LIT_TOTAL_TIME = 0;
	protected NonNullList<ItemStack> items = nativeClass(this.getClass()) ? net.minecraft.world.item.NativeItemSlots.withSize(3) : NonNullList.withSize(3, ItemStack.EMPTY);
	int litTimeRemaining;
	int litTotalTime;
	int cookingTimer;
	int cookingTotalTime;
	/**
	 * Exact stock furnaces keep their rows and timers in one native owner, which also
	 * decides each tick (see serverTick); subclasses keep the list and fields above.
	 */
	@Nullable
	private final net.minecraft.world.item.NativeItemState.Furnace nativeFurnace = nativeClass(this.getClass())
		? net.minecraft.world.item.NativeItemState.Furnace.create(this.items) : null;

	/** Ticks the native steps decided, and native ticks that continued an original Java segment. */
	static long nativeDecisions, nativeFallbacks;

	boolean nativeTicksForTest() { return this.nativeFurnace != null && this.nativeFurnace.bound(); }

	private static boolean nativeClass(Class<?> type) {
		return type == FurnaceBlockEntity.class || type == BlastFurnaceBlockEntity.class || type == SmokerBlockEntity.class;
	}

	int litTimeRemaining() { return this.nativeFurnace != null ? this.nativeFurnace.timer(0) : this.litTimeRemaining; }
	void litTimeRemaining(int i) { if (this.nativeFurnace != null) this.nativeFurnace.timer(0, i); else this.litTimeRemaining = i; }
	int litTotalTime() { return this.nativeFurnace != null ? this.nativeFurnace.timer(1) : this.litTotalTime; }
	void litTotalTime(int i) { if (this.nativeFurnace != null) this.nativeFurnace.timer(1, i); else this.litTotalTime = i; }
	int cookingTimer() { return this.nativeFurnace != null ? this.nativeFurnace.timer(2) : this.cookingTimer; }
	void cookingTimer(int i) { if (this.nativeFurnace != null) this.nativeFurnace.timer(2, i); else this.cookingTimer = i; }
	int cookingTotalTime() { return this.nativeFurnace != null ? this.nativeFurnace.timer(3) : this.cookingTotalTime; }
	void cookingTotalTime(int i) { if (this.nativeFurnace != null) this.nativeFurnace.timer(3, i); else this.cookingTotalTime = i; }
	protected final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int i) {
			switch (i) {
				case 0:
					return AbstractFurnaceBlockEntity.this.litTimeRemaining();
				case 1:
					return AbstractFurnaceBlockEntity.this.litTotalTime();
				case 2:
					return AbstractFurnaceBlockEntity.this.cookingTimer();
				case 3:
					return AbstractFurnaceBlockEntity.this.cookingTotalTime();
				default:
					return 0;
			}
		}

		@Override
		public void set(int i, int j) {
			switch (i) {
				case 0:
					AbstractFurnaceBlockEntity.this.litTimeRemaining(j);
					break;
				case 1:
					AbstractFurnaceBlockEntity.this.litTotalTime(j);
					break;
				case 2:
					AbstractFurnaceBlockEntity.this.cookingTimer(j);
					break;
				case 3:
					AbstractFurnaceBlockEntity.this.cookingTotalTime(j);
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};
	private final Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> recipesUsed = new Reference2IntOpenHashMap<>();
	private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

	protected AbstractFurnaceBlockEntity(
		BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState, RecipeType<? extends AbstractCookingRecipe> recipeType
	) {
		super(blockEntityType, blockPos, blockState);
		this.quickCheck = RecipeManager.createCheck(recipeType);
	}

	private boolean isLit() {
		return this.litTimeRemaining() > 0;
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items = this.nativeFurnace != null ? net.minecraft.world.item.NativeItemSlots.withSize(this.getContainerSize()) : NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (this.nativeFurnace != null) this.nativeFurnace.bind(this.items);
		ContainerHelper.loadAllItems(valueInput, this.items);
		this.cookingTimer(valueInput.getShortOr("cooking_time_spent", (short)0));
		this.cookingTotalTime(valueInput.getShortOr("cooking_total_time", (short)0));
		this.litTimeRemaining(valueInput.getShortOr("lit_time_remaining", (short)0));
		this.litTotalTime(valueInput.getShortOr("lit_total_time", (short)0));
		this.recipesUsed.clear();
		this.recipesUsed.putAll((Map<? extends ResourceKey<Recipe<?>>, ? extends Integer>)valueInput.read("RecipesUsed", RECIPES_USED_CODEC).orElse(Map.of()));
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.putShort("cooking_time_spent", (short)this.cookingTimer());
		valueOutput.putShort("cooking_total_time", (short)this.cookingTotalTime());
		valueOutput.putShort("lit_time_remaining", (short)this.litTimeRemaining());
		valueOutput.putShort("lit_total_time", (short)this.litTotalTime());
		ContainerHelper.saveAllItems(valueOutput, this.items);
		valueOutput.store("RecipesUsed", RECIPES_USED_CODEC, this.recipesUsed);
	}

	public static void serverTick(ServerLevel serverLevel, BlockPos blockPos, BlockState blockState, AbstractFurnaceBlockEntity abstractFurnaceBlockEntity) {
		boolean bl = abstractFurnaceBlockEntity.isLit();
		boolean bl2 = false;
		if (abstractFurnaceBlockEntity.isLit()) {
			abstractFurnaceBlockEntity.litTimeRemaining(abstractFurnaceBlockEntity.litTimeRemaining() - 1);
		}

		ItemStack itemStack = abstractFurnaceBlockEntity.items.get(1);
		ItemStack itemStack2 = abstractFurnaceBlockEntity.items.get(0);
		boolean bl3 = !itemStack2.isEmpty();
		boolean bl4 = !itemStack.isEmpty();
		if (abstractFurnaceBlockEntity.isLit() || bl4 && bl3) {
			SingleRecipeInput singleRecipeInput = new SingleRecipeInput(itemStack2);
			RecipeHolder<? extends AbstractCookingRecipe> recipeHolder;
			if (bl3) {
				recipeHolder = (RecipeHolder<? extends AbstractCookingRecipe>)abstractFurnaceBlockEntity.quickCheck
					.getRecipeFor(singleRecipeInput, serverLevel)
					.orElse(null);
			} else {
				recipeHolder = null;
			}

			int i = abstractFurnaceBlockEntity.getMaxStackSize();
			var nativeFurnace = abstractFurnaceBlockEntity.nativeFurnace;
			ItemStack template = recipeHolder == null ? null : recipeHolder.value().resultIfPlainCopy();
			if (nativeFurnace != null && nativeFurnace.bound() && (recipeHolder == null || template != null)) {
				bl2 = nativeDecisions(serverLevel, abstractFurnaceBlockEntity, nativeFurnace, recipeHolder, template, singleRecipeInput, itemStack, bl4, i, bl2);
			} else {
				bl2 = lightSegment(serverLevel, abstractFurnaceBlockEntity, recipeHolder, singleRecipeInput, itemStack, bl4, i, bl2);
				bl2 = cookSegment(serverLevel, abstractFurnaceBlockEntity, recipeHolder, singleRecipeInput, i, bl2);
			}
		} else if (!abstractFurnaceBlockEntity.isLit() && abstractFurnaceBlockEntity.cookingTimer() > 0) {
			abstractFurnaceBlockEntity.cookingTimer(Mth.clamp(abstractFurnaceBlockEntity.cookingTimer() - 2, 0, abstractFurnaceBlockEntity.cookingTotalTime()));
		}

		if (bl != abstractFurnaceBlockEntity.isLit()) {
			bl2 = true;
			blockState = blockState.setValue(AbstractFurnaceBlock.LIT, abstractFurnaceBlockEntity.isLit());
			serverLevel.setBlock(blockPos, blockState, 3);
		}

		if (bl2) {
			setChanged(serverLevel, blockPos, blockState);
		}
	}

	/**
	 * lightSegment + cookSegment for an exact furnace with native rows and a stock
	 * (or no) recipe: one native step decides both canBurn checks and the cook
	 * timer. Lighting and burning run the original code below; a decision only Java
	 * can make (codes 0x10-0x12) continues the original segment, unchanged.
	 */
	private static boolean nativeDecisions(
		ServerLevel serverLevel,
		AbstractFurnaceBlockEntity furnace,
		net.minecraft.world.item.NativeItemState.Furnace nativeFurnace,
		@Nullable RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
		@Nullable ItemStack template,
		SingleRecipeInput singleRecipeInput,
		ItemStack itemStack,
		boolean bl4,
		int i,
		boolean bl2
	) {
		long arg = (recipeHolder != null ? 1L << 32 : 0L) | (i & 0xFFFFFFFFL);
		long code = nativeFurnace.step(1, template, arg);
		if (code == 0x11 || code == 0x10) {
			nativeFallbacks++;
			bl2 = lightSegment(serverLevel, furnace, recipeHolder, singleRecipeInput, itemStack, bl4, i, bl2);
			return cookSegment(serverLevel, furnace, recipeHolder, singleRecipeInput, i, bl2);
		}
		if (code == 2) {
			bl2 = lightBody(serverLevel, furnace, itemStack, bl4, bl2);
			code = nativeFurnace.step(2, template, arg);
		}
		if (code == 0x12 || code == 0x10) {
			nativeFallbacks++;
			return cookSegment(serverLevel, furnace, recipeHolder, singleRecipeInput, i, bl2);
		}
		nativeDecisions++;
		if (code == 3) return burnBody(serverLevel, furnace, recipeHolder, singleRecipeInput, i, bl2);
		if (code != 0) throw new IllegalStateException("Unexpected native furnace step: " + code);
		return bl2;
	}

	private static boolean lightSegment(
		ServerLevel serverLevel,
		AbstractFurnaceBlockEntity abstractFurnaceBlockEntity,
		@Nullable RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
		SingleRecipeInput singleRecipeInput,
		ItemStack itemStack,
		boolean bl4,
		int i,
		boolean bl2
	) {
		if (!abstractFurnaceBlockEntity.isLit() && canBurn(serverLevel.registryAccess(), recipeHolder, singleRecipeInput, abstractFurnaceBlockEntity.items, i)) {
			bl2 = lightBody(serverLevel, abstractFurnaceBlockEntity, itemStack, bl4, bl2);
		}

		return bl2;
	}

	private static boolean lightBody(ServerLevel serverLevel, AbstractFurnaceBlockEntity abstractFurnaceBlockEntity, ItemStack itemStack, boolean bl4, boolean bl2) {
		abstractFurnaceBlockEntity.litTimeRemaining(abstractFurnaceBlockEntity.getBurnDuration(serverLevel.fuelValues(), itemStack));
		abstractFurnaceBlockEntity.litTotalTime(abstractFurnaceBlockEntity.litTimeRemaining());
		if (abstractFurnaceBlockEntity.isLit()) {
			bl2 = true;
			if (bl4) {
				Item item = itemStack.getItem();
				itemStack.shrink(1);
				if (itemStack.isEmpty()) {
					abstractFurnaceBlockEntity.items.set(1, item.getCraftingRemainder());
				}
			}
		}

		return bl2;
	}

	private static boolean cookSegment(
		ServerLevel serverLevel,
		AbstractFurnaceBlockEntity abstractFurnaceBlockEntity,
		@Nullable RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
		SingleRecipeInput singleRecipeInput,
		int i,
		boolean bl2
	) {
		if (abstractFurnaceBlockEntity.isLit() && canBurn(serverLevel.registryAccess(), recipeHolder, singleRecipeInput, abstractFurnaceBlockEntity.items, i)) {
			abstractFurnaceBlockEntity.cookingTimer(abstractFurnaceBlockEntity.cookingTimer() + 1);
			if (abstractFurnaceBlockEntity.cookingTimer() == abstractFurnaceBlockEntity.cookingTotalTime()) {
				abstractFurnaceBlockEntity.cookingTimer(0);
				bl2 = burnBody(serverLevel, abstractFurnaceBlockEntity, recipeHolder, singleRecipeInput, i, bl2);
			}
		} else {
			abstractFurnaceBlockEntity.cookingTimer(0);
		}

		return bl2;
	}

	/** After the cook timer reached its total and was reset. */
	private static boolean burnBody(
		ServerLevel serverLevel,
		AbstractFurnaceBlockEntity abstractFurnaceBlockEntity,
		@Nullable RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
		SingleRecipeInput singleRecipeInput,
		int i,
		boolean bl2
	) {
		abstractFurnaceBlockEntity.cookingTotalTime(getTotalCookTime(serverLevel, abstractFurnaceBlockEntity));
		if (burn(serverLevel.registryAccess(), recipeHolder, singleRecipeInput, abstractFurnaceBlockEntity.items, i)) {
			abstractFurnaceBlockEntity.setRecipeUsed(recipeHolder);
		}

		return true;
	}

	private static boolean canBurn(
		RegistryAccess registryAccess,
		@Nullable RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
		SingleRecipeInput singleRecipeInput,
		NonNullList<ItemStack> nonNullList,
		int i
	) {
		if (!nonNullList.get(0).isEmpty() && recipeHolder != null) {
			// The stock result is only compared here: read it instead of a transient copy.
			ItemStack template = recipeHolder.value().resultIfPlainCopy();
			ItemStack itemStack = template != null ? template : recipeHolder.value().assemble(singleRecipeInput, registryAccess);
			if (itemStack.isEmpty()) {
				return false;
			} else {
				ItemStack itemStack2 = nonNullList.get(2);
				if (itemStack2.isEmpty()) {
					return true;
				} else if (!ItemStack.isSameItemSameComponents(itemStack2, itemStack)) {
					return false;
				} else {
					return itemStack2.getCount() < i && itemStack2.getCount() < itemStack2.getMaxStackSize() ? true : itemStack2.getCount() < itemStack.getMaxStackSize();
				}
			}
		} else {
			return false;
		}
	}

	private static boolean burn(
		RegistryAccess registryAccess,
		@Nullable RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
		SingleRecipeInput singleRecipeInput,
		NonNullList<ItemStack> nonNullList,
		int i
	) {
		if (recipeHolder != null && canBurn(registryAccess, recipeHolder, singleRecipeInput, nonNullList, i)) {
			ItemStack itemStack = nonNullList.get(0);
			// One copy of a stock result (the original copied its transient copy).
			ItemStack template = recipeHolder.value().resultIfPlainCopy();
			ItemStack itemStack2 = template != null ? template : recipeHolder.value().assemble(singleRecipeInput, registryAccess);
			ItemStack itemStack3 = nonNullList.get(2);
			if (itemStack3.isEmpty()) {
				nonNullList.set(2, itemStack2.copy());
			} else if (ItemStack.isSameItemSameComponents(itemStack3, itemStack2)) {
				itemStack3.grow(1);
			}

			if (itemStack.is(Blocks.WET_SPONGE.asItem()) && !nonNullList.get(1).isEmpty() && nonNullList.get(1).is(Items.BUCKET)) {
				nonNullList.set(1, new ItemStack(Items.WATER_BUCKET));
			}

			itemStack.shrink(1);
			return true;
		} else {
			return false;
		}
	}

	protected int getBurnDuration(FuelValues fuelValues, ItemStack itemStack) {
		return fuelValues.burnDuration(itemStack);
	}

	private static int getTotalCookTime(ServerLevel serverLevel, AbstractFurnaceBlockEntity abstractFurnaceBlockEntity) {
		SingleRecipeInput singleRecipeInput = new SingleRecipeInput(abstractFurnaceBlockEntity.getItem(0));
		return (Integer)abstractFurnaceBlockEntity.quickCheck
			.getRecipeFor(singleRecipeInput, serverLevel)
			.map(recipeHolder -> ((AbstractCookingRecipe)recipeHolder.value()).cookingTime())
			.orElse(200);
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		if (direction == Direction.DOWN) {
			return SLOTS_FOR_DOWN;
		} else {
			return direction == Direction.UP ? SLOTS_FOR_UP : SLOTS_FOR_SIDES;
		}
	}

	@Override
	public boolean canPlaceItemThroughFace(int i, ItemStack itemStack, @Nullable Direction direction) {
		return this.canPlaceItem(i, itemStack);
	}

	@Override
	public boolean canTakeItemThroughFace(int i, ItemStack itemStack, Direction direction) {
		return direction == Direction.DOWN && i == 1 ? itemStack.is(Items.WATER_BUCKET) || itemStack.is(Items.BUCKET) : true;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
		if (this.nativeFurnace != null) this.nativeFurnace.bind(nonNullList);
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		ItemStack itemStack2 = this.items.get(i);
		boolean bl = !itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack2, itemStack);
		this.items.set(i, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
		if (i == 0 && !bl && this.level instanceof ServerLevel serverLevel) {
			this.cookingTotalTime(getTotalCookTime(serverLevel, this));
			this.cookingTimer(0);
			this.setChanged();
		}
	}

	@Override
	public boolean canPlaceItem(int i, ItemStack itemStack) {
		if (i == 2) {
			return false;
		} else if (i != 1) {
			return true;
		} else {
			ItemStack itemStack2 = this.items.get(1);
			return this.level.fuelValues().isFuel(itemStack) || itemStack.is(Items.BUCKET) && !itemStack2.is(Items.BUCKET);
		}
	}

	@Override
	public void setRecipeUsed(@Nullable RecipeHolder<?> recipeHolder) {
		if (recipeHolder != null) {
			ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
			this.recipesUsed.addTo(resourceKey, 1);
		}
	}

	@Nullable
	@Override
	public RecipeHolder<?> getRecipeUsed() {
		return null;
	}

	@Override
	public void awardUsedRecipes(Player player, List<ItemStack> list) {
	}

	public void awardUsedRecipesAndPopExperience(ServerPlayer serverPlayer) {
		List<RecipeHolder<?>> list = this.getRecipesToAwardAndPopExperience(serverPlayer.level(), serverPlayer.position());
		serverPlayer.awardRecipes(list);

		for (RecipeHolder<?> recipeHolder : list) {
			if (recipeHolder != null) {
				serverPlayer.triggerRecipeCrafted(recipeHolder, this.items);
			}
		}

		this.recipesUsed.clear();
	}

	public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel serverLevel, Vec3 vec3) {
		List<RecipeHolder<?>> list = Lists.<RecipeHolder<?>>newArrayList();

		for (Entry<ResourceKey<Recipe<?>>> entry : this.recipesUsed.reference2IntEntrySet()) {
			serverLevel.recipeAccess().byKey((ResourceKey<Recipe<?>>)entry.getKey()).ifPresent(recipeHolder -> {
				list.add(recipeHolder);
				createExperience(serverLevel, vec3, entry.getIntValue(), ((AbstractCookingRecipe)recipeHolder.value()).experience());
			});
		}

		return list;
	}

	private static void createExperience(ServerLevel serverLevel, Vec3 vec3, int i, float f) {
		int j = Mth.floor(i * f);
		float g = Mth.frac(i * f);
		if (g != 0.0F && Math.random() < g) {
			j++;
		}

		ExperienceOrb.award(serverLevel, vec3, j);
	}

	@Override
	public void fillStackedContents(StackedItemContents stackedItemContents) {
		for (ItemStack itemStack : this.items) {
			stackedItemContents.accountStack(itemStack);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
		super.preRemoveSideEffects(blockPos, blockState);
		if (this.level instanceof ServerLevel serverLevel) {
			this.getRecipesToAwardAndPopExperience(serverLevel, Vec3.atCenterOf(blockPos));
		}
	}
}
