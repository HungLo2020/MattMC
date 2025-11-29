package net.matt.quantize.item.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.matt.quantize.utils.ItemNBTHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrowelItem extends Item {

    private static final String TAG_PLACING_SEED = "placing_seed";
    private static final String TAG_LAST_STACK = "last_stack";

    public TrowelItem(Properties properties) {
        super(properties.durability(255));
        //CreativeModeTabs.TOOLS_AND_UTILITIES.add(this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        List<Integer> targets = new ArrayList<>();
        Inventory inventory = player.getInventory();

        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isValidTarget(stack, context)) {
                targets.add(i);
            }
        }

        if (targets.isEmpty()) return InteractionResult.PASS;

        ItemStack trowel = player.getItemInHand(hand);

        long seed = ItemNBTHelper.getLong(trowel, TAG_PLACING_SEED, 0);
        Random rand = new Random(seed);
        ItemNBTHelper.setLong(trowel, TAG_PLACING_SEED, rand.nextLong());

        int targetSlot = targets.get(rand.nextInt(targets.size()));
        ItemStack toPlaceStack = inventory.getItem(targetSlot);

        player.setItemInHand(hand, toPlaceStack);
        InteractionResult result = toPlaceStack.useOn(new TrowelBlockItemUseContext(context, toPlaceStack));

        ItemStack newHandItem = player.getItemInHand(hand);
        player.setItemInHand(hand, trowel);
        inventory.setItem(targetSlot, newHandItem);

        if (result.consumesAction()) {
            CompoundTag cmp = toPlaceStack.serializeNBT();
            ItemNBTHelper.setCompound(trowel, TAG_LAST_STACK, cmp);
            trowel.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
        }

        return result;
    }

    private static boolean isValidTarget(ItemStack stack, UseOnContext context) {
        Item item = stack.getItem();
        return !stack.isEmpty() && (item instanceof BlockItem);
    }

    public static ItemStack getLastStack(ItemStack stack) {
        CompoundTag cmp = ItemNBTHelper.getCompound(stack, TAG_LAST_STACK, false);
        return ItemStack.of(cmp);
    }

    class TrowelBlockItemUseContext extends BlockPlaceContext {
        public TrowelBlockItemUseContext(UseOnContext context, ItemStack stack) {
            super(context.getLevel(), context.getPlayer(), context.getHand(), stack,
                    new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
        }
    }
}