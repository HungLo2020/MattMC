package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opt-in gameplay state for matched shield captures; no renderer or animation state. */
public final class GraphicsAuditShieldPoseFixture {
    private static LocalPlayer owner;
    private static boolean originalUseKey;
    private static ItemStack originalOffHand;
    private GraphicsAuditShieldPoseFixture() {}
    static boolean blockingPose(String pose) {
        return switch (pose) {
            case "idle" -> false;
            case "blocking", "blocking-offhand" -> true;
            default -> throw new IllegalArgumentException("Unsupported shield capture pose: " + pose);
        };
    }
    public static String poseName() {
        String pose = System.getProperty("mattmc.dev.graphicsAuditShieldPose", "idle");
        blockingPose(pose); // Reject unknown authored fixtures before applying gameplay state.
        return pose;
    }
    static InteractionHand selectedUseHand(String pose) {
        blockingPose(pose);
        return "blocking-offhand".equals(pose) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
    public static boolean blockingRequested() {
        return blockingPose(poseName());
    }
    public static String observedUseHand(LocalPlayer player) {
        return player != null && player.isUsingItem() ? player.getUsedItemHand().name() : "NONE";
    }
    public static ItemStack fixtureStack(ItemStack existing, ItemStack replacement) {
        return blockingRequested() && ItemStack.matches(existing, replacement) ? existing : replacement;
    }
    public static void apply(Minecraft minecraft) {
        if (!blockingRequested() || minecraft.player == null) return;
        var player = minecraft.player;
        if (!player.getMainHandItem().is(Items.SHIELD))
            throw new IllegalStateException("Blocking capture requires a selected shield");
        if (owner != player) {
            restore(minecraft);
            owner = player;
            originalUseKey = minecraft.options.keyUse.isDown();
        }
        InteractionHand hand = selectedUseHand(poseName());
        if (hand == InteractionHand.OFF_HAND) {
            if (originalOffHand == null) originalOffHand = player.getOffhandItem();
            // Author a second shield through gameplay inventory state. Keep the
            // selected hotbar shield as the GUI/control fixture and preserve
            // exact off-hand stack identity throughout the normal use action.
            if (!ItemStack.matches(player.getOffhandItem(), player.getMainHandItem()))
                player.setItemInHand(hand, player.getMainHandItem().copy());
        }
        minecraft.options.keyUse.setDown(true);
        if (!player.isUsingItem() || player.getUsedItemHand() != hand
                || player.getUseItem() != player.getItemInHand(hand)) {
            player.stopUsingItem();
            player.startUsingItem(hand);
        }
    }
    public static void restore(Minecraft minecraft) {
        if (owner == null) return;
        owner.stopUsingItem();
        if (originalOffHand != null) owner.setItemInHand(InteractionHand.OFF_HAND, originalOffHand);
        originalOffHand = null;
        minecraft.options.keyUse.setDown(originalUseKey);
        owner = null;
    }
    public static boolean matchesPose(LocalPlayer player) {
        if (player == null) return false;
        InteractionHand hand = selectedUseHand(poseName());
        return blockingRequested()
            ? player.isUsingItem() && player.getUsedItemHand() == hand
                && player.getUseItem() == player.getItemInHand(hand) && player.getUseItemRemainingTicks() > 0
                && (hand != InteractionHand.OFF_HAND || ItemStack.matches(player.getOffhandItem(), player.getMainHandItem()))
            : !player.isUsingItem();
    }
}
