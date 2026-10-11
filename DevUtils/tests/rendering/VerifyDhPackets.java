import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.*;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Execute untouched Frozen packet code; replay identical inputs through Current's public route. */
public final class VerifyDhPackets {
    private static final EDhDirection[] DIRECTIONS = EDhDirection.values();
    private static final float[] SHADES = {.3f,1f,2f,-1f,Float.NaN,Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,Float.MIN_VALUE,Float.MAX_VALUE};
    private static final int[] COLORS = {0x80123456,0xff00ff00,0x00000000,0xffffffff,0x12345678};
    private static final int[] FAILURES = {-1,0,1,3,11};
    private static final Method[] PACKETS = new Method[4];
    private static Method close, bytes;
    private static boolean current;
    private static final ArrayList<String> trace = new ArrayList<>();
    private static int dirt, shade, calls, fail;
    private static final class Injected extends RuntimeException {}
    private static void visit(String label) {
        trace.add(label);
        if (calls++ == fail) throw new Injected();
    }
    private static Object defaultValue(Class<?> type) {
        if(type==boolean.class)return false;
        if(type==int.class)return 0;
        if(type==long.class)return 0L;
        if(type==float.class)return 0f;
        if(type==double.class)return 0d;
        return null;
    }
    private static final IClientLevelWrapper LEVEL = (IClientLevelWrapper)Proxy.newProxyInstance(
        IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{
            if(m.getName().equals("getDirtBlockColor")){visit("dirt");return COLORS[dirt++%COLORS.length];}
            return defaultValue(m.getReturnType());
        });
    private static void setup() throws Exception {
        var render=(IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),
            new Class<?>[]{IMinecraftRenderWrapper.class},(p,m,a)->{
                if(m.getName().equals("getDelayedSetupComplete"))return true;
                if(m.getName().equals("getShade")){visit("shade:"+((EDhDirection)a[0]).ordinal());return SHADES[shade++%SHADES.length];}
                return defaultValue(m.getReturnType());
            });
        SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class,render);
        try {close=LodQuadBuilder.class.getMethod("close");current=true;
            bytes=Class.forName(LodQuadBuilder.class.getName()+"$SemanticVertexBufferBuild").getMethod("packedVertexBuffers");
        }catch(NoSuchMethodException ignored){}
        String[] layers={"Opaque","Transparent","TransparentUp","TransparentWaterUp"};
        for(int i=0;i<4;i++)PACKETS[i]=LodQuadBuilder.class.getMethod("make"+layers[i]+(current?"RustSemanticBuffers":"VertexBuffers"));
    }
    private record Face(short direction,short x,short y,short z,short width,short height,int color,byte material,byte sky,byte block) {
        void write(DataOutputStream out)throws IOException{
            for(short v:new short[]{direction,x,y,z,width,height})out.writeShort(v);
            out.writeInt(color);out.writeByte(material);out.writeByte(sky);out.writeByte(block);
        }
        static Face read(DataInputStream in)throws IOException{return new Face(in.readShort(),in.readShort(),in.readShort(),in.readShort(),in.readShort(),in.readShort(),in.readInt(),in.readByte(),in.readByte(),in.readByte());}
        void add(LodQuadBuilder b){
            if(direction==0)b.addQuadDown(x,y,z,width,height,color,material,sky,block);
            else if(direction==1)b.addQuadUp(x,y,z,width,height,color,material,sky,block);
            else b.addQuadAdj(DIRECTIONS[direction],x,y,z,width,height,color,material,sky,block);
        }
    }
    private static List<Face> faces(int seed){
        var result=new ArrayList<Face>();byte[] lights={-128,-1,0,1,15,16,127};
        for(int i=0;i<72;i++){
            int group=i/6;
            result.add(new Face((short)(i%6),(short)(seed==0?32760:seed==1?-32768:group/2*3),
                (short)(group/2*5),(short)(seed==2?-11:group/2*7),(short)(group%2==0?20:3),
                (short)(group%2==0?12:4),(group%4==0?0x80000000:0xff000000)|0x336699,
                (byte)(group/4==0?13:group/4==1?12:2),lights[group%7],lights[(group+3)%7]));
        }
        return result;
    }
    private record Result(byte[] vertices,List<String> callbacks,boolean failed){}
    private static Result run(LodQuadBuilder builder,int route,int failure)throws Exception{
        trace.clear();dirt=shade=calls=0;fail=failure;
        var flat=new ByteArrayOutputStream();boolean failed=false;
        try{
            Object value=PACKETS[route].invoke(builder);
            if(current){for(Object packet:(List<?>)bytes.invoke(value))flat.write((byte[])packet);}
            else for(Object packet:(List<?>)value){var b=(ByteBuffer)packet;byte[] data=new byte[b.remaining()];b.get(data);flat.write(data);org.lwjgl.system.MemoryUtil.memFree(b);}
        }catch(InvocationTargetException e){if(e.getCause() instanceof Injected)failed=true;else throw e;}
        return new Result(flat.toByteArray(),List.copyOf(trace),failed);
    }
    private static String hash(Class<?> c)throws Exception{
        try(var in=c.getResourceAsStream(c.getSimpleName()+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}
    }
    private static void configure(boolean debug,int grass,boolean errors){
        Config.Client.Advanced.Debugging.debugRendering.setWithoutFiringEvents(debug?EDhApiDebugRendering.SHOW_DETAIL:EDhApiDebugRendering.OFF);
        Config.Client.Advanced.Graphics.Quality.grassSideRendering.setWithoutFiringEvents(grass==3?null:EDhApiGrassSideRendering.values()[grass]);
        Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
        Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(errors);
    }
    private static void record(Path file)throws Exception{
        if(current)throw new IllegalStateException("Recorder must run on untouched Frozen");
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))){
            out.writeInt(0x44485031);out.writeBoolean(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN);
            Class<?>[] pins={LodQuadBuilder.class,BufferQuad.class,EDhDirection.class,Config.class,
                com.seibel.distanthorizons.core.util.ColorUtil.class,EDhApiGrassSideRendering.class,EDhApiDebugRendering.class};
            out.writeInt(pins.length);for(var c:pins){out.writeUTF(c.getName());out.writeUTF(hash(c));}
            out.writeInt(96);
            for(int c=0;c<96;c++){
                boolean trans=(c&1)!=0,debug=(c&2)!=0,errors=(c&4)!=0;int grass=c/8%4,seed=c/32;
                out.writeBoolean(trans);out.writeBoolean(debug);out.writeBoolean(errors);out.writeInt(grass);
                configure(debug,grass,errors);var faces=faces(seed);out.writeInt(faces.size());for(var f:faces)f.write(out);
                var builder=new LodQuadBuilder(trans,LEVEL);for(var f:faces)f.add(builder);builder.mergeQuads();
                for(int route=0;route<4;route++)for(int failure:FAILURES){
                    var r=run(builder,route,failure);out.writeBoolean(r.failed);out.writeInt(r.vertices.length);out.write(r.vertices);
                    out.writeInt(r.callbacks.size());for(String event:r.callbacks)out.writeUTF(event);
                }
            }
        }
        System.out.println("Actual Frozen packet cases=96, routes=4, failure settings=5");
    }
    private static void verify(Path file)throws Exception{
        if(!current)throw new IllegalStateException("Replay must use Current public native route");
        int calls=0;
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))){
            if(in.readInt()!=0x44485031)throw new IOException("Wrong format");boolean little=in.readBoolean();
            if(little!=(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN))throw new IOException("Fixture endianness differs");
            int pins=in.readInt();for(int i=0;i<pins;i++){in.readUTF();if(in.readUTF().length()!=64)throw new IOException("Invalid class pin");}
            int cases=in.readInt();if(cases!=96)throw new IOException("Wrong case count");
            for(int c=0;c<cases;c++){
                boolean trans=in.readBoolean(),debug=in.readBoolean(),errors=in.readBoolean();int grass=in.readInt();configure(debug,grass,errors);
                var builder=new LodQuadBuilder(trans,LEVEL);
                try{
                    int count=in.readInt();for(int i=0;i<count;i++)Face.read(in).add(builder);builder.mergeQuads();
                    for(int route=0;route<4;route++)for(int failure:FAILURES){
                        boolean failed=in.readBoolean();byte[] expected=in.readNBytes(in.readInt());int n=in.readInt();var events=new ArrayList<String>();for(int i=0;i<n;i++)events.add(in.readUTF());
                        var actual=run(builder,route,failure);
                        if(failed!=actual.failed||!Arrays.equals(expected,actual.vertices)||!events.equals(actual.callbacks))throw new AssertionError("case="+c+" route="+route+" failure="+failure+" bytes="+Arrays.equals(expected,actual.vertices)+" callbacks="+events.equals(actual.callbacks));
                        calls++;
                    }
                }finally{close.invoke(builder);}
            }
            if(in.read()!=-1)throw new IOException("Trailing fixture data");
        }
        System.out.println("PASS Current public packet route: "+calls+" actual Frozen outputs/callback prefixes");
    }
    public static void main(String[] args)throws Exception{
        setup();if(args[0].equals("record"))record(Path.of(args[1]));else if(args[0].equals("verify"))verify(Path.of(args[1]));else throw new IllegalArgumentException("record|verify PATH");
    }
}
