package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.renderer.entity.state.ServerHitboxesRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState> {
	private static final float SHADOW_POWER_FALLOFF_Y = 0.5F;
	private static final float MAX_SHADOW_RADIUS = 32.0F;
	public static final float NAMETAG_SCALE = 0.025F;
	protected final EntityRenderDispatcher entityRenderDispatcher;
	private final Font font;
	protected float shadowRadius;
	protected float shadowStrength = 1.0F;

	protected EntityRenderer(EntityRendererProvider.Context context) {
		this.entityRenderDispatcher = context.getEntityRenderDispatcher();
		this.font = context.getFont();
	}

	public final int getPackedLightCoords(T entity, float f) {
		BlockPos blockPos = BlockPos.containing(entity.getLightProbePosition(f));
		return LightTexture.pack(this.getBlockLightLevel(entity, blockPos), this.getSkyLightLevel(entity, blockPos));
	}

	protected int getSkyLightLevel(T entity, BlockPos blockPos) {
		return entity.level().getBrightness(LightLayer.SKY, blockPos);
	}

	protected int getBlockLightLevel(T entity, BlockPos blockPos) {
		return entity.isOnFire() ? 15 : entity.level().getBrightness(LightLayer.BLOCK, blockPos);
	}

	public boolean shouldRender(T entity, Frustum frustum, double d, double e, double f) {
		if (!entity.shouldRender(d, e, f)) {
			return false;
		} else if (!this.affectedByCulling(entity)) {
			return true;
		} else {
			AABB aABB = this.getBoundingBoxForCulling(entity).inflate(0.5);
			if (aABB.hasNaN() || aABB.getSize() == 0.0) {
				aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0, entity.getX() + 2.0, entity.getY() + 2.0, entity.getZ() + 2.0);
			}

			if (frustum.isVisible(aABB)) {
				return true;
			} else {
				if (entity instanceof Leashable leashable) {
					Entity entity2 = leashable.getLeashHolder();
					if (entity2 != null) {
						AABB aABB2 = this.entityRenderDispatcher.getRenderer(entity2).getBoundingBoxForCulling(entity2);
						return frustum.isVisible(aABB2) || frustum.isVisible(aABB.minmax(aABB2));
					}
				}

				return false;
			}
		}
	}

	protected AABB getBoundingBoxForCulling(T entity) {
		return entity.getBoundingBox();
	}

	protected boolean affectedByCulling(T entity) {
		return true;
	}

	public Vec3 getRenderOffset(S entityRenderState) {
		return entityRenderState.passengerOffset != null ? entityRenderState.passengerOffset : Vec3.ZERO;
	}

	public void submit(S entityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (entityRenderState.leashStates != null) {
			for (EntityRenderState.LeashState leashState : entityRenderState.leashStates) {
				submitNodeCollector.submitLeash(poseStack, leashState);
			}
		}

		this.submitNameTag(entityRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected boolean shouldShowName(T entity, double d) {
		return entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity;
	}

	public Font getFont() {
		return this.font;
	}

	protected void submitNameTag(S entityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (entityRenderState.nameTag != null) {
			submitNodeCollector.submitNameTag(
				poseStack,
				entityRenderState.nameTagAttachment,
				0,
				entityRenderState.nameTag,
				!entityRenderState.isDiscrete,
				entityRenderState.lightCoords,
				entityRenderState.distanceToCameraSq,
				cameraRenderState
			);
		}
	}

	@Nullable
	protected Component getNameTag(T entity) {
		return entity.getDisplayName();
	}

	protected float getShadowRadius(S entityRenderState) {
		return this.shadowRadius;
	}

	protected float getShadowStrength(S entityRenderState) {
		return this.shadowStrength;
	}

	public abstract S createRenderState();

	public final S createRenderState(T entity, float f) {
		S entityRenderState = this.createRenderState();
		this.extractRenderState(entity, entityRenderState, f);
		this.finalizeRenderState(entity, entityRenderState);
		return entityRenderState;
	}

	public void extractRenderState(T entity, S entityRenderState, float f) {
		entityRenderState.entityType = entity.getType();
		entityRenderState.x = Mth.lerp(f, entity.xOld, entity.getX());
		entityRenderState.y = Mth.lerp(f, entity.yOld, entity.getY());
		entityRenderState.z = Mth.lerp(f, entity.zOld, entity.getZ());
		entityRenderState.isInvisible = entity.isInvisible();
		entityRenderState.ageInTicks = entity.tickCount + f;
		entityRenderState.boundingBoxWidth = entity.getBbWidth();
		entityRenderState.boundingBoxHeight = entity.getBbHeight();
		entityRenderState.eyeHeight = entity.getEyeHeight();
		if (entity.isPassenger()
			&& entity.getVehicle() instanceof AbstractMinecart abstractMinecart
			&& abstractMinecart.getBehavior() instanceof NewMinecartBehavior newMinecartBehavior
			&& newMinecartBehavior.cartHasPosRotLerp()) {
			double d = Mth.lerp(f, abstractMinecart.xOld, abstractMinecart.getX());
			double e = Mth.lerp(f, abstractMinecart.yOld, abstractMinecart.getY());
			double g = Mth.lerp(f, abstractMinecart.zOld, abstractMinecart.getZ());
			entityRenderState.passengerOffset = newMinecartBehavior.getCartLerpPosition(f).subtract(new Vec3(d, e, g));
		} else {
			entityRenderState.passengerOffset = null;
		}

		if (this.entityRenderDispatcher.camera != null) {
			entityRenderState.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entity);
			boolean bl = entityRenderState.distanceToCameraSq < 4096.0 && this.shouldShowName(entity, entityRenderState.distanceToCameraSq);
			if (bl) {
				entityRenderState.nameTag = this.getNameTag(entity);
				entityRenderState.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(f));
			} else {
				entityRenderState.nameTag = null;
			}
		}

		label83: {
			entityRenderState.isDiscrete = entity.isDiscrete();
			Level level = entity.level();
			if (entity instanceof Leashable leashable) {
				Entity h = leashable.getLeashHolder();
				if (h instanceof Entity) {
					float hx = entity.getPreciseBodyRotation(f) * (float) (Math.PI / 180.0);
					Vec3 vec3 = leashable.getLeashOffset(f);
					BlockPos blockPos = BlockPos.containing(entity.getEyePosition(f));
					BlockPos blockPos2 = BlockPos.containing(h.getEyePosition(f));
					int i = this.getBlockLightLevel(entity, blockPos);
					int j = this.entityRenderDispatcher.getRenderer(h).getBlockLightLevel(h, blockPos2);
					int k = level.getBrightness(LightLayer.SKY, blockPos);
					int l = level.getBrightness(LightLayer.SKY, blockPos2);
					boolean bl2 = h.supportQuadLeashAsHolder() && leashable.supportQuadLeash();
					int m = bl2 ? 4 : 1;
					if (entityRenderState.leashStates == null || entityRenderState.leashStates.size() != m) {
						entityRenderState.leashStates = new ArrayList(m);

						for (int n = 0; n < m; n++) {
							entityRenderState.leashStates.add(new EntityRenderState.LeashState());
						}
					}

					if (bl2) {
						float o = h.getPreciseBodyRotation(f) * (float) (Math.PI / 180.0);
						Vec3 vec32 = h.getPosition(f);
						Vec3[] vec3s = leashable.getQuadLeashOffsets();
						Vec3[] vec3s2 = h.getQuadLeashHolderOffsets();
						int p = 0;

						while (true) {
							if (p >= m) {
								break label83;
							}

							EntityRenderState.LeashState leashState = (EntityRenderState.LeashState)entityRenderState.leashStates.get(p);
							leashState.offset = vec3s[p].yRot(-hx);
							leashState.start = entity.getPosition(f).add(leashState.offset);
							leashState.end = vec32.add(vec3s2[p].yRot(-o));
							leashState.startBlockLight = i;
							leashState.endBlockLight = j;
							leashState.startSkyLight = k;
							leashState.endSkyLight = l;
							leashState.slack = false;
							p++;
						}
					} else {
						Vec3 vec33 = vec3.yRot(-hx);
						EntityRenderState.LeashState leashState2 = (EntityRenderState.LeashState)entityRenderState.leashStates.getFirst();
						leashState2.offset = vec33;
						leashState2.start = entity.getPosition(f).add(vec33);
						leashState2.end = h.getRopeHoldPosition(f);
						leashState2.startBlockLight = i;
						leashState2.endBlockLight = j;
						leashState2.startSkyLight = k;
						leashState2.endSkyLight = l;
						break label83;
					}
				}
			}

			entityRenderState.leashStates = null;
		}

		entityRenderState.displayFireAnimation = entity.displayFireAnimation();
		Minecraft minecraft = Minecraft.getInstance();
		boolean bl3 = minecraft.shouldEntityAppearGlowing(entity);
		entityRenderState.outlineColor = bl3 ? ARGB.opaque(entity.getTeamColor()) : 0;
		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) && !entityRenderState.isInvisible && !minecraft.showOnlyReducedInfo()) {
			this.extractHitboxes(entity, entityRenderState, f);
		} else {
			entityRenderState.hitboxesRenderState = null;
			entityRenderState.serverHitboxesRenderState = null;
		}

		entityRenderState.lightCoords = this.getPackedLightCoords(entity, f);
	}

	protected void finalizeRenderState(T entity, S entityRenderState) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = entity.level();
		this.extractShadow(entityRenderState, minecraft, level);
	}

	private void extractShadow(S entityRenderState, Minecraft minecraft, Level level) {
		entityRenderState.shadowPieces.clear();
		if (minecraft.options.entityShadows().get() && !entityRenderState.isInvisible) {
			float f = Math.min(this.getShadowRadius(entityRenderState), 32.0F);
			entityRenderState.shadowRadius = f;
			if (f > 0.0F) {
				double d = entityRenderState.distanceToCameraSq;
				float g = (float)((1.0 - d / 256.0) * this.getShadowStrength(entityRenderState));
				if (g > 0.0F) {
					int i = Mth.floor(entityRenderState.x - f);
					int j = Mth.floor(entityRenderState.x + f);
					int k = Mth.floor(entityRenderState.z - f);
					int l = Mth.floor(entityRenderState.z + f);
					float h = Math.min(g / 0.5F - 1.0F, f);
					int m = Mth.floor(entityRenderState.y - h);
					int n = Mth.floor(entityRenderState.y);
					MutableBlockPos mutableBlockPos = new MutableBlockPos();

					for (int o = k; o <= l; o++) {
						for (int p = i; p <= j; p++) {
							mutableBlockPos.set(p, 0, o);
							ChunkAccess chunkAccess = level.getChunk(mutableBlockPos);

							for (int q = m; q <= n; q++) {
								mutableBlockPos.setY(q);
								this.extractShadowPiece(entityRenderState, level, g, mutableBlockPos, chunkAccess);
							}
						}
					}
				}
			}
		} else {
			entityRenderState.shadowRadius = 0.0F;
		}
	}

	private void extractShadowPiece(S entityRenderState, Level level, float f, MutableBlockPos mutableBlockPos, ChunkAccess chunkAccess) {
		float g = f - (float)(entityRenderState.y - mutableBlockPos.getY()) * 0.5F;
		BlockPos blockPos = mutableBlockPos.below();
		BlockState blockState = chunkAccess.getBlockState(blockPos);
		if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
			int i = level.getMaxLocalRawBrightness(mutableBlockPos);
			if (i > 3) {
				if (blockState.isCollisionShapeFullBlock(chunkAccess, blockPos)) {
					VoxelShape voxelShape = blockState.getShape(chunkAccess, blockPos);
					if (!voxelShape.isEmpty()) {
						float h = Mth.clamp(g * 0.5F * LightTexture.getBrightness(level.dimensionType(), i), 0.0F, 1.0F);
						float j = (float)(mutableBlockPos.getX() - entityRenderState.x);
						float k = (float)(mutableBlockPos.getY() - entityRenderState.y);
						float l = (float)(mutableBlockPos.getZ() - entityRenderState.z);
						entityRenderState.shadowPieces.add(new EntityRenderState.ShadowPiece(j, k, l, voxelShape, h));
					}
				}
			}
		}
	}

	private void extractHitboxes(T entity, S entityRenderState, float f) {
		entityRenderState.hitboxesRenderState = this.extractHitboxes(entity, f, false);
		if (SharedConstants.DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES) {
			Entity entity2 = getServerSideEntity(entity);
			if (entity2 != null) {
				Vec3 vec3 = entity2.getDeltaMovement();
				entityRenderState.serverHitboxesRenderState = new ServerHitboxesRenderState(
					false, entity2.getX(), entity2.getY(), entity2.getZ(), vec3.x, vec3.y, vec3.z, entity2.getEyeHeight(), this.extractHitboxes((T)entity2, 1.0F, true)
				);
			} else {
				entityRenderState.serverHitboxesRenderState = new ServerHitboxesRenderState(true);
			}
		} else {
			entityRenderState.serverHitboxesRenderState = null;
		}
	}

	private HitboxesRenderState extractHitboxes(T entity, float f, boolean bl) {
		Builder<HitboxRenderState> builder = new Builder<>();
		AABB aABB = entity.getBoundingBox();
		HitboxRenderState hitboxRenderState;
		if (bl) {
			hitboxRenderState = new HitboxRenderState(
				aABB.minX - entity.getX(),
				aABB.minY - entity.getY(),
				aABB.minZ - entity.getZ(),
				aABB.maxX - entity.getX(),
				aABB.maxY - entity.getY(),
				aABB.maxZ - entity.getZ(),
				0.0F,
				1.0F,
				0.0F
			);
		} else {
			hitboxRenderState = new HitboxRenderState(
				aABB.minX - entity.getX(),
				aABB.minY - entity.getY(),
				aABB.minZ - entity.getZ(),
				aABB.maxX - entity.getX(),
				aABB.maxY - entity.getY(),
				aABB.maxZ - entity.getZ(),
				1.0F,
				1.0F,
				1.0F
			);
		}

		builder.add(hitboxRenderState);
		Entity entity2 = entity.getVehicle();
		if (entity2 != null) {
			float g = Math.min(entity2.getBbWidth(), entity.getBbWidth()) / 2.0F;
			float h = 0.0625F;
			Vec3 vec3 = entity2.getPassengerRidingPosition(entity).subtract(entity.position());
			HitboxRenderState hitboxRenderState2 = new HitboxRenderState(vec3.x - g, vec3.y, vec3.z - g, vec3.x + g, vec3.y + 0.0625, vec3.z + g, 1.0F, 1.0F, 0.0F);
			builder.add(hitboxRenderState2);
		}

		this.extractAdditionalHitboxes(entity, builder, f);
		Vec3 vec32 = entity.getViewVector(f);
		return new HitboxesRenderState(vec32.x, vec32.y, vec32.z, builder.build());
	}

	protected void extractAdditionalHitboxes(T entity, Builder<HitboxRenderState> builder, float f) {
	}

	@Nullable
	private static Entity getServerSideEntity(Entity entity) {
		IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
		if (integratedServer != null) {
			ServerLevel serverLevel = integratedServer.getLevel(entity.level().dimension());
			if (serverLevel != null) {
				return serverLevel.getEntity(entity.getId());
			}
		}

		return null;
	}
}
