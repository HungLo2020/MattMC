#!/usr/bin/env python3
"""Verify Rust chunk section serialization against Java's tag tree and tape writer and benchmark it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
# Last commit before Rust chunk section serialization; production edits are audited against it.
REFERENCE = '5c02fd8215f4c1dde624dbe3d21a476d38b16708'
CASES = ['encode', 'save']
BENCHMARK = 'net.minecraft.world.level.chunk.storage.NativeChunkSectionsVerification'
PARITY = ['net.minecraft.world.level.chunk.storage.NativeChunkSectionsTest']
# The original tag tree and tape writer (-Dmattmc.storage.javaChunkSections), against Rust sections.
BASELINE, CANDIDATE = 'java', 'native'
# Every production Java edit, as exact (original, replacement) pairs applied in
# order to the reference file; anything else is an unaudited change.
PRODUCTION_REWRITES = {
    'src/main/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java': [
        ('\t\tCodec<PalettedContainerRO<Holder<Biome>>> codec2 = this.containerFactory.biomeContainerCodec();\n\n\t\tfor (SerializableChunkData.SectionData sectionData : this.sectionData) {\n',
         '\t\tCodec<PalettedContainerRO<Holder<Biome>>> codec2 = this.containerFactory.biomeContainerCodec();\n\n\t\tfor (SerializableChunkData.SectionData sectionData : sectionsPlaceholder != null ? List.<SerializableChunkData.SectionData>of() : this.sectionData) {\n'),
        ('\t\t}\n\n\t\tListTag listTag = new ListTag();\n',
         '\t\t}\n\n\t\tListTag listTag = sectionsPlaceholder != null ? sectionsPlaceholder : new ListTag();\n'),
        ('\tpublic CompoundTag write() {\n',
         '\tpublic CompoundTag write() {\n\t\treturn this.write(null);\n\t}\n\n\t/** With a placeholder, the sections list is that (empty) tag instead. */\n\tprivate CompoundTag write(@Nullable ListTag sectionsPlaceholder) {\n'),
        ('\t\t\t\tlist2,\n\t\t\t\tcompoundTag2\n\t\t\t);\n\t\t}\n\t}\n\n',
         "\t\t\t\tlist2,\n\t\t\t\tcompoundTag2\n\t\t\t);\n\t\t}\n\t}\n\n\t/** A saved chunk's tape for its region file, with the tag built only if a\n\t * pending read asks for it. */\n\tpublic record Encoded(byte[] tape, java.util.function.Supplier<CompoundTag> tag) {\n\t}\n\n\t/** {@code NativeNbtRegionAccess.writeTape(write())}, with the sections list\n\t * encoded by Rust ({@link net.minecraft.world.level.chunk.NativeChunkSections})\n\t * and spliced into the tape. */\n\tpublic Encoded encode() {\n\t\ttry {\n\t\t\tbyte[] sections = net.minecraft.world.level.chunk.NativeChunkSections.encode(this.sectionData, this.containerFactory);\n\t\t\tif (sections == null) {\n\t\t\t\treturn new Encoded(net.minecraft.nbt.NativeNbtRegionAccess.writeTape(this.write()), this::write);\n\t\t\t}\n\t\t\tListTag placeholder = new ListTag();\n\t\t\treturn new Encoded(net.minecraft.nbt.NativeNbtRegionAccess.writeTape(this.write(placeholder), placeholder, sections), this::write);\n\t\t} catch (java.io.IOException exception) {\n\t\t\tthrow new java.io.UncheckedIOException(exception);\n\t\t}\n\t}\n\n"),
    ],
    'src/main/java/net/minecraft/world/level/chunk/storage/IOWorker.java': [
        ('\t\tCompoundTag copyData() {\n\t\t\tCompoundTag compoundTag = this.data;\n',
         '\t\tCompoundTag copyData() {\n\t\t\tCompoundTag compoundTag = this.data();\n'),
        ('\t\t}\n\n\t\t@Nullable\n',
         '\t\t}\n\n\t\t@Nullable\n\t\tCompoundTag data() {\n\t\t\tif (this.data == null && this.lazyData != null) {\n\t\t\t\tthis.data = this.lazyData.get();\n\t\t\t\tthis.lazyData = null;\n\t\t\t}\n\t\t\treturn this.data;\n\t\t}\n\n\t\t@Nullable\n'),
        ('\t\tCompoundTag data;\n',
         '\t\tCompoundTag data;\n\t\t// An encoded chunk: the tape to write and the tag, built on first read.\n\t\t@Nullable\n\t\tbyte[] tape;\n\t\t@Nullable\n\t\tSupplier<CompoundTag> lazyData;\n'),
        ('\t\ttry {\n\t\t\tthis.storage.write(chunkPos, pendingStore.data);\n',
         '\t\ttry {\n\t\t\tif (pendingStore.tape != null) {\n\t\t\t\tthis.storage.writeTape(chunkPos, pendingStore.tape);\n\t\t\t} else {\n\t\t\t\tthis.storage.write(chunkPos, pendingStore.data);\n\t\t\t}\n'),
        ('\t\t\t\tif (pendingStore != null) {\n\t\t\t\t\tif (pendingStore.data != null) {\n\t\t\t\t\t\tpendingStore.data.acceptAsRoot(streamTagVisitor);\n',
         '\t\t\t\tif (pendingStore != null) {\n\t\t\t\t\tCompoundTag data = pendingStore.data();\n\t\t\t\t\tif (data != null) {\n\t\t\t\t\t\tdata.acceptAsRoot(streamTagVisitor);\n'),
        ('\t\t\t\t\tpendingStore.data = compoundTag;\n',
         '\t\t\t\t\tpendingStore.data = compoundTag;\n\t\t\t\t\tpendingStore.tape = null;\n\t\t\t\t\tpendingStore.lazyData = null;\n\t\t\t\t\treturn pendingStore.result;\n\t\t\t\t}\n\t\t\t)\n\t\t\t.thenCompose(Function.identity());\n\t}\n\n\t/** {@link #store(ChunkPos, Supplier)} for a chunk already encoded to tape:\n\t * the region write uses the tape, and the tag is built only if a pending\n\t * read needs it. */\n\tpublic CompletableFuture<Void> storeEncoded(ChunkPos chunkPos, Supplier<SerializableChunkData.Encoded> supplier) {\n\t\treturn this.submitTask(\n\t\t\t\t() -> {\n\t\t\t\t\tSerializableChunkData.Encoded encoded = supplier.get();\n\t\t\t\t\tIOWorker.PendingStore pendingStore = (IOWorker.PendingStore)this.pendingWrites\n\t\t\t\t\t\t.computeIfAbsent(chunkPos, chunkPosxx -> new IOWorker.PendingStore(null));\n\t\t\t\t\tpendingStore.data = null;\n\t\t\t\t\tpendingStore.tape = encoded.tape();\n\t\t\t\t\tpendingStore.lazyData = encoded.tag();\n'),
    ],
    'src/main/java/net/minecraft/world/level/chunk/storage/RegionFileStorage.java': [
        ('\t\t\t}\n\t\t}\n\t}\n\n',
         '\t\t\t}\n\t\t}\n\t}\n\n\t/** {@link #write} of a chunk already encoded to NBT tape. */\n\tprotected void writeTape(ChunkPos chunkPos, byte[] tape) throws IOException {\n\t\tif (!SharedConstants.DEBUG_DONT_SAVE_WORLD) {\n\t\t\tthis.getRegionFile(chunkPos).writeChunkTape(chunkPos, tape);\n\t\t}\n\t}\n\n'),
    ],
    'src/main/java/net/minecraft/world/level/chunk/storage/RegionFile.java': [
        ('\n\tpublic synchronized void writeChunk(ChunkPos chunkPos, CompoundTag compoundTag) throws IOException {\n\t\tbyte[] tape = NativeNbtRegionAccess.writeTape(compoundTag);\n',
         '\n\tpublic void writeChunk(ChunkPos chunkPos, CompoundTag compoundTag) throws IOException {\n\t\tthis.writeChunkTape(chunkPos, NativeNbtRegionAccess.writeTape(compoundTag));\n\t}\n\n\tpublic synchronized void writeChunkTape(ChunkPos chunkPos, byte[] tape) throws IOException {\n'),
    ],
    'src/main/java/net/minecraft/world/level/chunk/storage/ChunkStorage.java': [
        ('\t\treturn this.worker.store(chunkPos, supplier);\n\t}\n\n',
         '\t\treturn this.worker.store(chunkPos, supplier);\n\t}\n\n\tpublic CompletableFuture<Void> writeEncoded(ChunkPos chunkPos, Supplier<SerializableChunkData.Encoded> supplier) {\n\t\tthis.handleLegacyStructureIndex(chunkPos);\n\t\treturn this.worker.storeEncoded(chunkPos, supplier);\n\t}\n\n'),
    ],
    'src/main/java/net/minecraft/server/level/ChunkMap.java': [
        ('\t\t\t\tSerializableChunkData serializableChunkData = SerializableChunkData.copyOf(this.level, chunkAccess);\n\t\t\t\tCompletableFuture<CompoundTag> completableFuture = CompletableFuture.supplyAsync(serializableChunkData::write, Util.backgroundExecutor());\n\t\t\t\tthis.write(chunkPos, completableFuture::join).handle((void_, throwable) -> {\n',
         '\t\t\t\tSerializableChunkData serializableChunkData = SerializableChunkData.copyOf(this.level, chunkAccess);\n\t\t\t\tCompletableFuture<SerializableChunkData.Encoded> completableFuture = CompletableFuture.supplyAsync(serializableChunkData::encode, Util.backgroundExecutor());\n\t\t\t\tthis.writeEncoded(chunkPos, completableFuture::join).handle((void_, throwable) -> {\n'),
    ],
    'src/main/java/net/minecraft/nbt/NativeNbt.java': [
        ('\t\tprivate void writeTag(String name, Tag tag) throws IOException {\n',
         '\t\tprivate void writeTag(String name, Tag tag) throws IOException {\n\t\t\tif (tag == this.placeholder && this.placeholder != null) {\n\t\t\t\tthis.output.write(this.splice);\n\t\t\t\treturn;\n\t\t\t}\n'),
        ('\t\tstatic byte[] writeRoot(CompoundTag tag) throws IOException {\n\t\t\tTapeWriter writer = new TapeWriter(estimateRootBytes(tag));\n',
         '\t\tstatic byte[] writeRoot(CompoundTag tag) throws IOException {\n\t\t\treturn writeRoot(tag, null, null);\n\t\t}\n\n\t\tstatic byte[] writeRoot(CompoundTag tag, Tag placeholder, byte[] splice) throws IOException {\n\t\t\tTapeWriter writer = new TapeWriter(estimateRootBytes(tag) + (splice == null ? 0 : splice.length));\n\t\t\twriter.placeholder = placeholder;\n\t\t\twriter.splice = splice;\n'),
        ('\t\tprivate final ByteArrayOutputStream output;\n',
         '\t\tprivate final ByteArrayOutputStream output;\n\t\tprivate Tag placeholder;\n\t\tprivate byte[] splice;\n'),
        ('\t\treturn TapeWriter.writeRoot(tag);\n',
         '\t\treturn TapeWriter.writeRoot(tag);\n\t}\n\n\t/** {@link #writeTape} with {@code placeholder} (by identity, wherever it\n\t * occurs as a named entry) written as the given complete record tape. */\n\tstatic byte[] writeTape(CompoundTag tag, Tag placeholder, byte[] splice) throws IOException {\n\t\treturn TapeWriter.writeRoot(tag, placeholder, splice);\n\t}\n\n\t/** The tape of {@code tag} as an unnamed list element. */\n\tstatic byte[] elementTape(Tag tag) throws IOException {\n\t\tTapeWriter writer = new TapeWriter(64);\n\t\twriter.writeTag("", tag);\n\t\treturn writer.output.toByteArray();\n'),
    ],
    'src/main/java/net/minecraft/nbt/NativeNbtRegionAccess.java': [
        ('\t\treturn NativeNbt.writeTape(tag);\n\t}\n',
         '\t\treturn NativeNbt.writeTape(tag);\n\t}\n\n\tpublic static byte[] writeTape(CompoundTag tag, Tag placeholder, byte[] splice) throws IOException {\n\t\treturn NativeNbt.writeTape(tag, placeholder, splice);\n\t}\n\n\tpublic static byte[] elementTape(Tag tag) throws IOException {\n\t\treturn NativeNbt.elementTape(tag);\n\t}\n'),
    ],
}

RUST = ['src/main/rust/storage/chunk/mod.rs', 'src/main/rust/storage/chunk/ffi.rs']


def run(command, path, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    path.write_text(result.stdout)
    if result.returncode:
        raise RuntimeError('Command failed; see ' + str(path))
    return result.stdout


def audit():
    for path, rewrites in PRODUCTION_REWRITES.items():
        expected = subprocess.check_output(['git', 'show', REFERENCE + ':' + path], cwd=ROOT, text=True)
        for old, new in rewrites:
            if expected.count(old) != 1:
                raise RuntimeError('Production rewrite does not apply exactly once: ' + path)
            expected = expected.replace(old, new)
        if (ROOT / path).read_text() != expected:
            raise RuntimeError('Production edit differs from its audited rewrites: ' + path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='build/chunk-sections-migration/acceptance')
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--cpu', type=int, default=5)
    parser.add_argument('--background-cpus', default='0,1')
    parser.add_argument('--case', default='all')
    parser.add_argument('--parity-only', action='store_true')
    parser.add_argument('--pilot', action='store_true')
    args = parser.parse_args()
    cases = CASES if args.case == 'all' else args.case.split(',')
    if any(case not in CASES for case in cases):
        parser.error('Unknown case')
    if not args.pilot and not args.parity_only and args.forks < 3:
        parser.error('At least three independent comparisons required')
    background = [int(cpu) for cpu in args.background_cpus.split(',')]
    if len(background) < 2 or args.cpu in background or not {args.cpu, *background}.issubset(os.sched_getaffinity(0)):
        parser.error('Separate available worker CPUs required')
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / 'build'):
        parser.error('Output must be under build/')
    out.mkdir(parents=True, exist_ok=True)
    audit()
    classpath, init = out / 'classpath.txt', out / 'classpath.gradle'
    init.write_text("gradle.projectsEvaluated { gradle.rootProject.tasks.named('test') { outputs.upToDateWhen { false } }; "
                    "gradle.rootProject.tasks.register('writeChunkSectionsClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeChunkSectionsClasspath', '-x', 'testRustNative', '--console=plain'], out / 'build.log')
    tests = [argument for name in PARITY for argument in ('--tests', name)]
    run(['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative', *tests, '--console=plain'], out / 'parity.log')
    parity, java_tests = [], 0
    for name in PARITY:
        xml = (ROOT / ('build/test-results/test/TEST-' + name + '.xml')).read_text()
        (out / (name.rsplit('.', 1)[1] + '.xml')).write_text(xml)
        result = ET.fromstring(xml)
        if int(result.attrib['failures']) or int(result.attrib['errors']):
            raise RuntimeError('Parity failed: ' + name)
        java_tests += int(result.attrib['tests'])
        parity += re.findall(r'[A-Z_]+_(?:PARITY|SYNTHETIC)[^\n<]*', xml)
    rust = run(['cargo', 'test', '--release', 'storage::chunk'], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / path for path in PRODUCTION_REWRITES] + [ROOT / path for path in RUST]
    files += [ROOT / 'src/main/java/net/minecraft/world/level/chunk/NativeChunkSections.java', Path(__file__).resolve(),
              ROOT / 'src/test/java/net/minecraft/world/level/chunk/storage/NativeChunkSectionsTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/chunk/storage/NativeChunkSectionsVerification.java',
              ROOT / 'src/test/resources/storage/chunks.bin']
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': java_tests, 'baseline': BASELINE, 'candidate': CANDIDATE,
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': ('Saving the 40 saved chunks of storage/chunks.bin: encode turns each SerializableChunkData into its NBT tape '
                        '(Java tag tree and tape writer, against Rust sections spliced into the Java root); save adds the region '
                        'file write of each tape. NBT parsing excluded. No whole-game timing.'),
              'cpu': args.cpu, 'background_cpus': background, 'jvm': flags, 'pilot': args.pilot, 'cases': cases,
              'java_version': subprocess.check_output(['java', '--version'], text=True),
              'rust_version': subprocess.check_output(['rustc', '--version'], text=True),
              'hardware': json.loads(subprocess.check_output(['lscpu', '-J'], text=True)),
              'native_sha256': hashlib.sha256(library.read_bytes()).hexdigest(),
              'sources': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
              'pairs': [], 'performance': {}}

    def save():
        (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n')

    save()
    if args.parity_only:
        print(out / 'results.json')
        return
    for fork in range(args.forks):
        pair = {}
        report['pairs'].append(pair)
        for name in cases:
            row = pair.setdefault(name, {})
            for mode in ([BASELINE, CANDIDATE] if fork % 2 == 0 else [CANDIDATE, BASELINE]):
                print(f'Comparison {fork + 1}: {name} {mode}', flush=True)
                text = run(base + [BENCHMARK, mode, name] + (['quick'] if args.pilot else []), out / f'{fork}-{name}-{mode}.log')
                bench = re.findall(r'PLAYER_DISTANCE_BENCH case=(\w+) mode=(\w+) warmup_ns=(\d+) repeats=\d+ ns=(\[.*?\]) '
                                   r'cpu_ns=(\[.*?\]) jit=(\[.*?\]) checksum=(-?\d+)', text)
                if len(bench) != 1 or bench[0][0] != name:
                    raise RuntimeError('Incomplete measurement')
                _, _, warmup, samples, cpu, jit, checksum = bench[0]
                if not args.pilot and int(warmup) < 15_000_000_000:
                    raise RuntimeError('Insufficient warmup')
                if 'checksum' in row and row['checksum'] != int(checksum):
                    raise RuntimeError('Route outputs differ for ' + name)
                row['checksum'] = int(checksum)
                row[mode] = json.loads(samples)
                row[mode + '_jit'] = json.loads(jit)
                save()
    rng = random.Random(1977)

    def summarize(pairs):
        ratios = [statistics.median(n) / statistics.median(j) for j, n in pairs]
        bootstrap = sorted(statistics.median(statistics.median(rng.choices(n, k=len(n))) / statistics.median(rng.choices(j, k=len(j)))
                                             for j, n in rng.choices(pairs, k=len(pairs))) for _ in range(10000))
        return {'baseline_ns': statistics.median(statistics.median(j) for j, n in pairs),
                'candidate_ns': statistics.median(statistics.median(n) for j, n in pairs),
                'ratios': ratios, 'ratio_ci95': [bootstrap[250], bootstrap[9749]],
                'passes': max(ratios) <= .95 and bootstrap[9749] <= .95}

    for name in cases:
        report['performance'][name] = summarize([(p[name][BASELINE], p[name][CANDIDATE]) for p in report['pairs']])
        report['performance'][name]['jit_active_samples'] = sum(
            1 for p in report['pairs'] for mode in (BASELINE, CANDIDATE) for value in p[name][mode + '_jit'] if value)
        if len({p[name]['checksum'] for p in report['pairs']}) != 1:
            raise RuntimeError('Cross-JVM output mismatch')
    for name, digest in report['sources'].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Measured source changed: ' + name)
    if hashlib.sha256(library.read_bytes()).hexdigest() != report['native_sha256']:
        raise RuntimeError('Measured library changed')
    report['integrity'] = {'source_hashes_match': True, 'native_hash_matches': True, 'route_checksums_match': True}
    save()
    print(json.dumps(report['performance'], indent=2))
    print(out / 'results.json')


if __name__ == '__main__':
    main()
