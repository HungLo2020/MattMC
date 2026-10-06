package net.minecraft.world.level.levelgen;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Replays saved, seed-generated structure geometry through the production cell provider. */
public final class NativeBeardifierVerification {
    private static volatile long sink;
    record Cell(int x,int y,int z) {}
    record Fixture(String name, String group, List<Beardifier.Rigid> pieces, List<JigsawJunction> junctions,
                           BoundingBox bounds, List<JavaBeardifier.Rigid> originalPieces, NoiseChunk chunk, List<Cell> full, List<Cell> active, double[] output) {
        DensityFunction function(boolean nativeMode) {
            return nativeMode ? Beardifier.forGeometry(pieces,junctions,bounds)
                : new JavaBeardifier(List.copyOf(originalPieces),List.copyOf(junctions),bounds);
        }
    }
    static List<Fixture> fixtures() throws Exception {
        var stream=NativeBeardifierVerification.class.getResourceAsStream("/worldgen/beardifier/structures.json");
        if(stream==null)throw new AssertionError("Missing structure corpus");
        var result=new ArrayList<Fixture>();
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            for(var element:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("structures")) {
                var structure=element.getAsJsonObject();String group=structure.get("adjustment").getAsString();
                var adjustment=TerrainAdjustment.valueOf(group.toUpperCase(Locale.ROOT));
                var children=structure.getAsJsonArray("children");
                var origins=new LinkedHashSet<Long>();
                var start=structure.getAsJsonArray("start");
                origins.add(net.minecraft.world.level.ChunkPos.asLong(start.get(0).getAsInt(),start.get(1).getAsInt()));
                var middle=children.get(children.size()/2).getAsJsonObject().getAsJsonArray("box");
                origins.add(net.minecraft.world.level.ChunkPos.asLong(middle.get(0).getAsInt()>>4,middle.get(2).getAsInt()>>4));
                for(long origin:origins) {
                    int x=net.minecraft.world.level.ChunkPos.getX(origin)*16,z=net.minecraft.world.level.ChunkPos.getZ(origin)*16;
                    var pieces=new ArrayList<Beardifier.Rigid>();var junctions=new ArrayList<JigsawJunction>();BoundingBox bounds=null;
                    for(var childElement:children) {
                        var child=childElement.getAsJsonObject();var a=child.getAsJsonArray("box");
                        var box=new BoundingBox(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt(),a.get(4).getAsInt(),a.get(5).getAsInt());
                        if(!box.intersects(x-12,z-12,x+27,z+27))continue;
                        if(child.get("rigid").getAsBoolean()) {
                            pieces.add(new Beardifier.Rigid(box,adjustment,child.get("delta").getAsInt()));
                            bounds=bounds==null?box:BoundingBox.encapsulating(bounds,box);
                        }
                        for(var je:child.getAsJsonArray("junctions")) {
                            var j=je.getAsJsonArray();int jx=j.get(0).getAsInt(),jy=j.get(1).getAsInt(),jz=j.get(2).getAsInt();
                            if(jx>x-12 && jz>z-12 && jx<x+27 && jz<z+27) {
                                junctions.add(new JigsawJunction(jx,jy,jz,0,StructureTemplatePool.Projection.RIGID));
                                var point=new BoundingBox(jx,jy,jz,jx,jy,jz);
                                bounds=bounds==null?point:BoundingBox.encapsulating(bounds,point);
                            }
                        }
                    }
                    if(bounds==null)continue;
                    bounds=bounds.inflatedBy(24);
                    var full=new ArrayList<Cell>();var active=new ArrayList<Cell>();
                    for(int dx=0;dx<16;dx+=4)for(int y=312;y>=-64;y-=8)for(int dz=0;dz<16;dz+=4) {
                        var cell=new Cell(x+dx,y,z+dz);full.add(cell);
                        if(bounds.intersects(new BoundingBox(x+dx,y,z+dz,x+dx+3,y+7,z+dz+3)))active.add(cell);
                    }
                    if(active.isEmpty())throw new AssertionError("No active cells");
                    result.add(new Fixture(structure.get("id").getAsString()+"/"+structure.get("seed")+"/"+x+","+z,
                        group,List.copyOf(pieces),List.copyOf(junctions),bounds,pieces.stream().map(p->new JavaBeardifier.Rigid(p.box(),p.terrainAdjustment(),p.groundLevelDelta())).toList(),NativeBeardifierTest.chunk(4,8,x,-64,z),List.copyOf(full),List.copyOf(active),new double[128]));
                }
            }
        }
        if(result.size()<12)throw new AssertionError("Incomplete real-geometry corpus: "+result.size());
        return result;
    }
    // Field setters are bound once. Both sides perform the same cell staging;
    // no reflective lookup, registry setup, or file I/O occurs in timed rounds.
    private static final java.lang.invoke.VarHandle X=field("cellStartBlockX"),Y=field("cellStartBlockY"),Z=field("cellStartBlockZ");
    private static java.lang.invoke.VarHandle field(String name) {
        try{return java.lang.invoke.MethodHandles.privateLookupIn(NoiseChunk.class,java.lang.invoke.MethodHandles.lookup()).findVarHandle(NoiseChunk.class,name,int.class);}
        catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    private static void stage(NoiseChunk owner,Cell c) {X.set(owner,c.x);Y.set(owner,c.y);Z.set(owner,c.z);}
    private static long run(List<Fixture> fixtures,boolean nativeMode,boolean full,int repeats) {
        long sum=0,start=System.nanoTime();
        for(int r=0;r<repeats;r++)for(var fixture:fixtures) {
            var function=fixture.function(nativeMode); // Include per-chunk plan creation.
            for(var cell:full?fixture.full:fixture.active) {
                stage(fixture.chunk,cell);function.fillArray(fixture.output,fixture.chunk);
                for(double value:fixture.output)sum=sum*31+Double.doubleToRawLongBits(value);
            }
        }
        sink=sum;return System.nanoTime()-start;
    }
    private static double median(long[] values) {values=values.clone();Arrays.sort(values);return values[values.length/2];}
    private static void bench(List<Fixture> fixtures,boolean nativeMode,boolean full,String group) {
        int points=fixtures.stream().mapToInt(f->(full?f.full:f.active).size()*128).sum();
        var jit=ManagementFactory.getCompilationMXBean();int repeats=1;
        for(int attempt=0;attempt<8;attempt++) {
            long start=System.nanoTime();long[] ns=new long[10],js=new long[10];boolean stable=false;
            for(int i=0;i<300;i++) {
                long before=jit.getTotalCompilationTime();long elapsed=run(fixtures,nativeMode,full,repeats);
                if(elapsed<200_000_000L){repeats*=Math.max(2,(int)Math.ceil(200_000_000.0/Math.max(1,elapsed)));i=-1;continue;}
                ns[i%10]=elapsed;js[i%10]=jit.getTotalCompilationTime()-before;
                if(i>=9 && System.nanoTime()-start>3_000_000_000L && Arrays.stream(js).sum()==0) {
                    long[] a=new long[5],b=new long[5];for(int k=0;k<5;k++){a[k]=ns[(i-k)%10];b[k]=ns[(i-k-5)%10];}
                    if(Math.abs(median(a)/median(b)-1)<.05){stable=true;break;}
                }
            }
            if(!stable)throw new AssertionError("Warmup unstable");
            double[] nsResult=new double[11];long[] compilation=new long[11];boolean accept=true;
            for(int i=0;i<11;i++) {
                long before=jit.getTotalCompilationTime();long elapsed=run(fixtures,nativeMode,full,repeats);
                compilation[i]=jit.getTotalCompilationTime()-before;
                if(compilation[i]!=0){accept=false;break;}
                nsResult[i]=(double)elapsed/repeats/points;
            }
            if(accept) {
                System.out.println("BEARD_BENCH group="+group+" full="+full+" native="+nativeMode+" points="+points+" repeats="+repeats+" ns="+Arrays.toString(nsResult)+" jit="+Arrays.toString(compilation)+" checksum="+sink);
                return;
            }
        }
        throw new AssertionError("Late JIT compilation");
    }
    public static void main(String[] args) throws Exception {
        NativeBeardifierTest.bootstrap();var fixtures=fixtures();long points=0;
        var field=Beardifier.class.getDeclaredField("nativeCells");field.setAccessible(true);
        for(var f:fixtures) {
            var original=f.function(false);var nativeFunction=f.function(true);
            if(field.get(nativeFunction)==null)throw new AssertionError("Native route unavailable");
            double[] expected=new double[128];
            for(var cell:f.full) {
                stage(f.chunk,cell);original.fillArray(expected,f.chunk);
                int x=f.chunk.inCellX,y=f.chunk.inCellY,z=f.chunk.inCellZ,index=f.chunk.arrayIndex;
                nativeFunction.fillArray(f.output,f.chunk);NativeBeardifierTest.same(expected,f.output);
                if(x!=f.chunk.inCellX || y!=f.chunk.inCellY || z!=f.chunk.inCellZ || index!=f.chunk.arrayIndex)throw new AssertionError("Provider state changed");
                points+=128;
            }
            System.out.println("BEARD_FIXTURE name="+f.name+" pieces="+f.pieces.size()+" junctions="+f.junctions.size()+" cells="+f.full.size()+" active="+f.active.size());
        }
        System.out.println("BEARD_PARITY points="+points+" fixtures="+fixtures.size()+" exact=true");
        if(args[0].equals("parity"))return;
        for(String group:List.of("beard_thin","beard_box","encapsulate")) {
            var selected=fixtures.stream().filter(f->f.group.equals(group)).toList();
            if(selected.isEmpty())throw new AssertionError("Missing group "+group);
            for(boolean full:new boolean[]{false,true}) bench(selected,args[0].equals("native"),full,group);
        }
    }
}
