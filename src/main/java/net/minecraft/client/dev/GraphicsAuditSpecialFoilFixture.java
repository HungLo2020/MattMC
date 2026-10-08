package net.minecraft.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Capture-only inventory data and observation. No renderer or GPU access. */
public final class GraphicsAuditSpecialFoilFixture {
    private GraphicsAuditSpecialFoilFixture() {}
    private static Player recoveryOwner;
    private static Optional<GlobalPos> originalLastDeathLocation = Optional.empty();
    // Shared gameplay data, independent of camera/model/renderer clocks.
    public static final GlobalPos RECOVERY_TARGET = GlobalPos.of(Level.OVERWORLD, new BlockPos(166, 100, 530));

    public static void applyRecoveryTarget(Player player) {
        if (recoveryOwner != player) {
            restoreRecoveryTarget();
            recoveryOwner = player;
            originalLastDeathLocation = player.getLastDeathLocation();
        }
        player.setLastDeathLocation(Optional.of(RECOVERY_TARGET));
    }

    public static void restoreRecoveryTarget() {
        if (recoveryOwner != null) recoveryOwner.setLastDeathLocation(originalLastDeathLocation);
        recoveryOwner = null;
        originalLastDeathLocation = Optional.empty();
    }

    public static List<ItemStack> items() { return items(false); }
    public static List<ItemStack> items(boolean recovery) {
        List<ItemStack> result=new ArrayList<>();
        result.add(new ItemStack(Items.APPLE));
        for(int slot=1;slot<9;slot++) {
            ItemStack stack=new ItemStack(slot%2==1 ? Items.CLOCK : recovery ? Items.RECOVERY_COMPASS : Items.COMPASS);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
            result.add(stack);
        }
        return List.copyOf(result);
    }
    public static String receipt(Minecraft minecraft) { return receipt(minecraft, false); }
    public static String receipt(Minecraft minecraft, boolean recovery) {
        if(minecraft.player==null) return "{\"complete\":false}";
        List<String> observed=new ArrayList<>();
        boolean complete=!recovery || (minecraft.player.level().dimension() == RECOVERY_TARGET.dimension()
            && minecraft.player.getLastDeathLocation().equals(Optional.of(RECOVERY_TARGET)));
        for(int slot=0;slot<9;slot++) {
            var stack=minecraft.player.getInventory().getItem(slot);
            var expected=slot==0 ? Items.APPLE : slot%2==1 ? Items.CLOCK : recovery ? Items.RECOVERY_COMPASS : Items.COMPASS;
            complete &= stack.is(expected) && stack.getCount()==1 && stack.hasFoil()==(slot>0);
            observed.add("{\"item\":\""+stack.getItem().builtInRegistryHolder().key().location()
                +"\",\"count\":"+stack.getCount()+",\"foil\":"+stack.hasFoil()+"}");
        }
        String target = recovery ? ",\"lastDeathTarget\":{\"dimension\":\"minecraft:overworld\",\"pos\":[166,100,530],\"matches\":"
            + minecraft.player.getLastDeathLocation().equals(Optional.of(RECOVERY_TARGET)) + "}" : "";
        return "{\"fixture\":\""+(recovery ? "gui-recovery-foil-v1" : "gui-special-foil-v1")+"\",\"items\":["+String.join(",",observed)
            +"],\"complete\":"+complete+target+"}";
    }
}
