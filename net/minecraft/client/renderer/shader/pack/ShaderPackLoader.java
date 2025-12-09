package net.minecraft.client.renderer.shader.pack;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Loads shader pack files from game resources (JAR).
 * Discovers shader programs dynamically by scanning resource listings.
 */
@Environment(EnvType.CLIENT)
public class ShaderPackLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final ResourceManager resourceManager;
    private final String packName;
    private final String packBasePath;
    private final Map<String, String> vertexSources = new HashMap<>();
    private final Map<String, String> fragmentSources = new HashMap<>();
    
    public ShaderPackLoader(ResourceManager resourceManager, String packName) {
        this.resourceManager = resourceManager;
        this.packName = packName;
        this.packBasePath = "shaders/" + packName;
    }
    
    /**
     * Loads the shader pack from resources.
     */
    public ShaderPack load() throws IOException {
        LOGGER.info("Loading shader pack: {}", packName);
        
        // Discover all shader programs
        discoverShaderPrograms();
        
        // Load shader properties if present
        net.minecraft.client.renderer.shader.config.ShaderProperties properties = loadProperties();
        
        LOGGER.info("Loaded {} shader programs from pack '{}'", vertexSources.size(), packName);
        
        ShaderPackMetadata metadata = ShaderPackMetadata.createDefault(packName);
        return new ShaderPack(packName, metadata, vertexSources, fragmentSources, properties);
    }
    
    /**
     * Loads shader properties from shaders.properties file.
     */
    private net.minecraft.client.renderer.shader.config.ShaderProperties loadProperties() {
        try {
            String propertiesPath = packBasePath + "/shaders.properties";
            String source = loadShaderFile(propertiesPath);
            if (source != null) {
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                net.minecraft.client.renderer.shader.config.ShaderPropertiesParser parser = 
                    new net.minecraft.client.renderer.shader.config.ShaderPropertiesParser();
                return parser.parse(bais);
            }
        } catch (IOException e) {
            LOGGER.debug("No shaders.properties found for pack '{}', using defaults", packName);
        }
        return new net.minecraft.client.renderer.shader.config.ShaderProperties(new HashMap<>());
    }
    
    /**
     * Dynamically discovers all shader program files in the shader pack.
     */
    private void discoverShaderPrograms() {
        // List all shader files under the pack's shaders/ directory
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
            packBasePath + "/shaders",
            location -> {
                String path = location.getPath();
                return path.endsWith(".vsh") || path.endsWith(".fsh");
            }
        );
        
        // Group shader files by program name
        Set<String> programNames = new HashSet<>();
        for (ResourceLocation location : resources.keySet()) {
            String path = location.getPath();
            // Extract program name from path
            // Path format: "shaders/pack_name/shaders/program_name.vsh"
            String[] parts = path.split("/");
            if (parts.length >= 3) {
                String fileName = parts[parts.length - 1];
                // Remove extension to get program name
                String programName = fileName.substring(0, fileName.lastIndexOf('.'));
                programNames.add(programName);
            }
        }
        
        // Load each discovered program
        for (String programName : programNames) {
            try {
                loadShaderProgram(programName);
            } catch (IOException e) {
                LOGGER.warn("Failed to load shader program '{}': {}", programName, e.getMessage());
            }
        }
    }
    
    /**
     * Loads a single shader program (vertex and/or fragment shader).
     */
    private void loadShaderProgram(String programName) throws IOException {
        String vshPath = packBasePath + "/shaders/" + programName + ".vsh";
        String fshPath = packBasePath + "/shaders/" + programName + ".fsh";
        
        String vertexSource = loadShaderFile(vshPath);
        String fragmentSource = loadShaderFile(fshPath);
        
        if (vertexSource != null) {
            vertexSources.put(programName, vertexSource);
        }
        if (fragmentSource != null) {
            fragmentSources.put(programName, fragmentSource);
        }
        
        if (vertexSource != null || fragmentSource != null) {
            LOGGER.debug("Loaded shader program: {}", programName);
        }
    }
    
    /**
     * Loads a shader file from resources.
     */
    private String loadShaderFile(String path) throws IOException {
        ResourceLocation location = ResourceLocation.withDefaultNamespace(path);
        Optional<Resource> resourceOpt = resourceManager.getResource(location);
        
        if (resourceOpt.isEmpty()) {
            return null;
        }
        
        Resource resource = resourceOpt.get();
        try (InputStream is = resource.open();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // Process #include directives
                if (line.trim().startsWith("#include")) {
                    String includePath = parseIncludePath(line);
                    if (includePath != null) {
                        String includedSource = loadIncludeFile(includePath);
                        if (includedSource != null) {
                            source.append(includedSource).append("\n");
                        }
                    }
                } else {
                    source.append(line).append("\n");
                }
            }
            
            return source.toString();
        }
    }
    
    /**
     * Parses an #include directive to extract the file path.
     */
    private String parseIncludePath(String line) {
        // Parse: #include "path/to/file.glsl" or #include <path/to/file.glsl>
        int startQuote = line.indexOf('"');
        int endQuote = line.lastIndexOf('"');
        
        if (startQuote != -1 && endQuote != -1 && startQuote < endQuote) {
            return line.substring(startQuote + 1, endQuote);
        }
        
        int startBracket = line.indexOf('<');
        int endBracket = line.lastIndexOf('>');
        
        if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
            return line.substring(startBracket + 1, endBracket);
        }
        
        return null;
    }
    
    /**
     * Loads an included shader file.
     */
    private String loadIncludeFile(String includePath) throws IOException {
        // Try relative to shader pack first
        String fullPath = packBasePath + "/shaders/" + includePath;
        String source = loadShaderFile(fullPath);
        
        if (source == null) {
            // Try in include/ directory
            fullPath = packBasePath + "/include/" + includePath;
            source = loadShaderFile(fullPath);
        }
        
        return source;
    }
}
