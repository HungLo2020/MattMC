package net.minecraft.world.level.levelgen.feature;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Direct ABI checks for tiny numeric fixtures that production leaves in Java. */
final class NativeGeodeGeometryBridge {
    private static final MethodHandle FIELDS=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_geode_fields",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,ValueLayout.JAVA_DOUBLE,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    static double[] fields(GeodeFixtures.Grid grid) {
        try(var arena=Arena.ofConfined()) {
            int[] frame=new int[6];int cells=1;
            int[] origin={grid.origin().getX(),grid.origin().getY(),grid.origin().getZ()};
            for(int i=0;i<3;i++){int a=origin[i]+grid.min(),b=origin[i]+grid.max();frame[i]=Math.min(a,b);frame[i+3]=(int)(Math.abs((long)a-b)+1);cells*=frame[i+3];}
            int[] points=new int[(grid.points().size()+grid.cracks().size())*4];int at=0;
            for(var p:grid.points()){points[at++]=p.getFirst().getX();points[at++]=p.getFirst().getY();points[at++]=p.getFirst().getZ();points[at++]=p.getSecond();}
            for(var p:grid.cracks()){points[at++]=p.getX();points[at++]=p.getY();points[at++]=p.getZ();points[at++]=grid.crackOffset();}
            var nativeFrame=arena.allocate(24,4);var nativePoints=arena.allocate(Math.max(4,points.length*4L),4);var output=arena.allocate(cells*16L,8);
            MemorySegment.copy(MemorySegment.ofArray(frame),0,nativeFrame,0,24);
            MemorySegment.copy(MemorySegment.ofArray(points),0,nativePoints,0,points.length*4L);
            var noise=grid.noise().nativeState().state();
            int status=(int)FIELDS.invokeExact(noise,noise.byteSize(),nativeFrame,6,nativePoints,
                grid.points().size(),grid.cracks().size(),grid.multiplier(),output,cells*2);
            if(status!=0)throw new AssertionError("Native geode status="+status);
            return output.toArray(ValueLayout.JAVA_DOUBLE);
        } catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
    }
}
