package net.minecraft.client.gui.components.debug;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugScreenEntries {
	private static final Map<ResourceLocation, DebugScreenEntry> ENTRIES_BY_LOCATION = new HashMap();
	public static final ResourceLocation GAME_VERSION = register("game_version", new DebugEntryVersion());
	public static final ResourceLocation FPS = register("fps", new DebugEntryFps());
	public static final ResourceLocation TPS = register("tps", new DebugEntryTps());
	public static final ResourceLocation MEMORY = register("memory", new DebugEntryMemory());
	public static final ResourceLocation SYSTEM_SPECS = register("system_specs", new DebugEntrySystemSpecs());
	public static final ResourceLocation LOOKING_AT_BLOCK = register("looking_at_block", new DebugEntryLookingAtBlock());
	public static final ResourceLocation LOOKING_AT_FLUID = register("looking_at_fluid", new DebugEntryLookingAtFluid());
	public static final ResourceLocation LOOKING_AT_ENTITY = register("looking_at_entity", new DebugEntryLookingAtEntity());
	public static final ResourceLocation CHUNK_RENDER_STATS = register("chunk_render_stats", new DebugEntryChunkRenderStats());
	public static final ResourceLocation CHUNK_GENERATION_STATS = register("chunk_generation_stats", new DebugEntryChunkGeneration());
	public static final ResourceLocation ENTITY_RENDER_STATS = register("entity_render_stats", new DebugEntryEntityRenderStats());
	public static final ResourceLocation PARTICLE_RENDER_STATS = register("particle_render_stats", new DebugEntryParticleRenderStats());
	public static final ResourceLocation CHUNK_SOURCE_STATS = register("chunk_source_stats", new DebugEntryChunkSourceStats());
	public static final ResourceLocation PLAYER_POSITION = register("player_position", new DebugEntryPosition());
	public static final ResourceLocation PLAYER_SECTION_POSITION = register("player_section_position", new DebugEntrySectionPosition());
	public static final ResourceLocation LIGHT_LEVELS = register("light_levels", new DebugEntryLight());
	public static final ResourceLocation HEIGHTMAP = register("heightmap", new DebugEntryHeightmap());
	public static final ResourceLocation BIOME = register("biome", new DebugEntryBiome());
	public static final ResourceLocation LOCAL_DIFFICULTY = register("local_difficulty", new DebugEntryLocalDifficulty());
	public static final ResourceLocation ENTITY_SPAWN_COUNTS = register("entity_spawn_counts", new DebugEntrySpawnCounts());
	public static final ResourceLocation SOUND_MOOD = register("sound_mood", new DebugEntrySoundMood());
	public static final ResourceLocation POST_EFFECT = register("post_effect", new DebugEntryPostEffect());
	public static final ResourceLocation ENTITY_HITBOXES = register("entity_hitboxes", new DebugEntryNoop());
	public static final ResourceLocation CHUNK_BORDERS = register("chunk_borders", new DebugEntryNoop());
	public static final ResourceLocation THREE_DIMENSIONAL_CROSSHAIR = register("3d_crosshair", new DebugEntryNoop());
	public static final ResourceLocation CHUNK_SECTION_PATHS = register("chunk_section_paths", new DebugEntryNoop());
	public static final ResourceLocation GPU_UTILIZATION = register("gpu_utilization", new DebugEntryGpuUtilization());
	public static final ResourceLocation SIMPLE_PERFORMANCE_IMPACTORS = register("simple_performance_impactors", new DebugEntrySimplePerformanceImpactors());
	public static final ResourceLocation CHUNK_SECTION_OCTREE = register("chunk_section_octree", new DebugEntryNoop());
	public static final ResourceLocation CHUNK_SECTION_VISIBILITY = register("chunk_section_visibility", new DebugEntryNoop());
	public static final Map<DebugScreenProfile, Map<ResourceLocation, DebugScreenEntryStatus>> PROFILES;

	private static ResourceLocation register(String string, DebugScreenEntry debugScreenEntry) {
		return register(ResourceLocation.withDefaultNamespace(string), debugScreenEntry);
	}

	public static ResourceLocation register(ResourceLocation resourceLocation, DebugScreenEntry debugScreenEntry) {
		ENTRIES_BY_LOCATION.put(resourceLocation, debugScreenEntry);
		return resourceLocation;
	}

	public static Map<ResourceLocation, DebugScreenEntry> allEntries() {
		return Map.copyOf(ENTRIES_BY_LOCATION);
	}

	@Nullable
	public static DebugScreenEntry getEntry(ResourceLocation resourceLocation) {
		return (DebugScreenEntry)ENTRIES_BY_LOCATION.get(resourceLocation);
	}

	static {
		Map<ResourceLocation, DebugScreenEntryStatus> map = Map.of(
			THREE_DIMENSIONAL_CROSSHAIR,
			DebugScreenEntryStatus.IN_F3,
			GAME_VERSION,
			DebugScreenEntryStatus.IN_F3,
			TPS,
			DebugScreenEntryStatus.IN_F3,
			FPS,
			DebugScreenEntryStatus.IN_F3,
			MEMORY,
			DebugScreenEntryStatus.IN_F3,
			SYSTEM_SPECS,
			DebugScreenEntryStatus.IN_F3,
			PLAYER_POSITION,
			DebugScreenEntryStatus.IN_F3,
			PLAYER_SECTION_POSITION,
			DebugScreenEntryStatus.IN_F3,
			SIMPLE_PERFORMANCE_IMPACTORS,
			DebugScreenEntryStatus.IN_F3
		);
		Map<ResourceLocation, DebugScreenEntryStatus> map2 = Map.of(
			TPS,
			DebugScreenEntryStatus.IN_F3,
			FPS,
			DebugScreenEntryStatus.ALWAYS_ON,
			GPU_UTILIZATION,
			DebugScreenEntryStatus.IN_F3,
			MEMORY,
			DebugScreenEntryStatus.IN_F3,
			SIMPLE_PERFORMANCE_IMPACTORS,
			DebugScreenEntryStatus.IN_F3
		);
		PROFILES = Map.of(DebugScreenProfile.DEFAULT, map, DebugScreenProfile.PERFORMANCE, map2);
	}
}
