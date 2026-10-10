package net.minecraft.world.entity.ai.village.poi;

import org.junit.jupiter.api.Tag;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the native-owned POI section distance tracker with the pinned
 * original, both driven through PoiManager's notification protocol: every
 * setDirty/onSectionLoad passes the section's current village-centre state. */
@Tag("parity")
class NativePoiDistanceTrackerTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    static final class Pair {
        // Shared truth: which sections are village centres, and which are loaded.
        final LongOpenHashSet centres = new LongOpenHashSet();
        final LongLinkedOpenHashSet loaded = new LongLinkedOpenHashSet();
        final JavaPoiDistanceTracker original = new JavaPoiDistanceTracker(this.centres::contains);
        final PoiManager.DistanceTracker candidate = new PoiManager.DistanceTracker(this.centres::contains, this.loaded::iterator);

        /** PoiManager.setDirty / onSectionLoad after the section's records changed. */
        void notify(long section, boolean centre) {
            this.loaded.add(section);
            if (centre) this.centres.add(section); else this.centres.remove(section);
            this.original.update(section, this.original.getLevelFromSource(section), false);
            this.candidate.sectionChanged(section);
        }

        /** A tag rebind changes village membership without notifications. */
        void silentlyChange(long section, boolean centre) {
            if (centre) this.centres.add(section); else this.centres.remove(section);
        }

        void tagsReloaded() {
            this.candidate.refreshCentres();
        }

        void run() {
            this.original.runAllUpdates();
            this.candidate.runAllUpdates();
        }

        void compare(long[] probes, String context) {
            assertEquals(entries(originalLevels(this.original)), entries(this.candidate.levels), () -> context + " published iteration order");
            for (long probe : probes) {
                assertEquals(this.original.getLevel(probe), this.candidate.getLevel(probe), () -> context + " level " + probe);
            }
        }
    }

    /** The pinned oracle keeps its map private; read it without changing its source. */
    static Long2ByteMap originalLevels(JavaPoiDistanceTracker tracker) {
        try {
            java.lang.reflect.Field field = JavaPoiDistanceTracker.class.getDeclaredField("levels");
            field.setAccessible(true);
            return (Long2ByteMap)field.get(tracker);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static List<String> entries(Long2ByteMap map) {
        List<String> entries = new ArrayList<>();
        for (Long2ByteMap.Entry entry : map.long2ByteEntrySet()) entries.add(entry.getLongKey() + "=" + entry.getByteValue());
        return entries;
    }

    static long[] probes(long anchor, int radius) {
        List<Long> probes = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) for (int dz = -radius; dz <= radius; dz++) {
            probes.add(SectionPos.offset(anchor, dx, dy, dz));
        }
        probes.add(Long.MAX_VALUE);
        return probes.stream().mapToLong(Long::longValue).toArray();
    }

    @Test void exhaustiveShortHistories() {
        // Every length-four history over two sections: centre on/off notifications,
        // an unchanged notification, a silent change plus tag reload, and runs.
        long[] sections = {SectionPos.asLong(0, 0, 0), SectionPos.asLong(2, 1, -1)};
        long[] probes = probes(sections[0], 8);
        int actions = 2 * 4 + 1, histories = 0;
        for (int path = 0, total = (int)Math.pow(actions, 4); path < total; path++) {
            Pair pair = new Pair();
            int remaining = path;
            for (int step = 0; step < 4; step++) {
                int action = remaining % actions;
                remaining /= actions;
                if (action == 8) {
                    pair.run();
                } else {
                    long section = sections[action / 4];
                    switch (action % 4) {
                        case 0 -> pair.notify(section, true);
                        case 1 -> pair.notify(section, false);
                        case 2 -> pair.notify(section, pair.centres.contains(section));
                        default -> {
                            pair.loaded.add(section);
                            pair.silentlyChange(section, !pair.centres.contains(section));
                            pair.tagsReloaded();
                        }
                    }
                }
                pair.compare(probes, "history " + path + " step " + step);
            }
            pair.run();
            pair.compare(probes, "history " + path + " settled");
            histories++;
        }
        System.out.println("POI_DISTANCE_EXHAUSTIVE histories=" + histories);
    }

    static void churn(long seed, int ax, int ay, int az, int steps, int spread) {
        Random random = new Random(seed);
        Pair pair = new Pair();
        long anchor = SectionPos.asLong(ax, ay, az);
        long[] probes = probes(anchor, spread / 2 + 7);
        for (int step = 0; step < steps; step++) {
            long section = SectionPos.asLong(ax + random.nextInt(spread) - spread / 2, ay + random.nextInt(spread) - spread / 2,
                az + random.nextInt(spread) - spread / 2);
            int action = random.nextInt(20);
            if (action < 12) {
                pair.notify(section, random.nextInt(3) != 0);
            } else if (action < 15) {
                pair.notify(section, pair.centres.contains(section));
            } else if (action == 15) {
                // A data pack reload flips village membership for several loaded sections.
                for (long loaded : pair.loaded.toLongArray()) if (random.nextInt(4) == 0) pair.silentlyChange(loaded, !pair.centres.contains(loaded));
                pair.tagsReloaded();
            } else {
                pair.run();
                pair.compare(probes, "seed " + seed + " step " + step);
            }
        }
        pair.run();
        pair.compare(probes, "seed " + seed + " final");
    }

    @Test void seededChurnMatchesAcrossPackingEdges() {
        churn(1, 0, 0, 0, 400, 6);
        churn(2, 40, -3, -77, 400, 14);
        // Packed coordinate wrap: x/z 22 bits, y 20 bits.
        churn(3, (1 << 21) - 1, (1 << 19) - 1, -(1 << 21), 300, 6);
        // Neighbours of the Long.MAX_VALUE source sentinel (2097151, -1, -1).
        churn(4, 2097150, -1, -1, 300, 6);
    }

    @Test void unreachableInstancesReleaseNativeState() throws Exception {
        for (int round = 0; round < 2000; round++) {
            Pair pair = new Pair();
            pair.notify(SectionPos.asLong(round, 0, -round), true);
            pair.candidate.runAllUpdates();
        }
        System.gc();
        Thread.sleep(50);
        Pair survivor = new Pair();
        survivor.notify(0, true);
        survivor.run();
        assertEquals(0, survivor.candidate.getLevel(0));
    }
}
