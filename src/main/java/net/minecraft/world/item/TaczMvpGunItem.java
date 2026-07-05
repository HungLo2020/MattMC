package net.minecraft.world.item;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
	private static final String FIRE_MODE_KEY = "TaczFireMode";
	private final TaczGunDefinitions.Gun definition;

	public TaczMvpGunItem(Item.Properties properties) {
		this("glock_17", properties);
	}

	public TaczMvpGunItem(String gunId, Item.Properties properties) {
		super(properties);
		this.definition = TaczGunDefinitions.GUN_BY_ID.getOrDefault(gunId, TaczGunDefinitions.GUN_BY_ID.get("glock_17"));
	}

	public TaczGunDefinitions.Gun definition() {
		return this.definition;
	}

	public String gunId() {
		return this.definition.id();
	}

	public java.util.List<TaczFireMode> supportedFireModes() {
		return TaczGunFireModes.modes(this.definition.id());
	}

	@Override
	public Set<TaczAttachmentType> supportedAttachmentTypes(ItemStack gunStack) {
		return this.definition.attachmentTypes();
	}

	@Override
	public boolean allowAttachment(ItemStack gunStack, ItemStack attachmentStack) {
		if (!(attachmentStack.getItem() instanceof TaczAttachmentItem attachment) || !this.allowAttachmentType(gunStack, attachment.getAttachmentType())) {
			return false;
		}

		String attachmentId = attachment.getAttachmentId();
		return attachmentId.isEmpty() || this.definition.attachmentIds().contains(attachmentId);
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
		if (ammo <= 0 || this.definition.magazineSize() <= 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), this.sound("empty"), SoundSource.PLAYERS, 0.7F, 1.0F);
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("item.minecraft.gun.empty").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.CONSUME;
		}

		if (level instanceof ServerLevel serverLevel) {
			this.scheduleTriggerPull(serverLevel, player, itemStack, ammo);
		}

		player.getCooldowns().addCooldown(itemStack, this.triggerCooldownTicks(itemStack));
		return InteractionResult.CONSUME;
	}

	private int triggerCooldownTicks(ItemStack itemStack) {
		if (getFireMode(itemStack) == TaczFireMode.BURST) {
			return TaczGunBurstData.burst(this.definition.id()).minIntervalTicks();
		}
		return Math.max(1, Math.round(this.shootIntervalMillis(itemStack) / 50.0F));
	}

	public int roundsPerTrigger(ItemStack itemStack, int ammo) {
		return getFireMode(itemStack) == TaczFireMode.BURST ? Math.min(TaczGunBurstData.burst(this.definition.id()).count(), ammo) : 1;
	}

	public long shootIntervalMillis(ItemStack itemStack) {
		int rpm = TaczGunFireModeAdjustments.rpm(this.definition.id(), getFireMode(itemStack), this.definition.rpm());
		return 60000L / Math.max(1, rpm);
	}

	public long triggerCooldownMillis(ItemStack itemStack) {
		return getFireMode(itemStack) == TaczFireMode.BURST ? TaczGunBurstData.burst(this.definition.id()).minIntervalMillis() : this.shootIntervalMillis(itemStack);
	}

	public long burstIntervalMillis(ItemStack itemStack) {
		return getFireMode(itemStack) == TaczFireMode.BURST ? TaczGunBurstData.burst(this.definition.id()).intervalMillis() : 1L;
	}

	private void scheduleTriggerPull(ServerLevel serverLevel, Player player, ItemStack itemStack, int ammo) {
		int rounds = this.roundsPerTrigger(itemStack, ammo);
		if (rounds <= 1) {
			this.fireRound(serverLevel, player, itemStack);
			return;
		}

		MinecraftServer server = serverLevel.getServer();
		int intervalTicks = TaczGunBurstData.burst(this.definition.id()).intervalTicks();
		int startTick = server.getTickCount();
		for (int round = 0; round < rounds; round++) {
			int scheduledRound = round;
			server.schedule(new TickTask(startTick + scheduledRound * intervalTicks, () -> {
				if (!player.isAlive() || player.getMainHandItem() != itemStack || getAmmo(itemStack) <= 0) {
					return;
				}
				this.fireRound(serverLevel, player, itemStack);
			}));
		}
	}

	private void fireRound(ServerLevel serverLevel, Player player, ItemStack itemStack) {
		if (getAmmo(itemStack) <= 0) {
			return;
		}

		int bulletCount = Math.max(1, this.definition.bulletCount());
		float damage = this.definition.damage() / bulletCount;
		float inaccuracy = Math.max(0.0F, this.definition.inaccuracy());
		for (int shot = 0; shot < bulletCount; shot++) {
			TaczBullet bullet = new TaczBullet(serverLevel, player, itemStack, damage, Math.max(1, this.definition.pierce()));
			bullet.setBulletProperties(
				this.definition.gravity(),
				this.definition.friction(),
				this.definition.lifeTicks(),
				this.definition.headshotMultiplier(),
				this.definition.knockback()
			);
			bullet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, this.definition.bulletSpeed(), inaccuracy);
			serverLevel.addFreshEntity(bullet);
		}

		setAmmo(itemStack, getAmmo(itemStack) - 1);
		player.awardStat(Stats.ITEM_USED.get(this));
		serverLevel.playSound(player, player.getX(), player.getY(), player.getZ(), this.sound("shoot"), SoundSource.PLAYERS, 1.25F, 0.96F + serverLevel.random.nextFloat() * 0.08F);
	}

	public InteractionResult tryStartReload(Level level, Player player, InteractionHand interactionHand, ItemStack itemStack) {
		if (getAmmo(itemStack) >= getMagazineSize(itemStack) || this.definition.magazineSize() <= 0) {
			return InteractionResult.FAIL;
		}

		if (!player.hasInfiniteMaterials() && this.findAmmo(player).isEmpty()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), this.sound("empty"), SoundSource.PLAYERS, 0.7F, 0.85F);
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("item.minecraft.gun.no_ammo").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.CONSUME;
		}

		player.startUsingItem(interactionHand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), this.sound("reload_start"), SoundSource.PLAYERS, 0.9F, 1.0F);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
		if (livingEntity instanceof Player player) {
			if (!level.isClientSide()) {
				this.reload(itemStack, player);
			}

			level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), this.sound("reload_end"), SoundSource.PLAYERS, 0.9F, 1.0F);
		}

		return itemStack;
	}

	@Override
	public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
		return this.definition.reloadTicks();
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
		int magazineSize = Math.max(1, getMagazineSize(itemStack));
		return Math.round(13.0F * getAmmo(itemStack) / magazineSize);
	}

	@Override
	public int getBarColor(ItemStack itemStack) {
		return 0xE0D35B;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag) {
		consumer.accept(Component.translatable("item.minecraft.gun.ammo", getAmmo(itemStack), getMagazineSize(itemStack)).withStyle(ChatFormatting.GRAY));
		consumer.accept(Component.translatable("item.minecraft.gun.reload_hint").withStyle(ChatFormatting.DARK_GRAY));
		for (TaczAttachmentType type : this.supportedAttachmentTypes(itemStack)) {
			ItemStack attachment = this.getAttachment(itemStack, type);
			if (!attachment.isEmpty()) {
				consumer.accept(Component.translatable("tooltip.tacz.refit.installed", attachment.getHoverName()).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	@Override
	public ItemStack getDefaultInstance() {
		ItemStack itemStack = super.getDefaultInstance();
		setAmmo(itemStack, getMagazineSize(itemStack));
		return itemStack;
	}

	private void reload(ItemStack gunStack, Player player) {
		int needed = getMagazineSize(gunStack) - getAmmo(gunStack);
		if (needed <= 0) {
			return;
		}

		int loaded = player.hasInfiniteMaterials() ? needed : this.consumeAmmo(player, needed);
		if (loaded > 0) {
			setAmmo(gunStack, getAmmo(gunStack) + loaded);
		}
	}

	private int consumeAmmo(Player player, int maxCount) {
		ItemStack ammoStack = this.findAmmo(player);
		if (ammoStack.isEmpty()) {
			return 0;
		}

		int consumed = Math.min(maxCount, ammoStack.getCount());
		ammoStack.shrink(consumed);
		return consumed;
	}

	private ItemStack findAmmo(Player player) {
		Item ammoItem = Items.TACZ_AMMO_BY_ID.get(this.definition.ammoId());
		if (ammoItem == null) {
			return ItemStack.EMPTY;
		}

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack itemStack = player.getInventory().getItem(i);
			if (itemStack.is(ammoItem)) {
				return itemStack;
			}
		}

		return ItemStack.EMPTY;
	}

	public static boolean canStartReload(Player player, ItemStack itemStack) {
		if (!(itemStack.getItem() instanceof TaczMvpGunItem gunItem) || getAmmo(itemStack) >= getMagazineSize(itemStack)) {
			return false;
		}

		return player.hasInfiniteMaterials() || !gunItem.findAmmo(player).isEmpty();
	}

	public static int getAmmo(ItemStack itemStack) {
		CompoundTag tag = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (tag.contains(AMMO_KEY)) {
			return tag.getIntOr(AMMO_KEY, 0);
		}

		return itemStack.getItem() instanceof TaczMvpGunItem gunItem ? gunItem.definition.magazineSize() : 0;
	}

	private static void setAmmo(ItemStack itemStack, int ammo) {
		CustomData.update(DataComponents.CUSTOM_DATA, itemStack, tag -> tag.putInt(AMMO_KEY, Math.max(0, Math.min(getMagazineSize(itemStack), ammo))));
	}

	public static int getMagazineSize(ItemStack itemStack) {
		if (!(itemStack.getItem() instanceof TaczMvpGunItem gunItem)) {
			return 0;
		}

		ItemStack extendedMag = TaczRefitGun.getStoredAttachment(itemStack, TaczAttachmentType.EXTENDED_MAG);
		if (extendedMag.getItem() instanceof TaczAttachmentItem attachment) {
			return gunItem.definition.extendedMagazineSize(attachment.getAttachmentLevel());
		}
		return gunItem.definition.magazineSize();
	}

	public static TaczFireMode getFireMode(ItemStack itemStack) {
		if (!(itemStack.getItem() instanceof TaczMvpGunItem gunItem)) {
			return TaczFireMode.SEMI;
		}

		CompoundTag tag = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		TaczFireMode mode = tag.getString(FIRE_MODE_KEY).map(TaczFireMode::byName).orElse(gunItem.supportedFireModes().get(0));
		return gunItem.supportedFireModes().contains(mode) ? mode : gunItem.supportedFireModes().get(0);
	}

	public TaczFireMode cycleFireMode(ItemStack itemStack) {
		java.util.List<TaczFireMode> modes = this.supportedFireModes();
		TaczFireMode current = getFireMode(itemStack);
		TaczFireMode next = modes.get((modes.indexOf(current) + 1) % modes.size());
		CustomData.update(DataComponents.CUSTOM_DATA, itemStack, tag -> tag.putString(FIRE_MODE_KEY, next.getSerializedName()));
		return next;
	}

	public int countReserveAmmo(Player player) {
		if (player.hasInfiniteMaterials()) {
			return Integer.MAX_VALUE;
		}

		Item ammoItem = Items.TACZ_AMMO_BY_ID.get(this.definition.ammoId());
		if (ammoItem == null) {
			return 0;
		}

		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack itemStack = player.getInventory().getItem(slot);
			if (itemStack.is(ammoItem)) {
				count += itemStack.getCount();
			}
		}
		return count;
	}

	private SoundEvent sound(String action) {
		return SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace(this.definition.id() + "." + action));
	}

	public SoundEvent shootSound() {
		return this.sound("shoot");
	}
}
