package net.minecraft.world.level.levelgen.feature;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Literal original streaming numeric loop; no added output allocation. */
final class JavaGeodeFields {
    interface Consumer { void accept(BlockPos pos, double body, double crack); }
    static void evaluate(GeodeFixtures.Grid grid, Consumer consumer) {
        NormalNoise normalNoise=grid.noise();BlockPos blockPos=grid.origin();
        int i=grid.min(),j=grid.max(),crackOffset=grid.crackOffset();
        double multiplier=grid.multiplier();List<Pair<BlockPos,Integer>> list=grid.points();List<BlockPos> list2=grid.cracks();
        for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos.offset(i, i, i), blockPos.offset(j, j, j))) {
			double r = normalNoise.getValue(blockPos3.getX(), blockPos3.getY(), blockPos3.getZ()) * multiplier;
			double s = 0.0;
			double t = 0.0;

			for (Pair<BlockPos, Integer> pair : list) {
				s += Mth.invSqrt(blockPos3.distSqr(pair.getFirst()) + pair.getSecond().intValue()) + r;
			}

			for (BlockPos blockPos4 : list2) {
				t += Mth.invSqrt(blockPos3.distSqr(blockPos4) + crackOffset) + r;
			}

            consumer.accept(blockPos3,s,t);
        }
    }
}
