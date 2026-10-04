package net.minecraft.world.entity.ai.village.poi;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.PlayerChunkDistancesVerification;

/** Production-path timing of the POI section distance tracker. One JVM runs one
 * backend ({@code java}: pinned original, {@code native}: production view):
 * PoiManager setDirty notifications (mostly unchanged centres, as ticket claims
 * and releases are), village centres appearing/disappearing, per-tick
 * runAllUpdates and sectionsToVillage queries. Construction is untimed. */
public final class PoiDistanceTrackerVerification {
    /** Notification: section and its village-centre state afterwards. */
    record Tick(long[] sections, boolean[] centres, long[] queries) {}

    interface Backend {
        void notify(long section, boolean centre);
        void runAll();
        int sectionsToVillage(long section);
    }

    static final class OriginalBackend implements Backend {
        final LongOpenHashSet centres = new LongOpenHashSet();
        final JavaPoiDistanceTracker tracker = new JavaPoiDistanceTracker(this.centres::contains);
        @Override public void notify(long section, boolean centre) {
            if (centre) this.centres.add(section); else this.centres.remove(section);
            this.tracker.update(section, this.tracker.getLevelFromSource(section), false);
        }
        @Override public void runAll() { this.tracker.runAllUpdates(); }
        @Override public int sectionsToVillage(long section) { this.tracker.runAllUpdates(); return this.tracker.getLevel(section); }
    }

    static final class NativeBackend implements Backend {
        final LongOpenHashSet centres = new LongOpenHashSet();
        final LongLinkedOpenHashSet loaded = new LongLinkedOpenHashSet();
        final PoiManager.DistanceTracker tracker = new PoiManager.DistanceTracker(this.centres::contains, this.loaded::iterator);
        @Override public void notify(long section, boolean centre) {
            if (centre) this.centres.add(section); else this.centres.remove(section);
            this.tracker.sectionChanged(section);
        }
        @Override public void runAll() { this.tracker.runAllUpdates(); }
        @Override public int sectionsToVillage(long section) { this.tracker.runAllUpdates(); return this.tracker.getLevel(section); }
    }

    static List<Tick> workload(String name) {
        int villages, ticks, notifications, toggleOneIn;
        switch (name) {
            case "village_occupancy" -> { villages = 3; ticks = 1200; notifications = 8; toggleOneIn = 40; }
            case "village_growth" -> { villages = 12; ticks = 600; notifications = 6; toggleOneIn = 3; }
            case "poi_churn" -> { villages = 6; ticks = 1200; notifications = 24; toggleOneIn = 200; }
            default -> throw new IllegalArgumentException(name);
        }
        Random random = new Random(name.hashCode());
        int[][] centresOf = new int[villages][];
        for (int village = 0; village < villages; village++) {
            centresOf[village] = new int[]{random.nextInt(80) - 40, random.nextInt(4) + 2, random.nextInt(80) - 40};
        }
        LongOpenHashSet centres = new LongOpenHashSet();
        List<Tick> workload = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            long[] sections = new long[notifications];
            boolean[] states = new boolean[notifications];
            for (int index = 0; index < notifications; index++) {
                int[] village = centresOf[random.nextInt(villages)];
                long section = SectionPos.asLong(village[0] + random.nextInt(5) - 2, village[1] + random.nextInt(3) - 1, village[2] + random.nextInt(5) - 2);
                boolean centre = centres.contains(section);
                if (tick == 0 || random.nextInt(toggleOneIn) == 0) centre = !centre || tick == 0;
                if (centre) centres.add(section); else centres.remove(section);
                sections[index] = section;
                states[index] = centre;
            }
            long[] queries = new long[32];
            for (int query = 0; query < queries.length; query++) {
                int[] village = centresOf[random.nextInt(villages)];
                queries[query] = SectionPos.asLong(village[0] + random.nextInt(17) - 8, village[1] + random.nextInt(5) - 2, village[2] + random.nextInt(17) - 8);
            }
            workload.add(new Tick(sections, states, queries));
        }
        return workload;
    }

    static long round(Backend backend, List<Tick> workload) {
        long checksum = 0;
        for (Tick tick : workload) {
            for (int index = 0; index < tick.sections().length; index++) backend.notify(tick.sections()[index], tick.centres()[index]);
            backend.runAll();
            for (long query : tick.queries()) checksum = checksum * 31 + backend.sectionsToVillage(query);
        }
        return checksum;
    }

    static String trace(List<Tick> workload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Tick tick : workload) {
            digest.update(Arrays.toString(tick.sections()).getBytes());
            digest.update(Arrays.toString(tick.centres()).getBytes());
            digest.update(Arrays.toString(tick.queries()).getBytes());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        List<Tick> workload = workload(name);
        java.util.function.Supplier<Backend> factory = switch (mode) {
            case "java" -> OriginalBackend::new;
            case "native" -> NativeBackend::new;
            default -> throw new IllegalArgumentException(mode);
        };
        long notifications = workload.stream().mapToLong(tick -> tick.sections().length).sum();
        System.out.println("PLAYER_DISTANCE_FIXTURE case=" + name + " ticks=" + workload.size() + " moves=" + notifications + " trace=" + trace(workload));
        PlayerChunkDistancesVerification.measure(mode, name, quick, factory, backend -> round(backend, workload));
    }
}
