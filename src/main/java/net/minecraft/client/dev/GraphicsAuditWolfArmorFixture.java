package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ordinary copied-world body equipment; never changes renderer inputs or clocks. */
final class GraphicsAuditWolfArmorFixture {
    static String mode() { return System.getProperty("mattmc.dev.graphicsAuditWolfArmor", ""); }
    static boolean requested() { return !mode().isEmpty(); }
    static Crackiness.Level expectedCrackiness(String mode) {
        return switch (mode) {
            case "none" -> Crackiness.Level.NONE;
            case "low" -> Crackiness.Level.LOW;
            case "medium" -> Crackiness.Level.MEDIUM;
            case "high" -> Crackiness.Level.HIGH;
            default -> throw new IllegalArgumentException("wolf armor requires none, low, medium or high");
        };
    }
    static void validateRequest() {
        if (!requested()) return;
        expectedCrackiness(mode());
        if (!"wolf".equals(System.getProperty("mattmc.dev.rustGalWorldMesh.modelScenario")))
            throw new IllegalStateException("wolf armor fixture requires the wolf scenario");
    }
    static ItemStack armor(String mode) {
        expectedCrackiness(mode);
        ItemStack stack = new ItemStack(Items.WOLF_ARMOR);
        int percent = switch (mode) { case "none" -> 0; case "low" -> 10; case "medium" -> 50; default -> 80; };
        stack.setDamageValue(stack.getMaxDamage() * percent / 100);
        if (Crackiness.WOLF_ARMOR.byDamage(stack) != expectedCrackiness(mode))
            throw new IllegalStateException("ordinary wolf armor damage did not select requested crackiness");
        return stack;
    }
    static void configure(Wolf wolf) {
        if (!requested()) return;
        validateRequest();
        GraphicsAuditWolfTickFixture.configured(wolf);
        wolf.setBodyArmorItem(armor(mode()));
        wolf.setYHeadRot(wolf.getYRot());
        wolf.setYBodyRot(wolf.getYRot());
    }
    static boolean armorMatches(ItemStack stack, String mode) {
        ItemStack expected = armor(mode);
        return stack.is(Items.WOLF_ARMOR) && stack.getCount() == 1
            && stack.getDamageValue() == expected.getDamageValue()
            && stack.getMaxDamage() == expected.getMaxDamage()
            && !stack.hasFoil() && stack.get(DataComponents.DYED_COLOR) == null
            && Crackiness.WOLF_ARMOR.byDamage(stack) == expectedCrackiness(mode);
    }
    static boolean ready(Entity entity) {
        if (!requested()) return true;
        return entity instanceof Wolf wolf && !wolf.isBaby() && !wolf.isTame()
            && wolf.get(DataComponents.WOLF_VARIANT) != null
            && wolf.get(DataComponents.WOLF_VARIANT).is(WolfVariants.DEFAULT)
            && armorMatches(wolf.getBodyArmorItem(), mode());
    }
    static JsonObject receipt(Entity entity) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "ordinary-wolf-armor-v1");
        result.addProperty("requested", requested());
        if (!requested()) return result;
        result.addProperty("mode", mode());
        result.addProperty("ready", ready(entity));
        if (entity instanceof Wolf wolf) {
            ItemStack stack = wolf.getBodyArmorItem();
            result.addProperty("item", stack.is(Items.WOLF_ARMOR) ? "minecraft:wolf_armor" : "unexpected");
            result.addProperty("count", stack.getCount());
            result.addProperty("damage", stack.getDamageValue());
            result.addProperty("maxDamage", stack.getMaxDamage());
            result.addProperty("crackiness", Crackiness.WOLF_ARMOR.byDamage(stack).name());
            result.addProperty("foil", stack.hasFoil());
            result.addProperty("baby", wolf.isBaby());
            result.addProperty("tame", wolf.isTame());
            result.addProperty("x", wolf.getX());result.addProperty("y", wolf.getY());result.addProperty("z", wolf.getZ());
            result.addProperty("yaw", wolf.getYRot());result.addProperty("headYaw", wolf.getYHeadRot());
            result.addProperty("bodyYaw", wolf.yBodyRot);
            var variant = wolf.get(DataComponents.WOLF_VARIANT);
            result.addProperty("variant", variant == null ? "missing" : variant.unwrapKey().map(k -> k.location().toString()).orElse("inline"));
        }
        return result;
    }
}
