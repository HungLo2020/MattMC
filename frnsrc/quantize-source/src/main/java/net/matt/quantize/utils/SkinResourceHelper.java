package net.matt.quantize.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

public final class SkinResourceHelper {
    private static final String NAMESPACE = "quantize";
    private static final Path SKIN_DIR = FMLPaths.GAMEDIR.get().resolve("skins");

    private SkinResourceHelper() {}

    public static List<String> listSkinBaseNames() {
        if (!Files.isDirectory(SKIN_DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(SKIN_DIR)) {
            return files
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .map(p -> {
                        String file = p.getFileName().toString();
                        return file.substring(0, file.length() - ".png".length());
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }
}