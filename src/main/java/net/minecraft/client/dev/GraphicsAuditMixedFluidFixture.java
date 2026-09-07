package net.minecraft.client.dev;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in copied-world fixture only; never changes renderer behavior. */
public final class GraphicsAuditMixedFluidFixture {
    private static final String VERSION = "sealed-mixed-fluid-v1";
    private static List<Cell> installed = List.of();
    private static String placement = "";

    record Cell(BlockPos position, boolean water) {
        BlockState state() { return (water ? Blocks.WATER : Blocks.BLUE_STAINED_GLASS).defaultBlockState(); }
    }

    static List<Cell> cells(BlockPos target, Direction forward) {
        if (forward.getAxis() == Direction.Axis.Y) throw new IllegalArgumentException("horizontal fixture direction required");
        var cells = new ArrayList<Cell>(64);
        Direction right = forward.getClockWise();
        for (int depth = -1; depth <= 2; depth++) {
            for (int lateral = -1; lateral <= 2; lateral++) {
                for (int height = -1; height <= 2; height++) {
                    boolean interior = depth >= 0 && depth <= 1 && lateral >= 0 && lateral <= 1 && height >= 0 && height <= 1;
                    boolean originalGlass = depth == 0 && lateral == 0 || depth == 1 && lateral == 1 && height == 0;
                    cells.add(new Cell(target.relative(forward, depth).relative(right, lateral).above(height),
                        interior && !originalGlass));
                }
            }
        }
        return List.copyOf(cells);
    }

    public static boolean install(Minecraft minecraft, BlockPos target, Direction forward) {
        if (minecraft.level == null || minecraft.getSingleplayerServer() == null) return false;
        var server = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
        if (server == null) return false;
        List<Cell> plan = cells(target, forward);
        // Preflight the entire fixture before any mutation. Build the sealed glass
        // shell first, so ordinary fluid ticks cannot create an FPS-dependent waterfall.
        for (Cell cell : plan) {
            if (!server.isLoaded(cell.position()) || !minecraft.level.isLoaded(cell.position())) return false;
        }
        for (boolean water : new boolean[] { false, true }) {
            for (Cell cell : plan) {
                if (cell.water() == water) {
                    server.setBlock(cell.position(), cell.state(), 3);
                    minecraft.level.setBlock(cell.position(), cell.state(), 3);
                }
            }
        }
        installed = plan;
        placement = target.getX() + "," + target.getY() + "," + target.getZ() + "/" + forward.getName();
        return true;
    }

    public static String receipt(Minecraft minecraft) {
        int matching = 0;
        if (minecraft.level != null && minecraft.getSingleplayerServer() != null) {
            var server = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
            if (server != null) {
                for (Cell cell : installed) {
                    if (minecraft.level.getBlockState(cell.position()).equals(cell.state())
                        && server.getBlockState(cell.position()).equals(cell.state())) matching++;
                }
            }
        }
        return "{\"fixture\":\"" + VERSION + "\",\"placement\":\"" + placement
            + "\",\"cells\":" + installed.size() + ",\"matchingCells\":" + matching
            + ",\"complete\":" + (installed.size() == 64 && matching == 64) + "}";
    }

    private GraphicsAuditMixedFluidFixture() {}
}
