package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.item.equipment.trim.TrimMaterials;

/** Shared opt-in server equipment and capture options; no renderer state. */
public final class GraphicsAuditEquipmentFixture {
    private static final String PROPERTY = "mattmc.dev.graphicsAuditEquipment";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Item[] ITEMS = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
    private static final Item[] LEATHER_ITEMS = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
    static final int LEATHER_DYE_RGB = 0x3366CC;
    private static Double previousSpeed;
    private static Double previousStrength;

    private static String mode() {
        String mode = System.getProperty(PROPERTY, "");
        if (!mode.isEmpty() && !mode.equals("base") && !mode.equals("foil"))
            throw new IllegalArgumentException("equipment fixture requires base or foil");
        return mode;
    }
    static boolean requested() { return !mode().isEmpty(); }

    static String trimVariant() {
        String variant = System.getProperty("mattmc.dev.graphicsAuditEquipmentTrim", "");
        if (!variant.isEmpty() && !variant.equals("gold-spire") && !variant.equals("gold-spire-decal"))
            throw new IllegalArgumentException("equipment trim requires gold-spire or gold-spire-decal");
        if (!variant.isEmpty() && !requested())
            throw new IllegalArgumentException("equipment trim requires an equipment fixture");
        return variant;
    }

    static String materialVariant() {
        String variant = System.getProperty("mattmc.dev.graphicsAuditEquipmentMaterial", "");
        if (!variant.isEmpty() && !variant.equals("leather-dyed"))
            throw new IllegalArgumentException("equipment material requires leather-dyed");
        if (!variant.isEmpty() && !requested())
            throw new IllegalArgumentException("equipment material requires an equipment fixture");
        return variant;
    }

    static String ageVariant() {
        String variant = System.getProperty("mattmc.dev.graphicsAuditEquipmentAge", "");
        if (!variant.isEmpty() && !variant.equals("baby"))
            throw new IllegalArgumentException("equipment age requires baby");
        if (!variant.isEmpty() && !requested())
            throw new IllegalArgumentException("equipment age requires an equipment fixture");
        return variant;
    }

    static boolean ageMatches(boolean baby) {
        return baby == ageVariant().equals("baby");
    }

    static ItemStack materialStack(int slot) {
        boolean leather = materialVariant().equals("leather-dyed");
        ItemStack stack = new ItemStack((leather ? LEATHER_ITEMS : ITEMS)[slot]);
        if (leather) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(LEATHER_DYE_RGB));
        return stack;
    }

    static boolean materialMatches(ItemStack stack, int slot) {
        boolean leather = materialVariant().equals("leather-dyed");
        DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
        return stack.is((leather ? LEATHER_ITEMS : ITEMS)[slot])
            && (leather ? dye != null && dye.rgb() == LEATHER_DYE_RGB : dye == null);
    }

    /** Inline patterns are ordinary codec-supported item components, not renderer overrides. */
    static Holder<TrimPattern> fixturePattern(Holder<TrimPattern> registered, boolean decal) {
        if (!decal) return registered;
        TrimPattern source = registered.value();
        return Holder.direct(new TrimPattern(source.assetId(), source.description(), true));
    }

    static boolean trimMatches(ItemStack stack, String variant) {
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (variant.isEmpty()) return trim == null;
        return trim != null && trim.material().is(TrimMaterials.GOLD)
            && trim.pattern().value().assetId().equals(TrimPatterns.SPIRE.location())
            && trim.pattern().value().decal() == variant.equals("gold-spire-decal")
            && (variant.equals("gold-spire") ? trim.pattern().is(TrimPatterns.SPIRE) : trim.pattern().unwrapKey().isEmpty());
    }

    static void configureOptions(Minecraft minecraft) {
        trimVariant();
        materialVariant();
        ageVariant();
        if (!requested()) return;
        if (!"zombie".equals(System.getProperty("mattmc.dev.rustGalWorldMesh.modelScenario")))
            throw new IllegalStateException("equipment fixture requires the zombie scenario");
        if (previousSpeed == null) {
            previousSpeed = minecraft.options.glintSpeed().get();
            previousStrength = minecraft.options.glintStrength().get();
        }
        if(GraphicsAuditEquipmentFoilTiming.movingRequested() && (!mode().equals("foil") || !GraphicsAuditEquipmentFoilTiming.enabled()))
            throw new IllegalStateException("moving equipment requires enchanted fixture and observed clocks");
        // Equivalent legal options; no render clock override.
        minecraft.options.glintSpeed().set(GraphicsAuditEquipmentFoilTiming.movingRequested()?0.5:0.0);
        minecraft.options.glintStrength().set(0.5);
    }

    static void restore(Minecraft minecraft) {
        GraphicsAuditEquipmentTickFixture.restore(minecraft);
        if (previousSpeed != null) {
            minecraft.options.glintSpeed().set(previousSpeed);
            minecraft.options.glintStrength().set(previousStrength);
            previousSpeed = null;
            previousStrength = null;
        }
    }

    static void configure(Zombie zombie) {
        if (!requested()) return;
        if (ageVariant().equals("baby")) zombie.setBaby(true);
        GraphicsAuditEquipmentTickFixture.configured(zombie);
        var enchantment = zombie.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        String trimVariant = trimVariant();
        ArmorTrim trim = trimVariant.isEmpty() ? null : new ArmorTrim(
            zombie.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.GOLD),
            fixturePattern(zombie.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SPIRE),
                trimVariant.equals("gold-spire-decal")));
        for (int i=0; i<SLOTS.length; i++) {
            ItemStack stack = materialStack(i);
            if (mode().equals("foil")) stack.enchant(enchantment, 1);
            if (trim != null) stack.set(DataComponents.TRIM, trim);
            zombie.setItemSlot(SLOTS[i], stack);
        }
        zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        zombie.setYHeadRot(zombie.getYRot());
        zombie.setYBodyRot(zombie.getYRot());
    }

    static boolean ready(Entity entity) {
        if (!requested()) return true;
        if (!(entity instanceof Zombie zombie) || !ageMatches(zombie.isBaby())) return false;
        var enchantment = zombie.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        boolean foil = mode().equals("foil");
        for (int i=0; i<SLOTS.length; i++) {
            var stack = zombie.getItemBySlot(SLOTS[i]);
            if (!materialMatches(stack, i) || stack.getCount()!=1 || stack.hasFoil()!=foil
                || stack.getEnchantments().getLevel(enchantment)!=(foil?1:0)
                || !trimMatches(stack, trimVariant())) return false;
        }
        return zombie.getMainHandItem().isEmpty() && zombie.getOffhandItem().isEmpty();
    }

    static JsonObject receipt(Minecraft minecraft, Entity entity) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", GraphicsAuditEquipmentFoilTiming.movingRequested()?"equipped-zombie-moving-v1":"equipped-zombie-stopped-v1");
        result.addProperty("requested", requested());
        result.addProperty("mode", mode());
        if (!ageVariant().isEmpty()) result.addProperty("ageVariant", ageVariant());
        if (!materialVariant().isEmpty()) result.addProperty("materialVariant", materialVariant());
        if (!trimVariant().isEmpty()) result.addProperty("trimVariant", trimVariant());
        result.addProperty("ready", ready(entity));
        result.addProperty("speed", minecraft.options.glintSpeed().get());
        result.addProperty("strength", minecraft.options.glintStrength().get());
        result.addProperty("complete", requested() && ready(entity)
            && minecraft.options.glintSpeed().get()==(GraphicsAuditEquipmentFoilTiming.movingRequested()?0.5:0.0) && minecraft.options.glintStrength().get()==0.5);
        if (entity instanceof Zombie zombie) {
            if (!ageVariant().isEmpty()) result.addProperty("isBaby", zombie.isBaby());
            result.addProperty("x", zombie.getX()); result.addProperty("y", zombie.getY()); result.addProperty("z", zombie.getZ());
            result.addProperty("yaw", zombie.getYRot()); result.addProperty("bodyYaw", zombie.yBodyRot);
            result.addProperty("headYaw", zombie.getYHeadRot());
            JsonArray equipment = new JsonArray();
            for (int i=0; i<SLOTS.length; i++) {
                var stack = zombie.getItemBySlot(SLOTS[i]);
                JsonObject slot = new JsonObject();
                slot.addProperty("slot", SLOTS[i].getName());
                slot.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                slot.addProperty("count", stack.getCount()); slot.addProperty("foil", stack.hasFoil());
                if (!materialVariant().isEmpty()) {
                    DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
                    if (dye != null) slot.addProperty("dyedColor", dye.rgb());
                }
                if (!trimVariant().isEmpty()) {
                    ArmorTrim trim = stack.get(DataComponents.TRIM);
                    if (trim != null) {
                        JsonObject observed = new JsonObject();
                        observed.addProperty("material", trim.material().unwrapKey().map(k -> k.location().toString()).orElse("inline"));
                        observed.addProperty("patternAsset", trim.pattern().value().assetId().toString());
                        observed.addProperty("decal", trim.pattern().value().decal());
                        observed.addProperty("patternKind", trim.pattern().unwrapKey().isPresent() ? "registry" : "inline");
                        slot.add("trim", observed);
                    }
                }
                equipment.add(slot);
            }
            result.add("equipment", equipment);
        }
        return result;
    }
    private GraphicsAuditEquipmentFixture() {}
}
