package net.minecraft.client.renderer.shaders.preprocessor;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shaders.helpers.StringPair;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Standard macros/defines for GLSL preprocessing.
 * 
 * Based on IRIS's StandardMacros.java.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/shader/StandardMacros.java
 */
public class StandardMacros {
	private static final Pattern SEMVER_PATTERN = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.*(?<bugfix>\\d*)(.*)");

	private static void define(List<StringPair> defines, String key) {
		defines.add(new StringPair(key, ""));
	}

	private static void define(List<StringPair> defines, String key, String value) {
		defines.add(new StringPair(key, value));
	}

	/**
	 * Creates the standard environment defines for shader preprocessing.
	 */
	public static ImmutableList<StringPair> createStandardEnvironmentDefines() {
		ArrayList<StringPair> standardDefines = new ArrayList<>();

		// Minecraft version (1.21.10 -> 12110)
		define(standardDefines, "MC_VERSION", "12110");
		
		// Mipmap level
		int mipmapLevel = 4; // Default mipmap level
		try {
			mipmapLevel = Minecraft.getInstance().options.mipmapLevels().get();
		} catch (Exception ignored) {}
		define(standardDefines, "MC_MIPMAP_LEVEL", String.valueOf(mipmapLevel));
		
		// Iris version (as MattMC, using 1.8.0)
		define(standardDefines, "IRIS_VERSION", "10800");
		
		// GL version
		try {
			define(standardDefines, "MC_GL_VERSION", getGlVersion(GL20C.GL_VERSION));
			define(standardDefines, "MC_GLSL_VERSION", getGlVersion(GL20C.GL_SHADING_LANGUAGE_VERSION));
		} catch (Exception e) {
			// Fallback to sensible defaults if GL not yet initialized
			define(standardDefines, "MC_GL_VERSION", "330");
			define(standardDefines, "MC_GLSL_VERSION", "330");
		}
		
		// OS defines
		define(standardDefines, getOsString());
		
		// Vendor and renderer defines
		try {
			define(standardDefines, getVendor());
			define(standardDefines, getRenderer());
		} catch (Exception e) {
			define(standardDefines, "MC_GL_VENDOR_OTHER");
			define(standardDefines, "MC_GL_RENDERER_OTHER");
		}
		
		// Iris compatibility defines
		define(standardDefines, "IS_IRIS");
		define(standardDefines, "IRIS_TAG_SUPPORT", "2");
		
		// Normal and specular map support
		define(standardDefines, "MC_NORMAL_MAP");
		define(standardDefines, "MC_SPECULAR_MAP");
		
		// Render quality (default values)
		define(standardDefines, "MC_RENDER_QUALITY", "1.0");
		define(standardDefines, "MC_SHADOW_QUALITY", "1.0");
		define(standardDefines, "MC_HAND_DEPTH", "0.125");
		
		// Precipitation types
		define(standardDefines, "PPT_NONE", "0");
		define(standardDefines, "PPT_RAIN", "1");
		define(standardDefines, "PPT_SNOW", "2");
		
		// Distant Horizons block types (for compatibility)
		define(standardDefines, "DH_BLOCK_UNKNOWN", "0");
		define(standardDefines, "DH_BLOCK_LEAVES", "1");
		define(standardDefines, "DH_BLOCK_STONE", "2");
		define(standardDefines, "DH_BLOCK_WOOD", "3");
		define(standardDefines, "DH_BLOCK_METAL", "4");
		define(standardDefines, "DH_BLOCK_DIRT", "5");
		define(standardDefines, "DH_BLOCK_LAVA", "6");
		define(standardDefines, "DH_BLOCK_DEEPSLATE", "7");
		define(standardDefines, "DH_BLOCK_SNOW", "8");
		define(standardDefines, "DH_BLOCK_SAND", "9");
		define(standardDefines, "DH_BLOCK_TERRACOTTA", "10");
		define(standardDefines, "DH_BLOCK_NETHER_STONE", "11");
		define(standardDefines, "DH_BLOCK_WATER", "12");
		define(standardDefines, "DH_BLOCK_GRASS", "13");
		define(standardDefines, "DH_BLOCK_AIR", "14");
		define(standardDefines, "DH_BLOCK_ILLUMINATED", "15");
		
		// GL extensions
		try {
			for (String glExtension : getGlExtensions()) {
				define(standardDefines, glExtension);
			}
		} catch (Exception ignored) {
			// GL not yet initialized, skip extensions
		}

		return ImmutableList.copyOf(standardDefines);
	}

	/**
	 * Returns the current GL Version
	 */
	public static String getGlVersion(int name) {
		String info = GlStateManager._getString(name);
		if (info == null) {
			return "330"; // fallback
		}

		Matcher matcher = SEMVER_PATTERN.matcher(info);

		if (!matcher.matches()) {
			return "330"; // fallback
		}

		String major = group(matcher, "major");
		String minor = group(matcher, "minor");
		String bugfix = group(matcher, "bugfix");

		if (bugfix == null || bugfix.isEmpty()) {
			bugfix = "0";
		}

		if (major == null || minor == null) {
			return "330"; // fallback
		}

		return major + minor + bugfix;
	}

	private static String group(Matcher matcher, String name) {
		try {
			return matcher.group(name);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return null;
		}
	}

	/**
	 * Returns the current OS String
	 */
	public static String getOsString() {
		return switch (Util.getPlatform()) {
			case OSX -> "MC_OS_MAC";
			case LINUX -> "MC_OS_LINUX";
			case WINDOWS -> "MC_OS_WINDOWS";
			default -> "MC_OS_UNKNOWN";
		};
	}

	/**
	 * Returns a string indicating the graphics card being used
	 */
	public static String getVendor() {
		String vendor = Objects.requireNonNullElse(RenderSystem.getDevice().getVendor(), "unknown").toLowerCase(Locale.ROOT);
		if (vendor.startsWith("ati")) {
			return "MC_GL_VENDOR_ATI";
		} else if (vendor.startsWith("intel")) {
			return "MC_GL_VENDOR_INTEL";
		} else if (vendor.startsWith("nvidia")) {
			return "MC_GL_VENDOR_NVIDIA";
		} else if (vendor.startsWith("amd")) {
			return "MC_GL_VENDOR_AMD";
		} else if (vendor.startsWith("x.org")) {
			return "MC_GL_VENDOR_XORG";
		}
		return "MC_GL_VENDOR_OTHER";
	}

	/**
	 * Returns the graphics driver being used
	 */
	public static String getRenderer() {
		String renderer = Objects.requireNonNullElse(RenderSystem.getDevice().getRenderer(), "unknown").toLowerCase(Locale.ROOT);
		if (renderer.startsWith("amd") || renderer.startsWith("ati") || renderer.startsWith("radeon")) {
			return "MC_GL_RENDERER_RADEON";
		} else if (renderer.startsWith("gallium")) {
			return "MC_GL_RENDERER_GALLIUM";
		} else if (renderer.startsWith("intel")) {
			return "MC_GL_RENDERER_INTEL";
		} else if (renderer.startsWith("geforce") || renderer.startsWith("nvidia")) {
			return "MC_GL_RENDERER_GEFORCE";
		} else if (renderer.startsWith("quadro") || renderer.startsWith("nvs")) {
			return "MC_GL_RENDERER_QUADRO";
		} else if (renderer.startsWith("mesa")) {
			return "MC_GL_RENDERER_MESA";
		} else if (renderer.startsWith("apple")) {
			return "MC_GL_RENDERER_APPLE";
		}
		return "MC_GL_RENDERER_OTHER";
	}

	/**
	 * Returns the list of currently enabled GL extensions
	 */
	public static Set<String> getGlExtensions() {
		int numExtensions = GlStateManager._getInteger(GL30C.GL_NUM_EXTENSIONS);

		String[] extensions = new String[numExtensions];

		for (int i = 0; i < numExtensions; i++) {
			extensions[i] = GL30C.glGetStringi(GL30C.GL_EXTENSIONS, i);
		}

		return Arrays.stream(extensions)
			.filter(Objects::nonNull)
			.map(s -> "MC_" + s)
			.collect(Collectors.toSet());
	}
}
