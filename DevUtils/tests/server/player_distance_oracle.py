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
