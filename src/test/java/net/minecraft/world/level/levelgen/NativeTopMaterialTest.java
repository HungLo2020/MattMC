package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The carvers' top material answered in Rust against Java's
 * {@code CarvingContext.topMaterial}: per request (the same storage state and
 * previous climate leaf, comparing the state and the leaf afterwards), and per
 * chunk with no Java upcall, over every vanilla setting, several seeds and
 * chunks straddling biome edges. */
class NativeTopMaterialTest {
    static final long[] SEEDS = {0, -7_340_013_412_337L, 42};

    @BeforeAll static void load() {
        NativeCarversTest.load();
    }

    @AfterAll static void close() {
        NativeTopMaterial.beforePublish = null;
        NativeTopMaterial.setVerify(false);
        NativeTopMaterial.setEnabled(true);
        NativeCarversTest.close();
    }

    /** Fixed positions plus chunks whose corner biomes differ (biome edges), found
     * on a coarse grid of the setting's own biome source. */
    static List<ChunkPos> positions(Holder<NoiseGeneratorSettings> setting, long seed) {
        return positions(setting, seed, 0);
    }

    /** {@link #positions} around chunk ({@code offset}, {@code offset}). */
    static List<ChunkPos> positions(Holder<NoiseGeneratorSettings> setting, long seed, int offset) {
        List<ChunkPos> positions = new ArrayList<>(List.of(new ChunkPos(offset, offset), new ChunkPos(offset - 1, offset + 3), new ChunkPos(offset + 37, offset - 91)));
        var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
        var source = NativeCarversTest.source(setting);
        int edges = 0;
        for (int gx = offset - 12; gx <= offset + 12 && edges < 3; gx += 4) {
            for (int gz = offset - 12; gz <= offset + 12 && edges < 3; gz += 4) {
                Set<Holder<Biome>> corners = new HashSet<>();
                for (int corner = 0; corner < 4; corner++) {
                    int qx = (gx * 16 + (corner & 1) * 15) >> 2, qz = (gz * 16 + (corner >> 1) * 15) >> 2;
                    corners.add(source.getNoiseBiome(qx, 16, qz, random.sampler()));
                }
                if (corners.size() > 1) {
                    positions.add(new ChunkPos(gx, gz));
                    edges++;
                }
            }
        }
        return positions;
    }

    /** Each native answer equals Java's from the same state and leaf; the stage
     * keeps Java's, so the chunk equals Java's carving. */
    @Test void everyRequestMatchesJavaTopMaterial() {
        long[] before = NativeTopMaterial.verification();
        int pairs = 0;
        NativeTopMaterial.setVerify(true);
        try {
            for (var setting : NativeNoiseFillTest.settings) {
                for (long seed : SEEDS) {
                    for (ChunkPos pos : positions(setting, seed)) {
                        String context = setting.key().location() + " seed " + seed + " " + pos;
                        var java = NativeCarversTest.carve(setting, seed, pos, false);
                        NativeCarversTest.compare(java, NativeCarversTest.carve(setting, seed, pos, true), context);
                        pairs++;
                    }
                }
            }
        } finally {
            NativeTopMaterial.setVerify(false);
        }
        long[] after = NativeTopMaterial.verification();
        long verified = after[0] - before[0], mismatched = after[1] - before[1];
        System.out.println("TOP_MATERIAL_VERIFY pairs=" + pairs + " verified=" + verified + " mismatched=" + mismatched);
        assertTrue(verified > 100, "the chunks reach top materials: " + verified);
        assertEquals(0, mismatched, () -> "first mismatch x,y,z,native,java,nativeLeaf,javaLeaf = "
            + java.util.Arrays.toString(java.util.Arrays.copyOfRange(after, 2, 9)));
    }

    /** A floor rule reaching every request kind the top material can make:
     * steep, noise thresholds, a vertical gradient through the carved range,
     * temperature, biomes, the secondary depth range, the preliminary surface and
     * the terracotta bands. Vanilla rules reach only biomes and the preliminary
     * surface from the carvers. */
    static Holder<NoiseGeneratorSettings> everyKind(Holder<NoiseGeneratorSettings> setting) {
        var block = (java.util.function.Function<net.minecraft.world.level.block.Block, SurfaceRules.RuleSource>)b -> SurfaceRules.state(b.defaultBlockState());
        var blocks = net.minecraft.world.level.block.Blocks.class;
        var rule = SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.steep(), block.apply(net.minecraft.world.level.block.Blocks.STONE)),
            SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.2), block.apply(net.minecraft.world.level.block.Blocks.GRAVEL)),
            SurfaceRules.ifTrue(SurfaceRules.verticalGradient("top_material_test", VerticalAnchor.absolute(-40), VerticalAnchor.absolute(90)),
                block.apply(net.minecraft.world.level.block.Blocks.DEEPSLATE)),
            SurfaceRules.ifTrue(SurfaceRules.temperature(), block.apply(net.minecraft.world.level.block.Blocks.ICE)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS, net.minecraft.world.level.biome.Biomes.ICE_SPIKES),
                block.apply(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)),
            SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(-6, true, 4, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                block.apply(net.minecraft.world.level.block.Blocks.SAND)),
            SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.bandlands()),
            block.apply(net.minecraft.world.level.block.Blocks.CLAY)));
        var c = setting.value();
        return Holder.direct(new NoiseGeneratorSettings(c.noiseSettings(), c.defaultBlock(), c.defaultFluid(), c.noiseRouter(), rule, c.spawnTarget(),
            c.seaLevel(), c.disableMobGeneration(), c.aquifersEnabled(), c.oreVeinsEnabled(), c.useLegacyRandomSource()));
    }

    /** Every request kind, per request against Java, from the overworld's carvers. */
    @Test void everyRequestKindMatchesJava() {
        var overworld = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var setting = everyKind(overworld);
        long[] before = NativeTopMaterial.verification();
        long[] requestsBefore = NativeTopMaterial.requests();
        int pairs = 0;
        long coldBefore = NativeCarvers.COLD_CALLS.get();
        NativeTopMaterial.setVerify(true);
        try {
            for (long seed : SEEDS) {
                List<ChunkPos> grid = new ArrayList<>(positions(overworld, seed, 9000));
                for (int i = 0; i < 12; i++) grid.add(new ChunkPos(9100 + i * 3, 9100 - i * 5));
                for (ChunkPos pos : grid) {
                    // Vanilla surfaces (grass over dirt), carved with the every-kind top material rule.
                    var surface = overworld.value().surfaceRule();
                    var java = NativeCarversTest.carve(setting, surface, seed, pos, false);
                    NativeCarversTest.compare(java, NativeCarversTest.carve(setting, surface, seed, pos, true), "every kind seed " + seed + " " + pos);
                    pairs++;
                }
            }
        } finally {
            NativeTopMaterial.setVerify(false);
        }
        long[] after = NativeTopMaterial.verification(), requests = NativeTopMaterial.requests();
        for (int kind = 0; kind < 8; kind++) requests[kind] -= requestsBefore[kind];
        System.out.println("TOP_MATERIAL_EVERY_KIND pairs=" + pairs + " verified=" + (after[0] - before[0]) + " mismatched=" + (after[1] - before[1])
            + " requests(band,secondary,minSurface,steep,gradient,noise,biome,temperature)=" + java.util.Arrays.toString(requests));
        assertEquals(0, after[1] - before[1], () -> "first mismatch x,y,z,native,java,nativeLeaf,javaLeaf = "
            + java.util.Arrays.toString(java.util.Arrays.copyOfRange(after, 2, 9)));
        for (int kind = 0; kind < 8; kind++) {
            int k = kind;
            assertTrue(requests[kind] > 0, () -> "the rule must request kind " + k);
        }
        // Temperature conditions are Java's coldEnoughToSnow, called where Java's evaluation calls it.
        assertTrue(NativeCarvers.COLD_CALLS.get() - coldBefore >= requests[7], "Java answers every temperature condition");
    }

    /** Each biome's per-thread temperature cache, in insertion (eviction) order:
     * packed position and float bits. Read on the calling thread. */
    static java.util.Map<String, List<long[]>> caches() throws ReflectiveOperationException {
        var field = Biome.class.getDeclaredField("temperatureCache");
        field.setAccessible(true);
        java.util.Map<String, List<long[]>> out = new java.util.TreeMap<>();
        for (var holder : NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
            var map = (it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap)((ThreadLocal<?>)field.get(holder.value())).get();
            List<long[]> entries = new ArrayList<>();
            for (var entry : map.long2FloatEntrySet()) entries.add(new long[]{entry.getLongKey(), Float.floatToRawIntBits(entry.getFloatValue())});
            if (!entries.isEmpty()) out.put(holder.key().location().toString(), entries);
        }
        return out;
    }

    static String describe(java.util.Map<String, List<long[]>> caches) {
        StringBuilder text = new StringBuilder();
        caches.forEach((biome, entries) -> {
            text.append(biome).append('=');
            for (long[] e : entries) text.append(e[0]).append(':').append(e[1]).append(',');
            text.append(';');
        });
        return text.toString();
    }

    record History(String before, String after, String reads, List<NativeNoiseFillTest.Filled> chunks, long temperatureCalls) {}

    /** On a fresh thread: seed each biome's cache under sea level 0 at {@code keys}
     * and filler positions up to the eviction boundary, carve, record the caches,
     * then read every key back through Java under the setting's sea level. */
    record Carve(long seed, ChunkPos pos) {}

    static History history(Holder<NoiseGeneratorSettings> setting, List<Carve> carves, java.util.Map<String, long[]> keys,
                           boolean nativeRoute) throws Exception {
        var result = new java.util.concurrent.atomic.AtomicReference<History>();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread thread = new Thread(() -> {
            try {
                var biomes = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME);
                for (var entry : keys.entrySet()) {
                    Biome biome = biomes.getValue(net.minecraft.resources.ResourceLocation.parse(entry.getKey()));
                    // Java before native: entries at the carved positions under another sea level.
                    for (long key : entry.getValue()) biome.coldEnoughToSnow(net.minecraft.core.BlockPos.of(key), 0);
                    // Filler up to two below the 1024 boundary, so the carvers' misses evict in FIFO order.
                    for (int i = 0; i < 1022 - entry.getValue().length; i++) biome.coldEnoughToSnow(new net.minecraft.core.BlockPos(4_000_000 + i, 70, 0), 0);
                }
                String before = describe(caches());
                long answersBefore = NativeSurface.JAVA_ANSWERS.get(6), coldBefore = NativeCarvers.COLD_CALLS.get();
                List<NativeNoiseFillTest.Filled> chunks = new ArrayList<>();
                var surface = NativeNoiseFillTest.settings.stream().filter(h -> h.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow().value().surfaceRule();
                for (Carve carve : carves) chunks.add(NativeCarversTest.carve(setting, surface, carve.seed(), carve.pos(), nativeRoute));
                // Temperature evaluations: Java condition answers (the SURFACE stage, and the
                // Java route's top materials) plus the native route's coldEnoughToSnow calls.
                long calls = NativeSurface.JAVA_ANSWERS.get(6) - answersBefore + NativeCarvers.COLD_CALLS.get() - coldBefore;
                String after = describe(caches());
                // Native, then Java: later Java reads observe the same history.
                StringBuilder reads = new StringBuilder();
                for (var entry : keys.entrySet()) {
                    Biome biome = biomes.getValue(net.minecraft.resources.ResourceLocation.parse(entry.getKey()));
                    for (long key : entry.getValue()) reads.append(biome.coldEnoughToSnow(net.minecraft.core.BlockPos.of(key), setting.value().seaLevel()) ? '1' : '0');
                }
                result.set(new History(before, after, reads.toString(), chunks, calls));
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "top-material-history-" + nativeRoute);
        thread.start();
        thread.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    /** A carver rule whose only Java-answered condition is temperature. */
    static Holder<NoiseGeneratorSettings> temperatureOnly(Holder<NoiseGeneratorSettings> setting) {
        var rule = SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
            SurfaceRules.ifTrue(SurfaceRules.temperature(), SurfaceRules.state(net.minecraft.world.level.block.Blocks.ICE.defaultBlockState())),
            SurfaceRules.state(net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState())));
        var c = setting.value();
        return Holder.direct(new NoiseGeneratorSettings(c.noiseSettings(), c.defaultBlock(), c.defaultFluid(), c.noiseRouter(), rule, c.spawnTarget(),
            c.seaLevel(), c.disableMobGeneration(), c.aquifersEnabled(), c.oreVeinsEnabled(), c.useLegacyRandomSource()));
    }

    /** Java's temperature cache history across sea levels: the same biome,
     * positions and thread, seeded under sea level 0 at the carved positions and
     * up to the FIFO boundary, then carved at sea level 63 by each route on its
     * own fresh thread. The caches (entries and eviction order), later Java
     * reads, the chunks and the temperature call counts must all agree. */
    @Test void temperatureCacheHistoryMatchesJava() throws Exception {
        var overworld = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var setting = temperatureOnly(overworld);
        // The every-kind test's chunks, which reach top materials.
        List<Carve> carves = new ArrayList<>();
        for (long seed : SEEDS) {
            for (ChunkPos pos : positions(overworld, seed, 9000)) carves.add(new Carve(seed, pos));
            for (int i = 0; i < 12; i++) carves.add(new Carve(seed, new ChunkPos(9100 + i * 3, 9100 - i * 5)));
        }
        // Discover the positions the carvers' temperature tests read, on a throwaway thread.
        var discovered = history(setting, carves, java.util.Map.of(), false);
        java.util.Map<String, long[]> keys = new java.util.TreeMap<>();
        for (String biome : discovered.after().split(";")) {
            if (biome.isEmpty()) continue;
            String[] parts = biome.split("=");
            if (parts.length < 2) continue;
            long[] all = java.util.Arrays.stream(parts[1].split(",")).filter(e -> !e.isEmpty()).mapToLong(e -> Long.parseLong(e.split(":")[0]))
                .filter(key -> net.minecraft.core.BlockPos.of(key).getX() < 3_000_000).toArray();
            if (all.length > 0) keys.put(parts[0], all);
        }
        System.out.println("TOP_MATERIAL_CACHE_DISCOVERY calls=" + discovered.temperatureCalls() + " after=" + discovered.after().length()
            + " sample=" + discovered.after().substring(0, Math.min(300, discovered.after().length())));
        assertFalse(keys.isEmpty(), "the carvers test temperatures");
        // The sea-level history must matter for some of them, or the sequence distinguishes nothing.
        var biomes = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME);
        int distinguishing = 0, total = 0;
        for (var entry : keys.entrySet()) {
            Biome biome = biomes.getValue(net.minecraft.resources.ResourceLocation.parse(entry.getKey()));
            for (long key : entry.getValue()) {
                total++;
                var pos = net.minecraft.core.BlockPos.of(key);
                var m = Biome.class.getDeclaredMethod("getHeightAdjustedTemperature", net.minecraft.core.BlockPos.class, int.class);
                m.setAccessible(true);
                float zero = (float)m.invoke(biome, pos, 0), level = (float)m.invoke(biome, pos, overworld.value().seaLevel());
                if ((zero >= 0.15F) != (level >= 0.15F)) distinguishing++;
            }
        }
        var java = history(setting, carves, keys, false);
        var candidate = history(setting, carves, keys, true);
        System.out.println("TOP_MATERIAL_CACHE_HISTORY keys=" + total + " distinguishing=" + distinguishing + " java_calls=" + java.temperatureCalls()
            + " native_calls=" + candidate.temperatureCalls() + " cache_entries_after=" + java.after().split(",").length);
        assertTrue(distinguishing > 0, "the sequence must distinguish sea levels");
        assertEquals(java.before(), candidate.before(), "identical seeded histories");
        assertEquals(java.temperatureCalls(), candidate.temperatureCalls(), "the same temperature evaluations");
        assertEquals(java.after(), candidate.after(), "the same cache entries and eviction order after carving");
        assertEquals(java.reads(), candidate.reads(), "later Java reads see the same history");
        for (int i = 0; i < carves.size(); i++) NativeCarversTest.compare(java.chunks().get(i), candidate.chunks().get(i), "history " + carves.get(i));
    }

    /** The binding is cached on its surface system and shared: chunks of one
     * RandomState carved concurrently on four threads reuse one binding and
     * equal Java's carving; a new RandomState (a reload) gets its own binding,
     * and the old one becomes unreachable once its system is, so its Cleaner
     * can free the Rust side. */
    record SharedRun(java.util.Map<ChunkPos, NativeNoiseFillTest.Filled> results, int bindings, java.lang.ref.WeakReference<Object> binding) {}

    /** Carves every position natively on four threads over one RandomState; only
     * a weak reference to its binding leaves this frame. */
    static SharedRun carveShared(Holder<NoiseGeneratorSettings> setting, SurfaceRules.RuleSource surface, long seed, List<ChunkPos> positions)
            throws Exception {
        RandomState shared = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
        var results = new java.util.concurrent.ConcurrentHashMap<ChunkPos, NativeNoiseFillTest.Filled>();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (ChunkPos pos : positions) futures.add(pool.submit(() -> results.put(pos, NativeCarversTest.carve(setting, surface, shared, seed, pos, true))));
            for (var future : futures) future.get();
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES);
        }
        @SuppressWarnings("unchecked")
        var cached = (List<Object>)shared.surfaceSystem().nativeTopMaterials;
        assertNotNull(cached, "the system caches its binding");
        Object binding = binding(cached);
        assertNotNull(binding, "the rule binds natively");
        return new SharedRun(new java.util.HashMap<>(results), cached.size(), new java.lang.ref.WeakReference<>(binding));
    }

    /** The binding is cached on its surface system and shared: chunks of one
     * RandomState carved concurrently on four threads reuse one binding and
     * equal Java's carving; a new RandomState (a reload) gets its own binding,
     * and the old one becomes unreachable with its system, so its Cleaner can
     * free the Rust side. */
    @Test void sharedBindingIsReusedConcurrentlyAndReleasedWithItsSystem() throws Exception {
        var overworld = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var setting = everyKind(overworld);
        var surface = overworld.value().surfaceRule();
        long seed = SEEDS[2];
        List<ChunkPos> positions = new ArrayList<>(positions(overworld, seed, 9000));
        for (int i = 0; i < 9; i++) positions.add(new ChunkPos(9100 + i * 3, 9100 - i * 5));
        long natives = NativeTopMaterial.nativeCalls();
        SharedRun run = carveShared(setting, surface, seed, positions);
        assertTrue(NativeTopMaterial.nativeCalls() > natives, "the shared binding answered top materials");
        assertEquals(1, run.bindings(), "every chunk and thread reused one binding");
        for (ChunkPos pos : positions) {
            var java = NativeCarversTest.carve(setting, surface, seed, pos, false);
            NativeCarversTest.compare(java, run.results().get(pos), "concurrent shared binding " + pos);
        }
        // A reload: a new RandomState has its own system and binding, distinct from the old one.
        RandomState reloaded = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
        NativeCarversTest.carve(setting, surface, reloaded, seed, positions.get(0), true);
        @SuppressWarnings("unchecked")
        Object current = binding((List<Object>)reloaded.surfaceSystem().nativeTopMaterials);
        assertNotNull(current, "the reloaded system binds natively");
        assertNotSame(run.binding().get(), current, "a reloaded system never reuses the old binding");
        current = null;
        // The old binding is released with its system: the chunks hold no reference to it.
        var released = run.binding();
        run = null;
        for (int attempt = 0; attempt < 40 && released.get() != null; attempt++) {
            System.gc();
            Thread.sleep(5);
        }
        assertNull(released.get(), "the old system's binding becomes unreachable, so its Cleaner frees the Rust side");
        java.lang.ref.Reference.reachabilityFence(reloaded);
    }

    /** The NativeTopMaterial of a system's single cached entry. */
    static Object binding(List<Object> cached) throws ReflectiveOperationException {
        Object entry = cached.get(0);
        var accessor = entry.getClass().getDeclaredMethod("binding");
        accessor.setAccessible(true);
        return accessor.invoke(entry);
    }

    @Test void concurrentFirstUsersPublishOneBindingAndImmediatelyReleaseLosers() throws Exception {
        var overworld = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var setting = everyKind(overworld);
        var positions = new ArrayList<>(positions(overworld, SEEDS[2], 9000));
        for (int i = 0; i < 9; i++) positions.add(new ChunkPos(9100 + i * 3, 9100 - i * 5));
        for (int round = 0; round < 3; round++) {
            var created = new java.util.concurrent.CopyOnWriteArrayList<NativeTopMaterial>();
            var gate = new java.util.concurrent.CyclicBarrier(4);
            NativeTopMaterial.beforePublish = (system, candidate) -> {
                assertFalse(Thread.holdsLock(system), "construction/verification must run outside the cache lock");
                assertNotNull(candidate);
                created.add(candidate);
                try { gate.await(30, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(failure); }
            };
            SharedRun run;
            try { run = carveShared(setting, overworld.value().surfaceRule(), SEEDS[2], positions); }
            finally { NativeTopMaterial.beforePublish = null; }
            assertEquals(4, created.size(), "the barrier must actually force four racing allocations");
            assertEquals(1, run.bindings(), "only the canonical binding is retained");
            Object canonical = run.binding().get();
            assertNotNull(canonical);
            int losers = 0;
            for (var candidate : created) {
                if (candidate == canonical) assertFalse(candidate.releasedForVerification());
                else {
                    losers++;
                    assertTrue(candidate.releasedForVerification(), "losing handles are freed while their Java objects remain reachable");
                    var field = NativeTopMaterial.class.getDeclaredField("cleanable"); field.setAccessible(true);
                    ((java.lang.ref.Cleaner.Cleanable)field.get(candidate)).clean();
                    assertTrue(candidate.releasedForVerification(), "a second cleanup is harmless");
                }
            }
            assertEquals(3, losers);
            // The existing shared-run test independently compares Java carving,
            // cache reuse after reload, and GC of the canonical binding.
            java.lang.ref.Reference.reachabilityFence(created);
        }
    }

    record DelegatingRule(SurfaceRules.RuleSource delegate) implements SurfaceRules.RuleSource {
        public net.minecraft.util.KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() { return delegate.codec(); }
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) { return delegate.apply(context); }
    }

    @Test void declinedBindingsKeepIdentityKeysAndTheEightEntryPolicy() {
        var setting = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), SEEDS[2]);
        var filled = NativeNoiseFillTest.fill(setting, random, new ChunkPos(0, 0), true);
        var registry = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME);
        var plains = registry.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(plains), setting);
        var manager = new net.minecraft.world.level.biome.BiomeManager((x, y, z) -> plains,
            net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(SEEDS[2]));
        var delegate = SurfaceRules.state(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var first = new DelegatingRule(delegate);
        var second = new DelegatingRule(delegate);
        assertEquals(first, second, "equal-valued rule objects must still use distinct identity keys");
        var misses = new java.util.concurrent.atomic.AtomicInteger();
        NativeTopMaterial.beforePublish = (system, candidate) -> { assertNull(candidate); misses.incrementAndGet(); };
        long declined = NativeTopMaterial.DECLINED.get();
        int calls = 0;
        try {
            for (var rule : List.of(first, first, second, new DelegatingRule(delegate), new DelegatingRule(delegate),
                new DelegatingRule(delegate), new DelegatingRule(delegate), new DelegatingRule(delegate), new DelegatingRule(delegate),
                new DelegatingRule(delegate), first)) {
                var context = new net.minecraft.world.level.levelgen.carver.CarvingContext(generator, NativeNoiseFillTest.registries,
                    filled.chunk(), filled.noise(), random, rule);
                assertNull(NativeTopMaterial.create(context, filled.chunk(), filled.noise(), manager, pos -> plains, null, plains));
                calls++;
            }
        } finally { NativeTopMaterial.beforePublish = null; }
        assertEquals(10, misses.get(), "same-object hit, distinct equal-valued rules, and eviction/retry");
        assertEquals(8, ((List<?>)random.surfaceSystem().nativeTopMaterials).size());
        assertEquals(calls, NativeTopMaterial.DECLINED.get() - declined);
    }

    @Test void failedPublicationReleasesItsAllocationAndCanRetry() {
        var setting = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), SEEDS[2]);
        var filled = NativeNoiseFillTest.fill(setting, random, new ChunkPos(0, 0), true);
        var plains = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(plains), setting);
        var manager = new net.minecraft.world.level.biome.BiomeManager((x, y, z) -> plains,
            net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(SEEDS[2]));
        var context = new net.minecraft.world.level.levelgen.carver.CarvingContext(generator, NativeNoiseFillTest.registries,
            filled.chunk(), filled.noise(), random, SurfaceRules.state(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
        var attempted = new java.util.concurrent.atomic.AtomicReference<NativeTopMaterial>();
        var failure = new IllegalStateException("publication sentinel");
        NativeTopMaterial.beforePublish = (system, candidate) -> { attempted.set(candidate); throw failure; };
        try {
            assertSame(failure, assertThrows(IllegalStateException.class,
                () -> NativeTopMaterial.create(context, filled.chunk(), filled.noise(), manager, pos -> plains, null, plains)));
        } finally { NativeTopMaterial.beforePublish = null; }
        assertNotNull(attempted.get());
        assertTrue(attempted.get().releasedForVerification(), "failed publication must not retain a native allocation");
        assertNull(random.surfaceSystem().nativeTopMaterials, "failure must not install a cache entry");
        var retried = NativeTopMaterial.create(context, filled.chunk(), filled.noise(), manager, pos -> plains, null, plains);
        assertNotNull(retried);
        assertFalse(retried.releasedForVerification(), "a successful retry stays alive");
        java.lang.ref.Reference.reachabilityFence(attempted);
    }

    /** The native route alone: no Java upcall, and the chunk equals Java's carving. */
    @Test void nativeRouteCarvesWithoutJavaUpcalls() {
        long javaBefore = NativeCarvers.TOP_CALLS.get(), nativeBefore = NativeTopMaterial.nativeCalls(), declined = NativeTopMaterial.DECLINED.get();
        long[] requestsBefore = NativeTopMaterial.requests();
        int pairs = 0;
        for (var setting : NativeNoiseFillTest.settings) {
            long seed = SEEDS[1];
            for (ChunkPos pos : positions(setting, seed)) {
                String context = setting.key().location() + " seed " + seed + " " + pos;
                var java = NativeCarversTest.carve(setting, seed, pos, false);
                long upcalls = NativeCarvers.TOP_CALLS.get();
                var candidate = NativeCarversTest.carve(setting, seed, pos, true);
                assertEquals(upcalls, NativeCarvers.TOP_CALLS.get(), () -> context + " must not call Java's top material");
                NativeCarversTest.compare(java, candidate, context);
                pairs++;
            }
        }
        long natives = NativeTopMaterial.nativeCalls() - nativeBefore;
        long[] requests = NativeTopMaterial.requests();
        for (int kind = 0; kind < 8; kind++) requests[kind] -= requestsBefore[kind];
        System.out.println("TOP_MATERIAL_NATIVE pairs=" + pairs + " native=" + natives + " java=" + (NativeCarvers.TOP_CALLS.get() - javaBefore)
            + " requests(band,secondary,minSurface,steep,gradient,noise,biome,temperature)=" + java.util.Arrays.toString(requests));
        assertTrue(natives > 0, "native top materials ran");
        assertEquals(declined, NativeTopMaterial.DECLINED.get(), "vanilla rules bind natively");
    }

    /** With the native binding disabled the stage keeps Java's upcall, with the same chunk. */
    @Test void disabledBindingKeepsJavaUpcall() {
        var setting = NativeNoiseFillTest.settings.stream().filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        // A chunk that reaches top materials.
        ChunkPos pos = null;
        NativeNoiseFillTest.Filled reference = null;
        for (ChunkPos candidate : positions(setting, SEEDS[1])) {
            long natives = NativeTopMaterial.nativeCalls();
            reference = NativeCarversTest.carve(setting, SEEDS[1], candidate, true);
            if (NativeTopMaterial.nativeCalls() > natives) { pos = candidate; break; }
        }
        assertNotNull(pos, "some overworld chunk reaches a top material");
        long upcalls = NativeCarvers.TOP_CALLS.get(), natives = NativeTopMaterial.nativeCalls();
        NativeTopMaterial.setEnabled(false);
        try {
            NativeCarversTest.compare(reference, NativeCarversTest.carve(setting, SEEDS[1], pos, true), "disabled native top material");
        } finally {
            NativeTopMaterial.setEnabled(true);
        }
        assertTrue(NativeCarvers.TOP_CALLS.get() > upcalls, "Java answered the top materials");
        assertEquals(natives, NativeTopMaterial.nativeCalls(), "no native top material ran");
    }
}
