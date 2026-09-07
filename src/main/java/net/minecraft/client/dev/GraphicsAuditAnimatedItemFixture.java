package net.minecraft.client.dev;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Shared copied-run inventory input and observed-state receipt; no renderer access. */
public final class GraphicsAuditAnimatedItemFixture {
    public static List<ItemStack> items() {
        return List.of(new ItemStack(Blocks.MAGMA_BLOCK), ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    public static String receipt(Minecraft minecraft) {
        var player = minecraft.player;
        var stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        int selected = player == null ? 0 : player.getInventory().getSelectedSlot() + 1;
        boolean complete = selected == 1 && stack.is(Blocks.MAGMA_BLOCK.asItem()) && stack.getCount() == 1;
        return "{\"fixture\":\"held-magma-v1\",\"selectedSlot\":" + selected
            + ",\"mainHand\":\"" + BuiltInRegistries.ITEM.getKey(stack.getItem())
            + "\",\"count\":" + stack.getCount() + ",\"complete\":" + complete + "}";
    }

    private GraphicsAuditAnimatedItemFixture() {}
}
