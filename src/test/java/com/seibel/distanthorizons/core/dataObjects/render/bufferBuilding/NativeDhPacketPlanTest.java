package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import com.seibel.distanthorizons.core.config.Config;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhPacketPlanTest {
    @Test @Tag("parity") void replayActualFrozenPacketsAndCallbackFailurePrefixes() throws Exception {
        int oldCurve=Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
        boolean oldErrors=Config.Client.Advanced.Debugging.showOverlappingQuadErrors.get();
        final float[] shades={.3f,1f,2f,-1f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MIN_VALUE,Float.MAX_VALUE};
        final int[] palette={0x80123456,0xff00ff00,0,0xffffffff,0x12345678};
        class Injected extends RuntimeException {}
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/rendering/frozen-dh-packets.bin.gz"))))) {
            assertEquals(0x44485031,in.readInt());assertEquals(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN,in.readBoolean());
            int pins=in.readInt();assertEquals(7,pins);for(int i=0;i<pins;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
            assertEquals(96,in.readInt());
            for(int c=0;c<96;c++) {
                boolean transparency=in.readBoolean(),debug=in.readBoolean(),errors=in.readBoolean();int mode=in.readInt();
                Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
                Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(errors);
                try(var builder=new NativeDhQuadBuilder(transparency)) {
                    int count=in.readInt();for(int i=0;i<count;i++) {
                        var dir=EDhDirection.values()[in.readShort()];short x=in.readShort(),y=in.readShort(),z=in.readShort(),w=in.readShort(),h=in.readShort();
                        int color=in.readInt();byte material=in.readByte(),sky=in.readByte(),block=in.readByte();
                        builder.add(dir,x,y,z,w,h,color,material,sky,block,0,(byte)0,0,dir.ordinal()>=2);
                    }
                    builder.merge();int grass=debug||mode==0?0:mode==1?2:mode==2?1:3;
                    for(int route=0;route<4;route++)for(int failure:new int[]{-1,0,1,3,11}) {
                        boolean failed=in.readBoolean();byte[] expected=in.readNBytes(in.readInt());int events=in.readInt();var wanted=new ArrayList<String>();for(int i=0;i<events;i++)wanted.add(in.readUTF());
                        var actual=new ArrayList<String>();int[] visits={0},dirt={0},shade={0};
                        var level=colors(()->{actual.add("dirt");if(visits[0]++==failure)throw new Injected();return palette[dirt[0]++%palette.length];});
                        var render=(IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftRenderWrapper.class},(p,m,a)->{
                            if(m.getName().equals("getShade")){actual.add("shade:"+((EDhDirection)a[0]).ordinal());if(visits[0]++==failure)throw new Injected();return shades[shade[0]++%shades.length];}
                            return null;
                        });
                        try(var plan=builder.packetPlan(route,grass)) {
                            int layer=route;
                            if(failed)assertThrows(Injected.class,()->build(plan,layer,level,render));
                            else {var flat=new ByteArrayOutputStream();for(byte[] bytes:build(plan,route,level,render).packedVertexBuffers())flat.write(bytes);assertArrayEquals(expected,flat.toByteArray(),"case "+c+" route "+route);}
                            assertEquals(wanted,actual,"callback prefix case "+c+" route "+route);
                        }
                    }
                }
            }
            assertEquals(-1,in.read());
        } finally {
            Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(oldCurve);
            Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(oldErrors);
        }
    }
    /** Production packet path: append one layer into a native column, then project it. */
    private static LodQuadBuilder.SemanticVertexBufferBuild build(NativeDhPacketPlan plan, int route,
        IClientLevelWrapper level, IMinecraftRenderWrapper render) {
        // Columns take layers in order; earlier layers come from an empty builder.
        try(var column=new NativeDhBuiltColumn();var empty=new NativeDhQuadBuilder(true)) {
            for(int layer=0;layer<route;layer++)try(var none=empty.packetPlan(layer,0)){none.appendTo(column,layer,null,null);}
            plan.appendTo(column,route,level,render);
            return column.project(route);
        }
    }
    private static IClientLevelWrapper level(Runnable callback) {
        return colors(()->{callback.run();return 0xff123456;});
    }
    private static IClientLevelWrapper colors(java.util.function.IntSupplier callback) {
        return (IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),
            new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{
                if(m.getName().equals("getDirtBlockColor"))return callback.getAsInt();
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==int.class)return 0;
                return null;
            });
    }
    private static IMinecraftRenderWrapper render(Runnable callback) {
        return (IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),
            new Class<?>[]{IMinecraftRenderWrapper.class},(p,m,a)->{
                if(m.getName().equals("getShade")){callback.run();return .5f;}
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==int.class)return 0;
                return null;
            });
    }
    private static void add(NativeDhQuadBuilder b, int identity) {
        b.add(EDhDirection.DOWN,(short)identity,(short)0,(short)0,(short)1,(short)1,
            0xff336699,(byte)13,(byte)15,(byte)0,identity,(byte)(identity%3),100000L+identity,false);
    }
    @Test void independentPlanSurvivesSourceMutationAndClosureDuringCallback() {
        var events=new ArrayList<String>();
        try(var builder=new NativeDhQuadBuilder(true)) {
            add(builder,7);
            try(var plan=builder.packetPlan(0,1)) {
                var output=build(plan,0,level(()->{events.add("dirt");if(events.size()==1){add(builder,8);builder.close();}}),
                    render(()->events.add("shade")));
                assertEquals(1,output.semanticMaterialIds().getFirst().length);
                assertArrayEquals(new int[]{7},output.semanticMaterialIds().getFirst());
                assertEquals(java.util.List.of("dirt","shade","dirt","shade","dirt","shade","dirt","shade"),events);
                assertEquals(0x09,Byte.toUnsignedInt(output.packedVertexBuffers().getFirst()[8]));
            }
            assertThrows(IllegalStateException.class,()->builder.packetPlan(0,1));
        }
    }
    @Test void splitPacketsKeepGlobalCallbackIndicesAndAllSemanticCompanions() {
        final int count=16385;int[] dirt={0},shade={0};
        try(var builder=new NativeDhQuadBuilder(true)) {
            for(int i=0;i<count;i++)add(builder,i);
            try(var plan=builder.packetPlan(0,1)) {
                var output=build(plan,0,colors(()->0xff000000|((dirt[0]++/4)%256)<<16),render(()->shade[0]++));
                assertEquals(2,output.packedVertexBuffers().size());assertEquals(count*4,dirt[0]);assertEquals(dirt[0],shade[0]);
                int identity=0;
                for(int p=0;p<2;p++) {
                    var ids=output.semanticMaterialIds().get(p);var states=output.semanticVariantStates().get(p);
                    var positions=output.semanticVariantPositions().get(p);var bytes=output.packedVertexBuffers().get(p);
                    assertEquals(ids.length*64,bytes.length);assertEquals(ids.length,states.length);assertEquals(ids.length,positions.length);
                    var rows=ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
                    for(int q=0;q<ids.length;q++,identity++) {
                        assertEquals(identity,ids[q]);assertEquals(identity%3,states[q]);assertEquals(identity%3==1?100000L+identity:0,positions[q]);
                        assertEquals((short)(identity+1),rows.getShort(q*64));
                        assertEquals((identity%256)/2,Byte.toUnsignedInt(bytes[q*64+8]));
                    }
                }
                assertEquals(count,identity);
            }
        }
    }
    @Test void failedCallbackStopsBeforeShadeAndClosedPlanRejectsReuse() {
        var events=new ArrayList<String>();
        try(var builder=new NativeDhQuadBuilder(true)) {
            add(builder,1);var plan=builder.packetPlan(0,1);
            try(plan) {
                assertThrows(IllegalArgumentException.class,()->build(plan,0,level(()->{events.add("dirt");throw new IllegalArgumentException("injected");}),render(()->events.add("shade"))));
            }
            assertEquals(java.util.List.of("dirt"),events);
            plan.close();assertThrows(IllegalStateException.class,()->build(plan,0,null,null));
            try(var fresh=builder.packetPlan(0,0)){assertEquals(64,build(fresh,0,null,null).packedVertexBuffers().getFirst().length);}
        }
    }
}
