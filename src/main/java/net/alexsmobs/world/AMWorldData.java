package net.alexsmobs.world;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.UUID;

/**
 * Stub implementation of world data for Alex's Mobs
 * Stores spawn timing and other world-level data
 */
public class AMWorldData extends SavedData {
    private static final SavedDataType<AMWorldData> TYPE = new SavedDataType<>(
        "alexsmobs_world_data",
        AMWorldData::new,
        createCodec(),
        null
    );
    
    private int beachedCachalotSpawnDelay = 0;
    private int beachedCachalotSpawnChance = 0;
    private UUID beachedCachalotID = null;

    public AMWorldData(SavedData.Context context) {
    }

    public AMWorldData() {
        this(new SavedData.Context(null, 0));
    }

    private static Codec<AMWorldData> createCodec() {
        return CompoundTag.CODEC.xmap(
            tag -> {
                AMWorldData data = new AMWorldData();
                data.beachedCachalotSpawnDelay = tag.getInt("BeachedCachalotSpawnDelay").orElse(0);
                data.beachedCachalotSpawnChance = tag.getInt("BeachedCachalotSpawnChance").orElse(0);
                if (tag.contains("BeachedCachalotIDMost") && tag.contains("BeachedCachalotIDLeast")) {
                    long most = tag.getLong("BeachedCachalotIDMost").orElse(0L);
                    long least = tag.getLong("BeachedCachalotIDLeast").orElse(0L);
                    data.beachedCachalotID = new UUID(most, least);
                }
                return data;
            },
            data -> {
                CompoundTag tag = new CompoundTag();
                tag.putInt("BeachedCachalotSpawnDelay", data.beachedCachalotSpawnDelay);
                tag.putInt("BeachedCachalotSpawnChance", data.beachedCachalotSpawnChance);
                if (data.beachedCachalotID != null) {
                    tag.putLong("BeachedCachalotIDMost", data.beachedCachalotID.getMostSignificantBits());
                    tag.putLong("BeachedCachalotIDLeast", data.beachedCachalotID.getLeastSignificantBits());
                }
                return tag;
            }
        );
    }

    public static AMWorldData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    public int getBeachedCachalotSpawnDelay() {
        return beachedCachalotSpawnDelay;
    }

    public void setBeachedCachalotSpawnDelay(int delay) {
        this.beachedCachalotSpawnDelay = delay;
        setDirty();
    }

    public int getBeachedCachalotSpawnChance() {
        return beachedCachalotSpawnChance;
    }

    public void setBeachedCachalotSpawnChance(int chance) {
        this.beachedCachalotSpawnChance = chance;
        setDirty();
    }

    public UUID getBeachedCachalotID() {
        return beachedCachalotID;
    }

    public void setBeachedCachalotID(UUID id) {
        this.beachedCachalotID = id;
        setDirty();
    }
}
