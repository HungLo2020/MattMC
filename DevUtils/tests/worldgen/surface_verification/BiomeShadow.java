package surfaceverification;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.NativeBiomeVerification;

/** Test-agent oracle: compare every lookup in the actual generation thread/order.
 * OriginalClimateOracle and renamed JavaClimate are extracted from the Git reference. */
public final class BiomeShadow {
    private record Reference(NativeBiomeVerification.Oracle oracle, Object[] values) {}
    private static final Map<Climate.ParameterList<?>,Reference> REFERENCES = new IdentityHashMap<>();
    private static final ThreadLocal<ArrayDeque<Call>> CALLS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final AtomicLong SCALARS = new AtomicLong(), BATCHES = new AtomicLong(), FAILURES = new AtomicLong();
    private static final NativeBiomeVerification.OracleFactory FACTORY;
    static {
        try {
            FACTORY=(NativeBiomeVerification.OracleFactory)Class.forName("net.minecraft.world.level.biome.OriginalClimateOracle").getConstructor().newInstance();
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static final class Call {
        final Reference reference;
        final Object[] output;
        final Object[] expected;
        int count;
        Call(Reference reference,Object[] output,int size) {
            this.reference=reference;this.output=output;this.expected=new Object[size];
        }
    }
    private static synchronized Reference reference(Climate.ParameterList<?> list) {
        if(list.getClass()!=Climate.ParameterList.class)throw new AssertionError("Shadow requires the default metric");
        return REFERENCES.computeIfAbsent(list, key -> {
            var entries=key.values();long[][] boxes=new long[entries.size()][13];Object[] values=new Object[entries.size()];
            for(int i=0;i<boxes.length;i++) {
                var point=entries.get(i).getFirst();
                Climate.Parameter[] axes={point.temperature(),point.humidity(),point.continentalness(),point.erosion(),point.depth(),point.weirdness()};
                for(int d=0;d<6;d++){boxes[i][d*2]=axes[d].min();boxes[i][d*2+1]=axes[d].max();}
                boxes[i][12]=point.offset();values[i]=entries.get(i).getSecond();
            }
            return new Reference(FACTORY.create(boxes),values);
        });
    }
    private static Object expected(Reference reference,Climate.TargetPoint p) {
        return reference.values[reference.oracle.find(new long[]{p.temperature(),p.humidity(),p.continentalness(),p.erosion(),p.depth(),p.weirdness()})];
    }
    public static void scalarStart(Climate.ParameterList<?> tree,Climate.TargetPoint point) {
        var call=new Call(reference(tree),null,1);call.expected[0]=expected(call.reference,point);CALLS.get().push(call);
    }
    public static void scalarEnd(Object value) {
        var call=CALLS.get().pop();check(call.expected[0],value);SCALARS.incrementAndGet();
    }
    public static void sectionStart(Climate.ParameterList<?> tree,Object[] output) {
        CALLS.get().push(new Call(reference(tree),output,64));
    }
    public static void sampled(Climate.TargetPoint point) {
        var call=CALLS.get().peek();
        if(call!=null && call.output!=null) {
            if(call.count==64)throw new AssertionError("More than 64 section samples");
            call.expected[call.count++]=expected(call.reference,point);
        }
    }
    public static void sectionEnd() {
        var call=CALLS.get().pop();
        if(call.count!=64)throw new AssertionError("Missing section samples: "+call.count);
        for(int i=0;i<64;i++)check(call.expected[i],call.output[i]);
        BATCHES.addAndGet(64);
    }
    private static void check(Object expected,Object actual) {
        if(expected!=actual){FAILURES.incrementAndGet();throw new AssertionError("Biome oracle mismatch: expected="+expected+" actual="+actual);}
    }
    public static Map<String,Long> results() {
        if(FAILURES.get()!=0 || SCALARS.get()+BATCHES.get()==0)throw new AssertionError("Biome shadow did not pass");
        return Map.of("scalar_queries",SCALARS.get(),"section_queries",BATCHES.get(),"mismatches",FAILURES.get());
    }
}
