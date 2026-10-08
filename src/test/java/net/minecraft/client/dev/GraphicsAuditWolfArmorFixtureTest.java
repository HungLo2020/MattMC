package net.minecraft.client.dev;

import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditWolfArmorFixtureTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void ordinaryDamageSelectsEveryActualCrackLayer() {
        for (String mode : new String[]{"none", "low", "medium", "high"}) {
            var stack = GraphicsAuditWolfArmorFixture.armor(mode);
            assertEquals(Crackiness.Level.valueOf(mode.toUpperCase(java.util.Locale.ROOT)), Crackiness.WOLF_ARMOR.byDamage(stack));
            assertTrue(GraphicsAuditWolfArmorFixture.armorMatches(stack, mode));
            stack.setCount(2);assertFalse(GraphicsAuditWolfArmorFixture.armorMatches(stack, mode));
            stack.setCount(1);stack.setDamageValue(stack.getDamageValue()+1);
            assertFalse(GraphicsAuditWolfArmorFixture.armorMatches(stack, mode));
        }
        assertFalse(GraphicsAuditWolfArmorFixture.armorMatches(Items.DIAMOND_CHESTPLATE.getDefaultInstance(), "high"));
        assertThrows(IllegalArgumentException.class, () -> GraphicsAuditWolfArmorFixture.armor("invalid"));
    }
}
