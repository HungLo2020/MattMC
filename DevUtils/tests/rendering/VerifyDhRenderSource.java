import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Identical observer/workload compiled separately against unchanged Frozen and
 * Current. Saved column geometry and lights are real; colors are fixed CPU inputs,
 * so this verifies storage/build policy, not contextual color or gameplay parity. */
public final class VerifyDhRenderSource {
    record Input(ColumnArrayView[] columns) {}
    static Constructor<?> view;
    static Method close, snapshot;
    static Field nativeQuads;
    static final Field[] legacy = new Field[2];
    static final float[] SHADES = {.5f,1f,.8f,.8f,.6f,.6f};
    static Object proxy(Class<?> type, java.lang.reflect.InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    static Object defaultValue(Method method) {
        if(method.getReturnType()==boolean.class)return false;
        if(method.getReturnType()==int.class)return 0;
        if(method.getReturnType()==float.class)return 0f;
        return null;
    }
    static IDhClientLevel setup() throws Exception {
        var interfaces=new ArrayList<Class<?>>(); interfaces.add(IMinecraftRenderWrapper.class);
        try{interfaces.add(Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper$NativeShadeFacts"));}
        catch(ClassNotFoundException ignored){}
        var render=(IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(), interfaces.toArray(Class<?>[]::new), (p,m,a)->switch(m.getName()) {
            case "getDelayedSetupComplete", "hasNativeShadeFacts" -> true;
            case "getNativeShadeFacts" -> 1;
            case "getShade" -> SHADES[((EDhDirection)a[0]).ordinal()];
            default -> defaultValue(m);
        });
        SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class,render);
        var world=(IClientLevelWrapper)proxy(IClientLevelWrapper.class,(p,m,a)->defaultValue(m));
        var level=(IDhClientLevel)proxy(IDhClientLevel.class,(p,m,a)->m.getName().equals("getLevelWrapper")?world:defaultValue(m));
        Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(false);
        Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
        Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(false);
        for(var ctor:ColumnArrayView.class.getConstructors())if(ctor.getParameterCount()==4)view=ctor;
        for(int i=0;i<2;i++){legacy[i]=LodQuadBuilder.class.getDeclaredField(i==0?"opaqueQuads":"transparentQuads");legacy[i].setAccessible(true);}
        try{nativeQuads=LodQuadBuilder.class.getDeclaredField("nativeQuads");nativeQuads.setAccessible(true);
            snapshot=nativeQuads.getType().getDeclaredMethod("snapshot",int.class,int.class);snapshot.setAccessible(true);close=LodQuadBuilder.class.getMethod("close");}
        catch(NoSuchFieldException ignored){}
        return level;
    }
    static List<Input> inputs(Path manifest) throws Exception {
        var data=com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();var inputs=new ArrayList<Input>();
        for(var item:data.getAsJsonArray("rows")) {
            var row=item.getAsJsonObject();var dto=FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
            dto.pos=DhSectionPos.encode((byte)(row.get("level").getAsInt()+6),row.get("x").getAsInt(),row.get("z").getAsInt());
            dto.dataFormatVersion=(byte)row.get("version").getAsInt();dto.compressionModeValue=(byte)row.get("compression").getAsInt();
            for(var field:row.getAsJsonObject("fields").entrySet())FullDataSourceV2DTO.class.getField(field.getKey()).set(dto,
                ByteArrayList.wrap(Files.readAllBytes(manifest.getParent().resolve(field.getValue().getAsJsonObject().get("file").getAsString()))));
            try(dto;FullDataSourceV2 source=dto.createUnitTestDataSource()) {
                var columns=new ColumnArrayView[4096];
                for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
                    var full=source.getColumnAtRelPos(x,z);long[] points=new long[full.size()];
                    for(int y=0;y<points.length;y++) {
                        long p=full.getLong(y);int min=FullDataPointUtil.getBottomY(p),max=min+FullDataPointUtil.getHeight(p);
                        points[y]=RenderDataPointUtil.createDataPoint(max,min,0xff336699,(byte)FullDataPointUtil.getSkyLight(p),(byte)FullDataPointUtil.getBlockLight(p),(byte)2);
                    }
                    columns[x*64+z]=(ColumnArrayView)view.newInstance(new LongArrayList(points),points.length,0,points.length);
                }
                inputs.add(new Input(columns));
            }
        }
        return inputs;
    }
    static long geometry(LodQuadBuilder builder) throws Exception {
        long hash=1;
        Object nativeOwner=nativeQuads==null?null:nativeQuads.get(builder);
        for(int layer=0;layer<2;layer++)for(int direction=0;direction<6;direction++) {
            @SuppressWarnings("unchecked") var rows=nativeOwner==null?((List<?>[])legacy[layer].get(builder))[direction]:(List<?>)snapshot.invoke(nativeOwner,layer,direction);
            hash=31*hash+rows.size();
            for(Object quad:rows)for(String name:List.of("x","y","z","widthEastWest","widthNorthSouthOrUpDown","color","irisBlockMaterialId","skyLight","blockLight","direction","hasError")) {
                Object value=quad.getClass().getField(name).get(quad);
                hash=31*hash+(value instanceof Number n?n.longValue():value instanceof Enum<?> e?e.ordinal():Boolean.TRUE.equals(value)?1:0);
            }
        }
        return hash;
    }
    static long run(List<Input> inputs, IDhClientLevel level, int height, boolean build, boolean oracle) throws Exception {
        long hash=1;
        for(Input input:inputs)try(var source=ColumnRenderSource.createEmpty(DhSectionPos.encode((byte)6,0,0),height,0)) {
            source.markNotEmpty();
            for(int x=0;x<64;x++)for(int z=0;z<64;z++) {
                var column=source.getVerticalDataPointView(x,z);var points=input.columns[x*64+z];
                if(points.size()>0)column.changeVerticalSizeFrom(points);
                hash=31*hash+(oracle?column.getDataHash():column.get(0));
            }
            if(oracle)hash=31*hash+(source.hasNonVoidDataPoints()?1:0);
            if(build) {
                var builder=new LodQuadBuilder(true, null);
                try {
                    ColumnRenderBufferBuilder.makeLodRenderData(builder,source,level,new ColumnRenderSource[4],new boolean[]{true,true,true,true});
                    builder.mergeQuads();
                    hash=31*hash+builder.getCurrentOpaqueQuadsCount(); hash=31*hash+builder.getCurrentTransparentQuadsCount();
                    if(oracle)hash=31*hash+geometry(builder);
                } finally {if(close!=null)close.invoke(builder);}
            }
        }
        return hash;
    }
    public static void main(String[] args) throws Exception {
        var level=setup();var inputs=inputs(Path.of(args[0]));int height=Integer.parseInt(args[1]);boolean build=Boolean.parseBoolean(args[2]);
        if(args.length>3&&args[3].equals("oracle")){System.out.println("ORACLE "+run(inputs,level,height,build,true));return;}
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
        long expected=0;
        for(int round=0;round<24;round++) {
            long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),begin=System.nanoTime();
            long hash=run(inputs,level,height,build,false),elapsed=System.nanoTime()-begin;
            allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;
            if(round==0)expected=hash;else if(hash!=expected)throw new AssertionError("Render source checksum changed");
            if(round>=8)System.out.println("SAMPLE "+(round-8)+" "+elapsed+" "+hash+" "+allocated);
        }
    }
}
