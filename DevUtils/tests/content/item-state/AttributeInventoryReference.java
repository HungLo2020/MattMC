import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import jdk.jfr.*;
import jdk.jfr.consumer.*;
import net.minecraft.world.item.ItemStack;

/** Single sealed-current diagnostic. Profile complete production operations;
 * reset/consumption remain in the recording but are excluded by actual operation
 * stack ancestry. Sample weights are estimates, never additive causal timings.
 */
public final class AttributeInventoryReference {
    static volatile long sink;
    static boolean operation(List<RecordedFrame> frames) {
        for(var frame:frames){var method=frame.getMethod();String type=method.getType().getName(),name=method.getName();
            if(type.equals("net.minecraft.world.inventory.ChestMenu")&&name.equals("quickMoveStack"))return true;
            if(type.equals("net.minecraft.world.entity.player.Inventory")&&(name.equals("add")||name.equals("addAndPickItem")))return true;
        }return false;
    }
    static String trace(List<RecordedFrame> frames) {
        var rows=new ArrayList<String>();for(var frame:frames){var method=frame.getMethod();rows.add(method.getType().getName()+"."+method.getName());if(rows.size()==7)break;}return String.join(" <- ",rows);
    }
    static void profile(Path out,String mode)throws Exception {
        boolean stock=mode.startsWith("forward_");var stocks=stock?new StockChestTransferTimingReference.Fixture[32]:null;
        var inventories=stock?null:new InventoryTimingReference.Fixture[32];
        for(int i=0;i<32;i++){if(stock)stocks[i]=new StockChestTransferTimingReference.Fixture(mode.substring(8));else inventories[i]=new InventoryTimingReference.Fixture(mode);}
        for(int i=0;i<12_000;i++){int index=i&31;if(stock){var f=stocks[index];f.reset();sink^=f.consume(f.menu.quickMoveStack(null,27));}else{var f=inventories[index];f.reset();sink^=f.consume(f.run());}}
        Path recording=out.resolve(mode+".jfr");
        try(var rec=new Recording()) {
            rec.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle","1000/s");
            rec.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5));
            rec.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(5));
            rec.start();
            for(int i=0;i<60_000;i++){int index=i&31;if(stock){var f=stocks[index];f.reset();sink^=f.consume(f.menu.quickMoveStack(null,27));}else{var f=inventories[index];f.reset();sink^=f.consume(f.run());}}
            rec.stop();rec.dump(recording);
        }
        var allocations=new TreeMap<String,Long>();var cpu=new TreeMap<String,Long>();long allAlloc=0,matchedAlloc=0,allCpu=0,matchedCpu=0;
        try(var input=new RecordingFile(recording)) {
            while(input.hasMoreEvents()) {
                var event=input.readEvent();var stack=event.getStackTrace();if(stack==null)continue;var frames=stack.getFrames();String kind=event.getEventType().getName();
                if(kind.equals("jdk.ObjectAllocationSample")){allAlloc++;if(!operation(frames))continue;matchedAlloc++;String key=event.getClass("objectClass").getName()+" @ "+trace(frames);allocations.merge(key,event.getLong("weight"),Long::sum);}
                else if(kind.equals("jdk.ExecutionSample")||kind.equals("jdk.NativeMethodSample")){allCpu++;if(!operation(frames))continue;matchedCpu++;cpu.merge(trace(frames),1L,Long::sum);}
            }
        }
        var lines=new ArrayList<String>();lines.add("mode="+mode+" operations=60000 allocation_samples_all="+allAlloc+" operation_allocation_samples="+matchedAlloc+" cpu_samples_all="+allCpu+" operation_cpu_samples="+matchedCpu);
        lines.add("ESTIMATED ALLOCATION WEIGHTS; reset/consumption excluded by production operation ancestry; no timing or acceptance claim");
        allocations.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).forEach(e->lines.add("ALLOC_WEIGHT "+e.getValue()+" "+e.getKey()));
        lines.add("CPU SAMPLE COUNTS; include native-method events and transition stubs, no additive causal decomposition");
        cpu.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).forEach(e->lines.add("CPU_SAMPLES "+e.getValue()+" "+e.getKey()));
        Files.write(out.resolve(mode+".txt"),lines);System.out.println(lines.get(0));
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(String mode:List.of("forward_zero","forward_many","forward_empty","add_many","pick_empty"))profile(Path.of(args[0]),mode);
        System.out.println("ATTRIBUTION_ONLY sink="+sink);
    }
}
