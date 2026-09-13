package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.item.enchantment.Enchantments;

/** Ordinary server inventory fixture and replicated observations; no renderer state. */
public final class GraphicsAuditInventoryEquipmentFixture {
    private static final String PROPERTY = "mattmc.dev.graphicsAuditInventoryEquipment";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    // Reading the diagnostic mode must not initialize game registries.
    private static final class EquipmentItems {
        private static final Item[] ITEMS = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
    }
    private static CompletableFuture<Applied> applied;
    private static Double oldSpeed, oldStrength;
    private static boolean inventoryOpened;
    private static double previousMouseX, previousMouseY;
    private record Applied(ServerPlayer player, EnumMap<EquipmentSlot, ItemStack> equipment, List<ItemStack> hotbar) {}

    static String mode() {
        String value = System.getProperty(PROPERTY, "");
		if (!value.isEmpty() && !value.equals("empty") && !value.equals("base") && !value.equals("foil") && !value.equals("foil-moving") && !value.equals("trim") && !value.equals("trim-decal"))
			throw new IllegalArgumentException("inventory equipment requires empty, base, foil, foil-moving, trim, or trim-decal");
        return value;
    }
	static boolean trimRequested() { return mode().equals("trim") || mode().equals("trim-decal"); }
	static boolean foilRequested() { return mode().equals("foil") || mode().equals("foil-moving"); }
	static boolean movingFoilRequested() { return mode().equals("foil-moving"); }

    static ItemStack stack(int slot) {
        ItemStack stack = new ItemStack(EquipmentItems.ITEMS[slot]);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
        return stack;
    }

    static boolean matchesStack(ItemStack stack, int slot, boolean foil) {
		return matchesStack(stack, slot, foil ? "foil" : "base");
	}

	static boolean matchesStack(ItemStack stack, int slot, String expectedMode) {
        var dye = stack.get(DataComponents.DYED_COLOR);
		var trim = stack.get(DataComponents.TRIM);
        return stack.is(EquipmentItems.ITEMS[slot]) && stack.getCount() == 1 && !stack.isDamaged()
			&& dye != null && dye.rgb() == 0x3366CC && stack.hasFoil() == (expectedMode.equals("foil") || expectedMode.equals("foil-moving"))
			&& !stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
			&& (expectedMode.equals("trim") || expectedMode.equals("trim-decal") ? trim != null
				&& trim.material().is(TrimMaterials.GOLD)
				&& trim.pattern().value().assetId().equals(TrimPatterns.SPIRE.location())
				&& trim.pattern().value().decal() == expectedMode.equals("trim-decal")
				&& (expectedMode.equals("trim") ? trim.pattern().is(TrimPatterns.SPIRE) : trim.pattern().unwrapKey().isEmpty())
				: trim == null);
    }

	private static boolean matches(Player player) {
		if (player == null) return false;
		for (int i=0; i<SLOTS.length; i++) {
			ItemStack equipped = player.getItemBySlot(SLOTS[i]);
			if (mode().equals("empty") ? !equipped.isEmpty() : !matchesStack(equipped, i, mode())) return false;
		}
        for (int i=0; i<9; i++) if (!player.getInventory().getItem(i).isEmpty()) return false;
        return player.getOffhandItem().isEmpty();
    }

    static boolean prepare(Minecraft minecraft) {
        String mode = mode();
        if (mode.isEmpty()) return true;
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) return false;
        if (oldSpeed == null) {
            oldSpeed = minecraft.options.glintSpeed().get();
            oldStrength = minecraft.options.glintStrength().get();
        }
        minecraft.options.glintSpeed().set(movingFoilRequested() ? 0.5 : 0.0);
        minecraft.options.glintStrength().set(0.5);
        if (applied == null) {
            var server = minecraft.getSingleplayerServer();
            var uuid = minecraft.player.getUUID();
            applied = server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) throw new IllegalStateException("inventory fixture player disappeared");
                var previous = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
                var hotbar = new ArrayList<ItemStack>();
                for (EquipmentSlot slot : SLOTS) previous.put(slot, player.getItemBySlot(slot).copy());
                previous.put(EquipmentSlot.OFFHAND, player.getOffhandItem().copy());
                for (int i=0; i<9; i++) hotbar.add(player.getInventory().getItem(i).copy());
				for (int i=0; i<SLOTS.length; i++) {
					ItemStack stack = mode.equals("empty") ? ItemStack.EMPTY : stack(i);
					if (!stack.isEmpty() && foilRequested()) stack.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(Enchantments.UNBREAKING), 1);
					if (!stack.isEmpty() && trimRequested()) stack.set(DataComponents.TRIM, new ArmorTrim(
						player.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.GOLD),
						GraphicsAuditEquipmentFixture.fixturePattern(
							player.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SPIRE),
							mode.equals("trim-decal"))));
					player.setItemSlot(SLOTS[i], stack);
                }
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                for (int i=0; i<9; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                return new Applied(player, previous, List.copyOf(hotbar));
            });
        }
        if (!applied.isDone()) return false;
        applied.join();
        if (!matches(minecraft.player)) return false;
        if (!inventoryOpened) {
            if (minecraft.screen != null) return false;
            previousMouseX=minecraft.mouseHandler.xpos();
            previousMouseY=minecraft.mouseHandler.ypos();
            GraphicsAuditInventoryPreviewInputs.resetReadiness();
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            // Deliver an ordinary cursor movement even when the physical cursor
            // was already centered but the handler has not observed it yet.
            var window=minecraft.getWindow();
            net.blaze3d.platform.InputConstants.grabOrReleaseMouse(window,212993,
                window.getScreenWidth()/4.0,window.getScreenHeight()/4.0);
            inventoryOpened = true;
            return false; // The ordinary screen must render before its frame is captured.
        }
        if (minecraft.screen instanceof InventoryScreen && !mouseCentered(minecraft)) {
            var window=minecraft.getWindow();
            net.blaze3d.platform.InputConstants.grabOrReleaseMouse(window,212993,
                window.getScreenWidth()/2.0,window.getScreenHeight()/2.0);
            return false;
        }
        return minecraft.screen instanceof InventoryScreen && mouseCentered(minecraft)
            && (!GraphicsAuditEquipmentFoilTiming.enabled() || GraphicsAuditInventoryPreviewInputs.ready());
    }

    private static boolean mouseCentered(Minecraft minecraft) {
        var window=minecraft.getWindow();
        return Math.abs(minecraft.mouseHandler.xpos()-window.getScreenWidth()/2.0)<0.5
            && Math.abs(minecraft.mouseHandler.ypos()-window.getScreenHeight()/2.0)<0.5;
    }

    static JsonObject receipt(Minecraft minecraft) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "inventory-equipment-fixture-v1");
        result.addProperty("requested", !mode().isEmpty());
        result.addProperty("mode", mode());
        boolean serverApplied = applied != null && applied.isDone() && !applied.isCompletedExceptionally();
        result.addProperty("serverApplied", serverApplied);
        result.addProperty("mouseCentered",mouseCentered(minecraft));
        JsonArray mouse=new JsonArray();mouse.add(minecraft.mouseHandler.xpos());mouse.add(minecraft.mouseHandler.ypos());
        result.add("mousePosition",mouse);
        result.addProperty("inventoryOpen", inventoryOpened && minecraft.screen instanceof InventoryScreen);
        result.addProperty("replicated", !mode().isEmpty() && matches(minecraft.player));
        result.addProperty("speed", minecraft.options.glintSpeed().get());
        result.addProperty("strength", minecraft.options.glintStrength().get());
        result.addProperty("complete", !mode().isEmpty() && serverApplied && mouseCentered(minecraft) && matches(minecraft.player)
            && inventoryOpened && minecraft.screen instanceof InventoryScreen
            && minecraft.options.glintSpeed().get() == (movingFoilRequested() ? 0.5 : 0.0)
            && minecraft.options.glintStrength().get() == 0.5);
        JsonArray equipment = new JsonArray();
        if (minecraft.player != null) for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            JsonObject row = new JsonObject();
            row.addProperty("slot", slot.getName());
            row.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            row.addProperty("count", stack.getCount());
            row.addProperty("foil", stack.hasFoil());
            var dye = stack.get(DataComponents.DYED_COLOR);
            if (dye != null) row.addProperty("dyedColor", dye.rgb());
			var trim = stack.get(DataComponents.TRIM);
			row.addProperty("trim", trim != null);
			if (trim != null) {
				row.addProperty("trimMaterial", trim.material().unwrapKey().orElseThrow().location().toString());
				row.addProperty("trimPattern", trim.pattern().value().assetId().toString());
				row.addProperty("trimDecal", trim.pattern().value().decal());
				row.addProperty("trimRegistered", trim.pattern().unwrapKey().isPresent());
			}
            equipment.add(row);
        }
        result.add("equipment", equipment);
        return result;
    }

    static void restore(Minecraft minecraft) {
        if (inventoryOpened && minecraft.screen instanceof InventoryScreen) {
            net.blaze3d.platform.InputConstants.grabOrReleaseMouse(minecraft.getWindow(),212993,previousMouseX,previousMouseY);
            minecraft.setScreen(null);
        }
        inventoryOpened = false;
        var server = minecraft.getSingleplayerServer();
        if (applied != null && server != null) applied.thenAccept(saved -> server.execute(() -> {
            saved.equipment.forEach(saved.player::setItemSlot);
            for (int i=0; i<9; i++) saved.player.getInventory().setItem(i, saved.hotbar.get(i));
            saved.player.inventoryMenu.broadcastChanges();
        }));
        applied = null;
        if (oldSpeed != null) {
            minecraft.options.glintSpeed().set(oldSpeed);
            minecraft.options.glintStrength().set(oldStrength);
        }
        oldSpeed = oldStrength = null;
    }

    private GraphicsAuditInventoryEquipmentFixture() {}
}
