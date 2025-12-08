package net.minecraft.client;

import com.mojang.serialization.Codec;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.OptionEnum;
import net.minecraft.util.StringRepresentable;

/**
 * Represents the available panorama themes for the main menu background.
 * <p>
 * Each theme corresponds to a set of 6 texture files (panorama_0.png through panorama_5.png)
 * located in {@code assets/minecraft/textures/gui/title/background/{theme}/}.
 * These textures are combined to create a 360-degree skybox panorama effect on the main menu.
 * </p>
 * 
 * <p>The panorama theme can be selected in the accessibility options menu. When changed,
 * the {@link net.minecraft.client.renderer.GameRenderer#reloadPanorama()} method is called
 * to load and register the new texture set.</p>
 * 
 * <p><strong>Adding New Themes:</strong></p>
 * <ol>
 *   <li>Add an enum entry with unique ID, directory name, and translation key</li>
 *   <li>Add translation in {@code assets/minecraft/lang/en_us.json}</li>
 *   <li>Add 6 textures in {@code assets/minecraft/textures/gui/title/background/{theme}/panorama_*.png}</li>
 * </ol>
 * 
 * @see net.minecraft.client.renderer.GameRenderer#reloadPanorama()
 * @see net.minecraft.client.Options#panoramaTheme
 */
@Environment(EnvType.CLIENT)
public enum PanoramaTheme implements OptionEnum, StringRepresentable {
	/** Aquatic theme featuring underwater scenery */
	AQUATIC(0, "aquatic", "options.panoramaTheme.aquatic"),
	
	/** Caves theme featuring underground cave formations */
	CAVES(1, "caves", "options.panoramaTheme.caves"),
	
	/** Copper Age theme featuring copper block architecture */
	COPPER_AGE(2, "copper_age", "options.panoramaTheme.copperAge"),
	
	/** Nether theme featuring the Nether dimension */
	NETHER(3, "nether", "options.panoramaTheme.nether"),
	
	/** Release theme featuring classic Minecraft landscapes */
	RELEASE(4, "release", "options.panoramaTheme.release"),
	
	/** Spring to Life theme featuring spring/nature scenery */
	SPRING_TO_LIFE(5, "spring_to_life", "options.panoramaTheme.springToLife"),
	
	/** Tricky Trials theme featuring trial chambers */
	TRICKY_TRIALS(6, "tricky_trials", "options.panoramaTheme.trickyTrials");

	/** Codec for serializing and deserializing PanoramaTheme values */
	public static final Codec<PanoramaTheme> CODEC = StringRepresentable.fromEnum(PanoramaTheme::values);
	
	/** Unique numeric identifier for this theme */
	private final int id;
	
	/** Directory name for this theme's textures (lowercase, underscore-separated) */
	private final String serializeName;
	
	/** Translation key for this theme's display name in the options menu */
	private final String key;

	/**
	 * Constructs a panorama theme.
	 * 
	 * @param id Unique numeric identifier (used for ordering and internal references)
	 * @param serializeName Directory name for textures (must be lowercase with underscores)
	 * @param key Translation key for the display name in options menu
	 */
	private PanoramaTheme(final int id, final String serializeName, final String key) {
		this.id = id;
		this.serializeName = serializeName;
		this.key = key;
	}

	/**
	 * Gets the serialized name used for file paths and storage.
	 * 
	 * @return The directory name for this theme (e.g., "aquatic", "tricky_trials")
	 */
	public String getSerializedName() {
		return this.serializeName;
	}

	/**
	 * Gets the unique numeric identifier for this theme.
	 * 
	 * @return The theme's ID number
	 */
	public int getId() {
		return this.id;
	}

	/**
	 * Gets the translation key for this theme's display name.
	 * 
	 * @return Translation key string (e.g., "options.panoramaTheme.aquatic")
	 */
	public String getKey() {
		return this.key;
	}

	/**
	 * Gets the path component for locating this theme's textures.
	 * <p>
	 * Returns the lowercase directory name matching the texture directory structure.
	 * This is required because ResourceLocation paths must use only lowercase characters.
	 * </p>
	 * 
	 * @return The lowercase path component for texture loading
	 */
	public String getPath() {
		// Return lowercase path matching directory structure
		// ResourceLocation requires lowercase paths
		return this.serializeName;
	}
}
