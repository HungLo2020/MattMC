package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhQuadBuilderTest {
    @Test @Tag("parity")
    void replayActualFrozenTopologyAndVertexBytes() throws Exception {
        int oldCurve=Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
        boolean oldErrors=Config.Client.Advanced.Debugging.showOverlappingQuadErrors.get();
        var field=LodQuadBuilder.class.getDeclaredField("nativeQuads");field.setAccessible(true);
        var put=LodQuadBuilder.class.getDeclaredMethod("putQuad",ByteBuffer.class,BufferQuad.class);put.setAccessible(true);
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/rendering/frozen-dh-quads.bin.gz"))))) {
            assertEquals(0x44485131,in.readInt());assertTrue(in.readUTF().startsWith("Actual Frozen"));
            int pins=in.readInt();for(int i=0;i<pins;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
            int cases=in.readInt();assertEquals(512,cases);
            for(int c=0;c<cases;c++) {
                boolean trans=in.readBoolean(),curve=in.readBoolean(),errors=in.readBoolean(),merge=in.readBoolean();
                Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(curve?100:0);
                Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(errors);
                try(var builder=new LodQuadBuilder(trans,null)) {
                    int size=in.readInt();for(int i=0;i<size;i++){
                        short[] q=new short[9];for(int j=0;j<9;j++)q[j]=in.readShort();
                        add(builder,q,0,(byte)0,0);
                    }
                    if(merge){builder.mergeQuads();builder.mergeQuads();}
                    var owner=(NativeDhQuadBuilder)field.get(builder);assertNotNull(owner);
                    var expectedPackets=new ByteArrayOutputStream[4];for(int i=0;i<4;i++)expectedPackets[i]=new ByteArrayOutputStream();
                    for(int layer=0;layer<2;layer++)for(int dir=0;dir<6;dir++){
                        var rows=owner.snapshot(layer,dir);assertEquals(in.readInt(),rows.size(),"case "+c+" layer "+layer+" dir "+dir);
                        for(var q:rows){
                            assertEquals(in.readShort(),q.x);assertEquals(in.readShort(),q.y);assertEquals(in.readShort(),q.z);
                            assertEquals(in.readShort(),q.widthEastWest);assertEquals(in.readShort(),q.widthNorthSouthOrUpDown);
                            assertEquals(in.readInt(),q.color);assertEquals(in.readByte(),q.irisBlockMaterialId);
                            assertEquals(in.readByte(),q.skyLight);assertEquals(in.readByte(),q.blockLight);
                            assertEquals(in.readByte(),q.direction.ordinal());assertEquals(in.readBoolean(),q.hasError);
                            byte[] expected=in.readNBytes(64);
                            int route=layer==0?0:dir!=1?1:q.irisBlockMaterialId==12?3:2;expectedPackets[route].write(expected);
                            var bytes=ByteBuffer.allocate(64).order(ByteOrder.nativeOrder());
                            put.invoke(builder,bytes,q);assertArrayEquals(expected,bytes.array(),"Frozen vertices case "+c);
                        }
                    }
                    var actual=new LodQuadBuilder.SemanticVertexBufferBuild[]{builder.makeOpaqueRustSemanticBuffers(),builder.makeTransparentRustSemanticBuffers(),builder.makeTransparentUpRustSemanticBuffers(),builder.makeTransparentWaterUpRustSemanticBuffers()};
                    for(int route=0;route<4;route++) {var flattened=new ByteArrayOutputStream();for(byte[] bytes:actual[route].packedVertexBuffers())flattened.write(bytes);assertArrayEquals(expectedPackets[route].toByteArray(),flattened.toByteArray(),"native packets case "+c+" route "+route);}
                }
            }
            assertEquals(-1,in.read());
        } finally {
            Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(oldCurve);
            Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(oldErrors);
        }
    }
    private static void add(LodQuadBuilder b,short[] q,int identity,byte variant,long position) {
        var dir=EDhDirection.values()[q[0]];int color=(q[7]<<24)|0x336699;
        if(dir==EDhDirection.UP)b.addQuadUp(q[1],q[2],q[3],q[4],q[5],color,(byte)q[6],(byte)15,(byte)q[8],identity,variant,position);
        else if(dir==EDhDirection.DOWN)b.addQuadDown(q[1],q[2],q[3],q[4],q[5],color,(byte)q[6],(byte)15,(byte)q[8],identity,variant,position);
        else b.addQuadAdj(dir,q[1],q[2],q[3],q[4],q[5],color,(byte)q[6],(byte)15,(byte)q[8],identity,variant,position);
    }
    @Test
    void currentSemanticCompanionsMatchActualCompatibilityBuilder() {
        class Compatibility extends LodQuadBuilder { Compatibility(){super(true,null);} }
        for(int seed=0;seed<64;seed++){
            var r=new Random(seed);
            try(var nativeBuilder=new LodQuadBuilder(true,null);var javaBuilder=new Compatibility()) {
                for(int i=0;i<256;i++){
                    short[] q={(short)(i%6),(short)(i/6%16),(short)(i/96),(short)1,1,1,(short)(i%7),255,15};
                    int identity=r.nextInt(4)-1;byte variant=(byte)r.nextInt(3);long pos=r.nextInt(2);
                    add(nativeBuilder,q,identity,variant,pos);add(javaBuilder,q,identity,variant,pos);
                }
                nativeBuilder.mergeQuads();javaBuilder.mergeQuads();
                assertEquals(javaBuilder.semanticQuadCoverage(),nativeBuilder.semanticQuadCoverage());
                assertBuild(javaBuilder.makeOpaqueRustSemanticBuffers(),nativeBuilder.makeOpaqueRustSemanticBuffers());
                assertBuild(javaBuilder.makeTransparentRustSemanticBuffers(),nativeBuilder.makeTransparentRustSemanticBuffers());
                assertBuild(javaBuilder.makeTransparentUpRustSemanticBuffers(),nativeBuilder.makeTransparentUpRustSemanticBuffers());
                assertBuild(javaBuilder.makeTransparentWaterUpRustSemanticBuffers(),nativeBuilder.makeTransparentWaterUpRustSemanticBuffers());
            }
        }
    }
    private static void assertBuild(LodQuadBuilder.SemanticVertexBufferBuild a,LodQuadBuilder.SemanticVertexBufferBuild b) {
        assertEquals(a.packedVertexBuffers().size(),b.packedVertexBuffers().size());
        for(int i=0;i<a.packedVertexBuffers().size();i++) {
            assertArrayEquals(a.packedVertexBuffers().get(i),b.packedVertexBuffers().get(i));
            assertArrayEquals(a.semanticMaterialIds().get(i),b.semanticMaterialIds().get(i));
            assertArrayEquals(a.semanticVariantStates().get(i),b.semanticVariantStates().get(i));
            assertArrayEquals(a.semanticVariantPositions().get(i),b.semanticVariantPositions().get(i));
        }
    }
    @Test
    void rejectionPrefixAndIdempotentClose() {
        var b=new LodQuadBuilder(false,null);
        assertThrows(IllegalArgumentException.class,()->b.addQuadUp((short)0,(short)0,(short)0,(short)0,(short)1,0,(byte)0,(byte)0,(byte)0));
        assertEquals(0,b.getCurrentOpaqueQuadsCount());assertEquals(0,b.semanticQuadCoverage().unavailable());
        assertThrows(NullPointerException.class,()->b.addQuadAdj(null,(short)0,(short)0,(short)0,(short)0,(short)1,0,(byte)0,(byte)0,(byte)0));
        b.close();b.close();assertThrows(IllegalStateException.class,b::getCurrentOpaqueQuadsCount);
    }
}
