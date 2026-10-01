package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeClimateTreeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static Climate.ParameterPoint box(long x) {
        var zero = new Climate.Parameter(0,0);
        return new Climate.ParameterPoint(new Climate.Parameter(x,x),zero,zero,zero,zero,zero,0);
    }
    private static Climate.TargetPoint point(long x) { return new Climate.TargetPoint(x,0,0,0,0,0); }
    private static Climate.ParameterList<Object> tree(Object a, Object b) {
        return new Climate.ParameterList<>(List.of(Pair.of(box(0),a),Pair.of(box(10),b)));
    }

    @Test void previousLeafWinsTiesAcrossJavaAndNativeCalls() {
        Object a=new Object(), b=new Object();var tree=tree(a,b);
        assertSame(b,tree.findValueIndex(point(10),Climate.RTree.Node::distance));
        assertSame(b,tree.findValue(point(5)));
        Object[] out=new Object[3];
        tree.findValues(new Climate.TargetPoint[]{point(0),point(5),point(10)},out);
        assertArrayEquals(new Object[]{a,a,b},out);
        assertSame(b,tree.findValueIndex(point(5),Climate.RTree.Node::distance));
        assertSame(a,tree.findValueIndex(point(0),Climate.RTree.Node::distance));
        assertSame(a,tree.findValue(point(5)));
    }

    @Test void batchPrefixAndNullFailurePreserveHistoryAndIdentity() {
        Object a=new Object(),b=new Object();var tree=tree(a,b);Object[] out={null,null,a};
        assertThrows(NullPointerException.class,() -> tree.findValues(new Climate.TargetPoint[]{point(10),null,point(0)},out));
        assertSame(b,out[0]);assertNull(out[1]);assertSame(a,out[2]);assertSame(b,tree.findValue(point(5)));
        assertThrows(IllegalArgumentException.class,() -> tree.findValues(new Climate.TargetPoint[]{point(0)},new Object[0]));
        tree.findValues(new Climate.TargetPoint[0],new Object[0]);
        assertSame(b,tree.findValue(point(5)));
        assertThrows(NullPointerException.class,() -> tree.findValue(null));
        assertSame(b,tree.findValue(point(5)));
    }

    @Test void subclassCallbacksRemainOrderedAndVirtual() {
        Object a=new Object(),b=new Object();
        class Custom extends Climate.ParameterList<Object> {
            int calls;
            Custom() {super(List.of(Pair.of(box(0),a),Pair.of(box(10),b)));}
            @Override protected Object findValueIndex(Climate.TargetPoint p, Climate.DistanceMetric<Object> metric) {
                calls++;return super.findValueIndex(p,metric);
            }
        }
        var tree=new Custom();Object[] out=new Object[3];
        tree.findValues(new Climate.TargetPoint[]{point(10),point(5),point(0)},out);
        assertEquals(3,tree.calls);assertArrayEquals(new Object[]{b,b,a},out);
    }

    @Test void threadLocalHistoryAndSharedNativeArena() throws Exception {
        Object a=new Object(),b=new Object();var tree=tree(a,b);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var jobs=new java.util.ArrayList<Future<?>>();
            for(int thread=0;thread<4;thread++) {
                final int id=thread;
                jobs.add(pool.submit(() -> {
                    Object expected=id%2==0?a:b;
                    for(int i=0;i<10000;i++) {
                        Object[] out=new Object[2];
                        tree.findValues(new Climate.TargetPoint[]{point(id%2==0?0:10),point(5)},out);
                        assertSame(expected,out[0]);assertSame(expected,out[1]);assertSame(expected,tree.findValue(point(5)));
                    }
                }));
            }
            for(var job:jobs)job.get();
        }
    }

    @Test void extremeDistanceFailureMatchesRecursiveJava() {
        var zero=new Climate.Parameter(0,0);
        var a=new Climate.RTree.Leaf<>(new Climate.ParameterPoint(new Climate.Parameter(3037000499L,3037000499L),
            new Climate.Parameter(76996,76996),new Climate.Parameter(377,377),new Climate.Parameter(25,25),
            new Climate.Parameter(6,6),zero,0),new Object());
        var b=new Climate.RTree.Leaf<>(new Climate.ParameterPoint(new Climate.Parameter(-3037000499L,-3037000499L),
            new Climate.Parameter(76996,76996),new Climate.Parameter(377,377),new Climate.Parameter(25,25),
            new Climate.Parameter(6,6),zero,0),new Object());
        var close=new Climate.RTree.Leaf<>(box(0),new Object());
        var root=new Climate.RTree.SubTree<>(List.of(new Climate.RTree.SubTree<>(List.of(a,b)),close));
        var nativeTree=new NativeClimateTree<>(root);
        assertEquals(Long.MAX_VALUE,a.distance(point(0).toParameterArray()));
        assertThrows(NullPointerException.class,() -> root.search(point(0).toParameterArray(),null,Climate.RTree.Node::distance));
        assertNull(nativeTree.search(point(0),null));
        assertSame(close,root.search(point(0).toParameterArray(),a,Climate.RTree.Node::distance));
        assertSame(close,nativeTree.search(point(0),a));
    }
}
