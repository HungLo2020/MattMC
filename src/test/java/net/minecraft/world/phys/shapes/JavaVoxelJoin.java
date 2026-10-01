package net.minecraft.world.phys.shapes;

/** Literal original complete grid join, pinned to Git by the verification driver. */
final class JavaVoxelJoin {
	static BitSetDiscreteVoxelShape join(
		DiscreteVoxelShape discreteVoxelShape,
		DiscreteVoxelShape discreteVoxelShape2,
		IndexMerger indexMerger,
		IndexMerger indexMerger2,
		IndexMerger indexMerger3,
		BooleanOp booleanOp
	) {
		BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(indexMerger.size() - 1, indexMerger2.size() - 1, indexMerger3.size() - 1);
		int[] is = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		indexMerger.forMergedIndexes((i, j, k) -> {
			boolean[] bls = new boolean[]{false};
			indexMerger2.forMergedIndexes((l, m, n) -> {
				boolean[] bls2 = new boolean[]{false};
				indexMerger3.forMergedIndexes((o, p, q) -> {
					if (booleanOp.apply(discreteVoxelShape.isFullWide(i, l, o), discreteVoxelShape2.isFullWide(j, m, p))) {
						bitSetDiscreteVoxelShape.storage.set(bitSetDiscreteVoxelShape.getIndex(k, n, q));
						is[2] = Math.min(is[2], q);
						is[5] = Math.max(is[5], q);
						bls2[0] = true;
					}

					return true;
				});
				if (bls2[0]) {
					is[1] = Math.min(is[1], n);
					is[4] = Math.max(is[4], n);
					bls[0] = true;
				}

				return true;
			});
			if (bls[0]) {
				is[0] = Math.min(is[0], k);
				is[3] = Math.max(is[3], k);
			}

			return true;
		});
		bitSetDiscreteVoxelShape.xMin = is[0];
		bitSetDiscreteVoxelShape.yMin = is[1];
		bitSetDiscreteVoxelShape.zMin = is[2];
		bitSetDiscreteVoxelShape.xMax = is[3] + 1;
		bitSetDiscreteVoxelShape.yMax = is[4] + 1;
		bitSetDiscreteVoxelShape.zMax = is[5] + 1;
		return bitSetDiscreteVoxelShape;
	}

}
