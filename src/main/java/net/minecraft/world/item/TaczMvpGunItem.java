package net.minecraft.world.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.TaczBullet;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class TaczMvpGunItem extends Item implements TaczRefitGun {
	private static final String AMMO_KEY = "TaczMvpAmmo";
	private static final int MAGAZINE_SIZE = 17;
	private static final int RELOAD_TICKS = 24;
	private static final float BULLET_SPEED = 12.0F;
	private static final float BULLET_INACCURACY = 0.55F;
	private static final float BULLET_DAMAGE = 7.0F;
	private static final int BULLET_LIFE_TICKS = 40;
	private static final float BULLET_GRAVITY = 0.005F;
	private static final float BULLET_FRICTION = 0.01F;
	private static final float BULLET_HEADSHOT_MULTIPLIER = 1.5F;
	private static final float BULLET_KNOCKBACK = 0.12F;

	public TaczMvpGunItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public java.util.Set<TaczAttachmentType> supportedAttachmentTypes(ItemStack gunStack) {
		return TaczRefitGun.only(TaczAttachmentType.EXTENDED_MAG);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (player.isShiftKeyDown()) {
			return this.tryStartReload(level, player, interactionHand, itemStack);
		}

		return this.tryFire(level, player, interactionHand, itemStack);
	}

	public InteractionResult tryFire(Level level, Player player, InteractionHand interactionHand, ItemStack itemStack) {
		int ammo = getAmmo(itemStack);
		if (ammo <= 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TACZ_GLOCK_17_EMPTY, SoundSource.PLAYERS, 0.7F, 1.0F);
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("item.minecraft.glock_17.empty").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.CONSUME;
		}

		if (level instanceof ServerLevel serverLevel) {
			TaczBullet bullet = new TaczBullet(serverLevel, player, itemStack, BULLET_DAMAGE, 1);
			bullet.setBulletProperties(BULLET_GRAVITY, BULLET_FRICTION, BULLET_LIFE_TICKS, BULLET_HEADSHOT_MULTIPLIER, BULLET_KNOCKBACK);
			bullet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, BULLET_SPEED, BULLET_INACCURACY);
			serverLevel.addFreshEntity(bullet);
			setAmmo(itemStack, ammo - 1);
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TACZ_GLOCK_17_SHOOT, SoundSource.PLAYERS, 1.25F, 0.96F + level.random.nextFloat() * 0.08F);
		player.getCooldowns().addCooldown(itemStack, 4);
		return InteractionResult.CONSUME;
	}

	public InteractionResult tryStartReload(Level level, Player player, InteractionHand interactionHand, ItemStack itemStack) {
		if (getAmmo(itemStack) >= getMagazineSize(itemStack)) {
			return InteractionResult.FAIL;
		}

		if (!player.hasInfiniteMaterials() && findAmmo(player).isEmpty()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TACZ_GLOCK_17_EMPTY, SoundSource.PLAYERS, 0.7F, 0.85F);
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("item.minecraft.glock_17.no_ammo").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.CONSUME;
		}

		player.startUsingItem(interactionHand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TACZ_GLOCK_17_RELOAD_START, SoundSource.PLAYERS, 0.9F, 1.0F);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
		if (livingEntity instanceof Player player) {
			if (!level.isClientSide()) {
				reload(itemStack, player);
			}

			level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), SoundEvents.TACZ_GLOCK_17_RELOAD_END, SoundSource.PLAYERS, 0.9F, 1.0F);
		}

		return itemStack;
	}

	@Override
	public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
		return RELOAD_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
		return ItemUseAnimation.CROSSBOW;
	}

	@Override
	public boolean isBarVisible(ItemStack itemStack) {
		return getAmmo(itemStack) < getMagazineSize(itemStack);
	}

	@Override
	public int getBarWidth(ItemStack itemStack) {
		return Math.round(13.0F * getAmmo(itemStack) / getMagazineSize(itemStack));
	}

	@Override
	public int getBarColor(ItemStack itemStack) {
		return 0xE0D35B;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag) {
		consumer.accept(Component.translatable("item.minecraft.glock_17.ammo", getAmmo(itemStack), getMagazineSize(itemStack)).withStyle(ChatFormatting.GRAY));
		consumer.accept(Component.translatable("item.minecraft.glock_17.reload_hint").withStyle(ChatFormatting.DARK_GRAY));
		ItemStack extendedMag = this.getAttachment(itemStack, TaczAttachmentType.EXTENDED_MAG);
		if (!extendedMag.isEmpty()) {
			consumer.accept(Component.translatable("tooltip.tacz.refit.installed", extendedMag.getHoverName()).withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public ItemStack getDefaultInstance() {
		ItemStack itemStack = super.getDefaultInstance();
		setAmmo(itemStack, getMagazineSize(itemStack));
		return itemStack;
	}

	private static void reload(ItemStack gunStack, Player player) {
		int needed = getMagazineSize(gunStack) - getAmmo(gunStack);
		if (needed <= 0) {
			return;
		}

		int loaded = player.hasInfiniteMaterials() ? needed : consumeAmmo(player, needed);
		if (loaded > 0) {
			setAmmo(gunStack, getAmmo(gunStack) + loaded);
		}
	}

	private static int consumeAmmo(Player player, int maxCount) {
		ItemStack ammoStack = findAmmo(player);
		if (ammoStack.isEmpty()) {
			return 0;
		}

		int consumed = Math.min(maxCount, ammoStack.getCount());
		ammoStack.shrink(consumed);
		return consumed;
	}

	private static ItemStack findAmmo(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack itemStack = player.getInventory().getItem(i);
			if (itemStack.is(Items.TACZ_NINE_MM_AMMO)) {
				return itemStack;
			}
		}

		return ItemStack.EMPTY;
	}

	public static boolean canStartReload(Player player, ItemStack itemStack) {
		if (getAmmo(itemStack) >= getMagazineSize(itemStack)) {
			return false;
		}

		return player.hasInfiniteMaterials() || !findAmmo(player).isEmpty();
	}

	public static int getAmmo(ItemStack itemStack) {
		CompoundTag tag = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(AMMO_KEY) ? tag.getIntOr(AMMO_KEY, 0) : MAGAZINE_SIZE;
	}

	private static void setAmmo(ItemStack itemStack, int ammo) {
		CustomData.update(DataComponents.CUSTOM_DATA, itemStack, tag -> tag.putInt(AMMO_KEY, Math.max(0, Math.min(getMagazineSize(itemStack), ammo))));
	}

	public static int getMagazineSize(ItemStack itemStack) {
		ItemStack extendedMag = TaczRefitGun.getStoredAttachment(itemStack, TaczAttachmentType.EXTENDED_MAG);
		if (extendedMag.getItem() instanceof TaczAttachmentItem attachment) {
			return switch (attachment.getAttachmentLevel()) {
				case 1 -> 21;
				case 2 -> 24;
				case 3 -> 33;
				default -> MAGAZINE_SIZE;
			};
		}
		return MAGAZINE_SIZE;
	}
}
