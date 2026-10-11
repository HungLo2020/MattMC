import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.validation.DirectoryValidator;

/**
 * Same-API observer for Reference and Current atlas builds: every known
 * atlas (`AtlasManager.KNOWN_ATLASES`) built by `SpriteLoader.loadAndStitch`
 * over the checkout's resources, alone and with a fixture pack of edge cases.
 * Records each atlas's size, mip level and map order, and per sprite its
 * placement, frames, flags, extra metadata and pixel hashes per mip level,
 * plus the uploaded atlas's first-frame snapshot per level; then times
 * loading all atlases in parallel.
 *
 * A third stack, a fixture pack alone whose sprites all allow four mip
 * levels, exercises mip generation (the checkout's own blocks atlas cannot
 * mipmap: some sprites have odd sizes).
 *
 * Usage: AtlasReference content-dir fixture-packs-dir mip-packs-dir record-file iterations
 */
public final class AtlasReference {
	private static final int MIP_LEVELS = 4;
	private static final int MAX_SIZE = 16384;
	private static PrintWriter record;

	record Atlas(ResourceLocation texture, ResourceLocation definition, boolean mipmaps, Set<MetadataSectionType<?>> metadata) {
	}

	public static void main(String[] args) throws Exception {
		Path content = Path.of(args[0]);
		Path fixtures = Path.of(args[1]);
		Path mipFixtures = Path.of(args[2]);
		Path recordFile = Path.of(args[3]);
		int iterations = Integer.parseInt(args[4]);
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ClientBootstrap.bootstrap();
		snapshots = installSemanticDevice();
		List<Atlas> atlases = knownAtlases();
		PackResources vanilla = new VanillaPackResourcesBuilder()
			.setMetadata(BuiltInMetadata.of())
			.exposeNamespace("minecraft", "voxelmap", "sodium", "iris", "distanthorizons", "tacz")
			.pushUniversalPath(content.resolve("resources"))
			.build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.empty()));
		List<PackResources> fixturePacks = openPacks(fixtures);
		List<PackResources> mipPacks = openPacks(mipFixtures);
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(recordFile, StandardCharsets.UTF_8))) {
			record = writer;
			MultiPackResourceManager plain = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(vanilla));
			List<PackResources> withFixtures = new ArrayList<>(List.of(vanilla));
			withFixtures.addAll(fixturePacks);
			MultiPackResourceManager fixtured = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, withFixtures);
			observe("vanilla", plain, atlases, executor);
			observe("fixtures", fixtured, atlases, executor);
			observe("mips", new MultiPackResourceManager(PackType.CLIENT_RESOURCES, mipPacks), atlases, executor);
			List<Long> times = new ArrayList<>();
			for (int iteration = 0; iteration < iterations; iteration++) {
				long started = System.nanoTime();
				List<SpriteLoader.Preparations> loaded = loadAll(plain, atlases, executor);
				times.add(System.nanoTime() - started);
				loaded.forEach(AtlasReference::close);
			}
			writer.flush();
			times.sort(null);
			String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(recordFile)));
			System.out.println("ATLAS_REFERENCE sha256=" + sha + " atlases_ns=" + times.get(times.size() / 2));
		} finally {
			executor.shutdownNow();
		}
	}

	private static List<PackResources> openPacks(Path folder) {
		PackRepository repository = new PackRepository(
			new FolderRepositorySource(folder, PackType.CLIENT_RESOURCES, PackSource.DEFAULT, new DirectoryValidator(path -> true)));
		repository.reload();
		List<PackResources> packs = new ArrayList<>();
		for (Pack pack : repository.getAvailablePacks()) {
			packs.add(pack.open());
		}
		return packs;
	}

	/**
	 * The CPU semantic device the atlas tests install, so uploaded atlases can
	 * be snapshotted. Absent in Frozen (only timed there), which skips snapshots.
	 */
	private static boolean installSemanticDevice() {
		try {
			Field device = Class.forName("net.vulkanic.VulkanicAPI").getDeclaredField("device");
			device.setAccessible(true);
			device.set(null, Class.forName("net.vulkanic.bridge.RustSemanticGpuDevice").getConstructor().newInstance());
			return true;
		} catch (ReflectiveOperationException absent) {
			return false;
		}
	}

	private static boolean snapshots;

	@SuppressWarnings("unchecked")
	private static List<Atlas> knownAtlases() throws Exception {
		Field field = net.minecraft.client.resources.model.AtlasManager.class.getDeclaredField("KNOWN_ATLASES");
		field.setAccessible(true);
		List<Atlas> atlases = new ArrayList<>();
		for (Object config : (List<Object>)field.get(null)) {
			Class<?> type = config.getClass();
			atlases.add(new Atlas((ResourceLocation)component(type, config, "textureId"), (ResourceLocation)component(type, config, "definitionLocation"),
				(boolean)component(type, config, "createMipmaps"), (Set<MetadataSectionType<?>>)component(type, config, "additionalMetadata")));
		}
		return atlases;
	}

	private static Object component(Class<?> type, Object instance, String name) throws Exception {
		var accessor = type.getDeclaredMethod(name);
		accessor.setAccessible(true);
		return accessor.invoke(instance);
	}

	private static List<SpriteLoader.Preparations> loadAll(MultiPackResourceManager manager, List<Atlas> atlases, ExecutorService executor) {
		List<CompletableFuture<SpriteLoader.Preparations>> futures = new ArrayList<>();
		for (Atlas atlas : atlases) {
			futures.add(new SpriteLoader(atlas.texture(), MAX_SIZE, 0, 0)
				.loadAndStitch(manager, atlas.definition(), atlas.mipmaps() ? MIP_LEVELS : 0, executor, atlas.metadata())
				.thenCompose(preparations -> preparations.readyForUpload().thenApply(ignored -> preparations)));
		}
		return futures.stream().map(CompletableFuture::join).toList();
	}

	private static void close(SpriteLoader.Preparations preparations) {
		preparations.regions().values().forEach(sprite -> sprite.contents().close());
	}

	private static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static String pixels(NativeImage image) throws Exception {
		int[] abgr = image.getPixelsABGR();
		ByteBuffer buffer = ByteBuffer.allocate(abgr.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		buffer.asIntBuffer().put(abgr);
		return image.getWidth() + "x" + image.getHeight() + ":" + sha(buffer.array()).substring(0, 16);
	}

	private static void observe(String stack, MultiPackResourceManager manager, List<Atlas> atlases, ExecutorService executor) throws Exception {
		List<SpriteLoader.Preparations> loaded = loadAll(manager, atlases, executor);
		for (int index = 0; index < atlases.size(); index++) {
			Atlas atlas = atlases.get(index);
			SpriteLoader.Preparations preparations = loaded.get(index);
			record.println("== " + stack + " " + atlas.texture() + " " + preparations.width() + "x" + preparations.height() + " mip=" + preparations.mipLevel()
				+ " sprites=" + preparations.regions().size() + " missing=" + preparations.missing().contents().name());
			// The map's own order (consumers iterate it).
			record.println("order " + sha(preparations.regions().keySet().toString().getBytes(StandardCharsets.UTF_8)));
			for (Map.Entry<ResourceLocation, TextureAtlasSprite> entry : new TreeMap<>(preparations.regions()).entrySet()) {
				TextureAtlasSprite sprite = entry.getValue();
				SpriteContents contents = sprite.contents();
				StringBuilder line = new StringBuilder("sprite ").append(entry.getKey())
					.append(" at=").append(sprite.getX()).append(',').append(sprite.getY())
					.append(" size=").append(contents.width()).append('x').append(contents.height())
					.append(" uv=").append(Float.floatToIntBits(sprite.getU0())).append(',').append(Float.floatToIntBits(sprite.getV0()))
					.append(',').append(Float.floatToIntBits(sprite.getU1())).append(',').append(Float.floatToIntBits(sprite.getV1()))
					.append(" transparent=").append(((net.sodium.client.render.chunk.compile.pipeline.SpriteContentsExtension)contents).sodium$hasTransparentPixels())
					.append(" translucent=").append(((net.sodium.client.render.chunk.compile.pipeline.SpriteContentsExtension)contents).sodium$hasTranslucentPixels())
					.append(" unique=").append(java.util.Arrays.toString(contents.getUniqueFrames().toArray()));
				if (contents.animatedTexture != null) {
					Field interpolate = contents.animatedTexture.getClass().getDeclaredField("interpolateFrames");
					interpolate.setAccessible(true);
					line.append(" frames=").append(contents.animatedTexture.frames).append(" row=").append(contents.animatedTexture.frameRowSize)
						.append(" interpolate=").append(interpolate.get(contents.animatedTexture));
				}
				contents.getAdditionalMetadata(GuiMetadataSection.TYPE).ifPresent(gui -> line.append(" gui=").append(gui));
				line.append(" mips=");
				for (NativeImage image : contents.byMipLevel) {
					line.append(pixels(image)).append(';');
				}
				record.println(line);
			}
			snapshot(atlas, preparations);
		}
		loaded.forEach(AtlasReference::close);
	}

	/** Uploads into a `TextureAtlas` and records its first-frame snapshot per mip level. */
	private static void snapshot(Atlas atlas, SpriteLoader.Preparations preparations) throws Exception {
		if (!snapshots) {
			return;
		}
		TextureAtlas textureAtlas = new TextureAtlas(atlas.texture());
		try {
			textureAtlas.upload(preparations);
			Object snapshot = TextureAtlas.class.getMethod("semanticRawSnapshot").invoke(textureAtlas);
			if (snapshot == null) {
				record.println("snapshot none");
				return;
			}
			Class<?> type = snapshot.getClass();
			StringBuilder line = new StringBuilder("snapshot ").append(type.getMethod("width").invoke(snapshot)).append('x')
				.append(type.getMethod("height").invoke(snapshot));
			for (Object level : (List<?>)type.getMethod("mipPixels").invoke(snapshot)) {
				line.append(' ').append(sha((byte[])level).substring(0, 16));
			}
			record.println(line);
		} catch (java.lang.reflect.InvocationTargetException failure) {
			record.println("snapshot failed " + failure.getCause().getClass().getSimpleName());
		} finally {
			// Leave the sprites to the caller; only drop the atlas's own animation state.
			Field sprites = TextureAtlas.class.getDeclaredField("sprites");
			sprites.setAccessible(true);
			sprites.set(textureAtlas, List.of());
			textureAtlas.clearTextureData();
		}
	}
}
