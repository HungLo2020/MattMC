package com.seibel.distanthorizons.coreapi.util;

import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;

/**
 * Miscellaneous string helper functions.
 * 
 * This is a hybrid implementation where performance-critical functions (like hex conversion)
 * are implemented in Rust via FFM, while higher-level Java-specific functions remain in Java.
 * 
 * @author James Seibel (original design)
 * @version 2024-02-04 (Partial Rust FFM migration for performance-critical functions)
 */
public class StringUtil
{
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle byteArrayToHexHandle;
	
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
			byteArrayToHexHandle = LINKER.downcallHandle(
				findFunction("stringutil_byte_array_to_hex"),
				FunctionDescriptor.of(
					ValueLayout.JAVA_INT,
					ValueLayout.ADDRESS,
					ValueLayout.JAVA_LONG,
					ValueLayout.ADDRESS
				)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for StringUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for StringUtil!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	
	/**
	 * Returns the n-th index of the given string. <br> <br>
	 *
	 * Original source: https://stackoverflow.com/questions/3976616/how-to-find-nth-occurrence-of-character-in-a-string
	 */
	public static int nthIndexOf(String str, String substr, int n)
	{
		int pos = str.indexOf(substr);
		while (--n > 0 && pos != -1)
		{
			pos = str.indexOf(substr, pos + 1);
		}
		return pos;
	}
	
	/** @see StringUtil#join(String, Iterable)  */
	public static <T> String join(String delimiter, T[] list) { return join(delimiter, Arrays.asList(list)); }
	/** Combines each item in the given list together separated by the given delimiter. */
	public static <T> String join(String delimiter, Iterable<T> list)
	{
		StringBuilder stringBuilder = new StringBuilder();
		
		boolean firstItem = true;
		for (T item : list)
		{
			if (!firstItem)
			{
				stringBuilder.append(delimiter);
			}
			
			stringBuilder.append(item);
			firstItem = false;
		}
		
		return stringBuilder.toString();
	}
	
	/**
	 * Converts the given byte array into a hex string representation. <br>
	 * source: https://stackoverflow.com/a/9855338
	 * 
	 * This function uses Rust FFM for performance.
	 */
	public static String byteArrayToHexString(byte[] bytes)
	{
		try (Arena arena = Arena.ofConfined()) {
			// Allocate native memory for input and output
			MemorySegment inputSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
			MemorySegment outputSegment = arena.allocate(ValueLayout.JAVA_BYTE, bytes.length * 2);
			
			// Call native function
			int resultLen = (int) byteArrayToHexHandle.invokeExact(
				inputSegment,
				(long) bytes.length,
				outputSegment
			);
			
			if (resultLen < 0) {
				throw new RuntimeException("Native byteArrayToHexString failed");
			}
			
			// Convert output bytes to Java string
			byte[] hexBytes = outputSegment.toArray(ValueLayout.JAVA_BYTE);
			return new String(hexBytes, 0, resultLen);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native byteArrayToHexString", e);
		}
	}
	
	/**
	 * Returns a shortened version of the given string that is no longer than maxLength. <br>
	 * If null returns the empty string.
	 */
	public static String shortenString(String str, int maxLength)
	{
		if (str == null)
		{
			return "";
		}
		else
		{
			return str.substring(0, Math.min(str.length(), maxLength));
		}
	}
	
	/**
	 * Source:
	 * https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java#3758880
	 */
	public static String convertBytesToHumanReadable(long bytes)
	{
		if (-1000 < bytes && bytes < 1000)
		{
			return bytes + " B";
		}
		CharacterIterator ci = new StringCharacterIterator("kMGTPE");
		while (bytes <= -999_950 || bytes >= 999_950)
		{
			bytes /= 1000;
			ci.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}
	
	
	
}
