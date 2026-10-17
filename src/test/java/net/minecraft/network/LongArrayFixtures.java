package net.minecraft.network;

import io.netty.buffer.*;
import java.nio.ByteOrder;
import java.util.SplittableRandom;

final class LongArrayFixtures {
    static final String[] NATIVE_KINDS={"heap","safe_heap","pooled_heap","direct","pooled_direct","heap_slice","direct_slice","heap_duplicate","pooled_direct_slice"};

    static ByteBuf buffer(String kind,int capacity,int maximum) {
        return switch(kind) {
            case "heap" -> UnpooledByteBufAllocator.DEFAULT.heapBuffer(capacity,maximum);
            case "safe_heap" -> new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT,capacity,maximum);
            case "pooled_heap" -> PooledByteBufAllocator.DEFAULT.heapBuffer(capacity,maximum);
            case "direct" -> UnpooledByteBufAllocator.DEFAULT.directBuffer(capacity,maximum);
            case "pooled_direct" -> PooledByteBufAllocator.DEFAULT.directBuffer(capacity,maximum);
            case "heap_slice","direct_slice","pooled_direct_slice" -> {
                String base=kind.equals("heap_slice")?"heap":kind.equals("direct_slice")?"direct":"pooled_direct";
                var parent=buffer(base,capacity+32,maximum+32);parent.writerIndex(capacity+32);
                var slice=parent.retainedSlice(16,capacity);parent.release();slice.clear();yield slice;
            }
            case "heap_duplicate" -> { var parent=buffer("heap",capacity,maximum);yield parent.duplicate(); }
            case "little" -> buffer("heap",capacity,maximum).order(ByteOrder.LITTLE_ENDIAN);
            case "readonly" -> buffer("heap",capacity,maximum).asReadOnly();
            default -> throw new IllegalArgumentException(kind);
        };
    }

    static long[] values(int count,long seed) {
        var random=new SplittableRandom(seed);var out=new long[count];
        for(int i=0;i<count;i++)out[i]=switch(i%8){case 0->0;case 1->-1;case 2->Long.MIN_VALUE;case 3->Long.MAX_VALUE;default->random.nextLong();};
        return out;
    }
    static byte[] bytes(ByteBuf buffer){var out=new byte[buffer.capacity()];buffer.getBytes(0,out);return out;}
    static void sentinel(ByteBuf buffer){buffer.setZero(0,buffer.capacity());}
    static String error(Runnable action){try{action.run();return null;}catch(Throwable e){return e.getClass().getName()+":"+e.getMessage();}}
    private LongArrayFixtures() {}
}
