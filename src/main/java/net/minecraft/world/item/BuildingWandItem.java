package net.minecraft.world.item;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Extends a contiguous plane of matching blocks from the face clicked by the player.
 */
public class BuildingWandItem extends Item {
	static final int MAX_BLOCKS = 128;
	private static final int MAX_VISITED_SOURCE_BLOCKS = 4096;

	public BuildingWandItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		BlockPos clickedPos = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clickedPos);
		if (clickedState.isAir()) {
			return InteractionResult.PASS;
		}

		Block sourceBlock = clickedState.getBlock();
		Direction face = context.getClickedFace();
		if (context.isSecondaryUseActive()) {
			int broken = this.breakPlane(level, player, sourceBlock, clickedPos, face);
			return broken == 0 ? InteractionResult.PASS : InteractionResult.SUCCESS_SERVER;
		}

		if (!(sourceBlock.asItem() instanceof BlockItem blockItem)) {
			return InteractionResult.PASS;
		}

		int placed = this.placePlane(level, player, context, blockItem, sourceBlock, clickedPos, face);
		if (placed == 0) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.inventoryMenu.sendAllDataToRemote();
		}

		return InteractionResult.SUCCESS_SERVER;
	}

	private int breakPlane(Level level, Player player, Block sourceBlock, BlockPos clickedPos, Direction face) {
		if (!player.mayBuild()) {
			return 0;
		}

		return this.visitMatchingPlane(level, sourceBlock, clickedPos, face, (sourcePos, sourceState) -> isBreakFaceExposed(level, sourcePos, sourceState, face), sourcePos -> {
			if (!level.mayInteract(player, sourcePos)) {
				return false;
			}

			BlockState sourceState = level.getBlockState(sourcePos);
			if (!canBreakSourceBlock(level, player, sourcePos, sourceState)) {
				return false;
			}

			return level.destroyBlock(sourcePos, false, player);
		});
	}

	private static boolean canBreakSourceBlock(Level level, Player player, BlockPos sourcePos, BlockState sourceState) {
		Block block = sourceState.getBlock();
		if (sourceState.getDestroySpeed(level, sourcePos) < 0.0F) {
			return false;
		}

		return !(block instanceof GameMasterBlock) || player.canUseGameMasterBlocks();
	}

	private static boolean isBreakFaceExposed(Level level, BlockPos sourcePos, BlockState sourceState, Direction face) {
		BlockState adjacentState = level.getBlockState(sourcePos.relative(face));
		return Block.shouldRenderFace(sourceState, adjacentState, face);
	}

	private int placePlane(Level level, Player player, UseOnContext context, BlockItem blockItem, Block sourceBlock, BlockPos clickedPos, Direction face) {
		return this.visitMatchingPlane(
			level,
			sourceBlock,
			clickedPos,
			face,
			(sourcePos, sourceState) -> canPlaceFromSource(level, player, context, blockItem, sourcePos, face),
			sourcePos -> this.tryPlaceFromSource(level, player, context, blockItem, sourcePos, face)
		);
	}

	private int visitMatchingPlane(Level level, Block sourceBlock, BlockPos clickedPos, Direction face, PlaneSourceFilter sourceFilter, PlaneAction action) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		int[][] planeOffsets = planeOffsets(face);
		queue.add(clickedPos);
		visited.add(clickedPos);

		int changed = 0;
		while (!queue.isEmpty() && changed < MAX_BLOCKS && visited.size() <= MAX_VISITED_SOURCE_BLOCKS) {
			BlockPos sourcePos = queue.removeFirst();
			BlockState sourceState = level.getBlockState(sourcePos);
			if (!sourceState.is(sourceBlock)) {
				continue;
			}

			if (!sourceFilter.test(sourcePos, sourceState)) {
				continue;
			}

			for (int[] offset : planeOffsets) {
				BlockPos adjacent = sourcePos.offset(offset[0], offset[1], offset[2]);
				if (visited.add(adjacent)) {
					queue.addLast(adjacent);
				}
			}

			if (action.apply(sourcePos)) {
				changed++;
			}
		}

		return changed;
	}

	private boolean tryPlaceFromSource(Level level, Player player, UseOnContext context, BlockItem blockItem, BlockPos sourcePos, Direction face) {
		BlockPos targetPos = sourcePos.relative(face);
		if (!level.mayInteract(player, targetPos)) {
			return false;
		}

		ItemStack placementStack = this.findPlacementStack(player, blockItem);
		if (placementStack.isEmpty()) {
			return false;
		}

		BlockHitResult hitResult = new BlockHitResult(hitLocationForFace(sourcePos, face), face, sourcePos, false);
		UseOnContext placementContext = new UseOnContext(level, player, context.getHand(), placementStack, hitResult);
		if (!placementStack.useOn(placementContext).consumesAction()) {
			return false;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			playPlaceSoundForPlayer(serverPlayer, level, targetPos);
		}

		return true;
	}

	private static boolean canPlaceFromSource(Level level, Player player, UseOnContext context, BlockItem blockItem, BlockPos sourcePos, Direction face) {
		BlockPos targetPos = sourcePos.relative(face);
		if (!level.mayInteract(player, targetPos)) {
			return false;
		}

		BlockHitResult hitResult = new BlockHitResult(hitLocationForFace(sourcePos, face), face, sourcePos, false);
		UseOnContext placementContext = new UseOnContext(level, player, context.getHand(), new ItemStack(blockItem), hitResult);
		return new BlockPlaceContext(placementContext).canPlace();
	}

	private static void playPlaceSoundForPlayer(ServerPlayer player, Level level, BlockPos targetPos) {
		BlockState placedState = level.getBlockState(targetPos);
		SoundType soundType = placedState.getSoundType();
		player.connection
			.send(
				new ClientboundSoundPacket(
					BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundType.getPlaceSound()),
					SoundSource.BLOCKS,
					targetPos.getX() + 0.5,
					targetPos.getY() + 0.5,
					targetPos.getZ() + 0.5,
					(soundType.getVolume() + 1.0F) / 2.0F,
					soundType.getPitch() * 0.8F,
					level.random.nextLong()
				)
			);
	}

	private ItemStack findPlacementStack(Player player, BlockItem blockItem) {
		if (player.hasInfiniteMaterials()) {
			return new ItemStack(blockItem);
		}

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isUsableBlockStack(stack, blockItem)) {
				return stack;
			}
		}

		ItemStack offhandStack = inventory.getItem(Inventory.SLOT_OFFHAND);
		return isUsableBlockStack(offhandStack, blockItem) ? offhandStack : ItemStack.EMPTY;
	}

	private static boolean isUsableBlockStack(ItemStack stack, BlockItem blockItem) {
		return !stack.isEmpty() && stack.is(blockItem);
	}

	static int[][] planeOffsets(Direction face) {
		Direction.Axis axis = face.getAxis();
		return switch (axis) {
			case X -> new int[][]{{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, -1}, {0, 1, 1}, {0, -1, -1}, {0, -1, 1}};
			case Y -> new int[][]{{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1}, {1, 0, 1}};
			case Z -> new int[][]{{0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {1, 0, 0}, {-1, 1, 0}, {1, 1, 0}, {-1, -1, 0}, {1, -1, 0}};
		};
	}

	private static Vec3 hitLocationForFace(BlockPos sourcePos, Direction face) {
		return new Vec3(
			sourcePos.getX() + 0.5 + face.getStepX() * 0.5,
			sourcePos.getY() + 0.5 + face.getStepY() * 0.5,
			sourcePos.getZ() + 0.5 + face.getStepZ() * 0.5
		);
	}

	@FunctionalInterface
	private interface PlaneAction {
		boolean apply(BlockPos sourcePos);
	}

	@FunctionalInterface
	private interface PlaneSourceFilter {
		boolean test(BlockPos sourcePos, BlockState sourceState);
	}
}
