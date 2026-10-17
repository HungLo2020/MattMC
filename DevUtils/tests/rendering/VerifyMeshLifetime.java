package net.vulkanic.world;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Internal renderer policy replay against the pinned pre-migration tracker.
 * This is supplemental to Frozen OpenGL gameplay, not a Frozen renderer oracle. */
public final class VerifyMeshLifetime {
    private static void equal(Object expected, Object actual, int step) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("step " + step + ": " + expected + " != " + actual);
    }
    private static Object retire(DynamicWorldMeshLifetime o, long frame) {
        try { return o.retireBeforeFrame(frame).stream().map(v -> v.meshKey() + ":" + v.meshGeneration()).toList(); }
        catch (IllegalArgumentException e) { return e.getMessage(); }
    }
    private static Object retire(LegacyDynamicWorldMeshLifetime o, long frame) {
        try { return o.retireBeforeFrame(frame).stream().map(v -> v.meshKey() + ":" + v.meshGeneration()).toList(); }
        catch (IllegalArgumentException e) { return e.getMessage(); }
    }
    private static void replay() {
        var current = new DynamicWorldMeshLifetime(); var old = new LegacyDynamicWorldMeshLifetime();
        var marks = new ArrayList<DynamicWorldMeshLifetime.Checkpoint>();
        var oldMarks = new ArrayList<LegacyDynamicWorldMeshLifetime.Checkpoint>();
        Random r = new Random(2809451);
        long frame = 1;
        for (int step = 0; step < 200000; step++) {
            long key = r.nextInt(97) - 48, generation = r.nextLong(), sampleFrame = frame + r.nextInt(3) - 1;
            switch (r.nextInt(10)) {
                case 0, 1, 2, 3 -> {
                    String a = "ok", b = "ok";
                    try { old.observe(key, generation, sampleFrame); } catch (IllegalArgumentException e) { a = e.getMessage(); }
                    try { current.observe(key, generation, sampleFrame); } catch (IllegalArgumentException e) { b = e.getMessage(); }
                    equal(a, b, step);
                }
                case 4 -> { old.forget(key); current.forget(key); }
                case 5 -> {
                    marks.add(current.checkpoint(frame)); oldMarks.add(old.checkpoint(frame));
                    equal((long)oldMarks.getLast().changeCount(), marks.getLast().changeCount(), step);
                }
                case 6 -> {
                    int index = marks.isEmpty() ? -1 : r.nextInt(marks.size());
                    String a = "ok", b = "ok";
                    try { old.rollbackTo(index < 0 ? null : oldMarks.get(index)); } catch (IllegalArgumentException e) { a = e.getMessage(); }
                    try { current.rollbackTo(index < 0 ? null : marks.get(index)); } catch (IllegalArgumentException e) { b = e.getMessage(); }
                    equal(a, b, step);
                }
                case 7 -> equal(retire(old, sampleFrame), retire(current, sampleFrame), step);
                case 8 -> { old.discardCheckpointHistory(); current.discardCheckpointHistory(); marks.clear(); oldMarks.clear(); }
                case 9 -> { frame++; if (marks.size() > 64) { marks.clear(); oldMarks.clear(); } }
            }
            equal(old.size(), current.size(), step);
            if (step % 100 == 0) {
                var a = old.checkpoint(frame); var b = current.checkpoint(frame);
                equal(retire(old, Long.MAX_VALUE), retire(current, Long.MAX_VALUE), step);
                // The extreme-frame retirement invalidates both marks.
                String x = "ok", y = "ok";
                try { old.rollbackTo(a); } catch (IllegalArgumentException e) { x = e.getMessage(); }
                try { current.rollbackTo(b); } catch (IllegalArgumentException e) { y = e.getMessage(); }
                equal(x, y, step);
            }
        }
        equal(retire(old, Long.MAX_VALUE), retire(current, Long.MAX_VALUE), 200000);
        System.out.println("REPLAY 200000 PASS");
    }
    interface Tracker {
        void checkpoint(long frame);
        void observe(long key, long generation, long frame);
        long retire(long frame);
        int size();
        default void rig(int model, int variant, long frame) {
            for(int part=0;part<16;part++) {
                long key=1000+model*16+part+variant*256;
                observe(key,key+17,frame);
            }
        }
    }
    static final class Current implements Tracker {
        final DynamicWorldMeshLifetime o = new DynamicWorldMeshLifetime();
        Current() {
            for(int variant=0;variant<4;variant++)for(int model=0;model<16;model++) {
                int[] parents=new int[16];long[] keys=new long[16],generations=new long[16];
                for(int part=0;part<16;part++){parents[part]=part==0?-1:0;keys[part]=1000+model*16+part+variant*256;generations[part]=keys[part]+17;}
                net.vulkanic.bridge.VulkanicGalBridge.registerModelRig(0x7d02_0000L+variant*16+model,parents,keys,generations);
            }
        }
        public void rig(int model,int variant,long frame) {o.observeRig(0x7d02_0000L+variant*16+model,frame);}
        public void checkpoint(long f) { o.checkpoint(f); }
        public void observe(long k,long g,long f) { o.observe(k,g,f); }
        public long retire(long f) { long sum=0;for(var row:o.retireBeforeFrame(f))sum+=row.meshKey()^row.meshGeneration();return sum; }
        public int size() { return o.size(); }
    }
    static final class Legacy implements Tracker {
        final LegacyDynamicWorldMeshLifetime o = new LegacyDynamicWorldMeshLifetime();
        public void checkpoint(long f) { o.checkpoint(f); }
        public void observe(long k,long g,long f) { o.observe(k,g,f); }
        public long retire(long f) { long sum=0;for(var row:o.retireBeforeFrame(f))sum+=row.meshKey()^row.meshGeneration();return sum; }
        public int size() { return o.size(); }
    }
    static long run(Tracker tracker, long first, boolean churn, boolean rigs) {
        long sum=0;
        for(long frame=first;frame<first+1024;frame++) {
            sum+=tracker.retire(frame);tracker.checkpoint(frame);
            for(int model=0;model<16;model++) {
                tracker.checkpoint(frame);
                if(rigs){tracker.rig(model,churn?(int)(frame%4):0,frame);tracker.rig(model,churn?(int)(frame%4):0,frame);continue;}
                for(int part=0;part<16;part++) {
                    long key=1000+model*16+part+(churn?(frame%4)*256:0);
                    tracker.observe(key,key+17,frame);
                    tracker.observe(key,key+17,frame); // Repeated model/foil usage.
                }
            }
            sum+=tracker.size();
        }
        return sum;
    }
    public static void main(String[] args) {
        if(args.length==0){replay();return;}
        Tracker tracker=args[0].equals("current")?new Current():new Legacy();
        boolean churn=args[1].endsWith("churn"),rigs=args[1].startsWith("rig-");
        var allocation=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        var compiler=ManagementFactory.getCompilationMXBean();
        long thread=Thread.currentThread().threadId();long[][] samples=new long[8][4];
        for(int round=-32;round<8;round++) {
            long first=(round+33L)*1024+1;
            long before=allocation.getThreadAllocatedBytes(thread),compiled=compiler.getTotalCompilationTime(),start=System.nanoTime();
            long sum=run(tracker,first,churn,rigs);
            long nanos=System.nanoTime()-start,bytes=allocation.getThreadAllocatedBytes(thread)-before;
            if(round>=0)samples[round]=new long[]{nanos,bytes,sum,compiler.getTotalCompilationTime()-compiled};
        }
        for(var row:samples)System.out.println("SAMPLE "+row[0]+" "+row[1]+" "+row[2]+" "+row[3]);
    }
}
