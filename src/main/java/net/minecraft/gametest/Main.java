package net.minecraft.gametest;

import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestMainUtil;

public class Main {
	public static void main(String[] strings) throws Exception {
		SharedConstants.tryDetectVersion();
		GameTestMainUtil.runGameTestServer(strings, string -> {});
	}
}
