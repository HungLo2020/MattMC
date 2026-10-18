package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import net.minecraft.core.*;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.NativeLightBlocks;

/** Original VM leaves for the native block and sky queue policies. */
final class NativeBlockPropagation {
    private static final MethodHandle RUN=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_block_run_effects",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CONNECTED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_block_run_connected_effects",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle SKY_RUN=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_sky_run_effects",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle SKY_CONNECTED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_sky_run_connected_effects",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final ThreadLocal<Invocation> CURRENT=new ThreadLocal<>();
    private static final MethodHandle CAPTURE=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_block_capture_state",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MemorySegment LEAF;
    static{
        try{
            var method=MethodHandles.lookup().findStatic(NativeBlockPropagation.class,"leaf",MethodType.methodType(long.class,int.class,long.class,long.class,long.class));
            LEAF=Linker.nativeLinker().upcallStub(method,FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),Arena.global());
        }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static final class Invocation {
        final LightEngine<?,?> engine;final BlockState[] states=new BlockState[2];Throwable failure;
        final NativeLightBlocks.NodeLease input=new NativeLightBlocks.NodeLease();
        Invocation(LightEngine<?,?> engine){this.engine=engine;}
    }
    @SuppressWarnings("unchecked")private static <T extends Throwable> RuntimeException exact(Throwable failure)throws T{throw(T)failure;}
    static int run(LightEngine<?,?> engine,NativeLightQueues queues){
        var call=new Invocation(engine);var previous=CURRENT.get();CURRENT.set(call);
        try(Arena arena=Arena.ofConfined()){
            var output=arena.allocate(ValueLayout.JAVA_LONG);int status;
            boolean sky=engine instanceof SkyLightEngine;
            MethodHandle run=sky?SKY_RUN:RUN,connected=sky?SKY_CONNECTED:CONNECTED;
            try{status=engine.lightCoordinator==null?(int)run.invokeExact(queues.ownerForNativeCall(),LEAF,output):
                (int)connected.invokeExact(queues.ownerForNativeCall(),engine.lightCoordinator.bindingForPropagation(),LEAF,output);}catch(Throwable failure){throw exact(failure);}
            if(call.failure!=null)throw exact(call.failure);
            if(status!=0)throw new IllegalStateException("Native light propagation effects failed: "+status);
            return(int)output.get(ValueLayout.JAVA_LONG,0);
        }finally{call.states[0]=null;call.states[1]=null;if(previous==null)CURRENT.remove();else CURRENT.set(previous);Reference.reachabilityFence(queues);Reference.reachabilityFence(engine);}
    }
    private static long leaf(int action,long a,long b,long c){
        var call=CURRENT.get();if(call==null)return Long.MIN_VALUE;
        try{
            var engine=call.engine;
            return switch(action){
                case 0->engine.storage.getStoredLevel(a);
                case 1->{engine.storage.setStoredLevel(a,(int)b);yield 0;}
                case 2->engine.storage.storingLightForSection(a)?1:0;
                case 3->{
                    BlockState state;
                    if(c!=0){
                        try{
                            state=engine instanceof BlockLightEngine block?block.stateForNativeNode(a,call.input):((SkyLightEngine)engine).stateForNativeNode(a,call.input);
                            if(call.input.hasOwner()){
                                int captured=(int)CAPTURE.invokeExact(MemorySegment.ofAddress(c),call.input.pointer(),call.input.index());
                                if(captured==1){call.states[(int)b]=null;yield -1;}
                                if(captured!=0)throw new IllegalStateException("Native propagation state capture failed: "+captured);
                                state=call.input.compatibility();
                            }
                        }finally{call.input.clear();}
                    }else state=engine instanceof BlockLightEngine block?block.stateForNativePropagation(a):((SkyLightEngine)engine).stateForNativePropagation(a);
                    call.states[(int)b]=state;
                    yield state!=null&&state.getClass()==BlockState.class?Block.BLOCK_STATE_REGISTRY.getId(state):-1;
                }
                case 4->engine.getOpacity(call.states[(int)a]);
                case 5->{int emission=call.states[(int)b].getLightEmission();yield emission>0&&engine.storage.lightOnInSection(SectionPos.blockToSection(a))?emission:0;}
                case 6->LightEngine.isEmptyShape(call.states[(int)a])?1:0;
                case 7->{
                    // Canonical states belong to the permanent registry. Restore
                    // their VM facades only for a mixed/custom shape effect.
                    if(b>0)call.states[0]=Block.BLOCK_STATE_REGISTRY.byId((int)b-1);
                    if(c>0)call.states[1]=Block.BLOCK_STATE_REGISTRY.byId((int)c-1);
                    yield engine.shapeOccludes(call.states[0],call.states[1],LightEngine.PROPAGATION_DIRECTIONS[(int)a])?1:0;
                }
                case 8->{call.states[(int)a]=null;yield 0;}
                case 9->{call.states[0]=Blocks.AIR.defaultBlockState();yield 0;}
                case 10->throw new java.util.NoSuchElementException();
                case 11->{engine.lightCoordinator.refreshPropagationEntries(a,b);yield 0;}
                case 12->{NativeLightCoordinator.checkEffectStatus((int)a,MemorySegment.ofArray(new long[]{b,c}));throw new AssertionError("Missing native storage error");}
                case 13->{
                    BlockState state=engine instanceof BlockLightEngine block?block.stateAfterNativeInvalidation(a):((SkyLightEngine)engine).stateAfterNativeInvalidation(a);
                    call.states[(int)b]=state;
                    yield state.getClass()==BlockState.class?Block.BLOCK_STATE_REGISTRY.getId(state):-1;
                }
                case 14->((SkyLightEngine)engine).storage.hasLightDataAtOrBelow((int)a)?1:0;
                default->throw new IllegalStateException("Unknown native block propagation leaf: "+action);
            };
        }catch(Throwable failure){call.failure=failure;return Long.MIN_VALUE;}
    }
}
