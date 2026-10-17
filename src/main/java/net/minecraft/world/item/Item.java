package net.minecraft.world.item;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import net.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class Item implements FeatureElement, ItemLike, net.iris.api.v0.item.IrisItemLightProvider {
	public static final Codec<Holder<Item>> CODEC = BuiltInRegistries.ITEM
		.holderByNameCodec()
		.validate(holder -> holder.is(Items.AIR.builtInRegistryHolder()) ? DataResult.error(() -> "Item must not be minecraft:air") : DataResult.success(holder));
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Map<Block, Item> BY_BLOCK = Maps.<Block, Item>newHashMap();
	public static final ResourceLocation BASE_ATTACK_DAMAGE_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
	public static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");
	public static final int DEFAULT_MAX_STACK_SIZE = 64;
	public static final int ABSOLUTE_MAX_STACK_SIZE = 99;
	public static final int MAX_BAR_WIDTH = 13;
	protected static final int APPROXIMATELY_INFINITE_USE_DURATION = 72000;
	private final Holder.Reference<Item> builtInRegistryHolder = BuiltInRegistries.ITEM.createIntrusiveHolder(this);
	@Nullable
	private DataComponentMap components;
	@Nullable
	private final Item craftingRemainingItem;
	protected final String descriptionId;
	private final FeatureFlagSet requiredFeatures;

	public static int getId(Item item) {
		return item == null ? 0 : BuiltInRegistries.ITEM.getId(item);
	}

	public static Item byId(int i) {
		return BuiltInRegistries.ITEM.byId(i);
	}

	@Deprecated
	public static Item byBlock(Block block) {
		return (Item)BY_BLOCK.getOrDefault(block, Items.AIR);
	}

	public Item(Item.Properties properties) {
		this.descriptionId = properties.descriptionId;
		this.craftingRemainingItem = properties.craftingRemainingItem;
		this.requiredFeatures = properties.requiredFeatures;
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			String string = this.getClass().getSimpleName();
			if (!string.endsWith("Item")) {
				LOGGER.error("Item classes should end with Item and {} doesn't.", string);
			}
		}
	}

	@Deprecated
	public Holder.Reference<Item> builtInRegistryHolder() {
		return this.builtInRegistryHolder;
	}

	private boolean nativeTransferCopyPure;

	/** Bootstrap dispatch metadata, never a default-component or count cache. */
	public final boolean nativeTransferCopyIsPure() { return this.nativeTransferCopyPure; }

	public DataComponentMap components() {
		DataComponentMap components = this.components;
		if (components == null) {
			throw new UnboundComponentsException(this.descriptionId);
		}
		return components;
	}

	/** Reading an item's components before {@link NativeItems#bindComponents} binds them. */
	public static final class UnboundComponentsException extends IllegalStateException {
		UnboundComponentsException(String descriptionId) {
			super("Default components of " + descriptionId + " are bound when built-in registries finish bootstrapping");
		}
	}

	/** Sets the default components decoded from the Rust definition, once; see {@link NativeItems#bindComponents}. */
	void bindComponents(DataComponentMap components) {
		if (this.components != null) {
			throw new IllegalStateException("Default components of " + this.descriptionId + " are already bound");
		}
		this.components = components;
		try { this.nativeTransferCopyPure = this.getClass().getMethod("asItem").getDeclaringClass() == Item.class; }
		catch (ReflectiveOperationException | SecurityException unavailable) { this.nativeTransferCopyPure = false; }
		if (this.nativeTransferCopyPure) NativeItemState.markPureCopy(this);
		// Shulker boxes refuse these through every face (canPlaceItemThroughFace); native hopper plans need the set.
		if (this instanceof BlockItem blockItem && blockItem.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) NativeItemState.markShulkerItem(this);
		// An inherited (empty) inventoryTick lets the native inventory tick run without a Java call.
		boolean tickNoop;
		try { tickNoop = this.getClass().getMethod("inventoryTick", ItemStack.class, ServerLevel.class, Entity.class, EquipmentSlot.class).getDeclaringClass() == Item.class; }
		catch (ReflectiveOperationException | SecurityException unavailable) { tickNoop = false; }
		if (tickNoop) NativeItemState.markInventoryTickNoop(this);
	}

	public int getDefaultMaxStackSize() {
		return this.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
	}

	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int i) {
	}

	public void onDestroyed(ItemEntity itemEntity) {
	}

	public boolean canDestroyBlock(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, LivingEntity livingEntity) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		return tool != null && !tool.canDestroyBlocksInCreative() ? !(livingEntity instanceof Player player && player.getAbilities().instabuild) : true;
	}

	@Override
	public Item asItem() {
		return this;
	}

	public InteractionResult useOn(UseOnContext useOnContext) {
		return InteractionResult.PASS;
	}

	public float getDestroySpeed(ItemStack itemStack, BlockState blockState) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		return tool != null ? tool.getMiningSpeed(blockState) : 1.0F;
	}

	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			return consumable.startConsuming(player, itemStack, interactionHand);
		} else {
			Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.swappable()) {
				return equippable.swapWithEquipmentSlot(itemStack, player);
			} else {
				BlocksAttacks blocksAttacks = itemStack.get(DataComponents.BLOCKS_ATTACKS);
				if (blocksAttacks != null) {
					player.startUsingItem(interactionHand);
					return InteractionResult.CONSUME;
				} else {
					return InteractionResult.PASS;
				}
			}
		}
	}

	public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		return consumable != null ? consumable.onConsume(level, livingEntity, itemStack) : itemStack;
	}

	public boolean isBarVisible(ItemStack itemStack) {
		return itemStack.isDamaged();
	}

	public int getBarWidth(ItemStack itemStack) {
		return Mth.clamp(Math.round(13.0F - itemStack.getDamageValue() * 13.0F / itemStack.getMaxDamage()), 0, 13);
	}

	public int getBarColor(ItemStack itemStack) {
		int i = itemStack.getMaxDamage();
		float f = Math.max(0.0F, ((float)i - itemStack.getDamageValue()) / i);
		return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
	}

	public boolean overrideStackedOnOther(ItemStack itemStack, Slot slot, ClickAction clickAction, Player player) {
		return false;
	}

	public boolean overrideOtherStackedOnMe(ItemStack itemStack, ItemStack itemStack2, Slot slot, ClickAction clickAction, Player player, SlotAccess slotAccess) {
		return false;
	}

	public float getAttackDamageBonus(Entity entity, float f, DamageSource damageSource) {
		return 0.0F;
	}

	@Nullable
	public DamageSource getDamageSource(LivingEntity livingEntity) {
		return null;
	}

	public void hurtEnemy(ItemStack itemStack, LivingEntity livingEntity, LivingEntity livingEntity2) {
	}

	public void postHurtEnemy(ItemStack itemStack, LivingEntity livingEntity, LivingEntity livingEntity2) {
	}

	public boolean mineBlock(ItemStack itemStack, Level level, BlockState blockState, BlockPos blockPos, LivingEntity livingEntity) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		if (tool == null) {
			return false;
		} else {
			if (!level.isClientSide() && blockState.getDestroySpeed(level, blockPos) != 0.0F && tool.damagePerBlock() > 0) {
				itemStack.hurtAndBreak(tool.damagePerBlock(), livingEntity, EquipmentSlot.MAINHAND);
			}

			return true;
		}
	}

	public boolean isCorrectToolForDrops(ItemStack itemStack, BlockState blockState) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		return tool != null && tool.isCorrectForDrops(blockState);
	}

	public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
		return InteractionResult.PASS;
	}

	public String toString() {
		return BuiltInRegistries.ITEM.wrapAsHolder(this).getRegisteredName();
	}

	public final ItemStack getCraftingRemainder() {
		return this.craftingRemainingItem == null ? ItemStack.EMPTY : new ItemStack(this.craftingRemainingItem);
	}

	public void inventoryTick(ItemStack itemStack, ServerLevel serverLevel, Entity entity, @Nullable EquipmentSlot equipmentSlot) {
	}

	public void onCraftedBy(ItemStack itemStack, Player player) {
		this.onCraftedPostProcess(itemStack, player.level());
	}

	public void onCraftedPostProcess(ItemStack itemStack, Level level) {
	}

	public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			return consumable.animation();
		} else {
			BlocksAttacks blocksAttacks = itemStack.get(DataComponents.BLOCKS_ATTACKS);
			return blocksAttacks != null ? ItemUseAnimation.BLOCK : ItemUseAnimation.NONE;
		}
	}

	public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			return consumable.consumeTicks();
		} else {
			BlocksAttacks blocksAttacks = itemStack.get(DataComponents.BLOCKS_ATTACKS);
			return blocksAttacks != null ? 72000 : 0;
		}
	}

	public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int i) {
		return false;
	}

	@Deprecated
	public void appendHoverText(
		ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag
	) {
	}

	public Optional<TooltipComponent> getTooltipImage(ItemStack itemStack) {
		return Optional.empty();
	}

	@VisibleForTesting
	public final String getDescriptionId() {
		return this.descriptionId;
	}

	public final Component getName() {
		return this.components().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
	}

	public Component getName(ItemStack itemStack) {
		return itemStack.getComponents().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
	}

	public boolean isFoil(ItemStack itemStack) {
		return itemStack.isEnchanted();
	}

	protected static BlockHitResult getPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid fluid) {
		Vec3 vec3 = player.getEyePosition();
		Vec3 vec32 = vec3.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
		return level.clip(new ClipContext(vec3, vec32, ClipContext.Block.OUTLINE, fluid, player));
	}

	public boolean useOnRelease(ItemStack itemStack) {
		return false;
	}

	public ItemStack getDefaultInstance() {
		return new ItemStack(this);
	}

	public boolean canFitInsideContainerItems() {
		return true;
	}

	@Override
	public FeatureFlagSet requiredFeatures() {
		return this.requiredFeatures;
	}

	public boolean shouldPrintOpWarning(ItemStack itemStack, @Nullable Player player) {
		return false;
	}

	/**
	 * An item's Rust definition, resolved for its constructor: see
	 * {@link NativeItems}. Subclasses receive it from their registration
	 * factory. Default components bind later, once every registry they
	 * reference exists.
	 */
	public static final class Properties {
		final String descriptionId;
		@Nullable
		final Item craftingRemainingItem;
		final FeatureFlagSet requiredFeatures;

		Properties(String descriptionId, @Nullable Item craftingRemainingItem, FeatureFlagSet requiredFeatures) {
			this.descriptionId = descriptionId;
			this.craftingRemainingItem = craftingRemainingItem;
			this.requiredFeatures = requiredFeatures;
		}
	}

	public interface TooltipContext {
		Item.TooltipContext EMPTY = new Item.TooltipContext() {
			@Nullable
			@Override
			public HolderLookup.Provider registries() {
				return null;
			}

			@Override
			public float tickRate() {
				return 20.0F;
			}

			@Nullable
			@Override
			public MapItemSavedData mapData(MapId mapId) {
				return null;
			}

			@Override
			public boolean isPeaceful() {
				return false;
			}
		};

		@Nullable
		HolderLookup.Provider registries();

		float tickRate();

		@Nullable
		MapItemSavedData mapData(MapId mapId);

		boolean isPeaceful();

		static Item.TooltipContext of(@Nullable Level level) {
			return level == null ? EMPTY : new Item.TooltipContext() {
				@Override
				public HolderLookup.Provider registries() {
					return level.registryAccess();
				}

				@Override
				public float tickRate() {
					return level.tickRateManager().tickrate();
				}

				@Override
				public MapItemSavedData mapData(MapId mapId) {
					return level.getMapData(mapId);
				}

				@Override
				public boolean isPeaceful() {
					return level.getDifficulty() == Difficulty.PEACEFUL;
				}
			};
		}

		static Item.TooltipContext of(HolderLookup.Provider provider) {
			return new Item.TooltipContext() {
				@Override
				public HolderLookup.Provider registries() {
					return provider;
				}

				@Override
				public float tickRate() {
					return 20.0F;
				}

				@Nullable
				@Override
				public MapItemSavedData mapData(MapId mapId) {
					return null;
				}

				@Override
				public boolean isPeaceful() {
					return false;
				}
			};
		}
	}
}
