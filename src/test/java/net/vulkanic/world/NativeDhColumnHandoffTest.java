package net.vulkanic.world;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDhColumnHandoffTest {
    @AfterEach void clear() {
        System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
        DistantHorizonsSemanticCollector.resetForTest();
    }
    @Test void nativeAndExplicitPacketPublicationReuseTheSameGenerationAndProvenance() {
        System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY,"true");
        try(var builder=new LodQuadBuilder(true,null)) {
            int identity=builder.internSemanticMaterial(new ColumnRenderSource.SemanticMaterialIdentity("minecraft:stone","minecraft:plains"));
            builder.addQuadUp((short)1,(short)2,(short)3,(short)1,(short)1,0xff336699,(byte)2,(byte)15,(byte)0,identity,(byte)1,123);
            LodQuadBuilder.SemanticVertexBufferBuild opaque,side,up,water;
            try(var projected=builder.makeNativeRustSemanticColumn()) {
                opaque=projected.project(0);side=projected.project(1);up=projected.project(2);water=projected.project(3);
            }
            long explicit=DistantHorizonsSemanticCollector.recordRustSemanticBuiltColumn(99201,new DhBlockPos(4,5,6),builder.semanticMaterials(),
                builder.semanticQuadCoverage(),builder.semanticQuadCoverage(),opaque,side,up,water);
            try(var column=builder.makeNativeRustSemanticColumn()) {
                long size=column.byteSize();assertEquals(64,size);
                var nativeLease=DistantHorizonsSemanticCollector.recordOwnedNativeRustSemanticBuiltColumn(99201,new DhBlockPos(4,5,6),builder.semanticMaterials(),builder.semanticQuadCoverage(),column);
                try(nativeLease) {
                    assertEquals(explicit,nativeLease.generation());
                    assertThrows(IllegalStateException.class,()->column.project(0));
                    var captured=DistantHorizonsSemanticCollector.snapshotForTest(99201);
                    assertEquals(4,captured.originX());assertEquals(5,captured.originY());assertEquals(6,captured.originZ());
                    assertArrayEquals(opaque.packedVertexBuffers().getFirst(),captured.opaque().getFirst().packedVerticesForRust());
                }
            }
        }
    }
    @Test void ordinaryPublicationWorksWithoutCaptureAndEmptyBuildDoesNotReplaceItsOwner() {
        var previous=Config.Client.Advanced.Debugging.rendererMode.get();
        Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(EDhApiRendererMode.DEFAULT);
        System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
        System.clearProperty(DistantHorizonsSemanticCollector.LEGACY_OBSERVATION_PROPERTY);
        try(var builder=new LodQuadBuilder(true,null);var empty=new LodQuadBuilder(true,null)) {
            builder.addQuadUp((short)0,(short)1,(short)0,(short)1,(short)1,0xff556677,(byte)2,(byte)15,(byte)0);
            try(var column=builder.makeNativeRustSemanticColumn();var vacant=empty.makeNativeRustSemanticColumn()) {
                var lease=DistantHorizonsSemanticCollector.recordOwnedNativeRustSemanticBuiltColumn(99202,new DhBlockPos(0,64,0),List.of(),builder.semanticQuadCoverage(),column);
                try(lease;var emptyLease=DistantHorizonsSemanticCollector.recordOwnedNativeRustSemanticBuiltColumn(99202,new DhBlockPos(0,64,0),List.of(),empty.semanticQuadCoverage(),vacant)) {
                    assertEquals(0,emptyLease.generation());assertTrue(DistantHorizonsSemanticCollector.hasColumn(99202,lease.generation()));
                    assertEquals(64,DistantHorizonsSemanticCollector.snapshotForTest(99202).byteSize());
                }
                assertFalse(DistantHorizonsSemanticCollector.hasColumn(99202));
            }
        } finally {Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(previous);}
    }
}
