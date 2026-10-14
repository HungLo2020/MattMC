package net.minecraft.world.level.storage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native clock/weather policy against a literal transcription of the pre-change Java. */
class NativeWeatherTimeTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    /** ServerLevel's providers (Frozen and pre-change Current are identical); loaded after bootstrap. */
    private static final class Providers {
        static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
        static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
        static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
        static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    }

    /** Pre-change PrimaryLevelData/DerivedLevelData fields plus one dimension's levels. */
    private static final class Reference {
        long gameTime, dayTime; int clear, rain, thunder; boolean raining, thundering;
        float oRain, rainLevel, oThunder, thunderLevel;
        Reference(long gameTime, long dayTime, int clear, int rain, boolean raining, int thunder, boolean thundering) {
            this.gameTime = gameTime; this.dayTime = dayTime; this.clear = clear; this.rain = rain;
            this.raining = raining; this.thunder = thunder; this.thundering = thundering;
        }
        Reference copyLevels() {
            var r = new Reference(gameTime, dayTime, clear, rain, raining, thunder, thundering);
            r.oRain = oRain; r.rainLevel = rainLevel; r.oThunder = oThunder; r.thunderLevel = thunderLevel; return r;
        }
        void prepare() { if (raining) { rainLevel = 1.0F; if (thundering) thunderLevel = 1.0F; } }
        /** {@code ServerLevel.advanceWeatherCycle} state changes; writes are dropped when !writable. */
        void advance(Reference levels, boolean writable, boolean skyLight, boolean cycle, RandomSource random) {
            if (skyLight) {
                if (cycle) {
                    int i = clear, j = thunder, k = rain; boolean bl2 = thundering, bl3 = raining;
                    if (i > 0) { i--; j = bl2 ? 0 : 1; k = bl3 ? 0 : 1; bl2 = false; bl3 = false; }
                    else {
                        if (j > 0) { if (--j == 0) bl2 = !bl2; } else if (bl2) j = Providers.THUNDER_DURATION.sample(random); else j = Providers.THUNDER_DELAY.sample(random);
                        if (k > 0) { if (--k == 0) bl3 = !bl3; } else if (bl3) k = Providers.RAIN_DURATION.sample(random); else k = Providers.RAIN_DELAY.sample(random);
                    }
                    if (writable) { thunder = j; rain = k; clear = i; thundering = bl2; raining = bl3; }
                }
                levels.oThunder = levels.thunderLevel;
                levels.thunderLevel = Mth.clamp(thundering ? levels.thunderLevel + 0.01F : levels.thunderLevel - 0.01F, 0.0F, 1.0F);
                levels.oRain = levels.rainLevel;
                levels.rainLevel = Mth.clamp(raining ? levels.rainLevel + 0.01F : levels.rainLevel - 0.01F, 0.0F, 1.0F);
            }
        }
    }

    private static int sample(RandomSource random, int kind, int duration) {
        IntProvider provider = kind == NativeWeatherTime.THUNDER_SAMPLE ? (duration != 0 ? Providers.THUNDER_DURATION : Providers.THUNDER_DELAY) : (duration != 0 ? Providers.RAIN_DURATION : Providers.RAIN_DELAY);
        return provider.sample(random);
    }

    private static void assertSame(Reference expected, Reference levels, NativeWeatherTime.View view, String where) {
        var clock = view.clock();
        assertEquals(expected.gameTime, clock.gameTime(), where);
        assertEquals(expected.dayTime, clock.dayTime(), where);
        assertEquals(expected.clear, clock.clearWeatherTime(), where);
        assertEquals(expected.rain, clock.rainTime(), where);
        assertEquals(expected.thunder, clock.thunderTime(), where);
        assertEquals(expected.raining, clock.raining(), where);
        assertEquals(expected.thundering, clock.thundering(), where);
        assertEquals(Float.floatToRawIntBits(levels.oRain), Float.floatToRawIntBits(view.oRainLevel()), where);
        assertEquals(Float.floatToRawIntBits(levels.rainLevel), Float.floatToRawIntBits(view.rainLevel()), where);
        assertEquals(Float.floatToRawIntBits(levels.oThunder), Float.floatToRawIntBits(view.oThunderLevel()), where);
        assertEquals(Float.floatToRawIntBits(levels.thunderLevel), Float.floatToRawIntBits(view.thunderLevel()), where);
    }

    @Test void overworldAndDerivedDimensionsMatchReferenceStateAndRandomDraws() {
        Object[][] starts = {
            {0, 0, false, 0, false}, {3, 0, true, 0, true}, {0, -7, false, Integer.MIN_VALUE, true},
            {0, 1, true, 1, false}, {0, 0, true, 0, true},
        };
        for (Object[] start : starts) {
            for (boolean cycle : new boolean[]{true, false}) {
                for (boolean derivedSkyLight : new boolean[]{true, false}) {
                    var expected = new Reference(100, 200, (int)start[0], (int)start[1], (boolean)start[2], (int)start[3], (boolean)start[4]);
                    var clock = new NativeWeatherTime.Clock(100, 200, (int)start[0], (int)start[1], (boolean)start[2], (int)start[3], (boolean)start[4]);
                    var overworld = new NativeWeatherTime.View(clock, true);
                    var derived = new NativeWeatherTime.View(clock, false);
                    var overworldLevels = expected.copyLevels(); var derivedLevels = expected.copyLevels();
                    overworld.prepare(); overworldLevels.prepare();
                    derived.prepare(); derivedLevels.prepare();
                    RandomSource nativeOverworld = RandomSource.create(41), nativeDerived = RandomSource.create(42);
                    RandomSource javaOverworld = RandomSource.create(41), javaDerived = RandomSource.create(42);
                    for (int tick = 0; tick < 30_000; tick++) {
                        // MinecraftServer ticks the overworld first, then other dimensions.
                        overworld.advanceWeather(true, cycle, null, (r, k, d) -> sample(nativeOverworld, k, d));
                        expected.advance(overworldLevels, true, true, cycle, javaOverworld);
                        derived.advanceWeather(derivedSkyLight, derivedSkyLight && cycle, null, (r, k, d) -> sample(nativeDerived, k, d));
                        expected.advance(derivedLevels, false, derivedSkyLight, derivedSkyLight && cycle, javaDerived);
                        String where = java.util.Arrays.toString(start) + " cycle=" + cycle + " tick=" + tick;
                        assertSame(expected, overworldLevels, overworld, where);
                        assertSame(expected, derivedLevels, derived, where);
                    }
                    // Equal subsequent draws prove equal draw counts.
                    assertEquals(javaOverworld.nextLong(), nativeOverworld.nextLong());
                    assertEquals(javaDerived.nextLong(), nativeDerived.nextLong());
                }
            }
        }
    }

    @Test void noSamplesWithoutSkyLightCycleOrDuringClearCountdown() {
        var clock = new NativeWeatherTime.Clock(0, 0, 5, 0, false, 0, false);
        var view = new NativeWeatherTime.View(clock, true);
        List<Integer> draws = new ArrayList<>();
        for (int i = 0; i < 5; i++) view.advanceWeather(true, true, null, (r, k, d) -> { draws.add(k); return 1; });
        view.advanceWeather(false, false, null, (r, k, d) -> { draws.add(k); return 1; });
        view.advanceWeather(true, false, null, (r, k, d) -> { draws.add(k); return 1; });
        assertTrue(draws.isEmpty());
        // The countdown left both counters at 1: this tick toggles without sampling.
        view.advanceWeather(true, true, null, (r, k, d) -> { draws.add(k); return 1; });
        assertTrue(draws.isEmpty());
        assertTrue(clock.raining() && clock.thundering());
        view.advanceWeather(true, true, null, (r, k, d) -> { draws.add(k); return 7; });
        assertEquals(List.of(NativeWeatherTime.THUNDER_SAMPLE, NativeWeatherTime.RAIN_SAMPLE), draws);
        assertEquals(7, clock.thunderTime());
        assertEquals(7, clock.rainTime());
    }

    @Test void samplerFailureLeavesStateUnchangedAndNextTickReplans() {
        var clock = new NativeWeatherTime.Clock(0, 0, 0, 0, false, 0, false);
        var view = new NativeWeatherTime.View(clock, true);
        assertThrows(IllegalStateException.class, () -> view.advanceWeather(true, true, null, (r, k, d) -> { throw new IllegalStateException("sampler"); }));
        assertEquals(0, clock.thunderTime());
        assertEquals(0.0F, view.rainLevel());
        view.advanceWeather(true, true, null, (r, k, d) -> 9);
        assertEquals(9, clock.rainTime());
    }

    @Test void writesDuringSamplingRejectTheCommitWithoutReplanning() {
        var clock = new NativeWeatherTime.Clock(0, 0, 0, 0, false, 0, false);
        var view = new NativeWeatherTime.View(clock, true);
        int[] calls = {0};
        var failure = assertThrows(IllegalStateException.class, () -> view.advanceWeather(true, true, null, (r, k, d) -> { calls[0]++; clock.setDayTime(5); return 3; }));
        assertTrue(failure.getMessage().contains("rejected"));
        assertEquals(2, calls[0]);
        assertEquals(0, clock.rainTime());
        assertEquals(0, clock.thunderTime());
    }

    @Test void timePhasesKeepScheduledCallbackBoundaryAndDerivedWritesAreIgnored() {
        var clock = new NativeWeatherTime.Clock(Long.MAX_VALUE, 10, 0, 0, false, 0, false);
        var overworld = new NativeWeatherTime.View(clock, true);
        var derived = new NativeWeatherTime.View(clock, false);
        long observed = overworld.advanceGameTime();
        assertEquals(Long.MIN_VALUE, observed);
        assertEquals(Long.MIN_VALUE, clock.gameTime());
        // A scheduled function runs here and sets the time; the day phase reads it afterwards.
        clock.setDayTime(-1);
        overworld.advanceDayTime();
        assertEquals(0, clock.dayTime());
        assertEquals(Long.MIN_VALUE + 1, derived.advanceGameTime());
        derived.advanceDayTime();
        derived.skipToMorning();
        derived.resetWeather();
        derived.setWeather(1, 2, true, true);
        assertEquals(Long.MIN_VALUE, clock.gameTime());
        assertEquals(0, clock.dayTime());
        assertFalse(clock.raining());
        // /time add over every level: only writable data advances the shared clock.
        for (var view : List.of(overworld, derived)) if (view.writable()) clock.setDayTime(clock.dayTime() + 1000);
        assertEquals(1000, clock.dayTime());
        overworld.skipToMorning();
        assertEquals(24000, clock.dayTime());
        clock.setDayTime(-24001);
        overworld.skipToMorning();
        long l = -24001L + 24000L;
        assertEquals(l - l % 24000L, clock.dayTime());
    }

    @Test void weatherParametersAndResetMatchPreChangeSetters() {
        var clock = new NativeWeatherTime.Clock(0, 0, 0, 0, false, 0, false);
        var view = new NativeWeatherTime.View(clock, true);
        view.setWeather(0, 1234, true, true);
        assertEquals(1234, clock.rainTime());
        assertEquals(1234, clock.thunderTime());
        assertTrue(clock.raining() && clock.thundering());
        view.setWeather(500, 0, false, false);
        assertEquals(500, clock.clearWeatherTime());
        view.resetWeather();
        assertEquals(500, clock.clearWeatherTime());
        assertFalse(clock.raining() || clock.thundering());
        view.setRainLevel(2.0F);
        view.setThunderLevel(-3.0F);
        assertEquals(1.0F, view.oRainLevel());
        assertEquals(0.0F, view.thunderLevel());
    }

    @Test void sleepersNeededMatchesJavaArithmetic() {
        int[] percentages = {Integer.MIN_VALUE, -5, 0, 1, 33, 50, 99, 100, 101, 1000, Integer.MAX_VALUE};
        for (int active = 0; active < 300; active++) {
            for (int percentage : percentages) {
                assertEquals(Math.max(1, Mth.ceil(active * percentage / 100.0F)), NativeWeatherTime.sleepersNeeded(active, percentage), active + "/" + percentage);
            }
        }
        for (int active : new int[]{Integer.MAX_VALUE, 21_474_837, 100_000_000}) {
            assertEquals(Math.max(1, Mth.ceil(active * 100 / 100.0F)), NativeWeatherTime.sleepersNeeded(active, 100));
        }
    }

    @Test void unreachableOwnersAreReleased() throws Exception {
        long before = NativeWeatherTime.liveAllocations();
        for (int round = 0; round < 50; round++) {
            var clock = new NativeWeatherTime.Clock(round, round, 0, 0, false, 0, false);
            for (int i = 0; i < 3; i++) new NativeWeatherTime.View(clock, i == 0).advanceWeather(true, true, null, (r, k, d) -> 1);
        }
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (NativeWeatherTime.liveAllocations() > before && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        assertTrue(NativeWeatherTime.liveAllocations() <= before, "retained " + (NativeWeatherTime.liveAllocations() - before));
    }

    @Test void viewsRejectMutationFromASecondThread() throws Exception {
        var view = new NativeWeatherTime.View(new NativeWeatherTime.Clock(0, 0, 0, 5, false, 5, false), true);
        view.prepare();
        Throwable[] failure = {null};
        Thread other = new Thread(() -> {
            try { view.advanceWeather(true, true, null, (r, k, d) -> 1); } catch (Throwable t) { failure[0] = t; }
        });
        other.start();
        other.join();
        assertInstanceOf(IllegalStateException.class, failure[0]);
        assertEquals(5, view.clock().rainTime());
        view.advanceWeather(true, true, null, (r, k, d) -> 1);
        assertEquals(4, view.clock().rainTime());
    }

    @Test void concurrentReadersSeeConsistentLevelsAndAtomicClockFields() throws Exception {
        var clock = new NativeWeatherTime.Clock(0, 0, 0, 0, false, 0, false);
        var view = new NativeWeatherTime.View(clock, true);
        view.prepare();
        var stop = new java.util.concurrent.atomic.AtomicBoolean();
        var errors = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                var levels = view.levels();
                // Each snapshot comes from one ramp step: old and current differ by at most one 0.01 step.
                if (Math.abs(levels.rain() - levels.oRain()) > 0.0101F || Math.abs(levels.thunder() - levels.oThunder()) > 0.0101F) {
                    errors.add(levels.toString());
                }
                long time = clock.gameTime();
                if (time < 0) errors.add("time " + time);
            }
        });
        reader.start();
        RandomSource random = RandomSource.create(3);
        for (int tick = 0; tick < 200_000; tick++) {
            view.advanceWeather(true, true, random, (r, k, d) -> sample(r, k, d));
            view.advanceGameTime();
            if (tick % 1000 == 0) view.setWeather(0, 50, tick % 2000 == 0, true);
        }
        stop.set(true);
        reader.join();
        assertTrue(errors.isEmpty(), () -> errors.peek());
    }

    @Test void simultaneousFirstMutationsAdmitExactlyOneOwner() throws Exception {
        for (int round = 0; round < 500; round++) {
            var view = new NativeWeatherTime.View(new NativeWeatherTime.Clock(0, 0, 0, 1000, false, 1000, false), true);
            var start = new java.util.concurrent.CountDownLatch(1);
            var successes = new java.util.concurrent.atomic.AtomicInteger();
            var rejections = new java.util.concurrent.atomic.AtomicInteger();
            Thread[] threads = new Thread[4];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        start.await();
                        view.advanceWeather(true, true, null, (r, k, d) -> 1);
                        successes.incrementAndGet();
                    } catch (IllegalStateException e) {
                        rejections.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                threads[i].start();
            }
            start.countDown();
            for (Thread thread : threads) thread.join();
            assertEquals(1, successes.get(), "round " + round);
            assertEquals(threads.length - 1, rejections.get());
            assertEquals(999, view.clock().rainTime(), "exactly one weather step applied");
        }
    }
}
