#!/usr/bin/env python3
"""Verify the Rust-owned NOISE fill against doFill's Java loop and benchmark it."""
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
# Last commit before the native NOISE fill; production edits are audited against it.
REFERENCE = '5218ac875eda9f2c4151ff1a97795f20f5f356cf'
CASES = ['overworld', 'amplified', 'nether']
BENCHMARK = 'net.minecraft.world.level.levelgen.NoiseFillVerification'
PARITY = 'net.minecraft.world.level.levelgen.NativeNoiseFillTest'
# Every production Java edit, as exact (original, replacement) pairs applied in
# order to the reference file; anything else is an unaudited change.
PRODUCTION_REWRITES = {
    'src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java': [
        ('\t\tHeightmap heightmap2 = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);\n',
         '\t\tHeightmap heightmap2 = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);\n\t\t// Rust owns the block loop and its writes when every rule it assumes holds.\n\t\tNativeNoiseFill nativeFill = NativeNoiseFill.prepare(this.settings.value(), noiseChunk, chunkAccess, heightmap, heightmap2, i, j);\n\t\tif (nativeFill != null) {\n\t\t\tnativeFill.run(i, j);\n\t\t\treturn chunkAccess;\n\t\t}\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NoiseChunk.java': [
        ('    boolean aquiferBatchSafe() { return randomState.aquiferBatchSafe(); }\n',
         '    boolean aquiferBatchSafe() { return randomState.aquiferBatchSafe(); }\n    RandomState randomState() { return this.randomState; }\n'),
        ('\t\t\tlist.add(OreVeinifier.create(noiseRouter2.veinToggle(), noiseRouter2.veinRidged(), noiseRouter2.veinGap(), randomState.oreRandom()));\n',
         '\t\t\tlist.add(OreVeinifier.create(noiseRouter2.veinToggle(), noiseRouter2.veinRidged(), noiseRouter2.veinGap(), randomState.oreRandom()));\n\t\t\tthis.veinToggle = noiseRouter2.veinToggle();\n\t\t\tthis.veinRidged = noiseRouter2.veinRidged();\n\t\t\tthis.veinGap = noiseRouter2.veinGap();\n\t\t} else {\n\t\t\tthis.veinToggle = null;\n\t\t\tthis.veinRidged = null;\n\t\t\tthis.veinGap = null;\n'),
        ('\tprivate final NoiseChunk.BlockStateFiller blockStateRule;\n',
         '\tprivate final NoiseChunk.BlockStateFiller blockStateRule;\n\t// Chunk-wrapped ore vein inputs for the native fill gate; null without ore veins.\n\t@Nullable\n\tfinal DensityFunction veinToggle;\n\t@Nullable\n\tfinal DensityFunction veinRidged;\n\t@Nullable\n\tfinal DensityFunction veinGap;\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/Aquifer.java': [
        ('        // Unknown factories visit precisely the original twelve centers in order.\n',
         '        // Unknown factories visit precisely the original twelve centers in order.\n        long[] locationCache() { return aquiferLocationCache; }\n\n        int skipSamplingAboveY() { return skipSamplingAboveY; }\n\n        Aquifer.FluidPicker globalFluidPicker() { return globalFluidPicker; }\n\n'),
        ('                    new int[]{minGridX,minGridY,minGridZ,gridSizeX,gridSizeZ},\n                    globalFluidPicker, barrierNoise, skipSamplingAboveY,\n                    positionalRandomFactory.getClass() == XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class\n                        || positionalRandomFactory.getClass() == LegacyRandomSource.LegacyPositionalRandomFactory.class);\n',
         '                    new int[]{minGridX,minGridY,minGridZ,gridSizeX,gridSizeZ},\n                    globalFluidPicker, barrierNoise, skipSamplingAboveY, positionalRandomFactory);\n'),
        ('\n        private NativeAquifer nativeAquifer() {\n',
         '\n        NativeAquifer nativeAquifer() {\n'),
        ('\n\t\t\t@Override\n\t\t\tpublic boolean shouldScheduleFluidUpdate() {\n\t\t\t\treturn false;\n\t\t\t}\n\t\t};\n',
         '\n\t/** The disabled aquifer, named so the native NOISE fill can recognize its picker. */\n\trecord Disabled(Aquifer.FluidPicker fluidPicker) implements Aquifer {\n\t\t@Nullable\n\t\t@Override\n\t\tpublic BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double d) {\n\t\t\treturn d > 0.0 ? null : this.fluidPicker.computeFluid(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ()).at(functionContext.blockY());\n\t\t}\n\n\t\t@Override\n\t\tpublic boolean shouldScheduleFluidUpdate() {\n\t\t\treturn false;\n\t\t}\n'),
        ('\tstatic Aquifer createDisabled(Aquifer.FluidPicker fluidPicker) {\n\t\treturn new Aquifer() {\n\t\t\t@Nullable\n\t\t\t@Override\n\t\t\tpublic BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double d) {\n\t\t\t\treturn d > 0.0 ? null : fluidPicker.computeFluid(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ()).at(functionContext.blockY());\n\t\t\t}\n',
         '\tstatic Aquifer createDisabled(Aquifer.FluidPicker fluidPicker) {\n\t\treturn new Aquifer.Disabled(fluidPicker);\n\t}\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NativeAquifer.java': [
        ('            materialX=sx;materialY=sy;materialZ=sz;materialEpoch=chunk.arrayInterpolationCounter;materialsReady=true;\n',
         '            materialX=sx;materialY=sy;materialZ=sz;materialEpoch=chunk.arrayInterpolationCounter;materialsReady=true;\n    }\n\n    /** The aquifer centres a cell batch reads, drawn natively for built-in factories. */\n    void cellLocations(int sx,int sy,int sz) throws Throwable {\n        if(locationKind==0) { owner.cellLocations(sx,sy,sz,chunk.cellWidth,chunk.cellHeight); return; }\n        check((int)LOCATIONS.invokeExact(locationsMemory,locations.length,shapeMemory,locationKind,locationSeedA,locationSeedB,\n            sx,sy,sz,chunk.cellWidth,chunk.cellHeight));\n    }\n\n    /** Whether the native NOISE fill can take this aquifer\'s per-cell material batch. */\n    boolean nativeFillReady() {\n        if(!pureSources || barrier==null || chunk.aquiferDensity==null)return false;\n        NativeNoiseState noise=barrier.noise().noise()==null?null:barrier.noise().noise().nativeState();\n        return noise==null || noise.critical();\n    }\n\n    /** The cell\'s (state, schedule) materials, as the first in-cell block\'s\n     * computeMaterial would prepare them; the cell caches must be selected. */\n    int[] prepareCellMaterials(int sx,int sy,int sz) {\n        try {\n            NativeNoiseState noise=barrier.noise().noise()==null?null:barrier.noise().noise().nativeState();\n            prepareMaterials(sx,sy,sz,noise);\n            return materials;\n        } catch(RuntimeException | Error e) { throw e; }\n        catch(Throwable t) { throw new IllegalStateException("Native aquifer cell failed",t); }\n'),
        ('            if(materials==null) {materials=new int[chunk.cellWidth*chunk.cellWidth*chunk.cellHeight*2];materialMemory=MemorySegment.ofArray(materials);}\n            owner.cellLocations(sx,sy,sz,chunk.cellWidth,chunk.cellHeight);\n',
         '            if(materials==null) {materials=new int[chunk.cellWidth*chunk.cellWidth*chunk.cellHeight*2];materialMemory=MemorySegment.ofArray(materials);}\n            cellLocations(sx,sy,sz);\n'),
        ('                if(cell==null) { cell=new int[chunk.cellWidth*chunk.cellWidth*chunk.cellHeight*8];cellMemory=MemorySegment.ofArray(cell); }\n                owner.cellLocations(sx,sy,sz,chunk.cellWidth,chunk.cellHeight);\n',
         '                if(cell==null) { cell=new int[chunk.cellWidth*chunk.cellWidth*chunk.cellHeight*8];cellMemory=MemorySegment.ofArray(cell); }\n                cellLocations(sx,sy,sz);\n'),
        ('        this.owner=owner;this.chunk=chunk;this.locations=locations;this.shape=shape;\n',
         '        this.owner=owner;this.chunk=chunk;this.locations=locations;this.shape=shape;\n        if(random.getClass()==XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class) {\n            var factory=(XoroshiroRandomSource.XoroshiroPositionalRandomFactory)random;\n            locationKind=1;locationSeedA=factory.seedLo();locationSeedB=factory.seedHi();\n        } else if(random.getClass()==LegacyRandomSource.LegacyPositionalRandomFactory.class) {\n            locationKind=2;locationSeedA=((LegacyRandomSource.LegacyPositionalRandomFactory)random).seed();locationSeedB=0;\n        } else {\n            locationKind=0;locationSeedA=0;locationSeedB=0;\n        }\n        boolean pureRandom=locationKind!=0;\n'),
        ('    NativeAquifer(Aquifer.NoiseBasedAquifer owner, NoiseChunk chunk, long[] locations,int[] shape,\n                  Aquifer.FluidPicker picker,DensityFunction barrier,int skipY,boolean pureRandom) {\n',
         '    NativeAquifer(Aquifer.NoiseBasedAquifer owner, NoiseChunk chunk, long[] locations,int[] shape,\n                  Aquifer.FluidPicker picker,DensityFunction barrier,int skipY,PositionalRandomFactory random) {\n'),
        ('    private final int surfaceMinX,surfaceMinZ,surfaceWidth,surfaceHeight;\n\n',
         '    private final int surfaceMinX,surfaceMinZ,surfaceWidth,surfaceHeight;\n\n    // Built-in positional factories draw aquifer centres natively: 1 Xoroshiro, 2 Legacy, 0 Java.\n    private final int locationKind;\n    private final long locationSeedA,locationSeedB;\n\n'),
        ('        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE));\n',
         '        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE));\n    private static final MethodHandle LOCATIONS = bind("locations",FunctionDescriptor.of(ValueLayout.JAVA_INT,\n        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,\n        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/XoroshiroRandomSource.java': [
        ('\t\t\tthis.seedHi = m;\n\t\t}\n\n',
         '\t\t\tthis.seedHi = m;\n\t\t}\n\n\t\tlong seedLo() { return this.seedLo; }\n\n\t\tlong seedHi() { return this.seedHi; }\n\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/LegacyRandomSource.java': [
        ('\t\t\tthis.seed = l;\n\t\t}\n\n',
         '\t\t\tthis.seed = l;\n\t\t}\n\n\t\tlong seed() { return this.seed; }\n\n'),
    ],
    'src/main/java/net/minecraft/world/level/chunk/PalettedContainer.java': [
        ('\t\t\treturn new PalettedContainer.Data<>(configuration, bitStorage, palette);\n\t\t}\n',
         "\t\t\treturn new PalettedContainer.Data<>(configuration, bitStorage, palette);\n\t\t}\n\t}\n\n\t/** Whether this is still a fresh single-value container holding only {@code value}. */\n\tboolean isUntouched(T value) {\n\t\tPalettedContainer.Data<T> data = this.data;\n\t\treturn data.palette instanceof SingleValuePalette<T> && data.storage instanceof ZeroBitStorage && data.palette.valueFor(0) == value;\n\t}\n\n\t/** Storage bits of this strategy's global palette. */\n\tint globalPaletteBits() {\n\t\treturn this.strategy.getConfigurationForBitCount(32).bitsInMemory();\n\t}\n\n\t/** Installs the palette and storage that replaying a write sequence into this\n\t * fresh container produces, as computed by the native NOISE fill. */\n\tvoid installGenerated(int requestedBits, List<T> entries, long[] raw) {\n\t\tConfiguration configuration = this.strategy.getConfigurationForBitCount(requestedBits);\n\t\tBitStorage bitStorage = configuration.bitsInMemory() == 0\n\t\t\t? new ZeroBitStorage(this.strategy.entryCount())\n\t\t\t: new SimpleBitStorage(configuration.bitsInMemory(), this.strategy.entryCount(), raw);\n\t\tthis.data = new PalettedContainer.Data<>(configuration, bitStorage, configuration.createPalette(this.strategy, entries));\n"),
    ],
    'src/main/java/net/minecraft/world/level/chunk/LevelChunkSection.java': [
        ('\t\treturn blockState2;\n',
         '\t\treturn blockState2;\n\t}\n\n\t/** A fresh all-air section whose block palette has never grown. */\n\tpublic boolean isUntouchedAirForGeneration() {\n\t\treturn this.nonEmptyBlockCount == 0 && this.tickingBlockCount == 0 && this.tickingFluidCount == 0\n\t\t\t&& this.states.isUntouched(Blocks.AIR.defaultBlockState());\n\t}\n\n\tpublic int generatedGlobalPaletteBits() {\n\t\treturn this.states.globalPaletteBits();\n\t}\n\n\t/** Installs a native NOISE fill result into a fresh section: the block palette\n\t * and storage its writes produce and the counters setBlockState would keep. */\n\tpublic void installGenerated(int requestedBits, List<BlockState> palette, long[] raw, int nonEmpty, int ticking, int fluid) {\n\t\tthis.states.installGenerated(requestedBits, palette, raw);\n\t\tthis.nonEmptyBlockCount = (short)nonEmpty;\n\t\tthis.tickingBlockCount = (short)ticking;\n\t\tthis.tickingFluidCount = (short)fluid;\n'),
        ('package net.minecraft.world.level.chunk;\n\n',
         'package net.minecraft.world.level.chunk;\n\nimport java.util.List;\nimport net.minecraft.world.level.block.Blocks;\n'),
    ],
}

RUST = ['src/main/rust/world/level/levelgen/random.rs', 'src/main/rust/world/level/levelgen/aquifer/locations.rs',
        'src/main/rust/world/level/levelgen/aquifer/ffi.rs']


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
    parser.add_argument('--output', default='build/noise-fill-migration/acceptance')
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
                    "gradle.rootProject.tasks.register('writeNoiseFillClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeNoiseFillClasspath', '-x', 'testRustNative', '--console=plain'], out / 'build.log')
    run(['./gradlew', '-I', str(init), 'test', '-x', 'buildRustNative', '-x', 'testRustNative', '--tests', PARITY, '--console=plain'], out / 'parity.log')
    xml = (ROOT / ('build/test-results/test/TEST-' + PARITY + '.xml')).read_text()
    (out / 'parity.xml').write_text(xml)
    result = ET.fromstring(xml)
    if int(result.attrib['failures']) or int(result.attrib['errors']):
        raise RuntimeError('Parity failed')
    parity = re.findall(r'[A-Z_]+_PARITY[^\n<]*', xml)
    rust = run(['cargo', 'test', '--release', 'world::level::levelgen'], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / path for path in PRODUCTION_REWRITES] + [ROOT / path for path in RUST]
    files += sorted((ROOT / 'src/main/rust/world/level/levelgen/noise_fill').glob('*.rs'))
    files += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java', Path(__file__).resolve(),
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeNoiseFillTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NoiseFillVerification.java']
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': int(result.attrib['tests']),
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': 'NoiseBasedChunkGenerator.fillFromNoise per fresh ProtoChunk: interpolation slices, cell caches, aquifer cell '
                       'materials, the block loop, section writes, heightmaps and post-processing; Java loop versus native fill. '
                       'Chunk and NoiseChunk construction excluded equally. No surface, carving, features or whole-game timing.',
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
            for mode in (['java', 'native'] if fork % 2 == 0 else ['native', 'java']):
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
        return {'java_ns': statistics.median(statistics.median(j) for j, n in pairs),
                'native_ns': statistics.median(statistics.median(n) for j, n in pairs),
                'ratios': ratios, 'ratio_ci95': [bootstrap[250], bootstrap[9749]],
                'passes': max(ratios) <= .95 and bootstrap[9749] <= .95}

    for name in cases:
        report['performance'][name] = summarize([(p[name]['java'], p[name]['native']) for p in report['pairs']])
        report['performance'][name]['jit_active_samples'] = sum(
            1 for p in report['pairs'] for mode in ('java', 'native') for value in p[name][mode + '_jit'] if value)
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
