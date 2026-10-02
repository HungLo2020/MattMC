package net.minecraft.world.level.levelgen.carver;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Direct tiny/boundary ABI fixtures, independently packed from production. */
final class CarverAbiBridge {
    private static final MethodHandle CALL=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_canyon_candidates",FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    static long[] evaluate(int[] frame,double[] shape,float[] widths) {
        int[] nativeFrame=java.util.Arrays.copyOf(frame,9);nativeFrame[8]=frame[9];
        double[] nativeShape=java.util.Arrays.copyOf(shape,5);
        try(var arena=Arena.ofConfined()) {
            var f=arena.allocate(36,4);var s=arena.allocate(40,8);var w=arena.allocate(Math.max(4,widths.length*4L),4);
            int count=(frame[3]-frame[2]+1)*(frame[7]-frame[6]+1)*((frame[5]-frame[4]+63)/64);
            var out=arena.allocate(count*8L,8);MemorySegment.copy(MemorySegment.ofArray(nativeFrame),0,f,0,36);MemorySegment.copy(MemorySegment.ofArray(nativeShape),0,s,0,40);
            MemorySegment.copy(MemorySegment.ofArray(widths),0,w,0,widths.length*4L);
            int status=(int)CALL.invokeExact(f,9,s,5,w,widths.length,out,count);if(status!=0)throw new AssertionError(status);return out.toArray(ValueLayout.JAVA_LONG);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
    }
}
