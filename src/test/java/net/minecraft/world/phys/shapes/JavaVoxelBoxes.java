package net.minecraft.world.phys.shapes;

/** Literal original box loop, using the same real snapshot and helpers. */
final class JavaVoxelBoxes {
	protected static void forAllBoxes(DiscreteVoxelShape discreteVoxelShape, DiscreteVoxelShape.IntLineConsumer intLineConsumer, boolean bl) {
		BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(discreteVoxelShape);

		for (int i = 0; i < bitSetDiscreteVoxelShape.ySize; i++) {
			for (int j = 0; j < bitSetDiscreteVoxelShape.xSize; j++) {
				int k = -1;

				for (int l = 0; l <= bitSetDiscreteVoxelShape.zSize; l++) {
					if (bitSetDiscreteVoxelShape.isFullWide(j, i, l)) {
						if (bl) {
							if (k == -1) {
								k = l;
							}
						} else {
							intLineConsumer.consume(j, i, l, j + 1, i + 1, l + 1);
						}
					} else if (k != -1) {
						int m = j;
						int n = i;
						bitSetDiscreteVoxelShape.clearZStrip(k, l, j, i);

						while (bitSetDiscreteVoxelShape.isZStripFull(k, l, m + 1, i)) {
							bitSetDiscreteVoxelShape.clearZStrip(k, l, m + 1, i);
							m++;
						}

						while (bitSetDiscreteVoxelShape.isXZRectangleFull(j, m + 1, k, l, n + 1)) {
							for (int o = j; o <= m; o++) {
								bitSetDiscreteVoxelShape.clearZStrip(k, l, o, n + 1);
							}

							n++;
						}

						intLineConsumer.consume(j, i, k, m + 1, n + 1, l);
						k = -1;
					}
				}
			}
		}
	}

}
