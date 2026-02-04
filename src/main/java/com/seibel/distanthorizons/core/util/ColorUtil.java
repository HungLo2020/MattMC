package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.awt.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * FFM-based ColorUtil using Rust native library.
 * Handles the bit-wise math used when dealing with colors stored as integers.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * Minecraft color format is:       0xAA BB GG RR
 * DH mod color format is:          0xAA RR GG BB
 * OpenGL RGBA format native order: 0xRR GG BB AA
 * OpenGL RGBA format Java Order:   0xAA BB GG RR
 * 
 * @author Cola
 * @author Leonardo Amato
 * @version 2026-02-04 (Rust FFM migration)
 */
public class ColorUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle rgbToInt;
	private static final MethodHandle argbToIntI;
	private static final MethodHandle argbToIntF;
	private static final MethodHandle getAlpha;
	private static final MethodHandle getRed;
	private static final MethodHandle getGreen;
	private static final MethodHandle getBlue;
	private static final MethodHandle setAlpha;
	private static final MethodHandle setRed;
	private static final MethodHandle setGreen;
	private static final MethodHandle setBlue;
	private static final MethodHandle applyShadeInt;
	private static final MethodHandle applyShadeFloat;
	private static final MethodHandle multiplyARGBwithRGB;
	private static final MethodHandle multiplyARGBwithARGB;
	private static final MethodHandle argbToAhsv;
	private static final MethodHandle ahsvToArgb;
	
	static {
		try {
			// Determine the platform-specific library name
			String osName = System.getProperty("os.name").toLowerCase();
			String libraryName;
			
			if (osName.contains("win")) {
				libraryName = "mattmc_native.dll";
			} else if (osName.contains("mac")) {
				libraryName = "libmattmc_native.dylib";
			} else {
				libraryName = "libmattmc_native.so";
			}
			
			// Load library from the JAR's native resources
			Path libraryPath = NativeLibraryLoader.loadLibraryFromJar(libraryName);
			
			// Load the library
			SymbolLookup lib = SymbolLookup.libraryLookup(libraryPath, Arena.global());
			LIBRARY = lib;
			
			// Initialize function handles
			rgbToInt = LINKER.downcallHandle(
				findFunction("colorutil_rgb_to_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			argbToIntI = LINKER.downcallHandle(
				findFunction("colorutil_argb_to_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			argbToIntF = LINKER.downcallHandle(
				findFunction("colorutil_argb_to_int_f"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			getAlpha = LINKER.downcallHandle(
				findFunction("colorutil_get_alpha"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			getRed = LINKER.downcallHandle(
				findFunction("colorutil_get_red"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			getGreen = LINKER.downcallHandle(
				findFunction("colorutil_get_green"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			getBlue = LINKER.downcallHandle(
				findFunction("colorutil_get_blue"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			setAlpha = LINKER.downcallHandle(
				findFunction("colorutil_set_alpha"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			setRed = LINKER.downcallHandle(
				findFunction("colorutil_set_red"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			setGreen = LINKER.downcallHandle(
				findFunction("colorutil_set_green"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			setBlue = LINKER.downcallHandle(
				findFunction("colorutil_set_blue"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			applyShadeInt = LINKER.downcallHandle(
				findFunction("colorutil_apply_shade_int"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			applyShadeFloat = LINKER.downcallHandle(
				findFunction("colorutil_apply_shade_float"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT)
			);
			
			multiplyARGBwithRGB = LINKER.downcallHandle(
				findFunction("colorutil_multiply_argb_with_rgb"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			multiplyARGBwithARGB = LINKER.downcallHandle(
				findFunction("colorutil_multiply_argb_with_argb"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			argbToAhsv = LINKER.downcallHandle(
				findFunction("colorutil_argb_to_ahsv"),
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
			);
			
			ahsvToArgb = LINKER.downcallHandle(
				findFunction("colorutil_ahsv_to_argb"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for ColorUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for ColorUtil!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	// Color constants - defined after static initialization to avoid circular dependency
	public static final int INVISIBLE = 0x00000000;
	
	public static final int BLACK = 0xFF000000;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int RED = 0xFFFF0000;
	public static final int DARK_RED = 0xFF640000;
	public static final int GREEN = 0xFF00FF00;
	public static final int DARK_GREEN = 0xFF508C50;
	public static final int BLUE = 0xFF0000FF;
	public static final int YELLOW = 0xFFFFFF00;
	public static final int CYAN = 0xFF00FFFF;
	public static final int MAGENTA = 0xFFFF00FF;
	public static final int ORANGE = 0xFFFF8000;
	public static final int DARK_ORANGE = 0xFF7D3E00;
	public static final int TAN = 0xFFB7A577;
	public static final int PINK = 0xFFFF8080;
	public static final int HOT_PINK = 0xFFFF69B4;
	public static final int GRAY = 0xFF808080;
	public static final int LIGHT_GRAY = 0xFFC0C0C0;
	public static final int DARK_GRAY = 0xFF404040;
	public static final int BROWN = 0xFF442E18;
	public static final int LIGHT_BROWN = 0xFF827043;
	public static final int PURPLE = 0xFF800080;
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	// ===== RGB/ARGB Construction Functions =====
	
	public static int rgbToInt(int red, int green, int blue) {
		try {
			return (int) rgbToInt.invokeExact(red, green, blue);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native rgbToInt", e);
		}
	}
	
	public static int argbToInt(int alpha, int red, int green, int blue) {
		try {
			return (int) argbToIntI.invokeExact(alpha, red, green, blue);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native argbToInt", e);
		}
	}
	
	public static int argbToInt(float alpha, float red, float green, float blue) {
		try {
			return (int) argbToIntF.invokeExact(alpha, red, green, blue);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native argbToInt(float)", e);
		}
	}
	
	// ===== Component Getters =====
	
	/** Returns a value between 0 and 255 */
	public static int getAlpha(int color) {
		try {
			return (int) getAlpha.invokeExact(color);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getAlpha", e);
		}
	}
	
	/** Returns a value between 0 and 255 */
	public static int getRed(int color) {
		try {
			return (int) getRed.invokeExact(color);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getRed", e);
		}
	}
	
	/** Returns a value between 0 and 255 */
	public static int getGreen(int color) {
		try {
			return (int) getGreen.invokeExact(color);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getGreen", e);
		}
	}
	
	/** Returns a value between 0 and 255 */
	public static int getBlue(int color) {
		try {
			return (int) getBlue.invokeExact(color);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getBlue", e);
		}
	}
	
	// ===== Component Setters =====
	
	/** @param newAlpha should be a value between 0 and 255 */
	public static int setAlpha(int color, int newAlpha) {
		try {
			return (int) setAlpha.invokeExact(color, newAlpha);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native setAlpha", e);
		}
	}
	
	/** @param newRed should be a value between 0 and 255 */
	public static int setRed(int color, int newRed) {
		try {
			return (int) setRed.invokeExact(color, newRed);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native setRed", e);
		}
	}
	
	/** @param newGreen should be a value between 0 and 255 */
	public static int setGreen(int color, int newGreen) {
		try {
			return (int) setGreen.invokeExact(color, newGreen);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native setGreen", e);
		}
	}
	
	/** @param newBlue should be a value between 0 and 255 */
	public static int setBlue(int color, int newBlue) {
		try {
			return (int) setBlue.invokeExact(color, newBlue);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native setBlue", e);
		}
	}
	
	// ===== Color Manipulation Functions =====
	
	public static int applyShade(int color, int shade) {
		try {
			return (int) applyShadeInt.invokeExact(color, shade);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native applyShade(int)", e);
		}
	}
	
	public static int applyShade(int color, float shade) {
		try {
			return (int) applyShadeFloat.invokeExact(color, shade);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native applyShade(float)", e);
		}
	}
	
	// ===== Color Blending Functions =====
	
	/** Multiply ARGB with RGB colors */
	public static int multiplyARGBwithRGB(int argb, int rgb) {
		try {
			return (int) multiplyARGBwithRGB.invokeExact(argb, rgb);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native multiplyARGBwithRGB", e);
		}
	}
	
	/** Multiply 2 ARGB colors */
	public static int multiplyARGBwithARGB(int color1, int color2) {
		try {
			return (int) multiplyARGBwithARGB.invokeExact(color1, color2);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native multiplyARGBwithARGB", e);
		}
	}
	
	// ===== Color Space Conversion Functions =====
	
	/**
	 * Below 2 functions are from: https://stackoverflow.com/questions/13806483/increase-or-decrease-color-saturation
	 * Alpha in [0.0,1.0], hue in [0.0,360.0], Sat in [0.0,1.0], Value in [0.0,1.0]
	 */
	public static float[] argbToAhsv(int color) {
		try (Arena arena = Arena.ofConfined()) {
			// Allocate memory for the output array (4 floats)
			MemorySegment output = arena.allocate(ValueLayout.JAVA_FLOAT, 4);
			
			// Call the native function
			argbToAhsv.invokeExact(color, output);
			
			// Read the results
			float[] result = new float[4];
			result[0] = output.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
			result[1] = output.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
			result[2] = output.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
			result[3] = output.getAtIndex(ValueLayout.JAVA_FLOAT, 3);
			
			return result;
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native argbToAhsv", e);
		}
	}
	
	/** Alpha in [0.0,1.0], hue in [0.0,360.0], Sat in [0.0,1.0], Value in [0.0,1.0] */
	public static int ahsvToArgb(float a, float h, float s, float v) {
		try {
			return (int) ahsvToArgb.invokeExact(a, h, s, v);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native ahsvToArgb", e);
		}
	}
	
	// ===== String Conversion Functions (Java implementation) =====
	
	/** Returns the hex value for the Alpha, Red, Green, and Blue channels. */
	public static String toHexString(int color) {
		return "A:" + Integer.toHexString(getAlpha(color)) +
				",R:" + Integer.toHexString(getRed(color)) +
				",G:" + Integer.toHexString(getGreen(color)) +
				",B:" + Integer.toHexString(getBlue(color));
	}
	
	/** Returns the int value (0-255) for the Alpha, Red, Green, and Blue channels. */
	public static String toString(int color) {
		return "A:" + getAlpha(color) +
				",R:" + getRed(color) +
				",G:" + getGreen(color) +
				",B:" + getBlue(color);
	}
	
	// ===== Java AWT Color Conversion Functions (Java implementation) =====
	
	public static Color toColorObjRGB(int color) {
		return new Color(getRed(color), getGreen(color), getBlue(color));
	}
	
	public static Color toColorObjARGB(int color) {
		return new Color(getRed(color), getGreen(color), getBlue(color), getAlpha(color));
	}
	
	public static int toColorInt(Color color) {
		return argbToInt(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
	}
}
