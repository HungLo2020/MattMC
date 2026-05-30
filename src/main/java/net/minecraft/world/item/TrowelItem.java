package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Trowel item. When right-clicking a block face the trowel randomly selects
 * a block-type item from the player's hotbar and places it as if the player had
 * that block in hand.  The trowel itself does not stack and has no durability.
 */
public class TrowelItem extends Item {

    public TrowelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // Let the client return success immediately to suppress default block interaction
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Collect all BlockItem stacks present in the player's hotbar (slots 0–8)
        Inventory inventory = player.getInventory();
        List<ItemStack> hotbarBlocks = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                hotbarBlocks.add(stack);
            }
        }

        if (hotbarBlocks.isEmpty()) {
            return InteractionResult.PASS;
        }

        // Pick a random block stack from the hotbar
        ItemStack chosenStack = hotbarBlocks.get(level.random.nextInt(hotbarBlocks.size()));
        BlockItem blockItem = (BlockItem) chosenStack.getItem();

        // Reconstruct the BlockHitResult from the available context data
        BlockHitResult hitResult = new BlockHitResult(
            context.getClickLocation(),
            context.getClickedFace(),
            context.getClickedPos(),
            context.isInside()
        );

        // Build a placement context using the chosen block's stack
        BlockPlaceContext placeContext = new BlockPlaceContext(
            level,
            player,
            context.getHand(),
            chosenStack,
            hitResult
        );

        return blockItem.place(placeContext);
    }
}
