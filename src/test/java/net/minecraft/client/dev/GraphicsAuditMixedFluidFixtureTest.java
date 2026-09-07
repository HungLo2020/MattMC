package net.minecraft.client.dev;

import java.util.HashSet;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditMixedFluidFixtureTest {
    @Test
    void allOrientationsHaveTheSameSealedSourceWaterAndGlassArrangement() {
        for (Direction forward : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos target = new BlockPos(146, 99, 532);
            var cells = GraphicsAuditMixedFluidFixture.cells(target, forward);
            assertEquals(64, cells.size());
            assertEquals(64, new HashSet<>(cells.stream().map(GraphicsAuditMixedFluidFixture.Cell::position).toList()).size());
            var byPosition = cells.stream().collect(Collectors.toMap(GraphicsAuditMixedFluidFixture.Cell::position, cell -> cell));
            assertEquals(5, cells.stream().filter(GraphicsAuditMixedFluidFixture.Cell::water).count());
            assertFalse(byPosition.get(target).water());
            assertFalse(byPosition.get(target.above()).water());
            assertFalse(byPosition.get(target.relative(forward).relative(forward.getClockWise())).water());
            for (var cell : cells) {
                if (cell.water()) {
                    for (Direction direction : Direction.values()) {
                        assertNotNull(byPosition.get(cell.position().relative(direction)),
                            "every water neighbor must be another source or the sealed glass shell");
                    }
                }
            }
            assertThrows(UnsupportedOperationException.class, () -> cells.clear());
        }
    }

    @Test
    void verticalOrientationsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> GraphicsAuditMixedFluidFixture.cells(BlockPos.ZERO, Direction.UP));
    }
}
