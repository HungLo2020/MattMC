import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Re-read preserved recordings; discard each thread's first allocation sample
 * because its weight can include allocation while recording was disabled.
 * Site sample counts identify paths; weights are approximate, never exact bytes.
 */
public final class SummarizeInventoryJfr {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);
        for(String mode:List.of("forward_zero","forward_many","forward_empty","add_many","pick_empty")) {
            var seenThreads=new HashSet<Long>();var sites=new HashMap<String,long[]>();var cpu=new HashMap<String,Long>();long discarded=0,discardedWeight=0;
            try(var recording=new RecordingFile(out.resolve(mode+".jfr"))) {
                while(recording.hasMoreEvents()) {
                    var event=recording.readEvent();String kind=event.getEventType().getName();
                    if(kind.equals("jdk.ObjectAllocationSample")) {
                        long thread=event.getThread()==null?-1:event.getThread().getJavaThreadId();
                        if(seenThreads.add(thread)){discarded++;discardedWeight+=event.getLong("weight");continue;}
                        if(event.getStackTrace()==null||!AttributeInventoryReference.operation(event.getStackTrace().getFrames()))continue;
                        String key=event.getClass("objectClass").getName()+" @ "+AttributeInventoryReference.trace(event.getStackTrace().getFrames());var value=sites.computeIfAbsent(key,k->new long[2]);value[0]++;value[1]+=event.getLong("weight");
                    } else if((kind.equals("jdk.ExecutionSample")||kind.equals("jdk.NativeMethodSample"))&&event.getStackTrace()!=null&&AttributeInventoryReference.operation(event.getStackTrace().getFrames())) {
                        cpu.merge(AttributeInventoryReference.trace(event.getStackTrace().getFrames()),1L,Long::sum);
                    }
                }
            }
            var lines=new ArrayList<String>();lines.add("mode="+mode+" discarded_first_samples="+discarded+" discarded_carry_in_weight="+discardedWeight);
            lines.add("SITE COUNTS identify allocation paths. Remaining weights are approximate sampled estimates, not exact operation bytes. CPU includes transition stubs; no additive causal timing/acceptance claim.");
            sites.entrySet().stream().sorted((a,b)->Long.compare(b.getValue()[0],a.getValue()[0])).forEach(e->lines.add("SAMPLES "+e.getValue()[0]+" WEIGHT "+e.getValue()[1]+" "+e.getKey()));
            cpu.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).forEach(e->lines.add("CPU_SAMPLES "+e.getValue()+" "+e.getKey()));
            Files.write(out.resolve(mode+"-filtered.txt"),lines);System.out.println(lines.get(0));
        }
    }
}
