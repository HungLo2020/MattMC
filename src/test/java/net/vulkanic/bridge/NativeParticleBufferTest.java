package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.vulkanic.bridge.VulkanicGalBridge.*;

class NativeParticleBufferTest {
    private static WorldParticleQuadRecord particle(int i) {
        return new WorldParticleQuadRecord(17,ParticleSurface.values()[i%5],0,
            new float[]{i+.125F,-2,3},new float[]{.1F,.2F,.3F,1},-.5F,
            new float[]{1,0,.25F,.75F},0x80402010,240);
    }
    @Test void growthSnapshotAndEncodingMatchTheExistingSemanticAbi() {
        try(var source=new NativeParticleBuffer();var arena=Arena.ofConfined()) {
            var expected=new ArrayList<WorldParticleQuadRecord>();
            for(int i=0;i<257;i++){var p=particle(i);expected.add(p);source.add(p);}
            try(var snapshot=source.snapshot()) {
                source.clear();source.add(particle(999));
                assertEquals(257,snapshot.size());
                var old=encodeParticleQuads(arena,expected);var fresh=snapshot.encode(arena,List.of());
                assertEquals(-1,old.mismatch(fresh));
                assertThrows(UnsupportedOperationException.class,snapshot::clear);
                assertSame(snapshot,NativeParticleBuffer.immutableCopy(snapshot));
                assertEquals(expected.get(0).center()[0],snapshot.get(0).center()[0]);
            }
        }
    }
    @Test void layerImportFailureAndRollbackPreserveCompletedPrefix() {
        try(var input=new NativeParticleBuffer();var frame=new NativeParticleBuffer()) {
            input.add(particle(0));
            assertEquals(1,frame.appendLayer(input,25,4,2));
            input.append(1,0,0,0,0,0,0,0,0,0,1,0,1,0,1,-1,0);
            assertThrows(IllegalArgumentException.class,()->frame.appendLayer(input,25,4,2));
            assertEquals(1,frame.size());assertEquals(25,frame.textureId(0));assertEquals(4,frame.surface(0));
            assertEquals(2,frame.materialIndex(0));
            frame.subList(0,frame.size()).clear();assertTrue(frame.isEmpty());
        }
    }
    @Test void closedSnapshotsAndCombinedQueueLimitAreEnforced() {
        var source=new NativeParticleBuffer(()->2);
        try {
            source.add(particle(0));source.add(particle(1));
            assertThrows(IllegalStateException.class,()->source.add(particle(2)));
            var snapshot=source.snapshot();snapshot.close();snapshot.close();
            assertThrows(IllegalStateException.class,snapshot::size);
            assertThrows(IllegalStateException.class,()->snapshot.get(0));
            assertEquals(2,source.size());
        } finally {source.close();}
    }
}
