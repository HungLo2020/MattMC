How Distant Horizons LOD Distance Works

The LOD (Level of Detail) render distance is set in the Distant Horizons mod configuration, found in:

frnsrc/distant-horizons/coreSubProjects/core/src/main/java/com/seibel/distanthorizons/core/config/Config.java

Key setting (line ~193):
Java

public static ConfigEntry<Integer> lodChunkRenderDistanceRadius = new ConfigEntry.Builder<Integer>()
.setMinDefaultMax(32, 256, 4096)
.comment("The radius of the mod's render distance. (measured in chunks)")
.build();

Default values:

    Minimum: 32 chunks
    Default: 256 chunks (the default LOD distance)
    Maximum: 4096 chunks
