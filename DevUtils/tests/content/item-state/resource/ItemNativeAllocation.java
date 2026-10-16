import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
/** Native diagnostic ABI, excluded from all acceptance latency runs. */
final class ItemNativeAllocation {
    static final Linker LINKER=Linker.nativeLinker();
    static final SymbolLookup SYMBOLS=SymbolLookup.libraryLookup(System.getProperty("fallen.alloc.library"),Arena.global());
    static final MethodHandle BEGIN=handle("fallen_alloc_begin",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    static final MethodHandle END=handle("fallen_alloc_end",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    static final MethodHandle SNAPSHOT=handle("fallen_alloc_snapshot",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    static final MethodHandle CONTROL=handle("fallen_alloc_control",FunctionDescriptor.ofVoid());
    static final MethodHandle HOLD=handle("fallen_alloc_hold",FunctionDescriptor.ofVoid());
    static final MethodHandle RELEASE=handle("fallen_alloc_release",FunctionDescriptor.ofVoid());
    static final MemorySegment BUFFER=Arena.global().allocate(22*8,8);
    static int next=10;
    static MethodHandle handle(String name,FunctionDescriptor desc){return LINKER.downcallHandle(SYMBOLS.find(name).orElseThrow(),desc);}
    static int begin()throws Throwable{int tag=next++;if((int)BEGIN.invokeExact(tag)!=0)throw new AssertionError("Invalid allocation gate");return tag;}
    static long[] end(int tag)throws Throwable{if((int)END.invokeExact(tag,BUFFER)!=0)throw new AssertionError("Invalid gate end");return BUFFER.toArray(ValueLayout.JAVA_LONG);}
    static long[] snapshot(int tag)throws Throwable{if((int)SNAPSHOT.invokeExact(tag,BUFFER)!=0)throw new AssertionError("Invalid snapshot");return BUFFER.toArray(ValueLayout.JAVA_LONG);}
    static Map<String,Object> sample(int tag,long[] values){
        if(values[21]!=0)throw new AssertionError("Native allocation observer overflow");
        return Map.of("tag",tag,"all",Arrays.copyOfRange(values,0,7),"rust",Arrays.copyOfRange(values,7,14),"otherNative",Arrays.copyOfRange(values,14,21));
    }
    static Map<String,Object> control()throws Throwable{
        for(int i=0;i<8;i++){CONTROL.invokeExact();HOLD.invokeExact();RELEASE.invokeExact();int tag=begin();end(tag);}
        int empty=begin();var emptyValues=end(empty);if(emptyValues[1]!=0)throw new AssertionError("Gate itself allocates native memory");
        int known=begin();CONTROL.invokeExact();var measured=end(known);
        if(measured[0]!=3||measured[1]!=58||measured[2]!=3||measured[3]!=58||measured[4]!=0||measured[5]!=45)throw new AssertionError("Known malloc/calloc/realloc control: "+Arrays.toString(measured));
        int delayed=begin();HOLD.invokeExact();var held=end(delayed);if(held[4]!=97)throw new AssertionError("Missing retained allocation");
        RELEASE.invokeExact();var released=snapshot(delayed);if(released[4]!=0||released[3]!=97)throw new AssertionError("Cross-thread free lost");
        return Map.of("empty",sample(empty,emptyValues),"known",sample(known,measured),"held",sample(delayed,held),"released",sample(delayed,released));
    }
}
