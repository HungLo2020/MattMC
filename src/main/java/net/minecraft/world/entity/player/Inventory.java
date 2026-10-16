package net.minecraft.world.entity.player;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.CrashReportDetail;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NativeItemSlots;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Inventory implements Container, Nameable {
	public static final int POP_TIME_DURATION = 5;
	public static final int INVENTORY_SIZE = 36;
	public static final int SELECTION_SIZE = 9;
	public static final int SLOT_OFFHAND = 40;
	public static final int SLOT_BODY_ARMOR = 41;
	public static final int SLOT_SADDLE = 42;
	public static final int NOT_FOUND_INDEX = -1;
	public static final Int2ObjectMap<EquipmentSlot> EQUIPMENT_SLOT_MAPPING = new Int2ObjectArrayMap<>(
		Map.of(
			EquipmentSlot.FEET.getIndex(36),
			EquipmentSlot.FEET,
			EquipmentSlot.LEGS.getIndex(36),
			EquipmentSlot.LEGS,
			EquipmentSlot.CHEST.getIndex(36),
			EquipmentSlot.CHEST,
			EquipmentSlot.HEAD.getIndex(36),
			EquipmentSlot.HEAD,
			40,
			EquipmentSlot.OFFHAND,
			41,
			EquipmentSlot.BODY,
			42,
			EquipmentSlot.SADDLE
		)
	);
	private static final Component DEFAULT_NAME = Component.translatable("container.inventory");
	private final NativeItemSlots nativeItems = this.getClass() == Inventory.class ? NativeItemSlots.inventory() : null;
	private final NonNullList<ItemStack> items = this.nativeItems != null ? this.nativeItems : NonNullList.withSize(36, ItemStack.EMPTY);
	private static final class LegacyState { int selected; int timesChanged; }
	private final LegacyState legacyState = this.nativeItems == null ? new LegacyState() : null;
	public final Player player;
	private final EntityEquipment equipment;

	public Inventory(Player player, EntityEquipment entityEquipment) {
		this.player = player;
		this.equipment = entityEquipment;
		if(this.nativeItems!=null)this.nativeItems.bindEquipment(entityEquipment);
	}

	private static int nativeEquipmentMapping(int index){EquipmentSlot slot=EQUIPMENT_SLOT_MAPPING.get(index);return slot==null?0:slot.ordinal()+1;}

	private int selectedValue() { return this.nativeItems != null ? this.nativeItems.selected() : this.legacyState.selected; }

	/** Exact stock backing admission; no virtual container getter is called. */
	public final NativeItemSlots nativeMainSlotsForTransfer() { return this.getClass() == Inventory.class && this.items == this.nativeItems ? this.nativeItems : null; }

	/** Remaining original setItem effect after an admitted native main-slot commit. */
	public final void nativeMainSetEffects(int index, ItemStack stack) {
		if (this.nativeMainSlotsForTransfer() == null || index < 0 || index >= 36) throw new IllegalStateException("Invalid native inventory effect");
		EquipmentSlot slot = EQUIPMENT_SLOT_MAPPING.get(index);
		if (slot != null) this.equipment.set(slot, stack);
	}

	public int getSelectedSlot() {
		return this.selectedValue();
	}

	public void setSelectedSlot(int i) {
		if (!isHotbarSlot(i)) {
			throw new IllegalArgumentException("Invalid selected slot");
		} else {
			if (this.nativeItems != null) this.nativeItems.selected(i); else this.legacyState.selected = i;
		}
	}

	public ItemStack getSelectedItem() {
		return this.items.get(this.selectedValue());
	}

	public ItemStack setSelectedItem(ItemStack itemStack) {
		return this.items.set(this.selectedValue(), itemStack);
	}

	public static int getSelectionSize() {
		return 9;
	}

	public NonNullList<ItemStack> getNonEquipmentItems() {
		return this.items;
	}

	private boolean hasRemainingSpaceForItem(ItemStack itemStack, ItemStack itemStack2) {
		return !itemStack.isEmpty()
			&& ItemStack.isSameItemSameComponents(itemStack, itemStack2)
			&& itemStack.isStackable()
			&& itemStack.getCount() < this.getMaxStackSize(itemStack);
	}

	public int getFreeSlot() {
		if (this.nativeItems != null) return this.nativeItems.freeSlot();
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	public void addAndPickItem(ItemStack itemStack) {
		if (this.nativeItems != null) { this.scanMain(4,itemStack,null); return; }
		this.setSelectedSlot(this.getSuitableHotbarSlot());
		if (!this.items.get(this.selectedValue()).isEmpty()) {
			int i = this.getFreeSlot();
			if (i != -1) {
				this.items.set(i, this.items.get(this.selectedValue()));
			}
		}

		this.items.set(this.selectedValue(), itemStack);
	}

	public void pickSlot(int i) {
		this.setSelectedSlot(this.getSuitableHotbarSlot());
		if (this.nativeItems != null) { this.nativeItems.swapSelected(i); return; }
		ItemStack itemStack = this.items.get(this.selectedValue());
		this.items.set(this.selectedValue(), this.items.get(i));
		this.items.set(i, itemStack);
	}

	public static boolean isHotbarSlot(int i) {
		return i >= 0 && i < 9;
	}

	public int findSlotMatchingItem(ItemStack itemStack) {
		if (this.nativeItems != null) return this.scanMain(1,itemStack,null);
		for (int i = 0; i < this.items.size(); i++) {
			if (!this.items.get(i).isEmpty() && ItemStack.isSameItemSameComponents(itemStack, this.items.get(i))) {
				return i;
			}
		}

		return -1;
	}

	public static boolean isUsableForCrafting(ItemStack itemStack) {
		return !itemStack.isDamaged() && !itemStack.isEnchanted() && !itemStack.has(DataComponents.CUSTOM_NAME);
	}

	public int findSlotMatchingCraftingIngredient(Holder<Item> holder, ItemStack itemStack) {
		if (this.nativeItems != null) return this.scanMain(3,itemStack,holder);
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack2 = this.items.get(i);
			if (!itemStack2.isEmpty()
				&& itemStack2.is(holder)
				&& isUsableForCrafting(itemStack2)
				&& (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemStack2))) {
				return i;
			}
		}

		return -1;
	}

	public int getSuitableHotbarSlot() {
		if (this.nativeItems != null) return this.scanMain(0,null,null);
		for (int i = 0; i < 9; i++) {
			int j = (this.selectedValue() + i) % 9;
			if (this.items.get(j).isEmpty()) {
				return j;
			}
		}

		for (int ix = 0; ix < 9; ix++) {
			int j = (this.selectedValue() + ix) % 9;
			if (!this.items.get(j).isEnchanted()) {
				return j;
			}
		}

		return this.selectedValue();
	}

	public int clearOrCountMatchingItems(Predicate<ItemStack> predicate, int i, Container container) {
		int j = 0;
		boolean bl = i == 0;
		j += ContainerHelper.clearOrCountMatchingItems(this, predicate, i - j, bl);
		j += ContainerHelper.clearOrCountMatchingItems(container, predicate, i - j, bl);
		ItemStack itemStack = this.player.containerMenu.getCarried();
		j += ContainerHelper.clearOrCountMatchingItems(itemStack, predicate, i - j, bl);
		if (itemStack.isEmpty()) {
			this.player.containerMenu.setCarried(ItemStack.EMPTY);
		}

		return j;
	}

	private int addResource(ItemStack itemStack) {
		int i = this.getSlotWithRemainingSpace(itemStack);
		if (i == -1) {
			i = this.getFreeSlot();
		}

		return i == -1 ? itemStack.getCount() : this.addResource(i, itemStack);
	}

	private int addResource(int i, ItemStack itemStack) {
		int j = itemStack.getCount();
		ItemStack itemStack2 = this.getItem(i);
		if (itemStack2.isEmpty()) {
			itemStack2 = itemStack.copyWithCount(0);
			this.setItem(i, itemStack2);
		}

		int k = this.getMaxStackSize(itemStack2) - itemStack2.getCount();
		int l = Math.min(j, k);
		if (l == 0) {
			return j;
		} else {
			j -= l;
			itemStack2.grow(l);
			itemStack2.setPopTime(5);
			return j;
		}
	}

	/** addResource with the empty-slot copy the native add already prepared (adopted only for a pure source). */
	private int addResourcePrepared(int i, ItemStack itemStack, long handle, long address, long adopted) {
		int j = itemStack.getCount();
		ItemStack itemStack2 = this.getItem(i);
		if (itemStack2.isEmpty()) {
			itemStack2 = !itemStack.isEmpty() && itemStack.getItem() != null && itemStack.getItem().nativeTransferCopyIsPure()
				? itemStack.adoptPreparedCopy(handle, address, adopted) : itemStack.copyWithCount(0);
			this.setItem(i, itemStack2);
		}

		int k = this.getMaxStackSize(itemStack2) - itemStack2.getCount();
		int l = Math.min(j, k);
		if (l == 0) {
			return j;
		} else {
			j -= l;
			itemStack2.grow(l);
			itemStack2.setPopTime(5);
			return j;
		}
	}

	private int scanMain(int mode,ItemStack source,Holder<Item> holder) {
		return this.nativeItems.scanInventory(source,mode,new net.minecraft.world.item.NativeItemState.InventoryAddEffects() {
			@Override public long effect(long action,int index) {
				return switch((int)action) {
					case 2 -> Inventory.this.hasRemainingSpaceForItem(Inventory.this.getItem(index),source)?1:0;
					case 11 -> nativeEquipmentMapping(index);
					case 6 -> Inventory.this.items.get(index).isEnchanted()?1:0;
					case 7 -> ItemStack.isSameItemSameComponents(source,Inventory.this.items.get(index))?1:0;
					case 8 -> {
						ItemStack target=Inventory.this.items.get(index);
						yield matchesCraftingCandidate(holder,source,target)?1:0;
					}
					case 12 -> {
						this.candidate=Inventory.this.items.get(index);
						yield this.candidateResult(this.candidate.is(holder));
					}
					case 13 -> isUsableForCrafting(this.candidate)?1:0;
					case 14 -> matchesCraftingSource(source,this.candidate)?1:0;
					case 9 -> { Inventory.this.items.set(index,Inventory.this.items.get(Inventory.this.selectedValue()));yield 0; }
					case 10 -> { Inventory.this.items.set(index,source);yield 0; }
					default -> throw new IllegalStateException("Unknown inventory scan effect: "+action);
				};
			}
		});
	}

	private static boolean matchesCraftingCandidate(Holder<Item> holder,ItemStack itemStack,ItemStack itemStack2) {
		return itemStack2.is(holder) && isUsableForCrafting(itemStack2)
			&& (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack,itemStack2));
	}
	private static boolean matchesCraftingSource(ItemStack itemStack,ItemStack itemStack2) {
		return itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack,itemStack2);
	}

	public int getSlotWithRemainingSpace(ItemStack itemStack) {
		if (this.nativeItems != null) return this.scanMain(2,itemStack,null);
		if (this.hasRemainingSpaceForItem(this.getItem(this.selectedValue()), itemStack)) {
			return this.selectedValue();
		} else if (this.hasRemainingSpaceForItem(this.getItem(40), itemStack)) {
			return 40;
		} else {
			for (int i = 0; i < this.items.size(); i++) {
				if (this.hasRemainingSpaceForItem(this.items.get(i), itemStack)) {
					return i;
				}
			}

			return -1;
		}
	}

	public void tick() {
		if (this.nativeItems != null) {
			this.nativeItems.visitInventory(null,2,36,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack itemStack) {
					Inventory.this.tickStack(index,itemStack);return 0;
				}
			});return;
		}
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack = this.getItem(i);
			if (!itemStack.isEmpty()) {
				itemStack.inventoryTick(this.player.level(), this.player, i == this.selectedValue() ? EquipmentSlot.MAINHAND : null);
			}
		}
	}

	public boolean add(ItemStack itemStack) {
		return this.add(-1, itemStack);
	}

	public boolean add(int i, ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			return false;
		} else {
			try {
				if (this.nativeItems != null) return this.nativeItems.addInventory(itemStack,i,new net.minecraft.world.item.NativeItemState.InventoryAddEffects() {
					@Override public long effect(long action,int index) {
						return switch((int)action) {
							case 1 -> itemStack.isDamaged()?1:0;
							case 2 -> Inventory.this.hasRemainingSpaceForItem(Inventory.this.getItem(index),itemStack)?1:0;
							case 11 -> nativeEquipmentMapping(index);
							case 3 -> Inventory.this.addResource(index,itemStack);
							case 4 -> { Inventory.this.items.set(index,itemStack.copyAndClear());Inventory.this.items.get(index).setPopTime(5);yield 1; }
							case 5 -> Inventory.this.infiniteMaterials()?1:0;
							default -> throw new IllegalStateException("Unknown inventory add effect: "+action);
						};
					}
					@Override public long prepared(long action,int index,long handle,long address,long adopted) {
						if(action==33)return Inventory.this.addResourcePrepared(index,itemStack,handle,address,adopted);
						// Original copyAndClear order: the items field, the copy, clearing the source, set, pop time.
						var items=Inventory.this.items;ItemStack copy;
						if(!itemStack.isEmpty()&&itemStack.getItem()!=null&&itemStack.getItem().nativeTransferCopyIsPure()){copy=itemStack.adoptPreparedCopy(handle,address,adopted);itemStack.setCount(0);}
						else copy=itemStack.copyAndClear();
						items.set(index,copy);Inventory.this.items.get(index).setPopTime(5);return 1;
					}
				});
				if (itemStack.isDamaged()) {
					if (i == -1) {
						i = this.getFreeSlot();
					}

					if (i >= 0) {
						this.items.set(i, itemStack.copyAndClear());
						this.items.get(i).setPopTime(5);
						return true;
					} else if (this.player.hasInfiniteMaterials()) {
						itemStack.setCount(0);
						return true;
					} else {
						return false;
					}
				} else {
					int j;
					do {
						j = itemStack.getCount();
						if (i == -1) {
							itemStack.setCount(this.addResource(itemStack));
						} else {
							itemStack.setCount(this.addResource(i, itemStack));
						}
					} while (!itemStack.isEmpty() && itemStack.getCount() < j);

					if (itemStack.getCount() == j && this.player.hasInfiniteMaterials()) {
						itemStack.setCount(0);
						return true;
					} else {
						return itemStack.getCount() < j;
					}
				}
			} catch (Throwable var6) {
				CrashReport crashReport = CrashReport.forThrowable(var6, "Adding item to inventory");
				CrashReportCategory crashReportCategory = crashReport.addCategory("Item being added");
				crashReportCategory.setDetail("Item ID", Item.getId(itemStack.getItem()));
				crashReportCategory.setDetail("Item data", itemStack.getDamageValue());
				crashReportCategory.setDetail("Item name", (CrashReportDetail<String>)(() -> itemStack.getHoverName().getString()));
				throw new ReportedException(crashReport);
			}
		}
	}

	public void placeItemBackInInventory(ItemStack itemStack) {
		this.placeItemBackInInventory(itemStack, true);
	}

	public void placeItemBackInInventory(ItemStack itemStack, boolean bl) {
		while (!itemStack.isEmpty()) {
			int i = this.getSlotWithRemainingSpace(itemStack);
			if (i == -1) {
				i = this.getFreeSlot();
			}

			if (i == -1) {
				this.player.drop(itemStack, false);
				break;
			}

			int j = itemStack.getMaxStackSize() - this.getItem(i).getCount();
			if (this.add(i, itemStack.split(j)) && bl && this.player instanceof ServerPlayer serverPlayer) {
				serverPlayer.connection.send(this.createInventoryUpdatePacket(i));
			}
		}
	}

	public ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int i) {
		return new ClientboundSetPlayerInventoryPacket(i, this.getItem(i).copy());
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		if (i < this.items.size()) {
			return ContainerHelper.removeItem(this.items, i, j);
		} else {
			EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
			if (equipmentSlot != null) {
				ItemStack itemStack = this.equipment.get(equipmentSlot);
				if (!itemStack.isEmpty()) {
					return itemStack.split(j);
				}
			}

			return ItemStack.EMPTY;
		}
	}

	public void removeItem(ItemStack itemStack) {
		if (this.nativeItems != null) {
			int index=this.scanMain(5,itemStack,null);
			if(index>=0){this.items.set(index,ItemStack.EMPTY);return;}
		} else for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i) == itemStack) {
				this.items.set(i, ItemStack.EMPTY);
				return;
			}
		}

		for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
			ItemStack itemStack2 = this.equipment.get(equipmentSlot);
			if (itemStack2 == itemStack) {
				this.equipment.set(equipmentSlot, ItemStack.EMPTY);
				return;
			}
		}
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		if (i < this.items.size()) {
			ItemStack itemStack = this.items.get(i);
			this.items.set(i, ItemStack.EMPTY);
			return itemStack;
		} else {
			EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
			return equipmentSlot != null ? this.equipment.set(equipmentSlot, ItemStack.EMPTY) : ItemStack.EMPTY;
		}
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		if (i < this.items.size()) {
			this.items.set(i, itemStack);
		}

		EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
		if (equipmentSlot != null) {
			this.equipment.set(equipmentSlot, itemStack);
		}
	}

	public void save(ValueOutput.TypedOutputList<ItemStackWithSlot> typedOutputList) {
		if (this.nativeItems != null) {
			this.nativeItems.visitInventory(null,0,36,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack itemStack) {saveStack(typedOutputList,index,itemStack);return 0;}
			});return;
		}
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack = this.items.get(i);
			if (!itemStack.isEmpty()) {
				typedOutputList.add(new ItemStackWithSlot(i, itemStack));
			}
		}
	}

	public void load(ValueInput.TypedInputList<ItemStackWithSlot> typedInputList) {
		this.items.clear();
		if (this.nativeItems != null && typedInputList != null) {
			this.nativeItems.visitInventory(null,8,0,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				private java.util.Iterator<ItemStackWithSlot> iterator;
				@Override public long effect(long action,int index,ItemStack ignored) {
					if(action==29){if(iterator==null)iterator=typedInputList.iterator();return iterator.hasNext()?1:0;}
					Inventory.this.loadEntry(iterator.next());return 0;
				}
			});return;
		}

		for (ItemStackWithSlot itemStackWithSlot : typedInputList) {
			if (itemStackWithSlot.isValidInContainer(this.items.size())) {
				this.setItem(itemStackWithSlot.slot(), itemStackWithSlot.stack());
			}
		}
	}

	private void loadEntry(ItemStackWithSlot itemStackWithSlot) {
		if(itemStackWithSlot.isValidInContainer(this.items.size()))this.setItem(itemStackWithSlot.slot(),itemStackWithSlot.stack());
	}

	@Override
	public int getContainerSize() {
		return this.items.size() + EQUIPMENT_SLOT_MAPPING.size();
	}

	@Override
	public boolean isEmpty() {
		if (this.nativeItems != null) {
			if (!this.nativeItems.mainEmpty()) return false;
		} else for (ItemStack itemStack : this.items) {
			if (!itemStack.isEmpty()) return false;
		}

		for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
			if (!this.equipment.get(equipmentSlot).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int i) {
		if (i < this.items.size()) {
			return this.items.get(i);
		} else {
			EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
			return equipmentSlot != null ? this.equipment.get(equipmentSlot) : ItemStack.EMPTY;
		}
	}

	@Override
	public Component getName() {
		return DEFAULT_NAME;
	}

	public void dropAll() {
		if (this.nativeItems != null) {
			this.nativeItems.visitInventory(null,1,36,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack itemStack) {Inventory.this.dropStack(index,itemStack);return 0;}
			});this.equipment.dropAll(this.player);return;
		}
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack = this.items.get(i);
			if (!itemStack.isEmpty()) {
				this.player.drop(itemStack, true, false);
				this.items.set(i, ItemStack.EMPTY);
			}
		}

		this.equipment.dropAll(this.player);
	}

	@Override
	public void setChanged() {
		if (this.nativeItems != null) this.nativeItems.markChanged(); else this.legacyState.timesChanged++;
	}

	public int getTimesChanged() {
		return this.nativeItems != null ? this.nativeItems.changes() : this.legacyState.timesChanged;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	public boolean contains(ItemStack itemStack) {
		if (this.nativeItems != null) {
			int size=this.getContainerSize();
			return this.nativeItems.visitInventory(itemStack,6,size,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack captured) {ItemStack itemStack2=captured==null?Inventory.this.getItem(index):captured;return !itemStack2.isEmpty()&&ItemStack.isSameItemSameComponents(itemStack2,itemStack)?1:0;}
			});
		}
		for (ItemStack itemStack2 : this) {
			if (!itemStack2.isEmpty() && ItemStack.isSameItemSameComponents(itemStack2, itemStack)) {
				return true;
			}
		}

		return false;
	}

	public boolean contains(TagKey<Item> tagKey) {
		if (this.nativeItems != null) {
			int size=this.getContainerSize();
			return this.nativeItems.visitInventory(null,5,size,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack captured) {ItemStack itemStack=captured==null?Inventory.this.getItem(index):captured;return !itemStack.isEmpty()&&itemStack.is(tagKey)?1:0;}
			});
		}
		for (ItemStack itemStack : this) {
			if (!itemStack.isEmpty() && itemStack.is(tagKey)) {
				return true;
			}
		}

		return false;
	}

	public boolean contains(Predicate<ItemStack> predicate) {
		if (this.nativeItems != null) {
			int size=this.getContainerSize();
			return this.nativeItems.visitInventory(null,4,size,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack captured) {return testStack(predicate,captured==null?Inventory.this.getItem(index):captured)?1:0;}
			});
		}
		for (ItemStack itemStack : this) {
			if (predicate.test(itemStack)) {
				return true;
			}
		}

		return false;
	}

	public void replaceWith(Inventory inventory) {
		if (this.nativeItems != null) {
			this.nativeItems.visitInventory(null,7,0,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack ignored) {if(action==27)return Inventory.this.getContainerSize();Inventory.this.replaceItem(inventory,index);return 0;}
			});this.setSelectedSlot(inventory.getSelectedSlot());return;
		}
		for (int i = 0; i < this.getContainerSize(); i++) {
			this.setItem(i, inventory.getItem(i));
		}

		this.setSelectedSlot(inventory.getSelectedSlot());
	}

	@Override
	public void clearContent() {
		this.items.clear();
		this.equipment.clear();
	}

	public void fillStackedContents(StackedItemContents stackedItemContents) {
		if (this.nativeItems != null) {
			this.nativeItems.visitInventory(null,3,36,new net.minecraft.world.item.NativeItemState.InventoryVisitEffects() {
				@Override public long effect(long action,int index,ItemStack itemStack) {fillStack(stackedItemContents,itemStack);return 0;}
			});return;
		}
		for (ItemStack itemStack : this.items) {
			stackedItemContents.accountSimpleStack(itemStack);
		}
	}

    // Keep effect argument order and the original parameter/field exception leaves.
    private boolean infiniteMaterials() { return this.player.hasInfiniteMaterials(); }
    private void tickStack(int i,ItemStack itemStack) { itemStack.inventoryTick(this.player.level(),this.player,i==this.selectedValue()?EquipmentSlot.MAINHAND:null); }
    private void dropStack(int i,ItemStack itemStack) { this.player.drop(itemStack,true,false);this.items.set(i,ItemStack.EMPTY); }
    private static void saveStack(ValueOutput.TypedOutputList<ItemStackWithSlot> typedOutputList,int i,ItemStack itemStack) { typedOutputList.add(new ItemStackWithSlot(i,itemStack)); }
    private static boolean testStack(Predicate<ItemStack> predicate,ItemStack itemStack) { return predicate.test(itemStack); }
    private static void fillStack(StackedItemContents stackedItemContents,ItemStack itemStack) { stackedItemContents.accountSimpleStack(itemStack); }
    private void replaceItem(Inventory inventory,int i) { this.setItem(i,inventory.getItem(i)); }

	public ItemStack removeFromSelected(boolean bl) {
		ItemStack itemStack = this.getSelectedItem();
		return itemStack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selectedValue(), bl ? itemStack.getCount() : 1);
	}
}
