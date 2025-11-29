package net.matt.quantize.utils;

import net.matt.quantize.Quantize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

// NOTE: BrandingControl is in "net.minecraftforge.internal" on Forge 1.20.1
import net.minecraftforge.internal.BrandingControl;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QTitleBadge {
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post evt) {
        if (!(evt.getScreen() instanceof TitleScreen)) return;

        final Minecraft mc = Minecraft.getInstance();
        final GuiGraphics gg = evt.getGuiGraphics();

        // Resolve version string from your mod's metadata
        final String version = ModList.get().getModContainerById(Quantize.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("dev");

        final String text = "Quantize " + version;

        // Figure out where Forge drew its bottom-left lines, then put ours just above them
        final int lineH = mc.font.lineHeight + 1;
        final int screenH = evt.getScreen().height;

        final int[] lines = {0};
        BrandingControl.forEachLine(true, false, (i, s) -> lines[0]++); // count Forge/Minecraft brandings

        // Left margin like Forge uses
        final int x = 2;
        // One line above the existing block of brandings
        final int y = screenH - 2 - (lines[0] + 1) * lineH;

        // Draw with shadow to match main menu styling
        gg.drawString(mc.font, text, x, y, 0xFFFFFF, true);
    }
}
