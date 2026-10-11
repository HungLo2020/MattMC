package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhBuiltColumnTest {
    @Test @Tag("parity") void actualFrozenColumnBytesAndOneOrderedCallbackTimeline() throws Exception {
        int oldCurve=Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
        boolean oldErrors=Config.Client.Advanced.Debugging.showOverlappingQuadErrors.get();
        final float[] shades={.3f,1f,2f,-1f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MIN_VALUE,Float.MAX_VALUE};
        final int[] colors={0x80123456,0xff00ff00,0,0xffffffff,0x12345678};
        class Injected extends RuntimeException {}
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/rendering/frozen-dh-column-packets.bin.gz"))))) {
            assertEquals(0x44484331,in.readInt());assertEquals(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN,in.readBoolean());
            assertEquals(7,in.readInt());for(int i=0;i<7;i++){in.readUTF();assertEquals(64,in.readUTF().length());}assertEquals(96,in.readInt());
            for(int c=0;c<96;c++) {
                boolean transparency=in.readBoolean(),debug=in.readBoolean(),errors=in.readBoolean();int mode=in.readInt();
                Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
                Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(errors);
                try(var source=new NativeDhQuadBuilder(transparency)) {
                    int faces=in.readInt();for(int i=0;i<faces;i++) {
                        var direction=EDhDirection.values()[in.readShort()];short x=in.readShort(),y=in.readShort(),z=in.readShort(),w=in.readShort(),h=in.readShort();int color=in.readInt();byte material=in.readByte(),sky=in.readByte(),block=in.readByte();
                        source.add(direction,x,y,z,w,h,color,material,sky,block,0,(byte)0,0,direction.ordinal()>=2);
                    }
                    source.merge();int grass=debug||mode==0?0:mode==1?2:mode==2?1:3;
                    for(int fail:new int[]{-1,0,1,3,11}) {
                        boolean failed=in.readBoolean();byte[][] expected=new byte[4][];for(int layer=0;layer<4;layer++)expected[layer]=in.readNBytes(in.readInt());int n=in.readInt();var wanted=new ArrayList<String>();for(int i=0;i<n;i++)wanted.add(in.readUTF());
                        var events=new ArrayList<String>();int[] calls={0},dirt={0},shade={0};
                        var level=(IClientLevelWrapper)Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),new Class<?>[]{IClientLevelWrapper.class},(p,m,a)->{if(m.getName().equals("getDirtBlockColor")){events.add("dirt");if(calls[0]++==fail)throw new Injected();return colors[dirt[0]++%colors.length];}return null;});
                        var render=(IMinecraftRenderWrapper)Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),new Class<?>[]{IMinecraftRenderWrapper.class},(p,m,a)->{if(m.getName().equals("getShade")){events.add("shade:"+((EDhDirection)a[0]).ordinal());if(calls[0]++==fail)throw new Injected();return shades[shade[0]++%shades.length];}return null;});
                        try(var column=new NativeDhBuiltColumn()) {
                            Runnable build=()->{for(int route=0;route<4;route++)try(var plan=source.packetPlan(route,grass)){plan.appendTo(column,route,level,render);}};
                            if(failed){assertThrows(Injected.class,build::run);assertThrows(IllegalStateException.class,()->column.validatePublication(1));}
                            else {build.run();column.validatePublication(1);for(int layer=0;layer<4;layer++){var flat=new ByteArrayOutputStream();for(byte[] bytes:column.project(layer).packedVertexBuffers())flat.write(bytes);assertArrayEquals(expected[layer],flat.toByteArray(),"case "+c+" layer "+layer);}}
                            assertEquals(wanted,events,"column callback prefix case "+c);
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
    @Test void finalPacketsSurviveSourceCloseAndSplitWithTheirCompanions() {
        try(var source=new NativeDhQuadBuilder(true);var column=new NativeDhBuiltColumn()) {
            for(int i=0;i<16385;i++)source.add(EDhDirection.UP,(short)i,(short)0,(short)0,(short)1,(short)1,0xff336699,(byte)2,(byte)15,(byte)0,i+1,(byte)1,i,false);
            for(int route=0;route<4;route++)try(var plan=source.packetPlan(route,0)){plan.appendTo(column,route,null,null);}
            source.close();column.validatePublication(1);var packets=column.project(0);
            assertEquals(2,packets.packedVertexBuffers().size());assertArrayEquals(new int[]{16385},packets.semanticMaterialIds().get(1));assertArrayEquals(new long[]{16384},packets.semanticVariantPositions().get(1));
            assertEquals(new LodQuadBuilder.SemanticQuadCoverage(16385,0,0,16385,0,0),column.coverage());
            column.close();column.close();assertThrows(IllegalStateException.class,()->column.project(0));
        }
    }
    @Test void incompletePublicationAndRestrictedMaterialFailBeforeLedgerUse() {
        try(var source=new NativeDhQuadBuilder(true);var column=new NativeDhBuiltColumn()) {
            assertThrows(IllegalStateException.class,()->column.validatePublication(1));
            source.add(EDhDirection.UP,(short)0,(short)0,(short)0,(short)1,(short)1,0xffffffff,(byte)16,(byte)15,(byte)0,0,(byte)0,0,false);
            for(int route=0;route<4;route++)try(var plan=source.packetPlan(route,0)){plan.appendTo(column,route,null,null);}
            var error=assertThrows(IllegalArgumentException.class,()->column.validatePublication(1));assertEquals("Distant Horizons LOD vertex is outside Rust semantic bounds",error.getMessage());
        }
    }
}
