package net.minecraft.world.entity.projectile;

import java.util.List;
import net.minecraft.core.particles.TaczBulletHoleParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.TaczKillHudS2CPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczGunBallistics;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TaczBullet extends Projectile {
	private static final EntityDataAccessor<String> DATA_GUN_ID = SynchedEntityData.defineId(TaczBullet.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_AMMO_ID = SynchedEntityData.defineId(TaczBullet.class, EntityDataSerializers.STRING);
	private static final int DEFAULT_LIFE = 40;
	private static final float DEFAULT_GRAVITY = 0.005F;
	private static final float DEFAULT_FRICTION = 0.01F;
	private static final int DEFAULT_PIERCE = 1;
	private static final float DEFAULT_HEADSHOT_MULTIPLIER = 1.5F;
	private static final float DEFAULT_KNOCKBACK = 0.12F;
	private int life = DEFAULT_LIFE;
	private float gravity = DEFAULT_GRAVITY;
	private float friction = DEFAULT_FRICTION;
	private float damage;
	private int pierce = DEFAULT_PIERCE;
	private float headshotMultiplier = DEFAULT_HEADSHOT_MULTIPLIER;
	private float knockback = DEFAULT_KNOCKBACK;
	private Vec3 startPos = Vec3.ZERO;
	private ItemStack weapon = ItemStack.EMPTY;
	private List<TaczGunBallistics.DamagePoint> damageCurve = List.of();

	public TaczBullet(EntityType<? extends TaczBullet> entityType, Level level) {
		super(entityType, level);
	}

	public TaczBullet(Level level, LivingEntity shooter, ItemStack weapon, float damage, int pierce) {
		this(EntityType.TACZ_BULLET, level);
		this.setOwner(shooter);
		this.weapon = weapon.copy();
		this.damage = Math.max(0.0F, damage);
		this.pierce = Math.max(1, pierce);
		this.setPos(shooter.getX(), shooter.getEyeY() - 0.1F, shooter.getZ());
		this.startPos = this.position();
	}

	public void setBulletProperties(float gravity, float friction, int life, float headshotMultiplier, float knockback) {
		this.gravity = Mth.clamp(gravity, 0.0F, 1.0F);
		this.friction = Mth.clamp(friction, 0.0F, 1.0F);
		this.life = Math.max(1, life);
		this.headshotMultiplier = Math.max(1.0F, headshotMultiplier);
		this.knockback = Math.max(0.0F, knockback);
	}

	public void setDamageCurve(List<TaczGunBallistics.DamagePoint> damageCurve) {
		this.damageCurve = List.copyOf(damageCurve);
	}

	public void setTaczIds(String gunId, String ammoId) {
		this.entityData.set(DATA_GUN_ID, gunId);
		this.entityData.set(DATA_AMMO_ID, ammoId);
	}

	public void shootFromRotation(Entity shooter, float pitch, float yaw, float roll, float velocity, TaczGunBallistics.SpreadOffset spreadOffset) {
		TaczGunBallistics.Vec3Like vector = TaczGunBallistics.directionFromScriptedSpread(pitch, yaw, velocity, spreadOffset);
		this.setDeltaMovement(vector.x(), vector.y(), vector.z());
		Vec3 shooterMovement = shooter.getKnownMovement();
		this.setDeltaMovement(this.getDeltaMovement().add(shooterMovement.x, shooter.onGround() ? 0.0 : shooterMovement.y, shooterMovement.z));
		this.updateBulletRotation(this.getDeltaMovement());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_GUN_ID, "");
		builder.define(DATA_AMMO_ID, "");
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 movement = this.getDeltaMovement();
		if (movement.lengthSqr() <= 1.0E-7) {
			this.discard();
			return;
		}

		if (this.level().isClientSide()) {
			TaczBulletEffectHooks.addAmmoParticle(this);
		} else {
			this.traceServerHits();
		}

		if (this.isRemoved()) {
			return;
		}

		Vec3 next = this.position().add(movement);
		this.updateBulletRotation(movement);
		this.setPos(next.x, next.y, next.z);
		float nextGravity = this.gravity;
		float nextFriction = this.friction;
		if (this.isInWater()) {
			nextFriction = 0.4F;
			nextGravity *= 0.6F;
			this.level().addParticle(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), movement.x, movement.y, movement.z);
		}

		this.setDeltaMovement(this.getDeltaMovement().scale(1.0F - nextFriction).add(0.0, -nextGravity, 0.0));
		if (this.tickCount >= this.life) {
			this.discard();
		}
	}

	private void traceServerHits() {
		Vec3 start = this.position();
		Vec3 end = start.add(this.getDeltaMovement());
		HitResult blockResult = this.level().clipIncludingBorder(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 clippedEnd = blockResult.getType() == HitResult.Type.MISS ? end : blockResult.getLocation();
		EntityHitResult entityResult = ProjectileUtil.getEntityHitResult(
			this.level(),
			this,
			start,
			clippedEnd,
			this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0),
			entity -> EntitySelector.CAN_BE_PICKED.test(entity) && this.canHitEntity(entity)
		);

		if (entityResult != null) {
			this.onBulletHitEntity(entityResult);
			if (this.isRemoved()) {
				return;
			}
		}

		if (blockResult.getType() != HitResult.Type.MISS) {
			this.onBulletHitBlock((BlockHitResult)blockResult);
		}
	}

	private void onBulletHitEntity(EntityHitResult entityHitResult) {
		Entity target = entityHitResult.getEntity();
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().bullet(this, owner);
		float finalDamage = this.getDamage(entityHitResult.getLocation());
		boolean headshot = this.isHeadshot(target, entityHitResult.getLocation());
		if (headshot) {
			finalDamage *= this.headshotMultiplier;
		}

		boolean hurt;
		if (this.level() instanceof ServerLevel serverLevel) {
			hurt = target.hurtServer(serverLevel, source, finalDamage);
		} else {
			return;
		}

		if (!hurt) {
			return;
		}

		this.applyBulletKnockback(target);
		target.invulnerableTime = 0;
		if (target instanceof LivingEntity livingEntity && !livingEntity.isAlive() && owner instanceof ServerPlayer serverPlayer) {
			serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new TaczKillHudS2CPayload(1)));
		}

		this.level()
			.playSound(
				null,
				this.getX(),
				this.getY(),
				this.getZ(),
				SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("bullet.hit")),
				this.getSoundSource(),
				0.45F,
				headshot ? 1.25F : 1.0F
			);
		this.pierce--;
		if (this.pierce <= 0) {
			this.discard();
		}
	}

	private void onBulletHitBlock(BlockHitResult blockHitResult) {
		this.setPos(blockHitResult.getLocation());
		if (this.level() instanceof ServerLevel serverLevel) {
			Vec3 hit = blockHitResult.getLocation();
			serverLevel.sendParticles(
				new TaczBulletHoleParticleOptions(
					blockHitResult.getDirection(),
					blockHitResult.getBlockPos(),
					this.getAmmoId(),
					this.getGunId(),
					this.getGunDisplayId()
				),
				hit.x,
				hit.y,
				hit.z,
				1,
				0.0,
				0.0,
				0.0,
				0.0
			);
		}
		this.onHitBlock(blockHitResult);
		this.level().gameEvent(GameEvent.PROJECTILE_LAND, blockHitResult.getBlockPos(), GameEvent.Context.of(this, this.level().getBlockState(blockHitResult.getBlockPos())));
		this.discard();
	}

	private boolean isHeadshot(Entity target, Vec3 hitLocation) {
		if (!(target instanceof LivingEntity livingEntity)) {
			return false;
		}

		double headStart = livingEntity.getY() + livingEntity.getEyeHeight() * 0.85;
		return hitLocation.y >= headStart;
	}

	private void applyBulletKnockback(Entity target) {
		if (this.knockback <= 0.0F) {
			return;
		}

		Vec3 horizontal = this.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() > 1.0E-7) {
			Vec3 push = horizontal.normalize().scale(this.knockback);
			target.push(push.x, 0.03, push.z);
		}
	}

	private void updateBulletRotation(Vec3 movement) {
		double horizontalDistance = movement.horizontalDistance();
		this.setYRot((float)(Mth.atan2(movement.x, movement.z) * 180.0F / (float)Math.PI));
		this.setXRot((float)(Mth.atan2(movement.y, horizontalDistance) * 180.0F / (float)Math.PI));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	public float getDamage(Vec3 hitLocation) {
		double distance = this.startPos.distanceTo(hitLocation);
		if (this.damageCurve.isEmpty()) {
			return this.damage;
		}
		for (TaczGunBallistics.DamagePoint point : this.damageCurve) {
			if (distance < point.distance()) {
				return point.damage();
			}
		}
		return 0.0F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putFloat("damage", this.damage);
		valueOutput.putInt("life", this.life);
		valueOutput.putFloat("gravity", this.gravity);
		valueOutput.putFloat("friction", this.friction);
		valueOutput.putInt("pierce", this.pierce);
		valueOutput.putFloat("headshot_multiplier", this.headshotMultiplier);
		valueOutput.putFloat("knockback", this.knockback);
		valueOutput.putString("gun_id", this.getGunId());
		valueOutput.putString("ammo_id", this.getAmmoId());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.damage = valueInput.getFloatOr("damage", 0.0F);
		this.life = valueInput.getIntOr("life", DEFAULT_LIFE);
		this.gravity = valueInput.getFloatOr("gravity", DEFAULT_GRAVITY);
		this.friction = valueInput.getFloatOr("friction", DEFAULT_FRICTION);
		this.pierce = valueInput.getIntOr("pierce", DEFAULT_PIERCE);
		this.headshotMultiplier = valueInput.getFloatOr("headshot_multiplier", DEFAULT_HEADSHOT_MULTIPLIER);
		this.knockback = valueInput.getFloatOr("knockback", DEFAULT_KNOCKBACK);
		this.setTaczIds(valueInput.getStringOr("gun_id", ""), valueInput.getStringOr("ammo_id", ""));
		this.startPos = this.position();
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRender(double d, double e, double f) {
		return false;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	public ItemStack getWeapon() {
		return this.weapon;
	}

	public String getGunId() {
		return this.entityData.get(DATA_GUN_ID);
	}

	public String getAmmoId() {
		return this.entityData.get(DATA_AMMO_ID);
	}

	public String getGunDisplayId() {
		String gunId = this.getGunId();
		return gunId.isEmpty() ? "" : "minecraft:" + gunId + "_display";
	}
}
