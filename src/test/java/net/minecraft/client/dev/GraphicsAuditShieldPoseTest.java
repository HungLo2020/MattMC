package net.minecraft.client.dev;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditShieldPoseTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void poseSelectionIsExplicitAndRejectsUnknownStates() {
        assertFalse(GraphicsAuditShieldPoseFixture.blockingPose("idle"));
        assertTrue(GraphicsAuditShieldPoseFixture.blockingPose("blocking"));
        assertTrue(GraphicsAuditShieldPoseFixture.blockingPose("blocking-offhand"));
        assertEquals(net.minecraft.world.InteractionHand.OFF_HAND,
            GraphicsAuditShieldPoseFixture.selectedUseHand("blocking-offhand"));
        assertEquals(net.minecraft.world.InteractionHand.MAIN_HAND,
            GraphicsAuditShieldPoseFixture.selectedUseHand("blocking"));
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditShieldPoseFixture.selectedUseHand("other"));
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditShieldPoseFixture.blockingPose("other"));
    }
    @Test void blockingKeepsTheExactUseStackButStillReplacesChangedComponents() {
        String previous = System.getProperty("mattmc.dev.graphicsAuditShieldPose");
        try {
            System.setProperty("mattmc.dev.graphicsAuditShieldPose", "blocking");
            var patterned = GraphicsAuditPatternedShieldFixture.items(true).getFirst();
            assertSame(patterned, GraphicsAuditShieldPoseFixture.fixtureStack(patterned,
                GraphicsAuditPatternedShieldFixture.items(true).getFirst()));
            var held = new ItemStack(Items.SHIELD);
            assertSame(held, GraphicsAuditShieldPoseFixture.fixtureStack(held, held.copy()));
            var changed = held.copy();
            changed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            assertSame(changed, GraphicsAuditShieldPoseFixture.fixtureStack(held, changed));
            System.setProperty("mattmc.dev.graphicsAuditShieldPose", "idle");
            var fresh = held.copy();
            assertSame(fresh, GraphicsAuditShieldPoseFixture.fixtureStack(held, fresh));
        } finally {
            if (previous == null) System.clearProperty("mattmc.dev.graphicsAuditShieldPose");
            else System.setProperty("mattmc.dev.graphicsAuditShieldPose", previous);
        }
    }
}
