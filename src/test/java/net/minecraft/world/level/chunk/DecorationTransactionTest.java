package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Overlapping FEATURES transactions with controlled interleaving: two steps whose
 * write regions share a chunk serialise on its ownership lock, either side may
 * fail or be cancelled, and each rollback restores only its own state.
 */
class DecorationTransactionTest {
	private static net.minecraft.server.packs.resources.CloseableResourceManager resources;
	private static RegistryAccess registries;

	@BeforeAll
	static void load() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
			List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
		var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
		var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
		var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
		registries = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
	}

	@AfterAll
	static void close() {
		resources.close();
	}

	private static ProtoChunk chunk(int x) {
		var chunk = new ProtoChunk(new ChunkPos(x, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-16, 32),
			PalettedContainerFactory.create(registries), null);
		chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS);
		return chunk;
	}

	private static final BlockPos AT = new BlockPos(1, 2, 3);

	/** A step's write into {@code chunk}, through first touch as WorldGenRegion hands it out. */
	private static void write(ProtoChunk chunk, BlockState state) {
		DecorationTransaction.touchCurrent(chunk);
		chunk.setBlockState(chunk.getPos().getWorldPosition().offset(AT), state, 0);
	}

	private static BlockState at(ProtoChunk chunk) {
		return chunk.getBlockState(chunk.getPos().getWorldPosition().offset(AT));
	}

	/** Runs a second step on another thread; it signals when it starts waiting and holds until {@code finish}. */
	private static CompletableFuture<Void> second(List<ChunkAccess> region, ChunkAccess center, CountDownLatch started,
												   AtomicReference<BlockState> seen, ProtoChunk shared, BlockState write, boolean fail) {
		return CompletableFuture.runAsync(() -> {
			started.countDown();
			var tx = DecorationTransaction.begin(region, center, registries);
			try {
				seen.set(at(shared));
				write(shared, write);
				if (fail) throw new IllegalStateException("second step fails");
			} catch (IllegalStateException failure) {
				tx.rollback();
				return;
			}
			tx.commit();
		});
	}

	@Test
	void overlappingStepsSerialiseAndAFailedFirstStepRestoresBeforeTheSecondSees() throws Exception {
		ProtoChunk a = chunk(0), shared = chunk(1), c = chunk(2);
		long waits = DecorationTransaction.WAITS.sum();
		var first = DecorationTransaction.begin(List.of(a, shared), a, registries);
		write(shared, Blocks.STONE.defaultBlockState());
		var started = new CountDownLatch(1);
		var seen = new AtomicReference<BlockState>();
		var other = second(List.of(shared, c), c, started, seen, shared, Blocks.DIRT.defaultBlockState(), false);
		assertTrue(started.await(10, TimeUnit.SECONDS));
		Thread.sleep(200);
		assertNull(seen.get(), "the second step must wait for the shared chunk's owner");
		first.rollback();
		other.get(10, TimeUnit.SECONDS);
		assertEquals(Blocks.AIR.defaultBlockState(), seen.get(), "the second step sees the rolled-back state");
		assertEquals(Blocks.DIRT.defaultBlockState(), at(shared), "only the second step's committed write remains");
		assertTrue(DecorationTransaction.WAITS.sum() > waits);
	}

	@Test
	void aFailedSecondStepKeepsTheFirstStepsCommittedWrites() throws Exception {
		ProtoChunk a = chunk(10), shared = chunk(11), c = chunk(12);
		var first = DecorationTransaction.begin(List.of(a, shared), a, registries);
		write(shared, Blocks.STONE.defaultBlockState());
		var started = new CountDownLatch(1);
		var seen = new AtomicReference<BlockState>();
		var other = second(List.of(shared, c), c, started, seen, shared, Blocks.DIRT.defaultBlockState(), true);
		assertTrue(started.await(10, TimeUnit.SECONDS));
		Thread.sleep(200);
		first.commit();
		other.get(10, TimeUnit.SECONDS);
		assertEquals(Blocks.STONE.defaultBlockState(), seen.get());
		assertEquals(Blocks.STONE.defaultBlockState(), at(shared), "the failed step restores the first step's committed state");
	}

	@Test
	void aCancelledWaiterReleasesNothingItDidNotTakeAndOthersProceed() throws Exception {
		ProtoChunk a = chunk(20), shared = chunk(21), c = chunk(22);
		var first = DecorationTransaction.begin(List.of(a, shared), a, registries);
		write(shared, Blocks.STONE.defaultBlockState());
		var failure = new AtomicReference<Throwable>();
		var waiter = new Thread(() -> {
			try {
				DecorationTransaction.begin(List.of(shared, c), c, registries).commit();
			} catch (Throwable thrown) {
				failure.set(thrown);
			}
		});
		waiter.start();
		Thread.sleep(200);
		waiter.interrupt();
		waiter.join(10_000);
		assertInstanceOf(java.util.concurrent.CancellationException.class, failure.get());
		// The waiter blocked on shared (ordered before c) holding nothing; c is free.
		var retry = CompletableFuture.supplyAsync(() -> {
			var tx = DecorationTransaction.begin(List.of(c), c, registries);
			tx.commit();
			return true;
		});
		assertTrue(retry.get(10, TimeUnit.SECONDS));
		first.commit();
		assertEquals(Blocks.STONE.defaultBlockState(), at(shared));
		// A retry of the cancelled step now runs.
		var again = DecorationTransaction.begin(List.of(shared, c), c, registries);
		write(shared, Blocks.DIRT.defaultBlockState());
		again.commit();
		assertEquals(Blocks.DIRT.defaultBlockState(), at(shared));
	}

	@Test
	void deferredEffectsRunInOrderOnlyOnCommit() {
		ProtoChunk a = chunk(30);
		List<String> ran = new ArrayList<>();
		var failed = DecorationTransaction.begin(List.of(a), a, registries);
		assertTrue(DecorationTransaction.defer(() -> ran.add("rolled back")));
		failed.rollback();
		assertTrue(ran.isEmpty());
		var committed = DecorationTransaction.begin(List.of(a), a, registries);
		DecorationTransaction.defer(() -> ran.add("first"));
		DecorationTransaction.defer(() -> ran.add("second"));
		committed.commit();
		assertEquals(List.of("first", "second"), ran);
		assertFalse(DecorationTransaction.defer(() -> ran.add("outside")), "outside a transaction effects run immediately");
	}

	/** A block-light engine over test chunks, standing in for the light thread. */
	private static net.minecraft.world.level.lighting.LevelLightEngine light(ProtoChunk... chunks) {
		var byPos = new java.util.HashMap<Long, ProtoChunk>();
		for (var chunk : chunks) byPos.put(chunk.getPos().toLong(), chunk);
		var engine = new net.minecraft.world.level.lighting.LevelLightEngine(new LightChunkGetter() {
			@Override
			public LightChunk getChunkForLighting(int x, int z) {
				return byPos.get(ChunkPos.asLong(x, z));
			}

			@Override
			public net.minecraft.world.level.BlockGetter getLevel() {
				return net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
			}
		}, true, false);
		for (var chunk : chunks) {
			chunk.setLightEngine(engine);
			engine.setLightEnabled(chunk.getPos(), true);
			var sections = chunk.getSectionsForRead();
			for (int i = 0; i < sections.length; i++) {
				if (!sections[i].hasOnlyAir()) engine.updateSectionStatus(net.minecraft.core.SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(i)), false);
			}
			engine.propagateLightSources(chunk.getPos());
		}
		engine.runLightUpdates();
		return engine;
	}

	private static ProtoChunk litChunk(int x) {
		var chunk = chunk(x);
		// A lit section that already holds blocks, so lighting keeps data for it.
		chunk.setBlockState(chunk.getPos().getWorldPosition().offset(AT).west(), Blocks.STONE.defaultBlockState(), 0);
		chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.INITIALIZE_LIGHT);
		return chunk;
	}

	private static int blockLightNear(net.minecraft.world.level.lighting.LevelLightEngine engine, ProtoChunk chunk) {
		return engine.getLayerListener(net.minecraft.world.level.LightLayer.BLOCK).getLightValue(chunk.getPos().getWorldPosition().offset(AT).east());
	}

	@Test
	void aRollbackAfterAStaleLightReadConvergesOnTheRestoredBlocks() {
		ProtoChunk center = chunk(50), lit = litChunk(51), reference = litChunk(53);
		var engine = light(lit, reference);
		var tx = DecorationTransaction.begin(List.of(center, lit), center, registries);
		int stale;
		try {
			write(lit, Blocks.GLOWSTONE.defaultBlockState());
			// A light task already queued elsewhere runs mid-step and reads the tentative glowstone.
			engine.checkBlock(lit.getPos().getWorldPosition().offset(AT));
			engine.runLightUpdates();
			stale = blockLightNear(engine, lit);
		} finally {
			tx.rollback();
		}
		assertEquals(14, stale, "the stale reader lit the tentative block");
		engine.runLightUpdates();
		assertEquals(blockLightNear(engine, reference), blockLightNear(engine, lit), "after quiescence, light matches a chunk that never held it");
		assertEquals(0, blockLightNear(engine, lit));
		// A successful retry lights exactly as an ordinary placement does.
		var retry = DecorationTransaction.begin(List.of(center, lit), center, registries);
		try {
			write(lit, Blocks.GLOWSTONE.defaultBlockState());
		} finally {
			retry.commit();
		}
		reference.setBlockState(reference.getPos().getWorldPosition().offset(AT), Blocks.GLOWSTONE.defaultBlockState(), 0);
		engine.runLightUpdates();
		assertEquals(blockLightNear(engine, reference), blockLightNear(engine, lit));
		assertEquals(14, blockLightNear(engine, lit));
	}

	@Test
	void transactionsNeverNestOnOneThread() {
		ProtoChunk a = chunk(40), b = chunk(41);
		var tx = DecorationTransaction.begin(List.of(a), a, registries);
		try {
			assertThrows(IllegalStateException.class, () -> DecorationTransaction.begin(List.of(b), b, registries));
		} finally {
			tx.commit();
		}
		// b was never locked: another step takes it immediately.
		DecorationTransaction.begin(List.of(b), b, registries).commit();
	}
}
