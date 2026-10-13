import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;

/** Actual production BiomeManager on both sides; no copied selection oracle. */
public final class VerifyBiomeQueries {
    static void image(String name) throws Exception {
        try (var in=ClassLoader.getSystemResourceAsStream(name.replace('.','/')+".class")) {
            if(in==null)throw new AssertionError(name);
            System.out.println("CLASS "+name+" "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
    }
    public static void main(String[] args) throws Exception {
        boolean frozen=args[0].equals("frozen");
        image("net.minecraft.world.level.biome.BiomeManager");
        image("net.minecraft.world.level.biome.BiomeManager$NoiseBiomeSource");
        image("net.minecraft.core.BlockPos");
        var file=Path.of(args[1]);
        DataOutputStream out=frozen?new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file))):null;
        DataInputStream in=frozen?null:new DataInputStream(new GZIPInputStream(Files.newInputStream(file)));
        int[] got=new int[4];
        BiomeManager.NoiseBiomeSource source=(x,y,z)->{got[0]=x;got[1]=y;got[2]=z;got[3]++;return null;};
        int[] edges={Integer.MIN_VALUE,Integer.MIN_VALUE+1,Integer.MIN_VALUE+2,-30000000,-17,-16,-3,-2,-1,0,1,2,3,15,16,17,30000000,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
        long[] seeds={0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x123456789abcdefL,-78482932};
        int count=0;
        try {
            for(long seed:seeds) {
                var manager=new BiomeManager(source,seed);var random=new Random(seed);
                for(int i=0;i<16384+edges.length*edges.length*edges.length;i++) {
                    int x,y,z;
                    if(i<16384){x=random.nextInt();y=random.nextInt();z=random.nextInt();}
                    else {int j=i-16384;x=edges[j%edges.length];y=edges[j/edges.length%edges.length];z=edges[j/edges.length/edges.length];}
                    got[3]=0;
                    if(manager.getBiome(new BlockPos(x,y,z))!=null || got[3]!=1)throw new AssertionError("Callback count");
                    for(int k=0;k<3;k++) {
                        if(frozen)out.writeInt(got[k]);
                        else if(in.readInt()!=got[k])throw new AssertionError("Coordinate mismatch at "+count+" axis "+k+" seed "+seed+" block "+x+","+y+","+z);
                    }
                    count++;
                }
                var failure=new IllegalStateException("source callback");
                var changed=manager.withDifferentSource((x,y,z)->{throw failure;});
                try {changed.getBiome(BlockPos.ZERO);throw new AssertionError("Missing source exception");}
                catch(IllegalStateException found){if(found!=failure)throw new AssertionError("Exception replaced");}
            }
            if(!frozen && in.read()!=-1)throw new AssertionError("Trailing oracle data");
        } finally {if(out!=null)out.close();if(in!=null)in.close();}
        System.out.println("BIOME_CASES "+count);
        if(!frozen) {
            image("net.minecraft.world.level.biome.NativeBiomeQuery");
            var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(!Files.readString(Path.of("/proc/self/maps")).contains(nativePath.toString()))throw new AssertionError("Wrong native image");
            System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(nativePath)))+" "+nativePath);
        }
    }
}
