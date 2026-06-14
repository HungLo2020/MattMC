package net.minecraft.world.item;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
		Block sourceBlock = clickedState.getBlock();
		if (!(sourceBlock.asItem() instanceof BlockItem blockItem)) {
			return InteractionResult.PASS;
		}

		Direction face = context.getClickedFace();
		int placed = this.placePlane(level, player, context, blockItem, sourceBlock, clickedPos, face);
		if (placed == 0) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.inventoryMenu.sendAllDataToRemote();
		}

		return InteractionResult.SUCCESS_SERVER;
	}

	private int placePlane(Level level, Player player, UseOnContext context, BlockItem blockItem, Block sourceBlock, BlockPos clickedPos, Direction face) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		int[][] planeOffsets = planeOffsets(face);
		queue.add(clickedPos);
		visited.add(clickedPos);

		int placed = 0;
		while (!queue.isEmpty() && placed < MAX_BLOCKS && visited.size() <= MAX_VISITED_SOURCE_BLOCKS) {
			BlockPos sourcePos = queue.removeFirst();
			BlockState sourceState = level.getBlockState(sourcePos);
			if (!sourceState.is(sourceBlock)) {
				continue;
			}

			for (int[] offset : planeOffsets) {
				BlockPos adjacent = sourcePos.offset(offset[0], offset[1], offset[2]);
				if (visited.add(adjacent)) {
					queue.addLast(adjacent);
				}
			}

			if (this.tryPlaceFromSource(level, player, context, blockItem, sourcePos, face)) {
				placed++;
			}
		}

		return placed;
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
		return placementStack.useOn(placementContext).consumesAction();
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
}
