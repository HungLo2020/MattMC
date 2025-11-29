package net.matt.quantize.modules.biomes;

import net.matt.quantize.Quantize;
import net.minecraftforge.fml.ModList;
import net.matt.quantize.modules.biomes.TerrablenderCompat;

public class ModCompatBridge {

    private static boolean terrablender;

    /*
        Must be executed after all mods that require citadel have been loaded
     */
    public static void afterAllModsLoaded(){
        if (ModList.get().isLoaded("terrablender")) {
            Quantize.LOGGER.info("adding citadel surface rules via terrablender...");
            TerrablenderCompat.setup();
            terrablender = true;
        }
    }

    public static boolean usingTerrablender(){
        return terrablender;
    }
}
