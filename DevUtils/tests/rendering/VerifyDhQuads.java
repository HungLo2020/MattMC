import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.*;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Untouched Frozen recorder and identical compiled CPU workload for both trees. */
public final class VerifyDhQuads {
    private static final EDhDirection[] DIRECTIONS = EDhDirection.values();
    private static Field nativeField;
    private static Method snapshot,close,put,packedVertexBuffers;
    private static Method nativeColumn,nativeColumnBytes;
    private static final Method[] packets = new Method[4];
    private static boolean encode;
    private static boolean grass;
    private static boolean columnExtent;
    private static final IClientLevelWrapper LEVEL = (IClientLevelWrapper)Proxy.newProxyInstance(
        IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{
            if(m.getName().equals("getDirtBlockColor"))return 0xff987654;
            if(m.getReturnType()==boolean.class)return false;
            if(m.getReturnType()==int.class)return 0;
            return null;
        });
    private static final Field[] legacy = new Field[2];
    private static void setup() throws Exception {
        var render = (IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),
            new Class<?>[]{IMinecraftRenderWrapper.class}, (p,m,a) -> {
                if(m.getName().equals("getShade")) return 1f;
                if(m.getName().equals("getDelayedSetupComplete")) return true;
                if(m.getReturnType()==boolean.class) return false;
                if(m.getReturnType()==int.class) return 0;
                return null;
            });
        SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class,render);
        Class<?> type=LodQuadBuilder.class;
        for(int i=0;i<2;i++){legacy[i]=type.getDeclaredField(i==0?"opaqueQuads":"transparentQuads");legacy[i].setAccessible(true);}
        try { nativeField=type.getDeclaredField("nativeQuads");nativeField.setAccessible(true);
            snapshot=nativeField.getType().getDeclaredMethod("snapshot",int.class,int.class);snapshot.setAccessible(true);
            close=type.getMethod("close");
            packedVertexBuffers=Class.forName(type.getName()+"$SemanticVertexBufferBuild").getMethod("packedVertexBuffers");
        } catch(NoSuchFieldException ignored) {}
        if(nativeField!=null)try {
            nativeColumn=type.getMethod("makeNativeRustSemanticColumn");
            nativeColumnBytes=nativeColumn.getReturnType().getMethod("byteSize");
        }catch(NoSuchMethodException ignored){}
        put=type.getDeclaredMethod("putQuad",ByteBuffer.class,BufferQuad.class);put.setAccessible(true);
        String[] layers={"Opaque","Transparent","TransparentUp","TransparentWaterUp"};
        for(int i=0;i<layers.length;i++)packets[i]=type.getMethod("make"+layers[i]+(nativeField==null?"VertexBuffers":"RustSemanticBuffers"));
    }
    @SuppressWarnings("unchecked")
    private static List<BufferQuad> rows(LodQuadBuilder builder,int layer,int direction) throws Exception {
        if(nativeField!=null) return (List<BufferQuad>)snapshot.invoke(nativeField.get(builder),layer,direction);
        return ((List<BufferQuad>[])legacy[layer].get(builder))[direction];
    }
    private static void release(LodQuadBuilder b) throws Exception { if(close!=null)close.invoke(b); }
    private static short[][] input(int seed,int size) {
        Random r=new Random(seed);short[][] result=new short[size][9];
        for(int i=0;i<size;i++) {
            int direction=i%6;int pattern=seed%4;
            int x=pattern==0?(i/6)%32:pattern==1?((i/6)%4)*2:r.nextInt(64)-32;
            int y=pattern<2?((i/6)/32)*2:r.nextInt(16)-8;
            int z=pattern<2?1:r.nextInt(64)-32;
            if(pattern==0) { int step=i/6; x=direction>=4?1:step%32; y=direction<2?8:step/32;
                z=direction>=4?step%32:direction<2?step/32:1; }
            result[i]=new short[]{(short)direction,(short)x,(short)y,(short)z,
                (short)(seed%11==0?2048:seed%13==0?32767:1+r.nextInt(pattern<2?1:8)),
                (short)(1+r.nextInt(pattern<2?1:8)),(short)(i%3==0?12:2),(short)(seed%7==0?128:255),(short)(pattern==0?0:i%5)};
        }
        return result;
    }
    private static void add(LodQuadBuilder b,short[] q) {
        var dir=DIRECTIONS[q[0]];int color=(q[7]<<24)|0x336699;
        if(dir==EDhDirection.UP)b.addQuadUp(q[1],q[2],q[3],q[4],q[5],color,(byte)q[6],(byte)15,(byte)q[8]);
        else if(dir==EDhDirection.DOWN)b.addQuadDown(q[1],q[2],q[3],q[4],q[5],color,(byte)q[6],(byte)15,(byte)q[8]);
        else b.addQuadAdj(dir,q[1],q[2],q[3],q[4],q[5],color,(byte)q[6],(byte)15,(byte)q[8]);
    }
    private static void writeQuad(DataOutputStream out,LodQuadBuilder b,BufferQuad q) throws Exception {
        out.writeShort(q.x);out.writeShort(q.y);out.writeShort(q.z);out.writeShort(q.widthEastWest);out.writeShort(q.widthNorthSouthOrUpDown);
        out.writeInt(q.color);out.writeByte(q.irisBlockMaterialId);out.writeByte(q.skyLight);out.writeByte(q.blockLight);
        out.writeByte(q.direction.ordinal());out.writeBoolean(q.hasError);
        ByteBuffer bytes=ByteBuffer.allocate(64).order(ByteOrder.nativeOrder());put.invoke(b,bytes,q);out.write(bytes.array());
    }
    private static String sha(Class<?> c) throws Exception {
        try(var in=c.getResourceAsStream(c.getSimpleName()+".class")){
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    private static void record(Path output) throws Exception {
        if(nativeField!=null)throw new IllegalStateException("Recorder must run on Frozen");
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(output)))) {
            out.writeInt(0x44485131);out.writeUTF("Actual Frozen DH quad add/premerge/greedy/vertex output; synthetic CPU only");
            Class<?>[] pins={LodQuadBuilder.class,BufferQuad.class,EDhDirection.class,Config.class};
            out.writeInt(pins.length);for(var c:pins){out.writeUTF(c.getName());out.writeUTF(sha(c));}
            out.writeInt(512);
            for(int seed=0;seed<512;seed++){
                boolean trans=(seed&1)!=0,curve=(seed&2)!=0,errors=(seed&4)!=0,merge=(seed&8)!=0;
                Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(curve?100:0);
                Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(errors);
                var input=input(seed,seed%16==0?1024:seed%128);
                out.writeBoolean(trans);out.writeBoolean(curve);out.writeBoolean(errors);out.writeBoolean(merge);
                out.writeInt(input.length);for(var q:input)for(short v:q)out.writeShort(v);
                var b=new LodQuadBuilder(trans,null);for(var q:input)add(b,q);if(merge){b.mergeQuads();b.mergeQuads();}
                for(int layer=0;layer<2;layer++)for(int dir=0;dir<6;dir++){
                    var values=rows(b,layer,dir);out.writeInt(values.size());for(var q:values)writeQuad(out,b,q);
                }
                release(b);
            }
        }
        System.out.println("Frozen quad cases=512; fixture="+output);
    }
    private static long run(short[][] values,int rounds) throws Exception {
        long sum=0;
        for(int i=0;i<rounds;i++){
            var b=new LodQuadBuilder(true,LEVEL);for(var q:values)add(b,q);b.mergeQuads();
            sum+=b.getCurrentOpaqueQuadsCount()*31L+b.getCurrentTransparentQuadsCount();
            if(columnExtent&&nativeColumn!=null){
                Object value=nativeColumn.invoke(b);
                try {sum+=(long)nativeColumnBytes.invoke(value);}finally{((AutoCloseable)value).close();}
            }else if(encode)for(var method:packets){
                Object result=method.invoke(b);
                if(nativeField==null){
                    for(Object value:(List<?>)result){var bytes=(ByteBuffer)value;
                        if(columnExtent)sum+=bytes.remaining();
                        else for(int j=0;j<bytes.remaining();j++)sum+=Byte.toUnsignedInt(bytes.get(bytes.position()+j));
                        org.lwjgl.system.MemoryUtil.memFree(bytes);
                    }
                }else{
                    var buffers=(List<?>)packedVertexBuffers.invoke(result);
                    for(Object value:buffers){if(columnExtent)sum+=((byte[])value).length;else for(byte v:(byte[])value)sum+=Byte.toUnsignedInt(v);}
                }
            }
            release(b);
        }
        return sum;
    }
    public static void main(String[] args) throws Exception {
        setup();
        if(args[0].equals("record")){record(Path.of(args[1]));return;}
        columnExtent=args.length>5&&(args[5].equals("native-column")||args[5].equals("grass-native-column"));
        encode=columnExtent||args.length>5 && (args[5].equals("packets") || args[5].equals("grass-packets"));
        grass=args.length>5 && (args[5].equals("grass-packets")||args[5].equals("grass-native-column"));
        boolean current=args[1].equals("current");if(current!=(nativeField!=null))throw new IllegalStateException("Wrong tree");
        Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
        Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(false);
        Config.Client.Advanced.Debugging.debugRendering.setWithoutFiringEvents(EDhApiDebugRendering.OFF);
        Config.Client.Advanced.Graphics.Quality.grassSideRendering.setWithoutFiringEvents(EDhApiGrassSideRendering.FADE_TO_DIRT);
        short[][] values=input(Integer.parseInt(args[2]),Integer.parseInt(args[3]));int rounds=Integer.parseInt(args[4]);
        if(grass)for(var q:values)if(q[6]!=12)q[6]=13;
        for(int i=0;i<5;i++)run(values,rounds);
        long begin=System.nanoTime(),sum=run(values,rounds),elapsed=System.nanoTime()-begin;
        System.out.printf(Locale.ROOT,"RESULT tree=%s ns=%d checksum=%d quads=%d rounds=%d%n",args[1],elapsed,sum,values.length,rounds);
    }
}
