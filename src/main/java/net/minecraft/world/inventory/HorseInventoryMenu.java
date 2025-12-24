package net.minecraft.world.inventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class HorseInventoryMenu extends AbstractContainerMenu {
	private static final ResourceLocation SADDLE_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot/saddle");
	private static final ResourceLocation LLAMA_ARMOR_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot/llama_armor");
	private static final ResourceLocation ARMOR_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot/horse_armor");
	private final Container horseContainer;
	private final AbstractHorse horse;
	private static final int SLOT_SADDLE = 0;
	private static final int SLOT_BODY_ARMOR = 1;
	private static final int SLOT_HORSE_INVENTORY_START = 2;

	public HorseInventoryMenu(int i, Inventory inventory, Container container, AbstractHorse abstractHorse, int j) {
		super(null, i);
		this.horseContainer = container;
		this.horse = abstractHorse;
		container.startOpen(inventory.player);
		Container container2 = abstractHorse.createEquipmentSlotContainer(EquipmentSlot.SADDLE);
		this.addSlot(new ArmorSlot(container2, abstractHorse, EquipmentSlot.SADDLE, 0, 8, 18, SADDLE_SLOT_SPRITE) {
			@Override
			public boolean isActive() {
				return abstractHorse.canUseSlot(EquipmentSlot.SADDLE) && abstractHorse.getType().is(EntityTypeTags.CAN_EQUIP_SADDLE);
			}
		});
		final boolean bl = abstractHorse instanceof Llama;
		ResourceLocation resourceLocation = bl ? LLAMA_ARMOR_SLOT_SPRITE : ARMOR_SLOT_SPRITE;
		Container container3 = abstractHorse.createEquipmentSlotContainer(EquipmentSlot.BODY);
		this.addSlot(new ArmorSlot(container3, abstractHorse, EquipmentSlot.BODY, 0, 8, 36, resourceLocation) {
			@Override
			public boolean isActive() {
				return abstractHorse.canUseSlot(EquipmentSlot.BODY) && (abstractHorse.getType().is(EntityTypeTags.CAN_WEAR_HORSE_ARMOR) || bl);
			}
		});
		if (j > 0) {
			for (int k = 0; k < 3; k++) {
				for (int l = 0; l < j; l++) {
					this.addSlot(new Slot(container, l + k * j, 80 + l * 18, 18 + k * 18));
				}
			}
		}

		this.addStandardInventorySlots(inventory, 8, 84);
	}

	@Override
	public boolean stillValid(Player player) {
		return !this.horse.hasInventoryChanged(this.horseContainer)
			&& this.horseContainer.stillValid(player)
			&& this.horse.isAlive()
			&& player.canInteractWithEntity(this.horse, 4.0);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int i) {
		ItemStack itemStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(i);
		if (slot != null && slot.hasItem()) {
			ItemStack itemStack2 = slot.getItem();
			itemStack = itemStack2.copy();
			int j = 2 + this.horseContainer.getContainerSize();
			if (i < j) {
				if (!this.moveItemStackTo(itemStack2, j, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(1).mayPlace(itemStack2) && !this.getSlot(1).hasItem()) {
				if (!this.moveItemStackTo(itemStack2, 1, 2, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(0).mayPlace(itemStack2) && !this.getSlot(0).hasItem()) {
				if (!this.moveItemStackTo(itemStack2, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.horseContainer.getContainerSize() == 0 || !this.moveItemStackTo(itemStack2, 2, j, false)) {
				int k = j + 27;
				int m = k + 9;
				if (i >= k && i < m) {
					if (!this.moveItemStackTo(itemStack2, j, k, false)) {
						return ItemStack.EMPTY;
					}
				} else if (i >= j && i < k) {
					if (!this.moveItemStackTo(itemStack2, k, m, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(itemStack2, k, k, false)) {
					return ItemStack.EMPTY;
				}

				return ItemStack.EMPTY;
			}

			if (itemStack2.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return itemStack;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.horseContainer.stopOpen(player);
	}
}
