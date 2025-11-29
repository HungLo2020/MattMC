package net.matt.quantize.gui;

import net.matt.quantize.gui.screen.SkinSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "quantize", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MainMenuButtonHandler {

    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;

        // Original button size + margin
        final int originalBw = 100;
        final int bh = 20;
        final int margin = 10;

        // Start at bottom-right with the original width
        int x = screen.width - originalBw - margin;   // bottom-right X (original)
        int y = screen.height - bh - margin + 2;          // bottom-right Y

        // Move up exactly one button height
        y -= bh;

        // Halve the width and keep the button CENTERED on the same spot
        int bw = originalBw / 2;                      // new (halved) width
        x += (originalBw - bw) / 2;                   // shift right by half the shrink

        Button btn = Button.builder(
                Component.literal("Skins"),
                b -> Minecraft.getInstance().setScreen(new SkinSelectionScreen())
        ).pos(x, y).size(bw, bh).build();

        event.addListener(btn);
    }
}
