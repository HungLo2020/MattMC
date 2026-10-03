package net.vulkanic.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ExperienceOrbSemanticCollectorTest {
    private static float[] identity() { return new org.joml.Matrix4f().get(new float[16]); }
    private static net.vulkanic.bridge.VulkanicGalBridge.WorldExperienceOrbInstanceRecord enqueue(
            ExperienceOrbSemanticCollector collector, int color, int index) {
        return collector.enqueue(1,color,5,240,identity(),new float[]{0,0,0,1},42,index);
    }

    @Test
    void reusesBoundedSlotsAndReplacesOnlyChangedAppearance() {
        var collector = new ExperienceOrbSemanticCollector();
        var first = enqueue(collector,127,0);
        var publication = collector.dirtyAssets();
        collector.accepted(publication);
        collector.clearFrame();
        var same = enqueue(collector,127,0);
        assertEquals(first.meshKey(),same.meshKey());
        assertEquals(first.meshGeneration(),same.meshGeneration());
        assertTrue(collector.dirtyAssets().isEmpty());
        for (int frame=0;frame<1000;frame++) {
            collector.clearFrame();
            var next = enqueue(collector,frame%256,0);
            assertEquals(first.meshKey(),next.meshKey());
            assertEquals(1,collector.residentCount());
            assertTrue(collector.dirtyAssets().size()<=1);
        }
        collector.accepted(publication);
        assertEquals(1,collector.dirtyAssets().size(),"stale acknowledgment cannot clear a newer generation");
        collector.accepted(collector.dirtyAssets());
        collector.invalidatePublications();
        assertEquals(1,collector.dirtyAssets().size(),"reload republishes retained immutable semantics");
    }

    @Test
    void remapsThroughAdmittedPrefixAndKeepsEqualIndexOrder() {
        var collector = new ExperienceOrbSemanticCollector();
        enqueue(collector,1,0);
        enqueue(collector,2,2);
        enqueue(collector,3,2);
        var mapped = collector.remap(new int[]{0,1,1,2});
        assertEquals(java.util.List.of(0,1,1),mapped.stream().map(x->x.meshIndex()).toList());
        assertNotEquals(mapped.get(1).meshKey(),mapped.get(2).meshKey());
        collector.clearFrame();
        assertTrue(collector.pendingInstances().isEmpty());
    }

    @Test
    void nonFinitePlacementCannotPublishOrMutateResidency() {
        var collector = new ExperienceOrbSemanticCollector();
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int index=0; index<16; index++) {
                float[] pose = identity();
                pose[index] = invalid;
                assertThrows(IllegalArgumentException.class, () -> collector.enqueue(
                    1,127,5,240,pose,new float[]{0,0,0,1},42,0));
            }
            for (int index=0; index<4; index++) {
                float[] rotation = {0,0,0,1};
                rotation[index] = invalid;
                assertThrows(IllegalArgumentException.class, () -> collector.enqueue(
                    1,127,5,240,identity(),rotation,42,0));
            }
            assertEquals(0,collector.residentCount());
            assertTrue(collector.dirtyAssets().isEmpty());
            assertTrue(collector.pendingInstances().isEmpty());
        }
        assertEquals(1,enqueue(collector,127,0).meshGeneration(),
            "rejected placement must not consume a resource generation");
    }

    @Test
    void suffixCompactionPreservesEqualOrderMetadataAndAppearanceResidency() {
        var collector = new ExperienceOrbSemanticCollector();
        var camera = enqueue(collector,1,0);
        var boundary = enqueue(collector,2,1);
        var culling = new net.vulkanic.bridge.VulkanicGalBridge.WorldEntityCullingRecord(1,
            new net.vulkanic.bridge.VulkanicGalBridge.WorldAabbRecord(-1,-1,-1,1,1,1),null);
        var shadow = collector.enqueue(1,3,5,240,identity(),new float[]{0,0,0,1},43,3,culling,true);
        var sibling = collector.enqueue(1,4,5,240,identity(),new float[]{0,0,0,1},44,3,culling,true);
        var tail = collector.enqueue(1,5,5,240,identity(),new float[]{0,0,0,1},45,5,culling,true);
        var assets = collector.dirtyAssets();
        collector.remapCollectedMeshSuffix(1,new int[]{0,1,1,2,2});
        var mapped = collector.pendingInstances();
        assertEquals(java.util.List.of(0,1,2,2,3),mapped.stream().map(x->x.meshIndex()).toList());
        assertSame(camera,mapped.get(0));
        assertSame(boundary,mapped.get(1));
        assertEquals(shadow.meshKey(),mapped.get(2).meshKey());
        assertEquals(sibling.meshKey(),mapped.get(3).meshKey());
        assertEquals(tail.meshKey(),mapped.get(4).meshKey());
        assertEquals(shadow.entityCulling(),mapped.get(2).entityCulling());
        assertTrue(mapped.get(2).shadowOnly());
        assertArrayEquals(shadow.entityTransform(),mapped.get(2).entityTransform());
        assertEquals(3,shadow.meshIndex());
        assertEquals(assets,collector.dirtyAssets());
        assertEquals(5,collector.residentCount());
        assertEquals(java.util.List.of(0,1,1,1,2),collector.remap(new int[]{0,1,1,2})
            .stream().map(x->x.meshIndex()).toList());
    }

    @Test
    void rejectedSuffixCompactionDoesNotPublishPartialOrdering() {
        var collector = new ExperienceOrbSemanticCollector();
        enqueue(collector,1,2);
        enqueue(collector,2,6);
        var before = collector.pendingInstances();
        assertThrows(IllegalStateException.class,()->collector.remapCollectedMeshSuffix(1,new int[]{0,0,1}));
        assertEquals(before,collector.pendingInstances());
        for (int[] invalid : new int[][]{{},{1},{0,-1},{0,2},{0,1,0}}) {
            assertThrows(IllegalArgumentException.class,()->collector.remapCollectedMeshSuffix(1,invalid));
            assertEquals(before,collector.pendingInstances());
        }
    }

    @Test
    void invalidPublicationIsTransactionalAndSlotsHaveAHardLimit() {
        var collector = new ExperienceOrbSemanticCollector();
        assertThrows(IllegalArgumentException.class,()->enqueue(collector,256,0));
        assertEquals(0,collector.residentCount());
        assertTrue(collector.dirtyAssets().isEmpty());
        for (int i=0;i<ExperienceOrbSemanticCollector.MAX_SLOTS;i++) enqueue(collector,1,0);
        assertThrows(IllegalStateException.class,()->enqueue(collector,1,0));
        assertEquals(ExperienceOrbSemanticCollector.MAX_SLOTS,collector.residentCount());
        assertTrue(ExperienceOrbSemanticCollector.ownsKey(collector.pendingInstances().getFirst().meshKey()));
    }
}
