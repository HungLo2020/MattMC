import com.google.gson.*;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.IColumnDataView;
import com.seibel.distanthorizons.core.util.RenderDataPointReducingList;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Executes Frozen reduction, never a copied expected algorithm. Synthetic CPU scope. */
public final class GenerateFrozenDhRenderReductionOracle {
    private static final Class<?>[] PINNED = {RenderDataPointUtil.class,RenderDataPointReducingList.class,
        ColumnArrayView.class,IColumnDataView.class,EDhApiBlockMaterial.class,it.unimi.dsi.fastutil.Arrays.class};
    private static String hash(Class<?> type) throws Exception {
        try(var in=type.getResourceAsStream(type.getSimpleName()+".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    private static long[] input(int count,int seed) {
        var random=new Random(seed);var values=new long[count];int y=0;
        for(int i=0;i<count;i++) {
            int gap=count>512?0:random.nextInt(3),height=count>512?1:1+random.nextInt(6);
            int bottom=y+gap,top=bottom+height;y=top;
            int alpha=switch((seed+i)%5){case 0->0;case 1->16;case 2->128;default->255;};
            values[i]=RenderDataPointUtil.createDataPoint(alpha,random.nextInt(256),random.nextInt(256),random.nextInt(256),
                top,bottom,random.nextInt(16),random.nextInt(16),random.nextInt(16));
        }
        // Unstable sort ties and unrelated source order matter to surviving data.
        for(int i=count-1;i>0;i--){int j=random.nextInt(i+1);long t=values[i];values[i]=values[j];values[j]=t;}
        return values;
    }
    private static void record(DataOutputStream out,String name,long[] input,int target) throws Exception {
        int size=Math.max(0,target)+2;var source=new ColumnArrayView(new LongArrayList(input),input.length,0,input.length);
        long[] result=new long[size+3];java.util.Arrays.fill(result,0x1122334455667788L);
        var output=new ColumnArrayView(new LongArrayList(result),size,1,target);String exception="";
        try{RenderDataPointUtil.mergeMultiData(source,output);}catch(RuntimeException e){exception=e.getClass().getName();}
        out.writeUTF(name);out.writeInt(target);out.writeInt(size);out.writeInt(input.length);for(long p:input)out.writeLong(p);
        out.writeUTF(exception);for(int i=0;i<result.length;i++)out.writeLong(output.data.getLong(i));
    }
    public static void main(String[] args) throws Exception {
        if(args.length==2 && args[0].equals("--pins")) {
            var pins=new JsonObject();for(var type:PINNED)pins.addProperty(type.getName(),hash(type));Files.writeString(Path.of(args[1]),pins.toString());return;
        }
        if(args.length!=2)throw new IllegalArgumentException("output class-pins-json");
        var pins=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        for(var type:PINNED)if(!hash(type).equals(pins.get(type.getName()).getAsString()))throw new IllegalStateException("Not pinned Frozen: "+type);
        int[] sizes={0,1,2,3,8,15,16,17,32,127,128,129,256,512};int[] targets={-1,0,1,2,3,4,8,16};
        int cases=sizes.length*32*targets.length+32;
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x44525231);out.writeInt(PINNED.length);for(var type:PINNED){out.writeUTF(type.getName());out.writeUTF(hash(type));}
            out.writeUTF("Actual Frozen render reduction; synthetic sorted/tied/gapped/shuffled geometry, all packed materials, water, empty/invalid inputs; no gameplay/performance claim");out.writeInt(cases);
            for(int n:sizes)for(int seed=0;seed<32;seed++)for(int target:targets)record(out,"nodes-"+n+"-seed-"+seed+"-target-"+target,input(n,seed),target);
            for(int seed=0;seed<2;seed++)for(int target:targets)record(out,"bounded4096-"+seed+"-target-"+target,input(4096,seed),target);
            long[] overlapping={RenderDataPointUtil.createDataPoint(8,0,0xff123456,15,0,2),RenderDataPointUtil.createDataPoint(12,4,0xff987654,9,2,3)};
            for(int seed=0;seed<2;seed++)for(int target:targets)record(out,"overlapping-"+seed+"-target-"+target,seed==0?overlapping:new long[]{overlapping[1],overlapping[0]},target);
        }
        System.out.println("Recorded actual Frozen cases="+cases);
    }
}
