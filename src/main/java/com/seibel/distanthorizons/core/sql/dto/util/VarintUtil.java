package com.seibel.distanthorizons.core.sql.dto.util;

import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * FFM-based VarintUtil using Rust native library.
 * This class provides variable-length integer encoding utilities implemented in Rust for performance.
 * 
 * IMPORTANT: This implementation has NO Java fallback. If the native library fails to load,
 * the game will fail hard as intended.
 *
 * @author James Seibel (original design)
 * @version 2024-02-04 (Rust FFM migration for zigzag encoding/decoding)
 */
public class VarintUtil
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle zigzagEncodeHandle;
	private static final MethodHandle zigzagDecodeHandle;
	
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
			zigzagEncodeHandle = LINKER.downcallHandle(
				findFunction("varintutil_zigzag_encode"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			zigzagDecodeHandle = LINKER.downcallHandle(
				findFunction("varintutil_zigzag_decode"),
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for VarintUtil: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for VarintUtil!");
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
	 * zigzagEncode maps 0=>0, -1=>1, 1=>2, -2=>3, 3=>4, etc.
	 * this helps encode small magnitude signed numbers as small varints.
	 * https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/
	 */
	public static int zigzagEncode(int n)
	{
		try {
			return (int) zigzagEncodeHandle.invokeExact(n);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native zigzagEncode", e);
		}
	}
	
	public static int zigzagDecode(int n)
	{
		try {
			return (int) zigzagDecodeHandle.invokeExact(n);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native zigzagDecode", e);
		}
	}
	
	
	
	/**
	 * @param value should be a zigzag encoded value 
	 *          created via {@link VarintUtil#zigzagEncode(int)}
	 */
	public static void writeVarint(DhDataOutputStream out, int value) throws IOException
	{
		if (value < 0)
		{
			throw new IllegalArgumentException("varint given ["+value+"], varint only accepts positive values.");
		}
		
		while (value >= 128)
		{
			out.writeByte(value | 128);
			value >>>= 7; // 128 = 2^7
		}
		out.writeByte(value);
	}
	
	public static int readVarint(DhDataInputStream in) throws IOException
	{
		int value = 0;
		int shift = 0;
		byte b;
		do
		{
			if (shift >= 32)
			{
				throw new IOException("invalid varint");
			}
			b = in.readByte();
			value |= (b & 127) << shift;
			shift += 7;
		}
		while ((b & 128) != 0);
		return value;
	}
	
	
	
}
