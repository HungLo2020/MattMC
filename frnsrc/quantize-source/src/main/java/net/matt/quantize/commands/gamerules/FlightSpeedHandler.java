package net.matt.quantize.commands.gamerules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.commands.QGameRules;

@Mod.EventBusSubscriber(modid = "quantize")
public class FlightSpeedHandler {
    private static final float BASE_SPEED = 0.05f; // Vanilla creative flight speed

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;   // only run on server
        if (!player.getAbilities().mayfly) return; // only applies if player can fly

        int ruleValue = player.level().getGameRules()
                .getRule(QGameRules.FLIGHT_SPEED).get();

        if (ruleValue < 1) ruleValue = 1; // clamp to minimum of 1x

        if (ruleValue > 10) ruleValue = 10; // clamp to minimum of 1x

        float newSpeed = BASE_SPEED * ruleValue;

        if (player.getAbilities().getFlyingSpeed() != newSpeed) {
            player.getAbilities().setFlyingSpeed(newSpeed);

            // sync to client
            if (player instanceof ServerPlayer sp) {
                sp.onUpdateAbilities();
            }
        }
    }
}
