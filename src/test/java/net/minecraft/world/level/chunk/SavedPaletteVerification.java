package net.minecraft.world.level.chunk;

import com.mojang.serialization.Lifecycle;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Runs the actual public saved-palette caller on either untouched Frozen or Current. */
public final class SavedPaletteVerification {
    static final int[] BLOCK_COUNTS = {1,2,3,4,8,16,17,31,32,33,64,65,128,129,256,257,512,513,1024,4096,16385,32769,65536};
    static final int[] BIOME_COUNTS = {1,2,3,4,5,8,9,16,17,64,65,257,513};
    static Strategy<BlockState> blocks;
    static Strategy<Holder<Biome>> biomes;
    static boolean ready;
    static void setup() throws Exception {
        if (ready) return;
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        blocks = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var registry = new MappedRegistry<Biome>(Registries.BIOME, Lifecycle.stable());
        for (int i=0;i<512;i++) {
            var biome = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.5F).downfall(.5F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(i).waterColor(0).waterFogColor(0).skyColor(i).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
            Registry.register(registry, ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("saved_palette_test_"+i)), biome);
        }
        registry.freeze(); biomes = Strategy.createForBiomes(registry.asHolderIdMap());
        try {
            var bridge=Class.forName("net.minecraft.world.level.chunk.NativeLiveBiomeSection");
            var register=bridge.getDeclaredMethod("register",Strategy.class,Registry.class); register.setAccessible(true);
            register.invoke(null,biomes,registry);
        } catch (ClassNotFoundException frozen) { /* Frozen has no native live owner. */ }
        ready=true;
    }
    record Input<T>(Strategy<T> strategy, List<T> palette, long[] words, int bits, String label) {
        PalettedContainer<T> load() {
            return PalettedContainer.unpack(strategy,new PalettedContainerRO.PackedData<>(palette,
                bits==0?Optional.empty():Optional.of(Arrays.stream(words.clone())),bits)).getOrThrow();
        }
    }
    static <T> Input<T> input(Strategy<T> strategy,int count,int seed,boolean aliases) {
        int bits=strategy.getConfigurationForPaletteSize(count).bitsInStorage();
        var palette=new ArrayList<T>(count);
        for(int i=0;i<count;i++) palette.add(strategy.globalMap().byId((aliases?i/2:i)%strategy.globalMap().size()));
        long[] words=bits==0?new long[0]:new long[(strategy.entryCount()+64/bits-1)/(64/bits)];
        var random=new Random(seed);
        Arrays.fill(words,-1L); // Include saved padding and unused palette slots.
        if(bits!=0) {
            var storage=new SimpleBitStorage(bits,strategy.entryCount(),words);
            for(int i=0;i<strategy.entryCount();i++) storage.set(i,random.nextInt(count));
        }
        return new Input<>(strategy,palette,words,bits,strategy.entryCount()+"/"+count+"/"+seed+"/"+aliases);
    }
    static List<Input<?>> cases() {
        var inputs=new ArrayList<Input<?>>();
        for(int n:BLOCK_COUNTS) for(int seed:new int[]{31,903}) inputs.add(input(blocks,n,seed,false));
        for(int n:BIOME_COUNTS) for(int seed:new int[]{31,903}) inputs.add(input(biomes,n,seed,false));
        for(int n:new int[]{2,16,17,256,257,513}) inputs.add(input(blocks,n,37,true));
        for(int n:new int[]{2,8,9,513}) inputs.add(input(biomes,n,37,true));
        return inputs;
    }
    @SuppressWarnings("unchecked")
    static <T> PalettedContainer.Data<T> data(PalettedContainer<T> owner) throws Exception {
        try {
            var read=PalettedContainer.class.getDeclaredMethod("dataForNativeScan"); read.setAccessible(true);
            return (PalettedContainer.Data<T>)read.invoke(owner);
        } catch(NoSuchMethodException frozen) {
            var field=PalettedContainer.class.getDeclaredField("data");field.setAccessible(true);
            return (PalettedContainer.Data<T>)field.get(owner);
        }
    }
    static void words(DataOutputStream out,long[] words) throws IOException {
        out.writeInt(words.length);for(long word:words) out.writeLong(word);
    }
    static <T> void snapshot(DataOutputStream out,PalettedContainer<T> owner,Strategy<T> strategy) throws Exception {
        var data=data(owner); out.writeInt(data.storage().getBits());out.writeInt(data.configuration().bitsInStorage());
        boolean global=data.palette()==strategy.globalPalette();out.writeBoolean(global);
        out.writeInt(global?0:data.palette().getSize());
        if(!global) for(int i=0;i<data.palette().getSize();i++) out.writeInt(strategy.globalMap().getId(data.palette().valueFor(i)));
        words(out,data.storage().getRaw());
        int axis=strategy.entryCount()==64?2:4,mask=(1<<axis)-1;
        for(int i=0;i<strategy.entryCount();i++) out.writeInt(strategy.globalMap().getId(owner.get(i&mask,i>>(axis*2),(i>>axis)&mask)));
        var packed=owner.pack(strategy); out.writeInt(packed.bitsPerEntry());out.writeInt(packed.paletteEntries().size());
        for(var value:packed.paletteEntries()) out.writeInt(strategy.globalMap().getId(value));
        words(out,packed.storage().map(LongStream::toArray).orElseGet(()->new long[0]));
    }
    static <T> byte[] outcome(Input<T> input) throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        var owner=input.load();snapshot(out,owner,input.strategy);
        var copy=owner.copy();copy.set(0,0,0,input.strategy.globalMap().byId(511));
        snapshot(out,owner,input.strategy);snapshot(out,copy,input.strategy);
        out.flush();return bytes.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        setup();
        if(Files.isRegularFile(Path.of("/proc/self/maps"))) {
            for(String line:Files.readAllLines(Path.of("/proc/self/maps"))) {
                if(line.contains("mattmc_rust-linux-x64.so")) {System.out.println("NATIVE_IMAGE "+line.substring(line.indexOf('/')));break;}
            }
        }
        if(args[0].equals("record")) {
            try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(Path.of(args[1]))))) {
                var inputs=cases();out.writeInt(0x53505631);out.writeInt(inputs.size());
                for(var input:inputs) {var result=outcome(input);out.writeUTF(input.label);out.writeInt(result.length);out.write(result);}
                System.out.println("Saved palette oracle: "+inputs.size()+" actual caller cases");
            }
        } else if(args[0].equals("verify")) {
            try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[1]))))) {
                var inputs=cases();if(in.readInt()!=0x53505631||in.readInt()!=inputs.size()) throw new AssertionError("Oracle header");
                for(var input:inputs) {
                    if(!input.label.equals(in.readUTF())) throw new AssertionError("Oracle input");
                    var expected=in.readNBytes(in.readInt());var actual=outcome(input);
                    int mismatch=Arrays.mismatch(expected,actual);
                    if(mismatch!=-1) throw new AssertionError("Frozen mismatch "+input.label+" byte "+mismatch);
                }
                if(in.read()!=-1) throw new AssertionError("Oracle trailing data");
                System.out.println("Saved palette parity: "+inputs.size()+" actual caller cases");
            }
        } else if(args[0].equals("bench")) {
            var input=args[1].equals("blocks")?input(blocks,Integer.parseInt(args[2]),31,false):input(biomes,Integer.parseInt(args[2]),31,false);
            int iterations=Integer.parseInt(args[3]);long checksum=0;
            int warmups=args.length>4?Integer.parseInt(args[4]):25;
            for(int warm=0;warm<warmups;warm++) checksum^=measure(input,iterations).checksum;
            for(int round=0;round<8;round++) {
                var sample=measure(input,iterations);checksum^=sample.checksum;
                System.out.println("TIMING "+sample.elapsed+" "+sample.checksum+" "+sample.compilation);
            }
            System.out.println("checksum="+checksum);
        }
    }
    private record Measurement(long elapsed,long checksum,long compilation) {}
    static <T> Measurement measure(Input<T> input,int iterations) {
        var compiler=java.lang.management.ManagementFactory.getCompilationMXBean();
        long compilation=compiler.getTotalCompilationTime();
        long checksum=0,start=System.nanoTime();
        for(int i=0;i<iterations;i++) {
            var owner=input.load();
            int axis=input.strategy.entryCount()==64?2:4,mask=(1<<axis)-1;
            for(int index=0;index<input.strategy.entryCount();index+=17)
                checksum+=input.strategy.globalMap().getId(owner.get(index&mask,index>>(axis*2),(index>>axis)&mask));
        }
        long elapsed=System.nanoTime()-start;
        return new Measurement(elapsed,checksum,compiler.getTotalCompilationTime()-compilation);
    }
}
