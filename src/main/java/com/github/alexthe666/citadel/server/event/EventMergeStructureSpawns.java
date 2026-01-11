package com.github.alexthe666.citadel.server.event;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
// TODO: Replace with Fabric event system
// import net.neoforged.bus.api.Event;
// import net.neoforged.neoforge.common.util.TriState;

import java.util.ArrayList;
import java.util.List;

// Temporary stub - TODO: Integrate with Fabric event system
public class EventMergeStructureSpawns {

    // Simple TriState replacement
    public enum TriState {
        DEFAULT, TRUE, FALSE;
        
        public boolean toBoolean(boolean defaultValue) {
            return this == TRUE ? true : (this == FALSE ? false : defaultValue);
        }
    }

    private StructureManager structureManager;
    private BlockPos pos;
    private MobCategory category;
    private WeightedList<MobSpawnSettings.SpawnerData> structureSpawns;
    private WeightedList<MobSpawnSettings.SpawnerData> biomeSpawns;
    private TriState result = TriState.DEFAULT;

    public EventMergeStructureSpawns(StructureManager structureManager, BlockPos pos, MobCategory category, WeightedList<MobSpawnSettings.SpawnerData> structureSpawns, WeightedList<MobSpawnSettings.SpawnerData> biomeSpawns) {
        this.structureManager = structureManager;
        this.pos = pos;
        this.category = category;
        this.structureSpawns = structureSpawns;
        this.biomeSpawns = biomeSpawns;
    }

    public StructureManager getStructureManager() {
        return structureManager;
    }

    public BlockPos getPos() {
        return pos;
    }

    public MobCategory getCategory() {
        return category;
    }

    public boolean isStructureTagged(TagKey<Structure> tagKey) {
        return structureManager.getStructureWithPieceAt(pos, tagKey).isValid();
    }

    public WeightedList<MobSpawnSettings.SpawnerData> getStructureSpawns() {
        return structureSpawns;
    }

    public void setStructureSpawns(WeightedList<MobSpawnSettings.SpawnerData> spawns) {
        structureSpawns = spawns;
    }

    public void mergeSpawns() {
        List<MobSpawnSettings.SpawnerData> list = new ArrayList<>();
        for (MobSpawnSettings.SpawnerData entry : biomeSpawns.unwrap()) {
            list.add(entry.data());
        }
        for (MobSpawnSettings.SpawnerData structureSpawn : structureSpawns.unwrap()) {
            MobSpawnSettings.SpawnerData spawnData = structureSpawn.data();
            if (!list.contains(spawnData)) {
                list.add(spawnData);
            }
        }
        this.setStructureSpawns(WeightedList.of(list));
    }

    public WeightedList<MobSpawnSettings.SpawnerData> getBiomeSpawns() {
        return biomeSpawns;
    }

    public TriState getResult() {
        return this.result;
    }

    public void setResult(TriState result) {
        this.result = result;
    }
}
