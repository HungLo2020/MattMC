package net.minecraft.world.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public interface InventoryCarrier {
   String TAG_INVENTORY = "Inventory";

   SimpleContainer getInventory();

   static void pickUpItem(Mob pMob, InventoryCarrier pCarrier, ItemEntity pItemEntity) {
      ItemStack itemstack = pItemEntity.getItem();
      if (pMob.wantsToPickUp(itemstack)) {
         SimpleContainer simplecontainer = pCarrier.getInventory();
         boolean flag = simplecontainer.canAddItem(itemstack);
         if (!flag) {
            return;
         }

         pMob.onItemPickup(pItemEntity);
         int i = itemstack.getCount();
         ItemStack itemstack1 = simplecontainer.addItem(itemstack);
         pMob.take(pItemEntity, i - itemstack1.getCount());
         if (itemstack1.isEmpty()) {
            pItemEntity.discard();
         } else {
            itemstack.setCount(itemstack1.getCount());
         }
      }

   }

   default void readInventoryFromTag(CompoundTag p_253699_) {
      if (p_253699_.contains("Inventory", 9)) {
         this.getInventory().fromTag(p_253699_.getList("Inventory", 10));
      }

   }

   default void writeInventoryToTag(CompoundTag p_254428_) {
      p_254428_.put("Inventory", this.getInventory().createTag());
   }
}