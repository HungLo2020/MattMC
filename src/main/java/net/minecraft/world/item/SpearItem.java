package net.minecraft.world.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SpearItem extends Item {
	public static final int THRUST_THRESHOLD_TIME = 8;
	private static final int CONTACT_COOLDOWN_TICKS = 10;
	private static final int DAMAGE_WINDOW_TICKS = 40;
	private static final float MIN_DAMAGE_RELATIVE_SPEED = 1.0F;
	private static final float DAMAGE_MULTIPLIER = 1.0F;
	private static final float MIN_REACH = 2.0F;
	private static final float MAX_REACH = 4.5F;
	private static final float MAX_CREATIVE_REACH = 6.5F;
	private static final float HITBOX_MARGIN = 0.125F;
	private static final float MOB_REACH_FACTOR = 0.5F;
	private static final float LUNGE_BASE_IMPULSE = 0.458F;
	private static final float LUNGE_EXHAUSTION = 4.0F;
	private final float thrustDamage;
	private final boolean woodenSounds;
	private final Map<Integer, Map<Integer, Long>> recentContactHits = new HashMap<>();

	public SpearItem(Item.Properties properties, float thrustDamage, boolean woodenSounds) {
		super(properties);
		this.thrustDamage = thrustDamage;
		this.woodenSounds = woodenSounds;
	}

	public static Item.Properties createProperties(ToolMaterial toolMaterial, float attackDamage, float attackSpeed) {
		return toolMaterial.applySwordProperties(new Item.Properties(), attackDamage, attackSpeed)
			.component(DataComponents.WEAPON, new Weapon(1));
	}

	public static ItemAttributeModifiers createAttributes(ToolMaterial toolMaterial, float attackDamage, float attackSpeed) {
		return ItemAttributeModifiers.builder()
			.add(
				Attributes.ATTACK_DAMAGE,
				new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage + toolMaterial.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
	}

	public static Tool createToolProperties() {
		return new Tool(List.of(), 1.0F, 2, false);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
		return ItemUseAnimation.SPEAR;
	}

	@Override
	public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
		return 72000;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.nextDamageWillBreak()) {
			return InteractionResult.FAIL;
		} else {
			player.startUsingItem(interactionHand);
			return InteractionResult.CONSUME;
		}
	}

	@Override
	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int remainingUseTicks) {
		if (!(level instanceof ServerLevel serverLevel) || itemStack.nextDamageWillBreak()) {
			return;
		}

		int ticksUsed = this.getUseDuration(itemStack, livingEntity) - remainingUseTicks;
		if (ticksUsed > DAMAGE_WINDOW_TICKS) {
			return;
		}

		Vec3 look = livingEntity.getLookAngle();
		double attackerSpeed = look.dot(this.getMotion(livingEntity));
		List<SpearHit> hits = this.findPiercedTargets(serverLevel, livingEntity);
		boolean affected = false;
		for (SpearHit hit : hits) {
			Entity target = this.resolveTarget(hit.target());
			if (this.wasRecentlyHit(livingEntity, target, serverLevel.getGameTime())) {
				continue;
			}

			this.rememberHit(livingEntity, target, serverLevel.getGameTime());
			double targetSpeed = look.dot(this.getMotion(target));
			double relativeSpeed = Math.max(0.0, attackerSpeed - targetSpeed);
			if (relativeSpeed >= this.requiredRelativeSpeed(livingEntity)) {
				float damage = (float)livingEntity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + (float)Math.floor(relativeSpeed * DAMAGE_MULTIPLIER);
				affected |= this.stabAttack(serverLevel, itemStack, livingEntity, target, damage, true, true, true);
			}
		}

		if (affected) {
			level.broadcastEntityEvent(livingEntity, (byte)2);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int remainingUseTicks) {
		int i = this.getUseDuration(itemStack, livingEntity) - remainingUseTicks;
		if (i < THRUST_THRESHOLD_TIME || itemStack.nextDamageWillBreak()) {
			return false;
		}

		if (!(level instanceof ServerLevel serverLevel)) {
			return true;
		}

		boolean hitSomething = false;
		for (SpearHit hit : this.findPiercedTargets(serverLevel, livingEntity)) {
			hitSomething |= this.stabAttack(serverLevel, itemStack, livingEntity, this.resolveTarget(hit.target()), this.thrustDamage, true, true, false);
		}

		this.postPiercingAttack(serverLevel, itemStack, livingEntity);
		if (hitSomething) {
			serverLevel.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), this.hitSound(), livingEntity.getSoundSource(), 1.0F, 1.0F);
		}

		serverLevel.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), this.useSound(), livingEntity.getSoundSource(), 1.0F, 1.0F);
		livingEntity.swing(livingEntity.getUsedItemHand());
		return true;
	}

	private List<SpearHit> findPiercedTargets(ServerLevel serverLevel, LivingEntity attacker) {
		Vec3 start = attacker.getEyePosition();
		Vec3 look = attacker.getLookAngle().normalize();
		float maxReach = this.maxReach(attacker);
		Vec3 end = start.add(look.scale(maxReach));
		HitResult blockHit = serverLevel.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}

		Vec3 finalEnd = end;
		double minReach = this.minReach(attacker);
		AABB searchBox = attacker.getBoundingBox().expandTowards(look.scale(maxReach)).inflate(1.0);
		return serverLevel.getEntities(attacker, searchBox, target -> this.canHit(attacker, target))
			.stream()
			.flatMap(target -> target.getBoundingBox().inflate(target.getPickRadius() + HITBOX_MARGIN).clip(start, finalEnd).map(location -> new SpearHit(target, location)).stream())
			.filter(hit -> this.inRange(start, hit.location(), minReach, maxReach))
			.sorted((first, second) -> Double.compare(first.location().distanceToSqr(start), second.location().distanceToSqr(start)))
			.toList();
	}

	private boolean canHit(LivingEntity attacker, Entity target) {
		if (!target.isAlive() || target == attacker || !target.isAttackable() || attacker.isPassengerOfSameVehicle(target)) {
			return false;
		}
		if (target instanceof Interaction) {
			return true;
		}
		if (!target.canBeHitByProjectile()) {
			return false;
		}
		if (target instanceof ArmorStand armorStand && armorStand.isMarker()) {
			return false;
		}
		if (target instanceof Player targetPlayer && attacker instanceof Player player && !player.canHarmPlayer(targetPlayer)) {
			return false;
		}
		if (target instanceof LivingEntity livingTarget && livingTarget.isAlliedTo(attacker)) {
			return false;
		}
		return !(target instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame() && tamableAnimal.isOwnedBy(attacker));
	}

	private Entity resolveTarget(Entity target) {
		return target instanceof EnderDragonPart enderDragonPart ? enderDragonPart.parentMob : target;
	}

	private boolean stabAttack(ServerLevel serverLevel, ItemStack itemStack, LivingEntity attacker, Entity target, float damage, boolean dealsDamage, boolean dealsKnockback, boolean dismounts) {
		if (!target.isAttackable() || target.skipAttackInteraction(attacker)) {
			return false;
		}

		DamageSource damageSource = attacker.damageSources().spear(attacker);
		float modifiedDamage = EnchantmentHelper.modifyDamage(serverLevel, itemStack, target, damageSource, damage);
		boolean hurt = !dealsDamage || target.hurtServer(serverLevel, damageSource, modifiedDamage);
		if (!hurt) {
			return false;
		}

		if (dealsKnockback) {
			this.applyPierceKnockback(serverLevel, attacker, target, itemStack, damageSource);
		}
		if (dismounts) {
			target.stopRiding();
		}

		attacker.setLastHurtMob(target);
		if (target instanceof LivingEntity livingTarget) {
			boolean shouldDamageItem = itemStack.hurtEnemy(livingTarget, attacker);
			if (shouldDamageItem) {
				itemStack.postHurtEnemy(livingTarget, attacker);
			}
		}

		EnchantmentHelper.doPostAttackEffects(serverLevel, target, damageSource);
		return true;
	}

	private void applyPierceKnockback(ServerLevel serverLevel, LivingEntity attacker, Entity target, ItemStack itemStack, DamageSource damageSource) {
		float knockback = EnchantmentHelper.modifyKnockback(serverLevel, itemStack, target, damageSource, 1.0F);
		if (knockback <= 0.0F) {
			return;
		}

		if (target instanceof LivingEntity livingTarget) {
			livingTarget.knockback(knockback * 0.5F, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
		} else {
			Vec3 vec3 = target.position().subtract(attacker.position()).normalize();
			target.push(vec3.x * 0.5F * knockback, 0.1, vec3.z * 0.5F * knockback);
		}
		if (target instanceof ServerPlayer serverPlayer) {
			serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
		}
	}

	private void postPiercingAttack(ServerLevel serverLevel, ItemStack itemStack, LivingEntity livingEntity) {
		if (livingEntity instanceof Player player) {
			player.resetAttackStrengthTicker();
		}

		this.applyLunge(serverLevel, itemStack, livingEntity);
	}

	private void applyLunge(ServerLevel serverLevel, ItemStack itemStack, LivingEntity livingEntity) {
		Holder<Enchantment> holder = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUNGE);
		int level = EnchantmentHelper.getItemEnchantmentLevel(holder, itemStack);
		if (level <= 0 || livingEntity.isPassenger() || livingEntity.isFallFlying() || livingEntity.isInWaterOrRain()) {
			return;
		}
		if (livingEntity instanceof Player player && !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() < 7) {
			return;
		}

		Vec3 look = livingEntity.getLookAngle();
		Vec3 impulse = new Vec3(look.x, 0.0, look.z).normalize().scale(LUNGE_BASE_IMPULSE * level);
		if (impulse.lengthSqr() > 0.0) {
			livingEntity.push(impulse.x, 0.0, impulse.z);
			if (livingEntity instanceof ServerPlayer serverPlayer) {
				serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
				serverPlayer.causeFoodExhaustion(LUNGE_EXHAUSTION * level);
			}

			itemStack.hurtAndBreak(1, livingEntity, livingEntity.getUsedItemHand());
			serverLevel.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), this.lungeSound(level), SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	private Vec3 getMotion(Entity entity) {
		if (!(entity instanceof Player) && entity.isPassenger()) {
			entity = entity.getRootVehicle();
		}

		return entity.getKnownMovement().scale(20.0);
	}

	private boolean wasRecentlyHit(LivingEntity attacker, Entity target, long gameTime) {
		this.recentContactHits.values().forEach(targets -> targets.entrySet().removeIf(entry -> gameTime - entry.getValue() > CONTACT_COOLDOWN_TICKS));
		this.recentContactHits.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		Map<Integer, Long> attackerHits = this.recentContactHits.get(attacker.getId());
		if (attackerHits == null) {
			return false;
		}

		Long lastHitTime = attackerHits.get(target.getId());
		return lastHitTime != null && gameTime - lastHitTime <= CONTACT_COOLDOWN_TICKS;
	}

	private void rememberHit(LivingEntity attacker, Entity target, long gameTime) {
		this.recentContactHits.computeIfAbsent(attacker.getId(), key -> new HashMap<>()).put(target.getId(), gameTime);
	}

	private double requiredRelativeSpeed(LivingEntity livingEntity) {
		return MIN_DAMAGE_RELATIVE_SPEED * (livingEntity instanceof Player ? 1.0F : 0.2F);
	}

	private float minReach(LivingEntity attacker) {
		return attacker instanceof Player ? MIN_REACH : MIN_REACH * MOB_REACH_FACTOR;
	}

	private float maxReach(LivingEntity attacker) {
		if (attacker instanceof Player player && player.getAbilities().instabuild) {
			return MAX_CREATIVE_REACH;
		}

		return attacker instanceof Player ? MAX_REACH : MAX_REACH * MOB_REACH_FACTOR;
	}

	private boolean inRange(Vec3 start, Vec3 location, double minReach, double maxReach) {
		double distance = start.distanceTo(location);
		return distance >= minReach - HITBOX_MARGIN && distance <= maxReach + HITBOX_MARGIN;
	}

	private Holder<SoundEvent> lungeSound(int level) {
		return switch (Math.min(level, 3)) {
			case 1 -> SoundEvents.LUNGE_1;
			case 2 -> SoundEvents.LUNGE_2;
			default -> SoundEvents.LUNGE_3;
		};
	}

	private Holder<SoundEvent> useSound() {
		return this.woodenSounds ? SoundEvents.SPEAR_WOOD_USE : SoundEvents.SPEAR_USE;
	}

	private Holder<SoundEvent> hitSound() {
		return this.woodenSounds ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT;
	}

	private record SpearHit(Entity target, Vec3 location) {
	}
}
