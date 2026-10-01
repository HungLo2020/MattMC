package net.minecraft.world.level.levelgen.feature;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.NativeLibraryLoader;

/** Component-only bridge. Production calls the fused vein entry. */
final class NativeOreGeometryBridge {
    private static final MethodHandle GEOMETRY=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_ore_geometry",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    static int[] expand(int[] spans){
        var result=new IntArrayList();
        for(int i=0;i<spans.length;i+=4)for(int z=spans[i+2];z<=spans[i+3];z++){
            result.add(spans[i]);result.add(spans[i+1]);result.add(z);
        }
        return result.toIntArray();
    }
    static int[] candidates(double[] input,int x,int y,int z){
        if(input.length%4!=0)throw new IllegalArgumentException("Incomplete sphere");
        if(input.length==0)return new int[0];
        try(var arena=Arena.ofConfined()){
            var spheres=arena.allocate(input.length*8L,8);
            int capacity=4096;
            while(true){
                MemorySegment.copy(MemorySegment.ofArray(input),0,spheres,0,input.length*8L);
                var output=arena.allocate(capacity*4L,4);
                long count;
                try{count=(long)GEOMETRY.invokeExact(spheres,input.length/4,output,capacity,x,y,z);}
                catch(Throwable e){throw new AssertionError(e);}
                if(count<0 || count>Integer.MAX_VALUE)throw new AssertionError("Invalid span count");
                if(count>capacity){capacity=(int)count;continue;}
                return expand(output.asSlice(0,count*4).toArray(ValueLayout.JAVA_INT));
            }
        }
    }
}
