package net.matt.quantize.modules.tweaks;

import net.matt.quantize.Quantize;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class RightClickHarvestHandler {

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Level level = event.getLevel();
		if (level.isClientSide) return; // server only

		Player player = event.getEntity();
		if (event.getHand() != InteractionHand.MAIN_HAND) return;

		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();

		// Vanilla crops (wheat, carrots, potatoes, beetroot, etc.)
		if (block instanceof CropBlock crop) {
			if (!crop.isMaxAge(state)) return;

			harvestAndReplantCrop((ServerLevel) level, pos, state, player, crop);
			// reset to age 0
			level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
			consume(event);
			return;
		}

		// Nether wart (grows to age 3)
		if (block instanceof NetherWartBlock) {
			int age = state.getValue(NetherWartBlock.AGE);
			if (age < 3) return;

			harvestAndRemoveOneSpecific((ServerLevel) level, pos, state, player, Items.NETHER_WART);
			level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), Block.UPDATE_ALL);
			consume(event);
		}
	}

	/**
	 * Harvest a {@link CropBlock} and remove exactly one matching seed item from the drops
	 * (without calling the protected getBaseSeedId()).
	 */
	private static void harvestAndReplantCrop(ServerLevel level, BlockPos pos, BlockState state, Player player, CropBlock crop) {
		LootParams.Builder builder = new LootParams.Builder(level)
				.withParameter(LootContextParams.BLOCK_STATE, state)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.TOOL, player.getMainHandItem())
				.withParameter(LootContextParams.THIS_ENTITY, player);

		List<ItemStack> drops = state.getDrops(builder);

		// Remove one seed that would replant THIS crop (find a BlockItem that places the same CropBlock class)
		removeOneSeedForCrop(drops, crop);

		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) Block.popResource(level, pos, drop);
		}

		// feedback particles/sound (bonemeal particles)
		level.levelEvent(2005, pos, 0);
	}

	/**
	 * Harvest a block and remove one specific item from drops (e.g., Nether Wart).
	 */
	private static void harvestAndRemoveOneSpecific(ServerLevel level, BlockPos pos, BlockState state, Player player, net.minecraft.world.item.Item itemToRemoveOne) {
		LootParams.Builder builder = new LootParams.Builder(level)
				.withParameter(LootContextParams.BLOCK_STATE, state)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.TOOL, player.getMainHandItem())
				.withParameter(LootContextParams.THIS_ENTITY, player);

		List<ItemStack> drops = state.getDrops(builder);

		removeOneOfItem(drops, itemToRemoveOne);

		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) Block.popResource(level, pos, drop);
		}

		level.levelEvent(2005, pos, 0);
	}

	/**
	 * For seeds like Wheat/Carrot/Potato/Beetroot:
	 * find one drop whose item is a BlockItem that places a CropBlock of the same class as 'crop' and remove 1.
	 */
	private static void removeOneSeedForCrop(List<ItemStack> drops, CropBlock crop) {
		for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
			ItemStack st = it.next();
			if (st.isEmpty()) continue;
			if (st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock planted) {
				// Compare by class to handle subclasses cleanly (wheat vs carrot, etc.)
				if (planted.getClass() == crop.getClass()) {
					st.shrink(1);
					if (st.getCount() <= 0) it.remove();
					return;
				}
			}
		}
	}

	private static void removeOneOfItem(List<ItemStack> drops, net.minecraft.world.item.Item item) {
		for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
			ItemStack st = it.next();
			if (st.getItem() == item) {
				st.shrink(1);
				if (st.getCount() <= 0) it.remove();
				return;
			}
		}
	}

	private static void consume(PlayerInteractEvent.RightClickBlock event) {
		event.setUseBlock(Event.Result.DENY);
		event.setUseItem(Event.Result.DENY);
		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}
}
