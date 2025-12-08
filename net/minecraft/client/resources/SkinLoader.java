package net.minecraft.client.resources;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages loading of built-in and custom player skins.
 */
@Environment(EnvType.CLIENT)
public class SkinLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	
	// Built-in skin names from resources
	private static final String[] BUILTIN_SKINS_WIDE = {
		"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri", "hunglo"
	};
	
	private static final String[] BUILTIN_SKINS_SLIM = {
		"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri", "hunglo"
	};
	
	private final Path skinsDirectory;
	private final List<SkinEntry> availableSkins = new ArrayList<>();
	
	public SkinLoader(Path gameDirectory) {
		this.skinsDirectory = gameDirectory.resolve("skins");
		ensureSkinsDirectoryExists();
		loadAllSkins();
	}
	
	private void ensureSkinsDirectoryExists() {
		try {
			if (!Files.exists(skinsDirectory)) {
				Files.createDirectories(skinsDirectory);
				LOGGER.info("Created skins directory at: {}", skinsDirectory);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to create skins directory", e);
		}
	}
	
	private void loadAllSkins() {
		availableSkins.clear();
		
		// Load built-in wide skins
		for (String skinName : BUILTIN_SKINS_WIDE) {
			ResourceLocation location = ResourceLocation.withDefaultNamespace("entity/player/wide/" + skinName);
			availableSkins.add(new SkinEntry(skinName + " (Wide)", location, PlayerModelType.WIDE, true));
		}
		
		// Load built-in slim skins
		for (String skinName : BUILTIN_SKINS_SLIM) {
			ResourceLocation location = ResourceLocation.withDefaultNamespace("entity/player/slim/" + skinName);
			availableSkins.add(new SkinEntry(skinName + " (Slim)", location, PlayerModelType.SLIM, true));
		}
		
		// Load custom skins from the skins directory
		loadCustomSkins();
		
		LOGGER.info("Loaded {} skins ({} custom)", availableSkins.size(), 
			availableSkins.stream().filter(s -> !s.builtin()).count());
	}
	
	private void loadCustomSkins() {
		if (!Files.exists(skinsDirectory) || !Files.isDirectory(skinsDirectory)) {
			return;
		}
		
		try (Stream<Path> paths = Files.list(skinsDirectory)) {
			paths.filter(path -> path.toString().toLowerCase().endsWith(".png"))
				.forEach(this::loadCustomSkin);
		} catch (IOException e) {
			LOGGER.error("Failed to list custom skins directory", e);
		}
	}
	
	private void loadCustomSkin(Path skinFile) {
		try {
			String fileName = skinFile.getFileName().toString();
			String skinName = fileName.substring(0, fileName.lastIndexOf('.'));
			
			// Validate it's a valid skin file by checking dimensions
			BufferedImage image = ImageIO.read(skinFile.toFile());
			if (image == null) {
				LOGGER.warn("Could not read image: {}", fileName);
				return;
			}
			
			int width = image.getWidth();
			int height = image.getHeight();
			
			// Valid Minecraft skin dimensions are 64x64, 64x32
			if ((width == 64 && height == 64) || (width == 64 && height == 32)) {
				// Create a custom resource location for this skin
				ResourceLocation location = ResourceLocation.fromNamespaceAndPath("custom", "skins/" + skinName);
				
				// Determine model type (we default to wide for custom skins)
				PlayerModelType modelType = PlayerModelType.WIDE;
				
				availableSkins.add(new SkinEntry(skinName + " (Custom)", location, modelType, false));
				LOGGER.info("Loaded custom skin: {}", skinName);
			} else {
				LOGGER.warn("Invalid skin dimensions for {}: {}x{} (expected 64x64 or 64x32)", 
					fileName, width, height);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to load custom skin: {}", skinFile.getFileName(), e);
		}
	}
	
	public void reload() {
		loadAllSkins();
	}
	
	public List<SkinEntry> getAvailableSkins() {
		return new ArrayList<>(availableSkins);
	}
	
	public SkinEntry getSkinByName(String displayName) {
		return availableSkins.stream()
			.filter(skin -> skin.displayName().equals(displayName))
			.findFirst()
			.orElse(availableSkins.isEmpty() ? null : availableSkins.get(0));
	}
	
	public SkinEntry getDefaultSkin() {
		// Return "steve (Wide)" as default
		return availableSkins.stream()
			.filter(skin -> skin.displayName().equals("steve (Wide)"))
			.findFirst()
			.orElse(availableSkins.isEmpty() ? null : availableSkins.get(0));
	}
	
	public Path getSkinsDirectory() {
		return skinsDirectory;
	}
	
	/**
	 * Represents a skin entry with display name and resource location.
	 */
	public record SkinEntry(String displayName, ResourceLocation location, PlayerModelType modelType, boolean builtin) {
		public PlayerSkin toPlayerSkin() {
			return new PlayerSkin(new ResourceTexture(location), null, null, modelType, true);
		}
	}
}
