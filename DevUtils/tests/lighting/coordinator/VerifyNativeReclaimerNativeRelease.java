package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Real native map/view/counter owners: exact prefixes and both 1024-record split orders. */
public final class VerifyNativeReclaimerNativeRelease {
    static final Object ROOT = new Object();
    static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name, descriptor);
    }
    static final MethodHandle REGISTRY = handle("mattmc_light_map_registry_create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    static final MethodHandle DROP_REGISTRY = handle("mattmc_light_map_registry_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    static final MethodHandle MAP = handle("mattmc_light_map_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    static final MethodHandle VIEW = handle("mattmc_light_map_view", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    static final MethodHandle COUNTER = handle("mattmc_section_counters_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    static final MethodHandle RELEASE = handle("mattmc_native_release_batch", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    record Pair(MemorySegment map, MemorySegment view, NativeReclaimer.LightOwner first, NativeReclaimer.LightOwner second) {}
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static Pair pair(MemorySegment registry, Runnable callback) throws Throwable {
        MemorySegment map = (MemorySegment)MAP.invokeExact(registry), view = (MemorySegment)VIEW.invokeExact(map);
        require(map.address() != 0 && view.address() != 0, "missing real map/view owner");
        var records = new NativeReclaimer.Owner[2];
        NativeReclaimer.registerLightMap(ROOT, map, view, callback, (at, first, second) -> { records[0] = first; records[1] = second; });
        require(records[0].claim() && records[1].claim() && !records[0].claim() && !records[1].claim(), "real pair claimed more than once");
        return new Pair(map, view, (NativeReclaimer.LightOwner)records[0], (NativeReclaimer.LightOwner)records[1]);
    }
    static int release(MemorySegment[] pointers, byte[] tags, NativeReclaimer.LightOwner[] callbacks) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            var kinds = arena.allocate(tags.length); var raw = arena.allocate((long)tags.length * Long.BYTES, Long.BYTES);
            for (int i = 0; i < tags.length; i++) { kinds.set(ValueLayout.JAVA_BYTE, i, tags[i]); raw.setAtIndex(ValueLayout.JAVA_LONG, i, pointers[i].address()); }
            return NativeReclaimer.releaseBatch(kinds, raw, tags.length, callbacks, RELEASE);
        }
    }
    static void partial(MemorySegment registry) throws Throwable {
        int[] calls = {0}; var pair = pair(registry, () -> calls[0]++);
        MemorySegment counter = (MemorySegment)COUNTER.invokeExact(7L); long before = NativeReclaimer.released();
        int count = release(new MemorySegment[]{pair.map, counter, MemorySegment.NULL, pair.view},
            new byte[]{NativeReclaimer.LIGHT_MAP, NativeReclaimer.SECTION_COUNTERS, 0, NativeReclaimer.LIGHT_MAP_VIEW},
            new NativeReclaimer.LightOwner[]{pair.first, null, null, pair.second});
        require(count == 2 && calls[0] == 1 && NativeReclaimer.released() == before + 2, "real partial prefix notified an unreleased view");
        // This test owns the explicitly returned unprocessed suffix. Production
        // still reports it as leaked; it does not retry an uncertain native call.
        require(release(new MemorySegment[]{pair.view}, new byte[]{NativeReclaimer.LIGHT_MAP_VIEW},
            new NativeReclaimer.LightOwner[]{pair.second}) == 1 && calls[0] == 2, "known unreleased view did not release once");
        pair.first.released(); pair.second.released(); require(calls[0] == 2, "real prefix notified twice");
        System.out.println("REAL_RELEASE partial=2 callbacks=1 remaining_view=1 callbacks_final=2");
    }
    static void split(MemorySegment registry, boolean reverse) throws Throwable {
        int[] calls = {0}; var pair = pair(registry, () -> calls[0]++);
        var pointers = new MemorySegment[1024]; var kinds = new byte[1024]; var callbacks = new NativeReclaimer.LightOwner[1024];
        for (int i = 0; i < 1023; i++) { pointers[i] = (MemorySegment)COUNTER.invokeExact((long)i); kinds[i] = NativeReclaimer.SECTION_COUNTERS; }
        pointers[1023] = reverse ? pair.view : pair.map; kinds[1023] = reverse ? NativeReclaimer.LIGHT_MAP_VIEW : NativeReclaimer.LIGHT_MAP;
        callbacks[1023] = reverse ? pair.second : pair.first; long before = NativeReclaimer.released();
        require(release(pointers, kinds, callbacks) == 1024 && calls[0] == 1, "real mixed first batch lost callback");
        require(release(new MemorySegment[]{reverse ? pair.map : pair.view},
            new byte[]{reverse ? NativeReclaimer.LIGHT_MAP : NativeReclaimer.LIGHT_MAP_VIEW},
            new NativeReclaimer.LightOwner[]{reverse ? pair.first : pair.second}) == 1 && calls[0] == 2, "real split suffix lost callback");
        require(NativeReclaimer.released() == before + 1025, "real other-kind release count changed");
        System.out.println("REAL_RELEASE split reverse=" + reverse + " counters=1023 map=1 view=1 callbacks=2");
    }
    public static void main(String[] args) throws Throwable {
        int before = NativeReclaimer.pending(); MemorySegment registry = (MemorySegment)REGISTRY.invokeExact();
        try { partial(registry); split(registry, false); split(registry, true); }
        finally { DROP_REGISTRY.invokeExact(registry); }
        require(NativeReclaimer.pending() == before, "real registered records survived explicit ownership claims");
        System.out.println("PASS real reclaimer release: unknown-kind prefix, exact-once map/view, mixed1025 split both orders and2047actual non-light counter releases; no fake pointers freed");
    }
}
