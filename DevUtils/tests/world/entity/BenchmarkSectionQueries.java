import java.lang.management.ManagementFactory;
import java.lang.foreign.Arena;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;

/** Production section queries with prebuilt primary entities; excludes simulation/GPU. */
public final class BenchmarkSectionQueries {
    private static volatile long sink;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId();
        try(var scope=Arena.ofConfined()) {
            if(args.length>0 && args[0].equals("world")) {
                worldQueries(scope,bean,thread);
                return;
            }
            for(int size:new int[]{1,8,64,256}) {
                var section=new EntitySection<VerifySectionMembers.SpatialEntity>(VerifySectionMembers.SpatialEntity.class,Visibility.TRACKED);
                for(int i=0;i<size;i++)section.add(VerifySectionMembers.spatial(i,scope));
                for(int hits:new int[]{0,1,size}) {
                    AABB query=new AABB(-1,-1,-1,hits==0?-0.5:hits*2-0.5,2,2);
                    var sum=new long[1];
                    AbortableIterationConsumer<VerifySectionMembers.SpatialEntity> consumer=e->{sum[0]+=e.ordinal+1;return AbortableIterationConsumer.Continuation.CONTINUE;};
                    for(int sample=-5;sample<8;sample++) {
                        sum[0]=0;
                        long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                        for(int repeat=0;repeat<32768;repeat++)section.getEntities(query,consumer);
                        long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;
                        sink=sum[0];
                        if(sample>=0)System.out.println("SECTION_TIMING "+size+" "+hits+" "+nanos+" "+bytes+" "+sink);
                    }
                }
            }
        }
    }

    private static void worldQueries(Arena scope,com.sun.management.ThreadMXBean bean,long thread) throws Exception {
        for(int size:new int[]{1,8,64,256})for(int hits:new int[]{0,1,size}) {
            var storage=new EntitySectionStorage<VerifySectionMembers.SpatialEntity>(VerifySectionMembers.SpatialEntity.class,k->Visibility.TRACKED);
            for(int i=0;i<size;i++) {
                var e=VerifySectionMembers.spatial(i,scope);
                e.setBoundingBox(new AABB(i*16+1,i<hits?0:8,0,i*16+2,i<hits?1:9,1));
                storage.getOrCreateSection(net.minecraft.core.SectionPos.asLong(i,0,0)).add(e);
            }
            var query=new AABB(0,-1,-1,size*16,2,2);
            long[] sum={0};
            AbortableIterationConsumer<VerifySectionMembers.SpatialEntity> consumer=e->{sum[0]+=e.ordinal+1;return AbortableIterationConsumer.Continuation.CONTINUE;};
            for(int sample=-5;sample<8;sample++) {
                sum[0]=0;
                long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                for(int repeat=0;repeat<4096;repeat++)storage.getEntities(query,consumer);
                long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;
                sink=sum[0];
                if(sample>=0)System.out.println("SECTION_TIMING "+size+" "+hits+" "+nanos+" "+bytes+" "+sink);
            }
        }
    }
}
