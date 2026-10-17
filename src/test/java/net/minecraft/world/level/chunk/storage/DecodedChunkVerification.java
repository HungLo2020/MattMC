package net.minecraft.world.level.chunk.storage;

import java.io.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.zip.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.*;

/** Actual Frozen/Current region-read and public chunk-parse callers; no reference algorithm. */
public final class DecodedChunkVerification {
    static net.minecraft.server.packs.resources.MultiPackResourceManager resources;
    static PalettedContainerFactory factory;
    static final LevelHeightAccessor HEIGHT=LevelHeightAccessor.create(-64,384);
    static MethodHandle readTape, loaded, parseLoaded, readOwnedTape, ownedLoaded;
    static final UnaryOperator<CompoundTag> UPGRADE=UnaryOperator.identity();
    static void setup() throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        resources=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers=net.minecraft.server.RegistryLayer.createRegistryAccess();
        var tags=net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
        var lookups=net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN),tags);
        RegistryAccess registries=net.minecraft.resources.RegistryDataLoader.load(resources,lookups,net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        factory=PalettedContainerFactory.create(registries);
        try {
            var type=Class.forName("net.minecraft.world.level.chunk.storage.IOWorker$Loaded");
            var lookup=MethodHandles.lookup();
            readTape=lookup.findVirtual(RegionFileStorage.class,"readTape",MethodType.methodType(byte[].class,ChunkPos.class));
            loaded=MethodHandles.insertArguments(lookup.findConstructor(type,MethodType.methodType(void.class,byte[].class,CompoundTag.class)),1,(CompoundTag)null)
                .asType(MethodType.methodType(Object.class,byte[].class));
            parseLoaded=lookup.findStatic(SerializableChunkData.class,"parseLoaded",MethodType.methodType(SerializableChunkData.class,
                LevelHeightAccessor.class,PalettedContainerFactory.class,type,UnaryOperator.class))
                .asType(MethodType.methodType(SerializableChunkData.class,LevelHeightAccessor.class,PalettedContainerFactory.class,Object.class,UnaryOperator.class));
            try {
                var tapeType=Class.forName("net.minecraft.world.level.chunk.storage.NativeChunkTape", false, DecodedChunkVerification.class.getClassLoader());
                readOwnedTape=lookup.findVirtual(RegionFileStorage.class,"readOwnedTape",MethodType.methodType(tapeType,ChunkPos.class))
                    .asType(MethodType.methodType(Object.class,RegionFileStorage.class,ChunkPos.class));
                ownedLoaded=MethodHandles.insertArguments(lookup.findConstructor(type,MethodType.methodType(void.class,byte[].class,CompoundTag.class,tapeType)),
                    0,(byte[])null,(CompoundTag)null).asType(MethodType.methodType(Object.class,Object.class));
            } catch(ClassNotFoundException | NoSuchMethodException predecessor) { /* Prior region-array route. */ }
        } catch(ClassNotFoundException frozen) { /* Original ChunkMap reads the full tag and calls parse. */ }
    }
    static List<CompoundTag> corpus() throws IOException {
        try(var in=new DataInputStream(Files.newInputStream(Path.of("src/test/resources/storage/chunks.bin")))) {
            if(in.readInt()!=0x434e4254) throw new AssertionError("Chunk corpus header");
            int count=in.readInt();var result=new ArrayList<CompoundTag>(count);
            for(int i=0;i<count;i++) {
                in.readInt();in.readInt();byte[] bytes=in.readNBytes(in.readInt());
                result.add(NbtIo.read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes))),NbtAccounter.unlimitedHeap()));
            }
            return result;
        }
    }
    static SerializableChunkData load(RegionFileStorage storage,ChunkPos pos) throws Throwable {
        if(readTape==null) return SerializableChunkData.parse(HEIGHT,factory,storage.read(pos));
        Object input;
        if(readOwnedTape!=null) {
            Object tape=(Object)readOwnedTape.invokeExact(storage,pos);
            input=(Object)ownedLoaded.invokeExact(tape);
        } else {
            byte[] tape=(byte[])readTape.invokeExact(storage,pos);
            input=(Object)loaded.invokeExact(tape);
        }
        return (SerializableChunkData)parseLoaded.invokeExact(HEIGHT,factory,input,UPGRADE);
    }
    static Object member(Object object,String name) throws Exception {
        var method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);
    }
    static <T> void container(DataOutputStream out,PalettedContainerRO<T> input,Strategy<T> strategy) throws Exception {
        Object data;
        try {
            var method=PalettedContainer.class.getDeclaredMethod("dataForNativeScan");method.setAccessible(true);data=method.invoke(input);
        } catch(NoSuchMethodException frozen) {
            var field=PalettedContainer.class.getDeclaredField("data");field.setAccessible(true);data=field.get(input);
        }
        var storage=(net.minecraft.util.BitStorage)member(data,"storage");
        var config=(Configuration)member(data,"configuration");
        @SuppressWarnings("unchecked") var palette=(Palette<T>)member(data,"palette");
        out.writeInt(storage.getBits());out.writeInt(config.bitsInStorage());
        boolean global=palette==strategy.globalPalette();out.writeBoolean(global);out.writeInt(global?0:palette.getSize());
        if(!global) for(int i=0;i<palette.getSize();i++) out.writeInt(strategy.globalMap().getId(palette.valueFor(i)));
        long[] words=storage.getRaw();out.writeInt(words.length);for(long word:words) out.writeLong(word);
        int axis=strategy.entryCount()==64?2:4,mask=(1<<axis)-1;
        for(int i=0;i<strategy.entryCount();i++) out.writeInt(strategy.globalMap().getId(input.get(i&mask,i>>(axis*2),(i>>axis)&mask)));
    }
    static byte[] fingerprint(SerializableChunkData data) throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeLong(data.chunkPos().toLong());out.writeUTF(data.chunkStatus().toString());out.writeInt(data.sectionData().size());
        for(var row:data.sectionData()) {
            out.writeInt(row.y());out.writeBoolean(row.chunkSection()!=null);
            if(row.chunkSection()!=null) {
                container(out,row.chunkSection().getStates(),factory.blockStatesStrategy());
                container(out,row.chunkSection().getBiomes(),factory.biomeStrategy());
                out.writeBoolean(row.chunkSection().hasOnlyAir());out.writeBoolean(row.chunkSection().isRandomlyTicking());
            }
            for(var layer:new DataLayer[]{row.blockLight(),row.skyLight()}) {
                out.writeBoolean(layer!=null);
                if(layer!=null) {out.writeBoolean(layer.isEmpty());for(int i=0;i<4096;i++) out.writeByte(layer.get(i&15,i>>8,(i>>4)&15));}
            }
        }
        NbtIo.write(data.write(),out);out.flush();return bytes.toByteArray();
    }
    private record Sample(long elapsed,long checksum,long compilation) {}
    static Sample measure(RegionFileStorage storage,List<ChunkPos> positions,int repeats) throws Throwable {
        var compiler=java.lang.management.ManagementFactory.getCompilationMXBean();long compilation=compiler.getTotalCompilationTime();
        long checksum=0,start=System.nanoTime();
        for(int repeat=0;repeat<repeats;repeat++) for(var pos:positions) {
            var data=load(storage,pos);checksum=checksum*31+data.chunkPos().toLong()+data.sectionData().size();
            for(var row:data.sectionData()) if(row.chunkSection()!=null) checksum+=Block.getId(row.chunkSection().getBlockState(7,7,7));
        }
        long elapsed=System.nanoTime()-start;
        return new Sample(elapsed,checksum,compiler.getTotalCompilationTime()-compilation);
    }
    public static void main(String[] args) throws Throwable {
        setup();
        System.out.println("LOAD_ROUTE " + (readOwnedTape != null ? "rust-owned-tape" : readTape != null ? "rust-array-tape" : "frozen-tag"));
        for (var type : new Class<?>[]{SerializableChunkData.class, RegionFileStorage.class, PalettedContainer.class, DataLayer.class}) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var bytes = type.getResourceAsStream(resource)) {
                System.out.println("CLASS_IMAGE " + type.getName() + " " + type.getResource(resource) + " "
                    + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())));
            }
        }
        var path=Path.of(args[1]);Files.createDirectories(path);
        try(var storage=new RegionFileStorage(new RegionStorageInfo("decoded-owner-verification",Level.OVERWORLD,"chunk"),path,false)) {
            var positions=new ArrayList<ChunkPos>();
            for(var tag:corpus()) {var pos=new ChunkPos(tag.getIntOr("xPos",0),tag.getIntOr("zPos",0));storage.write(pos,tag);positions.add(pos);}
            if(args[0].equals("record")) {
                try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(Path.of(args[2]))))) {
                    out.writeInt(0x44434f31);out.writeInt(positions.size());
                    for(var pos:positions) {var result=fingerprint(load(storage,pos));out.writeLong(pos.toLong());out.writeInt(result.length);out.write(result);}
                }
            } else if(args[0].equals("verify")) {
                try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[2]))))) {
                    if(in.readInt()!=0x44434f31||in.readInt()!=positions.size()) throw new AssertionError("Oracle header");
                    for(var pos:positions) {
                        if(in.readLong()!=pos.toLong()) throw new AssertionError("Input position");
                        var expected=in.readNBytes(in.readInt());var actual=fingerprint(load(storage,pos));
                        int mismatch=Arrays.mismatch(expected,actual);if(mismatch!=-1) throw new AssertionError("Frozen chunk mismatch "+pos+" byte "+mismatch);
                    }
                    if(in.read()!=-1) throw new AssertionError("Trailing oracle data");
                }
                System.out.println("PARITY actual Frozen saved chunks="+positions.size());
            } else if(args[0].equals("bench")) {
                for(int i=0;i<200;i++) measure(storage,positions,1);
                var samples = new Sample[8];
                for (int i=0;i<samples.length;i++) samples[i]=measure(storage,positions,1);
                for (var sample : samples) System.out.println("TIMING "+sample.elapsed+" "+sample.checksum+" "+sample.compilation);
            } else if (args[0].equals("profile")) {
                for (int i=0;i<200;i++) measure(storage,positions,1);
                try (var recording = new jdk.jfr.Recording()) {
                    recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1)).withStackTrace();
                    recording.enable("jdk.NativeMethodSample").withPeriod(java.time.Duration.ofMillis(1)).withStackTrace();
                    recording.enable("jdk.ObjectAllocationSample").withStackTrace();
                    recording.enable("jdk.GarbageCollection");
                    recording.start();
                    var sample=measure(storage,positions,1000);
                    recording.stop();recording.dump(Path.of(args[2]));
                    System.out.println("PROFILE_DONE "+sample.elapsed+" "+sample.checksum+" "+sample.compilation);
                }
            }
        } finally {
            resources.close();
            for (String line : Files.readAllLines(Path.of("/proc/self/maps"))) if (line.contains("mattmc_rust")) {
                System.out.println("NATIVE_IMAGE " + line.substring(line.indexOf('/')));
                break;
            }
        }
    }
}
