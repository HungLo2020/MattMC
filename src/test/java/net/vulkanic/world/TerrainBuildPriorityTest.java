package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import net.minecraft.core.SectionPos;
import net.sodium.client.render.chunk.RenderSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercise the actual scheduler with an established cache and queued shadow work. */
class TerrainBuildPriorityTest {
    @Test
    void visibleFrontierGetsABoundedBurstWhileNearbyShadowPrefetchStillAdvances() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos[] shadow = {SectionPos.of(0, -2, 0), SectionPos.of(0, -3, 0),
                SectionPos.of(0, -4, 0), SectionPos.of(0, -5, 0)};
        SectionPos[] visible = {SectionPos.of(4, 0, 0), SectionPos.of(5, 0, 0), SectionPos.of(6, 0, 0)};
        for (SectionPos section : shadow) pending(source).add(section);
        for (SectionPos section : visible) {
            pending(source).add(section);
            incoming(source).put(section.asLong(), 1);
        }
        assertEquals(shadow[0], poll(source));
        for (SectionPos section : visible) assertEquals(section, poll(source));
        assertEquals(shadow[1], poll(source));
        assertEquals(2, pending(source).size());
    }

    @Test
    void aTurnRedirectsTheForegroundBurstWithoutDiscardingNearOldViewWork() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos nearby = SectionPos.of(0, -1, 0);
        SectionPos oldView = SectionPos.of(2, 0, 0);
        SectionPos newView = SectionPos.of(-4, 0, 0);
        pending(source).add(nearby);
        pending(source).add(oldView);
        incoming(source).put(oldView.asLong(), 1);
        assertEquals(nearby, poll(source));
        var reset = RustGalWholeFrameTerrainSource.class.getDeclaredMethod("resetVisibilityFrontier");
        reset.setAccessible(true);
        reset.invoke(source);
        incoming(source).put(newView.asLong(), 1);
        pending(source).add(newView);
        assertEquals(newView, poll(source));
        assertEquals(oldView, poll(source));
    }

    @Test
    void newlyVisibleTerrainDoesNotWaitBehindOlderShadowWorkInAnEstablishedCache() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos shadow = SectionPos.of(-9, 0, 0);
        SectionPos visible = SectionPos.of(2, 0, 0);
        pending(source).add(shadow);
        pending(source).add(visible);
        incoming(source).put(visible.asLong(), 1);
        assertEquals(visible, poll(source));
        assertEquals(shadow, poll(source));
    }

    @Test
    void nearPortalFrontierPrecedesDistantShadowWorkDuringBootstrap() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(0);
        SectionPos shadow = SectionPos.of(-9, 0, 0);
        SectionPos visible = SectionPos.of(3, 0, 0);
        pending(source).add(shadow);
        pending(source).add(visible);
        incoming(source).put(visible.asLong(), 1);
        assertEquals(visible, poll(source));
    }

    @Test
    void shadowPrefetchBuildsNearbySurfacesBeforeDeepUndergroundSectionsInTheSameColumn() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos underground = SectionPos.of(0, -4, 0);
        SectionPos nearbySurface = SectionPos.of(-1, 0, 0);
        pending(source).add(underground);
        pending(source).add(nearbySurface);
        assertEquals(nearbySurface, poll(source));
        assertEquals(underground, poll(source));
    }

    @Test
    void currentPortalFrontierBuildsNearTerrainBeforeFarTerrainRegardlessOfArrivalOrder() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos far = SectionPos.of(9, 0, 0);
        SectionPos near = SectionPos.of(1, 0, 0);
        pending(source).add(far);
        pending(source).add(near);
        incoming(source).put(far.asLong(), 1);
        incoming(source).put(near.asLong(), 1);
        assertEquals(near, poll(source));
        assertEquals(far, poll(source));
    }

    @Test
    void residentBlockEditsKeepTheirUrgentPriorityAheadOfNewTerrain() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos edit = SectionPos.of(5, 0, 0);
        SectionPos visible = SectionPos.of(1, 0, 0);
        resident(source).put(edit.asLong(), new RenderSection(null, 5, 0, 0));
        pending(source).addFirst(edit);
        pending(source).addLast(visible);
        incoming(source).put(visible.asLong(), 1);
        assertEquals(edit, poll(source));
    }

    @Test
    void cameraTurnUsesTheNewPortalDomainAndPreservesBackgroundWork() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos oldView = SectionPos.of(2, 0, 0);
        SectionPos newView = SectionPos.of(-2, 0, 0);
        pending(source).add(oldView);
        pending(source).add(newView);
        incoming(source).put(oldView.asLong(), 1);
        var reset = RustGalWholeFrameTerrainSource.class.getDeclaredMethod("resetVisibilityFrontier");
        reset.setAccessible(true);
        reset.invoke(source);
        incoming(source).put(newView.asLong(), 1);
        assertEquals(newView, poll(source));
        assertEquals(oldView, poll(source));
        assertEquals(0, pending(source).size());
    }

    @Test
    void closeTerrainBehindTheCameraIsPreparedBeforeDistantCurrentViewWork() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos distantView = SectionPos.of(9, 0, 0);
        SectionPos closeBehind = SectionPos.of(-2, -2, 0);
        pending(source).add(distantView);
        pending(source).add(closeBehind);
        incoming(source).put(distantView.asLong(), 1);
        assertEquals(closeBehind, poll(source));
        assertEquals(distantView, poll(source));
    }

    @Test
    void equalDistancePrefersTheCurrentCameraPortalAndPreservesBackgroundWork() throws Exception {
        RustGalWholeFrameTerrainSource source = sourceWithResidentSections(256);
        SectionPos shadow = SectionPos.of(-3, 0, 0);
        SectionPos visible = SectionPos.of(3, 0, 0);
        pending(source).add(shadow);
        pending(source).add(visible);
        incoming(source).put(visible.asLong(), 1);
        assertEquals(visible, poll(source));
        assertEquals(shadow, poll(source));
    }

    private static RustGalWholeFrameTerrainSource sourceWithResidentSections(int count) throws Exception {
        RustGalWholeFrameTerrainSource source = new RustGalWholeFrameTerrainSource();
        Field camera = RustGalWholeFrameTerrainSource.class.getDeclaredField("lastCameraSection");
        camera.setAccessible(true);
        camera.setLong(source, SectionPos.asLong(0, 0, 0));
        for (int i = 0; i < count; i++) {
            RenderSection section = new RenderSection(null, 1000 + i, 0, 0);
            resident(source).put(section.getPositionAsLong(), section);
        }
        return source;
    }

    private static SectionPos poll(RustGalWholeFrameTerrainSource source) throws Exception {
        var method = RustGalWholeFrameTerrainSource.class.getDeclaredMethod("pollNearestPending");
        method.setAccessible(true);
        return (SectionPos) method.invoke(source);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(RustGalWholeFrameTerrainSource source, String name) throws Exception {
        Field field = RustGalWholeFrameTerrainSource.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(source);
    }

    private static ArrayDeque<SectionPos> pending(RustGalWholeFrameTerrainSource source) throws Exception {
        return field(source, "pending");
    }

    private static Long2IntOpenHashMap incoming(RustGalWholeFrameTerrainSource source) throws Exception {
        return field(source, "incomingDirections");
    }

    private static Long2ObjectOpenHashMap<RenderSection> resident(RustGalWholeFrameTerrainSource source) throws Exception {
        return field(source, "sections");
    }
}
