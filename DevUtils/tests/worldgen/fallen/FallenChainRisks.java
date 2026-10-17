package fallen;

import com.google.common.collect.ImmutableList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import static fallen.FallenAudit.*;

/** Calls the real public feature with retained configuration/list/context aliases. */
final class FallenChainRisks {
    static void inspectHandoff(Object configuration)throws Exception{
        if(!Boolean.getBoolean("fallen.nativeParent"))return;
        var encoder=Class.forName("net.minecraft.world.level.levelgen.NativeDecorationEncoder");
        java.lang.reflect.Field field;
        try{field=encoder.getDeclaredField("LAST_TABLES");}
        catch(NoSuchFieldException removed){
            // The candidate returns one actual Encoded owner to its consumer.
            Class.forName(encoder.getName()+"$Encoded").getDeclaredMethod("tables");
            if(Boolean.getBoolean("fallen.gcDiagnosticReleaseHandoff"))throw new AssertionError("No diagnostic-only root exists");
            java.nio.file.Files.writeString(OUTPUT.resolve("gc-handoff-diagnostic.json"),
                "{\"actualLastEncoderTableRetainsConfiguration\":false,\"diagnosticOnlyRootRelease\":false,\"ownerLocalHandoff\":true}");
            return;
        }
        field.setAccessible(true);
        var local=(ThreadLocal<?>)field.get(null);Object tables=local.get();
        boolean retained=tables!=null&&((List<?>)FallenAudit.field(tables,"javaFeatures")).stream()
            .anyMatch(value->((net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?,?>)value).config()==configuration);
        boolean release=Boolean.getBoolean("fallen.gcDiagnosticReleaseHandoff");
        java.nio.file.Files.writeString(OUTPUT.resolve("gc-handoff-diagnostic.json"),
            new com.google.gson.Gson().toJson(Map.of("actualLastEncoderTableRetainsConfiguration",retained,"diagnosticOnlyRootRelease",release)));
        if(release){if(!retained)throw new AssertionError("Diagnostic root was not observed");local.remove();}
    }
    static TreeDecorator attached(){return new AttachedToLogsDecorator(1,
        BlockStateProvider.simple(Blocks.RED_MUSHROOM),List.of(Direction.UP,Direction.NORTH));}
    static void run(ServerLevel level)throws Throwable{
        for(boolean guava:new boolean[]{false,true})fixture(level,"parent-native-chain-"+(guava?"guava":"jdk"),42,"flat",(f,t)->{
            List<TreeDecorator> chain=guava?ImmutableList.of(TrunkVineDecorator.INSTANCE,attached(),TrunkVineDecorator.INSTANCE,attached())
                :List.of(TrunkVineDecorator.INSTANCE,attached(),TrunkVineDecorator.INSTANCE,attached());
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,chain,chain);
        });
        for(boolean empty:new boolean[]{false,true})fixture(level,"parent-chain-mutable-"+(empty?"empty":"append")+"-after-compile",42,"flat",(f,t)->{
            var logs=new ArrayList<TreeDecorator>();if(!empty)logs.add(TrunkVineDecorator.INSTANCE);
            t.afterPrepare=()->logs.add(attached());
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),logs);
        });
        fixture(level,"parent-chain-same-list-set",42,"flat",(f,t)->{
            var logs=new ArrayList<TreeDecorator>();
            logs.add(new Effect(c->{t.event("replace-next");logs.set(1,attached());}));logs.add(TrunkVineDecorator.INSTANCE);
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),logs);
        });
        fixture(level,"parent-chain-same-list-add-fails",42,"flat",(f,t)->{
            t.expectedError=ConcurrentModificationException.class;var logs=new ArrayList<TreeDecorator>();
            logs.add(TrunkVineDecorator.INSTANCE);logs.add(new Effect(c->{t.event("append-active");logs.add(attached());}));logs.add(attached());
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),logs);
        });
        fixture(level,"parent-chain-custom-traversal",42,"flat",(f,t)->{
            List<TreeDecorator> logs=new AbstractList<>(){
                public int size(){t.event("custom-size");return 2;}
                public TreeDecorator get(int i){t.event("custom-get "+i);return i==0?TrunkVineDecorator.INSTANCE:attached();}
                public void forEach(java.util.function.Consumer<? super TreeDecorator> action){
                    t.event("actual-custom-forEach");t.random.nextGaussian();action.accept(attached());action.accept(TrunkVineDecorator.INSTANCE);
                }
            };
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),logs);
        });
        fixture(level,"parent-chain-escaped-log-alias",42,"flat",(f,t)->{
            var retained=new java.util.concurrent.atomic.AtomicReference<TreeDecorator.Context>();
            var logs=List.<TreeDecorator>of(TrunkVineDecorator.INSTANCE,new Effect(c->{
                retained.set(c);Collections.reverse(c.logs());c.logs().set(0,c.logs().getFirst().mutable());t.event("exposed-log-order "+c.logs());
            }),attached(),new Effect(c->{if(retained.get()!=c)throw new AssertionError("Context identity changed");
                ((BlockPos.MutableBlockPos)c.logs().getFirst()).move(0,1,0);t.event("escaped-context "+retained.get().logs());}),attached());
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),logs);
        });
        fixture(level,"parent-chain-prefix-reentry",42,"flat",(f,t)->config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),
            List.of(TrunkVineDecorator.INSTANCE,attached(),new Effect(c->{t.event("chain-nested-before");
                Feature.FALLEN_TREE.place(config(BlockStateProvider.simple(Blocks.BIRCH_LOG),4,List.of(),List.of(attached())),
                    f.region,f.generator,c.random(),new BlockPos(3,80,3));t.event("chain-nested-after");}),attached())));
        fixture(level,"parent-chain-checked-throw-prefix",42,"flat",(f,t)->{
            t.expectedError=Exception.class;
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(attached(),TrunkVineDecorator.INSTANCE,
                new Effect(c->{c.random().nextGaussian();t.event("chain-gaussian-then-throw");throw FallenAudit.<RuntimeException>propagate(t.checked);}),attached()));
        });
        fixture(level,"parent-chain-stump-mutates-later-membership",42,"flat",(f,t)->{
            var logs=new ArrayList<TreeDecorator>(List.of(TrunkVineDecorator.INSTANCE));
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(attached(),new Effect(c->{
                logs.clear();logs.add(attached());logs.add(TrunkVineDecorator.INSTANCE);t.event("replace-later-chain");})),logs);
        });
        fixture(level,"parent-chain-immutable-list-custom-directions",42,"flat",(f,t)->{
            List<Direction> directions=new AbstractList<>(){
                public int size(){t.event("chain-directions-size");return 1;}
                public Direction get(int i){t.random.nextInt(7);t.event("chain-directions-get");return Direction.UP;}
            };
            return config(BlockStateProvider.simple(Blocks.OAK_LOG),8,List.of(),List.of(TrunkVineDecorator.INSTANCE,
                new AttachedToLogsDecorator(1,BlockStateProvider.simple(Blocks.BROWN_MUSHROOM),directions),attached()));
        });
        if("parent-chain-escaped-context-gc".matches(System.getProperty("fallen.fixtures",".*"))){
            var retained=new java.util.concurrent.atomic.AtomicReference<TreeDecorator.Context>();
            var provider=new java.util.concurrent.atomic.AtomicReference<java.lang.ref.WeakReference<Object>>();
            var configuration=new java.util.concurrent.atomic.AtomicReference<java.lang.ref.WeakReference<Object>>();
            fixture(level,"parent-chain-escaped-context-gc",42,"flat",(f,t)->{
                var source=BlockStateProvider.simple(Blocks.OAK_LOG);
                var cfg=config(source,8,List.of(),List.of(TrunkVineDecorator.INSTANCE,attached(),new Effect(retained::set),attached()));
                provider.set(new java.lang.ref.WeakReference<>(source));configuration.set(new java.lang.ref.WeakReference<>(cfg));return cfg;
            });
            inspectHandoff(configuration.get().get());
            for(int i=0;i<60&&(provider.get().get()!=null||configuration.get().get()!=null);i++){System.gc();Thread.sleep(50);}
            if(provider.get().get()!=null||configuration.get().get()!=null)throw new AssertionError("Escaped chain context retained program configuration/provider");
            var context=Objects.requireNonNull(retained.get());var pos=new BlockPos(8,84,8);
            context.setBlock(pos,Blocks.RED_MUSHROOM.defaultBlockState());
            if(!context.checkBlock(pos,state->state.is(Blocks.RED_MUSHROOM)))throw new AssertionError("Escaped context setter lost its world");
            ROWS.getLast().put("escapedContextSurvivesProviderCollection",true);
            java.lang.ref.Reference.reachabilityFence(context);
        }
    }
}
