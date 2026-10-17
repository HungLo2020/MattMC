package net.minecraft.network;

import io.netty.buffer.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLongArrayTest {
    @Test void literalBytesIndexesAndTailsAcrossAllNativeBufferKinds() {
        int fixtures=0;long longs=0;
        for(String kind:LongArrayFixtures.NATIVE_KINDS)for(int count:new int[]{0,1,7,127,128,129,256,342,512,8191,8192,8193,32768})for(int offset:new int[]{0,1,3,7,15}) {
            var input=LongArrayFixtures.values(count,count*1977L+offset);int capacity=offset+count*8+17;
            var a=LongArrayFixtures.buffer(kind,capacity,capacity);var b=LongArrayFixtures.buffer(kind,capacity,capacity);
            try {
                LongArrayFixtures.sentinel(a);LongArrayFixtures.sentinel(b);a.setIndex(offset,offset);b.setIndex(offset,offset);
                JavaLongArray.writeFixedSizeLongArray(a,input);FriendlyByteBuf.writeFixedSizeLongArray(b,input);
                assertEquals(a.readerIndex(),b.readerIndex());assertEquals(a.writerIndex(),b.writerIndex());
                assertArrayEquals(LongArrayFixtures.bytes(a),LongArrayFixtures.bytes(b),kind+" count="+count+" offset="+offset);
                var expected=new long[count];var actual=new long[count];
                JavaLongArray.readFixedSizeLongArray(a,expected);assertSame(actual,FriendlyByteBuf.readFixedSizeLongArray(b,actual));
                assertArrayEquals(input,expected);assertArrayEquals(expected,actual);assertEquals(a.readerIndex(),b.readerIndex());
                if(count>=128) {
                    b.setIndex(offset,offset);assertTrue(NativeLongArray.write(b,input),kind+" "+b.getClass());
                    var direct=new long[count];assertTrue(NativeLongArray.read(b,direct),kind+" "+b.getClass());assertArrayEquals(input,direct);
                }
                fixtures++;longs+=count;
            } finally { a.release();b.release(); }
        }
        System.out.println("LONG_ARRAY_PARITY fixtures="+fixtures+" longs="+longs);
    }

    @Test void realFriendlyAndRegistryWrappersAndLengthPrefixedArrays() {
        for(String kind:LongArrayFixtures.NATIVE_KINDS)for(int count:new int[]{0,1,127,128,256,512,8193})for(int wrapper=0;wrapper<3;wrapper++) {
            var input=LongArrayFixtures.values(count,123);int cap=count*8+32;
            var rawA=LongArrayFixtures.buffer(kind,cap,cap);var rawB=LongArrayFixtures.buffer(kind,cap,cap);
            ByteBuf a=wrapper==0?rawA:wrapper==1?new FriendlyByteBuf(rawA):new RegistryFriendlyByteBuf(new FriendlyByteBuf(rawA),null);
            ByteBuf b=wrapper==0?rawB:wrapper==1?new FriendlyByteBuf(rawB):new RegistryFriendlyByteBuf(new FriendlyByteBuf(rawB),null);
            try {
                LongArrayFixtures.sentinel(a);LongArrayFixtures.sentinel(b);a.setIndex(3,3);b.setIndex(3,3);
                JavaLongArray.writeLongArray(a,input);FriendlyByteBuf.writeLongArray(b,input);
                assertArrayEquals(LongArrayFixtures.bytes(a),LongArrayFixtures.bytes(b));assertEquals(a.writerIndex(),b.writerIndex());
                assertArrayEquals(JavaLongArray.readLongArray(a),FriendlyByteBuf.readLongArray(b));assertEquals(a.readerIndex(),b.readerIndex());
            } finally { a.release();b.release(); }
        }
    }

    @Test void truncatedReadsPreserveExactPartialOutputAndIndexes() {
        for(String kind:LongArrayFixtures.NATIVE_KINDS)for(int available:new int[]{0,1,7,8,127*8,128*8-1}) {
            var a=LongArrayFixtures.buffer(kind,1040,1040);var b=LongArrayFixtures.buffer(kind,1040,1040);
            try {
                var input=LongArrayFixtures.values(128,1977);JavaLongArray.writeFixedSizeLongArray(a,input);JavaLongArray.writeFixedSizeLongArray(b,input);
                a.writerIndex(available);b.writerIndex(available);var expected=new long[128];var actual=new long[128];Arrays.fill(expected,123);Arrays.fill(actual,123);
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(a,expected)),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(b,actual)));
                assertArrayEquals(expected,actual);assertEquals(a.readerIndex(),b.readerIndex());
            } finally {a.release();b.release();}
        }
    }

    @Test void growthAndCapacityFailuresKeepOriginalMutationOrder() {
        for(String kind:new String[]{"heap","safe_heap","pooled_heap","direct","pooled_direct"})for(int maximum:new int[]{7,8,1016,1023,1024,4096}) {
            var a=LongArrayFixtures.buffer(kind,1,maximum);var b=LongArrayFixtures.buffer(kind,1,maximum);var input=LongArrayFixtures.values(128,1977);
            try {
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(a,input)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(b,input)));
                assertEquals(a.capacity(),b.capacity());assertEquals(a.writerIndex(),b.writerIndex());
                var writtenA=new byte[a.writerIndex()];var writtenB=new byte[b.writerIndex()];a.getBytes(0,writtenA);b.getBytes(0,writtenB);
                assertArrayEquals(writtenA,writtenB);
            } finally {a.release();b.release();}
        }
    }

    @Test void swappedReadOnlyNullAndReleasedBuffersKeepTheirErrors() {
        var input=LongArrayFixtures.values(128,1977);
        for(String kind:new String[]{"little","readonly"}) {
            var a=LongArrayFixtures.buffer(kind,1056,1056);var b=LongArrayFixtures.buffer(kind,1056,1056);
            try {
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(a,input)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(b,input)));
                assertEquals(a.writerIndex(),b.writerIndex());assertArrayEquals(LongArrayFixtures.bytes(a),LongArrayFixtures.bytes(b));
                var outA=new long[128];var outB=new long[128];
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(a,outA)),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(b,outB)));
                assertArrayEquals(outA,outB);assertEquals(a.readerIndex(),b.readerIndex());
            } finally {a.release();b.release();}
        }
        for(long[] values:new long[][]{null,new long[0],input}) {
            assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(null,values)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(null,values)));
            assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(null,values)),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(null,values)));
        }
        for(String kind:new String[]{"heap","direct","pooled_heap","pooled_direct"}) {
            var a=LongArrayFixtures.buffer(kind,1024,1024);var b=LongArrayFixtures.buffer(kind,1024,1024);a.writerIndex(1024);b.writerIndex(1024);a.release();b.release();
            assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(a,input)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(b,input)));
            assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(a,new long[128])),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(b,new long[128])));
        }
    }

    static final class Observed extends UnpooledHeapByteBuf {
        int reads,writes;
        Observed(){super(UnpooledByteBufAllocator.DEFAULT,2048,2048);}
        @Override public long readLong(){if(++reads==7)throw new IllegalStateException("read 7");return super.readLong()^reads;}
        @Override public ByteBuf writeLong(long value){if(++writes==7)throw new IllegalStateException("write 7");return super.writeLong(value^writes);}
    }
    @Test void customBuffersAndViewsKeepEveryObservableCallback() {
        var input=LongArrayFixtures.values(128,1977);
        for(boolean slice:new boolean[]{false,true}) {
            var rawA=new Observed();var rawB=new Observed();ByteBuf a=slice?rawA.slice(0,2048):rawA;ByteBuf b=slice?rawB.slice(0,2048):rawB;
            try {
                assertFalse(NativeLongArray.write(b,input));
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(a,input)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(b,input)));
                assertEquals(rawA.writes,rawB.writes);assertEquals(a.writerIndex(),b.writerIndex());
                a.writerIndex(1024);b.writerIndex(1024);var outA=new long[128];var outB=new long[128];
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(a,outA)),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(b,outB)));
                assertEquals(rawA.reads,rawB.reads);assertArrayEquals(outA,outB);assertEquals(a.readerIndex(),b.readerIndex());
            } finally {a.release();b.release();}
        }
    }

    @Test void concurrentOwnersSurviveGcDuringHeapAndDirectBatches() throws Exception {
        try(var pool=Executors.newFixedThreadPool(5)) {
            var tasks=new ArrayList<Future<?>>();
            for(int thread=0;thread<4;thread++){final int seed=thread;tasks.add(pool.submit(()->{
                for(int round=0;round<64;round++) {
                    var input=LongArrayFixtures.values(8193,seed*1977L+round);String kind=round%2==0?"heap":"pooled_direct";
                    var buffer=LongArrayFixtures.buffer(kind,input.length*8,input.length*8);
                    try{FriendlyByteBuf.writeFixedSizeLongArray(buffer,input);var output=new long[input.length];FriendlyByteBuf.readFixedSizeLongArray(buffer,output);assertArrayEquals(input,output);}finally{buffer.release();}
                }
            }));}
            tasks.add(pool.submit(()->{for(int round=0;round<8;round++)System.gc();}));for(var task:tasks)task.get();
        }
    }

    @Test void resizedParentsCannotBorrowBeyondLiveOwnerRegions() {
        for(String kind:new String[]{"heap","direct"}) {
            var parentA=LongArrayFixtures.buffer(kind,2048,2048);var parentB=LongArrayFixtures.buffer(kind,2048,2048);
            var input=LongArrayFixtures.values(256,1977);JavaLongArray.writeFixedSizeLongArray(parentA,input);JavaLongArray.writeFixedSizeLongArray(parentB,input);
            var a=parentA.retainedSlice(0,2048);var b=parentB.retainedSlice(0,2048);parentA.capacity(1024);parentB.capacity(1024);
            try {
                var outA=new long[128];var outB=new long[128];Arrays.fill(outA,123);Arrays.fill(outB,123);
                assertFalse(NativeLongArray.read(b,new long[256]));
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(a,outA)),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(b,outB)));
                assertArrayEquals(outA,outB);assertEquals(a.readerIndex(),b.readerIndex());
                a.clear();b.clear();assertFalse(NativeLongArray.write(b,input));
                var prefix=Arrays.copyOf(input,128);
                assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(a,prefix)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(b,prefix)));
                assertEquals(a.writerIndex(),b.writerIndex());assertArrayEquals(LongArrayFixtures.bytes(parentA),LongArrayFixtures.bytes(parentB));
            } finally {a.release();b.release();parentA.release();parentB.release();}
        }
    }

    static final class Tracker implements io.netty.util.ResourceLeakTracker<ByteBuf> {
        int records,closes;
        @Override public void record(){records++;}
        @Override public void record(Object hint){records++;}
        @Override public boolean close(ByteBuf buffer){closes++;return true;}
    }
    static ByteBuf tracked(ByteBuf buffer,Tracker tracker,boolean advanced) throws Exception {
        var type=Class.forName("io.netty.buffer."+(advanced?"AdvancedLeakAwareByteBuf":"SimpleLeakAwareByteBuf"));
        var constructor=type.getDeclaredConstructor(ByteBuf.class,io.netty.util.ResourceLeakTracker.class);constructor.setAccessible(true);
        return (ByteBuf)constructor.newInstance(buffer,tracker);
    }
    @Test void simpleTrackingIsPreservedAndAdvancedAccessTrackingKeepsJavaCallbacks() throws Exception {
        var input=LongArrayFixtures.values(128,1977);
        for(boolean advanced:new boolean[]{false,true}) {
            var ta=new Tracker();var tb=new Tracker();var a=tracked(LongArrayFixtures.buffer("safe_heap",1024,1024),ta,advanced);var b=tracked(LongArrayFixtures.buffer("safe_heap",1024,1024),tb,advanced);
            try {
                if(advanced)assertFalse(NativeLongArray.write(b,input));
                JavaLongArray.writeFixedSizeLongArray(a,input);FriendlyByteBuf.writeFixedSizeLongArray(b,input);
                assertEquals(ta.records,tb.records);
                var outA=new long[128];var outB=new long[128];JavaLongArray.readFixedSizeLongArray(a,outA);FriendlyByteBuf.readFixedSizeLongArray(b,outB);
                assertArrayEquals(outA,outB);assertEquals(ta.records,tb.records);
            } finally {a.release();b.release();}
            assertEquals(ta.closes,tb.closes);
        }
    }

    @Test void everyCallUsesCurrentMutableInputAndOutputRegions() {
        var buffer=LongArrayFixtures.buffer("heap",2064,2064);var input=new long[256];var output=new long[256];
        try {
            for(int round=0;round<256;round++) {
                var fresh=LongArrayFixtures.values(256,round);System.arraycopy(fresh,0,input,0,256);buffer.setIndex(7,7);
                FriendlyByteBuf.writeFixedSizeLongArray(buffer,input);buffer.setLong(7+128*8,round);input[128]=round;
                FriendlyByteBuf.readFixedSizeLongArray(buffer,output);assertArrayEquals(input,output);
            }
        } finally {buffer.release();}
    }

    @Test void malformedNullBackingWrappersKeepOriginalFailureMessages() {
        for(int depth=1;depth<=3;depth++)for(int count:new int[]{0,128}) {
            ByteBuf left=null,right=null;
            for(int i=0;i<depth;i++){left=new FriendlyByteBuf(left);right=new FriendlyByteBuf(right);}
            final ByteBuf a=left,b=right;var values=new long[count];
            assertEquals(LongArrayFixtures.error(()->JavaLongArray.writeFixedSizeLongArray(a,values)),LongArrayFixtures.error(()->FriendlyByteBuf.writeFixedSizeLongArray(b,values)));
            assertEquals(LongArrayFixtures.error(()->JavaLongArray.readFixedSizeLongArray(a,values)),LongArrayFixtures.error(()->FriendlyByteBuf.readFixedSizeLongArray(b,values)));
        }
    }
}
