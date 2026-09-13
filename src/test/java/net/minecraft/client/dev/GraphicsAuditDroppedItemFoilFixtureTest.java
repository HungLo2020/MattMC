package net.minecraft.client.dev;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditDroppedItemFoilFixtureTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test
    void frozenVanillaTimerSuppliesExactNonPlayerPartialTick() {
        var timer = new net.minecraft.client.DeltaTracker.Timer(20, 0, value -> value);
        timer.advanceTime(13, true);
        assertNotEquals(1.0F, timer.getGameTimeDeltaPartialTick(false));
        timer.updateFrozenState(true);
        assertEquals(1.0F, timer.getGameTimeDeltaPartialTick(false));
    }
    @Test
    void phaseMutationIsRestrictedAndObservableThroughVanillaField() {
        ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditDroppedItemFoilFixture.pinPhase(entity));
        entity.setId(Integer.MIN_VALUE + 4100);
        entity.tickCount = 250;
        GraphicsAuditDroppedItemFoilFixture.pinPhase(entity);
        assertEquals((float) Math.PI, entity.bobOffs);
        assertEquals(0, entity.tickCount);
    }
    @Test
    void disabledFixtureDoesNotAccessGameAndInvalidCountsReject() {
        String property = GraphicsAuditDroppedItemFoilFixture.PROPERTY;
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "0");
            GraphicsAuditDroppedItemFoilFixture.beforeRender(null);
            assertEquals("null", GraphicsAuditDroppedItemFoilFixture.receipt(null));
            for (int count : new int[] {1,64}) {
                System.setProperty(property, Integer.toString(count));
                assertEquals(count, GraphicsAuditDroppedItemFoilFixture.requestedCount());
            }
            System.setProperty(property, "2");
            assertThrows(IllegalArgumentException.class, GraphicsAuditDroppedItemFoilFixture::requestedCount);
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
        }
    }
    @Test
    void specialFixtureSelectsGameItemsAndRejectsMixedConfiguration() {
        String prior = System.getProperty(GraphicsAuditDroppedItemFoilFixture.SPECIAL_PROPERTY);
        String count = System.getProperty(GraphicsAuditDroppedItemFoilFixture.PROPERTY);
        try {
            System.setProperty(GraphicsAuditDroppedItemFoilFixture.SPECIAL_PROPERTY, "true");
            System.setProperty(GraphicsAuditDroppedItemFoilFixture.PROPERTY, "0");
            assertEquals(1, GraphicsAuditDroppedItemFoilFixture.requestedCount());
            ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
            entity.setId(Integer.MIN_VALUE + 4100);
            GraphicsAuditDroppedItemFoilFixture.pinPhase(entity);
            assertEquals((float)(Math.PI / 2), entity.bobOffs);
            assertEquals(0, entity.tickCount);
            assertSame(net.minecraft.world.item.Items.CLOCK, GraphicsAuditDroppedItemFoilFixture.expectedItem(0));
            assertSame(net.minecraft.world.item.Items.COMPASS, GraphicsAuditDroppedItemFoilFixture.expectedItem(1));
            assertThrows(IllegalArgumentException.class, () -> GraphicsAuditDroppedItemFoilFixture.expectedItem(2));
            System.setProperty(GraphicsAuditDroppedItemFoilFixture.PROPERTY, "1");
            assertThrows(IllegalArgumentException.class, GraphicsAuditDroppedItemFoilFixture::requestedCount);
            System.setProperty(GraphicsAuditDroppedItemFoilFixture.SPECIAL_PROPERTY, "false");
            assertSame(net.minecraft.world.item.Items.DIAMOND, GraphicsAuditDroppedItemFoilFixture.expectedItem(0));
            assertSame(net.minecraft.world.item.Items.STONE, GraphicsAuditDroppedItemFoilFixture.expectedItem(1));
        } finally {
            if (prior == null) System.clearProperty(GraphicsAuditDroppedItemFoilFixture.SPECIAL_PROPERTY);
            else System.setProperty(GraphicsAuditDroppedItemFoilFixture.SPECIAL_PROPERTY, prior);
            if (count == null) System.clearProperty(GraphicsAuditDroppedItemFoilFixture.PROPERTY);
            else System.setProperty(GraphicsAuditDroppedItemFoilFixture.PROPERTY, count);
        }
    }

    @Test
    void recoveryFixtureIsDistinctAndRequiresSpecialScope() {
        String special = GraphicsAuditDroppedItemFoilFixture.SPECIAL_PROPERTY;
        String recovery = GraphicsAuditDroppedItemFoilFixture.RECOVERY_PROPERTY;
        String priorSpecial = System.getProperty(special), priorRecovery = System.getProperty(recovery);
        try {
            System.setProperty(recovery, "true");
            System.setProperty(special, "false");
            assertThrows(IllegalArgumentException.class, GraphicsAuditDroppedItemFoilFixture::requestedCount);
            System.setProperty(special, "true");
            assertEquals(1, GraphicsAuditDroppedItemFoilFixture.requestedCount());
            assertSame(net.minecraft.world.item.Items.CLOCK, GraphicsAuditDroppedItemFoilFixture.expectedItem(0));
            assertSame(net.minecraft.world.item.Items.RECOVERY_COMPASS, GraphicsAuditDroppedItemFoilFixture.expectedItem(1));
            ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
            entity.setId(Integer.MIN_VALUE + 4101);
            GraphicsAuditDroppedItemFoilFixture.pinPhase(entity);
            assertEquals((float)(Math.PI / 2), entity.bobOffs);
            assertEquals(0, entity.tickCount);
        } finally {
            if (priorSpecial == null) System.clearProperty(special); else System.setProperty(special, priorSpecial);
            if (priorRecovery == null) System.clearProperty(recovery); else System.setProperty(recovery, priorRecovery);
        }
    }

    @Test
    void fixturePositionsAreDistinctAndBoundedForVerticalLook() {
        assertEquals(new Vec3(1,1.65,2),
            GraphicsAuditDroppedItemFoilFixture.position(new Vec3(0,2,0),new Vec3(0,0,1),0));
        Vec3 first = GraphicsAuditDroppedItemFoilFixture.position(Vec3.ZERO,new Vec3(0,1,0),0);
        Vec3 second = GraphicsAuditDroppedItemFoilFixture.position(Vec3.ZERO,new Vec3(0,1,0),1);
        assertTrue(first.distanceTo(second) > .6);
        assertTrue(first.length() < 3);
    }
}
