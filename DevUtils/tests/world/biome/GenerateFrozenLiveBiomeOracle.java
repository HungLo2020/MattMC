import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.*;

/** Record actual unchanged Frozen live biome palette behavior, including aliases. */
public final class GenerateFrozenLiveBiomeOracle {
    static final IdMapper<String> IDS = new IdMapper<>();
    static final String[] VALUES = new String[512];
    static Field DATA;
    static void verify(String name,String expected) throws Exception {
        try (InputStream stream=GenerateFrozenLiveBiomeOracle.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(stream==null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(expected))
                throw new IllegalStateException("Unexpected Frozen class: "+name);
        }
    }
    @SuppressWarnings("unchecked")
    static void snapshot(DataOutputStream out, PalettedContainer<String> container) throws Exception {
        var data=(PalettedContainer.Data<String>)DATA.get(container);
        boolean global=data.palette() instanceof GlobalPalette;
        out.writeInt(data.storage().getBits());out.writeInt(data.configuration().bitsInStorage());out.writeBoolean(global);
        int count=global?0:data.palette().getSize();out.writeInt(count);
        for(int i=0;i<count;i++)out.writeInt(IDS.getId(data.palette().valueFor(i)));
        long[] words=data.storage().getRaw();out.writeInt(words.length);for(long word:words)out.writeLong(word);
        for(int i=0;i<64;i++)out.writeInt(IDS.getId(container.get(i&3,i>>4,(i>>2)&3)));
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        verify("net/minecraft/world/level/chunk/PalettedContainer", "becf57aaabbd4376957e8ff2d0aee31c8b4333147ef45df3388378cfef502821");
        verify("net/minecraft/world/level/chunk/Strategy", "08346310a0d1c77eafa99525850ecee130291d1fb20d06d55eb13d004235302b");
        verify("net/minecraft/world/level/chunk/SingleValuePalette", "9aab0a0366c194794de9cf640d235f2c48de6045b8c95f10f04f04ba1ae0c222");
        verify("net/minecraft/world/level/chunk/LinearPalette", "6b83f0996ac9d16178f86340f7067adc84266550c27ab22ca69e98571664a4ca");
        verify("net/minecraft/world/level/chunk/GlobalPalette", "8f703f4247d7f1a4258eae82b9bbb041aba89f834a4711291d6028d945657194");
        verify("net/minecraft/util/SimpleBitStorage", "1586df6a3fa53b8de403ea141dc686be9bb1d5c110b361df2828f59094049124");
        // Construct the strategy after populating the registry: it captures global width.
        for(int i=0;i<VALUES.length;i++){VALUES[i]=new String("biome-"+i);IDS.add(VALUES[i]);}
        var strategy=Strategy.createForBiomes(IDS);
        DATA=PalettedContainer.class.getDeclaredField("data");DATA.setAccessible(true);
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x42494d31);out.writeInt(16);
            for(int scenario=0;scenario<16;scenario++) {
                PalettedContainer<String>[] owners=(PalettedContainer<String>[])new PalettedContainer<?>[4];
                // A fresh registry-width-correct import strategy is supplied to the constructor below.
                owners[0]=new PalettedContainer<>(VALUES[0],strategy);
                int requested=scenario%8;
                if(requested>0) {
                    FriendlyByteBuf input=new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        int bits=requested<=3?requested:9;input.writeByte(requested);
                        if(requested<=3){input.writeVarInt(1<<requested);for(int i=0;i<1<<requested;i++)input.writeVarInt(i);}
                        int per=64/bits;Random random=new Random(scenario+1);
                        for(int word=0;word<(64+per-1)/per;word++) {
                            long value=random.nextLong();
                            for(int slot=0;slot<per && word*per+slot<64;slot++) {
                                long mask=((1L<<bits)-1)<<(slot*bits);
                                int id=requested<=3?(word*per+slot)%((1<<bits)-1):((word*per+slot)*7+scenario)&511;
                                value=(value&~mask)|((long)id<<(slot*bits));
                            }
                            input.writeLong(value);
                        }
                        owners[0].read(input);
                    } finally {input.release();}
                }
                out.writeInt(512);out.writeInt(9);snapshot(out,owners[0]);
                int operations=100;out.writeInt(operations);
                for(int step=0;step<operations;step++) {
                    int kind=0,slot=0,index=(step*13+scenario)&63,value=(step*17+scenario)&511,old=-1;
                    if(step==0){kind=1;index=0;slot=1;owners[1]=owners[0].copy();}
                    else if(step==1 && requested==0){kind=2;slot=1;value=3;FriendlyByteBuf input=new FriendlyByteBuf(Unpooled.buffer());try{input.writeByte(0);input.writeVarInt(value);owners[1].read(input);}finally{input.release();}}
                    else if(step==5){kind=1;index=0;slot=2;owners[2]=owners[0].copy();}
                    else if(step==9){kind=1;index=1;slot=3;owners[3]=owners[1].copy();}
                    else {slot=step%4;if(owners[slot]==null)slot=0;old=IDS.getId(owners[slot].getAndSetUnchecked(index&3,index>>4,(index>>2)&3,VALUES[value]));}
                    out.writeInt(kind);out.writeInt(slot);out.writeInt(index);out.writeInt(value);out.writeInt(old);
                    int mask=0;for(int i=0;i<4;i++)if(owners[i]!=null)mask|=1<<i;out.writeInt(mask);
                    for(var owner:owners)if(owner!=null)snapshot(out,owner);
                }
            }
        }
        System.out.println("Recorded actual Frozen biome imports/padding/growth/copy/shared-single traces: 16 scenarios, 1600 operations");
    }
}
