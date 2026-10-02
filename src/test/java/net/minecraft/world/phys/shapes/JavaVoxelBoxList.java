package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Original complete coordinate/callback/list caller with only kernel redirection. */
final class JavaVoxelBoxList {
	static void forAllBoxes(VoxelShape source, Shapes.DoubleLineConsumer doubleLineConsumer) {
		DoubleList doubleList = source.getCoords(Direction.Axis.X);
		DoubleList doubleList2 = source.getCoords(Direction.Axis.Y);
		DoubleList doubleList3 = source.getCoords(Direction.Axis.Z);
		JavaVoxelBoxes.forAllBoxes(source.shape,
				(i, j, k, l, m, n) -> doubleLineConsumer.consume(
					doubleList.getDouble(i), doubleList2.getDouble(j), doubleList3.getDouble(k), doubleList.getDouble(l), doubleList2.getDouble(m), doubleList3.getDouble(n)
				),
				true
			);
	}

	static List<AABB> toAabbs(VoxelShape source) {
		List<AABB> list = Lists.<AABB>newArrayList();
		forAllBoxes(source, (d, e, f, g, h, i) -> list.add(new AABB(d, e, f, g, h, i)));
		return list;
	}

}
