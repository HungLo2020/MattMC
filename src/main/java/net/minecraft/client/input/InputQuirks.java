package net.minecraft.client.input;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.Util.OS;

@Environment(EnvType.CLIENT)
public class InputQuirks {
	private static final boolean ON_OSX = Util.getPlatform() == OS.OSX;
	public static final boolean REPLACE_CTRL_KEY_WITH_CMD_KEY = ON_OSX;
	public static final int EDIT_SHORTCUT_KEY_LEFT = REPLACE_CTRL_KEY_WITH_CMD_KEY ? 343 : 341;
	public static final int EDIT_SHORTCUT_KEY_RIGHT = REPLACE_CTRL_KEY_WITH_CMD_KEY ? 347 : 345;
	public static final int EDIT_SHORTCUT_KEY_MODIFIER = REPLACE_CTRL_KEY_WITH_CMD_KEY ? 8 : 2;
	public static final boolean SIMULATE_RIGHT_CLICK_WITH_LONG_LEFT_CLICK = ON_OSX;
	public static final boolean RESTORE_KEY_STATE_AFTER_MOUSE_GRAB = !ON_OSX;
}
