package net.minecraft.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClassInstanceMultiMapNativeTest {
    @Test void typedArrayProjectionPreservesVmFailureAndDestinationPrefix() {
        ClassInstanceMultiMap<Object> members=new ClassInstanceMultiMap<>(Object.class);
        members.add("ok");members.add(7);
        var view=members.find(Object.class);
        var destination=new String[]{"sentinel","sentinel","sentinel"};
        var error=assertThrows(ArrayStoreException.class,()->view.toArray(destination));
        assertEquals("arraycopy: element type mismatch: can not cast one of the elements of java.lang.Object[] to the type of the destination array, java.lang.String",error.getMessage());
        assertArrayEquals(new String[]{"ok","sentinel","sentinel"},destination);
        assertArrayEquals(new Object[]{"ok",7},view.toArray());
        var oversized=new Object[]{1,2,3,4};
        assertSame(oversized,view.toArray(oversized));
        assertArrayEquals(new Object[]{"ok",7,null,4},oversized);
    }
    @Test void typedViewsRemainLiveOrderedAndUnmodifiableWithDuplicateMembers() {
        ClassInstanceMultiMap<Number> members=new ClassInstanceMultiMap<>(Number.class);
        Collection<Integer> ints=members.find(Integer.class);
        assertTrue(members.add(1));assertTrue(members.add(2.5));assertTrue(members.add(1));
        assertEquals(List.of(1,1),new ArrayList<>(ints));
        var retained=members.getAllInstances();
        assertTrue(members.remove(1));
        assertEquals(List.of(1),new ArrayList<>(ints));
        assertEquals(List.of(1,2.5,1),retained);
        assertEquals(List.of(2.5,1),members.getAllInstances());
        assertThrows(UnsupportedOperationException.class,()->ints.clear());
        assertThrows(IllegalArgumentException.class,()->members.find(String.class));
        assertFalse(members.add(null));
    }
    @Test void liveIteratorsCheckMutationOnNextAndRetainEmptyIteratorBehavior() {
        ClassInstanceMultiMap<Object> members=new ClassInstanceMultiMap<>(Object.class);
        var empty=members.iterator();members.add("a");
        assertFalse(empty.hasNext());assertThrows(NoSuchElementException.class,empty::next);
        var iterator=members.iterator();assertEquals("a",iterator.next());members.add("b");
        assertTrue(iterator.hasNext());assertThrows(ConcurrentModificationException.class,iterator::next);
        var typed=members.find(String.class).iterator();
        assertEquals("a",typed.next());assertThrows(UnsupportedOperationException.class,typed::remove);
    }
    @Test void equalityMutationPreservesCapturedArrayAndFailurePrefix() {
        ClassInstanceMultiMap<Object> members=new ClassInstanceMultiMap<>(Object.class);
        for(int i=1;i<=10;i++)members.add(i);
        Object sought=new Object(){
            @Override public boolean equals(Object value){members.add(11);return true;}
        };
        var error=assertThrows(ArrayIndexOutOfBoundsException.class,()->members.remove(sought));
        assertEquals("arraycopy: last source index 11 out of bounds for object array[10]",error.getMessage());
        assertEquals(11,members.size());assertEquals(1,members.iterator().next());
    }
    @Test void containsUsesCapturedArrayWithoutFailFastOrRemoval() {
        ClassInstanceMultiMap<Object> members=new ClassInstanceMultiMap<>(Object.class);
        for(int i=1;i<=10;i++)members.add(i);
        var view=members.find(Object.class);
        Object sought=new Object(){
            boolean first=true;
            @Override public boolean equals(Object value){if(first){first=false;members.add(11);}return Objects.equals(value,10);}
        };
        assertTrue(view.contains(sought));assertEquals(11,members.size());
    }
    @Test void typedSpliteratorKeepsOldArrayForRemainingCallbacksAndChecksAtEnd() {
        ClassInstanceMultiMap<Object> members=new ClassInstanceMultiMap<>(Object.class);
        members.add("a");members.add("b");members.add("c");
        var cursor=members.find(String.class).spliterator();
        List<String> visited=new ArrayList<>();
        assertThrows(ConcurrentModificationException.class,()->cursor.forEachRemaining(value->{
            visited.add(value);if(value.equals("a"))members.add("d");
        }));
        assertEquals(List.of("a","b","c"),visited);
        assertEquals(List.of("a","b","c","d"),members.getAllInstances());
    }
}
