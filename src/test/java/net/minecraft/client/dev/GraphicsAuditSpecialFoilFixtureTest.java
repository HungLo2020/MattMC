package net.minecraft.client.dev;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GraphicsAuditSpecialFoilFixtureTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void fixtureUsesRealSpecialFoilItemsAndAnUnfoiledHeldControl() {
        var items=GraphicsAuditSpecialFoilFixture.items();
        assertEquals(9,items.size());
        for(int slot=0;slot<9;slot++) {
            var expected=slot==0 ? net.minecraft.world.item.Items.APPLE :
                slot%2==1 ? net.minecraft.world.item.Items.CLOCK : net.minecraft.world.item.Items.COMPASS;
            assertTrue(items.get(slot).is(expected));
            assertEquals(1,items.get(slot).getCount());
            assertEquals(slot>0,items.get(slot).hasFoil());
        }
        assertThrows(UnsupportedOperationException.class,items::clear);
        items.get(1).setCount(9);
        assertEquals(1,GraphicsAuditSpecialFoilFixture.items().get(1).getCount());
    }
    @Test void recoveryInventoryUsesRealRecoveryCompassAndPreservesOrdinaryFixture() {
        var items=GraphicsAuditSpecialFoilFixture.items(true);
        assertEquals(9, items.size());
        for (int slot=0; slot<9; slot++) {
            assertTrue(items.get(slot).is(slot==0 ? net.minecraft.world.item.Items.APPLE
                : slot%2==1 ? net.minecraft.world.item.Items.CLOCK : net.minecraft.world.item.Items.RECOVERY_COMPASS));
            assertEquals(1, items.get(slot).getCount());
            assertEquals(slot>0, items.get(slot).hasFoil());
        }
        assertTrue(GraphicsAuditSpecialFoilFixture.items().get(2).is(net.minecraft.world.item.Items.COMPASS));
    }

    @Test void recoveryTargetRestoresOriginalDataAfterRepeatedApplyAndOwnerChange() {
        var first=org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var second=org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var original=java.util.Optional.of(net.minecraft.core.GlobalPos.of(
            net.minecraft.world.level.Level.NETHER, new net.minecraft.core.BlockPos(4, 5, 6)));
        var firstState=new java.util.concurrent.atomic.AtomicReference<>(original);
        var secondState=new java.util.concurrent.atomic.AtomicReference<java.util.Optional<net.minecraft.core.GlobalPos>>(java.util.Optional.empty());
        org.mockito.Mockito.when(first.getLastDeathLocation()).thenAnswer(call -> firstState.get());
        org.mockito.Mockito.when(second.getLastDeathLocation()).thenAnswer(call -> secondState.get());
        org.mockito.Mockito.doAnswer(call -> { firstState.set(call.getArgument(0)); return null; })
            .when(first).setLastDeathLocation(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(call -> { secondState.set(call.getArgument(0)); return null; })
            .when(second).setLastDeathLocation(org.mockito.ArgumentMatchers.any());
        try {
            GraphicsAuditSpecialFoilFixture.applyRecoveryTarget(first);
            GraphicsAuditSpecialFoilFixture.applyRecoveryTarget(first);
            assertEquals(java.util.Optional.of(GraphicsAuditSpecialFoilFixture.RECOVERY_TARGET), firstState.get());
            GraphicsAuditSpecialFoilFixture.applyRecoveryTarget(second);
            assertEquals(original, firstState.get());
            assertEquals(java.util.Optional.of(GraphicsAuditSpecialFoilFixture.RECOVERY_TARGET), secondState.get());
            GraphicsAuditSpecialFoilFixture.restoreRecoveryTarget();
            assertEquals(java.util.Optional.empty(), secondState.get());
            GraphicsAuditSpecialFoilFixture.restoreRecoveryTarget();
            assertEquals(original, firstState.get());
        } finally {
            GraphicsAuditSpecialFoilFixture.restoreRecoveryTarget();
        }
    }
}
