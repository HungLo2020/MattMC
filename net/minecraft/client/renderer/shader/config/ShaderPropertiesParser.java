package net.minecraft.client.renderer.shader.config;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Parses shaders.properties files from shader packs.
 * These files contain configuration for shader behavior, options, and buffer formats.
 */
@Environment(EnvType.CLIENT)
public class ShaderPropertiesParser {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Parses a shaders.properties file from an input stream.
     */
    public ShaderProperties parse(InputStream inputStream) throws IOException {
        Properties props = new Properties();
        props.load(inputStream);
        
        Map<String, String> settings = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            settings.put(key, props.getProperty(key));
        }
        
        return new ShaderProperties(settings);
    }
}
