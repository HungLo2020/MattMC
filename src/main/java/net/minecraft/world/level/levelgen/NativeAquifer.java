package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.NativeNoiseState;

/** Chunk-owned native aquifer geometry and resumable material decisions.
 * Foreign calls borrow bounded heap buffers and never retain their addresses. */
final class NativeAquifer {
    private static final MethodHandle NEAREST = bind("nearest", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CELL = bind("cell", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle STEP = bind("step", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle FLUID = bind("fluid", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle FLUID_PURE = bind("fluid_pure", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_DOUBLE,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle MATERIALS = bind("materials",FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,
        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE));
    private static MethodHandle bind(String suffix, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_aquifer_"+suffix,descriptor,Linker.Option.critical(true));
    }
    private final Aquifer.NoiseBasedAquifer owner;
    private final NoiseChunk chunk;
    private final long[] locations;
    private final int[] shape, cache, frame = new int[20], centers = new int[48];
    private final double[] values = new double[2];
    private final MemorySegment frameMemory=MemorySegment.ofArray(frame), valuesMemory=MemorySegment.ofArray(values),
        centersMemory=MemorySegment.ofArray(centers), cacheMemory, locationsMemory, shapeMemory;
    private final boolean batch;
    private int[] cell;
    private MemorySegment cellMemory;
    private int cellX,cellY,cellZ;
    private boolean cellReady;
    private final AquiferFluidPicker global;
    private final DensityFunctions.Noise barrier;
    private final int skipY;
    private int[] materials;
    private MemorySegment materialMemory;
    private int materialX,materialY,materialZ;
    private long materialEpoch;
    private boolean materialsReady;
    private boolean lastSchedule;
    private int activeScalar;
    private final int[] batchFrame=new int[32];
    private final MemorySegment batchFrameMemory=MemorySegment.ofArray(batchFrame);
    private final boolean pureSources;
    private final int[] fluidPolicy,surfaceCache;
    private final MemorySegment fluidPolicyMemory,surfaceMemory;
    private final int surfaceMinX,surfaceMinZ,surfaceWidth,surfaceHeight;

    NativeAquifer(Aquifer.NoiseBasedAquifer owner, NoiseChunk chunk, long[] locations,int[] shape,
                  Aquifer.FluidPicker picker,DensityFunction barrier,int skipY,boolean pureRandom) {
        this.owner=owner;this.chunk=chunk;this.locations=locations;this.shape=shape;
        cache=new int[locations.length*3];
        for(int i=1;i<cache.length;i+=3)cache[i]=-1;
        cacheMemory=MemorySegment.ofArray(cache);locationsMemory=MemorySegment.ofArray(locations);shapeMemory=MemorySegment.ofArray(shape);
        batch=pureRandom && chunk.getClass()==NoiseChunk.class && chunk.cellCountXZ>1 && chunk.cellWidth>0 && chunk.cellWidth<=16
            && chunk.cellHeight>0 && chunk.cellHeight<=32 && chunk.cellWidth*chunk.cellWidth*chunk.cellHeight<=2048;
        this.global=picker instanceof AquiferFluidPicker known?known:null;
        this.barrier=barrier instanceof DensityFunctions.Noise n?n:null;this.skipY=skipY;
        // Outside BlockPos's representable X/Z range, its packed centers wrap;
        // retain ordered scalar requests instead of using a rectangular surface cache.
        pureSources=batch && global!=null && shape[0]>=-2097152 && (long)shape[0]+shape[3]<=2097152
            && shape[2]>=-2097152 && (long)shape[2]+shape[4]<=2097152
            && chunk.getBlender()==net.minecraft.world.level.levelgen.blending.Blender.empty() && chunk.aquiferBatchSafe();
        surfaceMinX=shape[0]*4-12;surfaceMinZ=shape[2]*4-4;
        surfaceWidth=shape[3]*4+19;surfaceHeight=shape[4]*4+11;
        surfaceCache=pureSources?new int[surfaceWidth*surfaceHeight*2]:new int[0];surfaceMemory=MemorySegment.ofArray(surfaceCache);
        fluidPolicy=pureSources?new int[]{global.lava.fluidLevel(),encode(global.lava.fluidType()),global.fluid.fluidLevel(),
            encode(global.fluid.fluidType()),global.fluid.fluidType().isAir()?1:0,
            net.minecraft.SharedConstants.DEBUG_DISABLE_FLUID_GENERATION?1:0,global.disabled.fluidLevel(),encode(Blocks.AIR.defaultBlockState())}:new int[0];
        fluidPolicyMemory=MemorySegment.ofArray(fluidPolicy);
    }
    private java.util.IdentityHashMap<BlockState,Integer> extraIds;
    private java.util.ArrayList<BlockState> extraStates;
    private int encode(BlockState state) {
        int id=Block.BLOCK_STATE_REGISTRY.getId(state);
        if(id>=0)return id;
        if(extraIds==null) {extraIds=new java.util.IdentityHashMap<>();extraStates=new java.util.ArrayList<>();}
        Integer previous=extraIds.get(state);if(previous!=null)return previous;
        int token=-2-extraStates.size();extraIds.put(state,token);extraStates.add(state);return token;
    }
    private BlockState decode(int id) {return id<=-2?extraStates.get(-2-id):Block.stateById(id);}

    private static void check(int status) { if(status<0)throw new IllegalStateException("Native aquifer: "+status); }
    private void nearest(DensityFunction.FunctionContext context,int x,int y,int z) throws Throwable {
        if(batch && context==chunk && chunk.interpolating && !chunk.fillingCell
                && chunk.inCellX>=0 && chunk.inCellX<chunk.cellWidth && chunk.inCellY>=0 && chunk.inCellY<chunk.cellHeight
                && chunk.inCellZ>=0 && chunk.inCellZ<chunk.cellWidth) {
            int sx=x-chunk.inCellX, sy=y-chunk.inCellY, sz=z-chunk.inCellZ;
            if(!cellReady || cellX!=sx || cellY!=sy || cellZ!=sz) {
                if(cell==null) { cell=new int[chunk.cellWidth*chunk.cellWidth*chunk.cellHeight*8];cellMemory=MemorySegment.ofArray(cell); }
                owner.cellLocations(sx,sy,sz,chunk.cellWidth,chunk.cellHeight);
                check((int)CELL.invokeExact(sx,sy,sz,chunk.cellWidth,chunk.cellHeight,locationsMemory,locations.length,shapeMemory,cellMemory));
                cellX=sx;cellY=sy;cellZ=sz;cellReady=true;
            }
            System.arraycopy(cell,((chunk.inCellY*chunk.cellWidth+chunk.inCellX)*chunk.cellWidth+chunk.inCellZ)*8,frame,0,8);
        } else {
            owner.centers(x,y,z,centers);
            check((int)NEAREST.invokeExact(x,y,z,centersMemory,frameMemory));
        }
    }
    BlockState compute(DensityFunction.FunctionContext context,double density,int x,int y,int z,boolean cachedMaterial) {
        try {
            if(cachedMaterial && pureSources && barrier!=null && context==chunk && chunk.interpolating && !chunk.fillingCell
                && chunk.aquiferDensity!=null
                && chunk.inCellX>=0 && chunk.inCellX<chunk.cellWidth && chunk.inCellY>=0 && chunk.inCellY<chunk.cellHeight
                && chunk.inCellZ>=0 && chunk.inCellZ<chunk.cellWidth) {
                if(prepareMaterialsIfNeeded(x,y,z))return material();
            }
            return scalar(context,density,x,y,z);
        } catch(RuntimeException | Error e) { throw e; }
        catch(Throwable t) { throw new IllegalStateException("Native aquifer call failed",t); }
    }
    private BlockState scalar(DensityFunction.FunctionContext context,double density,int x,int y,int z) throws Throwable {
        int[] savedFrame=null,savedCenters=null;double[] savedValues=null;
        if(activeScalar++!=0) {savedFrame=frame.clone();savedCenters=centers.clone();savedValues=values.clone();}
        try {
            nearest(context,x,y,z);
            frame[8]=0;frame[10]=y;frame[11]=-1;frame[14]=encode(Blocks.AIR.defaultBlockState());frame[15]=0;
            frame[16]=context.getClass()==NoiseChunk.class || context.getClass()==DensityFunction.SinglePointContext.class?0:1;frame[18]=0;
            values[0]=density;values[1]=Double.NaN;
            for(;;) {
                int status=(int)STEP.invokeExact(frameMemory,valuesMemory,cacheMemory,locations.length);
                switch(status) {
                    case 0: lastSchedule=frame[13]!=0;return frame[12]==-1 ? null : decode(frame[12]);
                    case 1: {
                        int index=frame[9];var fluid=owner.getAquiferStatus(index);int offset=index*3;
                        cache[offset]=fluid.fluidLevel();cache[offset+1]=encode(fluid.fluidType());
                        cache[offset+2]=fluid.fluidType().is(Blocks.WATER)?1:fluid.fluidType().is(Blocks.LAVA)?2:0;
                        break;
                    }
                    case 2: frame[11]=owner.lavaBelow(x,y,z)?1:0;break;
                    case 3: values[1]=owner.barrier(context);frame[15]=1;break;
                    case 4: frame[17]=context.blockY();frame[18]=1;break;
                    default: throw new IllegalStateException("Native aquifer decision: "+status);
                }
            }
        } finally {
            activeScalar--;
            if(savedFrame!=null) {System.arraycopy(savedFrame,0,frame,0,frame.length);System.arraycopy(savedCenters,0,centers,0,centers.length);System.arraycopy(savedValues,0,values,0,values.length);}
        }
    }
    boolean shouldScheduleFluidUpdate() { return lastSchedule; }

    private void status(int index) {
        var fluid=owner.getAquiferStatus(index);int offset=index*3;
        cache[offset]=fluid.fluidLevel();cache[offset+1]=encode(fluid.fluidType());
        cache[offset+2]=fluid.fluidType().is(Blocks.WATER)?1:fluid.fluidType().is(Blocks.LAVA)?2:0;
    }

    private boolean prepareMaterialsIfNeeded(int x,int y,int z) throws Throwable {
        int sx=x-chunk.inCellX,sy=y-chunk.inCellY,sz=z-chunk.inCellZ;
        if(!materialsReady || materialX!=sx || materialY!=sy || materialZ!=sz || materialEpoch!=chunk.arrayInterpolationCounter) {
            NativeNoiseState noise=barrier.noise().noise()==null?null:barrier.noise().noise().nativeState();
            if(noise!=null && !noise.critical())return false;
            prepareMaterials(sx,sy,sz,noise);
        }
        return true;
    }
    private BlockState material() {
        int index=(((chunk.cellHeight-1-chunk.inCellY)*chunk.cellWidth+chunk.inCellX)*chunk.cellWidth+chunk.inCellZ)*2;
        lastSchedule=materials[index+1]!=0;int state=materials[index];return state==-1?null:decode(state);
    }

    private void prepareMaterials(int sx,int sy,int sz,NativeNoiseState noise) throws Throwable {
            if(materials==null) {materials=new int[chunk.cellWidth*chunk.cellWidth*chunk.cellHeight*2];materialMemory=MemorySegment.ofArray(materials);}
            owner.cellLocations(sx,sy,sz,chunk.cellWidth,chunk.cellHeight);
            batchFrame[14]=encode(Blocks.AIR.defaultBlockState());batchFrame[16]=0;batchFrame[17]=0;
            batchFrame[18]=sx;batchFrame[19]=sy;batchFrame[20]=sz;batchFrame[21]=chunk.cellWidth;batchFrame[22]=chunk.cellHeight;
            batchFrame[23]=skipY;batchFrame[24]=Math.min(-54,global.fluid.fluidLevel());
            batchFrame[25]=global.lava.fluidLevel();batchFrame[26]=encode(global.lava.fluidType());
            batchFrame[27]=global.fluid.fluidLevel();batchFrame[28]=encode(global.fluid.fluidType());
            batchFrame[29]=global.fluid.fluidType().is(Blocks.LAVA)?2:global.fluid.fluidType().is(Blocks.WATER)?1:0;
            batchFrame[30]=net.minecraft.SharedConstants.DEBUG_DISABLE_FLUID_GENERATION?1:0;batchFrame[31]=global.disabled.fluidLevel();
            var densities=MemorySegment.ofArray(chunk.aquiferDensity.values);
            var noiseMemory=noise==null?MemorySegment.NULL:noise.state();
            for(;;) {
                int result=(int)MATERIALS.invokeExact(batchFrameMemory,valuesMemory,densities,materialMemory,locationsMemory,
                    shapeMemory,locations.length,cacheMemory,noiseMemory,barrier.xzScale(),barrier.yScale());
                if(result==0)break;
                if(result==1)status(batchFrame[9]);
                else if(result!=4)throw new IllegalStateException("Native aquifer cell: "+result);
            }
            materialX=sx;materialY=sy;materialZ=sz;materialEpoch=chunk.arrayInterpolationCounter;materialsReady=true;
    }

    Aquifer.FluidStatus fluid(int x,int y,int z) {
        // Only a cache miss enters here. Separate frames preserve the suspended
        // outer material decision and allow custom sources to reenter this lookup.
        int[] state=new int[26];var memory=MemorySegment.ofArray(state);
        state[1]=x;state[2]=y;state[3]=z;
        state[20]=encode(Blocks.LAVA.defaultBlockState());state[21]=DimensionType.WAY_BELOW_MIN_Y;
        state[22]=surfaceMinX;state[23]=surfaceMinZ;state[24]=surfaceWidth;state[25]=surfaceHeight;
        double answer=0.0;
        DensityFunction.SinglePointContext context=null;
        try {
            for(;;) {
                int request=pureSources?(int)FLUID_PURE.invokeExact(memory,answer,fluidPolicyMemory,surfaceMemory):(int)FLUID.invokeExact(memory,answer);
                if(request==0)return new Aquifer.FluidStatus(state[12],decode(state[13]));
                check(request);
                int rx=state[14],ry=state[15],rz=state[16];
                if(request==1) {
                    var status=owner.fluidSource(rx,ry,rz);
                    state[17]=status.fluidLevel();state[18]=encode(status.fluidType());state[19]=status.at(ry).isAir()?1:0;
                } else if(request==2) {
                    state[17]=owner.surfaceSource(rx,rz);
                    if(pureSources) {int offset=(((rz>>2)-surfaceMinZ)*surfaceWidth+(rx>>2)-surfaceMinX)*2;surfaceCache[offset]=state[17];surfaceCache[offset+1]=1;}
                } else {
                    if(request==3)context=new DensityFunction.SinglePointContext(x,y,z);
                    answer=owner.noiseSource(request,request<=5?context:new DensityFunction.SinglePointContext(rx,ry,rz));
                }
            }
        } catch(RuntimeException | Error e) { throw e; }
        catch(Throwable t) { throw new IllegalStateException("Native aquifer fluid source failed",t); }
    }
}
