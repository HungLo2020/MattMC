#!/usr/bin/env python3
"""Verify the SURFACE stage's Rust-answered conditions and cached rule programs against Java's and benchmark them."""
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
# Last commit before the Rust-owned fill aquifer and surface conditions; production edits are audited against it.
REFERENCE = '54611cfc25dbdf60ae4b11dc17557d2bec77469d'
CASES = ['overworld', 'amplified', 'nether']
BENCHMARK = 'net.minecraft.world.level.levelgen.NativeSurfaceChunkVerification'
PARITY = ['net.minecraft.world.level.levelgen.NativeSurfaceChunkTest', 'net.minecraft.world.level.levelgen.NativeSurfaceTest',
          'net.minecraft.world.level.levelgen.NativeCarversTest']
# Rust storage with Java answering conditions and compiling each chunk's program, against Rust answers and cached programs.
BASELINE, CANDIDATE = 'javaconditions', 'native'
# Every production Java edit, as exact (original, replacement) pairs applied in
# order to the reference file; anything else is an unaudited change.
PRODUCTION_REWRITES = {
    'src/main/java/net/minecraft/world/level/levelgen/NativeAquifer.java': [
        ("    /** Arrays are this aquifer's live caches; {@code noise} keeps the barrier state reachable. */\n    record CarverBinding(long[] locations,int[] shape,int[] cache,int locationKind,long seedA,long seedB,int skipY,int[] policy,\n",
         "    /** Arrays are this aquifer's live caches; {@code noise} keeps the barrier state reachable. */\n    record NativeBinding(long[] locations,int[] shape,int[] cache,int locationKind,long seedA,long seedB,int skipY,int[] policy,\n"),
        ('        }\n        return new CarverBinding(locations,shape,cache,locationKind,locationSeedA,locationSeedB,skipY,fluidPolicy,\n',
         '        }\n        return new NativeBinding(locations,shape,cache,locationKind,locationSeedA,locationSeedB,skipY,fluidPolicy,\n'),
        ('    @org.jetbrains.annotations.Nullable\n    CarverBinding carverBinding() {\n',
         '    @org.jetbrains.annotations.Nullable\n    NativeBinding nativeBinding() {\n'),
        ("\n    /** This aquifer's buffers for a native CARVERS stage, whose substance decisions\n     * run entirely in Rust: built-in pure sources and positional randomness, a\n     * plain fluid picker of water, lava or air, and a native barrier noise. Null\n     * keeps Java's carving. The caches are this aquifer's own; the stage copies\n     * them back. */\n",
         "\n    /** This aquifer's buffers for a native stage whose substance and material\n     * decisions run entirely in Rust (the NOISE fill's cell traversal, CARVERS):\n     * built-in pure sources and positional randomness, a plain fluid picker of\n     * water, lava or air, and a native barrier noise. Null keeps Java's\n     * decisions. The caches are this aquifer's own; the stage copies them back. */\n"),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NativeCarvers.java': [
        ('            } else if (aquifer instanceof Aquifer.NoiseBasedAquifer noiseAquifer) {\n                binding = noiseAquifer.nativeAquifer().carverBinding();\n',
         '            } else if (aquifer instanceof Aquifer.NoiseBasedAquifer noiseAquifer) {\n                binding = noiseAquifer.nativeAquifer().nativeBinding();\n'),
        ('        }\n        NativeAquifer.CarverBinding binding = null;\n',
         '        }\n        NativeAquifer.NativeBinding binding = null;\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java': [
        ('            Reference.reachabilityFence(router);\n',
         '            Reference.reachabilityFence(router);\n            Reference.reachabilityFence(aquiferBinding);\n'),
        ('                this.chunk.skipNativeCells(cells * cellCountY);\n            }\n            this.chunk.stopInterpolation();\n',
         '                this.chunk.skipNativeCells(cells * cellCountY);\n            }\n            this.chunk.stopInterpolation();\n            if (aquiferBinding != null) {\n                // The aquifer\'s caches are pure memos; keep what Rust filled.\n                int status = (int)AQUIFER_CACHES.invokeExact(handle, MemorySegment.ofArray(aquiferBinding.locations()), MemorySegment.ofArray(aquiferBinding.cache()),\n                    MemorySegment.ofArray(aquiferBinding.surface()), MemorySegment.ofArray(aquiferBinding.memo()), MemorySegment.ofArray(aquiferBinding.present()));\n                if (status != 0) throw new IllegalStateException("Native noise fill aquifer caches failed: " + status);\n            }\n'),
        ('                    if (status == 0) break;\n                    if (status != 1) throw new IllegalStateException("Native noise fill cells failed: " + status);\n',
         '                    if (status == 0) break;\n                    if (status != 1 || aquiferBinding != null) throw new IllegalStateException("Native noise fill cells failed: " + status);\n'),
        ('        int cells = 16 / this.chunk.cellWidth, cellSize = this.chunk.cellWidth * this.chunk.cellWidth * this.chunk.cellHeight;\n',
         '        int cells = 16 / this.chunk.cellWidth, cellSize = this.chunk.cellWidth * this.chunk.cellWidth * this.chunk.cellHeight;\n        NativeAquifer.NativeBinding aquiferBinding = this.bindAquifer(handle);\n'),
        ("\n    /** doFill's cell loop run by Rust; Java prepares aquifer materials on request. */\n",
         '\n    /** Gives the traversal this chunk\'s aquifer state when its decisions can run\n     * entirely in Rust; null keeps Java preparing cell materials on request. */\n    @Nullable\n    private NativeAquifer.NativeBinding bindAquifer(long handle) throws Throwable {\n        NativeAquifer.NativeBinding binding = this.aquifer == null || !nativeAquifer ? null : this.aquifer.nativeBinding();\n        if (binding == null) return null;\n        int[] ints = new int[27];\n        System.arraycopy(binding.shape(), 0, ints, 0, 5);\n        ints[5] = binding.skipY();\n        ints[6] = binding.locationKind();\n        System.arraycopy(binding.policy(), 0, ints, 7, 8);\n        System.arraycopy(binding.surfaceRect(), 0, ints, 15, 4);\n        System.arraycopy(binding.grid(), 0, ints, 19, 3);\n        ints[22] = net.minecraft.world.level.dimension.DimensionType.WAY_BELOW_MIN_Y;\n        ints[23] = Block.getId(Blocks.WATER.defaultBlockState());\n        ints[24] = Block.getId(Blocks.LAVA.defaultBlockState());\n        long[] longs = {binding.seedA(), binding.seedB(), Double.doubleToRawLongBits(binding.barrierXz()), Double.doubleToRawLongBits(binding.barrierY())};\n        int status = (int)AQUIFER.invokeExact(handle, MemorySegment.ofArray(ints), ints.length, MemorySegment.ofArray(longs),\n            MemorySegment.ofArray(binding.locations()), binding.locations().length, MemorySegment.ofArray(binding.cache()),\n            MemorySegment.ofArray(binding.surface()), binding.surface().length, MemorySegment.ofArray(binding.memo()), MemorySegment.ofArray(binding.present()),\n            binding.memo().length, binding.sources().handle(), binding.levels().handle(), binding.barrierState());\n        if (status != 0) throw new IllegalStateException("Native noise fill aquifer rejected: " + status);\n        AQUIFER_FILLS.incrementAndGet();\n        return binding;\n    }\n\n    /** doFill\'s cell loop run by Rust; Java prepares aquifer materials on request\n     * unless Rust owns the aquifer\'s state for the fill. */\n'),
        ('    // Chunks whose cells Rust traversed, so tests can prove the gate engaged.\n',
         '    // Chunks whose cells Rust traversed, so tests can prove the gate engaged.\n    // Traversals whose aquifer materials Rust prepared itself, so tests can prove the route engaged.\n    static final java.util.concurrent.atomic.AtomicLong AQUIFER_FILLS = new java.util.concurrent.atomic.AtomicLong();\n    // Tests compare Rust-owned aquifer materials with Java-prepared ones in one JVM.\n    static volatile boolean nativeAquifer = !Boolean.getBoolean("mattmc.worldgen.javaFillMaterials");\n'),
        ('    // A whole column of cells: an ordinary downcall with off-heap buffers.\n',
         '    // A whole column of cells: an ordinary downcall with off-heap buffers.\n    // The traversal takes the aquifer\'s state once and returns its caches after the fill.\n    private static final MethodHandle AQUIFER = bind("aquifer", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,\n        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,\n        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));\n    private static final MethodHandle AQUIFER_CACHES = bind("aquifer_caches", true, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,\n        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NativeSurfaceChunk.java': [
        ('            this.storage.close();\n',
         '            this.storage.close();\n            java.lang.ref.Reference.reachabilityFence(this.keep);\n'),
        ('        }\n        return new NativeSurfaceChunk(handle, storage);\n',
         '        }\n        return new NativeSurfaceChunk(handle, storage, keep);\n'),
        ('        try {\n            handle = (long)CREATE.invokeExact(storage.handle, MemorySegment.ofArray(intArray), intArray.length, biomes == null ? 0L : biomes.biomeZoomSeed);\n',
         '        try {\n            handle = (long)CREATE.invokeExact(storage.handle, MemorySegment.ofArray(intArray), intArray.length, biomes == null ? 0L : biomes.biomeZoomSeed,\n                MemorySegment.ofArray(slots.longs()), MemorySegment.ofArray(slots.doubles()), MemorySegment.ofArray(inputs));\n'),
        ('        it.unimi.dsi.fastutil.ints.IntArrayList ints = new it.unimi.dsi.fastutil.ints.IntArrayList();\n        ints.addElements(0, new int[]{Block.getId(defaultBlock), rule.usesBiomes() ? 1 : 0, qx, qy, qz, sizeX, sizeY, sizeZ, steep.length});\n        for (boolean slot : steep) ints.add(slot ? 1 : 0);\n',
         "        it.unimi.dsi.fastutil.ints.IntArrayList ints = new it.unimi.dsi.fastutil.ints.IntArrayList();\n        ints.addElements(0, new int[]{Block.getId(defaultBlock), rule.usesBiomes() ? 1 : 0, qx, qy, qz, sizeX, sizeY, sizeZ, slots.ints().length / 4});\n        ints.addElements(ints.size(), slots.ints());\n        // The system's band offset and secondary noises, and the noise chunk's\n        // preliminary surface program for minimum surface levels.\n        SurfaceSystem system = rule.system();\n        NoiseChunk noise = rule.noiseChunk();\n        NativeSurfaceLevel levels = noise != null && noise.getClass() == NoiseChunk.class ? noise.nativeSurfaceLevel() : null;\n        if (levels != null) keep.add(levels);\n        long[] inputs = nativeConditions\n            ? new long[]{address(system.clayBandsOffsetNoise(), keep), address(system.surfaceSecondaryNoise(), keep), levels == null ? 0L : levels.handle()}\n            : new long[3];\n"),
        ('        int sizeX = 6, sizeZ = 6, sizeY = ((minY + height - 2) >> 2) + 1 - qy + 1;\n        boolean[] steep = rule.steepSlots();\n',
         '        int sizeX = 6, sizeZ = 6, sizeY = ((minY + height - 2) >> 2) + 1 - qy + 1;\n        NativeSurface.Slots slots = nativeConditions ? rule.nativeSlots() : rule.javaSlots();\n        List<Object> keep = new ArrayList<>(slots.keep());\n'),
        ('        this.storage = storage;\n',
         "        this.storage = storage;\n        this.keep = keep;\n    }\n\n    /** A noise's native state address for Rust, or 0 to keep Java answering. */\n    private static long address(NormalNoise noise, List<Object> keep) {\n        var state = noise.nativeState();\n        if (state == null || !state.state().isNative()) return 0L;\n        keep.add(state);\n        return state.state().address();\n"),
        ('\n    private NativeSurfaceChunk(long handle, NativeProtoChunk storage) {\n',
         '\n    private NativeSurfaceChunk(long handle, NativeProtoChunk storage, List<Object> keep) {\n'),
        ('    private final NativeProtoChunk storage;\n',
         '    private final NativeProtoChunk storage;\n    // The noise states and programs Rust reads; they stay reachable with the stage.\n    private final List<Object> keep;\n'),
        ('    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceStorage");\n',
         '    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceStorage");\n    // Comparison runs keep Java answering conditions and surface inputs on Rust storage.\n    static volatile boolean nativeConditions = !Boolean.getBoolean("mattmc.worldgen.javaSurfaceConditions");\n'),
        ('    private static final MethodHandle CREATE = bind("create", true, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,\n        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));\n',
         '    private static final MethodHandle CREATE = bind("create", true, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,\n        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));\n'),
        ('import java.util.concurrent.atomic.AtomicLong;\n',
         'import java.util.concurrent.atomic.AtomicLong;\nimport net.minecraft.world.level.levelgen.synth.NormalNoise;\n'),
        ('import java.lang.invoke.MethodHandle;\n',
         'import java.lang.invoke.MethodHandle;\nimport java.util.ArrayList;\nimport java.util.List;\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/NativeSurface.java': [
        ('    private void respond(int x, int z) {\n',
         '    private void respond(int x, int z) {\n        if (frame[17] >= -3 && frame[17] < conditionSources.length) count(frame[17]);\n'),
        ('            return status;\n        } catch (Throwable t) { throw failure(t); }\n    }\n',
         '            return status;\n        } catch (Throwable t) { throw failure(t); }\n    }\n    // Requests Java answered, by kind: band offset, secondary noise, minimum\n    // surface level, steep, vertical gradient, noise threshold, other conditions.\n    static final java.util.concurrent.atomic.AtomicLongArray JAVA_ANSWERS = new java.util.concurrent.atomic.AtomicLongArray(7);\n\n    private void count(int request) {\n        int kind;\n        if (request < 0) {\n            kind = request == -1 ? 0 : request == -2 ? 1 : request == -3 ? 2 : 6;\n        } else {\n            var source = conditionSources[request];\n            kind = source == SurfaceRules.Steep.INSTANCE ? 3 : source instanceof SurfaceRules.VerticalGradientConditionSource ? 4\n                : source instanceof SurfaceRules.NoiseThresholdConditionSource ? 5 : 6;\n        }\n        JAVA_ANSWERS.incrementAndGet(kind);\n    }\n\n'),
        ('\n    boolean[] steepSlots() {\n        return steepSlots.clone();\n',
         "\n    /** How Rust answers each condition slot: per slot [kind (0 Java, 1 steep,\n     * 2 vertical gradient, 3 noise threshold), low, high, positional random\n     * kind] ints, two longs (gradient seeds or the noise state's address) and\n     * two doubles (threshold bounds); {@code keep} holds what must stay live. */\n    record Slots(int[] ints, long[] longs, double[] doubles, List<Object> keep) {}\n\n    /** Every slot answered by Java except steep (comparison runs). */\n    Slots javaSlots() {\n        int[] ints = new int[conditionSources.length * 4];\n        for (int slot = 0; slot < conditionSources.length; slot++) if (conditionSources[slot] == SurfaceRules.Steep.INSTANCE) ints[slot * 4] = 1;\n        return new Slots(ints, new long[conditionSources.length * 2], new double[conditionSources.length * 2], List.of());\n    }\n\n    Slots nativeSlots() {\n        int count = conditionSources.length;\n        int[] ints = new int[count * 4];\n        long[] longs = new long[count * 2];\n        double[] doubles = new double[count * 2];\n        List<Object> keep = new ArrayList<>();\n        for (int slot = 0; slot < count; slot++) {\n            var source = conditionSources[slot];\n            if (source == SurfaceRules.Steep.INSTANCE) {\n                ints[slot * 4] = 1;\n            } else if (source instanceof SurfaceRules.VerticalGradientConditionSource gradient) {\n                // The condition's own bounds and factory, as apply() resolves them.\n                var factory = context.randomState.getOrCreateRandomFactory(gradient.randomName());\n                if (factory.getClass() == XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class) {\n                    var xoroshiro = (XoroshiroRandomSource.XoroshiroPositionalRandomFactory)factory;\n                    System.arraycopy(new int[]{2, gradient.trueAtAndBelow().resolveY(context.context), gradient.falseAtAndAbove().resolveY(context.context), 1},\n                        0, ints, slot * 4, 4);\n                    longs[slot * 2] = xoroshiro.seedLo();\n                    longs[slot * 2 + 1] = xoroshiro.seedHi();\n                } else if (factory.getClass() == LegacyRandomSource.LegacyPositionalRandomFactory.class) {\n                    System.arraycopy(new int[]{2, gradient.trueAtAndBelow().resolveY(context.context), gradient.falseAtAndAbove().resolveY(context.context), 2},\n                        0, ints, slot * 4, 4);\n                    longs[slot * 2] = ((LegacyRandomSource.LegacyPositionalRandomFactory)factory).seed();\n                }\n            } else if (source instanceof SurfaceRules.NoiseThresholdConditionSource threshold) {\n                var state = context.randomState.getOrCreateNoise(threshold.noise()).nativeState();\n                if (state != null && state.state().isNative()) {\n                    ints[slot * 4] = 3;\n                    longs[slot * 2] = state.state().address();\n                    doubles[slot * 2] = threshold.minThreshold();\n                    doubles[slot * 2 + 1] = threshold.maxThreshold();\n                    keep.add(state);\n                }\n            }\n        }\n        return new Slots(ints, longs, doubles, keep);\n    }\n\n    /** The context's noise chunk, for its preliminary surface levels. */\n    NoiseChunk noiseChunk() {\n        return context.noiseChunk();\n    }\n\n    SurfaceSystem system() {\n        return context.system;\n"),
        ('                if (!known) canBatch = false;\n                if (source == SurfaceRules.Steep.INSTANCE) steep.add(conditions.size());\n                int slot = conditions.size(); conditions.add(source.apply(context)); pc = emit(2, slot, xz ? 1 : 0, 0, 0);\n',
         '                if (!known) canBatch = false;\n                int slot = conditions.size(); conditions.add(source.apply(context)); sources.add(source); pc = emit(2, slot, xz ? 1 : 0, 0, 0);\n'),
        ("                // Java's ordered bounds checks without a callback per block.\n                int slot = conditions.size(); conditions.add(source.apply(context));\n",
         "                // Java's ordered bounds checks without a callback per block.\n                int slot = conditions.size(); conditions.add(source.apply(context)); sources.add(source);\n"),
        ('        final List<SurfaceRules.SurfaceRule> rules = new ArrayList<>();\n        final java.util.Set<Integer> steep = new java.util.HashSet<>();\n',
         '        final List<SurfaceRules.SurfaceRule> rules = new ArrayList<>();\n        final List<SurfaceRules.ConditionSource> sources = new ArrayList<>();\n'),
        ('        }\n        program = compiler.words.stream().mapToInt(Integer::intValue).toArray();\n        conditions = compiler.conditions.toArray(SurfaceRules.Condition[]::new);\n        rules = compiler.rules.toArray(SurfaceRules.SurfaceRule[]::new);\n        steepSlots = new boolean[conditions.length];\n        for (int i = 0; i < steepSlots.length; i++) steepSlots[i] = compiler.steep.contains(i);\n        usesBiomes = compiler.usesBiomes;\n        canBatch = compiler.canBatch && this.biomeManager != null\n            && context.system.getClass() == SurfaceSystem.class\n            && context.chunk.getClass() == net.minecraft.world.level.chunk.ProtoChunk.class;\n        cache = new int[end / 8];\n',
         '        }\n        cache = new int[program[0] / 8];\n    }\n\n    /** The rule and registry by identity (hashing a rule tree would walk it), with the inputs compiling reads. */\n    private record Key(SurfaceRules.RuleSource source, Registry<Biome> registry, boolean biomes, int minY, int height) {\n        @Override\n        public boolean equals(Object other) {\n            return other instanceof Key key && key.source == this.source && key.registry == this.registry && key.biomes == this.biomes\n                && key.minY == this.minY && key.height == this.height;\n        }\n\n        @Override\n        public int hashCode() {\n            return ((System.identityHashCode(this.source) * 31 + System.identityHashCode(this.registry)) * 31 + (this.biomes ? 1 : 0)) * 31\n                + this.minY * 7919 + this.height;\n        }\n    }\n\n    private record Compiled(int[] program, SurfaceRules.ConditionSource[] sources, boolean usesBiomes) {}\n\n    // Comparison runs compile every context\'s program, as before the cache.\n    static volatile boolean cacheCompiled = !Boolean.getBoolean("mattmc.worldgen.surfaceCompileEachChunk");\n    // Batched programs per surface system; weak keys do not retain old RandomStates.\n    private static final java.util.Map<SurfaceSystem, ConcurrentHashMap<Key, Compiled>> COMPILED =\n        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());\n\n    private static void validate(int[] program) {\n'),
        ('        this.biomeManager = biomeManager != null && biomeManager.getClass() == BiomeManager.class ? biomeManager : null;\n        Compiler compiler = new Compiler(); compiler.rule(source, 0); compiler.emit(0, 0, 0, 0, 0);\n        int end = compiler.words.size(); compiler.words.set(0, end);\n        for (var data : compiler.biomeData) {\n            compiler.words.set(data[0] + 1, compiler.words.size());\n            for (int i = 1; i < data.length; i++) compiler.words.add(data[i]);\n',
         "        this.biomeManager = biomeManager != null && biomeManager.getClass() == BiomeManager.class ? biomeManager : null;\n        // A batched program depends only on the rule, the system's bands, the\n        // biome registry, native biomes and the generation height range: it is\n        // compiled and validated once per surface system, and each context only\n        // applies its condition sources.\n        Key key = new Key(source, registry, this.biomeManager != null, context.context.getMinGenY(), context.context.getGenDepth());\n        Compiled compiled = context.system.getClass() == SurfaceSystem.class && cacheCompiled\n            ? COMPILED.computeIfAbsent(context.system, system -> new ConcurrentHashMap<>()).get(key) : null;\n        if (compiled == null) {\n            Compiler compiler = new Compiler(); compiler.rule(source, 0); compiler.emit(0, 0, 0, 0, 0);\n            int end = compiler.words.size(); compiler.words.set(0, end);\n            for (var data : compiler.biomeData) {\n                compiler.words.set(data[0] + 1, compiler.words.size());\n                for (int i = 1; i < data.length; i++) compiler.words.add(data[i]);\n            }\n            program = compiler.words.stream().mapToInt(Integer::intValue).toArray();\n            conditions = compiler.conditions.toArray(SurfaceRules.Condition[]::new);\n            rules = compiler.rules.toArray(SurfaceRules.SurfaceRule[]::new);\n            conditionSources = compiler.sources.toArray(SurfaceRules.ConditionSource[]::new);\n            usesBiomes = compiler.usesBiomes;\n            validate(program);\n            if (compiler.canBatch && context.system.getClass() == SurfaceSystem.class && cacheCompiled) {\n                COMPILED.computeIfAbsent(context.system, system -> new ConcurrentHashMap<>()).put(key, new Compiled(program, conditionSources, usesBiomes));\n            }\n            canBatch = compiler.canBatch && this.biomeManager != null\n                && context.system.getClass() == SurfaceSystem.class\n                && context.chunk.getClass() == net.minecraft.world.level.chunk.ProtoChunk.class;\n        } else {\n            program = compiled.program();\n            conditionSources = compiled.sources();\n            conditions = new SurfaceRules.Condition[conditionSources.length];\n            for (int slot = 0; slot < conditions.length; slot++) conditions[slot] = conditionSources[slot].apply(context);\n            rules = new SurfaceRules.SurfaceRule[0];\n            usesBiomes = compiled.usesBiomes();\n            canBatch = this.biomeManager != null && context.chunk.getClass() == net.minecraft.world.level.chunk.ProtoChunk.class;\n"),
        ("    private final SurfaceRules.SurfaceRule[] rules;\n    // Condition slots holding the context's shared steep condition.\n    private final boolean[] steepSlots;\n",
         "    private final SurfaceRules.SurfaceRule[] rules;\n    // Each condition slot's source, for slots Rust can answer itself.\n    private final SurfaceRules.ConditionSource[] conditionSources;\n"),
        ('import java.util.List;\n',
         'import java.util.List;\nimport java.util.concurrent.ConcurrentHashMap;\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/SurfaceRules.java': [
        ('\t\t\treturn i << 4;\n',
         '\t\t\treturn i << 4;\n\t\t}\n\n\t\tNoiseChunk noiseChunk() {\n\t\t\treturn this.noiseChunk;\n'),
    ],
    'src/main/java/net/minecraft/world/level/levelgen/SurfaceSystem.java': [
        ('\t\treturn this.clayBands[(j + l + this.clayBands.length) % this.clayBands.length];\n\t}\n\n',
         '\t\treturn this.clayBands[(j + l + this.clayBands.length) % this.clayBands.length];\n\t}\n\n\t/** The band offset and secondary surface noises, for native surface stages. */\n\tNormalNoise clayBandsOffsetNoise() {\n\t\treturn this.clayBandsOffsetNoise;\n\t}\n\n\tNormalNoise surfaceSecondaryNoise() {\n\t\treturn this.surfaceSecondaryNoise;\n\t}\n\n'),
    ],
    'src/main/rust/world/level/levelgen/aquifer/substance.rs': [
        ('    }\n}\n',
         "    }\n}\n\n/// An aquifer's native state owned by Rust for a whole NOISE fill: copies of\n/// Java's caches (centres, statuses, surface levels, FlatCache corners) that\n/// Java copies back afterwards, and its policy, programs and barrier noise.\npub(crate) struct OwnedAquifer {\n    pub grid: Vec<i64>,\n    pub shape: [i32; 5],\n    pub cache: Vec<i32>,\n    pub random: Positional,\n    pub skip_y: i32,\n    pub policy: [i32; 8],\n    pub surface_rect: [i32; 4],\n    pub surface: Vec<i32>,\n    /// The sources and preliminary surface programs Java keeps alive for the fill.\n    pub sources: *const Program,\n    pub levels: *const Program,\n    /// The chunk's FlatCache grid [first quart X, first quart Z, size] and its memo.\n    pub flat: [i32; 3],\n    pub memo: Vec<f64>,\n    pub present: Vec<u8>,\n    pub barrier: *const State,\n    pub barrier_xz: f64,\n    pub barrier_y: f64,\n    pub water: i32,\n    pub lava: i32,\n    pub way_below: i32,\n    // The batch frame and values `NativeAquifer.prepareMaterials` keeps.\n    frame: [i32; 32],\n    values: [f64; 2],\n}\n\nimpl OwnedAquifer {\n    #[allow(clippy::too_many_arguments)]\n    pub(crate) fn new(grid: Vec<i64>, shape: [i32; 5], cache: Vec<i32>, random: Positional, skip_y: i32, policy: [i32; 8], surface_rect: [i32; 4],\n        surface: Vec<i32>, sources: *const Program, levels: *const Program, flat: [i32; 3], memo: Vec<f64>, present: Vec<u8>,\n        barrier: *const State, barrier_xz: f64, barrier_y: f64, water: i32, lava: i32, way_below: i32) -> Self {\n        OwnedAquifer { grid, shape, cache, random, skip_y, policy, surface_rect, surface, sources, levels, flat, memo, present, barrier, barrier_xz,\n            barrier_y, water, lava, way_below, frame: [0; 32], values: [0.0; 2] }\n    }\n\n    /// A substance evaluator over this state.\n    pub(crate) fn substance(&mut self) -> Substance<'_> {\n        Substance {\n            grid: &mut self.grid,\n            shape: self.shape,\n            cache: &mut self.cache,\n            random: self.random,\n            skip_y: self.skip_y,\n            policy: self.policy,\n            surface_rect: self.surface_rect,\n            surface: &mut self.surface,\n            sources: unsafe { &*self.sources },\n            levels: unsafe { &*self.levels },\n            binding: Binding { first_x: self.flat[0], first_z: self.flat[1], size: self.flat[2], memo: &mut self.memo, present: &mut self.present },\n            barrier: self.barrier,\n            barrier_xz: self.barrier_xz,\n            barrier_y: self.barrier_y,\n            water: self.water,\n            lava: self.lava,\n            way_below: self.way_below,\n        }\n    }\n\n    /// `NativeAquifer.prepareMaterials` for the cell at block (x, y, z) of\n    /// `width` x `width` x `height` with its densities: (state, schedule) per\n    /// block into `out`, statuses computed natively instead of asked of Java.\n    pub(crate) fn cell_materials(&mut self, x: i32, y: i32, z: i32, width: i32, height: i32, density: &[f64], out: &mut [i32]) -> Result<(), i32> {\n        if !fill_cell_locations(&mut self.grid, &self.shape, self.random, x, y, z, width, height) {\n            return Err(-3);\n        }\n        let p = self.policy;\n        let f = &mut self.frame;\n        f[14] = p[7];\n        f[16] = 0;\n        f[17] = 0;\n        f[18] = x;\n        f[19] = y;\n        f[20] = z;\n        f[21] = width;\n        f[22] = height;\n        f[23] = self.skip_y;\n        f[24] = (-54i32).min(p[2]);\n        f[25] = p[0];\n        f[26] = p[1];\n        f[27] = p[2];\n        f[28] = p[3];\n        f[29] = if p[3] == self.lava { 2 } else if p[3] == self.water { 1 } else { 0 };\n        f[30] = p[5];\n        f[31] = p[6];\n        loop {\n            let status = unsafe {\n                cell::materials(&mut self.frame, &mut self.values, density, out, &self.grid, &self.shape, &self.cache, self.barrier, self.barrier_xz,\n                    self.barrier_y)\n            };\n            match status {\n                0 => return Ok(()),\n                1 => {\n                    let index = self.frame[9] as usize;\n                    self.substance().status(index)?;\n                }\n                4 => {}\n                other => return Err(other.min(-1)),\n            }\n        }\n    }\n}\n"),
        ("    /// The fluid status at a grid index's centre (`getAquiferStatus`).\n    fn status(&mut self, index: usize) -> Result<(), i32> {\n",
         "    /// The fluid status at a grid index's centre (`getAquiferStatus`).\n    pub(crate) fn status(&mut self, index: usize) -> Result<(), i32> {\n"),
    ],
    'src/main/rust/world/level/levelgen/math.rs': [
        ('    )\n}\n',
         "    )\n}\n\n/// Java's `Math.round(double)`: halves round toward positive infinity, NaN is\n/// 0, out-of-range values saturate (bit-exact port of the JDK algorithm).\npub(crate) fn java_round(a: f64) -> i64 {\n    let bits = a.to_bits() as i64;\n    let biased_exp = (bits & 0x7FF0_0000_0000_0000) >> 52;\n    let shift = (53 - 2 + 1023) - biased_exp;\n    if shift & -64 == 0 {\n        let mut r = (bits & 0x000F_FFFF_FFFF_FFFF) | (0x000F_FFFF_FFFF_FFFF + 1);\n        if bits < 0 {\n            r = -r;\n        }\n        ((r >> shift) + 1) >> 1\n    } else {\n        a as i64\n    }\n}\n\n#[cfg(test)]\nmod tests {\n    use super::java_round;\n\n    #[test]\n    fn java_round_matches_math_round() {\n        assert_eq!(java_round(0.5), 1);\n        assert_eq!(java_round(-0.5), 0);\n        assert_eq!(java_round(-2.5), -2);\n        assert_eq!(java_round(2.5), 3);\n        assert_eq!(java_round(0.49999999999999994), 0);\n        assert_eq!(java_round(-1.5000000000000002), -2);\n        assert_eq!(java_round(f64::NAN), 0);\n        assert_eq!(java_round(1e20), i64::MAX);\n        assert_eq!(java_round(-1e20), i64::MIN);\n        assert_eq!(java_round(4503599627370497.0), 4503599627370497);\n    }\n}\n"),
    ],
    'src/main/rust/world/level/levelgen/noise_fill/ffi.rs': [
        ('    unsafe { traversal.set_beardifier(data, kernel) };\n    0\n}\n',
         '    unsafe { traversal.set_beardifier(data, kernel) };\n    0\n}\n\n/// Hands the chunk\'s aquifer state to the bound traversal, which then prepares\n/// cell materials itself. `ints`: [shape (5), skipY, location kind (1\n/// Xoroshiro, 2 Legacy), policy (8), surface rect (4), FlatCache grid (3),\n/// wayBelow, water id, lava id]; `longs` [seed a, seed b, barrier xz scale and\n/// y scale as raw bits]; the caches are copied (`grid` and `cache` with\n/// `grid_len` entries, `cache` three per entry). Returns 0 or negative.\n/// # Safety\n/// Buffers hold their stated counts for this call; `sources` and `levels`\n/// are programs and `barrier` null or a noise state that Java keeps alive for\n/// the fill.\n#[no_mangle]\npub unsafe extern "C" fn mattmc_noise_fill_aquifer(id: u64, ints: *const i32, int_count: i32, longs: *const i64, grid: *const i64, grid_len: i32,\n    cache: *const i32, surface: *const i32, surface_len: i32, memo: *const f64, present: *const u8, memo_len: i32, sources: u64, levels: u64,\n    barrier: *const State) -> i32 {\n    let Some(traversal) = unsafe { handle(id) }.traversal.as_mut() else { return -1 };\n    if ints.is_null() || longs.is_null() || grid.is_null() || cache.is_null() || surface.is_null() || memo.is_null() || present.is_null()\n        || int_count != 27 || grid_len <= 0 || surface_len <= 0 || memo_len < 0 || sources == 0 || levels == 0\n    {\n        return -1;\n    }\n    let i = unsafe { std::slice::from_raw_parts(ints, 27) };\n    let l = unsafe { std::slice::from_raw_parts(longs, 4) };\n    let Some(random) = Positional::from_abi(i[6], l[0], l[1]) else { return -1 };\n    let shape = [i[0], i[1], i[2], i[3], i[4]];\n    let rect = [i[15], i[16], i[17], i[18]];\n    let flat = [i[19], i[20], i[21]];\n    let program = unsafe { &*(sources as *const Program) };\n    if shape[3] <= 0 || shape[4] <= 0 || grid_len as usize % (shape[3] as usize * shape[4] as usize) != 0 || surface_len != rect[2] * rect[3] * 2\n        || !(1..=64).contains(&flat[2]) || program.root_count() != 5 || memo_len as usize != program.point_slots() * (flat[2] * flat[2]) as usize\n    {\n        return -2;\n    }\n    let mut policy = [0; 8];\n    policy.copy_from_slice(&i[7..15]);\n    let copy = |p: *const i32, n: usize| unsafe { std::slice::from_raw_parts(p, n) }.to_vec();\n    traversal.set_aquifer(OwnedAquifer::new(\n        unsafe { std::slice::from_raw_parts(grid, grid_len as usize) }.to_vec(),\n        shape,\n        copy(cache, grid_len as usize * 3),\n        random,\n        i[5],\n        policy,\n        rect,\n        copy(surface, surface_len as usize),\n        program,\n        levels as *const Program,\n        flat,\n        unsafe { std::slice::from_raw_parts(memo, memo_len as usize) }.to_vec(),\n        unsafe { std::slice::from_raw_parts(present, memo_len as usize) }.to_vec(),\n        barrier,\n        f64::from_bits(l[2] as u64),\n        f64::from_bits(l[3] as u64),\n        i[23],\n        i[24],\n        i[22],\n    ));\n    0\n}\n\n/// Copies the Rust-owned aquifer\'s caches back into Java\'s arrays (their\n/// lengths as given to `mattmc_noise_fill_aquifer`). Returns 0 or negative.\n/// # Safety\n/// Buffers are writable for their counts and borrowed for this call.\n#[no_mangle]\npub unsafe extern "C" fn mattmc_noise_fill_aquifer_caches(id: u64, grid: *mut i64, cache: *mut i32, surface: *mut i32, memo: *mut f64, present: *mut u8) -> i32 {\n    let Some(aquifer) = unsafe { handle(id) }.traversal.as_ref().and_then(|t| t.aquifer()) else { return -1 };\n    if grid.is_null() || cache.is_null() || surface.is_null() || memo.is_null() || present.is_null() {\n        return -1;\n    }\n    unsafe {\n        std::ptr::copy_nonoverlapping(aquifer.grid.as_ptr(), grid, aquifer.grid.len());\n        std::ptr::copy_nonoverlapping(aquifer.cache.as_ptr(), cache, aquifer.cache.len());\n        std::ptr::copy_nonoverlapping(aquifer.surface.as_ptr(), surface, aquifer.surface.len());\n        std::ptr::copy_nonoverlapping(aquifer.memo.as_ptr(), memo, aquifer.memo.len());\n        std::ptr::copy_nonoverlapping(aquifer.present.as_ptr(), present, aquifer.present.len());\n    }\n    0\n}\n'),
        ('        }\n        Err(super::Error::UnknownState(_)) => -2,\n        Err(super::Error::OutOfChunk) => -3,\n        Err(super::Error::CellProgram) => -4,\n',
         '        }\n        Err(super::Error::UnknownState(_)) => -2,\n        Err(super::Error::OutOfChunk) => -3,\n        Err(super::Error::CellProgram) => -4,\n        Err(super::Error::Aquifer(_)) => -5,\n'),
        ('        Ok(()) => 0,\n        Err(super::Error::UnknownState(_)) => -2,\n        Err(super::Error::OutOfChunk) => -3,\n        Err(super::Error::CellProgram) => -4,\n',
         '        Ok(()) => 0,\n        Err(super::Error::UnknownState(_)) => -2,\n        Err(super::Error::OutOfChunk) => -3,\n        Err(super::Error::CellProgram) => -4,\n        Err(super::Error::Aquifer(_)) => -5,\n'),
        ('use crate::world::level::levelgen::density::validation::density_validate;\nuse crate::world::level::levelgen::router::Router;\n',
         'use crate::world::level::levelgen::density::validation::density_validate;\nuse crate::world::level::levelgen::aquifer::substance::OwnedAquifer;\nuse crate::world::level::levelgen::router::{Program, Router};\n'),
    ],
    'src/main/rust/world/level/levelgen/noise_fill/mod.rs': [
        ('    CellProgram,\n',
         "    CellProgram,\n    /// The Rust-owned aquifer failed to prepare a cell's materials.\n    Aquifer(i32),\n"),
    ],
    'src/main/rust/world/level/levelgen/noise_fill/traversal.rs': [
        ('            }\n            fill.fill_cell(&cell)?;\n',
         '            }\n'),
        ('            if materials.is_none() && fill.needs_materials(&cell) {\n                self.pending = true;\n                return Ok(Step::Materials([x, y, z]));\n',
         "            if materials.is_none() && fill.needs_materials(&cell) {\n                let Some(aquifer) = self.aquifer.as_mut() else {\n                    self.pending = true;\n                    return Ok(Step::Materials([x, y, z]));\n                };\n                // The batch the cell's first such block would prepare, decided natively.\n                aquifer.cell_materials(x, y, z, width, height, &self.density, &mut self.native_materials).map_err(Error::Aquifer)?;\n                fill.fill_cell(&Cell { materials: &self.native_materials, ..cell })?;\n            } else {\n                fill.fill_cell(&cell)?;\n"),
        ('        Traversal { router, program, inputs, ore, layout, slices: [vec![0.; size], vec![0.; size]], density: vec![0.; cell], frame, beardifier: None, beard: vec![0.; cell],\n            cursor: 0, pending: false }\n',
         "        Traversal { router, program, inputs, ore, layout, slices: [vec![0.; size], vec![0.; size]], density: vec![0.; cell], frame, beardifier: None, beard: vec![0.; cell],\n            cursor: 0, pending: false, aquifer: None, native_materials: vec![0; cell * 2] }\n    }\n\n    /// Hands the chunk's aquifer state to the traversal for the rest of the fill.\n    pub(crate) fn set_aquifer(&mut self, aquifer: OwnedAquifer) {\n        self.aquifer = Some(aquifer);\n    }\n\n    pub(crate) fn aquifer(&self) -> Option<&OwnedAquifer> {\n        self.aquifer.as_ref()\n"),
        ('    pending: bool,\n',
         "    pending: bool,\n    /// The chunk's aquifer, when Rust owns its state for the fill: cells\n    /// needing batch materials get them here instead of from Java.\n    aquifer: Option<OwnedAquifer>,\n    native_materials: Vec<i32>,\n"),
        ('//! requests and fills a slice the router declines.\n',
         '//! requests and fills a slice the router declines.\nuse crate::world::level::levelgen::aquifer::substance::OwnedAquifer;\n'),
    ],
    'src/main/rust/world/level/levelgen/surface/chunk.rs': [
        ('        height(o, j) >= height(p, j) + 4\n    }\n}\n',
         "        height(o, j) >= height(p, j) + 4\n    }\n}\n\n#[cfg(test)]\nmod tests {\n    use super::band_offset;\n\n    #[test]\n    fn band_offset_rounds_halves_like_java() {\n        // Java's Math.round takes halves up: -2.5 -> -2, 2.5 -> 3.\n        assert_eq!(band_offset(-0.625), -2);\n        assert_eq!(band_offset(0.625), 3);\n        assert_eq!(band_offset(f64::NAN), 0);\n    }\n}\n"),
        ('                _ => continue,\n            }\n        }\n',
         "                _ => continue,\n            }\n        }\n    }\n\n    /// Answers the evaluator's request natively when its inputs are native;\n    /// false leaves it to Java.\n    fn answer(&mut self, frame: &mut [i32]) -> Result<bool, Error> {\n        let (x, z) = (self.x, self.z);\n        let y = frame[MIN_Y].wrapping_add(frame[Y_INDEX]);\n        let request = frame[REQUEST];\n        let condition = |value: bool, frame: &mut [i32]| {\n            frame[ANSWER] = value as i32;\n            frame[ANSWER_READY] = 1;\n        };\n        match request {\n            -1 if !self.inputs.band.is_null() => {\n                // SurfaceSystem.bandOffset: (int)Math.round(noise * 4.0).\n                let noise = unsafe { noise_eval(self.inputs.band, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };\n                frame[BAND_OFFSET] = band_offset(noise);\n                frame[BAND_OFFSET_READY] = 1;\n            }\n            -2 if !self.inputs.secondary.is_null() => {\n                self.secondary = unsafe { noise_eval(self.inputs.secondary, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };\n                frame[SECONDARY_READY] = 1;\n            }\n            -3 if !self.inputs.levels.is_null() => {\n                frame[MIN_SURFACE] = self.min_surface_level(frame[SURFACE_DEPTH])?;\n                frame[MIN_SURFACE_READY] = 1;\n            }\n            slot if slot >= 0 => match self.slots.get(slot as usize).copied().unwrap_or(Slot::Java) {\n                Slot::Java => return Ok(false),\n                Slot::Steep => {\n                    if self.steep_answer < 0 {\n                        self.steep_answer = self.steep() as i8;\n                    }\n                    condition(self.steep_answer != 0, frame);\n                }\n                Slot::Gradient { low, high, random } => {\n                    // The evaluator asks only inside the band: map(y, low, high, 1, 0).\n                    let (yd, lo, hi) = (y as f64, low as f64, high as f64);\n                    let d = crate::world::level::levelgen::math::lerp((yd - lo) / (hi - lo), 1.0, 0.0);\n                    condition(((random.at(x, y, z).next_float()) as f64) < d, frame);\n                }\n                Slot::Noise { state, min, max } => {\n                    let value = unsafe { noise_eval(state, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };\n                    condition(value >= min && value <= max, frame);\n                }\n            },\n            _ => return Ok(false),\n        }\n        Ok(true)\n    }\n\n    /// `SurfaceRules.Context.getMinSurfaceLevel`: the preliminary surface levels\n    /// at the column's surface cell corners, interpolated, plus surface depth - 8.\n    fn min_surface_level(&mut self, surface_depth: i32) -> Result<i32, Error> {\n        let (i, j) = (self.x >> 4, self.z >> 4);\n        let corners = match self.corners {\n            Some((cell, corners)) if cell == (i, j) => corners,\n            _ => {\n                let levels = unsafe { &*self.inputs.levels };\n                let xs = [i << 4, (i + 1) << 4, i << 4, (i + 1) << 4];\n                let zs = [j << 4, j << 4, (j + 1) << 4, (j + 1) << 4];\n                let mut corners = [0i32; 4];\n                with_frame(levels, |frame| levels.surface_levels(frame, &xs, &zs, &mut corners)).map_err(|_| Error::Evaluation(-5))?;\n                self.corners = Some(((i, j), corners));\n                corners\n            }\n        };\n        let fx = ((self.x & 15) as f32 / 16.0f32) as f64;\n        let fz = ((self.z & 15) as f32 / 16.0f32) as f64;\n        let level = lerp2(fx, fz, corners[0] as f64, corners[1] as f64, corners[2] as f64, corners[3] as f64);\n        // Mth.floor, then + surfaceDepth - 8.\n        let k = level as i32;\n        let k = if level < k as f64 { k.wrapping_sub(1) } else { k };\n        Ok(k.wrapping_add(surface_depth).wrapping_sub(8))\n"),
        ('                    }\n                    return Ok(2);\n',
         '                    }\n'),
        ('                2 => {\n                    let request = frame[REQUEST];\n                    if request >= 0 && self.steep.get(request as usize).copied().unwrap_or(false) {\n                        if self.steep_answer < 0 {\n                            self.steep_answer = self.steep() as i8;\n                        }\n                        frame[ANSWER] = self.steep_answer as i32;\n                        frame[ANSWER_READY] = 1;\n                        continue;\n',
         '                2 => {\n                    if !self.answer(frame)? {\n                        return Ok(2);\n'),
        ('        loop {\n',
         "        loop {\n            // A natively answered secondary noise is the column's; Java's otherwise.\n            let secondary = if self.inputs.secondary.is_null() { secondary } else { self.secondary };\n"),
        ('        self.steep_answer = -1;\n',
         '        self.steep_answer = -1;\n        self.secondary = 0.0;\n'),
        ('            biomes,\n            steep,\n',
         '            biomes,\n            slots,\n            inputs,\n            secondary: 0.0,\n            corners: None,\n'),
        ('    /// `storage` is live and used only through this stage while it runs.\n    pub(crate) unsafe fn new(storage: *mut ProtoStorage, default_block: i32, biomes: Option<Biomes>, steep: Vec<bool>) -> Result<Self, Error> {\n',
         '    /// `storage` is live and used only through this stage while it runs.\n    pub(crate) unsafe fn new(storage: *mut ProtoStorage, default_block: i32, biomes: Option<Biomes>, slots: Vec<Slot>, inputs: Inputs) -> Result<Self, Error> {\n'),
        ('    biomes: Option<Biomes>,\n    steep: Vec<bool>,\n',
         "    biomes: Option<Biomes>,\n    slots: Vec<Slot>,\n    inputs: Inputs,\n    /// The column's secondary surface noise once answered natively.\n    secondary: f64,\n    /// The preliminary surface levels at the last surface cell's corners, as\n    /// the context's `preliminarySurfaceCache` keeps them across columns.\n    corners: Option<((i32, i32), [i32; 4])>,\n"),
        ('use crate::world::level::levelgen::proto_chunk::{self, ProtoStorage};\n',
         "use crate::world::level::levelgen::proto_chunk::{self, ProtoStorage};\nuse crate::world::level::levelgen::random::Positional;\nuse crate::world::level::levelgen::router::{with_frame, Program};\nuse crate::world::level::levelgen::synth::{noise_eval, State};\n\n/// How the stage answers a rule condition slot.\n#[derive(Clone, Copy, Debug)]\npub(crate) enum Slot {\n    /// A Java condition (temperature, extensions).\n    Java,\n    /// `SurfaceRules.Steep`: the context's shared lazy steep condition.\n    Steep,\n    /// A vertical gradient between its resolved bounds, with its positional random.\n    Gradient { low: i32, high: i32, random: Positional },\n    /// A noise threshold: `NormalNoise.getValue(x, 0, z)` in [min, max].\n    Noise { state: *const State, min: f64, max: f64 },\n}\n\n/// `SurfaceSystem.bandOffset` from its noise value: `(int)Math.round(noise * 4.0)`.\npub(crate) fn band_offset(noise: f64) -> i32 {\n    java_round(noise * 4.0) as i32\n}\n\n/// The surface system's own inputs, each native when Java could provide it.\n#[derive(Clone, Copy, Debug)]\npub(crate) struct Inputs {\n    /// `clayBandsOffsetNoise` for `bandOffset`, or null.\n    pub band: *const State,\n    /// `surfaceSecondaryNoise` for `getSurfaceSecondary`, or null.\n    pub secondary: *const State,\n    /// The noise chunk's preliminary surface level program for\n    /// `getMinSurfaceLevel`, or null.\n    pub levels: *const Program,\n}\n"),
        ('use crate::world::level::levelgen::noise_fill::{FLAG_AIR, FLAG_FLUID};\n',
         'use crate::world::level::levelgen::noise_fill::{FLAG_AIR, FLAG_FLUID};\nuse crate::world::level::levelgen::math::{java_round, lerp2};\n'),
        ("//! written as `BlockColumn.setBlock` writes it (the chunk's `setBlockState`,\n//! then a post-processing mark for fluids). Java callbacks still answer\n//! non-native conditions; `steep` is answered here from the storage's\n//! heightmap, once per column, as its shared lazy Java condition caches it.\n",
         "//! written as `BlockColumn.setBlock` writes it (the chunk's `setBlockState`,\n//! then a post-processing mark for fluids). The evaluator's requests are\n//! answered here when their inputs are native: `steep` from the storage's\n//! heightmap (once per column, as its shared lazy Java condition caches it),\n//! vertical gradients with their positional random, noise thresholds, the\n//! secondary noise, the terracotta band offset and the minimum surface level.\n//! Java answers the rest (temperature, extension conditions).\n"),
    ],
    'src/main/rust/world/level/levelgen/surface/ffi.rs': [
        ('    };\n    (at == i.len()).then_some((default_block, biomes, steep))\n',
         '    };\n    (at == i.len()).then_some((default_block, biomes, slots))\n'),
        ('    let mut at = 9;\n    let steep: Vec<bool> = i.get(at..at + slots)?.iter().map(|v| *v != 0).collect();\n    at += slots;\n',
         "    let mut at = 9;\n    let mut slots = Vec::with_capacity(count);\n    for k in 0..count {\n        let h = i.get(at..at + 4)?;\n        slots.push(match h[0] {\n            0 => Slot::Java,\n            1 => Slot::Steep,\n            // Without a band the evaluator's bounds answer every Y.\n            2 if h[1] >= h[2] => Slot::Java,\n            2 => Slot::Gradient { low: h[1], high: h[2], random: Positional::from_abi(h[3], l[k * 2], l[k * 2 + 1])? },\n            3 if l[k * 2] != 0 => Slot::Noise { state: l[k * 2] as *const State, min: d[k * 2], max: d[k * 2 + 1] },\n            _ => return None,\n        });\n        at += 4;\n    }\n"),
        ('    let (default_block, uses_biomes) = (i[0], i[1] != 0);\n    let (sizes, slots) = ([usize::try_from(i[5]).ok()?, usize::try_from(i[6]).ok()?, usize::try_from(i[7]).ok()?], usize::try_from(i[8]).ok()?);\n    if slots > 65536 || sizes.iter().any(|s| *s > 4096) {\n',
         '    let (default_block, uses_biomes) = (i[0], i[1] != 0);\n    let (sizes, count) = ([usize::try_from(i[5]).ok()?, usize::try_from(i[6]).ok()?, usize::try_from(i[7]).ok()?], usize::try_from(i[8]).ok()?);\n    if sizes.iter().any(|s| *s > 4096) {\n'),
        ('\nfn parse(i: &[i32], seed: i64) -> Option<(i32, Option<Biomes>, Vec<bool>)> {\n',
         '\nfn parse(i: &[i32], seed: i64, l: &[i64], d: &[f64]) -> Option<(i32, Option<Biomes>, Vec<Slot>)> {\n'),
        ('    let i = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };\n    let Some((default_block, biomes, steep)) = parse(i, seed) else { return 0 };\n    match unsafe { SurfaceChunk::new(storage as *mut ProtoStorage, default_block, biomes, steep) } {\n',
         '    let i = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };\n    let Ok(slots) = usize::try_from(i[8]) else { return 0 };\n    if slots > 65536 {\n        return 0;\n    }\n    let l = unsafe { std::slice::from_raw_parts(slot_longs, slots * 2) };\n    let d = unsafe { std::slice::from_raw_parts(slot_doubles, slots * 2) };\n    let n = unsafe { std::slice::from_raw_parts(inputs, 3) };\n    let Some((default_block, biomes, slots)) = parse(i, seed, l, d) else { return 0 };\n    let inputs = Inputs { band: n[0] as *const State, secondary: n[1] as *const State, levels: n[2] as *const Program };\n    match unsafe { SurfaceChunk::new(storage as *mut ProtoStorage, default_block, biomes, slots, inputs) } {\n'),
        ('#[no_mangle]\npub unsafe extern "C" fn mattmc_surface_chunk_create(storage: u64, ints: *const i32, int_count: i32, seed: i64) -> u64 {\n    if storage == 0 || ints.is_null() || int_count < 9 {\n',
         '#[no_mangle]\npub unsafe extern "C" fn mattmc_surface_chunk_create(storage: u64, ints: *const i32, int_count: i32, seed: i64, slot_longs: *const i64,\n    slot_doubles: *const f64, inputs: *const i64) -> u64 {\n    if storage == 0 || ints.is_null() || int_count < 9 || slot_longs.is_null() || slot_doubles.is_null() || inputs.is_null() {\n'),
        ('/// # Safety\n/// `ints` holds `int_count` values for this call; `storage` is live, outlives\n/// the stage and is used only through it while the stage runs.\n',
         '/// # Safety\n/// `ints` holds `int_count` values and the slot arrays `ints[8]` pairs, all\n/// borrowed for this call; `storage` is live, outlives the stage and is used\n/// only through it while the stage runs; every noise state and program in\n/// the slots and `inputs` stays live while the stage runs.\n'),
        ('/// `ints`: [defaultBlock, usesBiomes, quart origin x, y, z, quart sizes x, y,\n/// z, steepSlots, then steep flags per condition slot, then quart biome ids].\n',
         "/// `ints`: [defaultBlock, usesBiomes, quart origin x, y, z, quart sizes x, y,\n/// z, slotCount, then per condition slot (kind: 0 Java, 1 steep, 2 vertical\n/// gradient, 3 noise threshold; gradient low, high; positional random kind),\n/// then quart biome ids]. Per slot `slot_longs` holds two values (a gradient's\n/// seeds, or a noise threshold's state address) and `slot_doubles` two (a\n/// noise threshold's min and max). `inputs`: [band noise state, secondary\n/// noise state, preliminary surface program], each 0 when Java answers it.\n"),
        ('\nuse super::chunk::{Biomes, SurfaceChunk};\n',
         '\nuse super::chunk::{Biomes, Inputs, Slot, SurfaceChunk};\nuse crate::world::level::levelgen::random::Positional;\nuse crate::world::level::levelgen::router::Program;\nuse crate::world::level::levelgen::synth::State;\n'),
    ],
}

RUST = ['src/main/rust/world/level/levelgen/surface/chunk.rs', 'src/main/rust/world/level/levelgen/aquifer/substance.rs',
        'src/main/rust/world/level/levelgen/noise_fill/traversal.rs', 'src/main/rust/world/level/levelgen/router/mod.rs']


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
    parser.add_argument('--output', default='build/surface-conditions-migration/acceptance')
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
                    "gradle.rootProject.tasks.register('writeSurfaceConditionsClasspath') { dependsOn 'testClasses'; doLast { new File("
                    + json.dumps(str(classpath)) + ").text = gradle.rootProject.sourceSets.test.runtimeClasspath.asPath } } }\n")
    run(['./gradlew', '-I', str(init), '-PmattmcRustProfile=release', 'writeSurfaceConditionsClasspath', '-x', 'testRustNative', '--console=plain'], out / 'build.log')
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
    rust = run(['cargo', 'test', '--release', 'world::level::levelgen'], out / 'rust-tests.log', ROOT / 'src/main/rust')
    library = ROOT / 'build/rust/native/mattmc_rust-linux-x64.so'
    flags = ['-Xbatch', '-Xms512m', '-Xmx3g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '--enable-native-access=ALL-UNNAMED',
             '-XX:ActiveProcessorCount=' + str(1 + len(background))]
    base = ['taskset', '-c', ','.join(str(cpu) for cpu in [args.cpu, *background]), 'java', *flags,
            '-Dmattmc.rust.natives.dir=' + str(library.parent), '-cp', classpath.read_text().strip()]
    files = [ROOT / path for path in PRODUCTION_REWRITES] + [ROOT / path for path in RUST]
    files += [ROOT / 'src/main/java/net/minecraft/world/level/levelgen/NativeSurfaceChunk.java', Path(__file__).resolve(),
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeSurfaceChunkTest.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeSurfaceChunkVerification.java',
              ROOT / 'src/test/java/net/minecraft/world/level/levelgen/NativeNoiseFillTest.java']
    report = {'reference': REFERENCE, 'parity': parity, 'java_tests': java_tests, 'baseline': BASELINE, 'candidate': CANDIDATE,
              'rust_tests': [line for line in rust.splitlines() if 'test result' in line],
              'scope': ('SurfaceSystem.buildSurface per NOISE-filled chunk on Rust storage with multi-noise biomes: Java answering '
                        'gradients, noise thresholds, band offsets, secondary noise and minimum surface levels and compiling each '
                        "chunk's rule program, versus Rust answers and cached programs. NOISE fill excluded. No whole-game timing."),
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
