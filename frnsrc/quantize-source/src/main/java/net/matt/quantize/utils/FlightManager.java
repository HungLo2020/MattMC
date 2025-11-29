package net.matt.quantize.utils;

import net.matt.quantize.item.item.IFlightItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.Quantize;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.stream.StreamSupport;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class FlightManager {

    private static boolean canPlayerFly(Player player) {
        // Check main inventory and hotbar
        boolean canFlyFromInventory = player.getInventory().items.stream()
                .filter(stack -> stack.getItem() instanceof IFlightItem)
                .anyMatch(stack -> ((IFlightItem) stack.getItem()).canEnableFlight(player, stack));

        // Check all equipment slots (armor slots)
        boolean canFlyFromEquipment = StreamSupport.stream(player.getArmorSlots().spliterator(), false)
                .filter(stack -> stack.getItem() instanceof IFlightItem)
                .anyMatch(stack -> ((IFlightItem) stack.getItem()).canEnableFlight(player, stack));


        // Check offhand slot
        ItemStack offhand = player.getInventory().offhand.get(0);
        boolean canFlyFromOffhand = offhand.getItem() instanceof IFlightItem &&
                ((IFlightItem) offhand.getItem()).canEnableFlight(player, offhand);

        boolean canFly = canFlyFromInventory || canFlyFromEquipment || canFlyFromOffhand;

        //Quantize.LOGGER.info("Player " + player.getName().getString() + " canFly: " + canFly);
        return canFly;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;

        // Only run on the server side
        if (player.level().isClientSide) return;

        boolean canFly = canPlayerFly(player);

        if (canFly && !player.getAbilities().mayfly) {
            enableFlight(player);
        } else if (!canFly && player.getAbilities().mayfly) {
            disableFlight(player);
        }
    }

    private static void enableFlight(Player player) {
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(serverPlayer.getAbilities()));
        }

        Quantize.LOGGER.info("Flight enabled for player: " + player.getName().getString());
    }

    private static void disableFlight(Player player) {
        if (player.isCreative() || player.isSpectator()) return;

        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(serverPlayer.getAbilities()));
        }

        Quantize.LOGGER.info("Flight disabled for player: " + player.getName().getString());
    }
}