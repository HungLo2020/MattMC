"""Derive the pinned original fixed-player-distance tracker oracle from Git."""
import subprocess

REFERENCE = '78e8e0423084f010bb47e36132550619b37644c2'
SOURCE = 'src/main/java/net/minecraft/server/level/DistanceManager.java'
ORACLE = 'src/test/java/net/minecraft/server/level/JavaFixedPlayerDistanceChunkTracker.java'
HEADER = '''package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.level.lighting.JavaChunkTracker;

/** Original DistanceManager.FixedPlayerDistanceChunkTracker at the pinned
 * reference, relocated onto the pinned original ChunkTracker/graph/queue. Only
 * the player-presence map is supplied instead of the enclosing instance's. */
'''
# Each rewrite is exact and must apply once; nothing else may differ.
REWRITES = [
    ('class FixedPlayerDistanceChunkTracker extends ChunkTracker {\n',
     'public class JavaFixedPlayerDistanceChunkTracker extends JavaChunkTracker {\n'
     '\tprivate final Long2ObjectMap<ObjectSet<Object>> playersPerChunk;\n'),
    ('protected FixedPlayerDistanceChunkTracker(final int i) {\n\t\tsuper(i + 2, 16, 256);\n',
     'protected JavaFixedPlayerDistanceChunkTracker(final int i, Long2ObjectMap<ObjectSet<Object>> playersPerChunk) {\n'
     '\t\tsuper(i + 2, 16, 256);\n\t\tthis.playersPerChunk = playersPerChunk;\n'),
    ('ObjectSet<ServerPlayer> objectSet = DistanceManager.this.playersPerChunk.get(l);',
     'ObjectSet<Object> objectSet = this.playersPerChunk.get(l);'),
]


def original_class(root):
    text = subprocess.check_output(['git', 'show', REFERENCE + ':' + SOURCE], cwd=root, text=True)
    start = text.index('\tclass FixedPlayerDistanceChunkTracker extends ChunkTracker {')
    end = text.index('\n\t}\n', start) + 4
    return ''.join(line[1:] if line.startswith('\t') else line for line in text[start:end].splitlines(True))


def expected_oracle(root):
    body = original_class(root)
    for old, new in REWRITES:
        if body.count(old) != 1:
            raise RuntimeError('Oracle rewrite does not apply exactly once: ' + old)
        body = body.replace(old, new)
    return HEADER + body


if __name__ == '__main__':
    from pathlib import Path
    root = Path(__file__).resolve().parents[3]
    (root / ORACLE).write_text(expected_oracle(root))
    print(root / ORACLE)


SIMULATION_SOURCE = 'src/main/java/net/minecraft/server/level/SimulationChunkTracker.java'
SIMULATION_ORACLE = 'src/test/java/net/minecraft/server/level/JavaSimulationChunkTracker.java'
SIMULATION_REWRITES = [
    ('import net.minecraft.world.level.TicketStorage;\n',
     'import net.minecraft.world.level.TicketStorage;\nimport net.minecraft.world.level.lighting.JavaChunkTracker;\n'),
    ('public class SimulationChunkTracker extends ChunkTracker {',
     'public class JavaSimulationChunkTracker extends JavaChunkTracker {'),
    ('public SimulationChunkTracker(TicketStorage ticketStorage) {',
     'public JavaSimulationChunkTracker(TicketStorage ticketStorage) {'),
]


def expected_simulation_oracle(root):
    """Original SimulationChunkTracker, renamed onto the pinned original ChunkTracker."""
    text = subprocess.check_output(['git', 'show', REFERENCE + ':' + SIMULATION_SOURCE], cwd=root, text=True)
    for old, new in SIMULATION_REWRITES:
        if text.count(old) != 1:
            raise RuntimeError('Simulation oracle rewrite does not apply exactly once: ' + old)
        text = text.replace(old, new)
    return text


def _rewrite(root, source, rewrites, label):
    text = subprocess.check_output(['git', 'show', REFERENCE + ':' + source], cwd=root, text=True)
    for old, new in rewrites:
        if text.count(old) != 1:
            raise RuntimeError(label + ' rewrite does not apply exactly once: ' + old)
        text = text.replace(old, new)
    return text


LOADING_SOURCE = 'src/main/java/net/minecraft/server/level/LoadingChunkTracker.java'
LOADING_ORACLE = 'src/test/java/net/minecraft/server/level/JavaLoadingChunkTracker.java'
LOADING_REWRITES = [
    ('import net.minecraft.world.level.TicketStorage;\n',
     'import net.minecraft.world.level.TicketStorage;\nimport net.minecraft.world.level.lighting.JavaChunkTracker;\n'),
    ('class LoadingChunkTracker extends ChunkTracker {', 'class JavaLoadingChunkTracker extends JavaChunkTracker {'),
    ('public LoadingChunkTracker(DistanceManager distanceManager, TicketStorage ticketStorage) {',
     'public JavaLoadingChunkTracker(DistanceManager distanceManager, TicketStorage ticketStorage) {'),
]

SECTION_SOURCE = 'src/main/java/net/minecraft/server/level/SectionTracker.java'
SECTION_ORACLE = 'src/test/java/net/minecraft/server/level/JavaSectionTracker.java'
SECTION_REWRITES = [
    ('import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;\n',
     'import net.minecraft.world.level.lighting.JavaDynamicGraphMinFixedPoint;\n'),
    ('public abstract class SectionTracker extends DynamicGraphMinFixedPoint {',
     'public abstract class JavaSectionTracker extends JavaDynamicGraphMinFixedPoint {'),
    ('protected SectionTracker(int i, int j, int k) {', 'protected JavaSectionTracker(int i, int j, int k) {'),
]

POI_SOURCE = 'src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java'
POI_ORACLE = 'src/test/java/net/minecraft/world/entity/ai/village/poi/JavaPoiDistanceTracker.java'
POI_HEADER = '''package net.minecraft.world.entity.ai.village.poi;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.function.LongPredicate;
import net.minecraft.server.level.JavaSectionTracker;

/** Original PoiManager.DistanceTracker at the pinned reference, relocated onto
 * the pinned original SectionTracker/graph/queue. Only isVillageCenter is
 * supplied instead of the enclosing PoiManager's. */
'''
POI_REWRITES = [
    ('final class DistanceTracker extends SectionTracker {\n',
     'public class JavaPoiDistanceTracker extends JavaSectionTracker {\n\tprivate final LongPredicate villageCenter;\n'),
    ('protected DistanceTracker() {\n\t\tsuper(7, 16, 256);\n',
     'public JavaPoiDistanceTracker(LongPredicate villageCenter) {\n\t\tsuper(7, 16, 256);\n\t\tthis.villageCenter = villageCenter;\n'),
    ('return PoiManager.this.isVillageCenter(l) ? 0 : 7;', 'return this.villageCenter.test(l) ? 0 : 7;'),
]


def expected_loading_oracle(root):
    return _rewrite(root, LOADING_SOURCE, LOADING_REWRITES, 'Loading oracle')


def expected_section_oracle(root):
    return _rewrite(root, SECTION_SOURCE, SECTION_REWRITES, 'Section oracle')


def expected_poi_oracle(root):
    text = subprocess.check_output(['git', 'show', REFERENCE + ':' + POI_SOURCE], cwd=root, text=True)
    start = text.index('\tfinal class DistanceTracker extends SectionTracker {')
    end = text.index('\n\t}\n', start) + 4
    body = ''.join(line[1:] if line.startswith('\t') else line for line in text[start:end].splitlines(True))
    for old, new in POI_REWRITES:
        if body.count(old) != 1:
            raise RuntimeError('POI oracle rewrite does not apply exactly once: ' + old)
        body = body.replace(old, new)
    return POI_HEADER + body


if __name__ == '__main__':
    for path, build in [(LOADING_ORACLE, expected_loading_oracle), (SECTION_ORACLE, expected_section_oracle), (POI_ORACLE, expected_poi_oracle)]:
        (root / path).parent.mkdir(parents=True, exist_ok=True)
        (root / path).write_text(build(root))
        print(root / path)
