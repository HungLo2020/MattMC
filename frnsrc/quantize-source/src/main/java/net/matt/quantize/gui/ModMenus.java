package net.matt.quantize.gui;

import net.matt.quantize.gui.menu.*;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModMenus {
    public static void register(IEventBus modEventBus) {
        SolarPanelMenu.MENUS.register(modEventBus);
        ElectricFurnaceMenu.MENUS.register(modEventBus);
        BatteryMenu.MENUS.register(modEventBus);
        PulverizerMenu.MENUS.register(modEventBus);
        EnergyConduitMenu.MENUS.register(modEventBus);
        WirelessCapacitorMenu.MENUS.register(modEventBus);
        BotanyPotMenu.MENUS.register(modEventBus);
        HydroponicsBasinMenu.MENUS.register(modEventBus);
        CrafterMenu.MENUS.register(modEventBus);
    }
}