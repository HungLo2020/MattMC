package net.matt.quantize.keys;

import net.matt.quantize.modules.tweaks.SwapHotbarsHandler;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModKeyBindings {

    public static final String CATEGORY = "key.categories.quantize";
    public static final KeyMapping SWAP_HOTBARS_KEY = new KeyMapping("key.quantize.swap_hotbars", GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping SPECIAL_ABILITY_KEY = new KeyMapping("key.quantize.special_ability", GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping MAP_KEY = new KeyMapping("key.quantize.map", GLFW.GLFW_KEY_M, CATEGORY);

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SWAP_HOTBARS_KEY);
        event.register(SPECIAL_ABILITY_KEY);
        event.register(MAP_KEY);

        SwapHotbarsHandler.init();
    }
}