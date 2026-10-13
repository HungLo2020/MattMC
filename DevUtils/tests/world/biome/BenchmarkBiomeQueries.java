import java.lang.management.ManagementFactory;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;

/** Scalar production-manager cost; excludes world lookup, simulation and GPU work. */
public final class BenchmarkBiomeQueries {
    private static volatile long sink;
    public static void main(String[] args) {
        var random=new Random(871623);
        var positions=new BlockPos[16384];
        for(int i=0;i<positions.length;i++)positions[i]=new BlockPos(random.nextInt(60000001)-30000000,random.nextInt(1024)-256,random.nextInt(60000001)-30000000);
        var sum=new long[1];
        var manager=new BiomeManager((x,y,z)->{sum[0]+=x*31L+y*17L+z;return null;},-683227129481L);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId();
        for(int sample=-5;sample<8;sample++) {
            sum[0]=0;
            long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int repeat=0;repeat<64;repeat++)for(var position:positions)manager.getBiome(position);
            long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;
            sink=sum[0];
            if(sample>=0)System.out.println("BIOME_TIMING "+nanos+" "+bytes+" "+sink);
        }
    }
}
