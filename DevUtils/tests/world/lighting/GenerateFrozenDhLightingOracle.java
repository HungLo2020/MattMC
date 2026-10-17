import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.util.LodUtil;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.ProtoChunkTicks;

import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.generation.AdjacentChunkHolder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import net.minecraft.world.level.block.Blocks;

/** Actual untouched Frozen production lighting recorder. */
public final class GenerateFrozenDhLightingOracle {
    private static String hash(Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private static ILevelWrapper registryOnlyLevel() throws Exception {
        // Production serialization reads Level.registryAccess only. No server,
        // world simulation or overridden DH calculation is involved in this fixture.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object unsafe = field.get(null);
        Level level = (Level)unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, ServerLevel.class);
        Field registry = Level.class.getDeclaredField("registryAccess"); registry.setAccessible(true);
        registry.set(level, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return (ILevelWrapper)Proxy.newProxyInstance(ILevelWrapper.class.getClassLoader(), new Class<?>[]{ILevelWrapper.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getWrappedMcObject")) return level;
                if (method.getName().equals("toString")) return "Frozen fixture registry-only level";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new UnsupportedOperationException("Unexpected fixture level callback: " + method);
            });
    }

    private static ProtoChunk recorded(JsonObject record, ChunkPos position) {
        int minY = record.get("min_y").getAsInt(), height = record.get("height").getAsInt();
        var sections = new LevelChunkSection[height / 16];
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (var item : record.getAsJsonArray("sections")) {
            var section = item.getAsJsonObject();
            var palette = new ArrayList<BlockState>();
            for (var state : section.getAsJsonArray("palette"))
                palette.add(BlockState.CODEC.parse(JsonOps.INSTANCE, state).result().orElseThrow());
            var data = section.getAsJsonArray("data"); long[] words = new long[data.size()];
            for (int i = 0; i < words.length; i++) words[i] = data.get(i).getAsLong();
            var packed = new PalettedContainerRO.PackedData<>(palette,
                words.length == 0 ? Optional.empty() : Optional.of(LongStream.of(words)));
            var states = PalettedContainer.unpack(strategy, packed).result().orElseThrow();
            sections[section.get("y").getAsInt() - minY / 16] = new LevelChunkSection(states, null);
        }
        for (var section : sections) if (section == null) throw new IllegalArgumentException("Incomplete saved chunk");
        return new ProtoChunk(position, UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(),
            new ProtoChunkTicks<>(), LevelHeightAccessor.create(minY, height), null, null);
    }


    private static final Class<?>[] PINNED = {BlockStateWrapper.class, ChunkWrapper.class, ProtoChunk.class,
        DhLightingEngine.class, AdjacentChunkHolder.class, ChunkLightStorage.class, EDhDirection.class};
    private static Method light;
    private static void lightBytes(DataOutputStream out, ChunkWrapper chunk) throws Exception {
        for(int y=chunk.getInclusiveMinBuildHeight();y<chunk.getExclusiveMaxBuildHeight();y++)
            for(int z=0;z<16;z++) for(int x=0;x<16;x++)
                out.writeByte(chunk.getDhBlockLight(x,y,z) | chunk.getDhSkyLight(x,y,z)<<4);
    }
    private static void runCase(DataOutputStream out, String name, JsonObject record, int[] slots,
                                int[] order, ILevelWrapper level, boolean editCachedSource) throws Exception {
        runCase(out,name,record,slots,order,level,editCachedSource,true);
    }
    private static void runCase(DataOutputStream out, String name, JsonObject record, int[] slots,
                                int[] order, ILevelWrapper level, boolean editCachedSource, boolean initialLight) throws Exception {
        var wrappers = new ChunkWrapper[9]; var chunks = new ProtoChunk[9];
        int cx=-37,cz=41;
        for(int slot:slots) {
            var position=new ChunkPos(cx+slot%3-1,cz+slot/3-1);
            chunks[slot]=recorded(record,position);wrappers[slot]=new ChunkWrapper(chunks[slot],level);
            // Exercise inherited light data as well as empty initial storage.
            if(initialLight) {
                wrappers[slot].setDhBlockLight(3,chunks[slot].getMinY()+3,5,9);
                wrappers[slot].setDhSkyLight(4,chunks[slot].getMinY()+4,6,7);
            }
            wrappers[slot].getWorldBlockLightPosList(); // Freeze the actual production cache before edits.
        }
        if(editCachedSource) {
            var c=chunks[4];var p=new BlockPos(0,c.getMinY()+2,0);
            c.setBlockState(p,Blocks.GLOWSTONE.defaultBlockState(),0);
            // The previously populated production source list intentionally remains stale.
        }
        out.writeUTF(name);out.writeInt(slots.length);
        for(int slot:slots) {
            var c=chunks[slot];var w=wrappers[slot];out.writeInt(slot);out.writeInt(c.getMinY());
            out.writeInt(c.getHeight());out.writeInt(w.getMinNonEmptyHeight());out.writeInt(w.getMaxNonEmptyHeight());
            for(var section:c.getSections()) for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                out.writeInt(Block.getId(section.getBlockState(x,y,z)));
            var sources=w.getWorldBlockLightPosList();out.writeInt(sources.size());
            for(var p:sources) out.writeInt((p.getY()-c.getMinY())*256+(p.getZ()&15)*16+(p.getX()&15));
            lightBytes(out,w);
        }
        out.writeInt(order.length);for(int slot:order)out.writeInt(slot);
        var nearby=new ArrayList<IChunkWrapper>();for(int slot:order)nearby.add(slot<0?null:wrappers[slot]);
        out.writeInt(3);
        for(int phase=0;phase<3;phase++) {
            int sky=phase==2?0:15; boolean updateSky=phase!=1;
            out.writeInt(sky);out.writeBoolean(true);out.writeBoolean(updateSky);
            int iterations=(int)light.invoke(DhLightingEngine.INSTANCE,wrappers[4],nearby,sky,true,updateSky);
            out.writeInt(iterations);
            for(int slot:slots) {
                lightBytes(out,wrappers[slot]);out.writeBoolean(wrappers[slot].isDhBlockLightingCorrect());
                out.writeBoolean(wrappers[slot].isDhSkyLightCorrect());
            }
            System.out.println(name+" phase="+phase+" iterations="+iterations);
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3 && args.length!=4)throw new IllegalArgumentException("output saved-chunks-json class-pin-json [cold]");
        boolean cold=args.length==4 && args[3].equals("cold");
        var pins=JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
        for(var type:PINNED)if(!hash(type).equals(pins.get(type.getName()).getAsString()))
            throw new IllegalStateException("Not pinned Frozen: "+type);
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();var level=registryOnlyLevel();
        light=DhLightingEngine.class.getDeclaredMethod("lightChunk",IChunkWrapper.class,ArrayList.class,int.class,boolean.class,boolean.class);
        light.setAccessible(true);
        byte[] saved=Files.readAllBytes(Path.of(args[1]));
        var records=JsonParser.parseString(new String(saved,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("chunks");
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x44484c31);out.writeInt(PINNED.length);for(var type:PINNED){out.writeUTF(type.getName());out.writeUTF(hash(type));}
            out.writeUTF(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(saved)));
            out.writeUTF("Actual Frozen DH production lighting including work count, initial data, cached sources, missing/duplicate neighbors, center clear after seeding, full/bake/no-sky passes. Supplemental CPU fixture.");
            out.writeInt(Block.BLOCK_STATE_REGISTRY.size());
            for(var state:Block.BLOCK_STATE_REGISTRY){var w=BlockStateWrapper.fromBlockState(state,level);out.writeByte(w.getOpacity());out.writeByte(w.getLightEmission());}
            if(cold) {
                out.writeInt(2);var base=records.get(0).getAsJsonObject();
                runCase(out,"cold-nine",base,new int[]{0,1,2,3,4,5,6,7,8},new int[]{8,0,4,2,6,1,7,3,5},level,false,false);
                runCase(out,"cold-missing-center",base,new int[]{1,4,5},new int[]{-1,5,5,1,-1},level,false,false);
                return;
            }
            out.writeInt(records.size()+4);
            for(int i=0;i<records.size();i++)runCase(out,"saved-solo-"+i,records.get(i).getAsJsonObject(),new int[]{4},new int[]{4},level,false);
            var base=records.get(0).getAsJsonObject();
            runCase(out,"saved-nine",base,new int[]{0,1,2,3,4,5,6,7,8},new int[]{8,0,4,2,6,1,7,3,5},level,false);
            runCase(out,"missing-center-seed-duplicates",base,new int[]{1,4,5},new int[]{-1,5,5,1,-1},level,false);
            runCase(out,"stale-emitter-cache",base,new int[]{4},new int[]{4},level,true);
            var empty=base.deepCopy();var sections=empty.getAsJsonArray("sections");
            for(var item:sections){var section=item.getAsJsonObject();section.add("data",new com.google.gson.JsonArray());
                var palette=new com.google.gson.JsonArray();palette.add(JsonParser.parseString("{\"Name\":\"minecraft:air\"}"));section.add("palette",palette);}
            runCase(out,"entirely-empty",empty,new int[]{4},new int[]{4},level,false);
        }
    }
}
