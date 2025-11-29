package net.matt.quantize.modules.dark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.matt.quantize.Quantize;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ShaderConfig {

    private List<ShaderValue> shaders;
    private int version;
    private int selectedShaderIndex;
    private static final File configFilePath = new File("config" + File.separator + "quantizeshaders.json");

    public ShaderConfig() {
        this.shaders = new ArrayList<>();
        this.version = 2;
        ResourceLocation tex_shader_location = ResourceLocation.tryParse("quantize:dark_position_tex");
        ResourceLocation tex_color_shader_location = ResourceLocation.tryParse("quantize:dark_position_tex_color");
        this.shaders.add(null);
        this.shaders.add(new ShaderValue(tex_shader_location, tex_color_shader_location, Component.translatable("gui.quantize.toasted_light"), (float)2, 16777215));
        this.selectedShaderIndex = 0;
    }

    public List<ShaderValue> getShaders() {
        return shaders;
    }

    public void setSelectedShaderIndex(int index) {
        selectedShaderIndex = index;
        Quantize.LOGGER.debug("Selected shader index updated to {}", selectedShaderIndex);
        createDefaultConfigFile();
    }

    public int getSelectedShaderIndex() {
        return selectedShaderIndex;
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(MutableComponent.class, new Component.Serializer())
                .create();
    }

    public static void load() {
        Quantize.LOGGER.info("Loading config from {}", configFilePath.getAbsolutePath());
        if (!configFilePath.exists()) {
            Quantize.LOGGER.warn("QClientConfig file does not exist. Creating default config.");
            createDefaultConfigFile();
        }
        Gson gson = createGson();
        try (FileReader reader = new FileReader(configFilePath)) {
            DClientProxy.CONFIG = gson.fromJson(reader, ShaderConfig.class);
            Quantize.LOGGER.info("QClientConfig loaded successfully: {}", DClientProxy.CONFIG);
            if (DClientProxy.CONFIG.version != new ShaderConfig().version) {
                throw new Exception("Invalid config version.");
            }
        } catch (Exception e) {
            Quantize.LOGGER.error("Failed to load config. Creating default config.", e);
            DClientProxy.CONFIG = new ShaderConfig();
            createDefaultConfigFile();
        }
    }

    public static void createDefaultConfigFile() {
        Gson gson = createGson();
        try (FileWriter fileWriter = new FileWriter(configFilePath)) {
            gson.toJson(DClientProxy.CONFIG, fileWriter);
            Quantize.LOGGER.info("QClientConfig file saved successfully at {}", configFilePath.getAbsolutePath());
        } catch (IOException e) {
            Quantize.LOGGER.error("Failed to save config file", e);
        }
    }

    public static class ShaderValue {
        public ResourceLocation texShaderLocation;
        public ResourceLocation texColorShaderLocation;
        public MutableComponent displayName;
        public float divideFactor;
        public int darkColorReplacement;

        public ShaderValue(ResourceLocation texShaderLocation, ResourceLocation texColorShaderLocation, MutableComponent displayName, float divideFactor, int darkColorReplacement) {
            this.texShaderLocation = texShaderLocation;
            this.texColorShaderLocation = texColorShaderLocation;
            this.displayName = displayName;
            this.divideFactor = divideFactor;
            this.darkColorReplacement = darkColorReplacement;
        }
    }
}