package net.matt.quantize.block.block;

import net.matt.quantize.block.QBlocks;
import net.matt.quantize.procedures.FireflybushCanBoneMealBeUsedOnThisBlockProcedure;
import net.matt.quantize.particle.tick.FireflybushOnTickUpdateProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.matt.quantize.item.QItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Almost 1-to-1 with the original MCreator block, but tightened up so that
 * *all* logic now happens from the scheduled-tick path that the new
 * {@link FireflybushOnTickUpdateProcedure} enqueues.
 */
public class FireflybushBlock extends FlowerBlock implements BonemealableBlock {

   /* ────── construction ────── */

   public FireflybushBlock() {
      super(MobEffects.NIGHT_VISION, 100,
              Properties.of()
                      .mapColor(MapColor.PLANT)
                      .noCollission()
                      .sound(SoundType.GRASS)
                      .instabreak()
                      .randomTicks()                 // important!
                      .dynamicShape()
                      .offsetType(OffsetType.XZ)
                      .pushReaction(PushReaction.DESTROY)
                      .lightLevel(s -> 5));
   }

   /* ────── random‑tick logic ────── */

   /** <span style="color:#999">Vanilla calls this a few times per minute for each bush.</span> */
   @Override
   public void randomTick(BlockState state,           // <‑‑ fires once in 70 s
                          ServerLevel level,
                          BlockPos pos,
                          RandomSource rng) {
      level.scheduleTick(pos, this, 2);             // arm 1‑second loop
   }

   /** Belt‑and‑braces: marks the block as eligible for random ticks. */
   @Override
   public boolean isRandomlyTicking(BlockState state) {
      return true;
   }

   @Override
   public void tick(BlockState state,                 // <‑‑ fires every second
                    ServerLevel level,
                    BlockPos pos,
                    RandomSource rng) {

      FireflybushOnTickUpdateProcedure.execute(
              level, pos.getX(), pos.getY(), pos.getZ());

      level.scheduleTick(pos, this, 2);             // re‑arm
   }

   @Override
   public void onPlace(BlockState state, Level level, BlockPos pos,
                       BlockState old, boolean moving) {
      super.onPlace(state, level, pos, old, moving);
      if (!level.isClientSide) level.scheduleTick(pos, this, 20);
   }

   @Override
   public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                Player player, InteractionHand hand, BlockHitResult hit) {
      ItemStack held = player.getItemInHand(hand);

      if (held.is(Items.GLASS_BOTTLE)) {
         if (!level.isClientSide) {
            // Give the jar, consume/replace the bottle (handles creative & stacking)
            ItemStack result = new ItemStack(QBlocks.FIREFLY_JAR.get());
            ItemStack replaced = ItemUtils.createFilledResult(held, player, result, false);
            player.setItemInHand(hand, replaced);

            // Optional: update the bush (e.g., mark harvested / remove)
            // level.setBlock(pos, state, 3);

            // Feedback
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
         }
         return InteractionResult.sidedSuccess(level.isClientSide);
      }

      return super.use(state, level, pos, player, hand, hit);
   }

   /* ---------- bonemeal hooks (unchanged) ---------- */

   @Override public boolean isValidBonemealTarget(LevelReader r, BlockPos p, BlockState s, boolean c) { return true; }
   @Override public boolean isBonemealSuccess(Level l, RandomSource r, BlockPos p, BlockState s) { return true; }
   @Override public void performBonemeal(ServerLevel w, RandomSource r, BlockPos p, BlockState s) {
      FireflybushCanBoneMealBeUsedOnThisBlockProcedure.execute(w, p.getX(), p.getY(), p.getZ());
   }

   /* ---------- flammability hooks (unchanged) ---------- */

   @Override public int getEffectDuration()                                        { return 100; }
   @Override public int getFlammability(BlockState s, BlockGetter w, BlockPos p, Direction f){ return 100; }
   @Override public int getFireSpreadSpeed(BlockState s, BlockGetter w, BlockPos p, Direction f){ return 60; }
}
