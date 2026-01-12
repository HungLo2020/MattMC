package com.github.alexmodguy.alexscaves.server.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

// Stub - actual registration happens in Items.java
public class ACItemRegistry {
    public static class ItemHolder {
        private final Item item;
        public ItemHolder(Item item) { this.item = item; }
        public Item get() { return item; }
    }
    
    public static final ItemHolder COOKED_TRILOCARIS_TAIL = new ItemHolder(Items.COOKED_COD);
    public static final ItemHolder TRILOCARIS_TAIL = new ItemHolder(Items.COD);
}
