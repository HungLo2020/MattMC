package net.matt.quantize.gui;

import net.minecraft.client.gui.screens.MenuScreens;
import net.matt.quantize.gui.menu.*;
import net.matt.quantize.gui.screen.*;

public class ModScreens {
    public static void registerScreens() {
        MenuScreens.register(SolarPanelMenu.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new);
        MenuScreens.register(ElectricFurnaceMenu.ELECTRIC_FURNACE_MENU.get(), ElectricFurnaceScreen::new);
        MenuScreens.register(BatteryMenu.BATTERY_MENU.get(), BatteryScreen::new);
        MenuScreens.register(PulverizerMenu.PULVERIZER_MENU.get(), PulverizerScreen::new);
        MenuScreens.register(EnergyConduitMenu.ENERGY_CONDUIT_MENU.get(), EnergyConduitScreen::new);
        MenuScreens.register(WirelessCapacitorMenu.WIRELESS_CAPACITOR_MENU.get(), WirelessCapacitorScreen::new);
        MenuScreens.register(BotanyPotMenu.BOTANY_POT_MENU.get(), BotanyPotScreen::new);
        MenuScreens.register(HydroponicsBasinMenu.HYDROPONICS_BASIN_MENU.get(), HydroponicsBasinScreen::new);
        MenuScreens.register(CrafterMenu.CRAFTER_MENU.get(), CrafterScreen::new);
    }
}