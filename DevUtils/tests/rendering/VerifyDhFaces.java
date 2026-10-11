import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.*;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pooling.*;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Actual untouched Frozen whole-box face recorder and identical compiled CPU workload. */
public final class VerifyDhFaces {
    static final List<Integer> EVENTS = new ArrayList<>();
    static int failure;
    static boolean productionFacts;
    static Method box, spanBox, snapshot, close;
    static List<?> semanticSpans=List.of();
    static Constructor<?> view;
    static Field nativeField;
    static final Field[] legacy = new Field[2];
    static PhantomArrayListCheckout checkout;
    static final float[] STANDARD_SHADES={.5f,1f,.8f,.8f,.6f,.6f};
    static final float[] SHADES = {.5f, 1f, .8f, .9f, .6f, .7f};
    static void setup() throws Exception {
        var interfaces=new ArrayList<Class<?>>();interfaces.add(IMinecraftRenderWrapper.class);
        try {interfaces.add(Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper$NativeShadeFacts"));}
        catch(ClassNotFoundException ignored){} // Untouched Frozen predates the CPU fact contract.
        var render = (IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),
            interfaces.toArray(Class<?>[]::new), (p,m,a)->{
                if (m.getName().equals("getDelayedSetupComplete")) return true;
                if (m.getName().equals("hasNativeShadeFacts")) return productionFacts;
                if (m.getName().equals("getNativeShadeFacts")) return 1; // ENABLED, immutable CPU shading policy
                if (m.getName().equals("getShade")) {
                    var d=(EDhDirection)a[0];EVENTS.add(d.ordinal());
                    if (failure != 0 && EVENTS.size() == failure) throw new IllegalStateException("injected shade failure");
                    return productionFacts ? STANDARD_SHADES[d.ordinal()] : SHADES[d.ordinal()];
                }
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==int.class)return 0;
                return null;
            });
        SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class,render);
        checkout=new PhantomArrayListCheckout(new PhantomArrayListPool("Frozen face recorder"));
        checkout.addLongArrayListRef(new LongArrayList());checkout.addLongArrayListRef(new LongArrayList());
        Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(false);
        Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
        Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(false);
        for (var m:ColumnBox.class.getMethods()) if(m.getName().equals("addBoxQuadsToBuilder")
            && (m.getParameterCount()==16 || m.getParameterCount()==19)) box=m;
        for(var m:ColumnBox.class.getMethods())if(m.getName().equals("addBoxQuadsToBuilder") && m.getParameterCount()==20)spanBox=m;
        if(box==null)throw new IllegalStateException("Missing whole-box entry");
        // Frozen uses LongArrayList here; Current admits LongList as well.
        for(var c:ColumnArrayView.class.getConstructors())if(c.getParameterCount()==4)view=c;
        if(view==null)throw new IllegalStateException("Missing column-view constructor");
        for(int i=0;i<2;i++){legacy[i]=LodQuadBuilder.class.getDeclaredField(i==0?"opaqueQuads":"transparentQuads");legacy[i].setAccessible(true);}
        try {
            nativeField=LodQuadBuilder.class.getDeclaredField("nativeQuads");nativeField.setAccessible(true);
            snapshot=nativeField.getType().getDeclaredMethod("snapshot",int.class,int.class);snapshot.setAccessible(true);
            close=LodQuadBuilder.class.getMethod("close");
        }catch(NoSuchFieldException ignored){}
    }
    static long point(int low,int high,int sky,int material,int alpha) {
        return (long)material<<60 | (long)(alpha>>>4)<<56 | 0x336699L<<32 | (long)high<<20 | (long)low<<8 | 2L<<4 | sky;
    }
    record Input(int seed,int trans,short width,short height,short x,short y,short z,int color,
        byte material,byte sky,byte block,long top,long bottom,long[][] neighbors,boolean[] same) {}
    static Input input(int seed) {
        var random=new Random(seed);int y=seed%8==0?0:16;int height=1+seed%32,width=1<<(seed%5);
        int material=seed%16,alpha=seed%3==0?128:255;
        long top=seed%4==0?0:point(y+height,y+height+8,12,(seed+3)%16,seed%2==0?255:128);
        long bottom=seed%5==0?0:point(Math.max(0,y-8),y,seed%16,(seed+7)%16,255);
        long[][] ns=new long[4][];boolean[] same=new boolean[4];
        for(int d=0;d<4;d++) {
            same[d]=random.nextBoolean();int pattern=(seed+d)%8;
            if(pattern==0)continue;
            if(pattern==1){ns[d]=new long[0];continue;}
            if(pattern==2){ns[d]=new long[]{point(0,0,15,0,255)};continue;}
            int count=pattern==7?128:2+seed%13;ns[d]=new long[count];
            for(int i=0;i<count;i++) {
                int low=(count-i-1)*2;ns[d][i]=pattern==3?0:point(low,low+1+random.nextInt(5),random.nextInt(16),random.nextInt(16),random.nextBoolean()?255:128);
            }
        }
        return new Input(seed,seed%3,(short)width,(short)height,(short)(seed%7==0?0:16),(short)y,
            (short)(seed%11==0?256-width:16),(alpha<<24)|0x336699,(byte)material,(byte)15,(byte)2,top,bottom,ns,same);
    }
    static ColumnArrayView[] views(Input in) throws Exception {
        var result=new ColumnArrayView[4];
        for(int d=0;d<4;d++)if(in.neighbors[d]!=null){
            long[] padded=new long[in.neighbors[d].length+4];Arrays.fill(padded,0x12345678);
            System.arraycopy(in.neighbors[d],0,padded,2,in.neighbors[d].length);
            result[d]=(ColumnArrayView)view.newInstance(new LongArrayList(padded),in.neighbors[d].length,2,in.neighbors[d].length);
        }
        return result;
    }
    static String build(LodQuadBuilder b,Input in,ColumnArrayView[] neighbors) throws Exception {
        Object[] args={b,checkout,null,in.width,in.height,in.x,in.y,in.z,in.color,in.material,in.sky,in.block,in.top,in.bottom,neighbors,in.same};
        if(nativeField!=null)args=new Object[]{b,checkout,null,in.width,in.height,in.x,in.y,in.z,in.color,in.material,0,(byte)0,0L,in.sky,in.block,in.top,in.bottom,neighbors,in.same};
        Method target=box;
        if(!semanticSpans.isEmpty()) {
            args=new Object[]{b,checkout,null,in.width,in.height,in.x,in.y,in.z,in.color,in.material,0,(byte)0,0L,semanticSpans,in.sky,in.block,in.top,in.bottom,neighbors,in.same};
            target=spanBox;
        }
        try {target.invoke(null,args);return "";}
        catch(InvocationTargetException e){return e.getCause().getClass().getName();}
    }
    @SuppressWarnings("unchecked")
    static List<BufferQuad> rows(LodQuadBuilder b,int layer,int dir) throws Exception {
        return nativeField==null?((List<BufferQuad>[])legacy[layer].get(b))[dir]:(List<BufferQuad>)snapshot.invoke(nativeField.get(b),layer,dir);
    }
    static void release(LodQuadBuilder b) throws Exception {if(close!=null)close.invoke(b);}
    static String hash(Class<?> c) throws Exception {
        try(var stream=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));}
    }
    static void writeInput(DataOutputStream out,Input in) throws Exception {
        out.writeInt(in.seed);out.writeInt(in.trans);
        for(short v:new short[]{in.width,in.height,in.x,in.y,in.z})out.writeShort(v);
        out.writeInt(in.color);out.writeByte(in.material);out.writeByte(in.sky);out.writeByte(in.block);out.writeLong(in.top);out.writeLong(in.bottom);
        for(int d=0;d<4;d++){out.writeBoolean(in.same[d]);out.writeInt(in.neighbors[d]==null?-1:in.neighbors[d].length);if(in.neighbors[d]!=null)for(long p:in.neighbors[d])out.writeLong(p);}
    }
    static void record(Path path) throws Exception {
        if(nativeField!=null)throw new IllegalStateException("Record on untouched Frozen only");
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(0x44484631);
            Class<?>[] pins={ColumnBox.class,LodQuadBuilder.class,BufferQuad.class,RenderDataPointUtil.class,ColorUtil.class,EDhDirection.class};
            out.writeInt(pins.length);for(var c:pins){out.writeUTF(c.getName());out.writeUTF(hash(c));}
            out.writeInt(384*8);
            for(int seed=0;seed<384;seed++)for(int fail:new int[]{0,1,3,5})for(boolean merge:new boolean[]{false,true}) {
                Input in=input(seed);writeInput(out,in);out.writeInt(fail);out.writeBoolean(merge);
                Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(EDhApiTransparency.values()[in.trans]);
                var b=new LodQuadBuilder(in.trans!=0,null);EVENTS.clear();failure=fail;
                String error=build(b,in,views(in));if(error.isEmpty()&&merge)b.mergeQuads();
                out.writeUTF(error);out.writeInt(EVENTS.size());for(int event:EVENTS)out.writeInt(event);
                for(int layer=0;layer<2;layer++)for(int dir=0;dir<6;dir++) {
                    var rows=rows(b,layer,dir);out.writeInt(rows.size());
                    for(var q:rows){out.writeShort(q.x);out.writeShort(q.y);out.writeShort(q.z);out.writeShort(q.widthEastWest);out.writeShort(q.widthNorthSouthOrUpDown);
                        out.writeInt(q.color);out.writeByte(q.irisBlockMaterialId);out.writeByte(q.skyLight);out.writeByte(q.blockLight);out.writeByte(q.direction.ordinal());out.writeBoolean(q.hasError);}
                }
                release(b);
            }
        }
        System.out.println("Recorded actual Frozen whole-box cases=3072");
    }
    static long run(Input[] inputs,ColumnArrayView[][] neighbors,int rounds,int copies) throws Exception {
        long sum=0;failure=0;
        for(int r=0;r<rounds;r++) {
            var b=new LodQuadBuilder(true,null);
            try {
                for(int c=0;c<copies;c++)for(int i=0;i<inputs.length;i++) {
                    EVENTS.clear();String error=build(b,inputs[i],neighbors[i]);if(!error.isEmpty())throw new IllegalStateException(error);
                }
                b.mergeQuads();sum+=b.getCurrentOpaqueQuadsCount()*31L+b.getCurrentTransparentQuadsCount();
            }finally{release(b);}
        }
        return sum;
    }
    /** Actual Frozen client shade getter, without constructing or changing a world. */
    static void recordShades(Path path) throws Exception {
        if(nativeField!=null)throw new IllegalStateException("Record on untouched Frozen only");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var unsafe=Class.forName("sun.misc.Unsafe");var uf=unsafe.getDeclaredField("theUnsafe");uf.setAccessible(true);
        var level=(net.minecraft.client.multiplayer.ClientLevel)unsafe.getMethod("allocateInstance",Class.class)
            .invoke(uf.get(null),net.minecraft.client.multiplayer.ClientLevel.class);
        var effects=net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredField("effects");effects.setAccessible(true);
        var dimensions=List.of(new net.minecraft.client.renderer.DimensionSpecialEffects.OverworldEffects(),
            new net.minecraft.client.renderer.DimensionSpecialEffects.NetherEffects(),
            new net.minecraft.client.renderer.DimensionSpecialEffects.EndEffects(),
            new net.minecraft.client.renderer.DimensionSpecialEffects.PrimordialCavesEffects());
        var settings=net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE;
        boolean previous=settings.shouldDisableDirectionalShading();
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x44485331);
            var pins=new ArrayList<Class<?>>();pins.add(level.getClass());pins.add(settings.getClass());
            for(var effect:dimensions)pins.add(effect.getClass());
            out.writeInt(pins.size());for(var c:pins){out.writeUTF(c.getName());out.writeUTF(hash(c));}
            out.writeInt(dimensions.size()*2*6);
            for(var effect:dimensions)for(boolean flat:new boolean[]{false,true}) {
                effects.set(level,effect);settings.setDisableDirectionalShading(flat);
                int facts=4 | (effect.constantAmbientLight()?8:0) | (flat?16:0);
                for(var direction:EDhDirection.values()) {
                    var mcDirection=net.minecraft.core.Direction.valueOf(direction.name());
                    out.writeInt(facts);out.writeByte(direction.ordinal());
                    out.writeInt(Float.floatToRawIntBits(level.getShade(mcDirection,true)));
                }
            }
        }finally{settings.setDisableDirectionalShading(previous);}
        System.out.println("Recorded actual Frozen shade facts=48");
    }
    public static void main(String[] args) throws Exception {
        setup();
        if(args[0].equals("record")){record(Path.of(args[1]));return;}
        if(args[0].equals("record-shades")){recordShades(Path.of(args[1]));return;}
        if(!args[0].equals("bench")||args.length!=4)throw new IllegalArgumentException("record PATH | bench PATTERN ROUNDS COPIES");
        productionFacts=Boolean.getBoolean("mattmc.dh.faces.productionFacts");
        int spanCount=Integer.getInteger("mattmc.dh.faces.semanticSpans",0);
        if(spanCount>0) {
            if(spanBox==null)throw new IllegalArgumentException("Frozen has no semantic-material extension; compare preceding Current");
            var type=Class.forName("com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource$SemanticMaterialSpan");
            var constructor=type.getConstructor(int.class,int.class,int.class,byte.class,long.class);
            var values=new ArrayList<>();
            for(int i=0;i<spanCount;i++)values.add(constructor.newInstance(i*2,i*2+2,1+i%4,(byte)(i%2),i%2==1?9L:0L));
            semanticSpans=List.copyOf(values);
        }
        int pattern=Integer.parseInt(args[1]),rounds=Integer.parseInt(args[2]),copies=Integer.parseInt(args[3]);
        var inputs=new Input[32];var neighbors=new ColumnArrayView[32][];
        Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(EDhApiTransparency.COMPLETE);
        for(int i=0;i<32;i++){inputs[i]=input(i*8+pattern);neighbors[i]=views(inputs[i]);}
        for(int i=0;i<5;i++)run(inputs,neighbors,rounds,copies);
        long start=System.nanoTime(),sum=run(inputs,neighbors,rounds,copies),elapsed=System.nanoTime()-start;
        System.out.println("{\"elapsed_ns\":"+elapsed+",\"checksum\":"+sum+",\"boxes\":"+(32L*rounds*copies)+"}");
    }
}
