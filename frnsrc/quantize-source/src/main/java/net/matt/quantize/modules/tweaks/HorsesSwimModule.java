// Java
package net.matt.quantize.modules.tweaks;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.Quantize;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class HorsesSwimModule {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        Player player = event.player;
        if (player.getVehicle() instanceof AbstractHorse horse) {
            boolean water = horse.isInWater();
            if (water) {
                boolean tallWater = horse.level().isWaterAt(horse.blockPosition().below());
                if (tallWater) {
                    horse.move(MoverType.PLAYER, new Vec3(0, 0.1, 0));
                }
            }
        }
    }
}