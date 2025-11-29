package net.matt.quantize.modules.storage;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import java.io.InputStream;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Central place for Quantize-managed folders.
 */
public final class QFolders {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Folder names
    public static final Path SKINS_DIR = FMLPaths.GAMEDIR.get().resolve("skins");
    public static final Path SHADERPACKS_DIR =FMLPaths.GAMEDIR.get().resolve("shaderpacks");
    public static final Path MODS_DIR =FMLPaths.GAMEDIR.get().resolve("mods");


    //Embedded Shaders Stuff
    // Embedded Shader Name
    private static final String SHADERPACK_NAME = "ComplementaryHungLoIfied.zip";
    // Resource path INSIDE your mod jar
    private static final String EMBEDDED_SHADERPACK_PATH =
            "assets/quantize/shaders/embedded/" + SHADERPACK_NAME;


    // Copy Skins Stuff
    private static final String SKINS_NAMESPACE = "quantize";
    private static final String EMBEDDED_SKINS_BASE = "textures/model/entity/player";


    private QFolders() {}


    /** Ensure all needed folders exist. Call once during init (e.g., in commonSetup enqueueWork). */
    public static void init() {
        ensureDir(SKINS_DIR, "skins");
        ensureDir(SHADERPACKS_DIR, "shaderpacks");
    }

    public static void run() {
        installEmbeddedShaderpackIfMissing();
        installEmbeddedSkinsIfMissing();
    }



    // Utils
    private static void ensureDir(Path dir, String label) {
        try {
            Files.createDirectories(dir);
            LOGGER.info("Ensured {} folder at {}", label, dir);
        } catch (IOException e) {
            LOGGER.error("Couldn't create {} folder {}", label, dir, e);
        }
    }

    // Install Shaders
    private static void installEmbeddedShaderpackIfMissing() {
        final Path dest = SHADERPACKS_DIR.resolve(SHADERPACK_NAME);

        if (Files.exists(dest)) {
            LOGGER.debug("Shaderpack already present at {}", dest);
            return;
        }

        try (InputStream in = QFolders.class.getClassLoader().getResourceAsStream(EMBEDDED_SHADERPACK_PATH)) {
            if (in == null) {
                LOGGER.warn("Embedded shaderpack not found in mod resources: {}", EMBEDDED_SHADERPACK_PATH);
                return;
            }
            Files.copy(in, dest);
            LOGGER.info("Installed embedded shaderpack to {}", dest);
        } catch (IOException e) {
            LOGGER.error("Failed copying embedded shaderpack to {}", dest, e);
        }
    }

    // Install Skins
    public static void installEmbeddedSkinsIfMissing() {
        // This requires client classes; skip on dedicated server.
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ResourceManager rm = Minecraft.getInstance().getResourceManager();

        // Find all resources under the embedded base that end with .png
        var resources = rm.listResources(EMBEDDED_SKINS_BASE, rl -> rl.getPath().endsWith(".png"));
        if (resources.isEmpty()) {
            LOGGER.debug("No embedded skins found under assets/{}/{}", SKINS_NAMESPACE, EMBEDDED_SKINS_BASE);
            return;
        }

        for (var entry : resources.entrySet()) {
            ResourceLocation rl = entry.getKey();   // e.g. quantize:textures/model/entity/player/steve.png
            Resource res = entry.getValue();

            if (!SKINS_NAMESPACE.equals(rl.getNamespace())) continue;

            String path = rl.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1); // "steve.png"
            Path dest = SKINS_DIR.resolve(fileName);

            if (Files.exists(dest)) {
                LOGGER.debug("Skin already present, skipping: {}", dest);
                continue;
            }

            try (InputStream in = res.open()) {
                Files.copy(in, dest);
                LOGGER.info("Installed embedded skin to {}", dest);
            } catch (IOException e) {
                LOGGER.error("Failed copying embedded skin {} to {}", rl, dest, e);
            }
        }
    }
}
