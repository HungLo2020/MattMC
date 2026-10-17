#!/usr/bin/env python3
"""Inject stage allocation/effect failures in isolated Java adapters.

Preserve actual source/native images. A deliberately leaked stage continuation
must fail its native-holder retirement witness in a disposable child JVM.
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
import static org.mockito.Mockito.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.*;
class ChunkStageFailureContractTest {
    static final OutOfMemoryError FAILURE = new OutOfMemoryError("isolated stage allocation failure");
    @BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    static void fail(String point) {
        if (point.equals(System.getProperty("chunk.stage.fail"))) {
            System.clearProperty("chunk.stage.fail"); throw FAILURE;
        }
    }
    @Test void failedAdmissionAllocationDoesNotClaimOrDispatch() {
        var f = new ChunkStageInvocationContractTest.Fixture();
        System.setProperty("chunk.stage.fail", "adopt");
        try { assertSame(FAILURE, assertThrows(OutOfMemoryError.class, () -> f.apply(ChunkStatus.EMPTY))); }
        finally { System.clearProperty("chunk.stage.fail"); }
        assertNull(f.holder.getLatestStatus());
        assertTrue(f.map.trace.isEmpty());
        assertTrue(f.holder.getAllFutures().stream().allMatch(pair -> pair.getSecond() == null));
        assertSame(f.engine.exposed, f.apply(ChunkStatus.EMPTY));
    }
    @Test void failedEngineEffectKeepsTheCommittedStartedPrefix() {
        var f = new ChunkStageInvocationContractTest.Fixture();
        System.setProperty("chunk.stage.fail", "engine");
        try { assertSame(FAILURE, assertThrows(OutOfMemoryError.class, () -> f.apply(ChunkStatus.EMPTY))); }
        finally { System.clearProperty("chunk.stage.fail"); }
        assertSame(ChunkStatus.EMPTY, f.holder.getLatestStatus());
        assertTrue(f.map.trace.isEmpty());
        assertFalse(f.apply(ChunkStatus.EMPTY).isDone());
        assertTrue(f.map.trace.isEmpty(), "failed stage was dispatched again");
    }
    @Test @SuppressWarnings("unchecked") void failedWorkWrapperPreservesPublishedSlotAndDoesNotReportFatal() throws Exception {
        var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("fatalException"); field.setAccessible(true);
        var fatal = (AtomicReference<RuntimeException>)field.get(null);
        RuntimeException previous = fatal.getAndSet(null);
        try {
            var f = new ChunkStageInvocationContractTest.Fixture();
            f.apply(ChunkStatus.EMPTY); var slot = f.apply(ChunkStatus.EMPTY);
            var chunk = mock(ProtoChunk.class);
            System.setProperty("chunk.stage.fail", "wrapper");
            try { assertSame(FAILURE, assertThrows(OutOfMemoryError.class, () -> f.engine.fire(chunk, null))); }
            finally { System.clearProperty("chunk.stage.fail"); }
            assertSame(chunk, slot.join().orElse(null));
            assertNull(fatal.get(), "completion allocation error was reclassified as engine failure");
        } finally { fatal.set(previous); }
    }
    static WeakReference<?> abandonedAdmission() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        var map = new GenerationChunkHolderContractTest.MapStub();
        System.setProperty("chunk.stage.fail", "adopt");
        try { assertSame(FAILURE, assertThrows(OutOfMemoryError.class,
            () -> GenerationChunkHolderContractTest.apply(holder, map, ChunkStatus.EMPTY))); }
        finally { System.clearProperty("chunk.stage.fail"); }
        return new WeakReference<>(holder);
    }
    static void settle() throws Exception {
        framework().clearInlineMocks();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        for (int i=0;i<40;i++){System.gc();Thread.sleep(20);}
    }
    @Test void failedAdmissionReleasesItsCapturedNativeHolder() throws Exception {
        settle(); long before = NativeChunkGeneration.statistics()[0];
        var weak = abandonedAdmission();
        for (int i=0;i<160;i++) {
            System.gc();Thread.sleep(20);
            if (weak.get()==null && NativeChunkGeneration.statistics()[0]<=before) break;
        }
        assertNull(weak.get(), "failed admission retained the VM holder");
        assertTrue(NativeChunkGeneration.statistics()[0]<=before,
            "failed stage driver retained its native holder owner");
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
    holder_source = source.with_name('GenerationChunkHolder.java')
    holder_original = holder_source.read_text()
    start = original.index('        void futureControl(int operation, FutureEffect effect) {')
    stop = original.index('        private long change(', start)
    body = original[start:stop]
    allocation = '                if (driver == 0) throw new OutOfMemoryError("Native holder future continuation allocation failed");'
    release = '                if (driver != 0) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);'
    if body.count(allocation) != 1 or body.count(release) != 1:
        raise ValueError('inspect changed stage driver allocation/release boundaries')
    positive = body.replace(allocation, allocation + '\n                if (operation == 8) ChunkStageFailureContractTest.fail("adopt");')
    patched_holder = holder_original.replace('                case 87:',
        '                case 87: ChunkStageFailureContractTest.fail("engine");')
    patched_holder = patched_holder.replace('                case 93: returnedResult',
        '                case 93: ChunkStageFailureContractTest.fail("wrapper"); returnedResult')
    if patched_holder == holder_original or 'fail("wrapper")' not in patched_holder:
        raise ValueError('inspect changed stage engine/publication effects')
    cases = [
        ('injected-failures', positive, ['ChunkStageFailureContractTest', 'ChunkStageInvocationContractTest'], None),
        ('missing-stage-release', positive.replace(release,
            '                if (driver != 0 && operation != 8 && operation != 9) releaseHandle(TaskBindings.DRIVER_RELEASE, driver);'),
            ['ChunkStageFailureContractTest'], 'failed stage driver retained its native holder owner'),
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
        (package / holder_source.name).write_text(patched_holder)
        probe = package / 'ChunkStageFailureContractTest.java'
        probe.write_text(PROBE)
        classes = run / 'classes'
        classes.mkdir()
        compile_command = [str(full.JDK / 'javac'), '-g', '-cp', cp, '-d', str(classes), str(copied), str(package / holder_source.name), str(probe), str(full.ROOT / 'src/test/java/net/minecraft/server/level/GenerationChunkHolderContractTest.java'), str(full.ROOT / 'src/test/java/net/minecraft/server/level/ChunkStageInvocationContractTest.java'), str(HERE / 'GenerationContractRunner.java')]
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
                        raise TimeoutError('stage mutation timed out: ' + label)
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
            raise RuntimeError('stage control missed its witness: ' + label)
        print(label, 'PASS' if witness is None else 'intended mutation detected', flush=True)
    after = end.inputs(full.ROOT)
    if before != after:
        raise RuntimeError('production inputs changed during isolated stage controls')
    (output / 'result.json').write_text(json.dumps({'passed': True, 'before': before, 'after': after, 'rows': rows}, indent=2) + '\n')


if __name__ == '__main__':
    main()
