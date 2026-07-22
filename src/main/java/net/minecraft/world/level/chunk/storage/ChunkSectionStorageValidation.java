package net.minecraft.world.level.chunk.storage;

import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO.PackedData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only chunk-section parity and fixture helpers.
 *
 * <p>Production section ownership lives in {@link SerializableChunkData}; this
 * class owns shadow comparison and validation-only residual mutation.
 */
public final class ChunkSectionStorageValidation {
	private static final String CUSTOM_ROOT_FIELD = "mattmc:chunk_section_validation_custom";

	private ChunkSectionStorageValidation() {
	}

	@Nullable
	public static SerializableChunkData parseWithRustSectionsForValidation(
		RegistryAccess registryAccess,
		LevelHeightAccessor levelHeightAccessor,
		PalettedContainerFactory palettedContainerFactory,
		CompoundTag compoundTag,
		Optional<NativeChunkSectionStorage.DecodeResult> rustSections,
		ChunkPos requestedChunkPos
	) {
		if (rustSections.isEmpty()) {
			ChunkSectionReadDiagnostics.javaFallback(requestedChunkPos, "pending-write-or-native-unavailable");
			return SerializableChunkData.parse(levelHeightAccessor, palettedContainerFactory, compoundTag);
		}

		NativeChunkSectionStorage.DecodeResult decodeResult = rustSections.get();
		NativeChunkSectionStorage.Result result = decodeResult.result();
		if (!result.present()) {
			ChunkSectionReadDiagnostics.absent(requestedChunkPos);
			return SerializableChunkData.parse(levelHeightAccessor, palettedContainerFactory, compoundTag);
		}
		if (result.requiresDfu() || result.dataVersion() < SharedConstants.getCurrentVersion().dataVersion().version()) {
			ChunkSectionReadDiagnostics.oldVersionFallback(requestedChunkPos, result.dataVersion());
			return SerializableChunkData.parse(levelHeightAccessor, palettedContainerFactory, compoundTag);
		}

		try {
			long resolveStarted = ChunkSectionReadDiagnostics.now();
			SerializableChunkData.NativeSectionBuild nativeBuild = SerializableChunkData.buildNativeSections(
				registryAccess,
				palettedContainerFactory,
				decodeResult.chunk()
			);
			long resolveNanos = ChunkSectionReadDiagnostics.elapsed(resolveStarted);
			long javaStarted = ChunkSectionReadDiagnostics.now();
			SerializableChunkData javaData = SerializableChunkData.parse(levelHeightAccessor, palettedContainerFactory, compoundTag);
			long javaParseNanos = ChunkSectionReadDiagnostics.elapsed(javaStarted);
			if (javaData == null) {
				return null;
			}
			long tickStarted = ChunkSectionReadDiagnostics.now();
			ChunkAccess.PackedTicks nativeTicks = SerializableChunkData.resolveNativeTicks(decodeResult, requestedChunkPos);
			long tickNanos = ChunkSectionReadDiagnostics.elapsed(tickStarted);
			long compareStarted = ChunkSectionReadDiagnostics.now();
			String mismatch = compareNativeSections(javaData, nativeBuild, palettedContainerFactory, decodeResult.chunk());
			if (mismatch == null) {
				mismatch = compareNativeTicks(javaData.packedTicks(), nativeTicks);
			}
			long compareNanos = ChunkSectionReadDiagnostics.elapsed(compareStarted);
			if (mismatch != null) {
				ChunkSectionReadDiagnostics.parityMismatch(requestedChunkPos, mismatch, result, javaParseNanos, resolveNanos, compareNanos);
				return javaData;
			}
			SerializableChunkData nativeData = javaData.withNativeSectionsAndTicks(
				nativeBuild.sections(),
				nativeBuild.heightmaps(),
				decodeResult.chunk().lightOn(),
				decodeResult.chunk().residual(),
				nativeTicks
			);
			ChunkSectionReadDiagnostics.rustDecoded(requestedChunkPos, result, javaParseNanos, resolveNanos, compareNanos);
			ChunkSectionReadDiagnostics.rustTicksDecoded(requestedChunkPos, result, nativeTicks, tickNanos);
			return nativeData;
		} catch (Exception exception) {
			ChunkSectionReadDiagnostics.malformed(requestedChunkPos, exception);
			return SerializableChunkData.parse(levelHeightAccessor, palettedContainerFactory, compoundTag);
		}
	}

	@Nullable
	public static String compareNativeSections(
		SerializableChunkData javaData,
		SerializableChunkData.NativeSectionBuild nativeBuild,
		PalettedContainerFactory palettedContainerFactory,
		NativeChunkSectionStorage.ChunkData nativeChunk
	) {
		if (!Objects.equals(javaData.chunkPos(), new ChunkPos(nativeChunk.chunkX(), nativeChunk.chunkZ()))) {
			return "chunk position mismatch java=" + javaData.chunkPos() + " rust=" + nativeChunk.chunkX() + "," + nativeChunk.chunkZ();
		}
		String javaStatus = net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(javaData.chunkStatus()).toString();
		if (!Objects.equals(javaStatus, nativeChunk.status())) {
			return "chunk status mismatch java=" + javaStatus + " rust=" + nativeChunk.status();
		}
		if (javaData.lightCorrect() != nativeChunk.lightOn()) {
			return "isLightOn mismatch java=" + javaData.lightCorrect() + " rust=" + nativeChunk.lightOn();
		}
		if (javaData.sectionData().size() != nativeBuild.sections().size()) {
			return "section count mismatch java=" + javaData.sectionData().size() + " rust=" + nativeBuild.sections().size();
		}
		for (int i = 0; i < javaData.sectionData().size(); i++) {
			String mismatch = compareSection(javaData.sectionData().get(i), nativeBuild.sections().get(i), palettedContainerFactory, i);
			if (mismatch != null) {
				return mismatch;
			}
		}
		for (Entry<Heightmap.Types, long[]> entry : javaData.heightmaps().entrySet()) {
			long[] nativeValues = nativeBuild.heightmaps().get(entry.getKey());
			if (!Arrays.equals(entry.getValue(), nativeValues)) {
				return "heightmap mismatch for " + entry.getKey().getSerializationKey();
			}
		}
		for (Heightmap.Types type : nativeBuild.heightmaps().keySet()) {
			if (!javaData.heightmaps().containsKey(type)) {
				return "extra Rust heightmap " + type.getSerializationKey();
			}
		}
		return null;
	}

	@Nullable
	private static String compareNativeTicks(ChunkAccess.PackedTicks javaTicks, ChunkAccess.PackedTicks nativeTicks) {
		if (!Objects.equals(javaTicks.blocks(), nativeTicks.blocks())) {
			return "block scheduled ticks mismatch java=" + javaTicks.blocks() + " rust=" + nativeTicks.blocks();
		}
		if (!Objects.equals(javaTicks.fluids(), nativeTicks.fluids())) {
			return "fluid scheduled ticks mismatch java=" + javaTicks.fluids() + " rust=" + nativeTicks.fluids();
		}
		return null;
	}

	static CompoundTag prepareWriteResidual(CompoundTag residual) {
		if (!ChunkSectionReadDiagnostics.writeValidationEnabled() || residual == null) {
			return residual;
		}
		boolean observed = residual.contains(CUSTOM_ROOT_FIELD);
		boolean injected = false;
		if (!observed) {
			residual.putString(CUSTOM_ROOT_FIELD, System.getProperty("mattmc.dev.runCaptureId", "unknown"));
			injected = true;
		}
		ChunkSectionReadDiagnostics.forcedValidationChunk(injected, observed);
		return residual;
	}

	static ChunkAccess.PackedTicks prepareTickValidationFixture(ChunkPos chunkPos, ChunkAccess.PackedTicks ticks) {
		if (!ChunkSectionReadDiagnostics.writeValidationEnabled()) {
			return ticks;
		}
		if (!ticks.blocks().isEmpty() && !ticks.fluids().isEmpty()) {
			return ticks;
		}
		BlockPos base = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
		java.util.List<SavedTick<net.minecraft.world.level.block.Block>> blocks = ticks.blocks().isEmpty()
			? java.util.List.of(new SavedTick<>(Blocks.STONE, base, 100000, TickPriority.HIGH))
			: ticks.blocks();
		java.util.List<SavedTick<net.minecraft.world.level.material.Fluid>> fluids = ticks.fluids().isEmpty()
			? java.util.List.of(new SavedTick<>(Fluids.WATER, base.above(), 100001, TickPriority.LOW))
			: ticks.fluids();
		return new ChunkAccess.PackedTicks(blocks, fluids);
	}

	@Nullable
	private static String compareSection(
		SerializableChunkData.SectionData javaSection,
		SerializableChunkData.SectionData nativeSection,
		PalettedContainerFactory palettedContainerFactory,
		int index
	) {
		if (javaSection.y() != nativeSection.y()) {
			return "section Y mismatch at index " + index + ": java=" + javaSection.y() + " rust=" + nativeSection.y();
		}
		if ((javaSection.chunkSection() == null) != (nativeSection.chunkSection() == null)) {
			return "section presence mismatch at Y " + javaSection.y();
		}
		if (javaSection.chunkSection() != null) {
			String blockMismatch = comparePackedData(
				"block states at Y " + javaSection.y(),
				javaSection.chunkSection().getStates().pack(palettedContainerFactory.blockStatesStrategy()),
				nativeSection.chunkSection().getStates().pack(palettedContainerFactory.blockStatesStrategy())
			);
			if (blockMismatch != null) {
				return blockMismatch;
			}
			String biomeMismatch = comparePackedData(
				"biomes at Y " + javaSection.y(),
				javaSection.chunkSection().getBiomes().pack(palettedContainerFactory.biomeStrategy()),
				nativeSection.chunkSection().getBiomes().pack(palettedContainerFactory.biomeStrategy())
			);
			if (biomeMismatch != null) {
				return biomeMismatch;
			}
		}
		if (!Arrays.equals(layerBytes(javaSection.blockLight()), layerBytes(nativeSection.blockLight()))) {
			return "BlockLight mismatch at Y " + javaSection.y();
		}
		if (!Arrays.equals(layerBytes(javaSection.skyLight()), layerBytes(nativeSection.skyLight()))) {
			return "SkyLight mismatch at Y " + javaSection.y();
		}
		return null;
	}

	@Nullable
	private static String comparePackedData(String label, PackedData<?> javaData, PackedData<?> nativeData) {
		if (!Objects.equals(javaData.paletteEntries(), nativeData.paletteEntries())) {
			return label + " palette mismatch";
		}
		if (!Arrays.equals(storageBytes(javaData), storageBytes(nativeData))) {
			return label + " packed data mismatch";
		}
		if (javaData.bitsPerEntry() != nativeData.bitsPerEntry()) {
			return label + " bits-per-entry mismatch java=" + javaData.bitsPerEntry() + " rust=" + nativeData.bitsPerEntry();
		}
		return null;
	}

	private static long[] storageBytes(PackedData<?> data) {
		return data.storage().map(LongStream::toArray).orElseGet(() -> new long[0]);
	}

	private static byte[] layerBytes(@Nullable DataLayer layer) {
		return layer == null ? new byte[0] : layer.getData();
	}
}
