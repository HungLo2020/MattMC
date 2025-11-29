package net.matt.quantize.worldgen;

import net.matt.quantize.utils.ResourceIdentifier;
import net.matt.quantize.worldgen.region.ModOverworldRegion;
import terrablender.api.Regions;

public class QTerrablender {
    public static void registerBiomes() {
        Regions.register(new ModOverworldRegion(new ResourceIdentifier("overworld"), 25));
    }
}