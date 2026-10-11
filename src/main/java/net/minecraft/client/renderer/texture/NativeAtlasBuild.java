package net.minecraft.client.renderer.texture;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.blaze3d.platform.NativeImage;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.NativeLog;
import org.jetbrains.annotations.Nullable;

/**
 * One atlas build in Rust (`assets/textures`): sprite sources, decoded and
 * preprocessed sprites, the stitched layout and mip levels. Java copies the
 * finished pixels into its own images, then closes the build.
 */
final class NativeAtlasBuild implements AutoCloseable {
	private static final Cleaner CLEANER = Cleaner.create();
	private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
	private static final MethodHandle LOAD = handle("atlas_load", ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle METADATA = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_atlas_metadata",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle DROP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_atlas_drop",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle STITCH = handle("atlas_stitch", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle GENERATE_MIPS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_atlas_generate_mips",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
	private static final MethodHandle COPY = handle("atlas_copy", ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_atlas_release",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
	private static final MethodHandle PREPARE = handle("sprite_prepare", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle COMPOSE = handle("atlas_compose", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle BUFFER_FREE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_buffer_free",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	/** Compiled `filter` source regexes, by pattern text. */
	private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

	static {
		NativeLog.ensureRegistered();
		try {
			MethodHandle target = MethodHandles.lookup().findStatic(NativeAtlasBuild.class, "pattern",
				MethodType.methodType(int.class, MemorySegment.class, long.class, MemorySegment.class, long.class, int.class));
			MemorySegment stub = Linker.nativeLinker().upcallStub(target, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
				ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), Arena.global());
			MethodHandle register = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_textures_register_pattern",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			register.invokeExact(stub);
		} catch (Throwable throwable) {
			throw new ExceptionInInitializerError(throwable);
		}
	}

	private final AtomicHandle handle;

	private NativeAtlasBuild(long handle) {
		this.handle = new AtomicHandle(handle);
		CLEANER.register(this, this.handle::release);
	}

	private static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.of(result, arguments));
	}

	/** `ResourceLocationPattern` checks for Rust: Java regexes matched with `find()`. */
	private static int pattern(MemorySegment pattern, long patternLength, MemorySegment text, long textLength, int compile) {
		String regex = utf8(pattern, patternLength);
		Pattern compiled;
		try {
			compiled = PATTERNS.computeIfAbsent(regex, Pattern::compile);
		} catch (PatternSyntaxException invalid) {
			return -1;
		}
		return compile != 0 ? 0 : compiled.matcher(utf8(text, textLength)).find() ? 1 : 0;
	}

	private static String utf8(MemorySegment text, long length) {
		return length == 0L ? "" : new String(text.reinterpret(length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
	}

	/** The native handle, released once by `close` or the cleaner. */
	private static final class AtomicHandle {
		private long value;

		AtomicHandle(long value) {
			this.value = value;
		}

		synchronized long get() {
			if (this.value == 0L) {
				throw new IllegalStateException("Atlas build is closed");
			}
			return this.value;
		}

		synchronized void release() {
			if (this.value != 0L) {
				try {
					RELEASE.invokeExact(this.value);
				} catch (Throwable throwable) {
					throw new IllegalStateException("Release atlas build failed", throwable);
				} finally {
					this.value = 0L;
				}
			}
		}
	}

	private static MemorySegment text(Arena arena, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
	}

	private static byte[] take(MemorySegment pointer, long length) throws Throwable {
		if (length == 0L) {
			return new byte[0];
		}
		MemorySegment buffer = pointer.get(ValueLayout.ADDRESS, 0).reinterpret(length);
		try {
			return buffer.toArray(ValueLayout.JAVA_BYTE);
		} finally {
			BUFFER_FREE.invokeExact(buffer, length);
		}
	}

	private static IllegalStateException failure(String what, Throwable throwable) {
		return new IllegalStateException(what, throwable);
	}

	/**
	 * Runs the atlas definition `definition` against `manager` and loads
	 * every sprite of the atlas `texture` (`SpriteSourceList` and
	 * `SpriteResourceLoader`). With
	 * `keepMetadata`, sprites keep their sidecar JSON for Java's extra
	 * metadata sections.
	 */
	static NativeAtlasBuild load(MultiPackResourceManager manager, ResourceLocation texture, ResourceLocation definition, boolean keepMetadata) {
		try (Arena arena = Arena.ofConfined()) {
			String textureId = texture.toString();
			String definitionId = definition.toString();
			MemorySegment error = arena.allocate(1024);
			long handle = (long)LOAD.invokeExact(manager.nativeHandle(), text(arena, textureId), (long)textureId.getBytes(StandardCharsets.UTF_8).length,
				text(arena, definitionId), (long)definitionId.getBytes(StandardCharsets.UTF_8).length, keepMetadata ? 1 : 0, error, 1024L);
			if (handle == 0L) {
				throw new net.minecraft.ResourceLocationException(error.getString(0L, StandardCharsets.UTF_8));
			}
			return new NativeAtlasBuild(handle);
		} catch (net.minecraft.ResourceLocationException invalid) {
			throw invalid;
		} catch (Throwable throwable) {
			throw failure("Load atlas " + definition + " failed", throwable);
		} finally {
			Reference.reachabilityFence(manager);
		}
	}

	/** A sprite's kept sidecar JSON. */
	record Metadata(int index, ResourceLocation name, String json) {
	}

	List<Metadata> metadata() {
		byte[] bytes;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			METADATA.invokeExact(this.handle.get(), pointer, length);
			bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable throwable) {
			throw failure("Read atlas metadata failed", throwable);
		}
		Reader reader = new Reader(bytes);
		List<Metadata> list = new ArrayList<>();
		while (reader.remaining()) {
			int index = reader.u32();
			ResourceLocation name = ResourceLocation.fromNamespaceAndPath(reader.text(), reader.text());
			list.add(new Metadata(index, name, reader.text()));
		}
		return list;
	}

	void drop(int[] indices) {
		if (indices.length == 0) {
			return;
		}
		try (Arena arena = Arena.ofConfined()) {
			DROP.invokeExact(this.handle.get(), arena.allocateFrom(ValueLayout.JAVA_INT, indices), (long)indices.length);
		} catch (Throwable throwable) {
			throw failure("Drop atlas sprites failed", throwable);
		}
	}

	/** A stitched sprite: its build index, placement, sizes, flags and frames. */
	record Placed(
		int index, int x, int y, int width, int height, int sheetWidth, int sheetHeight, boolean transparent, boolean translucent,
		boolean interpolate, ResourceLocation name, int frameRowSize, @Nullable List<SpriteContents.FrameInfo> frames
	) {
	}

	record Layout(int width, int height, int mipLevel, List<Placed> sprites) {
	}

	/**
	 * `SpriteLoader.stitch`'s layout. A sprite that does not fit throws the
	 * same crash report Java's stitcher did.
	 */
	Layout stitch(int mipLevel, int maxSize, int minWidth, int minHeight) {
		int status;
		byte[] bytes;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			status = (int)STITCH.invokeExact(this.handle.get(), mipLevel, maxSize, minWidth, minHeight, pointer, length);
			if (status < 0) {
				throw new IllegalStateException("Atlas build is invalid");
			}
			bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable throwable) {
			throw failure("Stitch atlas failed", throwable);
		}
		Reader reader = new Reader(bytes);
		if (status == 1) {
			String sprite = reader.text();
			int spriteWidth = reader.u32();
			int spriteHeight = reader.u32();
			int count = reader.u32();
			List<String> sprites = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				sprites.add(reader.text());
			}
			StitcherException exception = new StitcherException(sprite, spriteWidth, spriteHeight, sprites);
			CrashReport crashReport = CrashReport.forThrowable(exception, "Stitching");
			CrashReportCategory category = crashReport.addCategory("Stitcher");
			category.setDetail("Sprites", String.join(",", sprites));
			category.setDetail("Max Texture Size", maxSize);
			throw new ReportedException(crashReport);
		}
		int width = reader.u32();
		int height = reader.u32();
		int level = reader.u32();
		int count = reader.u32();
		List<Placed> sprites = new ArrayList<>(count);
		for (int position = 0; position < count; position++) {
			int index = reader.u32();
			int x = reader.u32();
			int y = reader.u32();
			int spriteWidth = reader.u32();
			int spriteHeight = reader.u32();
			int sheetWidth = reader.u32();
			int sheetHeight = reader.u32();
			int flags = reader.u32();
			ResourceLocation name = ResourceLocation.fromNamespaceAndPath(reader.text(), reader.text());
			int rowSize = reader.u32();
			int frameCount = reader.u32();
			List<SpriteContents.FrameInfo> frames = null;
			if (frameCount > 0) {
				frames = new ArrayList<>(frameCount);
				for (int frame = 0; frame < frameCount; frame++) {
					frames.add(new SpriteContents.FrameInfo(reader.u32(), reader.u32()));
				}
				frames = List.copyOf(frames);
			}
			sprites.add(new Placed(index, x, y, spriteWidth, spriteHeight, sheetWidth, sheetHeight, (flags & 1) != 0, (flags & 2) != 0,
				(flags & 4) != 0, name, rowSize, frames));
		}
		return new Layout(width, height, level, sprites);
	}

	void generateMips() {
		try {
			GENERATE_MIPS.invokeExact(this.handle.get());
		} catch (Throwable throwable) {
			throw failure("Generate atlas mip levels failed", throwable);
		}
	}

	/** Copies mip `level` of each sprite (by build index) into `images`. */
	void copy(int level, int[] indices, NativeImage[] images) {
		int count = indices.length;
		if (count == 0) {
			return;
		}
		long[] pointers = new long[count];
		long[] lengths = new long[count];
		for (int index = 0; index < count; index++) {
			pointers[index] = images[index].getPointer();
			lengths[index] = (long)images[index].getWidth() * images[index].getHeight() * 4L;
		}
		long mismatched;
		try (Arena arena = Arena.ofConfined()) {
			mismatched = (long)COPY.invokeExact(this.handle.get(), level, (long)count, arena.allocateFrom(ValueLayout.JAVA_INT, indices),
				arena.allocateFrom(ValueLayout.JAVA_LONG, pointers), arena.allocateFrom(ValueLayout.JAVA_LONG, lengths));
		} catch (Throwable throwable) {
			throw failure("Copy atlas sprites failed", throwable);
		} finally {
			Reference.reachabilityFence(images);
		}
		if (mismatched != 0L) {
			throw new IllegalStateException(mismatched + " atlas sprite images did not match their mip level " + level);
		}
	}

	@Override
	public void close() {
		this.handle.release();
	}

	/** The prepared state of a sprite built in Java (see `SpriteContents`'s compatibility constructor). */
	record Prepared(boolean transparent, boolean translucent, int frameRowSize, @Nullable List<SpriteContents.FrameInfo> frames) {
	}

	/**
	 * Validates a sprite's declared animation and applies Sodium's
	 * transparent-pixel fill to `image` in place.
	 */
	static Prepared prepare(ResourceLocation name, NativeImage image, int frameWidth, int frameHeight,
		@Nullable List<int[]> declaredFrames, boolean animated, int frameTime, boolean interpolate) {
		byte[] bytes;
		try (Arena arena = Arena.ofConfined()) {
			int[] pairs = new int[declaredFrames == null ? 0 : declaredFrames.size() * 2];
			if (declaredFrames != null) {
				for (int index = 0; index < declaredFrames.size(); index++) {
					pairs[index * 2] = declaredFrames.get(index)[0];
					pairs[index * 2 + 1] = declaredFrames.get(index)[1];
				}
			}
			MemorySegment frames = pairs.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_INT, pairs);
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			String namespace = name.getNamespace();
			String path = name.getPath();
			int status = (int)PREPARE.invokeExact(text(arena, namespace), (long)namespace.getBytes(StandardCharsets.UTF_8).length, text(arena, path),
				(long)path.getBytes(StandardCharsets.UTF_8).length, image.getPointer(), image.getWidth(), image.getHeight(), frameWidth, frameHeight,
				animated ? 1 : 0, frames, declaredFrames == null ? -1 : declaredFrames.size(), frameTime, interpolate ? 1 : 0, pointer, length);
			if (status != 0) {
				throw new IllegalArgumentException("Invalid sprite " + name);
			}
			bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (IllegalArgumentException invalid) {
			throw invalid;
		} catch (Throwable throwable) {
			throw failure("Prepare sprite " + name + " failed", throwable);
		} finally {
			Reference.reachabilityFence(image);
		}
		Reader reader = new Reader(bytes);
		int flags = reader.u32();
		int rowSize = reader.u32();
		int count = reader.u32();
		List<SpriteContents.FrameInfo> frames = null;
		if (count > 0) {
			frames = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				frames.add(new SpriteContents.FrameInfo(reader.u32(), reader.u32()));
			}
			frames = List.copyOf(frames);
		}
		return new Prepared((flags & 1) != 0, (flags & 2) != 0, rowSize, frames);
	}

	/**
	 * Composes the first-frame atlas at mip `level` from each sprite's image
	 * at that level, or null when a sprite does not fit.
	 */
	@Nullable
	static byte[] compose(int width, int height, int level, List<TextureAtlasSprite> sprites) {
		long[] records = new long[sprites.size() * 9];
		List<NativeImage> retained = new ArrayList<>(sprites.size());
		for (int index = 0; index < sprites.size(); index++) {
			TextureAtlasSprite sprite = sprites.get(index);
			SpriteContents contents = sprite.contents();
			if (level >= contents.byMipLevel.length || contents.byMipLevel[level] == null) {
				return null;
			}
			NativeImage image = contents.byMipLevel[level];
			retained.add(image);
			int frameColumn = 0;
			int frameRow = 0;
			if (contents.animatedTexture != null) {
				if (contents.animatedTexture.frames.isEmpty()) {
					return null;
				}
				int frame = contents.semanticFrameIndex();
				frameColumn = contents.animatedTexture.getFrameX(frame);
				frameRow = contents.animatedTexture.getFrameY(frame);
			}
			int at = index * 9;
			records[at] = image.getPointer();
			records[at + 1] = image.getWidth();
			records[at + 2] = image.getHeight();
			records[at + 3] = contents.width();
			records[at + 4] = contents.height();
			records[at + 5] = frameColumn;
			records[at + 6] = frameRow;
			records[at + 7] = sprite.getX();
			records[at + 8] = sprite.getY();
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment input = records.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_LONG, records);
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)COMPOSE.invokeExact(width, height, level, input, (long)sprites.size(), pointer, length);
			return status == 0 ? take(pointer, length.get(ValueLayout.JAVA_LONG, 0)) : null;
		} catch (Throwable throwable) {
			throw failure("Compose atlas snapshot failed", throwable);
		} finally {
			Reference.reachabilityFence(retained);
		}
	}

	/** A little-endian record reader. */
	private static final class Reader {
		private final MemorySegment buffer;
		private final byte[] bytes;
		private int at;

		Reader(byte[] bytes) {
			this.bytes = bytes;
			this.buffer = MemorySegment.ofArray(bytes);
		}

		boolean remaining() {
			return this.at < this.bytes.length;
		}

		int u32() {
			int value = this.buffer.get(INT, this.at);
			this.at += 4;
			return value;
		}

		String text() {
			int length = this.u32();
			String text = new String(this.bytes, this.at, length, StandardCharsets.UTF_8);
			this.at += length;
			return text;
		}
	}
}
