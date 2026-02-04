package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import com.seibel.distanthorizons.coreapi.util.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * A (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 3 element float vector.
 *
 * Static distance calculation methods now use Rust FFM for performance.
 *
 * @author James Seibel
 * @version 2024-02-04 (Rust FFM migration for static distance methods)
 */
public class Vec3f extends DhApiVec3f
{
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup LIBRARY;
	
	// Function handles for native calls
	private static final MethodHandle getManhattanDistanceHandle;
	private static final MethodHandle getDistanceHandle;
	
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
			getManhattanDistanceHandle = LINKER.downcallHandle(
				findFunction("vec3f_get_manhattan_distance"),
				FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, 
					ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
					ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			getDistanceHandle = LINKER.downcallHandle(
				findFunction("vec3f_get_distance"),
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, 
					ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
					ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT)
			);
			
			System.out.println("[MattMC] Successfully loaded Rust native library for Vec3f: " + libraryName);
		} catch (Throwable e) {
			// NO FALLBACK - fail hard as requested
			System.err.println("FATAL: Failed to load Rust native library for Vec3f!");
			System.err.println("This is a critical error. The game cannot continue without the native library.");
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static MemorySegment findFunction(String name) {
		return LIBRARY.find(name)
			.orElseThrow(() -> new UnsatisfiedLinkError("Missing function: " + name));
	}
	
	//==============//
	// constructors //
	//==============//
	
	public Vec3f() { this(0,0,0); }
	
	public Vec3f(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vec3f(DhApiVec3f pos)
	{
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}
	
	public Vec3f(Vec3d pos)
	{
		this.x = (float) pos.x;
		this.y = (float) pos.y;
		this.z = (float) pos.z;
	}
	
	
	
	
	//==============//
	// math methods //
	//==============//
	
	public void mul(float scalar)
	{
		this.x *= scalar;
		this.y *= scalar;
		this.z *= scalar;
	}
	
	public void mul(float x, float y, float z)
	{
		this.x *= x;
		this.y *= y;
		this.z *= z;
	}
	
	public void clamp(float min, float max)
	{
		this.x = MathUtil.clamp(min, this.x, max);
		this.y = MathUtil.clamp(min, this.y, max);
		this.z = MathUtil.clamp(min, this.z, max);
	}
	
	public void add(float x, float y, float z)
	{
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public void add(Vec3f vector)
	{
		this.x += vector.x;
		this.y += vector.y;
		this.z += vector.z;
	}
	
	public void subtract(Vec3f vector)
	{
		this.x -= vector.x;
		this.y -= vector.y;
		this.z -= vector.z;
	}
	
	public float dotProduct(Vec3f vector) { return this.x * vector.x + this.y * vector.y + this.z * vector.z; }
	
	/** @return true if normalization had to be done */
	public boolean normalize()
	{
		float squaredSum = this.x * this.x + this.y * this.y + this.z * this.z;
		if (squaredSum < 1.0E-5D)
		{
			return false;
		}
		else
		{
			float f1 = MathUtil.fastInvSqrt(squaredSum);
			this.x *= f1;
			this.y *= f1;
			this.z *= f1;
			return true;
		}
	}
	
	public void crossProduct(Vec3f vector)
	{
		float f = this.x;
		float f1 = this.y;
		float f2 = this.z;
		float f3 = vector.x;
		float f4 = vector.y;
		float f5 = vector.z;
		this.x = f1 * f5 - f2 * f4;
		this.y = f2 * f3 - f * f5;
		this.z = f * f4 - f1 * f3;
	}
	
	public static float getManhattanDistance(DhApiVec3f a, DhApiVec3f b)
	{
		try {
			return (float) getManhattanDistanceHandle.invokeExact(a.x, a.y, a.z, b.x, b.y, b.z);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getManhattanDistance", e);
		}
	}
	
	public static double getDistance(DhApiVec3f a, DhApiVec3f b)
	{
		try {
			return (double) getDistanceHandle.invokeExact(a.x, a.y, a.z, b.x, b.y, b.z);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to call native getDistance", e);
		}
	}
	
	
	
	//==============//
	// misc methods //
	//==============//
	
	public void set(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Vec3f copy() { return new Vec3f(this.x, this.y, this.z); }
	
}
