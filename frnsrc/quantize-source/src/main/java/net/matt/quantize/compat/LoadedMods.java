package net.matt.quantize.compat;

import net.minecraftforge.fml.ModList;

public class LoadedMods {

    private static boolean distantHorizonsLoaded;
    private static boolean entityCullingLoaded;


    public static void init(){
        distantHorizonsLoaded = ModList.get().isLoaded("distanthorizons");
        entityCullingLoaded = ModList.get().isLoaded("entityculling");

    }

    public static boolean isDistantHorizonsLoaded() {
        return distantHorizonsLoaded;
    }

    public static boolean isEntityCullingLoaded() {
        return entityCullingLoaded;
    }
}
