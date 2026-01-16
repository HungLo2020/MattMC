package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/**
 * @deprecated Use {@link net.fabricmc.loader.impl.launch.knot.KnotClient} instead
 */
@Deprecated
public final class KnotClient {
	public static void main(String[] args) {
		Knot.launch(args, EnvType.CLIENT);
	}
}
