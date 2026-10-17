package net.minecraft.world.level.chunk;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Actual registration/claim/release-prefix code with a modeled native release.
 * ROOT is permanent; fake addresses never reach native destruction or the phantom queue. */
public final class VerifyNativeReclaimerRelease {
    static final Object ROOT = new Object();
    static final IOException FAILURE = new IOException("modeled release failure");
    static final List<String> events = new ArrayList<>();
    static boolean insideRelease, failRelease;
    static int prefix, calls;
    static long address = 0x500;
    static final MethodHandle RELEASE;
    static {
        try { RELEASE = MethodHandles.lookup().findStatic(VerifyNativeReclaimerRelease.class, "release",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, int.class)); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static int release(MemorySegment kinds, MemorySegment pointers, int count) throws IOException {
        require(!insideRelease, "overlapping modeled release"); insideRelease = true;
        try {
            events.add("native:" + count);
            if (failRelease) throw FAILURE;
            return prefix;
        } finally { insideRelease = false; }
    }
    static NativeReclaimer.Owner[] pair(Runnable action, boolean pressureFailure) {
        var records = new NativeReclaimer.Owner[2];
        NativeReclaimer.registerLightMap(ROOT, MemorySegment.ofAddress(address++), MemorySegment.ofAddress(address++), action,
            (at, first, second) -> {
                records[0] = first; records[1] = second;
                if (at == 5 && pressureFailure) throw new AssertionError("optional pressure");
            });
        return records;
    }
    static NativeReclaimer.Owner plain(byte kind) {
        var owner = new NativeReclaimer.Owner(ROOT, address++, kind); owner.committed = true; return owner;
    }
    static void claim(NativeReclaimer.Owner... records) {
        for (var owner : records) require(owner.claim() && !owner.claim(), "claim must be exactly once");
    }
    static int execute(NativeReclaimer.Owner[] records, int completed) throws Throwable {
        prefix = completed;
        try (Arena arena = Arena.ofConfined()) {
            var kinds = arena.allocate(records.length); var pointers = arena.allocate((long)records.length * Long.BYTES, Long.BYTES);
            var lighting = new NativeReclaimer.LightOwner[records.length];
            for (int i = 0; i < records.length; i++) {
                kinds.set(ValueLayout.JAVA_BYTE, i, records[i].kind);
                pointers.setAtIndex(ValueLayout.JAVA_LONG, i, records[i].address);
                if (records[i] instanceof NativeReclaimer.LightOwner light) lighting[i] = light;
            }
            return NativeReclaimer.releaseBatch(kinds, pointers, records.length, lighting, RELEASE);
        }
    }
    static void registrationFailures() {
        int pending = NativeReclaimer.pending();
        for (int stage = 0; stage <= 4; stage++) {
            int failing = stage; var records = new NativeReclaimer.Owner[2]; int before = calls;
            Error failure = new AssertionError("registration " + stage);
            try {
                NativeReclaimer.registerLightMap(ROOT, MemorySegment.ofAddress(address++), MemorySegment.ofAddress(address++),
                    () -> calls++, (at, first, second) -> {
                        records[0] = first; records[1] = second;
                        if (at == failing) throw failure;
                    });
                throw new AssertionError("missing registration failure");
            } catch (Error actual) { require(actual == failure, "replaced registration failure"); }
            require(!records[0].claim() && !records[1].claim(), "cancelled pair can enter release batch");
            require(calls == before && NativeReclaimer.pending() == pending, "failed pair leaked or notified");
        }
        for (int stage : new int[]{0, 1, 4}) {
            int failing = stage; var records = new NativeReclaimer.Owner[1]; Error failure = new AssertionError("single " + stage);
            try {
                NativeReclaimer.registerLightMapOwner(ROOT, MemorySegment.ofAddress(address++), () -> calls++, (at, first, second) -> {
                    records[0] = first; require(second == null, "single root grew a view record"); if (at == failing) throw failure;
                });
                throw new AssertionError("missing single registration failure");
            } catch (Error actual) { require(actual == failure, "replaced single failure"); }
            require(!records[0].claim() && NativeReclaimer.pending() == pending, "cancelled single survived");
        }
    }
    static void splitPair(boolean reverse) throws Throwable {
        int before = calls; var pair = pair(() -> { require(!insideRelease, "callback under native release"); calls++; }, true);
        var records = new NativeReclaimer.Owner[1025];
        for (int i = 0; i < records.length; i++) records[i] = plain(NativeReclaimer.LIGHT_LAYER);
        records[1023] = pair[reverse ? 1 : 0]; records[1024] = pair[reverse ? 0 : 1]; claim(records);
        execute(Arrays.copyOfRange(records, 0, 1024), 1024); require(calls == before + 1, "first batch lost map/view callback");
        execute(Arrays.copyOfRange(records, 1024, 1025), 1); require(calls == before + 2, "split batch lost map/view callback");
        ((NativeReclaimer.LightOwner)pair[0]).released(); ((NativeReclaimer.LightOwner)pair[1]).released();
        require(calls == before + 2, "duplicate notification");
    }
    static void partialAndFailedRelease() throws Throwable {
        int before = calls; var pair = pair(() -> calls++, false);
        var unknown = plain((byte)0); claim(pair); claim(unknown);
        execute(new NativeReclaimer.Owner[]{pair[0], unknown, pair[1]}, 1);
        require(calls == before + 1, "notification escaped actual released prefix");
        before = calls; pair = pair(() -> calls++, false); unknown = plain((byte)0); claim(pair); claim(unknown);
        execute(new NativeReclaimer.Owner[]{unknown, pair[0], pair[1]}, 0);
        require(calls == before, "zero prefix notified");
        pair = pair(() -> calls++, false); claim(pair); failRelease = true; long released = NativeReclaimer.released();
        try { execute(pair, 2); throw new AssertionError("missing release failure"); }
        catch (Throwable actual) { require(actual == FAILURE, "release error object replaced"); }
        finally { failRelease = false; }
        require(calls == before && NativeReclaimer.released() == released, "failed release assumed completion");
    }
    static void callbackFailureAndReentry() throws Throwable {
        var first = new NativeReclaimer.Owner[2]; var second = new NativeReclaimer.Owner[2];
        int before = calls;
        var a = pair(() -> { require(!insideRelease && !Thread.holdsLock(first[0].leader), "callback ran under a gate"); calls++; throw new AssertionError("expected cleanup failure"); }, false);
        first[0] = a[0]; first[1] = a[1];
        var b = pair(() -> {
            require(!insideRelease && !Thread.holdsLock(second[0].leader), "callback reentry under gate"); calls++;
            var nested = new NativeReclaimer.Owner[1];
            NativeReclaimer.registerLightMapOwner(ROOT, MemorySegment.ofAddress(address++), () -> {}, (at, one, two) -> nested[0] = one);
            claim(nested); events.add("registered-from-cleanup");
        }, false);
        second[0] = b[0]; second[1] = b[1]; claim(a); claim(b);
        execute(new NativeReclaimer.Owner[]{a[0], b[0], a[1], b[1]}, 4);
        require(calls == before + 4, "one throwing callback suppressed another completed notification");
        require(events.contains("registered-from-cleanup"), "callback did not reenter registration");
    }
    public static void main(String[] args) throws Throwable {
        int pending = NativeReclaimer.pending();
        for (var field : NativeReclaimer.Owner.class.getDeclaredFields()) require(field.getType() != Runnable.class, "ordinary owner gained callback storage");
        registrationFailures(); splitPair(false); splitPair(true); partialAndFailedRelease(); callbackFailureAndReentry();
        require(NativeReclaimer.pending() == pending, "modeled registrations remain LIVE");
        System.out.println("PASS modeled reclaimer cleanup: pair/single cancellation, pressure failure, mixed1025 split map/view both orders, exact-once prefix/zero/failed release, throwing callback isolation and registration reentry; fake addresses never natively freed");
    }
}
