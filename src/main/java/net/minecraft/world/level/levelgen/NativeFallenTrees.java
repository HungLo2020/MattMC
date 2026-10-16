package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.NativeDecorationRegion;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FallenTreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;

/** Whole Rust invocation control. Fields here are the actual VM effect resources,
 * including publicly observable mutable positions, lists and context objects. */
public final class NativeFallenTrees {
    private static final ThreadLocal<Call> CURRENT = new ThreadLocal<>();
    private static final boolean ENABLED = !Boolean.getBoolean("mattmc.worldgen.javaDecoration");
    private static final int RNG_CALLBACK = 1 << 29;
    private NativeFallenTrees() {}

    private static final class Fields {
        static final VarHandle PROBABILITY, PROVIDER, DIRECTIONS;
        static {
            try {
                var lookup=MethodHandles.privateLookupIn(AttachedToLogsDecorator.class,MethodHandles.lookup());
                PROBABILITY=lookup.findVarHandle(AttachedToLogsDecorator.class,"probability",float.class);
                PROVIDER=lookup.findVarHandle(AttachedToLogsDecorator.class,"blockProvider",BlockStateProvider.class);
                DIRECTIONS=lookup.findVarHandle(AttachedToLogsDecorator.class,"directions",List.class);
            } catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    private static final class Bindings {
        static final MethodHandle FALLEN=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_fallen_tree_place",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
        static final MethodHandle ATTACHED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_attached_logs_place",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_FLOAT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
        static final MethodHandle VINES=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_fallen_vines_place",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
        static final MethodHandle GENERATION=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_fallen_decorator_generation",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
        static final Arena ARENA=Arena.ofAuto();
        static final MemorySegment EFFECT;
        static {
            try {
                EFFECT=Linker.nativeLinker().upcallStub(MethodHandles.lookup().findStatic(NativeFallenTrees.class,"effect",
                    MethodType.methodType(int.class,int.class,MemorySegment.class,int.class)),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT),ARENA);
            }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    private static final class Call {
        WorldGenLevel world;
        NativeDecorationRegion region;
        BlockPos origin, target, vineLog;
        final BlockPos.MutableBlockPos[] cursors=new BlockPos.MutableBlockPos[3];
        WorldgenRandom random;
        FallenTreeConfiguration config;
        TreeDecorator.Context context;
        AttachedToLogsDecorator decorator;
        BlockState state;
        Object[] snapshot;
        List<Direction> directions;
        Direction direction;
        Throwable failure;
    }

    public static boolean place(FallenTreeFeature owner,FeaturePlaceContext<FallenTreeConfiguration> context) {
        if(!ENABLED||owner.getClass()!=FallenTreeFeature.class||context==null||context.getClass()!=FeaturePlaceContext.class)return false;
        var config=context.config();
        if(config==null||config.getClass()!=FallenTreeConfiguration.class||config.trunkProvider==null
            ||config.logLength==null||config.stumpDecorators==null||config.logDecorators==null)return false;
        var world=context.level();var origin=context.origin();
        if(world==null||(world.getClass()!=WorldGenRegion.class&&world.getClass()!=ServerLevel.class)||origin==null
            ||(origin.getClass()!=BlockPos.class&&origin.getClass()!=BlockPos.MutableBlockPos.class)
            ||!(context.random() instanceof WorldgenRandom random)||!NativeDecoration.nativeRandom(random))return false;
        var call=new Call();call.world=world;call.origin=origin;call.config=config;call.random=random;
        call.region=NativeDecoration.fallenRegion(world);
        run(0,call,0);return true;
    }

    public static boolean attached(AttachedToLogsDecorator owner,TreeDecorator.Context context) {
        if(!ENABLED||owner.getClass()!=AttachedToLogsDecorator.class||context==null||context.getClass()!=TreeDecorator.Context.class
            ||!(context.random() instanceof WorldgenRandom random)||!NativeDecoration.nativeRandom(random))return false;
        // Null resources preserve the original body's conditional failure timing.
        if(Fields.PROVIDER.get(owner)==null||Fields.DIRECTIONS.get(owner)==null)return false;
        var call=new Call();call.decorator=owner;call.context=context;call.random=random;
        Call parent=CURRENT.get();
        if(parent!=null&&parent.context==context){call.region=parent.region;if(generation(call,0))return true;}
        run(1,call,(float)Fields.PROBABILITY.get(owner));return true;
    }

    private static boolean immutableDirections(Object directions) {
        return directions!=null&&(directions.getClass()==List.of(Direction.UP).getClass()
            ||directions.getClass()==List.of().getClass());
    }

    private static final Set<Class<?>> IMMUTABLE_CHAINS=Set.of(
        List.of().getClass(),List.of(0).getClass(),
        com.google.common.collect.ImmutableList.of().getClass(),
        com.google.common.collect.ImmutableList.of(0).getClass());

    /** Compile only proven immutable resources. A custom/mutable list must not
     * even be traversed during admission: its real forEach stays a VM effect. */
    static int[] compiledChain(List<TreeDecorator> chain) {
        if(chain==null||!IMMUTABLE_CHAINS.contains(chain.getClass())||chain.size()>4096)return null;
        var words=new ArrayList<Integer>();words.add(chain.size());
        for(var decorator:chain){
            if(decorator!=null&&decorator.getClass()==TrunkVineDecorator.class){
                words.add(1);
                var vine=Blocks.VINE.defaultBlockState();
                words.add(Block.getId(vine.setValue(VineBlock.EAST,true)));
                words.add(Block.getId(vine.setValue(VineBlock.WEST,true)));
                words.add(Block.getId(vine.setValue(VineBlock.SOUTH,true)));
                words.add(Block.getId(vine.setValue(VineBlock.NORTH,true)));
            }else if(decorator!=null&&decorator.getClass()==AttachedToLogsDecorator.class){
                Object resource=Fields.DIRECTIONS.get(decorator);
                if(!immutableDirections(resource))return null;
                var directions=(List<?>)resource;
                if(directions.isEmpty()||directions.size()>6)return null;
                for(Object direction:directions)if(!(direction instanceof Direction))return null;
                var provider=NativeDecorationEncoder.fallenProvider((BlockStateProvider)Fields.PROVIDER.get(decorator));
                if(provider==null)return null;
                words.add(0);words.add(Float.floatToRawIntBits((float)Fields.PROBABILITY.get(decorator)));
                for(int word:provider)words.add(word);
                words.add(directions.size());
                for(Object direction:directions)words.add(((Direction)direction).ordinal());
            }else return null;
        }
        return words.stream().mapToInt(Integer::intValue).toArray();
    }
    private static int[] generationDescriptor(Call call,int kind) {
        var words=new ArrayList<Integer>();
        words.add(Float.floatToRawIntBits(kind==0?(float)Fields.PROBABILITY.get(call.decorator):0));
        if(kind==0){
            Object resource=Fields.DIRECTIONS.get(call.decorator);
            if(!immutableDirections(resource))return null;
            var directions=(List<?>)resource;
            if(directions.isEmpty())return null;
            for(Object direction:directions)if(!(direction instanceof Direction))return null;
            var provider=NativeDecorationEncoder.fallenProvider((BlockStateProvider)Fields.PROVIDER.get(call.decorator));
            if(provider==null)return null;
            for(int word:provider)words.add(word);
            words.add(directions.size());
            for(Object direction:directions)words.add(((Direction)direction).ordinal());
        }else{
            var vine=Blocks.VINE.defaultBlockState();
            words.add(Block.getId(vine.setValue(VineBlock.EAST,true)));
            words.add(Block.getId(vine.setValue(VineBlock.WEST,true)));
            words.add(Block.getId(vine.setValue(VineBlock.SOUTH,true)));
            words.add(Block.getId(vine.setValue(VineBlock.NORTH,true)));
            words.add(0);
        }
        Object[] logs=call.context.logs().toArray();
        for(Object log:logs)if(log==null||log.getClass()!=BlockPos.class)return null;
        words.add(logs.length);
        for(Object log:logs){var pos=(BlockPos)log;words.add(pos.getX());words.add(pos.getY());words.add(pos.getZ());}
        return words.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean generation(Call call,int kind) {
        if(call.region==null)return false;
        int[] descriptor=generationDescriptor(call,kind);
        if(descriptor==null||call.region.refresh(true,true)!=net.minecraft.world.level.chunk.NativeDecorationRegion.REFRESHED)return false;
        Call previous=CURRENT.get();CURRENT.set(call);
        try(Arena arena=Arena.ofConfined()){
            var data=arena.allocateFrom(ValueLayout.JAVA_INT,descriptor);
            var state=arena.allocate(40,8);NativeDecoration.putInvocation(state,0,call.random);
            int result;
            try{result=(int)Bindings.GENERATION.invokeExact(kind,call.region.words(),(int)(call.region.words().byteSize()/8),
                call.region.out(),(int)(call.region.out().byteSize()/8),data,descriptor.length,state,NativeDecoration.fallenCallback());}
            finally{NativeDecoration.takeInvocation(state,0,call.random);}
            if(result==0)return false;
            NativeDecoration.finishFallenRegion(call.region,result);
            return true;
        }catch(Throwable failure){throw NativeFallenTrees.<RuntimeException>propagate(failure);}
        finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);Reference.reachabilityFence(call);}
    }

    private static void vines(TreeDecorator.Context context,BlockPos log) {
        var call=new Call();call.context=context;call.random=(WorldgenRandom)context.random();call.vineLog=log;
        run(2,call,0);
    }

    private static void run(int kind,Call call,float probability) {
        Call previous=CURRENT.get();CURRENT.set(call);
        try(Arena arena=Arena.ofConfined()) {
            var state=arena.allocate(40,8);NativeDecoration.putInvocation(state,0,call.random);
            int result;
            try {
                result=switch(kind) {
                    case 0 -> (int)Bindings.FALLEN.invokeExact(call.origin.getX(),call.origin.getY(),call.origin.getZ(),state,Bindings.EFFECT);
                    case 1 -> (int)Bindings.ATTACHED.invokeExact(probability,state,Bindings.EFFECT);
                    default -> (int)Bindings.VINES.invokeExact(state,Bindings.EFFECT);
                };
            }finally{NativeDecoration.takeInvocation(state,0,call.random);}
            if(call.failure!=null)throw NativeFallenTrees.<RuntimeException>propagate(call.failure);
            if(result!=1)throw new IllegalStateException("Native fallen invocation returned "+result);
        }catch(Throwable failure){throw NativeFallenTrees.<RuntimeException>propagate(failure);}
        finally {
            if(previous==null)CURRENT.remove();else CURRENT.set(previous);
            Reference.reachabilityFence(call);
        }
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException propagate(Throwable failure)throws E{throw (E)failure;}

    /** Invocation-owned actual VM resources for a compiled fallen program. */
    static final class Generation {
        final Call call;
        Generation(Call call){this.call=call;}
    }
    static Generation generationEffect(Generation current,NativeDecorationRegion region,WorldGenLevel world,WorldgenRandom random,
            ConfiguredFeature<?,?> configured,MemorySegment args,int count) {
        var words=args.reinterpret(count*8L);
        int operation=(int)words.getAtIndex(ValueLayout.JAVA_LONG,0);
        if(operation==0)return null;
        var header=words.asSlice(32);
        if(current==null){
            var call=new Call();call.world=world;call.region=region;call.random=random;call.config=(FallenTreeConfiguration)configured.config();
            for(int slot=0;slot<3;slot++)call.cursors[slot]=new BlockPos.MutableBlockPos(
                (int)header.getAtIndex(ValueLayout.JAVA_LONG,1+slot*3L),
                (int)header.getAtIndex(ValueLayout.JAVA_LONG,2+slot*3L),
                (int)header.getAtIndex(ValueLayout.JAVA_LONG,3+slot*3L));
            call.origin=call.cursors[0].immutable();
            call.state=Block.stateById((int)words.getAtIndex(ValueLayout.JAVA_LONG,3));
            current=new Generation(call);
        }
        try{fallenEffect(current.call,operation,header);}
        finally{exportCursors(current.call,header);}
        return current;
    }

    private static int effect(int operation,MemorySegment args,int count) {
        Call call=CURRENT.get();if(call==null)return -1;
        var words=args.reinterpret(count*8L);
        boolean framed=(operation&RNG_CALLBACK)!=0;
        int originalCount=framed?count-6:count;
        long stateAt=originalCount+1L;
        if(framed)NativeDecoration.takeInvocation(words,stateAt,call.random);
        int op=operation&~RNG_CALLBACK;
        try {
            if(op>=384)vinesEffect(call,op-384,words);
            else if(op>=256)attachedEffect(call,op-256,words);
            else fallenEffect(call,op-128,words);
            return 0;
        }catch(Throwable failure){if(call.failure==null)call.failure=failure;return -1;}
        finally {
            if(op>=128&&op<256) {
                exportCursors(call,words);
            }
            if(framed)NativeDecoration.putInvocation(words,stateAt,call.random);
        }
    }

    private static void exportCursors(Call call,MemorySegment words) {
        int mask=0;
        for(int slot=0;slot<3;slot++)if(call.cursors[slot]!=null){
            mask|=1<<slot;var pos=call.cursors[slot];
            words.setAtIndex(ValueLayout.JAVA_LONG,1+slot*3L,pos.getX());
            words.setAtIndex(ValueLayout.JAVA_LONG,2+slot*3L,pos.getY());
            words.setAtIndex(ValueLayout.JAVA_LONG,3+slot*3L,pos.getZ());
        }
        words.setAtIndex(ValueLayout.JAVA_LONG,0,mask);
    }

    private static void fallenEffect(Call call,int op,MemorySegment words) {
        int project=(int)words.getAtIndex(ValueLayout.JAVA_LONG,0);
        for(int slot=0;slot<3;slot++)if((project&(1<<slot))!=0)call.cursors[slot].set(
            (int)words.getAtIndex(ValueLayout.JAVA_LONG,1+slot*3L),(int)words.getAtIndex(ValueLayout.JAVA_LONG,2+slot*3L),
            (int)words.getAtIndex(ValueLayout.JAVA_LONG,3+slot*3L));
        int parameter=(int)words.getAtIndex(ValueLayout.JAVA_LONG,11);
        Direction direction=Direction.values()[(int)words.getAtIndex(ValueLayout.JAVA_LONG,10)];
        int reply=0;
        switch(op) {
            case 1 -> call.cursors[0]=call.origin.mutable();
            case 2 -> call.state=call.config.trunkProvider.getState(call.random,call.cursors[parameter]);
            case 3 -> {BlockState blockState=call.state;call.state=blockState.trySetValue(RotatedPillarBlock.AXIS,direction.getAxis());}
            case 4 -> call.world.setBlock(call.cursors[parameter],call.state,3);
            case 5 -> call.cursors[2]=call.cursors[parameter].mutable();
            case 6 -> reply=call.world.getBlockState(call.cursors[2]).isAir()?1:0;
            case 7 -> call.world.getChunk(call.cursors[2]).markPosForPostprocessing(call.cursors[2]);
            case 8 -> decorate(call,parameter!=0,words);
            case 9 -> reply=call.config.logLength.sample(call.random);
            case 10 -> call.cursors[1]=call.origin.relative(direction,parameter).mutable();
            case 11 -> reply=TreeFeature.validTreePos(call.world,call.cursors[1])?1:0;
            case 12 -> reply=call.world.getBlockState(call.cursors[1].below()).isFaceSturdy(call.world,call.cursors[1],Direction.UP)?1:0;
            default -> throw new IllegalStateException("Unknown fallen effect "+op);
        }
        words.setAtIndex(ValueLayout.JAVA_LONG,11,reply);
    }

    private static void decorate(Call call,boolean stump,MemorySegment words) {
        List<TreeDecorator> list=stump?call.config.stumpDecorators:call.config.logDecorators;
        if(list.isEmpty())return;
        int count=(int)words.getAtIndex(ValueLayout.JAVA_LONG,12);
        List<BlockPos> positions=new ArrayList<>(count);
        for(int i=0;i<count;i++)positions.add(new BlockPos((int)words.getAtIndex(ValueLayout.JAVA_LONG,13+i*3L),
            (int)words.getAtIndex(ValueLayout.JAVA_LONG,14+i*3L),(int)words.getAtIndex(ValueLayout.JAVA_LONG,15+i*3L)));
        // Preserve the native HashSet's exact iteration, without a second hash table.
        Set<BlockPos> set=new AbstractSet<>() {
            public int size(){return positions.size();}
            public Iterator<BlockPos> iterator(){return positions.iterator();}
        };
        // A retained public context owns its actual world/setter, not this
        // invocation's configuration, providers, cursors or region binding.
        WorldGenLevel world=call.world;
        var context=new TreeDecorator.Context(world,(pos,state)->world.setBlock(pos,state,19),call.random,set,Set.of(),Set.of());
        Call previous=CURRENT.get();var previousContext=call.context;call.context=context;CURRENT.set(call);
        try{list.forEach(treeDecorator->{
            if(treeDecorator!=null&&treeDecorator.getClass()==TrunkVineDecorator.class){
                var vines=new Call();vines.region=call.region;vines.context=context;vines.random=call.random;
                if(!generation(vines,1))context.logs().forEach(pos->vines(context,pos));
            }else treeDecorator.place(context);
        });}finally{call.context=previousContext;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }

    @SuppressWarnings("unchecked")
    private static void attachedEffect(Call call,int op,MemorySegment words) {
        int parameter=(int)words.getAtIndex(ValueLayout.JAVA_LONG,0),reply=0;
        switch(op) {
            case 1 -> {call.snapshot=call.context.logs().toArray();reply=call.snapshot.length;}
            case 2 -> {call.directions=(List<Direction>)Fields.DIRECTIONS.get(call.decorator);reply=call.directions.size();}
            case 3 -> {call.random.nextInt(parameter);throw new AssertionError("Invalid bound unexpectedly accepted");}
            case 4 -> call.direction=call.directions.get(parameter);
            case 5 -> call.target=call.vineLog.relative(call.direction);
            case 6 -> reply=call.context.isAir(call.target)?1:0;
            case 7 -> call.state=((BlockStateProvider)Fields.PROVIDER.get(call.decorator)).getState(call.random,call.target);
            case 8 -> call.context.setBlock(call.target,call.state);
            case 9 -> call.vineLog=(BlockPos)call.snapshot[parameter];
            default -> throw new IllegalStateException("Unknown attached-log effect "+op);
        }
        words.setAtIndex(ValueLayout.JAVA_LONG,1,reply);
    }
    private static void vinesEffect(Call call,int op,MemorySegment words) {
        int side=(int)words.getAtIndex(ValueLayout.JAVA_LONG,0),reply=0;
        if(op==1) {
            BlockPos blockPos=call.vineLog;
            call.target=switch(side){case 0->blockPos.west();case 1->blockPos.east();case 2->blockPos.north();default->blockPos.south();};
            reply=call.context.isAir(call.target)?1:0;
        }else if(op==2)call.context.placeVine(call.target,switch(side){case 0->VineBlock.EAST;case 1->VineBlock.WEST;case 2->VineBlock.SOUTH;default->VineBlock.NORTH;});
        else throw new IllegalStateException("Unknown vine effect "+op);
        words.setAtIndex(ValueLayout.JAVA_LONG,1,reply);
    }
}
