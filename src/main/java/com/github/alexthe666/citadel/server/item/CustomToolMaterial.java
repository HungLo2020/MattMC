package com.github.alexthe666.citadel.server.item;

import net.minecraft.tags.TagKey;
// TODO: Tier is a NeoForge interface - needs replacement or removal
// import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

// TODO: This class implements NeoForge's Tier interface which doesn't exist in vanilla
// For now, it's a standalone class. May need to be refactored when tool customization is needed.
public class CustomToolMaterial {
   private String name;
   private int harvestLevel;
   private int durability;
   private float damage;
   private float speed;
   private int enchantability;
    private Ingredient ingredient = null;
    private final TagKey<Block> incorrectDrops;

    public CustomToolMaterial(String name, int harvestLevel, int durability, float damage, float speed, int enchantability, TagKey<Block> incorrectDrops) {
        this.name = name;
        this.harvestLevel = harvestLevel;
        this.durability = durability;
        this.damage = damage;
        this.speed = speed;
        this.enchantability = enchantability;
        this.incorrectDrops = incorrectDrops;
    }

    public String getName() {
        return name;
    }

    public int getUses() {
        return durability;
    }

    public float getSpeed() {
        return speed;
    }

    public float getAttackDamageBonus() {
        return damage;
    }

    public TagKey<Block> getIncorrectBlocksForDrops() {
        return incorrectDrops;
    }

    public int getLevel() {
        return harvestLevel;
    }

    public int getEnchantmentValue() {
        return enchantability;
    }

    public Ingredient getRepairIngredient() {
        return ingredient == null ? Ingredient.EMPTY : ingredient;
    }

    public void setRepairMaterial(Ingredient ingredient){
        this.ingredient = ingredient;
    }
}
