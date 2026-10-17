package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DH's structure manager without a chunk (no level, or the chunk unavailable at
 * STRUCTURE_REFERENCES) answers empty results of the declared types; with a
 * chunk it delegates as before.
 */
class WorldGenStructFeatManagerTest {
	private static final SectionPos SECTION = SectionPos.of(1, 0, 2);
	private static final BlockPos POS = SECTION.origin();

	@BeforeAll static void load() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static WorldGenLevel unavailableChunks() {
		WorldGenLevel level = mock(WorldGenLevel.class);
		when(level.getChunk(anyInt(), anyInt(), any(ChunkStatus.class), eq(false))).thenReturn(null);
		return level;
	}

	@Test void withoutLevelStartsForStructureIsAnEmptyList() {
		var manager = new WorldGenStructFeatManager(null, null, null);
		assertEquals(List.of(), manager.startsForStructure(SECTION, mock(Structure.class)));
	}

	@Test void withoutLevelAllStructuresIsAnEmptyMap() {
		var manager = new WorldGenStructFeatManager(null, null, null);
		assertEquals(Map.of(), manager.getAllStructuresAt(POS));
	}

	@Test void withoutLevelTheOtherFallbacksAreUnchanged() {
		var manager = new WorldGenStructFeatManager(null, null, null);
		assertFalse(manager.hasAnyStructureAt(POS));
		assertEquals(List.of(), manager.startsForStructure(new ChunkPos(1, 2), structure -> true));
	}

	@Test void unavailableChunkStartsForStructureIsAnEmptyList() {
		WorldGenLevel level = unavailableChunks();
		var manager = new WorldGenStructFeatManager(null, level, null);
		assertEquals(List.of(), manager.startsForStructure(SECTION, mock(Structure.class)));
		verify(level).getChunk(SECTION.x(), SECTION.z(), ChunkStatus.STRUCTURE_REFERENCES, false);
	}

	@Test void unavailableChunkAllStructuresIsAnEmptyMap() {
		WorldGenLevel level = unavailableChunks();
		var manager = new WorldGenStructFeatManager(null, level, null);
		assertEquals(Map.of(), manager.getAllStructuresAt(POS));
		verify(level).getChunk(SECTION.x(), SECTION.z(), ChunkStatus.STRUCTURE_REFERENCES, false);
	}

	@Test void presentChunkStillDelegates() {
		Structure structure = mock(Structure.class);
		StructureStart start = mock(StructureStart.class);
		when(start.isValid()).thenReturn(true);
		ChunkAccess startChunk = mock(ChunkAccess.class);
		when(startChunk.getStartForStructure(structure)).thenReturn(start);
		LongSet references = new LongOpenHashSet(new long[] {ChunkPos.asLong(3, 4)});
		Map<Structure, LongSet> all = Map.of(structure, references);
		ChunkAccess chunk = mock(ChunkAccess.class);
		when(chunk.getReferencesForStructure(structure)).thenReturn(references);
		when(chunk.getAllReferences()).thenReturn(all);
		WorldGenLevel level = mock(WorldGenLevel.class);
		when(level.getChunk(SECTION.x(), SECTION.z(), ChunkStatus.STRUCTURE_REFERENCES, false)).thenReturn(chunk);
		when(level.getChunk(3, 4, ChunkStatus.STRUCTURE_STARTS)).thenReturn(startChunk);

		var manager = new WorldGenStructFeatManager(null, level, null);
		assertEquals(List.of(start), manager.startsForStructure(SECTION, structure));
		assertSame(all, manager.getAllStructuresAt(POS));
	}
}
