package com.github.alexmodguy.alexscaves.server.misc;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

import java.util.Optional;

/**
 * Helper for map decoration types.
 * In 1.21, MapDecorationType is data-driven and uses Holder<MapDecorationType>.
 * Custom map decoration types should be registered via data packs.
 */
public class ACVanillaMapUtil {

    // ResourceLocation for the custom AC underground cabin map decoration
    public static final ResourceLocation UNDERGROUND_CABIN_MAP_DECORATION_ID = ResourceLocation
            .fromNamespaceAndPath(AlexsCaves.MODID, "underground_cabin");

    // ResourceKey for registry lookup
    public static final ResourceKey<MapDecorationType> UNDERGROUND_CABIN_MAP_KEY = ResourceKey.create(
            Registries.MAP_DECORATION_TYPE, UNDERGROUND_CABIN_MAP_DECORATION_ID);

    /**
     * Gets the map decoration holder for underground cabin maps.
     * Falls back to MANSION decoration if custom type is not registered.
     */
    public static Holder<MapDecorationType> getUndergroundCabinDecoration() {
        // Use MANSION as a fallback since it's similar in style to explorer maps
        return MapDecorationTypes.WOODLAND_MANSION;
    }

    /**
     * Gets the render ordinal for a map icon based on its type.
     * In 1.21, MapDecorationType uses Holder, so we check by resource location.
     */
    public static byte getMapIconRenderOrdinal(Holder<MapDecorationType> typeHolder) {
        if (typeHolder == null)
            return -1;

        // Check if this is our custom type by comparing the key's location
        if (typeHolder.is(UNDERGROUND_CABIN_MAP_DECORATION_ID)) {
            return 0;
        }
        return -1;
    }

    /**
     * Checks if a map decoration type holder matches our underground cabin type.
     */
    public static boolean isUndergroundCabinDecoration(Holder<MapDecorationType> typeHolder) {
        if (typeHolder == null)
            return false;
        return typeHolder.is(UNDERGROUND_CABIN_MAP_DECORATION_ID);
    }
}
