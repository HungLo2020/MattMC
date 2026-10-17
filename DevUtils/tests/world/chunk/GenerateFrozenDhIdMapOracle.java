import com.google.gson.JsonParser;
import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.util.objects.dataStreams.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Records untouched Frozen map policy over canonical saved-corpus serial identities. */
public class GenerateFrozenDhIdMapOracle {
    static String hash(Class<?> type) throws Exception {
        try(var in=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    static Object wrapper(Class<?> type, String serial) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()) {
            case "getSerialString", "toString" -> serial;
            case "hashCode" -> Objects.hash(serial);
            case "equals" -> p==a[0];
            default -> throw new UnsupportedOperationException("Unexpected semantic fixture callback "+m);
        });
    }
    static void state(DataOutputStream out, FullDataPointIdMap map) throws Exception {
        out.writeLong(map.getPos());out.writeInt(map.hashCode());out.writeInt(map.size());
        for(int i=0;i<map.size();i++) { out.writeUTF(map.getBiomeWrapper(i).getSerialString());out.writeUTF(map.getBlockStateWrapper(i).getSerialString()); }
    }
    public static void main(String[] args) throws Exception {
        var pins=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var identity=hash(FullDataPointIdMap.class);
        if(!identity.equals(pins.get(FullDataPointIdMap.class.getName()).getAsString()))throw new IllegalStateException("Not pinned Frozen map");
        SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class,(IMinecraftSharedWrapper)Proxy.newProxyInstance(IMinecraftSharedWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftSharedWrapper.class},
            (p,m,a)->switch(m.getName()) {case "getInstallationDirectory" -> Path.of("run").toFile();case "isDedicatedServer" -> false;case "getDelayedSetupComplete" -> true;default -> throw new UnsupportedOperationException(m.toString());}));
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class,(IWrapperFactory)Proxy.newProxyInstance(IWrapperFactory.class.getClassLoader(),new Class<?>[]{IWrapperFactory.class},
            (p,m,a)->switch(m.getName()) {case "getDelayedSetupComplete" -> true;case "deserializeBiomeWrapperOrGetDefault" -> wrapper(IBiomeWrapper.class,(String)a[0]);case "deserializeBlockStateWrapperOrGetDefault" -> wrapper(IBlockStateWrapper.class,(String)a[0]);default -> throw new UnsupportedOperationException(m.toString());}));
        var bank=new ArrayList<String[]>();
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[2]))))) {
            if(in.readInt()!=0x44484331)throw new IllegalStateException("Not DHC1");
            for(int n=in.readInt();n>0;n--){in.readUTF();in.readUTF();}in.readUTF();in.readUTF();
            var biomes=new ArrayList<String>(); for(int n=in.readInt();n>0;n--){var s=in.readUTF();int h=in.readInt();if(h!=Objects.hash(s))throw new IllegalStateException("Biome hash policy");biomes.add(s);}
            var states=new ArrayList<String>();for(int n=in.readInt();n>0;n--){var s=in.readUTF();int h=in.readInt();if(h!=Objects.hash(s))throw new IllegalStateException("State hash policy");states.add(s);}
            for(int i=0;i<64;i++)bank.add(new String[]{biomes.get(i%biomes.size()),states.get(i*17%states.size())});
        }
        var maps=new FullDataPointIdMap[]{new FullDataPointIdMap(6),new FullDataPointIdMap(-1),new FullDataPointIdMap(0x800000007fffffffL)};
        var random=new Random(0x49444d31);int steps=300;
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x44484931);out.writeUTF(identity);out.writeUTF(hash(BlockBiomeWrapperPair.class));
            out.writeUTF("Actual Frozen ordered map operations; stock serial/hash facts via strict immutable CPU wrappers. Supplemental CPU proof, not gameplay.");
            out.writeInt(bank.size());for(var pair:bank){out.writeUTF(pair[0]);out.writeUTF(pair[1]);}out.writeInt(steps);
            for(int step=0;step<steps;step++) {
                int op=step<12?new int[]{0,0,2,4,0,1,2,1,3,0,1,4}[step]:random.nextInt(5), dst=random.nextInt(3), src=random.nextInt(3), entry=random.nextInt(bank.size());
                if(op==2 && src==dst)src=(src+1)%3;
                if(maps[dst].size()+maps[src].size()>1024)op=3;
                long pos=random.nextLong(); out.writeByte(op);out.writeByte(dst);out.writeByte(src);out.writeInt(entry);out.writeLong(pos);
                int[] ids=new int[0];String exception="",message="";
                try { switch(op) {
                    case 0 -> {var p=bank.get(entry);ids=new int[]{maps[dst].addIfNotPresentAndGetId((IBiomeWrapper)wrapper(IBiomeWrapper.class,p[0]),(IBlockStateWrapper)wrapper(IBlockStateWrapper.class,p[1]))};}
                    case 1 -> ids=maps[dst].mergeAndReturnRemappedEntityIds(maps[src]);
                    case 2 -> maps[dst].addAll(maps[src]);
                    case 3 -> maps[dst].clear(pos);
                    case 4 -> {var bytes=new ByteArrayList();try(var stream=DhDataOutputStream.create(EDhApiDataCompressionMode.UNCOMPRESSED,bytes)){maps[dst].serialize(stream);}try(var stream=DhDataInputStream.create(bytes,EDhApiDataCompressionMode.UNCOMPRESSED)){maps[dst]=FullDataPointIdMap.deserialize(stream,maps[dst].getPos(),null);}}
                } } catch(ArrayIndexOutOfBoundsException e) { exception=e.getClass().getSimpleName();message=e.getMessage(); }
                out.writeUTF(exception);out.writeUTF(message);
                out.writeInt(ids.length);for(int id:ids)out.writeInt(id);for(var map:maps)state(out,map);
            }
        }
        System.out.println("Recorded300 actual Frozen map operations across three maps; imports/duplicates/self-merge/clear/hash.");
    }
}
