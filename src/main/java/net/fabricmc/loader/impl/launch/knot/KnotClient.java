package net.fabricmc.loader.impl.launch.knot;

import net.fabricmc.api.EnvType;

public class KnotClient {
	public static void main(String[] args) {
		Knot.launch(args, EnvType.CLIENT);
	}
}
