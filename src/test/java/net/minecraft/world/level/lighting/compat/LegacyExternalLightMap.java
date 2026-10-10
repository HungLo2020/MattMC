package net.minecraft.world.level.lighting.compat;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

/** Original protected constructor remains unambiguous outside its package. */
public final class LegacyExternalLightMap extends DataLayerStorageMap<LegacyExternalLightMap> {
    private final DataLayer layer;
    public LegacyExternalLightMap(DataLayer layer) { super(null); this.layer = layer; }
    @Override public DataLayer getLayer(long key) { return layer; }
    @Override public LegacyExternalLightMap copy() { return new LegacyExternalLightMap(layer); }
}
