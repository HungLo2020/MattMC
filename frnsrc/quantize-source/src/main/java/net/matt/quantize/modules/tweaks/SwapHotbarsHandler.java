package net.matt.quantize.modules.tweaks;

import net.matt.quantize.keys.ModKeyBindings;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;

public class SwapHotbarsHandler {

    private static int currentRow = 0; // Tracks the current row to swap

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(SwapHotbarsHandler::onKeyInput);
    }

    private static void onKeyInput(InputEvent.Key event) {
        if (ModKeyBindings.SWAP_HOTBARS_KEY.isDown()) {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                swapHotbarWithRow(player);
            }
        }
    }

    private static void swapHotbarWithRow(Player player) {
        // Get the player's inventory
        var inventory = player.getInventory();

        // Define the number of inventory rows and slots per row
        final int slotsPerRow = 9; // Number of slots per row

        // Calculate the start index of the current inventory row
        int rowStartIndex = (currentRow * slotsPerRow) + slotsPerRow; // Offset by hotbar (first row)

        // Swap the current inventory row with the hotbar
        for (int i = 0; i < slotsPerRow; i++) {
            int hotbarIndex = i; // Hotbar slots are 0-8
            int inventoryIndex = rowStartIndex + i;

            // Swap items between the hotbar and the current inventory row
            var temp = inventory.getItem(hotbarIndex);
            inventory.setItem(hotbarIndex, inventory.getItem(inventoryIndex));
            inventory.setItem(inventoryIndex, temp);
        }

        // Move to the next row, looping back to the first row after the third
        currentRow = (currentRow + 1) % 3;
    }
}