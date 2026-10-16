#!/usr/bin/env python3
"""Exercise actual dependency publication, VM roots and native retirement.

Compile isolated adapter copies; do not edit production sources or outputs.
The injected post-adoption failure must preserve the old fence and retire the
unpublished native owner. Deliberately broken copies must fail their witnesses.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import VerifyFullGeneration as full
import VerifyEndFeatures as end

PROBE = r'''package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
class ChunkDependencyFailureContractTest {
    static final OutOfMemoryError FAILURE = new OutOfMemoryError("after native dependency adoption");
    @BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void failedAdoptionKeepsThePublishedFenceAndRetiresBeforeRetry() throws Exception {
        for(int i=0;i<10;i++){System.gc();Thread.sleep(20);}
        var route=ChunkSyncDependencyContractTest.route(true);
        CompletableFuture<?> first=NativeChunkFenceContractTest.generationFence();
        CompletableFuture<Void> second=new CompletableFuture<>();
        route.add().accept(first);
        long[] before=NativeChunkGeneration.rangeStatistics();
        System.setProperty("chunk.dependencies.failAfterAdopt","true");
        try{assertSame(FAILURE,assertThrows(OutOfMemoryError.class,()->route.add().accept(second)));}
        finally{System.clearProperty("chunk.dependencies.failAfterAdopt");}
        assertSame(first,route.get().get(),"failed dependency allocation published a replacement fence");
        long[] after=NativeChunkGeneration.rangeStatistics();
        for(int i=0;i<160;i++){
            System.gc();Thread.sleep(20);after=NativeChunkGeneration.rangeStatistics();
            if(after[0]<=before[0]&&after[2]>=before[2]+1)break;
        }
        assertEquals(before[1]+1,after[1]);
        assertTrue(after[0]<=before[0],"unpublished dependency owner did not retire");
        assertTrue(after[2]>=before[2]+1,"unpublished dependency destructor did not run");
        route.add().accept(second);
        assertNotSame(first,route.get().get());
        first.complete(null);second.complete(null);assertNull(route.get().get().join());
    }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(full.ROOT / 'build'):
        raise ValueError('output must be below build/')
    output.mkdir(parents=True, exist_ok=False)
    source = full.ROOT / 'src/main/java/net/minecraft/server/level/NativeChunkGeneration.java'
    original = source.read_text()
    start = original.index('    private static final class RangeDependencies {')
    stop = original.index('    static CompletableFuture<ChunkResult<List<ChunkAccess>>> rangeDependencies(', start)
    body = original[start:stop]
    adoption = '            owner = adopt(created, 1, arena, RELEASE);'
    binding = '                    ERROR.setRelease(errors, node, selection == 1 ? left : selection == 2 ? right : null);'
    commit = '                    publish((long)COMMIT.invokeExact(id, node));'
    roots = '        volatile List<? extends CompletableFuture<?>> prerequisites;'
    if any(body.count(marker) != 1 for marker in (adoption, binding, commit, roots)):
        raise ValueError('inspect changed dependency adoption/publication boundaries')
    hook = ('\n            if (Boolean.getBoolean("chunk.dependencies.failAfterAdopt")) { '
            'System.clearProperty("chunk.dependencies.failAfterAdopt"); '
            'throw ChunkDependencyFailureContractTest.FAILURE; }')
    positive = body.replace(adoption, adoption + hook)
    weak = positive.replace(roots, '        volatile java.lang.ref.WeakReference<List<? extends CompletableFuture<?>>> prerequisites;')
    weak = weak.replace('this.prerequisites = prerequisites;', 'this.prerequisites = new java.lang.ref.WeakReference<>(prerequisites);')
    methods = weak.index('        private void publish(')
    weak = weak[:methods] + weak[methods:].replace('prerequisites.get(', 'prerequisites.get().get(').replace('prerequisites.size()', 'prerequisites.get().size()').replace('prerequisites = List.of();', 'prerequisites = new java.lang.ref.WeakReference<>(List.of());')
    publication = binding + '\n                    left = null;\n                    right = null;\n' + commit
    if positive.count(publication) != 1:
        raise ValueError('inspect changed error-root retirement before publication')
    cases = [
        ('unchanged', positive, ['ChunkDependencyFailureContractTest', 'NativeChunkFenceContractTest', 'NativeChunkDependencyLifetimeTest'], None),
        ('early-native-publication', positive.replace(publication, commit + '\n' + binding + '\n                    left = null;\n                    right = null;'),
            ['NativeChunkFenceContractTest'], 'publishedNativeInnerErrorSurvivesRecoveryThroughANewFence'),
        ('weak-vm-prerequisites', weak, ['NativeChunkDependencyLifetimeTest'], 'the retained native dependency needs its actual other VM future'),
        ('missing-native-release', positive.replace(adoption, '            owner = MemorySegment.ofAddress(created);'),
            ['NativeChunkDependencyLifetimeTest'], 'last VM release left the native dependency owner live'),
    ]
    cp = args.classpath_file.read_text().strip()
    before = end.inputs(full.ROOT)
    rows = []
    for label, section, tests, witness in cases:
        run = output / label
        package = run / 'source/net/minecraft/server/level'
        package.mkdir(parents=True)
        copied = package / source.name
        copied.write_text(original[:start] + section + original[stop:])
        probe = package / 'ChunkDependencyFailureContractTest.java'
        probe.write_text(PROBE)
        classes = run / 'classes'
        classes.mkdir()
        compile_command = [str(full.JDK / 'javac'), '-g', '-cp', cp, '-d', str(classes), str(copied), str(probe), str(HERE / 'GenerationContractRunner.java')]
        with (run / 'compile.log').open('w') as log:
            compiled = subprocess.run(compile_command, stdout=log, stderr=subprocess.STDOUT)
        if compiled.returncode:
            raise RuntimeError('isolated adapter compile failed: ' + str(run / 'compile.log'))
        command = [str(full.JDK / 'java'), '--enable-native-access=ALL-UNNAMED', '-Dnet.bytebuddy.experimental=true',
            '-Dmattmc.rust.natives.dir=' + str(full.ROOT / 'build/rust/native'), '-cp', str(classes) + os.pathsep + cp,
            'GenerationContractRunner', *['net.minecraft.server.level.' + name for name in tests]]
        (run / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        mappings = {}
        with (run / 'test.log').open('w') as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, cwd=full.ROOT)
            try:
                deadline = time.monotonic() + 120
                while process.poll() is None:
                    try:
                        for line in Path(f'/proc/{process.pid}/maps').read_text().splitlines():
                            if 'mattmc' in line and '.so' in line:
                                path = line.split()[-1]
                                if Path(path).is_file() and path not in mappings:
                                    mappings[path] = full.sha(Path(path))
                    except (FileNotFoundError, ProcessLookupError):
                        pass
                    if time.monotonic() > deadline:
                        raise TimeoutError('dependency mutation timed out: ' + label)
                    time.sleep(.1)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
        log = (run / 'test.log').read_text()
        intended = witness is not None and process.returncode != 0 and witness in log
        mapped = before['native_sha256'] in mappings.values()
        rows.append({'case': label, 'exit': process.returncode, 'intended_failure': intended,
            'mapped_expected_native': mapped, 'native_mappings': mappings,
            'adapter_sha256': full.sha(copied), 'probe_sha256': full.sha(probe)})
        (output / 'rows.json').write_text(json.dumps(rows, indent=2) + '\n')
        if not mapped or (witness is None and process.returncode != 0) or (witness is not None and not intended):
            raise RuntimeError('dependency control missed its witness: ' + label)
        print(label, 'PASS' if witness is None else 'intended mutation detected', flush=True)
    after = end.inputs(full.ROOT)
    if before != after:
        raise RuntimeError('production inputs changed during isolated dependency controls')
    (output / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': after, 'rows': rows}, indent=2) + '\n')


if __name__ == '__main__':
    main()
