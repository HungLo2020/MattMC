package net.minecraft.world.level.lighting;

import java.io.*;
import java.lang.reflect.*;
import java.lang.foreign.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;

/** Actual custom source dispatch/order, compiled unchanged against each image. */
public final class VerifyCoordinatorSkySources {
    private static final List<String> trace = new ArrayList<>();
    private static final Error marker = new AssertionError("custom source neighbor");
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field value = type.getDeclaredField(name); value.setAccessible(true); return value.get(owner); }
            catch (NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static final class Sources extends ChunkSkyLightSources {
        final String name;
        final boolean fail;
        Sources(String name, boolean fail) { super(LevelHeightAccessor.create(0, 64)); this.name = name; this.fail = fail; }
        @Override public int getLowestSourceY(int x, int z) {
            trace.add(name + " " + x + " " + z);
            if (fail) throw marker;
            return Integer.MAX_VALUE;
        }
    }
    private static void fill(ChunkSkyLightSources sources,int height){
        try{Method fill=ChunkSkyLightSources.class.getDeclaredMethod("fill",int.class);fill.setAccessible(true);fill.invoke(sources,height);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static long nativeSeeds(SkyLightEngine engine)throws Exception{
        try{Object coordinator=field(engine,"lightCoordinator");MemorySegment header=(MemorySegment)field(coordinator,"metadata");return Integer.toUnsignedLong((int)ValueLayout.JAVA_INT.varHandle().getAcquire(header,36L));}
        catch(NoSuchFieldException original){return 0;}
        finally{java.lang.ref.Reference.reachabilityFence(engine);}
    }
    private static final class CopyMutation extends DataLayer {
        final ChunkSkyLightSources sources;
        CopyMutation(ChunkSkyLightSources sources){super(0);this.sources=sources;}
        @Override public DataLayer copy(){trace.add("copy");VerifyCoordinatorSkySources.fill(sources,64);return new DataLayer(0);}
    }
    public static void main(String[] args) throws Exception {
        if(args[1].equals("all")){
            Path directory=Path.of(args[0]);Files.createDirectories(directory);
            for(String mode:List.of("stock","default-write","default-skip","aliases","escaped","skip","throw-neighbor","copy-mutation","custom-storage-copy"))main(new String[]{directory.resolve(mode+".txt").toString(),mode});
            return;
        }
        trace.clear();
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Field output = VerifyLightCoordinator.class.getDeclaredField("out"); output.setAccessible(true);
        try (var quiet = new PrintWriter(OutputStream.nullOutputStream())) {
            output.set(null, quiet);
            Class<?> fixtureType = Class.forName(VerifyLightCoordinator.class.getName() + "$Fixture");
            Constructor<?> constructor = fixtureType.getDeclaredConstructor(boolean.class); constructor.setAccessible(true);
            Object fixture = constructor.newInstance(true);
            Object world = field(fixture, "world");
            Map<?, ?> chunks = (Map<?, ?>)field(world, "chunks");
            LevelLightEngine engine = (LevelLightEngine)field(fixture, "engine");
            SkyLightEngine sky=(SkyLightEngine)engine.getLayerListener(LightLayer.SKY);
            SkyLightSectionStorage storage=(SkyLightSectionStorage)field(sky,"storage");
            ChunkAccess center=(ChunkAccess)chunks.get(ChunkPos.asLong(0,0));
            if(args[1].equals("stock")||args[1].equals("default-write")||args[1].equals("default-skip")||args[1].equals("aliases")||args[1].equals("escaped")){
                long key=net.minecraft.core.SectionPos.asLong(0,storage.getTopSectionY(net.minecraft.core.SectionPos.getZeroNode(0,0))-1,0);
                DataLayer input=null;byte[] escaped=null;long lower=key-1;
                if(!args[1].equals("stock")){
                    input=new DataLayer(args[1].startsWith("default-")?15:3);
                    if(args[1].equals("escaped"))escaped=input.getData();
                    storage.updatingSectionData.setLayer(key,input);storage.changedSections.remove(key);
                    if(args[1].equals("aliases")){storage.updatingSectionData.setLayer(lower,input);storage.changedSections.remove(lower);fill(center.getSkyLightSources(),0);}
                    if(args[1].equals("default-skip"))fill(center.getSkyLightSources(),64);
                    storage.updatingSectionData.clearCache();
                }
                long before=nativeSeeds(sky);sky.propagateLightSources(new ChunkPos(0,0));long after=nativeSeeds(sky);
                boolean expected=Boolean.getBoolean("coordinator.expectSkyProducer")&&!args[1].equals("escaped");
                if(Boolean.getBoolean("coordinator.expectSkyProducer") && (after>before)!=expected)throw new AssertionError("Native complete sky producer admission did not match layer contract");
                DataLayer actual=storage.updatingSectionData.getLayer(key);
                trace.add("sample "+actual.get(0,0,0)+" lazy="+actual.isDefinitelyHomogenous());
                if(input!=null)trace.add("input "+input.get(0,0,0)+" lazy="+input.isDefinitelyHomogenous()+" copied="+(actual!=input));
                if(args[1].equals("aliases"))trace.add("alias "+(actual==storage.updatingSectionData.getLayer(lower))+" lower="+storage.updatingSectionData.getLayer(lower).get(0,0,0));
                if(escaped!=null)trace.add("escaped "+escaped[0]);
                trace.add("completed");Files.write(Path.of(args[0]),trace);return;
            }
            if(args[1].equals("custom-storage-copy")){
                SkyLightSectionStorage custom=new SkyLightSectionStorage((LightChunkGetter)world){
                    @Override protected int getTopSectionY(long column){return 4;}
                    @Override protected int getBottomSectionY(){return 3;}
                };
                long key=net.minecraft.core.SectionPos.asLong(0,3,0);
                custom.updatingSectionData.setLayer(key,new CopyMutation(center.getSkyLightSources()));custom.updatingSectionData.clearCache();
                new SkyLightEngine((LightChunkGetter)world,custom).propagateLightSources(new ChunkPos(0,0));
                trace.add("sample "+custom.updatingSectionData.getLayer(key).get(0,0,0));trace.add("completed");Files.write(Path.of(args[0]),trace);return;
            }
            if(args[1].equals("copy-mutation")){
                long key=net.minecraft.core.SectionPos.asLong(0,storage.getTopSectionY(net.minecraft.core.SectionPos.getZeroNode(0,0))-1,0);
                storage.updatingSectionData.setLayer(key,new CopyMutation(center.getSkyLightSources()));
                storage.updatingSectionData.clearCache();storage.changedSections.remove(key);
                sky.propagateLightSources(new ChunkPos(0,0));
                trace.add("sample "+storage.updatingSectionData.getLayer(key).get(0,0,0));
                trace.add("completed");Files.write(Path.of(args[0]),trace);return;
            }
            Field sources = ChunkAccess.class.getDeclaredField("skyLightSources"); sources.setAccessible(true);
            for (Object value : chunks.values()) {
                var chunk = (ChunkAccess)value; ChunkPos pos = chunk.getPos();
                sources.set(chunk, new Sources(pos.x + "," + pos.z, args[1].equals("throw-neighbor") && pos.x == 0 && pos.z == -1));
            }
            try {
                ((SkyLightEngine)engine.getLayerListener(LightLayer.SKY)).propagateLightSources(new ChunkPos(0, 0));
                trace.add("completed");
            } catch (Throwable failure) {
                trace.add("error " + failure.getClass().getName() + " original=" + (failure == marker));
            }
            Files.write(Path.of(args[0]), trace);
            System.out.println("CUSTOM_SKY_SOURCE_ROWS " + trace.size());
            java.lang.ref.Reference.reachabilityFence(fixture);
        }
    }
}
