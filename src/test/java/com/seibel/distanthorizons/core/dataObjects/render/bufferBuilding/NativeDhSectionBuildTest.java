package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Rust section build against the Java reference traversal and geometry
 * (HEAD 1490391f9) over randomized sections: neighbors, debug colors, color
 * adjustment, transparency, cave culling, semantic planes, spans and
 * contributor footprints. Quad rows, identity tables and packet bytes must match.
 */
class NativeDhSectionBuildTest {
	@Test @Tag("parity") void ordinaryBuildsMatchTheJavaReference(@TempDir Path directory) throws Exception { probe(directory, false); }
	@Test @Tag("parity") void exactTopologyBuildsMatchTheJavaReference(@TempDir Path directory) throws Exception { probe(directory, true); }

	// The build classes capture the render wrapper statically, and exact
	// topology is a launch property, so each mode runs in a fresh JVM.
	private void probe(Path directory, boolean exact) throws Exception {
		var paths = new LinkedHashSet<String>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
		for (var loader = getClass().getClassLoader(); loader != null; loader = loader.getParent())
			if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs())
				if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
		var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			"--enable-native-access=ALL-UNNAMED", "-Xmx2g",
			"-Dmattmc.rust.natives.dir=" + System.getProperty("mattmc.rust.natives.dir"),
			"-Dmattmc.dev.rustGalDistantHorizons.exactMaterialTopology=" + exact,
			"-cp", String.join(File.pathSeparator, paths), Probe.class.getName());
		var log = directory.resolve("section.log");
		var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		if (!process.waitFor(300, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(); fail(Files.readString(log)); }
		assertEquals(0, process.exitValue(), Files.readString(log));
		assertTrue(Files.readString(log).contains("SECTION BUILDS MATCH"), Files.readString(log));
		Files.readAllLines(log).stream().filter(line -> line.contains("SECTION BUILDS MATCH")).forEach(System.out::println);
	}

	public static final class Probe {
		static final float[][] SHADES = {
			{0f, 0f, 0f, 0f, 0f, 0f},              // AUTO without a level
			{.5f, 1f, .8f, .8f, .6f, .6f},         // ENABLED
			{1f, 1f, 1f, 1f, 1f, 1f},              // DISABLED
		};
		static int shadeMode = 1;
		static long quads, identities, errors;

		public static void main(String[] args) throws Exception {
			var render = (IMinecraftRenderWrapper) Proxy.newProxyInstance(IMinecraftRenderWrapper.class.getClassLoader(),
				new Class<?>[]{IMinecraftRenderWrapper.class, IMinecraftRenderWrapper.NativeShadeFacts.class}, (p, m, a) -> switch (m.getName()) {
					case "getNativeShadeFacts" -> shadeMode;
					case "getShade" -> SHADES[shadeMode][((EDhDirection) a[0]).ordinal()];
					case "getDelayedSetupComplete" -> true;
					default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
				});
			SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class, render);
			var world = (IClientLevelWrapper) Proxy.newProxyInstance(IClientLevelWrapper.class.getClassLoader(),
				new Class<?>[]{IClientLevelWrapper.class}, (p, m, a) -> switch (m.getName()) {
					case "getMinHeight" -> -64;
					case "getDirtBlockColor" -> 0xff7a5a3a;
					default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
				});
			var level = (IDhClientLevel) Proxy.newProxyInstance(IDhClientLevel.class.getClassLoader(),
				new Class<?>[]{IDhClientLevel.class}, (p, m, a) -> switch (m.getName()) {
					case "getLevelWrapper", "getClientLevelWrapper" -> world;
					default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
				});
			int builds = 0;
			for (int seed = 0; seed < 240; seed++)
			{
				var random = new Random(seed);
				configure(random);
				byte detail = (byte) (seed % 4 == 0 ? 0 : 1 + random.nextInt(4));
				int height = 4 + random.nextInt(12);
				long pos = DhSectionPos.encode((byte) (DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + detail), random.nextInt(9) - 4, random.nextInt(9) - 4);
				var sources = new ArrayList<ColumnRenderSource>();
				try
				{
					var center = source(random, pos, height, true);
					sources.add(center);
					var adjacent = new ColumnRenderSource[4];
					var same = new boolean[4];
					for (int d = 0; d < 4; d++)
					{
						same[d] = random.nextBoolean();
						if (random.nextInt(4) == 0) continue;
						adjacent[d] = source(random, pos, random.nextInt(6) == 0 ? height + 2 : height, false);
						sources.add(adjacent[d]);
					}
					compare(seed, center, adjacent, same, level, world);
					builds++;
				}
				finally
				{
					for (var source : sources) source.close();
				}
			}
			System.out.println("SECTION BUILDS MATCH " + builds + " quads=" + quads + " identities=" + identities + " errors=" + errors);
			if (quads < 100_000 || identities == 0) throw new AssertionError("Section oracle exercised too little");
		}

		static void configure(Random random)
		{
			Config.Client.Advanced.Graphics.Quality.transparency.setWithoutFiringEvents(EDhApiTransparency.values()[random.nextInt(3)]);
			var debug = random.nextInt(4) == 0 ? EDhApiDebugRendering.values()[random.nextInt(EDhApiDebugRendering.values().length)] : EDhApiDebugRendering.OFF;
			Config.Client.Advanced.Debugging.debugRendering.setWithoutFiringEvents(debug);
			boolean adjust = random.nextInt(3) == 0;
			Config.Client.Advanced.Graphics.Quality.saturationMultiplier.setWithoutFiringEvents(adjust ? random.nextDouble() * 2 : 1.0);
			Config.Client.Advanced.Graphics.Quality.brightnessMultiplier.setWithoutFiringEvents(adjust ? random.nextDouble() * 2 : 1.0);
			Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(random.nextBoolean());
			Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(random.nextInt(160) - 64);
			Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(random.nextInt(5) == 0 ? 100 : 0);
			Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(random.nextInt(5) == 0);
			shadeMode = random.nextInt(3);
		}

		static ColumnRenderSource source(Random random, long pos, int height, boolean semantic)
		{
			var source = ColumnRenderSource.createEmpty(pos, height, -64);
			source.markNotEmpty();
			source.fillDebugFlag(0, 0, 64, 64, ColumnRenderSource.DebugSourceFlag.values()[random.nextInt(3)]);
			if (random.nextInt(8) == 0) source.debugSourceFlags[random.nextInt(4096)] = null;
			int identities = 0;
			if (semantic) for (int i = 0; i < 12; i++) identities = source.internSemanticMaterial("minecraft:block" + i, "minecraft:biome" + i % 3);
			byte detail = source.getDataDetailLevel();
			for (int x = 0; x < 64; x++)
			{
				for (int z = 0; z < 64; z++)
				{
					if (random.nextInt(9) == 0) continue;
					int top = 40 + random.nextInt(200);
					int count = 1 + random.nextInt(height);
					var view = source.getVerticalDataPointView(x, z);
					for (int i = 0; i < count && top > 1; i++)
					{
						int bottom = Math.max(0, top - 1 - random.nextInt(24));
						int material = random.nextInt(16);
						int alpha = random.nextInt(3) == 0 ? random.nextInt(256) : 255;
						view.set(i, RenderDataPointUtil.createDataPoint(alpha, random.nextInt(256), random.nextInt(256), random.nextInt(256),
							top, bottom, random.nextInt(16), random.nextInt(16), material));
						if (semantic && random.nextInt(3) != 0)
						{
							source.setSemanticMaterialId(x, z, i, random.nextInt(6) == 0 ? ColumnRenderSource.SEMANTIC_MATERIAL_MIXED : random.nextInt(identities + 1));
							source.setSemanticVariantProvenance(x, z, i, (byte) random.nextInt(3), 0L);
						}
						if (semantic && random.nextInt(5) == 0 && top - bottom > 1)
						{
							int middle = bottom + 1 + random.nextInt(top - bottom - 1);
							source.setSemanticMaterialSpans(x, z, i, List.of(
								span(bottom, middle, random.nextInt(identities + 1), (byte) random.nextInt(3), random.nextInt(9)),
								new ColumnRenderSource.SemanticMaterialSpan(middle, top, random.nextInt(identities + 1), (byte) 1, random.nextInt(9))));
						}
						if (semantic && detail > 0 && random.nextInt(4) == 0)
						{
							var contributors = new ColumnRenderSource.SemanticHorizontalContributor[4];
							int shared = 1 + random.nextInt(identities);
							for (int c = 0; c < 4; c++)
							{
								if (random.nextInt(10) == 0) continue;
								int id = random.nextInt(3) == 0 ? 1 + random.nextInt(identities) : shared;
								if (random.nextBoolean() && top - bottom > 1)
								{
									int middle = bottom + 1 + random.nextInt(top - bottom - 1);
									contributors[c] = new ColumnRenderSource.SemanticHorizontalContributor(List.of(
										new ColumnRenderSource.SemanticMaterialSpan(bottom - random.nextInt(2), middle, id, (byte) 1, 7),
										new ColumnRenderSource.SemanticMaterialSpan(middle, top + random.nextInt(2), shared, (byte) 1, 9)));
								}
								else contributors[c] = new ColumnRenderSource.SemanticHorizontalContributor(List.of(
									span(bottom - random.nextInt(2), top, id, (byte) (random.nextInt(4) == 0 ? 2 : 1), 5)));
							}
							// Java's recovery dereferences every contributor; it is only exercised on complete sets.
							if (Arrays.stream(contributors).allMatch(Objects::nonNull)) source.setSemanticHorizontalContributorSpans(x, z, i, contributors);
						}
						top = bottom - random.nextInt(3);
					}
					if (semantic && detail > 0) source.setSemanticHorizontalUniformity(x, z, 0, random.nextBoolean());
				}
			}
			return source;
		}

		/** Only exact spans keep a position. */
		static ColumnRenderSource.SemanticMaterialSpan span(int min, int max, int id, byte variant, long position)
		{
			return new ColumnRenderSource.SemanticMaterialSpan(min, max, id, variant, variant == ColumnRenderSource.SEMANTIC_VARIANT_EXACT ? position : 0);
		}

		static void compare(int seed, ColumnRenderSource center, ColumnRenderSource[] adjacent, boolean[] same,
			IDhClientLevel level, IClientLevelWrapper world) throws Exception
		{
			boolean transparency = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
			try (var actual = new LodQuadBuilder(transparency, world); var expected = new JavaQuadListReference(transparency, world))
			{
				String actualError = "", expectedError = "";
				try { ColumnRenderBufferBuilder.makeLodRenderData(actual, center, level, adjacent, same); }
				catch (IllegalArgumentException e) { actualError = e.getClass().getName(); }
				try { JavaSectionBuildReference.makeLodRenderData(expected, center, level, adjacent, same); }
				catch (IllegalArgumentException e) { expectedError = e.getClass().getName(); }
				assertEquals(expectedError, actualError, "error seed " + seed);
				if (!expectedError.isEmpty()) { errors++; return; }
				quads += actual.getCurrentOpaqueQuadsCount() + actual.getCurrentTransparentQuadsCount();
				identities += actual.semanticMaterials().size();
				assertEquals(expected.semanticMaterials(), actual.semanticMaterials(), "identities seed " + seed);
				NativeDhFacesTest.Probe.equal(actual, expected);
				var packets = NativeDhQuadBuilderTest.packets(actual);
				var reference = new LodQuadBuilder.SemanticVertexBufferBuild[]{expected.makeOpaqueRustSemanticBuffers(),
					expected.makeTransparentRustSemanticBuffers(), expected.makeTransparentUpRustSemanticBuffers(),
					expected.makeTransparentWaterUpRustSemanticBuffers()};
				for (int layer = 0; layer < 4; layer++)
				{
					assertArrayEquals(flat(reference[layer].packedVertexBuffers()), flat(packets[layer].packedVertexBuffers()), "packets seed " + seed + " layer " + layer);
					assertArrayEquals(flatInts(reference[layer].semanticMaterialIds()), flatInts(packets[layer].semanticMaterialIds()), "ids seed " + seed + " layer " + layer);
				}
			}
		}
		static byte[] flat(List<byte[]> parts) { var out = new java.io.ByteArrayOutputStream(); parts.forEach(out::writeBytes); return out.toByteArray(); }
		static int[] flatInts(List<int[]> parts) { return parts.stream().flatMapToInt(Arrays::stream).toArray(); }
	}
}
