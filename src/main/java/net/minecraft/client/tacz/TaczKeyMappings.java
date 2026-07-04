package net.minecraft.client.tacz;

import java.util.ArrayList;
import java.util.Arrays;
import net.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class TaczKeyMappings {
	private static final KeyMapping.Category CATEGORY = findOrRegisterCategory(ResourceLocation.fromNamespaceAndPath("tacz", "keybinds"));
	public static final KeyMapping INSPECT = new KeyMapping("key.tacz.inspect.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
	public static final KeyMapping RELOAD = new KeyMapping("key.tacz.reload.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
	public static final KeyMapping SHOOT = new KeyMapping("key.tacz.shoot.desc", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CATEGORY);
	public static final KeyMapping INTERACT = new KeyMapping("key.tacz.interact.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
	public static final KeyMapping FIRE_SELECT = new KeyMapping("key.tacz.fire_select.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
	public static final KeyMapping AIM = new KeyMapping("key.tacz.aim.desc", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
	public static final KeyMapping CRAWL = new KeyMapping("key.tacz.crawl.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
	public static final KeyMapping REFIT = new KeyMapping("key.tacz.refit.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
	public static final KeyMapping ZOOM = new KeyMapping("key.tacz.zoom.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
	public static final KeyMapping MELEE = new KeyMapping("key.tacz.melee.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
	public static final KeyMapping OPEN_CONFIG = new TaczModifiedKeyMapping(
		"key.tacz.open_config.desc",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_T,
		InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT),
		CATEGORY
	);
	private static final KeyMapping[] ALL = {
		INSPECT,
		RELOAD,
		SHOOT,
		INTERACT,
		FIRE_SELECT,
		AIM,
		CRAWL,
		REFIT,
		ZOOM,
		MELEE,
		OPEN_CONFIG
	};
	private static boolean registered;

	private TaczKeyMappings() {
	}

	public static void register(Minecraft minecraft) {
		if (registered) {
			return;
		}

		ArrayList<KeyMapping> keyMappings = new ArrayList<>();
		keyMappings.addAll(Arrays.asList(minecraft.options.keyMappings));
		keyMappings.addAll(Arrays.asList(ALL));
		minecraft.options.keyMappings = keyMappings.toArray(new KeyMapping[0]);
		KeyMapping.resetMapping();
		registered = true;
	}

	public static boolean isOpenConfigModifierDown(Minecraft minecraft) {
		return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
			|| InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
	}

	private static KeyMapping.Category findOrRegisterCategory(ResourceLocation id) {
		for (KeyMapping.Category category : KeyMapping.Category.SORT_ORDER) {
			if (category.id().equals(id)) {
				return category;
			}
		}

		return KeyMapping.Category.register(id);
	}
}
