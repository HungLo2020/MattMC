package net.minecraft.world;

import net.minecraft.core.BlockPos;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Example performance benchmark demonstrating JMH usage.
 * This benchmarks BlockPos creation and manipulation operations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BlockPosBenchmark {
    
    private BlockPos pos;
    
    @Setup
    public void setup() {
        pos = new BlockPos(100, 64, 200);
    }
    
    @Benchmark
    public BlockPos benchmarkBlockPosCreation() {
        return new BlockPos(100, 64, 200);
    }
    
    @Benchmark
    public BlockPos benchmarkBlockPosOffset() {
        return pos.offset(1, 0, 1);
    }
    
    @Benchmark
    public BlockPos benchmarkBlockPosAbove() {
        return pos.above();
    }
    
    @Benchmark
    public BlockPos benchmarkBlockPosBelow() {
        return pos.below();
    }
    
    @Benchmark
    public long benchmarkBlockPosAsLong() {
        return pos.asLong();
    }
    
    /**
     * Main method to run the benchmark standalone.
     * This allows running the benchmark with: java BlockPosBenchmark
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(BlockPosBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
