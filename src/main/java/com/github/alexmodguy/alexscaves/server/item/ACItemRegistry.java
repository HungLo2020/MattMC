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
    
    // Using vanilla items as placeholders for now
    public static final ItemHolder COOKED_TRILOCARIS_TAIL = new ItemHolder(Items.COOKED_COD);
    public static final ItemHolder TRILOCARIS_TAIL = new ItemHolder(Items.COD);
    public static final ItemHolder DINOSAUR_CHOP = new ItemHolder(Items.COOKED_MUTTON);
    public static final ItemHolder SUBTERRANODON_SPAWN_EGG = new ItemHolder(Items.PARROT_SPAWN_EGG); // Will be replaced
    public static final ItemHolder DINOSAUR_TRANSFORMATION_AMBER = new ItemHolder(Items.AMETHYST_SHARD);
    public static final ItemHolder DINOSAUR_TRANSFORMATION_TECTONIC = new ItemHolder(Items.PRISMARINE_SHARD);
    public static final ItemHolder AMBER_CURIOSITY = new ItemHolder(Items.AMETHYST_SHARD);
    public static final ItemHolder TECTONIC_SHARD = new ItemHolder(Items.PRISMARINE_SHARD);
    public static final ItemHolder FERN_THATCH = new ItemHolder(Items.HAY_BLOCK);
}
