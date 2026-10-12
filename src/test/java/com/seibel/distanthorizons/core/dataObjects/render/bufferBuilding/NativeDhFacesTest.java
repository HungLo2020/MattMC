package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pooling.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.*;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhFacesTest {
    @Test @Tag("parity") void actualFrozenWholeBoxesAndCurrentSemanticControls(@TempDir Path directory) throws Exception {
        // ColumnBox captures the injected wrapper in a static field. A fresh JVM
        // exercises callback ordering independently of other suites' initialization.
        var paths=new LinkedHashSet<String>();
        paths.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for(var loader=getClass().getClassLoader();loader!=null;loader=loader.getParent())
            if(loader instanceof URLClassLoader urls)for(var url:urls.getURLs())
                if(url.getProtocol().equals("file"))paths.add(Path.of(url.toURI()).toString());
        var command=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "--enable-native-access=ALL-UNNAMED","-Xmx2g",
            "-Dmattmc.rust.natives.dir="+System.getProperty("mattmc.rust.natives.dir"),
            "-cp",String.join(File.pathSeparator,paths),Probe.class.getName());
        var log=directory.resolve("faces.log");var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();fail(Files.readString(log));}
        assertEquals(0,process.exitValue(),Files.readString(log));
    }

    public static final class Probe {
        static final List<Integer> events=new ArrayList<>();
        static final float[] shades={.5f,1f,.8f,.9f,.6f,.7f};
        static int failure;
        static int admittedFacts=-1;
        static Runnable shadeAction=()->{};
        static PhantomArrayListCheckout checkout;
        static NativeDhQuadBuilder owner(LodQuadBuilder b) throws Exception {
            var f=LodQuadBuilder.class.getDeclaredField("nativeQuads");f.setAccessible(true);return (NativeDhQuadBuilder)f.get(b);
        }
        static void setup() {
            var render=(IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftRenderWrapper.class,IMinecraftRenderWrapper.NativeShadeFacts.class},(p,m,a)->{
                if(m.getName().equals("getDelayedSetupComplete"))return true;
                if(m.getName().equals("hasNativeShadeFacts"))return admittedFacts>=0;
                if(m.getName().equals("getNativeShadeFacts"))return admittedFacts;
                if(m.getName().equals("getShade")){
                    int d=((EDhDirection)a[0]).ordinal();events.add(d);shadeAction.run();
                    if(failure!=0&&events.size()==failure)throw new IllegalStateException("injected shade failure");
                    return shades[d];
                }
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==int.class)return 0;
                return null;
            });
            SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class,render);
            checkout=new PhantomArrayListCheckout(new PhantomArrayListPool("Face replay"));
            checkout.addLongArrayListRef(new LongArrayList());checkout.addLongArrayListRef(new LongArrayList());
            Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(false);
            Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
            Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(false);
        }
        static void frozen(boolean primary) throws Exception {
            try(var storage=primary?new net.vulkanic.world.NativeDhRenderSource(5):null;
                var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(Probe.class.getResourceAsStream("/rendering/frozen-dh-faces.bin.gz"))))) {
                assertEquals(0x44484631,in.readInt());assertEquals(6,in.readInt());
                for(int i=0;i<6;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
                int cases=in.readInt();assertEquals(3072,cases);
                for(int c=0;c<cases;c++) {
                    int seed=in.readInt(),trans=in.readInt();short width=in.readShort(),height=in.readShort(),x=in.readShort(),y=in.readShort(),z=in.readShort();
                    int color=in.readInt();byte material=in.readByte(),sky=in.readByte(),block=in.readByte();long top=in.readLong(),bottom=in.readLong();
                    var neighbors=new ColumnArrayView[4];var same=new boolean[4];
                    for(int d=0;d<4;d++) {
                        same[d]=in.readBoolean();int n=in.readInt();if(n<0)continue;
                        long[] padded=new long[n+4];Arrays.fill(padded,0x12345678);for(int i=0;i<n;i++)padded[i+2]=in.readLong();
                        if(primary) {
                            int base=d*4100;
                            for(int i=0;i<padded.length;i++)storage.set(base+i,padded[i]);
                            neighbors[d]=new ColumnArrayView(storage,n,base+2,n);
                        } else neighbors[d]=new ColumnArrayView(new LongArrayList(padded),n,2,n);
                    }
                    failure=in.readInt();boolean merge=in.readBoolean();String expectedError=in.readUTF();
                    var wanted=new ArrayList<Integer>();int n=in.readInt();for(int i=0;i<n;i++)wanted.add(in.readInt());
                    Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(EDhApiTransparency.values()[trans]);
                    try(var b=new LodQuadBuilder(trans!=0,null);var whole=failure==0?new LodQuadBuilder(trans!=0,null):null) {
                        events.clear();String error="";
                        try {ColumnBox.addBoxQuadsToBuilder(b,checkout,null,width,height,x,y,z,color,material,0,(byte)0,0L,sky,block,top,bottom,neighbors,same);}
                        catch(RuntimeException e){error=e.getClass().getName();}
                        assertEquals(expectedError,error,"case "+c+" seed "+seed);
                        assertEquals(wanted,events,"callback prefix case "+c);
                        if(error.isEmpty()&&merge)b.mergeQuads();
                        if(whole!=null) {
                            String wholeError="";
                            var mode=EDhApiTransparency.values()[trans];
                            try { assertTrue(whole.addNativeBox(width,height,x,y,z,color,material,0,(byte)0,0L,List.of(),
                                sky,block,top,bottom,neighbors,same,Integer.MIN_VALUE,mode.transparencyEnabled,mode.fakeTransparencyEnabled,0,shades)); }
                            catch(RuntimeException e){wholeError=e.getClass().getName();}
                            assertEquals(expectedError,wholeError,"whole native case "+c);
                            if(wholeError.isEmpty()&&merge)whole.mergeQuads();
                            equal(b,whole);
                        }
                        var nativeOwner=owner(b);assertNotNull(nativeOwner);
                        for(int layer=0;layer<2;layer++)for(int d=0;d<6;d++) {
                            var rows=nativeOwner.snapshot(layer,d);assertEquals(in.readInt(),rows.size(),"case "+c+" direction "+d);
                            for(var q:rows) {
                                assertEquals(in.readShort(),q.x);assertEquals(in.readShort(),q.y);assertEquals(in.readShort(),q.z);
                                assertEquals(in.readShort(),q.widthEastWest);assertEquals(in.readShort(),q.widthNorthSouthOrUpDown);
                                assertEquals(in.readInt(),q.color);assertEquals(in.readByte(),q.irisBlockMaterialId);
                                assertEquals(in.readByte(),q.skyLight);assertEquals(in.readByte(),q.blockLight);
                                assertEquals(in.readByte(),q.direction.ordinal());assertEquals(in.readBoolean(),q.hasError);
                            }
                        }
                    }
                }
                assertEquals(-1,in.read());
            }
            failure=0;
        }
        static class Compatibility extends LodQuadBuilder {Compatibility(){super(true,null);}}
        static List<BufferQuad> rows(LodQuadBuilder b,int layer,int d) throws Exception {
            var owner=owner(b);if(owner!=null)return owner.snapshot(layer,d);
            var f=LodQuadBuilder.class.getDeclaredField(layer==0?"opaqueQuads":"transparentQuads");f.setAccessible(true);
            @SuppressWarnings("unchecked") var lists=(List<BufferQuad>[])f.get(b);return lists[d];
        }
        static void equal(LodQuadBuilder a,LodQuadBuilder b) throws Exception {
            assertEquals(a.semanticQuadCoverage(),b.semanticQuadCoverage());
            for(int layer=0;layer<2;layer++)for(int d=0;d<6;d++) {
                var aa=rows(a,layer,d);var bb=rows(b,layer,d);assertEquals(aa.size(),bb.size());
                for(int i=0;i<aa.size();i++) {
                    var u=aa.get(i);var v=bb.get(i);
                    assertEquals(List.of(u.x,u.y,u.z,u.widthEastWest,u.widthNorthSouthOrUpDown,u.color,u.irisBlockMaterialId,u.skyLight,u.blockLight,u.semanticMaterialId,u.semanticVariantState,u.semanticVariantPosition),
                        List.of(v.x,v.y,v.z,v.widthEastWest,v.widthNorthSouthOrUpDown,v.color,v.irisBlockMaterialId,v.skyLight,v.blockLight,v.semanticMaterialId,v.semanticVariantState,v.semanticVariantPosition));
                }
            }
        }
        static void controls() throws Exception {
            Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(EDhApiTransparency.COMPLETE);
            for(int seed=0;seed<192;seed++) {
                var random=new Random(seed);var spanList=new ArrayList<ColumnRenderSource.SemanticMaterialSpan>();
                for(int i=0;i<8;i++) {
                    int material=random.nextInt(4);byte variant=(byte)random.nextInt(3);
                    spanList.add(new ColumnRenderSource.SemanticMaterialSpan(i*3,i*3+2,material,variant,variant==1?random.nextInt(4):0));
                }
                var spans=List.copyOf(spanList);var direction=EDhDirection.values()[2+seed%4];
                long[] points=new long[16];for(int i=0;i<16;i++)points[i]=0x2f33669900000000L|(long)(i*2+1)<<20|(long)(i*2)<<8|seed%16;
                if(seed%7==0)points[8]=0x2f33669900201e0fL; // reversed packed interval, retained compatibility behavior
                var adjacent=new ColumnArrayView(new LongArrayList(points),16,0,16);
                short minY=seed%11==0?(short)32760:seed%13==0?Short.MIN_VALUE:0;
                short height=seed%11==0?(short)16:seed%13==0?Short.MAX_VALUE:32;
                try(var a=new LodQuadBuilder(true,null);var b=new Compatibility()) {
                    for(var builder:List.of(a,b))ColumnBox.makeAdjVerticalQuad(builder,checkout,adjacent,seed%2==0,seed%32,direction,
                        (short)(seed%3==0?0:256),minY,(short)(seed%5==0?0:256),(short)4,height,
                        seed%2==0?0xff336699:0x80336699,(byte)(seed%16),7,(byte)1,9L,spans,(byte)2);
                    equal(a,b);
                }
                // Complete production, including top/bottom semantic lookup and shaded neighbors.
                try(var a=new LodQuadBuilder(true,null);var b=new Compatibility()) {
                    var neighbors=new ColumnArrayView[]{adjacent,adjacent,adjacent,adjacent};
                    var same=new boolean[]{true,false,true,false};
                    ColumnBox.addBoxQuadsToBuilder(b,checkout,null,(short)4,(short)32,(short)0,(short)0,(short)0,
                        0x80336699,(byte)(seed%16),7,(byte)1,9L,spans,(byte)15,(byte)2,0,0,neighbors,same);
                    assertTrue(a.addNativeBox((short)4,(short)32,(short)0,(short)0,(short)0,0x80336699,(byte)(seed%16),
                        7,(byte)1,9L,spans,(byte)15,(byte)2,0,0,neighbors,same,Integer.MIN_VALUE,true,false,0,shades));
                    equal(a,b);a.mergeQuads();b.mergeQuads();equal(a,b);
                }
            }
            // Read the borrowed neighbor only after the shade callback mutates it.
            var list=new LongArrayList(new long[]{0x2f336699010000f0L});var adjacent=new ColumnArrayView(list,1,0,1);
            shadeAction=()->list.set(0,0);
            try(var a=new LodQuadBuilder(true,null);var b=new Compatibility()) {
                for(var builder:List.of(a,b)) {
                    list.set(0,0x2f336699010000f0L);
                    ColumnBox.makeAdjVerticalQuad(builder,checkout,adjacent,true,Integer.MIN_VALUE,EDhDirection.NORTH,
                        (short)1,(short)0,(short)1,(short)4,(short)16,0xff336699,(byte)2,7,(byte)1,9,List.of(),(byte)0);
                }
                equal(a,b);
            }finally{shadeAction=()->{};}
            // Custom list failures preserve earlier reads, shade call and empty output.
            var reads=new ArrayList<Integer>();
            var custom=new LongArrayList(new long[]{0x2f33669900100000L,0x2f33669900200100L}) {
                @Override public long getLong(int i){reads.add(i);if(i==1)throw new IllegalStateException("custom getter");return super.getLong(i);}
            };
            var customView=new ColumnArrayView(custom,2,0,2);
            try(var a=new LodQuadBuilder(true,null);var b=new Compatibility()) {
                List<Integer> wanted=null;
                for(var builder:List.of(a,b)) {
                    reads.clear();events.clear();
                    assertThrows(IllegalStateException.class,()->ColumnBox.makeAdjVerticalQuad(builder,checkout,customView,true,0,EDhDirection.NORTH,
                        (short)0,(short)0,(short)0,(short)1,(short)4,0xff336699,(byte)2,7,(byte)1,9,List.of(),(byte)0));
                    assertEquals(List.of(2),events);
                    if(wanted==null)wanted=List.copyOf(reads);else assertEquals(wanted,reads);
                }
                equal(a,b);
            }
            // Whole-box admission must decline custom storage before any getter or face mutation.
            try(var a=new LodQuadBuilder(true,null)) {
                reads.clear();
                assertFalse(a.addNativeBox((short)1,(short)4,(short)0,(short)0,(short)0,0xff336699,(byte)2,7,(byte)1,9L,List.of(),
                    (byte)15,(byte)0,0,0,new ColumnArrayView[]{customView,null,null,null},new boolean[4],0,true,false,1,null));
                assertTrue(reads.isEmpty());assertEquals(0,a.getCurrentOpaqueQuadsCount());
            }
            try(var b=new LodQuadBuilder(false,null)) {
                assertThrows(IllegalArgumentException.class,()->ColumnBox.addVerticalFaces(b,EDhDirection.NORTH,(short)0,(short)0,(short)0,(short)0,(short)1,0xff336699,(byte)2,(byte)15,(byte)0,0,(byte)0,0,List.of()));
                assertEquals(0,b.getCurrentOpaqueQuadsCount());
                b.close();
                assertDoesNotThrow(()->ColumnBox.addVerticalFaces(b,EDhDirection.NORTH,(short)0,(short)0,(short)0,(short)1,(short)0,0xff336699,(byte)2,(byte)15,(byte)0,0,(byte)0,0,List.of()));
                assertDoesNotThrow(()->ColumnBox.addVerticalFaces(b,EDhDirection.NORTH,(short)0,(short)0,(short)0,(short)1,(short)0,0xff336699,(byte)2,(byte)15,(byte)0,0,(byte)0,0,List.of(new ColumnRenderSource.SemanticMaterialSpan(8,9,7,(byte)1,1))));
                assertThrows(IllegalStateException.class,()->ColumnBox.addVerticalFaces(b,EDhDirection.NORTH,(short)0,(short)0,(short)0,(short)1,(short)1,0xff336699,(byte)2,(byte)15,(byte)0,0,(byte)0,0,List.of()));
            }
        }
        static void stockFactEntry() throws Exception {
            try(var in=new DataInputStream(Objects.requireNonNull(Probe.class.getResourceAsStream("/rendering/frozen-dh-shades.bin")))) {
                assertEquals(0x44485331,in.readInt());int pins=in.readInt();assertEquals(6,pins);
                for(int i=0;i<pins;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
                int cases=in.readInt();assertEquals(48,cases);
                for(int i=0;i<cases;i+=6) {
                    int facts=-1;
                    for(int d=0;d<6;d++){int f=in.readInt();if(facts<0)facts=f;assertEquals(facts,f);assertEquals(d,in.readByte());shades[d]=Float.intBitsToFloat(in.readInt());}
                    var neighbor=new ColumnArrayView(new LongArrayList(new long[]{0}),1,0,1);
                    var neighbors=new ColumnArrayView[]{neighbor,neighbor,neighbor,neighbor};var same=new boolean[]{true,true,true,true};
                    try(var a=new LodQuadBuilder(true,null);var b=new Compatibility()) {
                        admittedFacts=facts;events.clear();
                        ColumnBox.addBoxQuadsToBuilder(a,checkout,null,(short)4,(short)16,(short)1,(short)0,(short)1,
                            0xff336699,(byte)2,7,(byte)1,9L,(byte)15,(byte)2,0,0,neighbors,same);
                        assertTrue(events.isEmpty(),"stock facts must not call per-face shade callbacks");
                        admittedFacts=-1;
                        ColumnBox.addBoxQuadsToBuilder(b,checkout,null,(short)4,(short)16,(short)1,(short)0,(short)1,
                            0xff336699,(byte)2,7,(byte)1,9L,(byte)15,(byte)2,0,0,neighbors,same);
                        equal(a,b);a.mergeQuads();b.mergeQuads();equal(a,b);
                    }finally{admittedFacts=-1;}
                }
                assertEquals(-1,in.read());
            }
        }
        public static void main(String[] args) throws Exception {setup();frozen(false);frozen(true);controls();stockFactEntry();System.out.println("Frozen3072 x heap/native; semantic192; reentrant/custom/close controls pass");}
    }
}
