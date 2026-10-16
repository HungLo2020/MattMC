#!/usr/bin/env python3
"""Verify visible promotion retries after failed Java binding allocation.

Injects allocation failure into isolated adapter copies. The negative control
commits native state before Java adoption and must lose the pending retry.
Production sources, classes and native images remain unchanged.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

SOURCES = Path(__file__).resolve().parent
sys.path.insert(0, str(SOURCES.parent))
import VerifyFullGeneration as full
import VerifyEndFeatures as end

PROBE = 'package net.minecraft.server.level;\nimport static org.junit.jupiter.api.Assertions.*;\nimport it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;\nimport java.lang.reflect.Field;\nimport org.junit.jupiter.api.*;\nclass VisiblePromotionFailureContractTest {\n @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}\n static final class FailingMap extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {\n  @Override public Long2ObjectLinkedOpenHashMap<ChunkHolder> clone(){\n   if(Boolean.getBoolean("holder.promote.failOnce")){System.clearProperty("holder.promote.failOnce");throw new OutOfMemoryError("promoted binding allocation");}\n   return super.clone();\n  }\n }\n @Test void allocationFailureKeepsPreviousViewAndPendingPromotion() throws Exception {\n  ChunkMap map=VisibleHolderBoundaryContractTest.map();\n  try {Field f=ChunkMap.class.getDeclaredField("updatingChunkMap"); f.setAccessible(true);f.set(map,new FailingMap());}\n  catch(NoSuchFieldException nativeOwner){}\n  long key=net.minecraft.world.level.ChunkPos.asLong(4,-7);\n  ChunkHolder holder=map.updateChunkScheduling(key,33,null,ChunkLevel.MAX_LEVEL+1);\n  System.setProperty("holder.promote.failOnce","true");\n  try {assertEquals("promoted binding allocation",assertThrows(OutOfMemoryError.class,map::promoteChunkMap).getMessage());}\n  finally {System.clearProperty("holder.promote.failOnce");}\n  assertNull(map.getVisibleChunkIfPresent(key),"failed promotion must retain previous published view");\n  assertTrue(map.promoteChunkMap(),"failed allocation must leave the update pending for retry");\n  assertSame(holder,map.getVisibleChunkIfPresent(key));\n  assertFalse(map.promoteChunkMap());\n }\n}\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output = args.output.resolve()
    if not args.output.is_relative_to(full.ROOT / 'build'):
        raise ValueError('output must be under build/')
    args.output.mkdir(parents=True, exist_ok=False)
    cp = args.classpath_file.read_text().strip()
    before = end.inputs(full.ROOT)
    source = full.ROOT / 'src/main/java/net/minecraft/server/level/NativeChunkHolders.java'
    original = source.read_text()
    allocation = '            this.bindings = new ChunkHolder[source.length];'
    adoption = '        Visible prepared = new Visible(view, arena, this.holders);'
    commit = '            try { ViewBindings.COMMIT.invokeExact(this.id, view); }'
    if any(original.count(v) != 1 for v in [allocation, adoption, commit]):
        raise ValueError('inspect changed prepare/adopt/commit boundaries')
    hook = ('            if (Boolean.getBoolean("holder.promote.failOnce")) { '
            'System.clearProperty("holder.promote.failOnce"); '
            'throw new OutOfMemoryError("promoted binding allocation"); }\n')
    positive = original.replace(allocation, hook + allocation)
    # Reproduce the original early native commit, leaving all actual binding and
    # failure assertions identical. Move the whole production commit block.
    start = positive.index('        if (promote) {', positive.index(adoption))
    stop = positive.index('        return prepared;', start)
    block = positive[start:stop].replace(' Reference.reachabilityFence(prepared);', '')
    mutant = positive[:start] + positive[stop:]
    mutant = mutant.replace(adoption, block + adoption)
    rows = []
    for label, body in [('unchanged', positive), ('early-commit', mutant)]:
        out = args.output / label
        package = out / 'source/net/minecraft/server/level'
        package.mkdir(parents=True)
        copied = package / 'NativeChunkHolders.java'; copied.write_text(body)
        probe = package / 'VisiblePromotionFailureContractTest.java'; probe.write_text(PROBE)
        classes = out / 'classes'; classes.mkdir()
        with (out / 'javac.log').open('w') as log:
            subprocess.run([str(full.JDK / 'javac'), '-g', '-cp', cp, '-d', str(classes),
                str(copied), str(probe), str(SOURCES / 'GenerationContractRunner.java')],
                stdout=log, stderr=subprocess.STDOUT, check=True)
        command = [str(full.JDK / 'java'), '--enable-native-access=ALL-UNNAMED',
            '-Dnet.bytebuddy.experimental=true',
            '-Dmattmc.rust.natives.dir=' + str(full.ROOT / 'build/rust/native'),
            '-cp', str(classes) + os.pathsep + cp, 'GenerationContractRunner',
            'net.minecraft.server.level.VisiblePromotionFailureContractTest']
        (out / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        mappings = {}
        with (out / 'test.log').open('w') as log:
            proc = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 60
                while proc.poll() is None:
                    try:
                        for line in Path(f'/proc/{proc.pid}/maps').read_text().splitlines():
                            if 'mattmc' in line and '.so' in line:
                                path = line.split()[-1]
                                if path not in mappings and Path(path).is_file():
                                    mappings[path] = full.sha(Path(path))
                    except (FileNotFoundError, ProcessLookupError):
                        pass
                    if time.monotonic() > deadline:
                        raise TimeoutError('promotion failure control timed out')
                    time.sleep(.1)
            finally:
                if proc.poll() is None:
                    proc.kill(); proc.wait()
        result_log = (out / 'test.log').read_text()
        mapped = before['native_sha256'] in mappings.values()
        killed = proc.returncode != 0 and 'failed allocation must leave the update pending for retry' in result_log
        rows.append({'case': label, 'exit': proc.returncode,
            'adapter_sha256': full.sha(copied), 'probe_sha256': full.sha(probe),
            'mapped_expected_native': mapped, 'native_mappings': mappings,
            'intended_failure': killed})
        if not mapped:
            raise RuntimeError('expected actual native image not observed: ' + label)
        if label == 'unchanged' and proc.returncode != 0:
            raise RuntimeError('prepared promotion did not retry successfully')
        if label == 'early-commit' and not killed:
            raise RuntimeError('early-commit mutation missed its intended witness')
        print(label, 'PASS' if proc.returncode == 0 else 'intended mutation detected', flush=True)
    after = end.inputs(full.ROOT)
    if before != after:
        raise RuntimeError('production image changed during isolated failure controls')
    (args.output / 'result.json').write_text(json.dumps({'passed': True,
        'before': before, 'after': after, 'rows': rows}, indent=2) + '\n')


if __name__ == '__main__':
    main()
