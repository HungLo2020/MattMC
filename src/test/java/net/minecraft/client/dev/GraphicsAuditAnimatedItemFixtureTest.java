package net.minecraft.client.dev;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditAnimatedItemFixtureTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void magmaFixtureHasOneAnimatedBlockAndNoOtherInventoryWitnesses() {
        var items = GraphicsAuditAnimatedItemFixture.items();
        assertEquals(9, items.size());
        assertTrue(items.getFirst().is(Blocks.MAGMA_BLOCK.asItem()));
        assertEquals(1, items.getFirst().getCount());
        assertTrue(items.subList(1, 9).stream().allMatch(stack -> stack.isEmpty()));
        assertThrows(UnsupportedOperationException.class, () -> items.clear());
        items.getFirst().setCount(3);
        assertEquals(1, GraphicsAuditAnimatedItemFixture.items().getFirst().getCount(),
            "each copied run must receive a new owned item stack");
    }
}
