package net.minecraft.world.level.levelgen.feature;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.Mth;
/** Extracted verbatim loops from JavaOreFeature, with geometry output collected
 * at the original build-height check. Not used for headline performance. */
final class JavaOreGeometry {
 static int[] candidates(double[] input, int j, int k, int l) {
  double[] ds=input.clone(); int p=ds.length/4;
  var points=new IntArrayList();
		for (int q = 0; q < p - 1; q++) {
			if (!(ds[q * 4 + 3] <= 0.0)) {
				for (int x = q + 1; x < p; x++) {
					if (!(ds[x * 4 + 3] <= 0.0)) {
						double s = ds[q * 4 + 0] - ds[x * 4 + 0];
						double t = ds[q * 4 + 1] - ds[x * 4 + 1];
						double u = ds[q * 4 + 2] - ds[x * 4 + 2];
						double v = ds[q * 4 + 3] - ds[x * 4 + 3];
						if (v * v > s * s + t * t + u * u) {
							if (v > 0.0) {
								ds[x * 4 + 3] = -1.0;
							} else {
								ds[q * 4 + 3] = -1.0;
							}
						}
					}
				}
			}
		}

			for (int xx = 0; xx < p; xx++) {
				double s = ds[xx * 4 + 3];
				if (!(s < 0.0)) {
					double t = ds[xx * 4 + 0];
					double u = ds[xx * 4 + 1];
					double v = ds[xx * 4 + 2];
					int y = Math.max(Mth.floor(t - s), j);
					int z = Math.max(Mth.floor(u - s), k);
					int aa = Math.max(Mth.floor(v - s), l);
					int ab = Math.max(Mth.floor(t + s), y);
					int ac = Math.max(Mth.floor(u + s), z);
					int ad = Math.max(Mth.floor(v + s), aa);

					for (int ae = y; ae <= ab; ae++) {
						double af = (ae + 0.5 - t) / s;
						if (af * af < 1.0) {
							for (int ag = z; ag <= ac; ag++) {
								double ah = (ag + 0.5 - u) / s;
								if (af * af + ah * ah < 1.0) {
									for (int ai = aa; ai <= ad; ai++) {
										double aj = (ai + 0.5 - v) / s;
										if (af * af + ah * ah + aj * aj < 1.0) {
											points.add(ae); points.add(ag); points.add(ai);
										}
									}
								}
							}
						}
					}
				}
			}
 return points.toIntArray();
 }
}
