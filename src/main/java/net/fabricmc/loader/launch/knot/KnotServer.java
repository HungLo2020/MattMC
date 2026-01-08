package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/**
 * @deprecated Use {@link net.fabricmc.loader.impl.launch.knot.KnotServer} instead
 */
@Deprecated
public final class KnotServer {
	public static void main(String[] args) {
		Knot.launch(args, EnvType.SERVER);
	}
}
