package net.vulkanic.bridge;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntityShadowCandidatesTest {
    static MemorySegment slots(EntityShadowCandidates owner) {
        var header = owner.owner();
        return MemorySegment.ofAddress(header.get(ValueLayout.JAVA_LONG, 0))
            .reinterpret(header.get(ValueLayout.JAVA_INT, 16) * 104L);
    }
    @Test void growthAndClearPreserveCaptureAndRemovePriorHolder() {
        try (var owner = new EntityShadowCandidates()) {
            var box = new AABB(30_000_000.125,-2,-3,30_000_001.25,5,6);
            var holder = new AABB(-10,-11,-12,10,11,12);
            for (int i=0;i<257;i++) owner.add(17,box,holder);
            assertEquals(257,owner.size());
            var values = slots(owner);
            for (int i=0;i<257;i++) {
                assertEquals(17,values.get(ValueLayout.JAVA_INT,i*104L));
                assertEquals(box.minX,values.get(ValueLayout.JAVA_DOUBLE,i*104L+8));
                assertEquals(holder.maxZ,values.get(ValueLayout.JAVA_DOUBLE,i*104L+96));
            }
            owner.clear(); owner.add(1,box,null);
            assertEquals(1,owner.size());
            for(int i=0;i<6;i++) assertEquals(0.,slots(owner).get(ValueLayout.JAVA_DOUBLE,56+i*8L));
        }
    }
    @Test void invalidCaptureDoesNotPublishAPartialSlot() {
        try (var owner = new EntityShadowCandidates()) {
            var box = new AABB(0,0,0,1,1,1);
            owner.add(1,box,null);
            assertThrows(IllegalArgumentException.class,()->owner.add(17,box,null));
            assertThrows(IllegalArgumentException.class,()->owner.add(32,box,null));
            assertThrows(IllegalArgumentException.class,()->owner.add(1,new AABB(Double.NaN,0,0,1,1,1),null));
            assertEquals(1,owner.size());
            assertThrows(IndexOutOfBoundsException.class,()->owner.admitted(1));
            assertThrows(IndexOutOfBoundsException.class,()->owner.admitted(-1));
        }
    }
    @Test void scopeAndCapacityStayBounded() {
        var owner = new EntityShadowCandidates();
        try {
            var box = new AABB(0,0,0,1,1,1);
            for (int i=0;i<65536;i++) owner.add(1,box,null);
            assertThrows(IllegalStateException.class,()->owner.add(1,box,null));
            assertEquals(65536,owner.size());
            owner.clear(); owner.add(1,box,null); assertEquals(1,owner.size());
        } finally { owner.close(); }
        assertThrows(IllegalStateException.class,owner::clear);
        assertThrows(IllegalStateException.class,owner::size);
        owner.close();
    }
}
